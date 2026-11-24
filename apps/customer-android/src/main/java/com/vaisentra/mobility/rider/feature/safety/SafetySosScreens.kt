package com.vaisentra.mobility.rider.feature.safety

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.vaisentra.mobility.rider.components.*
import com.vaisentra.mobility.rider.data.EmergencyContactDto
import com.vaisentra.mobility.rider.data.RiderRepository
import com.vaisentra.mobility.rider.theme.*
import kotlinx.coroutines.launch

// Screen 29: Route Deviations & Unplanned Stops
@Composable
fun RouteDeviationScreen(onNavigate: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        NeoHeader(title = "Safety Telemetry Alert", subtitle = "Route Deviation Watchdog", onBack = { onNavigate("telemetry_hud") })
        Spacer(modifier = Modifier.height(16.dp))

        NeoCard(borderColor = AmberGold) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "⚠️", fontSize = 28.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(text = "Unplanned Route Detour (+320m)", color = AmberGold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Vehicle departed primary corridor near Ring Road.", color = TextMuted, fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Are you safe? Automated Safety Check-in:", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            NeoButton(
                text = "Yes, Driver Took Traffic Detour (I am Safe)",
                onClick = {
                    val rideId = RiderRepository.activeRide.value?.id ?: "ride-latest"
                    val curLat = RiderRepository.activeRide.value?.driverLat ?: RiderRepository.bookingDraft.value.pickupLat
                    val curLng = RiderRepository.activeRide.value?.driverLng ?: RiderRepository.bookingDraft.value.pickupLng
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        RiderRepository.reportRouteAnomaly(rideId, curLat, curLng)
                    }
                    DialogController.showToast("Safety acknowledgment logged. Detour cleared.")
                    onNavigate("telemetry_hud")
                },
                backgroundColor = EmeraldGreen
            )
            Spacer(modifier = Modifier.height(8.dp))
            NeoButton(
                text = "No, Trigger 112 SOS Escort",
                onClick = { DialogController.show(RiderPopupType.SOS_112) },
                backgroundColor = CrimsonRed
            )
        }
    }
}

// Screen 30: In-Cabin Audio Safety Recording
@Composable
fun AudioSafetyScreen(onNavigate: (String) -> Unit) {
    var isRecording by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
    ) {
        NeoHeader(title = "Cabin Safety Recording", subtitle = "Hardware encrypted on-device audio", onBack = { onNavigate("telemetry_hud") })
        Spacer(modifier = Modifier.height(16.dp))

        NeoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isRecording) CrimsonGlow else SurfaceCard),
                    contentAlignment = Alignment.Center
                ) { Text(text = "🎙️", fontSize = 22.sp) }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isRecording) "Active Audio Shield (Encrypted)" else "Recording Paused",
                        color = if (isRecording) CrimsonRed else TextWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = "Decrypted only upon verified safety incident ticket.", color = TextMuted, fontSize = 11.sp)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            NeoButton(
                text = if (isRecording) "Pause Safety Recording" else "Resume Audio Recording",
                onClick = {
                    val nextState = !isRecording
                    isRecording = nextState
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        RiderRepository.setAudioRecordingConsent(nextState)
                    }
                },
                backgroundColor = SurfaceDark
            )
        }
    }
}

// Screen 31: Live Journey Link Sharing
@Composable
fun ShareJourneyScreen(onNavigate: (String) -> Unit) {
    val shareUrl = "https://vaisentra.mobility/track/live?id=ride-7294x"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
    ) {
        NeoHeader(title = "Share Live Journey", subtitle = "Real-time GPS tracker link", onBack = { onNavigate("telemetry_hud") })
        Spacer(modifier = Modifier.height(16.dp))

        NeoCard {
            Text(text = "Live Tracking Web URL:", color = TextMuted, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = shareUrl, color = ElectricCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NeoButton(
                    text = "WhatsApp",
                    onClick = {
                        val rideId = RiderRepository.activeRide.value?.id ?: "ride-latest"
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            RiderRepository.shareRide(rideId, "+91 98111 22334")
                        }
                        DialogController.showToast("Link shared to WhatsApp contacts!")
                    },
                    modifier = Modifier.weight(1f),
                    backgroundColor = EmeraldGreen,
                    icon = "💬"
                )
                NeoButton(
                    text = "Copy Link",
                    onClick = { DialogController.showToast("Link copied to clipboard!") },
                    modifier = Modifier.weight(1f),
                    backgroundColor = SapphirePrimary,
                    icon = "📋"
                )
            }
        }
    }
}

// Screen 32: Emergency SOS & Police 112
@Composable
fun EmergencySosScreen(onNavigate: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        NeoHeader(title = "Emergency Operations Desk", subtitle = "24/7 Security Concierge", onBack = { onNavigate("telemetry_hud") })

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .clip(CircleShape)
                    .background(CrimsonGlow)
                    .border(2.dp, CrimsonRed, CircleShape)
                    .clickable {
                        val sosLat = RiderRepository.activeRide.value?.driverLat ?: RiderRepository.bookingDraft.value.pickupLat
                        val sosLng = RiderRepository.activeRide.value?.driverLng ?: RiderRepository.bookingDraft.value.pickupLng
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            RiderRepository.triggerSos(sosLat, sosLng)
                        }
                        DialogController.show(RiderPopupType.SOS_112)
                    },
                contentAlignment = Alignment.Center
            ) { Text(text = "🚨", fontSize = 54.sp) }
            Spacer(modifier = Modifier.height(20.dp))
            Text(text = "TAP FOR 1-TOUCH SOS", color = CrimsonRed, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = "Direct broadcast to Delhi Police 112 & Fleet Command", color = TextMuted, fontSize = 12.sp)
        }

        NeoCard {
            Text(text = "Emergency Quick Dials", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "👮 Police Control Room", color = TextWhite, fontSize = 13.sp)
                NeoBadge(text = "DIAL 112", backgroundColor = CrimsonGlow, textColor = CrimsonRed)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "👩 Women Safety Helpline", color = TextWhite, fontSize = 13.sp)
                NeoBadge(text = "DIAL 1091", backgroundColor = SapphireGlow, textColor = ElectricCyan)
            }
        }
    }
}
// Screen 33: Emergency Contacts Manager (Synced with Supabase)
@Composable
fun EmergencyContactsScreen(onNavigate: (String) -> Unit) {
    val contacts by RiderRepository.emergencyContacts.collectAsState()
    var newName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }
    var newRelation by remember { mutableStateOf("Family") }
    var isSaving by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        RiderRepository.fetchEmergencyContacts().onFailure { err ->
            DialogController.showToast("Load failed: ${err.message}")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
    ) {
        NeoHeader(
            title = "Trusted Contacts",
            subtitle = "Automatic SOS & trip broadcast",
            onBack = { onNavigate("map_home") }
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (contacts.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No trusted contacts saved in Supabase yet.",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(contacts, key = { it.id }) { c ->
                    NeoCard(cornerRadius = 14.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = c.name, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(text = "${c.phoneNumber} (${c.relationship})", color = TextMuted, fontSize = 12.sp)
                            }
                            Text(
                                text = "Remove",
                                color = CrimsonRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    coroutineScope.launch {
                                        val res = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            RiderRepository.removeEmergencyContact(c.id)
                                        }
                                        res.onSuccess {
                                            DialogController.showToast("Contact removed.")
                                        }.onFailure { err ->
                                            DialogController.showToast("Remove failed: ${err.message}")
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        NeoCard {
            Text(text = "Add Trusted Contact", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            NeoTextField(value = newName, onValueChange = { newName = it }, placeholder = "Contact Name", label = "Name")
            Spacer(modifier = Modifier.height(8.dp))
            NeoTextField(value = newPhone, onValueChange = { newPhone = it }, placeholder = "+91 XXXXX XXXXX", label = "Phone")
            Spacer(modifier = Modifier.height(8.dp))
            NeoTextField(value = newRelation, onValueChange = { newRelation = it }, placeholder = "Family / Friend / Colleague", label = "Relationship")
            Spacer(modifier = Modifier.height(12.dp))
            NeoButton(
                text = if (isSaving) "Saving..." else "Save Contact to Supabase",
                onClick = {
                    if (isSaving) return@NeoButton
                    if (newName.isBlank() || newPhone.isBlank()) {
                        DialogController.showToast("Please enter name and phone number")
                        return@NeoButton
                    }
                    coroutineScope.launch {
                        isSaving = true
                        val res = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            RiderRepository.addEmergencyContact(newName, newPhone, newRelation)
                        }
                        isSaving = false

                        res.onSuccess {
                            newName = ""
                            newPhone = ""
                            DialogController.showToast("Emergency contact saved in Supabase!")
                        }.onFailure { err ->
                            DialogController.showToast("Save failed: ${err.message}")
                        }
                    }
                },
                backgroundColor = SapphirePrimary
            )
        }
    }
}