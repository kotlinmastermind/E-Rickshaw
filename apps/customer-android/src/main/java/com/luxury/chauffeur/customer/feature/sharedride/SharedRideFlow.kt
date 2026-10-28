package com.luxury.chauffeur.customer.feature.sharedride

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luxury.chauffeur.customer.core.designsystem.LuxuryColors
import com.luxury.chauffeur.customer.core.network.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedRideBookingDialog(
    pickupAddress: String,
    pickupLatitude: Double,
    pickupLongitude: Double,
    destinationAddress: String,
    destinationLatitude: Double,
    destinationLongitude: Double,
    categoryId: String,
    categoryName: String,
    passengerCapacity: Int,
    onDismiss: () -> Unit,
    onBooked: (rideId: String, otp: String, pooledTripId: String, bookingId: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var seats by remember { mutableIntStateOf(1) }
    var maximumDetour by remember { mutableFloatStateOf(5f) }
    var shareStops by remember { mutableStateOf(false) }
    var createNewPoolIfFull by remember { mutableStateOf(false) }
    var preferenceStatus by remember { mutableStateOf<String?>(null) }
    var requestGeneration by remember { mutableIntStateOf(0) }
    var estimate by remember { mutableStateOf<SharedEstimateResponse?>(null) }
    var matches by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var selectedPool by remember { mutableStateOf<String?>(null) }
    var selectedPoolAvailable by remember { mutableIntStateOf(0) }
    var refreshingSeats by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var booking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val response = ApiClient.getService().getSharedRidePreferences()
            val saved = (response.body()?.get("maximum_detour_km") as? Number)?.toFloat()
            if (response.isSuccessful && saved != null) maximumDetour = saved.coerceIn(1f, 25f)
        } catch (_: Exception) { preferenceStatus = "Saved detour preference could not be loaded; using 5 km for this booking." }
    }

    LaunchedEffect(categoryId, pickupLatitude, pickupLongitude, destinationLatitude, destinationLongitude, seats, maximumDetour, requestGeneration) {
        loading = true
        error = null
        selectedPool = null
        delay(450)
        try {
            val api = ApiClient.getService()
            val request = SharedEstimateRequest(categoryId, pickupLatitude, pickupLongitude, destinationLatitude, destinationLongitude, seats)
            val estimateResponse = api.getSharedEstimate(request)
            if (!estimateResponse.isSuccessful || estimateResponse.body() == null) {
                error = if (estimateResponse.code() == 503) "Shared rides are temporarily unavailable." else "Could not price this shared route (${estimateResponse.code()})."
                estimate = null
                matches = emptyList()
            } else {
                estimate = estimateResponse.body()
                countdown = estimate?.reservationExpiresInSeconds ?: 0
                val matchResponse = withTimeoutOrNull(12_000L) { api.findSharedRideMatches(SharedMatchRequest(categoryId, pickupLatitude, pickupLongitude, destinationLatitude, destinationLongitude, seats, maximumDetour.toDouble())) }
                if (matchResponse?.isSuccessful == true) {
                    matches = (matchResponse.body()?.get("candidates") as? List<*>)?.filterIsInstance<Map<String, Any>>().orEmpty()
                } else {
                    matches = emptyList()
                    error = if (matchResponse == null) "No compatible match response arrived within 12 seconds. You may explicitly allow a new shared pool." else "Fare is ready, but pool matching could not refresh (${matchResponse.code()}). You may explicitly allow a new pool."
                }
            }
        } catch (_: Exception) {
            estimate = null
            matches = emptyList()
            error = "Shared-ride pricing needs a connection. Check your network and retry."
        }
        loading = false
    }

    LaunchedEffect(countdown, estimate?.estimateId) {
        if (estimate == null) return@LaunchedEffect
        while (countdown > 0) {
            delay(1000)
            countdown -= 1
        }
    }

    LaunchedEffect(selectedPool) {
        val poolId = selectedPool ?: return@LaunchedEffect
        while (selectedPool == poolId) {
            try {
                val availability = ApiClient.getService().getPoolSeatAvailability(poolId)
                if (availability.isSuccessful) {
                    val body = availability.body().orEmpty()
                    selectedPoolAvailable = (body["available_seats"] as? Number)?.toInt() ?: 0
                    if (body["accepting_matches"] == false || selectedPoolAvailable < seats) {
                        selectedPool = null
                        error = "That pool no longer has enough seats. Choose another match or refresh."
                    }
                }
            } catch (_: Exception) { /* Keep the last confirmed capacity and refresh again. */ }
            delay(10_000)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.92f),
            shape = RoundedCornerShape(24.dp),
            color = LuxuryColors.DeepCharcoal,
            border = androidx.compose.foundation.BorderStroke(1.dp, LuxuryColors.ChampagneGold.copy(alpha = 0.45f)),
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("SHARED RIDE", color = LuxuryColors.ChampagneGold, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("Route fit, available seats and your fare before booking", color = LuxuryColors.MutedSilver, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = onDismiss) { Text("Close", color = LuxuryColors.ChampagneGold) }
                }

                SharedRideCard("Journey") {
                    Text("Pickup · $pickupAddress", color = LuxuryColors.PlatinumWhite)
                    Text("Drop-off · $destinationAddress", color = LuxuryColors.PlatinumWhite)
                    Text("${categoryName} · capacity up to $passengerCapacity passengers", color = LuxuryColors.MutedSilver)
                }
                SharedRideCard("Seats and route preference") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Passengers", color = LuxuryColors.PlatinumWhite)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { seats = (seats - 1).coerceAtLeast(1) }, enabled = seats > 1) { Text("−", color = LuxuryColors.ChampagneGold) }
                            Text("$seats", color = LuxuryColors.PlatinumWhite, style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { seats = (seats + 1).coerceAtMost(minOf(4, passengerCapacity)) }, enabled = seats < minOf(4, passengerCapacity)) { Text("+", color = LuxuryColors.ChampagneGold) }
                        }
                    }
                    Text("Maximum additional detour · ${"%.1f".format(maximumDetour)} km", color = LuxuryColors.PlatinumWhite)
                    Slider(value = maximumDetour, onValueChange = { maximumDetour = it }, onValueChangeFinished = {
                        scope.launch {
                            try {
                                val response = ApiClient.getService().updateSharedRidePreferences(SharedRidePreferenceRequest(maximumDetour.toDouble()))
                                preferenceStatus = if (response.isSuccessful) "Your detour limit is saved for future shared rides." else "This limit applies now but could not be saved for next time."
                            } catch (_: Exception) { preferenceStatus = "This limit applies now but could not be saved for next time." }
                        }
                    }, valueRange = 1f..25f, steps = 23, colors = SliderDefaults.colors(thumbColor = LuxuryColors.ChampagneGold, activeTrackColor = LuxuryColors.ChampagneGold))
                    Text("Matches rank by lowest detour, then oldest open pool for fair rotation.", color = LuxuryColors.MutedSilver, style = MaterialTheme.typography.bodySmall)
                    preferenceStatus?.let { Text(it, color = LuxuryColors.MutedSilver, style = MaterialTheme.typography.bodySmall) }
                }

                SharedRideCard("Matching pools") {
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = LuxuryColors.ChampagneGold)
                    if (!loading && matches.isEmpty()) Text("No compatible open pool is available within this detour limit. Choose the explicit new-pool fallback below to continue.", color = LuxuryColors.MutedSilver)
                    matches.forEach { option ->
                        val id = option["trip_id"]?.toString().orEmpty()
                        val available = (option["available_seats"] as? Number)?.toInt() ?: 0
                        if (available >= seats) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selectedPool == id, onClick = { selectedPool = id; selectedPoolAvailable = available }, colors = RadioButtonDefaults.colors(selectedColor = LuxuryColors.ChampagneGold))
                            Column(Modifier.weight(1f)) {
                                Text("${"%.1f".format((option["detour_km"] as? Number)?.toDouble() ?: 0.0)} km detour · ${option["pickup_eta_minutes"] ?: "—"} min pickup estimate", color = LuxuryColors.PlatinumWhite)
                                Text("$available seats currently available · ${if (option["driver_assigned"] == true) "chauffeur assigned" else "chauffeur pending"}", color = LuxuryColors.MutedSilver, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (selectedPool != null) Text("Seat count refreshes every 10 seconds and is revalidated when you book.", color = LuxuryColors.MutedSilver, style = MaterialTheme.typography.bodySmall)
                    selectedPool?.let { poolId -> TextButton(onClick = {
                        scope.launch {
                            refreshingSeats = true
                            try {
                                val response = ApiClient.getService().getPoolSeatAvailability(poolId)
                                if (response.isSuccessful) {
                                    val body = response.body().orEmpty()
                                    selectedPoolAvailable = (body["available_seats"] as? Number)?.toInt() ?: 0
                                    if (selectedPoolAvailable < seats) { selectedPool = null; error = "That pool no longer has enough seats. Choose another match or enable the new-pool fallback." }
                                } else error = "Seat availability could not be refreshed (${response.code()})."
                            } catch (_: Exception) { error = "Seat availability could not be refreshed. Check your connection." }
                            refreshingSeats = false
                        }
                    }, enabled = !refreshingSeats) { Text(if (refreshingSeats) "Refreshing seats…" else "Refresh seat availability", color = LuxuryColors.ChampagneGold) } }
                }

                estimate?.let { quote -> SharedRideCard("Fare and expiry") {
                    Text("Shared fare · ₹${"%.2f".format(quote.totalPayableCents / 100.0)}", color = LuxuryColors.ChampagneGold, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Solo reference · ₹${"%.2f".format(quote.soloFareCents / 100.0)} per passenger", color = LuxuryColors.PlatinumWhite)
                    Text("Estimated savings · ₹${"%.2f".format(quote.guestSavingsCents / 100.0)} · ${quote.sharedDiscountPercentage}% policy discount", color = LuxuryColors.EmeraldGreen)
                    Text("${"%.1f".format(quote.distanceKm)} km · ${"%.0f".format(quote.estimatedDurationMinutes)} min estimated · fare split is shown after confirmation", color = LuxuryColors.MutedSilver, style = MaterialTheme.typography.bodySmall)
                    Text(if (countdown > 0) "Fare quote expires in ${countdown / 60}:${(countdown % 60).toString().padStart(2, '0')}" else "Fare quote expired. Refresh before booking.", color = if (countdown > 0) LuxuryColors.MutedSilver else MaterialTheme.colorScheme.error)
                } }

                SharedRideCard("Privacy and fallback") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(shareStops, { shareStops = it }, colors = CheckboxDefaults.colors(checkedColor = LuxuryColors.ChampagneGold))
                        Text("Allow fellow passengers to see my pickup and drop-off details", color = LuxuryColors.PlatinumWhite, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(createNewPoolIfFull, { createNewPoolIfFull = it }, colors = CheckboxDefaults.colors(checkedColor = LuxuryColors.ChampagneGold))
                        Text("If no match is available or this pool fills, allow a new shared pool", color = LuxuryColors.PlatinumWhite, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Names and contact details are not shared with co-passengers. New-pool fallback is off unless you choose it.", color = LuxuryColors.MutedSilver, style = MaterialTheme.typography.bodySmall)
                }

                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (estimate == null || error?.contains("could not refresh", ignoreCase = true) == true || error?.contains("could not price", ignoreCase = true) == true) {
                    TextButton(onClick = { requestGeneration += 1 }, enabled = !loading) { Text("Refresh quote and matches", color = LuxuryColors.ChampagneGold) }
                }
                success?.let { Text(it, color = LuxuryColors.EmeraldGreen) }
                Button(
                    onClick = {
                        val quote = estimate ?: return@Button
                        if (countdown <= 0) { error = "Fare quote expired. Recalculate before booking."; return@Button }
                        if (selectedPool != null && selectedPoolAvailable < seats) { error = "Refresh seat availability before booking."; return@Button }
                        booking = true
                        error = null
                        scope.launch {
                            try {
                                val response = ApiClient.getService().bookSharedRide(BookSharedRideRequest(
                                    categoryId = categoryId,
                                    estimateId = quote.estimateId,
                                    pickupAddress = pickupAddress,
                                    pickupLatitude = pickupLatitude,
                                    pickupLongitude = pickupLongitude,
                                    destinationAddress = destinationAddress,
                                    destinationLatitude = destinationLatitude,
                                    destinationLongitude = destinationLongitude,
                                    seatsReserved = seats,
                                    targetPooledTripId = selectedPool,
                                    fallbackPolicy = if (createNewPoolIfFull) "CREATE_NEW_POOL" else "FAIL",
                                    shareStopDetailsWithCoPassengers = shareStops,
                                    maximumDetourKm = maximumDetour.toDouble(),
                                ))
                                val body = response.body()
                                if (response.isSuccessful && body != null) {
                                    val rideId = body["ride_id"]?.toString().orEmpty()
                                    val poolId = body["pooled_trip_id"]?.toString().orEmpty()
                                    val bookingId = body["booking_id"]?.toString().orEmpty()
                                    val otp = body["start_otp"]?.toString().orEmpty()
                                    if (rideId.isBlank() || poolId.isBlank() || bookingId.isBlank()) error = "Booking response was incomplete. Contact support before retrying."
                                    else {
                                        success = "Booking confirmed for $seats passenger${if (seats == 1) "" else "s"}."
                                        onBooked(rideId, otp, poolId, bookingId)
                                    }
                                } else error = response.errorBody()?.string()?.take(240) ?: "Shared booking could not be confirmed (${response.code()}). Refresh seats and try again."
                            } catch (_: Exception) { error = "Booking could not be confirmed. Check your connection; do not assume a reservation was created." }
                            booking = false
                        }
                    },
                    enabled = !loading && !booking && estimate != null && countdown > 0 && (selectedPool != null && selectedPoolAvailable >= seats || selectedPool == null && createNewPoolIfFull),
                    colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.ChampagneGold, contentColor = LuxuryColors.ObsidianBlack),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(if (booking) "CONFIRMING SHARED SEATS…" else "CONFIRM SHARED RIDE · ₹${"%.2f".format((estimate?.totalPayableCents ?: 0) / 100.0)}", fontWeight = FontWeight.Bold) }
                Text("Cancellation is available before the trip begins. The server rechecks category eligibility, quote expiry and seat capacity at confirmation.", color = LuxuryColors.MutedSilver, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun SharedTripConsoleScreen(
    tripId: String,
    rideId: String,
    bookingId: String,
    onBack: () -> Unit,
    onOpenLiveTracking: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var details by remember { mutableStateOf<Map<String, Any>?>(null) }
    var messages by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var showCancel by remember { mutableStateOf(false) }
    var showSafetyReport by remember { mutableStateOf(false) }
    var incidentText by remember { mutableStateOf("") }
    var cancelled by remember { mutableStateOf(false) }

    suspend fun refresh() {
        refreshing = true
        try {
            val d = ApiClient.getService().getSharedTripDetails(tripId)
            val m = ApiClient.getService().getSharedTripMessages(tripId)
            if (d.isSuccessful && d.body() != null) { details = d.body(); error = null }
            else error = "Shared trip status unavailable (${d.code()}). Retry when connected."
            if (m.isSuccessful) messages = m.body().orEmpty()
        } catch (_: Exception) { error = "Could not refresh shared trip details. Check your connection." }
        loading = false
        refreshing = false
    }

    LaunchedEffect(tripId) {
        refresh()
        while (true) { delay(12_000); refresh() }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("SHARED TRIP", color = LuxuryColors.ChampagneGold, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Pool ${tripId.take(8).uppercase()} · your booking ${bookingId.take(8).uppercase()}", color = LuxuryColors.MutedSilver, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onBack) { Text("Back", color = LuxuryColors.ChampagneGold) }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = LuxuryColors.ChampagneGold)
        error?.let { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(it, color = MaterialTheme.colorScheme.error); OutlinedButton(onClick = { scope.launch { refresh() } }) { Text("Retry") } } }
        notice?.let { Text(it, color = LuxuryColors.EmeraldGreen) }
        details?.let { data ->
            val maxSeats = (data["max_seats"] as? Number)?.toInt() ?: 0
            val reserved = (data["reserved_seats"] as? Number)?.toInt() ?: 0
            val occupancy = (data["current_occupancy"] as? Number)?.toInt() ?: 0
            SharedRideCard("Live pool status") {
                Text("${data["status"] ?: "—"} · $reserved of $maxSeats seats reserved", color = LuxuryColors.PlatinumWhite, style = MaterialTheme.typography.titleMedium)
                Text("$occupancy passenger seats currently confirmed aboard · ${maxSeats - reserved} seat${if (maxSeats - reserved == 1) "" else "s"} open", color = LuxuryColors.MutedSilver)
                Text("Updates ${if (refreshing) "refreshing…" else "automatically every 12 seconds"}. Seat totals are server confirmed.", color = LuxuryColors.MutedSilver, style = MaterialTheme.typography.bodySmall)
                Text("Your booking · $bookingId · ${detailsBookingStatus(data, rideId)}", color = LuxuryColors.ChampagneGold)
            }
            SharedRideCard("Pickup and drop-off order") {
                val stops = (data["stops"] as? List<*>)?.filterIsInstance<Map<String, Any>>().orEmpty().sortedBy { (it["sequence_order"] as? Number)?.toInt() ?: Int.MAX_VALUE }
                if (stops.isEmpty()) Text("Stop order is not available yet.", color = LuxuryColors.MutedSilver)
                stops.forEach { stop ->
                    val order = stop["sequence_order"]?.toString() ?: "—"
                    val type = stop["stop_type"]?.toString()?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Stop"
                    Text("$order · $type · ${stop["address"] ?: "Private stop"} · ${stop["status"] ?: "SCHEDULED"}", color = LuxuryColors.PlatinumWhite)
                }
                Text("Passenger names and contact details are never displayed. Stop addresses marked private follow each passenger’s privacy choice.", color = LuxuryColors.MutedSilver, style = MaterialTheme.typography.bodySmall)
            }
            SharedRideCard("Passengers and safety") {
                val manifest = (data["manifest"] as? List<*>)?.filterIsInstance<Map<String, Any>>().orEmpty()
                Text("${manifest.size} booking${if (manifest.size == 1) "" else "s"} in this pool", color = LuxuryColors.PlatinumWhite)
                manifest.forEach { row ->
                    Text("${row["seats_reserved"] ?: 0} seat${if ((row["seats_reserved"] as? Number)?.toInt() == 1) "" else "s"} · ${row["booking_status"] ?: "PENDING"}${if (row["is_mine"] == true) " · your booking" else ""}", color = if (row["is_mine"] == true) LuxuryColors.ChampagneGold else LuxuryColors.MutedSilver)
                }
                OutlinedButton(onClick = { showSafetyReport = true }) { Text("Report a shared-trip safety issue") }
            }
        }
        SharedRideCard("Pickup coordination") {
            if (messages.isEmpty()) Text("No coordination messages yet. Keep messages focused on pickup and trip logistics.", color = LuxuryColors.MutedSilver)
            messages.takeLast(15).forEach { row ->
                Text("${row["sender"] ?: "Passenger"} · ${row["created_at"]?.toString()?.take(16) ?: ""}", color = LuxuryColors.ChampagneGold, style = MaterialTheme.typography.labelSmall)
                Text(row["message"]?.toString().orEmpty(), color = LuxuryColors.PlatinumWhite)
            }
            OutlinedTextField(value = message, onValueChange = { message = it.take(1000) }, label = { Text("Pickup message · max 1,000 characters") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                val text = message.trim()
                if (text.isEmpty()) { notice = "Enter a message before sending."; return@Button }
                scope.launch { try { val r = ApiClient.getService().sendSharedTripMessage(tripId, mapOf("message" to text)); if (r.isSuccessful) { message = ""; notice = "Message sent to shared-trip participants."; refresh() } else notice = "Message could not be sent (${r.code()})." } catch (_: Exception) { notice = "Message could not be sent. Check your connection." } }
            }, enabled = message.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.ChampagneGold, contentColor = LuxuryColors.ObsidianBlack)) { Text("Send message") }
        }
        SharedRideCard("Fare and cancellation") {
            val financial = details?.get("financial_summary") as? Map<*, *>
            Text("Your allocated fare · ₹${(((financial?.get("total_fare_cents") as? Number)?.toInt() ?: 0) / 100.0)} total trip fare reconciliation", color = LuxuryColors.PlatinumWhite)
            Text("Fare components are stored per booking. Any future adjustment must be recorded by the authorized fare workflow; this screen does not invent extra charges.", color = LuxuryColors.MutedSilver, style = MaterialTheme.typography.bodySmall)
            Text("You may cancel while your booking is confirmed and the ride has not begun. This cancellation path currently records no fare adjustment.", color = LuxuryColors.MutedSilver, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { showCancel = true }, enabled = !cancelled && detailsBookingStatus(details.orEmpty(), rideId) == "CONFIRMED") { Text(if (cancelled) "Booking cancelled" else "Cancel shared booking") }
        }
        Button(onClick = onOpenLiveTracking, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.ChampagneGold, contentColor = LuxuryColors.ObsidianBlack)) { Text("OPEN LIVE RIDE TRACKING", fontWeight = FontWeight.Bold) }
    }

    if (showCancel) AlertDialog(
        onDismissRequest = { showCancel = false },
        title = { Text("Cancel shared booking?") },
        text = { Text("The booking is cancelled only after the server confirms eligibility. This action is allowed before the ride begins and currently posts no fare adjustment.") },
        confirmButton = { TextButton(onClick = { scope.launch { try { val r = ApiClient.getService().cancelSharedRide(bookingId); if (r.isSuccessful) { cancelled = true; notice = r.body()?.get("policy")?.toString() ?: "Shared booking cancelled."; showCancel = false; refresh() } else notice = r.errorBody()?.string()?.take(200) ?: "Cancellation was declined (${r.code()})." } catch (_: Exception) { notice = "Cancellation status is unknown. Refresh the booking before retrying." } } }) { Text("Confirm cancellation") } },
        dismissButton = { TextButton(onClick = { showCancel = false }) { Text("Keep booking") } },
    )

    if (showSafetyReport) AlertDialog(
        onDismissRequest = { showSafetyReport = false }, title = { Text("Shared-trip safety report") },
        text = { OutlinedTextField(incidentText, { incidentText = it.take(3000) }, label = { Text("Describe what happened") }, minLines = 3) },
        confirmButton = { TextButton(onClick = {
            val clean = incidentText.trim()
            if (clean.length < 10) { notice = "Please add at least 10 characters of detail."; return@TextButton }
            scope.launch { try { val response = ApiClient.getService().lodgeIncidentReport(IncidentReportRequest(rideId = rideId, category = "SHARED_RIDE_SAFETY", severity = "MEDIUM", description = clean)); if (response.isSuccessful) { notice = response.body()?.message ?: "Report recorded."; showSafetyReport = false; incidentText = "" } else notice = "Safety report could not be saved (${response.code()})." } catch (_: Exception) { notice = "Safety report could not be saved. Call 112 in immediate danger." } }
        }) { Text("Submit report") } }, dismissButton = { TextButton(onClick = { showSafetyReport = false }) { Text("Cancel") } },
    )
}

private fun detailsBookingStatus(data: Map<String, Any>, rideId: String): String {
    val manifest = (data["manifest"] as? List<*>)?.filterIsInstance<Map<String, Any>>().orEmpty()
    return manifest.firstOrNull { it["ride_id"] == rideId }?.get("booking_status")?.toString() ?: "UNKNOWN"
}

@Composable
private fun SharedRideCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = LuxuryColors.CardSurface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = LuxuryColors.ChampagneGold, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}
