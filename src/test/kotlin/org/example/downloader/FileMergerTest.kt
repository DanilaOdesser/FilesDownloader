package org.example.downloader

import org.example.downloader.model.ByteRange
import java.nio.file.Path
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.example.downloader.exception.DownloadException
import org.junit.jupiter.api.io.TempDir

class FileMergerTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `merges multiple chunks in order`() {
        val chunks = mapOf(
            ByteRange(0, 4) to "Hello".toByteArray(),
            ByteRange(5, 6) to ", ".toByteArray(),
            ByteRange(7, 11) to "World".toByteArray(),
        )

        val outputPath = tempDir.resolve("output.txt")
        FileMerger.merge(chunks, outputPath)

        assertContentEquals("Hello, World".toByteArray(), outputPath.readBytes())
    }

    @Test
    fun `merges chunks regardless of insertion order`() {
        // LinkedHashMap preserves insertion order — we insert out of order
        val chunks = linkedMapOf(
            ByteRange(10, 14) to "World".toByteArray(),
            ByteRange(0, 4) to "Hello".toByteArray(),
            ByteRange(5, 9) to ", Wor".toByteArray(),
        )

        val outputPath = tempDir.resolve("output.txt")
        FileMerger.merge(chunks, outputPath)

        // FileMerger sorts by start byte, so result should be correct
        assertContentEquals("Hello, WorWorld".toByteArray(), outputPath.readBytes())
    }

    @Test
    fun `merges single chunk`() {
        val chunks = mapOf(
            ByteRange(0, 3) to "test".toByteArray(),
        )

        val outputPath = tempDir.resolve("output.txt")
        FileMerger.merge(chunks, outputPath)

        assertContentEquals("test".toByteArray(), outputPath.readBytes())
    }

    @Test
    fun `merges binary data correctly`() {
        val binaryData1 = byteArrayOf(0x00, 0x01, 0x02, 0xFF.toByte())
        val binaryData2 = byteArrayOf(0xFE.toByte(), 0xFD.toByte(), 0x03, 0x04)

        val chunks = mapOf(
            ByteRange(0, 3) to binaryData1,
            ByteRange(4, 7) to binaryData2,
        )

        val outputPath = tempDir.resolve("output.bin")
        FileMerger.merge(chunks, outputPath)

        assertContentEquals(binaryData1 + binaryData2, outputPath.readBytes())
    }

    @Test
    fun `creates file that did not exist`() {
        val outputPath = tempDir.resolve("new-file.txt")
        assertTrue(!outputPath.toFile().exists())

        FileMerger.merge(mapOf(ByteRange(0, 0) to byteArrayOf(42)), outputPath)

        assertTrue(outputPath.toFile().exists())
    }

    @Test
    fun `throws on empty chunks`() {
        val outputPath = tempDir.resolve("output.txt")

        assertFailsWith<IllegalArgumentException> {
            FileMerger.merge(emptyMap(), outputPath)
        }
    }

    @Test
    fun `throws FileWriteError on invalid path`() {
        val chunks = mapOf(ByteRange(0, 3) to "test".toByteArray())
        val invalidPath = Path.of("/nonexistent/directory/file.txt")

        assertFailsWith<DownloadException.FileWriteError> {
            FileMerger.merge(chunks, invalidPath)
        }
    }
}
