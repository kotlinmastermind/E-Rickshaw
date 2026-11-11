package com.vaisentra.mobility.core.database

/**
 * Cached Fleet / Vehicle Categories and Tariffs for offline booking sheet initialization.
 * Table: cached_vehicle_categories
 */
data class CachedVehicleCategory(
    val id: String,
    val code: String,
    val name: String,
    val description: String?,
    val iconUrl: String?,
    val capacity: Int,
    val sortOrder: Int,
    val isElectric: Boolean = true,
    val baseFarePaise: Long = 0L,
    val perKmPaise: Long = 0L
)
