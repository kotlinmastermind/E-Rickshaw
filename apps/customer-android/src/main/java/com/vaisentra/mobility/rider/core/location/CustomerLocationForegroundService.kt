package com.vaisentra.mobility.rider.core.location

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
import com.vaisentra.mobility.rider.MainActivity
import com.vaisentra.mobility.rider.core.network.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Enterprise Foreground Service for Customer High-Accuracy GPS Telemetry.
 * Tracks device location during active ride dispatch and streaming to WebSocket.
 */
class CustomerLocationForegroundService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var fusedClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var locationManager: LocationManager? = null
    private var frameworkLocationListener: LocationListener? = null

    private val isTracking = AtomicBoolean(false)
    private val isForeground = AtomicBoolean(false)
    @Volatile
    private var currentRideId: String? = null

    @Volatile
    private var telemetryWebSocket: WebSocket? = null

    companion object {
        const val CHANNEL_ID = "customer_location_channel"
        const val NOTIFICATION_ID = 2002
        const val ACTION_START = "ACTION_START_CUSTOMER_TRACKING"
        const val ACTION_STOP = "ACTION_STOP_CUSTOMER_TRACKING"
        const val EXTRA_RIDE_ID = "EXTRA_RIDE_ID"
        private const val TAG = "CustomerLocService"

        fun start(context: Context, rideId: String? = null) {
            val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!fine && !coarse) {
                Log.w(TAG, "Cannot start CustomerLocationForegroundService: location permissions missing.")
                return
            }

            val intent = Intent(context, CustomerLocationForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RIDE_ID, rideId)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start CustomerLocationForegroundService: ${e.message}")
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CustomerLocationForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to stop CustomerLocationForegroundService: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            fusedClient = LocationServices.getFusedLocationProviderClient(applicationContext)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to initialize FusedLocationProviderClient: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        currentRideId = intent?.getStringExtra(EXTRA_RIDE_ID)

        when (action) {
            ACTION_STOP -> {
                Log.i(TAG, "Received ACTION_STOP - terminating customer location tracking.")
                stopTracking()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                Log.i(TAG, "Received ACTION_START - starting customer location tracking.")
                startTracking()
                return START_NOT_STICKY
            }
            else -> {
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
            Log.w(TAG, "Location permission missing. Aborting tracking.")
            stopTracking()
            return
        }

        // 1. Establish Foreground notification
        if (!isForeground.get()) {
            val notification = buildNotification("RideGo Commuter Live GPS", "Active trip tracking and live pickup synchronization.")
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                isForeground.set(true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to startForeground: ${e.message}", e)
                try {
                    startForeground(NOTIFICATION_ID, notification)
                    isForeground.set(true)
                } catch (t: Throwable) {
                    stopTracking()
                    return
                }
            }
        }

        if (isTracking.getAndSet(true)) {
            return
        }

        stopLocationUpdates()

        // 2. Start hardware location acquisition
        serviceScope.launch(Dispatchers.IO) {
            ensureWebSocketConnected()

            val gmsStatus = try {
                GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(applicationContext)
            } catch (e: Throwable) {
                ConnectionResult.UNKNOWN
            }

            if (gmsStatus == ConnectionResult.SUCCESS && fusedClient != null) {
                try {
                    val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L)
                        .setMinUpdateIntervalMillis(4_000L)
                        .setMinUpdateDistanceMeters(10f)
                        .build()

                    val callback = object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            if (!isTracking.get()) return
                            val loc = result.lastLocation ?: return
                            sendLocationTelemetry(loc, "FUSED")
                        }
                    }
                    locationCallback = callback
                    fusedClient?.requestLocationUpdates(request, callback, mainLooper)
                        ?.addOnFailureListener {
                            if (isTracking.get()) startFrameworkLocationUpdates()
                        }
                    return@launch
                } catch (e: Throwable) {
                    Log.w(TAG, "Fused location request failed: ${e.message}")
                }
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
                    sendLocationTelemetry(location, "GPS_FRAMEWORK")
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

            lm.requestLocationUpdates(provider, 10000L, 10f, listener, mainLooper)
        } catch (e: Exception) {
            Log.e(TAG, "Framework location failed: ${e.message}")
        }
    }

    private fun ensureWebSocketConnected() {
        if (telemetryWebSocket != null) return
        try {
            val token = ApiClient.getToken().orEmpty()
            if (token.isBlank()) return
            val wsUrl = ApiClient.getWebSocketUrl("api/v1/ws/location")
            val request = Request.Builder()
                .url(wsUrl)
                .addHeader("Authorization", "Bearer $token")
                .build()
            telemetryWebSocket = ApiClient.getOkHttpClient().newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Log.d(TAG, "Customer telemetry stream connected.")
                    }
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.w(TAG, "Customer telemetry WebSocket error: ${t.message}")
                        telemetryWebSocket = null
                    }
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        telemetryWebSocket = null
                    }
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not open telemetry WebSocket: ${e.message}")
        }
    }

    private fun sendLocationTelemetry(loc: Location, source: String) {
        // Offload payload formatting, WebSocket connection verification, and network frame transmission to IO scope
        serviceScope.launch(Dispatchers.IO) {
            val rideIdStr = if (!currentRideId.isNullOrBlank()) """, "ride_id":"$currentRideId"""" else ""
            val json = """{"latitude":${loc.latitude},"longitude":${loc.longitude},"heading":${loc.bearing},"speed_mps":${loc.speed},"source":"$source"$rideIdStr}"""
            try {
                ensureWebSocketConnected()
                telemetryWebSocket?.send(json)
            } catch (_: Exception) {}
        }
    }

    private fun stopLocationUpdates() {
        locationCallback?.let { callback ->
            try { fusedClient?.removeLocationUpdates(callback) } catch (_: Exception) {}
        }
        locationCallback = null

        frameworkLocationListener?.let { listener ->
            try { locationManager?.removeUpdates(listener) } catch (_: Exception) {}
        }
        frameworkLocationListener = null

        try {
            telemetryWebSocket?.close(1000, "Service stopped")
        } catch (_: Exception) {}
        telemetryWebSocket = null
    }

    private fun stopTracking() {
        isTracking.set(false)
        stopLocationUpdates()

        if (isForeground.getAndSet(false)) {
            try {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping foreground: ${e.message}")
            }
        }

        try {
            getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}

        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Commuter Location Telemetry",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Used for real-time pickup synchronization during active rides."
            }
            try {
                getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create notification channel: ${e.message}")
            }
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
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

