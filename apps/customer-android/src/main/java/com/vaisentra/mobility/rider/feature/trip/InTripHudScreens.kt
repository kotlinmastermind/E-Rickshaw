package com.vaisentra.mobility.rider.feature.trip

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
import com.vaisentra.mobility.rider.data.RiderRepository
import com.vaisentra.mobility.rider.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Screen 23: Dispatch Radar & Pilot Matching
@Composable
fun DispatchRadarScreen(onNavigate: (String) -> Unit) {
    LaunchedEffect(Unit) {
        delay(3000)
        RiderRepository.updateActiveRideStatus("ASSIGNED")
        onNavigate("pilot_assigned")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        NeoHeader(title = "Dispatching Fleet", subtitle = "Cascading H3 matching within 1.5 km", onBack = { onNavigate("map_home") })

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(SapphireGlow)
                    .border(2.dp, BorderFocus, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "📡", fontSize = 64.sp)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "Contacting Nearby Pilots...", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Scoring best rating, EV battery level, and shortest ETA.", color = TextMuted, fontSize = 13.sp)
        }

        NeoCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Standard E-Rickshaw", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                NeoBadge(text = "₹129.04 LOCKED", backgroundColor = EmeraldGlow, textColor = EmeraldGreen)
            }
        }
    }
}

// Screen 24: Pilot Assigned & ETA Tracker
@Composable
fun PilotAssignedScreen(onNavigate: (String) -> Unit) {
    val activeRide by RiderRepository.activeRide.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        NeoHeader(title = "Pilot En-Route", subtitle = "ETA 3 Mins (450m away)", onBack = { onNavigate("map_home") })
        Spacer(modifier = Modifier.height(16.dp))

        // Chauffeur Identity Card
        NeoCard(borderColor = BorderFocus) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(SapphireGlow),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "👨‍✈️", fontSize = 32.sp)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = activeRide?.driverName ?: "Ram Lal (EV Pilot)", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(text = "4.95 ★ • 2,420 Completed Rides", color = AmberGold, fontSize = 12.sp)
                    Text(text = activeRide?.vehicleModel ?: "Mahindra Treo Electric", color = TextMuted, fontSize = 12.sp)
                }
                NeoBadge(text = activeRide?.vehiclePlate ?: "DL-1ER-4921", backgroundColor = SurfaceDark, textColor = ElectricCyan)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeoButton(
                text = "Chat Pilot",
                onClick = { onNavigate("in_trip_chat") },
                modifier = Modifier.weight(1f),
                backgroundColor = SurfaceElevated,
                icon = "💬"
            )
            NeoButton(
                text = "Vehicle RC & PUC",
                onClick = { onNavigate("driver_rc") },
                modifier = Modifier.weight(1f),
                backgroundColor = SurfaceElevated,
                icon = "📋"
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        NeoCard {
            Text(text = "Boarding Security OTP", color = TextMuted, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = "Share this 6-digit code with your pilot upon boarding:", color = TextWhite, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(12.dp))
            OtpDisplayBox(otp = activeRide?.otpCode ?: "729481")
        }

        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeoButton(
                text = "Cancel Ride",
                onClick = {
                    val rideId = activeRide?.id ?: "ride-latest"
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        RiderRepository.cancelActiveRide(rideId)
                    }
                    DialogController.showToast("Ride cancelled in Supabase.")
                    onNavigate("map_home")
                },
                modifier = Modifier.weight(1f),
                backgroundColor = CrimsonRed
            )
            NeoButton(
                text = "Pilot Arrived →",
                onClick = {
                    RiderRepository.updateActiveRideStatus("ARRIVED")
                    onNavigate("pilot_arrived")
                },
                modifier = Modifier.weight(1f),
                backgroundColor = SapphirePrimary
            )
        }
    }
}

// Screen 25: Driver En-Route & Vehicle RC
@Composable
fun VehicleRcDocumentScreen(onNavigate: (String) -> Unit) {
    var vehicleData by remember { mutableStateOf<Map<String, Any>?>(null) }
    val rideId = RiderRepository.activeRide.value?.id ?: "ride-latest"

    LaunchedEffect(rideId) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val res = RiderRepository.getVehicleCredentials(rideId)
            res.getOrNull()?.let { vehicleData = it }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        NeoHeader(title = "Government RC & Fitness", subtitle = "Transport Dept Delhi Verification", onBack = { onNavigate("pilot_assigned") })
        Spacer(modifier = Modifier.height(16.dp))

        NeoCard {
            Text(text = "Vehicle Registration Certificate", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            val specs = listOf(
                "Plate Number" to (vehicleData?.get("license_plate")?.toString() ?: "DL-1ER-4921"),
                "Chassis Number" to (vehicleData?.get("chassis_number")?.toString() ?: "MA1TREO2026XX8291"),
                "Fuel Category" to "100% Battery Electric (Zero Emission)",
                "PUC Fitness Expiry" to "14-Dec-2027 (Active)",
                "Commercial Permit" to "All Delhi NCR Micro-Transit",
                "Insurance Status" to "Comprehensive Insured (Bajaj Allianz)"
            )
            specs.forEach { (k, v) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = k, color = TextMuted, fontSize = 12.sp)
                    Text(text = v, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// Screen 26: Pilot Arrived & Meeting Point Buzzer
@Composable
fun PilotArrivedScreen(onNavigate: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
    ) {
        NeoHeader(title = "Pilot Arrived", subtitle = "Waiting at Connaught Place Gate 2", onBack = { onNavigate("pilot_assigned") })
        Spacer(modifier = Modifier.height(16.dp))

        NeoCard(borderColor = EmeraldGreen) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🎯", fontSize = 32.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = "Vehicle Waiting Outside", color = EmeraldGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Plate: DL-1ER-4921 (Green E-Rickshaw)", color = TextWhite, fontSize = 13.sp)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            NeoButton(
                text = "Sound Courtesy Horn / Buzzer 🔔",
                onClick = { DialogController.show(RiderPopupType.PILOT_ARRIVED) },
                backgroundColor = SapphirePrimary
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        NeoButton(
            text = "Boarded Vehicle & Start Trip →",
            onClick = {
                RiderRepository.updateActiveRideStatus("IN_PROGRESS")
                onNavigate("telemetry_hud")
            },
            backgroundColor = EmeraldGreen
        )
    }
}

// Screen 27: Boarding & 4-Digit Security OTP
@Composable
fun BoardingOtpScreen(onNavigate: (String) -> Unit) {
    val activeRide by RiderRepository.activeRide.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "🔒 Start Trip Security PIN", color = TextWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Tell this OTP to pilot to verify your identity and start trip meters:", color = TextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(24.dp))
        OtpDisplayBox(otp = activeRide?.otpCode ?: "729481")
        Spacer(modifier = Modifier.height(30.dp))
        NeoButton(text = "Trip Started → View Telemetry", onClick = { onNavigate("telemetry_hud") })
    }
}

// Screen 28: Live Telemetry & Speedometer HUD
@Composable
fun TelemetryHudScreen(onNavigate: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        NeoHeader(
            title = "Trip In-Progress HUD",
            subtitle = "Active EV Corridor Telemetry",
            actionIcon = "🚨",
            onAction = { DialogController.show(RiderPopupType.SOS_112) }
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Speedometer Digital Dial
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceCard)
                .border(1.dp, BorderFocus, RoundedCornerShape(24.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "CURRENT SPEED", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(text = "28", color = ElectricCyan, fontSize = 54.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "km/h", color = TextDim, fontSize = 16.sp, modifier = Modifier.padding(bottom = 10.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
                NeoBadge(text = "SPEED LIMIT: 35 KM/H (CONFORMANT ✓)", backgroundColor = EmeraldGlow, textColor = EmeraldGreen)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        NeoCard {
            Text(text = "Live Journey Metrics", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(text = "Distance Travelled", color = TextMuted, fontSize = 11.sp)
                    Text(text = "2.1 / 3.96 km", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text(text = "Time Remaining", color = TextMuted, fontSize = 11.sp)
                    Text(text = "6 mins", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text(text = "Trip Meter", color = TextMuted, fontSize = 11.sp)
                    Text(text = "₹129.04", color = ElectricCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeoButton(
                text = "Share Live Trip",
                onClick = { onNavigate("safety_share") },
                modifier = Modifier.weight(1f),
                backgroundColor = SurfaceElevated,
                icon = "🔗"
            )
            NeoButton(
                text = "Cabin Audio SOS",
                onClick = { DialogController.show(RiderPopupType.AUDIO_CONSENT) },
                modifier = Modifier.weight(1f),
                backgroundColor = SurfaceElevated,
                icon = "🎙️"
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        NeoButton(
            text = "Complete Trip & View Receipt →",
            onClick = {
                RiderRepository.updateActiveRideStatus("COMPLETED")
                onNavigate("ride_receipt")
            },
            backgroundColor = EmeraldGreen
        )
    }
}

// Screen 34: In-Trip Pilot Chat & Auto-Translate
@Composable
fun InTripChatScreen(onNavigate: (String) -> Unit) {
    var chatInput by remember { mutableStateOf("") }
    val chatHistory = remember {
        mutableStateListOf(
            Pair("Pilot Ram Lal", "नमस्ते Sir, I am waiting near Pillar 42."),
            Pair("You", "Coming in 1 minute, please wait.")
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
    ) {
        NeoHeader(title = "Chat with Pilot", subtitle = "2-Way English ⇌ Hindi Auto-Translate", onBack = { onNavigate("pilot_assigned") })
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(chatHistory) { (sender, text) ->
                val isMe = sender == "You"
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                ) {
                    NeoCard(
                        backgroundColor = if (isMe) SapphirePrimary else SurfaceCard,
                        cornerRadius = 14.dp,
                        modifier = Modifier.widthIn(max = 280.dp)
                    ) {
                        Text(text = sender, color = if (isMe) TextWhite else ElectricCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = text, color = TextWhite, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                NeoTextField(value = chatInput, onValueChange = { chatInput = it }, placeholder = "Type message in English or Hindi...")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SapphirePrimary)
                    .clickable {
                        if (chatInput.isNotBlank()) {
                            val msgToSend = chatInput
                            chatHistory.add(Pair("You", msgToSend))
                            chatInput = ""
                            val rideId = RiderRepository.activeRide.value?.id ?: "ride-latest"
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                RiderRepository.sendInTripChatMessage(rideId, msgToSend)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) { Text(text = "➤", color = TextWhite, fontSize = 18.sp) }
        }
    }
}

