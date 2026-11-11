package com.vaisentra.mobility.driver.core.dispatch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.vaisentra.mobility.driver.MainActivity

object DriverDispatchNotificationManager {
    private const val TAG = "DispatchNotificationMgr"
    const val CHANNEL_ID = "driver_dispatch_offers_channel"
    const val CHANNEL_NAME = "Dispatch Ride Offers"

    const val ACTION_ACCEPT_DISPATCH = "com.vaisentra.mobility.driver.action.ACCEPT_DISPATCH"
    const val ACTION_DECLINE_DISPATCH = "com.vaisentra.mobility.driver.action.DECLINE_DISPATCH"
    const val EXTRA_RIDE_ID = "ride_id"

    private const val BASE_NOTIFICATION_ID = 2000

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority heads-up alerts for incoming ride dispatch offers"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    null
                )
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            try {
                val nm = context.getSystemService(NotificationManager::class.java)
                nm?.createNotificationChannel(channel)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create dispatch notification channel: ${e.message}")
            }
        }
    }

    fun showOfferNotification(context: Context, offer: DispatchOffer) {
        createNotificationChannel(context)

        val notificationId = getNotificationId(offer.rideId)

        // 1. Content Intent: Tapping notification opens MainActivity focusing on offer Screen 10
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("ride_id", offer.rideId)
            putExtra("pickup_address", offer.pickupAddress)
            putExtra("destination_address", offer.destinationAddress)
            putExtra("fare_rupees", offer.formattedFare)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. Accept Action Intent: Executed via DriverDispatchActionReceiver
        val acceptIntent = Intent(context, DriverDispatchActionReceiver::class.java).apply {
            action = ACTION_ACCEPT_DISPATCH
            putExtra(EXTRA_RIDE_ID, offer.rideId)
        }
        val acceptPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1,
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Decline Action Intent: Executed via DriverDispatchActionReceiver
        val declineIntent = Intent(context, DriverDispatchActionReceiver::class.java).apply {
            action = ACTION_DECLINE_DISPATCH
            putExtra(EXTRA_RIDE_ID, offer.rideId)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 2,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle("🚖 New Ride Offer: ${offer.formattedFare}")
            .setContentText("${offer.pickupAddress} → ${offer.destinationAddress}")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "Pickup: ${offer.pickupAddress}\n" +
                        "Destination: ${offer.destinationAddress}\n" +
                        "Estimated Earnings: ${offer.formattedFare} (Timeout: ${offer.timeoutSeconds}s)"
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.checkbox_on_background, "ACCEPT", acceptPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "DECLINE", declinePendingIntent)

        if (offer.timeoutSeconds > 0) {
            builder.setTimeoutAfter(offer.timeoutSeconds.toLong() * 1000L)
        }

        try {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm?.notify(notificationId, builder.build())
            Log.i(TAG, "High-priority heads-up notification displayed for offer ${offer.rideId}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to post dispatch notification: ${e.message}")
        }
    }

    fun cancelOfferNotification(context: Context, rideId: String) {
        try {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm?.cancel(getNotificationId(rideId))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cancel dispatch notification for $rideId: ${e.message}")
        }
    }

    fun showConflictAlert(context: Context, message: String) {
        createNotificationChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Ride Offer Status")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(8000L)

        try {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm?.notify(BASE_NOTIFICATION_ID + 999, builder.build())
        } catch (_: Exception) {}
    }

    private fun getNotificationId(rideId: String): Int {
        return BASE_NOTIFICATION_ID + (rideId.hashCode() and 0x7FFF)
    }
}

