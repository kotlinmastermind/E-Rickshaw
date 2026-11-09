package com.vaisentra.mobility.driver.core.security

import android.content.Context
import com.vaisentra.mobility.core.security.BaseTokenManager

/**
 * Driver Token & Session Manager extending BaseTokenManager.
 * Centralizes persistent tokens, dispatch credentials, and active chauffeur ride sessions.
 */
class DriverTokenManager(context: Context) : BaseTokenManager(context, "luxury_driver_prefs", keyPrefix = "driver_") {

    companion object {
        @Volatile
        private var instance: DriverTokenManager? = null

        fun getInstance(context: Context): DriverTokenManager =
            instance ?: synchronized(this) {
                instance ?: DriverTokenManager(context.applicationContext).also { instance = it }
            }
    }

    override fun getRole(): String? = getStringCompat(KEY_ROLE, "driver") ?: "driver"
}
