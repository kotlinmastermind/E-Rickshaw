package com.vaisentra.mobility.rider.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.rider.theme.*

/**
 * High-performance vector map canvas that simulates realistic Rapido / Uber map tiles:
 * - Dark luxury street grid with avenues, boulevards & ring roads
 * - Live animated glowing Cyan Polyline with directional pulse
 * - 3D-styled Pickup Pin (Green) & Destination Pin (Red)
 * - Nearby live EV Pilot markers with simulated idle drifting/heading
 */
@Composable
fun RapidoLiveMapCanvas(
    modifier: Modifier = Modifier,
    showRoute: Boolean = true,
    pickupLabel: String = "Connaught Place",
    dropLabel: String = "Sector 62, Noida",
    nearbyVehiclesCount: Int = 8
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarMapTransitions")

    // Animated dash phase for glowing polyline
    val linePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 60f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PolylineFlow"
    )

    // Animated beacon pulse around rider pickup
    val radarPulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarPulse"
    )
    val radarAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarAlpha"
    )

    // Vehicle slight drift animation to look active
    val vehicleJitter by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "VehicleJitter"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090B10))
    ) {
        // 1. Vector Map Grid & Roads
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Background city blocks subtle fill
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF07090E), Color(0xFF0D111A), Color(0xFF080B12))
                )
            )

            // Street Grid lines (Avenues)
            val streetColor = Color(0xFF161B27)
            val majorAvenueColor = Color(0xFF1E2638)
            val ringRoadColor = Color(0xFF253047)

            // Horizontal secondary streets
            var y = 60f
            while (y < height) {
                drawLine(
                    color = streetColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 2.5f
                )
                y += 110f
            }

            // Vertical streets
            var x = 40f
            while (x < width) {
                drawLine(
                    color = streetColor,
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 2.5f
                )
                x += 100f
            }

            // Diagonal Expressway / Boulevard
            val mainRoadPath = Path().apply {
                moveTo(0f, height * 0.25f)
                cubicTo(
                    width * 0.35f, height * 0.3f,
                    width * 0.65f, height * 0.45f,
                    width, height * 0.55f
                )
            }
            drawPath(
                path = mainRoadPath,
                color = majorAvenueColor,
                style = Stroke(width = 16f, cap = StrokeCap.Round)
            )

            // Secondary Ring Road Curve
            val ringRoadPath = Path().apply {
                moveTo(width * 0.1f, 0f)
                cubicTo(
                    width * 0.2f, height * 0.5f,
                    width * 0.8f, height * 0.6f,
                    width * 0.95f, height
                )
            }
            drawPath(
                path = ringRoadPath,
                color = ringRoadColor,
                style = Stroke(width = 14f, cap = StrokeCap.Round)
            )

            // Metro Transit Line (Subtle Cyan dashes)
            val metroPath = Path().apply {
                moveTo(width * 0.05f, height * 0.85f)
                lineTo(width * 0.45f, height * 0.5f)
                lineTo(width * 0.95f, height * 0.15f)
            }
            drawPath(
                path = metroPath,
                color = Color(0x3300F2FE),
                style = Stroke(
                    width = 4f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                )
            )

            // 2. Active Route Glowing Polyline (when destination is selected)
            if (showRoute) {
                val startX = width * 0.35f
                val startY = height * 0.45f
                val endX = width * 0.78f
                val endY = height * 0.26f

                val routePath = Path().apply {
                    moveTo(startX, startY)
                    cubicTo(
                        startX + 40f, startY - 90f,
                        endX - 80f, startY - 40f,
                        endX - 30f, endY + 80f
                    )
                    lineTo(endX, endY)
                }

                // Outer Soft Blue Glow
                drawPath(
                    path = routePath,
                    color = Color(0x402563EB),
                    style = Stroke(width = 22f, cap = StrokeCap.Round)
                )

                // Mid Cyan Neon Track
                drawPath(
                    path = routePath,
                    color = Color(0xFF00F2FE),
                    style = Stroke(width = 8f, cap = StrokeCap.Round)
                )

                // Animated Directional Dash Pulse
                drawPath(
                    path = routePath,
                    color = Color(0xFFFFFFFF),
                    style = Stroke(
                        width = 4f,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 24f), linePhase)
                    )
                )

                // Pickup Pulsing Beacon Halo
                drawCircle(
                    color = Color(0xFF10B981).copy(alpha = radarAlpha),
                    radius = 35f * radarPulse,
                    center = Offset(startX, startY)
                )

                // Pickup Dot Core
                drawCircle(
                    color = Color(0xFF10B981),
                    radius = 8f,
                    center = Offset(startX, startY)
                )

                // Drop Dot Core
                drawCircle(
                    color = Color(0xFFEF4444),
                    radius = 8f,
                    center = Offset(endX, endY)
                )
            }
        }

        // 3. Floating Overlay Elements (Pins, Live Vehicles)
        // Pickup Pin Tag
        if (showRoute) {
            Box(
                modifier = Modifier
                    .offset(x = 100.dp, y = 300.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceDark)
                    .border(1.dp, EmeraldGreen, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(EmeraldGreen))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Pickup: $pickupLabel",
                        color = TextWhite,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Drop Pin Tag
            Box(
                modifier = Modifier
                    .offset(x = 240.dp, y = 170.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceDark)
                    .border(1.dp, CrimsonRed, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(CrimsonRed))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Drop: $dropLabel",
                        color = TextWhite,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Live Nearby Vehicles (Simulated Real-time GPS Positions)
        val vehicles = listOf(
            Pair(60.dp, 260.dp),
            Pair(160.dp, 210.dp),
            Pair(290.dp, 330.dp),
            Pair(210.dp, 400.dp),
            Pair(110.dp, 450.dp)
        )
        vehicles.forEachIndexed { index, (x, y) ->
            Box(
                modifier = Modifier
                    .offset(x = x + (vehicleJitter * (index % 2 * 2 - 1)).dp, y = y)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(SurfaceElevated)
                    .border(1.dp, BorderSubtle, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (index % 3) {
                        0 -> "🛺"
                        1 -> "⚡"
                        else -> "🛵"
                    },
                    fontSize = 16.sp
                )
            }
        }
    }
}

