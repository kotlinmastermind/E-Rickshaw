package com.luxury.chauffeur.customer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import com.luxury.chauffeur.customer.core.designsystem.LuxuryColors
import com.luxury.chauffeur.customer.core.network.ApiClient

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ApiClient.initialize(this)
        setContent {
            Surface(color = LuxuryColors.ObsidianBlack) {
                CustomerAppNavigation()
            }
        }
    }
}
