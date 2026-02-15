package org.example.downloader

import org.example.downloader.model.ByteRange

/**
 * Splits a file into a list of byte ranges for parallel downloading.
 *
 * This is a pure function with no side effects, making it trivially testable.
 */
object ChunkSplitter {

    /**
     * Calculates byte ranges that cover the entire file.
     *
     * @param contentLength Total file size in bytes.
     * @param chunkSize Maximum size of each chunk in bytes.
     * @return Ordered list of [ByteRange] covering bytes 0 until contentLength - 1.
     */
    fun split(contentLength: Long, chunkSize: Long): List<ByteRange> {
        require(contentLength > 0) { "Content length must be positive, got $contentLength" }
        require(chunkSize > 0) { "Chunk size must be positive, got $chunkSize" }

        val ranges = mutableListOf<ByteRange>()
        var start = 0L

        while (start < contentLength) {
            val end = minOf(start + chunkSize - 1, contentLength - 1)
            ranges.add(ByteRange(start, end))
            start = end + 1
        }

        return ranges
    }
}
