package com.vaisentra.mobility.driver.feature.fare

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.vaisentra.mobility.driver.components.*
import com.vaisentra.mobility.driver.data.DriverRepository
import com.vaisentra.mobility.driver.theme.*
import kotlinx.coroutines.launch

fun generateQrBitmap(content: String, size: Int = 512): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bmp
    } catch (e: Exception) {
        null
    }
}

/**
 * Screen 31: Trip Completed & Fare Calculation (GET /api/v1/drivers/financials/rides/{ride_id}/breakdown)
 */
@Composable
fun TripCompletedFareScreen(
    onNavigateBack: () -> Unit = {},
    onCollectCash: () -> Unit = {},
    onCollectUpi: () -> Unit = {},
    onFinishTrip: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val offer by DriverRepository.activeOffer.collectAsState()
    val rideId by DriverRepository.currentRideId.collectAsState()
    val breakdown by DriverRepository.rideBreakdown.collectAsState()
    var isFinishing by remember { mutableStateOf(false) }

    LaunchedEffect(rideId) {
        if (rideId.isNotBlank()) {
            DriverRepository.fetchRideBreakdown(rideId)
        }
    }

    val totalFare = breakdown?.fareBreakdown?.grossFareRupees
        ?.takeIf { it > 0.0 }
        ?: offer?.estimatedFareRupees?.takeIf { it > 0.0 }
    val driverGross = breakdown?.driverGrossEarningsRupees
        ?.takeIf { it > 0.0 }
        ?: offer?.estimatedDriverEarningsRupees?.takeIf { it > 0.0 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Trip Complete",
            subtitle = "Fare calculation & settlement",
            onBack = onNavigateBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                CockpitCard(glow = true) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("TOTAL FARE COLLECTIBLE", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (totalFare != null) "₹${"%.2f".format(totalFare)}" else "Fare Unavailable",
                            color = if (totalFare != null) EmeraldPrimary else PilotCrimson,
                            fontSize = if (totalFare != null) 36.sp else 22.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (driverGross != null) "Pilot Gross Earnings: ₹${"%.2f".format(driverGross)}" else "Gross Earnings: Pending Calculation",
                            color = if (driverGross != null) CockpitTextSecondary else PilotAmber,
                            fontSize = 12.sp
                        )
                        Text(
                            text = breakdown?.commissionCents?.let { amount -> "Commission tracked: ₹${"%.2f".format(amount / 100.0)}${breakdown?.commissionPercentage?.let { " (${it}%)" } ?: ""}" } ?: "Commission snapshot unavailable",
                            color = CockpitTextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CockpitButton(
                        text = if (totalFare != null) "COLLECT CASH (₹${"%.2f".format(totalFare)})" else "CANNOT COLLECT CASH (FARE MISSING)",
                        enabled = totalFare != null,
                        onClick = onCollectCash,
                        modifier = Modifier.fillMaxWidth()
                    )
                    CockpitButton(
                        text = "SHOW DYNAMIC UPI QR TO COMMUTER",
                        onClick = onCollectUpi,
                        modifier = Modifier.fillMaxWidth(),
                        isOutlined = true
                    )
                    CockpitButton(
                        text = if (isFinishing) "Completing..." else "Close & Return to Duty",
                        enabled = !isFinishing,
                        onClick = {
                            if (rideId.isNotBlank() && breakdown?.status != "COMPLETED") {
                                isFinishing = true
                                DriverRepository.finishTrip(rideId) { ok ->
                                    isFinishing = false
                                    if (ok) onFinishTrip()
                                    else onShowToast("Could not finish trip. Please retry.")
                                }
                            } else {
                                onFinishTrip()
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
 * Screen 32: Cash Collection Calculator (POST /api/v1/payments/cash-collect)
 */
@Composable
fun CashCollectionScreen(
    onNavigateBack: () -> Unit = {},
    onCashCollected: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val offer by DriverRepository.activeOffer.collectAsState()
    val rideId by DriverRepository.currentRideId.collectAsState()
    val breakdown by DriverRepository.rideBreakdown.collectAsState()

    LaunchedEffect(rideId) {
        if (rideId.isNotBlank()) {
            DriverRepository.fetchRideBreakdown(rideId)
        }
    }

    val totalBill = breakdown?.fareBreakdown?.grossFareRupees
        ?.takeIf { it > 0.0 }
        ?: offer?.estimatedFareRupees?.takeIf { it > 0.0 }
    var tenderedAmount by remember(totalBill) { mutableStateOf(totalBill?.let { "%.0f".format(it) } ?: "") }
    var isSubmitting by remember { mutableStateOf(false) }
    val tenderedVal = tenderedAmount.toDoubleOrNull() ?: 0.0
    val changeReturn = if (totalBill != null && tenderedVal >= totalBill) tenderedVal - totalBill else 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Cash Collection",
            subtitle = "Smart change calculator for cash payments",
            onBack = onNavigateBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CockpitCard(glow = true) {
                    Column {
                        Text("AMOUNT DUE FROM PASSENGER", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("₹${"%.2f".format(totalBill)}", color = CrimsonAlert, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }

                CockpitCard {
                    Text("TENDERED CASH PRESETS", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("90", "100", "200", "500").forEach { preset ->
                            Button(
                                onClick = { tenderedAmount = preset },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = CockpitSurfaceHighlight),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("₹$preset", color = CockpitTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                CockpitTextField(
                    value = tenderedAmount,
                    onValueChange = { tenderedAmount = it },
                    label = "Cash Given by Rider (₹)",
                    placeholder = "Enter amount"
                )

                CockpitCard {
                    Column {
                        Text("RETURN CHANGE TO COMMUTER", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("₹${"%.2f".format(changeReturn)}", color = EmeraldPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            CockpitButton(
                text = if (isSubmitting) "CONFIRMING..." else if (totalBill != null) "CONFIRM CASH RECEIVED (₹${"%.2f".format(totalBill)})" else "CANNOT COLLECT CASH (FARE MISSING)",
                enabled = !isSubmitting && totalBill != null && tenderedVal >= totalBill,
                onClick = {
                    if (rideId.isBlank()) {
                        onShowToast("No active ride ID.")
                        return@CockpitButton
                    }
                    isSubmitting = true
                    DriverRepository.collectCash(
                        rideId = rideId,
                        amountTendered = tenderedVal
                    ) { ok ->
                        isSubmitting = false
                        if (ok) {
                            onShowToast("Cash collected and reconciled!")
                            onCashCollected()
                        } else {
                            onShowToast("Failed to confirm cash collection.")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Screen 33: Digital UPI QR Settlement (POST /api/v1/payments/upi-checkout)
 */
@Composable
fun UpiSettlementScreen(
    onNavigateBack: () -> Unit = {},
    onPaymentConfirmed: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val offer by DriverRepository.activeOffer.collectAsState()
    val rideId by DriverRepository.currentRideId.collectAsState()
    val breakdown by DriverRepository.rideBreakdown.collectAsState()
    val totalBill = breakdown?.fareBreakdown?.grossFareRupees
        ?.takeIf { it > 0.0 }
        ?: offer?.estimatedFareRupees
        ?: 0.0
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Customer must complete payment through the in-app Razorpay checkout.") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(rideId) {
        if (rideId.isNotBlank()) DriverRepository.fetchRideBreakdown(rideId)
    }

    Column(
        modifier = Modifier.fillMaxSize().background(CockpitBackground).statusBarsPadding()
    ) {
        CockpitHeader(title = "UPI Payment Verification", subtitle = "Only backend-confirmed capture can settle this ride", onBack = onNavigateBack)
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CockpitCard(glow = true) {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("FARE TO VERIFY", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("₹${"%.2f".format(totalBill)}", color = EmeraldPrimary, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(14.dp))
                    Text("Direct UPI QR collection is disabled until the payment provider can confirm the exact merchant capture. Do not accept a screenshot or customer-side success screen as proof.", color = CockpitTextMuted, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(statusMessage, color = ElectricCyan, fontSize = 12.sp)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                CockpitButton(
                    text = if (isLoading) "CHECKING..." else "VERIFY CUSTOMER PAYMENT",
                    enabled = !isLoading && rideId.isNotBlank(),
                    onClick = {
                        isLoading = true
                        scope.launch {
                            val result = DriverRepository.verifyRideUpiPaymentStatus(rideId)
                            isLoading = false
                            val payment = result.getOrNull()
                            if (result.isFailure || payment == null) {
                                statusMessage = "Payment status could not be verified. Do not mark this ride as paid."
                                onShowToast("Verification failed: ${result.exceptionOrNull()?.message ?: "No authoritative status"}")
                            } else {
                                when {
                                    payment.settlementConfirmed && payment.gatewayVerified -> {
                                        statusMessage = "Backend confirms captured payment."
                                        onShowToast("Backend confirmed captured payment.")
                                        onPaymentConfirmed()
                                    }
                                    payment.refundState == "FULLY_REFUNDED" || payment.status == "REFUNDED" -> {
                                        statusMessage = "This payment was fully refunded. It is not a settled fare."
                                        onShowToast("Payment is refunded. Admin review is required.")
                                    }
                                    payment.refundState == "PARTIALLY_REFUNDED" || payment.status == "PARTIALLY_REFUNDED" -> {
                                        statusMessage = "This payment has a partial refund. Do not close fare settlement as fully paid."
                                        onShowToast("Partial refund detected. Admin review is required.")
                                    }
                                    else -> {
                                        statusMessage = "No confirmed capture yet. Wait for backend confirmation or use the supported cash collection flow."
                                        onShowToast("Payment is not captured yet.")
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                CockpitButton(
                    text = "Back to supported settlement options",
                    onClick = onNavigateBack,
                    modifier = Modifier.fillMaxWidth(),
                    isOutlined = true
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun UpiSettlementScreenPreview() {
    DriverGoTheme {
        UpiSettlementScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun CashCollectionScreenPreview() {
    DriverGoTheme {
        CashCollectionScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun TripCompletedFareScreenPreview() {
    DriverGoTheme {
        TripCompletedFareScreen()
    }
}