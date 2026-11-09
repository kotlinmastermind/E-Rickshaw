package com.vaisentra.mobility.driver.core.location

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
import com.vaisentra.mobility.driver.MainActivity
import com.vaisentra.mobility.driver.core.dispatch.DriverDispatchManager
import com.vaisentra.mobility.driver.core.dispatch.DriverDispatchNotificationManager
import com.vaisentra.mobility.driver.core.network.DriverApiClient
import com.vaisentra.mobility.driver.core.network.DriverLocationPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
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
        if (intent == null) {
            Log.i(TAG, "onStartCommand received null intent (OS restart). Validating session.")
            val token = DriverApiClient.getToken().orEmpty()
            if (token.isBlank() || !isTracking.get()) {
                stopTracking()
                return START_NOT_STICKY
            }
            startTracking()
            return START_NOT_STICKY
        }

        val action = intent.action
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

        // 3. Connect to background dispatch WebSocket bus and start heartbeat loop
        ensureDispatchWebSocketConnected()
        startHeartbeatAndSyncLoop()

        // 4. Start location acquisition asynchronously on IO dispatcher to avoid main-thread ANR
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

    private var telemetryWebSocket: okhttp3.WebSocket? = null
    private var dispatchWebSocket: okhttp3.WebSocket? = null
    private val isDispatchConnecting = AtomicBoolean(false)
    private var reconnectBackoffMs = 5000L
    private var heartbeatJob: Job? = null

    private fun ensureWebSocketConnected() {
        if (telemetryWebSocket != null) return
        try {
            val token = DriverApiClient.getToken().orEmpty()
            val wsUrl = DriverApiClient.getWebSocketUrl("api/v1/ws/location")
            val request = okhttp3.Request.Builder()
                .url(wsUrl)
                .apply {
                    if (token.isNotBlank()) {
                        addHeader("Authorization", "Bearer $token")
                    }
                }
                .build()
            telemetryWebSocket = DriverApiClient.getOkHttpClient().newWebSocket(
                request,
                object : okhttp3.WebSocketListener() {
                    override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
                        Log.d(TAG, "WebSocket Driver Telemetry Stream connected successfully")
                    }
                    override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: okhttp3.Response?) {
                        Log.w(TAG, "WebSocket Driver Telemetry Stream failure: ${t.message}")
                        telemetryWebSocket = null
                    }
                    override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
                        telemetryWebSocket = null
                    }
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to connect telemetry WebSocket: ${e.message}")
        }
    }

    private fun ensureDispatchWebSocketConnected() {
        if (dispatchWebSocket != null || isDispatchConnecting.get() || !isTracking.get()) return
        val token = DriverApiClient.getToken().orEmpty()
        if (token.isBlank()) {
            Log.w(TAG, "Cannot connect dispatch WebSocket: token is blank.")
            return
        }

        isDispatchConnecting.set(true)
        try {
            val wsUrl = DriverApiClient.getWebSocketUrl("api/v1/ws/driver/dispatch")
            val request = okhttp3.Request.Builder()
                .url(wsUrl)
                .addHeader("Authorization", "Bearer $token")
                .build()

            dispatchWebSocket = DriverApiClient.getOkHttpClient().newWebSocket(
                request,
                object : okhttp3.WebSocketListener() {
                    override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
                        isDispatchConnecting.set(false)
                        reconnectBackoffMs = 5000L
                        Log.i(TAG, "Driver Dispatch WebSocket connected successfully")
                        // Immediate durable offer reconciliation upon connect
                        DriverDispatchManager.reconcilePendingOffers(applicationContext)
                    }

                    override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                        handleDispatchWebSocketMessage(webSocket, text)
                    }

                    override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: okhttp3.Response?) {
                        isDispatchConnecting.set(false)
                        dispatchWebSocket = null
                        Log.w(TAG, "Driver Dispatch WebSocket failure: ${t.message}")
                        scheduleDispatchReconnect()
                    }

                    override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
                        isDispatchConnecting.set(false)
                        dispatchWebSocket = null
                        Log.i(TAG, "Driver Dispatch WebSocket closed (code $code, reason: $reason)")
                        if (code == 1008 || code == 4003) {
                            Log.w(TAG, "Dispatch WebSocket closed due to session policy or revocation.")
                            stopTracking()
                        } else {
                            scheduleDispatchReconnect()
                        }
                    }
                }
            )
        } catch (e: Exception) {
            isDispatchConnecting.set(false)
            Log.w(TAG, "Failed to connect dispatch WebSocket: ${e.message}")
            scheduleDispatchReconnect()
        }
    }

    private fun handleDispatchWebSocketMessage(webSocket: okhttp3.WebSocket, text: String) {
        try {
            val offer = DriverDispatchManager.parseOfferFromJson(text)
            if (offer != null) {
                Log.i(TAG, "Incoming dispatch offer received via WebSocket: ${offer.rideId}")
                // Send OFFER_ACK frame back to server
                val nowIso = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    java.time.Instant.now().toString()
                } else {
                    System.currentTimeMillis().toString()
                }
                val ack = """{"type":"OFFER_ACK","ride_id":"${offer.rideId}","received_at":"$nowIso"}"""
                webSocket.send(ack)

                DriverDispatchManager.onOfferReceived(applicationContext, offer)
                return
            }

            // Check for revocation
            val obj = JSONObject(text)
            val event = obj.optString("event")
            if (event == "SESSION_REVOKED") {
                val reason = obj.optString("reason")
                Log.w(TAG, "SESSION_REVOKED received: $reason")
                stopTracking()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error handling dispatch WS message: ${e.message}")
        }
    }

    private fun scheduleDispatchReconnect() {
        if (!isTracking.get()) return
        serviceScope.launch(Dispatchers.IO) {
            val delayMs = reconnectBackoffMs + (Math.random() * 2000).toLong()
            reconnectBackoffMs = (reconnectBackoffMs * 1.5).toLong().coerceAtMost(60_000L)
            Log.d(TAG, "Scheduling dispatch WebSocket reconnect in ${delayMs}ms")
            delay(delayMs)
            if (isTracking.get()) {
                ensureDispatchWebSocketConnected()
            }
        }
    }

    private fun startHeartbeatAndSyncLoop() {
        heartbeatJob?.cancel()
        heartbeatJob = serviceScope.launch(Dispatchers.IO) {
            while (isTracking.get()) {
                delay(30_000L)
                if (!isTracking.get()) break
                try {
                    // 1. Maintain dispatch WebSocket heartbeat
                    val ws = dispatchWebSocket
                    if (ws != null) {
                        val nowIso = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            java.time.Instant.now().toString()
                        } else {
                            System.currentTimeMillis().toString()
                        }
                        ws.send("""{"type":"PING","timestamp":"$nowIso"}""")
                    } else {
                        // Attempt reconnect if socket dropped
                        ensureDispatchWebSocketConnected()
                    }

                    // 2. Periodic reconciliation check
                    DriverDispatchManager.reconcilePendingOffers(applicationContext)

                    // 3. Attempt flushing any pending SQLite offline telemetry points
                    flushOfflineLocations()
                } catch (e: Exception) {
                    Log.d(TAG, "Error in dispatch heartbeat/sync loop: ${e.message}")
                }
            }
        }
    }

    private val offlineLocationDb by lazy { DriverOfflineLocationDbHelper.getInstance(applicationContext) }

    private fun flushOfflineLocations() {
        val count = offlineLocationDb.getPendingCount()
        if (count == 0) return
        serviceScope.launch(Dispatchers.IO) {
            try {
                val pending = offlineLocationDb.peekPendingLocations(30)
                if (pending.isEmpty()) return@launch

                val payloads = pending.map { it.second }
                val ids = pending.map { it.first }

                val resp = DriverApiClient.getService().pingLocationBatch(
                    com.vaisentra.mobility.driver.core.network.DriverLocationBatchPayload(payloads)
                )
                if (resp.isSuccessful) {
                    offlineLocationDb.deleteLocations(ids)
                    Log.d(TAG, "Successfully flushed ${ids.size} SQLite offline location pings via batch endpoint")
                    // If more remains, flush next chunk
                    if (offlineLocationDb.getPendingCount() > 0) {
                        flushOfflineLocations()
                    }
                } else {
                    Log.w(TAG, "Batch location flush received HTTP ${resp.code()}; retaining offline locations")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Batch location flush failed: ${e.message}; offline points remain safely persisted in SQLite")
            }
        }
    }

    private fun sendLocationPing(location: Location, source: String) {
        val mock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) location.isMock else location.isFromMockProvider
        
        // 1. Send over real-time WebSocket Stream
        try {
            ensureWebSocketConnected()
            val wsPayload = """{"latitude":${location.latitude},"longitude":${location.longitude},"heading":${location.bearing},"speed_mps":${location.speed},"source":"$source"}"""
            telemetryWebSocket?.send(wsPayload)
        } catch (_: Exception) {}

        // 2. Send over REST HTTP Ping Backup with Durable SQLite Persistence
        val payload = DriverLocationPayload(
            latitude = location.latitude,
            longitude = location.longitude,
            heading = location.takeIf { it.hasBearing() }?.bearing?.toDouble(),
            speedMps = location.takeIf { it.hasSpeed() }?.speed?.toDouble(),
            accuracyM = location.takeIf { it.hasAccuracy() }?.accuracy?.toDouble(),
            isMockLocation = mock,
            locationSource = source
        )

        serviceScope.launch(Dispatchers.IO) {
            try {
                val resp = DriverApiClient.getService().pingLocation(payload)
                if (resp.isSuccessful) {
                    flushOfflineLocations()
                } else {
                    offlineLocationDb.enqueueLocation(payload)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Driver location ping network glitch, safely stored in SQLite offline queue: ${e.message}")
                offlineLocationDb.enqueueLocation(payload)
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

        heartbeatJob?.cancel()
        heartbeatJob = null

        try {
            dispatchWebSocket?.close(1000, "Shift stopped")
        } catch (_: Exception) {}
        dispatchWebSocket = null

        try {
            telemetryWebSocket?.close(1000, "Shift stopped")
        } catch (_: Exception) {}
        telemetryWebSocket = null

        DriverDispatchManager.clearActiveOffer()

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
