package com.vaisentra.mobility.driver.core.security

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
        private const val KEY_ACTIVE_RIDE_ID = "driver_active_ride_id"

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

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)
    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)
    fun getPhone(): String? = prefs.getString(KEY_PHONE, null)
    fun getRole(): String? = prefs.getString(KEY_ROLE, "driver")

    fun isLoggedIn(): Boolean = !getToken().isNullOrBlank()

    fun saveActiveRideId(rideId: String?) {
        if (rideId.isNullOrBlank()) {
            prefs.edit().remove(KEY_ACTIVE_RIDE_ID).apply()
        } else {
            prefs.edit().putString(KEY_ACTIVE_RIDE_ID, rideId).apply()
        }
    }

    fun getActiveRideId(): String? = prefs.getString(KEY_ACTIVE_RIDE_ID, null)

    fun clearActiveRideId() {
        prefs.edit().remove(KEY_ACTIVE_RIDE_ID).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
