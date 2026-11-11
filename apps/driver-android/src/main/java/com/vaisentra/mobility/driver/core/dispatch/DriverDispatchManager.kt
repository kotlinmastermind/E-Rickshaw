package com.vaisentra.mobility.driver.core.dispatch

import android.content.Context
import android.util.Log
import com.vaisentra.mobility.driver.core.network.DriverApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Singleton state manager and event coordinator for background ride dispatching.
 * Synchronizes WebSocket dispatch frames, notification actions, durable REST reconciliation,
 * and Jetpack Compose cockpit UI.
 */
object DriverDispatchManager {
    private const val TAG = "DriverDispatchManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _activeOfferFlow = MutableSharedFlow<DispatchOffer>(replay = 1)
    val activeOfferFlow = _activeOfferFlow.asSharedFlow()

    private val currentOfferRef = AtomicReference<DispatchOffer?>(null)
    private val handledOfferIds = ConcurrentHashMap.newKeySet<String>()

    fun getCurrentOffer(): DispatchOffer? = currentOfferRef.get()

    fun onOfferReceived(context: Context, offer: DispatchOffer) {
        if (handledOfferIds.contains(offer.rideId)) {
            Log.d(TAG, "Ignoring already handled or rejected offer: ${offer.rideId}")
            return
        }

        currentOfferRef.set(offer)
        scope.launch {
            _activeOfferFlow.emit(offer)
        }
        DriverDispatchNotificationManager.showOfferNotification(context, offer)
    }

    fun onOfferAccepted(rideId: String) {
        handledOfferIds.add(rideId)
        if (currentOfferRef.get()?.rideId == rideId) {
            currentOfferRef.set(null)
        }
    }

    fun onOfferDeclined(rideId: String) {
        handledOfferIds.add(rideId)
        if (currentOfferRef.get()?.rideId == rideId) {
            currentOfferRef.set(null)
        }
    }

    fun clearActiveOffer() {
        currentOfferRef.set(null)
    }

    /**
     * Durable Reconciliation:
     * Queries PostgreSQL/Redis active offers via GET /api/v1/drivers/dispatch/offers.
     * Guaranteed to recover offers that arrived during cellular disconnects or socket drops.
     */
    fun reconcilePendingOffers(context: Context) {
        scope.launch {
            try {
                val res = DriverApiClient.getService().getDispatchOffers()
                if (res.isSuccessful && res.body() != null) {
                    val offers = res.body()!!
                    for (item in offers) {
                        val rideId = item["ride_id"] as? String ?: continue
                        if (handledOfferIds.contains(rideId)) continue

                        val pickup = item["pickup_address"] as? String ?: "Pickup Station"
                        val dest = (item["destination_address"] as? String)
                            ?: (item["destination_area"] as? String)
                            ?: "Destination Area"
                        val fareCents = ((item["estimated_fare_cents"] as? Number)?.toLong()) ?: 0L
                        val dist = (item["pickup_distance_km"] as? Number)?.toDouble()
                        val timeout = ((item["timeout_seconds"] as? Number)?.toInt()) ?: 20
                        val expires = item["expires_at"] as? String

                        val offer = DispatchOffer(
                            rideId = rideId,
                            pickupAddress = pickup,
                            destinationAddress = dest,
                            estimatedFareCents = fareCents,
                            pickupDistanceKm = dist,
                            timeoutSeconds = timeout,
                            expiresAt = expires
                        )
                        Log.i(TAG, "Durable reconciliation recovered active offer: $rideId")
                        onOfferReceived(context, offer)
                        break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed durable offer reconciliation query: ${e.message}")
            }
        }
    }

    fun parseOfferFromJson(jsonStr: String): DispatchOffer? {
        return try {
            val obj = JSONObject(jsonStr)
            val event = obj.optString("event")
            if (event != "DISPATCH_OFFER") return null

            val rideId = obj.optString("ride_id")
            if (rideId.isNullOrBlank()) return null

            DispatchOffer(
                rideId = rideId,
                pickupAddress = obj.optString("pickup_address", "Pickup location"),
                destinationAddress = obj.optString("destination_address", "Destination"),
                estimatedFareCents = obj.optLong("estimated_fare_cents", 0L),
                pickupDistanceKm = if (obj.has("pickup_distance_km") && !obj.isNull("pickup_distance_km")) {
                    obj.optDouble("pickup_distance_km")
                } else null,
                timeoutSeconds = obj.optInt("timeout_seconds", 20),
                expiresAt = if (obj.has("expires_at")) obj.optString("expires_at") else null
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse dispatch offer JSON: ${e.message}")
            null
        }
    }
}

