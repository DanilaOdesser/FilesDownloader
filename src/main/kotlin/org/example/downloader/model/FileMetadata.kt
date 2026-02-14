package org.example.downloader.model

/**
 * Metadata retrieved from a HEAD request to the file URL.
 *
 * @property contentLength Total size of the file in bytes.
 * @property acceptsRanges Whether the server supports byte-range requests.
 */
data class FileMetadata(
    val contentLength: Long,
    val acceptsRanges: Boolean,
) {

    init {
        require(contentLength > 0) { "Content length must be positive, got $contentLength" }
    }
}
