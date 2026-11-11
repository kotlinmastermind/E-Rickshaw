package com.vaisentra.mobility.core.error

import java.util.UUID

/**
 * Standardized error categories across Vasantra Mobility Android platform.
 */
enum class ErrorCategory {
    NETWORK,
    TIMEOUT,
    CONNECTION,
    AUTHENTICATION,
    AUTHORIZATION,
    VALIDATION,
    DATABASE,
    STORAGE,
    SERIALIZATION,
    RUNTIME,
    COROUTINE_CANCELLATION,
    CONCURRENCY,
    PERMISSION,
    LOCATION,
    WEB_SOCKET,
    PAYMENT,
    SECURITY,
    RESOURCE,
    CONFIGURATION,
    EXTERNAL_SDK,
    UNKNOWN
}

/**
 * Enterprise-grade typed AppError hierarchy.
 * Encapsulates safe user-facing messaging, sanitized diagnostics,
 * recoverability classification, and retry eligibility.
 */
sealed class AppError(
    open val code: String,
    open val userMessage: String,
    open val diagnosticMessage: String,
    open val category: ErrorCategory,
    open val isRecoverable: Boolean,
    open val isRetryEligible: Boolean,
    open val correlationId: String = UUID.randomUUID().toString(),
    override val cause: Throwable? = null
) : Exception(diagnosticMessage, cause) {

    // 1. Network & Connectivity Errors
    data class NetworkError(
        override val code: String = "ERR_NET_GENERAL",
        override val userMessage: String = "Network connection unavailable. Please check your internet connection.",
        override val diagnosticMessage: String = "Network transport or connectivity failure",
        override val isRecoverable: Boolean = true,
        override val isRetryEligible: Boolean = true,
        override val correlationId: String = UUID.randomUUID().toString(),
        override val cause: Throwable? = null
    ) : AppError(code, userMessage, diagnosticMessage, ErrorCategory.NETWORK, isRecoverable, isRetryEligible, correlationId, cause)

    data class TimeoutError(
        override val code: String = "ERR_NET_TIMEOUT",
        override val userMessage: String = "The server is taking too long to respond. Please try again.",
        override val diagnosticMessage: String = "Network connect, read, or write timeout",
        val timeoutMs: Long = 0L,
        override val isRecoverable: Boolean = true,
        override val isRetryEligible: Boolean = true,
        override val correlationId: String = UUID.randomUUID().toString(),
        override val cause: Throwable? = null
    ) : AppError(code, userMessage, diagnosticMessage, ErrorCategory.TIMEOUT, isRecoverable, isRetryEligible, correlationId, cause)

    open class ConnectionError(
        override val code: String = "ERR_NET_CONNECTION",
        override val userMessage: String = "Cannot connect to server. Please verify your connection.",
        override val diagnosticMessage: String = "TCP connection refused or host unreachable",
        override val isRecoverable: Boolean = true,
        override val isRetryEligible: Boolean = true,
        override val correlationId: String = UUID.randomUUID().toString(),
        override val cause: Throwable? = null
    ) : AppError(code, userMessage, diagnosticMessage, ErrorCategory.CONNECTION, isRecoverable, isRetryEligible, correlationId, cause)

    class DnsError(
        val hostname: String? = null,
        code: String = "ERR_NET_CONNECTION",
        userMessage: String = "Unable to resolve server address. Please check your internet and DNS settings.",
        diagnosticMessage: String = "DNS lookup failure${hostname?.let { " for host: $it" } ?: ""}",
        isRecoverable: Boolean = true,
        isRetryEligible: Boolean = true,
        correlationId: String = UUID.randomUUID().toString(),
        cause: Throwable? = null
    ) : ConnectionError(code, userMessage, diagnosticMessage, isRecoverable, isRetryEligible, correlationId, cause)

    data class TlsError(
        override val code: String = "ERR_NET_TLS",
        override val userMessage: String = "Secure TLS connection could not be established. Please check system date/time and network security.",
        override val diagnosticMessage: String = "TLS/SSL handshake or certificate verification failure",
        override val isRecoverable: Boolean = false,
        override val isRetryEligible: Boolean = false,
        override val correlationId: String = UUID.randomUUID().toString(),
        override val cause: Throwable? = null
    ) : AppError(code, userMessage, diagnosticMessage, ErrorCategory.SECURITY, isRecoverable, isRetryEligible, correlationId, cause)

    data class EmptyResponseError(
        val httpCode: Int = 200,
        override val code: String = "ERR_RESP_EMPTY",
        override val userMessage: String = "Received an empty response from server. Please try again.",
        override val diagnosticMessage: String = "HTTP $httpCode response body was unexpectedly null or empty",
        override val isRecoverable: Boolean = true,
        override val isRetryEligible: Boolean = true,
        override val correlationId: String = UUID.randomUUID().toString(),
        override val cause: Throwable? = null
    ) : AppError(code, userMessage, diagnosticMessage, ErrorCategory.SERIALIZATION, isRecoverable, isRetryEligible, correlationId, cause)

    data class HttpError(
        val statusCode: Int,
        override val code: String = "ERR_HTTP_$statusCode",
        override val userMessage: String = getUserMessageForHttpStatus(statusCode),
        override val diagnosticMessage: String = "HTTP Server response error: status $statusCode",
        val retryAfterSeconds: Long? = null,
        override val isRecoverable: Boolean = statusCode in 500..599 || statusCode == 429 || statusCode == 408,
        override val isRetryEligible: Boolean = statusCode in 500..599 || statusCode == 429 || statusCode == 408,
        override val correlationId: String = UUID.randomUUID().toString(),
        override val cause: Throwable? = null
    ) : AppError(code, userMessage, diagnosticMessage, ErrorCategory.NETWORK, isRecoverable, isRetryEligible, correlationId, cause)

    // 2. Authentication & Authorization Errors
    data class AuthenticationError(
        override val code: String = "ERR_AUTH_UNAUTHORIZED",
        override val userMessage: String = "Your session has expired. Please sign in again.",
        override val diagnosticMessage: String = "Authentication credentials missing, expired, or rejected (401)",
        override val isRecoverable: Boolean = false,
        override val isRetryEligible: Boolean = false,
        override val correlationId: String = UUID.randomUUID().toString(),
        override val cause: Throwable? = null
    ) : AppError(code, userMessage, diagnosticMessage, ErrorCategory.AUTHENTICATION, isRecoverable, isRetryEligible, correlationId, cause)

    data class AuthorizationError(
        override val code: String = "ERR_AUTH_FORBIDDEN",
        override val userMessage: String = "You do not have permission to perform this action.",
        override val diagnosticMessage: String = "Access forbidden for current user credentials (403)",
        override val isRecoverable: Boolean = false,
        override val isRetryEligible: Boolean = false,
        override val correlationId: String = UUID.randomUUID().toString(),
        override val cause: Throwable? = null
    ) : AppError(code, userMessage, diagnosticMessage, ErrorCategory.AUTHORIZATION, isRecoverable, isRetryEligible, correlationId, cause)

    // 3. Validation Errors
    data class ValidationError(
        val fieldName: String? = null,
        override val code: String = "ERR_VAL_INVALID",
        override val userMessage: String = "Please review your input and try again.",
        override val diagnosticMessage: String = "Validation rejected${fieldName?.let { " for field: $it" } ?: ""}",
        override val isRecoverable: Boolean = true,
        override val isRetryEligible: Boolean = false,
        override val correlationId: String = UUID.randomUUID().toString(),
        override val cause: Throwable? = null
    ) : AppError(code, userMessage, diagnosticMessage, ErrorCategory.VALIDATION, isRecoverable, isRetryEligible, correlationId, cause)

    // 4. Persistence & Storage Errors
    data class DatabaseError(
        override val code: String = "ERR_DB_OPERATION",
        override val userMessage: String = "A local data error occurred. Your changes were safely protected.",
        override val diagnosticMessage: String = "SQLite or Room database operation failure",
        override val isRecoverable: Boolean = true,
        override val isRetryEligible: Boolean = true,
        override val correlationId: String = UUID.randomUUID().toString(),
        override val cause: Throwable? = null
    ) : AppError(code, userMessage, diagnosticMessage, ErrorCategory.DATABASE, isRecoverable, isRetryEligible, correlationId, cause)

    data class StorageError(
        override val code: String = "ERR_STORAGE_IO",
        override val userMessage: String = "Storage error encountered. Please check available disk space.",
        override val diagnosticMessage: String = "Local file system read/write or cache storage failure",
        override val isRecoverable: Boolean = true,
        override val isRetryEligible: Boolean = true,
        override val correlationId: String = UUID.randomUUID().toString(),
        override val cause: Throwable? = null
    ) : AppError(code, userMessage, diagnosticMessage, ErrorCategory.STORAGE, isRecoverable, isRetryEligible, correlationId, cause)

    // 5. Data Serialization Errors
    data class SerializationError(
        override val code: String = "ERR_SERIALIZATION",
        override val userMessage: String = "Received an unexpected server response. Please try again later.",
        override val diagnosticMessage: String = "JSON deserialization, syntax or schema mismatch",
        override val isRecoverable: Boolean = false,
        override val isRetryEligible: Boolean = false,
        override val correlationId: String = UUID.randomUUID().toString(),
        override val cause: Throwable? = null
    ) : AppError(code, userMessage, diagnosticMessage, ErrorCategory.SERIALIZATION, isRecoverable, isRetryEligible, correlationId, cause)

    // 6. Runtime & Concurrency Errors
    data class RuntimeError(
        override val code: String = "ERR_RUNTIME_EXCEPTION",
        override val userMessage: String = "An unexpected error occurred. Please try again.",
        override val diagnosticMessage: String = "Uncaught runtime exception occurred",
        override val isRecoverable: Boolean = true,
        override val isRetryEligible: Boolean = false,
        override val correlationId: String = UUID.randomUUID().toString(),
        override val cause: Throwable? = null
    ) : AppError(code, userMessage, diagnosticMessage, ErrorCategory.RUNTIME, isRecoverable, isRetryEligible, correlationId, cause)

    data class CoroutineCancellation(
        override val code: String = "ERR_COROUTINE_CANCELLED",
        override val userMessage: String = "Operation was cancelled.",
        override val diagnosticMessage: String = "Coroutine job was normally cancelled",
        override val isRecoverable: Boolean = true,
        override val isRetryEligible: Boolean = false,
        override val correlationId: String = UUID.randomUUID().toString(),
        override val cause: Throwable? = null
    ) : AppError(code, userMessage, diagnosticMessage, ErrorCategory.COROUTINE_CANCELLATION, isRecoverable, isRetryEligible, correlationId, cause)

    data class ConcurrencyError(
        override val code: String = "ERR_CONCURRENCY_CONFLICT",
        override val userMessage: String = "Operation conflict. Please retry.",
        override val diagnosticMessage: String = "Concurrent modification or thread synchronization conflict",
        override val isRecoverable: Boolean = true,
        override val isRetryEligible: Boolean = true,
        override val correlationId: String = UUID.randomUUID().toString(),
        override val cause: Throwable? = null
    ) : AppError(code, userMessage, diagnosticMessage, ErrorCategory.CONCURRENCY, isRecoverable, isRetryEligible, correlationId, cause)

    // 7. Permission & Hardware Location Errors
    data class PermissionError(
        val permissionName: String,
        override val code: String = "ERR_PERM_DENIED",
        override val userMessage: String = "Permission required. Please grant access in Settings.",
        override val diagnosticMessage: String = "Required Android permission denied: $permissionName",
        override val isRecoverable: Boolean = true,
        override val isRetryEligible: Boolean = false,
        override val correlationId: String = UUID.randomUUID().toString(),
        override val cause: Throwable? = null
    ) : AppError(code, userMessage, diagnosticMessage, ErrorCategory.PERMISSION, isRecoverable, isRetryEligible, correlationId, cause)

    data class LocationError(
        val isGpsDisabled: Boolean = false,
        override val code: String = "ERR_LOC_UNAVAILABLE",
        override val userMessage: String = if (isGpsDisabled) "GPS is disabled. Please turn on location services." else "Unable to acquire GPS fix. Please ensure you have a clear view of the sky.",
        override val diagnosticMessage: String = "Location services unavailable (gpsDisabled=$isGpsDisabled)",
        override val isRecoverable: Boolean = true,
        override val isRetryEligible: Boolean = true,
        override val correlationId: String = UUID.randomUUID().toString(),
        override val cause: Throwable? = null
    ) : AppError(code, userMessage, diagnosticMessage, ErrorCategory.LOCATION, isRecoverable, isRetryEligible, correlationId, cause)

    // 8. WebSocket & Real-Time Errors
    data class WebSocketError(
        val isClosedByServer: Boolean = false,
        override val code: String = "ERR_WS_DISCONNECTED",
        override val userMessage: String = "Real-time updates interrupted. Reconnecting...",
        override val diagnosticMessage: String = "WebSocket failure (closedByServer=$isClosedByServer)",
        override val isRecoverable: Boolean = true,
        override val isRetryEligible: Boolean = true,
        override val correlationId: String = UUID.randomUUID().toString(),
        override val cause: Throwable? = null
    ) : AppError(code, userMessage, diagnosticMessage, ErrorCategory.WEB_SOCKET, isRecoverable, isRetryEligible, correlationId, cause)

    // 9. Payment Errors (Strict Non-Retry Guarantee)
    data class PaymentError(
        val paymentId: String? = null,
        val orderId: String? = null,
        override val code: String = "ERR_PAY_GATEWAY",
        override val userMessage: String = "Payment was not completed. You have not been charged.",
        override val diagnosticMessage: String = "Payment gateway failed or cancelled (orderId=$orderId)",
        override val isRecoverable: Boolean = true,
        override val isRetryEligible: Boolean = false, // NEVER retry payments automatically
        override val correlationId: String = UUID.randomUUID().toString(),
        override val cause: Throwable? = null
    ) : AppError(code, userMessage, diagnosticMessage, ErrorCategory.PAYMENT, isRecoverable, isRetryEligible, correlationId, cause)

    // 10. Security & Cryptography Errors
    data class SecurityError(
        override val code: String = "ERR_SEC_INTEGRITY",
        override val userMessage: String = "Security check failed. This action cannot be completed.",
        override val diagnosticMessage: String = "Android KeyStore or cryptographic integrity failure",
        override val isRecoverable: Boolean = false,
        override val isRetryEligible: Boolean = false,
        override val correlationId: String = UUID.randomUUID().toString(),
        override val cause: Throwable? = null
    ) : AppError(code, userMessage, diagnosticMessage, ErrorCategory.SECURITY, isRecoverable, isRetryEligible, correlationId, cause)

    // 11. Resource & System Errors
    data class ResourceError(
        override val code: String = "ERR_SYS_RESOURCE",
        override val userMessage: String = "The device is low on memory or resources.",
        override val diagnosticMessage: String = "System resource exhaustion or low memory warning",
        override val isRecoverable: Boolean = false,
        override val isRetryEligible: Boolean = false,
        override val correlationId: String = UUID.randomUUID().toString(),
        override val cause: Throwable? = null
    ) : AppError(code, userMessage, diagnosticMessage, ErrorCategory.RESOURCE, isRecoverable, isRetryEligible, correlationId, cause)

    data class ConfigurationError(
        override val code: String = "ERR_CONFIG_INVALID",
        override val userMessage: String = "Application configuration error. Please update the application.",
        override val diagnosticMessage: String = "Missing or malformed application configuration parameter",
        override val isRecoverable: Boolean = false,
        override val isRetryEligible: Boolean = false,
        override val correlationId: String = UUID.randomUUID().toString(),
        override val cause: Throwable? = null
    ) : AppError(code, userMessage, diagnosticMessage, ErrorCategory.CONFIGURATION, isRecoverable, isRetryEligible, correlationId, cause)

    data class ExternalSdkError(
        val sdkName: String,
        override val code: String = "ERR_EXT_SDK",
        override val userMessage: String = "An error occurred with a third-party service.",
        override val diagnosticMessage: String = "External SDK exception from $sdkName",
        override val isRecoverable: Boolean = true,
        override val isRetryEligible: Boolean = false,
        override val correlationId: String = UUID.randomUUID().toString(),
        override val cause: Throwable? = null
    ) : AppError(code, userMessage, diagnosticMessage, ErrorCategory.EXTERNAL_SDK, isRecoverable, isRetryEligible, correlationId, cause)

    data class UnknownError(
        override val code: String = "ERR_UNKNOWN",
        override val userMessage: String = "An unexpected error occurred. Please try again.",
        override val diagnosticMessage: String = "Unclassified or unexpected failure",
        override val isRecoverable: Boolean = true,
        override val isRetryEligible: Boolean = true,
        override val correlationId: String = UUID.randomUUID().toString(),
        override val cause: Throwable? = null
    ) : AppError(code, userMessage, diagnosticMessage, ErrorCategory.UNKNOWN, isRecoverable, isRetryEligible, correlationId, cause)

    companion object {
        fun getUserMessageForHttpStatus(statusCode: Int): String {
            return when (statusCode) {
                400 -> "Invalid request. Please verify the submitted data."
                401 -> "Your session has expired. Please log in again."
                403 -> "You do not have permission to access this resource."
                404 -> "The requested resource was not found."
                408 -> "Request timed out. Please try again."
                409 -> "A conflict occurred with the current state. Please refresh and retry."
                422 -> "Validation failed. Please verify the information entered."
                429 -> "Too many requests. Please wait a moment and try again."
                500 -> "Internal server error. Please try again shortly."
                502 -> "Bad Gateway. Backend server is starting up or temporarily unavailable. Please retry in a few moments."
                503 -> "Service temporarily unavailable. Please try again shortly."
                504 -> "Gateway timeout. Server took too long to respond. Please try again."
                in 500..599 -> "Our servers are experiencing issues. Please try again shortly."
                else -> "An unexpected network response was received. Please try again."
            }
        }
    }
}

