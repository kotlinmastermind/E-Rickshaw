package com.vaisentra.mobility.rider.data

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Real-time WebSocket Client for RiderGo
 * Connects to /api/v1/ws/rides/{ride_id}/tracking to receive:
 * - Driver dispatch and assignment updates
 * - Real-time vehicle GPS telemetry
 * - Trip state transitions (ASSIGNED -> ARRIVED -> IN_PROGRESS -> COMPLETED)
 */
object RiderWebSocketClient {

    private const val TAG = "RiderWebSocket"
    private var webSocket: WebSocket? = null
    private var activeRideId: String? = null

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS) // Indefinite for WebSocket
            .pingInterval(25, TimeUnit.SECONDS)
            .build()
    }

    fun connectRideTracking(rideId: String) {
        if (webSocket != null && activeRideId == rideId) {
            Log.d(TAG, "WebSocket already connected for ride: $rideId")
            return
        }
        disconnect()

        activeRideId = rideId
        val baseHttpUrl = NetworkClient.baseUrl.trimEnd('/')
        val wsBaseUrl = if (baseHttpUrl.startsWith("https://")) {
            baseHttpUrl.replaceFirst("https://", "wss://")
        } else {
            baseHttpUrl.replaceFirst("http://", "ws://")
        }
        val targetUrl = "$wsBaseUrl/api/v1/ws/rides/$rideId/tracking"

        val requestBuilder = Request.Builder().url(targetUrl)
        NetworkClient.authToken?.let { token ->
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        Log.i(TAG, "Connecting WebSocket to: $targetUrl")
        webSocket = client.newWebSocket(requestBuilder.build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connection established for ride $rideId")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "WebSocket message received: $text")
                try {
                    val json = JSONObject(text)
                    val eventType = json.optString("type", "")

                    when (eventType) {
                        "STATUS_UPDATE", "RIDE_UPDATE" -> {
                            val newStatus = json.optString("status", "")
                            if (newStatus.isNotEmpty()) {
                                RiderRepository.updateActiveRideStatus(newStatus)
                            }
                        }
                        "DRIVER_LOCATION", "LOCATION_UPDATE" -> {
                            val lat = json.optDouble("latitude", json.optDouble("lat", 0.0))
                            val lng = json.optDouble("longitude", json.optDouble("lng", 0.0))
                            val speed = json.optDouble("speed_kmh", 25.0)
                            if (lat != 0.0 && lng != 0.0) {
                                RiderRepository.updateDriverLiveLocation(lat, lng, speed)
                            }
                        }
                        "RIDE_COMPLETED" -> {
                            RiderRepository.updateActiveRideStatus("COMPLETED")
                        }
                        "RIDE_CANCELLED" -> {
                            RiderRepository.updateActiveRideStatus("CANCELLED")
                        }
                        else -> {
                            // Also check top-level "status"
                            val status = json.optString("status", "")
                            if (status.isNotEmpty()) {
                                RiderRepository.updateActiveRideStatus(status)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing WebSocket event: ${e.message}", e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing (code=$code, reason=$reason)")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed for ride $rideId")
                if (activeRideId == rideId) {
                    this@RiderWebSocketClient.webSocket = null
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket failure on ride $rideId: ${t.message}")
                this@RiderWebSocketClient.webSocket = null
            }
        })
    }

    fun disconnect() {
        try {
            webSocket?.close(1000, "App disconnected WebSocket")
        } catch (e: Exception) {
            Log.w(TAG, "Error closing WebSocket: ${e.message}")
        } finally {
            webSocket = null
            activeRideId = null
        }
    }
}

