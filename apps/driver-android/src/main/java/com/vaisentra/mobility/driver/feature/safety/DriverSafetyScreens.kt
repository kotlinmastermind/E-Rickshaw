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
import androidx.compose.ui.tooling.preview.Preview
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
                            val liveLoc = DriverRepository.driverLocation.value
                            val ride = DriverRepository.activeRide.value
                            val validCoords = when {
                                com.vaisentra.mobility.driver.data.NativeLocationEvaluator.isValidCoordinate(liveLoc.latitude, liveLoc.longitude) ->
                                    liveLoc.latitude to liveLoc.longitude
                                ride?.pickupLatitude != null && ride.pickupLongitude != null &&
                                    com.vaisentra.mobility.driver.data.NativeLocationEvaluator.isValidCoordinate(ride.pickupLatitude, ride.pickupLongitude) ->
                                    ride.pickupLatitude to ride.pickupLongitude
                                else -> null
                            }
                            if (validCoords == null) {
                                isSubmitting = false
                                onShowToast("Acquiring live GPS fix; please try again once GPS locks.")
                                return@launch
                            }
                            val res = DriverRepository.reportRouteDeviation(rideId, validCoords.first, validCoords.second, reason = selectedReason)
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
        "Minor Road Collision / Scratch" to "ROAD_COLLISION",
        "Customer Misconduct / Rude Behavior" to "CUSTOMER_BEHAVIOR",
        "Unsafe Road Condition / Waterlogging" to "ROAD_HAZARD"
    )
    var selectedIndex by remember { mutableIntStateOf(0) }
    var descriptionText by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    val rideId by DriverRepository.currentRideId.collectAsState()
    val liveIncidents by DriverRepository.driverIncidents.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        DriverRepository.fetchDriverIncidents()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Report Incident",
            subtitle = "Supabase compliance & safety reports",
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
                                colors = RadioButtonDefaults.colors(selectedColor = PilotElectricCyan)
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
                    text = if (isSubmitting) "Submitting to Supabase..." else "Submit Incident Report",
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
                                descriptionText = ""
                                onShowToast("Incident report submitted (${res.getOrNull()}).")
                                DriverRepository.fetchDriverIncidents()
                            } else {
                                onShowToast("Failed to submit: ${res.exceptionOrNull()?.message}")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("FILED INCIDENT REPORTS", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Table: incident_reports", color = PilotElectricCyan, fontSize = 10.sp)
                }
            }

            if (liveIncidents.isEmpty()) {
                item {
                    CockpitCard {
                        Text(
                            text = "No incidents reported yet. Any roadside disputes, breakdowns, or collisions filed will be securely logged in Supabase.",
                            color = CockpitTextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                items(liveIncidents) { inc ->
                    val isResolved = inc.status.uppercase() == "RESOLVED"
                    val isInvestigating = inc.status.uppercase() == "INVESTIGATING"
                    val badgeColor = when {
                        isResolved -> PilotOnlineGreen
                        isInvestigating -> PilotAmber
                        else -> PilotElectricCyan
                    }

                    CockpitCard(cornerRadius = 12.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = inc.ticketNumber.ifBlank { "INC-REPORT" },
                                color = CockpitTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            CockpitBadge(
                                text = inc.status,
                                backgroundColor = badgeColor.copy(alpha = 0.2f),
                                textColor = badgeColor
                            )
                        }

                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${inc.category.replace("_", " ")} • Severity: ${inc.severity}",
                            color = PilotAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = inc.description,
                            color = CockpitTextSecondary,
                            fontSize = 12.sp
                        )

                        if (!inc.createdAt.isNullOrBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Reported: ${inc.createdAt.take(19).replace("T", " ")}",
                                color = CockpitTextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
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

@Preview(showBackground = true)
@Composable
fun RouteDeviationScreenPreview() {
    DriverGoTheme {
        RouteDeviationScreen()
    }
}


@Preview(showBackground = true)
@Composable
fun AudioShieldScreenPreview() {
    DriverGoTheme {
        AudioShieldScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun EmergencySosScreenPreview() {
    DriverGoTheme {
        EmergencySosScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun DriverIncidentScreenPreview() {
    DriverGoTheme {
        DriverIncidentScreen()
    }
}