package com.luxury.chauffeur.driver.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.luxury.chauffeur.driver.MainActivity
import com.luxury.chauffeur.driver.core.network.DriverApiClient
import com.luxury.chauffeur.driver.core.network.DriverDeviceTokenPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DriverFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("DriverFCM", "New FCM registration token received: $token")
        // Register token with backend
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DriverApiClient.getService().registerDeviceToken(
                    DriverDeviceTokenPayload(fcmToken = token, deviceOs = "ANDROID")
                )
                Log.d("DriverFCM", "Successfully synchronized FCM token with backend")
            } catch (e: Exception) {
                Log.e("DriverFCM", "Failed to sync FCM token with backend", e)
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("DriverFCM", "Push dispatch message received from: ${remoteMessage.from}")

        val data = remoteMessage.data
        val title = data["title"] ?: remoteMessage.notification?.title ?: "NEW VIP RIDE REQUEST 🚖"
        val body = data["body"] ?: remoteMessage.notification?.body ?: "A passenger nearby has requested a luxury chauffeur trip."
        val rideId = data["ride_id"] ?: System.currentTimeMillis().toString()
        val pickup = data["pickup_address"] ?: "Mumbai Central"
        val dest = data["destination_address"] ?: "Chhatrapati Shivaji Terminal"
        val fare = data["fare_rupees"] ?: "1,600"

        triggerIncomingRideAlarmNotification(
            title = title,
            body = body,
            rideId = rideId,
            pickup = pickup,
            dest = dest,
            fare = fare
        )
    }

    private fun triggerIncomingRideAlarmNotification(
        title: String,
        body: String,
        rideId: String,
        pickup: String,
        dest: String,
        fare: String
    ) {
        val channelId = "driver_ride_dispatch_channel"
        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create high-importance audio notification channel for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build()

            val channel = NotificationChannel(
                channelId,
                "VIP Ride Dispatch Alerts (Loud Ringing)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Loud ringtone alarm when customer books a trip, even when screen is locked"
                setSound(ringtoneUri, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000, 500, 1000)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Full Screen Intent to wake device screen and display immediately over lockscreen
        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            action = "ACTION_RIDE_DISPATCH"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("ride_id", rideId)
            putExtra("pickup_address", pickup)
            putExtra("destination_address", dest)
            putExtra("fare_rupees", fare)
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            rideId.hashCode(),
            fullScreenIntent,
            pendingIntentFlags
        )

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚖 $title (₹$fare)")
            .setContentText("Pickup: $pickup")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("📍 PICKUP: $pickup\n🏁 DESTINATION: $dest\n💰 ESTIMATED FARE: ₹$fare\n\nTap to accept before dispatch timer expires!")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(ringtoneUri)
            .setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000, 500, 1000))
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(this).notify(rideId.hashCode(), notificationBuilder.build())
        } catch (_: SecurityException) {
            // Handled if permission not granted
        }

        // Play audible ringtone buzzer
        try {
            val r = RingtoneManager.getRingtone(applicationContext, ringtoneUri)
            r.play()
        } catch (_: Exception) {}
    }
}
