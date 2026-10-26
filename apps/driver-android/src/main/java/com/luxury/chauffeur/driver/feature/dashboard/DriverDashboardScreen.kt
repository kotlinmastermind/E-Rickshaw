package com.luxury.chauffeur.driver.feature.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luxury.chauffeur.driver.core.designsystem.*
import com.luxury.chauffeur.driver.core.network.DriverApiClient
import com.luxury.chauffeur.driver.core.network.DriverAvailabilityPayload
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DriverDashboardScreen(
    onAcceptRide: (rideId: String, pickup: String, dest: String) -> Unit,
    onNavigateDocuments: () -> Unit,
    onNavigateEarnings: () -> Unit,
    onNavigateProfile: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isOnline by remember { mutableStateOf(true) }
    var incomingRideOffer by remember { mutableStateOf<Map<String, String>?>(null) }
    var offerCountdown by remember { mutableIntStateOf(15) }

    // Sync online/offline status with backend & poll/simulate offers
    LaunchedEffect(isOnline) {
        try {
            DriverApiClient.getService().setAvailability(DriverAvailabilityPayload(isOnline))
        } catch (_: Exception) {}

        if (isOnline) {
            delay(4000)
            incomingRideOffer = mapOf(
                "ride_id" to "ride_lux_9941",
                "pickup" to "The Taj Mahal Palace, Colaba",
                "destination" to "CSM T2 VIP Private Terminal",
                "fare" to "₹1,600",
                "distance" to "24.8 km"
            )
            offerCountdown = 15
            while (offerCountdown > 0 && incomingRideOffer != null) {
                delay(1000)
                offerCountdown--
            }
            if (offerCountdown == 0) {
                incomingRideOffer = null // Rejection / Expiration timeout
            }
        } else {
            incomingRideOffer = null
        }
    }

    Scaffold(
        containerColor = DriverColors.MidnightBlack,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("CHAUFFEUR COCKPIT", style = DriverTypography.Title.copy(fontSize = 18.sp, color = DriverColors.ChauffeurGold))
                    Text("Mercedes-Benz E-Class • MH01-LX-0001", style = DriverTypography.Body.copy(fontSize = 12.sp, color = DriverColors.TextMuted))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onNavigateEarnings) {
                        Text("Earnings", color = DriverColors.TextPure)
                    }
                    TextButton(onClick = onNavigateProfile) {
                        Text("Profile", color = DriverColors.TextPure)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                // Online/Offline Availability Switch Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DriverColors.SurfaceCard,
                    border = BorderStroke(1.dp, DriverColors.SurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(
                                        color = if (isOnline) DriverColors.StatusOnline else DriverColors.StatusOffline,
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isOnline) "ONLINE & AVAILABLE" else "OFFLINE",
                                    style = DriverTypography.Subtitle.copy(
                                        color = if (isOnline) DriverColors.StatusOnline else DriverColors.StatusOffline,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = if (isOnline) "Dispatch matching enabled" else "Switch on to receive dispatches",
                                    style = DriverTypography.Body.copy(fontSize = 12.sp, color = DriverColors.TextMuted)
                                )
                            }
                        }

                        Switch(
                            checked = isOnline,
                            onCheckedChange = { isOnline = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DriverColors.MidnightBlack,
                                checkedTrackColor = DriverColors.StatusOnline,
                                uncheckedThumbColor = DriverColors.TextMuted,
                                uncheckedTrackColor = DriverColors.SurfaceBorder
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // KPI Overview
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DriverColors.CockpitDark,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("TODAY'S EARNINGS", style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("₹4,800", style = DriverTypography.MetricValue)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DriverColors.CockpitDark,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("RATING", style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("★ 4.98", style = DriverTypography.MetricValue.copy(color = DriverColors.TextPure))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Feature 3: Ride Request Popup with Countdown Timer
                AnimatedVisibility(visible = incomingRideOffer != null) {
                    val offer = incomingRideOffer ?: return@AnimatedVisibility
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = DriverColors.SurfaceCard,
                        border = BorderStroke(2.dp, DriverColors.ChauffeurGold),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("NEW VIP DISPATCH OFFER", style = DriverTypography.Subtitle.copy(fontWeight = FontWeight.Bold))
                                Surface(
                                    shape = CircleShape,
                                    color = DriverColors.ChauffeurGold
                                ) {
                                    Text(
                                        text = "${offerCountdown}s",
                                        color = DriverColors.MidnightBlack,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text("PICKUP", style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted))
                            Text(offer["pickup"] ?: "", style = DriverTypography.Body.copy(fontWeight = FontWeight.SemiBold))

                            Spacer(modifier = Modifier.height(8.dp))
                            Text("DESTINATION", style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted))
                            Text(offer["destination"] ?: "", style = DriverTypography.Body.copy(fontWeight = FontWeight.SemiBold))

                            Spacer(modifier = Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Guaranteed Payout: ${offer["fare"]}", style = DriverTypography.Subtitle)
                                Text("Est: ${offer["distance"]}", style = DriverTypography.Body.copy(color = DriverColors.TextMuted))
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Feature 4: Accept / Reject Action Controls
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { incomingRideOffer = null },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DriverColors.SurfaceBorder,
                                        contentColor = DriverColors.AlertDanger
                                    ),
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) {
                                    Text("REJECT", fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        val rideId = offer["ride_id"] ?: ""
                                        val pick = offer["pickup"] ?: ""
                                        val dest = offer["destination"] ?: ""
                                        incomingRideOffer = null
                                        scope.launch {
                                            try {
                                                DriverApiClient.getService().acceptRide(rideId)
                                            } catch (_: Exception) {}
                                            onAcceptRide(rideId, pick, dest)
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DriverColors.ChauffeurGold,
                                        contentColor = DriverColors.MidnightBlack
                                    ),
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) {
                                    Text("ACCEPT TRIP", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                Button(
                    onClick = onNavigateDocuments,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DriverColors.SurfaceCard,
                        contentColor = DriverColors.TextPure
                    ),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("COMPLIANCE & DOCUMENTS")
                }
            }
        }
    }
}
