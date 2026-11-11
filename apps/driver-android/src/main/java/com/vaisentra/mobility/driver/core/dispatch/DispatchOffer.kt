package com.vaisentra.mobility.driver.core.dispatch

/**
 * Server-authoritative ride dispatch offer model.
 * Matches the canonical event contract emitted by the backend dispatch bus (Redis Pub/Sub & WebSocket).
 */
data class DispatchOffer(
    val rideId: String,
    val pickupAddress: String,
    val destinationAddress: String,
    val estimatedFareCents: Long,
    val pickupDistanceKm: Double? = null,
    val timeoutSeconds: Int = 20,
    val expiresAt: String? = null
) {
    val estimatedFareRupees: Double
        get() = estimatedFareCents / 100.0

    val formattedFare: String
        get() = "₹ " + String.format(java.util.Locale.US, "%.2f", estimatedFareRupees)
}

