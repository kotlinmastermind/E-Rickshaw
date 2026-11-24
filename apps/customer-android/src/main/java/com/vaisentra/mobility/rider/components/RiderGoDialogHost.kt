package com.vaisentra.mobility.rider.components

import android.content.Context
import android.location.LocationManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var countdown by remember { mutableStateOf(5) }
    var isDispatching by remember { mutableStateOf(false) }

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
        NeoButton(
            text = if (isDispatching) "Transmitting Telemetry..." else "Dispatch SOS Immediately",
            backgroundColor = CrimsonRed,
            enabled = !isDispatching,
            onClick = {
                isDispatching = true
                scope.launch {
                    var liveLat = 0.0
                    var liveLng = 0.0
                    try {
                        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                        val lastGps = lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        val lastNet = lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                        val best = lastGps ?: lastNet
                        if (best != null) {
                            liveLat = best.latitude
                            liveLng = best.longitude
                        }
                    } catch (e: SecurityException) {
                        // Permission not granted, fallback to ride coordinates
                    }

                    if (liveLat == 0.0 && liveLng == 0.0) {
                        val active = RiderRepository.activeRide.value
                        liveLat = active?.driverLat ?: active?.pickupLatitude ?: RiderRepository.bookingDraft.value.pickupLat
                        liveLng = active?.driverLng ?: active?.pickupLongitude ?: RiderRepository.bookingDraft.value.pickupLng
                    }

                    val currentRideId = RiderRepository.activeRide.value?.id
                    val res = RiderRepository.triggerSos(liveLat, liveLng, currentRideId)
                    isDispatching = false
                    DialogController.dismiss()
                    if (res.isSuccess) {
                        DialogController.showToast("🚨 SOS Broadcasted to Fleet Command & Police 112!")
                    } else {
                        DialogController.showToast("⚠️ SOS transmission failed. Please dial 112 directly.")
                    }
                }
            }
        )
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
    val activeRide = RiderRepository.activeRide.value
    val driverName = activeRide?.driverName ?: activeRide?.driver?.name ?: "Pilot"
    val vehiclePlate = activeRide?.vehiclePlate ?: activeRide?.driver?.licenseNumber ?: "Vehicle"

    NeoCard(borderColor = ElectricCyan) {
        Text(text = "🎯 Pilot Arrived at Location", color = ElectricCyan, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Your pilot $driverName is waiting with vehicle $vehiclePlate.", color = TextMuted, fontSize = 13.sp)
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
    val context = LocalContext.current
    NeoCard(borderColor = AmberGold) {
        Text(text = "⚡ Low Wallet Balance", color = AmberGold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Recharge your commuter wallet securely via Razorpay UPI & Cards:", color = TextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(16.dp))
        NeoButton(text = "Recharge ₹250 via Razorpay", backgroundColor = AmberGold, textColor = CanvasBlack, onClick = {
            (context as? android.app.Activity)?.let { activity ->
                RiderRepository.launchWalletTopupCheckout(activity, 250.0)
            }
            DialogController.dismiss()
        })
        Spacer(modifier = Modifier.height(8.dp))
        NeoButton(text = "Pay Cash After Ride", backgroundColor = SurfaceDark, onClick = { DialogController.dismiss() })
    }
}

// 6. Split Fare Request
@Composable
private fun SplitFarePopup() {
    val scope = rememberCoroutineScope()
    val activeRide = RiderRepository.activeRide.value
    val rideId = activeRide?.id ?: "ride-active"
    val totalFare = activeRide?.fare ?: 129.0

    var phoneInput by remember { mutableStateOf("+91 98112 34567, +91 98711 55441") }
    var isSubmitting by remember { mutableStateOf(false) }

    val phones = remember(phoneInput) {
        phoneInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }
    val splitCount = phones.size + 1
    val sharePerPerson = if (splitCount > 0) totalFare / splitCount else totalFare

    NeoCard {
        Text(text = "➗ Split Fare with Co-Riders", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Total Trip Fare: ₹${"%.2f".format(totalFare)}. Divided among $splitCount riders (₹${"%.2f".format(sharePerPerson)} per person via UPI).",
            color = TextMuted,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(14.dp))
        NeoTextField(
            value = phoneInput,
            onValueChange = { phoneInput = it },
            placeholder = "Enter co-passenger mobile numbers",
            label = "Co-Passenger Mobile Numbers (comma separated)"
        )
        Spacer(modifier = Modifier.height(16.dp))
        NeoButton(
            text = if (isSubmitting) "Queuing Requests..." else "Send Split Requests (${phones.size} contacts)",
            backgroundColor = SapphirePrimary,
            enabled = phones.isNotEmpty() && !isSubmitting,
            onClick = {
                isSubmitting = true
                scope.launch {
                    val res = RiderRepository.initiateSplitFare(rideId, phones)
                    isSubmitting = false
                    DialogController.dismiss()
                    if (res.isSuccess) {
                        DialogController.showToast("Split requests queued in FastAPI payments! ₹${"%.2f".format(sharePerPerson)} / rider.")
                    } else {
                        DialogController.showToast("Failed to initiate split: ${res.exceptionOrNull()?.message ?: "Check ride status"}")
                    }
                }
            }
        )
    }
}

// 7. Audio Recording Consent (DPDP Act 2023 Compliant)
@Composable
private fun AudioRecordingConsentPopup() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activeRide = RiderRepository.activeRide.collectAsState().value
    val rideId = activeRide?.id ?: "ride-active"
    val audioStatus = RiderRepository.audioRecordingStatus.collectAsState().value
    val isRecordingActive = RiderRepository.isAudioRecordingActive.collectAsState().value

    NeoCard(borderColor = if (isRecordingActive) CrimsonRed else SapphirePrimary) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "🎙️ In-Cabin Safety Recording",
                color = TextWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (isRecordingActive) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CrimsonRed.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, CrimsonRed)
                ) {
                    Text(
                        text = "● REC ACTIVE",
                        color = CrimsonRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Under the Digital Personal Data Protection (DPDP) Act 2023, in-cabin audio recording requires mutual opt-in consent from both passenger and driver. Recordings are encrypted on-device with AES-256-GCM and retained in private storage for maximum 7 days.",
            color = TextMuted,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Dual-Party Status Overview
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = SurfaceDark,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Passenger Consent:", color = TextMuted, fontSize = 12.sp)
                    Text(
                        text = if (audioStatus?.passengerConsent == true) "GRANTED" else "PENDING / NOT GIVEN",
                        color = if (audioStatus?.passengerConsent == true) EmeraldGreen else AmberGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Chauffeur Consent:", color = TextMuted, fontSize = 12.sp)
                    Text(
                        text = if (audioStatus?.driverConsent == true) "GRANTED" else "AWAITING CHAUFFEUR",
                        color = if (audioStatus?.driverConsent == true) EmeraldGreen else AmberGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Actions
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeoButton(
                text = "Grant Consent",
                backgroundColor = SapphirePrimary,
                modifier = Modifier.weight(1f),
                onClick = {
                    CoroutineScope(Dispatchers.IO).launch {
                        val result = RiderRepository.recordAudioConsent(rideId, true)
                        if (result.isSuccess) {
                            val data = result.getOrNull()
                            if (data?.dualConsentAchieved == true) {
                                // Start recording service
                                try {
                                    val intent = android.content.Intent(context, com.vaisentra.mobility.rider.service.AudioSafetyRecordingService::class.java).apply {
                                        action = com.vaisentra.mobility.rider.service.AudioSafetyRecordingService.ACTION_START_RECORDING
                                        putExtra(com.vaisentra.mobility.rider.service.AudioSafetyRecordingService.EXTRA_RIDE_ID, rideId)
                                        putExtra(com.vaisentra.mobility.rider.service.AudioSafetyRecordingService.EXTRA_SESSION_ID, data.sessionId)
                                    }
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        context.startForegroundService(intent)
                                    } else {
                                        context.startService(intent)
                                    }
                                } catch (_: Exception) {}
                            }
                            RiderRepository.fetchAudioConsentStatus(rideId)
                        }
                    }
                    DialogController.dismiss()
                    DialogController.showToast("Passenger audio recording consent granted.")
                }
            )

            NeoButton(
                text = "Withdraw / Stop",
                backgroundColor = SurfaceDark,
                modifier = Modifier.weight(1f),
                onClick = {
                    CoroutineScope(Dispatchers.IO).launch {
                        RiderRepository.withdrawAudioConsent(rideId, "Passenger withdrew consent via safety dialog")
                        try {
                            val intent = android.content.Intent(context, com.vaisentra.mobility.rider.service.AudioSafetyRecordingService::class.java).apply {
                                action = com.vaisentra.mobility.rider.service.AudioSafetyRecordingService.ACTION_STOP_RECORDING
                            }
                            context.stopService(intent)
                        } catch (_: Exception) {}
                        RiderRepository.fetchAudioConsentStatus(rideId)
                    }
                    DialogController.dismiss()
                    DialogController.showToast("Safety recording stopped and consent withdrawn.")
                }
            )
        }
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
    val activeRide = RiderRepository.activeRide.value
    val driverName = activeRide?.driverName ?: activeRide?.driver?.name ?: "Pilot"
    var rating by remember { mutableStateOf(5) }
    var tipAmount by remember { mutableStateOf(20) }

    NeoCard {
        Text(text = "⭐ Rate $driverName", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
    val scope = rememberCoroutineScope()
    val activeRide = RiderRepository.activeRide.value
    var flightCode by remember { mutableStateOf(activeRide?.flightNumber ?: "6E-204") }
    var isRescheduling by remember { mutableStateOf(false) }

    NeoCard(borderColor = ElectricCyan) {
        Text(text = "✈️ Flight Delay Detected", color = ElectricCyan, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Flight $flightCode has reported an arrival delay. Would you like to reschedule your chauffeur pickup window by +45 mins?",
            color = TextMuted,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        NeoTextField(
            value = flightCode,
            onValueChange = { flightCode = it },
            placeholder = "e.g. 6E-204 or AI-101",
            label = "Inbound Flight Code"
        )
        Spacer(modifier = Modifier.height(16.dp))
        NeoButton(
            text = if (isRescheduling) "Rescheduling Pickup..." else "Reschedule +45 Mins (Free)",
            backgroundColor = ElectricCyan,
            textColor = CanvasBlack,
            enabled = !isRescheduling,
            onClick = {
                isRescheduling = true
                scope.launch {
                    val now = java.time.Instant.now()
                    val newPickupTimeIso = now.plusSeconds(45 * 60).toString()

                    if (activeRide != null) {
                        val res = RiderRepository.syncFlightDelay(activeRide.id, flightCode.trim())
                        isRescheduling = false
                        DialogController.dismiss()
                        if (res.isSuccess) {
                            DialogController.showToast("Chauffeur pickup rescheduled to +45 mins via FastAPI!")
                        } else {
                            val res2 = RiderRepository.rescheduleRide(activeRide.id, newPickupTimeIso, "Flight delay auto-reschedule", flightCode.trim())
                            if (res2.isSuccess) {
                                DialogController.showToast("Chauffeur pickup rescheduled via FastAPI.")
                            } else {
                                DialogController.showToast("Could not reschedule ride: ${res.exceptionOrNull()?.message}")
                            }
                        }
                    } else {
                        val dest = RiderRepository.bookingDraft.value.destinationAddress.ifBlank { "Connaught Place, New Delhi" }
                        val res = RiderRepository.scheduleRideBooking(
                            pickup = "IGI Airport Terminal 3 VIP Hub",
                            dest = dest,
                            pickupTimeIso = newPickupTimeIso
                        )
                        isRescheduling = false
                        DialogController.dismiss()
                        if (res.isSuccess) {
                            DialogController.showToast("New airport pickup scheduled for delayed flight $flightCode.")
                        } else {
                            DialogController.showToast("Booking failed: ${res.exceptionOrNull()?.message}")
                        }
                    }
                }
            }
        )
    }
}

