package com.vaisentra.mobility.core.database

interface RideHistoryDao {
    suspend fun insertAll(rides: List<CachedRideHistory>)
    suspend fun getRideHistory(userId: String, limit: Int = 50): List<CachedRideHistory>
    suspend fun clearHistory(userId: String)
    suspend fun clearAll()
}
