package com.luxury.chauffeur.driver

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.luxury.chauffeur.driver.core.notification.DriverFcmManager
import com.luxury.chauffeur.driver.core.security.DriverTokenManager
import com.luxury.chauffeur.driver.feature.luxury.DriverMasterCatalogFlow

@Composable
fun DriverAppNavigation(
    initialRideId: String? = null,
    initialPickup: String? = null,
    initialDest: String? = null,
    initialFare: String? = null
) {
    val context = LocalContext.current
    val tokenManager = remember { DriverTokenManager.getInstance(context) }

    LaunchedEffect(Unit) {
        DriverFcmManager.syncToken(context)
    }

    Surface(
        color = Color(0xFF0A0D0B),
        modifier = Modifier.fillMaxSize()
    ) {
        DriverMasterCatalogFlow(
            onSignOutCallback = {
                tokenManager.clear()
            }
        )
    }
}
