package org.example.downloader.model

/**
 * Configuration for the file downloader.
 *
 * @property chunkSize Size of each download chunk in bytes. Default is 1 MB.
 * @property maxParallelDownloads Maximum number of chunks downloaded concurrently. Default is 4.
 */
data class DownloadConfig(
    val chunkSize: Long = DEFAULT_CHUNK_SIZE,
    val maxParallelDownloads: Int = DEFAULT_MAX_PARALLEL_DOWNLOADS,
) {

    init {
        require(chunkSize > 0) { "Chunk size must be positive, got $chunkSize" }
        require(maxParallelDownloads > 0) { "Max parallel downloads must be positive, got $maxParallelDownloads" }
    }

    companion object {
        const val DEFAULT_CHUNK_SIZE: Long = 1_048_576L // 1 MB
        const val DEFAULT_MAX_PARALLEL_DOWNLOADS: Int = 4
    }
}
