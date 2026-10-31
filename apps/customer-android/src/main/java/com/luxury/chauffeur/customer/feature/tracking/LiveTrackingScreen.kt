package com.luxury.chauffeur.customer.feature.tracking

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.luxury.chauffeur.customer.core.designsystem.*
import com.luxury.chauffeur.customer.core.network.*
import com.luxury.chauffeur.customer.feature.map.LuxuryMapView
import com.luxury.chauffeur.customer.feature.safety.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.*

/**
 * Part 5: Comprehensive Live Tracking, Communication, Route Governance & Post-Trip
 * Features 101 - 125
 */
@Composable
fun LiveTrackingScreen(
    rideId: String,
    startOtp: String,
    onRideCancelled: () -> Unit,
    onProceedPayment: (rideId: String, amountRupees: Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gson = remember { Gson() }

    // Live ride lifecycle states: REQUESTED -> ASSIGNED -> ARRIVED -> IN_PROGRESS -> COMPLETED
    var tripStatus by remember { mutableStateOf("ASSIGNED") }
    var etaMinutes by remember { mutableIntStateOf(4) }
    var etaRangeMin by remember { mutableIntStateOf(3) }
    var etaRangeMax by remember { mutableIntStateOf(6) }
    var etaConfidence by remember { mutableIntStateOf(95) }
    var pickupCountdownSeconds by remember { mutableIntStateOf(240) }
    var freshnessSeconds by remember { mutableIntStateOf(2) }
    var freshnessStatus by remember { mutableStateOf("FRESH_GPS") }

    var showCancelDialog by remember { mutableStateOf(false) }
    var isCancelling by remember { mutableStateOf(false) }
    var isWsConnected by remember { mutableStateOf(false) }
    var reconnectAttempts by remember { mutableIntStateOf(0) }

    // Rich ride details from backend
    var rideDetails by remember { mutableStateOf<RideResponseDto?>(null) }
    var telemetryData by remember { mutableStateOf<AdvancedTelemetryResponse?>(null) }
    var consecutive404Count by remember { mutableIntStateOf(0) }
    var showStopsManagerDialog by remember { mutableStateOf(false) }
    var isExportingCalendar by remember { mutableStateOf(false) }
    var showDestinationChangeDialog by remember { mutableStateOf(false) }
    var destinationChangeStatus by remember { mutableStateOf<String?>(null) }
    var proposedDestination by remember { mutableStateOf<Map<String, Any>?>(null) }
    var newDestinationAddress by remember { mutableStateOf("") }
    var newDestinationLat by remember { mutableStateOf("") }
    var newDestinationLng by remember { mutableStateOf("") }

    // Chauffeur & Vehicle details
    var driverName by remember { mutableStateOf("Master Chauffeur") }
    var driverLicense by remember { mutableStateOf("Luxury Commercial Fleet") }
    var driverRating by remember { mutableDoubleStateOf(4.96) }
    var driverTrips by remember { mutableIntStateOf(142) }
    var driverPhone by remember { mutableStateOf("+91 22 6900 4820") }
    var vehicleModel by remember { mutableStateOf("Mercedes-Benz E-Class 220d Exclusive") }
    var vehiclePlate by remember { mutableStateOf("MH 01 DX 4921") }
    var vehicleColor by remember { mutableStateOf("Obsidian Metallic Black") }
    var arrivalPhotoUrl by remember { mutableStateOf("https://images.unsplash.com/photo-1549317661-bd32c8ce0db2?auto=format&fit=crop&w=600&q=80") }

    // Virtual Masked Phone
    var maskedNumber by remember { mutableStateOf("+91 22 6900 4820") }
    var maskedExt by remember { mutableStateOf("4921") }

    // Route attributes
    var routePreference by remember { mutableStateOf("FASTEST") }
    var routeDeviationDetected by remember { mutableStateOf(false) }
    var routeDeviationReason by remember { mutableStateOf<String?>(null) }
    var trafficDelayMinutes by remember { mutableIntStateOf(2) }
    var trafficNote by remember { mutableStateOf("Minor congestion on Senapati Bapat Marg (+2m)") }
    var accessibilityText by remember { mutableStateOf("Chauffeur approaching pickup point.") }

    var finalFareRupees by remember { mutableIntStateOf(1600) }

    // Coordinates for live tracking (Feature 101: Live approach map)
    var pickupLatLng by remember { mutableStateOf(LatLng(18.9220, 72.8347)) }
    var pickupTitle by remember { mutableStateOf("Pickup: Current Location") }
    var destLatLng by remember { mutableStateOf(LatLng(19.0974, 72.8744)) }
    var destTitle by remember { mutableStateOf("Dest: CSM T2 VIP Terminal") }
    var driverLatLng by remember { mutableStateOf(LatLng(18.9350, 72.8310)) }

    // Dialog & Sheet State
    var showShareDialog by remember { mutableStateOf(false) }
    var showChatDialog by remember { mutableStateOf(false) }
    var showMaskedCallDialog by remember { mutableStateOf(false) }
    var showArrivalPhotoDialog by remember { mutableStateOf(false) }
    var showRoutePreviewDialog by remember { mutableStateOf(false) }
    var showTimelineSheet by remember { mutableStateOf(false) }
    var showReceiptDialog by remember { mutableStateOf(false) }
    var showLostItemDialog by remember { mutableStateOf(false) }

    // Part 6 Safety & Emergency State (Features 126 - 150)
    var showSosDialog by remember { mutableStateOf(false) }
    var showContactsDialog by remember { mutableStateOf(false) }
    var showSafetyCheckinDialog by remember { mutableStateOf(false) }
    var showCredentialsDialog by remember { mutableStateOf(false) }
    var showSafetyCenterDialog by remember { mutableStateOf(false) }
    var showRecordingConsentDialog by remember { mutableStateOf(false) }
    var showIncidentDialog by remember { mutableStateOf(false) }
    var showBlockDriverDialog by remember { mutableStateOf(false) }
    var showSafeZonesDialog by remember { mutableStateOf(false) }
    var isHighRiskPickup by remember { mutableStateOf(false) }
    var unexpectedStopDetected by remember { mutableStateOf(false) }

    // Feature 106: Trigger Background Trip Tracking Notification
    fun postTrackingNotification(statusText: String, etaText: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "chauffeur_live_tracking"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Grand Chauffeur Live Tracking",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Live trip tracking notifications and chauffeur approach updates"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_menu_directions)
                .setContentTitle("Grand Chauffeur: $statusText")
                .setContentText("$vehicleModel ($vehiclePlate) • $etaText")
                .setOngoing(tripStatus != "COMPLETED" && tripStatus != "CANCELLED")
                .setPriority(NotificationCompat.PRIORITY_LOW)

            notificationManager.notify(1001, builder.build())
        } catch (_: Exception) {}
    }

    // Feature 105: Automatic Tracking Reconnect with Exponential Backoff
    fun connectTrackingWebSocket() {
        val token = ApiClient.getToken() ?: "mock_jwt_customer"
        val wsUrl = ApiClient.getWebSocketUrl("api/v1/ws/rides/$rideId/tracking?token=$token")
        val client = ApiClient.getOkHttpClient()
        val request = Request.Builder().url(wsUrl).build()

        client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                isWsConnected = true
                reconnectAttempts = 0
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val map = gson.fromJson<Map<String, Any>>(text, Map::class.java)
                    val status = map["status"] as? String
                    if (!status.isNullOrBlank()) {
                        tripStatus = when (status) {
                            "REQUESTED", "DRIVER_ASSIGNED" -> "ASSIGNED"
                            "DRIVER_ARRIVED" -> "ARRIVED"
                            "IN_PROGRESS" -> "IN_PROGRESS"
                            "COMPLETED" -> "COMPLETED"
                            "CANCELLED" -> "CANCELLED"
                            else -> status
                        }
                        if (tripStatus == "COMPLETED") {
                            val fare = ((map["final_fare_cents"] as? Number)?.toInt() ?: 160000) / 100
                            finalFareRupees = fare
                            onProceedPayment(rideId, fare)
                        }
                    }
                    val dLat = (map["driver_latitude"] as? Number)?.toDouble()
                    val dLng = (map["driver_longitude"] as? Number)?.toDouble()
                    if (dLat != null && dLng != null) {
                        driverLatLng = LatLng(dLat, dLng)
                    }
                } catch (_: Exception) {}
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                isWsConnected = false
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                isWsConnected = false
                // Exponential reconnect backoff
                if (tripStatus != "COMPLETED" && tripStatus != "CANCELLED") {
                    scope.launch {
                        reconnectAttempts++
                        val backoffDelay = (1000L * (1 shl minOf(reconnectAttempts, 4)))
                        delay(backoffDelay)
                        connectTrackingWebSocket()
                    }
                }
            }
        })
    }

    DisposableEffect(rideId) {
        connectTrackingWebSocket()
        onDispose {}
    }

    // Telemetry and HTTP Polling backup
    fun fetchAdvancedTelemetry() {
        if (rideId.isBlank()) return
        scope.launch {
            try {
                val res = ApiClient.getService().getRideTelemetry(rideId)
                if (res.isSuccessful && res.body() != null) {
                    consecutive404Count = 0
                    val tel = res.body()!!
                    telemetryData = tel
                    tripStatus = tel.status
                    etaMinutes = tel.etaMinutes
                    etaRangeMin = tel.etaRangeMin
                    etaRangeMax = tel.etaRangeMax
                    etaConfidence = tel.etaConfidencePercentage
                    pickupCountdownSeconds = tel.pickupCountdownSeconds
                    freshnessSeconds = tel.freshnessSeconds
                    freshnessStatus = tel.freshnessStatus
                    vehicleModel = tel.vehicleModel
                    vehiclePlate = tel.vehiclePlateNumber
                    vehicleColor = tel.vehicleColor
                    tel.driverArrivalPhotoUrl?.let { arrivalPhotoUrl = it }
                    maskedNumber = tel.maskedVirtualNumber
                    maskedExt = tel.maskedVirtualExt
                    routePreference = tel.routePreference
                    routeDeviationDetected = tel.routeDeviationDetected
                    routeDeviationReason = tel.routeDeviationReason
                    trafficDelayMinutes = tel.trafficDelayMinutes
                    trafficNote = tel.trafficNote
                    accessibilityText = tel.accessibilityAnnouncement

                    driverLatLng = LatLng(tel.driverLatitude, tel.driverLongitude)
                    pickupLatLng = LatLng(tel.pickupLatitude, tel.pickupLongitude)
                    pickupTitle = tel.pickupAddress
                    destLatLng = LatLng(tel.destinationLatitude, tel.destinationLongitude)
                    destTitle = tel.destinationAddress

                    if (tel.driver != null) {
                        driverName = tel.driver.name ?: "Master Chauffeur"
                        driverLicense = tel.driver.licenseNumber ?: "Luxury Fleet"
                        driverRating = tel.driver.rating ?: 4.95
                        driverTrips = tel.driver.totalTrips ?: 150
                    }

                    // Feature 106: Update background notification
                    postTrackingNotification(
                        statusText = when (tel.status) {
                            "ASSIGNED" -> "Chauffeur En Route"
                            "ARRIVED" -> "Chauffeur Arrived"
                            "IN_PROGRESS" -> "Journey In Progress"
                            else -> "Journey Finished"
                        },
                        etaText = "ETA: ${tel.etaMinutes}m (${tel.etaRangeMin}-${tel.etaRangeMax}m range)"
                    )
                }

                // Also refresh main ride details
                val rideRes = ApiClient.getService().getRideDetails(rideId)
                if (rideRes.isSuccessful && rideRes.body() != null) {
                    consecutive404Count = 0
                    rideDetails = rideRes.body()
                    if (rideRes.body()!!.status == "COMPLETED") {
                        val fare = (rideRes.body()!!.finalFareCents ?: rideRes.body()!!.estimatedFareCents) / 100
                        finalFareRupees = fare
                        onProceedPayment(rideId, fare)
                    }
                } else if (rideRes.code() == 404 || res.code() == 404) {
                    consecutive404Count++
                    if (consecutive404Count >= 3) {
                        tripStatus = "CANCELLED"
                        Toast.makeText(context, "Ride session ended or not found on server.", Toast.LENGTH_LONG).show()
                        onRideCancelled()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // Polling cycle every 3 seconds
    LaunchedEffect(rideId) {
        if (rideId.isBlank()) {
            return@LaunchedEffect
        }
        while (tripStatus != "COMPLETED" && tripStatus != "CANCELLED") {
            fetchAdvancedTelemetry()
            delay(3000)
        }
    }

    // Feature 103: Live Pickup Countdown decrementer
    LaunchedEffect(pickupCountdownSeconds) {
        if (pickupCountdownSeconds > 0 && (tripStatus == "ASSIGNED" || tripStatus == "REQUESTED")) {
            delay(1000)
            pickupCountdownSeconds--
        }
    }

    // Dialog instances
    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { if (!isCancelling) showCancelDialog = false },
            containerColor = LuxuryColors.CardSurface,
            title = {
                Text("CANCEL RESERVATION?", style = LuxuryTypography.HeadlineMedium.copy(color = LuxuryColors.CrimsonAlert))
            },
            text = {
                Text(
                    text = "A cancellation fee may apply according to our chauffeur allocation policy if cancelled after dispatch.",
                    style = LuxuryTypography.BodyRegular
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isCancelling = true
                        scope.launch {
                            try {
                                ApiClient.getService().cancelRide(rideId, mapOf("reason" to "Guest requested cancellation"))
                            } catch (_: Exception) {}
                            showCancelDialog = false
                            isCancelling = false
                            onRideCancelled()
                        }
                    },
                    enabled = !isCancelling
                ) {
                    Text(if (isCancelling) "CANCELLING..." else "YES, CANCEL", color = LuxuryColors.CrimsonAlert, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }, enabled = !isCancelling) {
                    Text("KEEP RESERVATION", color = LuxuryColors.ChampagneGold)
                }
            }
        )
    }

    if (showStopsManagerDialog) {
        ActiveStopsManagerDialog(
            rideId = rideId,
            currentStops = rideDetails?.stops ?: emptyList(),
            onDismiss = { showStopsManagerDialog = false },
            onStopsUpdated = {
                fetchAdvancedTelemetry()
                Toast.makeText(context, "Waypoints updated", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Part 5 Dialogs
    if (showShareDialog) {
        TripShareDialog(rideId = rideId, onDismiss = { showShareDialog = false })
    }

    if (showChatDialog) {
        InAppChauffeurChatDialog(
            rideId = rideId,
            chauffeurName = driverName,
            onDismiss = { showChatDialog = false }
        )
    }

    if (showMaskedCallDialog) {
        MaskedCallDialog(
            virtualNumber = maskedNumber,
            virtualExt = maskedExt,
            chauffeurName = driverName,
            onDismiss = { showMaskedCallDialog = false }
        )
    }

    if (showArrivalPhotoDialog) {
        DriverArrivalPhotoDialog(
            photoUrl = arrivalPhotoUrl,
            vehicleModel = vehicleModel,
            licensePlate = vehiclePlate,
            onDismiss = { showArrivalPhotoDialog = false }
        )
    }

    if (showRoutePreviewDialog) {
        RoutePreviewAndPreferenceDialog(
            rideId = rideId,
            currentPreference = routePreference,
            hasRouteDeviation = routeDeviationDetected,
            deviationReason = routeDeviationReason,
            onPreferenceChanged = { routePreference = it },
            onDismiss = { showRoutePreviewDialog = false }
        )
    }

    if (showTimelineSheet) {
        TripStatusTimelineSheet(
            timelineSteps = telemetryData?.timeline ?: emptyList(),
            onDismiss = { showTimelineSheet = false }
        )
    }

    if (showReceiptDialog) {
        TripCompletionReceiptDialog(rideId = rideId, onDismiss = { showReceiptDialog = false })
    }

    if (showLostItemDialog) {
        LostItemReportDialog(rideId = rideId, onDismiss = { showLostItemDialog = false })
    }

    Scaffold(
        containerColor = LuxuryColors.ObsidianBlack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Status, ETA Confidence Range, Countdown & Freshness
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = when (tripStatus) {
                            "ASSIGNED" -> "CHAUFFEUR EN ROUTE"
                            "ARRIVED" -> "CHAUFFEUR HAS ARRIVED"
                            "IN_PROGRESS" -> "JOURNEY IN PROGRESS"
                            else -> "JOURNEY COMPLETED"
                        },
                        style = LuxuryTypography.HeadlineMedium.copy(color = LuxuryColors.ChampagneGold, fontSize = 18.sp)
                    )

                    // Features 102 & 104: ETA Confidence Range & Location Freshness
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (tripStatus == "ASSIGNED") "$etaRangeMin-$etaRangeMax mins ($etaConfidence% conf)" else "Active Ride: ${rideId.take(8)}",
                            style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        )
                        Text("•", color = LuxuryColors.SubtleGray)
                        Text(
                            text = if (isWsConnected) "LIVE WS" else if (reconnectAttempts > 0) "RECONNECTING ($reconnectAttempts)" else "HTTP BACKUP",
                            style = LuxuryTypography.BodySubtle.copy(
                                color = if (isWsConnected) LuxuryColors.LuxuryEmerald else LuxuryColors.ChampagneGold,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    // Feature 104: Freshness Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (freshnessSeconds <= 15) LuxuryColors.LuxuryEmerald else LuxuryColors.ChampagneGold)
                        )
                        Text(
                            text = "GPS: $freshnessSeconds" + "s ago ($freshnessStatus)",
                            style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp, color = LuxuryColors.SubtleGray)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    GoldBadge(tripStatus)
                    // Feature 103: Pickup countdown timer
                    if (tripStatus == "ASSIGNED" && pickupCountdownSeconds > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val mins = pickupCountdownSeconds / 60
                        val secs = pickupCountdownSeconds % 60
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = LuxuryColors.CardSurface,
                            border = BorderStroke(1.dp, LuxuryColors.ChampagneGold.copy(alpha = 0.5f))
                        ) {
                            Text(
                                "⏱ %02d:%02d".format(mins, secs),
                                style = LuxuryTypography.BodySubtle.copy(
                                    color = LuxuryColors.ChampagneGold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Part 6 Feature 126 & 133: In-Trip Emergency Action Bar (SOS, Safety Center, Credentials)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showSosDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = SosRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1.3f).height(44.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("🚨 SOS EMERGENCY", color = LuxuryColors.PlatinumWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { showSafetyCenterDialog = true },
                    border = BorderStroke(1.dp, LuxuryColors.ChampagneGold),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = LuxuryColors.CardSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(44.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Text("🛡️ SAFETY HUB", color = LuxuryColors.ChampagneGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { showCredentialsDialog = true },
                    border = BorderStroke(1.dp, LuxuryColors.CardBorder),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = LuxuryColors.CardSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(0.9f).height(44.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text("🆔 CREDS", color = LuxuryColors.PlatinumWhite, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Part 6 Feature 128: Wellness Check-in Banner
            Surface(
                shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                color = LuxuryColors.DeepGraphite,
                border = BorderStroke(1.dp, LuxuryColors.ChampagneGold.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth().clickable { showSafetyCheckinDialog = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("⏱️", fontSize = 14.sp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AUTOMATED SAFETY CHECK-IN", style = LuxuryTypography.TitleGold.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold))
                        Text("Confirm your comfort during this voyage", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp, color = LuxuryColors.PlatinumWhite))
                    }
                    Text("CHECK IN ❯", color = LuxuryColors.ChampagneGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Feature 118: Route Deviation Notice Banner
            if (routeDeviationDetected) {
                Surface(
                    shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                    color = LuxuryColors.CrimsonAlert.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, LuxuryColors.CrimsonAlert),
                    modifier = Modifier.fillMaxWidth().clickable { showRoutePreviewDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("⚠️", fontSize = 16.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("ROUTE DEVIATION DETECTED", style = LuxuryTypography.TitleGold.copy(color = LuxuryColors.CrimsonAlert, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                            Text(routeDeviationReason ?: "Chauffeur altered corridor. Tap to review.", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp))
                        }
                        Text("VIEW ❯", color = LuxuryColors.CrimsonAlert, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Feature 120: Real-time Traffic Adjustment Pill
            if (trafficDelayMinutes > 0) {
                Surface(
                    shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                    color = LuxuryColors.DeepGraphite,
                    border = BorderStroke(1.dp, LuxuryColors.CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("🚦", fontSize = 13.sp)
                        Text(trafficNote, style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp, color = LuxuryColors.PlatinumWhite), modifier = Modifier.weight(1f))
                        GoldBadge("+$trafficDelayMinutes m")
                    }
                }
            }

            // Feature 101: Live Google Maps Driver Approach Surface
            Surface(
                shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                color = LuxuryColors.DeepGraphite,
                border = BorderStroke(1.dp, LuxuryColors.CardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) {
                Box {
                    LuxuryMapView(
                        modifier = Modifier.fillMaxSize(),
                        pickupLatLng = pickupLatLng,
                        pickupTitle = pickupTitle,
                        destLatLng = destLatLng,
                        destTitle = destTitle,
                        driverLatLng = driverLatLng
                    )
                    Surface(
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        color = LuxuryColors.CardSurface.copy(alpha = 0.92f),
                        border = BorderStroke(1.dp, LuxuryColors.ChampagneGold),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("TRIP PASSCODE: ", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                            Text(startOtp, style = LuxuryTypography.OtpDigits.copy(fontSize = 16.sp))
                        }
                    }
                }
            }

            // Feature 116: Vehicle & Plate Confirmation Card
            VehiclePlateConfirmationCard(
                vehicleModel = vehicleModel,
                licensePlate = vehiclePlate,
                vehicleColor = vehicleColor,
                startOtp = startOtp,
                onViewArrivalPhoto = { showArrivalPhotoDialog = true }
            )

            // Live Chauffeur Details Card
            LuxuryCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(driverName, style = LuxuryTypography.HeadlineMedium.copy(fontSize = 17.sp))
                        Text(
                            "Master Chauffeur • ★ %.2f (%d+ Trips)".format(driverRating, driverTrips),
                            style = LuxuryTypography.TitleGold.copy(fontSize = 12.sp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("License: $driverLicense", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                    }
                    GoldBadge("VERIFIED")
                }
            }

            // Action Ribbon: Chat (F110), Masked Call (F111), Share Trip (F107), Route (F117), Timeline (F122)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = { showChatDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.CardSurface),
                    border = BorderStroke(1.dp, LuxuryColors.ChampagneGold),
                    modifier = Modifier.weight(1f).height(42.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("💬 CHAT", color = LuxuryColors.ChampagneGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { showMaskedCallDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.CardSurface),
                    border = BorderStroke(1.dp, LuxuryColors.ChampagneGold),
                    modifier = Modifier.weight(1f).height(42.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("📞 CALL", color = LuxuryColors.ChampagneGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { showShareDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.CardSurface),
                    border = BorderStroke(1.dp, LuxuryColors.ChampagneGold),
                    modifier = Modifier.weight(1f).height(42.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("📤 SHARE", color = LuxuryColors.ChampagneGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { showRoutePreviewDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.CardSurface),
                    border = BorderStroke(1.dp, LuxuryColors.ChampagneGold),
                    modifier = Modifier.weight(1f).height(42.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("🗺️ ROUTE", color = LuxuryColors.ChampagneGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { showTimelineSheet = true },
                    colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.CardSurface),
                    border = BorderStroke(1.dp, LuxuryColors.ChampagneGold),
                    modifier = Modifier.weight(1f).height(42.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("⏱️ STATUS", color = LuxuryColors.ChampagneGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Part 6 Safety Tools Ribbon (Features 136, 127, 138, 141, 147)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = { showRecordingConsentDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.CardSurface),
                    border = BorderStroke(1.dp, LuxuryColors.ChampagneGold.copy(alpha = 0.7f)),
                    modifier = Modifier.weight(1f).height(38.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("🎙️ REC", color = LuxuryColors.PlatinumWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { showContactsDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.CardSurface),
                    border = BorderStroke(1.dp, LuxuryColors.ChampagneGold.copy(alpha = 0.7f)),
                    modifier = Modifier.weight(1f).height(38.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("👥 CIRCL", color = LuxuryColors.PlatinumWhite, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { showIncidentDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.CardSurface),
                    border = BorderStroke(1.dp, LuxuryColors.CrimsonAlert.copy(alpha = 0.7f)),
                    modifier = Modifier.weight(1f).height(38.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("⚠️ REPORT", color = LuxuryColors.CrimsonAlert, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { showBlockDriverDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.CardSurface),
                    border = BorderStroke(1.dp, SosRedDark),
                    modifier = Modifier.weight(1f).height(38.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("⛔ BLOCK", color = SosRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { showSafeZonesDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.CardSurface),
                    border = BorderStroke(1.dp, LuxuryColors.ChampagneGold.copy(alpha = 0.7f)),
                    modifier = Modifier.weight(1f).height(38.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("📍 ZONES", color = LuxuryColors.PlatinumWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Feature 121: Accessibility Announcement Trigger Banner
            Surface(
                shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                color = LuxuryColors.DeepGraphite,
                border = BorderStroke(1.dp, LuxuryColors.CardBorder),
                modifier = Modifier.fillMaxWidth().clickable {
                    Toast.makeText(context, "🔊 $accessibilityText", Toast.LENGTH_LONG).show()
                }
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🔊", fontSize = 16.sp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("TALKBACK ACCESSIBILITY ANNOUNCEMENT", style = LuxuryTypography.TitleGold.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold))
                        Text(accessibilityText, style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp, color = LuxuryColors.PlatinumWhite))
                    }
                    Text("ANNOUNCE ❯", color = LuxuryColors.ChampagneGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Intermediate Stops Summary & Active Stop Editing Button
            if (tripStatus in listOf("ASSIGNED", "ARRIVED", "IN_PROGRESS")) {
                OutlinedButton(onClick = {
                    val details = rideDetails
                    newDestinationAddress = details?.destinationAddress.orEmpty()
                    newDestinationLat = details?.destinationLatitude?.toString().orEmpty()
                    newDestinationLng = details?.destinationLongitude?.toString().orEmpty()
                    proposedDestination = null
                    destinationChangeStatus = null
                    showDestinationChangeDialog = true
                }, modifier = Modifier.fillMaxWidth()) { Text("REQUEST DESTINATION CHANGE", color = LuxuryColors.ChampagneGold) }
            }
            if (destinationChangeStatus != null) Text(destinationChangeStatus!!, color = LuxuryColors.ChampagneGold, style = LuxuryTypography.BodySubtle)
            val currentStops = rideDetails?.stops ?: emptyList()
            LuxuryCard {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("JOURNEY WAYPOINTS (${currentStops.size})", style = LuxuryTypography.TitleGold.copy(fontSize = 12.sp))
                        Button(
                            onClick = { showStopsManagerDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.ChampagneGold),
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("✎ EDIT STOPS", fontSize = 10.sp, color = LuxuryColors.ObsidianBlack, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (currentStops.isEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Direct route. Tap 'Edit Stops' to add intermediate stops with dwell times.", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                        currentStops.forEach { stp ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("• Stop ${stp.sequenceOrder}: ${stp.address.take(28)}", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp), maxLines = 1)
                                Text("${stp.dwellTimeMinutes}m dwell", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp, color = LuxuryColors.ChampagneGold))
                            }
                        }
                    }
                }
            }

            // Post-Trip Actions: Receipt (F123), Lost Item Report (F124), Post-Trip Contact (F125)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showLostItemDialog = true },
                    modifier = Modifier.weight(1f).height(42.dp),
                    border = BorderStroke(1.dp, LuxuryColors.ChampagneGold)
                ) {
                    Text("🧳 LOST ITEM / DESK", color = LuxuryColors.ChampagneGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                if (tripStatus == "COMPLETED") {
                    OutlinedButton(
                        onClick = { showReceiptDialog = true },
                        modifier = Modifier.weight(1f).height(42.dp),
                        border = BorderStroke(1.dp, LuxuryColors.LuxuryEmerald)
                    ) {
                        Text("🧾 TAX RECEIPT", color = LuxuryColors.LuxuryEmerald, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = { showCancelDialog = true },
                        modifier = Modifier.weight(1f).height(42.dp),
                        border = BorderStroke(1.dp, LuxuryColors.CrimsonAlert)
                    ) {
                        Text("CANCEL RIDE", color = LuxuryColors.CrimsonAlert, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Bottom Settlement Action
            if (tripStatus == "COMPLETED") {
                LuxuryButton(
                    text = "PROCEED TO SETTLEMENT (₹$finalFareRupees)",
                    onClick = { onProceedPayment(rideId, finalFareRupees) },
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }

    // --- Part 5 Dialog Invocations ---
    if (showDestinationChangeDialog) {
        val quote = proposedDestination
        AlertDialog(
            onDismissRequest = { showDestinationChangeDialog = false },
            title = { Text(if (quote == null) "Request a new destination" else "Review revised fare") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (quote == null) {
                    Text("The driver must approve the route change. A fare estimate is shown before you confirm.")
                    OutlinedTextField(newDestinationAddress, { newDestinationAddress = it.take(255) }, label = { Text("Destination address") }, singleLine = true)
                    OutlinedTextField(newDestinationLat, { newDestinationLat = it }, label = { Text("Latitude") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(newDestinationLng, { newDestinationLng = it }, label = { Text("Longitude") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                } else {
                    val fare = (quote["proposed_fare_cents"] as? Number)?.toInt() ?: 0
                    val delta = (quote["fare_difference_cents"] as? Number)?.toInt() ?: 0
                    Text("${quote["destination_address"]}\nRevised estimate: ₹${fare / 100}\nFare change: ₹${delta / 100}\nDistance: ${quote["proposed_distance_km"]} km. The estimate depends on the configured routing provider.")
                }
            } },
            confirmButton = { TextButton(onClick = {
                scope.launch {
                    try {
                        if (quote == null) {
                            val lat = newDestinationLat.toDoubleOrNull(); val lng = newDestinationLng.toDoubleOrNull()
                            if (newDestinationAddress.trim().length < 3 || lat == null || lng == null || lat !in -90.0..90.0 || lng !in -180.0..180.0) { destinationChangeStatus = "Enter a valid address and coordinates."; return@launch }
                            val r = ApiClient.getService().requestDestinationChange(rideId, mapOf("address" to newDestinationAddress.trim(), "latitude" to lat, "longitude" to lng))
                            if (r.isSuccessful) proposedDestination = r.body() else destinationChangeStatus = "Could not calculate the fare estimate (${r.code()})."
                        } else {
                            val requestId = quote["request_id"]?.toString() ?: return@launch
                            val r = ApiClient.getService().respondDestinationQuote(requestId, mapOf("accept" to true))
                            if (r.isSuccessful) { showDestinationChangeDialog = false; destinationChangeStatus = "Fare quote accepted. Waiting for driver approval before the new route takes effect." }
                            else destinationChangeStatus = "Your approval was not saved (${r.code()})."
                        }
                    } catch (_: Exception) { destinationChangeStatus = "Could not reach the trip service. Please retry." }
                }
            }) { Text(if (quote == null) "Get fare estimate" else "Accept quote") } },
            dismissButton = { TextButton(onClick = { showDestinationChangeDialog = false }) { Text("Cancel") } }
        )
    }
    if (showShareDialog) {
        TripShareDialog(rideId = rideId, onDismiss = { showShareDialog = false })
    }
    if (showChatDialog) {
        InAppChauffeurChatDialog(rideId = rideId, chauffeurName = driverName, onDismiss = { showChatDialog = false })
    }
    if (showMaskedCallDialog) {
        MaskedCallDialog(
            virtualNumber = maskedNumber,
            virtualExt = maskedExt,
            chauffeurName = driverName,
            onDismiss = { showMaskedCallDialog = false }
        )
    }
    if (showArrivalPhotoDialog) {
        DriverArrivalPhotoDialog(
            photoUrl = arrivalPhotoUrl,
            vehicleModel = vehicleModel,
            licensePlate = vehiclePlate,
            onDismiss = { showArrivalPhotoDialog = false }
        )
    }
    if (showRoutePreviewDialog) {
        RoutePreviewAndPreferenceDialog(
            rideId = rideId,
            currentPreference = routePreference,
            hasRouteDeviation = routeDeviationDetected,
            deviationReason = routeDeviationReason,
            onPreferenceChanged = { routePreference = it },
            onDismiss = { showRoutePreviewDialog = false }
        )
    }
    if (showTimelineSheet) {
        TripStatusTimelineSheet(
            timelineSteps = telemetryData?.timeline ?: emptyList(),
            onDismiss = { showTimelineSheet = false }
        )
    }
    if (showReceiptDialog) {
        TripCompletionReceiptDialog(rideId = rideId, onDismiss = { showReceiptDialog = false })
    }
    if (showLostItemDialog) {
        LostItemReportDialog(rideId = rideId, onDismiss = { showLostItemDialog = false })
    }
    if (showStopsManagerDialog) {
        ActiveStopsManagerDialog(
            rideId = rideId,
            currentStops = rideDetails?.stops ?: emptyList(),
            onDismiss = { showStopsManagerDialog = false },
            onStopsUpdated = {}
        )
    }

    // --- Part 6 Safety & Emergency Dialog Invocations ---
    if (showSosDialog) {
        EmergencySosDialog(
            rideId = rideId,
            currentLat = driverLatLng.latitude,
            currentLng = driverLatLng.longitude,
            onDismiss = { showSosDialog = false }
        )
    }
    if (showContactsDialog) {
        EmergencyContactSetupDialog(onDismiss = { showContactsDialog = false })
    }
    if (showSafetyCheckinDialog) {
        SafetyCheckInDialog(
            rideId = rideId,
            onDismiss = { showSafetyCheckinDialog = false },
            onNeedHelp = { showSosDialog = true }
        )
    }
    if (showCredentialsDialog) {
        ChauffeurIdentityCredentialsDialog(rideId = rideId, onDismiss = { showCredentialsDialog = false })
    }
    if (showSafetyCenterDialog) {
        SafetyCenterHubDialog(onDismiss = { showSafetyCenterDialog = false })
    }
    if (showRecordingConsentDialog) {
        RideRecordingConsentDialog(rideId = rideId, onDismiss = { showRecordingConsentDialog = false })
    }
    if (showIncidentDialog) {
        IncidentReportDialog(rideId = rideId, onDismiss = { showIncidentDialog = false })
    }
    if (showBlockDriverDialog) {
        DriverBlockConfirmationDialog(
            driverId = "drv-assigned",
            driverName = driverName,
            rideId = rideId,
            onDismiss = { showBlockDriverDialog = false },
            onBlocked = { onRideCancelled() }
        )
    }
    if (showSafeZonesDialog) {
        SafePickupZonesDialog(
            latitude = pickupLatLng.latitude,
            longitude = pickupLatLng.longitude,
            onSelectZone = { /* Safe zone selected */ },
            onDismiss = { showSafeZonesDialog = false }
        )
    }
}
