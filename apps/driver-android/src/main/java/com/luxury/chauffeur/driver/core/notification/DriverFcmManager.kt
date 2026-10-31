package com.luxury.chauffeur.driver.core.notification

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.luxury.chauffeur.driver.core.network.DriverApiClient
import com.luxury.chauffeur.driver.core.network.DriverDeviceTokenPayload
import com.luxury.chauffeur.driver.core.security.DriverTokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

object DriverFcmManager {
    private const val TAG = "DriverFcmManager"
    private const val PREFS_TOPICS = "driver_subscribed_topics"
    private const val MAX_RETRIES = 3

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var isSyncing = false

    fun onNewToken(context: Context, token: String) {
        Log.i(TAG, "New driver FCM token received: ${token.take(8)}...")
        val tokenManager = DriverTokenManager.getInstance(context)
        tokenManager.saveFcmToken(token)
        syncToken(context)
    }

    fun syncToken(context: Context, retryCount: Int = 0) {
        val tokenManager = DriverTokenManager.getInstance(context)

        // 1. Defer sync if driver is not logged in yet
        if (!tokenManager.isLoggedIn()) {
            Log.d(TAG, "Driver not logged in yet. Deferring FCM token registration.")
            return
        }

        val cachedToken = tokenManager.getFcmToken()
        if (cachedToken.isNullOrBlank()) {
            // Retrieve token from Firebase with safe error handling
            retrieveToken(context)
            return
        }

        // Prevent redundant network calls if already synced
        if (tokenManager.isFcmTokenSynced()) {
            Log.d(TAG, "Driver FCM token already synchronized with backend.")
            return
        }

        if (isSyncing) return
        isSyncing = true

        scope.launch {
            try {
                val response = DriverApiClient.getService().registerDeviceToken(
                    DriverDeviceTokenPayload(fcmToken = cachedToken, deviceOs = "ANDROID")
                )
                if (response.isSuccessful) {
                    tokenManager.setFcmTokenSynced(true)
                    Log.i(TAG, "Driver FCM device token registered successfully with backend.")
                } else {
                    val code = response.code()
                    Log.w(TAG, "Failed to register driver FCM device token (HTTP $code)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Network error registering driver FCM token: ${e.message}")
                if (retryCount < MAX_RETRIES && e !is IOException) {
                    delay((1000L * (1 shl retryCount)).coerceAtMost(8000L))
                    isSyncing = false
                    syncToken(context, retryCount + 1)
                    return@launch
                }
            } finally {
                isSyncing = false
            }
        }
    }

    fun retrieveToken(context: Context, onComplete: ((String?) -> Unit)? = null) {
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful && !task.result.isNullOrBlank()) {
                    val token = task.result
                    Log.i(TAG, "Driver FCM token retrieved successfully: ${token.take(8)}...")
                    val tokenManager = DriverTokenManager.getInstance(context)
                    tokenManager.saveFcmToken(token)
                    syncToken(context)
                    onComplete?.invoke(token)
                } else {
                    val ex = task.exception
                    val msg = ex?.message.orEmpty()
                    if (msg.contains("AUTHENTICATION_FAILED", ignoreCase = true)) {
                        Log.w(TAG, "Driver FCM authentication failed. Push notifications disabled; cockpit features unaffected.")
                    } else {
                        Log.w(TAG, "Driver FCM token retrieval failed: $msg")
                    }
                    onComplete?.invoke(null)
                }
            }.addOnFailureListener { ex ->
                val msg = ex.message.orEmpty()
                if (msg.contains("AUTHENTICATION_FAILED", ignoreCase = true)) {
                    Log.w(TAG, "Driver FCM permanent authentication failure: $msg")
                } else {
                    Log.w(TAG, "Driver FCM task failure: $msg")
                }
                onComplete?.invoke(null)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to request driver Firebase token: ${t.message}")
            onComplete?.invoke(null)
        }
    }

    fun subscribeToTopic(context: Context, topic: String, onComplete: ((Boolean) -> Unit)? = null) {
        val prefs = context.getSharedPreferences(PREFS_TOPICS, Context.MODE_PRIVATE)
        val subscribed = prefs.getStringSet("topics", emptySet()) ?: emptySet()
        if (subscribed.contains(topic)) {
            Log.d(TAG, "Already subscribed to driver topic: $topic (idempotent)")
            onComplete?.invoke(true)
            return
        }

        try {
            FirebaseMessaging.getInstance().subscribeToTopic(topic).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val updated = subscribed.toMutableSet().apply { add(topic) }
                    prefs.edit().putStringSet("topics", updated).apply()
                    Log.i(TAG, "Subscribed to driver FCM topic: $topic")
                    onComplete?.invoke(true)
                } else {
                    Log.w(TAG, "Failed to subscribe to driver topic $topic: ${task.exception?.message}")
                    onComplete?.invoke(false)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Exception during driver topic subscription: ${t.message}")
            onComplete?.invoke(false)
        }
    }

    fun unsubscribeFromTopic(context: Context, topic: String, onComplete: ((Boolean) -> Unit)? = null) {
        val prefs = context.getSharedPreferences(PREFS_TOPICS, Context.MODE_PRIVATE)
        val subscribed = prefs.getStringSet("topics", emptySet()) ?: emptySet()
        if (!subscribed.contains(topic)) {
            onComplete?.invoke(true)
            return
        }

        try {
            FirebaseMessaging.getInstance().unsubscribeFromTopic(topic).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val updated = subscribed.toMutableSet().apply { remove(topic) }
                    prefs.edit().putStringSet("topics", updated).apply()
                    Log.i(TAG, "Unsubscribed from driver FCM topic: $topic")
                    onComplete?.invoke(true)
                } else {
                    Log.w(TAG, "Failed to unsubscribe from driver topic $topic: ${task.exception?.message}")
                    onComplete?.invoke(false)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Exception during driver topic unsubscription: ${t.message}")
            onComplete?.invoke(false)
        }
    }
}

