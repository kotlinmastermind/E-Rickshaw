package com.luxury.chauffeur.driver

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import com.google.firebase.messaging.FirebaseMessaging
import com.luxury.chauffeur.driver.core.designsystem.DriverColors
import com.luxury.chauffeur.driver.core.network.DriverApiClient
import com.luxury.chauffeur.driver.core.network.DriverDeviceTokenPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DriverApiClient.initialize(this)

        // Ensure lock screen wakeup & turn screen on for incoming VIP trip dispatches
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        // Request POST_NOTIFICATIONS runtime permission on Android 13+ (API 33)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        // Synchronize Firebase Cloud Messaging (FCM) device registration token
        if (!DriverApiClient.getToken().isNullOrBlank()) try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful && task.result != null) {
                    val token = task.result
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            DriverApiClient.getService().registerDeviceToken(
                                DriverDeviceTokenPayload(fcmToken = token, deviceOs = "ANDROID")
                            )
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {}

        val dispatchRideId = intent?.getStringExtra("ride_id")
        val dispatchPickup = intent?.getStringExtra("pickup_address")
        val dispatchDest = intent?.getStringExtra("destination_address")
        val dispatchFare = intent?.getStringExtra("fare_rupees")

        setContent {
            Surface(color = DriverColors.MidnightBlack) {
                DriverAppNavigation(
                    initialRideId = dispatchRideId,
                    initialPickup = dispatchPickup,
                    initialDest = dispatchDest,
                    initialFare = dispatchFare
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
