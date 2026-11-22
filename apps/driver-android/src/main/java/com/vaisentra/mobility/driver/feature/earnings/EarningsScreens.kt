package com.vaisentra.mobility.driver.feature.earnings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.driver.components.*
import com.vaisentra.mobility.driver.data.DriverRepository
import com.vaisentra.mobility.driver.theme.*
import kotlinx.coroutines.launch

/**
 * Screen 34: Shift Summary & Daily Net Pay
 */
@Composable
fun ShiftSummaryScreen(
    onNavigateBack: () -> Unit = {},
    onGoToLedger: () -> Unit = {},
    onShowToast: (String) -> Unit = {},
    onNavigateAction: ((String) -> Unit)? = null
) {
    val earnings by DriverRepository.earnings.collectAsState()
    val scorecard by DriverRepository.scorecard.collectAsState()

    LaunchedEffect(Unit) {
        DriverRepository.fetchEarningsSummary()
        DriverRepository.fetchPerformanceScorecard()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Shift Summary",
            subtitle = "Daily earnings, completed trips & net payout",
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
                        Text("TODAY'S NET EARNINGS", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("₹${"%.2f".format(earnings.todayNetRupees)}", color = EmeraldPrimary, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(4.dp))
                        Text("${earnings.todayCompletedTrips} Completed Trips Today", color = CockpitTextSecondary, fontSize = 12.sp)
                    }
                }
            }

            item {
                CockpitCard {
                    Text("FINANCIAL SUMMARY", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    SummaryRow("Today Earnings", "₹${"%.2f".format(earnings.todayNetRupees)}")
                    SummaryRow("Weekly Earnings", "₹${"%.2f".format(earnings.weeklyRupees)}")
                    SummaryRow("Lifetime Earnings", "₹${"%.2f".format(earnings.totalEarningsCents / 100.0)}")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = CockpitBorder)
                    SummaryRow("Wallet Balance", "₹${"%.2f".format(earnings.walletBalanceRupees)}", EmeraldPrimary, isBold = true)
                }
            }

            item {
                CockpitCard {
                    Text("OPERATIONAL METRICS", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    SummaryRow("Acceptance Rate", "${"%.1f".format(scorecard.acceptanceRate)}%")
                    SummaryRow("Driver Rating", "${"%.2f".format(scorecard.rating)} ★")
                }
            }

            item {
                CockpitButton(
                    text = "View Detailed Transaction Ledger",
                    onClick = onGoToLedger,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (onNavigateAction != null) {
                item {
                    CockpitCard {
                        Text("SHARED POOL & MULTI-STOP TOOLS", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onNavigateAction("multi_stop") }.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Multi-Stop Waypoints", color = CockpitTextPrimary, fontSize = 13.sp)
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = ElectricCyan)
                        }
                        HorizontalDivider(color = CockpitBorder)
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onNavigateAction("pool_manifest") }.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Shared Pool Manifest", color = CockpitTextPrimary, fontSize = 13.sp)
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = ElectricCyan)
                        }
                        HorizontalDivider(color = CockpitBorder)
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onNavigateAction("corridor_waypoints") }.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Corridor Waypoints", color = CockpitTextPrimary, fontSize = 13.sp)
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = ElectricCyan)
                        }
                        HorizontalDivider(color = CockpitBorder)
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onNavigateAction("co_passenger_broadcast") }.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Co-Passenger Broadcast", color = CockpitTextPrimary, fontSize = 13.sp)
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = ElectricCyan)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Screen 39: Pilot Earnings & Balance
 */
@Composable
fun PilotEarningsScreen(
    onNavigateBack: () -> Unit = {},
    onInstantPayout: () -> Unit = {},
    onManageBanks: () -> Unit = {},
    onShowToast: (String) -> Unit = {},
    onNavigateAction: ((String) -> Unit)? = null
) {
    val earnings by DriverRepository.earnings.collectAsState()

    LaunchedEffect(Unit) {
        DriverRepository.fetchEarningsSummary()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Pilot Earnings & Wallet",
            subtitle = "Live wallet balance & instant payout gateway",
            onBack = onNavigateBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                CockpitCard(glow = true) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("AVAILABLE WITHDRAWABLE BALANCE", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("₹${"%.2f".format(earnings.walletBalanceRupees)}", color = EmeraldPrimary, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Today: ₹${"%.2f".format(earnings.todayNetRupees)}", color = AmberWarning, fontSize = 12.sp)
                            Text("Currency: ${earnings.currency}", color = CockpitTextMuted, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    CockpitButton(
                        text = "WITHDRAW INSTANTLY TO BANK (IMPS)",
                        onClick = onInstantPayout,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                CockpitCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Payout Accounts", color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Manage bank accounts & UPI VPA", color = CockpitTextSecondary, fontSize = 12.sp)
                        }
                        IconButton(onClick = onManageBanks) {
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = ElectricCyan)
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CockpitCard(modifier = Modifier.weight(1f)) {
                        Text("THIS WEEK", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("₹${"%.0f".format(earnings.weeklyRupees)}", color = ElectricCyan, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("${earnings.completedTripsToday} Total Trips", color = CockpitTextSecondary, fontSize = 12.sp)
                    }
                    CockpitCard(modifier = Modifier.weight(1f)) {
                        Text("RATING", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("${"%.2f".format(earnings.ratingAverage)} ★", color = AmberWarning, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("${earnings.ratingCount} Reviews", color = CockpitTextSecondary, fontSize = 12.sp)
                    }
                }
            }

            if (onNavigateAction != null) {
                item {
                    CockpitCard {
                        Text("FINANCIAL GOALS & MISSIONS", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onNavigateAction("driver_ledger") }.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Driver Financial Ledger", color = CockpitTextPrimary, fontSize = 13.sp)
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = ElectricCyan)
                        }
                        HorizontalDivider(color = CockpitBorder)
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onNavigateAction("daily_targets") }.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Daily Targets & Incentives", color = CockpitTextPrimary, fontSize = 13.sp)
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = ElectricCyan)
                        }
                        HorizontalDivider(color = CockpitBorder)
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onNavigateAction("weekly_goals") }.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Weekly Tier Goals", color = CockpitTextPrimary, fontSize = 13.sp)
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = ElectricCyan)
                        }
                        HorizontalDivider(color = CockpitBorder)
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onNavigateAction("streak_missions") }.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Streak Missions & Badges", color = CockpitTextPrimary, fontSize = 13.sp)
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = ElectricCyan)
                        }
                        HorizontalDivider(color = CockpitBorder)
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onNavigateAction("shift_summary") }.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Today's Shift Summary", color = CockpitTextPrimary, fontSize = 13.sp)
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = ElectricCyan)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Screen 40: Payout Bank & UPI Accounts (GET/POST /api/v1/drivers/financials/payout-accounts)
 */
@Composable
fun  PayoutAccountsScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val accounts by DriverRepository.payoutAccounts.collectAsState()
    val scope = rememberCoroutineScope()
    var accountNo by remember { mutableStateOf("") }
    var ifsc by remember { mutableStateOf("") }
    var holderName by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        DriverRepository.fetchPayoutAccounts()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Payout Accounts",
            subtitle = "Manage settlement bank accounts and UPI IDs",
            onBack = onNavigateBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                CockpitCard {
                    Text("LINK NEW BANK ACCOUNT", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    CockpitTextField(value = holderName, onValueChange = { holderName = it }, placeholder = "Account Holder Name", label = "Beneficiary Name")
                    Spacer(Modifier.height(8.dp))
                    CockpitTextField(value = accountNo, onValueChange = { accountNo = it }, placeholder = "Account Number", label = "Account Number")
                    Spacer(Modifier.height(8.dp))
                    CockpitTextField(value = ifsc, onValueChange = { ifsc = it }, placeholder = "e.g. HDFC0001234", label = "IFSC Code")
                    Spacer(Modifier.height(12.dp))
                    CockpitButton(
                        text = if (isSaving) "Linking..." else "+ Link Bank Account",
                        enabled = !isSaving && holderName.isNotBlank() && accountNo.isNotBlank() && ifsc.isNotBlank(),
                        onClick = {
                            isSaving = true
                            scope.launch {
                                val res = DriverRepository.addPayoutBankAccount(accountNo, ifsc, holderName)
                                isSaving = false
                                if (res.isSuccess) {
                                    onShowToast("Bank account linked for verification!")
                                    accountNo = ""
                                    ifsc = ""
                                    holderName = ""
                                } else {
                                    onShowToast("Failed: ${res.exceptionOrNull()?.message}")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("SAVED PAYOUT ACCOUNTS (${accounts.size})", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Table: driver_payout_accounts", color = ElectricCyan, fontSize = 10.sp)
                }
            }

            if (accounts.isEmpty()) {
                item {
                    CockpitCard {
                        Text("No saved payout accounts found.", color = CockpitTextMuted, fontSize = 13.sp)
                    }
                }
            } else {
                items(accounts) { acc ->
                    CockpitCard(glow = acc.isPrimary) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    acc.bankName ?: acc.accountType,
                                    color = CockpitTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    acc.upiVpa ?: "${acc.accountNumberMask ?: "****"} • ${acc.ifscCode ?: ""}",
                                    color = CockpitTextSecondary,
                                    fontSize = 12.sp
                                )
                                Text(
                                    "${acc.beneficiaryName ?: ""} • ${acc.verificationStatus}",
                                    color = CockpitTextMuted,
                                    fontSize = 11.sp
                                )
                            }
                            if (acc.isPrimary) {
                                CockpitBadge("PRIMARY", EmeraldPrimary)
                            } else {
                                CockpitButton(
                                    text = "Set Primary",
                                    isOutlined = true,
                                    onClick = {
                                        scope.launch {
                                            val res = DriverRepository.setPrimaryPayoutAccount(acc.id)
                                            if (res.isSuccess) onShowToast("Primary payout account updated")
                                            else onShowToast("Failed to set primary")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Screen 41: Instant IMPS/UPI Payout (POST /api/v1/drivers/payouts/request)
 */
@Composable
fun InstantPayoutScreen(
    onNavigateBack: () -> Unit = {},
    onPayoutSuccess: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val earnings by DriverRepository.earnings.collectAsState()
    val accounts by DriverRepository.payoutAccounts.collectAsState()
    var withdrawAmount by remember { mutableStateOf("") }
    var payoutMethod by remember { mutableStateOf("IMPS Instant") }
    var isSubmitting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        DriverRepository.fetchEarningsSummary()
        DriverRepository.fetchPayoutAccounts()
    }

    val primaryAccount = accounts.firstOrNull { it.isPrimary } ?: accounts.firstOrNull()
    val resolvedMask = if (payoutMethod == "UPI Direct") {
        primaryAccount?.upiVpa ?: primaryAccount?.accountNumberMask ?: "UPI-DEFAULT"
    } else {
        primaryAccount?.accountNumberMask ?: "**** 9988"
    }
    val resolvedIfsc = if (payoutMethod == "UPI Direct") "UPI" else (primaryAccount?.ifscCode ?: "HDFC0001234")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Instant Cashout",
            subtitle = "Zero-delay IMPS/UPI payout directly to your bank",
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
                            Text("MAX WITHDRAWABLE", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("₹${"%.2f".format(earnings.walletBalanceRupees)}", color = EmeraldPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                        CockpitBadge("IMPS READY", ElectricCyan)
                    }
                }

                CockpitTextField(
                    value = withdrawAmount,
                    onValueChange = { withdrawAmount = it },
                    label = "Withdrawal Amount (₹)",
                    placeholder = "Enter amount"
                )

                CockpitCard {
                    Text("SELECT SETTLEMENT METHOD", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("IMPS Instant", "UPI Direct").forEach { method ->
                            Button(
                                onClick = { payoutMethod = method },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (payoutMethod == method) ElectricCyan else CockpitSurfaceHighlight
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    method,
                                    color = if (payoutMethod == method) Color.Black else CockpitTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (primaryAccount != null) "Destination: $resolvedMask ($resolvedIfsc)" else "Destination: Pending account link",
                            color = CockpitTextSecondary,
                            fontSize = 11.sp
                        )
                        Text("Table: driver_payout_requests", color = ElectricCyan, fontSize = 10.sp)
                    }
                }
            }

            CockpitButton(
                text = if (isSubmitting) "PROCESSING..." else "CONFIRM WITHDRAWAL OF ₹${withdrawAmount.ifBlank { "0" }}",
                enabled = !isSubmitting && (withdrawAmount.toDoubleOrNull() ?: 0.0) > 0.0,
                onClick = {
                    val amt = withdrawAmount.toDoubleOrNull() ?: 0.0
                    isSubmitting = true
                    DriverRepository.requestInstantWithdrawal(
                        amountRupees = amt,
                        accountNumberMask = resolvedMask,
                        ifsc = resolvedIfsc
                    ) { ok ->
                        isSubmitting = false
                        if (ok) {
                            onShowToast("Payout request of ₹$amt submitted!")
                            onPayoutSuccess()
                        } else {
                            onShowToast("Payout failed. Check wallet balance.")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Screen 42: Driver Account Ledger
 */
@Composable
fun DriverLedgerScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val liveLedger by DriverRepository.ledger.collectAsState()
    val earnings by DriverRepository.earnings.collectAsState()

    LaunchedEffect(Unit) {
        DriverRepository.fetchAccountLedger()
        DriverRepository.fetchEarningsSummary()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Account Ledger",
            subtitle = "Itemized credit/debit transaction history",
            onBack = onNavigateBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                CockpitCard(glow = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("RUNNING WALLET BALANCE", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            val displayBal = liveLedger.firstOrNull()?.balanceAfterRupees ?: earnings.walletBalanceRupees
                            Text("₹${"%.2f".format(displayBal)}", color = EmeraldPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            CockpitBadge("LIVE AUDIT", ElectricCyan)
                            Spacer(Modifier.height(4.dp))
                            Text("Table: driver_ledger_entries", color = ElectricCyan, fontSize = 9.sp)
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("TRANSACTION HISTORY (${liveLedger.size})", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Sync: wallet_transactions", color = CockpitTextMuted, fontSize = 10.sp)
                }
            }

            if (liveLedger.isEmpty()) {
                item {
                    CockpitCard {
                        Text(
                            text = "No ledger entries yet in Supabase. Completed rides, tips, incentives, and payouts will appear here live.",
                            color = CockpitTextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                items(liveLedger) { item ->
                    val isCredit = item.type.uppercase() == "CREDIT"
                    val amtColor = if (isCredit) EmeraldPrimary else CrimsonAlert
                    val sign = if (isCredit) "+" else "-"

                    CockpitCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title.ifBlank { item.category },
                                    color = CockpitTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(Modifier.height(2.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    CockpitBadge(
                                        text = item.category.ifBlank { item.type },
                                        backgroundColor = if (isCredit) EmeraldPrimary.copy(alpha = 0.15f) else CrimsonAlert.copy(alpha = 0.15f),
                                        textColor = amtColor
                                    )
                                    if (item.timestamp.isNotBlank()) {
                                        Text(
                                            text = item.timestamp.take(19).replace("T", " "),
                                            color = CockpitTextMuted,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "$sign₹${"%.2f".format(item.amountRupees)}",
                                    color = amtColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                if (item.balanceAfterRupees > 0.0) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "Bal: ₹${"%.2f".format(item.balanceAfterRupees)}",
                                        color = CockpitTextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, color: Color = CockpitTextPrimary, isBold: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = CockpitTextSecondary, fontSize = 12.sp)
        Text(value, color = color, fontSize = 13.sp, fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal)
    }
}

@Preview(showBackground = true)
@Composable
fun PayoutAccountsScreenPreview() {
    DriverGoTheme {
        PayoutAccountsScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun InstantPayoutScreenPreview() {
    DriverGoTheme {
        InstantPayoutScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun DriverLedgerScreenPreview() {
    DriverGoTheme {
        DriverLedgerScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun SummaryRowPreview() {
    DriverGoTheme {
        SummaryRow(
            label = "Today Earnings",
            value = "₹450.00"
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ShiftSummaryScreenPreview() {
    DriverGoTheme {
        ShiftSummaryScreen()
    }
}