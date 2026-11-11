package com.vaisentra.mobility.core.database

interface UserProfileDao {
    suspend fun insertOrUpdate(profile: CachedUserProfile)
    suspend fun getProfile(userId: String): CachedUserProfile?
    suspend fun clearProfile(userId: String)
    suspend fun clearAll()
}
