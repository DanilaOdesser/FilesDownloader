package org.example.downloader.model

/**
 * Configuration for the file downloader.
 *
 * @property chunkSize Size of each download chunk in bytes. Default is 1 MB.
 * @property maxParallelDownloads Maximum number of chunks downloaded concurrently. Default is 4.
 * @property maxRetries Maximum number of retry attempts per chunk on failure. Default is 3.
 * @property retryDelayMs Initial delay between retries in milliseconds. Doubles on each attempt (exponential backoff). Default is 1000ms.
 */
data class DownloadConfig(
    val chunkSize: Long = DEFAULT_CHUNK_SIZE,
    val maxParallelDownloads: Int = DEFAULT_MAX_PARALLEL_DOWNLOADS,
    val maxRetries: Int = DEFAULT_MAX_RETRIES,
    val retryDelayMs: Long = DEFAULT_RETRY_DELAY_MS,
) {

    init {
        require(chunkSize > 0) { "Chunk size must be positive, got $chunkSize" }
        require(maxParallelDownloads > 0) { "Max parallel downloads must be positive, got $maxParallelDownloads" }
        require(maxRetries >= 0) { "Max retries must be non-negative, got $maxRetries" }
        require(retryDelayMs >= 0) { "Retry delay must be non-negative, got $retryDelayMs" }
    }

    companion object {
        const val DEFAULT_CHUNK_SIZE: Long = 1_048_576L // 1 MB
        const val DEFAULT_MAX_PARALLEL_DOWNLOADS: Int = 4
        const val DEFAULT_MAX_RETRIES: Int = 3
        const val DEFAULT_RETRY_DELAY_MS: Long = 1_000L
    }
}
