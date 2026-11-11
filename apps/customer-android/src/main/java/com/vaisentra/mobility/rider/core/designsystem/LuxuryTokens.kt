package com.vaisentra.mobility.rider.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.core.designsystem.SharedDesignTokens

object LuxuryColors {
    val ObsidianBlack = SharedDesignTokens.M3SurfaceDark
    val DeepGraphite = SharedDesignTokens.M3SurfaceContainerDark
    val DeepCharcoal = DeepGraphite
    val CardSurface = SharedDesignTokens.M3SurfaceContainerHighDark
    val CardBorder = SharedDesignTokens.M3OutlineDark
    
    val ChampagneGold = SharedDesignTokens.M3PrimaryDark
    val GoldMuted = SharedDesignTokens.GoldMuted
    val GoldLight = SharedDesignTokens.GoldLight
    
    val PlatinumWhite = Color(0xFFE2E2E9)
    val SubtleGray = SharedDesignTokens.Slate400
    val MutedSilver = SubtleGray
    val BorderGray = SharedDesignTokens.M3OutlineDark
    
    val LuxuryEmerald = SharedDesignTokens.M3EcoGreen
    val EmeraldGreen = SharedDesignTokens.M3EcoGreen
    val CharcoalSurface = DeepGraphite
    val CrimsonAlert = SharedDesignTokens.Red
    val RoyalIndigo = SharedDesignTokens.RoyalIndigo
}

object MobilityColors {
    val BrandGold = SharedDesignTokens.BrandGold
    val PrimaryYellow = SharedDesignTokens.PrimaryYellow
    val PrimaryYellowDark = SharedDesignTokens.PrimaryYellowDark
    val EcoGreen = SharedDesignTokens.EcoGreen
    val PrimaryGreen = SharedDesignTokens.EcoGreen
    val LightGreen = SharedDesignTokens.LightGreen
    val WarningYellow = SharedDesignTokens.WarningYellow
    val WarningBorder = SharedDesignTokens.WarningBorder
    val WarningText = SharedDesignTokens.WarningText
    val BackgroundPure = SharedDesignTokens.BackgroundPure
    val SurfaceLight = SharedDesignTokens.SurfaceLight
    val BorderLight = SharedDesignTokens.BorderLight
    val TextDark = SharedDesignTokens.TextDark
    val TextMuted = SharedDesignTokens.TextMuted
    val SelectedBlue = SharedDesignTokens.SelectedBlue
    val BadgeTeal = SharedDesignTokens.BadgeTeal
    val BadgeTealText = SharedDesignTokens.BadgeTealText
    val DiscountGreen = SharedDesignTokens.DiscountGreen
}

object LuxuryDimensions {
    val RadiusSmall = SharedDesignTokens.RadiusSmall
    val RadiusMedium = SharedDesignTokens.RadiusMedium
    val RadiusLarge = SharedDesignTokens.RadiusLarge
    val RadiusPill = SharedDesignTokens.RadiusPill
    
    val PaddingSmall = SharedDesignTokens.PaddingSmall
    val PaddingMedium = SharedDesignTokens.PaddingMedium
    val PaddingLarge = SharedDesignTokens.PaddingLarge
    val PaddingXLarge = SharedDesignTokens.PaddingXLarge
    
    val ElevationNone = SharedDesignTokens.ElevationNone
    val ElevationSubtle = SharedDesignTokens.ElevationSubtle
    val ElevationFloating = SharedDesignTokens.ElevationFloating
}

object LuxuryTypography {
    val HeadlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        color = LuxuryColors.PlatinumWhite,
        letterSpacing = 0.sp
    )
    val HeadlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        color = LuxuryColors.PlatinumWhite,
        letterSpacing = 0.25.sp
    )
    val TitleGold = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        color = LuxuryColors.ChampagneGold,
        letterSpacing = 0.15.sp
    )
    val BodyRegular = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = LuxuryColors.PlatinumWhite,
        lineHeight = 20.sp
    )
    val BodySubtle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        color = LuxuryColors.SubtleGray
    )
    val OtpDigits = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        color = LuxuryColors.ChampagneGold,
        letterSpacing = 8.sp
    )
}

val ObsidianBlack = LuxuryColors.ObsidianBlack
val DeepCharcoal = Color(0xFF16161E)
val DeepGraphite = LuxuryColors.DeepGraphite
val ChampagneGold = LuxuryColors.ChampagneGold
val PlatinumWhite = LuxuryColors.PlatinumWhite
val MutedSilver = Color(0xFF9CA3AF)
val SubtleGray = LuxuryColors.SubtleGray
val EmeraldStatus = LuxuryColors.LuxuryEmerald
val CardSurface = LuxuryColors.CardSurface
val CardBorder = LuxuryColors.CardBorder
