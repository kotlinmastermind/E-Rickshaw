package com.luxury.chauffeur.core.notification

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.luxury.chauffeur.core.security.BaseTokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Shared Base FCM Manager.
 * Encapsulates token lifecycle, safe background retrieval, and token persistence.
 */
abstract class BaseFcmManager(private val tag: String) {
    protected val scope = CoroutineScope(Dispatchers.IO + Job())
    @Volatile protected var isSyncing = false

    abstract fun getTokenManager(context: Context): BaseTokenManager
    abstract suspend fun uploadTokenToBackend(context: Context, token: String): Boolean

    fun onNewToken(context: Context, token: String) {
        Log.i(tag, "New FCM token received: ${token.take(8)}...")
        val tokenManager = getTokenManager(context)
        tokenManager.saveFcmToken(token)
        syncToken(context)
    }

    fun syncToken(context: Context, retryCount: Int = 0) {
        val tokenManager = getTokenManager(context)
        if (!tokenManager.isLoggedIn()) {
            Log.d(tag, "User not logged in yet. Deferring token sync.")
            return
        }

        val cachedToken = tokenManager.getFcmToken()
        if (cachedToken.isNullOrBlank()) {
            retrieveToken(context)
            return
        }

        if (tokenManager.isFcmTokenSynced()) {
            Log.d(tag, "FCM token already synchronized.")
            return
        }

        if (isSyncing) return
        isSyncing = true

        scope.launch {
            try {
                val success = uploadTokenToBackend(context, cachedToken)
                if (success) {
                    tokenManager.setFcmTokenSynced(true)
                    Log.i(tag, "FCM token successfully registered with backend.")
                } else {
                    scheduleRetry(context, retryCount)
                }
            } catch (e: Exception) {
                Log.w(tag, "FCM registration failed: ${e.message}")
                scheduleRetry(context, retryCount)
            } finally {
                isSyncing = false
            }
        }
    }

    fun retrieveToken(context: Context) {
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(tag, "Fetching FCM registration token failed: ${task.exception?.message}")
                    return@addOnCompleteListener
                }
                val token = task.result
                if (!token.isNullOrBlank()) {
                    onNewToken(context, token)
                }
            }
    }

    private fun scheduleRetry(context: Context, retryCount: Int) {
        if (retryCount >= 3) {
            Log.w(tag, "Max FCM token sync retries reached. Awaiting next session.")
            return
        }
        scope.launch {
            delay((2000L * (retryCount + 1)))
            syncToken(context, retryCount + 1)
        }
    }
}

