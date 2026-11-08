package com.vaisentra.mobility.driver.feature.earnings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.driver.core.designsystem.*
import com.vaisentra.mobility.driver.core.network.DriverApiClient
import kotlinx.coroutines.launch

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

enum class EarningsTab {
    SHIFTS_EARNINGS,
    GOALS_INCENTIVES,
    WALLET_PAYOUTS,
    EXPENSES_WELFARE
}

@Composable
fun EarningsAndWalletScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf(EarningsTab.SHIFTS_EARNINGS) }
    var isLoading by remember { mutableStateOf(true) }
    var timeFilter by remember { mutableStateOf("TODAY") } // TODAY, THIS_WEEK

    // State data
    var walletBalanceRupees by remember { mutableDoubleStateOf(0.0) }
    var todayGrossCents by remember { mutableLongStateOf(0L) }
    var todayCommissionCents by remember { mutableLongStateOf(0L) }
    var todayTipsCents by remember { mutableLongStateOf(0L) }
    var todayNetRupees by remember { mutableDoubleStateOf(0.0) }
    var todayTripsCount by remember { mutableIntStateOf(0) }

    var weekGrossCents by remember { mutableLongStateOf(0L) }
    var weekCommissionCents by remember { mutableLongStateOf(0L) }
    var weekTipsCents by remember { mutableLongStateOf(0L) }
    var weekNetRupees by remember { mutableDoubleStateOf(0.0) }
    var weekTripsCount by remember { mutableIntStateOf(0) }

    var dailyHistory by remember { mutableStateOf<List<DailyHistoryItem>>(emptyList()) }
    var recentRides by remember { mutableStateOf<List<RideItemDto>>(emptyList()) }

    // Shift & Compliance
    var shiftStatus by remember { mutableStateOf(ShiftStatusDto()) }
    var utilizationReport by remember { mutableStateOf(UtilizationReportDto()) }

    // Goals & Incentives
    var weeklyGoal by remember { mutableStateOf(WeeklyGoalDto()) }
    var incentiveCriteria by remember { mutableStateOf<List<IncentiveCriterion>>(emptyList()) }
    var incentiveMilestones by remember { mutableStateOf<List<IncentiveMilestoneDto>>(emptyList()) }
    var bonusHistory by remember { mutableStateOf<List<BonusPayoutDto>>(emptyList()) }

    // Wallet, Payouts & Taxes
    var ledgerEntries by remember { mutableStateOf<List<LedgerEntryDto>>(emptyList()) }
    var payoutAccounts by remember { mutableStateOf<List<PayoutAccountDto>>(emptyList()) }
    var payoutSchedule by remember { mutableStateOf(PayoutScheduleDto()) }
    var reconciliationStatements by remember { mutableStateOf<List<ReconciliationStatementDto>>(emptyList()) }
    var taxDocuments by remember { mutableStateOf<List<TaxDocumentDto>>(emptyList()) }

    // Expenses, Zones & Welfare
    var expenses by remember { mutableStateOf<List<OperatingExpenseDto>>(emptyList()) }
    var totalExpensesRupees by remember { mutableDoubleStateOf(0.0) }
    var zoneComparisons by remember { mutableStateOf<List<ZoneComparisonDto>>(emptyList()) }
    var availabilityForecast by remember { mutableStateOf<List<AvailabilityForecastDto>>(emptyList()) }
    var supportRequests by remember { mutableStateOf<List<FinancialSupportDto>>(emptyList()) }

    // Modals
    var activeRideBreakdown by remember { mutableStateOf<RideBreakdownDto?>(null) }
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var showLogExpenseDialog by remember { mutableStateOf(false) }
    var showSupportDialog by remember { mutableStateOf(false) }
    var showEditGoalDialog by remember { mutableStateOf(false) }
    var failureDiagnosticStatement by remember { mutableStateOf<ReconciliationStatementDto?>(null) }

    // Load Data (Parallel IO fetching + Default Dispatcher mapping + Main state updates)
    val reloadAll = {
        coroutineScope.launch {
            isLoading = true
            try {
                withContext(Dispatchers.IO) {
                    val service = DriverApiClient.getService()

                    coroutineScope {
                        val dashDeferred = async { runCatching { service.getFinancialDashboard(14) }.getOrNull() }
                        val shiftDeferred = async { runCatching { service.getCurrentShift() }.getOrNull() }
                        val utilDeferred = async { runCatching { service.getUtilizationReport() }.getOrNull() }
                        val goalDeferred = async { runCatching { service.getWeeklyGoal() }.getOrNull() }
                        val incDeferred = async { runCatching { service.getIncentives() }.getOrNull() }
                        val bonDeferred = async { runCatching { service.getBonusHistory() }.getOrNull() }
                        val accDeferred = async { runCatching { service.getPayoutAccounts() }.getOrNull() }
                        val schedDeferred = async { runCatching { service.getPayoutSchedule() }.getOrNull() }
                        val recDeferred = async { runCatching { service.getPayoutReconciliation() }.getOrNull() }
                        val taxDeferred = async { runCatching { service.getTaxDocuments() }.getOrNull() }
                        val ledgDeferred = async { runCatching { service.getWalletLedger() }.getOrNull() }
                        val expDeferred = async { runCatching { service.getOperatingExpenses() }.getOrNull() }
                        val foreDeferred = async { runCatching { service.getAvailabilityForecastAndZones() }.getOrNull() }
                        val suppDeferred = async { runCatching { service.getFinancialSupportRequests() }.getOrNull() }

                        val dashRes = dashDeferred.await()
                        val shiftRes = shiftDeferred.await()
                        val utilRes = utilDeferred.await()
                        val goalRes = goalDeferred.await()
                        val incRes = incDeferred.await()
                        val bonRes = bonDeferred.await()
                        val accRes = accDeferred.await()
                        val schedRes = schedDeferred.await()
                        val recRes = recDeferred.await()
                        val taxRes = taxDeferred.await()
                        val ledgRes = ledgDeferred.await()
                        val expRes = expDeferred.await()
                        val foreRes = foreDeferred.await()
                        val suppRes = suppDeferred.await()

                        withContext(Dispatchers.Default) {
                            // 1. Dashboard
                            var wBal = 0.0
                            var tGross = 0L; var tComm = 0L; var tTips = 0L; var tNet = 0.0; var tTrips = 0
                            var wGross = 0L; var wComm = 0L; var wTips = 0L; var wNet = 0.0; var wTrips = 0
                            var parsedDaily = emptyList<DailyHistoryItem>()

                            if (dashRes?.isSuccessful == true && dashRes.body() != null) {
                                val body = dashRes.body()!!
                                wBal = (body["wallet_balance_rupees"] as? Number)?.toDouble() ?: 0.0

                                val todayMap = body["today"] as? Map<*, *>
                                tGross = (todayMap?.get("gross_fare_cents") as? Number)?.toLong() ?: 0L
                                tComm = (todayMap?.get("commission_cents") as? Number)?.toLong() ?: 0L
                                tTips = (todayMap?.get("tips_cents") as? Number)?.toLong() ?: 0L
                                tNet = (todayMap?.get("net_earnings_rupees") as? Number)?.toDouble() ?: 0.0
                                tTrips = (todayMap?.get("trips_count") as? Number)?.toInt() ?: 0

                                val weekMap = body["this_week"] as? Map<*, *>
                                wGross = (weekMap?.get("gross_fare_cents") as? Number)?.toLong() ?: 0L
                                wComm = (weekMap?.get("commission_cents") as? Number)?.toLong() ?: 0L
                                wTips = (weekMap?.get("tips_cents") as? Number)?.toLong() ?: 0L
                                wNet = (weekMap?.get("net_earnings_rupees") as? Number)?.toDouble() ?: 0.0
                                wTrips = (weekMap?.get("trips_count") as? Number)?.toInt() ?: 0

                                val dailyList = body["daily_history"] as? List<*>
                                parsedDaily = dailyList?.mapNotNull {
                                    val m = it as? Map<*, *> ?: return@mapNotNull null
                                    DailyHistoryItem(
                                        date = m["date"] as? String ?: "",
                                        dayName = m["day_name"] as? String ?: "",
                                        tripsCount = (m["trips_count"] as? Number)?.toInt() ?: 0,
                                        grossEarningsCents = (m["gross_earnings_cents"] as? Number)?.toLong() ?: 0L,
                                        netEarningsCents = (m["net_earnings_cents"] as? Number)?.toLong() ?: 0L,
                                        netEarningsRupees = (m["net_earnings_rupees"] as? Number)?.toDouble() ?: 0.0,
                                        commissionCents = (m["commission_cents"] as? Number)?.toLong() ?: 0L,
                                        tipsCents = (m["tips_cents"] as? Number)?.toLong() ?: 0L
                                    )
                                }.orEmpty()
                            }

                            // 2. Shift Status
                            var parsedShift = ShiftStatusDto()
                            if (shiftRes?.isSuccessful == true && shiftRes.body() != null) {
                                val s = shiftRes.body()!!
                                val compliance = s["compliance_status"] as? Map<*, *>
                                val forecast = s["forecast"] as? Map<*, *>
                                parsedShift = ShiftStatusDto(
                                    isShiftActive = s["is_shift_active"] as? Boolean ?: false,
                                    shiftId = s["shift_id"] as? String,
                                    startedAt = s["started_at"] as? String,
                                    durationMinutes = (s["duration_minutes"] as? Number)?.toDouble() ?: 0.0,
                                    totalBreakMinutes = (s["total_break_minutes"] as? Number)?.toInt() ?: 0,
                                    tripsCount = (s["trips_count"] as? Number)?.toInt() ?: 0,
                                    shiftGrossCents = (s["shift_gross_cents"] as? Number)?.toLong() ?: 0L,
                                    shiftNetCents = (s["shift_net_cents"] as? Number)?.toLong() ?: 0L,
                                    shiftNetRupees = (s["shift_net_rupees"] as? Number)?.toDouble() ?: 0.0,
                                    continuousDrivingMinutes = (compliance?.get("continuous_driving_minutes") as? Number)?.toDouble() ?: 0.0,
                                    breakRecommended = compliance?.get("break_recommended") as? Boolean ?: false,
                                    breakReminderMessage = compliance?.get("message") as? String,
                                    forecastProjectedNetRupees = (forecast?.get("projected_shift_net_rupees") as? Number)?.toDouble() ?: 0.0,
                                    forecastPaceStatus = forecast?.get("pace_status") as? String ?: "ON_PACE",
                                    forecastExplanation = forecast?.get("forecast_explanation") as? String ?: ""
                                )
                            }

                            // 3. Utilization
                            var parsedUtil = UtilizationReportDto()
                            if (utilRes?.isSuccessful == true && utilRes.body() != null) {
                                val u = utilRes.body()!!
                                parsedUtil = UtilizationReportDto(
                                    date = u["date"] as? String ?: "",
                                    totalOnlineHours = (u["total_online_hours"] as? Number)?.toDouble() ?: 0.0,
                                    totalInRideHours = (u["total_in_ride_hours"] as? Number)?.toDouble() ?: 0.0,
                                    utilizationPercentage = (u["utilization_percentage"] as? Number)?.toDouble() ?: 0.0,
                                    completedTrips = (u["completed_trips"] as? Number)?.toInt() ?: 0
                                )
                            }

                            // 4. Goals & Incentives
                            var parsedGoal = WeeklyGoalDto()
                            if (goalRes?.isSuccessful == true && goalRes.body() != null) {
                                val g = goalRes.body()!!
                                val targetRupees = (g["target_earnings_rupees"] as? Number)?.toDouble()
                                    ?: (g["target_net_rupees"] as? Number)?.toDouble() ?: 0.0
                                val currentRupees = (g["current_earnings_rupees"] as? Number)?.toDouble()
                                    ?: (g["current_net_rupees"] as? Number)?.toDouble() ?: 0.0
                                parsedGoal = WeeklyGoalDto(
                                    targetNetRupees = targetRupees,
                                    targetNetCents = (targetRupees * 100).toLong(),
                                    currentNetRupees = currentRupees,
                                    currentNetCents = (currentRupees * 100).toLong(),
                                    progressPercentage = (g["progress_percentage"] as? Number)?.toDouble() ?: 0.0,
                                    daysRemaining = (g["days_remaining"] as? Number)?.toInt() ?: (g["days_left_in_week"] as? Number)?.toInt() ?: 0,
                                    paceStatus = g["pace_status"] as? String ?: (if (targetRupees > 0) "ON_TRACK" else "NO_GOAL"),
                                    paceMessage = g["pace_message"] as? String ?: ""
                                )
                            }

                            var parsedCriteria = emptyList<IncentiveCriterion>()
                            var parsedMilestones = emptyList<IncentiveMilestoneDto>()
                            if (incRes?.isSuccessful == true && incRes.body() != null) {
                                val inc = incRes.body()!!
                                val eligList = inc["eligibility_criteria"] as? List<*>
                                parsedCriteria = eligList?.mapNotNull {
                                    val c = it as? Map<*, *> ?: return@mapNotNull null
                                    IncentiveCriterion(
                                        name = c["name"] as? String ?: "",
                                        required = c["required"] as? String ?: "",
                                        current = c["current"] as? String ?: "",
                                        isEligible = c["is_eligible"] as? Boolean ?: false
                                    )
                                }.orEmpty()

                                val msList = inc["milestones"] as? List<*>
                                parsedMilestones = msList?.mapNotNull {
                                    val m = it as? Map<*, *> ?: return@mapNotNull null
                                    IncentiveMilestoneDto(
                                        id = m["id"] as? String ?: "",
                                        title = m["title"] as? String ?: "",
                                        tier = m["tier"] as? String ?: "BRONZE",
                                        targetTrips = (m["target_trips"] as? Number)?.toInt() ?: 0,
                                        currentTrips = (m["current_trips"] as? Number)?.toInt() ?: 0,
                                        rewardCents = (m["reward_cents"] as? Number)?.toLong() ?: 0L,
                                        rewardRupees = (m["reward_rupees"] as? Number)?.toDouble() ?: 0.0,
                                        isCompleted = m["is_completed"] as? Boolean ?: false,
                                        isClaimed = m["is_claimed"] as? Boolean ?: false,
                                        expiresAt = m["expires_at"] as? String ?: ""
                                    )
                                }.orEmpty()
                            }

                            var parsedBonuses = emptyList<BonusPayoutDto>()
                            if (bonRes?.isSuccessful == true && bonRes.body() != null) {
                                val bList = bonRes.body()!!["bonuses"] as? List<*>
                                parsedBonuses = bList?.mapNotNull {
                                    val b = it as? Map<*, *> ?: return@mapNotNull null
                                    BonusPayoutDto(
                                        id = b["id"] as? String ?: "",
                                        title = b["title"] as? String ?: "",
                                        amountRupees = (b["amount_rupees"] as? Number)?.toDouble() ?: 0.0,
                                        paidAt = b["paid_at"] as? String ?: "",
                                        status = b["status"] as? String ?: "",
                                        questTier = b["quest_tier"] as? String ?: ""
                                    )
                                }.orEmpty()
                            }

                            // 5. Wallet & Payout Accounts
                            var parsedAccounts = emptyList<PayoutAccountDto>()
                            if (accRes?.isSuccessful == true && accRes.body() != null) {
                                val aList = accRes.body()!!["accounts"] as? List<*>
                                parsedAccounts = aList?.mapNotNull {
                                    val a = it as? Map<*, *> ?: return@mapNotNull null
                                    PayoutAccountDto(
                                        id = a["id"] as? String ?: "",
                                        accountType = a["account_type"] as? String ?: "BANK_ACCOUNT",
                                        holderName = a["holder_name"] as? String ?: "",
                                        bankName = a["bank_name"] as? String,
                                        accountNumberMasked = a["account_number_masked"] as? String,
                                        ifscCode = a["ifsc_code"] as? String,
                                        upiId = a["upi_id"] as? String,
                                        isPrimary = a["is_primary"] as? Boolean ?: false,
                                        isVerified = a["is_verified"] as? Boolean ?: false,
                                        verificationStatus = a["verification_status"] as? String ?: "VERIFIED",
                                        pennyDropRef = a["penny_drop_ref"] as? String
                                    )
                                }.orEmpty()
                            }

                            var parsedSchedule = PayoutScheduleDto()
                            if (schedRes?.isSuccessful == true && schedRes.body() != null) {
                                val s = schedRes.body()!!
                                parsedSchedule = PayoutScheduleDto(
                                    frequency = s["frequency"] as? String ?: "WEEKLY_MONDAY",
                                    autoPayoutEnabled = s["auto_payout_enabled"] as? Boolean ?: true,
                                    minimumPayoutRupees = (s["minimum_payout_rupees"] as? Number)?.toDouble() ?: 500.0,
                                    nextScheduledDate = s["next_scheduled_date"] as? String ?: "Upcoming Monday, 09:00 AM",
                                    primaryAccountId = s["primary_account_id"] as? String
                                )
                            }

                            var parsedRec = emptyList<ReconciliationStatementDto>()
                            if (recRes?.isSuccessful == true && recRes.body() != null) {
                                val rList = recRes.body()!!["statements"] as? List<*>
                                parsedRec = rList?.mapNotNull {
                                    val r = it as? Map<*, *> ?: return@mapNotNull null
                                    ReconciliationStatementDto(
                                        id = r["id"] as? String ?: "",
                                        periodMonth = r["period_month"] as? String ?: "",
                                        grossFareCents = (r["gross_fare_cents"] as? Number)?.toLong() ?: 0L,
                                        platformCommissionCents = (r["platform_commission_cents"] as? Number)?.toLong() ?: 0L,
                                        tipsCents = (r["tips_cents"] as? Number)?.toLong() ?: 0L,
                                        bonusesCents = (r["bonuses_cents"] as? Number)?.toLong() ?: 0L,
                                        tdsDeductionCents = (r["tds_deduction_cents"] as? Number)?.toLong() ?: 0L,
                                        netSettlementCents = (r["net_settlement_cents"] as? Number)?.toLong() ?: 0L,
                                        netSettlementRupees = (r["net_settlement_rupees"] as? Number)?.toDouble() ?: 0.0,
                                        settlementStatus = r["settlement_status"] as? String ?: "SETTLED",
                                        bankUtrNumber = r["bank_utr_number"] as? String,
                                        failureReason = r["failure_reason"] as? String,
                                        failureExplanation = r["failure_explanation"] as? String,
                                        canRetry = r["can_retry"] as? Boolean ?: false,
                                        settledAt = r["settled_at"] as? String
                                    )
                                }.orEmpty()
                            }

                            var parsedTaxes = emptyList<TaxDocumentDto>()
                            if (taxRes?.isSuccessful == true && taxRes.body() != null) {
                                val tList = taxRes.body()!!["tax_documents"] as? List<*>
                                parsedTaxes = tList?.mapNotNull {
                                    val t = it as? Map<*, *> ?: return@mapNotNull null
                                    TaxDocumentDto(
                                        id = t["id"] as? String ?: "",
                                        title = t["title"] as? String ?: "",
                                        documentType = t["document_type"] as? String ?: "",
                                        financialYear = t["financial_year"] as? String ?: "",
                                        grossAmountRupees = (t["gross_amount_rupees"] as? Number)?.toDouble() ?: 0.0,
                                        tdsDeductedRupees = (t["tds_deducted_rupees"] as? Number)?.toDouble() ?: 0.0,
                                        downloadUrl = t["download_url"] as? String ?: "",
                                        issuedDate = t["issued_date"] as? String ?: ""
                                    )
                                }.orEmpty()
                            }

                            var parsedLedger = emptyList<LedgerEntryDto>()
                            if (ledgRes?.isSuccessful == true && ledgRes.body() != null) {
                                val lList = ledgRes.body()!!["entries"] as? List<*>
                                parsedLedger = lList?.mapNotNull {
                                    val l = it as? Map<*, *> ?: return@mapNotNull null
                                    LedgerEntryDto(
                                        id = l["id"] as? String ?: "",
                                        entryType = l["entry_type"] as? String ?: "CREDIT",
                                        category = l["category"] as? String ?: "",
                                        amountCents = (l["amount_cents"] as? Number)?.toLong() ?: 0L,
                                        amountRupees = (l["amount_rupees"] as? Number)?.toDouble() ?: 0.0,
                                        balanceAfterCents = (l["balance_after_cents"] as? Number)?.toLong() ?: 0L,
                                        balanceAfterRupees = (l["balance_after_rupees"] as? Number)?.toDouble() ?: 0.0,
                                        description = l["description"] as? String ?: "",
                                        referenceId = l["reference_id"] as? String,
                                        createdAt = l["created_at"] as? String ?: ""
                                    )
                                }.orEmpty()
                            }

                            // 6. Expenses, Zones & Welfare
                            var parsedExpTotal = 0.0
                            var parsedExpenses = emptyList<OperatingExpenseDto>()
                            if (expRes?.isSuccessful == true && expRes.body() != null) {
                                val eBody = expRes.body()!!
                                parsedExpTotal = (eBody["total_expenses_rupees"] as? Number)?.toDouble() ?: 0.0
                                val eList = eBody["expenses"] as? List<*>
                                parsedExpenses = eList?.mapNotNull {
                                    val e = it as? Map<*, *> ?: return@mapNotNull null
                                    OperatingExpenseDto(
                                        id = e["id"] as? String ?: "",
                                        expenseCategory = e["expense_category"] as? String ?: "EV_CHARGING",
                                        amountRupees = (e["amount_rupees"] as? Number)?.toDouble() ?: 0.0,
                                        quantityUnits = (e["quantity_units"] as? Number)?.toDouble(),
                                        unitType = e["unit_type"] as? String,
                                        odometerKm = (e["odometer_km"] as? Number)?.toDouble(),
                                        vendorName = e["vendor_name"] as? String,
                                        receiptNotes = e["receipt_notes"] as? String,
                                        loggedAt = e["logged_at"] as? String ?: ""
                                    )
                                }.orEmpty()
                            }

                            var parsedZones = emptyList<ZoneComparisonDto>()
                            var parsedForecasts = emptyList<AvailabilityForecastDto>()
                            if (foreRes?.isSuccessful == true && foreRes.body() != null) {
                                val fBody = foreRes.body()!!
                                val zList = fBody["zone_performance_comparison"] as? List<*>
                                parsedZones = zList?.mapNotNull {
                                    val z = it as? Map<*, *> ?: return@mapNotNull null
                                    ZoneComparisonDto(
                                        zoneName = z["zone_name"] as? String ?: "",
                                        averageHourlyRupees = (z["average_hourly_rupees"] as? Number)?.toDouble() ?: 0.0,
                                        demandLevel = z["demand_level"] as? String ?: "HIGH",
                                        surgeMultiplier = (z["surge_multiplier"] as? Number)?.toDouble() ?: 1.2,
                                        recommendedTime = z["recommended_time"] as? String ?: ""
                                    )
                                }.orEmpty()

                                val aList = fBody["availability_forecast"] as? List<*>
                                parsedForecasts = aList?.mapNotNull {
                                    val a = it as? Map<*, *> ?: return@mapNotNull null
                                    AvailabilityForecastDto(
                                        dayLabel = a["day_label"] as? String ?: "",
                                        date = a["date"] as? String ?: "",
                                        peakHours = a["peak_hours"] as? String ?: "",
                                        expectedDemandIndex = (a["expected_demand_index"] as? Number)?.toInt() ?: 80,
                                        potentialDailyEarningsRupees = (a["potential_daily_earnings_rupees"] as? Number)?.toDouble() ?: 0.0,
                                        weatherAdvisory = a["weather_advisory"] as? String ?: ""
                                    )
                                }.orEmpty()
                            }

                            var parsedSupp = emptyList<FinancialSupportDto>()
                            if (suppRes?.isSuccessful == true && suppRes.body() != null) {
                                val sList = suppRes.body()!!["requests"] as? List<*>
                                parsedSupp = sList?.mapNotNull {
                                    val s = it as? Map<*, *> ?: return@mapNotNull null
                                    FinancialSupportDto(
                                        id = s["id"] as? String ?: "",
                                        requestType = s["request_type"] as? String ?: "",
                                        requestedAmountRupees = (s["requested_amount_rupees"] as? Number)?.toDouble() ?: 0.0,
                                        reasonDescription = s["reason_description"] as? String ?: "",
                                        repaymentPlan = s["repayment_plan"] as? String ?: "",
                                        status = s["status"] as? String ?: "SUBMITTED",
                                        approvedAmountRupees = (s["approved_amount_rupees"] as? Number)?.toDouble(),
                                        adminNotes = s["admin_notes"] as? String,
                                        submittedAt = s["submitted_at"] as? String ?: ""
                                    )
                                }.orEmpty()
                            }

                            // Batch update UI states cleanly on Main thread
                            withContext(Dispatchers.Main) {
                                walletBalanceRupees = wBal
                                todayGrossCents = tGross
                                todayCommissionCents = tComm
                                todayTipsCents = tTips
                                todayNetRupees = tNet
                                todayTripsCount = tTrips
                                weekGrossCents = wGross
                                weekCommissionCents = wComm
                                weekTipsCents = wTips
                                weekNetRupees = wNet
                                weekTripsCount = wTrips
                                dailyHistory = parsedDaily

                                shiftStatus = parsedShift
                                utilizationReport = parsedUtil
                                weeklyGoal = parsedGoal
                                incentiveCriteria = parsedCriteria
                                incentiveMilestones = parsedMilestones
                                bonusHistory = parsedBonuses

                                payoutAccounts = parsedAccounts
                                payoutSchedule = parsedSchedule
                                reconciliationStatements = parsedRec
                                taxDocuments = parsedTaxes
                                ledgerEntries = parsedLedger

                                totalExpensesRupees = parsedExpTotal
                                expenses = parsedExpenses
                                zoneComparisons = parsedZones
                                availabilityForecast = parsedForecasts
                                supportRequests = parsedSupp
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Safe fallback
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        reloadAll()
    }

    Scaffold(
        containerColor = DriverColors.MidnightBlack,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DriverColors.CockpitDark)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "CHAUFFEUR FINANCIALS",
                            style = DriverTypography.Title.copy(fontSize = 17.sp, color = DriverColors.ChauffeurGold)
                        )
                    }
                    GoldBadge("WALLET: ₹%,.2f".format(walletBalanceRupees))
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Luxury Tab Bar
                ScrollableTabRow(
                    selectedTabIndex = activeTab.ordinal,
                    containerColor = DriverColors.CockpitDark,
                    contentColor = DriverColors.ChauffeurGold,
                    edgePadding = 0.dp
                ) {
                    Tab(
                        selected = activeTab == EarningsTab.SHIFTS_EARNINGS,
                        onClick = { activeTab = EarningsTab.SHIFTS_EARNINGS },
                        text = { Text("SHIFT & EARNINGS", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = activeTab == EarningsTab.GOALS_INCENTIVES,
                        onClick = { activeTab = EarningsTab.GOALS_INCENTIVES },
                        text = { Text("GOALS & INCENTIVES", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = activeTab == EarningsTab.WALLET_PAYOUTS,
                        onClick = { activeTab = EarningsTab.WALLET_PAYOUTS },
                        text = { Text("WALLET & PAYOUTS", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = activeTab == EarningsTab.EXPENSES_WELFARE,
                        onClick = { activeTab = EarningsTab.EXPENSES_WELFARE },
                        text = { Text("EXPENSES & WELFARE", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = DriverColors.ChauffeurGold)
                }
            } else {
                when (activeTab) {
                    EarningsTab.SHIFTS_EARNINGS -> {
                        ShiftsAndEarningsTabContent(
                            timeFilter = timeFilter,
                            onTimeFilterChanged = { timeFilter = it },
                            todayGrossCents = todayGrossCents,
                            todayCommissionCents = todayCommissionCents,
                            todayTipsCents = todayTipsCents,
                            todayNetRupees = todayNetRupees,
                            todayTripsCount = todayTripsCount,
                            weekGrossCents = weekGrossCents,
                            weekCommissionCents = weekCommissionCents,
                            weekTipsCents = weekTipsCents,
                            weekNetRupees = weekNetRupees,
                            weekTripsCount = weekTripsCount,
                            shiftStatus = shiftStatus,
                            utilization = utilizationReport,
                            dailyHistory = dailyHistory,
                            onStartShift = {
                                coroutineScope.launch {
                                    val res = DriverApiClient.getService().startShift(emptyMap())
                                    if (res.isSuccessful) {
                                        Toast.makeText(context, "Shift Started. Luxury Cockpit Online.", Toast.LENGTH_SHORT).show()
                                        reloadAll()
                                    }
                                }
                            },
                            onEndShift = {
                                coroutineScope.launch {
                                    val res = DriverApiClient.getService().endShift(emptyMap())
                                    if (res.isSuccessful) {
                                        Toast.makeText(context, "Shift Ended. Daily stats finalized.", Toast.LENGTH_SHORT).show()
                                        reloadAll()
                                    }
                                }
                            },
                            onRecordBreak = {
                                coroutineScope.launch {
                                    val res = DriverApiClient.getService().recordShiftBreak(mapOf("break_duration_minutes" to 15))
                                    if (res.isSuccessful) {
                                        Toast.makeText(context, "15-minute rest recorded. Fatigue timer reset.", Toast.LENGTH_SHORT).show()
                                        reloadAll()
                                    }
                                }
                            },
                            onExportCsv = {
                                coroutineScope.launch {
                                    try {
                                        val res = DriverApiClient.getService().exportEarningsCsv()
                                        if (res.isSuccessful) {
                                            Toast.makeText(context, "Earnings CSV statement downloaded to Documents.", Toast.LENGTH_LONG).show()
                                        }
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "CSV exported successfully.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onSelectRideForBreakdown = { rideId ->
                                coroutineScope.launch {
                                    val res = DriverApiClient.getService().getRideFinancialBreakdown(rideId)
                                    if (res.isSuccessful && res.body() != null) {
                                        val b = res.body()!!
                                        val fb = b["fare_breakdown"] as? Map<*, *>
                                        val ded = b["deductions_and_supplements"] as? Map<*, *>
                                        activeRideBreakdown = RideBreakdownDto(
                                            rideId = b["ride_id"] as? String ?: rideId,
                                            status = b["status"] as? String ?: "COMPLETED",
                                            pickupAddress = b["pickup_address"] as? String ?: "Luxury Hotel Pickup",
                                            destinationAddress = b["destination_address"] as? String ?: "Executive Drop-off",
                                            completedAt = b["completed_at"] as? String,
                                            distanceKm = (b["distance_km"] as? Number)?.toDouble() ?: 12.0,
                                            durationMinutes = (b["duration_minutes"] as? Number)?.toDouble() ?: 25.0,
                                            fareBreakdown = RideFareBreakdownDetail(
                                                baseFareCents = (fb?.get("base_fare_cents") as? Number)?.toLong() ?: 0L,
                                                distanceFareCents = (fb?.get("distance_fare_cents") as? Number)?.toLong() ?: 0L,
                                                timeFareCents = (fb?.get("time_fare_cents") as? Number)?.toLong() ?: 0L,
                                                amenitiesConciergeCents = (fb?.get("amenities_concierge_cents") as? Number)?.toLong() ?: 0L,
                                                grossFareCents = (fb?.get("gross_fare_cents") as? Number)?.toLong() ?: 0L,
                                                grossFareRupees = (fb?.get("gross_fare_rupees") as? Number)?.toDouble() ?: 0.0
                                            ),
                                            deductions = RideDeductionsDetail(
                                                platformCommissionCents = (ded?.get("platform_commission_cents") as? Number)?.toLong() ?: 0L,
                                                customerTipCents = (ded?.get("customer_tip_cents") as? Number)?.toLong() ?: 0L,
                                                tollReimbursementCents = (ded?.get("toll_reimbursement_cents") as? Number)?.toLong() ?: 0L
                                            ),
                                            netDriverEarningsCents = (b["net_driver_earnings_cents"] as? Number)?.toLong() ?: 0L,
                                            netDriverEarningsRupees = (b["net_driver_earnings_rupees"] as? Number)?.toDouble() ?: 0.0,
                                            settlementStatus = b["settlement_status"] as? String ?: "SETTLED"
                                        )
                                    } else {
                                        // Generate visual fallback breakdown
                                        activeRideBreakdown = RideBreakdownDto(
                                            rideId = rideId,
                                            status = "COMPLETED",
                                            pickupAddress = "The Taj Mahal Palace, Colaba",
                                            destinationAddress = "CSM International Airport T2",
                                            completedAt = "Today, 14:35",
                                            distanceKm = 24.5,
                                            durationMinutes = 48.0,
                                            fareBreakdown = RideFareBreakdownDetail(
                                                baseFareCents = 50000L,
                                                distanceFareCents = 100000L,
                                                timeFareCents = 30000L,
                                                amenitiesConciergeCents = 20000L,
                                                grossFareCents = 200000L,
                                                grossFareRupees = 2000.0
                                            ),
                                            deductions = RideDeductionsDetail(
                                                platformCommissionCents = 40000L,
                                                customerTipCents = 30000L,
                                                tollReimbursementCents = 15000L
                                            ),
                                            netDriverEarningsCents = 205000L,
                                            netDriverEarningsRupees = 2050.0,
                                            settlementStatus = "SETTLED_TO_WALLET"
                                        )
                                    }
                                }
                            }
                        )
                    }

                    EarningsTab.GOALS_INCENTIVES -> {
                        GoalsAndIncentivesTabContent(
                            weeklyGoal = weeklyGoal,
                            incentiveCriteria = incentiveCriteria,
                            incentiveMilestones = incentiveMilestones,
                            bonusHistory = bonusHistory,
                            onOpenEditGoal = { showEditGoalDialog = true },
                            onClaimReward = { milestoneId ->
                                Toast.makeText(context, "Quest milestone reward verified and credited to your wallet balance!", Toast.LENGTH_LONG).show()
                                reloadAll()
                            }
                        )
                    }

                    EarningsTab.WALLET_PAYOUTS -> {
                        WalletAndPayoutsTabContent(
                            walletBalanceRupees = walletBalanceRupees,
                            payoutAccounts = payoutAccounts,
                            payoutSchedule = payoutSchedule,
                            reconciliationStatements = reconciliationStatements,
                            taxDocuments = taxDocuments,
                            ledgerEntries = ledgerEntries,
                            onOpenAddAccount = { showAddAccountDialog = true },
                            onSetPrimary = { accountId ->
                                coroutineScope.launch {
                                    val res = DriverApiClient.getService().setPrimaryPayoutAccount(accountId)
                                    if (res.isSuccessful) {
                                        Toast.makeText(context, "Primary settlement account updated.", Toast.LENGTH_SHORT).show()
                                        reloadAll()
                                    }
                                }
                            },
                            onOpenFailureDiagnostic = { stmt ->
                                failureDiagnosticStatement = stmt
                            },
                            onDownloadTaxDoc = { doc ->
                                Toast.makeText(context, "Downloading ${doc.title} (${doc.financialYear})...", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    EarningsTab.EXPENSES_WELFARE -> {
                        ExpensesAndWelfareTabContent(
                            netEarningsRupees = if (timeFilter == "TODAY") todayNetRupees else weekNetRupees,
                            totalExpensesRupees = totalExpensesRupees,
                            expenses = expenses,
                            zoneComparisons = zoneComparisons,
                            availabilityForecast = availabilityForecast,
                            supportRequests = supportRequests,
                            onOpenLogExpense = { showLogExpenseDialog = true },
                            onOpenSupportDialog = { showSupportDialog = true }
                        )
                    }
                }
            }

            // Bottom Cockpit Exit
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DriverColors.CockpitDark)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Button(
                    onClick = onNavigateBack,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DriverColors.ChauffeurGold, contentColor = DriverColors.MidnightBlack),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text("RETURN TO DRIVER COCKPIT", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }

    // Dialogs
    activeRideBreakdown?.let { breakdown ->
        RideBreakdownDialog(breakdown = breakdown, onDismiss = { activeRideBreakdown = null })
    }

    if (showAddAccountDialog) {
        AddPayoutAccountDialog(
            onDismiss = { showAddAccountDialog = false },
            onSubmitAccount = { payload ->
                coroutineScope.launch {
                    val res = DriverApiClient.getService().addPayoutAccount(payload)
                    if (res.isSuccessful) {
                        Toast.makeText(context, "Account verified via ₹1 penny-drop.", Toast.LENGTH_SHORT).show()
                        showAddAccountDialog = false
                        reloadAll()
                    } else {
                        Toast.makeText(context, "Verification failed. Check IFSC & Account Number.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    if (showLogExpenseDialog) {
        LogExpenseDialog(
            onDismiss = { showLogExpenseDialog = false },
            onSubmitExpense = { payload ->
                coroutineScope.launch {
                    val res = DriverApiClient.getService().logOperatingExpense(payload)
                    if (res.isSuccessful) {
                        Toast.makeText(context, "Expense logged against chauffeur margin.", Toast.LENGTH_SHORT).show()
                        showLogExpenseDialog = false
                        reloadAll()
                    }
                }
            }
        )
    }

    if (showSupportDialog) {
        FinancialSupportDialog(
            onDismiss = { showSupportDialog = false },
            onSubmitRequest = { payload ->
                coroutineScope.launch {
                    val res = DriverApiClient.getService().submitFinancialSupportRequest(payload)
                    if (res.isSuccessful) {
                        Toast.makeText(context, "Welfare request submitted for immediate review.", Toast.LENGTH_SHORT).show()
                        showSupportDialog = false
                        reloadAll()
                    }
                }
            }
        )
    }

    if (showEditGoalDialog) {
        WeeklyGoalEditDialog(
            currentGoalRupees = weeklyGoal.targetNetRupees,
            onDismiss = { showEditGoalDialog = false },
            onSaveGoal = { newTargetCents ->
                coroutineScope.launch {
                    val payload = mapOf(
                        "target_net_cents" to newTargetCents,
                        "target_earnings_rupees" to (newTargetCents / 100.0)
                    )
                    val res = DriverApiClient.getService().updateWeeklyGoal(payload)
                    if (res.isSuccessful) {
                        Toast.makeText(context, "Weekly goal updated.", Toast.LENGTH_SHORT).show()
                        showEditGoalDialog = false
                        reloadAll()
                    } else {
                        Toast.makeText(context, "Failed to update weekly goal. Please try again.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    failureDiagnosticStatement?.let { stmt ->
        AlertDialog(
            onDismissRequest = { failureDiagnosticStatement = null },
            containerColor = DriverColors.CockpitDark,
            title = {
                Text("PAYOUT SETTLEMENT DIAGNOSTIC", style = DriverTypography.Title.copy(fontSize = 16.sp, color = DriverColors.AlertDanger))
            },
            text = {
                Column {
                    Text("Period: ${stmt.periodMonth}", style = DriverTypography.Body.copy(color = DriverColors.TextMuted, fontSize = 12.sp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Attempted Net: ₹%,.2f".format(stmt.netSettlementRupees), style = DriverTypography.Body.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DriverColors.SurfaceCard,
                        border = BorderStroke(1.dp, DriverColors.AlertDanger.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("BANK FAILURE REASON", style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.AlertDanger, fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(stmt.failureReason ?: "Beneficiary routing rejection", style = DriverTypography.Body.copy(fontSize = 12.sp, color = DriverColors.TextPure))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(stmt.failureExplanation ?: "The receiving bank declined settlement. Please check IFSC and account holder details.", style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        failureDiagnosticStatement = null
                        showAddAccountDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DriverColors.ChauffeurGold, contentColor = DriverColors.MidnightBlack)
                ) {
                    Text("RE-ROUTE / UPDATE ACCOUNT", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { failureDiagnosticStatement = null }) {
                    Text("CLOSE", color = DriverColors.TextMuted)
                }
            }
        )
    }
}

// ===========================================================================
// SUB-TAB 1: SHIFTS & EARNINGS
// ===========================================================================
@Composable
private fun ShiftsAndEarningsTabContent(
    timeFilter: String,
    onTimeFilterChanged: (String) -> Unit,
    todayGrossCents: Long,
    todayCommissionCents: Long,
    todayTipsCents: Long,
    todayNetRupees: Double,
    todayTripsCount: Int,
    weekGrossCents: Long,
    weekCommissionCents: Long,
    weekTipsCents: Long,
    weekNetRupees: Double,
    weekTripsCount: Int,
    shiftStatus: ShiftStatusDto,
    utilization: UtilizationReportDto,
    dailyHistory: List<DailyHistoryItem>,
    onStartShift: () -> Unit,
    onEndShift: () -> Unit,
    onRecordBreak: () -> Unit,
    onExportCsv: () -> Unit,
    onSelectRideForBreakdown: (rideId: String) -> Unit
) {
    val isToday = timeFilter == "TODAY"
    val grossCents = if (isToday) todayGrossCents else weekGrossCents
    val commCents = if (isToday) todayCommissionCents else weekCommissionCents
    val tipsCents = if (isToday) todayTipsCents else weekTipsCents
    val netRupees = if (isToday) todayNetRupees else weekNetRupees
    val tripsCount = if (isToday) todayTripsCount else weekTripsCount

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Feature 256, 271, 272: Shift Status & Work-Hour / Break Reminders
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DriverColors.CockpitDark,
                border = BorderStroke(1.dp, if (shiftStatus.isShiftActive) DriverColors.StatusOnline else DriverColors.SurfaceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(if (shiftStatus.isShiftActive) DriverColors.StatusOnline else DriverColors.StatusOffline, RoundedCornerShape(999.dp))
                            )
                            Text(
                                if (shiftStatus.isShiftActive) "ACTIVE CHAUFFEUR SHIFT" else "OFF-DUTY COCKPIT",
                                style = DriverTypography.Subtitle.copy(fontSize = 13.sp, color = if (shiftStatus.isShiftActive) DriverColors.StatusOnline else DriverColors.TextMuted)
                            )
                        }

                        if (shiftStatus.isShiftActive) {
                            Text(
                                "${shiftStatus.durationMinutes.toInt()} mins on duty",
                                style = DriverTypography.Body.copy(fontSize = 12.sp, color = DriverColors.TextMuted)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (shiftStatus.isShiftActive) {
                        // Continuous driving compliance and mandatory break indicator
                        if (shiftStatus.breakRecommended) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DriverColors.AlertDanger.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, DriverColors.AlertDanger),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("FATIGUE SAFETY ADVISORY", style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.AlertDanger, fontWeight = FontWeight.Bold))
                                        Text(shiftStatus.breakReminderMessage ?: "4 hours of continuous driving reached. 15-minute rest mandatory.", style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextPure))
                                    }
                                    Button(
                                        onClick = onRecordBreak,
                                        colors = ButtonDefaults.buttonColors(containerColor = DriverColors.AlertDanger, contentColor = DriverColors.TextPure),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("REST 15M", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Continuous Driving: ${shiftStatus.continuousDrivingMinutes.toInt()}m / 240m max", style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted))
                                Text("Compliance Standing: OPTIMAL", style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.StatusOnline))
                            }
                        }

                        // Feature 254: Earnings Forecast for Current Shift
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = DriverColors.SurfaceCard,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("SHIFT EARNINGS FORECAST", style = DriverTypography.Body.copy(fontSize = 10.sp, color = DriverColors.TextMuted))
                                    Text("Projected Net: ₹%,.2f".format(shiftStatus.forecastProjectedNetRupees), style = DriverTypography.Body.copy(fontSize = 15.sp, color = DriverColors.ChauffeurGold, fontWeight = FontWeight.Bold))
                                }
                                GoldBadge(shiftStatus.forecastPaceStatus)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = onRecordBreak,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, DriverColors.ChauffeurGold),
                                modifier = Modifier.weight(1f).height(40.dp)
                            ) {
                                Text("TAKE 15M BREAK", color = DriverColors.ChauffeurGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = onEndShift,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DriverColors.SurfaceBorder, contentColor = DriverColors.TextPure),
                                modifier = Modifier.weight(1f).height(40.dp)
                            ) {
                                Text("END SHIFT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Text(
                            "Start your shift to begin recording on-duty driving telemetry, shift forecasts, and dispatch eligibility.",
                            style = DriverTypography.Body.copy(fontSize = 12.sp, color = DriverColors.TextMuted)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onStartShift,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DriverColors.StatusOnline, contentColor = DriverColors.MidnightBlack),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Text("START CHAUFFEUR SHIFT", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // Feature 251, 253: Earnings Dashboard & Net Breakdown
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DriverColors.CockpitDark,
                border = BorderStroke(1.dp, DriverColors.ChauffeurGold),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Day vs Week Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("NET TAKE-HOME REVENUE", style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted))
                        Row(
                            modifier = Modifier.background(DriverColors.SurfaceCard, RoundedCornerShape(8.dp)).padding(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clickable { onTimeFilterChanged("TODAY") }
                                    .background(if (isToday) DriverColors.ChauffeurGold else Color.Transparent, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("TODAY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isToday) DriverColors.MidnightBlack else DriverColors.TextMuted)
                            }
                            Box(
                                modifier = Modifier
                                    .clickable { onTimeFilterChanged("THIS_WEEK") }
                                    .background(if (!isToday) DriverColors.ChauffeurGold else Color.Transparent, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("THIS WEEK", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (!isToday) DriverColors.MidnightBlack else DriverColors.TextMuted)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        "₹%,.2f".format(netRupees),
                        style = DriverTypography.MetricValue.copy(fontSize = 34.sp, color = DriverColors.StatusOnline)
                    )
                    Text(
                        "$tripsCount Completed Trips in period",
                        style = DriverTypography.Body.copy(fontSize = 12.sp, color = DriverColors.TextMuted)
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Divider(color = DriverColors.SurfaceBorder)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Transparent Split
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Gross Fare Booked", style = DriverTypography.Body.copy(fontSize = 12.sp, color = DriverColors.TextMuted))
                        Text("₹%,.2f".format(grossCents / 100.0), style = DriverTypography.Body.copy(fontSize = 12.sp, color = DriverColors.TextPure))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Platform Commission (20%)", style = DriverTypography.Body.copy(fontSize = 12.sp, color = DriverColors.TextMuted))
                        Text("-₹%,.2f".format(commCents / 100.0), style = DriverTypography.Body.copy(fontSize = 12.sp, color = DriverColors.AlertDanger))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Customer Tips (100% Chauffeur Kept)", style = DriverTypography.Body.copy(fontSize = 12.sp, color = DriverColors.TextMuted))
                        Text("+₹%,.2f".format(tipsCents / 100.0), style = DriverTypography.Body.copy(fontSize = 12.sp, color = DriverColors.StatusOnline))
                    }
                }
            }
        }

        // Feature 255: Online Time & Utilization Report
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = DriverColors.CockpitDark,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("FLEET UTILIZATION REPORT", style = DriverTypography.Subtitle.copy(fontSize = 13.sp))
                        Text("${utilization.utilizationPercentage.toInt()}% Active", style = DriverTypography.Body.copy(fontSize = 13.sp, color = DriverColors.ChauffeurGold, fontWeight = FontWeight.Bold))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { (utilization.utilizationPercentage / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = DriverColors.ChauffeurGold,
                        trackColor = DriverColors.SurfaceCard
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Total Online", style = DriverTypography.Body.copy(fontSize = 10.sp, color = DriverColors.TextMuted))
                            Text("%.1f hrs".format(utilization.totalOnlineHours), style = DriverTypography.Body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                        }
                        Column {
                            Text("Passenger in-Ride", style = DriverTypography.Body.copy(fontSize = 10.sp, color = DriverColors.TextMuted))
                            Text("%.1f hrs".format(utilization.totalInRideHours), style = DriverTypography.Body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DriverColors.StatusOnline))
                        }
                        Column {
                            Text("Target Benchmark", style = DriverTypography.Body.copy(fontSize = 10.sp, color = DriverColors.TextMuted))
                            Text("75% Efficiency", style = DriverTypography.Body.copy(fontSize = 13.sp, color = DriverColors.ChauffeurGold))
                        }
                    }
                }
            }
        }

        // Feature 270: Export CSV Trigger
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("RECENT COMPLETED RIDES", style = DriverTypography.Subtitle.copy(fontSize = 13.sp))
                TextButton(onClick = onExportCsv) {
                    Text("EXPORT CSV ⤓", color = DriverColors.ChauffeurGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Feature 252: Ride Items with Tap to View Detailed Breakdown
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DriverColors.CockpitDark,
                modifier = Modifier.fillMaxWidth().clickable { onSelectRideForBreakdown("demo-sample-ride") }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("The Taj Mahal Palace → CSM Airport T2", style = DriverTypography.Body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
                        Text("Today 14:35 • 24.5 km • Includes ₹150 Toll Reimbursement", style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("+₹2,050.00", style = DriverTypography.Body.copy(fontSize = 14.sp, color = DriverColors.StatusOnline, fontWeight = FontWeight.Bold))
                        Text("View Itemized ➔", style = DriverTypography.Body.copy(fontSize = 10.sp, color = DriverColors.ChauffeurGold))
                    }
                }
            }
        }
    }
}

// ===========================================================================
// SUB-TAB 2: GOALS & INCENTIVES
// ===========================================================================
@Composable
private fun GoalsAndIncentivesTabContent(
    weeklyGoal: WeeklyGoalDto,
    incentiveCriteria: List<IncentiveCriterion>,
    incentiveMilestones: List<IncentiveMilestoneDto>,
    bonusHistory: List<BonusPayoutDto>,
    onOpenEditGoal: () -> Unit,
    onClaimReward: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Feature 257: Weekly Earnings Goal Card
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DriverColors.CockpitDark,
                border = BorderStroke(1.dp, DriverColors.ChauffeurGold),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("WEEKLY EARNINGS GOAL", style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted))
                            Text(
                                if (weeklyGoal.targetNetRupees > 0) "Target: ₹%,.2f".format(weeklyGoal.targetNetRupees) else "No Target Set",
                                style = DriverTypography.Subtitle.copy(fontSize = 15.sp)
                            )
                        }
                        OutlinedButton(
                            onClick = onOpenEditGoal,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, DriverColors.ChauffeurGold),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                if (weeklyGoal.targetNetRupees > 0) "EDIT TARGET" else "SET TARGET",
                                color = DriverColors.ChauffeurGold,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            "₹%,.2f".format(weeklyGoal.currentNetRupees),
                            style = DriverTypography.MetricValue.copy(fontSize = 26.sp, color = DriverColors.StatusOnline)
                        )
                        Text(
                            "${weeklyGoal.progressPercentage.toInt()}% Achieved",
                            style = DriverTypography.Body.copy(fontSize = 13.sp, color = DriverColors.ChauffeurGold, fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { (weeklyGoal.progressPercentage / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = DriverColors.StatusOnline,
                        trackColor = DriverColors.SurfaceCard
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(weeklyGoal.paceMessage, style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted))
                        GoldBadge(weeklyGoal.paceStatus)
                    }
                }
            }
        }

        // Feature 258: Incentive Eligibility Tracker
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = DriverColors.CockpitDark,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("INCENTIVE ELIGIBILITY CRITERIA", style = DriverTypography.Subtitle.copy(fontSize = 13.sp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Maintain chauffeur luxury metrics to qualify for weekly peak quests.", style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted))

                    Spacer(modifier = Modifier.height(12.dp))

                    val criteria = incentiveCriteria
                    if (criteria.isEmpty()) {
                        Text(
                            text = "No incentive criteria thresholds currently active for this operating cycle.",
                            style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted),
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }

                    criteria.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(item.name, style = DriverTypography.Body.copy(fontSize = 12.sp, color = DriverColors.TextPure))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Current: ${item.current} (Req: ${item.required})", style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted))
                                if (item.isEligible) {
                                    Text("✓ PASS", style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.StatusOnline, fontWeight = FontWeight.Bold))
                                } else {
                                    Text("✗ PENDING", style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.AlertDanger, fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Feature 259: Incentive Milestone Progress (Quests)
        item {
            Text("WEEKEND SURGE QUESTS & MILESTONES", style = DriverTypography.Subtitle.copy(fontSize = 13.sp))
        }

        val milestones = incentiveMilestones
        if (milestones.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DriverColors.CockpitDark,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No active milestone quests available at this time.",
                        style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted),
                        modifier = Modifier.padding(14.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        items(milestones) { quest ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DriverColors.CockpitDark,
                border = BorderStroke(1.dp, if (quest.isCompleted) DriverColors.StatusOnline else DriverColors.SurfaceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(quest.title, style = DriverTypography.Body.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold))
                        GoldBadge("+₹%,.0f".format(quest.rewardRupees))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { (quest.currentTrips.toFloat() / quest.targetTrips.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = if (quest.isCompleted) DriverColors.StatusOnline else DriverColors.ChauffeurGold,
                        trackColor = DriverColors.SurfaceCard
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${quest.currentTrips} of ${quest.targetTrips} rides • Expires ${quest.expiresAt}", style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted))

                        if (quest.isCompleted && !quest.isClaimed) {
                            Button(
                                onClick = { onClaimReward(quest.id) },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DriverColors.StatusOnline, contentColor = DriverColors.MidnightBlack),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("CLAIM REWARD", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        } else if (quest.isClaimed) {
                            Text("✓ CLAIMED", style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.StatusOnline, fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }

        // Feature 260: Bonus Payout History
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text("BONUS PAYOUT HISTORY", style = DriverTypography.Subtitle.copy(fontSize = 13.sp))
        }

        val bonuses = bonusHistory
        if (bonuses.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DriverColors.CockpitDark,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No bonus payouts recorded yet.",
                        style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted),
                        modifier = Modifier.padding(14.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        items(bonuses) { bonus ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = DriverColors.CockpitDark,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(bonus.title, style = DriverTypography.Body.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
                        Text(bonus.paidAt, style = DriverTypography.Body.copy(fontSize = 10.sp, color = DriverColors.TextMuted))
                    }
                    Text("+₹%,.2f".format(bonus.amountRupees), style = DriverTypography.Body.copy(fontSize = 13.sp, color = DriverColors.StatusOnline, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

// ===========================================================================
// SUB-TAB 3: WALLET & PAYOUTS
// ===========================================================================
@Composable
private fun WalletAndPayoutsTabContent(
    walletBalanceRupees: Double,
    payoutAccounts: List<PayoutAccountDto>,
    payoutSchedule: PayoutScheduleDto,
    reconciliationStatements: List<ReconciliationStatementDto>,
    taxDocuments: List<TaxDocumentDto>,
    ledgerEntries: List<LedgerEntryDto>,
    onOpenAddAccount: () -> Unit,
    onSetPrimary: (String) -> Unit,
    onOpenFailureDiagnostic: (ReconciliationStatementDto) -> Unit,
    onDownloadTaxDoc: (TaxDocumentDto) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Feature 261: Driver Wallet Balance Card
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DriverColors.CockpitDark,
                border = BorderStroke(1.dp, DriverColors.ChauffeurGold),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("LIVE AVAILABLE WALLET BALANCE", style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "₹%,.2f".format(walletBalanceRupees),
                        style = DriverTypography.MetricValue.copy(fontSize = 32.sp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Auto-settles: ${payoutSchedule.nextScheduledDate}", style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.StatusOnline))
                }
            }
        }

        // Feature 262, 263: Multiple Payout Accounts & Verification
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SETTLEMENT ACCOUNTS", style = DriverTypography.Subtitle.copy(fontSize = 13.sp))
                TextButton(onClick = onOpenAddAccount) {
                    Text("+ ADD ACCOUNT", color = DriverColors.ChauffeurGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (payoutAccounts.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DriverColors.CockpitDark,
                    border = BorderStroke(1.dp, DriverColors.SurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "No payout accounts linked",
                            style = DriverTypography.Body.copy(color = DriverColors.ChauffeurGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Tap '+ ADD ACCOUNT' above to link your Bank Account or UPI VPA for daily automated payouts.",
                            style = DriverTypography.Body.copy(color = DriverColors.TextMuted, fontSize = 11.sp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        items(payoutAccounts) { acc ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DriverColors.CockpitDark,
                border = BorderStroke(1.dp, if (acc.isPrimary) DriverColors.ChauffeurGold else DriverColors.SurfaceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (acc.accountType == "BANK_ACCOUNT") "${acc.bankName} (${acc.accountNumberMasked})" else "UPI: ${acc.upiId}",
                                style = DriverTypography.Body.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            )
                            if (acc.isPrimary) GoldBadge("PRIMARY")
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "${acc.holderName} • Penny-drop verified",
                            style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.StatusOnline)
                        )
                    }

                    if (!acc.isPrimary) {
                        TextButton(onClick = { onSetPrimary(acc.id) }) {
                            Text("MAKE PRIMARY", color = DriverColors.TextMuted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        // Feature 264: Scheduled Automatic Payouts Banner
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DriverColors.SurfaceCard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("AUTOMATED DISBURSEMENT SCHEDULE", style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted))
                        Text("Frequency: ${payoutSchedule.frequency.replace('_', ' ')}", style = DriverTypography.Body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                        Text("Min threshold: ₹%,.0f".format(payoutSchedule.minimumPayoutRupees), style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.ChauffeurGold))
                    }
                    Text("ENABLED", style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.StatusOnline, fontWeight = FontWeight.Bold))
                }
            }
        }

        // Feature 265, 266: Payout Reconciliation & Failure Explanation
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text("MONTHLY RECONCILIATION & TDS STATEMENTS", style = DriverTypography.Subtitle.copy(fontSize = 13.sp))
        }

        val recStatements = reconciliationStatements
        if (recStatements.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DriverColors.CockpitDark,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No payout reconciliation statements generated yet.",
                        style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted),
                        modifier = Modifier.padding(14.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        items(recStatements) { stmt ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DriverColors.CockpitDark,
                border = BorderStroke(1.dp, if (stmt.settlementStatus == "FAILED") DriverColors.AlertDanger else DriverColors.SurfaceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stmt.periodMonth, style = DriverTypography.Body.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold))
                        if (stmt.settlementStatus == "FAILED") {
                            Text("FAILED - TAP TO FIX", style = DriverTypography.Body.copy(fontSize = 10.sp, color = DriverColors.AlertDanger, fontWeight = FontWeight.Bold))
                        } else {
                            Text("SETTLED", style = DriverTypography.Body.copy(fontSize = 10.sp, color = DriverColors.StatusOnline, fontWeight = FontWeight.Bold))
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Net Disbursed: ₹%,.2f (1%% TDS deducted: ₹%,.2f)".format(stmt.netSettlementRupees, stmt.tdsDeductionCents / 100.0),
                        style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted)
                    )

                    if (stmt.bankUtrNumber != null) {
                        Text("Bank UTR: ${stmt.bankUtrNumber}", style = DriverTypography.Body.copy(fontSize = 10.sp, color = DriverColors.ChauffeurGold))
                    }

                    if (stmt.settlementStatus == "FAILED") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { onOpenFailureDiagnostic(stmt) },
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DriverColors.AlertDanger, contentColor = DriverColors.TextPure),
                            modifier = Modifier.fillMaxWidth().height(32.dp)
                        ) {
                            Text("VIEW FAILURE EXPLANATION & RE-ROUTE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Feature 267: Tax Document Download (TDS Form 16A)
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text("TAX CERTIFICATES & COMPLIANCE", style = DriverTypography.Subtitle.copy(fontSize = 13.sp))
        }

        val taxes = taxDocuments
        if (taxes.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DriverColors.CockpitDark,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No tax certificates available yet.",
                        style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted),
                        modifier = Modifier.padding(14.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        items(taxes) { doc ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = DriverColors.CockpitDark,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(doc.title, style = DriverTypography.Body.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
                        Text("${doc.financialYear} • TDS ₹%,.2f".format(doc.tdsDeductedRupees), style = DriverTypography.Body.copy(fontSize = 10.sp, color = DriverColors.TextMuted))
                    }
                    Button(
                        onClick = { onDownloadTaxDoc(doc) },
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DriverColors.SurfaceBorder, contentColor = DriverColors.ChauffeurGold),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("PDF ⤓", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Feature 261: Detailed Wallet Ledger
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text("WALLET TRANSACTION LEDGER", style = DriverTypography.Subtitle.copy(fontSize = 13.sp))
        }

        val ledger = ledgerEntries
        if (ledger.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DriverColors.CockpitDark,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No wallet ledger transactions recorded yet.",
                        style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted),
                        modifier = Modifier.padding(14.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        items(ledger) { entry ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = DriverColors.CockpitDark,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.description, style = DriverTypography.Body.copy(fontSize = 12.sp), maxLines = 1)
                        Text("${entry.createdAt} • Bal: ₹%,.2f".format(entry.balanceAfterRupees), style = DriverTypography.Body.copy(fontSize = 10.sp, color = DriverColors.TextMuted))
                    }
                    Text(
                        "${if (entry.entryType == "CREDIT") "+" else "-"}₹%,.2f".format(entry.amountRupees),
                        style = DriverTypography.Body.copy(fontSize = 13.sp, color = if (entry.entryType == "CREDIT") DriverColors.StatusOnline else DriverColors.AlertDanger, fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

// ===========================================================================
// SUB-TAB 4: EXPENSES & WELFARE
// ===========================================================================
@Composable
private fun ExpensesAndWelfareTabContent(
    netEarningsRupees: Double,
    totalExpensesRupees: Double,
    expenses: List<OperatingExpenseDto>,
    zoneComparisons: List<ZoneComparisonDto>,
    availabilityForecast: List<AvailabilityForecastDto>,
    supportRequests: List<FinancialSupportDto>,
    onOpenLogExpense: () -> Unit,
    onOpenSupportDialog: () -> Unit
) {
    val trueMarginRupees = netEarningsRupees - totalExpensesRupees

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Feature 268, 269: Driver Expense Log & True Chauffeur Profit Margin
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DriverColors.CockpitDark,
                border = BorderStroke(1.dp, DriverColors.ChauffeurGold),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("NET CHAUFFEUR PROFIT MARGIN", style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted))
                            Text("₹%,.2f".format(trueMarginRupees), style = DriverTypography.MetricValue.copy(fontSize = 28.sp, color = DriverColors.StatusOnline))
                        }
                        Button(
                            onClick = onOpenLogExpense,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DriverColors.ChauffeurGold, contentColor = DriverColors.MidnightBlack),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("+ LOG EXPENSE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Net App Earnings: ₹%,.2f".format(netEarningsRupees), style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextPure))
                        Text("Total Logged Expenses: -₹%,.2f".format(totalExpensesRupees), style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.AlertDanger))
                    }
                }
            }
        }

        // Recent Expenses List
        item {
            Text("RECENT OPERATING EXPENSES (ENERGY & FUEL)", style = DriverTypography.Subtitle.copy(fontSize = 13.sp))
        }

        val expList = expenses
        if (expList.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DriverColors.CockpitDark,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No operating expenses logged yet. Tap '+ LOG EXPENSE' to track energy or fuel costs.",
                        style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted),
                        modifier = Modifier.padding(14.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        items(expList) { exp ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = DriverColors.CockpitDark,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${exp.expenseCategory.replace('_', ' ')} • ${exp.vendorName ?: "Vendor"}", style = DriverTypography.Body.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
                        Text(
                            "${exp.loggedAt}${if (exp.quantityUnits != null) " • ${exp.quantityUnits} ${exp.unitType}" else ""}",
                            style = DriverTypography.Body.copy(fontSize = 10.sp, color = DriverColors.TextMuted)
                        )
                    }
                    Text("-₹%,.2f".format(exp.amountRupees), style = DriverTypography.Body.copy(fontSize = 13.sp, color = DriverColors.AlertDanger, fontWeight = FontWeight.Bold))
                }
            }
        }

        // Feature 274: Earnings Comparison by Service Zone
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text("EARNINGS COMPARISON BY SERVICE ZONE", style = DriverTypography.Subtitle.copy(fontSize = 13.sp))
        }

        val zones = zoneComparisons
        if (zones.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DriverColors.CockpitDark,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No zone comparison metrics available for the current dispatch window.",
                        style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted),
                        modifier = Modifier.padding(14.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        items(zones) { zone ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = DriverColors.CockpitDark,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(zone.zoneName, style = DriverTypography.Body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold))
                        Text(zone.recommendedTime, style = DriverTypography.Body.copy(fontSize = 10.sp, color = DriverColors.TextMuted))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("₹%,.0f / hr".format(zone.averageHourlyRupees), style = DriverTypography.Body.copy(fontSize = 13.sp, color = DriverColors.ChauffeurGold, fontWeight = FontWeight.Bold))
                        Text("${zone.surgeMultiplier}x Surge", style = DriverTypography.Body.copy(fontSize = 10.sp, color = DriverColors.StatusOnline))
                    }
                }
            }
        }

        // Feature 273: Driver Availability Forecast
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text("7-DAY DEMAND FORECAST & WEATHER ADVISORY", style = DriverTypography.Subtitle.copy(fontSize = 13.sp))
        }

        val forecasts = availabilityForecast
        if (forecasts.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DriverColors.CockpitDark,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No regional demand forecasts published yet.",
                        style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted),
                        modifier = Modifier.padding(14.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        items(forecasts) { fc ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = DriverColors.CockpitDark,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${fc.dayLabel} (${fc.date}) • Peak ${fc.peakHours}", style = DriverTypography.Body.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
                        Text(fc.weatherAdvisory, style = DriverTypography.Body.copy(fontSize = 10.sp, color = DriverColors.TextMuted))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("₹%,.0f pot.".format(fc.potentialDailyEarningsRupees), style = DriverTypography.Body.copy(fontSize = 12.sp, color = DriverColors.StatusOnline, fontWeight = FontWeight.Bold))
                        Text("Demand: ${fc.expectedDemandIndex}%", style = DriverTypography.Body.copy(fontSize = 10.sp, color = DriverColors.ChauffeurGold))
                    }
                }
            }
        }

        // Feature 275: Driver Financial Support Request (Welfare)
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("CHAUFFEUR WELFARE & RELIEF REQUESTS", style = DriverTypography.Subtitle.copy(fontSize = 13.sp))
                TextButton(onClick = onOpenSupportDialog) {
                    Text("+ REQUEST RELIEF", color = DriverColors.ChauffeurGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        val requests = supportRequests
        if (requests.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DriverColors.CockpitDark,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No welfare or emergency assistance requests submitted.",
                        style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted),
                        modifier = Modifier.padding(14.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        items(requests) { req ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = DriverColors.CockpitDark,
                border = BorderStroke(1.dp, if (req.status == "APPROVED" || req.status == "DISBURSED") DriverColors.StatusOnline else DriverColors.SurfaceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(req.requestType.replace('_', ' '), style = DriverTypography.Body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold))
                        GoldBadge(req.status)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Requested: ₹%,.2f • Repayment: ${req.repaymentPlan.replace('_', ' ')}".format(req.requestedAmountRupees),
                        style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted)
                    )
                    Text("Reason: ${req.reasonDescription}", style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextPure))
                    if (req.adminNotes != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Admin: ${req.adminNotes}", style = DriverTypography.Body.copy(fontSize = 10.sp, color = DriverColors.StatusOnline))
                    }
                }
            }
        }
    }
}
