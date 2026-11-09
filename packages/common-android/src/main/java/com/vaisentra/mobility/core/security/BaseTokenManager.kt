package com.vaisentra.mobility.core.security

import android.content.Context
import android.content.SharedPreferences

/**
 * Standardized Base Token & Session Manager across Customer and Driver applications.
 * Enforces DRY principles for token persistence, session rotation, auth status,
 * user role state, security PIN/biometrics, and accessibility preferences.
 */
open class BaseTokenManager(
    context: Context,
    prefsName: String,
    protected val keyPrefix: String = ""
) {
    protected val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    companion object {
        const val KEY_TOKEN = "jwt_access_token"
        const val KEY_REFRESH_TOKEN = "jwt_refresh_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_PHONE = "user_phone"
        const val KEY_ROLE = "user_role"
        const val KEY_EMAIL = "user_email"
        const val KEY_FULL_NAME = "user_full_name"
        const val KEY_APP_LOCK_PIN = "app_lock_pin"
        const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        const val KEY_HIGH_CONTRAST = "high_contrast"
        const val KEY_REDUCE_MOTION = "reduce_motion"
        const val KEY_TEXT_SCALE = "text_scale"
        const val KEY_LANGUAGE = "preferred_language"
        const val KEY_CURRENCY = "currency_code"
        const val KEY_ACTIVE_RIDE_ID = "active_ride_id"
        const val KEY_SERVER_URL = "custom_server_url"
    }

    protected fun resolveKey(key: String): String {
        return if (keyPrefix.isNotEmpty()) "$keyPrefix$key" else key
    }

    protected fun getStringCompat(key: String, defValue: String? = null): String? {
        val primaryKey = resolveKey(key)
        if (prefs.contains(primaryKey)) {
            return prefs.getString(primaryKey, defValue)
        }
        return prefs.getString(key, defValue)
    }

    open fun saveAuth(
        token: String,
        userId: String,
        phone: String,
        role: String,
        email: String? = null,
        fullName: String? = null,
        refreshToken: String? = null
    ) {
        val encryptedToken = KeystoreTokenEncryptor.encrypt(token) ?: token
        val editor = prefs.edit()
            .putString(resolveKey(KEY_TOKEN), encryptedToken)
            .putString(resolveKey(KEY_USER_ID), userId)
            .putString(resolveKey(KEY_PHONE), phone)
            .putString(resolveKey(KEY_ROLE), role)

        if (email != null) editor.putString(resolveKey(KEY_EMAIL), email)
        if (fullName != null) editor.putString(resolveKey(KEY_FULL_NAME), fullName)
        if (refreshToken != null) {
            val encryptedRefreshToken = KeystoreTokenEncryptor.encrypt(refreshToken) ?: refreshToken
            editor.putString(resolveKey(KEY_REFRESH_TOKEN), encryptedRefreshToken)
        }
        editor.apply()
    }

    open fun saveToken(token: String) {
        val encryptedToken = KeystoreTokenEncryptor.encrypt(token) ?: token
        prefs.edit().putString(resolveKey(KEY_TOKEN), encryptedToken).apply()
    }

    open fun saveTokens(token: String, refreshToken: String? = null) {
        val encryptedToken = KeystoreTokenEncryptor.encrypt(token) ?: token
        val editor = prefs.edit().putString(resolveKey(KEY_TOKEN), encryptedToken)
        if (refreshToken != null) {
            val encryptedRefreshToken = KeystoreTokenEncryptor.encrypt(refreshToken) ?: refreshToken
            editor.putString(resolveKey(KEY_REFRESH_TOKEN), encryptedRefreshToken)
        }
        editor.apply()
    }

    open fun getToken(): String? {
        val raw = getStringCompat(KEY_TOKEN) ?: return null
        return KeystoreTokenEncryptor.decrypt(raw)
    }

    open fun getRefreshToken(): String? {
        val raw = getStringCompat(KEY_REFRESH_TOKEN) ?: return null
        return KeystoreTokenEncryptor.decrypt(raw)
    }

    open fun getUserId(): String? = getStringCompat(KEY_USER_ID)
    open fun getPhone(): String? = getStringCompat(KEY_PHONE)
    open fun getRole(): String? = getStringCompat(KEY_ROLE)
    open fun getEmail(): String? = getStringCompat(KEY_EMAIL)
    open fun getFullName(): String? = getStringCompat(KEY_FULL_NAME)

    open fun isLoggedIn(): Boolean = !getToken().isNullOrBlank()

    open fun isAppLockEnabled(): Boolean = !getStringCompat(KEY_APP_LOCK_PIN).isNullOrBlank()
    open fun getAppLockPin(): String? {
        val raw = getStringCompat(KEY_APP_LOCK_PIN) ?: return null
        return KeystoreTokenEncryptor.decrypt(raw)
    }
    open fun setAppLockPin(pin: String?) {
        if (pin.isNullOrBlank()) {
            prefs.edit().remove(resolveKey(KEY_APP_LOCK_PIN)).remove(KEY_APP_LOCK_PIN).apply()
        } else {
            val encryptedPin = KeystoreTokenEncryptor.encrypt(pin) ?: pin
            prefs.edit().putString(resolveKey(KEY_APP_LOCK_PIN), encryptedPin).apply()
        }
    }

    open fun isBiometricEnabled(): Boolean = prefs.getBoolean(resolveKey(KEY_BIOMETRIC_ENABLED), false)
    open fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(resolveKey(KEY_BIOMETRIC_ENABLED), enabled).apply()
    }

    open fun isHighContrast(): Boolean = prefs.getBoolean(resolveKey(KEY_HIGH_CONTRAST), false)
    open fun setHighContrast(enabled: Boolean) {
        prefs.edit().putBoolean(resolveKey(KEY_HIGH_CONTRAST), enabled).apply()
    }

    open fun isReduceMotion(): Boolean = prefs.getBoolean(resolveKey(KEY_REDUCE_MOTION), false)
    open fun setReduceMotion(enabled: Boolean) {
        prefs.edit().putBoolean(resolveKey(KEY_REDUCE_MOTION), enabled).apply()
    }

    open fun getTextScale(): String = getStringCompat(KEY_TEXT_SCALE, "standard") ?: "standard"
    open fun setTextScale(scale: String) {
        prefs.edit().putString(resolveKey(KEY_TEXT_SCALE), scale).apply()
    }

    open fun getPreferredLanguage(): String = getStringCompat(KEY_LANGUAGE, "en") ?: "en"
    open fun setPreferredLanguage(language: String) {
        prefs.edit().putString(resolveKey(KEY_LANGUAGE), language).apply()
    }

    open fun getCurrencyCode(): String = getStringCompat(KEY_CURRENCY, "INR") ?: "INR"
    open fun setCurrencyCode(curr: String) {
        prefs.edit().putString(resolveKey(KEY_CURRENCY), curr).apply()
    }

    open fun saveActiveRideId(rideId: String?) {
        if (rideId.isNullOrBlank()) {
            prefs.edit().remove(resolveKey(KEY_ACTIVE_RIDE_ID)).remove(KEY_ACTIVE_RIDE_ID).apply()
        } else {
            prefs.edit().putString(resolveKey(KEY_ACTIVE_RIDE_ID), rideId).apply()
        }
    }

    open fun getActiveRideId(): String? = getStringCompat(KEY_ACTIVE_RIDE_ID)

    open fun clearActiveRideId() {
        prefs.edit().remove(resolveKey(KEY_ACTIVE_RIDE_ID)).remove(KEY_ACTIVE_RIDE_ID).apply()
    }

    open fun getServerUrl(): String? = getStringCompat(KEY_SERVER_URL)

    open fun setServerUrl(url: String?) {
        if (url.isNullOrBlank()) {
            prefs.edit().remove(resolveKey(KEY_SERVER_URL)).remove(KEY_SERVER_URL).apply()
        } else {
            val formatted = if (url.endsWith("/")) url else "$url/"
            prefs.edit().putString(resolveKey(KEY_SERVER_URL), formatted).apply()
        }
    }

    open fun clear() {
        prefs.edit().clear().apply()
    }

    open fun clearAuth() {
        prefs.edit()
            .remove(resolveKey(KEY_TOKEN))
            .remove(resolveKey(KEY_REFRESH_TOKEN))
            .remove(resolveKey(KEY_USER_ID))
            .remove(resolveKey(KEY_PHONE))
            .remove(resolveKey(KEY_ROLE))
            .remove(resolveKey(KEY_EMAIL))
            .remove(resolveKey(KEY_FULL_NAME))
            .apply()
    }
}
