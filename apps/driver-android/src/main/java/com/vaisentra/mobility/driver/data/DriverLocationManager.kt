package com.vaisentra.mobility.driver.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

data class DriverLiveLocation(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val heading: Double = 0.0,
    val speedKmh: Double = 0.0,
    val accuracyMeters: Float = 0.0f,
    val address: String = "Acquiring GPS signal...",
    val isLiveGps: Boolean = false,
    val providerSource: String = "GPS",
    val lastUpdatedTimestamp: Long = 0L
) {
    fun hasValidCoordinates(): Boolean =
        NativeLocationEvaluator.isValidCoordinate(latitude, longitude)
}

enum class LocationProviderStatus {
    IDLE,
    PERMISSION_REQUIRED,
    PROVIDERS_DISABLED,
    ACQUIRING,
    ACTIVE,
    ERROR
}

object DriverLocationManager {
    private const val TAG = "DriverLocationManager"

    private val _currentLocation = MutableStateFlow(DriverLiveLocation())
    val currentLocation: StateFlow<DriverLiveLocation> = _currentLocation.asStateFlow()

    private val _providerStatus = MutableStateFlow(LocationProviderStatus.IDLE)
    val providerStatus: StateFlow<LocationProviderStatus> = _providerStatus.asStateFlow()

    fun hasValidLiveFix(): Boolean {
        val loc = _currentLocation.value
        return loc.isLiveGps && NativeLocationEvaluator.isValidCoordinate(loc.latitude, loc.longitude)
    }

    private var appContext: Context? = null
    private var locationManager: LocationManager? = null
    private var activeListener: LocationListener? = null
    private var lastAcceptedSample: LocationSample? = null
    private var refreshCancellationSignal: CancellationSignal? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var isTracking = false

    private var lastBackendPingMs: Long = 0L
    private var lastGeocodedLat: Double = 0.0
    private var lastGeocodedLng: Double = 0.0
    private var lastGeocodedTimeMs: Long = 0L
    @Volatile private var isGeocodingInProgress: Boolean = false

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (locationManager == null) {
            locationManager = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        }
    }

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun hasBackgroundLocationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return hasLocationPermission(context)
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun startTracking(context: Context) {
        init(context)
        if (!hasLocationPermission(context)) {
            _providerStatus.value = LocationProviderStatus.PERMISSION_REQUIRED
            Log.w(TAG, "Location permission missing; skipping native LocationManager updates.")
            return
        }

        val lm = locationManager
        if (lm == null) {
            _providerStatus.value = LocationProviderStatus.ERROR
            return
        }

        if (isTracking && activeListener != null) return

        val enabledProviders = resolveAvailableProviders(lm)
        if (enabledProviders.isEmpty()) {
            _providerStatus.value = LocationProviderStatus.PROVIDERS_DISABLED
            Log.w(TAG, "All Android location providers (GPS/Network/Passive) are disabled.")
            return
        }

        _providerStatus.value = LocationProviderStatus.ACQUIRING

        try {
            // Seed immediately from the freshest valid cached fix across providers.
            val cachedCandidates = enabledProviders.mapNotNull { provider ->
                try {
                    lm.getLastKnownLocation(provider)?.toSample(provider)
                } catch (_: SecurityException) {
                    null
                }
            }
            NativeLocationEvaluator.selectBestLastKnown(cachedCandidates)?.let { sample ->
                applyAcceptedLocation(context.applicationContext, sample)
            }

            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    val sample = location.toSample(location.provider ?: LocationManager.GPS_PROVIDER)
                    if (NativeLocationEvaluator.shouldAcceptLocationUpdate(lastAcceptedSample, sample)) {
                        applyAcceptedLocation(context.applicationContext, sample)
                    }
                }

                @Deprecated("Deprecated in Android API 29")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

                override fun onProviderEnabled(provider: String) {
                    _providerStatus.value = LocationProviderStatus.ACTIVE
                }

                override fun onProviderDisabled(provider: String) {
                    if (resolveAvailableProviders(lm).isEmpty()) {
                        _providerStatus.value = LocationProviderStatus.PROVIDERS_DISABLED
                    }
                }
            }

            val hasActiveRide = DriverRepository.activeRide.value != null
            val gpsIntervalMs = if (hasActiveRide) 3000L else 5000L
            val gpsMinDistanceM = if (hasActiveRide) 2f else 5f

            var registeredCount = 0
            if (lm.allProviders.contains(LocationManager.GPS_PROVIDER) &&
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            ) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    gpsIntervalMs,
                    gpsMinDistanceM,
                    listener,
                    Looper.getMainLooper()
                )
                registeredCount++
            }
            if (lm.allProviders.contains(LocationManager.NETWORK_PROVIDER) &&
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            ) {
                lm.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    8000L,
                    15f,
                    listener,
                    Looper.getMainLooper()
                )
                registeredCount++
            }
            if (registeredCount == 0 &&
                lm.allProviders.contains(LocationManager.PASSIVE_PROVIDER) &&
                lm.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)
            ) {
                lm.requestLocationUpdates(
                    LocationManager.PASSIVE_PROVIDER,
                    10000L,
                    20f,
                    listener,
                    Looper.getMainLooper()
                )
                registeredCount++
            }

            if (registeredCount > 0) {
                activeListener = listener
                isTracking = true
                Log.d(TAG, "Native LocationManager tracking started ($registeredCount provider(s))")
            } else {
                _providerStatus.value = LocationProviderStatus.PROVIDERS_DISABLED
            }
        } catch (e: SecurityException) {
            _providerStatus.value = LocationProviderStatus.PERMISSION_REQUIRED
            Log.w(TAG, "Location permission missing: ${e.message}")
        } catch (e: Exception) {
            _providerStatus.value = LocationProviderStatus.ERROR
            Log.e(TAG, "startTracking error: ${e.message}", e)
        }
    }

    fun stopTracking() {
        try {
            refreshCancellationSignal?.cancel()
            refreshCancellationSignal = null
            activeListener?.let { listener ->
                locationManager?.removeUpdates(listener)
            }
            activeListener = null
            isTracking = false
            _providerStatus.value = LocationProviderStatus.IDLE
        } catch (e: Exception) {
            Log.w(TAG, "stopTracking error: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshCurrentLocation(context: Context, onUpdated: ((DriverLiveLocation) -> Unit)? = null) {
        init(context)
        if (!hasLocationPermission(context)) {
            _providerStatus.value = LocationProviderStatus.PERMISSION_REQUIRED
            onUpdated?.invoke(_currentLocation.value)
            return
        }

        val lm = locationManager
        if (lm == null) {
            _providerStatus.value = LocationProviderStatus.ERROR
            onUpdated?.invoke(_currentLocation.value)
            return
        }

        val primaryProvider = when {
            lm.allProviders.contains(LocationManager.GPS_PROVIDER) &&
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.allProviders.contains(LocationManager.NETWORK_PROVIDER) &&
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }

        if (primaryProvider == null) {
            _providerStatus.value = LocationProviderStatus.PROVIDERS_DISABLED
            onUpdated?.invoke(_currentLocation.value)
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                refreshCancellationSignal?.cancel()
                val signal = CancellationSignal()
                refreshCancellationSignal = signal
                lm.getCurrentLocation(
                    primaryProvider,
                    signal,
                    ContextCompat.getMainExecutor(context.applicationContext)
                ) { loc: Location? ->
                    if (loc != null) {
                        val sample = loc.toSample(primaryProvider)
                        val live = applyAcceptedLocation(context.applicationContext, sample)
                        onUpdated?.invoke(live)
                    } else {
                        val fallback = readBestLastKnown(lm)
                        onUpdated?.invoke(
                            if (fallback != null) applyAcceptedLocation(context.applicationContext, fallback)
                            else _currentLocation.value
                        )
                    }
                }
            } else {
                val fallback = readBestLastKnown(lm)
                if (fallback != null) {
                    val live = applyAcceptedLocation(context.applicationContext, fallback)
                    onUpdated?.invoke(live)
                } else {
                    @Suppress("DEPRECATION")
                    lm.requestSingleUpdate(primaryProvider, { loc: Location ->
                        val live = applyAcceptedLocation(
                            context.applicationContext,
                            loc.toSample(primaryProvider)
                        )
                        onUpdated?.invoke(live)
                    }, Looper.getMainLooper())
                }
            }
        } catch (e: SecurityException) {
            _providerStatus.value = LocationProviderStatus.PERMISSION_REQUIRED
            onUpdated?.invoke(_currentLocation.value)
        } catch (e: Exception) {
            _providerStatus.value = LocationProviderStatus.ERROR
            Log.w(TAG, "refreshCurrentLocation error: ${e.message}")
            onUpdated?.invoke(_currentLocation.value)
        }
    }

    private fun resolveAvailableProviders(lm: LocationManager): List<String> {
        val candidates = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        return candidates.filter { p ->
            try {
                lm.allProviders.contains(p) && lm.isProviderEnabled(p)
            } catch (_: Exception) {
                false
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun readBestLastKnown(lm: LocationManager): LocationSample? {
        val samples = resolveAvailableProviders(lm).mapNotNull { provider ->
            try {
                lm.getLastKnownLocation(provider)?.toSample(provider)
            } catch (_: SecurityException) {
                null
            }
        }
        return NativeLocationEvaluator.selectBestLastKnown(samples)
    }

    private fun Location.toSample(fallbackProvider: String): LocationSample {
        val isMockFix = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            isMock
        } else {
            @Suppress("DEPRECATION")
            isFromMockProvider
        }
        return LocationSample(
            latitude = latitude,
            longitude = longitude,
            provider = provider ?: fallbackProvider,
            accuracyMeters = if (hasAccuracy()) accuracy else 15.0f,
            timestampMs = if (time > 0L) time else System.currentTimeMillis(),
            heading = if (hasBearing()) bearing.toDouble() else _currentLocation.value.heading,
            speedMps = if (hasSpeed()) speed.toDouble() else 0.0,
            isMock = isMockFix
        )
    }

    private fun applyAcceptedLocation(context: Context, sample: LocationSample): DriverLiveLocation {
        lastAcceptedSample = sample
        _providerStatus.value = LocationProviderStatus.ACTIVE

        val speedKmh = (sample.speedMps * 3.6).let { if (it.isFinite() && it >= 0.0) it else 0.0 }
        val sourceLabel = NativeLocationEvaluator.normalizeLocationSource(sample.provider)
        val currentAddress = _currentLocation.value.address

        val updated = DriverLiveLocation(
            latitude = sample.latitude,
            longitude = sample.longitude,
            heading = sample.heading,
            speedKmh = speedKmh,
            accuracyMeters = sample.accuracyMeters,
            address = currentAddress,
            isLiveGps = true,
            providerSource = sourceLabel,
            lastUpdatedTimestamp = sample.timestampMs
        )
        _currentLocation.value = updated

        DriverRepository.updateCurrentLocation(
            lat = sample.latitude,
            lng = sample.longitude,
            heading = sample.heading,
            speedMps = sample.speedMps,
            address = currentAddress
        )

        // Throttled Mapbox/OSM reverse-geocoding via backend API (never android.location.Geocoder)
        val now = System.currentTimeMillis()
        val distMoved = FloatArray(1)
        Location.distanceBetween(lastGeocodedLat, lastGeocodedLng, sample.latitude, sample.longitude, distMoved)
        val shouldGeocode = !isGeocodingInProgress &&
            (lastGeocodedTimeMs == 0L || distMoved[0] > 60f || (now - lastGeocodedTimeMs > 45000L))

        if (shouldGeocode && DriverNetworkClient.isAuthenticated()) {
            isGeocodingInProgress = true
            scope.launch {
                try {
                    val resolved = DriverRepository.reverseGeocodeLocation(sample.latitude, sample.longitude)
                    val cleanAddress = resolved?.takeIf { it.isNotBlank() }
                        ?: String.format(
                            Locale.US,
                            "%.4f, %.4f (~%dm)",
                            sample.latitude,
                            sample.longitude,
                            sample.accuracyMeters.roundToInt()
                        )
                    lastGeocodedLat = sample.latitude
                    lastGeocodedLng = sample.longitude
                    lastGeocodedTimeMs = System.currentTimeMillis()
                    _currentLocation.value = _currentLocation.value.copy(address = cleanAddress)
                    DriverRepository.updateCurrentLocation(
                        lat = sample.latitude,
                        lng = sample.longitude,
                        heading = sample.heading,
                        speedMps = sample.speedMps,
                        address = cleanAddress
                    )
                } finally {
                    isGeocodingInProgress = false
                }
            }
        }

        // Throttled telemetry ping to backend when driver is online
        if (DriverRepository.isOnline.value) {
            val minPingIntervalMs = if (DriverRepository.activeRide.value != null) 5000L else 8000L
            if (now - lastBackendPingMs >= minPingIntervalMs) {
                lastBackendPingMs = now
                DriverRepository.pingLocation(
                    lat = sample.latitude,
                    lng = sample.longitude,
                    heading = sample.heading,
                    speedMps = sample.speedMps,
                    accuracyM = sample.accuracyMeters.toDouble(),
                    isMockLocation = sample.isMock,
                    locationSource = sourceLabel
                )
            }
        }

        return updated
    }
}
