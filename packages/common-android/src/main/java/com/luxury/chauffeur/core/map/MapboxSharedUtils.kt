package com.luxury.chauffeur.core.map

import java.util.Locale
import kotlin.math.*

/**
 * Shared Mapbox Configuration, Coordinate Utilities & Telemetry Helpers
 * Follows DRY Principle for Customer Android and Driver Android applications.
 */
object MapboxSharedConfig {
    // Primary Mapbox Access Tokens
    const val DEFAULT_PUBLIC_TOKEN = ""
    
    // Mapbox Vector Style URLs (v12 / v11)
    const val STYLE_STREETS = "mapbox://styles/mapbox/streets-v12"
    const val STYLE_DARK = "mapbox://styles/mapbox/dark-v11"
    const val STYLE_SATELLITE = "mapbox://styles/mapbox/satellite-streets-v12"
    const val STYLE_OUTDOORS = "mapbox://styles/mapbox/outdoors-v12"
    const val STYLE_NAVIGATION_DAY = "mapbox://styles/mapbox/navigation-day-v1"
    const val STYLE_NAVIGATION_NIGHT = "mapbox://styles/mapbox/navigation-night-v1"

    // Raster fallback tile URLs for webview/offline leaflet compatibility
    fun getMapboxRasterTileUrl(styleId: String = "streets-v12", token: String = DEFAULT_PUBLIC_TOKEN): String {
        return "https://api.mapbox.com/styles/v1/mapbox/$styleId/tiles/{z}/{x}/{y}?access_token=$token"
    }
}

/**
 * 4 Base Map Types supported across Customer & Driver apps.
 */
enum class AppMapType(val code: String, val displayName: String, val icon: String, val description: String) {
    LIGHT("LIGHT", "Light Mode", "☀️", "Clean daylight streets & landmarks"),
    DARK("DARK", "Dark Mode", "🌙", "High-contrast nighttime navigation"),
    SATELLITE("SATELLITE", "Satellite", "🛰️", "High-res aerial photographic imagery"),
    TERRAIN("TERRAIN", "Terrain", "🏔️", "Topographic contours & physical elevation")
}

/**
 * 7 Map Detail Overlays supported across Customer & Driver apps.
 */
data class AppMapDetailItem(
    val key: String,
    val title: String,
    val description: String,
    val icon: String,
    val defaultEnabled: Boolean = false
)

object AppMapDetailsCatalog {
    val ALL_DETAILS = listOf(
        AppMapDetailItem("traffic", "Traffic", "Live real-time congestion flow", "🚦", defaultEnabled = true),
        AppMapDetailItem("transit", "Public Transport", "Metro, rail corridors & transit stops", "🚇", defaultEnabled = false),
        AppMapDetailItem("cycling", "Cycling & EV", "Dedicated e-rickshaw & cycle paths", "🚲", defaultEnabled = false),
        AppMapDetailItem("buildings", "Raised Buildings", "3D isometric building footprints", "🏢", defaultEnabled = true),
        AppMapDetailItem("streetview", "Street View", "360° interactive panoramic viewer", "📷", defaultEnabled = false),
        AppMapDetailItem("wildfires", "Wildfires & Hazards", "Disaster warnings & danger zones", "🔥", defaultEnabled = false),
        AppMapDetailItem("airquality", "Air Quality (AQI)", "Real-time pollution index & heatmap", "🍃", defaultEnabled = false)
    )

    fun getDefaultMap(): Map<String, Boolean> {
        return ALL_DETAILS.associate { it.key to it.defaultEnabled }
    }
}

data class MapboxCoordinate(
    val latitude: Double,
    val longitude: Double
) {
    fun toLngLatString(): String = String.format(Locale.US, "%.6f,%.6f", longitude, latitude)
    fun toLatLngString(): String = String.format(Locale.US, "%.6f,%.6f", latitude, longitude)
}

object MapboxGeoUtils {
    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Accurate Haversine distance in meters between two geographical points.
     */
    fun computeDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(max(0.0, 1.0 - a)))
        return EARTH_RADIUS_METERS * c
    }

    fun computeDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        return computeDistanceMeters(lat1, lon1, lat2, lon2) / 1000.0
    }

    /**
     * Compass bearing (0° to 360°) from point 1 to point 2 for vehicle rotation.
     */
    fun computeBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        val theta = atan2(y, x)
        val bearing = (Math.toDegrees(theta) + 360.0) % 360.0
        return bearing.toFloat()
    }

    /**
     * Estimates urban travel ETA in minutes given distance and speed limit.
     */
    fun estimateEtaMinutes(distanceKm: Double, averageSpeedKmh: Double = 24.0): Int {
        val rawMinutes = (distanceKm / max(5.0, averageSpeedKmh)) * 60.0
        return max(1, rawMinutes.roundToInt())
    }

    /**
     * Interpolates between previous driver coordinate and new telemetry GPS coordinate
     * for buttery-smooth car animation on Mapbox.
     */
    fun interpolateCoordinate(
        start: MapboxCoordinate,
        end: MapboxCoordinate,
        fraction: Float
    ): MapboxCoordinate {
        val lat = start.latitude + (end.latitude - start.latitude) * fraction
        val lng = start.longitude + (end.longitude - start.longitude) * fraction
        return MapboxCoordinate(lat, lng)
    }
}

object MapboxUrlBuilder {
    fun buildDirectionsUrl(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        token: String = MapboxSharedConfig.DEFAULT_PUBLIC_TOKEN,
        profile: String = "driving-traffic" // driving-traffic, driving, walking, cycling
    ): String {
        return "https://api.mapbox.com/directions/v5/mapbox/$profile/" +
                "${String.format(Locale.US, "%.6f,%.6f", originLng, originLat)};" +
                "${String.format(Locale.US, "%.6f,%.6f", destLng, destLat)}" +
                "?access_token=$token&geometries=geojson&overview=full&steps=true&banner_instructions=true&voice_instructions=true"
    }

    fun buildGeocodingUrl(
        query: String,
        token: String = MapboxSharedConfig.DEFAULT_PUBLIC_TOKEN,
        proximityLat: Double? = null,
        proximityLng: Double? = null,
        limit: Int = 8
    ): String {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        var url = "https://api.mapbox.com/geocoding/v5/mapbox.places/$encodedQuery.json?access_token=$token&autocomplete=true&limit=$limit"
        if (proximityLat != null && proximityLng != null) {
            url += "&proximity=${String.format(Locale.US, "%.6f,%.6f", proximityLng, proximityLat)}"
        }
        return url
    }

    fun buildReverseGeocodingUrl(
        lat: Double,
        lng: Double,
        token: String = MapboxSharedConfig.DEFAULT_PUBLIC_TOKEN
    ): String {
        return "https://api.mapbox.com/geocoding/v5/mapbox.places/${String.format(Locale.US, "%.6f,%.6f", lng, lat)}.json?access_token=$token&limit=1"
    }
}

/**
 * Handles Mapbox HTTP rate-limit (429) & connection fallbacks gracefully.
 */
object MapboxErrorHandler {
    fun isRateLimitOrServerError(httpCode: Int): Boolean {
        return httpCode == 429 || httpCode in 500..599
    }

    fun getActionableMessage(httpCode: Int): String {
        return when (httpCode) {
            401 -> "Mapbox access token is invalid or expired. Check MAPBOX_SECRET_TOKEN."
            403 -> "Mapbox API scope or URL restriction error."
            429 -> "Mapbox rate limit reached. Switching to resilient local routing engine."
            in 500..599 -> "Mapbox servers temporarily unreachable. Fallback cache active."
            else -> "Map network communication status: HTTP $httpCode"
        }
    }
}

