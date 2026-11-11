package com.vaisentra.mobility.driver.core.location

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.vaisentra.mobility.driver.core.network.DriverLocationPayload

/**
 * Robust SQLite persistent queue for driver GPS telemetry while offline or encountering network outages.
 * Zero external dependencies; uses native Android SQLiteOpenHelper.
 */
class DriverOfflineLocationDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val TAG = "DriverOfflineLocDb"
        private const val DATABASE_NAME = "driver_offline_telemetry.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_LOCATIONS = "offline_locations"
        const val COLUMN_ID = "_id"
        const val COLUMN_LAT = "latitude"
        const val COLUMN_LNG = "longitude"
        const val COLUMN_HEADING = "heading"
        const val COLUMN_SPEED = "speed_mps"
        const val COLUMN_ACCURACY = "accuracy_m"
        const val COLUMN_IS_MOCK = "is_mock"
        const val COLUMN_SOURCE = "location_source"
        const val COLUMN_TIMESTAMP = "timestamp_ms"

        @Volatile
        private var instance: DriverOfflineLocationDbHelper? = null

        fun getInstance(context: Context): DriverOfflineLocationDbHelper {
            return instance ?: synchronized(this) {
                instance ?: DriverOfflineLocationDbHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createQuery = """
            CREATE TABLE $TABLE_LOCATIONS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_LAT REAL NOT NULL,
                $COLUMN_LNG REAL NOT NULL,
                $COLUMN_HEADING REAL,
                $COLUMN_SPEED REAL,
                $COLUMN_ACCURACY REAL,
                $COLUMN_IS_MOCK INTEGER NOT NULL DEFAULT 0,
                $COLUMN_SOURCE TEXT NOT NULL,
                $COLUMN_TIMESTAMP INTEGER NOT NULL
            )
        """.trimIndent()
        db.execSQL(createQuery)
        db.execSQL("CREATE INDEX idx_offline_loc_ts ON $TABLE_LOCATIONS($COLUMN_TIMESTAMP)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_LOCATIONS")
        onCreate(db)
    }

    @Synchronized
    fun enqueueLocation(payload: DriverLocationPayload, maxQueueLimit: Int = 500): Long {
        return try {
            val db = writableDatabase
            db.beginTransaction()
            try {
                // Check size and prune oldest if necessary
                var count = 0
                db.rawQuery("SELECT COUNT(*) FROM $TABLE_LOCATIONS", null).use { countCursor ->
                    if (countCursor.moveToFirst()) {
                        count = countCursor.getInt(0)
                    }
                }

                if (count >= maxQueueLimit) {
                    db.execSQL("""
                        DELETE FROM $TABLE_LOCATIONS 
                        WHERE $COLUMN_ID IN (
                            SELECT $COLUMN_ID FROM $TABLE_LOCATIONS ORDER BY $COLUMN_ID ASC LIMIT ?
                        )
                    """.trimIndent(), arrayOf((count - maxQueueLimit + 1).toString()))
                }

                val values = ContentValues().apply {
                    put(COLUMN_LAT, payload.latitude)
                    put(COLUMN_LNG, payload.longitude)
                    payload.heading?.let { put(COLUMN_HEADING, it) }
                    payload.speedMps?.let { put(COLUMN_SPEED, it) }
                    payload.accuracyM?.let { put(COLUMN_ACCURACY, it) }
                    put(COLUMN_IS_MOCK, if (payload.isMockLocation) 1 else 0)
                    put(COLUMN_SOURCE, payload.locationSource)
                    put(COLUMN_TIMESTAMP, System.currentTimeMillis())
                }
                val inserted = db.insert(TABLE_LOCATIONS, null, values)
                db.setTransactionSuccessful()
                inserted
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist offline location: ${e.message}")
            -1L
        }
    }

    @Synchronized
    fun peekPendingLocations(limit: Int = 30): List<Pair<Long, DriverLocationPayload>> {
        val list = mutableListOf<Pair<Long, DriverLocationPayload>>()
        try {
            val db = readableDatabase
            db.query(
                TABLE_LOCATIONS,
                null,
                null,
                null,
                null,
                null,
                "$COLUMN_ID ASC",
                limit.toString()
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
                    val lat = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LAT))
                    val lng = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LNG))
                    val heading = if (cursor.isNull(cursor.getColumnIndexOrThrow(COLUMN_HEADING))) null else cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_HEADING))
                    val speed = if (cursor.isNull(cursor.getColumnIndexOrThrow(COLUMN_SPEED))) null else cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_SPEED))
                    val accuracy = if (cursor.isNull(cursor.getColumnIndexOrThrow(COLUMN_ACCURACY))) null else cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_ACCURACY))
                    val isMock = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_MOCK)) == 1
                    val source = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SOURCE)) ?: "FUSED"

                    val payload = DriverLocationPayload(
                        latitude = lat,
                        longitude = lng,
                        heading = heading,
                        speedMps = speed,
                        accuracyM = accuracy,
                        isMockLocation = isMock,
                        locationSource = source
                    )
                    list.add(Pair(id, payload))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read offline locations: ${e.message}")
        }
        return list
    }

    @Synchronized
    fun deleteLocations(ids: List<Long>) {
        if (ids.isEmpty()) return
        try {
            val db = writableDatabase
            db.beginTransaction()
            try {
                // Chunk to 200 items to prevent SQLite parameter count overflow
                ids.chunked(200).forEach { batch ->
                    val placeholders = batch.joinToString(",") { "?" }
                    val args = batch.map { it.toString() }.toTypedArray()
                    db.delete(TABLE_LOCATIONS, "$COLUMN_ID IN ($placeholders)", args)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete offline locations: ${e.message}")
        }
    }

    @Synchronized
    fun getPendingCount(): Int {
        return try {
            val db = readableDatabase
            var count = 0
            db.rawQuery("SELECT COUNT(*) FROM $TABLE_LOCATIONS", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    count = cursor.getInt(0)
                }
            }
            count
        } catch (e: Exception) {
            0
        }
    }
}
