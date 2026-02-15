package org.example.downloader

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.example.downloader.exception.DownloadException
import org.example.downloader.http.HttpClient
import org.example.downloader.model.ByteRange
import org.example.downloader.model.DownloadConfig
import org.example.downloader.util.withRetry
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong

/**
 * Orchestrates parallel file downloading.
 *
 * Flow when the server supports range requests:
 * 1. HEAD request to get file size and confirm range support
 * 2. Split file into byte ranges using [ChunkSplitter]
 * 3. Download all chunks in parallel (bounded by [DownloadConfig.maxParallelDownloads])
 *    with retry on transient failures
 * 4. Verify each chunk's size matches the expected range length
 * 5. Merge chunks into the output file using [FileMerger]
 *
 * Fallback when the server does not support ranges:
 * 1. Downloads the entire file in a single GET request
 * 2. Writes the result directly to the output file
 *
 * @param httpClient The HTTP client to use for requests.
 * @param config Download configuration (chunk size, parallelism, retry settings).
 * @param progressListener Optional listener for download progress updates.
 */
class FileDownloader(
    private val httpClient: HttpClient,
    private val config: DownloadConfig = DownloadConfig(),
    private val progressListener: ProgressListener? = null,
) {

    /**
     * Downloads a file from the given URL and saves it to the output path.
     *
     * If the server supports byte-range requests, the file is downloaded in parallel chunks.
     * Otherwise, falls back to a single-stream full download.
     *
     * @param url The URL of the file to download.
     * @param outputPath The local path where the file will be saved.
     * @throws DownloadException.NetworkError if a network error occurs and all retries are exhausted.
     * @throws DownloadException.ChunkSizeMismatch if a downloaded chunk's size doesn't match the expected range.
     * @throws DownloadException.FileWriteError if writing to the output file fails.
     */
    suspend fun download(url: String, outputPath: Path) {
        val metadata = httpClient.fetchMetadata(url)

        if (!metadata.acceptsRanges) {
            downloadFullFile(url, outputPath, metadata.contentLength)
            return
        }

        val ranges = ChunkSplitter.split(metadata.contentLength, config.chunkSize)

        val chunks = downloadChunksInParallel(url, ranges, metadata.contentLength)

        FileMerger.merge(chunks, outputPath)
    }

    private suspend fun downloadFullFile(url: String, outputPath: Path, totalBytes: Long) {
        val bytes = withRetry(
            maxRetries = config.maxRetries,
            initialDelayMs = config.retryDelayMs,
            shouldRetry = { it is DownloadException.NetworkError },
        ) {
            httpClient.downloadFull(url)
        }

        progressListener?.onProgress(bytes.size.toLong(), totalBytes)

        val chunk = mapOf(ByteRange(0, bytes.size.toLong() - 1) to bytes)
        FileMerger.merge(chunk, outputPath)
    }

    private suspend fun downloadChunksInParallel(
        url: String,
        ranges: List<ByteRange>,
        totalBytes: Long,
    ): Map<ByteRange, ByteArray> {
        val semaphore = Semaphore(config.maxParallelDownloads)
        val downloadedBytes = AtomicLong(0)

        return coroutineScope {
            ranges.map { range ->
                async {
                    semaphore.withPermit {
                        val (downloadedRange, bytes) = withRetry(
                            maxRetries = config.maxRetries,
                            initialDelayMs = config.retryDelayMs,
                            shouldRetry = { it is DownloadException.NetworkError },
                        ) {
                            range to httpClient.downloadRange(url, range)
                        }

                        verifyChunkSize(downloadedRange, bytes)

                        val newTotal = downloadedBytes.addAndGet(bytes.size.toLong())
                        progressListener?.onProgress(newTotal, totalBytes)

                        downloadedRange to bytes
                    }
                }
            }.awaitAll().toMap()
        }
    }

    private fun verifyChunkSize(range: ByteRange, bytes: ByteArray) {
        if (bytes.size.toLong() != range.length) {
            throw DownloadException.ChunkSizeMismatch(
                expectedBytes = range.length,
                actualBytes = bytes.size,
                range = range.toRangeHeader(),
            )
        }
    }
}
