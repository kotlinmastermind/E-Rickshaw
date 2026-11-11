package com.vaisentra.mobility.driver.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vaisentra.mobility.driver.core.designsystem.DriverColors

@Composable
fun DriverAuthScreen(
    onAuthenticationSuccess: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DriverColors.MidnightBlack)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        DriverAuthenticationContent(
            onAuthenticationSuccess = onAuthenticationSuccess,
            onDismiss = null
        )
    }
}

