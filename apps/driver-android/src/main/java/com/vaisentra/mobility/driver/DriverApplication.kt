package com.vaisentra.mobility.driver

import android.app.Application
import android.util.Log
import com.vaisentra.mobility.driver.data.DriverNetworkClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * DriverGo Application entry point.
 * Initializes base URLs, repositories, telemetry broadcasters and Supabase connection configs.
 */
class DriverApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        lateinit var instance: DriverApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize persistent secure preferences
        DriverNetworkClient.init(this)
        com.vaisentra.mobility.driver.data.DriverRepository.initProfilePhoto()
        com.vaisentra.mobility.driver.data.DriverLocationManager.init(this)
        com.vaisentra.mobility.driver.fcm.DriverFcmManager.init(this)

        // Configure default backend endpoint pointing to FastAPI server
        DriverNetworkClient.baseUrl = "https://e-rickshaw-z32z.onrender.com/"
        com.vaisentra.mobility.driver.fcm.DriverFcmManager.syncTokenIfAuthenticated(this)

        // Asynchronous non-blocking warm-up ping to wake up sleeping Render container immediately on startup
        warmupAndKeepAliveBackend(DriverNetworkClient.baseUrl)
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
                        .header("User-Agent", "DriverGo-Warmup-Ping")
                        .build()
                    val response = client.newCall(request).execute()
                    response.close()
                    Log.d("DriverApp", "Backend keep-alive ping success: ${response.code}")
                } catch (e: Exception) {
                    Log.w("DriverApp", "Backend warmup ping pending or retry: ${e.message}")
                }
                // Ping every 9 minutes (Render free tier spins down after 15 minutes of inactivity)
                delay(9 * 60 * 1000L)
            }
        }
    }
}

