package com.vaisentra.mobility.driver.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.Style
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.ViewAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.extension.compose.style.MapStyle
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import com.vaisentra.mobility.driver.theme.*

/**
 * Native Android Mapbox SDK (Kotlin + Jetpack Compose) for DriverGo Pilot Cockpit.
 * Supports Driver live GPS positioning, customer pickup heading, navigation polyline, and surge heatmap corridors.
 */
@Composable
fun DriverMapboxLiveView(
    modifier: Modifier = Modifier,
    driverLat: Double = 28.6139,
    driverLng: Double = 77.2090,
    targetLat: Double? = null,
    targetLng: Double? = null,
    targetLabel: String = "Passenger Pickup",
    isNavigating: Boolean = false,
    headingDegrees: Double = 0.0,
    onRecenterClicked: (() -> Unit)? = null
) {
    if (LocalInspectionMode.current) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(PilotCanvas),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "🗺️ Driver Native Mapbox ($targetLabel)", color = PilotTextDim, fontSize = 14.sp)
        }
        return
    }

    val driverPoint = remember(driverLat, driverLng) {
        Point.fromLngLat(driverLng, driverLat)
    }

    val targetPoint = remember(targetLat, targetLng) {
        if (targetLat != null && targetLng != null) {
            Point.fromLngLat(targetLng, targetLat)
        } else null
    }

    var selectedStyleUri by remember { mutableStateOf(Style.DARK) }
    var showStyleSwitcher by remember { mutableStateOf(false) }

    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(driverPoint)
            zoom(15.0)
            pitch(if (isNavigating) 45.0 else 30.0)
            bearing(headingDegrees)
        }
    }

    // Dynamic camera adjustment for driver navigation
    LaunchedEffect(driverLat, driverLng, targetLat, targetLng, isNavigating, headingDegrees) {
        val nonNullTargetLat = targetLat
        val nonNullTargetLng = targetLng
        if (isNavigating && nonNullTargetLat != null && nonNullTargetLng != null) {
            val midLat = (driverLat + nonNullTargetLat) / 2.0
            val midLng = (driverLng + nonNullTargetLng) / 2.0
            mapViewportState.flyTo(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(midLng, midLat))
                    .zoom(14.0)
                    .pitch(45.0)
                    .bearing(headingDegrees)
                    .build()
            )
        } else {
            mapViewportState.flyTo(
                CameraOptions.Builder()
                    .center(driverPoint)
                    .zoom(15.2)
                    .pitch(35.0)
                    .bearing(headingDegrees)
                    .build()
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PilotCanvas)
    ) {
        // 1. Native Compose Mapbox Map
        MapboxMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = mapViewportState,
            style = {
                MapStyle(style = selectedStyleUri)
            }
        ) {
            // A. Driver EV Marker (Heading Indicator)
            ViewAnnotation(
                options = viewAnnotationOptions {
                    geometry(driverPoint)
                    allowOverlap(true)
                }
            ) {
                DriverPuckNode()
            }

            // B. Passenger Pickup / Drop Target Marker & Navigation Polyline
            if (targetPoint != null) {
                ViewAnnotation(
                    options = viewAnnotationOptions {
                        geometry(targetPoint)
                        allowOverlap(true)
                    }
                ) {
                    TargetLocationNode(label = targetLabel)
                }

                // Glowing Navigation Polyline Corridors
                val routePoints = remember(driverPoint, targetPoint) {
                    listOf(driverPoint, targetPoint)
                }

                PolylineAnnotation(
                    points = routePoints
                ) {
                    lineColor = Color(0xFF10B981) // Emerald Green dispatch line
                    lineWidth = 6.0
                    lineOpacity = 0.95
                }
            }
        }

        // 2. Cockpit Quick Map Controls (Recenter & Style Mode)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(PilotSurfaceElevated.copy(alpha = 0.9f))
                    .border(1.dp, PilotBorder, CircleShape)
                    .clickable { showStyleSwitcher = !showStyleSwitcher },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = "Map Style",
                    tint = PilotTextWhite,
                    modifier = Modifier.size(20.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(PilotSurfaceElevated.copy(alpha = 0.9f))
                    .border(1.dp, PilotBorder, CircleShape)
                    .clickable {
                        mapViewportState.flyTo(
                            CameraOptions.Builder()
                                .center(driverPoint)
                                .zoom(15.5)
                                .pitch(40.0)
                                .bearing(headingDegrees)
                                .build()
                        )
                        onRecenterClicked?.invoke()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.GpsFixed,
                    contentDescription = "Recenter",
                    tint = PilotElectricCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Style Switcher Card
        AnimatedVisibility(
            visible = showStyleSwitcher,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 68.dp)
        ) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = PilotSurfaceElevated.copy(alpha = 0.96f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, PilotBorder),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Cockpit Map",
                        color = PilotTextDim,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    DriverStyleChip(
                        name = "🌙 Night Cockpit",
                        isSelected = selectedStyleUri == Style.DARK,
                        onClick = {
                            selectedStyleUri = Style.DARK
                            showStyleSwitcher = false
                        }
                    )
                    DriverStyleChip(
                        name = "☀️ Day Dispatch",
                        isSelected = selectedStyleUri == Style.MAPBOX_STREETS,
                        onClick = {
                            selectedStyleUri = Style.MAPBOX_STREETS
                            showStyleSwitcher = false
                        }
                    )
                    DriverStyleChip(
                        name = "🛰️ Satellite Corridors",
                        isSelected = selectedStyleUri == Style.SATELLITE_STREETS,
                        onClick = {
                            selectedStyleUri = Style.SATELLITE_STREETS
                            showStyleSwitcher = false
                        }
                    )
                    DriverStyleChip(
                        name = "🚦 Traffic Live",
                        isSelected = selectedStyleUri == Style.TRAFFIC_NIGHT,
                        onClick = {
                            selectedStyleUri = Style.TRAFFIC_NIGHT
                            showStyleSwitcher = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DriverStyleChip(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) PilotElectricCyan.copy(alpha = 0.2f) else Color.Transparent)
            .border(1.dp, if (isSelected) PilotElectricCyan else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = name,
            color = if (isSelected) PilotElectricCyan else PilotTextWhite,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun DriverPuckNode() {
    Box(
        modifier = Modifier
            .size(42.dp)
            .shadow(10.dp, CircleShape)
            .clip(CircleShape)
            .background(PilotSurfaceElevated)
            .border(2.5.dp, PilotElectricCyan, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "🛺", fontSize = 20.sp)
    }
}

@Composable
private fun TargetLocationNode(label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.offset(y = (-14).dp)
    ) {
        Box(
            modifier = Modifier
                .shadow(8.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(PilotSurface.copy(alpha = 0.95f))
                .border(1.dp, PilotOnlineGreen.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = "🎯 $label",
                color = PilotTextWhite,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(PilotOnlineGreen)
                .border(2.5.dp, Color.White, CircleShape)
        )
    }
}

@Preview
@Composable
fun DriverMapboxLiveViewPreview() {
    DriverGoTheme {
        DriverMapboxLiveView()
    }
}
