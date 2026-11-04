package com.luxury.chauffeur.driver

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import com.luxury.chauffeur.driver.core.designsystem.DriverColors
import com.luxury.chauffeur.driver.core.network.DriverApiClient
import com.luxury.chauffeur.driver.core.notification.DriverFcmManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        val dispatchRideId = intent?.getStringExtra("ride_id")
        val dispatchPickup = intent?.getStringExtra("pickup_address")
        val dispatchDest = intent?.getStringExtra("destination_address")
        val dispatchFare = intent?.getStringExtra("fare_rupees")

        // Render Compose UI immediately to satisfy Android OS startup watchdog in <50ms
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

        // Post-launch initialization: Permissions & FCM sync run after UI frame is drawn
        window.decorView.post {
            DriverApiClient.initialize(this@MainActivity)

            // Request POST_NOTIFICATIONS runtime permission on Android 13+ (API 33)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
                }
            }

            // Synchronize Firebase Cloud Messaging token safely in background IO coroutine
            CoroutineScope(Dispatchers.IO).launch {
                DriverFcmManager.syncToken(this@MainActivity)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
