package org.example.downloader

import org.example.downloader.exception.DownloadException
import org.example.downloader.model.ByteRange
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.outputStream

/**
 * Merges downloaded chunks into a single output file.
 *
 * Chunks are written sequentially in the order of their byte ranges.
 */
object FileMerger {

    /**
     * Writes ordered chunks to the output file.
     *
     * @param chunks Map of byte ranges to their downloaded content, in any order.
     * @param outputPath Path where the merged file will be written.
     * @throws DownloadException.FileWriteError if writing to the file fails.
     */
    fun merge(chunks: Map<ByteRange, ByteArray>, outputPath: Path) {
        require(chunks.isNotEmpty()) { "Chunks must not be empty" }

        val sortedChunks = chunks.entries.sortedBy { it.key.start }

        try {
            outputPath.outputStream().buffered().use { output ->
                for ((_, data) in sortedChunks) {
                    output.write(data)
                }
            }
        } catch (e: IOException) {
            throw DownloadException.FileWriteError(
                "Failed to write to file: $outputPath", e
            )
        }
    }
}
