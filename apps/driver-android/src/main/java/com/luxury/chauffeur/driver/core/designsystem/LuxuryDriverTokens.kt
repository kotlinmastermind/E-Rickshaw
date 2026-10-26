package com.luxury.chauffeur.driver.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object DriverColors {
    val MidnightBlack = Color(0xFF090A0F)
    val CockpitDark = Color(0xFF13151F)
    val SurfaceCard = Color(0xFF1B1E2E)
    val SurfaceBorder = Color(0xFF282C42)
    
    val ChauffeurGold = Color(0xFFD4AF37)
    val StatusOnline = Color(0xFF10B981)
    val StatusBusy = Color(0xFFF59E0B)
    val StatusOffline = Color(0xFF6B7280)
    val AlertSuccess = Color(0xFF10B981)
    val AlertDanger = Color(0xFFEF4444)
    
    val TextPure = Color(0xFFFFFFFF)
    val TextMuted = Color(0xFF9CA3AF)
}

object DriverTypography {
    val Title = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        color = DriverColors.TextPure
    )
    val Subtitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        color = DriverColors.ChauffeurGold
    )
    val Body = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = DriverColors.TextPure
    )
    val MetricValue = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        color = DriverColors.ChauffeurGold
    )
}
