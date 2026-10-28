package com.luxury.chauffeur.driver.feature.earnings

import com.google.gson.annotations.SerializedName

// ---------------------------------------------------------------------------
// Features 251, 252, 253: Earnings Dashboard & Breakdown
// ---------------------------------------------------------------------------
data class DashboardMetric(
    val tripsCount: Int = 0,
    val grossFareCents: Long = 0L,
    val commissionCents: Long = 0L,
    val tipsCents: Long = 0L,
    val netEarningsCents: Long = 0L,
    val netEarningsRupees: Double = 0.0,
    val commissionRatePercentage: Double = 20.0
)

data class DailyHistoryItem(
    val date: String,
    val dayName: String,
    val tripsCount: Int,
    val grossEarningsCents: Long,
    val netEarningsCents: Long,
    val netEarningsRupees: Double,
    val commissionCents: Long,
    val tipsCents: Long
)

data class WeeklyHistoryItem(
    val weekLabel: String,
    val weekStart: String,
    val tripsCount: Int,
    val grossEarningsCents: Long,
    val netEarningsCents: Long,
    val netEarningsRupees: Double,
    val commissionCents: Long,
    val tipsCents: Long
)

data class RideItemDto(
    val id: String,
    val pickupAddress: String,
    val destinationAddress: String,
    val completedAt: String?,
    val grossFareCents: Long,
    val commissionCents: Long,
    val tipCents: Long,
    val netEarningsCents: Long,
    val netEarningsRupees: Double
)

data class RideFareBreakdownDetail(
    val baseFareCents: Long = 0L,
    val distanceFareCents: Long = 0L,
    val timeFareCents: Long = 0L,
    val amenitiesConciergeCents: Long = 0L,
    val grossFareCents: Long = 0L,
    val grossFareRupees: Double = 0.0
)

data class RideDeductionsDetail(
    val platformCommissionCents: Long = 0L,
    val platformCommissionPercentage: Double = 20.0,
    val customerTipCents: Long = 0L,
    val customerTipRupees: Double = 0.0,
    val tollReimbursementCents: Long = 0L,
    val tollReimbursementRupees: Double = 0.0
)

data class RideBreakdownDto(
    val rideId: String,
    val status: String,
    val pickupAddress: String,
    val destinationAddress: String,
    val completedAt: String?,
    val distanceKm: Double,
    val durationMinutes: Double,
    val fareBreakdown: RideFareBreakdownDetail,
    val deductions: RideDeductionsDetail,
    val netDriverEarningsCents: Long,
    val netDriverEarningsRupees: Double,
    val settlementStatus: String
)

// ---------------------------------------------------------------------------
// Features 254, 255, 256, 271, 272: Shift, Forecast & Reminders
// ---------------------------------------------------------------------------
data class ShiftStatusDto(
    val isShiftActive: Boolean = false,
    val shiftId: String? = null,
    val startedAt: String? = null,
    val durationMinutes: Double = 0.0,
    val totalBreakMinutes: Int = 0,
    val tripsCount: Int = 0,
    val shiftGrossCents: Long = 0L,
    val shiftNetCents: Long = 0L,
    val shiftNetRupees: Double = 0.0,
    // Work hour and break reminders
    val continuousDrivingMinutes: Double = 0.0,
    val maxContinuousDrivingMinutes: Int = 240,
    val breakRecommended: Boolean = false,
    val breakReminderMessage: String? = null,
    val shiftLimitApproaching: Boolean = false,
    val shiftReminderMessage: String? = null,
    // Shift forecast
    val forecastTargetNetCents: Long = 0L,
    val forecastProjectedNetCents: Long = 0L,
    val forecastProjectedNetRupees: Double = 0.0,
    val forecastPaceStatus: String = "ON_PACE",
    val forecastExplanation: String = ""
)

data class UtilizationReportDto(
    val date: String = "",
    val totalOnlineMinutes: Double = 0.0,
    val totalOnlineHours: Double = 0.0,
    val totalInRideMinutes: Double = 0.0,
    val totalInRideHours: Double = 0.0,
    val utilizationPercentage: Double = 0.0,
    val targetUtilizationPercentage: Double = 75.0,
    val completedTrips: Int = 0,
    val idleMinutes: Double = 0.0
)

// ---------------------------------------------------------------------------
// Features 257, 258, 259, 260: Weekly Goal & Incentives
// ---------------------------------------------------------------------------
data class WeeklyGoalDto(
    val targetNetCents: Long = 2500000L,
    val targetNetRupees: Double = 25000.0,
    val currentNetCents: Long = 0L,
    val currentNetRupees: Double = 0.0,
    val progressPercentage: Double = 0.0,
    val daysRemaining: Int = 3,
    val paceStatus: String = "ON_TRACK",
    val paceMessage: String = "Pacing steadily towards your goal."
)

data class IncentiveCriterion(
    val name: String,
    val required: String,
    val current: String,
    val isEligible: Boolean
)

data class IncentiveMilestoneDto(
    val id: String,
    val title: String,
    val tier: String, // BRONZE, SILVER, GOLD
    val targetTrips: Int,
    val currentTrips: Int,
    val rewardCents: Long,
    val rewardRupees: Double,
    val isCompleted: Boolean,
    val isClaimed: Boolean,
    val expiresAt: String
)

data class BonusPayoutDto(
    val id: String,
    val title: String,
    val amountRupees: Double,
    val paidAt: String,
    val status: String,
    val questTier: String
)

// ---------------------------------------------------------------------------
// Features 261, 262, 263, 264, 265, 266, 267: Wallet, Payouts & Taxes
// ---------------------------------------------------------------------------
data class LedgerEntryDto(
    val id: String,
    val entryType: String, // CREDIT, DEBIT
    val category: String, // RIDE_PAYOUT, TIP, INCENTIVE_BONUS, AUTO_PAYOUT, EXPENSE_REIMBURSEMENT
    val amountCents: Long,
    val amountRupees: Double,
    val balanceAfterCents: Long,
    val balanceAfterRupees: Double,
    val description: String,
    val referenceId: String?,
    val createdAt: String
)

data class PayoutAccountDto(
    val id: String,
    val accountType: String, // BANK_ACCOUNT, UPI
    val holderName: String,
    val bankName: String?,
    val accountNumberMasked: String?,
    val ifscCode: String?,
    val upiId: String?,
    val isPrimary: Boolean,
    val isVerified: Boolean,
    val verificationStatus: String, // VERIFIED, PENNY_DROP_PENDING, REJECTED
    val pennyDropRef: String?
)

data class PayoutScheduleDto(
    val frequency: String = "WEEKLY_MONDAY", // DAILY, WEEKLY_MONDAY, BI_WEEKLY, INSTANT_MANUAL
    val autoPayoutEnabled: Boolean = true,
    val minimumPayoutCents: Long = 50000L,
    val minimumPayoutRupees: Double = 500.0,
    val nextScheduledDate: String = "Upcoming Monday, 09:00 AM",
    val primaryAccountId: String? = null
)

data class ReconciliationStatementDto(
    val id: String,
    val periodMonth: String,
    val grossFareCents: Long,
    val platformCommissionCents: Long,
    val tipsCents: Long,
    val bonusesCents: Long,
    val tdsDeductionCents: Long, // 1% TDS under Section 194C
    val netSettlementCents: Long,
    val netSettlementRupees: Double,
    val settlementStatus: String, // SETTLED, PROCESSING, FAILED
    val bankUtrNumber: String?,
    val failureReason: String?,
    val failureExplanation: String?,
    val canRetry: Boolean,
    val settledAt: String?
)

data class TaxDocumentDto(
    val id: String,
    val title: String,
    val documentType: String, // TDS_FORM_16A, ANNUAL_SUMMARY, GST_INVOICE
    val financialYear: String,
    val grossAmountRupees: Double,
    val tdsDeductedRupees: Double,
    val downloadUrl: String,
    val issuedDate: String
)

// ---------------------------------------------------------------------------
// Features 268, 269, 273, 274, 275: Expenses, Forecast, Zones & Support
// ---------------------------------------------------------------------------
data class OperatingExpenseDto(
    val id: String,
    val expenseCategory: String, // EV_CHARGING, FUEL_DIESEL, TOLL, VEHICLE_MAINTENANCE, CAR_WASH
    val amountRupees: Double,
    val quantityUnits: Double?, // kWh or Liters
    val unitType: String?, // KWH, LITERS
    val odometerKm: Double?,
    val vendorName: String?,
    val receiptNotes: String?,
    val loggedAt: String
)

data class ZoneComparisonDto(
    val zoneName: String,
    val averageHourlyRupees: Double,
    val demandLevel: String, // VERY_HIGH, HIGH, MODERATE
    val surgeMultiplier: Double,
    val recommendedTime: String
)

data class AvailabilityForecastDto(
    val dayLabel: String,
    val date: String,
    val peakHours: String,
    val expectedDemandIndex: Int, // 1 to 100
    val potentialDailyEarningsRupees: Double,
    val weatherAdvisory: String
)

data class FinancialSupportDto(
    val id: String,
    val requestType: String, // MEDICAL_EMERGENCY, VEHICLE_REPAIR, FAMILY_ASSISTANCE, ADVANCE_EARNINGS
    val requestedAmountRupees: Double,
    val reasonDescription: String,
    val repaymentPlan: String, // WEEKLY_DEDUCTION, EXTENDED_12_WEEKS, LUMP_SUM
    val status: String, // SUBMITTED, UNDER_REVIEW, APPROVED, DISBURSED, REJECTED
    val approvedAmountRupees: Double?,
    val adminNotes: String?,
    val submittedAt: String
)
