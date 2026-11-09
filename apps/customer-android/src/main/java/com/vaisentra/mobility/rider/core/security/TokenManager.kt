package com.vaisentra.mobility.rider.core.security

import android.content.Context
import com.vaisentra.mobility.core.security.BaseTokenManager

/**
 * Rider Token & Session Manager extending BaseTokenManager.
 * Centralizes persistent tokens, biometric preferences, and ride session state.
 */
class TokenManager(context: Context) : BaseTokenManager(context, "luxury_customer_prefs") {

    companion object {
        @Volatile
        private var instance: TokenManager? = null

        fun getInstance(context: Context): TokenManager =
            instance ?: synchronized(this) {
                instance ?: TokenManager(context.applicationContext).also { instance = it }
            }
    }

    override fun getRole(): String? = getStringCompat(KEY_ROLE, "customer") ?: "customer"
}
