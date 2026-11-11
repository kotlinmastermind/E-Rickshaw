package com.vaisentra.mobility.driver.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PilotOnlineGreen,
    onPrimary = PilotCanvas,
    secondary = PilotElectricCyan,
    onSecondary = PilotCanvas,
    tertiary = PilotAmber,
    background = PilotCanvas,
    onBackground = PilotTextWhite,
    surface = PilotSurface,
    onSurface = PilotTextWhite,
    surfaceVariant = PilotSurfaceElevated,
    onSurfaceVariant = PilotTextMuted,
    outline = PilotBorder,
    error = PilotCrimson,
    onError = PilotTextWhite
)

@Composable
fun DriverGoTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            window?.let {
                it.statusBarColor = PilotCanvas.toArgb()
                it.navigationBarColor = PilotCanvas.toArgb()
                WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(it, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}

