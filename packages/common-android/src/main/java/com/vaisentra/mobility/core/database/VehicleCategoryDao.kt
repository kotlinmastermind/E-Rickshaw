package com.vaisentra.mobility.core.database

interface VehicleCategoryDao {
    suspend fun insertAll(categories: List<CachedVehicleCategory>)
    suspend fun getAllCategories(): List<CachedVehicleCategory>
    suspend fun clearAll()
}
