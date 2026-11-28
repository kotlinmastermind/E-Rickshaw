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
                try {
                    val json = JSONObject(text)
                    val eventType = json.optString("event_type")
                        .ifBlank { json.optString("type") }
                        .ifBlank { json.optString("event") }
                    val payload = json.optJSONObject("payload") ?: json

                    when (eventType) {
                        "STATUS_UPDATE", "RIDE_UPDATE", "RIDE_STATE_SNAPSHOT",
                        "RIDE_ACCEPTED", "DRIVER_ARRIVED", "TRIP_STARTED",
                        "TRIP_PAUSED", "TRIP_RESUMED", "TRIP_COMPLETED",
                        "RIDE_CANCELLED", "RIDE_EXPIRED" -> {
                            val status = payload.optString("status").ifBlank {
                                json.optString("status")
                            }.ifBlank {
                                when (eventType) {
                                    "RIDE_ACCEPTED" -> "ASSIGNED"
                                    "DRIVER_ARRIVED" -> "ARRIVED"
                                    "TRIP_STARTED", "TRIP_RESUMED" -> "IN_PROGRESS"
                                    "TRIP_PAUSED" -> "PAUSED"
                                    "TRIP_COMPLETED" -> "COMPLETED"
                                    "RIDE_CANCELLED" -> "CANCELLED"
                                    "RIDE_EXPIRED" -> "EXPIRED"
                                    else -> ""
                                }
                            }
                            if (status.isNotBlank()) RiderRepository.updateActiveRideStatus(status)
                        }
                        "DRIVER_LOCATION_UPDATE", "DRIVER_LOCATION", "LOCATION_UPDATE" -> {
                            val lat = payload.optDouble("driver_latitude", payload.optDouble("latitude", payload.optDouble("lat", Double.NaN)))
                            val lng = payload.optDouble("driver_longitude", payload.optDouble("longitude", payload.optDouble("lng", Double.NaN)))
                            val speedMps = if (payload.has("speed_mps") && !payload.isNull("speed_mps")) payload.optDouble("speed_mps", Double.NaN) else Double.NaN
                            val speedKmh = if (payload.has("speed_kmh") && !payload.isNull("speed_kmh")) {
                                payload.optDouble("speed_kmh", Double.NaN)
                            } else if (speedMps.isFinite() && speedMps >= 0.0) {
                                speedMps * 3.6
                            } else null
                            if (lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0) {
                                RiderRepository.updateDriverLiveLocation(lat, lng, speedKmh)
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
                if (this@RiderWebSocketClient.webSocket === webSocket && activeRideId == rideId) {
                    this@RiderWebSocketClient.webSocket = null
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket failure on ride $rideId: ${t.message}")
                if (this@RiderWebSocketClient.webSocket === webSocket && activeRideId == rideId) {
                    this@RiderWebSocketClient.webSocket = null
                }
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

