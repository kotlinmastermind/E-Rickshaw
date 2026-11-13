package com.vaisentra.mobility.driver.feature.earnings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.driver.components.*
import com.vaisentra.mobility.driver.data.DriverRepository
import com.vaisentra.mobility.driver.theme.*
import kotlinx.coroutines.launch

/**
 * Screen 34: Shift Summary & Daily Net Pay (gross fare, platform commission, net pay)
 */
@Composable
fun ShiftSummaryScreen(
    onNavigateBack: () -> Unit = {},
    onGoToLedger: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
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
                        Text("₹1,240.00", color = EmeraldPrimary, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(4.dp))
                        Text("14 Completed Trips • 6h 20m On Duty", color = CockpitTextSecondary, fontSize = 12.sp)
                    }
                }
            }

            item {
                CockpitCard {
                    Text("FINANCIAL SUMMARY", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    SummaryRow("Gross Fare Billed", "₹1,420.00")
                    SummaryRow("Platform Fee (-10%)", "-₹142.00", CrimsonAlert)
                    SummaryRow("Peak Surge Bonus", "+₹112.00", EmeraldPrimary)
                    SummaryRow("Morning Target Incentive", "+₹150.00", EmeraldPrimary)
                    SummaryRow("TDS Withheld (1% Sec 194C)", "-₹14.20", CockpitTextMuted)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = CockpitBorder)
                    SummaryRow("Total Wallet Credited", "₹1,240.00", EmeraldPrimary, isBold = true)
                }
            }

            item {
                CockpitCard {
                    Text("OPERATIONAL METRICS", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    SummaryRow("Total Distance Traveled", "48.6 km")
                    SummaryRow("Energy Consumed", "3.2 kWh (~₹22 cost)")
                    SummaryRow("Acceptance Rate", "98.2%")
                    SummaryRow("Customer Rating Today", "5.0 ★")
                }
            }

            item {
                CockpitButton(
                    text = "View Detailed Transaction Ledger",
                    onClick = onGoToLedger,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Screen 39: Pilot Earnings & Balance (wallet balance, today's pay, unsettled credits)
 */
@Composable
fun PilotEarningsScreen(
    onNavigateBack: () -> Unit = {},
    onInstantPayout: () -> Unit = {},
    onManageBanks: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
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
                        Text("₹${earnings.walletBalanceRupees.toInt()}.00", color = EmeraldPrimary, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Pending Clearance: ₹${earnings.pendingPayoutRupees.toInt()}.00", color = AmberWarning, fontSize = 12.sp)
                            Text("Auto-payout: Daily 23:00", color = CockpitTextMuted, fontSize = 12.sp)
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
                            Text("Primary Payout Account", color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("HDFC Bank •• 4912 (IFSC: HDFC0000288)", color = CockpitTextSecondary, fontSize = 12.sp)
                        }
                        IconButton(onClick = onManageBanks) {
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = ElectricCyan)
                        }
                    }
                }
            }

            item {
                Text("THIS WEEK'S HIGHLIGHTS", color = CockpitTextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CockpitCard(modifier = Modifier.weight(1f)) {
                        Text("THIS WEEK", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("₹8,620", color = ElectricCyan, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("84 Trips", color = CockpitTextSecondary, fontSize = 12.sp)
                    }
                    CockpitCard(modifier = Modifier.weight(1f)) {
                        Text("INCENTIVES", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("₹1,200", color = AmberWarning, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("4 Milestones", color = CockpitTextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/**
 * Screen 40: Payout Bank & UPI Accounts (bank account, IFSC, primary VPA)
 */
@Composable
fun PayoutAccountsScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val bankAccounts = listOf(
        BankItem("HDFC Bank", "Savings A/C •• 4912", "HDFC0000288", "VERIFIED (Penny Drop)", true),
        BankItem("State Bank of India", "Savings A/C •• 8821", "SBIN0001234", "VERIFIED", false)
    )

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
            items(bankAccounts) { item ->
                CockpitCard(glow = item.isPrimary) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(item.bankName, color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(item.accountNumber, color = CockpitTextSecondary, fontSize = 12.sp)
                            Text("IFSC: ${item.ifsc}", color = CockpitTextMuted, fontSize = 11.sp)
                        }
                        if (item.isPrimary) {
                            CockpitBadge("PRIMARY ACCOUNT", EmeraldPrimary)
                        }
                    }
                }
            }

            item {
                CockpitCard {
                    Text("PRIMARY UPI VPA", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("ramesh.pilot@okhdfcbank", color = ElectricCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Instant UPI settlement supported up to ₹1,00,000/day", color = CockpitTextSecondary, fontSize = 11.sp)
                }
            }

            item {
                CockpitButton(
                    text = "+ Link New Bank Account",
                    onClick = {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            DriverRepository.addPayoutBankAccount("98124912001", "HDFC0000288", "Ram Lal")
                        }
                        onShowToast("Penny drop verification initiated with Supabase!")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    isOutlined = true
                )
            }
        }
    }
}

/**
 * Screen 41: Instant IMPS/UPI Payout (instant settlement withdrawal)
 */
@Composable
fun InstantPayoutScreen(
    onNavigateBack: () -> Unit = {},
    onPayoutSuccess: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    var withdrawAmount by remember { mutableStateOf("1500") }
    var payoutMethod by remember { mutableStateOf("IMPS") } // or UPI

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
                            Text("₹3,450.00", color = EmeraldPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
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
                    Text("Fee: ₹0.00 (Free 2 instant cashouts per day)", color = CockpitTextSecondary, fontSize = 11.sp)
                }
            }

            CockpitButton(
                text = "CONFIRM WITHDRAWAL OF ₹$withdrawAmount",
                onClick = {
                    onShowToast("IMPS Transfer Dispatched! Ref UTR: 432109876543")
                    onPayoutSuccess()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Screen 42: Driver Account Ledger (itemized statement of rides and deductions)
 */
@Composable
fun DriverLedgerScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val liveLedger by DriverRepository.ledger.collectAsState()

    LaunchedEffect(Unit) {
        DriverRepository.fetchAccountLedger()
    }

    val defaultItems = listOf(
        LedgerEntry("Ride Fare #TRP-9812", "Trip to Anand Vihar ISBT", "+₹74.25", EmeraldPrimary, "Today 18:20"),
        LedgerEntry("Morning Target Incentive", "10 Peak rides completed", "+₹300.00", EmeraldPrimary, "Today 14:00"),
        LedgerEntry("TDS Deduction", "1% Section 194C", "-₹3.74", CrimsonAlert, "Today 18:20"),
        LedgerEntry("Instant IMPS Payout", "Transferred to HDFC •• 4912", "-₹1,500.00", CockpitTextSecondary, "Yesterday 21:15"),
        LedgerEntry("Ride Fare #TRP-9780", "Trip to Mayur Vihar", "+₹62.10", EmeraldPrimary, "Yesterday 20:30")
    )

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
            if (liveLedger.isNotEmpty()) {
                items(liveLedger) { item ->
                    CockpitCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(item.timestamp, color = CockpitTextMuted, fontSize = 10.sp)
                            }
                            val amtColor = if (item.type == "CREDIT") EmeraldPrimary else CrimsonAlert
                            val sign = if (item.type == "CREDIT") "+" else "-"
                            Text("$sign₹${item.amountRupees}", color = amtColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            } else {
                items(defaultItems) { item ->
                    CockpitCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(item.desc, color = CockpitTextSecondary, fontSize = 12.sp)
                                Text(item.timestamp, color = CockpitTextMuted, fontSize = 10.sp)
                            }
                            Text(item.amount, color = item.color, fontWeight = FontWeight.Bold, fontSize = 15.sp)
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

private data class BankItem(val bankName: String, val accountNumber: String, val ifsc: String, val status: String, val isPrimary: Boolean)
private data class LedgerEntry(val title: String, val desc: String, val amount: String, val color: Color, val timestamp: String)

