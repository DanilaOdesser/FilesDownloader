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
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.io.TempDir

class FileDownloaderTest {

    @TempDir
    lateinit var tempDir: Path

    /**
     * A fake HttpClient for testing that returns predictable data.
     * Each chunk is filled with bytes derived from its range index.
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
}
