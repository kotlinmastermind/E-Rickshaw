package com.vaisentra.mobility.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Standardized Shared Design System Tokens for Vaisentra Mobility applications.
 * Enforces Material 3 Expressive (Google Pixel style) design principles:
 * - Dynamic tonal palettes (Primary Blue, Eco Mint/Teal accents)
 * - Tonal elevation surfaces (SurfaceContainer, OnSurface)
 * - Expressive rounded shapes (28.dp cards, 999.dp pills)
 * - High-contrast readable typography
 */
object SharedDesignTokens {
    // Material 3 Expressive - Primary Palette
    val M3Primary = Color(0xFF0B57D0)          // Pixel Vibrant Blue
    val M3PrimaryDark = Color(0xFFA8C7FA)      // Pixel Blue for dark surfaces
    val M3PrimaryContainer = Color(0xFFD3E3FD) // Soft blue container
    val M3OnPrimaryContainer = Color(0xFF041E49)

    // Material 3 Expressive - Eco / EV Accent Palette
    val M3EcoGreen = Color(0xFF00875A)         // Clean electric green
    val M3EcoGreenLight = Color(0xFFC4EED0)
    val M3EcoGreenDark = Color(0xFF004D34)

    // Material 3 Expressive - Tonal Surfaces
    val M3SurfaceDark = Color(0xFF111318)
    val M3SurfaceContainerDark = Color(0xFF1D2024)
    val M3SurfaceContainerHighDark = Color(0xFF282A2F)
    val M3SurfaceLight = Color(0xFFF8F9FF)
    val M3SurfaceContainerLight = Color(0xFFEEF0F8)
    val M3OutlineDark = Color(0xFF44474E)
    val M3OutlineLight = Color(0xFF74777F)

    // Legacy Brand Aliases (smoothly adapted to M3 Expressive)
    val BrandGold = Color(0xFF0B57D0)          // Mapped to M3 Primary Blue for unified modern theme
    val ChampagneGold = Color(0xFFA8C7FA)
    val ChauffeurGold = Color(0xFF0B57D0)
    val GoldMuted = Color(0xFF7C8BA1)
    val GoldLight = Color(0xFFD3E3FD)
    val BackgroundDark = M3SurfaceDark

    // Status & Accent Palettes
    val Emerald = Color(0xFF10B981)
    val EmeraldDark = Color(0xFF059669)
    val EmeraldAlpha = Color(0x2610B981)
    val EmeraldBorder = Color(0x4D10B981)

    val Amber = Color(0xFFF59E0B)
    val AmberDark = Color(0xFFD97706)
    val AmberAlpha = Color(0x26F59E0B)
    val AmberBorder = Color(0x4DF59E0B)

    val Red = Color(0xFFEF4444)
    val RedAlpha = Color(0x26EF4444)
    val RedBorder = Color(0x4DEF4444)

    val Blue = Color(0xFF3B82F6)
    val BlueAlpha = Color(0x263B82F6)
    val BlueBorder = Color(0x4D3B82F6)
    val SelectedBlue = Color(0xFF0B57D0)

    val Purple = Color(0xFFA855F7)
    val PurpleAlpha = Color(0x26A855F7)
    val PurpleBorder = Color(0x4DA855F7)
    val RoyalIndigo = Color(0xFF6366F1)

    // Dark Neutral / Slate Surfaces
    val Slate900 = Color(0xFF111318)
    val Slate800 = Color(0xFF1D2024)
    val Slate700 = Color(0xFF282A2F)
    val Slate400 = Color(0xFF8E9199)
    val White = Color(0xFFFFFFFF)

    // Mobility UI Colors
    val PrimaryYellow = Color(0xFF0B57D0)
    val PrimaryYellowDark = Color(0xFF041E49)
    val EcoGreen = M3EcoGreen
    val LightGreen = M3EcoGreenLight
    val WarningYellow = Color(0xFFFEF9C3)
    val WarningBorder = Color(0xFFFDE047)
    val WarningText = Color(0xFF713F12)
    val BackgroundPure = Color(0xFFFFFFFF)
    val SurfaceLight = Color(0xFFF8F9FA)
    val BorderLight = Color(0xFFE0E2EC)
    val TextDark = Color(0xFF1F1F1F)
    val TextMuted = Color(0xFF74777F)
    val BadgeTeal = Color(0xFFC4EED0)
    val BadgeTealText = Color(0xFF004D34)
    val DiscountGreen = Color(0xFF00875A)

    // Standard Layout Dimensions (Material 3 Expressive Shapes)
    val RadiusSmall = 12.dp
    val RadiusMedium = 20.dp
    val RadiusLarge = 28.dp                    // M3 Expressive Card Radius
    val RadiusPill = 999.dp                    // M3 Pill Shape

    val PaddingSmall = 8.dp
    val PaddingMedium = 16.dp
    val PaddingLarge = 24.dp
    val PaddingXLarge = 32.dp

    val ElevationNone = 0.dp
    val ElevationSubtle = 2.dp
    val ElevationFloating = 8.dp
}
