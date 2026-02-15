package org.example.downloader

/**
 * Listener for download progress updates.
 *
 * This is a functional interface, so it can be implemented with a lambda:
 * ```
 * val listener = ProgressListener { downloaded, total ->
 *     println("$downloaded / $total bytes")
 * }
 * ```
 */
fun interface ProgressListener {

    /**
     * Called after each chunk is downloaded.
     *
     * @param bytesDownloaded Total bytes downloaded so far.
     * @param totalBytes Total file size in bytes.
     */
    fun onProgress(bytesDownloaded: Long, totalBytes: Long)
}
