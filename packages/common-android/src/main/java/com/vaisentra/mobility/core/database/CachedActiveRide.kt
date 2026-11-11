package com.vaisentra.mobility.core.database

/**
 * Cached Active Ride state for instant app relaunch without blank loading states.
 * Table: cached_active_ride
 */
data class CachedActiveRide(
    val rideId: String,
    val customerId: String,
    val driverId: String?,
    val status: String,
    val pickupAddress: String,
    val destinationAddress: String,
    val pickupLat: Double,
    val pickupLng: Double,
    val destLat: Double,
    val destLng: Double,
    val farePaise: Long,
    val startOtp: String?,
    val estimatedMinutes: Int?,
    val updatedAt: Long = System.currentTimeMillis()
)
