package com.luxury.chauffeur.customer.core.notification

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.luxury.chauffeur.customer.core.network.ApiClient
import com.luxury.chauffeur.customer.core.network.CustomerDeviceTokenPayload
import com.luxury.chauffeur.customer.core.security.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

object CustomerFcmManager {
    private const val TAG = "CustomerFcmManager"
    private const val PREFS_TOPICS = "customer_subscribed_topics"
    private const val MAX_RETRIES = 3

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var isSyncing = false

    fun onNewToken(context: Context, token: String) {
        Log.i(TAG, "New FCM token received: ${token.take(8)}...")
        val tokenManager = TokenManager.getInstance(context)
        tokenManager.saveFcmToken(token)
        syncToken(context)
    }

    fun syncToken(context: Context, retryCount: Int = 0) {
        val tokenManager = TokenManager.getInstance(context)

        // 1. If user is not logged in, defer synchronization until user logs in
        if (!tokenManager.isLoggedIn()) {
            Log.d(TAG, "Customer not logged in yet. Deferring FCM token registration.")
            return
        }

        val cachedToken = tokenManager.getFcmToken()
        if (cachedToken.isNullOrBlank()) {
            // Retrieve token from Firebase with safe error handling
            retrieveToken(context)
            return
        }

        // If already synced, no need for duplicate network call
        if (tokenManager.isFcmTokenSynced()) {
            Log.d(TAG, "FCM token already synchronized with backend.")
            return
        }

        if (isSyncing) return
        isSyncing = true

        scope.launch {
            try {
                val response = ApiClient.getService().registerCustomerDeviceToken(
                    CustomerDeviceTokenPayload(fcmToken = cachedToken, deviceOs = "ANDROID")
                )
                if (response.isSuccessful) {
                    tokenManager.setFcmTokenSynced(true)
                    Log.i(TAG, "Customer FCM device token registered successfully with backend.")
                } else {
                    val code = response.code()
                    Log.w(TAG, "Failed to register FCM device token (HTTP $code)")
                    if (code == 401) {
                        // Auth expired, token remains unsynced for next login
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Network error registering FCM token: ${e.message}")
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
                    Log.i(TAG, "FCM token retrieved successfully: ${token.take(8)}...")
                    val tokenManager = TokenManager.getInstance(context)
                    tokenManager.saveFcmToken(token)
                    syncToken(context)
                    onComplete?.invoke(token)
                } else {
                    val ex = task.exception
                    val msg = ex?.message.orEmpty()
                    if (msg.contains("AUTHENTICATION_FAILED", ignoreCase = true)) {
                        // Permanent failure (signing key mismatch or Play Services issue)
                        Log.w(TAG, "FCM authentication failed. Push notifications disabled; app features unaffected.")
                    } else {
                        Log.w(TAG, "FCM token retrieval failed: $msg")
                    }
                    onComplete?.invoke(null)
                }
            }.addOnFailureListener { ex ->
                val msg = ex.message.orEmpty()
                if (msg.contains("AUTHENTICATION_FAILED", ignoreCase = true)) {
                    Log.w(TAG, "FCM permanent authentication failure: $msg")
                } else {
                    Log.w(TAG, "FCM task failure: $msg")
                }
                onComplete?.invoke(null)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to request Firebase token: ${t.message}")
            onComplete?.invoke(null)
        }
    }

    fun subscribeToTopic(context: Context, topic: String, onComplete: ((Boolean) -> Unit)? = null) {
        val prefs = context.getSharedPreferences(PREFS_TOPICS, Context.MODE_PRIVATE)
        val subscribed = prefs.getStringSet("topics", emptySet()) ?: emptySet()
        if (subscribed.contains(topic)) {
            Log.d(TAG, "Already subscribed to topic: $topic (idempotent)")
            onComplete?.invoke(true)
            return
        }

        try {
            FirebaseMessaging.getInstance().subscribeToTopic(topic).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val updated = subscribed.toMutableSet().apply { add(topic) }
                    prefs.edit().putStringSet("topics", updated).apply()
                    Log.i(TAG, "Subscribed to FCM topic: $topic")
                    onComplete?.invoke(true)
                } else {
                    Log.w(TAG, "Failed to subscribe to topic $topic: ${task.exception?.message}")
                    onComplete?.invoke(false)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Exception during topic subscription: ${t.message}")
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
                    Log.i(TAG, "Unsubscribed from FCM topic: $topic")
                    onComplete?.invoke(true)
                } else {
                    Log.w(TAG, "Failed to unsubscribe from topic $topic: ${task.exception?.message}")
                    onComplete?.invoke(false)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Exception during topic unsubscription: ${t.message}")
            onComplete?.invoke(false)
        }
    }
}

