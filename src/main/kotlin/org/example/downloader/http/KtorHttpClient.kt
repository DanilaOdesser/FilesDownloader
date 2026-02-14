package org.example.downloader.http

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.example.downloader.exception.DownloadException
import org.example.downloader.model.ByteRange
import org.example.downloader.model.FileMetadata

/**
 * [HttpClient] implementation backed by Ktor with a configurable engine.
 *
 * @param engine The Ktor HTTP engine to use. Defaults to CIO (coroutine-based, lightweight).
 *               Pass a different engine (e.g. MockEngine) for testing.
 */
class KtorHttpClient(engine: HttpClientEngine = CIO.create()) : HttpClient {

    private val client = io.ktor.client.HttpClient(engine)

    override suspend fun fetchMetadata(url: String): FileMetadata {
        try {
            val response = client.head(url)

            val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                ?: throw DownloadException.NetworkError(
                    "Server did not return Content-Length header for: $url"
                )

            val acceptRanges = response.headers[HttpHeaders.AcceptRanges]
            val supportsRanges = acceptRanges?.contains("bytes", ignoreCase = true) == true

            return FileMetadata(
                contentLength = contentLength,
                acceptsRanges = supportsRanges,
            )
        } catch (e: DownloadException) {
            throw e
        } catch (e: Exception) {
            throw DownloadException.NetworkError("Failed to fetch metadata from: $url", e)
        }
    }

    override suspend fun downloadRange(url: String, range: ByteRange): ByteArray {
        try {
            val response = client.get(url) {
                header(HttpHeaders.Range, range.toRangeHeader())
            }

            if (response.status != HttpStatusCode.PartialContent) {
                throw DownloadException.NetworkError(
                    "Expected 206 Partial Content but got ${response.status} for range $range"
                )
            }

            return response.bodyAsBytes()
        } catch (e: DownloadException) {
            throw e
        } catch (e: Exception) {
            throw DownloadException.NetworkError("Failed to download range $range from: $url", e)
        }
    }

    override fun close() {
        client.close()
    }
}
