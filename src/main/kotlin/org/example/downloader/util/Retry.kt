package org.example.downloader.util

import kotlinx.coroutines.delay

/**
 * Retries a suspending [block] up to [maxRetries] times with exponential backoff.
 *
 * @param maxRetries Maximum number of retry attempts (0 means no retries, just one attempt).
 * @param initialDelayMs Delay before the first retry, in milliseconds. Doubles on each subsequent attempt.
 * @param shouldRetry Predicate to decide if the exception is retryable. Defaults to retrying all exceptions.
 * @param block The operation to attempt.
 * @return The result of [block] on success.
 * @throws Exception The last exception if all attempts are exhausted.
 */
suspend fun <T> withRetry(
    maxRetries: Int,
    initialDelayMs: Long,
    shouldRetry: (Exception) -> Boolean = { true },
    block: suspend () -> T,
): T {
    var currentDelay = initialDelayMs
    var lastException: Exception? = null

    repeat(maxRetries + 1) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            lastException = e

            if (attempt >= maxRetries || !shouldRetry(e)) {
                throw e
            }

            delay(currentDelay)
            currentDelay *= 2
        }
    }

    // Unreachable, but satisfies the compiler
    throw lastException!!
}
