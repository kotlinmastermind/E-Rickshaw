package com.vaisentra.mobility.driver.feature.safety

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
 * Screen 23: Route Deviation & Anomaly Alarm (unexpected detours & delay detection)
 */
@Composable
fun RouteDeviationScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    var isDeviationAcknowledged by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Route Deviation Alert",
            subtitle = "Automatic anomaly watchdog: 340m off course",
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
                    val reasons = listOf(
                        "Road block / municipal construction ahead",
                        "Passenger requested alternate drop point",
                        "Severe traffic / waterlogged bypass",
                        "Accidental missed turn"
                    )
                    reasons.forEach { reason ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isDeviationAcknowledged,
                                onClick = { isDeviationAcknowledged = true },
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
                    text = "CONFIRM DETOUR REASON & UPDATE ROUTE",
                    onClick = {
                        onShowToast("Detour reason logged in Supabase trips_audit")
                        onNavigateBack()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Screen 27: Speedometer & Urban Limits (35 km/h urban limit gauge)
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

            CockpitCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("MUNICIPAL SPEED CAP", color = CockpitTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("35 km/h limit", color = CockpitTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("ACCELERATION G-FORCE", color = CockpitTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("0.08 G (Smooth)", color = EmeraldPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CockpitButton(
                    text = "Test 25 km/h",
                    onClick = { speed = 25 },
                    modifier = Modifier.weight(1f),
                    isOutlined = true
                )
                CockpitButton(
                    text = "Test 38 km/h Alert",
                    onClick = {
                        speed = 38
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
 * Screen 28: Cabin Audio Safety Shield (encrypted ambient audio capture)
 */
@Composable
fun AudioShieldScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    var isShieldActive by remember { mutableStateOf(false) }

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
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CockpitCard(glow = isShieldActive) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Active Trip Audio Sentinel", color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                if (isShieldActive) "Recording active: AES-256 encrypted" else "Shield is idle (tap toggle to arm)",
                                color = if (isShieldActive) EmeraldPrimary else CockpitTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = isShieldActive,
                            onCheckedChange = {
                                isShieldActive = it
                                onShowToast(if (it) "Audio Shield activated" else "Audio Shield stopped")
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = EmeraldPrimary,
                                checkedTrackColor = EmeraldPrimary.copy(alpha = 0.3f)
                            )
                        )
                    }
                }

                CockpitCard {
                    Text("PRIVACY & ENCRYPTION GUARANTEE", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "• Audio files are encrypted on device with asymmetric public key\n• Pilot and passenger cannot play back recordings\n• Files are only unlocked by safety audit officers upon dispute/FIR filing\n• Auto-deleted after 7 days if no safety flag is raised",
                        color = CockpitTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            CockpitButton(
                text = if (isShieldActive) "STOP RECORDING" else "START AUDIO SHIELD",
                onClick = {
                    isShieldActive = !isShieldActive
                    onShowToast(if (isShieldActive) "Audio shield armed" else "Audio shield disarmed")
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Screen 29: Emergency SOS & Pilot 112 (1-tap panic trigger to Delhi Police 112)
 */
@Composable
fun EmergencySosScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
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
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("CRITICAL EMERGENCY DISPATCH", color = CrimsonAlert, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Pressing the SOS button immediately:\n1. Dials Delhi Police Control Room (112)\n2. Sends live GPS coordinates to Driver Emergency Contacts\n3. Triggers 24x7 Security Operations Center (SOC) agent callback",
                        color = CockpitTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Big SOS Button
            Button(
                onClick = {
                    onShowToast("EMERGENCY 112 BROADCAST DISPATCHED!")
                },
                modifier = Modifier.size(180.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Emergency, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(4.dp))
                    Text("SOS 112", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
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
 * Screen 30: Low Battery Swap Depot (route to Anand Vihar swap hub)
 */
@Composable
fun BatterySwapDepotScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val depots = listOf(
        SwapDepotItem("Sun Mobility Hub - Anand Vihar ISBT", "0.8 km", "12 Charged Packs Ready", EmeraldPrimary),
        SwapDepotItem("Battery Smart Depot - Preet Vihar Gate 1", "2.1 km", "7 Charged Packs Ready", EmeraldPrimary),
        SwapDepotItem("Bounce Infinity Station - Laxmi Nagar", "3.4 km", "3 Charged Packs Ready", AmberWarning)
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
            item {
                CockpitCard(glow = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("CURRENT VEHICLE BATTERY", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("16% SoC (Range: ~8 km)", color = CrimsonAlert, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        CockpitBadge("RESERVE WARNING", CrimsonAlert)
                    }
                }
            }

            items(depots) { depot ->
                CockpitCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(depot.name, color = CockpitTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Spacer(Modifier.height(2.dp))
                            Text("${depot.distance} • ${depot.status}", color = depot.tint, fontSize = 12.sp)
                        }
                    }
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
 * Screen 45: Driver Incident Reporting (collision, passenger dispute, mechanical fault)
 */
@Composable
fun DriverIncidentScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    var incidentType by remember { mutableStateOf("Passenger Dispute") }
    var descriptionText by remember { mutableStateOf("") }

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
                    val types = listOf("Passenger Dispute / Fare Refusal", "Vehicle Breakdown / Flat Tyre", "Minor Road Collision / Scratch", "Police Harassment / Challan")
                    types.forEach { type ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = incidentType == type,
                                onClick = { incidentType = type },
                                colors = RadioButtonDefaults.colors(selectedColor = ElectricCyan)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(type, color = CockpitTextPrimary, fontSize = 13.sp)
                        }
                    }
                }
            }

            item {
                CockpitTextField(
                    value = descriptionText,
                    onValueChange = { descriptionText = it },
                    label = "Detailed Incident Description",
                    placeholder = "Describe what occurred, location landmark, etc."
                )
            }

            item {
                CockpitButton(
                    text = "Submit Incident Report",
                    onClick = {
                        onShowToast("Incident filed. Ticket #INC-${(1000..9999).random()} created.")
                        onNavigateBack()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Screen 48: Driver Emergency Contacts (family emergency contacts management)
 */
@Composable
fun EmergencyContactsScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val contacts = listOf(
        EmergencyContact("Sunita Sharma", "Spouse", "+91 98110 54321", true),
        EmergencyContact("Ajay Sharma", "Brother", "+91 98712 99887", true),
        EmergencyContact("Mohan Lal", "Garage Partner", "+91 99100 12345", false)
    )

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
            items(contacts) { contact ->
                CockpitCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(contact.name, color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("${contact.relation} • ${contact.phone}", color = CockpitTextSecondary, fontSize = 12.sp)
                        }
                        if (contact.isPrimary) {
                            CockpitBadge("PRIMARY", EmeraldPrimary)
                        }
                    }
                }
            }

            item {
                CockpitButton(
                    text = "+ Add New Emergency Contact",
                    onClick = { onShowToast("Add contact form opened") },
                    modifier = Modifier.fillMaxWidth(),
                    isOutlined = true
                )
            }
        }
    }
}

private data class SwapDepotItem(
    val name: String,
    val distance: String,
    val status: String,
    val tint: Color
)

private data class EmergencyContact(
    val name: String,
    val relation: String,
    val phone: String,
    val isPrimary: Boolean
)

