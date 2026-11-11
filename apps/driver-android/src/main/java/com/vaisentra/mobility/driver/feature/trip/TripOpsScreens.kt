package com.vaisentra.mobility.driver.feature.trip

import androidx.compose.animation.core.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.driver.components.*
import com.vaisentra.mobility.driver.theme.*

/**
 * Screen 15: Incoming Ride Offer Card (full countdown view)
 */
@Composable
fun IncomingOfferScreen(
    onNavigateBack: () -> Unit = {},
    onAcceptOffer: () -> Unit = {},
    onRejectOffer: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    var secondsLeft by remember { mutableIntStateOf(15) }

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            kotlinx.coroutines.delay(1000)
            secondsLeft--
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Incoming Ride Offer",
            subtitle = "Smart dispatch match for your corridor",
            onBack = onNavigateBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            CockpitCard(glow = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CockpitBadge("E-RICKSHAW STANDARD", ElectricCyan)
                    Text("Auto-Reject in ${secondsLeft}s", color = CrimsonAlert, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("ESTIMATED NET PAY", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("₹82.50", color = EmeraldPrimary, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("DISTANCE & TIME", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("4.2 km • 14 min", color = CockpitTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = CockpitBorder)
                Spacer(Modifier.height(16.dp))

                // Route breakdown
                Row(verticalAlignment = Alignment.Top) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 4.dp)) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(EmeraldPrimary))
                        Box(Modifier.width(2.dp).height(38.dp).background(CockpitBorder))
                        Box(Modifier.size(10.dp).clip(CircleShape).background(ElectricCyan))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column {
                            Text("PICKUP (0.6 km away)", color = CockpitTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("Mayur Vihar Ph-1 Metro Gate 2", color = CockpitTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Column {
                            Text("DROP", color = CockpitTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("Pocket 1 Market, Mayur Vihar", color = CockpitTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Payment: UPI Digital Auto-Pay", color = CockpitTextSecondary, fontSize = 12.sp)
                    Text("Rider: Priya S. (4.9★)", color = CockpitTextSecondary, fontSize = 12.sp)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CockpitButton(
                    text = "ACCEPT RIDE OFFER (₹82.50)",
                    onClick = {
                        onShowToast("Offer Accepted! Route initialized.")
                        onAcceptOffer()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                CockpitButton(
                    text = "PASS / REJECT",
                    onClick = {
                        onShowToast("Offer passed")
                        onRejectOffer()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    isOutlined = true
                )
            }
        }
    }
}

/**
 * Screen 16: Auto-Dispatch Offer Queue (continuous trip matching)
 */
@Composable
fun OfferQueueScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    var autoAcceptPool by remember { mutableStateOf(true) }
    var maxQueueDepth by remember { mutableIntStateOf(3) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Auto-Dispatch Queue",
            subtitle = "Zero-idle continuous corridor trip chaining",
            onBack = onNavigateBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                CockpitCard(glow = autoAcceptPool) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Continuous Chained Dispatch", color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Match next ride before current trip drops off", color = CockpitTextSecondary, fontSize = 12.sp)
                        }
                        Switch(
                            checked = autoAcceptPool,
                            onCheckedChange = {
                                autoAcceptPool = it
                                onShowToast(if (it) "Chained dispatch active" else "Chained dispatch paused")
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
                Text("QUEUED UP NEXT", color = CockpitTextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            item {
                CockpitCard {
                    CockpitBadge("CHAINED TRIP #1 (AUTO-MATCHED)", EmeraldPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text("Pickup: Noida Sec 15 Metro Station Gate 1", color = CockpitTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Drop: Atta Market Sector 18", color = CockpitTextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Est. Net: ₹45.00", color = EmeraldPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Start: In ~8 min (after drop)", color = ElectricCyan, fontSize = 12.sp)
                    }
                }
            }

            item {
                CockpitCard {
                    CockpitBadge("CHAINED TRIP #2 (STANDBY)", AmberWarning)
                    Spacer(Modifier.height(8.dp))
                    Text("Pickup: Great India Place Mall Drop Gate", color = CockpitTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Drop: Botanical Garden Metro", color = CockpitTextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Est. Net: ₹52.00", color = EmeraldPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Start: In ~22 min", color = CockpitTextSecondary, fontSize = 12.sp)
                    }
                }
            }

            item {
                CockpitButton(
                    text = "Clear Standby Queue",
                    onClick = { onShowToast("Standby queue cleared") },
                    modifier = Modifier.fillMaxWidth(),
                    isOutlined = true
                )
            }
        }
    }
}

/**
 * Screen 17: Navigation to Pickup Spot (turn-by-turn with traffic avoidance)
 */
@Composable
fun PickupNavScreen(
    onNavigateBack: () -> Unit = {},
    onArrived: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "En-Route to Pickup",
            subtitle = "Navigating to passenger pickup location",
            onBack = onNavigateBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Navigation instruction card
                CockpitCard(glow = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TurnRight, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("In 120 meters", color = ElectricCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Turn right onto Metro Feeder Corridor Road", color = CockpitTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("ETA: 3 mins • 700m remaining", color = CockpitTextSecondary, fontSize = 12.sp)
                        Text("Target: Gate 2", color = EmeraldPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Passenger details card
                CockpitCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Rider: Ankit Verma", color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Pickup: Metro Gate 2 Pillar 28", color = CockpitTextSecondary, fontSize = 12.sp)
                        }
                        IconButton(
                            onClick = { onShowToast("Dialing passenger via masked proxy...") },
                            modifier = Modifier.clip(CircleShape).background(EmeraldPrimary.copy(alpha = 0.2f))
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = "Call", tint = EmeraldPrimary)
                        }
                    }
                }
            }

            CockpitButton(
                text = "ARRIVED AT PICKUP POINT",
                onClick = {
                    onShowToast("Status updated to ARRIVED")
                    onArrived()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Screen 18: Arrived at Pickup Point (GPS proximity verification)
 */
@Composable
fun ArrivedPickupScreen(
    onNavigateBack: () -> Unit = {},
    onStartWaitingTimer: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Arrived at Pickup",
            subtitle = "GPS proximity locked within 25 meters",
            onBack = onNavigateBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CockpitCard(glow = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Proximity Confirmed", color = EmeraldPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Vehicle is parked at designated pickup point", color = CockpitTextSecondary, fontSize = 12.sp)
                        }
                    }
                }

                CockpitCard {
                    Text("COMMUTER NOTIFIED", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Notification sent: 'Pilot Ramesh has arrived at Metro Gate 2'", color = CockpitTextPrimary, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("Waiting grace: 5 minutes complimentary before paid meter begins (₹1.50/min)", color = CockpitTextSecondary, fontSize = 12.sp)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CockpitButton(
                    text = "START WAITING TIMER & BILLABLE METER",
                    onClick = {
                        onShowToast("Waiting timer started")
                        onStartWaitingTimer()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                CockpitButton(
                    text = "Call Passenger (Masked Proxy)",
                    onClick = { onShowToast("Connecting masked call...") },
                    modifier = Modifier.fillMaxWidth(),
                    isOutlined = true
                )
            }
        }
    }
}

/**
 * Screen 19: Waiting Timer & Billable Meter (5-min complimentary grace & waiting fee)
 */
@Composable
fun WaitingTimerScreen(
    onNavigateBack: () -> Unit = {},
    onProceedToOtp: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    var elapsedSeconds by remember { mutableIntStateOf(340) } // 5m 40s (into billable time)
    val graceSeconds = 300 // 5 minutes
    val billableSeconds = maxOf(0, elapsedSeconds - graceSeconds)
    val billableMinutes = (billableSeconds + 59) / 60
    val waitingFeeRupees = billableMinutes * 1.50

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Waiting Meter",
            subtitle = "Active waiting timer & passenger grace tracker",
            onBack = onNavigateBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CockpitCard(glow = billableSeconds > 0) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("TOTAL ELAPSED WAITING", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        val mins = elapsedSeconds / 60
                        val secs = elapsedSeconds % 60
                        Text(
                            String.format("%02d:%02d", mins, secs),
                            color = if (billableSeconds > 0) AmberWarning else EmeraldPrimary,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(Modifier.height(6.dp))
                        CockpitBadge(
                            if (billableSeconds > 0) "BILLABLE WAITING ACTIVE" else "COMPLIMENTARY GRACE PERIOD",
                            if (billableSeconds > 0) AmberWarning else EmeraldPrimary
                        )
                    }
                }

                CockpitCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("ACCUMULATED WAITING FEE", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("₹${String.format("%.2f", waitingFeeRupees)}", color = EmeraldPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("BILLABLE MINUTES", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("${billableMinutes} min (₹1.50/min)", color = CockpitTextPrimary, fontSize = 14.sp)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CockpitButton(
                    text = "PASSENGER BOARDED (ENTER OTP)",
                    onClick = {
                        onProceedToOtp()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                CockpitButton(
                    text = "Cancel Trip (Rider No-Show)",
                    onClick = { onShowToast("No-show fee of ₹30 credited to wallet") },
                    modifier = Modifier.fillMaxWidth(),
                    isOutlined = true
                )
            }
        }
    }
}

/**
 * Screen 20: Boarding & Commuter OTP Verify (4-digit start OTP unlock)
 */
@Composable
fun BoardingOtpScreen(
    onNavigateBack: () -> Unit = {},
    onTripStarted: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    var otpDigits by remember { mutableStateOf(listOf("", "", "", "")) }
    var isVerifying by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Start Trip OTP",
            subtitle = "Verify 4-digit ride unlock code from commuter",
            onBack = onNavigateBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            CockpitCard(glow = true) {
                Text(
                    "ASK COMMUTER FOR START OTP",
                    color = CockpitTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                ) {
                    otpDigits.forEachIndexed { index, digit ->
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CockpitSurfaceHighlight),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = digit.ifEmpty { "•" },
                                color = if (digit.isNotEmpty()) ElectricCyan else CockpitTextMuted,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Simulated numpad
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val rows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("C", "0", "⌫")
                    )
                    rows.forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { key ->
                                Button(
                                    onClick = {
                                        when (key) {
                                            "C" -> otpDigits = listOf("", "", "", "")
                                            "⌫" -> {
                                                val lastIdx = otpDigits.indexOfLast { it.isNotEmpty() }
                                                if (lastIdx >= 0) {
                                                    otpDigits = otpDigits.toMutableList().also { it[lastIdx] = "" }
                                                }
                                            }
                                            else -> {
                                                val firstEmpty = otpDigits.indexOfFirst { it.isEmpty() }
                                                if (firstEmpty >= 0) {
                                                    otpDigits = otpDigits.toMutableList().also { it[firstEmpty] = key }
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = CockpitSurfaceHighlight),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(key, color = CockpitTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            CockpitButton(
                text = if (isVerifying) "VERIFYING OTP..." else "START RIDE",
                onClick = {
                    val code = otpDigits.joinToString("")
                    if (code.length == 4) {
                        isVerifying = true
                        onShowToast("OTP $code verified! Meter is LIVE")
                        onTripStarted()
                    } else {
                        onShowToast("Please enter all 4 digits")
                    }
                },
                enabled = otpDigits.all { it.isNotEmpty() } && !isVerifying,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Screen 21: In-Trip Turn Navigation (speed limit alerts & next turn)
 */
@Composable
fun InTripNavScreen(
    onNavigateBack: () -> Unit = {},
    onEndTrip: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    var currentSpeed by remember { mutableIntStateOf(26) }
    val speedLimit = 35

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Trip In Progress",
            subtitle = "Active commuter trip navigation & telemetry",
            onBack = onNavigateBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Next turn banner
                CockpitCard(glow = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Straight, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("In 450 meters", color = ElectricCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Continue straight past Metro Pillar 68", color = CockpitTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Cockpit HUD (Speedometer & distance)
                CockpitCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("CURRENT SPEED", color = CockpitTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("$currentSpeed km/h", color = if (currentSpeed > speedLimit) CrimsonAlert else EmeraldPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Limit: $speedLimit km/h", color = CockpitTextSecondary, fontSize = 11.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("REMAINING DISTANCE", color = CockpitTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("2.4 km", color = CockpitTextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Text("Est. Time: 7 mins", color = ElectricCyan, fontSize = 12.sp)
                        }
                    }
                }

                // Destination Card
                CockpitCard {
                    Text("DROP DESTINATION", color = CockpitTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Pocket 1 Market, Mayur Vihar Phase 1", color = CockpitTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            CockpitButton(
                text = "COMPLETE TRIP & COLLECT FARE",
                onClick = {
                    onShowToast("Trip ended. Calculating final fare...")
                    onEndTrip()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Screen 22: Multi-Stop Waypoints (sequential stop navigation & passenger notes)
 */
@Composable
fun MultiStopScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val stops = listOf(
        WaypointStop(1, "Stop 1: Metro Station Gate 2", "Dropped passenger 1", true),
        WaypointStop(2, "Stop 2: Main Market SBI ATM", "Current Destination • In 300m", false),
        WaypointStop(3, "Stop 3: Pocket B Residential Gate", "Next Final Drop", false)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Multi-Stop Waypoints",
            subtitle = "Sequential corridor itinerary & drop queue",
            onBack = onNavigateBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(stops) { stop ->
                CockpitCard(glow = !stop.isCompleted && stop.order == 2) {
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
                                    .background(if (stop.isCompleted) EmeraldPrimary else ElectricCyan),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${stop.order}", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(stop.title, color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(stop.subtitle, color = CockpitTextSecondary, fontSize = 12.sp)
                            }
                        }
                        if (stop.isCompleted) {
                            CockpitBadge("COMPLETED", EmeraldPrimary)
                        } else {
                            CockpitBadge("ACTIVE", ElectricCyan)
                        }
                    }
                    if (!stop.isCompleted && stop.order == 2) {
                        Spacer(Modifier.height(10.dp))
                        CockpitButton(
                            text = "Mark Stop 2 Reached",
                            onClick = { onShowToast("Waypoint #2 marked reached") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

private data class WaypointStop(
    val order: Int,
    val title: String,
    val subtitle: String,
    val isCompleted: Boolean
)

