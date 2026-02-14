package org.example.downloader.exception

/**
 * Sealed hierarchy of exceptions that can occur during file download.
 * Using a sealed class allows exhaustive when-expressions for error handling.
 */
sealed class DownloadException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** The server does not support byte-range requests. */
    class RangesNotSupported(url: String) :
        DownloadException("Server does not support range requests for: $url")

    /** A network or HTTP error occurred during the request. */
    class NetworkError(message: String, cause: Throwable? = null) :
        DownloadException(message, cause)

    /** Failed to write downloaded data to the output file. */
    class FileWriteError(message: String, cause: Throwable? = null) :
        DownloadException(message, cause)
}
