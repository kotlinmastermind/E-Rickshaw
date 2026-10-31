package com.luxury.chauffeur.customer

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import com.google.android.gms.maps.MapsInitializer
import com.luxury.chauffeur.customer.core.network.ApiClient
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ApiClient.initialize(this)

        java.util.concurrent.Executors.newSingleThreadExecutor().execute {
            try {
                MapsInitializer.initialize(applicationContext, MapsInitializer.Renderer.LATEST) { renderer ->
                    Log.d("MapsInit", "Customer Maps initialized with renderer: $renderer")
                }
            } catch (_: Throwable) {
                try {
                    MapsInitializer.initialize(applicationContext)
                } catch (_: Throwable) {}
            }
        }

        setContent {
            Surface(color = Color.White) {
                CustomerAppNavigation()
            }
        }
    }
}
