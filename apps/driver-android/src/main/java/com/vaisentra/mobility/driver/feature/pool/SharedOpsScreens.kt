package com.vaisentra.mobility.driver.feature.pool

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
 * Screen 24: Shared Pool Passenger Manifest (passenger seat allocation, names, stops)
 */
@Composable
fun PoolManifestScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val seats = listOf(
        SeatManifestItem("Seat #1 (Front)", "Kavita S.", "Mayur Vihar Ph-1 Metro", "Preet Vihar Pillar 12", true, "₹25.00"),
        SeatManifestItem("Seat #2 (Rear Left)", "Rohit Kumar", "Mayur Vihar Ph-1 Metro", "Nirman Vihar Metro", true, "₹30.00"),
        SeatManifestItem("Seat #3 (Rear Center)", "Deepak Joshi", "Laxmi Nagar Chowk", "Anand Vihar ISBT", false, "₹35.00"),
        SeatManifestItem("Seat #4 (Rear Right)", "EMPTY SEAT", "-", "-", false, "Matching...")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Shared Pool Manifest",
            subtitle = "Eco-Pool 4-passenger manifest & seat allocations",
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
                            Text("TOTAL POOL FARE EARNING", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("₹90.00 / trip", color = EmeraldPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        CockpitBadge("3 / 4 SEATS OCCUPIED", ElectricCyan)
                    }
                }
            }

            items(seats) { seat ->
                CockpitCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(seat.seatNumber, color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(seat.commuterName, color = if (seat.isBoarded) CockpitTextPrimary else CockpitTextSecondary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("Drop: ${seat.dropLocation}", color = CockpitTextSecondary, fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(seat.fare, color = if (seat.isBoarded) EmeraldPrimary else CockpitTextMuted, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(Modifier.height(4.dp))
                            CockpitBadge(
                                if (seat.isBoarded) "BOARDED" else if (seat.commuterName != "EMPTY SEAT") "PENDING PICKUP" else "VACANT",
                                if (seat.isBoarded) EmeraldPrimary else if (seat.commuterName != "EMPTY SEAT") AmberWarning else CockpitTextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Screen 25: Corridor Waypoint Management (pickup/drop sequence on corridor)
 */
@Composable
fun CorridorWaypointsScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val corridorSteps = listOf(
        CorridorStep(1, "PICKUP", "Mayur Vihar Metro Gate 2", "Commuter: Kavita S.", true),
        CorridorStep(2, "PICKUP", "Mayur Vihar Metro Gate 2", "Commuter: Rohit Kumar", true),
        CorridorStep(3, "DROP", "Preet Vihar Pillar 12", "Commuter: Kavita S.", false),
        CorridorStep(4, "PICKUP", "Laxmi Nagar Chowk", "Commuter: Deepak Joshi", false),
        CorridorStep(5, "DROP", "Nirman Vihar Metro", "Commuter: Rohit Kumar", false),
        CorridorStep(6, "DROP", "Anand Vihar ISBT", "Commuter: Deepak Joshi", false)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Corridor Sequence",
            subtitle = "Optimized routing order for all pool riders",
            onBack = onNavigateBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(corridorSteps) { step ->
                CockpitCard(glow = !step.done && step.stepIndex == 3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(if (step.done) EmeraldPrimary else if (step.type == "PICKUP") ElectricCyan else AmberWarning),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${step.stepIndex}", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                CockpitBadge(step.type, if (step.type == "PICKUP") ElectricCyan else AmberWarning)
                                Spacer(Modifier.height(2.dp))
                                Text(step.location, color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(step.passenger, color = CockpitTextSecondary, fontSize = 11.sp)
                            }
                        }
                        if (step.done) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = EmeraldPrimary)
                        } else if (step.stepIndex == 3) {
                            CockpitButton(
                                text = "Arrived",
                                onClick = { onShowToast("Arrived at Preet Vihar Pillar 12") }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Screen 26: Co-Passenger Broadcast (send announcement to corridor riders)
 */
@Composable
fun CoPassengerBroadcastScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    var broadcastMsg by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Co-Passenger Broadcast",
            subtitle = "Send announcement to all 4 pool riders at once",
            onBack = onNavigateBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CockpitCard {
                    Text("QUICK ANNOUNCEMENT PRESETS", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    val presets = listOf(
                        "Delayed by 3 mins due to traffic jam at flyover",
                        "Arriving at Metro Gate in 60 seconds. Please be ready!",
                        "Heavy rain: Moving slowly for passenger safety",
                        "Vehicle battery swap in progress (2 mins delay)"
                    )
                    presets.forEach { preset ->
                        Button(
                            onClick = {
                                broadcastMsg = preset
                                onShowToast("Broadcast: '$preset' sent to all riders!")
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CockpitSurfaceHighlight),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(preset, color = CockpitTextPrimary, fontSize = 12.sp)
                        }
                    }
                }

                CockpitTextField(
                    value = broadcastMsg,
                    onValueChange = { broadcastMsg = it },
                    label = "Custom Announcement",
                    placeholder = "Type message for in-trip riders..."
                )
            }

            CockpitButton(
                text = "SEND BROADCAST NOTIFICATION",
                onClick = {
                    if (broadcastMsg.isNotBlank()) {
                        onShowToast("Notification broadcasted to 4 riders")
                        broadcastMsg = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private data class SeatManifestItem(
    val seatNumber: String,
    val commuterName: String,
    val pickupLocation: String,
    val dropLocation: String,
    val isBoarded: Boolean,
    val fare: String
)

private data class CorridorStep(
    val stepIndex: Int,
    val type: String,
    val location: String,
    val passenger: String,
    val done: Boolean
)

