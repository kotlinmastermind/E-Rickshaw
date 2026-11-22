package com.vaisentra.mobility.driver.feature.safety

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.driver.components.*
import com.vaisentra.mobility.driver.data.DriverRepository
import com.vaisentra.mobility.driver.theme.*
import kotlinx.coroutines.launch

/**
 * Screen 23: Route Deviation & Anomaly Alarm
 */
@Composable
fun RouteDeviationScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val reasons = listOf(
        "Road block / municipal construction ahead",
        "Passenger requested alternate drop point",
        "Severe traffic / waterlogged bypass",
        "Accidental missed turn"
    )
    var selectedReason by remember { mutableStateOf(reasons.first()) }
    var isSubmitting by remember { mutableStateOf(false) }
    val rideId by DriverRepository.currentRideId.collectAsState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Route Deviation Alert",
            subtitle = "Automatic anomaly watchdog",
            onBack = onNavigateBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                CockpitCard(glow = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Unscheduled Detour Detected", color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Vehicle drifted >300m away from recommended path", color = CockpitTextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }

            item {
                CockpitCard {
                    Text("SELECT REASON FOR DETOUR", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    reasons.forEach { reason ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedReason == reason,
                                onClick = { selectedReason = reason },
                                colors = RadioButtonDefaults.colors(selectedColor = ElectricCyan)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(reason, color = CockpitTextPrimary, fontSize = 13.sp)
                        }
                    }
                }
            }

            item {
                CockpitButton(
                    text = if (isSubmitting) "LOGGING DETOUR..." else "CONFIRM DETOUR REASON & UPDATE ROUTE",
                    enabled = !isSubmitting,
                    onClick = {
                        if (rideId.isBlank()) {
                            onShowToast("No active ride found.")
                            return@CockpitButton
                        }
                        isSubmitting = true
                        scope.launch {
                            val res = DriverRepository.reportRouteDeviation(rideId, 28.6289, 77.3710)
                            isSubmitting = false
                            if (res.isSuccess) {
                                onShowToast("Detour reason logged: $selectedReason")
                                onNavigateBack()
                            } else {
                                onShowToast("Failed to log detour: ${res.exceptionOrNull()?.message}")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Screen 27: Speedometer & Urban Limits
 */
@Composable
fun SpeedometerScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    var speed by remember { mutableIntStateOf(28) }
    val maxLimit = 35

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Speedometer & Safety HUD",
            subtitle = "Delhi Transport Department e-rickshaw limit compliance",
            onBack = onNavigateBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CockpitCard(glow = speed > maxLimit) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("CURRENT SPEED", color = CockpitTextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "$speed",
                        color = if (speed > maxLimit) CrimsonAlert else EmeraldPrimary,
                        fontSize = 80.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text("KILOMETERS PER HOUR", color = CockpitTextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(16.dp))
                    CockpitBadge(
                        if (speed > maxLimit) "OVERSPEEDING (>35 KM/H)" else "SAFE URBAN SPEED COMPLIANT",
                        if (speed > maxLimit) CrimsonAlert else EmeraldPrimary
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CockpitButton(
                    text = "Test 25 km/h",
                    onClick = {
                        speed = 25
                        DriverRepository.pingLocation(28.6139, 77.2090, 0.0, 25.0 / 3.6)
                    },
                    modifier = Modifier.weight(1f),
                    isOutlined = true
                )
                CockpitButton(
                    text = "Test 38 km/h Alert",
                    onClick = {
                        speed = 38
                        DriverRepository.pingLocation(28.6139, 77.2090, 0.0, 38.0 / 3.6)
                        onShowToast("Auditory alert triggered: Speed > 35 km/h")
                    },
                    modifier = Modifier.weight(1f),
                    isOutlined = true
                )
            }
        }
    }
}

/**
 * Screen 28: Cabin Audio Safety Shield
 */
@Composable
fun AudioShieldScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    var isShieldActive by remember { mutableStateOf(false) }
    var isUpdating by remember { mutableStateOf(false) }
    val rideId by DriverRepository.currentRideId.collectAsState()
    val scope = rememberCoroutineScope()

    fun toggleShield(targetState: Boolean) {
        if (rideId.isBlank()) {
            isShieldActive = targetState
            onShowToast(if (targetState) "Audio Shield armed (local standby — no active ride)" else "Audio Shield stopped")
            return
        }
        isUpdating = true
        scope.launch {
            val res = DriverRepository.updateAudioShieldConsent(rideId, targetState)
            isUpdating = false
            if (res.isSuccess) {
                isShieldActive = targetState
                onShowToast(if (targetState) "Audio Shield consent synced & active" else "Audio Shield stopped")
            } else {
                onShowToast("Failed to sync Audio Shield: ${res.exceptionOrNull()?.message}")
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
            title = "Audio Safety Shield",
            subtitle = "Encrypted in-cabin audio recording for safety disputes",
            onBack = onNavigateBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            CockpitCard(glow = isShieldActive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Active Trip Audio Sentinel", color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            if (isShieldActive) "Recording active: AES-256 encrypted" else "Shield is idle",
                            color = if (isShieldActive) EmeraldPrimary else CockpitTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = isShieldActive,
                        enabled = !isUpdating,
                        onCheckedChange = { toggleShield(it) }
                    )
                }
            }

            CockpitButton(
                text = if (isUpdating) "SYNCING..." else if (isShieldActive) "STOP RECORDING" else "START AUDIO SHIELD",
                enabled = !isUpdating,
                onClick = { toggleShield(!isShieldActive) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Screen 29: Emergency SOS & Pilot 112
 */
@Composable
fun EmergencySosScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val rideId by DriverRepository.currentRideId.collectAsState()
    var isSending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Pilot Emergency SOS",
            subtitle = "Immediate 1-tap police & security response center broadcast",
            onBack = onNavigateBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CockpitCard(glow = true) {
                Text(
                    "Pressing SOS immediately alerts Security Operations Center (SOC) & Emergency Contacts.",
                    color = CockpitTextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }

            Button(
                enabled = !isSending,
                onClick = {
                    isSending = true
                    scope.launch {
                        val res = DriverRepository.triggerEmergencySos(rideId)
                        isSending = false
                        if (res.isSuccess) {
                            onShowToast(res.getOrNull() ?: "EMERGENCY 112 BROADCAST DISPATCHED!")
                        } else {
                            onShowToast("SOS alert error: ${res.exceptionOrNull()?.message}")
                        }
                    }
                },
                modifier = Modifier.size(180.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Emergency, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(if (isSending) "SENDING..." else "SOS 112", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                }
            }

            CockpitButton(
                text = "Cancel / False Alarm",
                onClick = onNavigateBack,
                modifier = Modifier.fillMaxWidth(),
                isOutlined = true
            )
        }
    }
}

/**
 * Screen 30: Low Battery Swap Depot
 */
@Composable
fun BatterySwapDepotScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val depots = listOf(
        SwapDepotItem("Sun Mobility Hub - Anand Vihar ISBT", "0.8 km", "12 Charged Packs Ready", EmeraldPrimary),
        SwapDepotItem("Battery Smart Depot - Preet Vihar Gate 1", "2.1 km", "7 Charged Packs Ready", EmeraldPrimary)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Battery Swap Depots",
            subtitle = "Smart swap stations for 2-minute battery replacement",
            onBack = onNavigateBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(depots) { depot ->
                CockpitCard {
                    Text(depot.name, color = CockpitTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(2.dp))
                    Text("${depot.distance} • ${depot.status}", color = depot.tint, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    CockpitButton(
                        text = "Reserve Pack & Navigate (${depot.distance})",
                        onClick = { onShowToast("Battery pack reserved at ${depot.name}") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Screen 45: Driver Incident Reporting
 */
@Composable
fun DriverIncidentScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val types = listOf(
        "Passenger Dispute / Fare Refusal" to "PASSENGER_DISPUTE",
        "Vehicle Breakdown / Flat Tyre" to "VEHICLE_BREAKDOWN",
        "Minor Road Collision / Scratch" to "ROAD_COLLISION"
    )
    var selectedIndex by remember { mutableIntStateOf(0) }
    var descriptionText by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    val rideId by DriverRepository.currentRideId.collectAsState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Report Incident",
            subtitle = "File formal report to safety compliance team",
            onBack = onNavigateBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                CockpitCard {
                    Text("INCIDENT CATEGORY", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    types.forEachIndexed { idx, (label, _) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedIndex == idx,
                                onClick = { selectedIndex = idx },
                                colors = RadioButtonDefaults.colors(selectedColor = ElectricCyan)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label, color = CockpitTextPrimary, fontSize = 13.sp)
                        }
                    }
                }
            }

            item {
                CockpitTextField(
                    value = descriptionText,
                    onValueChange = { descriptionText = it },
                    label = "Detailed Incident Description (min 10 chars)",
                    placeholder = "Describe what occurred..."
                )
            }

            item {
                CockpitButton(
                    text = if (isSubmitting) "Submitting..." else "Submit Incident Report",
                    enabled = !isSubmitting,
                    onClick = {
                        if (descriptionText.trim().length < 10) {
                            onShowToast("Please enter at least 10 characters in description.")
                            return@CockpitButton
                        }
                        isSubmitting = true
                        val categoryCode = types[selectedIndex].second
                        scope.launch {
                            val res = DriverRepository.submitDriverIncident(
                                rideId = rideId,
                                category = categoryCode,
                                description = descriptionText
                            )
                            isSubmitting = false
                            if (res.isSuccess) {
                                onShowToast("Incident report submitted (${res.getOrNull()}).")
                                onNavigateBack()
                            } else {
                                onShowToast("Failed to submit: ${res.exceptionOrNull()?.message}")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Screen 48: Driver Emergency Contacts
 */
@Composable
fun EmergencyContactsScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val contacts by DriverRepository.emergencyContacts.collectAsState()
    var newName by remember { mutableStateOf("") }
    var newRelation by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        DriverRepository.fetchEmergencyContacts()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Emergency Contacts",
            subtitle = "Family and kin notified during SOS / accidents",
            onBack = onNavigateBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                CockpitCard {
                    Text("ADD EMERGENCY CONTACT", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    CockpitTextField(value = newName, onValueChange = { newName = it }, placeholder = "Contact Name", label = "Full Name")
                    Spacer(Modifier.height(8.dp))
                    CockpitTextField(value = newRelation, onValueChange = { newRelation = it }, placeholder = "e.g. Spouse, Brother", label = "Relationship")
                    Spacer(Modifier.height(8.dp))
                    CockpitTextField(value = newPhone, onValueChange = { newPhone = it }, placeholder = "+91 98110 54321", label = "Phone Number")
                    Spacer(Modifier.height(10.dp))
                    CockpitButton(
                        text = if (isSaving) "Saving..." else "+ Save Emergency Contact",
                        enabled = !isSaving && newName.trim().length >= 2 && newPhone.trim().length >= 10,
                        onClick = {
                            isSaving = true
                            scope.launch {
                                val res = DriverRepository.addEmergencyContact(newName, newRelation, newPhone)
                                isSaving = false
                                if (res.isSuccess) {
                                    newName = ""
                                    newRelation = ""
                                    newPhone = ""
                                    onShowToast("Emergency contact saved!")
                                } else {
                                    onShowToast("Failed: ${res.exceptionOrNull()?.message}")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (contacts.isEmpty()) {
                item {
                    CockpitCard {
                        Text("No emergency contacts saved yet.", color = CockpitTextMuted, fontSize = 13.sp)
                    }
                }
            } else {
                items(contacts) { contact ->
                    CockpitCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(contact.name, color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("${contact.relationship} • ${contact.phoneNumber}", color = CockpitTextSecondary, fontSize = 12.sp)
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    val res = DriverRepository.deleteEmergencyContact(contact.id)
                                    if (res.isSuccess) {
                                        onShowToast("Contact removed")
                                    } else {
                                        val msg = res.exceptionOrNull()?.message ?: "Could not remove contact"
                                        onShowToast(msg)
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CrimsonAlert)
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class SwapDepotItem(val name: String, val distance: String, val status: String, val tint: Color)