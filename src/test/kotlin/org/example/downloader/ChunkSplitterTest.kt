package org.example.downloader

import org.example.downloader.model.ByteRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChunkSplitterTest {

    @Test
    fun `splits file evenly into chunks`() {
        val ranges = ChunkSplitter.split(contentLength = 4096, chunkSize = 1024)

        assertEquals(4, ranges.size)
        assertEquals(ByteRange(0, 1023), ranges[0])
        assertEquals(ByteRange(1024, 2047), ranges[1])
        assertEquals(ByteRange(2048, 3071), ranges[2])
        assertEquals(ByteRange(3072, 4095), ranges[3])
    }

    @Test
    fun `last chunk is smaller when file does not split evenly`() {
        val ranges = ChunkSplitter.split(contentLength = 5000, chunkSize = 2048)

        assertEquals(3, ranges.size)
        assertEquals(ByteRange(0, 2047), ranges[0])
        assertEquals(ByteRange(2048, 4095), ranges[1])
        assertEquals(ByteRange(4096, 4999), ranges[2])
        assertEquals(904, ranges[2].length)
    }

    @Test
    fun `single chunk when file is smaller than chunk size`() {
        val ranges = ChunkSplitter.split(contentLength = 500, chunkSize = 1024)

        assertEquals(1, ranges.size)
        assertEquals(ByteRange(0, 499), ranges[0])
    }

    @Test
    fun `single chunk when file equals chunk size`() {
        val ranges = ChunkSplitter.split(contentLength = 1024, chunkSize = 1024)

        assertEquals(1, ranges.size)
        assertEquals(ByteRange(0, 1023), ranges[0])
    }

    @Test
    fun `handles 1 byte file`() {
        val ranges = ChunkSplitter.split(contentLength = 1, chunkSize = 1024)

        assertEquals(1, ranges.size)
        assertEquals(ByteRange(0, 0), ranges[0])
        assertEquals(1, ranges[0].length)
    }

    @Test
    fun `ranges cover entire file without gaps or overlaps`() {
        val contentLength = 10_000_000L
        val chunkSize = 3_000_000L
        val ranges = ChunkSplitter.split(contentLength, chunkSize)

        // Verify no gaps: each range starts where the previous one ended + 1
        for (i in 1 until ranges.size) {
            assertEquals(ranges[i - 1].end + 1, ranges[i].start)
        }

        // Verify full coverage
        assertEquals(0L, ranges.first().start)
        assertEquals(contentLength - 1, ranges.last().end)

        // Verify total length matches
        val totalLength = ranges.sumOf { it.length }
        assertEquals(contentLength, totalLength)
    }

    @Test
    fun `throws on zero content length`() {
        assertFailsWith<IllegalArgumentException> {
            ChunkSplitter.split(contentLength = 0, chunkSize = 1024)
        }
    }

    @Test
    fun `throws on negative content length`() {
        assertFailsWith<IllegalArgumentException> {
            ChunkSplitter.split(contentLength = -1, chunkSize = 1024)
        }
    }

    @Test
    fun `throws on zero chunk size`() {
        assertFailsWith<IllegalArgumentException> {
            ChunkSplitter.split(contentLength = 1024, chunkSize = 0)
        }
    }

    @Test
    fun `throws on negative chunk size`() {
        assertFailsWith<IllegalArgumentException> {
            ChunkSplitter.split(contentLength = 1024, chunkSize = -1)
        }
    }
}
