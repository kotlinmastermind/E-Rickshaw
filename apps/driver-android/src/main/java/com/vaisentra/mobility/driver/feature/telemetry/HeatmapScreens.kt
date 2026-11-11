package com.vaisentra.mobility.driver.feature.telemetry

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.driver.components.*
import com.vaisentra.mobility.driver.theme.*

/**
 * Screen 11: High-Frequency GPS Ping (5-second telemetry, bearing, anti-spoof)
 */
@Composable
fun GpsPingScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    var isPinging by remember { mutableStateOf(true) }
    var pingIntervalSec by remember { mutableIntStateOf(5) }
    var currentSpeedKmh by remember { mutableFloatStateOf(24.5f) }
    var currentHeadingDeg by remember { mutableIntStateOf(142) }
    var gpsAccuracyMeters by remember { mutableFloatStateOf(1.8f) }
    var antiSpoofVerified by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "High-Freq GPS Ping",
            subtitle = "5-sec telemetry, dead-reckoning & anti-spoof",
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
                                if (isPinging) "Active: 5s WebSocket stream to Supabase" else "Paused: Standby mode",
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
                        MetricBlock("LATITUDE", "28.6139° N")
                        MetricBlock("LONGITUDE", "77.2090° E")
                        MetricBlock("ACCURACY", "±${gpsAccuracyMeters}m", EmeraldPrimary)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MetricBlock("SPEED", "${currentSpeedKmh.toInt()} km/h", ElectricCyan)
                        MetricBlock("BEARING", "${currentHeadingDeg}° SE")
                        MetricBlock("ALTITUDE", "216m MSL")
                    }
                }
            }

            item {
                CockpitCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (antiSpoofVerified) Icons.Default.VerifiedUser else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (antiSpoofVerified) EmeraldPrimary else CrimsonAlert,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                if (antiSpoofVerified) "Anti-Spoofing: Hardware Verified" else "GPS Mocking Detected!",
                                color = CockpitTextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                "Fused cell-tower + gyroscope + mock-provider sentinel",
                                color = CockpitTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            item {
                CockpitButton(
                    text = "Force Sync Telemetry Ping",
                    onClick = {
                        onShowToast("Ping dispatched to FastAPI /driver/telemetry: 200 OK")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Screen 13: Heatmap & Surge Zones (demand clusters, 1.3x to 1.8x multipliers)
 */
@Composable
fun HeatmapSurgeScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val surgeZones = listOf(
        SurgeZoneItem("Anand Vihar ISBT & Metro", "1.8x", "+₹65/trip", "Extreme Demand", EmeraldPrimary, 42),
        SurgeZoneItem("Connaught Place Outer Circle", "1.5x", "+₹40/trip", "High Demand", ElectricCyan, 28),
        SurgeZoneItem("Laxmi Nagar Metro Pillar 40", "1.4x", "+₹30/trip", "Medium Demand", AmberWarning, 19),
        SurgeZoneItem("Noida Sector 18 Wave Mall", "1.3x", "+₹20/trip", "Building Surge", CockpitTextSecondary, 12),
        SurgeZoneItem("Akshardham Temple Corridor", "1.2x", "+₹15/trip", "Moderate", CockpitTextMuted, 8)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Demand Heatmap & Surge",
            subtitle = "Real-time dynamic pricing clusters & demand corridors",
            onBack = onNavigateBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                CockpitCard(glow = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("CURRENT SECTOR", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("East Delhi Sector 7 (IP Extn)", color = CockpitTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        CockpitBadge("1.4x Multiplier", AmberWarning)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Heading 800m East to Anand Vihar will unlock 1.8x peak multiplier (+₹65 bonus)",
                        color = CockpitTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            item {
                Text(
                    "HOTSPOT SURGE ZONES",
                    color = CockpitTextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            items(surgeZones) { zone ->
                CockpitCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(zone.name, color = CockpitTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Spacer(Modifier.height(2.dp))
                            Text("${zone.intensity} • ${zone.tripsWaiting} requests in cue", color = zone.tint, fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(zone.multiplier, color = zone.tint, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(zone.bonus, color = CockpitTextSecondary, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    CockpitButton(
                        text = "Navigate to Zone",
                        onClick = { onShowToast("Routing navigation to ${zone.name}") },
                        modifier = Modifier.fillMaxWidth(),
                        isOutlined = true
                    )
                }
            }
        }
    }
}

/**
 * Screen 14: Road Restrictions & Hazards (municipal advisories, road works, waterlogging)
 */
@Composable
fun RoadHazardsScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val hazards = listOf(
        HazardItem("Waterlogging", "Vikas Marg near Preet Vihar underpass", "Severe (slow down to <10 km/h)", CrimsonAlert),
        HazardItem("Road Construction", "Gazipur Flyover slip lane 2", "1 Lane Closed (15m delay)", AmberWarning),
        HazardItem("VIP Movement", "India Gate C-Hexagon to Barakhamba", "Diversion in effect till 19:30", ElectricCyan),
        HazardItem("Protest / Chokepoint", "ITO Intersection Metro Gate 3", "Congestion: 5 km/h creep", AmberWarning)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Road Hazards & Alerts",
            subtitle = "Delhi Traffic Police live corridor advisories",
            onBack = onNavigateBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                CockpitCard(glow = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ReportProblem, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Active Pilot Advisory", color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Monsoon rain protocol active: High regenerative brake recommended", color = CockpitTextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }

            items(hazards) { item ->
                CockpitCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            CockpitBadge(item.type, item.color)
                            Spacer(Modifier.height(6.dp))
                            Text(item.location, color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(item.impact, color = CockpitTextSecondary, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CockpitButton(
                            text = "Avoid Route",
                            onClick = { onShowToast("Auto-reroute applied avoiding ${item.location}") },
                            modifier = Modifier.weight(1f),
                            isOutlined = true
                        )
                        CockpitButton(
                            text = "Report Cleared",
                            onClick = { onShowToast("Hazard feedback submitted") },
                            modifier = Modifier.weight(1f),
                            isOutlined = true
                        )
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

private data class SurgeZoneItem(
    val name: String,
    val multiplier: String,
    val bonus: String,
    val intensity: String,
    val tint: Color,
    val tripsWaiting: Int
)

private data class HazardItem(
    val type: String,
    val location: String,
    val impact: String,
    val color: Color
)

