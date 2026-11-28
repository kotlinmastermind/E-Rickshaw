package com.vaisentra.mobility.driver.feature.earnings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.vaisentra.mobility.driver.data.*
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
            subtitle = "Gross ride earnings and separately tracked commission",
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
                        Text("TODAY'S GROSS EARNINGS", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("₹${"%.2f".format(earnings.todayGrossRupees)}", color = EmeraldPrimary, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(4.dp))
                        Text("${earnings.todayCompletedTrips} Completed Trips Today", color = CockpitTextSecondary, fontSize = 12.sp)
                    }
                }
            }

            item {
                CockpitCard {
                    Text("FINANCIAL SUMMARY", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    SummaryRow("Today Gross Earnings", "₹${"%.2f".format(earnings.todayGrossRupees)}")
                    SummaryRow("Weekly Gross Earnings", "₹${"%.2f".format(earnings.weeklyRupees)}")
                    SummaryRow("Commission Accrued This Week", "₹${"%.2f".format(earnings.weeklyCommissionRupees)}", AmberWarning)
                    SummaryRow("Lifetime Gross Earnings", "₹${"%.2f".format(earnings.totalGrossRupees)}")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = CockpitBorder)
                    SummaryRow("Wallet Balance", "₹${"%.2f".format(earnings.walletBalanceRupees)}")
                    SummaryRow("Commission Outstanding", "₹${"%.2f".format(earnings.commissionOutstandingRupees)}", AmberWarning)
                    SummaryRow("Available to Withdraw", "₹${"%.2f".format(earnings.withdrawableBalanceRupees)}", EmeraldPrimary, isBold = true)
                    SummaryRow("Commission Accrued", "₹${"%.2f".format(earnings.commissionAccruedRupees)}", AmberWarning)
                    SummaryRow("Commission Payable", "₹${"%.2f".format(earnings.commissionPayableRupees)}")
                    SummaryRow("Cash Commission Receivable", "₹${"%.2f".format(earnings.commissionReceivableRupees)}")
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
    val supportRequests by DriverRepository.financialSupportRequests.collectAsState()
    val scope = rememberCoroutineScope()

    var showWelfareDialog by remember { mutableStateOf(false) }
    var selectedSupportType by remember { mutableStateOf("EMERGENCY_REPAIR") }
    var amountRupeesText by remember { mutableStateOf("10000") }
    var repaymentWeeks by remember { mutableIntStateOf(4) }
    var supportReason by remember { mutableStateOf("") }
    var isSubmittingSupport by remember { mutableStateOf(false) }

    var showGiftCardDialog by remember { mutableStateOf(false) }
    var giftCardCodeInput by remember { mutableStateOf("") }
    var giftCardPinInput by remember { mutableStateOf("") }
    var isCheckingGiftCard by remember { mutableStateOf(false) }
    var giftCardCheckResult by remember { mutableStateOf<DriverGiftCardBalanceResponse?>(null) }
    var giftCardCheckError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        DriverRepository.fetchEarningsSummary()
        DriverRepository.fetchFinancialSupportRequests()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Pilot Earnings & Wallet",
            subtitle = "Gross ride earnings, commission tracking & wallet balance",
            onBack = onNavigateBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                CockpitCard(glow = true) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("AVAILABLE TO WITHDRAW", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("₹${"%.2f".format(earnings.withdrawableBalanceRupees)}", color = EmeraldPrimary, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Today gross: ₹${"%.2f".format(earnings.todayGrossRupees)}", color = AmberWarning, fontSize = 12.sp)
                            Text("Commission today: ₹${"%.2f".format(earnings.todayCommissionRupees)}", color = CockpitTextMuted, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    CockpitButton(
                        text = "REQUEST BANK PAYOUT",
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
                        Text("THIS WEEK · GROSS", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("₹${"%.0f".format(earnings.weeklyRupees)}", color = ElectricCyan, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("${earnings.totalCompletedTrips} All-time Trips", color = CockpitTextSecondary, fontSize = 12.sp)
                    }
                    CockpitCard(modifier = Modifier.weight(1f)) {
                        Text("RATING", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(if (earnings.ratingCount > 0) "${"%.2f".format(earnings.ratingAverage)} ★" else "Not rated", color = AmberWarning, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("${earnings.ratingCount} Reviews", color = CockpitTextSecondary, fontSize = 12.sp)
                    }
                }
            }

            item {
                CockpitCard {
                    Text("COMMISSION TRACKING", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    SummaryRow("Wallet Balance", "₹${"%.2f".format(earnings.walletBalanceRupees)}")
                    SummaryRow("Commission Accrued This Week", "₹${"%.2f".format(earnings.weeklyCommissionRupees)}", AmberWarning)
                    SummaryRow("Total Commission Accrued", "₹${"%.2f".format(earnings.commissionAccruedRupees)}")
                    SummaryRow("Commission Outstanding", "₹${"%.2f".format(earnings.commissionOutstandingRupees)}", AmberWarning)
                    SummaryRow("Commission Payable", "₹${"%.2f".format(earnings.commissionPayableRupees)}")
                    SummaryRow("Cash Commission Receivable", "₹${"%.2f".format(earnings.commissionReceivableRupees)}")
                    if (earnings.commissionSnapshotMissingCount > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text("Commission history unavailable for ${earnings.commissionSnapshotMissingCount} completed ride(s); no rate has been guessed.", color = AmberWarning, fontSize = 11.sp)
                    }
                }
            }

            item {
                CockpitCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("CHAUFFEUR WELFARE BOARD", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Surface(
                                    color = EmeraldPrimary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        "0% APR AID",
                                        color = EmeraldPrimary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("Emergency Relief & Welfare Assistance", color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("₹1,000 – ₹50,000 emergency capital with weekly settlement deductions.", color = CockpitTextSecondary, fontSize = 11.sp)
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    CockpitButton(
                        text = "+ Apply for Welfare Relief Assistance",
                        onClick = { showWelfareDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (supportRequests.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("ACTIVE & PAST WELFARE APPLICATIONS (${supportRequests.size})", color = CockpitTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            supportRequests.take(3).forEach { req ->
                                val statusColor = when (req.status.uppercase()) {
                                    "APPROVED", "DISBURSED" -> EmeraldPrimary
                                    "REJECTED" -> CrimsonAlert
                                    else -> AmberWarning
                                }
                                Surface(
                                    color = CockpitSurfaceHighlight,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                req.supportType.replace("_", " "),
                                                color = CockpitTextPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                            Surface(
                                                color = statusColor.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    req.status,
                                                    color = statusColor,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Aid: ₹${"%.0f".format(req.amountRupees)}", color = ElectricCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            val weeklyDeduction = if (req.repaymentTermsWeeks > 0) req.amountRupees / req.repaymentTermsWeeks else 0.0
                                            Text("₹${"%.0f".format(weeklyDeduction)}/wk (${req.repaymentTermsWeeks} wks)", color = CockpitTextMuted, fontSize = 11.sp)
                                        }
                                        if (!req.reason.isNullOrBlank()) {
                                            Spacer(Modifier.height(2.dp))
                                            Text(req.reason, color = CockpitTextSecondary, fontSize = 10.sp, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                CockpitCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                giftCardCodeInput = ""
                                giftCardPinInput = ""
                                giftCardCheckResult = null
                                giftCardCheckError = null
                                showGiftCardDialog = true
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("🎁", fontSize = 22.sp)
                            Column {
                                Text("Gift Cards & Commuter Credits", color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Check voucher balance & commuter guidelines", color = CockpitTextSecondary, fontSize = 11.sp)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AmberWarning)
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

        if (showWelfareDialog) {
            AlertDialog(
                onDismissRequest = { if (!isSubmittingSupport) showWelfareDialog = false },
                containerColor = CockpitCardBackground,
                title = {
                    Column {
                        Text("Apply for Welfare Assistance", color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Zero-interest emergency capital with automated weekly deductions", color = CockpitTextMuted, fontSize = 11.sp)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("ASSISTANCE CATEGORY", color = CockpitTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        val types = listOf(
                            "EMERGENCY_REPAIR" to "Vehicle Repair",
                            "MEDICAL_ASSISTANCE" to "Medical Aid",
                            "FUEL_WORKING_CAPITAL" to "Fuel Working Capital",
                            "HARDSHIP_GRANT" to "Hardship Grant"
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            types.take(2).forEach { (key, label) ->
                                val sel = selectedSupportType == key
                                Surface(
                                    color = if (sel) ElectricCyan.copy(alpha = 0.2f) else CockpitSurface,
                                    shape = RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (sel) ElectricCyan else CockpitBorder),
                                    modifier = Modifier.weight(1f).clickable { selectedSupportType = key }
                                ) {
                                    Text(
                                        label,
                                        color = if (sel) ElectricCyan else CockpitTextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            types.drop(2).forEach { (key, label) ->
                                val sel = selectedSupportType == key
                                Surface(
                                    color = if (sel) ElectricCyan.copy(alpha = 0.2f) else CockpitSurface,
                                    shape = RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (sel) ElectricCyan else CockpitBorder),
                                    modifier = Modifier.weight(1f).clickable { selectedSupportType = key }
                                ) {
                                    Text(
                                        label,
                                        color = if (sel) ElectricCyan else CockpitTextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }

                        Text("REQUESTED AMOUNT (₹1,000 – ₹50,000)", color = CockpitTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("5000", "10000", "20000", "30000").forEach { preset ->
                                val sel = amountRupeesText == preset
                                Surface(
                                    color = if (sel) EmeraldPrimary.copy(alpha = 0.2f) else CockpitSurface,
                                    shape = RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (sel) EmeraldPrimary else CockpitBorder),
                                    modifier = Modifier.weight(1f).clickable { amountRupeesText = preset }
                                ) {
                                    Text(
                                        "₹$preset",
                                        color = if (sel) EmeraldPrimary else CockpitTextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                        CockpitTextField(
                            value = amountRupeesText,
                            onValueChange = { amountRupeesText = it.filter { ch -> ch.isDigit() }.take(6) },
                            placeholder = "Enter amount (₹)",
                            label = "Aid Amount (₹)"
                        )

                        Text("REPAYMENT TENURE (0% APR DEDUCTION)", color = CockpitTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(4 to "4 Weeks", 8 to "8 Weeks", 12 to "12 Weeks").forEach { (weeks, label) ->
                                val sel = repaymentWeeks == weeks
                                Surface(
                                    color = if (sel) AmberWarning.copy(alpha = 0.2f) else CockpitSurface,
                                    shape = RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (sel) AmberWarning else CockpitBorder),
                                    modifier = Modifier.weight(1f).clickable { repaymentWeeks = weeks }
                                ) {
                                    Text(
                                        label,
                                        color = if (sel) AmberWarning else CockpitTextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }

                        val parsedAmt = amountRupeesText.toDoubleOrNull() ?: 0.0
                        if (parsedAmt > 0 && repaymentWeeks > 0) {
                            val weeklyDed = parsedAmt / repaymentWeeks
                            Surface(
                                color = CockpitSurfaceHighlight,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Est. Weekly Deduction:", color = CockpitTextSecondary, fontSize = 11.sp)
                                    Text("₹${"%.0f".format(weeklyDed)} / week", color = EmeraldPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }

                        CockpitTextField(
                            value = supportReason,
                            onValueChange = { supportReason = it },
                            placeholder = "Explain emergency reason (min 10 characters)...",
                            label = "Emergency Description"
                        )
                    }
                },
                confirmButton = {
                    val parsedAmt = amountRupeesText.toDoubleOrNull() ?: 0.0
                    val isValid = parsedAmt in 1000.0..50000.0 && supportReason.trim().length >= 10
                    Button(
                        onClick = {
                            isSubmittingSupport = true
                            scope.launch {
                                val res = DriverRepository.submitFinancialSupportRequest(
                                    supportType = selectedSupportType,
                                    amountRupees = parsedAmt,
                                    repaymentTermsWeeks = repaymentWeeks,
                                    reason = supportReason.trim()
                                )
                                isSubmittingSupport = false
                                if (res.isSuccess) {
                                    onShowToast("Welfare aid request submitted to Chauffeur Relief Board!")
                                    showWelfareDialog = false
                                    supportReason = ""
                                } else {
                                    onShowToast("Failed: ${res.exceptionOrNull()?.message}")
                                }
                            }
                        },
                        enabled = !isSubmittingSupport && isValid,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                    ) {
                        Text(if (isSubmittingSupport) "Submitting..." else "Submit Request", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showWelfareDialog = false }, enabled = !isSubmittingSupport) {
                        Text("Cancel", color = CockpitTextMuted)
                    }
                }
            )
        }

        if (showGiftCardDialog) {
            AlertDialog(
                onDismissRequest = { if (!isCheckingGiftCard) showGiftCardDialog = false },
                containerColor = CockpitCardBackground,
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🎁", fontSize = 20.sp)
                        Column {
                            Text("Gift Cards & Commuter Credits", color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Closed-loop passenger credits (RBI PPI Compliance)", color = CockpitTextMuted, fontSize = 11.sp)
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = AmberWarning.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AmberWarning.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    "REGULATORY NOTICE",
                                    color = AmberWarning,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Gift cards are non-cashable commuter credits redeemable for passenger rides or gifting to family/friends. Per RBI Prepaid Instrument guidelines, gift cards cannot be converted into driver earnings or withdrawn to bank accounts.",
                                    color = CockpitTextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }

                        Text("VERIFY GIFT CARD / VOUCHER", color = CockpitTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)

                        CockpitTextField(
                            value = giftCardCodeInput,
                            onValueChange = { giftCardCodeInput = it.uppercase() },
                            placeholder = "GC-XXXX-YYYY-ZZZZ",
                            label = "Gift Card Code"
                        )

                        CockpitTextField(
                            value = giftCardPinInput,
                            onValueChange = { if (it.length <= 4) giftCardPinInput = it.filter { c -> c.isDigit() } },
                            placeholder = "4-digit security PIN (if required)",
                            label = "Security PIN"
                        )

                        if (giftCardCheckError != null) {
                            Surface(
                                color = CrimsonAlert.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    giftCardCheckError ?: "",
                                    color = CrimsonAlert,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }

                        val result = giftCardCheckResult
                        if (result != null) {
                            Surface(
                                color = EmeraldPrimary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Balance Available:", color = CockpitTextSecondary, fontSize = 11.sp)
                                        Text("₹${"%.2f".format(result.remainingBalanceRupees)}", color = EmeraldPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Card Code:", color = CockpitTextMuted, fontSize = 10.sp)
                                        Text(result.maskedCode, color = CockpitTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Status:", color = CockpitTextMuted, fontSize = 10.sp)
                                        Text(result.status, color = if (result.isRedeemable) EmeraldPrimary else AmberWarning, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (giftCardCodeInput.trim().isBlank()) {
                                giftCardCheckError = "Please enter a valid gift card code"
                                return@Button
                            }
                            isCheckingGiftCard = true
                            giftCardCheckError = null
                            giftCardCheckResult = null
                            scope.launch {
                                val res = DriverRepository.checkGiftCardBalance(
                                    code = giftCardCodeInput,
                                    pin = giftCardPinInput
                                )
                                isCheckingGiftCard = false
                                if (res.isSuccess) {
                                    giftCardCheckResult = res.getOrNull()
                                } else {
                                    giftCardCheckError = res.exceptionOrNull()?.message ?: "Verification failed"
                                }
                            }
                        },
                        enabled = !isCheckingGiftCard && giftCardCodeInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                    ) {
                        Text(if (isCheckingGiftCard) "Verifying..." else "Check Balance", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showGiftCardDialog = false }, enabled = !isCheckingGiftCard) {
                        Text("Close", color = CockpitTextMuted)
                    }
                }
            )
        }
    }
}

/**
 * Screen 40: Payout Bank & UPI Accounts (GET/POST /api/v1/drivers/financials/payout-accounts)
 */
@Composable
fun PayoutAccountsScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val accounts by DriverRepository.payoutAccounts.collectAsState()
    val scope = rememberCoroutineScope()
    var accountType by remember { mutableStateOf("BANK_ACCOUNT") }
    var accountNo by remember { mutableStateOf("") }
    var ifsc by remember { mutableStateOf("") }
    var upiVpa by remember { mutableStateOf("") }
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
            subtitle = "Manage verified bank accounts and UPI IDs",
            onBack = onNavigateBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                CockpitCard {
                    Text("ADD PAYOUT DESTINATION", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("BANK_ACCOUNT" to "Bank Account", "UPI" to "UPI ID").forEach { (type, label) ->
                            Button(
                                onClick = { accountType = type },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (accountType == type) ElectricCyan else CockpitSurfaceHighlight
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    label,
                                    color = if (accountType == type) Color.Black else CockpitTextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    CockpitTextField(
                        value = holderName,
                        onValueChange = { holderName = it },
                        placeholder = "Account holder / beneficiary name",
                        label = "Beneficiary Name"
                    )
                    Spacer(Modifier.height(8.dp))
                    if (accountType == "BANK_ACCOUNT") {
                        CockpitTextField(
                            value = accountNo,
                            onValueChange = { accountNo = it.filter(Char::isDigit).take(25) },
                            placeholder = "Account Number",
                            label = "Account Number"
                        )
                        Spacer(Modifier.height(8.dp))
                        CockpitTextField(
                            value = ifsc,
                            onValueChange = { ifsc = it.uppercase().take(11) },
                            placeholder = "e.g. HDFC0001234",
                            label = "IFSC Code"
                        )
                    } else {
                        CockpitTextField(
                            value = upiVpa,
                            onValueChange = { upiVpa = it.trim() },
                            placeholder = "name@bank",
                            label = "UPI ID"
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    CockpitButton(
                        text = if (isSaving) "Submitting..." else if (accountType == "BANK_ACCOUNT") "+ Link Bank Account" else "+ Link UPI ID",
                        enabled = !isSaving && holderName.isNotBlank() &&
                            if (accountType == "BANK_ACCOUNT") accountNo.length in 6..25 && ifsc.length == 11
                            else upiVpa.isNotBlank(),
                        onClick = {
                            isSaving = true
                            scope.launch {
                                val res = if (accountType == "BANK_ACCOUNT") {
                                    DriverRepository.addPayoutBankAccount(accountNo, ifsc, holderName)
                                } else {
                                    DriverRepository.addPayoutUpiAccount(upiVpa, holderName)
                                }
                                isSaving = false
                                if (res.isSuccess) {
                                    onShowToast("Payout destination submitted for Admin verification.")
                                    accountNo = ""
                                    ifsc = ""
                                    upiVpa = ""
                                    holderName = ""
                                } else {
                                    onShowToast("Failed: ${res.exceptionOrNull()?.message ?: "Unable to submit destination"}")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "New destinations remain unavailable for withdrawal until Admin verification is complete.",
                        color = CockpitTextMuted,
                        fontSize = 11.sp
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
                    Text("Persisted destinations", color = ElectricCyan, fontSize = 10.sp)
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
                                    if (acc.accountType == "UPI") "UPI payout destination" else (acc.bankName ?: "Bank account"),
                                    color = CockpitTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    "${acc.accountNumberMask ?: "****"}${if (acc.accountType == "BANK_ACCOUNT" && !acc.ifscCode.isNullOrBlank()) " • ${acc.ifscCode}" else ""}",
                                    color = CockpitTextSecondary,
                                    fontSize = 12.sp
                                )
                                val statusColor = when (acc.verificationStatus) {
                                    "VERIFIED" -> EmeraldPrimary
                                    "REJECTED" -> CrimsonAlert
                                    else -> AmberWarning
                                }
                                Text(
                                    "${acc.beneficiaryName ?: ""} • ${acc.verificationStatus}",
                                    color = statusColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (acc.verificationStatus == "REJECTED" && !acc.rejectionReason.isNullOrBlank()) {
                                    Text("Reason: ${acc.rejectionReason}", color = CrimsonAlert, fontSize = 10.sp)
                                }
                            }
                            if (acc.isPrimary && acc.verificationStatus == "VERIFIED") {
                                CockpitBadge("PRIMARY", EmeraldPrimary)
                            } else if (acc.verificationStatus == "VERIFIED") {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            val res = DriverRepository.setPrimaryPayoutAccount(acc.id)
                                            if (res.isSuccess) {
                                                onShowToast("Primary payout account updated")
                                                DriverRepository.fetchPayoutAccounts()
                                            } else {
                                                onShowToast("Failed to set primary: ${res.exceptionOrNull()?.message ?: ""}")
                                            }
                                        }
                                    },
                                    modifier = Modifier.height(32.dp).wrapContentWidth(),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldPrimary),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldPrimary)
                                ) {
                                    Text("Set Primary", color = EmeraldPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                CockpitBadge(
                                    if (acc.verificationStatus == "REJECTED") "REJECTED" else "PENDING",
                                    if (acc.verificationStatus == "REJECTED") CrimsonAlert else AmberWarning
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
    val payoutHistory by DriverRepository.payoutHistory.collectAsState()
    val payoutHistoryLoading by DriverRepository.payoutHistoryLoading.collectAsState()
    val payoutHistoryError by DriverRepository.payoutHistoryError.collectAsState()
    var withdrawAmount by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        DriverRepository.fetchEarningsSummary()
        DriverRepository.fetchPayoutAccounts()
        DriverRepository.fetchPayoutHistory()
    }

    val verifiedAccounts = accounts.filter { it.verificationStatus == "VERIFIED" }
    val primaryAccount = verifiedAccounts.firstOrNull { it.isPrimary }
    val resolvedMask = primaryAccount?.accountNumberMask.orEmpty()
    val resolvedIfsc = primaryAccount?.ifscCode.orEmpty()
    val hasValidDestination = primaryAccount?.id?.isNotBlank() == true
    val transferRail = when (primaryAccount?.accountType) {
        "UPI" -> "UPI transfer"
        "BANK_ACCOUNT" -> "IMPS bank transfer"
        else -> "Unavailable"
    }
    var attemptNonce by remember(withdrawAmount, primaryAccount?.id) { mutableStateOf(0) }
    val pendingIdempotencyKey = remember(withdrawAmount, primaryAccount?.id, attemptNonce) {
        "payout_${java.util.UUID.randomUUID()}"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Payout Request",
            subtitle = "Transfer route is determined by your verified primary destination",
            onBack = onNavigateBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CockpitCard(glow = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("MAX WITHDRAWABLE", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("₹${"%.2f".format(earnings.withdrawableBalanceRupees)}", color = EmeraldPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    CockpitBadge(if (hasValidDestination) "ACCOUNT VERIFIED" else "ACCOUNT REQUIRED", if (hasValidDestination) ElectricCyan else AmberWarning)
                }
            }

            CockpitTextField(
                value = withdrawAmount,
                onValueChange = { raw ->
                    val candidate = raw.filter { ch -> ch.isDigit() || ch == '.' }.take(12)
                    val decimalIndex = candidate.indexOf('.')
                    if (candidate.count { it == '.' } <= 1 &&
                        (decimalIndex < 0 || candidate.length - decimalIndex - 1 <= 2)
                    ) {
                        withdrawAmount = candidate
                    }
                },
                label = "Withdrawal Amount (₹)",
                placeholder = "Enter amount"
            )

            CockpitCard {
                Text("VERIFIED PRIMARY DESTINATION", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    transferRail,
                    color = if (hasValidDestination) CockpitTextPrimary else AmberWarning,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(6.dp))
                val destStatusText = when {
                    primaryAccount == null && verifiedAccounts.isNotEmpty() -> "Set one verified account as Primary in Payout Accounts before withdrawing"
                    primaryAccount != null -> "${if (primaryAccount.accountType == "UPI") "UPI destination" else "Bank destination"}: $resolvedMask${if (resolvedIfsc.isNotBlank()) " ($resolvedIfsc)" else ""}"
                    accounts.any { it.verificationStatus == "PENDING_VERIFICATION" } -> "Payout account pending Admin verification"
                    else -> "Add and verify a bank account or UPI ID before requesting a payout"
                }
                Text(destStatusText, color = if (hasValidDestination) CockpitTextSecondary else AmberWarning, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Text("The backend selects UPI or IMPS from the verified destination type.", color = CockpitTextMuted, fontSize = 10.sp)
            }

            CockpitButton(
                text = if (isSubmitting) "SUBMITTING..." else "REQUEST PAYOUT OF ₹${withdrawAmount.ifBlank { "0" }}",
                enabled = !isSubmitting && hasValidDestination &&
                    (withdrawAmount.toDoubleOrNull() ?: 0.0) >= 100.0 &&
                    (withdrawAmount.toDoubleOrNull() ?: 0.0) * 100.0 <= earnings.withdrawableBalanceCents.toDouble(),
                onClick = {
                    val amt = withdrawAmount.toDoubleOrNull() ?: 0.0
                    isSubmitting = true
                    DriverRepository.requestInstantWithdrawal(
                        amountRupees = amt,
                        payoutAccountId = primaryAccount?.id.orEmpty(),
                        idempotencyKey = pendingIdempotencyKey
                    ) { ok ->
                        isSubmitting = false
                        if (ok) {
                            attemptNonce++
                            onShowToast("Payout request of ₹$amt submitted for review.")
                            onPayoutSuccess()
                        } else {
                            onShowToast("Payout request failed. Check balance and verified primary destination.")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "PAYOUT REQUEST HISTORY",
                    color = CockpitTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = { DriverRepository.fetchPayoutHistory() },
                    enabled = !payoutHistoryLoading
                ) {
                    Text(if (payoutHistoryLoading) "Loading..." else "Refresh")
                }
            }

            if (payoutHistoryLoading && payoutHistory.isEmpty()) {
                CockpitCard {
                    Text("Loading payout history...", color = CockpitTextSecondary, fontSize = 12.sp)
                }
            } else if (payoutHistoryError != null && payoutHistory.isEmpty()) {
                CockpitCard {
                    Text(payoutHistoryError.orEmpty(), color = CrimsonAlert, fontSize = 12.sp)
                    TextButton(onClick = { DriverRepository.fetchPayoutHistory() }) {
                        Text("Try again")
                    }
                }
            } else if (payoutHistory.isEmpty()) {
                CockpitCard {
                    Text("No payout requests yet.", color = CockpitTextSecondary, fontSize = 12.sp)
                }
            } else {
                if (payoutHistoryError != null) {
                    Text(payoutHistoryError.orEmpty(), color = CrimsonAlert, fontSize = 11.sp)
                }
                payoutHistory.take(10).forEach { item ->
                    val statusTint = when (item.status) {
                        "PAID" -> EmeraldPrimary
                        "FAILED", "REJECTED" -> CrimsonAlert
                        "AWAITING_MANUAL_TRANSFER", "PROCESSING", "APPROVED" -> ElectricCyan
                        else -> AmberWarning
                    }
                    CockpitCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "₹${"%.2f".format(item.amountCents / 100.0)} • ${item.bankAccountMask}",
                                    color = CockpitTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                val detailNote = item.failureReason
                                    ?: item.rejectionReason
                                    ?: item.settlementReference?.let { "Ref: $it" }
                                    ?: item.requestedAt.take(19).replace("T", " ")
                                Text(detailNote, color = CockpitTextMuted, fontSize = 11.sp)
                                if (item.retryCount > 0) {
                                    Text("Retry attempt: ${item.retryCount}", color = CockpitTextMuted, fontSize = 10.sp)
                                }
                                item.paidAt?.let {
                                    Text("Paid: ${it.take(19).replace("T", " ")}", color = EmeraldPrimary, fontSize = 10.sp)
                                }
                                item.failedAt?.let {
                                    Text("Failed: ${it.take(19).replace("T", " ")}", color = CrimsonAlert, fontSize = 10.sp)
                                }
                            }
                            CockpitBadge(item.status, statusTint)
                        }
                    }
                }
            }
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
                            val displayBal = earnings.walletBalanceRupees
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
fun SummaryRowPreview2() {
    DriverGoTheme {
        SummaryRow(
            label = "Wallet Balance",
            value = "₹1,250.00",
            color = EmeraldPrimary,
            isBold = true
        )
    }
}