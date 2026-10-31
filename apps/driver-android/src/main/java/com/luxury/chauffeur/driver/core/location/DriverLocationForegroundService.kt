package com.luxury.chauffeur.driver.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.luxury.chauffeur.driver.MainActivity
import com.luxury.chauffeur.driver.core.network.DriverApiClient
import com.luxury.chauffeur.driver.core.network.DriverLocationPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground, battery-conscious location reporting while the driver has chosen to be online.
 * Fully hardened against ANR, illegal stopForeground flags, and concurrent service starts/stops.
 */
class DriverLocationForegroundService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var fusedClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var locationManager: LocationManager? = null
    private var frameworkLocationListener: LocationListener? = null

    // Track active lifecycle states to prevent redundant or out-of-order transitions
    private val isTracking = AtomicBoolean(false)
    private val isForeground = AtomicBoolean(false)

    companion object {
        const val CHANNEL_ID = "driver_location_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START_TRACKING"
        const val ACTION_STOP = "ACTION_STOP_TRACKING"
        private const val TAG = "DriverLocService"

        fun start(context: Context) {
            val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!fine && !coarse) {
                Log.w(TAG, "Cannot start DriverLocationForegroundService: location permissions not granted.")
                return
            }

            val intent = Intent(context, DriverLocationForegroundService::class.java).apply { action = ACTION_START }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start DriverLocationForegroundService: ${e.message}")
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, DriverLocationForegroundService::class.java).apply { action = ACTION_STOP }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send stop action to DriverLocationForegroundService: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            fusedClient = LocationServices.getFusedLocationProviderClient(applicationContext)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to instantiate FusedLocationProviderClient: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_STOP -> {
                Log.i(TAG, "Received ACTION_STOP - safely stopping tracking.")
                stopTracking()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                Log.i(TAG, "Received ACTION_START - starting tracking.")
                startTracking()
                return START_NOT_STICKY
            }
            else -> {
                Log.i(TAG, "Received onStartCommand with unhandled action: $action")
                if (!isTracking.get()) {
                    stopTracking()
                }
                return START_NOT_STICKY
            }
        }
    }

    private fun startTracking() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            Log.w(TAG, "Location permission missing. Stopping foreground tracking.")
            updateNotification("Location permission needed", "Open the app and allow location to receive dispatch while online")
            stopTracking()
            return
        }

        // 1. Immediately satisfy Android's foreground service contract (must call startForeground within 5s)
        if (!isForeground.get()) {
            val notification = buildNotification("Location sharing is on", "Battery-conscious GPS updates while you are online")
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                isForeground.set(true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start foreground service with location type: ${e.message}", e)
                try {
                    startForeground(NOTIFICATION_ID, notification)
                    isForeground.set(true)
                } catch (t: Throwable) {
                    Log.e(TAG, "startForeground fallback failed: ${t.message}", t)
                    stopTracking()
                    return
                }
            }
        }

        // 2. Prevent duplicate subscriptions if tracking is already active
        if (isTracking.getAndSet(true)) {
            Log.d(TAG, "Location tracking is already running; skipping duplicate listener registration.")
            return
        }

        // Clean up any stale updates before requesting new ones
        stopLocationUpdates()

        // 3. Start location acquisition asynchronously on IO dispatcher to avoid main-thread ANR
        serviceScope.launch(Dispatchers.IO) {
            val gmsStatus = try {
                GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(applicationContext)
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException checking Google Play Services: ${e.message}")
                ConnectionResult.SERVICE_INVALID
            } catch (e: Throwable) {
                ConnectionResult.UNKNOWN
            }

            if (gmsStatus == ConnectionResult.SUCCESS && fusedClient != null) {
                try {
                    val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 15_000L)
                        .setMinUpdateIntervalMillis(5_000L)
                        .setMinUpdateDistanceMeters(15f)
                        .build()

                    val callback = object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            if (!isTracking.get()) return
                            val location = result.lastLocation ?: return
                            sendLocationPing(location, "FUSED")
                        }
                    }
                    locationCallback = callback
                    fusedClient?.requestLocationUpdates(request, callback, mainLooper)
                        ?.addOnFailureListener { e ->
                            Log.w(TAG, "FusedLocationProviderClient request failed: ${e.message}. Falling back to LocationManager.")
                            if (isTracking.get()) {
                                startFrameworkLocationUpdates()
                            }
                        }
                    return@launch
                } catch (e: Throwable) {
                    Log.w(TAG, "Error starting FusedLocationProviderClient: ${e.message}. Falling back to LocationManager.")
                }
            } else {
                Log.w(TAG, "Google Play Services unavailable (code $gmsStatus). Falling back to LocationManager.")
            }

            if (isTracking.get()) {
                startFrameworkLocationUpdates()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startFrameworkLocationUpdates() {
        try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
            locationManager = lm

            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (!isTracking.get()) return
                    sendLocationPing(location, "GPS_FRAMEWORK")
                }
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            frameworkLocationListener = listener

            val provider = when {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> LocationManager.PASSIVE_PROVIDER
            }

            lm.requestLocationUpdates(provider, 15000L, 15f, listener, mainLooper)

            // Query last known location asynchronously on IO thread to prevent main-thread IPC hitch
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val lastKnown = lm.getLastKnownLocation(provider)
                    if (lastKnown != null && isTracking.get()) {
                        sendLocationPing(lastKnown, "GPS_FRAMEWORK_LAST")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error querying last known framework location: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "LocationManager fallback failed: ${e.message}")
        }
    }

    private fun sendLocationPing(location: Location, source: String) {
        val mock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) location.isMock else location.isFromMockProvider
        serviceScope.launch(Dispatchers.IO) {
            try {
                DriverApiClient.getService().pingLocation(
                    DriverLocationPayload(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        heading = location.takeIf { it.hasBearing() }?.bearing?.toDouble(),
                        speedMps = location.takeIf { it.hasSpeed() }?.speed?.toDouble(),
                        accuracyM = location.takeIf { it.hasAccuracy() }?.accuracy?.toDouble(),
                        isMockLocation = mock,
                        locationSource = source
                    )
                )
            } catch (e: Exception) {
                Log.d(TAG, "Driver location ping skipped/failed: ${e.message}")
            }
        }
    }

    private fun stopLocationUpdates() {
        locationCallback?.let { callback ->
            try {
                fusedClient?.removeLocationUpdates(callback)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove fused location updates: ${e.message}")
            }
        }
        locationCallback = null

        frameworkLocationListener?.let { listener ->
            try {
                locationManager?.removeUpdates(listener)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove framework location updates: ${e.message}")
            }
        }
        frameworkLocationListener = null
    }

    private fun stopTracking() {
        isTracking.set(false)
        stopLocationUpdates()

        // Safely exit foreground state only if it was actually entered
        if (isForeground.getAndSet(false)) {
            try {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping foreground: ${e.message}")
            }
        }

        // Cancel notification explicitly across all Android versions
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}

        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Driver location sharing", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Visible while location is shared for dispatch and active trip operations" }
            try {
                getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create notification channel: ${e.message}")
            }
        }
    }

    private fun updateNotification(title: String, content: String) {
        try {
            getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification(title, content))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update notification: ${e.message}")
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        stopTracking()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
