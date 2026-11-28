package com.vaisentra.mobility.rider

import android.app.Application
import android.util.Log
import com.vaisentra.mobility.rider.data.NetworkClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class RiderApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Log.i("RiderGo", "RiderGo Application initialized with Material 3 & FastAPI network client.")

        // Load persisted auth and Mapbox configuration before any startup network work.
        NetworkClient.init(this)

        // NetworkClient.baseUrl comes from the active product flavor's BuildConfig.
        // Preserve it so dev, staging, and production use their configured endpoints.
        com.vaisentra.mobility.rider.fcm.RiderFcmManager.init(this)
        com.vaisentra.mobility.rider.fcm.RiderFcmManager.syncTokenIfAuthenticated(this)

        // Warm up the same endpoint selected for Retrofit requests.
        warmupAndKeepAliveBackend(NetworkClient.baseUrl)
    }

    private fun warmupAndKeepAliveBackend(targetUrl: String) {
        appScope.launch {
            val client = OkHttpClient.Builder()
                .connectTimeout(45, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()

            val pingUrl = "${targetUrl.trimEnd('/')}/health/live"
            while (true) {
                try {
                    val request = Request.Builder()
                        .url(pingUrl)
                        .header("Accept", "application/json")
                        .header("User-Agent", "RiderGo-Warmup-Ping")
                        .build()
                    val response = client.newCall(request).execute()
                    response.close()
                    Log.d("RiderApp", "Backend keep-alive ping success: ${response.code}")
                } catch (e: Exception) {
                    Log.w("RiderApp", "Backend warmup ping pending or retry: ${e.message}")
                }
                // Ping every 9 minutes while the app process is alive.
                delay(9 * 60 * 1000L)
            }
        }
    }
}
