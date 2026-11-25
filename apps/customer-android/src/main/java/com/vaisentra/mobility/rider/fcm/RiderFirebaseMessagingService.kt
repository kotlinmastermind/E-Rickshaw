package com.vaisentra.mobility.rider.fcm

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
import com.vaisentra.mobility.rider.MainActivity
import com.vaisentra.mobility.rider.data.NetworkClient
import com.vaisentra.mobility.rider.data.RiderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RiderFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM registration token refreshed for rider.")
        RiderFcmManager.onNewToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        NetworkClient.init(applicationContext)
        RiderFcmManager.ensureNotificationChannels(applicationContext)

        val data = message.data
        val title = message.notification?.title ?: data["title"] ?: "RiderGo Notification"
        val body = message.notification?.body ?: data["body"] ?: "You have a new trip update."
        val targetRoute = RiderFcmDeepLinkResolver.resolveRoute(data)

        if (!NetworkClient.authToken.isNullOrBlank()) {
            serviceScope.launch {
                RiderRepository.fetchNotifications()
                if (!data["ride_id"].isNullOrBlank() || targetRoute == "pilot_assigned") {
                    RiderRepository.fetchActiveRide()
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
            eventType.contains("EMERGENCY") || data["emergency"] == "true" -> RiderFcmManager.CHANNEL_SAFETY_ALERTS
            eventType.contains("BROADCAST") && data["emergency"] != "true" -> RiderFcmManager.CHANNEL_PROMO_UPDATES
            else -> RiderFcmManager.CHANNEL_RIDE_NOTIFICATIONS
        }

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(RiderFcmDeepLinkResolver.EXTRA_FCM_ROUTE, route)
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
        private const val TAG = "RiderFcmService"
    }
}
