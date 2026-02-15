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

/**
 * Orchestrates parallel file downloading.
 *
 * Flow:
 * 1. HEAD request to get file size and confirm range support
 * 2. Split file into byte ranges using [ChunkSplitter]
 * 3. Download all chunks in parallel (bounded by [DownloadConfig.maxParallelDownloads])
 *    with retry on transient failures
 * 4. Merge chunks into the output file using [FileMerger]
 *
 * @param httpClient The HTTP client to use for requests.
 * @param config Download configuration (chunk size, parallelism, retry settings).
 */
class FileDownloader(
    private val httpClient: HttpClient,
    private val config: DownloadConfig = DownloadConfig(),
) {

    /**
     * Downloads a file from the given URL and saves it to the output path.
     *
     * @param url The URL of the file to download.
     * @param outputPath The local path where the file will be saved.
     * @throws DownloadException.RangesNotSupported if the server does not support range requests.
     * @throws DownloadException.NetworkError if a network error occurs and all retries are exhausted.
     * @throws DownloadException.FileWriteError if writing to the output file fails.
     */
    suspend fun download(url: String, outputPath: Path) {
        val metadata = httpClient.fetchMetadata(url)

        if (!metadata.acceptsRanges) {
            throw DownloadException.RangesNotSupported(url)
        }

        val ranges = ChunkSplitter.split(metadata.contentLength, config.chunkSize)

        val chunks = downloadChunksInParallel(url, ranges)

        FileMerger.merge(chunks, outputPath)
    }

    private suspend fun downloadChunksInParallel(
        url: String,
        ranges: List<ByteRange>,
    ): Map<ByteRange, ByteArray> {
        val semaphore = Semaphore(config.maxParallelDownloads)

        return coroutineScope {
            ranges.map { range ->
                async {
                    semaphore.withPermit {
                        withRetry(
                            maxRetries = config.maxRetries,
                            initialDelayMs = config.retryDelayMs,
                            shouldRetry = { it is DownloadException.NetworkError },
                        ) {
                            range to httpClient.downloadRange(url, range)
                        }
                    }
                }
            }.awaitAll().toMap()
        }
    }
}
