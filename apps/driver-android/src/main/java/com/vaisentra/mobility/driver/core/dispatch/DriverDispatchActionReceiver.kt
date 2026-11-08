package com.vaisentra.mobility.driver.core.dispatch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.vaisentra.mobility.driver.MainActivity
import com.vaisentra.mobility.driver.core.network.DriverApiClient
import com.vaisentra.mobility.driver.core.security.DriverTokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles user interactions with dispatch Heads-Up Notifications ("ACCEPT" / "DECLINE").
 * Operates safely in the background using goAsync() to call authorized backend APIs.
 */
class DriverDispatchActionReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "DispatchActionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val rideId = intent.getStringExtra(DriverDispatchNotificationManager.EXTRA_RIDE_ID)

        if (rideId.isNullOrBlank()) {
            Log.w(TAG, "Received notification action $action with null/blank rideId")
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    DriverDispatchNotificationManager.ACTION_ACCEPT_DISPATCH -> {
                        Log.i(TAG, "Executing background acceptance for ride: $rideId")
                        val res = DriverApiClient.getService().acceptRide(rideId)
                        if (res.isSuccessful) {
                            Log.i(TAG, "Ride $rideId accepted successfully by backend")
                            DriverTokenManager.getInstance(context).saveActiveRideId(rideId)
                            DriverDispatchManager.onOfferAccepted(rideId)
                            DriverDispatchNotificationManager.cancelOfferNotification(context, rideId)

                            // Launch MainActivity to show active trip navigation
                            val launchIntent = Intent(context, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                putExtra("ride_id", rideId)
                                putExtra("action", "RIDE_ACCEPTED")
                            }
                            context.startActivity(launchIntent)
                        } else if (res.code() == 409) {
                            Log.w(TAG, "Ride $rideId was already accepted or expired (409 Conflict)")
                            DriverDispatchManager.onOfferDeclined(rideId)
                            DriverDispatchNotificationManager.cancelOfferNotification(context, rideId)
                            DriverDispatchNotificationManager.showConflictAlert(
                                context,
                                "Offer no longer available (accepted by another driver or expired)."
                            )
                        } else {
                            Log.w(TAG, "Backend rejected acceptance for ride $rideId: HTTP ${res.code()}")
                            DriverDispatchNotificationManager.showConflictAlert(
                                context,
                                "Unable to accept ride (HTTP ${res.code()}). Please try again."
                            )
                        }
                    }

                    DriverDispatchNotificationManager.ACTION_DECLINE_DISPATCH -> {
                        Log.i(TAG, "Executing background decline for ride: $rideId")
                        try {
                            DriverApiClient.getService().rejectDispatchOffer(
                                rideId,
                                mapOf("rejection_reason" to "DECLINED_BY_DRIVER")
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to send reject API call to server: ${e.message}")
                        }
                        DriverDispatchManager.onOfferDeclined(rideId)
                        DriverDispatchNotificationManager.cancelOfferNotification(context, rideId)
                    }

                    else -> {
                        Log.w(TAG, "Unknown action received: $action")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception handling dispatch action $action: ${e.message}", e)
                if (action == DriverDispatchNotificationManager.ACTION_ACCEPT_DISPATCH) {
                    DriverDispatchNotificationManager.showConflictAlert(
                        context,
                        "Network error accepting ride: ${e.message}"
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

