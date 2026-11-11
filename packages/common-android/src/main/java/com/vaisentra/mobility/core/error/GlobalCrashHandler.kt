package com.vaisentra.mobility.core.error

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Enterprise Global Uncaught Exception Observer.
 * Captures diagnostic crash metadata, device forensics, and breadcrumbs
 * while preserving Android's standard process termination and preventing crash loops.
 */
class GlobalCrashHandler(
    private val context: Context,
    private val appModuleName: String,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            recordCrashTombstone(thread, throwable)
        } catch (t: Throwable) {
            // Crash observation failure must never suppress primary fatal exception
            Log.e("GlobalCrashHandler", "Failed to write diagnostic crash tombstone: ${t.message}")
        } finally {
            // Unconditionally delegate to previous/default OS handler to ensure clean process termination
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun recordCrashTombstone(thread: Thread, throwable: Throwable) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val sanitizedTrace = StructuredLogger.sanitize(Log.getStackTraceString(throwable))
        val recentBreadcrumbs = StructuredLogger.getBreadcrumbs().takeLast(20).joinToString("\n  ")

        val tombstone = buildString {
            appendLine("=== VASANTRA MOBILITY DIAGNOSTIC CRASH FORENSIC ===")
            appendLine("Timestamp: $timestamp")
            appendLine("Module: $appModuleName")
            appendLine("Thread: ${thread.name} (id=${thread.id}, priority=${thread.priority})")
            appendLine("Exception: ${throwable.javaClass.name}")
            appendLine("Message: ${StructuredLogger.sanitize(throwable.message.orEmpty())}")
            appendLine("Android OS: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})")
            appendLine("Recent Breadcrumbs:")
            appendLine("  $recentBreadcrumbs")
            appendLine("Sanitized Stack Trace:")
            appendLine(sanitizedTrace)
            appendLine("====================================================")
        }

        Log.e("VasantraCrashForensic", tombstone)

        // Write tombstone to local cache file for post-crash retrieval
        try {
            val crashDir = File(context.cacheDir, "crash_forensics")
            if (!crashDir.exists()) crashDir.mkdirs()
            val crashFile = File(crashDir, "latest_crash_tombstone.txt")
            crashFile.writeText(tombstone)
        } catch (_: Throwable) {}
    }

    companion object {
        fun install(context: Context, appModuleName: String) {
            val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
            if (currentHandler !is GlobalCrashHandler) {
                Thread.setDefaultUncaughtExceptionHandler(
                    GlobalCrashHandler(context.applicationContext, appModuleName, currentHandler)
                )
            }
        }
    }
}

