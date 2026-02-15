package org.example.downloader.http

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import org.example.downloader.exception.DownloadException
import org.example.downloader.model.ByteRange
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KtorHttpClientTest {

    @Test
    fun `fetchMetadata parses Content-Length and Accept-Ranges`() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = "",
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentLength to listOf("1024"),
                    HttpHeaders.AcceptRanges to listOf("bytes"),
                ),
            )
        }

        KtorHttpClient(mockEngine).use { client ->
            val metadata = client.fetchMetadata("http://example.com/file.txt")

            assertEquals(1024L, metadata.contentLength)
            assertTrue(metadata.acceptsRanges)
        }
    }

    @Test
    fun `fetchMetadata detects when server does not support ranges`() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = "",
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentLength to listOf("2048"),
                ),
            )
        }

        KtorHttpClient(mockEngine).use { client ->
            val metadata = client.fetchMetadata("http://example.com/file.txt")

            assertEquals(2048L, metadata.contentLength)
            assertEquals(false, metadata.acceptsRanges)
        }
    }

    @Test
    fun `fetchMetadata throws when Content-Length is missing`() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = "",
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.AcceptRanges to listOf("bytes"),
                ),
            )
        }

        KtorHttpClient(mockEngine).use { client ->
            assertFailsWith<DownloadException.NetworkError> {
                client.fetchMetadata("http://example.com/file.txt")
            }
        }
    }

    @Test
    fun `downloadRange returns bytes for 206 response`() = runTest {
        val expectedBytes = "Hello, World!".toByteArray()

        val mockEngine = MockEngine { request ->
            assertEquals("bytes=0-12", request.headers[HttpHeaders.Range])

            respond(
                content = expectedBytes,
                status = HttpStatusCode.PartialContent,
                headers = headersOf(HttpHeaders.ContentType, "application/octet-stream"),
            )
        }

        KtorHttpClient(mockEngine).use { client ->
            val bytes = client.downloadRange("http://example.com/file.txt", ByteRange(0, 12))

            assertContentEquals(expectedBytes, bytes)
        }
    }

    @Test
    fun `downloadRange sends correct Range header`() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("bytes=1024-2047", request.headers[HttpHeaders.Range])

            respond(
                content = ByteArray(1024),
                status = HttpStatusCode.PartialContent,
            )
        }

        KtorHttpClient(mockEngine).use { client ->
            client.downloadRange("http://example.com/file.txt", ByteRange(1024, 2047))
        }
    }

    @Test
    fun `downloadRange throws when response is not 206`() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = "Full file content",
                status = HttpStatusCode.OK,
            )
        }

        KtorHttpClient(mockEngine).use { client ->
            val exception = assertFailsWith<DownloadException.NetworkError> {
                client.downloadRange("http://example.com/file.txt", ByteRange(0, 100))
            }
            assertTrue(exception.message!!.contains("206"))
        }
    }

    @Test
    fun `downloadFull returns entire file for 200 response`() = runTest {
        val expectedBytes = "Complete file content".toByteArray()

        val mockEngine = MockEngine { _ ->
            respond(
                content = expectedBytes,
                status = HttpStatusCode.OK,
            )
        }

        KtorHttpClient(mockEngine).use { client ->
            val bytes = client.downloadFull("http://example.com/file.txt")

            assertContentEquals(expectedBytes, bytes)
        }
    }

    @Test
    fun `downloadFull throws when response is not 200`() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = "Not Found",
                status = HttpStatusCode.NotFound,
            )
        }

        KtorHttpClient(mockEngine).use { client ->
            assertFailsWith<DownloadException.NetworkError> {
                client.downloadFull("http://example.com/missing.txt")
            }
        }
    }
}
