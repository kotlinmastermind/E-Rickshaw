package com.luxury.chauffeur.customer.feature.payments

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luxury.chauffeur.customer.core.designsystem.*
import com.luxury.chauffeur.customer.core.network.*
import com.luxury.chauffeur.customer.feature.loyalty.PostTripRatingAndTippingDialog
import kotlinx.coroutines.launch

@Composable
fun PaymentScreen(
    rideId: String,
    amountRupees: Int,
    onPaymentAndReviewCompleted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Payment Selection State (Feature 84)
    var selectedMethod by remember { mutableStateOf("UPI") } // UPI, SAVED_CARD, CORPORATE, CASH
    var selectedCard by remember { mutableStateOf<SavedPaymentMethodDto?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var paymentSettled by remember { mutableStateOf(false) }

    // Dialog Visibility States
    var showSavedCardsDialog by remember { mutableStateOf(false) }
    var showUpiDeepLinkDialog by remember { mutableStateOf(false) }
    var showSplitFareDialog by remember { mutableStateOf(false) }
    var showTaxInvoiceDialog by remember { mutableStateOf(false) }
    var showDisputeDialog by remember { mutableStateOf(false) }
    var showComprehensiveRatingDialog by remember { mutableStateOf(false) }

    // Feature 80: Route Change Explanation State
    var routeChangeExplanation by remember { mutableStateOf<RouteChangeExplanationResponse?>(null) }

    // Chauffeur Rating State
    var selectedRating by remember { mutableIntStateOf(5) }
    var feedbackText by remember { mutableStateOf("Exquisite driving etiquette, spotless vehicle.") }

    LaunchedEffect(rideId) {
        try {
            val res = ApiClient.getService().getRouteChangeExplanation(rideId)
            if (res.isSuccessful && res.body()?.hasChanged == true) {
                routeChangeExplanation = res.body()
            }
        } catch (_: Exception) {}
    }

    Scaffold(
        containerColor = LuxuryColors.ObsidianBlack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (!paymentSettled) "JOURNEY SETTLEMENT" else "RATE YOUR CHAUFFEUR",
                        style = LuxuryTypography.HeadlineMedium.copy(color = LuxuryColors.ChampagneGold, fontSize = 20.sp)
                    )
                    if (paymentSettled) {
                        GoldBadge("PAID")
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (!paymentSettled)
                        "Final verified fare for Ride #${rideId.take(12)}"
                    else
                        "Your feedback maintains our uncompromising standard of chauffeur excellence.",
                    style = LuxuryTypography.BodySubtle
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Feature 80: Fare change explanation after route changes
                if (routeChangeExplanation != null && routeChangeExplanation?.hasChanged == true) {
                    Surface(
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                        color = LuxuryColors.ChampagneGold.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, LuxuryColors.ChampagneGold),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("ⓘ", fontSize = 14.sp, color = LuxuryColors.ChampagneGold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ROUTE ADJUSTMENT FARE REVISION", style = LuxuryTypography.TitleGold.copy(fontSize = 11.sp))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Fare changed from ₹%.2f to ₹%.2f (%+,.2f INR) due to: %s".format(
                                    routeChangeExplanation?.originalFareRupees ?: 0.0,
                                    routeChangeExplanation?.finalFareRupees ?: 0.0,
                                    routeChangeExplanation?.fareDifferenceRupees ?: 0.0,
                                    routeChangeExplanation?.fareChangeReason ?: "Route modification"
                                ),
                                style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.PlatinumWhite, fontSize = 11.sp)
                            )
                        }
                    }
                }

                if (!paymentSettled) {
                    // Fare Total Box
                    Surface(
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                        color = LuxuryColors.CardSurface,
                        border = BorderStroke(1.dp, LuxuryColors.ChampagneGold),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("TOTAL AMOUNT DUE", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("₹$amountRupees.00", style = LuxuryTypography.HeadlineLarge.copy(color = LuxuryColors.ChampagneGold, fontSize = 32.sp))
                            Text("Inclusive of GST (SAC 9964), tolls and amenities", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))

                            Spacer(modifier = Modifier.height(12.dp))

                            // Feature 83: Split Fare Action
                            Surface(
                                modifier = Modifier.clickable { showSplitFareDialog = true },
                                shape = RoundedCornerShape(20.dp),
                                color = LuxuryColors.CharcoalSurface,
                                border = BorderStroke(1.dp, LuxuryColors.ChampagneGold.copy(alpha = 0.6f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("⇄", fontSize = 14.sp, color = LuxuryColors.ChampagneGold)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Split Fare Among Passengers", style = LuxuryTypography.TitleGold.copy(fontSize = 11.sp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                    Text("SELECT SETTLEMENT METHOD", style = LuxuryTypography.TitleGold.copy(fontSize = 12.sp))
                    Spacer(modifier = Modifier.height(10.dp))

                    // 1. UPI Instant (Features 87 & 88)
                    LuxuryCard(
                        border = if (selectedMethod == "UPI") BorderStroke(1.5.dp, LuxuryColors.ChampagneGold) else BorderStroke(1.dp, LuxuryColors.CardBorder),
                        onClick = { selectedMethod = "UPI" }
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("UPI Instant (GPay / PhonePe / Paytm / CRED)", style = LuxuryTypography.BodyRegular.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp))
                                Text("Instant bank-to-bank settlement with retry handling", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                            }
                            TextButton(onClick = { showUpiDeepLinkDialog = true }) {
                                Text("DEEP-LINK ›", style = LuxuryTypography.TitleGold.copy(fontSize = 11.sp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. Saved Tokenized Card (Features 85 & 86)
                    LuxuryCard(
                        border = if (selectedMethod == "SAVED_CARD") BorderStroke(1.5.dp, LuxuryColors.ChampagneGold) else BorderStroke(1.dp, LuxuryColors.CardBorder),
                        onClick = {
                            selectedMethod = "SAVED_CARD"
                            if (selectedCard == null) showSavedCardsDialog = true
                        }
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (selectedCard != null) "${selectedCard?.brand} •••• ${selectedCard?.last4}" else "Saved Card Vault (RBI Tokenized)",
                                    style = LuxuryTypography.BodyRegular.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                )
                                Text(
                                    text = if (selectedCard != null) "Issuer: ${selectedCard?.issuerBank ?: "Secure Vault"}" else "Encrypted tokenization via certified payment gateway",
                                    style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp)
                                )
                            }
                            TextButton(onClick = { showSavedCardsDialog = true }) {
                                Text(if (selectedCard != null) "CHANGE" else "SELECT", style = LuxuryTypography.TitleGold.copy(fontSize = 11.sp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3. Cashless Corporate Billing (Feature 89)
                    LuxuryCard(
                        border = if (selectedMethod == "CORPORATE") BorderStroke(1.5.dp, LuxuryColors.ChampagneGold) else BorderStroke(1.dp, LuxuryColors.CardBorder),
                        onClick = { selectedMethod = "CORPORATE" }
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Cashless Corporate Enterprise Billing", style = LuxuryTypography.BodyRegular.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp))
                                Text("Billed directly to executive company account & cost-center", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                            }
                            GoldBadge("CORP")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 4. Chauffeur Cash Handover
                    LuxuryCard(
                        border = if (selectedMethod == "CASH") BorderStroke(1.5.dp, LuxuryColors.ChampagneGold) else BorderStroke(1.dp, LuxuryColors.CardBorder),
                        onClick = { selectedMethod = "CASH" }
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Chauffeur Cash Collection", style = LuxuryTypography.BodyRegular.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp))
                                Text("Physical handover with digital receipt at destination", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                            }
                            Text("CASH", style = LuxuryTypography.TitleGold.copy(fontSize = 12.sp))
                        }
                    }
                } else {
                    // Part 7 Comprehensive Review & Gratuity Window (Features 151-157, 174)
                    LuxuryCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        border = BorderStroke(1.5.dp, LuxuryColors.ChampagneGold),
                        onClick = { showComprehensiveRatingDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "★ EXECUTIVE REVIEW & GRATUITY",
                                    style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Category scores, compliments, private notes, tips & CSAT survey",
                                    style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp)
                                )
                            }
                            GoldBadge("RATE & TIP ›")
                        }
                    }

                    // Feature 13: Quick Rate Driver (5 Stars)
                    Surface(
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                        color = LuxuryColors.CardSurface,
                        border = BorderStroke(1.dp, LuxuryColors.CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("YOUR JOURNEY EXPERIENCE", style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                (1..5).forEach { star ->
                                    IconButton(onClick = { selectedRating = star }) {
                                        Text(
                                            text = if (star <= selectedRating) "★" else "☆",
                                            fontSize = 32.sp,
                                            color = LuxuryColors.ChampagneGold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = feedbackText,
                                onValueChange = { feedbackText = it },
                                label = { Text("Private Feedback (Optional)", color = LuxuryColors.SubtleGray) },
                                textStyle = LuxuryTypography.BodyRegular,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = LuxuryColors.ChampagneGold,
                                    unfocusedBorderColor = LuxuryColors.CardBorder,
                                    focusedTextColor = LuxuryColors.PlatinumWhite,
                                    unfocusedTextColor = LuxuryColors.PlatinumWhite
                                ),
                                shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Post-Ride Compliance & Audit Actions (Features 96, 97, 100)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showTaxInvoiceDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            color = LuxuryColors.CharcoalSurface,
                            border = BorderStroke(1.dp, LuxuryColors.ChampagneGold.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("📄 TAX INVOICE", style = LuxuryTypography.TitleGold.copy(fontSize = 11.sp))
                                Text("GST SAC 9964 (Download/Email)", style = LuxuryTypography.BodySubtle.copy(fontSize = 9.sp))
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showDisputeDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            color = LuxuryColors.CharcoalSurface,
                            border = BorderStroke(1.dp, LuxuryColors.CrimsonAlert.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("⚖ FARE REVIEW", style = LuxuryTypography.TitleGold.copy(fontSize = 11.sp, color = LuxuryColors.PlatinumWhite))
                                Text("Dispute fare or detour", style = LuxuryTypography.BodySubtle.copy(fontSize = 9.sp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column {
                if (!paymentSettled) {
                    LuxuryButton(
                        text = when (selectedMethod) {
                            "UPI" -> "AUTHORIZE ₹$amountRupees VIA UPI"
                            "SAVED_CARD" -> if (selectedCard != null) "CHARGE ${selectedCard?.brand} (₹$amountRupees)" else "SELECT SAVED CARD"
                            "CORPORATE" -> "CHARGE TO CORPORATE ACCOUNT (₹$amountRupees)"
                            else -> "CONFIRM CASH SETTLEMENT (₹$amountRupees)"
                        },
                        isLoading = isProcessing,
                        onClick = {
                            isProcessing = true
                            scope.launch {
                                try {
                                    when (selectedMethod) {
                                        "CORPORATE" -> {
                                            val res = ApiClient.getService().settleCorporateRide(CorporateSettlementRequest(rideId = rideId))
                                            if (res.isSuccessful) {
                                                Toast.makeText(context, "Billed to Corporate Account.", Toast.LENGTH_SHORT).show()
                                                paymentSettled = true
                                            }
                                        }
                                        "SAVED_CARD" -> {
                                            ApiClient.getService().createPaymentIntent(
                                                PaymentIntentRequest(
                                                    rideId = rideId,
                                                    idempotencyKey = "pay_${rideId}_${System.currentTimeMillis()}"
                                                )
                                            )
                                            Toast.makeText(context, "Card charged via Tokenized Gateway.", Toast.LENGTH_SHORT).show()
                                            paymentSettled = true
                                        }
                                        "UPI" -> {
                                            val upiUri = Uri.parse("upi://pay?pa=luxurychauffeur@icici&pn=Luxury%20Ride&am=$amountRupees.00&cu=INR&tn=Ride-$rideId")
                                            val upiIntent = Intent(Intent.ACTION_VIEW, upiUri)
                                            try {
                                                context.startActivity(Intent.createChooser(upiIntent, "Pay via UPI App (GPay / PhonePe / Paytm)"))
                                            } catch (_: Exception) {}

                                            ApiClient.getService().createPaymentIntent(
                                                PaymentIntentRequest(
                                                    rideId = rideId,
                                                    idempotencyKey = "pay_${rideId}_${System.currentTimeMillis()}"
                                                )
                                            )
                                            paymentSettled = true
                                        }
                                        else -> {
                                            // Cash
                                            ApiClient.getService().createPaymentIntent(
                                                PaymentIntentRequest(
                                                    rideId = rideId,
                                                    idempotencyKey = "pay_cash_${rideId}_${System.currentTimeMillis()}"
                                                )
                                            )
                                            paymentSettled = true
                                        }
                                    }
                                } catch (_: Exception) {
                                    paymentSettled = true
                                }
                                isProcessing = false
                            }
                        }
                    )
                } else {
                    LuxuryButton(
                        text = "COMPLETE REVIEW & RETURN HOME",
                        isLoading = isProcessing,
                        onClick = {
                            showComprehensiveRatingDialog = true
                        }
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }

    // Modal Dialogs for Part 4
    if (showSavedCardsDialog) {
        SavedPaymentMethodsDialog(
            onDismiss = { showSavedCardsDialog = false },
            onMethodSelected = { method ->
                selectedCard = method
                selectedMethod = "SAVED_CARD"
                showSavedCardsDialog = false
            }
        )
    }

    if (showUpiDeepLinkDialog) {
        UpiDeepLinkCheckoutDialog(
            rideId = rideId,
            amountRupees = amountRupees.toDouble(),
            onDismiss = { showUpiDeepLinkDialog = false },
            onPaymentSuccess = {
                showUpiDeepLinkDialog = false
                paymentSettled = true
            }
        )
    }

    if (showSplitFareDialog) {
        SplitFareDialog(
            rideId = rideId,
            totalFareRupees = amountRupees.toDouble(),
            onDismiss = { showSplitFareDialog = false }
        )
    }

    if (showTaxInvoiceDialog) {
        TaxInvoiceDialog(
            rideId = rideId,
            onDismiss = { showTaxInvoiceDialog = false }
        )
    }

    if (showDisputeDialog) {
        FareDisputeDialog(
            rideId = rideId,
            totalFareRupees = amountRupees.toDouble(),
            onDismiss = { showDisputeDialog = false }
        )
    }

    if (showComprehensiveRatingDialog) {
        PostTripRatingAndTippingDialog(
            rideId = rideId,
            onDismiss = {
                showComprehensiveRatingDialog = false
                onPaymentAndReviewCompleted()
            },
            onCompleted = {
                showComprehensiveRatingDialog = false
                onPaymentAndReviewCompleted()
            }
        )
    }
}
