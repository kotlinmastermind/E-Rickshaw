package com.vaisentra.mobility.core.database

/**
 * Cached User Profile entity for offline-first resilience.
 * Table: cached_user_profile
 */
data class CachedUserProfile(
    val id: String,
    val phoneNumber: String,
    val fullName: String?,
    val email: String?,
    val role: String,
    val isActive: Boolean = true,
    val isVerified: Boolean = false,
    val rating: Double? = null,
    val totalRides: Int? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
