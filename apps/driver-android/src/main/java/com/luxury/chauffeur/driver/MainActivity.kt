package com.luxury.chauffeur.driver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import com.luxury.chauffeur.driver.core.designsystem.DriverColors
import com.luxury.chauffeur.driver.core.network.DriverApiClient

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DriverApiClient.initialize(this)
        setContent {
            Surface(color = DriverColors.MidnightBlack) {
                DriverAppNavigation()
            }
        }
    }
}
