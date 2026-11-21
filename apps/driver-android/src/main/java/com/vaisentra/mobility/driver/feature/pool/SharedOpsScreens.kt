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
import com.vaisentra.mobility.driver.data.DriverRepository
import com.vaisentra.mobility.driver.theme.*
import kotlinx.coroutines.launch

/**
 * Screen 24: Shared Pool Passenger Manifest (GET /api/v1/pools/ride/{ride_id})
 */
@Composable
fun PoolManifestScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val rideId by DriverRepository.currentRideId.collectAsState()
    val sharedTrip by DriverRepository.sharedPoolTrip.collectAsState()

    LaunchedEffect(rideId) {
        if (rideId.isNotBlank()) {
            DriverRepository.fetchSharedTripForRide(rideId)
        }
    }

    val manifest = sharedTrip?.manifest.orEmpty()
    val totalPoolEarningsRupees = (sharedTrip?.financialSummary?.totalDriverEarningsCents ?: 0L) / 100.0
    val occupiedSeats = sharedTrip?.reservedSeats ?: 0
    val maxSeats = sharedTrip?.maxSeats ?: 4

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Shared Pool Manifest",
            subtitle = "Eco-Pool passenger manifest & seat allocations",
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
                            Text("₹${"%.2f".format(totalPoolEarningsRupees)} / trip", color = EmeraldPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        CockpitBadge("$occupiedSeats / $maxSeats SEATS OCCUPIED", ElectricCyan)
                    }
                }
            }

            if (manifest.isEmpty()) {
                item {
                    CockpitCard {
                        Text(
                            text = if (rideId.isBlank()) "No active shared ride." else "No pool passengers in manifest yet.",
                            color = CockpitTextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                items(manifest) { entry ->
                    val fareRupees = (entry.driverEarningsCents ?: entry.allocatedFareCents ?: 0L) / 100.0
                    val isBoarded = entry.bookingStatus in listOf("IN_TRANSIT", "ACTIVE_BOARDING", "COMPLETED")
                    CockpitCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Booking ${entry.bookingId?.take(8) ?: "-"}", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("${entry.seatsReserved} Seat(s) Reserved", color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("₹${"%.2f".format(fareRupees)}", color = EmeraldPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(Modifier.height(4.dp))
                                CockpitBadge(
                                    entry.bookingStatus,
                                    if (isBoarded) EmeraldPrimary else AmberWarning
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Screen 25: Corridor Waypoint Management (GET /api/v1/pools/ride/{ride_id} & /api/v1/rides/{ride_id}/stops)
 */
@Composable
fun CorridorWaypointsScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val rideId by DriverRepository.currentRideId.collectAsState()
    val sharedTrip by DriverRepository.sharedPoolTrip.collectAsState()
    val rideStops by DriverRepository.rideStops.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(rideId) {
        if (rideId.isNotBlank()) {
            DriverRepository.fetchSharedTripForRide(rideId)
            DriverRepository.fetchRideStops(rideId)
        }
    }

    val poolStops = sharedTrip?.stops.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Corridor Sequence",
            subtitle = "Routing order for active ride stops",
            onBack = onNavigateBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (poolStops.isNotEmpty()) {
                items(poolStops) { step ->
                    val done = step.status in listOf("COMPLETED", "ARRIVED", "DEPARTED")
                    CockpitCard(glow = !done) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(if (done) EmeraldPrimary else if (step.stopType == "PICKUP") ElectricCyan else AmberWarning),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("${step.sequenceOrder}", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    CockpitBadge(step.stopType, if (step.stopType == "PICKUP") ElectricCyan else AmberWarning)
                                    Spacer(Modifier.height(2.dp))
                                    Text(step.address, color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Status: ${step.status}", color = CockpitTextSecondary, fontSize = 11.sp)
                                }
                            }
                            if (done) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = EmeraldPrimary)
                            }
                        }
                    }
                }
            } else if (rideStops.isNotEmpty()) {
                items(rideStops) { stop ->
                    val done = stop.status in listOf("COMPLETED", "ARRIVED", "DEPARTED")
                    CockpitCard(glow = !done) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(if (done) EmeraldPrimary else ElectricCyan),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("${stop.sequenceOrder}", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(stop.address, color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Status: ${stop.status}", color = CockpitTextSecondary, fontSize = 11.sp)
                                }
                            }
                            if (done) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = EmeraldPrimary)
                            } else {
                                CockpitButton(
                                    text = "Arrived",
                                    onClick = {
                                        scope.launch {
                                            val res = DriverRepository.markRideStopArrived(rideId, stop.id)
                                            if (res.isSuccess) onShowToast("Arrived at ${stop.address}")
                                            else onShowToast("Failed to update stop: ${res.exceptionOrNull()?.message}")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    CockpitCard {
                        Text(
                            text = if (rideId.isBlank()) "No active ride." else "No intermediate stops for this ride.",
                            color = CockpitTextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Screen 26: Co-Passenger Broadcast (POST /api/v1/pools/{trip_id}/messages or /api/v1/rides/{ride_id}/chat/messages)
 */
@Composable
fun CoPassengerBroadcastScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val rideId by DriverRepository.currentRideId.collectAsState()
    val sharedTrip by DriverRepository.sharedPoolTrip.collectAsState()
    var broadcastMsg by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(rideId) {
        if (rideId.isNotBlank()) {
            DriverRepository.fetchSharedTripForRide(rideId)
        }
    }

    fun sendAnnouncement(text: String) {
        if (text.isBlank()) return
        if (rideId.isBlank() && sharedTrip?.tripId.isNullOrBlank()) {
            onShowToast("No active ride or pool trip.")
            return
        }
        isSending = true
        scope.launch {
            val res = DriverRepository.sendCoPassengerBroadcast(sharedTrip?.tripId, rideId, text)
            isSending = false
            if (res.isSuccess) {
                broadcastMsg = ""
                onShowToast("Broadcast sent!")
            } else {
                onShowToast("Failed to send: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Co-Passenger Broadcast",
            subtitle = "Send announcement to active trip riders",
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
                            enabled = !isSending,
                            onClick = { sendAnnouncement(preset) },
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
                text = if (isSending) "SENDING..." else "SEND BROADCAST NOTIFICATION",
                enabled = !isSending && broadcastMsg.isNotBlank(),
                onClick = { sendAnnouncement(broadcastMsg) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}