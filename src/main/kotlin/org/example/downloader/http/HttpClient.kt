package org.example.downloader.http

import org.example.downloader.model.ByteRange
import org.example.downloader.model.FileMetadata

/**
 * Abstraction over HTTP operations needed for file downloading.
 *
 * Implementations can use any HTTP library (Ktor, OkHttp, Java HttpClient, etc.).
 * This interface exists to decouple the download logic from the HTTP transport,
 * making the downloader testable and the HTTP layer swappable.
 */
interface HttpClient : AutoCloseable {

    /**
     * Sends a HEAD request to retrieve file metadata.
     *
     * @param url The file URL to query.
     * @return [FileMetadata] containing content length and range support info.
     * @throws org.example.downloader.exception.DownloadException.NetworkError on request failure.
     */
    suspend fun fetchMetadata(url: String): FileMetadata

    /**
     * Downloads a specific byte range of the file.
     *
     * @param url The file URL to download from.
     * @param range The byte range to request.
     * @return The raw bytes for the requested range.
     * @throws org.example.downloader.exception.DownloadException.NetworkError on request failure.
     */
    suspend fun downloadRange(url: String, range: ByteRange): ByteArray
}
