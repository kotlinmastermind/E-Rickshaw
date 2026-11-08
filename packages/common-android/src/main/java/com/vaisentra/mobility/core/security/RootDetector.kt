package com.vaisentra.mobility.core.security

import android.os.Build
import java.io.File

/**
 * Task 091: Root detection security engine.
 * Inspects build tags, known superuser binary paths, and execution properties
 * to detect rooted or compromised device environments and protect sensitive financial operations.
 */
object RootDetector {

    private val SU_PATHS = arrayOf(
        "/system/app/Superuser.apk",
        "/sbin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/su",
        "/su/bin/su",
        "/magisk/.core/bin/su"
    )

    fun isDeviceRooted(): Boolean {
        return checkBuildTags() || checkSuBinaries() || checkSuExecution()
    }

    private fun checkBuildTags(): Boolean {
        val tags = Build.TAGS
        return tags != null && tags.contains("test-keys")
    }

    private fun checkSuBinaries(): Boolean {
        for (path in SU_PATHS) {
            try {
                val file = File(path)
                if (file.exists()) {
                    return true
                }
            } catch (_: Exception) {
                // Access check failure
            }
        }
        return false
    }

    private fun checkSuExecution(): Boolean {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (_: Exception) {
            false
        } finally {
            process?.destroy()
        }
    }
}
