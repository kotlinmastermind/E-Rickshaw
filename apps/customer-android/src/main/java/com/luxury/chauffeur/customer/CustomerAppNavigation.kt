package com.luxury.chauffeur.customer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.luxury.chauffeur.customer.core.notification.CustomerFcmManager
import com.luxury.chauffeur.customer.core.security.TokenManager
import com.luxury.chauffeur.customer.feature.luxury.CustomerMasterCatalogFlow

@Composable
fun CustomerAppNavigation() {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager.getInstance(context) }

    LaunchedEffect(Unit) {
        CustomerFcmManager.syncToken(context)
    }

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
