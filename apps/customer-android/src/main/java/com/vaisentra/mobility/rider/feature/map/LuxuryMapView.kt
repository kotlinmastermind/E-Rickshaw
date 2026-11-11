package com.vaisentra.mobility.rider.feature.map

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.core.map.AppMapDetailsCatalog
import com.vaisentra.mobility.core.map.LatLng
import com.vaisentra.mobility.core.map.MapboxCoordinate
import com.vaisentra.mobility.core.map.MapboxSharedConfig
import com.vaisentra.mobility.rider.core.network.ApiClient
import com.vaisentra.mobility.rider.core.network.IsochroneRequest
import com.vaisentra.mobility.rider.core.network.NearbyVehicleDto
import kotlinx.coroutines.launch

/**
 * Modern Clean Luxury Map & Navigation UI Component for Customer App.
 * Powered 100% by native Mapbox Maps SDK v11.
 * - Mapbox Native Vector View rendered directly with high-performance vector tiles.
 * - Interactive Map Overlays: Recenter FAB, Traffic Layer Toggle, Map Type Switcher.
 * - Smooth dynamic routing with Mapbox Directions polyline and markers.
 */
@Composable
fun LuxuryMapView(
    modifier: Modifier = Modifier,
    pickupLatLng: LatLng? = null,
    pickupTitle: String = "Pickup Point",
    destLatLng: LatLng? = null,
    destTitle: String? = null,
    driverLatLng: LatLng? = null,
    nearbyDrivers: List<LatLng> = emptyList(),
    nearbyVehicles: List<NearbyVehicleDto> = emptyList(),
    routePoints: List<LatLng> = emptyList(),
    routeDistanceKm: Double? = null,
    routeDurationMinutes: Double? = null,
    zoomLevel: Float = 16f,
    recenterTrigger: Int = 0,
    cameraTargetLatLng: LatLng? = null,
    cameraAnimTrigger: Int = 0,
    cameraAnimType: String = "flyTo",
    isLightMode: Boolean = true,
    onVehicleClick: ((NearbyVehicleDto) -> Unit)? = null,
    onMapClick: ((LatLng) -> Unit)? = null,
    onCameraIdle: ((LatLng) -> Unit)? = null
) {
    val context = LocalContext.current
    val effectivePickup = pickupLatLng 
        ?: com.vaisentra.mobility.core.map.DeviceLocationProvider.getLastKnownLocation(context)?.let { LatLng(it.latitude, it.longitude) }
        ?: LatLng(0.0, 0.0)
    var selectedVehiclePopup by remember { mutableStateOf<NearbyVehicleDto?>(null) }
    var currentMapLayer by remember { mutableStateOf(if (isLightMode) "LIGHT" else "DARK") } // "LIGHT", "DARK", "SATELLITE", "TERRAIN"
    var mapDetailsState by remember { mutableStateOf(AppMapDetailsCatalog.getDefaultMap()) }
    var internalRecenterCount by remember { mutableIntStateOf(recenterTrigger) }

    // Map Controls State
    val scope = rememberCoroutineScope()
    var isTrafficEnabled by remember { mutableStateOf(true) }
    var showIsochroneRadius by remember { mutableStateOf(false) }
    var isochroneLoading by remember { mutableStateOf(false) }

    // Fetch dynamic Isochrone reachable polygon when toggled
    LaunchedEffect(showIsochroneRadius, effectivePickup) {
        if (showIsochroneRadius) {
            isochroneLoading = true
            try {
                ApiClient.getService().getIsochrone(
                    IsochroneRequest(effectivePickup.latitude, effectivePickup.longitude, 15)
                )
            } catch (_: Exception) {
            } finally {
                isochroneLoading = false
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Native Mapbox Map Core View
        MapboxCustomerMapView(
            modifier = Modifier.fillMaxSize(),
            pickupLatLng = effectivePickup,
            pickupTitle = pickupTitle,
            destLatLng = destLatLng,
            destTitle = destTitle,
            driverLatLng = driverLatLng,
            nearbyVehicles = nearbyVehicles,
            routePoints = routePoints,
            mapLayer = currentMapLayer,
            mapDetails = mapDetailsState,
            recenterTrigger = internalRecenterCount + recenterTrigger,
            cameraTargetLatLng = cameraTargetLatLng,
            cameraAnimTrigger = cameraAnimTrigger,
            cameraAnimType = cameraAnimType,
            zoomLevel = zoomLevel,
            mapboxToken = MapboxSharedConfig.DEFAULT_PUBLIC_TOKEN,
            onMapClick = { clickedPos ->
                selectedVehiclePopup = null
                onMapClick?.invoke(clickedPos)
            },
            onCameraMoved = onCameraIdle,
            onVehicleClick = { veh ->
                selectedVehiclePopup = veh
                onVehicleClick?.invoke(veh)
            }
        )

        // 2. Top-Right Floating Controls (Recenter, Layer Switcher, Traffic)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End
        ) {
            // Recenter Button
            FloatingActionButton(
                onClick = {
                    internalRecenterCount++
                    onCameraIdle?.invoke(effectivePickup)
                },
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                containerColor = Color(0xFF1E293B),
                contentColor = Color(0xFFF1F5F9),
                elevation = FloatingActionButtonDefaults.elevation(6.dp)
            ) {
                Text("🎯", fontSize = 18.sp)
            }

            // Layer Switcher (Light / Dark / Satellite)
            FloatingActionButton(
                onClick = {
                    currentMapLayer = when (currentMapLayer) {
                        "LIGHT" -> "DARK"
                        "DARK" -> "SATELLITE"
                        "SATELLITE" -> "TERRAIN"
                        else -> "LIGHT"
                    }
                },
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                containerColor = Color(0xFF1E293B),
                contentColor = Color(0xFFF1F5F9),
                elevation = FloatingActionButtonDefaults.elevation(6.dp)
            ) {
                Text("🗺️", fontSize = 18.sp)
            }

            // Traffic Toggle
            FloatingActionButton(
                onClick = {
                    isTrafficEnabled = !isTrafficEnabled
                    mapDetailsState = mapDetailsState.toMutableMap().apply {
                        this["traffic"] = isTrafficEnabled
                    }
                },
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                containerColor = if (isTrafficEnabled) Color(0xFF0F8A4E) else Color(0xFF1E293B),
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(6.dp)
            ) {
                Text("🚦", fontSize = 18.sp)
            }
        }

        // 3. Floating Route Stats Pill (if route is active)
        if (routeDistanceKm != null && routeDistanceKm > 0 && routeDurationMinutes != null) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF0F172A).copy(alpha = 0.95f),
                border = BorderStroke(1.dp, Color(0xFF10B981)),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("⚡", fontSize = 14.sp)
                    Text(
                        text = "%.1f km".format(routeDistanceKm),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text("•", color = Color(0xFF64748B), fontSize = 12.sp)
                    Text(
                        text = "${routeDurationMinutes.toInt()} mins",
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // 4. Vehicle Selection Popup Card
        selectedVehiclePopup?.let { vehicle ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E293B),
                border = BorderStroke(1.dp, Color(0xFFD4AF37)),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF0F172A),
                        border = BorderStroke(1.5.dp, Color(0xFFD4AF37)),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("🛺", fontSize = 20.sp)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = vehicle.modelName.ifBlank { "Electric Rickshaw" },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Driver: ${vehicle.driverName.ifBlank { "Verified Pilot" }} • Rating: ★ ${vehicle.rating}",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = { selectedVehiclePopup = null }) {
                        Text("✕", color = Color(0xFF94A3B8), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
