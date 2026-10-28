package com.luxury.chauffeur.customer.core.security

import android.content.Context
import android.content.SharedPreferences

class TokenManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("luxury_customer_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TOKEN = "jwt_access_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_PHONE = "user_phone"
        private const val KEY_ROLE = "user_role"
        private const val KEY_EMAIL = "user_email"
        private const val KEY_FULL_NAME = "user_full_name"
        private const val KEY_APP_LOCK_PIN = "app_lock_pin"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_HIGH_CONTRAST = "high_contrast"
        private const val KEY_REDUCE_MOTION = "reduce_motion"
        private const val KEY_TEXT_SCALE = "text_scale"
        private const val KEY_LANGUAGE = "preferred_language"
        private const val KEY_CURRENCY = "currency_code"

        @Volatile
        private var instance: TokenManager? = null

        fun getInstance(context: Context): TokenManager =
            instance ?: synchronized(this) {
                instance ?: TokenManager(context.applicationContext).also { instance = it }
            }
    }

    fun saveAuth(token: String, userId: String, phone: String, role: String, email: String? = null, fullName: String? = null) {
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
    fun getRole(): String? = prefs.getString(KEY_ROLE, "customer")
    fun getEmail(): String? = prefs.getString(KEY_EMAIL, null)
    fun getFullName(): String? = prefs.getString(KEY_FULL_NAME, null)

    fun isLoggedIn(): Boolean = !getToken().isNullOrBlank()

    fun isAppLockEnabled(): Boolean = !prefs.getString(KEY_APP_LOCK_PIN, null).isNullOrBlank()
    fun getAppLockPin(): String? = prefs.getString(KEY_APP_LOCK_PIN, null)
    fun setAppLockPin(pin: String?) {
        if (pin.isNullOrBlank()) {
            prefs.edit().remove(KEY_APP_LOCK_PIN).apply()
        } else {
            prefs.edit().putString(KEY_APP_LOCK_PIN, pin).apply()
        }
    }

    fun isBiometricEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    fun setBiometricEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()

    fun isHighContrast(): Boolean = prefs.getBoolean(KEY_HIGH_CONTRAST, false)
    fun setHighContrast(enabled: Boolean) = prefs.edit().putBoolean(KEY_HIGH_CONTRAST, enabled).apply()

    fun isReduceMotion(): Boolean = prefs.getBoolean(KEY_REDUCE_MOTION, false)
    fun setReduceMotion(enabled: Boolean) = prefs.edit().putBoolean(KEY_REDUCE_MOTION, enabled).apply()

    fun getTextScale(): String = prefs.getString(KEY_TEXT_SCALE, "standard") ?: "standard"
    fun setTextScale(scale: String) = prefs.edit().putString(KEY_TEXT_SCALE, scale).apply()

    fun getPreferredLanguage(): String = prefs.getString(KEY_LANGUAGE, "en") ?: "en"
    fun setPreferredLanguage(lang: String) = prefs.edit().putString(KEY_LANGUAGE, lang).apply()

    fun getCurrencyCode(): String = prefs.getString(KEY_CURRENCY, "INR") ?: "INR"
    fun setCurrencyCode(curr: String) = prefs.edit().putString(KEY_CURRENCY, curr).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }
}
