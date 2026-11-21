package com.vaisentra.mobility.driver.feature.telemetry

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.driver.components.*
import com.vaisentra.mobility.driver.data.DriverRepository
import com.vaisentra.mobility.driver.theme.*
import kotlinx.coroutines.launch

/**
 * Screen 11: High-Frequency GPS Ping (POST /api/v1/drivers/location)
 */
@Composable
fun GpsPingScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    var isPinging by remember { mutableStateOf(true) }
    var currentSpeedKmh by remember { mutableFloatStateOf(0f) }
    var currentHeadingDeg by remember { mutableIntStateOf(0) }
    var lastSyncStatus by remember { mutableStateOf("Ready to sync") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "High-Freq GPS Ping",
            subtitle = "Telemetry broadcaster & location sync",
            onBack = onNavigateBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                CockpitCard(glow = isPinging) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Live Telemetry Broadcaster",
                                color = CockpitTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                if (isPinging) "Active: $lastSyncStatus" else "Paused: Standby mode",
                                color = if (isPinging) EmeraldPrimary else AmberWarning,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = isPinging,
                            onCheckedChange = {
                                isPinging = it
                                onShowToast(if (it) "Telemetry streaming resumed" else "Telemetry paused")
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = EmeraldPrimary,
                                checkedTrackColor = EmeraldPrimary.copy(alpha = 0.3f)
                            )
                        )
                    }
                }
            }

            item {
                CockpitCard {
                    Text("GNSS & SENSOR TELEMETRY", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MetricBlock("SPEED", "${currentSpeedKmh.toInt()} km/h", ElectricCyan)
                        MetricBlock("BEARING", "${currentHeadingDeg}°")
                        MetricBlock("STATUS", if (isPinging) "ACTIVE" else "PAUSED", if (isPinging) EmeraldPrimary else AmberWarning)
                    }
                }
            }

            item {
                CockpitButton(
                    text = "Force Sync Telemetry Ping",
                    enabled = isPinging,
                    onClick = {
                        DriverRepository.pingLocation(28.6139, 77.2090, currentHeadingDeg.toDouble(), (currentSpeedKmh / 3.6))
                        lastSyncStatus = "Ping dispatched to /api/v1/drivers/location"
                        onShowToast(lastSyncStatus)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Screen 13: Heatmap & Surge Zones (GET /api/v1/drivers/heat-map)
 */
@Composable
fun HeatmapSurgeScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val clusters by DriverRepository.heatMapClusters.collectAsState()

    LaunchedEffect(Unit) {
        DriverRepository.fetchHeatMap()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Demand Heatmap & Surge",
            subtitle = "Live privacy-preserving demand clusters",
            onBack = onNavigateBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "LIVE DEMAND CLUSTERS (${clusters.size})",
                    color = CockpitTextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            if (clusters.isEmpty()) {
                item {
                    CockpitCard {
                        Text(
                            text = "No high-demand clusters detected in the last 2 hours.",
                            color = CockpitTextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                items(clusters) { cluster ->
                    val tint = when {
                        cluster.intensity >= 0.75 -> EmeraldPrimary
                        cluster.intensity >= 0.45 -> ElectricCyan
                        else -> AmberWarning
                    }
                    val level = when {
                        cluster.intensity >= 0.75 -> "High Demand"
                        cluster.intensity >= 0.45 -> "Medium Demand"
                        else -> "Moderate Demand"
                    }
                    CockpitCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Zone (${"%.2f".format(cluster.latitude)}, ${"%.2f".format(cluster.longitude)})",
                                    color = CockpitTextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "$level • ${cluster.count} ride requests",
                                    color = tint,
                                    fontSize = 12.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${"%.0f".format(cluster.intensity * 100)}%",
                                    color = tint,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Text("Intensity", color = CockpitTextSecondary, fontSize = 11.sp)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        CockpitButton(
                            text = "Navigate to Zone",
                            onClick = {
                                onShowToast("Routing to ${"%.2f".format(cluster.latitude)}, ${"%.2f".format(cluster.longitude)}")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            isOutlined = true
                        )
                    }
                }
            }
        }
    }
}

/**
 * Screen 14: Road Restrictions & Hazards (GET/DELETE /api/v1/location/road-restrictions & POST .../reroute)
 */
@Composable
fun RoadHazardsScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val restrictions by DriverRepository.roadRestrictions.collectAsState()
    val rideId by DriverRepository.currentRideId.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        DriverRepository.fetchRoadRestrictions()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Road Hazards & Alerts",
            subtitle = "Live corridor restrictions from backend",
            onBack = onNavigateBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (restrictions.isEmpty()) {
                item {
                    CockpitCard {
                        Text(
                            text = "No active road restrictions reported nearby.",
                            color = CockpitTextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                items(restrictions) { item ->
                    val badgeColor = when (item.restrictionType.uppercase()) {
                        "WATERLOGGING", "HAZARD" -> CrimsonAlert
                        "ROAD_CLOSURE", "CONSTRUCTION" -> AmberWarning
                        else -> ElectricCyan
                    }
                    CockpitCard {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            CockpitBadge(item.restrictionType, badgeColor)
                            Spacer(Modifier.height(6.dp))
                            Text(item.title, color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            if (item.description.isNotBlank()) {
                                Text(item.description, color = CockpitTextSecondary, fontSize = 12.sp)
                            }
                            Text(
                                "Radius: ${item.radiusMeters.toInt()}m (${"%.4f".format(item.latitude)}, ${"%.4f".format(item.longitude)})",
                                color = CockpitTextMuted,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CockpitButton(
                                text = "Avoid Route",
                                onClick = {
                                    scope.launch {
                                        val res = DriverRepository.rerouteActiveRide(rideId, "Avoiding ${item.title}")
                                        if (res.isSuccess) {
                                            onShowToast("Rerouted avoiding ${item.title}")
                                        } else {
                                            onShowToast(res.exceptionOrNull()?.message ?: "Reroute unavailable")
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                isOutlined = true
                            )
                            CockpitButton(
                                text = "Report Cleared",
                                onClick = {
                                    scope.launch {
                                        val res = DriverRepository.clearRoadRestriction(item.id)
                                        if (res.isSuccess) {
                                            onShowToast("Restriction cleared")
                                        } else {
                                            onShowToast("Failed to clear: ${res.exceptionOrNull()?.message}")
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                isOutlined = true
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricBlock(label: String, value: String, valueColor: Color = CockpitTextPrimary) {
    Column {
        Text(label, color = CockpitTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}