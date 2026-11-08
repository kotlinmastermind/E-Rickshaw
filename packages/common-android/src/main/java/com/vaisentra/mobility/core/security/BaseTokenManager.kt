package com.vaisentra.mobility.core.security

import android.content.Context
import android.content.SharedPreferences

/**
 * Standardized Base Token & Session Manager across Customer and Driver applications.
 * Enforces DRY principles for token persistence, auth status, and user role state.
 */
open class BaseTokenManager(
    context: Context,
    prefsName: String
) {
    protected val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    companion object {
        const val KEY_TOKEN = "jwt_access_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_PHONE = "user_phone"
        const val KEY_ROLE = "user_role"
        const val KEY_EMAIL = "user_email"
        const val KEY_FULL_NAME = "user_full_name"
        const val KEY_APP_LOCK_PIN = "app_lock_pin"
        const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        const val KEY_LANGUAGE = "preferred_language"
    }

    open fun saveAuth(
        token: String,
        userId: String,
        phone: String,
        role: String,
        email: String? = null,
        fullName: String? = null
    ) {
        val encryptedToken = KeystoreTokenEncryptor.encrypt(token) ?: token
        prefs.edit()
            .putString(KEY_TOKEN, encryptedToken)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_PHONE, phone)
            .putString(KEY_ROLE, role)
            .apply {
                if (email != null) putString(KEY_EMAIL, email)
                if (fullName != null) putString(KEY_FULL_NAME, fullName)
            }
            .apply()
    }

    fun getToken(): String? {
        val raw = prefs.getString(KEY_TOKEN, null) ?: return null
        return KeystoreTokenEncryptor.decrypt(raw)
    }
    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)
    fun getPhone(): String? = prefs.getString(KEY_PHONE, null)
    fun getRole(): String? = prefs.getString(KEY_ROLE, null)
    fun getEmail(): String? = prefs.getString(KEY_EMAIL, null)
    fun getFullName(): String? = prefs.getString(KEY_FULL_NAME, null)

    fun isLoggedIn(): Boolean = !getToken().isNullOrBlank()

    fun isAppLockEnabled(): Boolean = !prefs.getString(KEY_APP_LOCK_PIN, null).isNullOrBlank()
    fun getAppLockPin(): String? {
        val raw = prefs.getString(KEY_APP_LOCK_PIN, null) ?: return null
        return KeystoreTokenEncryptor.decrypt(raw)
    }
    fun setAppLockPin(pin: String?) {
        val encryptedPin = KeystoreTokenEncryptor.encrypt(pin)
        prefs.edit().putString(KEY_APP_LOCK_PIN, encryptedPin).apply()
    }

    fun isBiometricEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun getPreferredLanguage(): String = prefs.getString(KEY_LANGUAGE, "en") ?: "en"
    fun setPreferredLanguage(language: String) {
        prefs.edit().putString(KEY_LANGUAGE, language).apply()
    }

    open fun clearAuth() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_PHONE)
            .remove(KEY_ROLE)
            .remove(KEY_EMAIL)
            .remove(KEY_FULL_NAME)
            .apply()
    }
}

