package com.vaisentra.mobility.driver.feature.fare

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.driver.components.*
import com.vaisentra.mobility.driver.data.DriverRepository
import com.vaisentra.mobility.driver.theme.*
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    var isFinishing by remember { mutableStateOf(false) }

    LaunchedEffect(rideId) {
        if (rideId.isNotBlank()) {
            DriverRepository.fetchRideBreakdown(rideId)
        }
    }

    val totalFare = breakdown?.fareBreakdown?.grossFareRupees
        ?.takeIf { it > 0.0 }
        ?: offer?.estimatedFareRupees
        ?: 0.0
    val netPay = breakdown?.netDriverEarningsRupees
        ?.takeIf { it > 0.0 }
        ?: offer?.estimatedDriverEarningsRupees
        ?: (totalFare * 0.8)

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
                        Text("₹${"%.2f".format(totalFare)}", color = EmeraldPrimary, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(4.dp))
                        Text("Pilot Net Pay: ₹${"%.2f".format(netPay)}", color = CockpitTextSecondary, fontSize = 12.sp)
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CockpitButton(
                        text = "COLLECT CASH (₹${"%.2f".format(totalFare)})",
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
        ?: offer?.estimatedFareRupees
        ?: 0.0
    var tenderedAmount by remember { mutableStateOf("100") }
    var isSubmitting by remember { mutableStateOf(false) }
    val tenderedVal = tenderedAmount.toDoubleOrNull() ?: 0.0
    val changeReturn = maxOf(0.0, tenderedVal - totalBill)

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
                text = if (isSubmitting) "CONFIRMING..." else "CONFIRM CASH RECEIVED (₹${"%.2f".format(totalBill)})",
                enabled = !isSubmitting,
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
    var upiIntentUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(rideId) {
        if (rideId.isNotBlank()) {
            DriverRepository.fetchRideBreakdown(rideId)
            val res = DriverRepository.generateDynamicUpi(rideId, totalBill.coerceAtLeast(1.0))
            upiIntentUrl = res.getOrNull()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Dynamic UPI QR",
            subtitle = "Direct instant settlement to your bank account",
            onBack = onNavigateBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CockpitCard(glow = true) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("AMOUNT TO PAY", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("₹${"%.2f".format(totalBill)}", color = EmeraldPrimary, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(14.dp))

                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.QrCode2, contentDescription = null, tint = Color.Black, modifier = Modifier.size(150.dp))
                            Text(
                                if (upiIntentUrl != null) "UPI READY" else "GENERATING QR...",
                                color = Color.DarkGray,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(upiIntentUrl ?: "Waiting for UPI intent...", color = ElectricCyan, fontSize = 11.sp, maxLines = 1)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                CockpitButton(
                    text = if (isLoading) "REFRESHING..." else "GENERATE / VERIFY UPI",
                    enabled = !isLoading,
                    onClick = {
                        if (rideId.isBlank()) {
                            onShowToast("No active ride ID.")
                            return@CockpitButton
                        }
                        isLoading = true
                        scope.launch {
                            val res = DriverRepository.generateDynamicUpi(rideId, totalBill.coerceAtLeast(1.0))
                            isLoading = false
                            if (res.isSuccess) {
                                upiIntentUrl = res.getOrNull()
                                onShowToast("UPI checkout ready!")
                                onPaymentConfirmed()
                            } else {
                                onShowToast("UPI error: ${res.exceptionOrNull()?.message}")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                CockpitButton(
                    text = "Switch to Cash Collection",
                    onClick = onNavigateBack,
                    modifier = Modifier.fillMaxWidth(),
                    isOutlined = true
                )
            }
        }
    }
}