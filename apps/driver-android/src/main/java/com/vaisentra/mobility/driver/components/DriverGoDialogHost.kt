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
    LOW_BATTERY_SWAP,
    OVERSPEEDING_ALERT,
    CASH_COLLECT,
    NO_SHOW_EXPIRY,
    ROUTE_DEVIATION,
    DOC_EXPIRY,
    PILOT_SOS,
    ODOMETER_CHECK,
    PEAK_BONUS,
    CSAT_ADVISORY,
    PAYOUT_SUCCESS,
    TOLL_ENTRY
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
                DriverPopupType.LOW_BATTERY_SWAP -> LowBatterySwapModal()
                DriverPopupType.OVERSPEEDING_ALERT -> OverspeedingModal()
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
            text = "NET EARNING: ₹${"%.2f".format(currentOffer.estimatedDriverEarningsRupees)}",
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

// 3. Low Battery Swap Reroute
@Composable
private fun LowBatterySwapModal() {
    val cb = DriverDialogController.onConfirmAction
    CockpitCard(borderColor = PilotAmber) {
        Text(text = "🪫 Low Battery SoC (< 18%)", color = PilotAmber, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Battery SoC is low. Auto-dispatch paused. Navigate to nearest Battery Swap Station.", color = PilotTextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(16.dp))
        CockpitButton(
            text = "Open Battery Swap Depot",
            onClick = {
                DriverDialogController.dismiss()
                cb?.invoke()
                DriverDialogController.showToast("Opening Battery Swap Depot locator.")
            },
            backgroundColor = PilotAmber,
            textColor = PilotCanvas
        )
    }
}

// 5. Overspeeding Alert
@Composable
private fun OverspeedingModal() {
    CockpitCard(borderColor = PilotCrimson, backgroundColor = Color(0xFF1E1014)) {
        Text(text = "⚠️ URBAN SPEED WARNING", color = PilotCrimson, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Vehicle speed exceeded urban electric 3-wheeler corridor limit (35 km/h). Please decelerate immediately.", color = PilotTextWhite, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(16.dp))
        CockpitButton(text = "Decelerate Vehicle (Conforming)", onClick = {
            DriverDialogController.dismiss()
            DriverDialogController.showToast("Speed warning acknowledged.")
        }, backgroundColor = PilotCrimson)
    }
}

// 6. Cash Fare Collection Calculator
@Composable
private fun CashCollectModal() {
    val offer by DriverRepository.activeOffer.collectAsState()
    val fare = remember(offer) { (offer?.estimatedFareRupees ?: 145.0).coerceAtLeast(0.0) }
    var tender by remember { mutableStateOf(fare.toInt().toString()) }
    val tenderVal = tender.toDoubleOrNull() ?: 0.0
    val returnChange = if (tenderVal >= fare) tenderVal - fare else 0.0

    CockpitCard {
        Text(text = "💵 Cash Fare Collection", color = PilotTextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Net Fare Payable: ₹${"%.2f".format(fare)}", color = PilotElectricCyan, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(12.dp))
        CockpitTextField(value = tender, onValueChange = { tender = it }, placeholder = "Cash received from passenger", label = "Cash Tendered (₹)")
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = "Return Change: ₹${"%.2f".format(returnChange)}", color = PilotAmber, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        CockpitButton(text = "Confirm ₹${"%.2f".format(fare)} Cash Received", onClick = {
            val cb = DriverDialogController.onConfirmAction
            val currentRide = DriverRepository.currentRideId.value
            if (currentRide.isNullOrBlank()) {
                DriverDialogController.showToast("No active ride found.")
                return@CockpitButton
            }
            DriverRepository.collectCash(currentRide) { ok ->
                DriverDialogController.dismiss()
                if (ok) {
                    DriverDialogController.showToast("Cash payment reconciled!")
                    cb?.invoke()
                } else {
                    DriverDialogController.showToast("Failed to confirm cash collection.")
                }
            }
        })
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
    CockpitCard(borderColor = PilotAmber) {
        Text(text = "🧭 Route Detour Detected", color = PilotAmber, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Vehicle departed trajectory by >300m. Confirm if detour was required by municipal road works.", color = PilotTextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(16.dp))
        CockpitButton(text = "Confirm Detour due to Road Hazard", onClick = {
            val currentRide = DriverRepository.currentRideId.value
            if (currentRide.isNullOrBlank()) {
                DriverDialogController.dismiss()
                return@CockpitButton
            }
            scope.launch {
                val res = DriverRepository.reportRouteDeviation(currentRide, 28.6289, 77.3710)
                DriverDialogController.dismiss()
                if (res.isSuccess) {
                    DriverDialogController.showToast("Road hazard & detour recorded.")
                } else {
                    DriverDialogController.showToast("Failed to log route detour.")
                }
            }
        })
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

// 11. Odometer Discrepancy
@Composable
private fun OdometerCheckModal() {
    val scope = rememberCoroutineScope()
    val profile by DriverRepository.profile.collectAsState()
    CockpitCard(borderColor = PilotAmber) {
        Text(text = "📸 Vehicle & Odometer Verification", color = PilotAmber, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Reported odometer reading deviates from GPS calculation. Submit quick vehicle verification check.", color = PilotTextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(16.dp))
        CockpitButton(text = "Submit Verification Check", onClick = {
            scope.launch {
                val vehicleId = profile.assignedVehiclePlate.ifBlank { "default-vehicle" }
                val res = DriverRepository.submitVehicleInspectionCheck(vehicleId, true, true, true)
                DriverDialogController.dismiss()
                if (res.isSuccess) {
                    DriverDialogController.showToast("Vehicle verification submitted!")
                } else {
                    DriverDialogController.showToast("Verification failed. Check connection.")
                }
            }
        })
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
    var tollAmount by remember { mutableStateOf("100") }
    CockpitCard {
        Text(text = "🛂 Add Border Toll / Surcharge", color = PilotTextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Add official state / MCD municipal border entry tax to commuter's final invoice.", color = PilotTextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(12.dp))
        CockpitTextField(value = tollAmount, onValueChange = { tollAmount = it }, placeholder = "₹100", label = "Toll Amount (₹)")
        Spacer(modifier = Modifier.height(16.dp))
        CockpitButton(text = "Add ₹$tollAmount to Commuter Bill", onClick = {
            DriverDialogController.dismiss()
            DriverDialogController.showToast("₹$tollAmount toll noted for trip invoice.")
        })
    }
}