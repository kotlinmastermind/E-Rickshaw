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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.driver.components.*
import com.vaisentra.mobility.driver.data.DriverRepository
import com.vaisentra.mobility.driver.theme.*
import kotlinx.coroutines.launch

// Screen 10: Duty Dashboard -> Uses VasantraDriverHomeScreen
@Composable
fun DutyDashboardScreen(onNavigate: (String) -> Unit) {
    LaunchedEffect(Unit) {
        DriverRepository.fetchDriverProfile()
        DriverRepository.fetchEarningsSummary()
    }
    VasantraDriverHomeScreen(onNavigate = onNavigate)
}

// Screen 7: Assigned Vehicle & Daily Inspection
@Composable
fun VehicleInspectionScreen(onNavigate: (String) -> Unit) {
    var tyrePressureOk by remember { mutableStateOf(true) }
    var lightsOk by remember { mutableStateOf(true) }
    var brakeBuzzerOk by remember { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }
    val profile by DriverRepository.profile.collectAsState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PilotCanvas)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        CockpitHeader(
            title = "Vehicle Inspection Check",
            subtitle = "${profile.assignedVehiclePlate.ifBlank { "Vehicle" }} • Pre-Shift Walkaround",
            onBack = { onNavigate("duty_dashboard") }
        )
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
        CockpitButton(
            text = if (isSubmitting) "Submitting..." else "Sign-Off Inspection & Log Odometer →",
            enabled = !isSubmitting,
            onClick = {
                isSubmitting = true
                scope.launch {
                    val vehicleId = profile.assignedVehiclePlate.ifBlank { "default-vehicle" }
                    val res = DriverRepository.submitVehicleInspectionCheck(vehicleId, tyrePressureOk, lightsOk, brakeBuzzerOk)
                    isSubmitting = false
                    if (res.isSuccess) {
                        DriverDialogController.showToast("Vehicle inspection logged!")
                        onNavigate("shift_start")
                    } else {
                        DriverDialogController.showToast("Inspection failed: ${res.exceptionOrNull()?.message}")
                    }
                }
            }
        )
    }
}

// Screen 8: Vehicle RC, Fitness & PUC
@Composable
fun VehicleRcStatusScreen(onNavigate: (String) -> Unit) {
    val profile by DriverRepository.profile.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PilotCanvas)
            .padding(16.dp)
    ) {
        CockpitHeader(title = "RC, Fitness & Commercial PUC", subtitle = "Vehicle Compliance", onBack = { onNavigate("duty_dashboard") })
        Spacer(modifier = Modifier.height(16.dp))

        CockpitCard {
            val specs = listOf(
                "Registration No" to profile.assignedVehiclePlate.ifBlank { "Pending Assignment" },
                "Model" to profile.assignedVehicleModel.ifBlank { "EV 3-Wheeler" },
                "Approval Status" to profile.approvalStatus,
                "License" to profile.licenseNumber.ifBlank { "-" }
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
    var isSubmitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PilotCanvas)
            .padding(16.dp)
    ) {
        CockpitHeader(title = "Start Duty Shift", subtitle = "Opening meter declaration", onBack = { onNavigate("duty_dashboard") })
        Spacer(modifier = Modifier.height(16.dp))

        CockpitCard {
            CockpitTextField(value = odoReading, onValueChange = { odoReading = it }, placeholder = "e.g. 48210", label = "Opening Odometer (km)")
            Spacer(modifier = Modifier.height(12.dp))
            CockpitTextField(value = batterySoc, onValueChange = { batterySoc = it }, placeholder = "e.g. 92", label = "Battery SoC Percentage (%)", trailingText = "BATTERY OK ✓")
            Spacer(modifier = Modifier.height(20.dp))
            CockpitButton(
                text = if (isSubmitting) "Starting Shift..." else "Unlock Shift & Go Online",
                enabled = !isSubmitting,
                onClick = {
                    val odo = odoReading.toDoubleOrNull() ?: 0.0
                    isSubmitting = true
                    scope.launch {
                        val res = DriverRepository.startShiftOdometer(odo)
                        isSubmitting = false
                        if (res.isSuccess) {
                            DriverRepository.setDutyStatus(true)
                            DriverDialogController.showToast("Shift started! Odometer $odo km recorded.")
                            onNavigate("duty_dashboard")
                        } else {
                            DriverDialogController.showToast("Start shift failed: ${res.exceptionOrNull()?.message}")
                        }
                    }
                },
                backgroundColor = PilotOnlineGreen
            )
        }
    }
}

// Screen 12: Driver Fatigue & Rest Check
@Composable
fun FatigueCheckScreen(onNavigate: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PilotCanvas)
            .padding(16.dp)
    ) {
        CockpitHeader(title = "Driver Fatigue Monitor", subtitle = "Fleet safety rest intervals", onBack = { onNavigate("duty_dashboard") })
        Spacer(modifier = Modifier.height(16.dp))

        CockpitCard(borderColor = PilotAmber) {
            Text(text = "Fatigue Safety Check", color = PilotAmber, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "To guarantee passenger safety, pilots should take a 15-minute rest pause after every 4.5 hours of continuous transit.", color = PilotTextMuted, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(16.dp))
            CockpitButton(text = "Start 15-Min Break Now", onClick = {
                scope.launch {
                    val res = DriverRepository.submitFatigueCheckResult("NEEDS_REST", "Driver initiated 15-min rest break")
                    if (res.isSuccess) {
                        DriverDialogController.show(DriverPopupType.FATIGUE_BREAK)
                    } else {
                        DriverDialogController.showToast("Failed to log fatigue check")
                    }
                }
            }, backgroundColor = PilotAmber, textColor = PilotCanvas)
        }
    }
}

// Screen 35: Shift End & Closing Odometer
@Composable
fun ShiftEndScreen(onNavigate: (String) -> Unit) {
    var closingOdo by remember { mutableStateOf("48292") }
    var isSubmitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PilotCanvas)
            .padding(16.dp)
    ) {
        CockpitHeader(title = "End Duty Shift", subtitle = "Closing meter & vehicle handover", onBack = { onNavigate("duty_dashboard") })
        Spacer(modifier = Modifier.height(16.dp))

        CockpitCard {
            CockpitTextField(value = closingOdo, onValueChange = { closingOdo = it }, placeholder = "e.g. 48292", label = "Closing Odometer Reading (km)")
            Spacer(modifier = Modifier.height(20.dp))
            CockpitButton(
                text = if (isSubmitting) "Closing Shift..." else "Close Shift & Submit Handover",
                enabled = !isSubmitting,
                onClick = {
                    val odo = closingOdo.toDoubleOrNull() ?: 0.0
                    isSubmitting = true
                    scope.launch {
                        val res = DriverRepository.endShiftOdometer(odo)
                        isSubmitting = false
                        if (res.isSuccess) {
                            DriverRepository.setDutyStatus(false)
                            DriverDialogController.showToast("Shift ended! Handover submitted.")
                            onNavigate("shift_summary")
                        } else {
                            DriverDialogController.showToast("End shift failed: ${res.exceptionOrNull()?.message}")
                        }
                    }
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
        CockpitButton(text = "Save Preferences", onClick = {
            DriverDialogController.showToast("Preferences updated.")
            onNavigate("duty_dashboard")
        })
    }
}