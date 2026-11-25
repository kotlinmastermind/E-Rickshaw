package com.vaisentra.mobility.rider.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.vaisentra.mobility.rider.data.DeviceTokenRegisterRequest
import com.vaisentra.mobility.rider.data.NetworkClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object RiderFcmDeepLinkResolver {
    const val EXTRA_FCM_ROUTE = "fcm_route"

    fun resolveRoute(data: Map<String, String>): String {
        val explicit = data[EXTRA_FCM_ROUTE] ?: data["route"]
        if (!explicit.isNullOrBlank()) return explicit

        val actionUrl = (data["action_url"] ?: "").lowercase()
        val eventType = (data["event_type"] ?: "").uppercase()

        return when {
            eventType.contains("EMERGENCY") || actionUrl.startsWith("/safety/") -> "emergency_sos"
            actionUrl.startsWith("/rides/") || actionUrl.startsWith("/shared-rides/") -> "pilot_assigned"
            actionUrl.startsWith("/support/") -> "support_tickets"
            actionUrl.startsWith("/payments/") -> "wallet_hub"
            !data["ride_id"].isNullOrBlank() -> "pilot_assigned"
            else -> "notifications"
        }
    }
}

object RiderFcmManager {
    private const val TAG = "RiderFcmManager"
    private const val PREFS_NAME = "rider_fcm_prefs"
    private const val KEY_FCM_TOKEN = "fcm_token"
    private const val KEY_SYNCED_TOKEN = "synced_fcm_token"

    const val CHANNEL_RIDE_NOTIFICATIONS = "ride_notifications"
    const val CHANNEL_SAFETY_ALERTS = "safety_alerts"
    const val CHANNEL_PROMO_UPDATES = "promo_updates"

    @Volatile
    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val logoutHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val _pendingNotificationRoute = MutableStateFlow<String?>(null)
    val pendingNotificationRoute: StateFlow<String?> = _pendingNotificationRoute.asStateFlow()

    fun init(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        ensureNotificationChannels(appCtx)
    }

    fun ensureNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val rideChannel = NotificationChannel(
            CHANNEL_RIDE_NOTIFICATIONS,
            "Ride Status & Driver Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Booking confirmations, driver arrival, trip status, and announcements."
            enableVibration(true)
        }

        val safetyChannel = NotificationChannel(
            CHANNEL_SAFETY_ALERTS,
            "Safety & SOS Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Critical ride security and emergency safety alerts."
            enableVibration(true)
        }

        val promoChannel = NotificationChannel(
            CHANNEL_PROMO_UPDATES,
            "Service & Account Updates",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Operational updates and general account notices."
        }

        nm.createNotificationChannel(rideChannel)
        nm.createNotificationChannel(safetyChannel)
        nm.createNotificationChannel(promoChannel)
    }

    fun isFirebaseConfigured(context: Context): Boolean {
        return try {
            val appCtx = context.applicationContext
            if (FirebaseApp.getApps(appCtx).isNotEmpty()) {
                true
            } else {
                FirebaseApp.initializeApp(appCtx) != null
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun syncTokenIfAuthenticated(context: Context? = null) {
        val appCtx = (context ?: appContext)?.applicationContext ?: return
        appContext = appCtx
        ensureNotificationChannels(appCtx)
        if (NetworkClient.authToken.isNullOrBlank()) return

        val stored = getSavedToken(appCtx)
        if (!stored.isNullOrBlank()) {
            registerTokenWithBackend(appCtx, stored)
        }

        if (!isFirebaseConfigured(appCtx)) {
            Log.d(TAG, "Firebase config unavailable in this build; skipping FCM token fetch.")
            return
        }

        try {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    if (!token.isNullOrBlank()) {
                        onNewToken(appCtx, token)
                    }
                }
                .addOnFailureListener { exc ->
                    Log.w(TAG, "FCM token retrieval failed: ${exc.message}")
                }
        } catch (t: Throwable) {
            Log.w(TAG, "FCM token retrieval skipped: ${t.message}")
        }
    }

    fun onNewToken(context: Context, token: String) {
        val clean = token.trim()
        if (clean.length < 8) return
        val appCtx = context.applicationContext
        appContext = appCtx
        appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FCM_TOKEN, clean)
            .apply()

        if (!NetworkClient.authToken.isNullOrBlank()) {
            registerTokenWithBackend(appCtx, clean)
        }
    }

    private fun registerTokenWithBackend(context: Context, token: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val deviceLabel = "${Build.MANUFACTURER} ${Build.MODEL}".take(64)
        scope.launch {
            try {
                val res = NetworkClient.apiService.registerDeviceToken(
                    DeviceTokenRegisterRequest(
                        fcmToken = token,
                        deviceOs = "ANDROID",
                        deviceLabel = deviceLabel
                    )
                )
                if (res.isSuccessful) {
                    prefs.edit().putString(KEY_SYNCED_TOKEN, token).apply()
                    Log.d(TAG, "FCM device token synced for rider.")
                } else {
                    Log.w(TAG, "FCM device token sync rejected: HTTP ${res.code()}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "FCM device token sync error: ${e.message}")
            }
        }
    }

    /**
     * Snapshots bearer & FCM token BEFORE synchronously clearing local session state,
     * then asynchronously deactivates the device token and revokes backend session.
     */
    fun deactivateAndLogoutAsync(context: Context? = null) {
        val appCtx = (context ?: appContext)?.applicationContext
        val prefs = appCtx?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val fcmToken = prefs?.getString(KEY_SYNCED_TOKEN, null)
            ?: prefs?.getString(KEY_FCM_TOKEN, null)
        prefs?.edit()?.remove(KEY_SYNCED_TOKEN)?.apply()

        val bearerSnapshot = NetworkClient.authToken
        val baseUrl = NetworkClient.baseUrl.trimEnd('/')
        NetworkClient.clearAuthToken()

        if (bearerSnapshot.isNullOrBlank()) return

        scope.launch {
            val jsonType = "application/json; charset=utf-8".toMediaType()
            val payloadJson = JSONObject().apply {
                if (!fcmToken.isNullOrBlank()) put("fcm_token", fcmToken)
            }.toString()

            if (!fcmToken.isNullOrBlank()) {
                try {
                    val delReq = Request.Builder()
                        .url("$baseUrl/api/v1/customers/device-token")
                        .header("Authorization", "Bearer $bearerSnapshot")
                        .header("Accept", "application/json")
                        .delete(payloadJson.toRequestBody(jsonType))
                        .build()
                    logoutHttpClient.newCall(delReq).execute().close()
                } catch (e: Exception) {
                    Log.w(TAG, "FCM token deactivate error on logout: ${e.message}")
                }
            }

            try {
                val logoutReq = Request.Builder()
                    .url("$baseUrl/api/v1/auth/logout")
                    .header("Authorization", "Bearer $bearerSnapshot")
                    .header("Accept", "application/json")
                    .post(payloadJson.toRequestBody(jsonType))
                    .build()
                logoutHttpClient.newCall(logoutReq).execute().close()
            } catch (_: Exception) {
            }
        }
    }

    fun getSavedToken(context: Context): String? {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FCM_TOKEN, null)
    }

    fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return
        val extras = intent.extras ?: return
        val data = HashMap<String, String>()
        for (key in extras.keySet()) {
            val v = extras.getString(key)
            if (!v.isNullOrBlank()) {
                data[key] = v
            }
        }
        val hasPushExtras = data.containsKey(RiderFcmDeepLinkResolver.EXTRA_FCM_ROUTE) ||
            data.containsKey("action_url") ||
            data.containsKey("ride_id") ||
            data.containsKey("event_type") ||
            data.containsKey("google.message_id")
        if (!hasPushExtras) return

        val resolved = RiderFcmDeepLinkResolver.resolveRoute(data)
        if (resolved.isNotBlank()) {
            _pendingNotificationRoute.value = resolved
        }
    }

    fun consumePendingRoute() {
        _pendingNotificationRoute.value = null
    }
}
