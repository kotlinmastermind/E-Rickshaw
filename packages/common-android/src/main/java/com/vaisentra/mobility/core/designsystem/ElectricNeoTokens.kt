package com.vaisentra.mobility.core.designsystem

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * High-Tech Electric Neo Design System Tokens for Vaisentra Mobility Apps.
 * Visual Identity:
 * - Cyber Obsidian void canvas (#06090E)
 * - Luminescent Electric Cyan (#00F2FE) & Neon Emerald (#10B981) accents
 * - Sleek glassmorphism borders and high-visibility digital telemetry readouts
 */
object ElectricNeoTokens {
    // Canvas & Surface Palettes
    val CyberVoid = Color(0xFF06090E)               // Deepest dark canvas
    val CyberSurface = Color(0xFF0B111B)            // Card & container surface
    val CyberSurfaceElevated = Color(0xFF111A28)    // Modal sheets & headers
    val CyberSurfaceCard = Color(0xFF0E1624)        // Interactive list cards
    val CyberSurfaceHighlight = Color(0xFF172338)   // Active row focus

    // Luminescent Accents
    val ElectricCyan = Color(0xFF00F2FE)            // Primary GPS HUD & Route corridor
    val ElectricCyanDark = Color(0xFF0398A8)
    val ElectricCyanGlow = Color(0x3300F2FE)        // 20% alpha glow halo
    val ElectricCyanBorder = Color(0x4D00F2FE)      // 30% border stroke

    val NeonEmerald = Color(0xFF10B981)             // Online duty, active fares, confirmations
    val NeonEmeraldDark = Color(0xFF059669)
    val NeonEmeraldGlow = Color(0x3310B981)
    val NeonEmeraldBorder = Color(0x4D10B981)

    val SolarAmber = Color(0xFFF59E0B)              // Waiting timers, surge demand, caution
    val SolarAmberGlow = Color(0x33F59E0B)
    val SolarAmberBorder = Color(0x4DF59E0B)

    val CyberCrimson = Color(0xFFEF4444)            // Emergency SOS panic, dispatches, cancellations
    val CyberCrimsonGlow = Color(0x33EF4444)
    val CyberCrimsonBorder = Color(0x4DEF4444)

    val RoyalViolet = Color(0xFF8B5CF6)             // VIP pass, corporate reservations
    val RoyalVioletGlow = Color(0x338B5CF6)

    // Typography Colors
    val TextWhite = Color(0xFFFFFFFF)
    val TextCyan = Color(0xFF00F2FE)
    val TextEmerald = Color(0xFF10B981)
    val TextSecondary = Color(0xFF94A3B8)           // Slate matrix metadata
    val TextMuted = Color(0xFF64748B)               // Inactive placeholders
    val TextDim = Color(0xFF334155)

    // Glowing Neon Brushes & Gradients
    val CyanEmeraldGradient = Brush.horizontalGradient(
        colors = listOf(ElectricCyan, NeonEmerald)
    )

    val CyanGlowGradient = Brush.verticalGradient(
        colors = listOf(Color(0x3300F2FE), Color(0x0500F2FE))
    )

    val CyberCardGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF111A28), Color(0xFF0B111B))
    )

    val EmeraldGlowGradient = Brush.verticalGradient(
        colors = listOf(Color(0x3310B981), Color(0x0510B981))
    )

    val AmberGlowGradient = Brush.verticalGradient(
        colors = listOf(Color(0x33F59E0B), Color(0x05F59E0B))
    )

    val CrimsonGlowGradient = Brush.verticalGradient(
        colors = listOf(Color(0x33EF4444), Color(0x05EF4444))
    )

    // Corner Radii
    val RadiusXSmall = 8.dp
    val RadiusSmall = 14.dp
    val RadiusMedium = 20.dp
    val RadiusLarge = 28.dp
    val RadiusPill = 999.dp

    // Spacing
    val SpacingXSmall = 4.dp
    val SpacingSmall = 8.dp
    val SpacingMedium = 16.dp
    val SpacingLarge = 24.dp
    val SpacingXLarge = 32.dp

    // Telemetry Gauges Dimensions
    val SpeedometerSize = 130.dp
    val BatteryRingSize = 64.dp
    val AvatarRingSize = 56.dp
}

