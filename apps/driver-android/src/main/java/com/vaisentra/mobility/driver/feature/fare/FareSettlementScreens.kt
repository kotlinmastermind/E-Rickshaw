package com.vaisentra.mobility.driver.feature.fare

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.driver.components.*
import com.vaisentra.mobility.driver.theme.*

/**
 * Screen 31: Trip Completed & Fare Calculation (itemized fare calculation)
 */
@Composable
fun TripCompletedFareScreen(
    onNavigateBack: () -> Unit = {},
    onCollectCash: () -> Unit = {},
    onCollectUpi: () -> Unit = {},
    onFinishTrip: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Trip Complete",
            subtitle = "Itemized fare calculation & settlement",
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
                        Text("₹82.50", color = EmeraldPrimary, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(4.dp))
                        Text("Pilot Net Pay: ₹74.25 (Commission 10%: ₹8.25)", color = CockpitTextSecondary, fontSize = 12.sp)
                    }
                }
            }

            item {
                CockpitCard {
                    Text("ITEMIZED FARE BREAKDOWN", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    FareRow("Base Fare (First 1.5 km)", "₹20.00")
                    FareRow("Distance Fare (2.7 km × ₹10/km)", "₹27.00")
                    FareRow("Ride Time Fare (14 min × ₹1/min)", "₹14.00")
                    FareRow("Waiting Time (4 min over grace)", "₹6.00")
                    FareRow("Surge Multiplier (1.2x Peak Demand)", "₹15.50")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = CockpitBorder)
                    FareRow("Gross Rider Bill", "₹82.50", isBold = true, textColor = CockpitTextPrimary)
                    FareRow("Platform Tech Fee (10%)", "-₹8.25", textColor = CrimsonAlert)
                    FareRow("Pilot Final Credit", "₹74.25", isBold = true, textColor = EmeraldPrimary)
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CockpitButton(
                        text = "COLLECT CASH (₹82.50)",
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
                        text = "Close & Return to Duty",
                        onClick = onFinishTrip,
                        modifier = Modifier.fillMaxWidth(),
                        isOutlined = true
                    )
                }
            }
        }
    }
}

/**
 * Screen 32: Cash Collection Calculator (tendered cash, change computation)
 */
@Composable
fun CashCollectionScreen(
    onNavigateBack: () -> Unit = {},
    onCashCollected: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val totalBill = 82.50
    var tenderedAmount by remember { mutableStateOf("100") }
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("AMOUNT DUE FROM PASSENGER", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("₹82.50", color = CrimsonAlert, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                        }
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("RETURN CHANGE TO COMMUTER", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("₹${String.format("%.2f", changeReturn)}", color = EmeraldPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            CockpitButton(
                text = "CONFIRM CASH RECEIVED (₹82.50)",
                onClick = {
                    onShowToast("Cash collected. ₹74.25 credited to wallet balance")
                    onCashCollected()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Screen 33: Digital UPI QR Settlement (passenger scans pilot's dynamic UPI QR)
 */
@Composable
fun UpiSettlementScreen(
    onNavigateBack: () -> Unit = {},
    onPaymentConfirmed: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
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
                    Text("₹82.50", color = EmeraldPrimary, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(14.dp))

                    // QR Code Simulation
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.QrCode2, contentDescription = null, tint = Color.Black, modifier = Modifier.size(150.dp))
                            Text("UPI // RAZORPAY AUTOPAY", color = Color.DarkGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("UPI ID: vaisentra.mobility@okhdfcbank", color = CockpitTextSecondary, fontSize = 12.sp)
                    Text("Auto-verifying payment gateway webhook...", color = ElectricCyan, fontSize = 11.sp)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                CockpitButton(
                    text = "VERIFY PAYMENT RECEIVED",
                    onClick = {
                        onShowToast("Payment of ₹82.50 verified via Razorpay webhook!")
                        onPaymentConfirmed()
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

@Composable
private fun FareRow(
    label: String,
    value: String,
    isBold: Boolean = false,
    textColor: Color = CockpitTextSecondary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = CockpitTextSecondary, fontSize = 12.sp)
        Text(value, color = textColor, fontSize = 13.sp, fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal)
    }
}

