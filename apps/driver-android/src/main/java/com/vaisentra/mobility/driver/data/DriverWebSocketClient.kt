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

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .build()
    }

    fun connectDispatchChannel() {
        if (dispatchWebSocket != null) return

        val baseHttpUrl = DriverNetworkClient.baseUrl.trimEnd('/')
        val wsBaseUrl = if (baseHttpUrl.startsWith("https://")) {
            baseHttpUrl.replaceFirst("https://", "wss://")
        } else {
            baseHttpUrl.replaceFirst("http://", "ws://")
        }
        val targetUrl = "$wsBaseUrl/api/v1/ws/driver/dispatch"

        val requestBuilder = Request.Builder().url(targetUrl)
        DriverNetworkClient.authToken?.let { token ->
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        Log.i(TAG, "Connecting Driver Dispatch WebSocket: $targetUrl")
        dispatchWebSocket = client.newWebSocket(requestBuilder.build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Driver Dispatch WebSocket connected.")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Driver Dispatch Message: $text")
                try {
                    val json = JSONObject(text)
                    val eventType = json.optString("type", "")

                    if (eventType == "DISPATCH_OFFER" || json.has("ride_id")) {
                        val offer = DispatchOfferDto(
                            rideId = json.optString("ride_id", "ride-" + System.currentTimeMillis()),
                            assignmentId = json.optString("assignment_id", "asg-01"),
                            passengerName = json.optString("passenger_name", "Commuter Passenger"),
                            passengerPhone = json.optString("passenger_phone", "+91 99999 00000"),
                            passengerRating = json.optDouble("passenger_rating", 4.90),
                            pickupAddress = json.optString("pickup_address", "Current Pickup Location"),
                            destinationAddress = json.optString("destination_address", "Destination"),
                            distanceKm = json.optDouble("distance_km", 4.5),
                            pickupDistanceMeters = json.optInt("pickup_distance_meters", 350),
                            estimatedFareRupees = json.optDouble("estimated_fare_rupees", 85.0),
                            paymentMode = json.optString("payment_mode", "UPI"),
                            timeoutSeconds = json.optInt("timeout_seconds", 15)
                        )
                        DriverRepository.onNewDispatchOfferReceived(offer)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing dispatch offer message: ${e.message}", e)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Driver Dispatch WebSocket closed.")
                dispatchWebSocket = null
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Driver Dispatch WebSocket failure: ${t.message}")
                dispatchWebSocket = null
            }
        })
    }

    fun connectRideTracking(rideId: String) {
        disconnectRideTracking()

        val baseHttpUrl = DriverNetworkClient.baseUrl.trimEnd('/')
        val wsBaseUrl = if (baseHttpUrl.startsWith("https://")) {
            baseHttpUrl.replaceFirst("https://", "wss://")
        } else {
            baseHttpUrl.replaceFirst("http://", "ws://")
        }
        val targetUrl = "$wsBaseUrl/api/v1/ws/rides/$rideId/tracking"

        val requestBuilder = Request.Builder().url(targetUrl)
        DriverNetworkClient.authToken?.let { token ->
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        Log.i(TAG, "Connecting Pilot Ride Tracking WebSocket: $targetUrl")
        rideTrackingWebSocket = client.newWebSocket(requestBuilder.build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Pilot Ride Tracking WebSocket connected for $rideId")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Ride Tracking Event: $text")
                try {
                    val json = JSONObject(text)
                    val status = json.optString("status", "")
                    if (status == "CANCELLED") {
                        DriverRepository.handleRideCancelledByPassenger()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in ride tracking message: ${e.message}")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                rideTrackingWebSocket = null
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Ride tracking WebSocket failure: ${t.message}")
                rideTrackingWebSocket = null
            }
        })
    }

    fun disconnectRideTracking() {
        try {
            rideTrackingWebSocket?.close(1000, "Trip concluded")
        } catch (_: Exception) {}
        rideTrackingWebSocket = null
    }

    fun disconnectAll() {
        disconnectRideTracking()
        try {
            dispatchWebSocket?.close(1000, "Driver went offline")
        } catch (_: Exception) {}
        dispatchWebSocket = null
    }
}

