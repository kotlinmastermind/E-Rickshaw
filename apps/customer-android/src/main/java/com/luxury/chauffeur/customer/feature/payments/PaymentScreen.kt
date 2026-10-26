package com.luxury.chauffeur.customer.feature.payments

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luxury.chauffeur.customer.core.designsystem.*
import com.luxury.chauffeur.customer.core.network.ApiClient
import com.luxury.chauffeur.customer.core.network.PaymentIntentRequest
import kotlinx.coroutines.launch

@Composable
fun PaymentScreen(
    rideId: String,
    amountRupees: Int,
    onPaymentAndReviewCompleted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedMethod by remember { mutableStateOf("UPI") }
    var isProcessing by remember { mutableStateOf(false) }
    var paymentSettled by remember { mutableStateOf(false) }
    var selectedRating by remember { mutableIntStateOf(5) }
    var feedbackText by remember { mutableStateOf("Exquisite driving etiquette, spotless vehicle.") }

    Scaffold(
        containerColor = LuxuryColors.ObsidianBlack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = if (!paymentSettled) "JOURNEY SETTLEMENT" else "RATE YOUR CHAUFFEUR",
                    style = LuxuryTypography.HeadlineMedium.copy(color = LuxuryColors.ChampagneGold)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (!paymentSettled)
                        "Final verified fare for Ride #$rideId"
                    else
                        "Your feedback maintains our uncompromising standard of chauffeur excellence.",
                    style = LuxuryTypography.BodySubtle
                )
                Spacer(modifier = Modifier.height(24.dp))

                if (!paymentSettled) {
                    // Fare Total Box
                    Surface(
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                        color = LuxuryColors.CardSurface,
                        border = BorderStroke(1.dp, LuxuryColors.ChampagneGold),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("AMOUNT DUE", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("₹$amountRupees.00", style = LuxuryTypography.HeadlineLarge.copy(color = LuxuryColors.ChampagneGold))
                            Text("Inclusive of luxury road taxes and tolls", style = LuxuryTypography.BodySubtle)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text("SELECT SETTLEMENT METHOD", style = LuxuryTypography.TitleGold)
                    Spacer(modifier = Modifier.height(12.dp))

                    LuxuryCard(
                        border = if (selectedMethod == "UPI") BorderStroke(1.5.dp, LuxuryColors.ChampagneGold) else BorderStroke(1.dp, LuxuryColors.CardBorder),
                        onClick = { selectedMethod = "UPI" }
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("UPI Instant (GPay / PhonePe / Paytm)", style = LuxuryTypography.BodyRegular.copy(fontWeight = FontWeight.Bold))
                                Text("Zero contact direct bank transfer", style = LuxuryTypography.BodySubtle)
                            }
                            GoldBadge("FAST")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LuxuryCard(
                        border = if (selectedMethod == "CASH") BorderStroke(1.5.dp, LuxuryColors.ChampagneGold) else BorderStroke(1.dp, LuxuryColors.CardBorder),
                        onClick = { selectedMethod = "CASH" }
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Chauffeur Cash Collection", style = LuxuryTypography.BodyRegular.copy(fontWeight = FontWeight.Bold))
                                Text("Physical cash handover at destination", style = LuxuryTypography.BodySubtle)
                            }
                            Text("CASH", style = LuxuryTypography.TitleGold)
                        }
                    }
                } else {
                    // Feature 13: Rate Driver (5 Stars)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        (1..5).forEach { star ->
                            IconButton(onClick = { selectedRating = star }) {
                                Text(
                                    text = if (star <= selectedRating) "★" else "☆",
                                    fontSize = 36.sp,
                                    color = LuxuryColors.ChampagneGold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

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

            Column {
                if (!paymentSettled) {
                    LuxuryButton(
                        text = if (selectedMethod == "UPI") "AUTHORIZE ₹$amountRupees VIA UPI" else "CONFIRM CASH SETTLEMENT",
                        isLoading = isProcessing,
                        onClick = {
                            isProcessing = true
                            if (selectedMethod == "UPI") {
                                val upiUri = Uri.parse("upi://pay?pa=luxurychauffeur@icici&pn=Luxury%20Ride&am=$amountRupees.00&cu=INR&tn=Ride-$rideId")
                                val upiIntent = Intent(Intent.ACTION_VIEW, upiUri)
                                try {
                                    context.startActivity(Intent.createChooser(upiIntent, "Pay via UPI App (GPay / PhonePe / Paytm)"))
                                } catch (_: Exception) {}
                            }
                            scope.launch {
                                try {
                                    ApiClient.getService().createPaymentIntent(
                                        PaymentIntentRequest(
                                            rideId = rideId,
                                            idempotencyKey = "pay_${rideId}_${System.currentTimeMillis()}"
                                        )
                                    )
                                } catch (_: Exception) {}
                                isProcessing = false
                                paymentSettled = true
                            }
                        }
                    )
                } else {
                    LuxuryButton(
                        text = "SUBMIT RATING & RETURN HOME",
                        isLoading = isProcessing,
                        onClick = {
                            isProcessing = true
                            scope.launch {
                                try {
                                    ApiClient.getService().rateDriver(
                                        rideId,
                                        com.luxury.chauffeur.customer.core.network.RateDriverRequest(
                                            rating = selectedRating,
                                            feedback = feedbackText
                                        )
                                    )
                                } catch (_: Exception) {}
                                isProcessing = false
                                onPaymentAndReviewCompleted()
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
