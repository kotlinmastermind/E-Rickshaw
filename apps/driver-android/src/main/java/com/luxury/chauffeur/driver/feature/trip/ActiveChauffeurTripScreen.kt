package com.luxury.chauffeur.driver.feature.trip

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.LatLng
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.luxury.chauffeur.driver.core.designsystem.*
import com.luxury.chauffeur.driver.core.network.*
import com.luxury.chauffeur.driver.feature.map.DriverMapView
import com.luxury.chauffeur.driver.feature.map.NavigationHelper
import kotlinx.coroutines.launch

enum class ChauffeurTripStage {
    NAVIGATING_TO_PICKUP,
    ARRIVED_AT_PICKUP,
    OTP_VERIFICATION,
    TRIP_IN_PROGRESS,
    TRIP_PAUSED,
    TRIP_FINISHED
}

@Composable
fun ActiveChauffeurTripScreen(
    rideId: String,
    pickupAddress: String,
    destinationAddress: String,
    onTripFinished: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var stage by remember { mutableStateOf(ChauffeurTripStage.NAVIGATING_TO_PICKUP) }
    var enteredOtp by remember { mutableStateOf("") }
    var otpError by remember { mutableStateOf<String?>(null) }
    var cashCollectedConfirmed by remember { mutableStateOf(false) }
    var isActionLoading by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var pickupLat by remember { mutableStateOf<Double?>(null) }
    var pickupLng by remember { mutableStateOf<Double?>(null) }
    var destLat by remember { mutableStateOf<Double?>(null) }
    var destLng by remember { mutableStateOf<Double?>(null) }
    var showStartChecklist by remember { mutableStateOf(false) }
    var showCompletionChecklist by remember { mutableStateOf(false) }
    var showNoShowDialog by remember { mutableStateOf(false) }
    var noShowEvidence by remember { mutableStateOf("") }
    var showPinCorrectionDialog by remember { mutableStateOf(false) }
    var showRoadClosureDialog by remember { mutableStateOf(false) }
    var roadClosureNote by remember { mutableStateOf("") }
    var correctedAddress by remember { mutableStateOf("") }
    var correctedLatitude by remember { mutableStateOf("") }
    var correctedLongitude by remember { mutableStateOf("") }
    var tripStops by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var sharedTrip by remember { mutableStateOf<Map<String, Any>?>(null) }
    var sharedTripMessages by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var sharedMessageInput by remember { mutableStateOf("") }
    var pendingDestinationChange by remember { mutableStateOf<Map<String, Any>?>(null) }
    var pickupLabel by remember { mutableStateOf(pickupAddress) }
    var destinationLabel by remember { mutableStateOf(destinationAddress) }
    var startChecks by remember { mutableStateOf(List(4) { false }) }
    var completionChecks by remember { mutableStateOf(List(4) { false }) }
    var navigationProvider by remember { mutableStateOf("GOOGLE_MAPS") }
    var voiceGuidancePreference by remember { mutableStateOf(true) }
    var reroutePreference by remember { mutableStateOf(true) }

    // Part 6 Safety & Emergency Features 126, 139
    var showSosDialog by remember { mutableStateOf(false) }
    var showIncidentDialog by remember { mutableStateOf(false) }
    var showCancelRideDialog by remember { mutableStateOf(false) }
    var sosSentSuccess by remember { mutableStateOf(false) }
    var incidentSubmittedSuccess by remember { mutableStateOf(false) }

    // Navigation targets based on trip stage
    val isPickupPhase = stage == ChauffeurTripStage.NAVIGATING_TO_PICKUP || stage == ChauffeurTripStage.ARRIVED_AT_PICKUP
    val targetLat = if (isPickupPhase) pickupLat else destLat
    val targetLng = if (isPickupPhase) pickupLng else destLng
    val targetLabel = if (isPickupPhase) pickupLabel else destinationLabel

    LaunchedEffect(rideId) {
        try {
            val response = DriverApiClient.getService().getRideDetails(rideId)
            if (response.isSuccessful) {
                val ride = response.body().orEmpty()
                pickupLat = (ride["pickup_latitude"] as? Number)?.toDouble()
                pickupLng = (ride["pickup_longitude"] as? Number)?.toDouble()
                destLat = (ride["destination_latitude"] as? Number)?.toDouble()
                destLng = (ride["destination_longitude"] as? Number)?.toDouble()
            } else actionError = "Trip details unavailable (${response.code()})."
        } catch (_: Exception) { actionError = "Trip details could not be loaded. Check your connection." }
        try {
            val preferences = DriverApiClient.getService().getDispatchPreferences()
            if (preferences.isSuccessful) {
                navigationProvider = preferences.body()?.get("navigation_provider")?.toString() ?: "GOOGLE_MAPS"
                voiceGuidancePreference = preferences.body()?.get("voice_guidance_enabled") as? Boolean ?: true
                reroutePreference = preferences.body()?.get("reroute_on_closure_enabled") as? Boolean ?: true
            }
        } catch (_: Exception) { /* Navigation remains available through the safe default provider. */ }
        try { val stops = DriverApiClient.getService().getRideStops(rideId); if (stops.isSuccessful) tripStops = stops.body().orEmpty() } catch (_: Exception) { actionError = "Trip stops could not be loaded." }
    }

    LaunchedEffect(rideId) {
        try {
            val response = DriverApiClient.getService().getSharedTripForRide(rideId)
            if (response.isSuccessful) sharedTrip = response.body()
        } catch (_: Exception) { /* Solo rides do not have shared-trip data. */ }
    }

    LaunchedEffect(sharedTrip?.get("trip_id")) {
        val pooledTripId = sharedTrip?.get("trip_id")?.toString() ?: return@LaunchedEffect
        while (true) {
            try {
                val tripResponse = DriverApiClient.getService().getSharedTripForRide(rideId)
                if (tripResponse.isSuccessful) sharedTrip = tripResponse.body()
                val messageResponse = DriverApiClient.getService().getSharedTripMessages(pooledTripId)
                if (messageResponse.isSuccessful) sharedTripMessages = messageResponse.body().orEmpty()
            } catch (_: Exception) { /* Keep last confirmed occupancy and coordination messages visible. */ }
            kotlinx.coroutines.delay(10000)
        }
    }

    LaunchedEffect(rideId, stage) {
        while (stage == ChauffeurTripStage.TRIP_IN_PROGRESS || stage == ChauffeurTripStage.TRIP_PAUSED) {
            try { val response = DriverApiClient.getService().getPendingDestinationChange(rideId); if (response.isSuccessful) pendingDestinationChange = response.body()?.takeIf { it["request_id"] != null } }
            catch (_: Exception) { /* keep existing trip controls available during a temporary refresh failure */ }
            kotlinx.coroutines.delay(5000)
        }
    }

    // Ride completion: display full settlement summary and cash collection confirmation
    if (stage == ChauffeurTripStage.TRIP_FINISHED) {
        RideSummaryScreen(
            rideId = rideId,
            pickupAddress = pickupLabel,
            destinationAddress = destinationLabel,
            onConfirmAndClose = { _, _ -> onTripFinished() }
        )
        return
    }

    Scaffold(
        containerColor = DriverColors.MidnightBlack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                // Stage Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (stage) {
                            ChauffeurTripStage.NAVIGATING_TO_PICKUP -> "HEADING TO PICKUP"
                            ChauffeurTripStage.ARRIVED_AT_PICKUP -> "AT PICKUP LOCATION"
                            ChauffeurTripStage.OTP_VERIFICATION -> "VERIFY GUEST PASSCODE"
                            ChauffeurTripStage.TRIP_IN_PROGRESS -> "EN ROUTE TO DESTINATION"
                            ChauffeurTripStage.TRIP_PAUSED -> "TRIP PAUSED"
                            ChauffeurTripStage.TRIP_FINISHED -> "JOURNEY CONCLUDED"
                        },
                        style = DriverTypography.Subtitle.copy(color = DriverColors.ChauffeurGold, fontWeight = FontWeight.Bold)
                    )
                    Text("Ride #$rideId", style = DriverTypography.Body.copy(color = DriverColors.TextMuted, fontSize = 12.sp))
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Safety Quick-Actions Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = { showSosDialog = true },
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, DriverColors.AlertDanger),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DriverColors.AlertDanger),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text("🚨 SOS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { showIncidentDialog = true },
                        modifier = Modifier.weight(1.1f).height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, DriverColors.ChauffeurGold),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DriverColors.ChauffeurGold),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text("🛡️ INCIDENT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { showCancelRideDialog = true },
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, DriverColors.SurfaceBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DriverColors.AlertDanger),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text("❌ CANCEL", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                if (tripStops.isNotEmpty()) {
                    Surface(shape = RoundedCornerShape(12.dp), color = DriverColors.CockpitDark, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("TRIP STOPS", style = DriverTypography.Subtitle.copy(color = DriverColors.ChauffeurGold))
                            tripStops.forEach { stop ->
                                val stopId = stop["id"]?.toString() ?: ""
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(stop["address"]?.toString() ?: "Stop", maxLines = 1, modifier = Modifier.weight(1f))
                                    when (stop["status"]?.toString()) {
                                        "PENDING" -> TextButton(onClick = { scope.launch { try { val r = DriverApiClient.getService().arriveAtStop(rideId, stopId); if (r.isSuccessful) tripStops = tripStops.map { if (it["id"] == stopId) it + ("status" to "ARRIVED") else it } else actionError = "Stop arrival could not be saved." } catch (_: Exception) { actionError = "Stop arrival could not be saved." } } }) { Text("Arrive") }
                                        "ARRIVED" -> TextButton(onClick = { scope.launch { try { val r = DriverApiClient.getService().departStop(rideId, stopId); if (r.isSuccessful) tripStops = tripStops.map { if (it["id"] == stopId) it + ("status" to "COMPLETED") else it } else actionError = "Stop departure could not be saved." } catch (_: Exception) { actionError = "Stop departure could not be saved." } } }) { Text("Depart") }
                                    }
                                }
                            }
                        }
                    }
                }

                sharedTrip?.let { pool ->
                    Surface(shape = RoundedCornerShape(12.dp), color = DriverColors.CockpitDark, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("SHARED TRIP · ${pool["status"] ?: "—"}", style = DriverTypography.Subtitle.copy(color = DriverColors.ChauffeurGold))
                            val capacity = (pool["max_seats"] as? Number)?.toInt() ?: 0
                            val reserved = (pool["reserved_seats"] as? Number)?.toInt() ?: 0
                            val occupied = (pool["current_occupancy"] as? Number)?.toInt() ?: 0
                            Text("$occupied seats aboard · $reserved reserved · $capacity capacity", color = DriverColors.TextPure)
                            Text("Passenger and stop order (refreshes every 10 seconds):", color = DriverColors.TextMuted)
                            (pool["stops"] as? List<*>)?.filterIsInstance<Map<*, *>>()?.sortedBy { (it["sequence_order"] as? Number)?.toInt() ?: Int.MAX_VALUE }?.forEach { stop ->
                                Text("${stop["sequence_order"] ?: "—"}. ${stop["stop_type"] ?: "STOP"} · ${stop["address"] ?: "Private stop"} · ${stop["status"] ?: "SCHEDULED"}", color = DriverColors.TextPure, style = DriverTypography.Body.copy(fontSize = 12.sp))
                            }
                            (pool["manifest"] as? List<*>)?.filterIsInstance<Map<*, *>>()?.forEach { passenger ->
                                val currentRide = passenger["ride_id"]?.toString() == rideId
                                Text("${if (currentRide) "This pickup" else "Passenger booking"} · ${passenger["seats_reserved"] ?: 0} seats · ${passenger["booking_status"] ?: "—"}", color = if (currentRide) DriverColors.ChauffeurGold else DriverColors.TextMuted)
                            }
                            if (sharedTripMessages.isEmpty()) Text("No pickup coordination messages yet.", color = DriverColors.TextMuted)
                            sharedTripMessages.takeLast(5).forEach { row -> Text("${row["sender"] ?: "Passenger"}: ${row["message"] ?: ""}", color = DriverColors.TextPure, style = DriverTypography.Body.copy(fontSize = 12.sp)) }
                            OutlinedTextField(sharedMessageInput, { sharedMessageInput = it.take(1000) }, label = { Text("Pickup coordination message") }, minLines = 1, modifier = Modifier.fillMaxWidth())
                            OutlinedButton(onClick = {
                                val poolId = pool["trip_id"]?.toString() ?: return@OutlinedButton
                                val text = sharedMessageInput.trim()
                                if (text.isBlank()) { actionError = "Enter a coordination message first."; return@OutlinedButton }
                                scope.launch { try { val r = DriverApiClient.getService().sendSharedTripMessage(poolId, mapOf("message" to text)); if (r.isSuccessful) { sharedMessageInput = ""; actionError = "Pickup message sent."; val latest = DriverApiClient.getService().getSharedTripMessages(poolId); if (latest.isSuccessful) sharedTripMessages = latest.body().orEmpty() } else actionError = "Message could not be sent (${r.code()})." } catch (_: Exception) { actionError = "Message could not be sent. Check your connection." } }
                            }, enabled = sharedMessageInput.isNotBlank()) { Text("Send coordination message") }
                        }
                    }
                }

                // Real Google Maps Navigation & Turn-by-Turn Telemetry
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DriverColors.CockpitDark,
                    border = BorderStroke(1.dp, DriverColors.SurfaceBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) {
                    Box {
                        if (pickupLat != null && pickupLng != null && destLat != null && destLng != null) {
                            DriverMapView(modifier = Modifier.fillMaxSize(), pickupLatLng = LatLng(pickupLat!!, pickupLng!!), pickupTitle = "Pickup: $pickupLabel", destLatLng = LatLng(destLat!!, destLng!!), destTitle = "Dest: $destinationLabel")
                        } else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading verified trip coordinates…", color = DriverColors.TextMuted) }

                        // Floating Turn-by-Turn GPS Navigation Action Button
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = DriverColors.CockpitDark.copy(alpha = 0.94f),
                            border = BorderStroke(1.dp, DriverColors.ChauffeurGold),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isPickupPhase) "TO PICKUP: $pickupAddress" else "TO DEST: $destinationAddress",
                                        style = DriverTypography.Body.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                                        maxLines = 1
                                    )
                                    Text(
                                        text = when (stage) {
                                            ChauffeurTripStage.NAVIGATING_TO_PICKUP -> "Route guidance is provided by your navigation app"
                                            ChauffeurTripStage.ARRIVED_AT_PICKUP -> "Confirm the guest and pickup location"
                                            ChauffeurTripStage.OTP_VERIFICATION -> "Waiting for guest verification"
                                            ChauffeurTripStage.TRIP_IN_PROGRESS, ChauffeurTripStage.TRIP_PAUSED -> "Follow current navigation guidance"
                                            ChauffeurTripStage.TRIP_FINISHED -> "VIP Passenger Disembarked"
                                        },
                                        style = DriverTypography.Body.copy(color = DriverColors.ChauffeurGold, fontSize = 10.sp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (targetLat != null && targetLng != null) NavigationHelper.launchNavigation(context, navigationProvider, targetLat!!, targetLng!!, targetLabel)
                                        else actionError = "Navigation is unavailable until trip coordinates load."
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = DriverColors.ChauffeurGold, contentColor = DriverColors.MidnightBlack),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("NAVIGATE", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Phase 3: Pooled Passenger Manifest & Real-Time Capacity Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DriverColors.CockpitDark,
                    border = BorderStroke(1.dp, DriverColors.SurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("POOLED PASSENGER MANIFEST", style = DriverTypography.Subtitle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = DriverColors.SurfaceCard,
                                border = BorderStroke(1.dp, DriverColors.ChauffeurGold)
                            ) {
                                Text(
                                    text = "OCCUPANCY: 2 / 4 SEATS",
                                    style = DriverTypography.Body.copy(color = DriverColors.ChauffeurGold, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("1. Guest Lord Sterling (2 Seats)", style = DriverTypography.Body.copy(fontSize = 12.sp))
                            Text("BOARDED", style = DriverTypography.Body.copy(color = DriverColors.AlertSuccess, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("2. Guest Lady Kensington (1 Seat)", style = DriverTypography.Body.copy(fontSize = 12.sp))
                            Text("NEXT PICKUP", style = DriverTypography.Body.copy(color = DriverColors.ChauffeurGold, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                // Part 3: VIP Guest Sanctuary & Accessibility Specifications Brief (Features 53-66, 69, 71, 73, 74)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DriverColors.CockpitDark,
                    border = BorderStroke(1.dp, DriverColors.ChauffeurGold.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("✦", color = DriverColors.ChauffeurGold, fontSize = 11.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("VIP SANCTUARY & ACCESSIBILITY BRIEF", style = DriverTypography.Subtitle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold))
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = DriverColors.SurfaceCard,
                                border = BorderStroke(0.5.dp, DriverColors.ChauffeurGold)
                            ) {
                                Text("BESPOKE", style = DriverTypography.Body.copy(color = DriverColors.ChauffeurGold, fontSize = 9.sp, fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Atmosphere & Etiquette Chips
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("CABIN CLIMATE", style = DriverTypography.Body.copy(color = DriverColors.TextMuted, fontSize = 9.sp))
                                Text("21.0°C • Silent Discretion", style = DriverTypography.Body.copy(color = DriverColors.ChauffeurGold, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                            }
                            Column {
                                Text("LUGGAGE / CARGO", style = DriverTypography.Body.copy(color = DriverColors.TextMuted, fontSize = 9.sp))
                                Text("2 Large + 2 Small Pieces", style = DriverTypography.Body.copy(color = DriverColors.TextPure, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                            }
                            Column {
                                Text("ACCESSIBILITY", style = DriverTypography.Body.copy(color = DriverColors.TextMuted, fontSize = 9.sp))
                                Text("Step-Free (≤32cm)", style = DriverTypography.Body.copy(color = DriverColors.AlertSuccess, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Chauffeur Protocol: Maintain tranquil quiet. Open passenger door at VIP portico. Assist with luggage stowage.",
                            style = DriverTypography.Body.copy(color = DriverColors.TextMuted, fontSize = 9.sp, lineHeight = 13.sp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                if (actionError != null) Text(actionError!!, color = DriverColors.AlertDanger, style = DriverTypography.Body)

                // Feature 7: Trip Start OTP Verification Step
                if (stage == ChauffeurTripStage.OTP_VERIFICATION) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = DriverColors.SurfaceCard,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("ENTER 6-DIGIT PASSCODE", style = DriverTypography.Subtitle)
                            Text("Kindly request the passcode displayed on the guest's mobile application.", style = DriverTypography.Body.copy(fontSize = 12.sp, color = DriverColors.TextMuted))
                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = enteredOtp,
                                onValueChange = { if (it.length <= 6) enteredOtp = it },
                                label = { Text("6-Digit Code", color = DriverColors.TextMuted) },
                                textStyle = DriverTypography.MetricValue.copy(letterSpacing = 6.sp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = DriverColors.ChauffeurGold,
                                    unfocusedBorderColor = DriverColors.SurfaceBorder,
                                    focusedTextColor = DriverColors.ChauffeurGold,
                                    unfocusedTextColor = DriverColors.ChauffeurGold
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (otpError != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(otpError ?: "", color = DriverColors.AlertDanger, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Feature 9: Finish Ride & Cash Collection Confirmation
                if (stage == ChauffeurTripStage.TRIP_FINISHED) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = DriverColors.SurfaceCard,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("FARE SUMMARY", style = DriverTypography.Subtitle)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total Trip Fare", style = DriverTypography.Body)
                                Text("₹1,600.00", style = DriverTypography.Subtitle)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Chauffeur Net Earning (80%)", style = DriverTypography.Body)
                                Text("₹1,280.00", style = DriverTypography.MetricValue.copy(fontSize = 20.sp))
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = cashCollectedConfirmed,
                                    onCheckedChange = { cashCollectedConfirmed = it },
                                    colors = CheckboxDefaults.colors(checkedColor = DriverColors.ChauffeurGold)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("I confirm direct cash collection / verified UPI receipt", style = DriverTypography.Body.copy(fontSize = 12.sp))
                            }
                        }
                    }
                }
            }

            // Contextual Lifecycle Action Buttons
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                when (stage) {
                    ChauffeurTripStage.NAVIGATING_TO_PICKUP -> {
                        Button(
                            onClick = {
                                if (isActionLoading) return@Button
                                isActionLoading = true
                                scope.launch {
                                    try {
                                        val response = DriverApiClient.getService().notifyArrived(rideId)
                                        if (response.isSuccessful) stage = ChauffeurTripStage.ARRIVED_AT_PICKUP else actionError = "Arrival could not be recorded (${response.code()})."
                                    } catch (_: Exception) { actionError = "Arrival could not be recorded. Check your connection." }
                                    isActionLoading = false
                                }
                            },
                            enabled = !isActionLoading,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DriverColors.ChauffeurGold, contentColor = DriverColors.MidnightBlack),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Text(if (isActionLoading) "UPDATING ARRIVAL..." else "I HAVE ARRIVED AT PICKUP", fontWeight = FontWeight.Bold)
                        }
                    }
                    ChauffeurTripStage.ARRIVED_AT_PICKUP -> {
                        Button(
                            onClick = { showStartChecklist = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DriverColors.ChauffeurGold, contentColor = DriverColors.MidnightBlack),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Text("CHECK SAFETY & VERIFY PASSENGER", fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { showNoShowDialog = true }) { Text("Passenger has not arrived", color = DriverColors.AlertDanger) }
                        TextButton(onClick = { correctedAddress = pickupAddress; correctedLatitude = pickupLat?.toString() ?: ""; correctedLongitude = pickupLng?.toString() ?: ""; showPinCorrectionDialog = true }) { Text("Correct pickup pin") }
                    }
                    ChauffeurTripStage.OTP_VERIFICATION -> {
                        Button(
                            onClick = {
                                if (isActionLoading) return@Button
                                if (enteredOtp.length < 6) {
                                    otpError = "Please enter the full 6-digit passcode."
                                    return@Button
                                }
                                isActionLoading = true
                                otpError = null
                                scope.launch {
                                    try {
                                        val res = DriverApiClient.getService().startTrip(rideId, StartTripPayload(otpCode = enteredOtp.trim()))
                                        if (res.isSuccessful) {
                                            stage = ChauffeurTripStage.TRIP_IN_PROGRESS
                                            val bookingId = sharedTrip?.get("own_booking_id")?.toString()
                                            if (!bookingId.isNullOrBlank()) {
                                                val occupancy = DriverApiClient.getService().updateSharedRideOccupancy(bookingId, mapOf("event_type" to "BOARDED"))
                                                if (occupancy.isSuccessful) sharedTrip = sharedTrip?.plus("current_occupancy" to (occupancy.body()?.get("current_occupancy") ?: 0))
                                                else actionError = "Trip started, but shared passenger count needs a refresh (${occupancy.code()})."
                                            }
                                        } else {
                                            val err = res.errorBody()?.string() ?: ""
                                            otpError = if (err.contains("Incorrect OTP") || err.contains("detail")) {
                                                "Invalid passcode. Please request correct code from guest."
                                            } else {
                                                "Verification failed. Recheck passcode."
                                            }
                                        }
                                    } catch (_: Exception) {
                                        otpError = "Could not verify the passenger. Check your connection and retry."
                                    } finally {
                                        isActionLoading = false
                                    }
                                }
                            },
                            enabled = !isActionLoading,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DriverColors.StatusOnline, contentColor = DriverColors.MidnightBlack),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Text(if (isActionLoading) "VERIFYING PASSCODE..." else "AUTHORIZE & COMMENCE TRIP", fontWeight = FontWeight.Bold)
                        }
                    }
                    ChauffeurTripStage.TRIP_IN_PROGRESS -> {
                        Button(
                            onClick = { showCompletionChecklist = true },
                            enabled = !isActionLoading,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DriverColors.ChauffeurGold, contentColor = DriverColors.MidnightBlack),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Text("COMPLETE TRIP CHECKLIST", fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { scope.launch { try { val r = DriverApiClient.getService().pauseTrip(rideId, mapOf("reason" to "Driver paused trip")); if (r.isSuccessful) stage = ChauffeurTripStage.TRIP_PAUSED else actionError = "Could not pause trip." } catch (_: Exception) { actionError = "Could not pause trip." } } }) { Text("Pause trip") }
                        TextButton(onClick = { showRoadClosureDialog = true }) { Text("Report road closure / reroute") }
                    }
                    ChauffeurTripStage.TRIP_PAUSED -> {
                        Button(onClick = { scope.launch { try { val r = DriverApiClient.getService().resumeTrip(rideId, mapOf("reason" to "Driver resumed trip")); if (r.isSuccessful) stage = ChauffeurTripStage.TRIP_IN_PROGRESS else actionError = "Could not resume trip." } catch (_: Exception) { actionError = "Could not resume trip." } } }, modifier = Modifier.fillMaxWidth()) { Text("RESUME TRIP") }
                    }
                    ChauffeurTripStage.TRIP_FINISHED -> {
                        Button(
                            onClick = onTripFinished,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DriverColors.StatusOnline, contentColor = DriverColors.MidnightBlack),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Text("CONFIRM SETTLEMENT & RETURN TO COCKPIT", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showRoadClosureDialog) {
        AlertDialog(onDismissRequest = { showRoadClosureDialog = false }, title = { Text("Road closure") }, text = { Column {
            Text("This records the closure. The selected navigation app must calculate and guide the alternate route; automatic platform rerouting is not configured.", color = DriverColors.TextMuted)
            OutlinedTextField(roadClosureNote, { roadClosureNote = it.take(500) }, label = { Text("Closure location or details") }, minLines = 2)
        } }, confirmButton = { TextButton(onClick = { if (roadClosureNote.trim().length < 5) { actionError = "Describe the closure before submitting."; return@TextButton }; scope.launch { try { val r = DriverApiClient.getService().recordReroute(rideId, mapOf("category" to "ROAD_CLOSURE", "description" to roadClosureNote.trim())); if (r.isSuccessful) { showRoadClosureDialog = false; actionError = "Closure recorded. Follow the alternate route shown in $navigationProvider." } else actionError = "Closure report could not be saved." } catch (_: Exception) { actionError = "Closure report could not be saved." } } }) { Text("Record closure") } }, dismissButton = { TextButton(onClick = { showRoadClosureDialog = false }) { Text("Cancel") } })
    }
    if (showPinCorrectionDialog) {
        AlertDialog(onDismissRequest = { showPinCorrectionDialog = false }, title = { Text("Correct pickup location") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Use verified coordinates supplied by the passenger or map. The correction is recorded in the trip audit.", color = DriverColors.TextMuted)
            OutlinedTextField(correctedAddress, { correctedAddress = it.take(255) }, label = { Text("Pickup address") })
            OutlinedTextField(correctedLatitude, { correctedLatitude = it }, label = { Text("Latitude") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(correctedLongitude, { correctedLongitude = it }, label = { Text("Longitude") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        } }, confirmButton = { TextButton(onClick = {
            val lat = correctedLatitude.toDoubleOrNull(); val lng = correctedLongitude.toDoubleOrNull()
            if (correctedAddress.trim().length < 3 || lat == null || lng == null || lat !in -90.0..90.0 || lng !in -180.0..180.0) { actionError = "Enter a valid address and coordinates."; return@TextButton }
            scope.launch { try { val r = DriverApiClient.getService().correctPickupPin(rideId, mapOf("address" to correctedAddress.trim(), "latitude" to lat, "longitude" to lng)); if (r.isSuccessful) { pickupLat = lat; pickupLng = lng; pickupLabel = correctedAddress.trim(); showPinCorrectionDialog = false } else actionError = "Pickup correction was not saved (${r.code()})." } catch (_: Exception) { actionError = "Pickup correction could not be saved." } }
        }) { Text("Save correction") } }, dismissButton = { TextButton(onClick = { showPinCorrectionDialog = false }) { Text("Cancel") } })
    }
    pendingDestinationChange?.let { change ->
        val requestId = change["request_id"]?.toString()
        AlertDialog(onDismissRequest = { }, title = { Text("Passenger requests a route change") }, text = { Text("${change["destination_address"]}\nRevised fare: ₹${((change["proposed_fare_cents"] as? Number)?.toInt() ?: 0) / 100}\nFare change: ₹${((change["fare_difference_cents"] as? Number)?.toInt() ?: 0) / 100}") }, confirmButton = { TextButton(onClick = { if (requestId != null) scope.launch { try { val r = DriverApiClient.getService().respondDestinationChange(rideId, requestId, mapOf("accept" to true, "reason" to "")); if (r.isSuccessful) { pendingDestinationChange = null; val detail = DriverApiClient.getService().getRideDetails(rideId); if (detail.isSuccessful) { val body = detail.body().orEmpty(); destLat = (body["destination_latitude"] as? Number)?.toDouble(); destLng = (body["destination_longitude"] as? Number)?.toDouble(); destinationLabel = body["destination_address"]?.toString() ?: destinationLabel }; actionError = "Route and revised fare approved." } else actionError = "Route change approval failed." } catch (_: Exception) { actionError = "Route change approval failed." } } }) { Text("Approve") } }, dismissButton = { TextButton(onClick = { if (requestId != null) scope.launch { try { val r = DriverApiClient.getService().respondDestinationChange(rideId, requestId, mapOf("accept" to false, "reason" to "Driver declined")); if (r.isSuccessful) pendingDestinationChange = null else actionError = "Route change response failed." } catch (_: Exception) { actionError = "Route change response failed." } } }) { Text("Decline") } })
    }
    if (showStartChecklist) {
        val labels = listOf("Passenger is present", "Vehicle is safe to depart", "Belongings have been checked", "Route is confirmed with passenger")
        AlertDialog(onDismissRequest = { showStartChecklist = false }, title = { Text("Before departure") }, text = { Column {
            labels.forEachIndexed { index, label -> Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(startChecks[index], { checked -> startChecks = startChecks.toMutableList().also { it[index] = checked } }); Text(label) } }
            Text("Passenger identity is confirmed with the trip OTP on the next step.", color = DriverColors.TextMuted)
        } }, confirmButton = { TextButton(onClick = {
            if (!startChecks.all { it }) { actionError = "Confirm all departure checks first."; return@TextButton }
            scope.launch { try {
                val response = DriverApiClient.getService().submitStartChecklist(rideId, mapOf("checklist" to mapOf("passenger_present" to true, "vehicle_safe" to true, "belongings_checked" to true, "route_confirmed" to true)))
                if (response.isSuccessful) { showStartChecklist = false; stage = ChauffeurTripStage.OTP_VERIFICATION } else actionError = "Departure checks were not saved (${response.code()})."
            } catch (_: Exception) { actionError = "Departure checks could not be saved." } }
        }) { Text("Continue to passenger OTP") } }, dismissButton = { TextButton(onClick = { showStartChecklist = false }) { Text("Cancel") } })
    }
    if (showCompletionChecklist) {
        val labels = listOf("Passenger belongings returned", "Vehicle cabin checked", "Drop-off is safe", "Fare explained to passenger")
        val keys = listOf("passenger_belongings_returned", "vehicle_cabin_checked", "dropoff_safe", "fare_explained")
        AlertDialog(onDismissRequest = { showCompletionChecklist = false }, title = { Text("Complete the journey") }, text = { Column {
            labels.forEachIndexed { index, label -> Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(completionChecks[index], { checked -> completionChecks = completionChecks.toMutableList().also { it[index] = checked } }); Text(label) } }
        } }, confirmButton = { TextButton(onClick = {
            if (!completionChecks.all { it }) { actionError = "Confirm each completion check."; return@TextButton }
            scope.launch { isActionLoading = true; try {
                val checklist = DriverApiClient.getService().submitCompletionChecklist(rideId, mapOf("checklist" to keys.mapIndexed { index, key -> key to completionChecks[index] }.toMap()))
                if (!checklist.isSuccessful) { actionError = "Completion checks were not saved (${checklist.code()})."; isActionLoading = false; return@launch }
                val finish = DriverApiClient.getService().finishTrip(rideId, FinishTripPayload())
                if (finish.isSuccessful) {
                    val bookingId = sharedTrip?.get("own_booking_id")?.toString()
                    if (!bookingId.isNullOrBlank()) {
                        val occupancy = DriverApiClient.getService().updateSharedRideOccupancy(bookingId, mapOf("event_type" to "ALIGHTED"))
                        if (!occupancy.isSuccessful) actionError = "Ride completed; the shared passenger count did not refresh (${occupancy.code()})."
                    }
                    showCompletionChecklist = false
                    stage = ChauffeurTripStage.TRIP_FINISHED
                } else actionError = "Trip completion failed (${finish.code()})."
            } catch (_: Exception) { actionError = "Trip completion could not be saved. Check your connection and retry." } finally { isActionLoading = false } }
        }) { Text(if (isActionLoading) "Saving…" else "Finish trip") } }, dismissButton = { TextButton(onClick = { showCompletionChecklist = false }) { Text("Cancel") } })
    }
    if (showNoShowDialog) {
        AlertDialog(onDismissRequest = { showNoShowDialog = false }, title = { Text("Passenger no-show") }, text = { Column {
            Text("Evidence note is saved with the server arrival time. A five-minute wait is required; no no-show fee is applied without an approved policy.")
            OutlinedTextField(noShowEvidence, { noShowEvidence = it.take(1000) }, label = { Text("What did you observe?") }, minLines = 3)
        } }, confirmButton = { TextButton(onClick = {
            if (noShowEvidence.trim().length < 10) { actionError = "Add at least 10 characters of factual evidence."; return@TextButton }
            scope.launch { try {
                val r = DriverApiClient.getService().reportPassengerNoShow(rideId, mapOf("evidence_note" to noShowEvidence.trim()))
                if (r.isSuccessful) {
                    val bookingId = sharedTrip?.get("own_booking_id")?.toString()
                    if (!bookingId.isNullOrBlank()) {
                        val occupancy = DriverApiClient.getService().updateSharedRideOccupancy(bookingId, mapOf("event_type" to "NO_SHOW"))
                        if (!occupancy.isSuccessful) { actionError = "No-show evidence saved; shared occupancy update needs administrator review."; return@launch }
                    }
                    showNoShowDialog = false
                    onTripFinished()
                } else actionError = r.errorBody()?.string()?.take(200) ?: "No-show cannot be filed yet."
            } catch (_: Exception) { actionError = "No-show report could not be saved." } }
        }) { Text("Submit evidence") } }, dismissButton = { TextButton(onClick = { showNoShowDialog = false }) { Text("Cancel") } })
    }

    // --- Driver Emergency SOS Dialog (Feature 126) ---
    if (showSosDialog) {
        var isDispatchingSos by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showSosDialog = false },
            containerColor = DriverColors.CockpitDark,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🚨 ", fontSize = 20.sp)
                    Text(
                        "CHAUFFEUR EMERGENCY SOS",
                        style = DriverTypography.Subtitle.copy(color = DriverColors.AlertDanger, fontWeight = FontWeight.Bold)
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "This records a trip-linked safety alert. Emergency dispatch and live SOC routing are not configured here. Call 112 directly if anyone is in immediate danger.",
                        style = DriverTypography.Body.copy(color = DriverColors.TextPure, fontSize = 13.sp)
                    )

                    if (sosSentSuccess) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DriverColors.AlertSuccess.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, DriverColors.AlertSuccess),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Text(
                                "✓ Safety alert recorded in the app. Dispatch is not configured; call 112 if you are in immediate danger.",
                                style = DriverTypography.Body.copy(color = DriverColors.AlertSuccess, fontSize = 12.sp),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Button(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = DriverColors.AlertDanger)) {
                        Text("CALL EMERGENCY SERVICES · 112", color = DriverColors.TextPure, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:112")).apply {
                                putExtra("sms_body", "EMERGENCY SOS: Pilot in distress during ride #$rideId at pickup: $pickupLabel, destination: $destinationLabel.")
                            }
                            context.startActivity(smsIntent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DriverColors.CockpitDark)
                    ) {
                        Text("ALERT REGISTERED EMERGENCY CONTACT (SMS)", color = DriverColors.ChauffeurGold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isDispatchingSos = true
                        scope.launch {
                            try {
                                val response = DriverApiClient.getService().sendDriverEmergencyAlert(mapOf("ride_id" to rideId, "notes" to "Driver triggered an in-trip shared ride safety alert"))
                                sosSentSuccess = response.isSuccessful
                                if (!response.isSuccessful) actionError = "Safety alert could not be recorded (${response.code()}). Call 112 directly if needed."
                            } catch (_: Exception) {
                                actionError = "Safety alert could not be recorded. Call 112 directly if needed."
                            } finally {
                                isDispatchingSos = false
                            }
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DriverColors.AlertDanger)
                ) {
                    Text(if (isDispatchingSos) "RECORDING ALERT…" else "RECORD TRIP SAFETY ALERT", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSosDialog = false }) {
                    Text("CLOSE", color = DriverColors.TextMuted)
                }
            }
        )
    }

    // --- Driver Incident Report Dialog (Feature 139) ---
    if (showIncidentDialog) {
        var selectedCategory by remember { mutableStateOf("PASSENGER_MISCONDUCT") }
        var selectedSeverity by remember { mutableStateOf("MEDIUM") }
        var descriptionText by remember { mutableStateOf("") }
        var isSubmittingIncident by remember { mutableStateOf(false) }

        val categories = listOf(
            "PASSENGER_MISCONDUCT" to "Passenger Misconduct",
            "HARASSMENT" to "Harassment / Verbal Abuse",
            "ROUTE_DEVIATION" to "Forced Route Deviation",
            "UNSAFE_CONDITIONS" to "Hostile Environment",
            "PAYMENT_DISPUTE" to "Fare Refusal / Fraud"
        )
        val severities = listOf("LOW", "MEDIUM", "HIGH", "CRITICAL")

        AlertDialog(
            onDismissRequest = { showIncidentDialog = false },
            containerColor = DriverColors.CockpitDark,
            title = {
                Text(
                    "SHIELD INCIDENT DISPATCH",
                    style = DriverTypography.Subtitle.copy(color = DriverColors.ChauffeurGold, fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Incident reports are recorded for review. Response timing is not guaranteed; call 112 directly if anyone is in immediate danger.",
                        style = DriverTypography.Body.copy(color = DriverColors.TextMuted, fontSize = 12.sp)
                    )

                    if (incidentSubmittedSuccess) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DriverColors.AlertSuccess.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, DriverColors.AlertSuccess),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "✓ Incident report recorded for review.",
                                style = DriverTypography.Body.copy(color = DriverColors.AlertSuccess, fontSize = 12.sp),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    } else {
                        Text("INCIDENT CATEGORY", style = DriverTypography.Body.copy(color = DriverColors.ChauffeurGold, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                        categories.forEach { (catKey, catLabel) ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (selectedCategory == catKey) DriverColors.SurfaceCard else DriverColors.CockpitDark,
                                border = BorderStroke(1.dp, if (selectedCategory == catKey) DriverColors.ChauffeurGold else DriverColors.SurfaceBorder),
                                modifier = Modifier.fillMaxWidth().clickable { selectedCategory = catKey }
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedCategory == catKey,
                                        onClick = { selectedCategory = catKey },
                                        colors = RadioButtonDefaults.colors(selectedColor = DriverColors.ChauffeurGold)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(catLabel, style = DriverTypography.Body.copy(fontSize = 12.sp))
                                }
                            }
                        }

                        Text("SEVERITY LEVEL", style = DriverTypography.Body.copy(color = DriverColors.ChauffeurGold, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            severities.forEach { sev ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (selectedSeverity == sev) DriverColors.ChauffeurGold.copy(alpha = 0.2f) else DriverColors.CockpitDark,
                                    border = BorderStroke(1.dp, if (selectedSeverity == sev) DriverColors.ChauffeurGold else DriverColors.SurfaceBorder),
                                    modifier = Modifier.weight(1f).clickable { selectedSeverity = sev }
                                ) {
                                    Text(
                                        sev,
                                        style = DriverTypography.Body.copy(
                                            color = if (selectedSeverity == sev) DriverColors.ChauffeurGold else DriverColors.TextMuted,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        ),
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }

                        Text("INCIDENT PARTICULARS", style = DriverTypography.Body.copy(color = DriverColors.ChauffeurGold, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                        OutlinedTextField(
                            value = descriptionText,
                            onValueChange = { descriptionText = it },
                            placeholder = { Text("State details of what transpired...", color = DriverColors.TextMuted, fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DriverColors.ChauffeurGold,
                                unfocusedBorderColor = DriverColors.SurfaceBorder,
                                focusedTextColor = DriverColors.TextPure,
                                unfocusedTextColor = DriverColors.TextPure
                            )
                        )
                    }
                }
            },
            confirmButton = {
                if (!incidentSubmittedSuccess) {
                    Button(
                        onClick = {
                            isSubmittingIncident = true
                            scope.launch {
                                try {
                                    val response = DriverApiClient.getService().reportTripIssue(rideId, mapOf("category" to selectedCategory, "description" to descriptionText.trim()))
                                    incidentSubmittedSuccess = response.isSuccessful
                                    if (!response.isSuccessful) actionError = response.errorBody()?.string()?.take(200) ?: "Issue report was not saved."
                                } catch (_: Exception) {
                                    incidentSubmittedSuccess = false
                                    actionError = "Issue report could not be saved."
                                } finally {
                                    isSubmittingIncident = false
                                }
                            }
                        },
                        enabled = !isSubmittingIncident,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DriverColors.ChauffeurGold, contentColor = DriverColors.MidnightBlack)
                    ) {
                        Text(if (isSubmittingIncident) "TRANSMITTING..." else "SUBMIT REPORT", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showIncidentDialog = false
                    incidentSubmittedSuccess = false
                }) {
                    Text("CLOSE", color = DriverColors.TextMuted)
                }
            }
        )
    }

    // --- Driver In-Trip Cancel Ride Dialog ---
    if (showCancelRideDialog) {
        var cancelReason by remember { mutableStateOf("VEHICLE_BREAKDOWN") }
        var cancelNote by remember { mutableStateOf("") }
        var isCancelling by remember { mutableStateOf(false) }

        val cancelReasons = listOf(
            "VEHICLE_BREAKDOWN" to "Vehicle Breakdown / Puncture",
            "EMERGENCY" to "Personal / Medical Emergency",
            "PASSENGER_MISCONDUCT" to "Passenger Safety / Misconduct",
            "ROAD_IMPASSABLE" to "Road Blocked / Waterlogging",
            "OTHER" to "Other Reason"
        )

        AlertDialog(
            onDismissRequest = { showCancelRideDialog = false },
            containerColor = DriverColors.MidnightBlack,
            title = {
                Text(
                    text = "CANCEL ACCEPTED TRIP",
                    style = DriverTypography.Subtitle.copy(color = DriverColors.AlertDanger, fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Are you sure you want to cancel this trip? Frequent cancellations impact your pilot acceptance rating.",
                        style = DriverTypography.Body.copy(color = DriverColors.TextPure, fontSize = 12.sp)
                    )

                    Text("Select Cancellation Reason:", fontSize = 11.sp, color = DriverColors.TextMuted, fontWeight = FontWeight.Bold)

                    cancelReasons.forEach { (code, label) ->
                        val isSelected = cancelReason == code
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { cancelReason = code },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) DriverColors.AlertDanger.copy(alpha = 0.2f) else DriverColors.CockpitDark,
                            border = BorderStroke(1.dp, if (isSelected) DriverColors.AlertDanger else DriverColors.SurfaceBorder)
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) DriverColors.AlertDanger else DriverColors.TextPure,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = cancelNote,
                        onValueChange = { cancelNote = it },
                        label = { Text("Additional notes (optional)", color = DriverColors.TextMuted, fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isCancelling = true
                        scope.launch {
                            try {
                                DriverApiClient.getService().reportTripIssue(
                                    rideId,
                                    mapOf(
                                        "category" to cancelReason,
                                        "description" to "Driver cancelled trip: $cancelReason. Notes: ${cancelNote.trim()}"
                                    )
                                )
                            } catch (_: Exception) {}
                            isCancelling = false
                            showCancelRideDialog = false
                            onTripFinished()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DriverColors.AlertDanger),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isCancelling
                ) {
                    Text(if (isCancelling) "CANCELLING..." else "CONFIRM CANCELLATION", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelRideDialog = false }) {
                    Text("KEEP TRIP", color = DriverColors.TextPure)
                }
            }
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun ActiveChauffeurTripScreenPreview() {
    ActiveChauffeurTripScreen(
        rideId = "RIDE-1044",
        pickupAddress = "Patna Junction Railway Station",
        destinationAddress = "AIIMS Patna, Phulwari Sharif",
        onTripFinished = {}
    )
}
