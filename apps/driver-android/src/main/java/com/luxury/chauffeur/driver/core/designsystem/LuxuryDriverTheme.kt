package com.luxury.chauffeur.driver.core.designsystem

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * Premium dark color scheme — gold/champagne accent on deep midnight surfaces.
 * Maps existing [DriverColors] values into proper Material 3 color roles.
 */
private val LuxuryDarkColorScheme = darkColorScheme(
    primary = Color(0xFFD4AF37),
    onPrimary = Color(0xFF090A0F),
    primaryContainer = Color(0xFF3D3117),
    onPrimaryContainer = Color(0xFFEDD9A3),
    secondary = Color(0xFFD4C5A0),
    onSecondary = Color(0xFF383016),
    secondaryContainer = Color(0xFF4F472B),
    onSecondaryContainer = Color(0xFFF1E1BA),
    tertiary = Color(0xFF10B981),
    onTertiary = Color(0xFF003822),
    tertiaryContainer = Color(0xFF005234),
    onTertiaryContainer = Color(0xFF6FF7BC),
    error = Color(0xFFEF4444),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF090A0F),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF13151F),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1B1E2E),
    onSurfaceVariant = Color(0xFF9CA3AF),
    outline = Color(0xFF282C42),
    outlineVariant = Color(0xFF1E2030),
    inverseSurface = Color(0xFFE4E2DB),
    inverseOnSurface = Color(0xFF1B1C17),
    inversePrimary = Color(0xFF7B6B2D),
    surfaceTint = Color(0xFFD4AF37)
)

/** Light color scheme for optional light-mode support. */
private val LuxuryLightColorScheme = lightColorScheme(
    primary = Color(0xFFB8941F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFF0C7),
    onPrimaryContainer = Color(0xFF3D3117),
    secondary = Color(0xFF6B5E3F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF5E6C0),
    onSecondaryContainer = Color(0xFF241A04),
    tertiary = Color(0xFF0F8A4E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE8F5E9),
    onTertiaryContainer = Color(0xFF003822),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF1E2022),
    surface = Color(0xFFF7F8FA),
    onSurface = Color(0xFF1E2022),
    surfaceVariant = Color(0xFFEDE8DB),
    onSurfaceVariant = Color(0xFF4C4639),
    outline = Color(0xFFE5E7EB),
    outlineVariant = Color(0xFFCFC6B4),
    inverseSurface = Color(0xFF313033),
    inverseOnSurface = Color(0xFFF4EFF4),
    inversePrimary = Color(0xFFD4AF37),
    surfaceTint = Color(0xFFB8941F)
)

/** Material 3 typography scale for the driver app. */
val LuxuryTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.5.sp)
)

/** Consistent rounded shapes matching the app's premium aesthetic. */
val LuxuryShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

/** Spacing tokens for consistent padding/margins across screens. */
object LuxurySpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val section = 40.dp
}

/**
 * Centralized Material 3 theme for the E-Rickshaw Driver app.
 *
 * Wraps [MaterialTheme] with premium dark + gold/champagne color scheme.
 * Supports dark/light mode switching via [isSystemInDarkTheme].
 * All new and existing screens should be wrapped in this theme.
 *
 * Existing [DriverColors] and [DriverTypography] objects remain available
 * for backward compatibility until screens are fully migrated.
 *
 * @param darkTheme Whether to use the dark color scheme. Defaults to system preference.
 * @param content The composable content to theme.
 */
@Composable
fun LuxuryDriverTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) LuxuryDarkColorScheme else LuxuryLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LuxuryTypography,
        shapes = LuxuryShapes,
        content = content
    )
}
