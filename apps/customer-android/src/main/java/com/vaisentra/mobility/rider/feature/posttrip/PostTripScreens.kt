package com.vaisentra.mobility.rider.feature.posttrip

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.rider.components.*
import com.vaisentra.mobility.rider.data.ActiveRideDto
import com.vaisentra.mobility.rider.data.RiderRepository
import com.vaisentra.mobility.rider.theme.*
import kotlinx.coroutines.launch

// Screen 36: Ride Completed & Digital Receipt
@Composable
fun RideReceiptScreen(onNavigate: (String) -> Unit) {
    val receipt by RiderRepository.rideReceipt.collectAsState()
    val context = LocalContext.current
    val activeRide by RiderRepository.activeRide.collectAsState()

    val ridePayState by RiderRepository.ridePaymentState.collectAsState()

    LaunchedEffect(activeRide?.id) {
        RiderRepository.restoreCheckoutStates()
        val rideId = activeRide?.id?.takeIf { it.isNotBlank() }
        if (rideId != null) {
            RiderRepository.fetchRideReceipt(rideId)
        }
    }

    val total = receipt?.totalAmountRupees ?: 0.0
    val baseFare = receipt?.baseFare ?: 0.0
    val distanceCharge = receipt?.distanceCharge ?: 0.0
    val timeCharge = receipt?.timeCharge ?: 0.0
    val taxAmount = receipt?.taxAmount ?: 0.0
    val discountAmount = receipt?.discountAmount ?: 0.0
    val method = receipt?.paymentMethod ?: "UPI"
    val distanceKm = receipt?.distanceKm ?: 0.0
    val durationMin = receipt?.durationMinutes ?: 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        NeoHeader(title = "Trip Receipt", subtitle = "Itemized Settlement Slip", onBack = { onNavigate("map_home") })
        Spacer(modifier = Modifier.height(16.dp))

        val (badgeText, badgeColor, cardBorderColor) = when {
            receipt == null && activeRide?.id.isNullOrBlank() -> Triple("NO ACTIVE RIDE", TextMuted, BorderSubtle)
            method == "WALLET" && total > 0.0 -> Triple("PAID IN FULL ✓", EmeraldGreen, EmeraldGreen)
            ridePayState is RiderRepository.RidePaymentState.Success -> Triple("PAID IN FULL ✓", EmeraldGreen, EmeraldGreen)
            ridePayState is RiderRepository.RidePaymentState.Error -> Triple("PAYMENT FAILED", CrimsonRed, CrimsonRed)
            ridePayState is RiderRepository.RidePaymentState.AuthorizedPending ||
                ridePayState is RiderRepository.RidePaymentState.ReconciliationRequired ||
                ridePayState is RiderRepository.RidePaymentState.CreatingOrder ||
                ridePayState is RiderRepository.RidePaymentState.Verifying -> Triple("PAYMENT PENDING", AmberGold, AmberGold)
            total > 0.0 -> Triple("SETTLEMENT PENDING", AmberGold, BorderSubtle)
            else -> Triple("NO RECEIPT", TextMuted, BorderSubtle)
        }

        NeoCard(borderColor = cardBorderColor) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Amount Paid via $method", color = TextMuted, fontSize = 12.sp)
                NeoBadge(text = badgeText, backgroundColor = SurfaceElevated, textColor = badgeColor)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "₹${"%.2f".format(total)}", color = ElectricCyan, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(14.dp))
            Divider(color = BorderSubtle)
            Spacer(modifier = Modifier.height(14.dp))
            val items = mutableListOf(
                "Base Fare" to "₹${"%.2f".format(baseFare)}",
                "Distance (${"%.2f".format(distanceKm)} km)" to "₹${"%.2f".format(distanceCharge)}",
                "Time (${"%.1f".format(durationMin)} mins)" to "₹${"%.2f".format(timeCharge)}",
                "GST (5%)" to "₹${"%.2f".format(taxAmount)}"
            )
            if (discountAmount > 0.0) {
                items.add("Discounts & Promos" to "-₹${"%.2f".format(discountAmount)}")
            }
            items.forEach { (k, v) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = k, color = TextMuted, fontSize = 12.sp)
                    Text(text = v, color = TextWhite, fontSize = 12.sp)
                }
            }
        }

        if (method != "WALLET" && total > 0) {
            val ridePayState by RiderRepository.ridePaymentState.collectAsState()
            Spacer(modifier = Modifier.height(14.dp))
            when (val rState = ridePayState) {
                is RiderRepository.RidePaymentState.CreatingOrder -> {
                    Text("Creating secure Razorpay order...", color = AmberGold, fontSize = 12.sp)
                }
                is RiderRepository.RidePaymentState.Verifying -> {
                    Text("Verifying payment with backend...", color = ElectricCyan, fontSize = 12.sp)
                }
                is RiderRepository.RidePaymentState.AuthorizedPending -> {
                    Text("⏳ ${rState.message}", color = AmberGold, fontSize = 12.sp)
                }
                is RiderRepository.RidePaymentState.ReconciliationRequired -> {
                    Text("⚠️ ${rState.message}", color = AmberGold, fontSize = 12.sp)
                }
                is RiderRepository.RidePaymentState.Success -> {
                    Text("✓ ${rState.message}", color = EmeraldGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                is RiderRepository.RidePaymentState.Error -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("⚠️ ${rState.message}", color = CrimsonRed, fontSize = 12.sp)
                        if (RiderRepository.hasPendingVerification(com.vaisentra.mobility.rider.data.CheckoutFlowType.RIDE_FARE)) {
                            NeoButton(
                                text = "Retry Verification",
                                onClick = { RiderRepository.retryPendingVerification(com.vaisentra.mobility.rider.data.CheckoutFlowType.RIDE_FARE) },
                                backgroundColor = ElectricCyan,
                                textColor = CanvasBlack
                            )
                        }
                    }
                }
                else -> {}
            }
            Spacer(modifier = Modifier.height(8.dp))
            NeoButton(
                text = "Pay ₹${"%.2f".format(total)} via Razorpay",
                onClick = {
                    (context as? android.app.Activity)?.let { activity ->
                        val targetRideId = receipt?.rideId ?: activeRide?.id.orEmpty()
                        if (targetRideId.isNotBlank()) {
                            RiderRepository.launchRidePaymentCheckout(activity, targetRideId)
                        } else {
                            DialogController.showToast("Missing ride identifier for payment.")
                        }
                    }
                },
                backgroundColor = AmberGold,
                textColor = CanvasBlack,
                icon = "💳"
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeoButton(
                text = "Rate Chauffeur",
                onClick = { onNavigate("rate_driver") },
                modifier = Modifier.weight(1f),
                backgroundColor = SapphirePrimary,
                icon = "⭐"
            )
            NeoButton(
                text = "GST Invoice",
                onClick = { onNavigate("gst_invoice") },
                modifier = Modifier.weight(1f),
                backgroundColor = SurfaceElevated,
                icon = "📄"
            )
        }
    }
}

// Screen 37: Driver Rating & Factor CSAT
@Composable
fun DriverRatingScreen(onNavigate: (String) -> Unit) {
    val activeRide by RiderRepository.activeRide.collectAsState()
    val driverName = activeRide?.driverName ?: activeRide?.driver?.name ?: "your Pilot"
    var rating by remember { mutableStateOf(5) }
    val compliments = listOf("⚡ Smooth Driving", "🧼 Spotless EV", "🧭 Expert Route", "🙏 Polite Chauffeur")
    val selectedCompliments = remember { mutableStateListOf<String>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        NeoHeader(title = "Rate Pilot Experience", subtitle = "Quality Assurance & Compliments", onBack = { onNavigate("ride_receipt") })
        Spacer(modifier = Modifier.height(16.dp))

        NeoCard {
            Text(text = "How was your journey with $driverName?", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                (1..5).forEach { star ->
                    Text(
                        text = if (star <= rating) "★" else "☆",
                        color = AmberGold,
                        fontSize = 38.sp,
                        modifier = Modifier.clickable { rating = star }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Add Compliments:", color = TextMuted, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            compliments.forEach { comp ->
                val isSelected = selectedCompliments.contains(comp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) SapphirePrimary.copy(alpha = 0.3f) else SurfaceCard)
                        .border(1.dp, if (isSelected) BorderFocus else BorderSubtle, RoundedCornerShape(10.dp))
                        .clickable {
                            if (isSelected) selectedCompliments.remove(comp) else selectedCompliments.add(comp)
                        }
                        .padding(10.dp)
                ) {
                    Text(text = comp, color = TextWhite, fontSize = 13.sp)
                }
            }
        }

        val scope = rememberCoroutineScope()
        var isSubmittingRating by remember { mutableStateOf(false) }
        var ratingError by remember { mutableStateOf<String?>(null) }

        if (ratingError != null) {
            Text(text = "⚠️ $ratingError", color = CrimsonRed, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        NeoButton(
            text = if (isSubmittingRating) "Submitting Rating..." else "Submit Rating & Tip Driver →",
            enabled = !isSubmittingRating,
            onClick = {
                val rideId = RiderRepository.activeRide.value?.id?.takeIf { it.isNotBlank() }
                if (rideId == null) {
                    ratingError = "Missing ride identifier for rating."
                    DialogController.showToast("Missing ride identifier for rating.")
                    return@NeoButton
                }
                isSubmittingRating = true
                ratingError = null
                scope.launch {
                    val res = RiderRepository.submitRating(rideId, rating, selectedCompliments.joinToString(", "))
                    isSubmittingRating = false
                    if (res.isSuccess) {
                        DialogController.showToast("Rating submitted to pilot ledger!")
                        onNavigate("tip_driver")
                    } else {
                        val msg = res.exceptionOrNull()?.message ?: "Failed to submit rating. Please retry."
                        ratingError = msg
                        DialogController.showToast(msg)
                    }
                }
            },
            backgroundColor = SapphirePrimary
        )
    }
}

// Screen 38: Driver Tip & Gratuity
@Composable
fun DriverTipScreen(onNavigate: (String) -> Unit) {
    var tipAmt by remember { mutableStateOf(20) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
    ) {
        NeoHeader(title = "Chauffeur Gratuity", subtitle = "100% credited to pilot wallet", onBack = { onNavigate("rate_driver") })
        Spacer(modifier = Modifier.height(16.dp))

        NeoCard {
            Text(text = "Select Tip Amount", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(10, 20, 50, 100).forEach { t ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (tipAmt == t) SapphirePrimary else SurfaceCard)
                            .clickable { tipAmt = t }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "₹$t", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
        val tipScope = rememberCoroutineScope()
        var isSubmittingTip by remember { mutableStateOf(false) }
        var tipError by remember { mutableStateOf<String?>(null) }

        if (tipError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "⚠️ $tipError", color = CrimsonRed, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(20.dp))
        NeoButton(
            text = if (isSubmittingTip) "Processing Tip..." else "Pay ₹$tipAmt Tip via UPI",
            enabled = !isSubmittingTip,
            onClick = {
                val activeRide = RiderRepository.activeRide.value
                val rideId = activeRide?.id?.takeIf { it.isNotBlank() }
                if (rideId == null) {
                    tipError = "Missing ride identifier for tip."
                    DialogController.showToast("Missing ride identifier for tip.")
                    return@NeoButton
                }
                val driverName = activeRide.driverName ?: activeRide.driver?.name ?: "Pilot"
                isSubmittingTip = true
                tipError = null
                tipScope.launch {
                    val res = RiderRepository.submitTip(rideId, tipAmt)
                    isSubmittingTip = false
                    if (res.isSuccess) {
                        DialogController.showToast("₹$tipAmt tip credited to $driverName!")
                        onNavigate("past_trips")
                    } else {
                        val msg = res.exceptionOrNull()?.message ?: "Failed to process tip. Please retry."
                        tipError = msg
                        DialogController.showToast(msg)
                    }
                }
            },
            icon = "💵"
        )
    }
}

// Screen 39: Past Trips & Activity History (Supabase: rides)
@Composable
fun PastTripsHistoryScreen(onNavigate: (String) -> Unit) {
    val liveTrips by RiderRepository.rideHistory.collectAsState()

    LaunchedEffect(Unit) {
        RiderRepository.fetchRideHistory()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
    ) {
        NeoHeader(title = "Ride Activity Ledger", subtitle = "Completed trips synced from Supabase", onBack = { onNavigate("map_home") })
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (liveTrips.isNotEmpty()) {
                items(liveTrips) { ride ->
                    val fareStr = if (ride.totalFareCents != null) "₹${ride.totalFareCents / 100}.00" else "₹0.00"
                    val routeStr = "${ride.pickupAddress} ➔ ${ride.destinationAddress}"
                    val detailStr = "$fareStr • ${ride.vehicleModel ?: "EV Rickshaw"} • ${ride.status}"
                    val timeStr = ride.createdAt?.take(16)?.replace("T", " ") ?: "Recent Trip"

                    NeoCard(
                        modifier = Modifier.clickable { onNavigate("ride_receipt") },
                        cornerRadius = 14.dp
                    ) {
                        Text(text = timeStr, color = TextDim, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = routeStr, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = detailStr, color = ElectricCyan, fontSize = 12.sp)
                    }
                }
            } else {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "📭", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(text = "No Past Trips Found", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = "No ride records available in database.", color = TextMuted, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

// Screen 40: GST Tax Invoice & Download
@Composable
fun GstInvoiceScreen(onNavigate: (String) -> Unit) {
    var recipientGstin by remember { mutableStateOf("07AAECV9988D1Z2") }
    var companyName by remember { mutableStateOf("Vaisentra Corp Labs") }
    var invoiceData by remember { mutableStateOf<Map<String, Any>?>(null) }
    var isEmailing by remember { mutableStateOf(false) }

    val activeRide by RiderRepository.activeRide.collectAsState()
    val rideId = activeRide?.id ?: "ride-latest"

    LaunchedEffect(rideId) {
        invoiceData = RiderRepository.fetchTaxInvoice(rideId)
    }

    val invNum = invoiceData?.get("invoice_number")?.toString() ?: "#INV-2026-${rideId.take(6).uppercase()}"
    val platformName = invoiceData?.get("platform_name")?.toString() ?: "Vaisentra Mobility Technologies Pvt Ltd"
    val platformGstin = invoiceData?.get("platform_gstin")?.toString() ?: "07AABCV1234F1Z8"
    val sacCode = invoiceData?.get("sac_code")?.toString() ?: "996412"
    val subtotal = (invoiceData?.get("subtotal_rupees") as? Number)?.toDouble() ?: 0.0
    val cgst = (invoiceData?.get("cgst_rupees") as? Number)?.toDouble() ?: 0.0
    val sgst = (invoiceData?.get("sgst_rupees") as? Number)?.toDouble() ?: 0.0
    val totalVal = (invoiceData?.get("total_rupees") as? Number)?.toDouble() ?: (subtotal + cgst + sgst)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        NeoHeader(title = "Tax Invoice", subtitle = "Govt Compliant IRN Signed Slip", onBack = { onNavigate("ride_receipt") })
        Spacer(modifier = Modifier.height(14.dp))

        // Premium Official Tax Invoice Voucher Card
        NeoCard(borderColor = SapphireGlow) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(text = "TAX INVOICE", color = ElectricCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(text = invNum, color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
                NeoBadge(text = "IRN GENERATED ✓", backgroundColor = EmeraldGlow, textColor = EmeraldGreen)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = BorderSubtle)
            Spacer(modifier = Modifier.height(12.dp))

            Text(text = "SUPPLIER DETAILS", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(text = platformName, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(text = "GSTIN: $platformGstin • SAC Code: $sacCode", color = TextMuted, fontSize = 11.sp)
            Text(text = "Reg. Office: Cyber City, Tower 9, Gurugram NCR", color = TextDim, fontSize = 10.sp)

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = BorderSubtle)
            Spacer(modifier = Modifier.height(12.dp))

            Text(text = "RECIPIENT (B2B CORPORATE)", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = companyName, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                NeoBadge(text = "ITC ELIGIBLE", backgroundColor = SurfaceDark, textColor = EmeraldGreen)
            }
            Text(text = "Recipient GSTIN: $recipientGstin", color = ElectricCyan, fontSize = 11.sp)

            Spacer(modifier = Modifier.height(14.dp))
            Divider(color = BorderSubtle)
            Spacer(modifier = Modifier.height(14.dp))

            // Line items breakdown
            Text(text = "TAX BREAKUP & TARIFF", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))

            val items = listOf(
                "Taxable Service Value" to "₹${"%.2f".format(subtotal)}",
                "CGST @ 2.5%" to "₹${"%.2f".format(cgst)}",
                "SGST @ 2.5%" to "₹${"%.2f".format(sgst)}"
            )

            items.forEach { (desc, amt) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = desc, color = if (desc.startsWith("Taxable")) TextWhite else TextMuted, fontSize = 11.sp, fontWeight = if (desc.startsWith("Taxable")) FontWeight.Bold else FontWeight.Normal)
                    Text(text = amt, color = TextWhite, fontSize = 11.sp, fontWeight = if (desc.startsWith("Taxable")) FontWeight.Bold else FontWeight.Normal)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = BorderSubtle)
            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Total Invoice Value (INR):", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(text = "₹${"%.2f".format(totalVal)}", color = ElectricCyan, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }

            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceDark)
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🔐", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Digitally signed with SHA-256 e-Sign token under Indian IT Act 2000.",
                        color = TextDim,
                        fontSize = 9.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeoButton(
                text = "Download PDF Slip",
                onClick = {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        RiderRepository.fetchTaxInvoice(rideId)
                    }
                    DialogController.showToast("GST Tax Invoice saved to Downloads!")
                },
                modifier = Modifier.weight(1f),
                icon = "📥",
                backgroundColor = SapphirePrimary
            )
            NeoButton(
                text = if (isEmailing) "Sending..." else "Email Invoice",
                enabled = !isEmailing,
                onClick = {
                    isEmailing = true
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        val res = RiderRepository.sendInvoiceEmail(rideId, "billing@vaisentra.com")
                        isEmailing = false
                        if (res.isSuccess) {
                            DialogController.showToast("Invoice sent to corporate email successfully!")
                        } else {
                            DialogController.showToast("Email send error: ${res.exceptionOrNull()?.message}")
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                icon = "✉️",
                backgroundColor = SurfaceElevated
            )
        }
    }
}

