package com.luxury.chauffeur.customer.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object LuxuryColors {
    val ObsidianBlack = Color(0xFF0D0D11)
    val DeepGraphite = Color(0xFF16161E)
    val CardSurface = Color(0xFF1F1F2B)
    val CardBorder = Color(0xFF2C2C3E)
    
    val ChampagneGold = Color(0xFFD4AF37)
    val GoldMuted = Color(0xFFA68726)
    val GoldLight = Color(0xFFF3E5AB)
    
    val PlatinumWhite = Color(0xFFF8F9FA)
    val SubtleGray = Color(0xFF9CA3AF)
    val BorderGray = Color(0xFF374151)
    
    val LuxuryEmerald = Color(0xFF10B981)
    val CrimsonAlert = Color(0xFFEF4444)
    val RoyalIndigo = Color(0xFF6366F1)
}

object LuxuryDimensions {
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

object LuxuryTypography {
    val HeadlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        color = LuxuryColors.PlatinumWhite,
        letterSpacing = 0.5.sp
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
