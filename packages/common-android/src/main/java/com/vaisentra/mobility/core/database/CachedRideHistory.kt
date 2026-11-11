package com.vaisentra.mobility.core.database

/**
 * Cached Ride History records for offline trips review.
 * Table: cached_ride_history
 */
data class CachedRideHistory(
    val rideId: String,
    val userId: String,
    val pickupAddress: String,
    val destinationAddress: String,
    val farePaise: Long,
    val status: String,
    val vehicleCategory: String?,
    val completedAt: Long,
    val rating: Int? = null
)
