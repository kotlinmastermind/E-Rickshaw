package com.luxury.chauffeur.core.security

import android.content.Context
import android.content.SharedPreferences

/**
 * Standardized Base Token & Session Manager across Customer and Driver applications.
 * Enforces DRY principles for token persistence, auth status, user role, and FCM registration state.
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
        const val KEY_FCM_TOKEN = "fcm_device_token"
        const val KEY_FCM_TOKEN_SYNCED = "fcm_device_token_synced"
    }

    open fun saveAuth(
        token: String,
        userId: String,
        phone: String,
        role: String,
        email: String? = null,
        fullName: String? = null
    ) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_PHONE, phone)
            .putString(KEY_ROLE, role)
            .apply {
                if (email != null) putString(KEY_EMAIL, email)
                if (fullName != null) putString(KEY_FULL_NAME, fullName)
            }
            .apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)
    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)
    fun getPhone(): String? = prefs.getString(KEY_PHONE, null)
    fun getRole(): String? = prefs.getString(KEY_ROLE, null)
    fun getEmail(): String? = prefs.getString(KEY_EMAIL, null)
    fun getFullName(): String? = prefs.getString(KEY_FULL_NAME, null)

    fun isLoggedIn(): Boolean = !getToken().isNullOrBlank()

    fun isAppLockEnabled(): Boolean = !prefs.getString(KEY_APP_LOCK_PIN, null).isNullOrBlank()
    fun getAppLockPin(): String? = prefs.getString(KEY_APP_LOCK_PIN, null)
    fun setAppLockPin(pin: String?) {
        prefs.edit().putString(KEY_APP_LOCK_PIN, pin).apply()
    }

    fun isBiometricEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun getPreferredLanguage(): String = prefs.getString(KEY_LANGUAGE, "en") ?: "en"
    fun setPreferredLanguage(language: String) {
        prefs.edit().putString(KEY_LANGUAGE, language).apply()
    }

    fun saveFcmToken(token: String) {
        val currentToken = prefs.getString(KEY_FCM_TOKEN, null)
        if (currentToken != token) {
            prefs.edit()
                .putString(KEY_FCM_TOKEN, token)
                .putBoolean(KEY_FCM_TOKEN_SYNCED, false)
                .apply()
        }
    }

    fun getFcmToken(): String? = prefs.getString(KEY_FCM_TOKEN, null)
    fun isFcmTokenSynced(): Boolean = prefs.getBoolean(KEY_FCM_TOKEN_SYNCED, false)
    fun setFcmTokenSynced(synced: Boolean) {
        prefs.edit().putBoolean(KEY_FCM_TOKEN_SYNCED, synced).apply()
    }

    open fun clearAuth() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_PHONE)
            .remove(KEY_ROLE)
            .remove(KEY_EMAIL)
            .remove(KEY_FULL_NAME)
            .remove(KEY_FCM_TOKEN_SYNCED)
            .apply()
    }
}

