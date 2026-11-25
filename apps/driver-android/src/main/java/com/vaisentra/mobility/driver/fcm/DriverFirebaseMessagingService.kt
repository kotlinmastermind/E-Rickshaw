package com.vaisentra.mobility.driver.fcm

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.vaisentra.mobility.driver.MainActivity
import com.vaisentra.mobility.driver.data.DriverNetworkClient
import com.vaisentra.mobility.driver.data.DriverRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DriverFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM registration token refreshed for driver.")
        DriverFcmManager.onNewToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        DriverNetworkClient.init(applicationContext)
        DriverFcmManager.ensureNotificationChannels(applicationContext)

        val data = message.data
        val title = message.notification?.title ?: data["title"] ?: "DriverGo Alert"
        val body = message.notification?.body ?: data["body"] ?: "You have a new fleet notification."
        val targetRoute = DriverFcmDeepLinkResolver.resolveRoute(data)

        // Refresh unread notifications & dispatch state cleanly while foregrounded
        if (DriverNetworkClient.isAuthenticated()) {
            serviceScope.launch {
                DriverRepository.fetchUnreadNotificationsCount()
                DriverRepository.fetchNotifications()
                val eventType = (data["event_type"] ?: "").uppercase()
                if (eventType.contains("OFFER") || !data["ride_id"].isNullOrBlank()) {
                    DriverRepository.fetchActiveOffers()
                    DriverRepository.fetchActiveRide()
                }
            }
        }

        showForegroundNotification(title, body, targetRoute, data)
    }

    private fun showForegroundNotification(
        title: String,
        body: String,
        route: String,
        data: Map<String, String>
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val eventType = (data["event_type"] ?: "").uppercase()
        val channelId = when {
            eventType.contains("EMERGENCY") || data["emergency"] == "true" -> DriverFcmManager.CHANNEL_SAFETY_ALERTS
            eventType.contains("OFFER") -> DriverFcmManager.CHANNEL_DISPATCH_ALERTS
            else -> DriverFcmManager.CHANNEL_RIDE_NOTIFICATIONS
        }

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(DriverFcmDeepLinkResolver.EXTRA_FCM_ROUTE, route)
            for ((k, v) in data) {
                putExtra(k, v)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            (System.currentTimeMillis() and 0x7FFFFFFF).toInt(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(this)
                .notify((System.currentTimeMillis() and 0x7FFFFFFF).toInt(), notification)
        } catch (sec: SecurityException) {
            Log.w(TAG, "Notification display skipped due to permission: ${sec.message}")
        }
    }

    companion object {
        private const val TAG = "DriverFcmService"
    }
}
