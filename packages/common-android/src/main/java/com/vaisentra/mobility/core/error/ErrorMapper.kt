package com.vaisentra.mobility.core.error

import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Authoritative Error Mapper for converting system, network, and runtime exceptions
 * into structured, safe, and typed [AppError] instances without leaking internal details.
 */
object ErrorMapper {

    fun map(throwable: Throwable): AppError {
        // If it's already an AppError, preserve it
        if (throwable is AppError) return throwable

        // CancellationException must be identified explicitly
        if (throwable is CancellationException) {
            return AppError.CoroutineCancellation(cause = throwable)
        }

        return when (throwable) {
            is HttpException -> mapHttpException(throwable)
            is SocketTimeoutException -> AppError.TimeoutError(
                diagnosticMessage = "Socket timeout: ${throwable.message ?: "read/write timeout"}",
                cause = throwable
            )
            is UnknownHostException -> AppError.DnsError(
                hostname = throwable.message,
                diagnosticMessage = "DNS resolution failure for host: ${throwable.message}",
                cause = throwable
            )
            is javax.net.ssl.SSLException -> AppError.TlsError(
                diagnosticMessage = "TLS handshake or verification failure: ${throwable.message}",
                cause = throwable
            )
            is ConnectException, is NoRouteToHostException -> AppError.ConnectionError(
                diagnosticMessage = "TCP connection refused or host unreachable: ${throwable.message}",
                cause = throwable
            )
            is IOException -> AppError.NetworkError(
                diagnosticMessage = "I/O transmission failure: ${throwable.message}",
                cause = throwable
            )
            is com.google.gson.JsonSyntaxException, is com.google.gson.stream.MalformedJsonException -> AppError.SerializationError(
                diagnosticMessage = "JSON schema parsing failed: ${throwable.message}",
                cause = throwable
            )
            is android.database.sqlite.SQLiteException -> AppError.DatabaseError(
                diagnosticMessage = "SQLite database error: ${throwable.message}",
                cause = throwable
            )
            is SecurityException -> AppError.SecurityError(
                diagnosticMessage = "Security restriction or permission rejection: ${throwable.message}",
                cause = throwable
            )
            is IllegalArgumentException -> AppError.ValidationError(
                diagnosticMessage = "Invalid parameter supplied: ${throwable.message}",
                cause = throwable
            )
            is IllegalStateException -> AppError.ConcurrencyError(
                diagnosticMessage = "Invalid component state or concurrency conflict: ${throwable.message}",
                cause = throwable
            )
            else -> AppError.UnknownError(
                diagnosticMessage = "Unhandled exception (${throwable.javaClass.simpleName}): ${throwable.message}",
                cause = throwable
            )
        }
    }

    private fun mapHttpException(httpException: HttpException): AppError {
        val statusCode = httpException.code()
        val retryAfterHeader = httpException.response()?.headers()?.get("Retry-After")
        val retryAfterSeconds = retryAfterHeader?.toLongOrNull()

        return when (statusCode) {
            401 -> AppError.AuthenticationError(cause = httpException)
            403 -> AppError.AuthorizationError(cause = httpException)
            422 -> AppError.ValidationError(
                code = "ERR_VAL_UNPROCESSABLE_ENTITY",
                userMessage = "The request could not be processed due to invalid parameters.",
                diagnosticMessage = "HTTP 422 Unprocessable Entity",
                cause = httpException
            )
            else -> AppError.HttpError(
                statusCode = statusCode,
                retryAfterSeconds = retryAfterSeconds,
                cause = httpException
            )
        }
    }
}

