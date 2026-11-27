package com.vaisentra.mobility.rider.feature.giftcard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.rider.components.*
import com.vaisentra.mobility.rider.data.*
import com.vaisentra.mobility.rider.theme.*
import kotlinx.coroutines.launch

// Helper: Maps theme string to rich visual Brush gradients
fun getThemeGradient(designId: String): Brush {
    return when (designId.uppercase()) {
        "ROYAL_GOLD" -> Brush.linearGradient(listOf(Color(0xFF2C2208), Color(0xFF5A4410), Color(0xFF1A1404)))
        "SAPPHIRE_ELEGANCE" -> Brush.linearGradient(listOf(Color(0xFF071C35), Color(0xFF0F3B70), Color(0xFF030E1C)))
        "EMERALD_GREEN" -> Brush.linearGradient(listOf(Color(0xFF08281B), Color(0xFF145338), Color(0xFF04140D)))
        "DIWALI_FESTIVE" -> Brush.linearGradient(listOf(Color(0xFF3B0B0B), Color(0xFF6B1B1B), Color(0xFF1C0505)))
        "METRO_COMMUTE" -> Brush.linearGradient(listOf(Color(0xFF08233D), Color(0xFF0E477D), Color(0xFF03101E)))
        else -> Brush.linearGradient(listOf(Color(0xFF202025), Color(0xFF32323A), Color(0xFF141418)))
    }
}

fun getThemeAccent(designId: String): Color {
    return when (designId.uppercase()) {
        "ROYAL_GOLD" -> Color(0xFFFFD700)
        "SAPPHIRE_ELEGANCE" -> Color(0xFF00E5FF)
        "EMERALD_GREEN" -> Color(0xFF00E676)
        "DIWALI_FESTIVE" -> Color(0xFFFF9100)
        "METRO_COMMUTE" -> Color(0xFF40C4FF)
        else -> Color(0xFF00E5FF)
    }
}

// -----------------------------------------------------------------------------
// Visual Digital Gift Card Preview Component
// -----------------------------------------------------------------------------
@Composable
fun DigitalGiftCardVisual(
    amountRupees: Double,
    cardDesign: String,
    recipientName: String?,
    senderName: String?,
    maskedCode: String?,
    status: String = "ACTIVE",
    expiryText: String? = "Valid for 1 Year",
    modifier: Modifier = Modifier
) {
    val gradient = getThemeGradient(cardDesign)
    val accent = getThemeAccent(cardDesign)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(gradient)
            .border(1.5.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
            .padding(18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Row: Brand & Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🎁", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "RiderGo PASS",
                        color = TextWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    )
                }
                NeoBadge(
                    text = status.uppercase(),
                    backgroundColor = if (status == "ACTIVE") EmeraldGlow else CrimsonGlow,
                    textColor = if (status == "ACTIVE") EmeraldGreen else CrimsonRed
                )
            }

            // Middle Row: Amount & Recipient
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = if (!recipientName.isNullOrBlank()) "FOR $recipientName" else "COMMUTER CREDIT",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "₹${"%.0f".format(amountRupees)}",
                        color = accent,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                if (!senderName.isNullOrBlank()) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "FROM", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(text = senderName, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Footer Row: Masked Code & Expiry
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = maskedCode ?: "GC-••••-••••-••••",
                    color = TextWhite.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = expiryText ?: "Valid for 1 Year",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Screen 1: Gift Cards Home Hub
// -----------------------------------------------------------------------------
@Composable
fun GiftCardsHomeScreen(onNavigate: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val wallet by RiderRepository.wallet.collectAsState()
    val myCards by RiderRepository.myGiftCards.collectAsState()
    val config by RiderRepository.giftCardConfig.collectAsState()

    var activeTab by remember { mutableStateOf("ALL") }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(activeTab) {
        isLoading = true
        RiderRepository.fetchGiftCardConfig()
        RiderRepository.fetchWalletBalance()
        RiderRepository.fetchMyGiftCards(activeTab)
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        NeoHeader(
            title = "Premium Gift Cards",
            subtitle = "Sovereign Commuter Credits",
            onBack = { onNavigate("wallet_hub") }
        )
        Spacer(modifier = Modifier.height(14.dp))

        // Hero Digital Card Preview
        DigitalGiftCardVisual(
            amountRupees = 1000.0,
            cardDesign = "ROYAL_GOLD",
            recipientName = "A Cherished Friend",
            senderName = "You",
            maskedCode = "GC-9A2E-••••-7F10",
            status = "ACTIVE",
            expiryText = "365 Days Validity"
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Quick CTAs: Buy & Redeem
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceCard)
                    .border(1.dp, BorderFocus, RoundedCornerShape(16.dp))
                    .clickable { onNavigate("gift_cards_buy") }
                    .padding(14.dp)
            ) {
                Column {
                    Text(text = "🎁", fontSize = 24.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Buy Gift Card", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Instant digital delivery", color = TextMuted, fontSize = 11.sp)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceCard)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                    .clickable { onNavigate("gift_cards_redeem") }
                    .padding(14.dp)
            ) {
                Column {
                    Text(text = "⚡", fontSize = 24.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Redeem Card", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Credit to wallet balance", color = TextMuted, fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Current Wallet Balance & Quick Balance Inquiry
        NeoCard(backgroundColor = SurfaceDark) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Redeemed Balance in Wallet", color = TextMuted, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = wallet?.displayBalanceRupees?.let { "₹${it.toInt()}.00" } ?: "Unavailable",
                        color = ElectricCyan,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                NeoButton(
                    text = "Check Balance",
                    onClick = { onNavigate("gift_cards_redeem") },
                    backgroundColor = SurfaceElevated
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Section: My Gift Cards
        Text(text = "My Gift Cards & Vouchers", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))

        // Filter Chips Row
        val tabs = listOf("ALL" to "All", "ACTIVE" to "Active", "REDEEMED" to "Redeemed", "PURCHASED" to "Sent", "RECEIVED" to "Received")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabs.forEach { (key, label) ->
                val isSelected = activeTab == key
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) SapphirePrimary else SurfaceElevated)
                        .clickable { activeTab = key }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) TextWhite else TextMuted,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Cards List / Empty State
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = ElectricCyan)
            }
        } else if (myCards.isEmpty()) {
            NeoCard(backgroundColor = SurfaceDark) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "🎟️", fontSize = 38.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = "No Gift Cards in this category", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Purchase a new card for family or redeem a received voucher code.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    NeoButton(
                        text = "Buy a Gift Card Now",
                        onClick = { onNavigate("gift_cards_buy") }
                    )
                }
            }
        } else {
            myCards.forEach { card ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceCard)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                        .clickable { onNavigate("gift_cards_detail/${card.giftCardId}") }
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(getThemeGradient(card.cardDesign)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "🎁", fontSize = 20.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = card.maskedCode,
                                    color = TextWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (card.isPurchasedByMe) "Sent to ${card.recipientName ?: "Friend"}" else "From ${card.senderName ?: "Commuter"}",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "₹${"%.0f".format(card.remainingValueRupees)}",
                                color = getThemeAccent(card.cardDesign),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            NeoBadge(
                                text = card.status,
                                backgroundColor = if (card.status == "ACTIVE") EmeraldGlow else CrimsonGlow,
                                textColor = if (card.status == "ACTIVE") EmeraldGreen else CrimsonRed
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

// -----------------------------------------------------------------------------
// Screen 2: Buy Gift Card Screen
// -----------------------------------------------------------------------------
@Composable
fun BuyGiftCardScreen(onNavigate: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val config by RiderRepository.giftCardConfig.collectAsState()

    var selectedDesign by remember { mutableStateOf("ROYAL_GOLD") }
    var selectedAmount by remember { mutableStateOf(1000) }
    var customAmount by remember { mutableStateOf("") }
    var recipientName by remember { mutableStateOf("") }
    var recipientContact by remember { mutableStateOf("") }
    var senderName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Wishing you smooth journeys and royal comfort!") }
    var deliveryMethod by remember { mutableStateOf("IN_APP") }
    var paymentMethod by remember { mutableStateOf("UPI") }

    var isSubmitting by remember { mutableStateOf(false) }
    var purchaseSuccessData by remember { mutableStateOf<GiftCardPurchaseResponse?>(null) }
    val context = LocalContext.current

    val finalAmount = customAmount.toIntOrNull() ?: selectedAmount

    LaunchedEffect(Unit) {
        RiderRepository.fetchGiftCardConfig()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        NeoHeader(
            title = "Buy Gift Card",
            subtitle = "Customizable Digital Mobility Pass",
            onBack = { onNavigate("gift_cards_hub") }
        )
        Spacer(modifier = Modifier.height(14.dp))

        // Live Interactive Preview
        DigitalGiftCardVisual(
            amountRupees = finalAmount.toDouble(),
            cardDesign = selectedDesign,
            recipientName = recipientName.ifBlank { "Recipient" },
            senderName = senderName.ifBlank { "You" },
            maskedCode = "GC-••••-••••-PREV",
            status = "ACTIVE"
        )

        Spacer(modifier = Modifier.height(18.dp))

        // 1. Theme Selection
        Text(text = "Choose Theme & Visual Style", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        val designs = listOf(
            Triple("ROYAL_GOLD", "Royal Gold", "👑"),
            Triple("SAPPHIRE_ELEGANCE", "Sapphire", "💎"),
            Triple("EMERALD_GREEN", "Eco Green", "🌱"),
            Triple("DIWALI_FESTIVE", "Festive", "🪔"),
            Triple("METRO_COMMUTE", "Metro Cyan", "⚡")
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            designs.forEach { (id, name, icon) ->
                val isSelected = selectedDesign == id
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) SurfaceCard else SurfaceElevated)
                        .border(1.5.dp, if (isSelected) getThemeAccent(id) else BorderSubtle, RoundedCornerShape(14.dp))
                        .clickable { selectedDesign = id }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = icon, fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = name, color = if (isSelected) TextWhite else TextMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 2. Amount Selection
        Text(text = "Select Gift Amount", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        val presetList = config?.presetDenominationsRupees ?: listOf(250, 500, 1000, 2000, 5000)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presetList.forEach { amt ->
                val isSelected = selectedAmount == amt && customAmount.isBlank()
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) SapphirePrimary else SurfaceElevated)
                        .clickable {
                            selectedAmount = amt
                            customAmount = ""
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "₹$amt",
                        color = if (isSelected) TextWhite else TextMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        NeoTextField(
            value = customAmount,
            onValueChange = { customAmount = it },
            placeholder = "Or enter custom amount (₹100 - ₹10,000)",
            label = "Custom Amount (₹)",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Spacer(modifier = Modifier.height(18.dp))

        // 3. Recipient Details
        Text(text = "Recipient & Personal Message", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        NeoTextField(
            value = recipientName,
            onValueChange = { recipientName = it },
            placeholder = "e.g. Priya Sharma",
            label = "Recipient's Name"
        )
        Spacer(modifier = Modifier.height(10.dp))
        NeoTextField(
            value = recipientContact,
            onValueChange = { recipientContact = it },
            placeholder = "e.g. +91 98765 43210 or name@example.com",
            label = "Recipient Phone or Email"
        )
        Spacer(modifier = Modifier.height(10.dp))
        NeoTextField(
            value = senderName,
            onValueChange = { senderName = it },
            placeholder = "Your name / pseudonym",
            label = "From (Sender Name)"
        )
        Spacer(modifier = Modifier.height(10.dp))
        NeoTextField(
            value = message,
            onValueChange = { message = it },
            placeholder = "Personal greetings message...",
            label = "Personal Message"
        )

        Spacer(modifier = Modifier.height(18.dp))

        // 4. Delivery Method
        Text(text = "Delivery Channel", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("IN_APP" to "Instant App", "SMS" to "SMS Text", "EMAIL" to "Email").forEach { (method, label) ->
                val isSelected = deliveryMethod == method
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) SapphirePrimary else SurfaceElevated)
                        .clickable { deliveryMethod = method }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = label, color = if (isSelected) TextWhite else TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 5. Payment Breakdown
        NeoCard(backgroundColor = SurfaceCard) {
            Text(text = "Bill Breakdown", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Gift Card Value", color = TextMuted, fontSize = 13.sp)
                Text(text = "₹$finalAmount.00", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Convenience Fee", color = TextMuted, fontSize = 13.sp)
                Text(text = "FREE", color = EmeraldGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "GST (Prepaid Instrument)", color = TextMuted, fontSize = 13.sp)
                Text(text = "₹0.00", color = TextWhite, fontSize = 13.sp)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = BorderSubtle)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Total Payable Amount", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(text = "₹$finalAmount.00", color = ElectricCyan, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Submit Button
        NeoButton(
            text = if (isSubmitting) "Processing Secure Payment..." else "Pay ₹$finalAmount & Issue Gift Card",
            enabled = !isSubmitting && finalAmount >= 100 && finalAmount <= 10000,
            onClick = {
                val isEmail = recipientContact.contains("@")
                val isPhone = !isEmail && recipientContact.isNotBlank()

                val req = GiftCardPurchaseRequest(
                    amountCents = finalAmount * 100,
                    cardDesign = selectedDesign,
                    recipientName = recipientName.ifBlank { null },
                    recipientEmail = if (isEmail) recipientContact else null,
                    recipientPhone = if (isPhone) recipientContact else null,
                    senderName = senderName.ifBlank { null },
                    message = message.ifBlank { null },
                    deliveryMethod = deliveryMethod,
                    paymentMethod = paymentMethod
                )

                isSubmitting = true
                scope.launch {
                    val res = RiderRepository.purchaseGiftCard(req)
                    isSubmitting = false
                    res.onSuccess { data ->
                        purchaseSuccessData = data
                        DialogController.showToast("Gift Card Issued Successfully!")
                    }.onFailure { err ->
                        DialogController.showToast("Purchase Failed: ${err.message}")
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(40.dp))
    }

    // Success Modal Layer
    purchaseSuccessData?.let { data ->
        AlertDialog(
            onDismissRequest = {
                purchaseSuccessData = null
                onNavigate("gift_cards_hub")
            },
            title = {
                Text(text = "🎉 Gift Card Issued!", color = TextWhite, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(text = "Your digital gift card code is ready to share:", color = TextMuted, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceDark)
                            .border(1.dp, BorderFocus, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = data.code,
                                color = ElectricCyan,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 2.sp
                            )
                            if (!data.pin.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "PIN: ${data.pin}", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = "Value: ₹${data.initialValueRupees.toInt()} • Valid for 365 Days", color = TextMuted, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Gift Card Code", data.code))
                    DialogController.showToast("Code copied to clipboard!")
                    purchaseSuccessData = null
                    onNavigate("gift_cards_hub")
                }) {
                    Text("Copy Code & Close", color = ElectricCyan, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

// -----------------------------------------------------------------------------
// Screen 3: Redeem Gift Card Screen
// -----------------------------------------------------------------------------
@Composable
fun RedeemGiftCardScreen(onNavigate: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var voucherCode by remember { mutableStateOf("") }
    var pinCode by remember { mutableStateOf("") }
    var isChecking by remember { mutableStateOf(false) }
    var isRedeeming by remember { mutableStateOf(false) }

    var balanceCheckResult by remember { mutableStateOf<GiftCardBalanceResponse?>(null) }
    var redeemSuccessResult by remember { mutableStateOf<GiftCardRedeemResponse?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        NeoHeader(
            title = "Redeem Gift Card",
            subtitle = "Claim Commuter Wallet Credits",
            onBack = { onNavigate("gift_cards_hub") }
        )
        Spacer(modifier = Modifier.height(16.dp))

        NeoCard(backgroundColor = SurfaceCard) {
            Text(text = "Enter Gift Card Code", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Enter the 16-character code (e.g. GC-9A2E-8B3F-7K2L) printed on your voucher or sent via SMS/Email.",
                color = TextMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                NeoTextField(
                    value = voucherCode,
                    onValueChange = { voucherCode = it.uppercase() },
                    placeholder = "GC-XXXX-YYYY-ZZZZ",
                    label = "Gift Card Code",
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                NeoButton(
                    text = "Paste",
                    backgroundColor = SurfaceElevated,
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                        if (!clip.isNullOrBlank()) {
                            voucherCode = clip.trim().uppercase()
                            DialogController.showToast("Pasted from clipboard")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            NeoTextField(
                value = pinCode,
                onValueChange = { pinCode = it },
                placeholder = "4-digit security PIN (if applicable)",
                label = "Security PIN (Optional)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Check Balance Button
                NeoButton(
                    text = if (isChecking) "Checking..." else "Check Balance",
                    enabled = !isChecking && voucherCode.length >= 8,
                    modifier = Modifier.weight(1f),
                    backgroundColor = SurfaceElevated,
                    onClick = {
                        isChecking = true
                        scope.launch {
                            val res = RiderRepository.checkGiftCardBalance(voucherCode, pinCode.ifBlank { null })
                            isChecking = false
                            res.onSuccess { data ->
                                balanceCheckResult = data
                                DialogController.showToast("Available Balance: ₹${data.remainingValueRupees.toInt()}")
                            }.onFailure { err ->
                                balanceCheckResult = null
                                DialogController.showToast(err.message ?: "Invalid code")
                            }
                        }
                    }
                )

                // Redeem Button
                NeoButton(
                    text = if (isRedeeming) "Claiming..." else "Redeem Now",
                    enabled = !isRedeeming && voucherCode.length >= 8,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        isRedeeming = true
                        scope.launch {
                            val res = RiderRepository.redeemGiftCard(voucherCode, pinCode.ifBlank { null })
                            isRedeeming = false
                            res.onSuccess { data ->
                                redeemSuccessResult = data
                                DialogController.showToast("₹${data.redeemedAmountRupees.toInt()} added to your wallet!")
                            }.onFailure { err ->
                                DialogController.showToast(err.message ?: "Redemption failed")
                            }
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Balance Check Result Card
        balanceCheckResult?.let { info ->
            NeoCard(
                borderColor = getThemeAccent(info.cardDesign),
                backgroundColor = SurfaceDark
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Card Status: ${info.status}", color = TextMuted, fontSize = 12.sp)
                        Text(text = info.maskedCode, color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        if (!info.senderName.isNullOrBlank()) {
                            Text(text = "Gifted by ${info.senderName}", color = TextMuted, fontSize = 11.sp)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "Available Balance", color = TextMuted, fontSize = 11.sp)
                        Text(
                            text = "₹${info.remainingValueRupees.toInt()}.00",
                            color = getThemeAccent(info.cardDesign),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Terms info box
        NeoCard(backgroundColor = SurfaceDark) {
            Text(text = "Redemption Rules", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "• Once redeemed, credits are permanently bound to your commuter wallet.\n" +
                        "• Balance never expires after redemption.\n" +
                        "• Applicable to all vehicle classes across Delhi NCR, Mumbai & Bengaluru.\n" +
                        "• Unused card values cannot be refunded to bank accounts under RBI guidelines.",
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(40.dp))
    }

    // Redeem Celebration Modal
    redeemSuccessResult?.let { data ->
        AlertDialog(
            onDismissRequest = {
                redeemSuccessResult = null
                onNavigate("gift_cards_hub")
            },
            title = {
                Text(text = "🎊 Credit Added to Wallet!", color = TextWhite, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(text = "You have successfully redeemed:", color = TextMuted, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "+₹${data.redeemedAmountRupees.toInt()}.00",
                        color = EmeraldGreen,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "New Wallet Balance: ₹${data.newWalletBalanceRupees.toInt()}.00",
                        color = TextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!data.message.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = "\"${data.message}\"", color = ElectricCyan, fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    redeemSuccessResult = null
                    onNavigate("gift_cards_hub")
                }) {
                    Text("Go to My Gift Cards", color = ElectricCyan, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

// -----------------------------------------------------------------------------
// Screen 4: Gift Card Details & Transaction Ledger Screen
// -----------------------------------------------------------------------------
@Composable
fun GiftCardDetailsScreen(
    giftCardId: String,
    onNavigate: (String) -> Unit
) {
    var cardDetails by remember { mutableStateOf<GiftCardDetailDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showUnmaskedCode by remember { mutableStateOf(false) }

    val context = LocalContext.current

    LaunchedEffect(giftCardId) {
        isLoading = true
        val res = RiderRepository.fetchGiftCardDetails(giftCardId)
        isLoading = false
        res.onSuccess {
            cardDetails = it
        }.onFailure {
            errorMessage = it.message ?: "Failed to load details"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        NeoHeader(
            title = "Gift Card Details",
            subtitle = "Transaction Ledger & Voucher State",
            onBack = { onNavigate("gift_cards_hub") }
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ElectricCyan)
            }
        } else if (errorMessage != null || cardDetails == null) {
            NeoCard(backgroundColor = SurfaceDark) {
                Text(text = "Error Loading Card", color = CrimsonRed, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = errorMessage ?: "Card not found", color = TextMuted, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(14.dp))
                NeoButton(text = "Back to Gift Cards", onClick = { onNavigate("gift_cards_hub") })
            }
        } else {
            val card = cardDetails!!

            // Card Visual
            DigitalGiftCardVisual(
                amountRupees = card.remainingValueRupees,
                cardDesign = card.cardDesign,
                recipientName = card.recipientName,
                senderName = card.senderName,
                maskedCode = if (showUnmaskedCode && !card.unmaskedCode.isNullOrBlank()) card.unmaskedCode else card.maskedCode,
                status = card.status
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Code Reveal / Copy Card
            NeoCard(backgroundColor = SurfaceCard) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "VOUCHER CODE", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (showUnmaskedCode && !card.unmaskedCode.isNullOrBlank()) card.unmaskedCode else card.maskedCode,
                            color = TextWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    if (!card.unmaskedCode.isNullOrBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { showUnmaskedCode = !showUnmaskedCode }) {
                                Text(if (showUnmaskedCode) "Hide" else "Reveal", color = ElectricCyan, fontSize = 12.sp)
                            }
                            TextButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Gift Card", card.unmaskedCode))
                                DialogController.showToast("Code copied!")
                            }) {
                                Text("Copy", color = ElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Metadata Card
            NeoCard(backgroundColor = SurfaceCard) {
                Text(text = "Card Information", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Original Value", color = TextMuted, fontSize = 12.sp)
                    Text(text = "₹${card.initialValueRupees.toInt()}.00", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Remaining Value", color = TextMuted, fontSize = 12.sp)
                    Text(text = "₹${card.remainingValueRupees.toInt()}.00", color = getThemeAccent(card.cardDesign), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Delivery Channel", color = TextMuted, fontSize = 12.sp)
                    Text(text = card.deliveryMethod, color = TextWhite, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Expires At", color = TextMuted, fontSize = 12.sp)
                    Text(text = card.expiresAt.take(10), color = TextWhite, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Transaction History Ledger
            Text(text = "Transaction History", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))

            if (card.transactions.isEmpty()) {
                NeoCard(backgroundColor = SurfaceDark) {
                    Text(text = "No ledger entries recorded yet.", color = TextMuted, fontSize = 12.sp)
                }
            } else {
                card.transactions.forEach { tx ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceCard)
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = tx.transactionType.replace("_", " "),
                                    color = if (tx.transactionType == "REDEMPTION") EmeraldGreen else ElectricCyan,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tx.notes ?: "Balance adjustment",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = tx.createdAt.take(16).replace("T", " "),
                                    color = TextMuted.copy(alpha = 0.7f),
                                    fontSize = 10.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${if (tx.transactionType == "REDEMPTION") '-' else '+'}₹${tx.amountRupees.toInt()}",
                                    color = TextWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Bal: ₹${tx.balanceAfterRupees.toInt()}",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}
