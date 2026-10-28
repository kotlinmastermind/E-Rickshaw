package com.luxury.chauffeur.driver.core.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
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

/** Foreground, battery-conscious location reporting while the driver has chosen to be online. */
class DriverLocationForegroundService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var fusedClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    companion object {
        const val CHANNEL_ID = "driver_location_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START_TRACKING"
        const val ACTION_STOP = "ACTION_STOP_TRACKING"

        fun start(context: Context) {
            val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!fine && !coarse) return

            val intent = Intent(context, DriverLocationForegroundService::class.java).apply { action = ACTION_START }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
                // Ignore if launch restricted from background
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, DriverLocationForegroundService::class.java).apply { action = ACTION_STOP }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("Location sharing is on", "Battery-conscious GPS updates while you are online")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (_: Exception) {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        if (intent?.action == ACTION_STOP) {
            stopTracking()
        } else {
            startTracking()
        }
        return START_NOT_STICKY
    }

    private fun startTracking() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            updateNotification("Location permission needed", "Open the app and allow location to receive dispatch while online")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        stopLocationUpdates()
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 15_000L)
            .setMinUpdateIntervalMillis(5_000L)
            .setMinUpdateDistanceMeters(15f)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val mock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) location.isMock else location.isFromMockProvider
                serviceScope.launch {
                    try {
                        DriverApiClient.getService().pingLocation(
                            DriverLocationPayload(
                                latitude = location.latitude,
                                longitude = location.longitude,
                                heading = location.takeIf { it.hasBearing() }?.bearing?.toDouble(),
                                speedMps = location.takeIf { it.hasSpeed() }?.speed?.toDouble(),
                                accuracyM = location.takeIf { it.hasAccuracy() }?.accuracy?.toDouble(),
                                isMockLocation = mock,
                                locationSource = "FUSED"
                            )
                        )
                    } catch (_: Exception) {
                        // Next GPS callback retries naturally.
                    }
                }
            }
        }
        locationCallback = callback
        try {
            fusedClient.requestLocationUpdates(request, callback, mainLooper)
        } catch (_: SecurityException) {
            updateNotification("Location permission needed", "Enable location access in app settings")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopLocationUpdates() {
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        locationCallback = null
    }

    private fun stopTracking() {
        stopLocationUpdates()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Driver location sharing", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Visible while location is shared for dispatch and active trip operations" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun updateNotification(title: String, content: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(title, content))
    }

    private fun buildNotification(title: String, content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle(title).setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation).setOngoing(true).setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW).build()
    }

    override fun onDestroy() {
        stopLocationUpdates()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
