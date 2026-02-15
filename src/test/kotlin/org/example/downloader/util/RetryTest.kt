package org.example.downloader.util

import kotlinx.coroutines.test.runTest
import org.example.downloader.exception.DownloadException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RetryTest {

    @Test
    fun `succeeds on first attempt without retry`() = runTest {
        var attempts = 0

        val result = withRetry(maxRetries = 3, initialDelayMs = 0) {
            attempts++
            "success"
        }

        assertEquals("success", result)
        assertEquals(1, attempts)
    }

    @Test
    fun `retries and succeeds on second attempt`() = runTest {
        var attempts = 0

        val result = withRetry(maxRetries = 3, initialDelayMs = 0) {
            attempts++
            if (attempts < 2) throw DownloadException.NetworkError("transient failure")
            "success"
        }

        assertEquals("success", result)
        assertEquals(2, attempts)
    }

    @Test
    fun `retries and succeeds on last attempt`() = runTest {
        var attempts = 0

        val result = withRetry(maxRetries = 2, initialDelayMs = 0) {
            attempts++
            if (attempts <= 2) throw DownloadException.NetworkError("transient failure")
            "success"
        }

        assertEquals("success", result)
        assertEquals(3, attempts) // 1 initial + 2 retries
    }

    @Test
    fun `throws after all retries exhausted`() = runTest {
        var attempts = 0

        assertFailsWith<DownloadException.NetworkError> {
            withRetry(maxRetries = 2, initialDelayMs = 0) {
                attempts++
                throw DownloadException.NetworkError("persistent failure")
            }
        }

        assertEquals(3, attempts) // 1 initial + 2 retries
    }

    @Test
    fun `does not retry when maxRetries is zero`() = runTest {
        var attempts = 0

        assertFailsWith<DownloadException.NetworkError> {
            withRetry(maxRetries = 0, initialDelayMs = 0) {
                attempts++
                throw DownloadException.NetworkError("failure")
            }
        }

        assertEquals(1, attempts)
    }

    @Test
    fun `does not retry when shouldRetry returns false`() = runTest {
        var attempts = 0

        assertFailsWith<DownloadException.RangesNotSupported> {
            withRetry(
                maxRetries = 3,
                initialDelayMs = 0,
                shouldRetry = { it is DownloadException.NetworkError },
            ) {
                attempts++
                throw DownloadException.RangesNotSupported("http://example.com")
            }
        }

        assertEquals(1, attempts) // no retry for non-network errors
    }

    @Test
    fun `only retries matching exceptions`() = runTest {
        var attempts = 0

        assertFailsWith<IllegalStateException> {
            withRetry(
                maxRetries = 3,
                initialDelayMs = 0,
                shouldRetry = { it is DownloadException.NetworkError },
            ) {
                attempts++
                throw IllegalStateException("not retryable")
            }
        }

        assertEquals(1, attempts)
    }
}
