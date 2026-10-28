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
        Log.d("DriverFCM", "FCM registration token refreshed")
        // Register token with backend
        if (DriverApiClient.getToken().isNullOrBlank()) return
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
        val eventType = data["type"] ?: data["event_type"] ?: "NEW_RIDE_DISPATCH"
        val title = data["title"] ?: remoteMessage.notification?.title ?: "Pilot Cockpit Notice"
        val body = data["body"] ?: remoteMessage.notification?.body ?: "Open cockpit to view details."
        val rideId = data["ride_id"] ?: ""
        val pickup = data["pickup_address"] ?: ""
        val dest = data["destination_address"] ?: ""
        val fare = data["fare_rupees"] ?: ""

        when (eventType.uppercase()) {
            "RIDE_CANCELLED", "TRIP_CANCELLED" -> {
                showInformationalNotification(
                    notificationId = if (rideId.isNotBlank()) rideId.hashCode() else 2002,
                    channelId = "driver_ride_updates_channel",
                    channelName = "Trip Updates & Cancellations",
                    title = "❌ Ride Cancelled by Customer",
                    body = "Ride #${rideId.take(8)} was cancelled by the customer. You are back online for new offers."
                )
            }
            "KYC_APPROVED", "DOCUMENTS_VERIFIED" -> {
                showInformationalNotification(
                    notificationId = 3001,
                    channelId = "driver_compliance_channel",
                    channelName = "KYC & Compliance Notifications",
                    title = "🎉 Documents Verified & Approved!",
                    body = "Your driver & vehicle KYC verification is complete. You may now switch online to receive rides."
                )
            }
            "KYC_REJECTED", "DOCUMENTS_REJECTED" -> {
                val reason = data["reason"] ?: "One or more documents require re-upload."
                showInformationalNotification(
                    notificationId = 3002,
                    channelId = "driver_compliance_channel",
                    channelName = "KYC & Compliance Notifications",
                    title = "⚠️ Document Verification Action Required",
                    body = "Reason: $reason. Tap to re-upload clear photos."
                )
            }
            "PAYOUT_COMPLETED", "WALLET_PAYOUT" -> {
                val amount = data["amount"] ?: fare
                showInformationalNotification(
                    notificationId = 4001,
                    channelId = "driver_earnings_channel",
                    channelName = "Earnings & Payouts",
                    title = "💰 Bank Payout Successful",
                    body = "Payout of ₹$amount has been credited to your linked bank account."
                )
            }
            else -> {
                // High-priority incoming dispatch offer with sound & vibration
                triggerIncomingRideAlarmNotification(
                    title = title,
                    body = body,
                    rideId = rideId,
                    pickup = pickup,
                    dest = dest,
                    fare = fare
                )
            }
        }
    }

    private fun showInformationalNotification(
        notificationId: Int,
        channelId: String,
        channelName: String,
        title: String,
        body: String
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, notificationId, openAppIntent, flags)

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(this).notify(notificationId, builder.build())
        } catch (_: SecurityException) {}
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

