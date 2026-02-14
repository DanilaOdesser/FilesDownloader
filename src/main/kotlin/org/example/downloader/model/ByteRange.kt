package org.example.downloader.model

/**
 * Represents an inclusive byte range for a file chunk.
 * Used in the HTTP Range header as "bytes=start-end".
 */
data class ByteRange(val start: Long, val end: Long) {

    init {
        require(start >= 0) { "Start must be non-negative, got $start" }
        require(end >= start) { "End ($end) must be >= start ($start)" }
    }

    /** Number of bytes in this range. */
    val length: Long get() = end - start + 1

    /** Formats as the value for an HTTP Range header: "bytes=start-end". */
    fun toRangeHeader(): String = "bytes=$start-$end"
}
