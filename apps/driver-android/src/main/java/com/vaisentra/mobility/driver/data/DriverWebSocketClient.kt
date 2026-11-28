package com.vaisentra.mobility.driver.data

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Real-time WebSocket Client for DriverGo Pilot Cockpit
 * Connects to:
 * 1. /api/v1/ws/driver/dispatch (Listens for incoming H3 corridor ride offers)
 * 2. /api/v1/ws/rides/{ride_id}/tracking (Listens for active ride updates & cancellations)
 */
object DriverWebSocketClient {

    private const val TAG = "DriverWebSocket"
    private var dispatchWebSocket: WebSocket? = null
    private var rideTrackingWebSocket: WebSocket? = null
    private var activeTrackingRideId: String? = null

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .build()
    }

    private fun buildWsBaseUrl(): String {
        val baseHttpUrl = DriverNetworkClient.baseUrl.trimEnd('/')
        return if (baseHttpUrl.startsWith("https://")) {
            baseHttpUrl.replaceFirst("https://", "wss://")
        } else {
            baseHttpUrl.replaceFirst("http://", "ws://")
        }
    }

    fun connectDispatchChannel() {
        val token = DriverNetworkClient.authToken
        if (token.isNullOrBlank()) {
            Log.w(TAG, "Cannot connect Dispatch WebSocket: authToken is null/blank.")
            return
        }
        if (dispatchWebSocket != null) return

        val targetUrl = "${buildWsBaseUrl()}/api/v1/ws/driver/dispatch"
        val request = Request.Builder()
            .url(targetUrl)
            .addHeader("Authorization", "Bearer $token")
            .build()

        Log.i(TAG, "Connecting Driver Dispatch WebSocket: $targetUrl")
        dispatchWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Driver Dispatch WebSocket connected.")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (this@DriverWebSocketClient.dispatchWebSocket !== webSocket) return
                if (text == "pong") return
                try {
                    val json = JSONObject(text)
                    val eventType = json.optString("event", json.optString("type", ""))

                    when {
                        eventType == "SESSION_REVOKED" -> {
                            Log.w(TAG, "Dispatch WebSocket session revoked by backend.")
                            disconnectAll()
                        }
                        eventType == "DISPATCH_OFFER" || json.has("ride_id") -> {
                            val rideId = json.optString("ride_id", "")
                            if (rideId.isBlank()) return

                            val fareCents = when {
                                json.has("estimated_fare_cents") && !json.isNull("estimated_fare_cents") ->
                                    json.optLong("estimated_fare_cents", 0L)
                                json.has("estimated_fare_rupees") ->
                                    (json.optDouble("estimated_fare_rupees", 0.0) * 100).toLong()
                                else -> 0L
                            }

                            val pickupDistKm = if (json.has("pickup_distance_km") && !json.isNull("pickup_distance_km")) {
                                json.optDouble("pickup_distance_km", 0.0)
                            } else null

                            val pickupEta = if (json.has("pickup_duration_minutes") && !json.isNull("pickup_duration_minutes")) {
                                json.optInt("pickup_duration_minutes", 3)
                            } else null

                            val tripEta = if (json.has("trip_duration_minutes") && !json.isNull("trip_duration_minutes")) {
                                json.optInt("trip_duration_minutes", 12)
                            } else null

                            val offer = DispatchOfferDto(
                                rideId = rideId,
                                assignmentId = json.optString("assignment_id", ""),
                                passengerName = json.optString("passenger_name", "Commuter Passenger"),
                                passengerPhone = json.optString("passenger_phone", ""),
                                passengerRating = json.optDouble("passenger_rating", 0.0),
                                pickupAddress = json.optString("pickup_address", "Pickup Location"),
                                destinationAddress = json.optString(
                                    "destination_address",
                                    json.optString("destination_area", "Destination")
                                ),
                                distanceKm = json.optDouble("estimated_trip_distance_km", json.optDouble("distance_km", 0.0)),
                                pickupDistanceKm = pickupDistKm,
                                pickupDurationMinutes = pickupEta,
                                tripDurationMinutes = tripEta,
                                estimatedFareCents = fareCents,
                                paymentMode = json.optString("payment_mode", "UPI"),
                                expiresAt = if (json.isNull("expires_at")) null else json.optString("expires_at"),
                                timeoutSeconds = json.optInt("timeout_seconds", 25)
                            )

                            // Send delivery acknowledgement expected by backend ws.py
                            val ackPayload = JSONObject().apply {
                                put("type", "OFFER_ACK")
                                put("ride_id", rideId)
                            }
                            webSocket.send(ackPayload.toString())

                            DriverRepository.onNewDispatchOfferReceived(offer)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing dispatch offer message: ${e.message}", e)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Driver Dispatch WebSocket closed: $code $reason")
                if (this@DriverWebSocketClient.dispatchWebSocket === webSocket) {
                    this@DriverWebSocketClient.dispatchWebSocket = null
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Driver Dispatch WebSocket failure (${response?.code}): ${t.message}")
                if (this@DriverWebSocketClient.dispatchWebSocket === webSocket) {
                    this@DriverWebSocketClient.dispatchWebSocket = null
                }
            }
        })
    }

    fun connectRideTracking(rideId: String) {
        val token = DriverNetworkClient.authToken
        if (token.isNullOrBlank()) {
            Log.w(TAG, "Cannot connect Ride Tracking WebSocket: authToken is null/blank.")
            return
        }
        if (rideTrackingWebSocket != null && activeTrackingRideId == rideId) return
        disconnectRideTracking()
        activeTrackingRideId = rideId

        val targetUrl = "${buildWsBaseUrl()}/api/v1/ws/rides/$rideId/tracking"
        val request = Request.Builder()
            .url(targetUrl)
            .addHeader("Authorization", "Bearer $token")
            .build()

        Log.i(TAG, "Connecting Pilot Ride Tracking WebSocket: $targetUrl")
        rideTrackingWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Pilot Ride Tracking WebSocket connected for $rideId")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!isCurrentRideTrackingCallback(
                        this@DriverWebSocketClient.rideTrackingWebSocket,
                        webSocket,
                        activeTrackingRideId,
                        rideId
                    )
                ) return
                if (text == "pong") return
                try {
                    val json = JSONObject(text)
                    val eventPayload = json.optJSONObject("payload") ?: json
                    val event = json.optString("event_type")
                        .ifBlank { json.optString("event") }
                        .ifBlank { json.optString("type") }
                        .uppercase()
                    if (event == "SESSION_REVOKED") {
                        Log.w(TAG, "Ride tracking session revoked by backend.")
                        disconnectAll()
                        return
                    }

                    val rideId = eventPayload.optString("ride_id")
                        .ifBlank { json.optString("ride_id") }
                    val status = eventPayload.optString("status")
                        .ifBlank { json.optString("status") }
                        .ifBlank {
                            when (event) {
                                "RIDE_ACCEPTED" -> "ASSIGNED"
                                "DRIVER_ARRIVED" -> "ARRIVED"
                                "TRIP_STARTED", "TRIP_RESUMED" -> "IN_PROGRESS"
                                "TRIP_PAUSED" -> "PAUSED"
                                "TRIP_COMPLETED", "RIDE_COMPLETED" -> "COMPLETED"
                                "RIDE_CANCELLED" -> "CANCELLED"
                                "RIDE_EXPIRED" -> "EXPIRED"
                                else -> ""
                            }
                        }
                        .uppercase()

                    if (rideId.isNotBlank() && status.isNotBlank()) {
                        DriverRepository.updateActiveRideStatus(rideId, status)
                        if (status == "CANCELLED" || event == "RIDE_CANCELLED") {
                            DriverRepository.handleRideCancelledByPassenger()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in ride tracking message: ${e.message}", e)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (rideTrackingWebSocket === webSocket && activeTrackingRideId == rideId) {
                    rideTrackingWebSocket = null
                    activeTrackingRideId = null
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Ride tracking WebSocket failure: ${t.message}")
                if (rideTrackingWebSocket === webSocket && activeTrackingRideId == rideId) {
                    rideTrackingWebSocket = null
                    activeTrackingRideId = null
                }
            }
        })
    }

    fun disconnectRideTracking() {
        try {
            rideTrackingWebSocket?.close(1000, "Trip concluded")
        } catch (_: Exception) {}
        rideTrackingWebSocket = null
        activeTrackingRideId = null
    }

    fun disconnectAll() {
        disconnectRideTracking()
        try {
            dispatchWebSocket?.close(1000, "Driver went offline")
        } catch (_: Exception) {}
        dispatchWebSocket = null
    }
}
