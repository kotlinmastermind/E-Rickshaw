package com.vaisentra.mobility.core.map

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Enterprise Mapbox Place Suggestion Entity for Autocomplete & Search.
 */
data class MapboxPlaceSuggestion(
    val id: String,
    val name: String,
    val fullAddress: String,
    val latitude: Double,
    val longitude: Double,
    val category: String? = null,
    val distanceKm: Double? = null
)

/**
 * Spoken and displayed turn-by-turn instruction maneuver step.
 */
data class MapboxNavigationStep(
    val instruction: String,
    val maneuver: String,
    val distanceMeters: Double,
    val durationSeconds: Double
)

/**
 * NavigationRoute with decoded real-road coordinates, distances and steps.
 */
data class NavigationRouteResult(
    val coordinates: List<MapboxCoordinate>,
    val distanceKm: Double,
    val durationMinutes: Double,
    val steps: List<MapboxNavigationStep>,
    val summary: String
)

/**
 * High-performance Mapbox Geocoding & Search Autocomplete Engine.
 */
object MapboxSearchService {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun searchPlaces(
        query: String,
        proximityLat: Double? = null,
        proximityLng: Double? = null,
        token: String = MapboxSharedConfig.DEFAULT_PUBLIC_TOKEN,
        limit: Int = 8
    ): List<MapboxPlaceSuggestion> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()

        val url = MapboxUrlBuilder.buildGeocodingUrl(
            query = q,
            token = token,
            proximityLat = proximityLat,
            proximityLng = proximityLng,
            limit = limit
        )

        try {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val bodyStr = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(bodyStr)
            val features = json.optJSONArray("features") ?: return@withContext emptyList()

            val results = mutableListOf<MapboxPlaceSuggestion>()
            for (i in 0 until features.length()) {
                val f = features.getJSONObject(i)
                val id = f.optString("id", "mb_$i")
                val placeName = f.optString("place_name", "")
                val text = f.optString("text", "")
                val center = f.optJSONArray("center") // [lng, lat]

                if (center != null && center.length() >= 2) {
                    val lng = center.getDouble(0)
                    val lat = center.getDouble(1)
                    val name = if (text.isNotBlank()) text else placeName.substringBefore(",")
                    val fullAddress = if (placeName.isNotBlank()) placeName else name

                    val distance = if (proximityLat != null && proximityLng != null) {
                        MapboxGeoUtils.computeDistanceKm(proximityLat, proximityLng, lat, lng)
                    } else null

                    results.add(
                        MapboxPlaceSuggestion(
                            id = id,
                            name = name,
                            fullAddress = fullAddress,
                            latitude = lat,
                            longitude = lng,
                            category = f.optJSONArray("place_type")?.optString(0),
                            distanceKm = distance
                        )
                    )
                }
            }
            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun reverseGeocode(
        lat: Double,
        lng: Double,
        token: String = MapboxSharedConfig.DEFAULT_PUBLIC_TOKEN
    ): String? = withContext(Dispatchers.IO) {
        val url = MapboxUrlBuilder.buildReverseGeocodingUrl(lat, lng, token)
        try {
            val request = Request.Builder().url(url).get().build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string() ?: return@withContext null
                val json = JSONObject(bodyStr)
                val features = json.optJSONArray("features")
                if (features != null && features.length() > 0) {
                    return@withContext features.getJSONObject(0).optString("place_name")
                }
            }
        } catch (_: Exception) {}
        null
    }
}

/**
 * Enterprise Mapbox Directions API v5 Routing Engine.
 */
object MapboxDirectionsService {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun fetchNavigationRoute(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        token: String = MapboxSharedConfig.DEFAULT_PUBLIC_TOKEN,
        profile: String = "driving-traffic"
    ): NavigationRouteResult? = withContext(Dispatchers.IO) {
        val url = MapboxUrlBuilder.buildDirectionsUrl(
            originLat = originLat,
            originLng = originLng,
            destLat = destLat,
            destLng = destLng,
            token = token,
            profile = profile
        )

        try {
            val request = Request.Builder().url(url).get().build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val bodyStr = response.body?.string() ?: return@withContext null
            val json = JSONObject(bodyStr)
            val routes = json.optJSONArray("routes") ?: return@withContext null
            if (routes.length() == 0) return@withContext null

            val r0 = routes.getJSONObject(0)
            val distMeters = r0.optDouble("distance", 0.0)
            val durSecs = r0.optDouble("duration", 0.0)

            val geom = r0.optJSONObject("geometry")
            val coordsArr = geom?.optJSONArray("coordinates")
            val coordsList = mutableListOf<MapboxCoordinate>()
            if (coordsArr != null) {
                for (i in 0 until coordsArr.length()) {
                    val pt = coordsArr.getJSONArray(i)
                    // GeoJSON coordinates are [longitude, latitude]
                    val lng = pt.getDouble(0)
                    val lat = pt.getDouble(1)
                    coordsList.add(MapboxCoordinate(latitude = lat, longitude = lng))
                }
            }

            // Turn-by-turn Navigation Steps
            val stepsList = mutableListOf<MapboxNavigationStep>()
            val legs = r0.optJSONArray("legs")
            var summary = ""
            if (legs != null && legs.length() > 0) {
                val leg0 = legs.getJSONObject(0)
                summary = leg0.optString("summary", "")
                val stepsArr = leg0.optJSONArray("steps")
                if (stepsArr != null) {
                    for (s in 0 until stepsArr.length()) {
                        val stepObj = stepsArr.getJSONObject(s)
                        val man = stepObj.optJSONObject("maneuver")
                        val instruction = man?.optString("instruction") ?: stepObj.optString("name", "Continue straight")
                        val maneuverType = man?.optString("type", "turn") ?: "straight"
                        val stepDist = stepObj.optDouble("distance", 0.0)
                        val stepDur = stepObj.optDouble("duration", 0.0)
                        stepsList.add(
                            MapboxNavigationStep(
                                instruction = instruction,
                                maneuver = maneuverType,
                                distanceMeters = stepDist,
                                durationSeconds = stepDur
                            )
                        )
                    }
                }
            }

            NavigationRouteResult(
                coordinates = coordsList,
                distanceKm = Math.round((distMeters / 1000.0) * 10.0) / 10.0,
                durationMinutes = Math.round((durSecs / 60.0) * 10.0) / 10.0,
                steps = stepsList,
                summary = summary
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchMultiStopRoute(
        waypoints: List<LatLng>,
        token: String = MapboxSharedConfig.DEFAULT_PUBLIC_TOKEN,
        profile: String = "driving-traffic"
    ): NavigationRouteResult? = withContext(Dispatchers.IO) {
        if (waypoints.size < 2) return@withContext null
        val url = MapboxUrlBuilder.buildMultiStopDirectionsUrl(
            waypoints = waypoints,
            token = token,
            profile = profile
        )
        try {
            val request = Request.Builder().url(url).get().build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val bodyStr = response.body?.string() ?: return@withContext null
            val json = JSONObject(bodyStr)
            val routes = json.optJSONArray("routes") ?: return@withContext null
            if (routes.length() == 0) return@withContext null

            val r0 = routes.getJSONObject(0)
            val distMeters = r0.optDouble("distance", 0.0)
            val durSecs = r0.optDouble("duration", 0.0)

            val geom = r0.optJSONObject("geometry")
            val coordsArr = geom?.optJSONArray("coordinates")
            val coordsList = mutableListOf<MapboxCoordinate>()
            if (coordsArr != null) {
                for (i in 0 until coordsArr.length()) {
                    val pt = coordsArr.getJSONArray(i)
                    val lng = pt.getDouble(0)
                    val lat = pt.getDouble(1)
                    coordsList.add(MapboxCoordinate(latitude = lat, longitude = lng))
                }
            }

            val stepsList = mutableListOf<MapboxNavigationStep>()
            val legs = r0.optJSONArray("legs")
            var summary = ""
            if (legs != null) {
                for (l in 0 until legs.length()) {
                    val leg = legs.getJSONObject(l)
                    if (summary.isBlank()) {
                        summary = leg.optString("summary", "")
                    }
                    val stepsArr = leg.optJSONArray("steps")
                    if (stepsArr != null) {
                        for (s in 0 until stepsArr.length()) {
                            val stepObj = stepsArr.getJSONObject(s)
                            val man = stepObj.optJSONObject("maneuver")
                            val instruction = man?.optString("instruction") ?: stepObj.optString("name", "Continue straight")
                            val maneuverType = man?.optString("type", "turn") ?: "straight"
                            val stepDist = stepObj.optDouble("distance", 0.0)
                            val stepDur = stepObj.optDouble("duration", 0.0)
                            stepsList.add(
                                MapboxNavigationStep(
                                    instruction = instruction,
                                    maneuver = maneuverType,
                                    distanceMeters = stepDist,
                                    durationSeconds = stepDur
                                )
                            )
                        }
                    }
                }
            }

            NavigationRouteResult(
                coordinates = coordsList,
                distanceKm = Math.round((distMeters / 1000.0) * 10.0) / 10.0,
                durationMinutes = Math.round((durSecs / 60.0) * 10.0) / 10.0,
                steps = stepsList,
                summary = summary
            )
        } catch (e: Exception) {
            null
        }
    }
}

