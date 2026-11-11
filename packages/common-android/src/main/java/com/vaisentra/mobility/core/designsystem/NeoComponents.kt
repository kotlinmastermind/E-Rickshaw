package com.vaisentra.mobility.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Reusable High-Tech Electric Neo UI Components.
 */

@Composable
fun NeoCard(
    modifier: Modifier = Modifier,
    borderColor: Color = ElectricNeoTokens.ElectricCyanBorder,
    backgroundColor: Color = ElectricNeoTokens.CyberSurfaceCard,
    cornerRadius: Dp = ElectricNeoTokens.RadiusMedium,
    glowElevation: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .shadow(glowElevation, shape = RoundedCornerShape(cornerRadius), ambientColor = borderColor, spotColor = borderColor)
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(cornerRadius))
            .padding(ElectricNeoTokens.SpacingMedium),
        content = content
    )
}

@Composable
fun NeoGlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    accentColor: Color = ElectricNeoTokens.ElectricCyan,
    textColor: Color = Color.Black
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(ElectricNeoTokens.RadiusMedium))
            .background(
                if (enabled) {
                    Brush.horizontalGradient(listOf(accentColor, ElectricNeoTokens.NeonEmerald))
                } else {
                    SolidColor(ElectricNeoTokens.CyberSurfaceHighlight)
                }
            )
            .clickable(enabled = enabled && !isLoading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = textColor,
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.5.dp
            )
        } else {
            Text(
                text = text,
                color = if (enabled) textColor else ElectricNeoTokens.TextMuted,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun NeoSpeedometerGauge(
    speedKmh: Float,
    maxSpeedKmh: Float = 60f,
    modifier: Modifier = Modifier
) {
    val progress = (speedKmh / maxSpeedKmh).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 300),
        label = "speedProgress"
    )

    Box(
        modifier = modifier
            .size(ElectricNeoTokens.SpeedometerSize)
            .clip(CircleShape)
            .background(ElectricNeoTokens.CyberSurface)
            .border(2.dp, ElectricNeoTokens.ElectricCyanBorder, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${speedKmh.toInt()}",
                color = ElectricNeoTokens.ElectricCyan,
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "KM/H",
                color = ElectricNeoTokens.TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun NeoBatteryGauge(
    batteryPercent: Int,
    isCharging: Boolean = false,
    modifier: Modifier = Modifier
) {
    val color = when {
        batteryPercent > 50 -> ElectricNeoTokens.NeonEmerald
        batteryPercent > 20 -> ElectricNeoTokens.SolarAmber
        else -> ElectricNeoTokens.CyberCrimson
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(ElectricNeoTokens.RadiusPill))
            .background(ElectricNeoTokens.CyberSurface)
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(ElectricNeoTokens.RadiusPill))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = if (isCharging) "⚡" else "🔋", fontSize = 12.sp)
        Text(
            text = "$batteryPercent%",
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun NeoStatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accentColor: Color = ElectricNeoTokens.ElectricCyan
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(ElectricNeoTokens.RadiusPill))
            .background(ElectricNeoTokens.CyberSurfaceElevated)
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(ElectricNeoTokens.RadiusPill))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            color = ElectricNeoTokens.TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            color = accentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun NeoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(ElectricNeoTokens.RadiusSmall))
            .background(ElectricNeoTokens.CyberSurface)
            .border(1.dp, ElectricNeoTokens.ElectricCyanBorder, RoundedCornerShape(ElectricNeoTokens.RadiusSmall))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(10.dp))
            }
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = ElectricNeoTokens.TextMuted,
                        fontSize = 13.sp
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = TextStyle(
                        color = ElectricNeoTokens.TextWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    cursorBrush = SolidColor(ElectricNeoTokens.ElectricCyan),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(10.dp))
                trailingIcon()
            }
        }
    }
}

