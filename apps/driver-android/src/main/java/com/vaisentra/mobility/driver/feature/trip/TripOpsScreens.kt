package com.vaisentra.mobility.driver.feature.trip

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.driver.components.*
import com.vaisentra.mobility.driver.data.DriverRepository
import com.vaisentra.mobility.driver.theme.*
import java.util.Locale
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.launch

/**
 * Screen 15: Incoming Ride Offer Card (State-driven from DriverRepository.activeOffer)
 */
@Composable
fun IncomingOfferScreen(
    onNavigateBack: () -> Unit = {},
    onAcceptOffer: () -> Unit = {},
    onRejectOffer: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val activeOffer by DriverRepository.activeOffer.collectAsState()

    LaunchedEffect(Unit) {
        if (activeOffer == null) {
            DriverRepository.fetchActiveOffers()
        }
    }

    val offer = activeOffer
    var secondsLeft by remember(offer?.rideId) { mutableIntStateOf(offer?.timeoutSeconds ?: 25) }

    LaunchedEffect(offer?.rideId) {
        if (offer != null) {
            while (secondsLeft > 0) {
                kotlinx.coroutines.delay(1.seconds)
                secondsLeft--
            }
            DriverRepository.rejectOffer(offer.rideId, "DISPATCH_TIMEOUT")
            onRejectOffer()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Incoming Ride Offer",
            subtitle = "Smart dispatch match for your corridor",
            onBack = onNavigateBack
        )

        if (offer == null) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                CockpitCard {
                    Text("No active ride offers right now.", color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Stay online on the Duty Dashboard to receive live WebSocket offers.", color = CockpitTextMuted, fontSize = 12.sp)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                CockpitCard(glow = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CockpitBadge(offer.vehicleCategory.ifBlank { "E-RICKSHAW" }, ElectricCyan)
                        Text("Auto-Reject in ${secondsLeft}s", color = CrimsonAlert, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("ESTIMATED NET PAY", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("₹${"%.2f".format(offer.estimatedDriverEarningsRupees)}", color = EmeraldPrimary, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("DISTANCE", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("${"%.1f".format(offer.distanceKm)} km", color = CockpitTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = CockpitBorder)
                    Spacer(Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.Top) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 4.dp)) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(EmeraldPrimary))
                            Box(Modifier.width(2.dp).height(38.dp).background(CockpitBorder))
                            Box(Modifier.size(10.dp).clip(CircleShape).background(ElectricCyan))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Column {
                                Text("PICKUP (${offer.pickupDistanceMeters}m away)", color = CockpitTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(offer.pickupAddress, color = CockpitTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Column {
                                Text("DROP", color = CockpitTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(offer.destinationAddress, color = CockpitTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CockpitButton(
                        text = "ACCEPT RIDE OFFER (₹${"%.2f".format(offer.estimatedDriverEarningsRupees)})",
                        onClick = {
                            DriverRepository.acceptOffer(offer.rideId) { ok ->
                                if (ok) {
                                    onShowToast("Offer Accepted! Route initialized.")
                                    onAcceptOffer()
                                } else {
                                    onShowToast("Could not accept offer. It may have expired.")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    CockpitButton(
                        text = "PASS / REJECT",
                        onClick = {
                            DriverRepository.rejectOffer(offer.rideId, "PILOT_DECLINED") {
                                onShowToast("Offer passed")
                                onRejectOffer()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        isOutlined = true
                    )
                }
            }
        }
    }
}

/**
 * Screen 16: Auto-Dispatch Offer Queue (POST /api/v1/drivers/auto-accept)
 */
@Composable
fun OfferQueueScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    var autoAcceptPool by remember { mutableStateOf(true) }
    var isUpdating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Auto-Dispatch Queue",
            subtitle = "Zero-idle continuous corridor trip chaining",
            onBack = onNavigateBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                CockpitCard(glow = autoAcceptPool) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Continuous Chained Dispatch", color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Match next ride before current trip drops off", color = CockpitTextSecondary, fontSize = 12.sp)
                        }
                        Switch(
                            checked = autoAcceptPool,
                            enabled = !isUpdating,
                            onCheckedChange = { next ->
                                isUpdating = true
                                scope.launch {
                                    val res = DriverRepository.updateAutoAccept(next)
                                    isUpdating = false
                                    if (res.isSuccess) {
                                        autoAcceptPool = next
                                        onShowToast(if (next) "Chained dispatch active" else "Chained dispatch paused")
                                    } else {
                                        onShowToast("Failed to update auto-accept: ${res.exceptionOrNull()?.message}")
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = EmeraldPrimary,
                                checkedTrackColor = EmeraldPrimary.copy(alpha = 0.3f)
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Screen 17: Navigation to Pickup Spot (GET /api/v1/rides/active + POST /api/v1/rides/{ride_id}/arrived)
 */
@Composable
fun PickupNavScreen(
    onNavigateBack: () -> Unit = {},
    onArrived: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val rideId by DriverRepository.currentRideId.collectAsState()
    val activeRide by DriverRepository.activeRide.collectAsState()
    val liveLocation by DriverRepository.driverLocation.collectAsState()
    var isUpdating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        DriverRepository.fetchActiveRide()
    }

    val hasDriverFix = com.vaisentra.mobility.driver.data.NativeLocationEvaluator.isValidCoordinate(liveLocation.latitude, liveLocation.longitude)
    val driverLat = if (hasDriverFix) liveLocation.latitude else 0.0
    val driverLng = if (hasDriverFix) liveLocation.longitude else 0.0
    val targetLat = activeRide?.pickupLatitude
    val targetLng = activeRide?.pickupLongitude
    val currentSpeed = liveLocation.speedKmh.toInt().coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "En-Route to Pickup",
            subtitle = "Ride: ${rideId.ifBlank { "Active Pickup" }}",
            onBack = onNavigateBack
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            // 1. Live Mapbox Vector Map
            DriverMapboxLiveView(
                modifier = Modifier.fillMaxSize(),
                driverLat = driverLat,
                driverLng = driverLng,
                targetLat = targetLat,
                targetLng = targetLng,
                targetLabel = activeRide?.pickupAddress?.ifBlank { "Passenger Pickup" } ?: "Passenger Pickup",
                isNavigating = true,
                headingDegrees = liveLocation.heading
            )

            // 2. Floating Turn-by-Turn Top Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                color = CockpitCardBackground.copy(alpha = 0.95f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CockpitBorder)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(ElectricCyan.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = "Navigate",
                            tint = ElectricCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Head to Passenger Pickup", color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            activeRide?.pickupAddress?.ifBlank { "Approaching pickup location" } ?: "Approaching pickup location",
                            color = CockpitTextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "$currentSpeed",
                            color = EmeraldPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                        Text("km/h", color = CockpitTextMuted, fontSize = 9.sp)
                    }
                }
            }

            // 3. Floating Bottom Arrive Action Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.BottomCenter),
                color = CockpitCardBackground.copy(alpha = 0.95f),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CockpitBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("PICKUP POINT", color = CockpitTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(
                                activeRide?.pickupAddress?.ifBlank { "Pickup Point Not Available" } ?: "Pickup Point Not Available",
                                color = CockpitTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }
                        Surface(
                            color = AmberWarning.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "EN-ROUTE",
                                color = AmberWarning,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    CockpitButton(
                        text = if (isUpdating) "UPDATING..." else "ARRIVED AT PICKUP POINT",
                        enabled = !isUpdating,
                        onClick = {
                            if (rideId.isBlank()) {
                                onShowToast("No active ride found.")
                                return@CockpitButton
                            }
                            isUpdating = true
                            DriverRepository.markArrived(rideId, targetLat, targetLng) { ok ->
                                isUpdating = false
                                if (ok) {
                                    onShowToast("Status updated to ARRIVED")
                                    onArrived()
                                } else {
                                    onShowToast("Failed to mark arrived.")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Screen 18: Arrived at Pickup Point
 */
@Composable
fun ArrivedPickupScreen(
    onNavigateBack: () -> Unit = {},
    onStartWaitingTimer: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val rideId by DriverRepository.currentRideId.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Arrived at Pickup",
            subtitle = "GPS proximity confirmed",
            onBack = onNavigateBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            CockpitCard(glow = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Proximity Confirmed", color = EmeraldPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Waiting grace: 5 minutes complimentary before paid meter begins.", color = CockpitTextSecondary, fontSize = 12.sp)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CockpitButton(
                    text = "START WAITING TIMER & BILLABLE METER",
                    onClick = {
                        if (rideId.isNotBlank()) {
                            DriverRepository.startTripWaiting(rideId) { ok ->
                                if (ok) {
                                    onShowToast("Waiting timer started")
                                    onStartWaitingTimer()
                                } else {
                                    onShowToast("Could not start waiting timer.")
                                }
                            }
                        } else {
                            onStartWaitingTimer()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Screen 19: Waiting Timer & Billable Meter
 */
@Composable
fun WaitingTimerScreen(
    onNavigateBack: () -> Unit = {},
    onProceedToOtp: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val rideId by DriverRepository.currentRideId.collectAsState()
    var elapsedSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1.seconds)
            elapsedSeconds++
        }
    }

    val graceSeconds = 300
    val billableSeconds = maxOf(0, elapsedSeconds - graceSeconds)
    val billableMinutes = (billableSeconds + 59) / 60
    val waitingFeeRupees = billableMinutes * 1.50

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Waiting Meter",
            subtitle = "Active waiting timer & passenger grace tracker",
            onBack = onNavigateBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CockpitCard(glow = billableSeconds > 0) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("TOTAL ELAPSED WAITING", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        val mins = elapsedSeconds / 60
                        val secs = elapsedSeconds % 60
                        Text(
                            String.format(Locale.US, "%02d:%02d", mins, secs),
                            color = if (billableSeconds > 0) AmberWarning else EmeraldPrimary,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(Modifier.height(6.dp))
                        CockpitBadge(
                            if (billableSeconds > 0) "BILLABLE WAITING ACTIVE" else "COMPLIMENTARY GRACE PERIOD",
                            if (billableSeconds > 0) AmberWarning else EmeraldPrimary
                        )
                    }
                }

                CockpitCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("ACCUMULATED WAITING FEE", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("₹${String.format(Locale.US, "%.2f", waitingFeeRupees)}", color = EmeraldPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("BILLABLE MINUTES", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("$billableMinutes min (₹1.50/min)", color = CockpitTextPrimary, fontSize = 14.sp)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CockpitButton(
                    text = "PASSENGER BOARDED (ENTER OTP)",
                    onClick = {
                        if (rideId.isNotBlank()) {
                            DriverRepository.stopTripWaiting(rideId) {
                                onProceedToOtp()
                            }
                        } else {
                            onProceedToOtp()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                CockpitButton(
                    text = "Cancel Trip (Rider No-Show)",
                    onClick = {
                        if (rideId.isNotBlank()) {
                            DriverRepository.claimNoShow(rideId) { ok ->
                                if (ok) {
                                    onShowToast("No-show claimed!")
                                    onNavigateBack()
                                } else {
                                    onShowToast("Cannot claim no-show yet.")
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    isOutlined = true
                )
            }
        }
    }
}

/**
 * Screen 20: Boarding & Commuter OTP Verify
 */
@Composable
fun BoardingOtpScreen(
    onNavigateBack: () -> Unit = {},
    onTripStarted: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val rideId by DriverRepository.currentRideId.collectAsState()
    var otpDigits by remember { mutableStateOf(listOf("", "", "", "", "", "")) }
    var isVerifying by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Start Trip OTP",
            subtitle = "Verify 6-digit ride unlock code from commuter",
            onBack = onNavigateBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            CockpitCard(glow = true) {
                Text(
                    "ASK COMMUTER FOR 6-DIGIT START OTP",
                    color = CockpitTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    otpDigits.forEach { digit ->
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CockpitSurfaceHighlight),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = digit.ifEmpty { "•" },
                                color = if (digit.isNotEmpty()) ElectricCyan else CockpitTextMuted,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val rows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("C", "0", "⌫")
                    )
                    rows.forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { key ->
                                Button(
                                    onClick = {
                                        when (key) {
                                            "C" -> otpDigits = listOf("", "", "", "", "", "")
                                            "⌫" -> {
                                                val lastIdx = otpDigits.indexOfLast { it.isNotEmpty() }
                                                if (lastIdx >= 0) {
                                                    otpDigits = otpDigits.toMutableList().also { it[lastIdx] = "" }
                                                }
                                            }
                                            else -> {
                                                val firstEmpty = otpDigits.indexOfFirst { it.isEmpty() }
                                                if (firstEmpty >= 0) {
                                                    otpDigits = otpDigits.toMutableList().also { it[firstEmpty] = key }
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = CockpitSurfaceHighlight),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(key, color = CockpitTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            CockpitButton(
                text = if (isVerifying) "VERIFYING OTP..." else "START RIDE",
                onClick = {
                    val code = otpDigits.joinToString("")
                    if (code.length == 6 && rideId.isNotBlank()) {
                        isVerifying = true
                        DriverRepository.verifyStartOtp(
                            rideId = rideId,
                            otp = code,
                            onSuccess = {
                                isVerifying = false
                                onShowToast("OTP $code verified! Meter is LIVE")
                                onTripStarted()
                            },
                            onFailure = {
                                isVerifying = false
                                onShowToast("Invalid Start OTP. Please ask commuter.")
                            }
                        )
                    } else {
                        onShowToast("Enter 6-digit OTP on an active ride")
                    }
                },
                enabled = otpDigits.all { it.isNotEmpty() } && !isVerifying,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}



/**
 * Screen 21: In-Trip Turn Navigation with Live Voice Guidance
 */
@Composable
fun InTripNavScreen(
    onNavigateBack: () -> Unit = {},
    onEndTrip: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val rideId by DriverRepository.currentRideId.collectAsState()
    val activeRide by DriverRepository.activeRide.collectAsState()
    val liveLocation by DriverRepository.driverLocation.collectAsState()
    val isAudioRecordingActive by DriverRepository.isAudioRecordingActive.collectAsState()
    var isFinishing by remember { mutableStateOf(false) }

    // Live Voice Navigation Engine States
    val routePoints by DriverVoiceNavigationEngine.routePoints.collectAsState()
    val currentInstruction by DriverVoiceNavigationEngine.currentInstruction.collectAsState()
    val currentManeuver by DriverVoiceNavigationEngine.currentManeuver.collectAsState()
    val distanceToManeuver by DriverVoiceNavigationEngine.distanceToNextManeuverMeters.collectAsState()
    val isVoiceMuted by DriverVoiceNavigationEngine.isVoiceMuted.collectAsState()

    val hasDriverFix = com.vaisentra.mobility.driver.data.NativeLocationEvaluator.isValidCoordinate(liveLocation.latitude, liveLocation.longitude)
    val driverLat = if (hasDriverFix) liveLocation.latitude else 0.0
    val driverLng = if (hasDriverFix) liveLocation.longitude else 0.0
    val targetLat = activeRide?.destinationLatitude
    val targetLng = activeRide?.destinationLongitude
    val hasDestFix = targetLat != null && targetLng != null &&
        com.vaisentra.mobility.driver.data.NativeLocationEvaluator.isValidCoordinate(targetLat, targetLng)
    val currentSpeed = liveLocation.speedKmh.toInt().coerceAtLeast(0)
    val speedLimit = 35
    var navStarted by remember(activeRide?.id, targetLat, targetLng) { mutableStateOf(false) }

    LaunchedEffect(activeRide?.id) {
        DriverRepository.fetchActiveRide()
    }

    // Start Live Turn-by-Turn Voice Engine once real GPS & destination coordinates exist
    LaunchedEffect(activeRide?.id, hasDriverFix, driverLat, driverLng, hasDestFix, targetLat, targetLng) {
        if (!navStarted && hasDriverFix && hasDestFix) {
            navStarted = true
            DriverVoiceNavigationEngine.startNavigation(
                context = context,
                originLat = driverLat,
                originLng = driverLng,
                destLat = targetLat!!,
                destLng = targetLng!!,
                destinationTitle = activeRide?.destinationAddress ?: "Passenger Destination"
            )
        }
    }

    // Pass every valid live GPS ping to Voice Engine
    LaunchedEffect(hasDriverFix, driverLat, driverLng) {
        if (hasDriverFix) {
            DriverVoiceNavigationEngine.onLocationUpdate(driverLat, driverLng)
        }
    }

    // Stop Voice Engine when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            DriverVoiceNavigationEngine.stopNavigation()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Trip In Progress (Live Voice Nav)",
            subtitle = "Ride: ${rideId.ifBlank { "Active Trip" }}",
            onBack = onNavigateBack
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            // 1. Live Mapbox Map with Real Curved Street Polyline
            DriverMapboxLiveView(
                modifier = Modifier.fillMaxSize(),
                driverLat = driverLat,
                driverLng = driverLng,
                targetLat = targetLat,
                targetLng = targetLng,
                targetLabel = activeRide?.destinationAddress?.ifBlank { "Destination" } ?: "Destination",
                isNavigating = true,
                headingDegrees = liveLocation.heading,
                routePoints = routePoints
            )

            // 2. Real-Time Dynamic Turn-by-Turn HUD Banner
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
                    .align(Alignment.TopCenter),
                color = CockpitCardBackground.copy(alpha = 0.96f),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Dynamic Turn Maneuver Icon
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(ElectricCyan.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        val maneuverIcon = when {
                            currentManeuver.contains("LEFT") -> Icons.Default.TurnLeft
                            currentManeuver.contains("RIGHT") -> Icons.Default.TurnRight
                            currentManeuver.contains("UTURN") -> Icons.Default.TurnLeft
                            currentManeuver.contains("ARRIVE") -> Icons.Default.Flag
                            else -> Icons.Default.Navigation
                        }
                        Icon(
                            imageVector = maneuverIcon,
                            contentDescription = "Turn Maneuver",
                            tint = ElectricCyan,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (distanceToManeuver > 0) "In ${distanceToManeuver}m" else "Turn Now",
                            color = EmeraldPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = currentInstruction,
                            color = CockpitTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 2
                        )
                    }

                    // Voice Mute / Unmute Button
                    IconButton(
                        onClick = { DriverVoiceNavigationEngine.toggleVoiceMute() },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(CockpitSurfaceHighlight)
                    ) {
                        Icon(
                            imageVector = if (isVoiceMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = "Mute Voice",
                            tint = if (isVoiceMuted) CrimsonAlert else EmeraldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // 3. Floating Bottom Trip Action Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.BottomCenter),
                color = CockpitCardBackground.copy(alpha = 0.95f),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CockpitBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("DROP-OFF DESTINATION", color = CockpitTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(
                                activeRide?.destinationAddress?.ifBlank { "Passenger Destination" } ?: "Passenger Destination",
                                color = CockpitTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }
                        Surface(
                            color = EmeraldPrimary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "METER ACTIVE",
                                color = EmeraldPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    if (isAudioRecordingActive) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = CrimsonAlert.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CrimsonAlert.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "🔴", fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "IN-CABIN AUDIO SAFETY ACTIVE (DPDP Encrypted)",
                                    color = CrimsonAlert,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                DriverDialogController.show(DriverPopupType.AUDIO_RECORDING_CONSENT)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (isAudioRecordingActive) "🎙️ Audio: Active" else "🎙️ Safety Audio",
                                color = if (isAudioRecordingActive) EmeraldPrimary else CockpitTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                DriverDialogController.show(DriverPopupType.TOLL_ENTRY)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(text = "🛂 Add Toll", color = CockpitTextSecondary, fontSize = 12.sp)
                        }
                    }

                    CockpitButton(
                        text = if (isFinishing) "COMPLETING TRIP..." else "ARRIVED & COLLECT FARE",
                        enabled = !isFinishing,
                        onClick = {
                            if (rideId.isBlank()) {
                                onShowToast("No active ride found.")
                                return@CockpitButton
                            }
                            isFinishing = true
                            DriverVoiceNavigationEngine.stopNavigation()
                            DriverRepository.finishTrip(rideId, targetLat, targetLng) { ok ->
                                isFinishing = false
                                if (ok) {
                                    onShowToast("Trip finished! Generating fare settlement.")
                                    onEndTrip()
                                } else {
                                    onShowToast("Failed to complete trip.")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Screen 22: Multi-Stop Waypoints (GET /api/v1/rides/{ride_id}/stops)
 */
@Composable
fun MultiStopScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val rideId by DriverRepository.currentRideId.collectAsState()
    val stops by DriverRepository.rideStops.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(rideId) {
        if (rideId.isNotBlank()) {
            DriverRepository.fetchRideStops(rideId)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Multi-Stop Waypoints",
            subtitle = "Sequential corridor itinerary & drop queue",
            onBack = onNavigateBack
        )

        if (stops.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = if (rideId.isBlank()) "No active ride." else "No intermediate stops for this ride.",
                    color = CockpitTextMuted,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(stops) { stop ->
                    val isCompleted = stop.status in listOf("COMPLETED", "ARRIVED", "DEPARTED")
                    CockpitCard(glow = !isCompleted) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Stop ${stop.sequenceOrder}: ${stop.address}", color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Status: ${stop.status}", color = CockpitTextSecondary, fontSize = 12.sp)
                            }
                            if (isCompleted) {
                                CockpitBadge("COMPLETED", EmeraldPrimary)
                            } else {
                                CockpitButton(
                                    text = "Arrived",
                                    onClick = {
                                        scope.launch {
                                            val res = DriverRepository.markRideStopArrived(rideId, stop.id)
                                            if (res.isSuccess) onShowToast("Arrived at ${stop.address}")
                                            else onShowToast("Failed to update stop")
                                        }
                                    }
                                )
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
fun IncomingOfferScreenPreview() {
    DriverGoTheme {
        IncomingOfferScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun OfferQueueScreenPreview() {
    DriverGoTheme {
        OfferQueueScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun PickupNavScreenPreview() {
    DriverGoTheme {
        PickupNavScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun ArrivedPickupScreenPreview() {
    DriverGoTheme {
        ArrivedPickupScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun WaitingTimerScreenPreview() {
    DriverGoTheme {
        WaitingTimerScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun BoardingOtpScreenPreview() {
    DriverGoTheme {
        BoardingOtpScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun InTripNavScreenPreview() {
    DriverGoTheme {
        InTripNavScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun MultiStopScreenPreview() {
    DriverGoTheme {
        MultiStopScreen()
    }
}