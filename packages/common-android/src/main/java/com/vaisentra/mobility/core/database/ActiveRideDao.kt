package com.vaisentra.mobility.core.database

interface ActiveRideDao {
    suspend fun insertOrUpdate(ride: CachedActiveRide)
    suspend fun getActiveRide(customerId: String): CachedActiveRide?
    suspend fun getActiveRideById(rideId: String): CachedActiveRide?
    suspend fun clearActiveRide(rideId: String)
    suspend fun clearAll()
}
