package com.vaisentra.mobility.driver.feature.duty

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

// Screen 10: Duty Dashboard & Auto-Accept Radar (Main Cockpit Home)
@Composable
fun DutyDashboardScreen(onNavigate: (String) -> Unit) {
    val isOnline by DriverRepository.isOnline.collectAsState()
    val earnings by DriverRepository.earnings.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PilotCanvas)
    ) {
        // Top Cockpit Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(PilotSurfaceElevated)
                    .clickable { onNavigate("catalog") }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "⚡ DriverGo", color = PilotTextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    CockpitBadge(text = "50 SCREENS", backgroundColor = SurgeGlow, textColor = PilotAmber)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(PilotSurfaceElevated)
                        .clickable { DriverDialogController.show(DriverPopupType.PILOT_SOS) },
                    contentAlignment = Alignment.Center
                ) { Text(text = "🚨", fontSize = 16.sp) }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(PilotSurfaceElevated)
                        .clickable { onNavigate("pilot_profile") },
                    contentAlignment = Alignment.Center
                ) { Text(text = "👨‍✈️", fontSize = 16.sp) }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Master Duty Radar Switch
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape)
                    .background(if (isOnline) OnlineGlow else PilotCard)
                    .border(2.5.dp, if (isOnline) PilotOnlineGreen else PilotOfflineGray, CircleShape)
                    .clickable {
                        val newStatus = !isOnline
                        DriverRepository.setDutyStatus(newStatus)
                        DriverDialogController.showToast(if (newStatus) "You are ONLINE! Receiving dispatch offers." else "You are OFFLINE.")
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = if (isOnline) "🟢 ONLINE" else "⚪ OFFLINE", color = if (isOnline) PilotOnlineGreen else PilotTextMuted, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = if (isOnline) "Auto-Accept Radar Active" else "Tap to Go Online", color = PilotTextDim, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Quick Today Metrics Card
            CockpitCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(text = "TODAY'S PAY", color = PilotTextMuted, fontSize = 11.sp)
                        Text(text = "₹${earnings.todayNetRupees.toInt()}", color = PilotElectricCyan, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text(text = "TRIPS", color = PilotTextMuted, fontSize = 11.sp)
                        Text(text = "${earnings.completedTripsToday}", color = PilotTextWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text(text = "HOURS", color = PilotTextMuted, fontSize = 11.sp)
                        Text(text = "${earnings.onlineHoursToday}h", color = PilotTextWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Active Vehicle Card
            CockpitCard(modifier = Modifier.clickable { onNavigate("vehicle_inspect") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🛺", fontSize = 28.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "DL-1ER-4921 (Mahindra Treo EV)", color = PilotTextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Battery SoC: 78% • Range: 64 km", color = PilotOnlineGreen, fontSize = 12.sp)
                    }
                    Text(text = "Inspect →", color = PilotElectricCyan, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Quick Nav Links
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CockpitButton(
                    text = "Surge Heatmap",
                    onClick = { onNavigate("heatmap") },
                    modifier = Modifier.weight(1f),
                    backgroundColor = PilotCard,
                    textColor = PilotTextWhite,
                    icon = "🔥"
                )
                CockpitButton(
                    text = "Test Incoming Offer",
                    onClick = { DriverDialogController.show(DriverPopupType.INCOMING_OFFER) },
                    modifier = Modifier.weight(1f),
                    backgroundColor = PilotOnlineGreen,
                    textColor = PilotCanvas,
                    icon = "🚖"
                )
            }
        }
    }
}

// Screen 7: Assigned Vehicle & Daily Inspection
@Composable
fun VehicleInspectionScreen(onNavigate: (String) -> Unit) {
    var tyrePressureOk by remember { mutableStateOf(true) }
    var lightsOk by remember { mutableStateOf(true) }
    var brakeBuzzerOk by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PilotCanvas)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        CockpitHeader(title = "Vehicle Inspection Check", subtitle = "DL-1ER-4921 • Pre-Shift Walkaround", onBack = { onNavigate("duty_dashboard") })
        Spacer(modifier = Modifier.height(16.dp))

        CockpitCard {
            Text(text = "EV Readiness Checklist", color = PilotTextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Tyre Pressure (32 PSI Front, 36 PSI Rear)", color = PilotTextMuted, fontSize = 12.sp)
                Switch(checked = tyrePressureOk, onCheckedChange = { tyrePressureOk = it })
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Headlights & Hazard Indicators", color = PilotTextMuted, fontSize = 12.sp)
                Switch(checked = lightsOk, onCheckedChange = { lightsOk = it })
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Reverse Warning Buzzer & Horn", color = PilotTextMuted, fontSize = 12.sp)
                Switch(checked = brakeBuzzerOk, onCheckedChange = { brakeBuzzerOk = it })
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        CockpitButton(text = "Sign-Off Inspection & Log Odometer →", onClick = { onNavigate("shift_start") })
    }
}

// Screen 8: Vehicle RC, Fitness & PUC
@Composable
fun VehicleRcStatusScreen(onNavigate: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PilotCanvas)
            .padding(16.dp)
    ) {
        CockpitHeader(title = "RC, Fitness & Commercial PUC", subtitle = "Vehicle DL-1ER-4921 Compliance", onBack = { onNavigate("duty_dashboard") })
        Spacer(modifier = Modifier.height(16.dp))

        CockpitCard {
            val specs = listOf(
                "Registration No" to "DL-1ER-4921",
                "Owner / Fleet" to "Vaisentra Electric Mobility Fleet 1",
                "Permit Type" to "Delhi NCR 3-Wheeler Passenger Permit",
                "Fitness Validity" to "28-Feb-2028 (Active ✓)",
                "Commercial Insurance" to "Policy #BAJAJ-ALLIANZ-82910"
            )
            specs.forEach { (k, v) ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = k, color = PilotTextMuted, fontSize = 12.sp)
                    Text(text = v, color = PilotTextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// Screen 9: Shift Start & Opening Odometer Log
@Composable
fun ShiftStartScreen(onNavigate: (String) -> Unit) {
    var odoReading by remember { mutableStateOf("48210") }
    var batterySoc by remember { mutableStateOf("92") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PilotCanvas)
            .padding(16.dp)
    ) {
        CockpitHeader(title = "Start Duty Shift", subtitle = "Opening meter declaration in Supabase", onBack = { onNavigate("duty_dashboard") })
        Spacer(modifier = Modifier.height(16.dp))

        CockpitCard {
            CockpitTextField(value = odoReading, onValueChange = { odoReading = it }, placeholder = "e.g. 48210", label = "Opening Odometer (km)")
            Spacer(modifier = Modifier.height(12.dp))
            CockpitTextField(value = batterySoc, onValueChange = { batterySoc = it }, placeholder = "e.g. 92", label = "Battery SoC Percentage (%)", trailingText = "BATTERY OK ✓")
            Spacer(modifier = Modifier.height(20.dp))
            CockpitButton(
                text = "Unlock Shift & Go Online",
                onClick = {
                    DriverRepository.setDutyStatus(true)
                    DriverDialogController.showToast("Shift started! Odometer 48,210 km recorded in Supabase.")
                    onNavigate("duty_dashboard")
                },
                backgroundColor = PilotOnlineGreen
            )
        }
    }
}

// Screen 12: Driver Fatigue & Rest Check
@Composable
fun FatigueCheckScreen(onNavigate: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PilotCanvas)
            .padding(16.dp)
    ) {
        CockpitHeader(title = "Driver Fatigue Monitor", subtitle = "Fleet safety rest intervals", onBack = { onNavigate("duty_dashboard") })
        Spacer(modifier = Modifier.height(16.dp))

        CockpitCard(borderColor = PilotAmber) {
            Text(text = "Duty Cycle: 4 Hours 15 Mins", color = PilotAmber, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "To guarantee high passenger alertness, pilots must take a 15-minute rest pause after every 4.5 hours of continuous transit.", color = PilotTextMuted, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(16.dp))
            CockpitButton(text = "Start 15-Min Break Now", onClick = {
                DriverDialogController.show(DriverPopupType.FATIGUE_BREAK)
            }, backgroundColor = PilotAmber, textColor = PilotCanvas)
        }
    }
}

// Screen 35: Shift End & Closing Odometer
@Composable
fun ShiftEndScreen(onNavigate: (String) -> Unit) {
    var closingOdo by remember { mutableStateOf("48292") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PilotCanvas)
            .padding(16.dp)
    ) {
        CockpitHeader(title = "End Duty Shift", subtitle = "Closing meter & vehicle handover", onBack = { onNavigate("duty_dashboard") })
        Spacer(modifier = Modifier.height(16.dp))

        CockpitCard {
            Text(text = "Today's Shift Distance: 82 km", color = PilotElectricCyan, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            CockpitTextField(value = closingOdo, onValueChange = { closingOdo = it }, placeholder = "e.g. 48292", label = "Closing Odometer Reading (km)")
            Spacer(modifier = Modifier.height(20.dp))
            CockpitButton(
                text = "Close Shift & Submit Handover",
                onClick = {
                    DriverRepository.setDutyStatus(false)
                    DriverDialogController.showToast("Shift ended! Handover report submitted.")
                    onNavigate("shift_summary")
                },
                backgroundColor = PilotCrimson
            )
        }
    }
}

// Screen 50: Driver Preferences & Zones
@Composable
fun DriverPreferencesScreen(onNavigate: (String) -> Unit) {
    var autoAcceptPool by remember { mutableStateOf(true) }
    var preferEastDelhi by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PilotCanvas)
            .padding(16.dp)
    ) {
        CockpitHeader(title = "Pilot Corridor Preferences", subtitle = "Custom dispatch routing preferences", onBack = { onNavigate("duty_dashboard") })
        Spacer(modifier = Modifier.height(16.dp))

        CockpitCard {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Auto-Accept Eco-Pool Feeder Rides", color = PilotTextWhite, fontSize = 13.sp)
                Switch(checked = autoAcceptPool, onCheckedChange = { autoAcceptPool = it })
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Preferred Corridor: Anand Vihar / CP", color = PilotTextWhite, fontSize = 13.sp)
                Switch(checked = preferEastDelhi, onCheckedChange = { preferEastDelhi = it })
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        CockpitButton(text = "Save Preferences to Supabase", onClick = {
            DriverDialogController.showToast("Pilot preferences saved in Supabase!")
            onNavigate("duty_dashboard")
        })
    }
}

