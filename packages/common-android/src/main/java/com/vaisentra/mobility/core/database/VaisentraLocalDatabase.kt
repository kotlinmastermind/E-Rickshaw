package com.vaisentra.mobility.core.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Enterprise SQLite-backed local database for Vasantra Mobility Android applications.
 * Provides offline-first caching for user profiles, active rides, ride history, and vehicle categories.
 * Features strict account-level data isolation and transaction safety with zero external dependency overhead.
 */
class VaisentraLocalDatabase private constructor(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    companion object {
        const val DATABASE_NAME = "vaisentra_mobility_local.db"
        const val DATABASE_VERSION = 1

        // Table names
        const val TABLE_USER_PROFILE = "cached_user_profile"
        const val TABLE_ACTIVE_RIDE = "cached_active_ride"
        const val TABLE_RIDE_HISTORY = "cached_ride_history"
        const val TABLE_VEHICLE_CATEGORIES = "cached_vehicle_categories"

        @Volatile
        private var INSTANCE: VaisentraLocalDatabase? = null

        fun getInstance(context: Context): VaisentraLocalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VaisentraLocalDatabase(context).also { INSTANCE = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_USER_PROFILE (
                id TEXT PRIMARY KEY,
                phone_number TEXT NOT NULL,
                full_name TEXT,
                email TEXT,
                role TEXT NOT NULL,
                is_active INTEGER NOT NULL DEFAULT 1,
                is_verified INTEGER NOT NULL DEFAULT 0,
                rating REAL,
                total_rides INTEGER,
                updated_at INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_ACTIVE_RIDE (
                ride_id TEXT PRIMARY KEY,
                customer_id TEXT NOT NULL,
                driver_id TEXT,
                status TEXT NOT NULL,
                pickup_address TEXT NOT NULL,
                destination_address TEXT NOT NULL,
                pickup_lat REAL NOT NULL,
                pickup_lng REAL NOT NULL,
                dest_lat REAL NOT NULL,
                dest_lng REAL NOT NULL,
                fare_paise INTEGER NOT NULL,
                start_otp TEXT,
                estimated_minutes INTEGER,
                updated_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_active_ride_cust ON $TABLE_ACTIVE_RIDE(customer_id)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_RIDE_HISTORY (
                ride_id TEXT PRIMARY KEY,
                user_id TEXT NOT NULL,
                pickup_address TEXT NOT NULL,
                destination_address TEXT NOT NULL,
                fare_paise INTEGER NOT NULL,
                status TEXT NOT NULL,
                vehicle_category TEXT,
                completed_at INTEGER NOT NULL,
                rating INTEGER
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ride_history_user ON $TABLE_RIDE_HISTORY(user_id, completed_at DESC)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_VEHICLE_CATEGORIES (
                id TEXT PRIMARY KEY,
                code TEXT NOT NULL UNIQUE,
                name TEXT NOT NULL,
                description TEXT,
                icon_url TEXT,
                capacity INTEGER NOT NULL DEFAULT 4,
                sort_order INTEGER NOT NULL DEFAULT 0,
                is_electric INTEGER NOT NULL DEFAULT 1,
                base_fare_paise INTEGER NOT NULL DEFAULT 0,
                per_km_paise INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USER_PROFILE")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ACTIVE_RIDE")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_RIDE_HISTORY")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_VEHICLE_CATEGORIES")
        onCreate(db)
    }

    /**
     * Clear all user-identifiable cached data upon account sign-out or session revocation.
     */
    suspend fun clearAccountData(userId: String) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_USER_PROFILE, "id = ?", arrayOf(userId))
            db.delete(TABLE_ACTIVE_RIDE, "customer_id = ? OR driver_id = ?", arrayOf(userId, userId))
            db.delete(TABLE_RIDE_HISTORY, "user_id = ?", arrayOf(userId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun clearAllTables() = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_USER_PROFILE, null, null)
            db.delete(TABLE_ACTIVE_RIDE, null, null)
            db.delete(TABLE_RIDE_HISTORY, null, null)
            db.delete(TABLE_VEHICLE_CATEGORIES, null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // --- DAO Implementations ---

    val userProfileDao: UserProfileDao = object : UserProfileDao {
        override suspend fun insertOrUpdate(profile: CachedUserProfile) = withContext(Dispatchers.IO) {
            val db = writableDatabase
            val values = ContentValues().apply {
                put("id", profile.id)
                put("phone_number", profile.phoneNumber)
                put("full_name", profile.fullName)
                put("email", profile.email)
                put("role", profile.role)
                put("is_active", if (profile.isActive) 1 else 0)
                put("is_verified", if (profile.isVerified) 1 else 0)
                put("rating", profile.rating)
                put("total_rides", profile.totalRides)
                put("updated_at", profile.updatedAt)
            }
            db.insertWithOnConflict(TABLE_USER_PROFILE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            Unit
        }

        override suspend fun getProfile(userId: String): CachedUserProfile? = withContext(Dispatchers.IO) {
            val db = readableDatabase
            db.rawQuery("SELECT * FROM $TABLE_USER_PROFILE WHERE id = ? LIMIT 1", arrayOf(userId)).use { cursor ->
                if (cursor.moveToFirst()) parseUserProfile(cursor) else null
            }
        }

        override suspend fun clearProfile(userId: String) = withContext(Dispatchers.IO) {
            writableDatabase.delete(TABLE_USER_PROFILE, "id = ?", arrayOf(userId))
            Unit
        }

        override suspend fun clearAll() = withContext(Dispatchers.IO) {
            writableDatabase.delete(TABLE_USER_PROFILE, null, null)
            Unit
        }

        private fun parseUserProfile(cursor: Cursor): CachedUserProfile {
            return CachedUserProfile(
                id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                phoneNumber = cursor.getString(cursor.getColumnIndexOrThrow("phone_number")),
                fullName = cursor.getString(cursor.getColumnIndexOrThrow("full_name")),
                email = cursor.getString(cursor.getColumnIndexOrThrow("email")),
                role = cursor.getString(cursor.getColumnIndexOrThrow("role")),
                isActive = cursor.getInt(cursor.getColumnIndexOrThrow("is_active")) == 1,
                isVerified = cursor.getInt(cursor.getColumnIndexOrThrow("is_verified")) == 1,
                rating = if (cursor.isNull(cursor.getColumnIndexOrThrow("rating"))) null else cursor.getDouble(cursor.getColumnIndexOrThrow("rating")),
                totalRides = if (cursor.isNull(cursor.getColumnIndexOrThrow("total_rides"))) null else cursor.getInt(cursor.getColumnIndexOrThrow("total_rides")),
                updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"))
            )
        }
    }

    val activeRideDao: ActiveRideDao = object : ActiveRideDao {
        override suspend fun insertOrUpdate(ride: CachedActiveRide) = withContext(Dispatchers.IO) {
            val db = writableDatabase
            val values = ContentValues().apply {
                put("ride_id", ride.rideId)
                put("customer_id", ride.customerId)
                put("driver_id", ride.driverId)
                put("status", ride.status)
                put("pickup_address", ride.pickupAddress)
                put("destination_address", ride.destinationAddress)
                put("pickup_lat", ride.pickupLat)
                put("pickup_lng", ride.pickupLng)
                put("dest_lat", ride.destLat)
                put("dest_lng", ride.destLng)
                put("fare_paise", ride.farePaise)
                put("start_otp", ride.startOtp)
                put("estimated_minutes", ride.estimatedMinutes)
                put("updated_at", ride.updatedAt)
            }
            db.insertWithOnConflict(TABLE_ACTIVE_RIDE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            Unit
        }

        override suspend fun getActiveRide(customerId: String): CachedActiveRide? = withContext(Dispatchers.IO) {
            val db = readableDatabase
            db.rawQuery("SELECT * FROM $TABLE_ACTIVE_RIDE WHERE customer_id = ? ORDER BY updated_at DESC LIMIT 1", arrayOf(customerId)).use { cursor ->
                if (cursor.moveToFirst()) parseActiveRide(cursor) else null
            }
        }

        override suspend fun getActiveRideById(rideId: String): CachedActiveRide? = withContext(Dispatchers.IO) {
            val db = readableDatabase
            db.rawQuery("SELECT * FROM $TABLE_ACTIVE_RIDE WHERE ride_id = ? LIMIT 1", arrayOf(rideId)).use { cursor ->
                if (cursor.moveToFirst()) parseActiveRide(cursor) else null
            }
        }

        override suspend fun clearActiveRide(rideId: String) = withContext(Dispatchers.IO) {
            writableDatabase.delete(TABLE_ACTIVE_RIDE, "ride_id = ?", arrayOf(rideId))
            Unit
        }

        override suspend fun clearAll() = withContext(Dispatchers.IO) {
            writableDatabase.delete(TABLE_ACTIVE_RIDE, null, null)
            Unit
        }

        private fun parseActiveRide(cursor: Cursor): CachedActiveRide {
            return CachedActiveRide(
                rideId = cursor.getString(cursor.getColumnIndexOrThrow("ride_id")),
                customerId = cursor.getString(cursor.getColumnIndexOrThrow("customer_id")),
                driverId = cursor.getString(cursor.getColumnIndexOrThrow("driver_id")),
                status = cursor.getString(cursor.getColumnIndexOrThrow("status")),
                pickupAddress = cursor.getString(cursor.getColumnIndexOrThrow("pickup_address")),
                destinationAddress = cursor.getString(cursor.getColumnIndexOrThrow("destination_address")),
                pickupLat = cursor.getDouble(cursor.getColumnIndexOrThrow("pickup_lat")),
                pickupLng = cursor.getDouble(cursor.getColumnIndexOrThrow("pickup_lng")),
                destLat = cursor.getDouble(cursor.getColumnIndexOrThrow("dest_lat")),
                destLng = cursor.getDouble(cursor.getColumnIndexOrThrow("dest_lng")),
                farePaise = cursor.getLong(cursor.getColumnIndexOrThrow("fare_paise")),
                startOtp = cursor.getString(cursor.getColumnIndexOrThrow("start_otp")),
                estimatedMinutes = if (cursor.isNull(cursor.getColumnIndexOrThrow("estimated_minutes"))) null else cursor.getInt(cursor.getColumnIndexOrThrow("estimated_minutes")),
                updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"))
            )
        }
    }

    val rideHistoryDao: RideHistoryDao = object : RideHistoryDao {
        override suspend fun insertAll(rides: List<CachedRideHistory>) = withContext(Dispatchers.IO) {
            val db = writableDatabase
            db.beginTransaction()
            try {
                for (ride in rides) {
                    val values = ContentValues().apply {
                        put("ride_id", ride.rideId)
                        put("user_id", ride.userId)
                        put("pickup_address", ride.pickupAddress)
                        put("destination_address", ride.destinationAddress)
                        put("fare_paise", ride.farePaise)
                        put("status", ride.status)
                        put("vehicle_category", ride.vehicleCategory)
                        put("completed_at", ride.completedAt)
                        put("rating", ride.rating)
                    }
                    db.insertWithOnConflict(TABLE_RIDE_HISTORY, null, values, SQLiteDatabase.CONFLICT_REPLACE)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

        override suspend fun getRideHistory(userId: String, limit: Int): List<CachedRideHistory> = withContext(Dispatchers.IO) {
            val list = mutableListOf<CachedRideHistory>()
            val db = readableDatabase
            db.rawQuery("SELECT * FROM $TABLE_RIDE_HISTORY WHERE user_id = ? ORDER BY completed_at DESC LIMIT ?", arrayOf(userId, limit.toString())).use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(CachedRideHistory(
                        rideId = cursor.getString(cursor.getColumnIndexOrThrow("ride_id")),
                        userId = cursor.getString(cursor.getColumnIndexOrThrow("user_id")),
                        pickupAddress = cursor.getString(cursor.getColumnIndexOrThrow("pickup_address")),
                        destinationAddress = cursor.getString(cursor.getColumnIndexOrThrow("destination_address")),
                        farePaise = cursor.getLong(cursor.getColumnIndexOrThrow("fare_paise")),
                        status = cursor.getString(cursor.getColumnIndexOrThrow("status")),
                        vehicleCategory = cursor.getString(cursor.getColumnIndexOrThrow("vehicle_category")),
                        completedAt = cursor.getLong(cursor.getColumnIndexOrThrow("completed_at")),
                        rating = if (cursor.isNull(cursor.getColumnIndexOrThrow("rating"))) null else cursor.getInt(cursor.getColumnIndexOrThrow("rating"))
                    ))
                }
            }
            list
        }

        override suspend fun clearHistory(userId: String) = withContext(Dispatchers.IO) {
            writableDatabase.delete(TABLE_RIDE_HISTORY, "user_id = ?", arrayOf(userId))
            Unit
        }

        override suspend fun clearAll() = withContext(Dispatchers.IO) {
            writableDatabase.delete(TABLE_RIDE_HISTORY, null, null)
            Unit
        }
    }

    val vehicleCategoryDao: VehicleCategoryDao = object : VehicleCategoryDao {
        override suspend fun insertAll(categories: List<CachedVehicleCategory>) = withContext(Dispatchers.IO) {
            val db = writableDatabase
            db.beginTransaction()
            try {
                for (cat in categories) {
                    val values = ContentValues().apply {
                        put("id", cat.id)
                        put("code", cat.code)
                        put("name", cat.name)
                        put("description", cat.description)
                        put("icon_url", cat.iconUrl)
                        put("capacity", cat.capacity)
                        put("sort_order", cat.sortOrder)
                        put("is_electric", if (cat.isElectric) 1 else 0)
                        put("base_fare_paise", cat.baseFarePaise)
                        put("per_km_paise", cat.perKmPaise)
                    }
                    db.insertWithOnConflict(TABLE_VEHICLE_CATEGORIES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

        override suspend fun getAllCategories(): List<CachedVehicleCategory> = withContext(Dispatchers.IO) {
            val list = mutableListOf<CachedVehicleCategory>()
            val db = readableDatabase
            db.rawQuery("SELECT * FROM $TABLE_VEHICLE_CATEGORIES ORDER BY sort_order ASC", null).use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(CachedVehicleCategory(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        code = cursor.getString(cursor.getColumnIndexOrThrow("code")),
                        name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        description = cursor.getString(cursor.getColumnIndexOrThrow("description")),
                        iconUrl = cursor.getString(cursor.getColumnIndexOrThrow("icon_url")),
                        capacity = cursor.getInt(cursor.getColumnIndexOrThrow("capacity")),
                        sortOrder = cursor.getInt(cursor.getColumnIndexOrThrow("sort_order")),
                        isElectric = cursor.getInt(cursor.getColumnIndexOrThrow("is_electric")) == 1,
                        baseFarePaise = cursor.getLong(cursor.getColumnIndexOrThrow("base_fare_paise")),
                        perKmPaise = cursor.getLong(cursor.getColumnIndexOrThrow("per_km_paise"))
                    ))
                }
            }
            list
        }

        override suspend fun clearAll() = withContext(Dispatchers.IO) {
            writableDatabase.delete(TABLE_VEHICLE_CATEGORIES, null, null)
            Unit
        }
    }
}
