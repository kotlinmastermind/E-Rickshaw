package com.vaisentra.mobility.rider.feature.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.rider.components.*
import com.vaisentra.mobility.rider.data.RiderRepository
import com.vaisentra.mobility.rider.theme.*

// Screen 15: Payment Method & UPI Picker
@Composable
fun PaymentMethodPickerScreen(onNavigate: (String) -> Unit) {
    var selectedMethod by remember { mutableStateOf("UPI") }

    val methods = listOf(
        Triple("UPI", "Instant UPI (Google Pay, PhonePe, Paytm)", "⚡"),
        Triple("WALLET", "RiderGo Cashless Wallet (₹485.00 Available)", "👛"),
        Triple("CARD", "Credit / Debit Card (HDFC **** 4812)", "💳"),
        Triple("CASH", "Cash to Pilot upon Arrival", "💵")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
    ) {
        NeoHeader(title = "Payment Methods", subtitle = "Razorpay & Cashless Settlement", onBack = { onNavigate("fare_matrix") })
        Spacer(modifier = Modifier.height(16.dp))

        methods.forEach { (code, label, icon) ->
            val isSelected = selectedMethod == code
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) SurfaceCard else SurfaceElevated)
                    .border(1.5.dp, if (isSelected) BorderFocus else BorderSubtle, RoundedCornerShape(16.dp))
                    .clickable { selectedMethod = code }
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = icon, fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(text = label, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (isSelected) {
                        NeoBadge(text = "PRIMARY", backgroundColor = SapphireGlow, textColor = ElectricCyan)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        NeoButton(text = "Confirm Payment Method", onClick = {
            DialogController.showToast("Selected $selectedMethod as payment method.")
            onNavigate("fare_matrix")
        })
    }
}

// Screen 16: Cashless Wallet Hub
@Composable
fun CashlessWalletHubScreen(onNavigate: (String) -> Unit) {
    val wallet by RiderRepository.wallet.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        NeoHeader(title = "RiderGo Wallet", subtitle = "Zero-commission commuter balance", onBack = { onNavigate("map_home") })
        Spacer(modifier = Modifier.height(16.dp))

        NeoCard(
            borderColor = BorderFocus,
            backgroundColor = SurfaceCard
        ) {
            Text(text = "Available Wallet Balance", color = TextMuted, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "₹${wallet.balanceRupees.toInt()}.00",
                color = ElectricCyan,
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(14.dp))
            NeoBadge(text = "100% SECURE ESCROW IN SUPABASE", backgroundColor = EmeraldGlow, textColor = EmeraldGreen)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Quick 1-Tap UPI Top-Up", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(250.0, 500.0, 1000.0).forEach { amt ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceElevated)
                        .clickable {
                            RiderRepository.topupWalletBalance(amt)
                            DialogController.showToast("₹${amt.toInt()} added via Razorpay UPI!")
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "+₹${amt.toInt()}", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        NeoCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Auto-Topup Standing Order", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Auto-recharge when balance falls below ₹100", color = TextMuted, fontSize = 11.sp)
                }
                Text(
                    text = "Configure →",
                    color = ElectricCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onNavigate("wallet_auto_topup") }
                )
            }
        }
    }
}

// Screen 17: Wallet Auto-Topup Rules
@Composable
fun WalletAutoTopupScreen(onNavigate: (String) -> Unit) {
    var threshold by remember { mutableStateOf("100") }
    var rechargeAmount by remember { mutableStateOf("500") }
    var isEnabled by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
    ) {
        NeoHeader(title = "Auto-Topup Rules", subtitle = "Bank recurring UPI instruction", onBack = { onNavigate("wallet_hub") })
        Spacer(modifier = Modifier.height(16.dp))

        NeoCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Enable Auto-Reload", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
            }
            Spacer(modifier = Modifier.height(16.dp))
            NeoTextField(value = threshold, onValueChange = { threshold = it }, placeholder = "₹100", label = "Trigger When Balance Falls Below (₹)")
            Spacer(modifier = Modifier.height(12.dp))
            NeoTextField(value = rechargeAmount, onValueChange = { rechargeAmount = it }, placeholder = "₹500", label = "Automatic Reload Amount (₹)")
            Spacer(modifier = Modifier.height(20.dp))
            NeoButton(text = "Save Auto-Topup Mandate", onClick = {
                DialogController.showToast("Standing mandate saved in Supabase!")
                onNavigate("wallet_hub")
            })
        }
    }
}

// Screen 35: Split Fare with Co-Riders
@Composable
fun SplitFareScreen(onNavigate: (String) -> Unit) {
    var phone1 by remember { mutableStateOf("+91 98765 43210") }
    var phone2 by remember { mutableStateOf("+91 99887 76655") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
    ) {
        NeoHeader(title = "Split Ride Invoice", subtitle = "Automated UPI share request", onBack = { onNavigate("map_home") })
        Spacer(modifier = Modifier.height(16.dp))

        NeoCard {
            Text(text = "Trip Total: ₹129.04", color = ElectricCyan, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = "Splitting 3 ways = ₹43.01 per passenger", color = TextMuted, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(16.dp))
            NeoTextField(value = phone1, onValueChange = { phone1 = it }, placeholder = "Passenger 1 Phone", label = "Co-Passenger 1")
            Spacer(modifier = Modifier.height(10.dp))
            NeoTextField(value = phone2, onValueChange = { phone2 = it }, placeholder = "Passenger 2 Phone", label = "Co-Passenger 2")
            Spacer(modifier = Modifier.height(20.dp))
            NeoButton(text = "Dispatch Split Payment Link", onClick = {
                DialogController.show(RiderPopupType.SPLIT_FARE)
            })
        }
    }
}

