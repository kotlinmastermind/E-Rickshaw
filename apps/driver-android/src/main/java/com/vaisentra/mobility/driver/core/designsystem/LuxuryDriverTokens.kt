package com.vaisentra.mobility.driver.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding
import com.vaisentra.mobility.core.designsystem.SharedDesignTokens

object DriverColors {
    val MidnightBlack = Color(0xFF090A0F)
    val CockpitDark = Color(0xFF13151F)
    val SurfaceCard = Color(0xFF1B1E2E)
    val SurfaceBorder = Color(0xFF282C42)
    
    val ChauffeurGold = SharedDesignTokens.ChauffeurGold
    val StatusOnline = SharedDesignTokens.Emerald
    val StatusBusy = SharedDesignTokens.Amber
    val StatusOffline = Color(0xFF6B7280)
    val AlertSuccess = SharedDesignTokens.Emerald
    val AlertDanger = SharedDesignTokens.Red
    
    val TextPure = SharedDesignTokens.White
    val TextMuted = Color(0xFF9CA3AF)
}

object DriverMobilityColors {
    val PrimaryYellow = SharedDesignTokens.PrimaryYellow
    val PrimaryYellowDark = SharedDesignTokens.PrimaryYellowDark
    val EcoGreen = SharedDesignTokens.EcoGreen
    val LightGreen = SharedDesignTokens.LightGreen
    val BackgroundPure = SharedDesignTokens.BackgroundPure
    val SurfaceLight = SharedDesignTokens.SurfaceLight
    val BorderLight = SharedDesignTokens.BorderLight
    val TextDark = SharedDesignTokens.TextDark
    val TextMuted = SharedDesignTokens.TextMuted
    val StatusOnline = SharedDesignTokens.Emerald
    val StatusOffline = SharedDesignTokens.Red
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
