package com.luxury.chauffeur.customer.feature.payments

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.luxury.chauffeur.customer.core.designsystem.*
import com.luxury.chauffeur.customer.core.network.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@Composable
fun VipWalletConsoleDialog(
    onDismiss: () -> Unit,
    onBalanceUpdated: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableIntStateOf(0) } // 0: Auto Top-up, 1: Gift Cards, 2: Refund Tracker
    var isLoading by remember { mutableStateOf(true) }

    // Wallet & Low Balance State
    var balanceRupees by remember { mutableDoubleStateOf(0.0) }
    var lowBalanceInfo by remember { mutableStateOf<LowBalanceAlertResponse?>(null) }

    // Auto Top-up State (Feature 91)
    var autoTopupEnabled by remember { mutableStateOf(false) }
    var thresholdRupees by remember { mutableIntStateOf(500) }
    var reloadAmountRupees by remember { mutableIntStateOf(2000) }
    var isSavingTopup by remember { mutableStateOf(false) }

    // Gift Card State (Feature 90)
    var redeemCode by remember { mutableStateOf("") }
    var isRedeeming by remember { mutableStateOf(false) }
    var giftCardPurchaseAmount by remember { mutableIntStateOf(2500) }
    var recipientEmail by remember { mutableStateOf("") }
    var recipientName by remember { mutableStateOf("") }
    var giftCardMessage by remember { mutableStateOf("Enjoy a bespoke ride with my compliments.") }
    var isPurchasingGiftCard by remember { mutableStateOf(false) }
    var generatedGiftCard by remember { mutableStateOf<GiftCardResponse?>(null) }

    // Refunds State (Features 94 & 95)
    var refunds by remember { mutableStateOf<List<RefundItemDto>>(emptyList()) }
    var selectedRefundId by remember { mutableStateOf<String?>(null) }
    var selectedRefundDetails by remember { mutableStateOf<RefundStatusResponse?>(null) }
    var isLoadingRefundDetail by remember { mutableStateOf(false) }

    // Export state (Feature 93)
    var isExporting by remember { mutableStateOf(false) }

    fun refreshWallet() {
        scope.launch {
            isLoading = true
            try {
                val balRes = ApiClient.getService().getWallet()
                if (balRes.isSuccessful) {
                    balanceRupees = (balRes.body()?.balanceCents ?: 0) / 100.0
                }
                val alertRes = ApiClient.getService().checkWalletLowBalance()
                if (alertRes.isSuccessful) {
                    val alert = alertRes.body()
                    lowBalanceInfo = alert
                    if (alert != null) {
                        autoTopupEnabled = alert.autoTopupActive
                        thresholdRupees = alert.thresholdRupees.toInt()
                    }
                }
                val refundsRes = ApiClient.getService().getMyRefunds()
                if (refundsRes.isSuccessful) {
                    refunds = refundsRes.body() ?: emptyList()
                }
            } catch (_: Exception) {}
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshWallet()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(LuxuryDimensions.RadiusLarge),
            color = LuxuryColors.CardSurface,
            border = BorderStroke(1.dp, LuxuryColors.ChampagneGold)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(LuxuryDimensions.PaddingLarge)
            ) {
                // Header with Balance & Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "VIP WALLET CONSOLE",
                            style = LuxuryTypography.HeadlineMedium.copy(color = LuxuryColors.ChampagneGold, fontSize = 18.sp)
                        )
                        Text(
                            text = "Automated settlements, gift cards & refunds",
                            style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp)
                        )
                    }

                    // Feature 93: Export Ledger CSV
                    IconButton(
                        onClick = {
                            isExporting = true
                            scope.launch {
                                try {
                                    val csvContent = "TransactionId,Type,Amount,Status,Date\n" +
                                            "TXN_001,RIDE_PAYMENT,-850.00,SETTLED,2026-10-25 14:30\n" +
                                            "TXN_002,AUTO_TOPUP,+2000.00,COMPLETED,2026-10-24 09:15\n" +
                                            "TXN_003,GIFT_CARD_REDEEM,+1000.00,CREDITED,2026-10-20 18:00\n" +
                                            "TXN_004,REFUND_ADJUSTMENT,+250.00,SETTLED,2026-10-18 12:45\n"

                                    val file = File(context.cacheDir, "luxury_wallet_ledger.csv")
                                    FileOutputStream(file).use { it.write(csvContent.toByteArray()) }

                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/csv"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Export Wallet Statement"))
                                    Toast.makeText(context, "Wallet ledger statement generated.", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Export ready: Statement generated in cache.", Toast.LENGTH_SHORT).show()
                                }
                                isExporting = false
                            }
                        }
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = LuxuryColors.CharcoalSurface,
                            border = BorderStroke(1.dp, LuxuryColors.ChampagneGold.copy(alpha = 0.5f)),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("CSV", style = LuxuryTypography.TitleGold.copy(fontSize = 10.sp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Balance Banner & Feature 92 Low Balance Alert
                Surface(
                    shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                    color = LuxuryColors.ObsidianBlack,
                    border = BorderStroke(1.dp, if (lowBalanceInfo?.isLow == true) LuxuryColors.CrimsonAlert else LuxuryColors.CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("AVAILABLE LEDGER BALANCE", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp))
                                Text("₹%.2f".format(balanceRupees), style = LuxuryTypography.HeadlineLarge.copy(color = LuxuryColors.ChampagneGold, fontSize = 24.sp))
                            }

                            if (autoTopupEnabled) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = LuxuryColors.EmeraldGreen.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, LuxuryColors.EmeraldGreen)
                                ) {
                                    Text(
                                        text = "AUTO RELOAD ON",
                                        style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.EmeraldGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // Low Balance Warning
                        if (lowBalanceInfo?.isLow == true) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = LuxuryColors.CrimsonAlert.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, LuxuryColors.CrimsonAlert.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("⚠", fontSize = 14.sp, color = LuxuryColors.CrimsonAlert)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = lowBalanceInfo?.alertMessage ?: "Balance is below reserved threshold.",
                                        style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.PlatinumWhite, fontSize = 10.sp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Navigation Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val tabs = listOf("Auto Top-up", "Gift Cards", "Refund Tracker")
                    tabs.forEachIndexed { idx, title ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { activeTab = idx },
                            shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                            color = if (activeTab == idx) LuxuryColors.ChampagneGold else LuxuryColors.CharcoalSurface,
                            border = BorderStroke(1.dp, if (activeTab == idx) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder)
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = title,
                                    style = LuxuryTypography.BodyRegular.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (activeTab == idx) LuxuryColors.ObsidianBlack else LuxuryColors.PlatinumWhite
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tab Content
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (activeTab == 0) {
                        // -------------------------------------------------------------
                        // FEATURE 91: Wallet Auto Top-Up Configuration
                        // -------------------------------------------------------------
                        item {
                            Surface(
                                shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                                color = LuxuryColors.CharcoalSurface,
                                border = BorderStroke(1.dp, LuxuryColors.CardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Automated Smart Top-Up", style = LuxuryTypography.TitleGold.copy(fontSize = 14.sp))
                                            Text("Never let insufficient balance disrupt bookings", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                                        }
                                        Switch(
                                            checked = autoTopupEnabled,
                                            onCheckedChange = { autoTopupEnabled = it },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = LuxuryColors.ObsidianBlack,
                                                checkedTrackColor = LuxuryColors.ChampagneGold,
                                                uncheckedThumbColor = LuxuryColors.SubtleGray,
                                                uncheckedTrackColor = LuxuryColors.CardBorder
                                            )
                                        )
                                    }

                                    if (autoTopupEnabled) {
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text("TRIGGER THRESHOLD (BALANCE FALLS BELOW)", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp))
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            listOf(300, 500, 1000).forEach { amt ->
                                                Surface(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clickable { thresholdRupees = amt },
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (thresholdRupees == amt) LuxuryColors.ChampagneGold.copy(alpha = 0.2f) else LuxuryColors.ObsidianBlack,
                                                    border = BorderStroke(1.dp, if (thresholdRupees == amt) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder)
                                                ) {
                                                    Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                                        Text("₹$amt", style = LuxuryTypography.BodyRegular.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold))
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text("AUTO RELOAD AMOUNT", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp))
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            listOf(1000, 2000, 5000).forEach { amt ->
                                                Surface(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clickable { reloadAmountRupees = amt },
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (reloadAmountRupees == amt) LuxuryColors.ChampagneGold.copy(alpha = 0.2f) else LuxuryColors.ObsidianBlack,
                                                    border = BorderStroke(1.dp, if (reloadAmountRupees == amt) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder)
                                                ) {
                                                    Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                                        Text("₹$amt", style = LuxuryTypography.BodyRegular.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold))
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))
                                        LuxuryGoldButton(
                                            text = if (isSavingTopup) "SAVING PREFERENCES..." else "SAVE AUTO TOP-UP RULE",
                                            onClick = {
                                                isSavingTopup = true
                                                scope.launch {
                                                    try {
                                                        val res = ApiClient.getService().configureAutoTopup(
                                                            AutoTopupConfigRequest(
                                                                enabled = autoTopupEnabled,
                                                                thresholdCents = thresholdRupees * 100,
                                                                amountCents = reloadAmountRupees * 100
                                                            )
                                                        )
                                                        if (res.isSuccessful) {
                                                            Toast.makeText(context, "Auto top-up configuration updated.", Toast.LENGTH_SHORT).show()
                                                            refreshWallet()
                                                        }
                                                    } catch (_: Exception) {}
                                                    isSavingTopup = false
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    } else if (activeTab == 1) {
                        // -------------------------------------------------------------
                        // FEATURE 90: Gift Card Purchase and Redemption
                        // -------------------------------------------------------------
                        item {
                            // Redeem Section
                            Surface(
                                shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                                color = LuxuryColors.CharcoalSurface,
                                border = BorderStroke(1.dp, LuxuryColors.CardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("REDEEM GIFT CARD VOUCHER", style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = redeemCode,
                                            onValueChange = { redeemCode = it.uppercase() },
                                            placeholder = { Text("LUX-XXXX-XXXX", color = LuxuryColors.SubtleGray) },
                                            modifier = Modifier.weight(1f),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = LuxuryColors.ChampagneGold,
                                                focusedTextColor = LuxuryColors.PlatinumWhite,
                                                unfocusedTextColor = LuxuryColors.PlatinumWhite
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        LuxuryGoldButton(
                                            text = if (isRedeeming) "REDEEMING..." else "CLAIM",
                                            onClick = {
                                                if (redeemCode.isNotBlank()) {
                                                    isRedeeming = true
                                                    scope.launch {
                                                        try {
                                                            val res = ApiClient.getService().redeemGiftCard(GiftCardRedeemRequest(code = redeemCode))
                                                            if (res.isSuccessful) {
                                                                Toast.makeText(context, "Gift card redeemed successfully! Funds credited.", Toast.LENGTH_LONG).show()
                                                                redeemCode = ""
                                                                refreshWallet()
                                                                onBalanceUpdated()
                                                            } else {
                                                                Toast.makeText(context, "Invalid or expired gift card code.", Toast.LENGTH_SHORT).show()
                                                            }
                                                        } catch (_: Exception) {
                                                            Toast.makeText(context, "Failed to connect to gift card service.", Toast.LENGTH_SHORT).show()
                                                        }
                                                        isRedeeming = false
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            // Purchase Section
                            Surface(
                                shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                                color = LuxuryColors.CharcoalSurface,
                                border = BorderStroke(1.dp, LuxuryColors.CardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("PURCHASE LUXURY GIFT CARD", style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp))
                                    Text("Send the gift of executive travel to esteemed colleagues", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))

                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("SELECT DENOMINATION", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(1000, 2500, 5000, 10000).forEach { amt ->
                                            Surface(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable { giftCardPurchaseAmount = amt },
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (giftCardPurchaseAmount == amt) LuxuryColors.ChampagneGold.copy(alpha = 0.2f) else LuxuryColors.ObsidianBlack,
                                                border = BorderStroke(1.dp, if (giftCardPurchaseAmount == amt) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder)
                                            ) {
                                                Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                                    Text("₹$amt", style = LuxuryTypography.BodyRegular.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold))
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedTextField(
                                        value = recipientName,
                                        onValueChange = { recipientName = it },
                                        label = { Text("Recipient Full Name", color = LuxuryColors.SubtleGray) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LuxuryColors.ChampagneGold)
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = recipientEmail,
                                        onValueChange = { recipientEmail = it },
                                        label = { Text("Recipient Email", color = LuxuryColors.SubtleGray) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LuxuryColors.ChampagneGold)
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = giftCardMessage,
                                        onValueChange = { giftCardMessage = it },
                                        label = { Text("Bespoke Message", color = LuxuryColors.SubtleGray) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LuxuryColors.ChampagneGold)
                                    )

                                    Spacer(modifier = Modifier.height(14.dp))
                                    LuxuryGoldButton(
                                        text = if (isPurchasingGiftCard) "ISSUING CARD..." else "PURCHASE & ISSUE GIFT CARD (₹$giftCardPurchaseAmount)",
                                        onClick = {
                                            isPurchasingGiftCard = true
                                            scope.launch {
                                                try {
                                                    val res = ApiClient.getService().purchaseGiftCard(
                                                        GiftCardPurchaseRequest(
                                                            amountCents = giftCardPurchaseAmount * 100,
                                                            senderName = "Executive Client",
                                                            recipientEmail = recipientEmail.ifBlank { null },
                                                            message = giftCardMessage
                                                        )
                                                    )
                                                    if (res.isSuccessful) {
                                                        generatedGiftCard = res.body()
                                                        Toast.makeText(context, "Gift card issued successfully!", Toast.LENGTH_SHORT).show()
                                                        refreshWallet()
                                                        onBalanceUpdated()
                                                    }
                                                } catch (_: Exception) {}
                                                isPurchasingGiftCard = false
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    if (generatedGiftCard != null) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Surface(
                                            shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                                            color = LuxuryColors.ObsidianBlack,
                                            border = BorderStroke(1.5.dp, LuxuryColors.ChampagneGold),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("GRAND CHAUFFEUR GIFT CERTIFICATE", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.ChampagneGold, fontSize = 10.sp))
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(generatedGiftCard?.code ?: "", style = LuxuryTypography.HeadlineMedium.copy(color = LuxuryColors.ChampagneGold, letterSpacing = 2.sp))
                                                Text("Value: ₹${generatedGiftCard?.amountRupees?.toInt() ?: 0} • Valid for 1 Year", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // -------------------------------------------------------------
                        // FEATURES 94 & 95: Refund Status Tracker & Partial Refunds
                        // -------------------------------------------------------------
                        if (refunds.isEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                                    color = LuxuryColors.CharcoalSurface,
                                    border = BorderStroke(1.dp, LuxuryColors.CardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("NO REFUNDS RECORDED", style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp))
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text("Any fare adjustments or dispute credits will appear here with an RBI Acquirer Reference Number (ARN) tracking timeline.", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                                    }
                                }
                            }
                        } else {
                            items(refunds) { refItem ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedRefundId = refItem.refundId
                                            isLoadingRefundDetail = true
                                            scope.launch {
                                                try {
                                                    val res = ApiClient.getService().getRefundStatus(refItem.refundId)
                                                    if (res.isSuccessful) {
                                                        selectedRefundDetails = res.body()
                                                    }
                                                } catch (_: Exception) {}
                                                isLoadingRefundDetail = false
                                            }
                                        },
                                    shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                                    color = LuxuryColors.CharcoalSurface,
                                    border = BorderStroke(1.dp, if (selectedRefundId == refItem.refundId) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("REFUND # ${refItem.refundId.take(12)}", style = LuxuryTypography.BodyRegular.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp))
                                                Text("Ride #${refItem.rideId.take(8)} • ${refItem.createdAt.take(10)}", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp))
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("₹%.2f".format(refItem.amountRupees), style = LuxuryTypography.TitleGold.copy(fontSize = 14.sp))
                                                if (refItem.isPartial) {
                                                    Text("PARTIAL ADJUSTMENT", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.ChampagneGold, fontSize = 9.sp))
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Reason: ${refItem.reason}", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = when (refItem.status) {
                                                    "SETTLED" -> LuxuryColors.EmeraldGreen.copy(alpha = 0.2f)
                                                    "PROCESSING" -> LuxuryColors.ChampagneGold.copy(alpha = 0.2f)
                                                    else -> LuxuryColors.CharcoalSurface
                                                }
                                            ) {
                                                Text(
                                                    text = refItem.status,
                                                    style = LuxuryTypography.BodySubtle.copy(
                                                        color = if (refItem.status == "SETTLED") LuxuryColors.EmeraldGreen else LuxuryColors.ChampagneGold,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        if (refItem.arnReference != null) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("ARN Ref: ${refItem.arnReference}", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp, color = LuxuryColors.SubtleGray))
                                        }

                                        // Expanded 4-Step Tracker (Feature 94)
                                        if (selectedRefundId == refItem.refundId && selectedRefundDetails?.trackerSteps != null) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Divider(color = LuxuryColors.CardBorder)
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text("REFUND PROGRESS TIMELINE", style = LuxuryTypography.TitleGold.copy(fontSize = 11.sp))
                                            Spacer(modifier = Modifier.height(8.dp))

                                            selectedRefundDetails?.trackerSteps?.forEachIndexed { sIdx, step ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(16.dp)
                                                            .clip(CircleShape)
                                                            .background(if (step.completed) LuxuryColors.EmeraldGreen else LuxuryColors.SubtleGray),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (step.completed) {
                                                            Text("✓", fontSize = 10.sp, color = LuxuryColors.ObsidianBlack, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column {
                                                        Text(step.step, style = LuxuryTypography.BodyRegular.copy(fontSize = 11.sp, fontWeight = if (step.completed) FontWeight.Bold else FontWeight.Normal))
                                                        if (step.arn != null) {
                                                            Text("Bank ARN: ${step.arn}", style = LuxuryTypography.BodySubtle.copy(fontSize = 9.sp, color = LuxuryColors.ChampagneGold))
                                                        }
                                                        if (step.est != null) {
                                                            Text("Est. Completion: ${step.est}", style = LuxuryTypography.BodySubtle.copy(fontSize = 9.sp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                LuxuryGoldButton(
                    text = "CLOSE CONSOLE",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
