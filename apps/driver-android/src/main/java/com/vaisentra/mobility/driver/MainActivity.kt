package com.vaisentra.mobility.driver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.vaisentra.mobility.driver.navigation.DriverAppNavigation
import com.vaisentra.mobility.driver.theme.CockpitBackground
import com.vaisentra.mobility.driver.theme.DriverGoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DriverGoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = CockpitBackground
                ) {
                    DriverAppNavigation()
                }
            }
        }
    }
}

