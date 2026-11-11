package com.vaisentra.mobility.core.security

import android.content.Context

/**
 * Session Scope identifier to guarantee complete isolation between
 * Customer and Driver authentication credentials and SharedPreferences files.
 */
enum class SessionScope(
    val prefsName: String,
    val keyPrefix: String,
    val defaultRole: String
) {
    CUSTOMER("luxury_customer_prefs", "", "customer"),
    DRIVER("luxury_driver_prefs", "driver_", "driver")
}

/**
 * Consolidated authoritative Token & Session Manager for Vasantra Mobility applications.
 * Enforces DRY principles by centralizing Keystore AES-256-GCM encryption, JWT access/refresh
 * token persistence, concurrent refresh coordination, biometric preferences, and server URLs
 * while preserving strict session isolation across Customer and Driver apps.
 */
open class TokenManager(
    context: Context,
    val scope: SessionScope = SessionScope.CUSTOMER
) : BaseTokenManager(context, scope.prefsName, keyPrefix = scope.keyPrefix) {

    companion object {
        @Volatile
        private var customerInstance: TokenManager? = null

        @Volatile
        private var driverInstance: TokenManager? = null

        /**
         * Resolves the authoritative TokenManager for the specified session scope.
         * Guarantees that customer and driver tokens use distinct encrypted SharedPreferences files.
         */
        @JvmStatic
        fun getInstance(context: Context, scope: SessionScope = SessionScope.CUSTOMER): TokenManager =
            when (scope) {
                SessionScope.CUSTOMER -> customerInstance ?: synchronized(this) {
                    customerInstance ?: TokenManager(context.applicationContext, SessionScope.CUSTOMER).also {
                        customerInstance = it
                    }
                }
                SessionScope.DRIVER -> driverInstance ?: synchronized(this) {
                    driverInstance ?: TokenManager(context.applicationContext, SessionScope.DRIVER).also {
                        driverInstance = it
                    }
                }
            }

        @JvmStatic
        fun getCustomerInstance(context: Context): TokenManager = getInstance(context, SessionScope.CUSTOMER)

        @JvmStatic
        fun getDriverInstance(context: Context): TokenManager = getInstance(context, SessionScope.DRIVER)
    }

    override fun getRole(): String? = getStringCompat(KEY_ROLE, scope.defaultRole) ?: scope.defaultRole
}
