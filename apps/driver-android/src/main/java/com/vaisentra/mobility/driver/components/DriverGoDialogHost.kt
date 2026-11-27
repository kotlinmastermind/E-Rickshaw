package com.vaisentra.mobility.driver.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vaisentra.mobility.driver.data.DriverRepository
import com.vaisentra.mobility.driver.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class DriverPopupType {
    NONE,
    INCOMING_OFFER,
    RIDER_CANCELLED,
    CASH_COLLECT,
    NO_SHOW_EXPIRY,
    ROUTE_DEVIATION,
    DOC_EXPIRY,
    PILOT_SOS,
    ODOMETER_CHECK,
    PEAK_BONUS,
    CSAT_ADVISORY,
    PAYOUT_SUCCESS,
    TOLL_ENTRY,
    AUDIO_RECORDING_CONSENT
}

object DriverDialogController {
    var activePopup by mutableStateOf(DriverPopupType.NONE)
    var toastMessage by mutableStateOf<String?>(null)
    var onConfirmAction: (() -> Unit)? = null

    fun show(popup: DriverPopupType, onConfirm: (() -> Unit)? = null) {
        activePopup = popup
        onConfirmAction = onConfirm
    }

    fun dismiss() {
        activePopup = DriverPopupType.NONE
        onConfirmAction = null
    }

    fun showToast(msg: String) {
        toastMessage = msg
    }
}

@Composable
fun DriverGoDialogHost() {
    val active = DriverDialogController.activePopup

    // Auto-dismissing Toast Banner
    DriverDialogController.toastMessage?.let { msg ->
        LaunchedEffect(msg) {
            delay(3000)
            DriverDialogController.toastMessage = null
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp, start = 20.dp, end = 20.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(PilotCard)
                    .border(1.dp, PilotBorderFocus, RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "⚡", fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                    Text(text = msg, color = PilotTextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (active != DriverPopupType.NONE) {
        Dialog(onDismissRequest = { DriverDialogController.dismiss() }) {
            when (active) {
                DriverPopupType.INCOMING_OFFER -> IncomingOfferModal()
                DriverPopupType.RIDER_CANCELLED -> RiderCancelledModal()
                DriverPopupType.CASH_COLLECT -> CashCollectModal()
                DriverPopupType.NO_SHOW_EXPIRY -> NoShowExpiryModal()
                DriverPopupType.ROUTE_DEVIATION -> RouteDeviationModal()
                DriverPopupType.DOC_EXPIRY -> DocExpiryModal()
                DriverPopupType.PILOT_SOS -> PilotSosModal()
                DriverPopupType.ODOMETER_CHECK -> OdometerCheckModal()
                DriverPopupType.PEAK_BONUS -> PeakBonusModal()
                DriverPopupType.CSAT_ADVISORY -> CsatAdvisoryModal()
                DriverPopupType.PAYOUT_SUCCESS -> PayoutSuccessModal()
                DriverPopupType.TOLL_ENTRY -> TollEntryModal()
                DriverPopupType.AUDIO_RECORDING_CONSENT -> DriverAudioRecordingConsentModal()
                DriverPopupType.NONE -> Unit
            }
        }
    }
}

// 1. Incoming Ride Offer (State-driven from DriverRepository.activeOffer)
@Composable
private fun IncomingOfferModal() {
    val offer by DriverRepository.activeOffer.collectAsState()
    val currentOffer = offer ?: run {
        LaunchedEffect(Unit) { DriverDialogController.dismiss() }
        return
    }

    var timer by remember(currentOffer.rideId) { mutableStateOf(currentOffer.timeoutSeconds.coerceAtLeast(1)) }
    LaunchedEffect(currentOffer.rideId, timer) {
        if (timer > 0) {
            delay(1000)
            timer--
        } else {
            DriverRepository.rejectOffer(currentOffer.rideId, "DISPATCH_TIMEOUT")
            DriverDialogController.dismiss()
        }
    }

    CockpitCard(borderColor = PilotOnlineGreen, backgroundColor = Color(0xFF0F181C)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "🚖 NEW RIDE OFFER", color = PilotOnlineGreen, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            CockpitBadge(text = "${timer}s LEFT", backgroundColor = AlertGlow, textColor = PilotCrimson)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "GROSS FARE ESTIMATE: ₹${"%.2f".format(currentOffer.estimatedDriverEarningsRupees)}",
            color = PilotElectricCyan,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Pickup: ${currentOffer.pickupAddress} (${currentOffer.pickupDistanceMeters}m away)",
            color = PilotTextWhite,
            fontSize = 13.sp
        )
        Text(
            text = "Drop: ${currentOffer.destinationAddress} (${"%.1f".format(currentOffer.distanceKm)} km)",
            color = PilotTextMuted,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(20.dp))
        CockpitButton(
            text = "Accept Ride Offer (${timer}s)",
            onClick = {
                val cb = DriverDialogController.onConfirmAction
                DriverRepository.acceptOffer(currentOffer.rideId) { ok ->
                    DriverDialogController.dismiss()
                    if (ok) {
                        DriverDialogController.showToast("Ride Accepted! Routing to pickup spot.")
                        cb?.invoke()
                    } else {
                        DriverDialogController.showToast("Could not accept ride. Offer may have expired.")
                    }
                }
            },
            backgroundColor = PilotOnlineGreen
        )
        Spacer(modifier = Modifier.height(8.dp))
        CockpitButton(
            text = "Decline Offer",
            onClick = {
                DriverRepository.rejectOffer(currentOffer.rideId, "PILOT_DECLINED")
                DriverDialogController.dismiss()
                DriverDialogController.showToast("Offer declined. Returning to radar.")
            },
            backgroundColor = PilotSurface
        )
    }
}

// 2. Rider Cancelled Trip
@Composable
private fun RiderCancelledModal() {
    CockpitCard(borderColor = PilotCrimson) {
        Text(text = "🛑 Passenger Cancelled Trip", color = PilotCrimson, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "The passenger cancelled the dispatch. Applicable cancellation compensation is synced to your ledger.", color = PilotTextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(20.dp))
        CockpitButton(text = "Acknowledge & Return to Duty", onClick = { DriverDialogController.dismiss() })
    }
}


// 6. Cash Fare Collection Calculator
@Composable
private fun CashCollectModal() {
    val scope = rememberCoroutineScope()
    val offer by DriverRepository.activeOffer.collectAsState()
    val breakdown by DriverRepository.rideBreakdown.collectAsState()
    val currentRide by DriverRepository.currentRideId.collectAsState()

    val validFare: Double? = breakdown?.fareBreakdown?.grossFareRupees?.takeIf { it > 0.0 }
        ?: offer?.estimatedFareRupees?.takeIf { it > 0.0 }

    var tender by remember(validFare) { mutableStateOf(validFare?.let { "%.0f".format(it) } ?: "") }
    val tenderVal = tender.toDoubleOrNull() ?: 0.0
    val returnChange = if (validFare != null && tenderVal >= validFare) tenderVal - validFare else 0.0

    CockpitCard {
        Text(text = "💵 Cash Fare Collection", color = PilotTextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        if (validFare == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(PilotCrimson.copy(alpha = 0.15f))
                    .padding(12.dp)
            ) {
                Column {
                    Text(text = "⚠️ Fare Unavailable / Calculating", color = PilotCrimson, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Server has not finalized the authoritative fare for this ride. Cash collection is blocked until a valid fare snapshot is available.", color = PilotTextMuted, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    CockpitButton(
                        text = "Refresh Fare Breakdown",
                        onClick = {
                            val rideId = currentRide
                            if (!rideId.isNullOrBlank()) {
                                scope.launch {
                                    DriverRepository.fetchRideBreakdown(rideId)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            Text(text = "Net Fare Payable: ₹${"%.2f".format(validFare)}", color = PilotElectricCyan, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(12.dp))
            CockpitTextField(value = tender, onValueChange = { tender = it }, placeholder = "Cash received from passenger", label = "Cash Tendered (₹)")
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = "Return Change: ₹${"%.2f".format(returnChange)}", color = PilotAmber, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            CockpitButton(
                text = "Confirm ₹${"%.2f".format(validFare)} Cash Received",
                enabled = tenderVal >= validFare,
                onClick = {
                    val cb = DriverDialogController.onConfirmAction
                    val rideId = currentRide
                    if (rideId.isNullOrBlank()) {
                        DriverDialogController.showToast("No active ride found.")
                        return@CockpitButton
                    }
                    DriverRepository.collectCash(rideId) { ok ->
                        DriverDialogController.dismiss()
                        if (ok) {
                            DriverDialogController.showToast("Cash payment reconciled!")
                            cb?.invoke()
                        } else {
                            DriverDialogController.showToast("Failed to confirm cash collection.")
                        }
                    }
                }
            )
        }
    }
}

// 7. Commuter No-Show Expiry
@Composable
private fun NoShowExpiryModal() {
    CockpitCard(borderColor = PilotAmber) {
        Text(text = "⏱️ 5-Minute Waiting Grace Elapsed", color = PilotAmber, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Passenger did not arrive at pickup gate. You are authorized to cancel with no-show compensation.", color = PilotTextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(16.dp))
        CockpitButton(text = "Claim No-Show Compensation", onClick = {
            val currentRide = DriverRepository.currentRideId.value
            if (currentRide.isNullOrBlank()) {
                DriverDialogController.dismiss()
                DriverDialogController.showToast("No active ride found.")
                return@CockpitButton
            }
            DriverRepository.claimNoShow(currentRide) { ok ->
                DriverDialogController.dismiss()
                if (ok) {
                    DriverDialogController.showToast("No-show compensation logged in ledger.")
                } else {
                    DriverDialogController.showToast("Could not claim no-show. Check ride status.")
                }
            }
        }, backgroundColor = PilotAmber, textColor = PilotCanvas)
    }
}

// 8. Route Deviation Anomaly
@Composable
private fun RouteDeviationModal() {
    val scope = rememberCoroutineScope()
    var selectedReason by remember { mutableStateOf("MUNICIPAL_ROAD_WORK") }
    var isSubmitting by remember { mutableStateOf(false) }

    CockpitCard(borderColor = PilotAmber) {
        Text(text = "🧭 Route Detour Detected", color = PilotAmber, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Vehicle departed corridor trajectory. Confirm detour cause to inform rider and Security Operations Center.", color = PilotTextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(14.dp))
        CockpitTextField(
            value = selectedReason,
            onValueChange = { selectedReason = it },
            placeholder = "e.g. Municipal Road Works / Traffic Diversion",
            label = "Detour / Deviation Reason"
        )
        Spacer(modifier = Modifier.height(16.dp))
        CockpitButton(
            text = if (isSubmitting) "Logging Detour..." else "Confirm Detour & Notify Rider",
            enabled = !isSubmitting,
            onClick = {
                val currentRide = DriverRepository.currentRideId.value
                if (currentRide.isNullOrBlank()) {
                    DriverDialogController.dismiss()
                    DriverDialogController.showToast("No active ride found.")
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
                        DriverDialogController.showToast("Acquiring live GPS fix; retry once GPS is ready.")
                        return@launch
                    }
                    val res = DriverRepository.reportRouteDeviation(currentRide, validCoords.first, validCoords.second, reason = selectedReason)
                    isSubmitting = false
                    DriverDialogController.dismiss()
                    if (res.isSuccess) {
                        DriverDialogController.showToast("Road hazard & detour recorded in fleet telemetry.")
                    } else {
                        DriverDialogController.showToast("Failed to log route detour: ${res.exceptionOrNull()?.message}")
                    }
                }
            }
        )
    }
}

// 9. Document Expiry Urgent
@Composable
private fun DocExpiryModal() {
    val scope = rememberCoroutineScope()
    val cb = DriverDialogController.onConfirmAction
    CockpitCard(borderColor = PilotAmber) {
        Text(text = "📋 Commercial DL Renewal Alert", color = PilotAmber, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Your Commercial Driving License is nearing expiry. Upload renewal certificate now to avoid duty pause.", color = PilotTextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(16.dp))
        CockpitButton(text = "Open Document Upload Vault", onClick = {
            scope.launch {
                DriverRepository.fetchDriverDocuments()
                DriverDialogController.dismiss()
                cb?.invoke()
            }
        })
    }
}

// 10. Pilot SOS Dispatched
@Composable
private fun PilotSosModal() {
    CockpitCard(borderColor = PilotCrimson, backgroundColor = Color(0xFF1E1014)) {
        Text(text = "🚨 PILOT PANIC SOS DISPATCHED", color = PilotCrimson, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Live vehicle GPS and telemetry streamed to 24/7 Security Operations Center.", color = PilotTextWhite, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(16.dp))
        CockpitButton(text = "Acknowledge SOC Response Team", onClick = {
            DriverDialogController.dismiss()
            DriverDialogController.showToast("SOC Alert Acknowledged.")
        }, backgroundColor = PilotCrimson)
    }
}

// 11. Daily Vehicle & Odometer Verification
@Composable
private fun OdometerCheckModal() {
    val cb = DriverDialogController.onConfirmAction
    CockpitCard(borderColor = PilotAmber) {
        Text(text = "📸 Daily Vehicle Inspection Required", color = PilotAmber, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "To ensure passenger safety and road compliance, capture vehicle photographs and verify current odometer reading before going on duty.",
            color = PilotTextMuted,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        CockpitButton(
            text = "Start Vehicle Inspection & Camera Capture →",
            onClick = {
                DriverDialogController.dismiss()
                cb?.invoke()
            }
        )
    }
}

// 12. Daily Peak Bonus
@Composable
private fun PeakBonusModal() {
    val scope = rememberCoroutineScope()
    val target by DriverRepository.dailyTarget.collectAsState()
    CockpitCard(borderColor = PilotOnlineGreen) {
        Text(text = "🎉 Daily Target Progress", color = PilotOnlineGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${target.completedRides}/${target.targetRides} rides completed. Bonus: ₹${"%.0f".format(target.rewardRupees)}.",
            color = PilotTextMuted,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        CockpitButton(text = "Refresh Target Status", onClick = {
            scope.launch {
                DriverRepository.fetchDailyTargets()
                DriverDialogController.dismiss()
                DriverDialogController.showToast("Daily target synced!")
            }
        })
    }
}

// 13. CSAT Review Notice
@Composable
private fun CsatAdvisoryModal() {
    val scope = rememberCoroutineScope()
    val scorecard by DriverRepository.scorecard.collectAsState()
    CockpitCard {
        Text(text = "⭐ CSAT Standards Advisory", color = PilotTextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Current Rating: ${"%.2f".format(scorecard.rating)} ★. Keep cabin clean and maintain smooth acceleration.",
            color = PilotTextMuted,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        CockpitButton(text = "Understood (Sync Scorecard)", onClick = {
            scope.launch {
                DriverRepository.fetchPerformanceScorecard()
                DriverDialogController.dismiss()
            }
        })
    }
}

// 14. Payout Success
@Composable
private fun PayoutSuccessModal() {
    val scope = rememberCoroutineScope()
    val cb = DriverDialogController.onConfirmAction
    CockpitCard(borderColor = PilotOnlineGreen) {
        Text(text = "⚡ Instant Payout Requested", color = PilotOnlineGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Your payout request has been submitted to your registered bank account.", color = PilotTextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(16.dp))
        CockpitButton(text = "View Account Ledger", onClick = {
            scope.launch {
                DriverRepository.fetchAccountLedger()
                DriverDialogController.dismiss()
                cb?.invoke()
            }
        })
    }
}

// 15. Toll & Surcharge Entry
@Composable
private fun TollEntryModal() {
    val scope = rememberCoroutineScope()
    var tollAmount by remember { mutableStateOf("100") }
    var tollName by remember { mutableStateOf("MCD Delhi Border Toll") }
    var isSubmitting by remember { mutableStateOf(false) }

    CockpitCard {
        Text(text = "🛂 Add Border Toll / Surcharge", color = PilotTextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Add verified municipal entry or expressway toll tax directly to commuter's final invoice with driver reimbursement credit.", color = PilotTextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(12.dp))
        CockpitTextField(
            value = tollAmount,
            onValueChange = { tollAmount = it },
            placeholder = "100",
            label = "Toll Amount (₹)"
        )
        Spacer(modifier = Modifier.height(10.dp))
        CockpitTextField(
            value = tollName,
            onValueChange = { tollName = it },
            placeholder = "e.g. MCD Border Toll Plaza / DND Flyway",
            label = "Toll Plaza / Border Gate Name"
        )
        Spacer(modifier = Modifier.height(16.dp))
        CockpitButton(
            text = if (isSubmitting) "Submitting Toll..." else "Add ₹$tollAmount to Commuter Bill",
            enabled = !isSubmitting && (tollAmount.toDoubleOrNull() ?: 0.0) > 0.0,
            onClick = {
                val currentRide = DriverRepository.currentRideId.value
                if (currentRide.isNullOrBlank()) {
                    DriverDialogController.dismiss()
                    DriverDialogController.showToast("No active ride found.")
                    return@CockpitButton
                }
                val amountVal = tollAmount.toDoubleOrNull() ?: 0.0
                if (amountVal <= 0.0) {
                    DriverDialogController.showToast("Please enter a valid toll amount.")
                    return@CockpitButton
                }

                isSubmitting = true
                scope.launch {
                    val liveLoc = DriverRepository.driverLocation.value
                    val lat = if (liveLoc.latitude != 0.0) liveLoc.latitude else null
                    val lng = if (liveLoc.longitude != 0.0) liveLoc.longitude else null

                    val res = DriverRepository.submitRideToll(
                        rideId = currentRide,
                        amountRupees = amountVal,
                        tollName = tollName.ifBlank { "Municipal Border Entry Tax" },
                        lat = lat,
                        lng = lng
                    )
                    isSubmitting = false
                    DriverDialogController.dismiss()
                    if (res.isSuccess) {
                        DriverDialogController.showToast("₹${"%.2f".format(amountVal)} toll added to invoice & driver wallet!")
                        DriverRepository.fetchRideBreakdown(currentRide)
                    } else {
                        DriverDialogController.showToast("Failed to add toll: ${res.exceptionOrNull()?.message ?: "Check connection"}")
                    }
                }
            }
        )
    }
}

// 14. Pilot Audio Recording Safety Consent Modal (DPDP Act 2023 Compliant)
@Composable
private fun DriverAudioRecordingConsentModal() {
    val scope = rememberCoroutineScope()
    var isSubmitting by remember { mutableStateOf(false) }
    val currentRide = DriverRepository.currentRideId.collectAsState().value
    val status by DriverRepository.audioRecordingStatus.collectAsState()

    CockpitCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "🎙️", fontSize = 24.sp, modifier = Modifier.padding(end = 8.dp))
            Column {
                Text(
                    text = "Pilot In-Cabin Safety Recording",
                    color = PilotTextWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Statutory DPDP Act 2023 Mutual Consent",
                    color = EmeraldPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "To protect both Pilot and Rider against dispute or harassment, in-cabin audio can be recorded under mutual dual-party opt-in.\n\n" +
                    "• Recording ONLY activates when both Pilot and Passenger grant explicit opt-in.\n" +
                    "• Encrypted with AES-256-GCM before upload to private sovereign cloud storage.\n" +
                    "• Auto-purged after 7 days unless locked by law enforcement / dispute incident.\n" +
                    "• You may withdraw consent at any time during the ride to stop recording immediately.",
            color = PilotTextMuted,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        val sessionStatus = status?.session?.recordingStatus ?: status?.driverConsentStatus ?: "NOT_REQUESTED"
        if (status?.isRecordingActive == true || status?.dualConsentAchieved == true) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1B3B2B))
                    .padding(10.dp)
            ) {
                Text(
                    text = "STATUS: $sessionStatus (Consent Granted by Both Parties)",
                    color = EmeraldPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    if (currentRide.isNotBlank()) {
                        scope.launch {
                            DriverRepository.withdrawAudioConsent(currentRide, "Pilot declined in-cabin recording")
                            DriverDialogController.dismiss()
                            DriverDialogController.showToast("In-cabin recording declined")
                        }
                    } else {
                        DriverDialogController.dismiss()
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Decline", color = Color(0xFFFF5252))
            }

            CockpitButton(
                text = if (isSubmitting) "Saving..." else "Opt-In & Agree",
                enabled = !isSubmitting,
                modifier = Modifier.weight(1.5f),
                onClick = {
                    if (currentRide.isBlank()) {
                        DriverDialogController.dismiss()
                        DriverDialogController.showToast("No active ride found")
                        return@CockpitButton
                    }
                    isSubmitting = true
                    scope.launch {
                        val res = DriverRepository.recordAudioConsent(
                            rideId = currentRide,
                            granted = true,
                            consentText = "Pilot voluntarily consents to encrypted in-cabin audio safety recording under DPDP Act 2023."
                        )
                        isSubmitting = false
                        DriverDialogController.dismiss()
                        if (res.isSuccess) {
                            DriverDialogController.showToast("Safety Audio Consent Recorded")
                            // Check if both parties granted; if so, attempt authorization
                            DriverRepository.authorizeAudioRecording(currentRide)
                        } else {
                            DriverDialogController.showToast("Consent registration failed: ${res.exceptionOrNull()?.message}")
                        }
                    }
                }
            )
        }
    }
}
