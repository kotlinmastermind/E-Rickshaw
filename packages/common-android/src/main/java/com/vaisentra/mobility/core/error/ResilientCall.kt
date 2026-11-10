package com.vaisentra.mobility.core.error

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Enterprise-grade resilient execution primitives for network, I/O, and business operations.
 * Implements bounded exponential backoff with jitter and respects retry eligibility.
 */
object ResilientCall {

    /**
     * Executes a suspending block safely. Maps exceptions to [AppError] inside [AppResult].
     * Never swallows [CancellationException], preserving Kotlin structured concurrency.
     */
    suspend fun <T> safeCall(block: suspend () -> T): AppResult<T> {
        return try {
            AppResult.Success(block())
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            AppResult.Failure(ErrorMapper.map(t))
        }
    }

    /**
     * Executes a suspending block with bounded exponential backoff and jitter.
     * Retries ONLY transient, retry-eligible failures up to [maxRetries] attempts.
     * Respects Retry-After headers and never retries non-idempotent or client validation errors.
     */
    suspend fun <T> safeCallWithRetry(
        maxRetries: Int = 3,
        initialDelayMs: Long = 500L,
        maxDelayMs: Long = 4000L,
        factor: Double = 2.0,
        isIdempotent: Boolean = true,
        block: suspend () -> T
    ): AppResult<T> {
        var currentDelay = initialDelayMs
        var attempts = 0

        while (true) {
            attempts++
            try {
                return AppResult.Success(block())
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                val appError = ErrorMapper.map(t)

                // If operation is non-idempotent, or error is not eligible for retry, or retries exhausted
                val canRetry = isIdempotent && appError.isRetryEligible && attempts <= maxRetries

                if (!canRetry) {
                    return AppResult.Failure(appError)
                }

                // Calculate delay with jitter (0..20% jitter)
                val serverRetryAfter = (appError as? AppError.HttpError)?.retryAfterSeconds
                val waitTimeMs = if (serverRetryAfter != null && serverRetryAfter > 0) {
                    (serverRetryAfter * 1000L).coerceAtMost(10_000L)
                } else {
                    val jitter = Random.nextLong(0, (currentDelay * 0.2).toLong().coerceAtLeast(10L))
                    (currentDelay + jitter).coerceAtMost(maxDelayMs)
                }

                delay(waitTimeMs)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMs)
            }
        }
    }
}
