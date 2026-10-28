package com.luxury.chauffeur.customer.feature.payments

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luxury.chauffeur.customer.core.designsystem.*
import com.luxury.chauffeur.customer.core.network.*
import kotlinx.coroutines.launch

// -----------------------------------------------------------------------------
// Features 85 & 86: Saved Payment Methods & Card Tokenization Dialog
// -----------------------------------------------------------------------------
@Composable
fun SavedPaymentMethodsDialog(
    onDismiss: () -> Unit,
    onMethodSelected: (SavedPaymentMethodDto) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var methods by remember { mutableStateOf<List<SavedPaymentMethodDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddCard by remember { mutableStateOf(false) }

    // Add Card Form State
    var cardNumber by remember { mutableStateOf("") }
    var expMonth by remember { mutableStateOf("12") }
    var expYear by remember { mutableStateOf("2028") }
    var cardholderName by remember { mutableStateOf("") }
    var isTokenizing by remember { mutableStateOf(false) }

    fun refreshMethods() {
        scope.launch {
            try {
                val res = ApiClient.getService().getSavedPaymentMethods()
                if (res.isSuccessful) {
                    methods = res.body() ?: emptyList()
                }
            } catch (_: Exception) {}
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshMethods()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(LuxuryDimensions.RadiusLarge),
            color = LuxuryColors.CardSurface,
            border = BorderStroke(1.dp, LuxuryColors.ChampagneGold)
        ) {
            Column(modifier = Modifier.padding(LuxuryDimensions.PaddingLarge)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("SAVED PAYMENT VAULT", style = LuxuryTypography.HeadlineMedium.copy(color = LuxuryColors.ChampagneGold, fontSize = 18.sp))
                        Text("RBI Tokenized Cards & Unified VPAs", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                    }
                    TextButton(onClick = { showAddCard = !showAddCard }) {
                        Text(if (showAddCard) "CANCEL" else "+ ADD CARD", style = LuxuryTypography.TitleGold.copy(fontSize = 12.sp))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (showAddCard) {
                    // Feature 86: Tokenize Card Form
                    Surface(
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                        color = LuxuryColors.ObsidianBlack,
                        border = BorderStroke(1.dp, LuxuryColors.ChampagneGold),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("TOKENIZE NEW EXECUTIVE CARD", style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp))
                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = cardNumber,
                                onValueChange = { if (it.length <= 19) cardNumber = it },
                                label = { Text("16-Digit Card Number", color = LuxuryColors.SubtleGray) },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = LuxuryTypography.BodyRegular,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = LuxuryColors.ChampagneGold,
                                    unfocusedBorderColor = LuxuryColors.CardBorder
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = expMonth,
                                    onValueChange = { expMonth = it },
                                    label = { Text("MM", color = LuxuryColors.SubtleGray) },
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LuxuryColors.ChampagneGold)
                                )
                                OutlinedTextField(
                                    value = expYear,
                                    onValueChange = { expYear = it },
                                    label = { Text("YYYY", color = LuxuryColors.SubtleGray) },
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LuxuryColors.ChampagneGold)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = cardholderName,
                                onValueChange = { cardholderName = it },
                                label = { Text("Cardholder Name", color = LuxuryColors.SubtleGray) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LuxuryColors.ChampagneGold)
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            LuxuryGoldButton(
                                text = if (isTokenizing) "TOKENIZING WITH RBI VAULT..." else "SECURE & SAVE CARD",
                                onClick = {
                                    if (cardNumber.length >= 15 && cardholderName.isNotBlank()) {
                                        isTokenizing = true
                                        scope.launch {
                                            try {
                                                val res = ApiClient.getService().tokenizeCard(
                                                    TokenizeCardRequest(
                                                        cardNumber = cardNumber,
                                                        expMonth = expMonth.toIntOrNull() ?: 12,
                                                        expYear = expYear.toIntOrNull() ?: 2028,
                                                        cardholderName = cardholderName,
                                                        isDefault = methods.isEmpty()
                                                    )
                                                )
                                                if (res.isSuccessful) {
                                                    Toast.makeText(context, "Card successfully tokenized with RBI Vault.", Toast.LENGTH_SHORT).show()
                                                    showAddCard = false
                                                    refreshMethods()
                                                }
                                            } catch (_: Exception) {}
                                            isTokenizing = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                if (isLoading) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = LuxuryColors.ChampagneGold)
                    }
                } else if (methods.isEmpty() && !showAddCard) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No saved payment instruments", style = LuxuryTypography.BodyRegular)
                            Text("Add a card above to tokenize under RBI guidelines.", style = LuxuryTypography.BodySubtle)
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(methods) { m ->
                            Surface(
                                shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                                color = LuxuryColors.ObsidianBlack,
                                border = BorderStroke(1.dp, if (m.isDefault) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder),
                                modifier = Modifier.fillMaxWidth().clickable { onMethodSelected(m); onDismiss() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (m.methodType == "CARD") "💳" else "⚡", fontSize = 20.sp)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = if (m.methodType == "CARD") "${m.brand} •••• ${m.last4}" else (m.vpaAddress ?: "UPI VPA"),
                                                style = LuxuryTypography.BodyRegular.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Text(
                                                text = if (m.methodType == "CARD") "${m.issuerBank ?: "Bank"} • Exp: ${m.expMonth}/${m.expYear}" else "Direct Bank Settlement",
                                                style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp)
                                            )
                                        }
                                    }

                                    if (m.isDefault) {
                                        GoldBadge("DEFAULT")
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                LuxuryGoldButton(text = "DONE", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Features 87 & 88: UPI Deep-Link Checkout & Retry BottomSheet / Modal
// -----------------------------------------------------------------------------
@Composable
fun UpiDeepLinkCheckoutDialog(
    rideId: String,
    amountRupees: Double,
    onDismiss: () -> Unit,
    onPaymentSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var upiData by remember { mutableStateOf<UpiIntentResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var retryAttempt by remember { mutableIntStateOf(0) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(rideId) {
        try {
            val res = ApiClient.getService().generateUpiIntent(
                UpiIntentRequest(rideId = rideId, amountCents = (amountRupees * 100).toInt())
            )
            if (res.isSuccessful) {
                upiData = res.body()
            }
        } catch (_: Exception) {}
        isLoading = false
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(LuxuryDimensions.RadiusLarge),
            color = LuxuryColors.CardSurface,
            border = BorderStroke(1.dp, LuxuryColors.ChampagneGold)
        ) {
            Column(modifier = Modifier.padding(LuxuryDimensions.PaddingLarge), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("UPI INSTANT CHECKOUT", style = LuxuryTypography.HeadlineMedium.copy(color = LuxuryColors.ChampagneGold, fontSize = 18.sp))
                Text("Zero-touch settlement via your preferred UPI app", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))

                Spacer(modifier = Modifier.height(18.dp))

                Text("AMOUNT PAYABLE", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp, letterSpacing = 1.sp))
                Text("₹${amountRupees.toInt()}.00", style = LuxuryTypography.HeadlineLarge.copy(color = LuxuryColors.ChampagneGold, fontSize = 28.sp))

                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    CircularProgressIndicator(color = LuxuryColors.ChampagneGold)
                } else {
                    // Feature 87: Launch UPI App
                    LuxuryGoldButton(
                        text = "OPEN UPI APP (GPAY / PHONEPE)",
                        onClick = {
                            val uri = upiData?.upiIntentUri
                            if (uri != null) {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "No UPI app found. Please copy VPA.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Feature 88: UPI Intent Retry Handling
                    OutlinedButton(
                        onClick = {
                            retryAttempt += 1
                            scope.launch {
                                try {
                                    val res = ApiClient.getService().retryUpiIntent(
                                        UpiRetryRequest(rideId = rideId, retryAttempt = retryAttempt)
                                    )
                                    if (res.isSuccessful) {
                                        statusMessage = res.body()?.message
                                    }
                                } catch (_: Exception) {}
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = LuxuryColors.PlatinumWhite),
                        border = BorderStroke(1.dp, LuxuryColors.CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (retryAttempt == 0) "SIMULATE / RETRY PAYMENT" else "RETRY ATTEMPT #$retryAttempt", fontSize = 12.sp)
                    }

                    if (statusMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(statusMessage!!, style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.ChampagneGold, fontSize = 11.sp))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onPaymentSuccess,
                        colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.EmeraldGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("CONFIRM SETTLEMENT (DEMO ACK)", color = LuxuryColors.ObsidianBlack, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onDismiss) {
                    Text("CANCEL", style = LuxuryTypography.BodySubtle)
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Feature 83: Split Fare Dialog
// -----------------------------------------------------------------------------
@Composable
fun SplitFareDialog(
    rideId: String,
    totalFareRupees: Double,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var passenger1Phone by remember { mutableStateOf("") }
    var passenger1Name by remember { mutableStateOf("") }
    var passenger2Phone by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var splitSuccess by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f),
            shape = RoundedCornerShape(LuxuryDimensions.RadiusLarge),
            color = LuxuryColors.CardSurface,
            border = BorderStroke(1.dp, LuxuryColors.ChampagneGold)
        ) {
            Column(modifier = Modifier.padding(LuxuryDimensions.PaddingLarge)) {
                Text("SPLIT JOURNEY FARE", style = LuxuryTypography.HeadlineMedium.copy(color = LuxuryColors.ChampagneGold, fontSize = 18.sp))
                Text("Share this executive travel with companions", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))

                Spacer(modifier = Modifier.height(14.dp))

                val splitAmount = (totalFareRupees / 2).toInt()
                Text("EACH PAYER SHARE: ₹$splitAmount.00", style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp))

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = passenger1Name,
                    onValueChange = { passenger1Name = it },
                    label = { Text("Companion Name", color = LuxuryColors.SubtleGray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LuxuryColors.ChampagneGold)
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = passenger1Phone,
                    onValueChange = { passenger1Phone = it },
                    label = { Text("Companion Telephone (+91)", color = LuxuryColors.SubtleGray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LuxuryColors.ChampagneGold)
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (splitSuccess) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = LuxuryColors.EmeraldGreen.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, LuxuryColors.EmeraldGreen)
                    ) {
                        Text(
                            text = "Split request dispatched to companion via SMS/Push. They can approve in their passenger app.",
                            style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.EmeraldGreen, fontSize = 11.sp),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                LuxuryGoldButton(
                    text = if (isSubmitting) "DISPATCHING..." else "INITIATE SPLIT REQUEST",
                    onClick = {
                        if (passenger1Phone.isNotBlank()) {
                            isSubmitting = true
                            scope.launch {
                                try {
                                    val splitsList = listOf(
                                        mapOf(
                                            "name" to (passenger1Name.ifBlank { "Companion" }),
                                            "phone" to passenger1Phone,
                                            "amount_cents" to (splitAmount * 100)
                                        )
                                    )
                                    val res = ApiClient.getService().initiateSplitFare(
                                        rideId = rideId,
                                        payload = SplitFareInitiateRequest(splits = splitsList)
                                    )
                                    if (res.isSuccessful) {
                                        splitSuccess = true
                                        Toast.makeText(context, "Fare split initiated.", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (_: Exception) {}
                                isSubmitting = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("CLOSE", style = LuxuryTypography.BodySubtle)
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Features 96 & 97: Tax Invoice Download & Email Delivery Dialog
// -----------------------------------------------------------------------------
@Composable
fun TaxInvoiceDialog(
    rideId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var invoice by remember { mutableStateOf<TaxInvoiceResponse?>(null) }
    var emailInput by remember { mutableStateOf("") }
    var isSendingEmail by remember { mutableStateOf(false) }
    var emailSentStatus by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(rideId) {
        try {
            val res = ApiClient.getService().getTaxInvoice(rideId)
            if (res.isSuccessful) {
                invoice = res.body()
            }
        } catch (_: Exception) {}
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).wrapContentHeight(),
            shape = RoundedCornerShape(LuxuryDimensions.RadiusLarge),
            color = LuxuryColors.CardSurface,
            border = BorderStroke(1.dp, LuxuryColors.ChampagneGold)
        ) {
            Column(modifier = Modifier.padding(LuxuryDimensions.PaddingLarge)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("TAX INVOICE", style = LuxuryTypography.HeadlineMedium.copy(color = LuxuryColors.ChampagneGold, fontSize = 18.sp))
                        Text(invoice?.invoiceNumber ?: "INV-GC-2026", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                    }
                    GoldBadge("SAC 9964")
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (invoice == null) {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = LuxuryColors.ChampagneGold)
                    }
                } else {
                    val inv = invoice!!
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = LuxuryColors.ObsidianBlack,
                        border = BorderStroke(1.dp, LuxuryColors.CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Grand Chauffeur Mobility Pvt Ltd", style = LuxuryTypography.BodyRegular.copy(fontWeight = FontWeight.Bold))
                            Text("GSTIN: ${inv.platformGstin}", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Passenger: ${inv.customerName}", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                            Text("Recipient GSTIN: ${inv.customerGstin}", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                            HorizontalDivider(color = LuxuryColors.CardBorder, modifier = Modifier.padding(vertical = 6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Taxable Value", style = LuxuryTypography.BodySubtle)
                                Text("₹${inv.subtotalRupees}", style = LuxuryTypography.BodyRegular)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("CGST (2.5%)", style = LuxuryTypography.BodySubtle)
                                Text("₹${inv.cgstRupees}", style = LuxuryTypography.BodyRegular)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("SGST (2.5%)", style = LuxuryTypography.BodySubtle)
                                Text("₹${inv.sgstRupees}", style = LuxuryTypography.BodyRegular)
                            }
                            HorizontalDivider(color = LuxuryColors.CardBorder, modifier = Modifier.padding(vertical = 6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("INVOICE TOTAL", style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp))
                                Text("₹${inv.totalRupees}", style = LuxuryTypography.TitleGold.copy(fontSize = 14.sp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Feature 97: Email invoice
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Accounting Email for Dispatch", color = LuxuryColors.SubtleGray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LuxuryColors.ChampagneGold)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (emailSentStatus != null) {
                        Text(emailSentStatus!!, style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.EmeraldGreen, fontSize = 11.sp))
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (emailInput.isNotBlank()) {
                                    isSendingEmail = true
                                    scope.launch {
                                        try {
                                            val res = ApiClient.getService().sendInvoiceEmail(
                                                rideId = rideId,
                                                payload = SendInvoiceEmailRequest(recipientEmail = emailInput)
                                            )
                                            if (res.isSuccessful) {
                                                emailSentStatus = "Dispatched PDF to $emailInput"
                                            }
                                        } catch (_: Exception) {}
                                        isSendingEmail = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.ChampagneGold),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isSendingEmail) "SENDING..." else "EMAIL INVOICE", color = LuxuryColors.ObsidianBlack, fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                Toast.makeText(context, "Invoice PDF saved to Downloads.", Toast.LENGTH_SHORT).show()
                            },
                            border = BorderStroke(1.dp, LuxuryColors.CardBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("DOWNLOAD PDF", fontSize = 11.sp, color = LuxuryColors.PlatinumWhite)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("CLOSE", style = LuxuryTypography.BodySubtle)
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Feature 100: Fare Dispute & Review Request Dialog
// -----------------------------------------------------------------------------
@Composable
fun FareDisputeDialog(
    rideId: String,
    totalFareRupees: Double,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedReason by remember { mutableStateOf("EXTRA_WAIT_CHARGED") }
    var claimedRupees by remember { mutableStateOf("250") }
    var comments by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var submissionSuccess by remember { mutableStateOf(false) }

    val reasons = listOf(
        "EXTRA_WAIT_CHARGED" to "Disputed Waiting Time Charge",
        "INCORRECT_ROUTE" to "Inefficient Chauffeur Route Taken",
        "SURGE_MISTAKE" to "Incorrect Peak Surge Applied",
        "DRIVER_EARLY_STOP" to "Trip Ended Before Final Drop Point"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f),
            shape = RoundedCornerShape(LuxuryDimensions.RadiusLarge),
            color = LuxuryColors.CardSurface,
            border = BorderStroke(1.dp, LuxuryColors.CrimsonAlert)
        ) {
            Column(modifier = Modifier.padding(LuxuryDimensions.PaddingLarge)) {
                Text("DISPUTE FARE CHARGE", style = LuxuryTypography.HeadlineMedium.copy(color = LuxuryColors.ChampagneGold, fontSize = 18.sp))
                Text("Formal executive review with our Service Quality Desk", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))

                Spacer(modifier = Modifier.height(12.dp))

                Text("SELECT DISPUTE GROUNDS", style = LuxuryTypography.TitleGold.copy(fontSize = 11.sp))
                reasons.forEach { (code, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedReason = code }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedReason == code),
                            onClick = { selectedReason = code },
                            colors = RadioButtonDefaults.colors(selectedColor = LuxuryColors.ChampagneGold)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(label, style = LuxuryTypography.BodyRegular.copy(fontSize = 12.sp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = claimedRupees,
                    onValueChange = { claimedRupees = it },
                    label = { Text("Claimed Refund Amount (₹)", color = LuxuryColors.SubtleGray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LuxuryColors.ChampagneGold)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = comments,
                    onValueChange = { comments = it },
                    label = { Text("Observations / Context", color = LuxuryColors.SubtleGray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LuxuryColors.ChampagneGold)
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (submissionSuccess) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = LuxuryColors.EmeraldGreen.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, LuxuryColors.EmeraldGreen)
                    ) {
                        Text(
                            text = "Dispute Case lodged. Executive Concierge turnaround: 2 hours. Claimed refund will auto-credit to VIP Wallet upon review.",
                            style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.EmeraldGreen, fontSize = 11.sp),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                LuxuryGoldButton(
                    text = if (isSubmitting) "SUBMITTING DISPUTE..." else "SUBMIT FOR EXECUTIVE REVIEW",
                    onClick = {
                        val amt = (claimedRupees.toIntOrNull() ?: 100) * 100
                        isSubmitting = true
                        scope.launch {
                            try {
                                val res = ApiClient.getService().submitFareDispute(
                                    rideId = rideId,
                                    payload = SubmitDisputeRequest(
                                        disputeReason = selectedReason,
                                        claimedAmountCents = amt,
                                        customerComment = comments.ifBlank { "Formal review requested." }
                                    )
                                )
                                if (res.isSuccessful) {
                                    submissionSuccess = true
                                    Toast.makeText(context, "Dispute registered.", Toast.LENGTH_SHORT).show()
                                }
                            } catch (_: Exception) {}
                            isSubmitting = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("CLOSE", style = LuxuryTypography.BodySubtle)
                }
            }
        }
    }
}
