package com.vaisentra.mobility.rider

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.vaisentra.mobility.rider.core.security.TokenManager
import com.vaisentra.mobility.rider.feature.luxury.CustomerMasterCatalogFlow

@Composable
fun CustomerAppNavigation() {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager.getInstance(context) }

    Surface(
        color = Color(0xFF0B0B0F),
        modifier = Modifier.fillMaxSize()
    ) {
        CustomerMasterCatalogFlow(
            onSignOutCallback = {
                tokenManager.clear()
            }
        )
    }
}
