package com.vaisentra.mobility.rider

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import com.vaisentra.mobility.rider.core.network.ApiClient
import com.vaisentra.mobility.rider.feature.payments.RazorpayCheckoutHelper

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            ApiClient.initialize(this)
        } catch (_: Throwable) {}

        try {
            RazorpayCheckoutHelper.initialize(this)
        } catch (_: Throwable) {}

        setContent {
            Surface(color = Color(0xFF0F172A)) {
                CustomerAppNavigation()
            }
        }
    }
}
