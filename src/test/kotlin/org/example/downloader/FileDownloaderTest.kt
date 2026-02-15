package org.example.downloader

import kotlinx.coroutines.test.runTest
import org.example.downloader.exception.DownloadException
import org.example.downloader.http.HttpClient
import org.example.downloader.model.ByteRange
import org.example.downloader.model.DownloadConfig
import org.example.downloader.model.FileMetadata
import java.nio.file.Path
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class FileDownloaderTest {

    @TempDir
    lateinit var tempDir: Path

    /**
     * A fake HttpClient for testing that returns predictable data.
     */
    private class FakeHttpClient(
        private val fileContent: ByteArray,
        private val supportsRanges: Boolean = true,
    ) : HttpClient {

        override suspend fun fetchMetadata(url: String): FileMetadata {
            return FileMetadata(
                contentLength = fileContent.size.toLong(),
                acceptsRanges = supportsRanges,
            )
        }

        override suspend fun downloadRange(url: String, range: ByteRange): ByteArray {
            return fileContent.copyOfRange(range.start.toInt(), range.end.toInt() + 1)
        }

        override fun close() {}
    }

    /**
     * A fake HttpClient that fails a configurable number of times before succeeding.
     * Used to test retry behavior in FileDownloader.
     */
    private class FlakyHttpClient(
        private val fileContent: ByteArray,
        private val failuresBeforeSuccess: Int,
    ) : HttpClient {

        private val attemptCounts = mutableMapOf<ByteRange, Int>()

        override suspend fun fetchMetadata(url: String): FileMetadata {
            return FileMetadata(
                contentLength = fileContent.size.toLong(),
                acceptsRanges = true,
            )
        }

        override suspend fun downloadRange(url: String, range: ByteRange): ByteArray {
            val attempts = attemptCounts.getOrDefault(range, 0) + 1
            attemptCounts[range] = attempts

            if (attempts <= failuresBeforeSuccess) {
                throw DownloadException.NetworkError("Simulated failure #$attempts for range $range")
            }

            return fileContent.copyOfRange(range.start.toInt(), range.end.toInt() + 1)
        }

        override fun close() {}
    }

    @Test
    fun `downloads small file as single chunk`() = runTest {
        val content = "Hello, World!".toByteArray()
        val httpClient = FakeHttpClient(content)
        val config = DownloadConfig(chunkSize = 1024)
        val downloader = FileDownloader(httpClient, config)

        val outputPath = tempDir.resolve("output.txt")
        downloader.download("http://example.com/file.txt", outputPath)

        assertContentEquals(content, outputPath.readBytes())
    }

    @Test
    fun `downloads file split into multiple chunks`() = runTest {
        val content = ByteArray(5000) { (it % 256).toByte() }
        val httpClient = FakeHttpClient(content)
        val config = DownloadConfig(chunkSize = 1024)
        val downloader = FileDownloader(httpClient, config)

        val outputPath = tempDir.resolve("output.bin")
        downloader.download("http://example.com/file.bin", outputPath)

        assertContentEquals(content, outputPath.readBytes())
    }

    @Test
    fun `downloads file when size equals chunk size`() = runTest {
        val content = ByteArray(1024) { (it % 256).toByte() }
        val httpClient = FakeHttpClient(content)
        val config = DownloadConfig(chunkSize = 1024)
        val downloader = FileDownloader(httpClient, config)

        val outputPath = tempDir.resolve("output.bin")
        downloader.download("http://example.com/file.bin", outputPath)

        assertContentEquals(content, outputPath.readBytes())
    }

    @Test
    fun `downloads large file with limited parallelism`() = runTest {
        val content = ByteArray(10_000_000) { (it % 256).toByte() }
        val httpClient = FakeHttpClient(content)
        val config = DownloadConfig(chunkSize = 1_048_576, maxParallelDownloads = 2)
        val downloader = FileDownloader(httpClient, config)

        val outputPath = tempDir.resolve("output.bin")
        downloader.download("http://example.com/large.bin", outputPath)

        assertContentEquals(content, outputPath.readBytes())
    }

    @Test
    fun `throws when server does not support ranges`() = runTest {
        val content = "test".toByteArray()
        val httpClient = FakeHttpClient(content, supportsRanges = false)
        val downloader = FileDownloader(httpClient)

        val outputPath = tempDir.resolve("output.txt")

        assertFailsWith<DownloadException.RangesNotSupported> {
            downloader.download("http://example.com/file.txt", outputPath)
        }
    }

    @Test
    fun `downloads 1 byte file`() = runTest {
        val content = byteArrayOf(42)
        val httpClient = FakeHttpClient(content)
        val downloader = FileDownloader(httpClient)

        val outputPath = tempDir.resolve("output.bin")
        downloader.download("http://example.com/tiny.bin", outputPath)

        assertContentEquals(content, outputPath.readBytes())
    }

    @Test
    fun `succeeds after transient failures with retry`() = runTest {
        val content = ByteArray(3000) { (it % 256).toByte() }
        val httpClient = FlakyHttpClient(content, failuresBeforeSuccess = 2)
        val config = DownloadConfig(
            chunkSize = 1024,
            maxRetries = 3,
            retryDelayMs = 0,
        )
        val downloader = FileDownloader(httpClient, config)

        val outputPath = tempDir.resolve("output.bin")
        downloader.download("http://example.com/file.bin", outputPath)

        assertContentEquals(content, outputPath.readBytes())
    }

    @Test
    fun `fails after retries are exhausted`() = runTest {
        val content = ByteArray(1024) { (it % 256).toByte() }
        val httpClient = FlakyHttpClient(content, failuresBeforeSuccess = 10)
        val config = DownloadConfig(
            chunkSize = 1024,
            maxRetries = 2,
            retryDelayMs = 0,
        )
        val downloader = FileDownloader(httpClient, config)

        val outputPath = tempDir.resolve("output.bin")

        assertFailsWith<DownloadException.NetworkError> {
            downloader.download("http://example.com/file.bin", outputPath)
        }
    }

    @Test
    fun `reports progress after each chunk`() = runTest {
        val content = ByteArray(3072) { (it % 256).toByte() } // 3 chunks of 1024
        val httpClient = FakeHttpClient(content)
        val config = DownloadConfig(chunkSize = 1024, maxParallelDownloads = 1) // sequential for predictable order

        val progressUpdates = mutableListOf<Pair<Long, Long>>()
        val listener = ProgressListener { downloaded, total ->
            progressUpdates.add(downloaded to total)
        }

        val downloader = FileDownloader(httpClient, config, listener)

        val outputPath = tempDir.resolve("output.bin")
        downloader.download("http://example.com/file.bin", outputPath)

        // Should have 3 progress updates (one per chunk)
        assertEquals(3, progressUpdates.size)

        // All updates should report correct total
        assertTrue(progressUpdates.all { it.second == 3072L })

        // Final update should show all bytes downloaded
        assertEquals(3072L, progressUpdates.last().first)
    }

    @Test
    fun `progress listener is optional`() = runTest {
        val content = "test".toByteArray()
        val httpClient = FakeHttpClient(content)
        val downloader = FileDownloader(httpClient) // no listener

        val outputPath = tempDir.resolve("output.txt")
        downloader.download("http://example.com/file.txt", outputPath)

        assertContentEquals(content, outputPath.readBytes())
    }
}
