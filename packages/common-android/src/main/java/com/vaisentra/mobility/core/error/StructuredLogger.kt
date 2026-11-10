package com.vaisentra.mobility.core.error

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Enterprise Diagnostic Logger for Vasantra Mobility.
 * Enforces automated PII and credential sanitization, asynchronous logging,
 * and in-memory breadcrumb tracking for crash diagnostics.
 */
object StructuredLogger {
    private const val TAG_PREFIX = "Vasantra"
    private const val MAX_BREADCRUMBS = 30
    private val breadcrumbs = ConcurrentLinkedQueue<String>()
    private val logScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    // Regex patterns for redacting sensitive authentication, card, and secret data
    private val TOKEN_REGEX = Regex("""(?i)(bearer\s+)[A-Za-z0-9\-._~+/]+=*""")
    private val PASSWORD_REGEX = Regex("""(?i)(password|pin|otp|secret|cvv|token)\s*[:=]\s*["']?([^"',\s]+)["']?""")
    private val CARD_REGEX = Regex("""\b(?:\d[ -]*?){13,16}\b""")

    fun sanitize(message: String): String {
        var sanitized = message
        sanitized = TOKEN_REGEX.replace(sanitized, "$1[REDACTED_TOKEN]")
        sanitized = PASSWORD_REGEX.replace(sanitized, "$1=[REDACTED]")
        sanitized = CARD_REGEX.replace(sanitized, "[REDACTED_PAN]")
        return sanitized
    }

    fun recordBreadcrumb(category: String, message: String) {
        val timestamp = synchronized(dateFormat) { dateFormat.format(Date()) }
        val entry = "[$timestamp][$category] ${sanitize(message)}"
        breadcrumbs.add(entry)
        while (breadcrumbs.size > MAX_BREADCRUMBS) {
            breadcrumbs.poll()
        }
    }

    fun getBreadcrumbs(): List<String> = breadcrumbs.toList()

    fun logError(
        module: String,
        context: String,
        error: AppError,
        retryCount: Int = 0
    ) {
        val sanitizedDiagnostic = sanitize(error.diagnosticMessage)
        val logPayload = buildString {
            append("code=").append(error.code)
            append(" | category=").append(error.category.name)
            append(" | module=").append(module)
            append(" | context=").append(context)
            append(" | correlationId=").append(error.correlationId)
            append(" | recoverable=").append(error.isRecoverable)
            append(" | retryCount=").append(retryCount)
            append(" | diagnostic=").append(sanitizedDiagnostic)
        }

        // Asynchronously dispatch diagnostic logging so Main thread is never blocked
        logScope.launch {
            try {
                Log.e("$TAG_PREFIX-$module", logPayload, error.cause)
                recordBreadcrumb(module, "ERROR [${error.code}]: $sanitizedDiagnostic")
            } catch (_: Throwable) {
                // Logging failure must never crash the app
            }
        }
    }

    fun logInfo(module: String, message: String) {
        val sanitized = sanitize(message)
        logScope.launch {
            try {
                Log.i("$TAG_PREFIX-$module", sanitized)
                recordBreadcrumb(module, sanitized)
            } catch (_: Throwable) {}
        }
    }

    fun logWarn(module: String, message: String) {
        val sanitized = sanitize(message)
        logScope.launch {
            try {
                Log.w("$TAG_PREFIX-$module", sanitized)
                recordBreadcrumb(module, "WARN: $sanitized")
            } catch (_: Throwable) {}
        }
    }
}
