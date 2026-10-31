package com.luxury.chauffeur.driver.core.security

import android.content.Context
import android.content.SharedPreferences

class DriverTokenManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("luxury_driver_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TOKEN = "driver_jwt_access_token"
        private const val KEY_USER_ID = "driver_user_id"
        private const val KEY_PHONE = "driver_phone"
        private const val KEY_ROLE = "driver_role"
        private const val KEY_FCM_TOKEN = "driver_fcm_device_token"
        private const val KEY_FCM_TOKEN_SYNCED = "driver_fcm_device_token_synced"

        @Volatile
        private var instance: DriverTokenManager? = null

        fun getInstance(context: Context): DriverTokenManager =
            instance ?: synchronized(this) {
                instance ?: DriverTokenManager(context.applicationContext).also { instance = it }
            }
    }

    fun saveAuth(token: String, userId: String, phone: String, role: String) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_PHONE, phone)
            .putString(KEY_ROLE, role)
            .apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)
    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)
    fun getPhone(): String? = prefs.getString(KEY_PHONE, null)
    fun getRole(): String? = prefs.getString(KEY_ROLE, "driver")

    fun isLoggedIn(): Boolean = !getToken().isNullOrBlank()

    fun saveFcmToken(token: String) {
        val current = getFcmToken()
        if (current != token) {
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

    fun clear() {
        val fcmToken = getFcmToken()
        prefs.edit().clear().apply()
        if (!fcmToken.isNullOrBlank()) {
            saveFcmToken(fcmToken)
            setFcmTokenSynced(false)
        }
    }
}
