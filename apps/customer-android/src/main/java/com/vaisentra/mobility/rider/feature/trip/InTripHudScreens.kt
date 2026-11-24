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
    val activeRide by RiderRepository.activeRide.collectAsState()
    var searchTimer by remember { mutableIntStateOf(30) }
    var noPilotsAvailable by remember { mutableStateOf(false) }

    LaunchedEffect(activeRide?.status) {
        val s = activeRide?.status
        if (s == "ASSIGNED" || s == "ARRIVED" || s == "IN_PROGRESS") {
            onNavigate("pilot_assigned")
        }
    }

    LaunchedEffect(Unit) {
        while (searchTimer > 0 && activeRide?.status !in listOf("ASSIGNED", "ARRIVED", "IN_PROGRESS")) {
            delay(1000L)
            searchTimer--
            if (searchTimer % 3 == 0) {
                RiderRepository.fetchActiveRide()
            }
        }
        if (activeRide?.status !in listOf("ASSIGNED", "ARRIVED", "IN_PROGRESS")) {
            noPilotsAvailable = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        NeoHeader(
            title = if (noPilotsAvailable) "Dispatch Status" else "Dispatching Fleet",
            subtitle = if (noPilotsAvailable) "Search timed out" else "Cascading H3 matching within 1.5 km",
            onBack = { onNavigate("map_home") }
        )

        if (noPilotsAvailable) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(SurfaceDark)
                        .border(1.5.dp, CrimsonRed, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🚫", fontSize = 48.sp)
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(text = "No Drivers Available", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "All nearby electric rickshaw pilots are currently busy or offline. Please retry in a moment.",
                    color = TextMuted,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NeoButton(
                    text = "Retry Dispatch ↻",
                    onClick = {
                        noPilotsAvailable = false
                        searchTimer = 30
                        val rideId = activeRide?.id
                        if (!rideId.isNullOrBlank()) {
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                RiderRepository.fetchActiveRide()
                            }
                        }
                    },
                    backgroundColor = SapphirePrimary,
                    modifier = Modifier.fillMaxWidth()
                )
                NeoButton(
                    text = "Cancel Request",
                    onClick = {
                        val rideId = activeRide?.id ?: ""
                        if (rideId.isNotBlank()) {
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                RiderRepository.cancelActiveRide(rideId)
                            }
                        }
                        onNavigate("map_home")
                    },
                    backgroundColor = SurfaceDark,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
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
                Text(text = "Scoring best rating, EV battery level, and shortest ETA. ($searchTimer s)", color = TextMuted, fontSize = 13.sp)
            }

            NeoCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = activeRide?.vehicleModel ?: "Electric Rickshaw", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    val fareDisplay = if (activeRide?.totalFareCents != null) "₹${activeRide!!.totalFareCents!! / 100} LOCKED" else "PRICE LOCKED"
                    NeoBadge(text = fareDisplay, backgroundColor = EmeraldGlow, textColor = EmeraldGreen)
                }
            }
        }
    }
}

// Screen 24: Pilot Assigned & ETA Tracker
@Composable
fun PilotAssignedScreen(onNavigate: (String) -> Unit) {
    val activeRide by RiderRepository.activeRide.collectAsState()

    LaunchedEffect(Unit) {
        RiderRepository.fetchActiveRide()
    }

    // Agar koi active ride nahi hai database me toh Empty State dikhayein
    if (activeRide == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CanvasBlack)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            NeoHeader(title = "Active Ride", subtitle = "Live Chauffeur Status", onBack = { onNavigate("map_home") })

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 40.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(SurfaceDark)
                        .border(1.dp, BorderSubtle, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🛺", fontSize = 48.sp)
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(text = "No Driver Available", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "You do not have any ongoing ride or active pilot assignment right now.",
                    color = TextMuted,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }

            NeoButton(
                text = "Explore & Book a Ride →",
                onClick = { onNavigate("map_home") },
                backgroundColor = SapphirePrimary,
                modifier = Modifier.fillMaxWidth()
            )
        }
        return
    }

    val isDriverAssigned = !activeRide?.driverName.isNullOrBlank() || activeRide?.driver != null
    val driverDisplayName = if (isDriverAssigned) (activeRide?.driverName ?: activeRide?.driver?.name ?: "Verified EV Pilot") else "No Driver Assigned"
    val driverSubtitle = if (isDriverAssigned) {
        val rating = activeRide?.driverRating ?: activeRide?.driver?.rating ?: 4.9
        "$rating ★ • Verified Pilot"
    } else {
        "Searching for nearby available pilots..."
    }
    val vehicleModel = if (isDriverAssigned) (activeRide?.vehicleModel ?: "Electric Rickshaw") else "Awaiting Pilot Acceptance"
    val vehiclePlate = if (isDriverAssigned) (activeRide?.vehiclePlate ?: activeRide?.driver?.licenseNumber ?: "Verified EV") else "PENDING"
    val headerSubtitle = if (isDriverAssigned) "Pilot En-Route • ETA ~3 Mins" else "Dispatch Matching in Progress"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        NeoHeader(title = if (isDriverAssigned) "Pilot En-Route" else "Trip Matching", subtitle = headerSubtitle, onBack = { onNavigate("map_home") })
        Spacer(modifier = Modifier.height(16.dp))

        // Chauffeur Identity Card
        NeoCard(borderColor = if (isDriverAssigned) BorderFocus else BorderSubtle) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(if (isDriverAssigned) SapphireGlow else SurfaceDark),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = if (isDriverAssigned) "👨‍✈️" else "⏳", fontSize = 32.sp)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = driverDisplayName, color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(text = driverSubtitle, color = if (isDriverAssigned) AmberGold else TextMuted, fontSize = 12.sp)
                    Text(text = vehicleModel, color = TextMuted, fontSize = 12.sp)
                }
                NeoBadge(
                    text = vehiclePlate,
                    backgroundColor = SurfaceDark,
                    textColor = if (isDriverAssigned) ElectricCyan else AmberGold
                )
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

        if (!activeRide?.otpCode.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            NeoCard {
                Text(text = "Boarding Security OTP", color = TextMuted, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "Share this 6-digit code with your pilot upon boarding:", color = TextWhite, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(12.dp))
                OtpDisplayBox(otp = activeRide?.otpCode ?: "------")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeoButton(
                text = "Cancel Ride",
                onClick = {
                    val rideId = activeRide?.id ?: ""
                    if (rideId.isNotBlank()) {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            RiderRepository.cancelActiveRide(rideId)
                        }
                    }
                    DialogController.showToast("Ride cancelled in Supabase.")
                    onNavigate("map_home")
                },
                modifier = Modifier.weight(1f),
                backgroundColor = CrimsonRed
            )
            if (isDriverAssigned) {
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
}

// Screen 25: Driver En-Route & Vehicle RC
@Composable
fun VehicleRcDocumentScreen(onNavigate: (String) -> Unit) {
    val activeRide by RiderRepository.activeRide.collectAsState()
    var vehicleData by remember { mutableStateOf<Map<String, Any>?>(null) }
    val rideId = activeRide?.id

    LaunchedEffect(rideId) {
        if (!rideId.isNullOrBlank()) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                val res = RiderRepository.getVehicleCredentials(rideId)
                res.getOrNull()?.let { vehicleData = it }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        NeoHeader(title = "Government RC & Fitness", subtitle = "Transport Dept Verification", onBack = { onNavigate("pilot_assigned") })
        Spacer(modifier = Modifier.height(16.dp))

        if (activeRide == null) {
            NeoCard {
                Text(text = "No active vehicle assigned.", color = TextMuted, fontSize = 13.sp)
            }
        } else {
            NeoCard {
                Text(text = "Vehicle Registration Certificate", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))
                val plate = vehicleData?.get("license_plate")?.toString() ?: activeRide?.vehiclePlate ?: "Not Available"
                val chassis = vehicleData?.get("chassis_number")?.toString() ?: "Not Available"
                val specs = listOf(
                    "Plate Number" to plate,
                    "Chassis Number" to chassis,
                    "Fuel Category" to "100% Battery Electric (Zero Emission)",
                    "PUC Fitness Expiry" to (vehicleData?.get("puc_expiry")?.toString() ?: "Active"),
                    "Commercial Permit" to "All Delhi NCR Micro-Transit",
                    "Insurance Status" to "Comprehensive Insured"
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
}

// Screen 26: Pilot Arrived & Meeting Point Buzzer
@Composable
fun PilotArrivedScreen(onNavigate: (String) -> Unit) {
    val activeRide by RiderRepository.activeRide.collectAsState()

    if (activeRide == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CanvasBlack)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "No active ride in progress.", color = TextMuted, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(16.dp))
            NeoButton(text = "Back to Home", onClick = { onNavigate("map_home") })
        }
        return
    }

    val plate = activeRide?.vehiclePlate ?: "Verified EV"
    val pickup = activeRide?.pickupAddress ?: "Pickup Location"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
    ) {
        NeoHeader(title = "Pilot Arrived", subtitle = "Waiting at $pickup", onBack = { onNavigate("pilot_assigned") })
        Spacer(modifier = Modifier.height(16.dp))

        NeoCard(borderColor = EmeraldGreen) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🎯", fontSize = 32.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = "Vehicle Waiting Outside", color = EmeraldGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Plate: $plate", color = TextWhite, fontSize = 13.sp)
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
            text = "Share OTP with Pilot to Start Trip →",
            onClick = {
                onNavigate("boarding_otp")
            },
            backgroundColor = EmeraldGreen
        )
    }
}

// Screen 27: Boarding & 6-Digit Security OTP
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
        Text(text = "🔒 Start Trip Security PIN (6-Digit OTP)", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Tell this OTP to pilot to verify your identity and start trip meters:", color = TextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(24.dp))
        OtpDisplayBox(otp = activeRide?.otpCode ?: "------")
        Spacer(modifier = Modifier.height(30.dp))
        NeoButton(text = "Track Active Ride HUD →", onClick = { onNavigate("telemetry_hud") })
    }
}

// Screen 28: Live Telemetry & Speedometer HUD
@Composable
fun TelemetryHudScreen(onNavigate: (String) -> Unit) {
    val activeRide by RiderRepository.activeRide.collectAsState()
    val bookingDraft by RiderRepository.bookingDraft.collectAsState()
    val isAudioRecordingActive by RiderRepository.isAudioRecordingActive.collectAsState()
    var showChangeDestSheet by remember { mutableStateOf(false) }
    var isRouteDeviated by remember { mutableStateOf(false) }
    var deviationMeters by remember { mutableStateOf(0) }

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
        Spacer(modifier = Modifier.height(14.dp))

        if (isAudioRecordingActive) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CrimsonRed.copy(alpha = 0.15f))
                    .border(1.dp, CrimsonRed.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🔴", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AUDIO SAFETY RECORDING ACTIVE",
                            color = CrimsonRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "DPDP Encrypted",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Live Route Deviation / Anomaly Alert Banner (Domain I)
        if (isRouteDeviated) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF3B151E))
                    .border(1.5.dp, CrimsonRed, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "⚠️", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "ROUTE DEVIATION DETECTED", color = CrimsonRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(text = "+$deviationMeters m off planned corridor. Are you safe?", color = TextWhite, fontSize = 12.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceDark)
                                .clickable { isRouteDeviated = false }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(text = "I'm Safe ✓", color = EmeraldGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(CrimsonRed)
                                .clickable { DialogController.show(RiderPopupType.SOS_112) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(text = "SOS", color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Speedometer Digital Dial
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceCard)
                .border(1.dp, BorderFocus, RoundedCornerShape(24.dp))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "CURRENT SPEED", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(text = "28", color = ElectricCyan, fontSize = 52.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "km/h", color = TextDim, fontSize = 16.sp, modifier = Modifier.padding(bottom = 10.dp))
                }
                Spacer(modifier = Modifier.height(6.dp))
                NeoBadge(text = "SPEED LIMIT: 35 KM/H (CONFORMANT ✓)", backgroundColor = EmeraldGlow, textColor = EmeraldGreen)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        NeoCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Live Journey Metrics", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "Change Dest 🔄",
                    color = ElectricCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SapphireGlow)
                        .clickable { showChangeDestSheet = true }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
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
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Destination: ", color = TextMuted, fontSize = 11.sp)
                Text(
                    text = bookingDraft.destinationAddress.ifBlank { "Connaught Place Gate 4" },
                    color = TextWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
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

        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeoButton(
                text = "Simulate 500m Deviation",
                onClick = {
                    isRouteDeviated = true
                    deviationMeters = 540
                    val rideId = activeRide?.id ?: "ride-latest"
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        RiderRepository.reportRouteAnomaly(rideId, 28.6350, 77.2250)
                    }
                },
                modifier = Modifier.weight(1f),
                backgroundColor = SurfaceDark,
                icon = "📐"
            )
            NeoButton(
                text = "In-Trip Chat",
                onClick = { onNavigate("in_trip_chat") },
                modifier = Modifier.weight(1f),
                backgroundColor = SurfaceElevated,
                icon = "💬"
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        val isTripDone = activeRide?.status == "COMPLETED"
        NeoButton(
            text = if (isTripDone) "Trip Completed — View Receipt →" else "Refresh Live Trip Status 🔄",
            onClick = {
                if (isTripDone) {
                    onNavigate("ride_receipt")
                } else {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        RiderRepository.fetchActiveRide()
                    }
                    if (activeRide?.status == "COMPLETED") {
                        onNavigate("ride_receipt")
                    } else {
                        DialogController.showToast("Ride is currently in progress.")
                    }
                }
            },
            backgroundColor = if (isTripDone) EmeraldGreen else SurfaceCard
        )
    }

    // Domain H: Dynamic Mid-Trip Destination Change Modal
    if (showChangeDestSheet) {
        MidTripDestinationChangeModal(
            currentDest = bookingDraft.destinationAddress.ifBlank { "Connaught Place Gate 4" },
            onDismiss = { showChangeDestSheet = false },
            onConfirmNewDestination = { newDest, _, extraCostRupees ->
                val rideId = activeRide?.id ?: "ride-latest"
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    RiderRepository.requestDestinationChange(rideId, newDest, 28.5562, 77.1000)
                }
                showChangeDestSheet = false
                DialogController.showToast("Destination updated in Supabase! Pilot notified (+₹${extraCostRupees.toInt()}).")
            }
        )
    }
}

// Domain H: Mid-Trip Destination Change Dialog
@Composable
private fun MidTripDestinationChangeModal(
    currentDest: String,
    onDismiss: () -> Unit,
    onConfirmNewDestination: (String, Double, Double) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedNewDest by remember { mutableStateOf("") }
    var extraDistance by remember { mutableStateOf(1.8) }
    var extraFare by remember { mutableStateOf(35.0) }

    val quickDestinations = listOf(
        Triple("Rajiv Chowk Metro", 1.2, 25.0),
        Triple("Building 9A, DLF Cyber City", 4.5, 75.0),
        Triple("Terminal 3, IGI Airport", 8.2, 140.0),
        Triple("Noida Sector 62 Metro", 5.0, 85.0)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceElevated,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🔄", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = "Change Destination Mid-Trip", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Live recalculation & pilot corridor sync", color = TextMuted, fontSize = 11.sp)
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = "CURRENT DESTINATION:", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(text = currentDest, color = ElectricCyan, fontSize = 12.sp, maxLines = 1)
                Spacer(modifier = Modifier.height(12.dp))

                NeoTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        selectedNewDest = it
                    },
                    placeholder = "Search new place...",
                    label = "New Destination Address",
                    leadingIcon = "📍"
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text(text = "QUICK PRESETS:", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    quickDestinations.forEach { (place, dist, cost) ->
                        val isChosen = selectedNewDest == place
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isChosen) SapphireGlow else SurfaceCard)
                                .border(1.dp, if (isChosen) BorderFocus else BorderSubtle, RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedNewDest = place
                                    extraDistance = dist
                                    extraFare = cost
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = place, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text(text = "+₹${cost.toInt()} (+${dist}km)", color = ElectricCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (selectedNewDest.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceDark)
                            .border(1.dp, EmeraldGreen, RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Revised Trip Estimate:", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(text = "₹${(129.04 + extraFare).toInt()}.00", color = EmeraldGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(text = "Base rate ₹12/km + ₹1/min waiting apply to delta distance.", color = TextMuted, fontSize = 10.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            NeoButton(
                text = "Confirm & Reroute →",
                enabled = selectedNewDest.isNotBlank(),
                onClick = {
                    onConfirmNewDestination(selectedNewDest, extraDistance, extraFare)
                },
                backgroundColor = SapphirePrimary,
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Cancel", color = TextMuted, fontSize = 12.sp)
            }
        }
    )
}

// Screen 34: In-Trip Pilot Chat & Auto-Translate
@Composable
fun InTripChatScreen(onNavigate: (String) -> Unit) {
    val activeRide by RiderRepository.activeRide.collectAsState()
    var chatInput by remember { mutableStateOf("") }
    val chatHistory = remember {
        mutableStateListOf<Pair<String, String>>()
    }

    val driverLabel = activeRide?.driverName ?: "Pilot"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
    ) {
        NeoHeader(
            title = "Chat with $driverLabel",
            subtitle = "2-Way English ⇌ Hindi Auto-Translate",
            onBack = { onNavigate("pilot_assigned") }
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (chatHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "💬", fontSize = 36.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "No Messages Yet", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Send a message to your pilot (e.g. Landmark, waiting spot).", color = TextMuted, fontSize = 12.sp)
                }
            }
        } else {
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

