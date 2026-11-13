package com.vaisentra.mobility.driver.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class DriverPopupType {
    NONE,
    INCOMING_OFFER,
    RIDER_CANCELLED,
    LOW_BATTERY_SWAP,
    FATIGUE_BREAK,
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
                DriverPopupType.FATIGUE_BREAK -> FatigueBreakModal()
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

// 1. Incoming Ride Offer (15s countdown)
@Composable
private fun IncomingOfferModal() {
    var timer by remember { mutableStateOf(15) }
    LaunchedEffect(timer) {
        if (timer > 0) {
            delay(1000)
            timer--
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
        Text(text = "NET EARNING: ₹145.00", color = PilotElectricCyan, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = "Pickup: Connaught Place Gate 4 (350m away)", color = PilotTextWhite, fontSize = 13.sp)
        Text(text = "Drop: Sector 62, Cyber Heights, Noida (14.2 km)", color = PilotTextMuted, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(20.dp))
        CockpitButton(
            text = "Accept Ride Offer (${timer}s)",
            onClick = {
                val cb = DriverDialogController.onConfirmAction
                DriverDialogController.dismiss()
                com.vaisentra.mobility.driver.data.DriverRepository.acceptOffer("ride-offer-8291")
                DriverDialogController.showToast("Ride Accepted! Routing to pickup spot.")
                cb?.invoke()
            },
            backgroundColor = PilotOnlineGreen
        )
        Spacer(modifier = Modifier.height(8.dp))
        CockpitButton(
            text = "Decline Offer",
            onClick = {
                DriverDialogController.dismiss()
                com.vaisentra.mobility.driver.data.DriverRepository.rejectOffer("ride-offer-8291")
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
        Text(text = "The passenger cancelled the dispatch. ₹25.00 cancellation fee credited to your wallet balance.", color = PilotTextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(16.dp))
        CockpitBadge(text = "+₹25.00 WALLET COMPENATION", backgroundColor = OnlineGlow, textColor = PilotOnlineGreen)
        Spacer(modifier = Modifier.height(20.dp))
        CockpitButton(text = "Acknowledge & Return to Duty", onClick = { DriverDialogController.dismiss() })
    }
}

// 3. Low Battery Swap Reroute
@Composable
private fun LowBatterySwapModal() {
    CockpitCard(borderColor = PilotAmber) {
        Text(text = "🪫 Low Battery SoC (< 18%)", color = PilotAmber, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Battery SoC at 16%. Auto-dispatch paused. Guided to nearest Anand Vihar 3-minute Battery Swap Station.", color = PilotTextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(16.dp))
        CockpitButton(
            text = "Navigate to Fast-Swap Depot (1.2 km)",
            onClick = {
                CoroutineScope(Dispatchers.IO).launch {
                    DriverRepository.fetchHeatMap()
                }
                DriverDialogController.dismiss()
                DriverDialogController.showToast("Routing to Anand Vihar Swap Station via FastAPI.")
            },
            backgroundColor = PilotAmber,
            textColor = PilotCanvas
        )
    }
}

// 4. Mandatory Fatigue Break
@Composable
private fun FatigueBreakModal() {
    CockpitCard(borderColor = PilotAmber) {
        Text(text = "☕ Mandatory Fatigue Cooldown", color = PilotAmber, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "You have completed 4.5 hours of continuous transit duty. A 15-minute rest cooldown is required by fleet safety regulations.", color = PilotTextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(16.dp))
        CockpitButton(text = "Start 15-Min Safety Rest", onClick = {
            CoroutineScope(Dispatchers.IO).launch {
                DriverRepository.submitFatigueCheckResult("NEEDS_REST", "Pilot initiated 15-min cooldown modal")
            }
            DriverDialogController.dismiss()
            DriverDialogController.showToast("Fatigue break active in Supabase. Resume duty in 15 mins.")
        })
    }
}

// 5. Overspeeding Alert
@Composable
private fun OverspeedingModal() {
    CockpitCard(borderColor = PilotCrimson, backgroundColor = Color(0xFF1E1014)) {
        Text(text = "⚠️ URBAN SPEED WARNING", color = PilotCrimson, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Current speed is 42 km/h. Urban electric 3-wheeler corridor limit is 35 km/h. Please decelerate vehicle immediately.", color = PilotTextWhite, fontSize = 13.sp)
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
    var tender by remember { mutableStateOf("200") }
    val fare = 145.0
    val tenderVal = tender.toDoubleOrNull() ?: 0.0
    val returnChange = if (tenderVal >= fare) tenderVal - fare else 0.0

    CockpitCard {
        Text(text = "💵 Cash Fare Collection", color = PilotTextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Net Fare Payable: ₹145.00", color = PilotElectricCyan, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(12.dp))
        CockpitTextField(value = tender, onValueChange = { tender = it }, placeholder = "Cash received from passenger", label = "Cash Tendered (₹)")
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = "Return Change: ₹${returnChange.toInt()}.00", color = PilotAmber, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        CockpitButton(text = "Confirm ₹145 Cash Received", onClick = {
            val cb = DriverDialogController.onConfirmAction
            val currentRide = DriverRepository.currentRideId.value ?: "ride-latest"
            DriverDialogController.dismiss()
            DriverRepository.collectCash(currentRide, tenderVal)
            DriverDialogController.showToast("Cash payment reconciled in Supabase ledger!")
            cb?.invoke()
        })
    }
}

// 7. Commuter No-Show Expiry
@Composable
private fun NoShowExpiryModal() {
    CockpitCard(borderColor = PilotAmber) {
        Text(text = "⏱️ 5-Minute Waiting Grace Elapsed", color = PilotAmber, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Passenger did not arrive at pickup gate. You are authorized to cancel with ₹30.00 compensation credited to your ledger.", color = PilotTextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(16.dp))
        CockpitButton(text = "Claim ₹30 No-Show Compensation", onClick = {
            val currentRide = DriverRepository.currentRideId.value ?: "ride-latest"
            DriverRepository.stopTripWaiting(currentRide)
            DriverDialogController.dismiss()
            DriverDialogController.showToast("₹30 No-show compensation logged in ledger.")
        }, backgroundColor = PilotAmber, textColor = PilotCanvas)
    }
}

// 8. Route Deviation Anomaly
@Composable
private fun RouteDeviationModal() {
    CockpitCard(borderColor = PilotAmber) {
        Text(text = "🧭 Route Detour Detected", color = PilotAmber, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Vehicle departed trajectory by >300m near Akshardham. Please confirm if detour was required by municipal road works.", color = PilotTextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(16.dp))
        CockpitButton(text = "Confirm Detour due to Road Hazard", onClick = {
            val currentRide = DriverRepository.currentRideId.value ?: "ride-latest"
            CoroutineScope(Dispatchers.IO).launch {
                DriverRepository.reportRouteDeviation(currentRide, 28.6289, 77.3710)
            }
            DriverDialogController.dismiss()
            DriverDialogController.showToast("Road hazard & detour recorded in Supabase.")
        })
    }
}

// 9. Document Expiry Urgent
@Composable
private fun DocExpiryModal() {
    CockpitCard(borderColor = PilotAmber) {
        Text(text = "📋 Commercial DL Renewal Alert", color = PilotAmber, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Your Commercial Driving License expires in 3 days. Upload renewal certificate now to avoid automatic duty pause.", color = PilotTextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(16.dp))
        CockpitButton(text = "Open Document Upload Vault", onClick = {
            CoroutineScope(Dispatchers.IO).launch {
                DriverRepository.fetchDriverDocuments()
            }
            DriverDialogController.dismiss()
        })
    }
}

// 10. Pilot SOS Dispatched
@Composable
private fun PilotSosModal() {
    CockpitCard(borderColor = PilotCrimson, backgroundColor = Color(0xFF1E1014)) {
        Text(text = "🚨 PILOT PANIC SOS DISPATCHED", color = PilotCrimson, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Live vehicle GPS, audio feed, and cabin telemetry streamed to 24/7 Security Operations Center. Calling pilot phone...", color = PilotTextWhite, fontSize = 13.sp)
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
    CockpitCard(borderColor = PilotAmber) {
        Text(text = "📸 Odometer Meter Photo Required", color = PilotAmber, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Reported ending reading (48,290 km) deviates from GPS calculation. Snap a photo of the vehicle instrument cluster.", color = PilotTextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(16.dp))
        CockpitButton(text = "Capture Cluster Photo", onClick = {
            CoroutineScope(Dispatchers.IO).launch {
                DriverRepository.submitVehicleInspectionCheck("DL-1ER-4921", true, true, true)
            }
            DriverDialogController.dismiss()
            DriverDialogController.showToast("Meter cluster photo verified with Supabase!")
        })
    }
}

// 12. Daily Peak Bonus
@Composable
private fun PeakBonusModal() {
    CockpitCard(borderColor = PilotOnlineGreen) {
        Text(text = "🎉 Morning Peak Bonus Unlocked!", color = PilotOnlineGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Milestone achieved! 10 peak morning corridor rides completed. ₹300 incentive credited to wallet balance.", color = PilotTextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(16.dp))
        CockpitButton(text = "Claim ₹300 Cash Bonus", onClick = {
            CoroutineScope(Dispatchers.IO).launch {
                DriverRepository.fetchDailyTargets()
            }
            DriverDialogController.dismiss()
            DriverDialogController.showToast("₹300 Bonus Claimed!")
        })
    }
}

// 13. CSAT Review Notice
@Composable
private fun CsatAdvisoryModal() {
    CockpitCard {
        Text(text = "⭐ CSAT Standards Advisory", color = PilotTextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Rating is 4.95 ★. Remember to keep passenger cabin clean, offer courteous greeting, and maintain smooth electric acceleration.", color = PilotTextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(16.dp))
        CockpitButton(text = "Understood (Maintain Standards)", onClick = {
            CoroutineScope(Dispatchers.IO).launch {
                DriverRepository.fetchPerformanceScorecard()
            }
            DriverDialogController.dismiss()
        })
    }
}

// 14. Payout Success
@Composable
private fun PayoutSuccessModal() {
    CockpitCard(borderColor = PilotOnlineGreen) {
        Text(text = "⚡ Instant IMPS Payout Processed", color = PilotOnlineGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "UTR #4391028471. ₹800.00 settled instantly to your registered bank account (SBI **9102).", color = PilotTextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(16.dp))
        CockpitButton(text = "View Payout Slip", onClick = {
            CoroutineScope(Dispatchers.IO).launch {
                DriverRepository.fetchAccountLedger()
            }
            DriverDialogController.dismiss()
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
            DriverDialogController.showToast("₹$tollAmount toll added to trip invoice.")
        })
    }
}

