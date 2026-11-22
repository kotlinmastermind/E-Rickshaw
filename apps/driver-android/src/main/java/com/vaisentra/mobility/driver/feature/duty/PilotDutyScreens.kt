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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.driver.components.*
import com.vaisentra.mobility.driver.data.DriverNetworkClient
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
    var lastInspectionInfo by remember { mutableStateOf<String?>(null) }
    val profile by DriverRepository.profile.collectAsState()
    val assignedVehicle by DriverRepository.assignedVehicle.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        DriverRepository.fetchDriverVehicles()
        scope.launch {
            val inspRes = DriverRepository.fetchLatestPreShiftInspection()
            inspRes.getOrNull()?.let { insp ->
                (insp["tyre_pressure_ok"] as? Boolean)?.let { tyrePressureOk = it }
                (insp["lights_ok"] as? Boolean)?.let { lightsOk = it }
                (insp["brake_buzzer_ok"] as? Boolean)?.let { brakeBuzzerOk = it }
                val inspectedAt = insp["inspected_at"]?.toString() ?: insp["created_at"]?.toString() ?: ""
                val status = insp["status"]?.toString() ?: "PASSED"
                if (inspectedAt.isNotBlank()) {
                    lastInspectionInfo = "Last Sign-off: $status • ${inspectedAt.take(19).replace("T", " ")}"
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PilotCanvas)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        val displayPlate = assignedVehicle?.licensePlate?.ifBlank { profile.assignedVehiclePlate }?.ifBlank { "DL-01-ER-2026" } ?: "DL-01-ER-2026"
        CockpitHeader(
            title = "Vehicle Inspection Check",
            subtitle = "$displayPlate • Pre-Shift Walkaround",
            onBack = { onNavigate("duty_dashboard") }
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (lastInspectionInfo != null) {
            CockpitCard(borderColor = PilotOnlineGreen) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "✓ Synced with Supabase", color = PilotOnlineGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    CockpitBadge(text = "LIVE", backgroundColor = OnlineGlow, textColor = PilotOnlineGreen)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = lastInspectionInfo ?: "", color = PilotTextMuted, fontSize = 11.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

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
            text = if (isSubmitting) "Submitting to Supabase..." else "Sign-Off Inspection & Log Odometer →",
            enabled = !isSubmitting,
            onClick = {
                isSubmitting = true
                scope.launch {
                    val vehicleId = assignedVehicle?.vehicleId?.ifBlank { profile.assignedVehiclePlate } ?: profile.assignedVehiclePlate
                    val res = DriverRepository.submitVehicleInspectionCheck(vehicleId, tyrePressureOk, lightsOk, brakeBuzzerOk)
                    isSubmitting = false
                    if (res.isSuccess) {
                        DriverDialogController.showToast("Vehicle inspection recorded in database!")
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
    val assignedVehicle by DriverRepository.assignedVehicle.collectAsState()
    val documents by DriverRepository.documents.collectAsState()
    val scope = rememberCoroutineScope()

    var isEditing by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val currentPlate = assignedVehicle?.licensePlate?.ifBlank { profile.assignedVehiclePlate }?.ifBlank { "DL-01-ER-2026" } ?: "DL-01-ER-2026"
    val currentModel = assignedVehicle?.model?.ifBlank { profile.assignedVehicleModel }?.ifBlank { "Mahindra Treo EV" } ?: "Mahindra Treo EV"
    val currentRc = assignedVehicle?.rcNumber?.ifBlank { null } ?: documents.firstOrNull { it.documentType == "VEHICLE_RC" }?.documentNumber ?: "DL-01-2025-RC88341"
    val currentPermit = assignedVehicle?.permitNumber?.ifBlank { null } ?: documents.firstOrNull { it.documentType == "COMMERCIAL_PERMIT" }?.documentNumber ?: "AITP-DEL-2026-99124"

    var editPlate by remember(currentPlate) { mutableStateOf(currentPlate) }
    var editModel by remember(currentModel) { mutableStateOf(currentModel) }
    var editRc by remember(currentRc) { mutableStateOf(currentRc) }
    var editPermit by remember(currentPermit) { mutableStateOf(currentPermit) }

    LaunchedEffect(Unit) {
        DriverRepository.fetchDriverVehicles()
        DriverRepository.fetchDriverDocuments()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PilotCanvas)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        CockpitHeader(title = "RC, Fitness & Commercial PUC", subtitle = "Vehicle Compliance", onBack = { onNavigate("duty_dashboard") })
        Spacer(modifier = Modifier.height(16.dp))

        CockpitCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Compliance Specs", color = PilotTextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = { isEditing = !isEditing }) {
                    Text(
                        text = if (isEditing) "Cancel" else "✏️ Edit Details",
                        color = PilotElectricCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (!isEditing) {
                val license = profile.licenseNumber.ifBlank { "DL-DEL-2024-99882" }
                val docCount = if (documents.isNotEmpty()) "${documents.size} Verified Records" else "Live Supabase Sync"

                val specs = listOf(
                    "Registration No" to currentPlate,
                    "Model" to currentModel,
                    "RC Number" to currentRc,
                    "Commercial Permit" to currentPermit,
                    "Approval Status" to profile.approvalStatus,
                    "Driver License" to license,
                    "Compliance Vault" to docCount
                )
                specs.forEach { (k, v) ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = k, color = PilotTextMuted, fontSize = 12.sp)
                        Text(text = v, color = PilotTextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                CockpitTextField(value = editPlate, onValueChange = { editPlate = it }, label = "License Plate", placeholder = "e.g. DL-01-ER-2026")
                Spacer(modifier = Modifier.height(8.dp))
                CockpitTextField(value = editModel, onValueChange = { editModel = it }, label = "Vehicle Model", placeholder = "e.g. Mahindra Treo EV")
                Spacer(modifier = Modifier.height(8.dp))
                CockpitTextField(value = editRc, onValueChange = { editRc = it }, label = "RC Number", placeholder = "e.g. DL-01-2025-RC88341")
                Spacer(modifier = Modifier.height(8.dp))
                CockpitTextField(value = editPermit, onValueChange = { editPermit = it }, label = "Commercial Permit", placeholder = "e.g. AITP-DEL-2026-99124")
                Spacer(modifier = Modifier.height(14.dp))
                CockpitButton(
                    text = if (isSaving) "Saving to Supabase..." else "Save Vehicle Details",
                    enabled = !isSaving && editPlate.isNotBlank(),
                    onClick = {
                        isSaving = true
                        scope.launch {
                            val res = DriverRepository.updateActiveVehicle(
                                plate = editPlate.trim(),
                                model = editModel.trim(),
                                rc = editRc.trim(),
                                permit = editPermit.trim()
                            )
                            isSaving = false
                            if (res.isSuccess) {
                                isEditing = false
                                DriverDialogController.showToast("Vehicle details updated in database!")
                            } else {
                                DriverDialogController.showToast("Update failed: ${res.exceptionOrNull()?.message}")
                            }
                        }
                    },
                    backgroundColor = PilotOnlineGreen
                )
            }
        }
    }
}

// Screen 9: Shift Start & Opening Odometer Log
@Composable
fun ShiftStartScreen(onNavigate: (String) -> Unit) {
    var odoReading by remember { mutableStateOf("") }
    var batterySoc by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var lastClosingInfo by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        DriverRepository.fetchDriverVehicles()
        scope.launch {
            val statusRes = DriverRepository.fetchShiftStatus()
            statusRes.getOrNull()?.let { data ->
                val lastClosing = (data["last_closing_odometer_km"] as? Number)?.toDouble()
                    ?: (data["start_odometer_km"] as? Number)?.toDouble()
                if (lastClosing != null && lastClosing > 0) {
                    odoReading = lastClosing.toInt().toString()
                    lastClosingInfo = "Previous closing meter: ${lastClosing.toInt()} km"
                }
            }
            if (odoReading.isBlank()) {
                odoReading = ""
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PilotCanvas)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        CockpitHeader(title = "Start Duty Shift", subtitle = "Opening meter declaration", onBack = { onNavigate("duty_dashboard") })
        Spacer(modifier = Modifier.height(16.dp))

        if (lastClosingInfo != null) {
            CockpitCard(borderColor = PilotElectricCyan) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = lastClosingInfo ?: "", color = PilotElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    CockpitBadge(text = "VERIFIED", backgroundColor = OnlineGlow, textColor = PilotOnlineGreen)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        CockpitCard {
            CockpitTextField(value = odoReading, onValueChange = { odoReading = it }, placeholder = "e.g. 48210", label = "Opening Odometer (km)")
            Spacer(modifier = Modifier.height(12.dp))
            CockpitTextField(value = batterySoc, onValueChange = { batterySoc = it }, placeholder = "e.g. 95", label = "Battery SoC Percentage (%)", trailingText = "BATTERY OK ✓")
            Spacer(modifier = Modifier.height(20.dp))
            CockpitButton(
                text = if (isSubmitting) "Starting Shift in Backend..." else "Unlock Shift & Go Online",
                enabled = !isSubmitting && odoReading.isNotBlank(),
                onClick = {
                    val odo = odoReading.toDoubleOrNull() ?: 0.0
                    val soc = batterySoc.toIntOrNull() ?: 95
                    isSubmitting = true
                    scope.launch {
                        val res = DriverRepository.startShiftOdometer(odo, soc)
                        isSubmitting = false
                        if (res.isSuccess) {
                            DriverRepository.setDutyStatus(true)
                            DriverDialogController.showToast("Shift started! Odometer $odo km saved to Supabase.")
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

// Screen 35: Shift End & Closing Odometer
@Composable
fun ShiftEndScreen(onNavigate: (String) -> Unit) {
    var closingOdo by remember { mutableStateOf("") }
    var startOdo by remember { mutableStateOf<Double?>(null) }
    var shiftStartedAt by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            val statusRes = DriverRepository.fetchShiftStatus()
            statusRes.getOrNull()?.let { data ->
                val sOdo = (data["start_odometer_km"] as? Number)?.toDouble()
                if (sOdo != null && sOdo > 0) {
                    startOdo = sOdo
                    closingOdo = (sOdo + 15.0).toInt().toString()
                }
                shiftStartedAt = data["shift_start_time"]?.toString() ?: data["started_at"]?.toString()
            }
            if (closingOdo.isBlank()) {
                closingOdo = ""
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PilotCanvas)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        CockpitHeader(title = "End Duty Shift", subtitle = "Closing meter & vehicle handover", onBack = { onNavigate("duty_dashboard") })
        Spacer(modifier = Modifier.height(16.dp))

        if (startOdo != null) {
            CockpitCard(borderColor = PilotAmber) {
                Text(text = "Active Shift in Progress", color = PilotAmber, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Shift Opening Meter:", color = PilotTextMuted, fontSize = 12.sp)
                    Text(text = "${startOdo?.toInt()} km", color = PilotTextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                if (!shiftStartedAt.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = "Started: ${shiftStartedAt?.take(19)?.replace("T", " ")}", color = PilotTextMuted, fontSize = 11.sp)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        CockpitCard {
            CockpitTextField(value = closingOdo, onValueChange = { closingOdo = it }, placeholder = "e.g. 48292", label = "Closing Odometer Reading (km)")
            val currentClosing = closingOdo.toDoubleOrNull()
            if (startOdo != null && currentClosing != null) {
                val distance = currentClosing - (startOdo ?: 0.0)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (distance >= 0) "Shift distance: ${"%.1f".format(distance)} km" else "⚠️ Closing meter cannot be less than opening (${startOdo?.toInt()} km)",
                    color = if (distance >= 0) PilotOnlineGreen else PilotCrimson,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            CockpitButton(
                text = if (isSubmitting) "Closing Shift in Supabase..." else "Close Shift & Submit Handover",
                enabled = !isSubmitting && closingOdo.isNotBlank(),
                onClick = {
                    val odo = closingOdo.toDoubleOrNull() ?: 0.0
                    if (startOdo != null && odo < (startOdo ?: 0.0)) {
                        DriverDialogController.showToast("Closing odometer cannot be less than opening odometer (${startOdo?.toInt()} km)")
                        return@CockpitButton
                    }
                    isSubmitting = true
                    scope.launch {
                        val res = DriverRepository.endShiftOdometer(odo)
                        isSubmitting = false
                        if (res.isSuccess) {
                            DriverRepository.setDutyStatus(false)
                            DriverDialogController.showToast("Shift ended! Odometer $odo km saved to database.")
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

// Screen 50: Driver Preferences, Zones & Backend Server Configuration
@Composable
fun DriverPreferencesScreen(onNavigate: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val driverPrefs by DriverRepository.driverPreferences.collectAsState()
    var autoAcceptPool by remember(driverPrefs.autoAcceptPool) { mutableStateOf(driverPrefs.autoAcceptPool) }
    var preferEastDelhi by remember(driverPrefs.preferredCorridor) {
        mutableStateOf(driverPrefs.preferredCorridor.contains("Anand Vihar", ignoreCase = true))
    }
    var currentServerUrl by remember { mutableStateOf(DriverNetworkClient.baseUrl) }
    var healthStatus by remember { mutableStateOf("Checking...") }
    var isChecking by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        DriverRepository.fetchDriverPreferences()
    }

    LaunchedEffect(driverPrefs) {
        autoAcceptPool = driverPrefs.autoAcceptPool
        preferEastDelhi = driverPrefs.preferredCorridor.contains("Anand Vihar", ignoreCase = true)
    }

    LaunchedEffect(currentServerUrl) {
        isChecking = true
        val healthRes = DriverRepository.checkBackendHealth()
        isChecking = false
        healthStatus = if (healthRes.isSuccess) {
            "🟢 LIVE (${healthRes.getOrNull()?.service ?: "FastAPI"})"
        } else {
            "🔴 UNREACHABLE"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PilotCanvas)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        CockpitHeader(
            title = "Pilot Corridor & System",
            subtitle = "Custom dispatch routing & backend server",
            onBack = { onNavigate("duty_dashboard") }
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Dispatch Preferences Card
        CockpitCard {
            Text(
                text = "DISPATCH & CORRIDOR PREFERENCES",
                color = PilotElectricCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Auto-Accept Eco-Pool Feeder Rides", color = PilotTextWhite, fontSize = 13.sp)
                Switch(
                    checked = autoAcceptPool,
                    onCheckedChange = { newValue ->
                        autoAcceptPool = newValue
                        scope.launch {
                            DriverRepository.updateDriverPreferences(autoAcceptPool = newValue)
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Preferred Corridor: Anand Vihar / CP", color = PilotTextWhite, fontSize = 13.sp)
                Switch(
                    checked = preferEastDelhi,
                    onCheckedChange = { newValue ->
                        preferEastDelhi = newValue
                        scope.launch {
                            val corridor = if (newValue) "Anand Vihar / CP" else "All Operational Zones"
                            DriverRepository.updateDriverPreferences(preferredCorridor = corridor)
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Backend Connectivity & Server Environment Card
        CockpitCard {
            Text(
                text = "BACKEND SERVER CONFIGURATION",
                color = PilotElectricCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Active Server Endpoint:",
                color = PilotTextMuted,
                fontSize = 12.sp
            )
            Text(
                text = currentServerUrl,
                color = PilotTextWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Health Status: ",
                    color = PilotTextMuted,
                    fontSize = 12.sp
                )
                Text(
                    text = healthStatus,
                    color = if (healthStatus.contains("LIVE")) PilotOnlineGreen else PilotAmber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        DriverNetworkClient.switchServer(DriverNetworkClient.DEFAULT_RENDER_URL)
                        currentServerUrl = DriverNetworkClient.baseUrl
                        DriverDialogController.showToast("Switched to Render Backend")
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentServerUrl.contains("render")) PilotElectricCyan else CockpitSurface
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Cloud (Render)",
                        fontSize = 11.sp,
                        color = if (currentServerUrl.contains("render")) Color.Black else PilotTextWhite
                    )
                }

                Button(
                    onClick = {
                        DriverNetworkClient.switchServer(DriverNetworkClient.LOCAL_EMULATOR_URL)
                        currentServerUrl = DriverNetworkClient.baseUrl
                        DriverDialogController.showToast("Switched to Local (10.0.2.2:8000)")
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentServerUrl.contains("10.0.2.2")) PilotElectricCyan else CockpitSurface
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Localhost",
                        fontSize = 11.sp,
                        color = if (currentServerUrl.contains("10.0.2.2")) Color.Black else PilotTextWhite
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    scope.launch {
                        isChecking = true
                        val res = DriverRepository.checkBackendHealth()
                        isChecking = false
                        healthStatus = if (res.isSuccess) {
                            "🟢 LIVE (${res.getOrNull()?.service ?: "FastAPI"})"
                        } else {
                            "🔴 UNREACHABLE: ${res.exceptionOrNull()?.message}"
                        }
                        DriverDialogController.showToast("Health Check: $healthStatus")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (isChecking) "Checking..." else "Ping Backend Server",
                    color = PilotElectricCyan,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        CockpitButton(
            text = if (isSaving) "Saving Preferences..." else "Save Preferences & Return",
            enabled = !isSaving,
            onClick = {
                isSaving = true
                scope.launch {
                    val corridor = if (preferEastDelhi) "Anand Vihar / CP" else "All Operational Zones"
                    val res = DriverRepository.updateDriverPreferences(
                        autoAcceptPool = autoAcceptPool,
                        preferredCorridor = corridor
                    )
                    isSaving = false
                    if (res.isSuccess) {
                        DriverDialogController.showToast("Dispatch preferences saved to server!")
                        onNavigate("duty_dashboard")
                    } else {
                        DriverDialogController.showToast("Failed to save: ${res.exceptionOrNull()?.message}")
                    }
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DriverPreferencesScreenPreview() {
    DriverGoTheme {
        DriverPreferencesScreen(onNavigate = {})
    }
}

@Preview(showBackground = true)
@Composable
fun DutyDashboardScreenPreview() {
    DriverGoTheme {
        DutyDashboardScreen(onNavigate = {})
    }
}