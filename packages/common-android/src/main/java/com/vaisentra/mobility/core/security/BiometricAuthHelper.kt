package com.vaisentra.mobility.core.security

import android.content.Context
import android.os.Build

/**
 * Task 087: Biometric authentication helper with hardware checks and status reporting.
 * Handles biometric availability validation across Android devices.
 */
object BiometricAuthHelper {

    enum class BiometricStatus {
        AVAILABLE,
        NO_HARDWARE,
        HARDWARE_UNAVAILABLE,
        NONE_ENROLLED,
        SECURITY_UPDATE_REQUIRED,
        UNSUPPORTED
    }

    interface BiometricCallback {
        fun onAuthenticationSucceeded()
        fun onAuthenticationFailed()
        fun onAuthenticationError(errorCode: Int, errString: CharSequence)
    }

    fun canAuthenticate(context: Context): BiometricStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return BiometricStatus.UNSUPPORTED
        }
        return try {
            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
            if (keyguardManager == null || !keyguardManager.isKeyguardSecure) {
                BiometricStatus.NONE_ENROLLED
            } else {
                BiometricStatus.AVAILABLE
            }
        } catch (_: Exception) {
            BiometricStatus.HARDWARE_UNAVAILABLE
        }
    }

    fun isBiometricReady(context: Context): Boolean {
        return canAuthenticate(context) == BiometricStatus.AVAILABLE
    }
}
