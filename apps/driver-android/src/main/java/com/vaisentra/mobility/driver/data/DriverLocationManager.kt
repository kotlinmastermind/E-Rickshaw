package com.vaisentra.mobility.driver.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

data class DriverLiveLocation(
    val latitude: Double = 28.6139,
    val longitude: Double = 77.2090,
    val heading: Double = 0.0,
    val speedKmh: Double = 0.0,
    val accuracyMeters: Float = 5.0f,
    val address: String = "Connaught Place, New Delhi",
    val isLiveGps: Boolean = false,
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
)

object DriverLocationManager {
    private const val TAG = "DriverLocationManager"

    private val _currentLocation = MutableStateFlow(DriverLiveLocation())
    val currentLocation: StateFlow<DriverLiveLocation> = _currentLocation.asStateFlow()

    private var fusedClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var isTracking = false

    fun init(context: Context) {
        if (fusedClient == null) {
            fusedClient = LocationServices.getFusedLocationProviderClient(context.applicationContext)
        }
    }

    @SuppressLint("MissingPermission")
    fun startTracking(context: Context) {
        if (isTracking) return
        init(context)

        val client = fusedClient ?: return
        try {
            // Immediately fetch last known location if available
            client.lastLocation.addOnSuccessListener { loc: Location? ->
                loc?.let { updateLocation(context, it) }
            }

            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
                .setMinUpdateIntervalMillis(2500L)
                .setMinUpdateDistanceMeters(2f)
                .build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation ?: return
                    updateLocation(context, loc)
                }
            }

            locationCallback = callback
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            isTracking = true
            Log.d(TAG, "Driver GPS live tracking active")
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission missing: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "startTracking error: ${e.message}", e)
        }
    }

    fun stopTracking() {
        try {
            locationCallback?.let {
                fusedClient?.removeLocationUpdates(it)
            }
            locationCallback = null
            isTracking = false
        } catch (e: Exception) {
            Log.w(TAG, "stopTracking error: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshCurrentLocation(context: Context, onUpdated: ((DriverLiveLocation) -> Unit)? = null) {
        init(context)
        try {
            fusedClient?.lastLocation?.addOnSuccessListener { loc: Location? ->
                if (loc != null) {
                    val liveLoc = updateLocation(context, loc)
                    onUpdated?.invoke(liveLoc)
                } else {
                    onUpdated?.invoke(_currentLocation.value)
                }
            }?.addOnFailureListener {
                onUpdated?.invoke(_currentLocation.value)
            }
        } catch (e: Exception) {
            onUpdated?.invoke(_currentLocation.value)
        }
    }

    private fun updateLocation(context: Context, loc: Location): DriverLiveLocation {
        val lat = loc.latitude
        val lng = loc.longitude
        val heading = if (loc.hasBearing()) loc.bearing.toDouble() else _currentLocation.value.heading
        val speedKmh = if (loc.hasSpeed()) (loc.speed * 3.6).toDouble() else 0.0
        val accuracy = if (loc.hasAccuracy()) loc.accuracy else 5.0f

        val prevAddress = _currentLocation.value.address
        var address = prevAddress

        // Resolve friendly address / locality
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(lat, lng, 1) { addresses ->
                    if (addresses.isNotEmpty()) {
                        val addr = addresses[0]
                        val feature = addr.featureName
                        val subLoc = addr.subLocality ?: addr.locality ?: addr.subAdminArea
                        val resolved = listOfNotNull(feature, subLoc).distinct().joinToString(", ")
                        if (resolved.isNotBlank()) {
                            _currentLocation.value = _currentLocation.value.copy(address = resolved)
                            DriverRepository.updateCurrentLocation(lat, lng, heading, (speedKmh / 3.6), resolved)
                        }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    val feature = addr.featureName
                    val subLoc = addr.subLocality ?: addr.locality ?: addr.subAdminArea
                    val resolved = listOfNotNull(feature, subLoc).distinct().joinToString(", ")
                    if (resolved.isNotBlank()) {
                        address = resolved
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback gracefully
        }

        val updated = DriverLiveLocation(
            latitude = lat,
            longitude = lng,
            heading = heading,
            speedKmh = speedKmh,
            accuracyMeters = accuracy,
            address = address,
            isLiveGps = true,
            lastUpdatedTimestamp = System.currentTimeMillis()
        )
        _currentLocation.value = updated

        // Sync with Central DriverRepository
        DriverRepository.updateCurrentLocation(lat, lng, heading, (speedKmh / 3.6), address)

        // Ping real FastAPI backend if driver is currently online
        if (DriverRepository.isOnline.value) {
            DriverRepository.pingLocation(lat, lng, heading, (speedKmh / 3.6), accuracy.toDouble())
        }

        return updated
    }
}
