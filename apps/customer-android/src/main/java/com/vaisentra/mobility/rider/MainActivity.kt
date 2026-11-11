package com.vaisentra.mobility.rider

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.vaisentra.mobility.rider.navigation.RiderAppNavigation
import com.vaisentra.mobility.rider.theme.CanvasBlack
import com.vaisentra.mobility.rider.theme.RiderGoTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RiderGoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = CanvasBlack
                ) {
                    RiderAppNavigation()
                }
            }
        }
    }
}

