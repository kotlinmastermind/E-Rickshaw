package com.luxury.chauffeur.driver.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding

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

object DriverMobilityColors {
    val PrimaryYellow = Color(0xFFF9C933)
    val PrimaryYellowDark = Color(0xFFE5B422)
    val EcoGreen = Color(0xFF0F8A4E)
    val LightGreen = Color(0xFFE8F5E9)
    val BackgroundPure = Color(0xFFFFFFFF)
    val SurfaceLight = Color(0xFFF7F8FA)
    val BorderLight = Color(0xFFE5E7EB)
    val TextDark = Color(0xFF1E2022)
    val TextMuted = Color(0xFF6B7280)
    val StatusOnline = Color(0xFF10B981)
    val StatusOffline = Color(0xFFEF4444)
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

@androidx.compose.runtime.Composable
fun GoldBadge(text: String) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
        color = DriverColors.ChauffeurGold.copy(alpha = 0.2f),
        border = androidx.compose.foundation.BorderStroke(1.dp, DriverColors.ChauffeurGold)
    ) {
        androidx.compose.material3.Text(
            text = text,
            color = DriverColors.ChauffeurGold,
            fontSize = 11.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
