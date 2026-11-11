package com.vaisentra.mobility.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Standardized Shared Design System Tokens for Vasantra Mobility applications.
 * Enforces DRY principles by centralizing identical brand, status, slate, and surface
 * color constants, standard layout dimensions, and typography scale factors.
 */
object SharedDesignTokens {
    // Brand & Accent Colors
    val BrandGold = Color(0xFFD4AF37)
    val ChampagneGold = BrandGold
    val ChauffeurGold = BrandGold
    val GoldMuted = Color(0xFFA68726)
    val GoldLight = Color(0xFFF3E5AB)
    val BackgroundDark = Color(0xFF0B0B0F)

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
    val SelectedBlue = Color(0xFF1A56DB)

    val Purple = Color(0xFFA855F7)
    val PurpleAlpha = Color(0x26A855F7)
    val PurpleBorder = Color(0x4DA855F7)
    val RoyalIndigo = Color(0xFF6366F1)

    // Dark Neutral / Slate Surfaces
    val Slate900 = Color(0xFF0F172A)
    val Slate800 = Color(0xFF1E293B)
    val Slate700 = Color(0xFF334155)
    val Slate400 = Color(0xFF94A3B8)
    val White = Color(0xFFFFFFFF)

    // Mobility UI Colors
    val PrimaryYellow = Color(0xFFF9C933)
    val PrimaryYellowDark = Color(0xFFE5B422)
    val EcoGreen = Color(0xFF0F8A4E)
    val LightGreen = Color(0xFFE8F5E9)
    val WarningYellow = Color(0xFFFEF9C3)
    val WarningBorder = Color(0xFFFDE047)
    val WarningText = Color(0xFF713F12)
    val BackgroundPure = Color(0xFFFFFFFF)
    val SurfaceLight = Color(0xFFF7F8FA)
    val BorderLight = Color(0xFFE5E7EB)
    val TextDark = Color(0xFF1E2022)
    val TextMuted = Color(0xFF6B7280)
    val BadgeTeal = Color(0xFFD1FAE5)
    val BadgeTealText = Color(0xFF065F46)
    val DiscountGreen = Color(0xFF16A34A)

    // Standard Layout Dimensions
    val RadiusSmall = 8.dp
    val RadiusMedium = 16.dp
    val RadiusLarge = 24.dp
    val RadiusPill = 999.dp

    val PaddingSmall = 8.dp
    val PaddingMedium = 16.dp
    val PaddingLarge = 24.dp
    val PaddingXLarge = 32.dp

    val ElevationNone = 0.dp
    val ElevationSubtle = 4.dp
    val ElevationFloating = 12.dp
}
