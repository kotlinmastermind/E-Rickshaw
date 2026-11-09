package com.vaisentra.mobility.driver

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import com.vaisentra.mobility.driver.core.designsystem.DriverColors
import com.vaisentra.mobility.driver.core.network.DriverApiClient

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            DriverApiClient.initialize(applicationContext)
        } catch (_: Throwable) {}

        val dispatchRideId = intent?.getStringExtra("ride_id")
        val dispatchPickup = intent?.getStringExtra("pickup_address")
        val dispatchDest = intent?.getStringExtra("destination_address")
        val dispatchFare = intent?.getStringExtra("fare_rupees")

        // Wake screen only when launched to display an incoming trip offer
        if (!dispatchRideId.isNullOrBlank()) {
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
        }

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
