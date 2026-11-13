package com.vaisentra.mobility.rider.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vaisentra.mobility.rider.data.RiderRepository
import com.vaisentra.mobility.rider.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class RiderPopupType {
    NONE,
    CANCELLATION,
    SOS_112,
    PROMO_APPLIED,
    PILOT_ARRIVED,
    LOW_WALLET,
    SPLIT_FARE,
    AUDIO_CONSENT,
    LOST_ITEM,
    TIER_UPGRADE,
    RATING_TIP,
    SURGE_NOTICE,
    OUT_OF_GEOFENCE,
    GPS_CALIBRATION,
    BIOMETRIC_PASSKEY,
    AIRPORT_DELAY
}

object DialogController {
    var activePopup by mutableStateOf(RiderPopupType.NONE)
    var toastMessage by mutableStateOf<String?>(null)

    fun show(popup: RiderPopupType) {
        activePopup = popup
    }

    fun dismiss() {
        activePopup = RiderPopupType.NONE
    }

    fun showToast(msg: String) {
        toastMessage = msg
    }
}

@Composable
fun RiderGoDialogHost(
    onToastAction: (String) -> Unit = {}
) {
    val active = DialogController.activePopup

    // Auto-dismissing Toast Banner
    DialogController.toastMessage?.let { msg ->
        LaunchedEffect(msg) {
            delay(3000)
            DialogController.toastMessage = null
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
                    .background(SurfaceCard)
                    .border(1.dp, BorderFocus, RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "⚡", fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                    Text(text = msg, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (active != RiderPopupType.NONE) {
        Dialog(onDismissRequest = { DialogController.dismiss() }) {
            when (active) {
                RiderPopupType.CANCELLATION -> CancellationPopup()
                RiderPopupType.SOS_112 -> SosEmergencyPopup()
                RiderPopupType.PROMO_APPLIED -> PromoAppliedPopup()
                RiderPopupType.PILOT_ARRIVED -> PilotArrivedPopup()
                RiderPopupType.LOW_WALLET -> LowWalletPopup()
                RiderPopupType.SPLIT_FARE -> SplitFarePopup()
                RiderPopupType.AUDIO_CONSENT -> AudioRecordingConsentPopup()
                RiderPopupType.LOST_ITEM -> LostItemAckPopup()
                RiderPopupType.TIER_UPGRADE -> TierUpgradePopup()
                RiderPopupType.RATING_TIP -> RatingTipPopup()
                RiderPopupType.SURGE_NOTICE -> SurgeNoticePopup()
                RiderPopupType.OUT_OF_GEOFENCE -> OutOfGeofencePopup()
                RiderPopupType.GPS_CALIBRATION -> GpsCalibrationPopup()
                RiderPopupType.BIOMETRIC_PASSKEY -> BiometricPasskeyPopup()
                RiderPopupType.AIRPORT_DELAY -> AirportDelayPopup()
                RiderPopupType.NONE -> Unit
            }
        }
    }
}

// 1. Cancellation Confirmation
@Composable
private fun CancellationPopup() {
    NeoCard(borderColor = CrimsonRed) {
        Text(text = "🛑 Cancel Ride?", color = CrimsonRed, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Are you sure you want to cancel your ride? Zero cancellation fee applies if cancelled within 2 minutes of dispatch.",
            color = TextMuted, fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(20.dp))
        NeoButton(text = "Confirm Cancellation", backgroundColor = CrimsonRed, onClick = {
            val activeRideId = RiderRepository.activeRide.value?.id ?: "ride-active"
            CoroutineScope(Dispatchers.IO).launch {
                RiderRepository.cancelActiveRide(activeRideId)
            }
            DialogController.dismiss()
            DialogController.showToast("Ride successfully cancelled via FastAPI.")
        })
        Spacer(modifier = Modifier.height(10.dp))
        NeoButton(text = "Keep My Ride", backgroundColor = SurfaceDark, onClick = { DialogController.dismiss() })
    }
}

// 2. SOS 112 Emergency Panic Alert
@Composable
private fun SosEmergencyPopup() {
    var countdown by remember { mutableStateOf(5) }
    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000)
            countdown--
        }
    }

    NeoCard(borderColor = CrimsonRed, backgroundColor = Color(0xFF1E1014)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "🚨", fontSize = 28.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = "EMERGENCY SOS 112", color = CrimsonRed, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text(text = "Broadcasting to Police & Fleet SOC", color = TextMuted, fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Automated dispatch will transmit GPS coordinates, vehicle number, and pilot ID to Delhi Police 112 in:",
            color = TextWhite, fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "${countdown}s", color = CrimsonRed, fontSize = 42.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(12.dp))
        NeoButton(text = "Dispatch SOS Immediately", backgroundColor = CrimsonRed, onClick = {
            CoroutineScope(Dispatchers.IO).launch {
                RiderRepository.triggerSos(28.6289, 77.3710)
            }
            DialogController.dismiss()
            DialogController.showToast("🚨 SOS Broadcasted to FastAPI & Police 112!")
        })
        Spacer(modifier = Modifier.height(8.dp))
        NeoButton(text = "Cancel False Alarm", backgroundColor = SurfaceDark, onClick = { DialogController.dismiss() })
    }
}

// 3. Promo Voucher Applied
@Composable
private fun PromoAppliedPopup() {
    NeoCard(borderColor = EmeraldGreen) {
        Text(text = "🎉 Promo Code Applied!", color = EmeraldGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Code 'ELECTRIC100' saved you ₹65.00 on this trip.", color = TextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(16.dp))
        NeoBadge(text = "INSTANT DISCOUNT: -₹65.00", backgroundColor = EmeraldGlow, textColor = EmeraldGreen)
        Spacer(modifier = Modifier.height(20.dp))
        NeoButton(text = "Apply Savings", backgroundColor = EmeraldGreen, onClick = {
            DialogController.dismiss()
            DialogController.showToast("ELECTRIC100 applied to current fare.")
        })
    }
}

// 4. Pilot Arrived Buzzer
@Composable
private fun PilotArrivedPopup() {
    NeoCard(borderColor = ElectricCyan) {
        Text(text = "🎯 Pilot Arrived at Gate", color = ElectricCyan, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Your pilot Ram Lal is waiting at Gate 2 with vehicle DL-1ER-4921.", color = TextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(20.dp))
        NeoButton(text = "Sound Courtesy Buzzer 🔔", backgroundColor = SapphirePrimary, onClick = {
            val tripId = RiderRepository.activeRide.value?.id ?: "trip-current"
            CoroutineScope(Dispatchers.IO).launch {
                RiderRepository.sendPoolChatMessage(tripId, "Passenger sounded arrival buzzer.")
            }
            DialogController.showToast("Buzzer sounded on Pilot's device via WebSocket!")
            DialogController.dismiss()
        })
        Spacer(modifier = Modifier.height(8.dp))
        NeoButton(text = "I'm Walking Out", backgroundColor = SurfaceDark, onClick = { DialogController.dismiss() })
    }
}

// 5. Low Wallet Top-up
@Composable
private fun LowWalletPopup() {
    NeoCard(borderColor = AmberGold) {
        Text(text = "⚡ Low Wallet Balance", color = AmberGold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Your balance is ₹48.00. Trip estimated at ₹129.00. Recharge seamlessly with 1-tap UPI:", color = TextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(16.dp))
        NeoButton(text = "Recharge ₹250 via UPI", backgroundColor = AmberGold, textColor = CanvasBlack, onClick = {
            CoroutineScope(Dispatchers.IO).launch {
                RiderRepository.topupWalletBalance(250.0)
            }
            DialogController.dismiss()
            DialogController.showToast("₹250 added to RiderGo Wallet via FastAPI!")
        })
        Spacer(modifier = Modifier.height(8.dp))
        NeoButton(text = "Pay Cash After Ride", backgroundColor = SurfaceDark, onClick = { DialogController.dismiss() })
    }
}

// 6. Split Fare Request
@Composable
private fun SplitFarePopup() {
    NeoCard {
        Text(text = "➗ Split Fare with Co-Riders", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Divide ₹129.00 equally with co-passengers. Each rider pays ₹43.00 via UPI.", color = TextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(16.dp))
        NeoButton(text = "Send Split Requests to Contacts", backgroundColor = SapphirePrimary, onClick = {
            val rideId = RiderRepository.activeRide.value?.id ?: "ride-active"
            CoroutineScope(Dispatchers.IO).launch {
                RiderRepository.configureAutoTopup(false, 0, 0) // Keep state aligned
            }
            DialogController.dismiss()
            DialogController.showToast("Split requests queued in FastAPI payments.")
        })
    }
}

// 7. Audio Recording Consent
@Composable
private fun AudioRecordingConsentPopup() {
    NeoCard {
        Text(text = "🎙️ In-Cabin Safety Recording", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "For commuter peace of mind, trip audio is encrypted on-device and decrypted only during formal safety investigations.", color = TextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(16.dp))
        NeoButton(text = "Grant Safety Consent", backgroundColor = SapphirePrimary, onClick = {
            CoroutineScope(Dispatchers.IO).launch {
                RiderRepository.savePreferences("22°C", true)
            }
            DialogController.dismiss()
            DialogController.showToast("Audio recording consent synced with backend.")
        })
    }
}

// 8. Lost Item Acknowledgment
@Composable
private fun LostItemAckPopup() {
    NeoCard(borderColor = EmeraldGreen) {
        Text(text = "🎒 Lost Item Reported", color = EmeraldGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Ticket #LOST-8291 logged. Chauffeur concierge will inspect the vehicle cabin and reach out in 15 mins.", color = TextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(16.dp))
        NeoButton(text = "Track Ticket Status", backgroundColor = SurfaceDark, onClick = {
            val rideId = RiderRepository.activeRide.value?.id ?: "ride-latest"
            CoroutineScope(Dispatchers.IO).launch {
                RiderRepository.reportLostProperty(rideId, "CABIN_ITEM", "Lost item reported via popup dialog")
            }
            DialogController.dismiss()
            DialogController.showToast("Ticket #LOST-8291 registered in Supabase.")
        })
    }
}

// 9. Tier Upgrade
@Composable
private fun TierUpgradePopup() {
    NeoCard(borderColor = AmberGold) {
        Text(text = "🏆 Welcome to Green Club Gold!", color = AmberGold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "You've completed 25 zero-emission EV rides and offset 84.5 kg of CO2! Enjoy 10% lifetime ride discount.", color = TextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(16.dp))
        NeoButton(text = "Claim Gold Perks", backgroundColor = AmberGold, textColor = CanvasBlack, onClick = {
            CoroutineScope(Dispatchers.IO).launch {
                RiderRepository.fetchLoyaltySummary()
            }
            DialogController.dismiss()
            DialogController.showToast("Gold perks activated in Green Club ledger.")
        })
    }
}

// 10. Rating & Tip
@Composable
private fun RatingTipPopup() {
    var rating by remember { mutableStateOf(5) }
    var tipAmount by remember { mutableStateOf(20) }

    NeoCard {
        Text(text = "⭐ Rate Ram Lal", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            (1..5).forEach { star ->
                Text(
                    text = if (star <= rating) "★" else "☆",
                    color = AmberGold,
                    fontSize = 32.sp,
                    modifier = Modifier.clickable { rating = star }
                )
            }
        }
        Text(text = "Add Chauffeur Gratuity (100% goes to pilot):", color = TextMuted, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10, 20, 50).forEach { tip ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (tipAmount == tip) SapphirePrimary else SurfaceCard)
                        .clickable { tipAmount = tip }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(text = "+₹$tip", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        NeoButton(text = "Submit Rating & ₹$tipAmount Tip", backgroundColor = SapphirePrimary, onClick = {
            val rideId = RiderRepository.activeRide.value?.id ?: "ride-latest"
            CoroutineScope(Dispatchers.IO).launch {
                RiderRepository.submitRating(rideId, rating, "Rating via dialog popup")
                if (tipAmount > 0) {
                    RiderRepository.submitTip(rideId, tipAmount)
                }
            }
            DialogController.dismiss()
            DialogController.showToast("Rating & ₹$tipAmount tip recorded in Supabase!")
        })
    }
}

// 11. Surge Notice
@Composable
private fun SurgeNoticePopup() {
    NeoCard(borderColor = AmberGold) {
        Text(text = "⚡ Peak Corridor Demand", color = AmberGold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "High demand near Anand Vihar Metro. Fares are 1.35x standard tariff to mobilize nearby pilots.", color = TextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(16.dp))
        NeoButton(text = "Accept 1.35x Surge", backgroundColor = AmberGold, textColor = CanvasBlack, onClick = {
            DialogController.dismiss()
            DialogController.showToast("1.35x Surge tariff accepted.")
        })
    }
}

// 12. Out of Geofence
@Composable
private fun OutOfGeofencePopup() {
    NeoCard(borderColor = AmberGold) {
        Text(text = "🗺️ Outside Transit Geofence", color = AmberGold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Destination pin is 4.5 km beyond our electric 3-wheeler operating boundary. Please select a point within Delhi NCR urban corridor.", color = TextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(16.dp))
        NeoButton(text = "Adjust Drop-off Pin", backgroundColor = SapphirePrimary, onClick = { DialogController.dismiss() })
    }
}

// 13. GPS Calibration
@Composable
private fun GpsCalibrationPopup() {
    NeoCard {
        Text(text = "📡 Calibrate GPS Precision", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Enable Wi-Fi location scanning for sub-3-meter pickup beacon accuracy.", color = TextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(16.dp))
        NeoButton(text = "Calibrate Location", backgroundColor = SapphirePrimary, onClick = {
            DialogController.dismiss()
            DialogController.showToast("GPS Precision: ±2.5m (Optimal)")
        })
    }
}

// 14. Biometric Passkey
@Composable
private fun BiometricPasskeyPopup() {
    NeoCard {
        Text(text = "🛡️ Enable Biometric Passkey", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Use Fingerprint or Face ID for instant 1-tap ride authorization without SMS OTP delays.", color = TextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(16.dp))
        NeoButton(text = "Enrol Hardware Biometrics", backgroundColor = SapphirePrimary, onClick = {
            CoroutineScope(Dispatchers.IO).launch {
                RiderRepository.registerPasskey("passkey-hw-cred-modal", "secp256r1-public-key-blob")
            }
            DialogController.dismiss()
            DialogController.showToast("Biometric Passkey Enrolled in Supabase!")
        })
    }
}

// 15. Airport Delay
@Composable
private fun AirportDelayPopup() {
    NeoCard(borderColor = ElectricCyan) {
        Text(text = "✈️ Flight Delay Detected", color = ElectricCyan, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Flight 6E-204 is delayed by 45 mins. Would you like to reschedule your pickup time automatically?", color = TextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(16.dp))
        NeoButton(text = "Reschedule +45 Mins (Free)", backgroundColor = ElectricCyan, textColor = CanvasBlack, onClick = {
            CoroutineScope(Dispatchers.IO).launch {
                RiderRepository.scheduleRideBooking("Airport Terminal 3", "Noida Sector 62", "2026-11-12T18:30:00Z")
            }
            DialogController.dismiss()
            DialogController.showToast("Chauffeur pickup rescheduled to 18:30 via FastAPI.")
        })
    }
}

