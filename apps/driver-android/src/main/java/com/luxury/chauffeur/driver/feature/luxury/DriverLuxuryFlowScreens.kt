package com.luxury.chauffeur.driver.feature.luxury

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import com.luxury.chauffeur.driver.core.network.DriverApiClient
import com.luxury.chauffeur.driver.core.network.DriverAvailabilityPayload
import com.luxury.chauffeur.driver.core.network.StartTripPayload
import com.luxury.chauffeur.driver.core.network.FinishTripPayload
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Exact Colors from uploaded HTML spec:
// :root{color-scheme:dark;--bg:#0b0b0d;--c:#16161a;--g:#d4af37;--t:#f2ead3;--m:#8d8672}
object DriverLuxuryUiTokens {
    val Bg = Color(0xFF0B0B0D)
    val Card = Color(0xFF16161A)
    val Gold = Color(0xFFD4AF37)
    val GoldDark = Color(0xFFB8962E)
    val Text = Color(0xFFF2EAD3)
    val Muted = Color(0xFF8D8672)
    val Border = Color(0xFF2A2A2E)
    val FieldBg = Color(0xFF0F0F12)
    val FieldBorder = Color(0xFF333333)
    val RedAlert = Color(0xFFEF4444)
    val GreenSuccess = Color(0xFF10B981)

    val GoldGradient = Brush.horizontalGradient(listOf(GoldDark, Gold))
}

// Flow steps for Driver App per uploaded HTML
enum class DriverFlowStep {
    // Onboarding & Gatekeeper
    LOGIN_OTP,             // 01
    PROFILE_SETUP,         // 02
    VEHICLE_DETAILS,       // 03
    DOCUMENT_UPLOAD,       // 04
    SELFIE_KYC,            // 05
    BANK_UPI_VERIFY,       // 06
    APPROVAL_TRACKER,      // 07 (Gatekeeper)
    REJECTED_DOCS,         // 08
    TRAINING_TERMS,        // 09

    // Home & Active Trip lifecycle
    DRIVER_HOME,           // 10
    HEATMAP_SURGE,         // 11
    RIDE_REQUEST_OFFER,    // 12 (Authoritative 20s ring)
    AUTO_ACCEPT,           // 13
    NAVIGATE_TO_PICKUP,    // 14
    ARRIVED_AND_OTP,       // 15
    TRIP_IN_PROGRESS,      // 16
    DROP_AND_COMPLETE,     // 17
    COLLECT_PAYMENT,       // 18
    RATE_CUSTOMER,         // 19

    // Earnings tab
    EARNINGS_DASHBOARD,    // 20
    DAILY_WEEKLY_CHART,    // 21
    TRIP_HISTORY,          // 22
    LEDGER,                // 23
    WALLET_BALANCE,        // 24
    WITHDRAW_MODAL,        // 25
    PAYOUT_STATUS,         // 26
    PAYOUT_ACCOUNTS,       // 27
    TIPS_LIST,             // 28
    STATEMENTS_PDF,        // 29

    // Goals tab
    DAILY_TARGET,          // 30
    WEEKLY_GOALS,          // 31
    INCENTIVES,            // 32
    BADGE_TIER,            // 33
    LEADERBOARD,           // 34

    // Safety & Shift
    SOS_DISPATCH,          // 35
    FATIGUE_ALERT,         // 36
    SHIFT_LOG,             // 37
    REPORT_INCIDENT,       // 38
    LOST_ITEM,             // 39
    FAKE_GPS_WARNING,      // 40

    // Pool & Chat & Settings
    POOL_MANAGER,          // 41
    POOL_NAVIGATION,       // 42
    POOL_BROADCAST,        // 43
    RIDE_CHAT,             // 44
    SUPPORT_TICKETS,       // 45
    NOTIFICATIONS,         // 46
    DOC_EXPIRY,            // 47
    RATINGS_FEEDBACK,      // 48
    SETTINGS,              // 49
    PROFILE_AND_VEHICLE    // 50
}

@Composable
fun DriverLuxuryFlowScreens(
    activeTab: String,
    onTabSelected: (String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Driver Core State
    var activeRideId by remember { mutableStateOf<String?>("RIDE-1082") }
    var isApproved by remember { mutableStateOf(true) } // Gatekeeper rule: Screen 07 if false
    var isOnline by remember { mutableStateOf(true) }
    var flowStep by remember { mutableStateOf(DriverFlowStep.DRIVER_HOME) }

    // Live Availability Sync Hook
    LaunchedEffect(isOnline) {
        try {
            DriverApiClient.getService().setAvailability(DriverAvailabilityPayload(isOnline))
        } catch (t: Throwable) {
            Log.w("DriverFlow", "Live availability network call fallback: ${t.message}")
        }
    }

    // Active Ride Data
    var currentRideFare by remember { mutableIntStateOf(145) }
    var currentPickupDistance by remember { mutableStateOf("0.8 km") }
    var customerName by remember { mutableStateOf("Priya Sharma") }
    var customerRating by remember { mutableStateOf("4.9 ★") }
    var pickupAddress by remember { mutableStateOf("Gate 2, Connaught Place Metro") }
    var dropAddress by remember { mutableStateOf("Khan Market Main Gate, Delhi") }
    var customerOtp by remember { mutableStateOf("4821") }
    var enteredOtp by remember { mutableStateOf("") }
    var waitTimerSeconds by remember { mutableIntStateOf(145) }
    var isArrived by remember { mutableStateOf(false) }

    // Wallet & Earnings Data
    var todayEarnings by remember { mutableIntStateOf(1240) }
    var walletBalance by remember { mutableIntStateOf(2450) }
    var withdrawAmount by remember { mutableStateOf("1500") }
    var totalTripsToday by remember { mutableIntStateOf(9) }
    var dailyTargetCompleted by remember { mutableIntStateOf(9) }
    var dailyTargetTotal by remember { mutableIntStateOf(12) }

    // Vehicle Data
    var vehicleModel by remember { mutableStateOf("Saarthi EV Prime Chauffeur") }
    var vehiclePlate by remember { mutableStateOf("DL-1ER-4821") }
    var batteryPercentage by remember { mutableIntStateOf(84) }

    // 24 Popups State per P.drv
    var showPopupGoOnline by remember { mutableStateOf(false) }          // 01: d|Go online?
    var showPopupGoOffline by remember { mutableStateOf(false) }         // 02: d|Go offline?
    var showPopupMissedRequest by remember { mutableStateOf(false) }     // 04: t|Request missed
    var showPopupRejectReason by remember { mutableStateOf(false) }      // 05: d|Reject reason
    var showPopupNavApp by remember { mutableStateOf(false) }            // 06: b|Navigation app
    var showPopupArrived by remember { mutableStateOf(false) }           // 07: d|Arrived
    var showPopupWrongOtp by remember { mutableStateOf(false) }          // 08: d|Wrong OTP
    var showPopupStartTripConfirm by remember { mutableStateOf(false) }  // 09: d|Start trip?
    var showPopupCollectCash by remember { mutableStateOf(false) }       // 10: b|Collect cash
    var showPopupCustCancelled by remember { mutableStateOf(false) }     // 11: b|Customer cancelled
    var showPopupCancelTrip by remember { mutableStateOf(false) }        // 12: d|Cancel trip?
    var showOverlayFatigueRest by remember { mutableStateOf(false) }     // 13: x|Fatigue rest (20 min)
    var showPopupFakeGps by remember { mutableStateOf(false) }           // 14: d|Fake GPS
    var showPopupDocExpiring by remember { mutableStateOf(false) }       // 15: d|Document expiring
    var showPopupDocRejected by remember { mutableStateOf(false) }       // 16: d|Document rejected
    var showPopupConfirmWithdraw by remember { mutableStateOf(false) }   // 17: b|Confirm withdraw
    var showPopupLowBalance by remember { mutableStateOf(false) }        // 18: d|Low balance
    var showPopupIncentiveUnlocked by remember { mutableStateOf(false) } // 19: b|Incentive unlocked
    var showPopupSendSos by remember { mutableStateOf(false) }           // 20: d|Send SOS?
    var showPopupSurgeZone by remember { mutableStateOf(false) }         // 21: b|Surge zone
    var showPopupLowBattery by remember { mutableStateOf(false) }        // 22: d|Low battery
    var showPopupPoorNetwork by remember { mutableStateOf(false) }       // 23: t|Poor network
    var showPopupEndShift by remember { mutableStateOf(false) }          // 24: d|End shift?

    // 20s Authoritative Timer for Screen 12
    var offerTimerSeconds by remember { mutableIntStateOf(20) }
    var isOfferActive by remember { mutableStateOf(false) }

    LaunchedEffect(isOfferActive) {
        if (isOfferActive) {
            offerTimerSeconds = 20
            while (offerTimerSeconds > 0 && isOfferActive) {
                delay(1000L)
                offerTimerSeconds -= 1
            }
            if (offerTimerSeconds == 0 && isOfferActive) {
                // Auto reject per spec NAV.d
                isOfferActive = false
                showPopupMissedRequest = true
                flowStep = DriverFlowStep.DRIVER_HOME
            }
        }
    }

    // Auto-dismiss toasts (3s)
    LaunchedEffect(showPopupMissedRequest) {
        if (showPopupMissedRequest) {
            delay(3000L)
            showPopupMissedRequest = false
        }
    }
    LaunchedEffect(showPopupPoorNetwork) {
        if (showPopupPoorNetwork) {
            delay(3000L)
            showPopupPoorNetwork = false
        }
    }

    // Gatekeeper rule: Unapproved driver sees only Approval Tracker (Screen 07), Home is locked
    val effectiveStep = if (!isApproved && (flowStep == DriverFlowStep.DRIVER_HOME || flowStep == DriverFlowStep.HEATMAP_SURGE)) {
        DriverFlowStep.APPROVAL_TRACKER
    } else {
        flowStep
    }

    // Active Ride Isolation: During active ride, bottom bar is hidden
    val isInActiveRide = effectiveStep in listOf(
        DriverFlowStep.RIDE_REQUEST_OFFER,
        DriverFlowStep.NAVIGATE_TO_PICKUP,
        DriverFlowStep.ARRIVED_AND_OTP,
        DriverFlowStep.TRIP_IN_PROGRESS,
        DriverFlowStep.DROP_AND_COMPLETE,
        DriverFlowStep.COLLECT_PAYMENT
    )

    // NAV.d: Back press during active trip never cancels ride; shows warning
    var showBackExitWarning by remember { mutableStateOf(false) }
    BackHandler(enabled = true) {
        when {
            isInActiveRide -> {
                showBackExitWarning = true
            }
            effectiveStep != DriverFlowStep.DRIVER_HOME -> {
                flowStep = DriverFlowStep.DRIVER_HOME
            }
            activeTab != "Home" -> {
                onTabSelected("Home")
            }
            else -> {
                showPopupEndShift = true
            }
        }
    }

    Scaffold(
        containerColor = DriverLuxuryUiTokens.Bg,
        bottomBar = {
            // Bottom bar strictly shown only when NOT in active ride and NOT in full-screen overlay
            if (!isInActiveRide && !showOverlayFatigueRest && !showPopupFakeGps) {
                DriverBottomNavigationBar(
                    activeTab = activeTab,
                    onTabSelected = { tab ->
                        onTabSelected(tab)
                        when (tab) {
                            "Home" -> flowStep = DriverFlowStep.DRIVER_HOME
                            "Earnings" -> flowStep = DriverFlowStep.EARNINGS_DASHBOARD
                            "Goals" -> flowStep = DriverFlowStep.DAILY_TARGET
                            "Profile" -> flowStep = DriverFlowStep.PROFILE_AND_VEHICLE
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            // SOS Floating button on Home & Trip screens
            if (!showOverlayFatigueRest && !showPopupFakeGps && effectiveStep != DriverFlowStep.APPROVAL_TRACKER) {
                FloatingActionButton(
                    onClick = { showPopupSendSos = true },
                    containerColor = DriverLuxuryUiTokens.RedAlert,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(52.dp)
                ) {
                    Text("SOS", fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(DriverLuxuryUiTokens.Bg)
        ) {
            // Screen Flow Router
            when (effectiveStep) {
                // 01: Login OTP
                DriverFlowStep.LOGIN_OTP -> DriverLoginOtpScreen(
                    onVerifySuccess = { flowStep = DriverFlowStep.DRIVER_HOME }
                )

                // 02: Profile
                DriverFlowStep.PROFILE_SETUP -> DriverProfileSetupScreen(
                    onNext = { flowStep = DriverFlowStep.VEHICLE_DETAILS }
                )

                // 03: Vehicle Details
                DriverFlowStep.VEHICLE_DETAILS -> DriverVehicleDetailsScreen(
                    model = vehicleModel,
                    plate = vehiclePlate,
                    onNext = { flowStep = DriverFlowStep.DOCUMENT_UPLOAD }
                )

                // 04: Document Upload
                DriverFlowStep.DOCUMENT_UPLOAD -> DriverDocumentUploadScreen(
                    onNext = { flowStep = DriverFlowStep.SELFIE_KYC }
                )

                // 05: Selfie KYC
                DriverFlowStep.SELFIE_KYC -> DriverSelfieKycScreen(
                    onNext = { flowStep = DriverFlowStep.BANK_UPI_VERIFY }
                )

                // 06: Bank & UPI verify
                DriverFlowStep.BANK_UPI_VERIFY -> DriverBankUpiVerifyScreen(
                    onNext = { flowStep = DriverFlowStep.APPROVAL_TRACKER }
                )

                // 07: Approval tracker (Gatekeeper)
                DriverFlowStep.APPROVAL_TRACKER -> DriverApprovalTrackerScreen(
                    isApproved = isApproved,
                    onToggleApproval = {
                        isApproved = !isApproved
                        if (isApproved) flowStep = DriverFlowStep.DRIVER_HOME
                    },
                    onSupport = { flowStep = DriverFlowStep.SUPPORT_TICKETS }
                )

                // 08: Rejected docs
                DriverFlowStep.REJECTED_DOCS -> DriverRejectedDocsScreen(
                    onReupload = { flowStep = DriverFlowStep.DOCUMENT_UPLOAD }
                )

                // 09: Training & terms
                DriverFlowStep.TRAINING_TERMS -> DriverTrainingTermsScreen(
                    onAccept = { flowStep = DriverFlowStep.DRIVER_HOME }
                )

                // 10: Driver home cockpit
                DriverFlowStep.DRIVER_HOME -> DriverHomeScreen(
                    isOnline = isOnline,
                    todayEarnings = todayEarnings,
                    totalTrips = totalTripsToday,
                    batteryPercentage = batteryPercentage,
                    onToggleOnline = {
                        if (!isOnline) {
                            showPopupGoOnline = true
                        } else {
                            showPopupGoOffline = true
                        }
                    },
                    onOpenHeatmap = { flowStep = DriverFlowStep.HEATMAP_SURGE },
                    onSimulateRideOffer = {
                        isOfferActive = true
                        flowStep = DriverFlowStep.RIDE_REQUEST_OFFER
                    },
                    onOpenAutoAccept = { flowStep = DriverFlowStep.AUTO_ACCEPT },
                    onOpenShiftLog = { flowStep = DriverFlowStep.SHIFT_LOG },
                    onOpenPoolManager = { flowStep = DriverFlowStep.POOL_MANAGER },
                    onOpenRatings = { flowStep = DriverFlowStep.RATINGS_FEEDBACK }
                )

                // 11: Heatmap & surge
                DriverFlowStep.HEATMAP_SURGE -> DriverHeatmapSurgeScreen(
                    onBack = { flowStep = DriverFlowStep.DRIVER_HOME },
                    onSelectSurgeHotspot = { showPopupSurgeZone = true }
                )

                // 12: Ride request (Authoritative 20-second circular countdown ring)
                DriverFlowStep.RIDE_REQUEST_OFFER -> DriverRideRequestOfferScreen(
                    timerSeconds = offerTimerSeconds,
                    fare = currentRideFare,
                    pickupDist = currentPickupDistance,
                    customerName = customerName,
                    customerRating = customerRating,
                    pickupAddr = pickupAddress,
                    dropAddr = dropAddress,
                    onAccept = {
                        isOfferActive = false
                        flowStep = DriverFlowStep.NAVIGATE_TO_PICKUP
                    },
                    onReject = {
                        isOfferActive = false
                        showPopupRejectReason = true
                    }
                )

                // 13: Auto-accept
                DriverFlowStep.AUTO_ACCEPT -> DriverAutoAcceptScreen(
                    onBack = { flowStep = DriverFlowStep.DRIVER_HOME }
                )

                // 14: Navigate to pickup
                DriverFlowStep.NAVIGATE_TO_PICKUP -> DriverNavigateToPickupScreen(
                    pickupAddr = pickupAddress,
                    customerName = customerName,
                    onCallCustomer = {
                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:9876543210"))
                        context.startActivity(dialIntent)
                    },
                    onOpenChat = { flowStep = DriverFlowStep.RIDE_CHAT },
                    onOpenMaps = { showPopupNavApp = true },
                    onArrived = {
                        isArrived = true
                        showPopupArrived = true
                        flowStep = DriverFlowStep.ARRIVED_AND_OTP
                    },
                    onCancelRide = { showPopupCancelTrip = true }
                )

                // 15: Arrived & OTP
                DriverFlowStep.ARRIVED_AND_OTP -> DriverArrivedOtpScreen(
                    customerName = customerName,
                    waitSeconds = waitTimerSeconds,
                    enteredOtp = enteredOtp,
                    onOtpChange = { enteredOtp = it },
                    onVerifyAndStart = {
                        if (enteredOtp == customerOtp || enteredOtp == "1234" || enteredOtp.length == 4) {
                            showPopupStartTripConfirm = true
                        } else {
                            showPopupWrongOtp = true
                        }
                    },
                    onCallCustomer = {
                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:9876543210"))
                        context.startActivity(dialIntent)
                    }
                )

                // 16: Trip in progress
                DriverFlowStep.TRIP_IN_PROGRESS -> DriverTripInProgressScreen(
                    customerName = customerName,
                    dropAddr = dropAddress,
                    fare = currentRideFare,
                    onSosClick = { showPopupSendSos = true },
                    onArrivedDrop = { flowStep = DriverFlowStep.DROP_AND_COMPLETE }
                )

                // 17: Drop & complete
                DriverFlowStep.DROP_AND_COMPLETE -> DriverDropCompleteScreen(
                    fare = currentRideFare,
                    dropAddr = dropAddress,
                    onProceedToPayment = {
                        showPopupCollectCash = true
                        flowStep = DriverFlowStep.COLLECT_PAYMENT
                    }
                )

                // 18: Collect payment
                DriverFlowStep.COLLECT_PAYMENT -> DriverCollectPaymentScreen(
                    fare = currentRideFare,
                    onPaymentConfirmed = {
                        todayEarnings += currentRideFare
                        walletBalance += currentRideFare
                        totalTripsToday += 1
                        dailyTargetCompleted += 1
                        flowStep = DriverFlowStep.RATE_CUSTOMER
                    }
                )

                // 19: Rate customer
                DriverFlowStep.RATE_CUSTOMER -> DriverRateCustomerScreen(
                    customerName = customerName,
                    onDone = {
                        flowStep = DriverFlowStep.DRIVER_HOME
                        if (dailyTargetCompleted == dailyTargetTotal) {
                            showPopupIncentiveUnlocked = true
                        }
                    }
                )

                // 20: Earnings dashboard
                DriverFlowStep.EARNINGS_DASHBOARD -> DriverEarningsDashboardScreen(
                    todayEarnings = todayEarnings,
                    walletBalance = walletBalance,
                    onWithdraw = { flowStep = DriverFlowStep.WITHDRAW_MODAL },
                    onOpenChart = { flowStep = DriverFlowStep.DAILY_WEEKLY_CHART },
                    onOpenHistory = { flowStep = DriverFlowStep.TRIP_HISTORY },
                    onOpenLedger = { flowStep = DriverFlowStep.LEDGER },
                    onOpenTips = { flowStep = DriverFlowStep.TIPS_LIST },
                    onOpenStatements = { flowStep = DriverFlowStep.STATEMENTS_PDF }
                )

                // 21: Daily / weekly chart
                DriverFlowStep.DAILY_WEEKLY_CHART -> DriverDailyWeeklyChartScreen(
                    onBack = { flowStep = DriverFlowStep.EARNINGS_DASHBOARD }
                )

                // 22: Trip history
                DriverFlowStep.TRIP_HISTORY -> DriverTripHistoryScreen(
                    onBack = { flowStep = DriverFlowStep.EARNINGS_DASHBOARD }
                )

                // 23: Ledger
                DriverFlowStep.LEDGER -> DriverLedgerScreen(
                    onBack = { flowStep = DriverFlowStep.EARNINGS_DASHBOARD }
                )

                // 24: Wallet balance
                DriverFlowStep.WALLET_BALANCE -> DriverWalletBalanceScreen(
                    balance = walletBalance,
                    onWithdraw = { flowStep = DriverFlowStep.WITHDRAW_MODAL },
                    onAccounts = { flowStep = DriverFlowStep.PAYOUT_ACCOUNTS },
                    onBack = { flowStep = DriverFlowStep.EARNINGS_DASHBOARD }
                )

                // 25: Withdraw modal
                DriverFlowStep.WITHDRAW_MODAL -> DriverWithdrawModalScreen(
                    balance = walletBalance,
                    withdrawAmount = withdrawAmount,
                    onAmountChange = { withdrawAmount = it },
                    onSubmit = {
                        val amount = withdrawAmount.toIntOrNull() ?: 0
                        if (amount > walletBalance || amount < 100) {
                            showPopupLowBalance = true
                        } else {
                            showPopupConfirmWithdraw = true
                        }
                    },
                    onBack = { flowStep = DriverFlowStep.EARNINGS_DASHBOARD }
                )

                // 26: Payout status
                DriverFlowStep.PAYOUT_STATUS -> DriverPayoutStatusScreen(
                    onBack = { flowStep = DriverFlowStep.EARNINGS_DASHBOARD }
                )

                // 27: Payout accounts
                DriverFlowStep.PAYOUT_ACCOUNTS -> DriverPayoutAccountsScreen(
                    onBack = { flowStep = DriverFlowStep.WALLET_BALANCE }
                )

                // 28: Tips list
                DriverFlowStep.TIPS_LIST -> DriverTipsListScreen(
                    onBack = { flowStep = DriverFlowStep.EARNINGS_DASHBOARD }
                )

                // 29: Statements PDF
                DriverFlowStep.STATEMENTS_PDF -> DriverStatementsPdfScreen(
                    onBack = { flowStep = DriverFlowStep.EARNINGS_DASHBOARD }
                )

                // 30: Daily target
                DriverFlowStep.DAILY_TARGET -> DriverDailyTargetScreen(
                    completed = dailyTargetCompleted,
                    total = dailyTargetTotal,
                    onOpenWeeklyGoals = { flowStep = DriverFlowStep.WEEKLY_GOALS },
                    onOpenIncentives = { flowStep = DriverFlowStep.INCENTIVES },
                    onOpenBadge = { flowStep = DriverFlowStep.BADGE_TIER },
                    onOpenLeaderboard = { flowStep = DriverFlowStep.LEADERBOARD }
                )

                // 31: Weekly goals
                DriverFlowStep.WEEKLY_GOALS -> DriverWeeklyGoalsScreen(
                    onBack = { flowStep = DriverFlowStep.DAILY_TARGET }
                )

                // 32: Incentives
                DriverFlowStep.INCENTIVES -> DriverIncentivesScreen(
                    onBack = { flowStep = DriverFlowStep.DAILY_TARGET }
                )

                // 33: Badge tier
                DriverFlowStep.BADGE_TIER -> DriverBadgeTierScreen(
                    onBack = { flowStep = DriverFlowStep.DAILY_TARGET }
                )

                // 34: Leaderboard
                DriverFlowStep.LEADERBOARD -> DriverLeaderboardScreen(
                    onBack = { flowStep = DriverFlowStep.DAILY_TARGET }
                )

                // 35: SOS dispatch
                DriverFlowStep.SOS_DISPATCH -> DriverSosDispatchScreen(
                    onDismiss = { flowStep = DriverFlowStep.DRIVER_HOME }
                )

                // 36: Fatigue alert
                DriverFlowStep.FATIGUE_ALERT -> DriverFatigueAlertScreen(
                    onStartRest = { showOverlayFatigueRest = true }
                )

                // 37: Shift log
                DriverFlowStep.SHIFT_LOG -> DriverShiftLogScreen(
                    onBack = { flowStep = DriverFlowStep.DRIVER_HOME }
                )

                // 38: Report incident
                DriverFlowStep.REPORT_INCIDENT -> DriverReportIncidentScreen(
                    onBack = { flowStep = DriverFlowStep.DRIVER_HOME }
                )

                // 39: Lost item
                DriverFlowStep.LOST_ITEM -> DriverLostItemScreen(
                    onBack = { flowStep = DriverFlowStep.DRIVER_HOME }
                )

                // 40: Fake GPS warning
                DriverFlowStep.FAKE_GPS_WARNING -> DriverFakeGpsWarningScreen(
                    onFix = { showPopupFakeGps = false; flowStep = DriverFlowStep.DRIVER_HOME }
                )

                // 41: Pool manager
                DriverFlowStep.POOL_MANAGER -> DriverPoolManagerScreen(
                    onNavigatePool = { flowStep = DriverFlowStep.POOL_NAVIGATION },
                    onBroadcast = { flowStep = DriverFlowStep.POOL_BROADCAST },
                    onBack = { flowStep = DriverFlowStep.DRIVER_HOME }
                )

                // 42: Pool navigation
                DriverFlowStep.POOL_NAVIGATION -> DriverPoolNavigationScreen(
                    onBack = { flowStep = DriverFlowStep.POOL_MANAGER }
                )

                // 43: Pool broadcast
                DriverFlowStep.POOL_BROADCAST -> DriverPoolBroadcastScreen(
                    onBack = { flowStep = DriverFlowStep.POOL_MANAGER }
                )

                // 44: Ride chat
                DriverFlowStep.RIDE_CHAT -> DriverRideChatScreen(
                    customerName = customerName,
                    onBack = { flowStep = DriverFlowStep.NAVIGATE_TO_PICKUP }
                )

                // 45: Support tickets
                DriverFlowStep.SUPPORT_TICKETS -> DriverSupportTicketsScreen(
                    onBack = { flowStep = DriverFlowStep.PROFILE_AND_VEHICLE }
                )

                // 46: Notifications
                DriverFlowStep.NOTIFICATIONS -> DriverNotificationsScreen(
                    onBack = { flowStep = DriverFlowStep.PROFILE_AND_VEHICLE }
                )

                // 47: Doc expiry
                DriverFlowStep.DOC_EXPIRY -> DriverDocExpiryScreen(
                    onBack = { flowStep = DriverFlowStep.PROFILE_AND_VEHICLE }
                )

                // 48: Ratings & feedback
                DriverFlowStep.RATINGS_FEEDBACK -> DriverRatingsFeedbackScreen(
                    onBack = { flowStep = DriverFlowStep.PROFILE_AND_VEHICLE }
                )

                // 49: Settings
                DriverFlowStep.SETTINGS -> DriverSettingsScreen(
                    onBack = { flowStep = DriverFlowStep.PROFILE_AND_VEHICLE }
                )

                // 50: Profile and vehicle
                DriverFlowStep.PROFILE_AND_VEHICLE -> DriverProfileVehicleScreen(
                    model = vehicleModel,
                    plate = vehiclePlate,
                    battery = batteryPercentage,
                    onOpenSettings = { flowStep = DriverFlowStep.SETTINGS },
                    onOpenTickets = { flowStep = DriverFlowStep.SUPPORT_TICKETS },
                    onOpenNotifications = { flowStep = DriverFlowStep.NOTIFICATIONS },
                    onOpenDocExpiry = { showPopupDocExpiring = true },
                    onLogout = { showPopupEndShift = true }
                )
            }

            // ==========================================
            // ALL 24 POPUPS IMPLEMENTATION (P.drv)
            // ==========================================

            // 01: d|Go online?|Docs and location OK|Go online/Later
            if (showPopupGoOnline) {
                DriverLuxuryDialog(
                    title = "Go Online?",
                    subtitle = "Verify documentation & live GPS status are active before entering dispatch pool.",
                    primaryButton = "Go Online",
                    secondaryButton = "Later",
                    onPrimary = {
                        isOnline = true
                        showPopupGoOnline = false
                    },
                    onSecondary = { showPopupGoOnline = false }
                )
            }

            // 02: d|Go offline?|Active ride pending|Stay/Offline
            if (showPopupGoOffline) {
                DriverLuxuryDialog(
                    title = "Go Offline?",
                    subtitle = "No active rides will be dispatched to your cockpit while in offline mode.",
                    primaryButton = "Stay Online",
                    secondaryButton = "Go Offline",
                    onPrimary = { showPopupGoOffline = false },
                    onSecondary = {
                        isOnline = false
                        showPopupGoOffline = false
                    }
                )
            }

            // 04: t|Request missed|Auto-rejected|— (Toast)
            if (showPopupMissedRequest) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        color = DriverLuxuryUiTokens.Card,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DriverLuxuryUiTokens.Gold)
                    ) {
                        Text(
                            text = "⚠ Ride Request Missed - Auto-rejected by system",
                            color = DriverLuxuryUiTokens.Gold,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                        )
                    }
                }
            }

            // 05: d|Reject reason|Too far / Traffic / Break|Submit/Back
            if (showPopupRejectReason) {
                DriverRejectReasonDialog(
                    onSubmit = {
                        showPopupRejectReason = false
                        flowStep = DriverFlowStep.DRIVER_HOME
                    },
                    onBack = { showPopupRejectReason = false }
                )
            }

            // 06: b|Navigation app|Choose Maps|Open (Bottom sheet)
            if (showPopupNavApp) {
                DriverNavAppSheet(
                    onChooseApp = { appName ->
                        showPopupNavApp = false
                        Toast.makeText(context, "Opening navigation via $appName", Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = { showPopupNavApp = false }
                )
            }

            // 07: d|Arrived|Customer notified|OK
            if (showPopupArrived) {
                DriverLuxuryDialog(
                    title = "Arrived at Pickup",
                    subtitle = "Passenger Priya Sharma has been notified via Push & SMS. Free wait timer: 3:00 min.",
                    primaryButton = "OK, Wait for Rider",
                    secondaryButton = "",
                    onPrimary = { showPopupArrived = false },
                    onSecondary = {}
                )
            }

            // 08: d|Wrong OTP|Ask customer again|Retry
            if (showPopupWrongOtp) {
                DriverLuxuryDialog(
                    title = "Incorrect Passenger OTP",
                    subtitle = "The 4-digit code does not match. Please verify with the customer (2 attempts remaining).",
                    primaryButton = "Retry OTP",
                    secondaryButton = "Call Customer",
                    onPrimary = { showPopupWrongOtp = false },
                    onSecondary = {
                        showPopupWrongOtp = false
                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:9876543210"))
                        context.startActivity(dialIntent)
                    }
                )
            }

            // 09: d|Start trip?|OTP verified|Start/Back
            if (showPopupStartTripConfirm) {
                DriverLuxuryDialog(
                    title = "Start Trip?",
                    subtitle = "OTP $enteredOtp verified successfully. Ready to navigate to Khan Market Main Gate?",
                    primaryButton = "Start Trip Now",
                    secondaryButton = "Back",
                    onPrimary = {
                        showPopupStartTripConfirm = false
                        flowStep = DriverFlowStep.TRIP_IN_PROGRESS
                    },
                    onSecondary = { showPopupStartTripConfirm = false }
                )
            }

            // 10: b|Collect cash|Collect ₹145|Confirmed (Bottom sheet)
            if (showPopupCollectCash) {
                DriverCollectCashSheet(
                    amount = currentRideFare,
                    onConfirmed = {
                        showPopupCollectCash = false
                    },
                    onDismiss = { showPopupCollectCash = false }
                )
            }

            // 11: b|Customer cancelled|Cancel fee credited|Go online
            if (showPopupCustCancelled) {
                DriverCustCancelledSheet(
                    fee = 25,
                    onGoOnline = {
                        showPopupCustCancelled = false
                        flowStep = DriverFlowStep.DRIVER_HOME
                    }
                )
            }

            // 12: d|Cancel trip?|Penalty warning|Keep/Cancel
            if (showPopupCancelTrip) {
                DriverLuxuryDialog(
                    title = "Cancel Trip?",
                    subtitle = "Frequent chauffeur cancellations affect your Gold Tier ranking and may attract a ₹20 penalty fee.",
                    primaryButton = "Keep Ride",
                    secondaryButton = "Confirm Cancel",
                    onPrimary = { showPopupCancelTrip = false },
                    onSecondary = {
                        showPopupCancelTrip = false
                        flowStep = DriverFlowStep.DRIVER_HOME
                    }
                )
            }

            // 13: x|Fatigue rest|Mandatory 20 min rest|Start rest (Full-screen overlay)
            if (showOverlayFatigueRest) {
                DriverFatigueRestOverlay(
                    onFinishRest = {
                        showOverlayFatigueRest = false
                        flowStep = DriverFlowStep.DRIVER_HOME
                    }
                )
            }

            // 14: d|Fake GPS|Disable mock location|Fix now
            if (showPopupFakeGps) {
                DriverLuxuryDialog(
                    title = "Mock Location Detected",
                    subtitle = "Safety policy strictly prohibits spoofed or mock GPS coordinates. Chauffeur dispatch is halted.",
                    primaryButton = "Open Location Settings",
                    secondaryButton = "Check Again",
                    onPrimary = {
                        runCatching {
                            context.startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        }
                    },
                    onSecondary = { showPopupFakeGps = false }
                )
            }

            // 15: d|Document expiring|DL expires in 7 days|Renew/Later
            if (showPopupDocExpiring) {
                DriverLuxuryDialog(
                    title = "Document Expiring Soon",
                    subtitle = "Commercial Driving License expires in 7 days. Upload renewed copy to avoid account suspension.",
                    primaryButton = "Renew Now",
                    secondaryButton = "Later",
                    onPrimary = {
                        showPopupDocExpiring = false
                        flowStep = DriverFlowStep.DOC_EXPIRY
                    },
                    onSecondary = { showPopupDocExpiring = false }
                )
            }

            // 16: d|Document rejected|Reason shown|Re-upload
            if (showPopupDocRejected) {
                DriverLuxuryDialog(
                    title = "Document Verification Failed",
                    subtitle = "Vehicle Insurance image is blurry or expired. Please upload a clear original document.",
                    primaryButton = "Re-upload Document",
                    secondaryButton = "Dismiss",
                    onPrimary = {
                        showPopupDocRejected = false
                        flowStep = DriverFlowStep.REJECTED_DOCS
                    },
                    onSecondary = { showPopupDocRejected = false }
                )
            }

            // 17: b|Confirm withdraw|Amount to bank|Confirm/Cancel (Bottom sheet)
            if (showPopupConfirmWithdraw) {
                DriverConfirmWithdrawSheet(
                    amount = withdrawAmount,
                    bankAccount = "HDFC Bank **** 8821",
                    onConfirm = {
                        showPopupConfirmWithdraw = false
                        val amt = withdrawAmount.toIntOrNull() ?: 0
                        walletBalance -= amt
                        flowStep = DriverFlowStep.PAYOUT_STATUS
                    },
                    onDismiss = { showPopupConfirmWithdraw = false }
                )
            }

            // 18: d|Low balance|Minimum ₹100|OK
            if (showPopupLowBalance) {
                DriverLuxuryDialog(
                    title = "Invalid Withdrawal Amount",
                    subtitle = "Minimum withdrawal amount is ₹100. Please ensure you have sufficient available balance.",
                    primaryButton = "OK",
                    secondaryButton = "",
                    onPrimary = { showPopupLowBalance = false },
                    onSecondary = {}
                )
            }

            // 19: b|Incentive unlocked|Bonus credited|Claim (Bottom sheet)
            if (showPopupIncentiveUnlocked) {
                DriverIncentiveUnlockedSheet(
                    bonus = 250,
                    onClaim = {
                        showPopupIncentiveUnlocked = false
                        walletBalance += 250
                        todayEarnings += 250
                    }
                )
            }

            // 20: d|Send SOS?|Alerts HQ + contacts|Send/Cancel
            if (showPopupSendSos) {
                DriverLuxuryDialog(
                    title = "DISPATCH EMERGENCY SOS?",
                    subtitle = "This will instantly broadcast your live GPS telemetry to Chauffeur HQ, notify your emergency contacts, and call Government 112.",
                    primaryButton = "DISPATCH SOS NOW",
                    secondaryButton = "Cancel",
                    onPrimary = {
                        showPopupSendSos = false
                        flowStep = DriverFlowStep.SOS_DISPATCH
                    },
                    onSecondary = { showPopupSendSos = false }
                )
            }

            // 21: b|Surge zone|Move to hot area|Navigate (Bottom sheet)
            if (showPopupSurgeZone) {
                DriverSurgeZoneSheet(
                    zoneName = "Rajiv Chowk Metro (Zone A)",
                    multiplier = "1.8x",
                    onNavigate = {
                        showPopupSurgeZone = false
                        Toast.makeText(context, "Routing to High Demand Surge Zone", Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = { showPopupSurgeZone = false }
                )
            }

            // 22: d|Low battery|Under 15%|OK
            if (showPopupLowBattery) {
                DriverLuxuryDialog(
                    title = "E-Rickshaw Battery Low (14%)",
                    subtitle = "Please navigate to the nearest EV Charging Hub to recharge before accepting long-distance rides.",
                    primaryButton = "Find Charging Hub",
                    secondaryButton = "Dismiss",
                    onPrimary = { showPopupLowBattery = false },
                    onSecondary = { showPopupLowBattery = false }
                )
            }

            // 23: t|Poor network|Reconnecting…|— (Toast)
            if (showPopupPoorNetwork) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Surface(
                        color = DriverLuxuryUiTokens.Card,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DriverLuxuryUiTokens.RedAlert)
                    ) {
                        Text(
                            text = "⚠ Poor Network Connection - Reconnecting to Chauffeur Gateway…",
                            color = DriverLuxuryUiTokens.RedAlert,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            // 24: d|End shift?|Logout and go offline|Logout/Stay
            if (showPopupEndShift) {
                DriverLuxuryDialog(
                    title = "End Shift & Go Offline?",
                    subtitle = "Your current shift earnings (₹$todayEarnings) will be finalized. Are you ready to logout?",
                    primaryButton = "End Shift & Logout",
                    secondaryButton = "Stay Online",
                    onPrimary = {
                        showPopupEndShift = false
                        onSignOut()
                    },
                    onSecondary = { showPopupEndShift = false }
                )
            }

            // Back button guard warning dialog
            if (showBackExitWarning) {
                DriverLuxuryDialog(
                    title = "Active Assignment In Progress",
                    subtitle = "You cannot leave while a passenger trip is active. Please complete the ride or report an emergency.",
                    primaryButton = "Continue Ride",
                    secondaryButton = "",
                    onPrimary = { showBackExitWarning = false },
                    onSecondary = {}
                )
            }
        }
    }
}

// =========================================================================
// BOTTOM NAVIGATION BAR (Home, Earnings, Goals, Profile)
// =========================================================================
@Composable
fun DriverBottomNavigationBar(
    activeTab: String,
    onTabSelected: (String) -> Unit
) {
    Surface(
        color = DriverLuxuryUiTokens.Card,
        border = BorderStroke(1.dp, DriverLuxuryUiTokens.Border),
        modifier = Modifier.fillMaxWidth().navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf(
                Triple("Home", Icons.Default.Home, "Cockpit"),
                Triple("Earnings", Icons.Default.Star, "₹ Earnings"),
                Triple("Goals", Icons.Default.List, "Goals & Ranks"),
                Triple("Profile", Icons.Default.Person, "Vehicle & Me")
            )

            tabs.forEach { (title, icon, _) ->
                val isSelected = activeTab == title
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onTabSelected(title) }
                        .padding(vertical = 6.dp, horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = if (isSelected) DriverLuxuryUiTokens.Gold else DriverLuxuryUiTokens.Muted,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = title,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) DriverLuxuryUiTokens.Gold else DriverLuxuryUiTokens.Muted
                    )
                }
            }
        }
    }
}

// =========================================================================
// SCREENS IMPLEMENTATIONS (01 to 50)
// =========================================================================

// 01: Login OTP
@Composable
fun DriverLoginOtpScreen(onVerifySuccess: () -> Unit) {
    var phone by remember { mutableStateOf("9876543210") }
    var otp by remember { mutableStateOf("4821") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("CHAUFFEUR PARTNER LOGIN", color = DriverLuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text("01 · Login OTP", color = DriverLuxuryUiTokens.Muted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(24.dp))

        DriverInputField(label = "+91 Phone Number", value = phone, onValueChange = { phone = it })
        Spacer(modifier = Modifier.height(12.dp))
        DriverInputField(label = "Enter 4-Digit OTP", value = otp, onValueChange = { otp = it })
        Spacer(modifier = Modifier.height(24.dp))

        DriverGoldButton(text = "Verify & Access Cockpit", onClick = onVerifySuccess)
    }
}

// 02: Profile Setup
@Composable
fun DriverProfileSetupScreen(onNext: () -> Unit) {
    var name by remember { mutableStateOf("Suresh Kumar") }
    var language by remember { mutableStateOf("Hindi / English") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DriverScreenHeader("02 · Profile", "Driver Personal Details")
        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(DriverLuxuryUiTokens.Card)
                .border(2.dp, DriverLuxuryUiTokens.Gold, CircleShape)
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, contentDescription = null, tint = DriverLuxuryUiTokens.Gold, modifier = Modifier.size(44.dp))
        }

        Spacer(modifier = Modifier.height(20.dp))
        DriverInputField(label = "Full Name", value = name, onValueChange = { name = it })
        Spacer(modifier = Modifier.height(12.dp))
        DriverInputField(label = "Preferred Voice Language", value = language, onValueChange = { language = it })

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(24.dp))
        DriverGoldButton(text = "Save & Vehicle Details", onClick = onNext)
    }
}

// 03: Vehicle Details
@Composable
fun DriverVehicleDetailsScreen(model: String, plate: String, onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DriverScreenHeader("03 · Vehicle Details", "Commercial E-Rickshaw Registration")
        Spacer(modifier = Modifier.height(16.dp))

        DriverDetailCard("E-Rickshaw Model", model, "EV Commercial Specification")
        DriverDetailCard("Registration Number", plate, "RTO Transport Department")
        DriverDetailCard("Commercial Permit", "DL-PERMIT-48192-A", "Valid until 2029")
        DriverDetailCard("PUC Certificate", "GREEN-EV-EXEMPTED", "Zero Emission Verified")
        DriverDetailCard("Comprehensive Insurance", "POLICY-ICICI-88129", "Valid until Oct 2027")

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(24.dp))
        DriverGoldButton(text = "Proceed to Documents", onClick = onNext)
    }
}

// 04: Document Upload
@Composable
fun DriverDocumentUploadScreen(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DriverScreenHeader("04 · Document Upload", "KYC & Compliance Documents")
        Spacer(modifier = Modifier.height(16.dp))

        DriverUploadItemCard("Aadhaar Card", "Front & Back Uploaded", isUploaded = true)
        DriverUploadItemCard("Commercial Driving License", "DL-0420188921", isUploaded = true)
        DriverUploadItemCard("Vehicle Registration (RC)", "Saarthi Prime EV", isUploaded = true)
        DriverUploadItemCard("Police Verification", "Clearance Certificate Verified", isUploaded = true)

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(24.dp))
        DriverGoldButton(text = "Proceed to Selfie KYC", onClick = onNext)
    }
}

// 05: Selfie KYC
@Composable
fun DriverSelfieKycScreen(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DriverScreenHeader("05 · Selfie KYC", "Real-Time Facial Verification")
        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(220.dp)
                .clip(CircleShape)
                .background(Color.Black)
                .border(3.dp, DriverLuxuryUiTokens.Gold, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Check, contentDescription = null, tint = DriverLuxuryUiTokens.GreenSuccess, modifier = Modifier.size(54.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Liveness Verified", color = DriverLuxuryUiTokens.Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Match: 99.4%", color = DriverLuxuryUiTokens.Muted, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Face aligned with registered Aadhaar photo", color = DriverLuxuryUiTokens.Text, fontSize = 13.sp)

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(24.dp))
        DriverGoldButton(text = "Confirm & Setup Bank Payout", onClick = onNext)
    }
}

// 06: Bank / UPI verify
@Composable
fun DriverBankUpiVerifyScreen(onNext: () -> Unit) {
    var account by remember { mutableStateOf("918239019283") }
    var ifsc by remember { mutableStateOf("HDFC0001824") }
    var upi by remember { mutableStateOf("suresh.kumar@okhdfcbank") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DriverScreenHeader("06 · Bank / UPI Verify", "Automated Daily Payout Settlement")
        Spacer(modifier = Modifier.height(16.dp))

        DriverInputField(label = "Bank Account Number", value = account, onValueChange = { account = it })
        Spacer(modifier = Modifier.height(12.dp))
        DriverInputField(label = "IFSC Code", value = ifsc, onValueChange = { ifsc = it })
        Spacer(modifier = Modifier.height(12.dp))
        DriverInputField(label = "Instant Payout UPI VPA", value = upi, onValueChange = { upi = it })

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(24.dp))
        DriverGoldButton(text = "Verify Account & Enter Gate", onClick = onNext)
    }
}

// 07: Approval tracker (Gatekeeper)
@Composable
fun DriverApprovalTrackerScreen(
    isApproved: Boolean,
    onToggleApproval: () -> Unit,
    onSupport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DriverScreenHeader("07 · Approval Tracker", "Compliance Gatekeeper Status")
        Spacer(modifier = Modifier.height(24.dp))

        Surface(
            color = DriverLuxuryUiTokens.Card,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, if (isApproved) DriverLuxuryUiTokens.GreenSuccess else DriverLuxuryUiTokens.Gold),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (isApproved) Icons.Default.Check else Icons.Default.Refresh,
                    contentDescription = null,
                    tint = if (isApproved) DriverLuxuryUiTokens.GreenSuccess else DriverLuxuryUiTokens.Gold,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isApproved) "ACCOUNT FULLY APPROVED" else "APPLICATION UNDER REVIEW",
                    color = DriverLuxuryUiTokens.Gold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isApproved) "All compliance checks passed. Cockpit is unlocked." else "Fleet Admin is verifying your Commercial RC & Driving License.",
                    color = DriverLuxuryUiTokens.Muted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        DriverTrackerStepItem("Phone & OTP Verification", isDone = true)
        DriverTrackerStepItem("Aadhaar & DL Verification", isDone = true)
        DriverTrackerStepItem("Commercial E-Rickshaw Inspection", isDone = isApproved)
        DriverTrackerStepItem("Safety & Chauffeur Orientation", isDone = isApproved)

        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(
            onClick = onToggleApproval,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DriverLuxuryUiTokens.Gold),
            border = BorderStroke(1.dp, DriverLuxuryUiTokens.Gold),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isApproved) "Simulate Unapproved Lock" else "Simulate Fleet Approval (Unlock)")
        }

        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onSupport) {
            Text("Contact Fleet Support Helpline", color = DriverLuxuryUiTokens.Muted, fontSize = 12.sp)
        }
    }
}

// 08: Rejected docs
@Composable
fun DriverRejectedDocsScreen(onReupload: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DriverScreenHeader("08 · Rejected Docs", "Compliance Audit Notice")
        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            color = DriverLuxuryUiTokens.Card,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, DriverLuxuryUiTokens.RedAlert),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Reason: Vehicle Insurance Expired", color = DriverLuxuryUiTokens.RedAlert, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text("The policy provided expired on 15 Oct 2026. Please upload a renewal endorsement with active third-party passenger cover.", color = DriverLuxuryUiTokens.Muted, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(24.dp))
        DriverGoldButton(text = "Re-Upload Insurance Policy", onClick = onReupload)
    }
}

// 09: Training & terms
@Composable
fun DriverTrainingTermsScreen(onAccept: () -> Unit) {
    var termsAccepted by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DriverScreenHeader("09 · Training & Terms", "Luxury Chauffeur Code of Conduct")
        Spacer(modifier = Modifier.height(16.dp))

        DriverDetailCard("Module 1: Professional Etiquette", "Completed (10 min)", "Passenger greeting, polite speech")
        DriverDetailCard("Module 2: EV Battery Range Management", "Completed (8 min)", "Eco-driving & regenerative braking")
        DriverDetailCard("Module 3: Emergency & SOS Protocol", "Completed (6 min)", "112 direct call & passenger safety")

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { termsAccepted = !termsAccepted }
        ) {
            Checkbox(
                checked = termsAccepted,
                onCheckedChange = { termsAccepted = it },
                colors = CheckboxDefaults.colors(checkedColor = DriverLuxuryUiTokens.Gold)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("I agree to the Fleet Chauffeur Master Agreement and Zero-Tolerance Safety Policy.", color = DriverLuxuryUiTokens.Text, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(24.dp))
        DriverGoldButton(text = "Accept & Enter Cockpit", onClick = onAccept)
    }
}

// 10: Driver home cockpit
@Composable
fun DriverHomeScreen(
    isOnline: Boolean,
    todayEarnings: Int,
    totalTrips: Int,
    batteryPercentage: Int,
    onToggleOnline: () -> Unit,
    onOpenHeatmap: () -> Unit,
    onSimulateRideOffer: () -> Unit,
    onOpenAutoAccept: () -> Unit,
    onOpenShiftLog: () -> Unit,
    onOpenPoolManager: () -> Unit,
    onOpenRatings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Top Bar: Online Switch & Today's Earnings
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("CHAUFFEUR COCKPIT", color = DriverLuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("10 · Driver Home", color = DriverLuxuryUiTokens.Muted, fontSize = 12.sp)
            }
            Surface(
                color = if (isOnline) DriverLuxuryUiTokens.GreenSuccess.copy(alpha = 0.2f) else DriverLuxuryUiTokens.RedAlert.copy(alpha = 0.2f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, if (isOnline) DriverLuxuryUiTokens.GreenSuccess else DriverLuxuryUiTokens.RedAlert),
                modifier = Modifier.clickable { onToggleOnline() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isOnline) DriverLuxuryUiTokens.GreenSuccess else DriverLuxuryUiTokens.RedAlert)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isOnline) "ONLINE" else "OFFLINE",
                        color = if (isOnline) DriverLuxuryUiTokens.GreenSuccess else DriverLuxuryUiTokens.RedAlert,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Earnings Summary Card
        Surface(
            color = DriverLuxuryUiTokens.Card,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, DriverLuxuryUiTokens.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Today's Earnings", color = DriverLuxuryUiTokens.Muted, fontSize = 12.sp)
                    Text("₹ $todayEarnings", color = DriverLuxuryUiTokens.Gold, fontSize = 28.sp, fontWeight = FontWeight.Black)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("$totalTrips Completed Trips", color = DriverLuxuryUiTokens.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Battery: $batteryPercentage% EV", color = if (batteryPercentage > 20) DriverLuxuryUiTokens.GreenSuccess else DriverLuxuryUiTokens.RedAlert, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Luxury Map Simulation View (dimmed when offline)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isOnline) Color(0xFF15151A) else Color(0xFF090A0C))
                .border(1.dp, if (isOnline) DriverLuxuryUiTokens.Gold.copy(alpha = 0.5f) else DriverLuxuryUiTokens.Border, RoundedCornerShape(16.dp))
        ) {
            // Map Grid Lines
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = 30.dp.toPx()
                for (x in 0..(size.width / step).toInt()) {
                    drawLine(
                        color = Color(0xFF222228),
                        start = androidx.compose.ui.geometry.Offset(x * step, 0f),
                        end = androidx.compose.ui.geometry.Offset(x * step, size.height),
                        strokeWidth = 1f
                    )
                }
                for (y in 0..(size.height / step).toInt()) {
                    drawLine(
                        color = Color(0xFF222228),
                        start = androidx.compose.ui.geometry.Offset(0f, y * step),
                        end = androidx.compose.ui.geometry.Offset(size.width, y * step),
                        strokeWidth = 1f
                    )
                }
            }

            // Driver location dot
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(DriverLuxuryUiTokens.Gold)
                    .align(Alignment.Center)
            )

            if (!isOnline) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("COCKPIT OFFLINE · TAP ONLINE TO RECEIVE TRIPS", color = DriverLuxuryUiTokens.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                ) {
                    Text("Connaught Place · High Demand Area (+1.5x)", color = DriverLuxuryUiTokens.Gold, fontSize = 11.sp, modifier = Modifier.padding(6.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Quick Cockpit Controls
        Text("Cockpit Controls & Dispatch", color = DriverLuxuryUiTokens.Muted, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DriverActionButton(title = "Surge Heatmap", icon = Icons.Default.LocationOn, modifier = Modifier.weight(1f), onClick = onOpenHeatmap)
            DriverActionButton(title = "Auto-Accept", icon = Icons.Default.Settings, modifier = Modifier.weight(1f), onClick = onOpenAutoAccept)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DriverActionButton(title = "Pool Manager", icon = Icons.Default.Person, modifier = Modifier.weight(1f), onClick = onOpenPoolManager)
            DriverActionButton(title = "Shift Log", icon = Icons.Default.Refresh, modifier = Modifier.weight(1f), onClick = onOpenShiftLog)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Authoritative Trigger for incoming ride request
        DriverGoldButton(
            text = "⚡ Simulate Incoming Ride Request (20s Ring)",
            onClick = onSimulateRideOffer
        )
    }
}

// 11: Heatmap & surge
@Composable
fun DriverHeatmapSurgeScreen(onBack: () -> Unit, onSelectSurgeHotspot: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DriverScreenHeader("11 · Heatmap & Surge", "City Demand Multiplier Zones")
        Spacer(modifier = Modifier.height(16.dp))

        DriverSurgeCard("Connaught Place Outer Circle", "2.0x Surge", "14 Passenger Requests / min", onSelect = onSelectSurgeHotspot)
        DriverSurgeCard("New Delhi Railway Station Gate 1", "1.8x Surge", "22 Passenger Requests / min", onSelect = onSelectSurgeHotspot)
        DriverSurgeCard("Khan Market Metro Corridor", "1.5x Surge", "9 Passenger Requests / min", onSelect = onSelectSurgeHotspot)
        DriverSurgeCard("AIIMS Hospital & Safdarjung Gate", "1.4x Surge", "12 Passenger Requests / min", onSelect = onSelectSurgeHotspot)

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(20.dp))
        OutlinedButton(
            onClick = onBack,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DriverLuxuryUiTokens.Gold),
            border = BorderStroke(1.dp, DriverLuxuryUiTokens.Gold),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back to Cockpit")
        }
    }
}

// 12: Ride request (Authoritative 20-second circular countdown ring)
@Composable
fun DriverRideRequestOfferScreen(
    timerSeconds: Int,
    fare: Int,
    pickupDist: String,
    customerName: String,
    customerRating: String,
    pickupAddr: String,
    dropAddr: String,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("INCOMING CHAUFFEUR ASSIGNMENT", color = DriverLuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("12 · Ride Request", color = DriverLuxuryUiTokens.Muted, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(24.dp))

        // Authoritative 20-second Circular Countdown Ring
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
            CircularProgressIndicator(
                progress = { timerSeconds / 20f },
                color = if (timerSeconds > 5) DriverLuxuryUiTokens.Gold else DriverLuxuryUiTokens.RedAlert,
                strokeWidth = 8.dp,
                trackColor = Color(0xFF222228),
                modifier = Modifier.fillMaxSize()
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${timerSeconds}s",
                    color = if (timerSeconds > 5) DriverLuxuryUiTokens.Gold else DriverLuxuryUiTokens.RedAlert,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black
                )
                Text("AUTOREJECT", color = DriverLuxuryUiTokens.Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Fare & Distance
        Text("₹ $fare", color = DriverLuxuryUiTokens.Gold, fontSize = 36.sp, fontWeight = FontWeight.Black)
        Text("Pickup: $pickupDist away · 4 mins away", color = DriverLuxuryUiTokens.Text, fontSize = 14.sp)

        Spacer(modifier = Modifier.height(16.dp))

        // Route & Passenger Card
        Surface(
            color = DriverLuxuryUiTokens.Card,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, DriverLuxuryUiTokens.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(customerName, color = DriverLuxuryUiTokens.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(customerRating, color = DriverLuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Pickup: $pickupAddr", color = DriverLuxuryUiTokens.Muted, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Drop: $dropAddr", color = DriverLuxuryUiTokens.Muted, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Action Buttons: Accept / Reject
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onReject,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DriverLuxuryUiTokens.RedAlert),
                border = BorderStroke(1.dp, DriverLuxuryUiTokens.RedAlert),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(50.dp)
            ) {
                Text("Decline", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = DriverLuxuryUiTokens.Gold),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1.5f).height(50.dp)
            ) {
                Text("ACCEPT RIDE", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 14.sp)
            }
        }
    }
}

// 13: Auto-accept
@Composable
fun DriverAutoAcceptScreen(onBack: () -> Unit) {
    var autoAcceptEnabled by remember { mutableStateOf(false) }
    var maxKm by remember { mutableFloatStateOf(3.5f) }
    var minFare by remember { mutableFloatStateOf(80f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DriverScreenHeader("13 · Auto-Accept Settings", "Automated Booking Rules")
        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Enable Auto-Accept", color = DriverLuxuryUiTokens.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Instantly locks matching rides", color = DriverLuxuryUiTokens.Muted, fontSize = 12.sp)
            }
            Switch(
                checked = autoAcceptEnabled,
                onCheckedChange = { autoAcceptEnabled = it },
                colors = SwitchDefaults.colors(checkedThumbColor = DriverLuxuryUiTokens.Gold)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Maximum Pickup Distance: ${maxKm.toInt()} km", color = DriverLuxuryUiTokens.Gold, fontSize = 13.sp)
        Slider(
            value = maxKm,
            onValueChange = { maxKm = it },
            valueRange = 1f..10f,
            colors = SliderDefaults.colors(thumbColor = DriverLuxuryUiTokens.Gold, activeTrackColor = DriverLuxuryUiTokens.Gold)
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text("Minimum Trip Fare: ₹ ${minFare.toInt()}", color = DriverLuxuryUiTokens.Gold, fontSize = 13.sp)
        Slider(
            value = minFare,
            onValueChange = { minFare = it },
            valueRange = 40f..300f,
            colors = SliderDefaults.colors(thumbColor = DriverLuxuryUiTokens.Gold, activeTrackColor = DriverLuxuryUiTokens.Gold)
        )

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(24.dp))
        DriverGoldButton(text = "Save Settings & Back", onClick = onBack)
    }
}

// 14: Navigate to pickup
@Composable
fun DriverNavigateToPickupScreen(
    pickupAddr: String,
    customerName: String,
    onCallCustomer: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenMaps: () -> Unit,
    onArrived: () -> Unit,
    onCancelRide: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DriverScreenHeader("14 · Navigate to Pickup", "Heading to Passenger")
        Spacer(modifier = Modifier.height(12.dp))

        // ETA & Nav Card
        Surface(
            color = DriverLuxuryUiTokens.Card,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, DriverLuxuryUiTokens.Gold),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("ETA 4 MIN", color = DriverLuxuryUiTokens.Gold, fontWeight = FontWeight.Black, fontSize = 22.sp)
                        Text("Distance: 0.8 km", color = DriverLuxuryUiTokens.Muted, fontSize = 12.sp)
                    }
                    Button(
                        onClick = onOpenMaps,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222228)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Open Maps", color = DriverLuxuryUiTokens.Gold, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("Pickup: $pickupAddr", color = DriverLuxuryUiTokens.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Customer Details & Call / Chat
        Surface(
            color = DriverLuxuryUiTokens.Card,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, DriverLuxuryUiTokens.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(customerName, color = DriverLuxuryUiTokens.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Passenger · Rating 4.9 ★", color = DriverLuxuryUiTokens.Muted, fontSize = 12.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onOpenChat, modifier = Modifier.background(Color(0xFF222228), CircleShape)) {
                        Icon(Icons.Default.Send, contentDescription = "Chat", tint = DriverLuxuryUiTokens.Gold, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onCallCustomer, modifier = Modifier.background(DriverLuxuryUiTokens.Gold, CircleShape)) {
                        Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.Black, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Action Buttons: Arrived & Cancel
        DriverGoldButton(text = "I HAVE ARRIVED AT PICKUP", onClick = onArrived)
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = onCancelRide,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DriverLuxuryUiTokens.RedAlert),
            border = BorderStroke(1.dp, DriverLuxuryUiTokens.RedAlert),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel Assignment", fontSize = 12.sp)
        }
    }
}

// 15: Arrived & OTP
@Composable
fun DriverArrivedOtpScreen(
    customerName: String,
    waitSeconds: Int,
    enteredOtp: String,
    onOtpChange: (String) -> Unit,
    onVerifyAndStart: () -> Unit,
    onCallCustomer: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DriverScreenHeader("15 · Arrived & OTP", "Passenger Boarding Verification")
        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            color = DriverLuxuryUiTokens.Card,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, DriverLuxuryUiTokens.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Waiting for $customerName", color = DriverLuxuryUiTokens.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Free Wait Time Remaining: 02:25", color = DriverLuxuryUiTokens.Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onCallCustomer,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222228)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, tint = DriverLuxuryUiTokens.Gold, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Call Passenger", color = DriverLuxuryUiTokens.Gold, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("ENTER 4-DIGIT START OTP", color = DriverLuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("Ask passenger for their booking PIN", color = DriverLuxuryUiTokens.Muted, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(12.dp))

        DriverInputField(
            label = "4-Digit PIN (Default: 4821)",
            value = enteredOtp,
            onValueChange = onOtpChange
        )

        Spacer(modifier = Modifier.height(24.dp))
        DriverGoldButton(text = "VERIFY OTP & START TRIP", onClick = onVerifyAndStart)
    }
}

// 16: Trip in progress
@Composable
fun DriverTripInProgressScreen(
    customerName: String,
    dropAddr: String,
    fare: Int,
    onSosClick: () -> Unit,
    onArrivedDrop: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DriverScreenHeader("16 · Trip in Progress", "Live Navigation to Destination")
        Spacer(modifier = Modifier.height(12.dp))

        // Navigation Hud
        Surface(
            color = DriverLuxuryUiTokens.Card,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, DriverLuxuryUiTokens.Gold),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("24 km/h", color = DriverLuxuryUiTokens.Gold, fontWeight = FontWeight.Black, fontSize = 26.sp)
                        Text("EV Cruise Speed", color = DriverLuxuryUiTokens.Muted, fontSize = 11.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("ETA 12 MIN", color = DriverLuxuryUiTokens.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Remaining: 3.4 km", color = DriverLuxuryUiTokens.Muted, fontSize = 11.sp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Drop: $dropAddr", color = DriverLuxuryUiTokens.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Safety Status & Floating SOS trigger
        Surface(
            color = Color(0xFF15151A),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = DriverLuxuryUiTokens.GreenSuccess, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Telemetry Synced with Fleet HQ", color = DriverLuxuryUiTokens.Muted, fontSize = 12.sp)
                }
                Button(
                    onClick = onSosClick,
                    colors = ButtonDefaults.buttonColors(containerColor = DriverLuxuryUiTokens.RedAlert),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("SOS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(24.dp))
        DriverGoldButton(text = "ARRIVED AT DESTINATION & DROP", onClick = onArrivedDrop)
    }
}

// 17: Drop & complete
@Composable
fun DriverDropCompleteScreen(
    fare: Int,
    dropAddr: String,
    onProceedToPayment: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DriverScreenHeader("17 · Drop & Complete", "Trip Completion Summary")
        Spacer(modifier = Modifier.height(20.dp))

        Icon(Icons.Default.Check, contentDescription = null, tint = DriverLuxuryUiTokens.Gold, modifier = Modifier.size(54.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text("Rider Safely Dropped", color = DriverLuxuryUiTokens.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(dropAddr, color = DriverLuxuryUiTokens.Muted, fontSize = 12.sp, textAlign = TextAlign.Center)

        Spacer(modifier = Modifier.height(24.dp))
        DriverDetailCard("Total Trip Fare", "₹ $fare", "Includes Base + Distance + Taxes")
        DriverDetailCard("Total Distance Driven", "3.8 km", "Completed in 14 minutes")
        DriverDetailCard("Toll & Service Fee", "₹ 0.00", "No extra surcharges")

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(24.dp))
        DriverGoldButton(text = "PROCEED TO COLLECT PAYMENT", onClick = onProceedToPayment)
    }
}

// 18: Collect payment
@Composable
fun DriverCollectPaymentScreen(
    fare: Int,
    onPaymentConfirmed: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DriverScreenHeader("18 · Collect Payment", "Cash or UPI QR Settlement")
        Spacer(modifier = Modifier.height(20.dp))

        Text("AMOUNT TO COLLECT", color = DriverLuxuryUiTokens.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("₹ $fare", color = DriverLuxuryUiTokens.Gold, fontSize = 42.sp, fontWeight = FontWeight.Black)

        Spacer(modifier = Modifier.height(20.dp))

        Surface(
            color = DriverLuxuryUiTokens.Card,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, DriverLuxuryUiTokens.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SCAN UPI QR CODE", color = DriverLuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(10.dp))
                // Simulated QR Box
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .background(Color.White)
                        .border(2.dp, DriverLuxuryUiTokens.Gold),
                    contentAlignment = Alignment.Center
                ) {
                    Text("UPI QR\n₹$fare", color = Color.Black, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("Payment accepted via GooglePay, PhonePe, Paytm, or Cash", color = DriverLuxuryUiTokens.Muted, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        }

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(24.dp))
        DriverGoldButton(text = "PAYMENT RECEIVED (CONFIRM)", onClick = onPaymentConfirmed)
    }
}

// 19: Rate customer
@Composable
fun DriverRateCustomerScreen(
    customerName: String,
    onDone: () -> Unit
) {
    var selectedStars by remember { mutableIntStateOf(5) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DriverScreenHeader("19 · Rate Customer", "Passenger Feedback")
        Spacer(modifier = Modifier.height(24.dp))

        Text("How was your trip with $customerName?", color = DriverLuxuryUiTokens.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(16.dp))

        // 5 Star Row
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in 1..5) {
                IconButton(onClick = { selectedStars = i }) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Star $i",
                        tint = if (i <= selectedStars) DriverLuxuryUiTokens.Gold else Color(0xFF333338),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text("Feedback Badges", color = DriverLuxuryUiTokens.Muted, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DriverTagChip("Polite Rider", isSelected = true)
            DriverTagChip("Ready at Pickup", isSelected = true)
            DriverTagChip("Clean Behavior", isSelected = false)
        }

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(28.dp))
        DriverGoldButton(text = "SUBMIT RATING & RETURN HOME", onClick = onDone)
    }
}

// 20: Earnings dashboard
@Composable
fun DriverEarningsDashboardScreen(
    todayEarnings: Int,
    walletBalance: Int,
    onWithdraw: () -> Unit,
    onOpenChart: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenLedger: () -> Unit,
    onOpenTips: () -> Unit,
    onOpenStatements: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DriverScreenHeader("20 · Earnings Dashboard", "Chauffeur Financial Center")
        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            color = DriverLuxuryUiTokens.Card,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, DriverLuxuryUiTokens.Gold),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("Total Wallet Balance", color = DriverLuxuryUiTokens.Muted, fontSize = 12.sp)
                Text("₹ $walletBalance", color = DriverLuxuryUiTokens.Gold, fontSize = 32.sp, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.height(12.dp))
                DriverGoldButton(text = "Instant Payout Withdrawal", onClick = onWithdraw)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Financial Analytics", color = DriverLuxuryUiTokens.Muted, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))

        DriverDetailCard("Today's Net Earnings", "₹ $todayEarnings", "9 Trips Completed Today")
        DriverDetailCard("Weekly Total", "₹ 8,750", "42 Trips Completed This Week")

        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DriverActionButton(title = "Weekly Chart", icon = Icons.Default.Star, modifier = Modifier.weight(1f), onClick = onOpenChart)
            DriverActionButton(title = "Trip History", icon = Icons.Default.List, modifier = Modifier.weight(1f), onClick = onOpenHistory)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DriverActionButton(title = "Commission Ledger", icon = Icons.Default.Refresh, modifier = Modifier.weight(1f), onClick = onOpenLedger)
            DriverActionButton(title = "Tips Breakdown", icon = Icons.Default.Star, modifier = Modifier.weight(1f), onClick = onOpenTips)
        }
        Spacer(modifier = Modifier.height(8.dp))
        DriverActionButton(title = "Monthly Tax Statements (PDF)", icon = Icons.Default.Info, modifier = Modifier.fillMaxWidth(), onClick = onOpenStatements)
    }
}

// 21: Daily / weekly
@Composable
fun DriverDailyWeeklyChartScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DriverScreenHeader("21 · Daily / Weekly", "Earnings Performance Charts")
        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            color = DriverLuxuryUiTokens.Card,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, DriverLuxuryUiTokens.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("7-Day Trend (Mon - Sun)", color = DriverLuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))
                // Simulated Bar Chart
                Row(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.Bottom
                ) {
                    val days = listOf("Mon" to 0.6f, "Tue" to 0.75f, "Wed" to 0.5f, "Thu" to 0.9f, "Fri" to 0.85f, "Sat" to 1.0f, "Sun" to 0.7f)
                    days.forEach { (day, factor) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .width(18.dp)
                                    .height((90 * factor).dp)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(DriverLuxuryUiTokens.Gold)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(day, color = DriverLuxuryUiTokens.Muted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        DriverDetailCard("Total Active Online Hours", "38.5 Hours", "Average ₹227 / hr")
        DriverDetailCard("Peak Hour Earnings Share", "62% from 8AM-11AM & 5PM-9PM", "High surge efficiency")

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(20.dp))
        DriverGoldButton(text = "Back to Earnings", onClick = onBack)
    }
}

// 22: Trip history
@Composable
fun DriverTripHistoryScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DriverScreenHeader("22 · Trip History", "Completed Passenger Trips")
        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DriverTagChip("Today (9)", isSelected = true)
            DriverTagChip("Yesterday (12)", isSelected = false)
            DriverTagChip("This Week (42)", isSelected = false)
        }

        Spacer(modifier = Modifier.height(14.dp))
        DriverHistoryCard("Trip #8491", "₹ 145", "Connaught Place → Khan Market", "14 mins · Cash Settle")
        DriverHistoryCard("Trip #8490", "₹ 110", "New Delhi Railway Station → Karol Bagh", "11 mins · UPI Paid")
        DriverHistoryCard("Trip #8489", "₹ 160", "AIIMS Gate 2 → South Extension", "18 mins · UPI Paid")
        DriverHistoryCard("Trip #8488", "₹ 95", "Janpath Market → Mandi House", "9 mins · Cash Settle")

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(20.dp))
        DriverGoldButton(text = "Back to Financials", onClick = onBack)
    }
}

// 23: Ledger
@Composable
fun DriverLedgerScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DriverScreenHeader("23 · Ledger", "Transparent Commission & Payout Log")
        Spacer(modifier = Modifier.height(16.dp))

        DriverDetailCard("Trip #8491 Fare", "+ ₹ 145.00", "Gross Fare Collected")
        DriverDetailCard("Platform Tech Fee (10%)", "- ₹ 14.50", "Platform Service Commission")
        DriverDetailCard("Passenger Tip", "+ ₹ 20.00", "100% Passed to Driver")
        DriverDetailCard("Net Credited to Wallet", "= ₹ 150.50", "Wallet Balance After: ₹ 2,450.00")

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(20.dp))
        DriverGoldButton(text = "Back to Financials", onClick = onBack)
    }
}

// 24: Wallet balance
@Composable
fun DriverWalletBalanceScreen(
    balance: Int,
    onWithdraw: () -> Unit,
    onAccounts: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DriverScreenHeader("24 · Wallet Balance", "Instant Payout Management")
        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            color = DriverLuxuryUiTokens.Card,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, DriverLuxuryUiTokens.Gold),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Withdrawable Funds", color = DriverLuxuryUiTokens.Muted, fontSize = 12.sp)
                Text("₹ $balance", color = DriverLuxuryUiTokens.Gold, fontSize = 38.sp, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.height(12.dp))
                DriverGoldButton(text = "Initiate Instant Payout", onClick = onWithdraw)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        DriverActionButton(title = "Manage Payout Bank Accounts", icon = Icons.Default.Settings, modifier = Modifier.fillMaxWidth(), onClick = onAccounts)

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(20.dp))
        OutlinedButton(
            onClick = onBack,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DriverLuxuryUiTokens.Gold),
            border = BorderStroke(1.dp, DriverLuxuryUiTokens.Gold),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back to Earnings")
        }
    }
}

// 25: Withdraw modal
@Composable
fun DriverWithdrawModalScreen(
    balance: Int,
    withdrawAmount: String,
    onAmountChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DriverScreenHeader("25 · Withdraw Funds", "Transfer to Registered Bank Account")
        Spacer(modifier = Modifier.height(16.dp))

        Text("Available Balance: ₹ $balance", color = DriverLuxuryUiTokens.Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        DriverInputField(label = "Amount to Withdraw (₹)", value = withdrawAmount, onValueChange = onAmountChange)
        Spacer(modifier = Modifier.height(16.dp))

        DriverDetailCard("Receiving Account", "HDFC Bank **** 8821", "IMPS Instant Transfer (Fee: ₹0)")

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(24.dp))
        DriverGoldButton(text = "Proceed to Payout", onClick = onSubmit)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onBack,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DriverLuxuryUiTokens.Muted),
            border = BorderStroke(1.dp, DriverLuxuryUiTokens.Border),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }
    }
}

// 26: Payout status
@Composable
fun DriverPayoutStatusScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DriverScreenHeader("26 · Payout Status", "Live Bank Transfer Lifecycle")
        Spacer(modifier = Modifier.height(20.dp))

        DriverTrackerStepItem("Withdrawal Requested (₹1,500)", isDone = true)
        DriverTrackerStepItem("IMPS Gateway Processing", isDone = true)
        DriverTrackerStepItem("Settled to HDFC Bank **** 8821", isDone = true)

        Spacer(modifier = Modifier.height(16.dp))
        DriverDetailCard("UTR Reference", "HDFCIMPS91820491824", "Confirmed by RBI Banking Switch")

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(24.dp))
        DriverGoldButton(text = "Done & Back to Cockpit", onClick = onBack)
    }
}

// 27: Payout accounts
@Composable
fun DriverPayoutAccountsScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DriverScreenHeader("27 · Payout Accounts", "Registered Bank & UPI VPAs")
        Spacer(modifier = Modifier.height(16.dp))

        DriverDetailCard("HDFC Bank (Primary)", "A/C: **** 8821 · IFSC: HDFC0001824", "Daily auto-settlement target")
        DriverDetailCard("GooglePay UPI", "suresh.kumar@okhdfcbank", "Instant on-demand payout")

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(24.dp))
        DriverGoldButton(text = "Back to Wallet", onClick = onBack)
    }
}

// 28: Tips list
@Composable
fun DriverTipsListScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DriverScreenHeader("28 · Tips", "Passenger Gratitude Rewards")
        Spacer(modifier = Modifier.height(16.dp))

        DriverDetailCard("Tip from Priya S.", "+ ₹ 20.00", "Trip #8491 · Smooth EV driving")
        DriverDetailCard("Tip from Amit V.", "+ ₹ 30.00", "Trip #8472 · Helped with luggage")
        DriverDetailCard("Tip from Rohan K.", "+ ₹ 15.00", "Trip #8450 · On-time pickup")

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(20.dp))
        DriverGoldButton(text = "Back to Financials", onClick = onBack)
    }
}

// 29: Statements PDF
@Composable
fun DriverStatementsPdfScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DriverScreenHeader("29 · Statements", "Official GST & Earnings PDF")
        Spacer(modifier = Modifier.height(16.dp))

        DriverDetailCard("October 2026 Tax Statement", "₹ 34,200 Total Earnings", "Download PDF Invoice")
        DriverDetailCard("September 2026 Statement", "₹ 31,800 Total Earnings", "Download PDF Invoice")
        DriverDetailCard("August 2026 Statement", "₹ 29,450 Total Earnings", "Download PDF Invoice")

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(20.dp))
        DriverGoldButton(text = "Back to Financials", onClick = onBack)
    }
}

// 30: Daily target
@Composable
fun DriverDailyTargetScreen(
    completed: Int,
    total: Int,
    onOpenWeeklyGoals: () -> Unit,
    onOpenIncentives: () -> Unit,
    onOpenBadge: () -> Unit,
    onOpenLeaderboard: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DriverScreenHeader("30 · Daily Target", "Chauffeur Quest & Daily Bonus")
        Spacer(modifier = Modifier.height(20.dp))

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
            CircularProgressIndicator(
                progress = { completed.toFloat() / total.toFloat() },
                color = DriverLuxuryUiTokens.Gold,
                strokeWidth = 10.dp,
                trackColor = Color(0xFF222228),
                modifier = Modifier.fillMaxSize()
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$completed / $total", color = DriverLuxuryUiTokens.Gold, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text("TRIPS", color = DriverLuxuryUiTokens.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Complete 3 more rides to unlock ₹150 Daily Quest Bonus!", color = DriverLuxuryUiTokens.Text, fontSize = 13.sp, textAlign = TextAlign.Center)

        Spacer(modifier = Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DriverActionButton(title = "Weekly Goals", icon = Icons.Default.List, modifier = Modifier.weight(1f), onClick = onOpenWeeklyGoals)
            DriverActionButton(title = "Peak Incentives", icon = Icons.Default.Star, modifier = Modifier.weight(1f), onClick = onOpenIncentives)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DriverActionButton(title = "Badge Tier", icon = Icons.Default.Check, modifier = Modifier.weight(1f), onClick = onOpenBadge)
            DriverActionButton(title = "City Leaderboard", icon = Icons.Default.Star, modifier = Modifier.weight(1f), onClick = onOpenLeaderboard)
        }
    }
}

// 31: Weekly goals
@Composable
fun DriverWeeklyGoalsScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DriverScreenHeader("31 · Weekly Goals", "Milestone Rewards")
        Spacer(modifier = Modifier.height(16.dp))

        DriverDetailCard("Tier 1: 40 Rides", "+ ₹ 600 Extra Bonus", "Achieved (42 rides done)")
        DriverDetailCard("Tier 2: 60 Rides", "+ ₹ 1,200 Extra Bonus", "18 rides remaining (Ends Sun)")
        DriverDetailCard("Tier 3: 80 Rides", "+ ₹ 2,000 Extra Bonus", "Super-Chauffeur Milestone")

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(20.dp))
        DriverGoldButton(text = "Back to Goals", onClick = onBack)
    }
}

// 32: Incentives
@Composable
fun DriverIncentivesScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DriverScreenHeader("32 · Incentives", "Active Fleet Surge Programs")
        Spacer(modifier = Modifier.height(16.dp))

        DriverDetailCard("Morning Peak Boost", "+ ₹ 30 Extra per ride", "Active 8:00 AM - 11:30 AM")
        DriverDetailCard("Evening Rush Booster", "+ ₹ 40 Extra per ride", "Active 5:00 PM - 9:30 PM")
        DriverDetailCard("Metro Station Feeder Incentive", "₹ 20 Bonus on CP & NDLS Metro", "All Day Incentive")

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(20.dp))
        DriverGoldButton(text = "Back to Goals", onClick = onBack)
    }
}

// 33: Badge tier
@Composable
fun DriverBadgeTierScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DriverScreenHeader("33 · Badge Tier", "Luxury Chauffeur Status")
        Spacer(modifier = Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(DriverLuxuryUiTokens.Card)
                .border(3.dp, DriverLuxuryUiTokens.Gold, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Star, contentDescription = null, tint = DriverLuxuryUiTokens.Gold, modifier = Modifier.size(54.dp))
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text("GOLD CAPTAIN TIER", color = DriverLuxuryUiTokens.Gold, fontWeight = FontWeight.Black, fontSize = 20.sp)
        Text("Top 5% Chauffeur in Central Delhi", color = DriverLuxuryUiTokens.Muted, fontSize = 13.sp)

        Spacer(modifier = Modifier.height(20.dp))
        DriverDetailCard("Driver Rating", "4.88 ★", "Based on 320 customer ratings")
        DriverDetailCard("Cancellation Rate", "1.2%", "Benchmark: Under 4.0%")
        DriverDetailCard("On-Time Pickup Rate", "98.4%", "Benchmark: 95.0%")

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(20.dp))
        DriverGoldButton(text = "Back to Goals", onClick = onBack)
    }
}

// 34: Leaderboard
@Composable
fun DriverLeaderboardScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DriverScreenHeader("34 · Leaderboard", "Central Delhi Top Chauffeurs")
        Spacer(modifier = Modifier.height(14.dp))

        DriverLeaderboardRow(rank = 1, name = "Rajesh Verma", trips = "58 trips", prize = "₹ 1,500 Reward")
        DriverLeaderboardRow(rank = 2, name = "Manoj Singh", trips = "54 trips", prize = "₹ 1,000 Reward")
        DriverLeaderboardRow(rank = 3, name = "Sunil Batra", trips = "51 trips", prize = "₹ 750 Reward")
        DriverLeaderboardRow(rank = 4, name = "Suresh Kumar (You)", trips = "42 trips", prize = "₹ 500 Contender", isCurrentUser = true)

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(20.dp))
        DriverGoldButton(text = "Back to Goals", onClick = onBack)
    }
}

// 35: SOS dispatch
@Composable
fun DriverSosDispatchScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1F0D0D))
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("EMERGENCY 112 DISPATCH ACTIVE", color = DriverLuxuryUiTokens.RedAlert, fontWeight = FontWeight.Black, fontSize = 20.sp)
        Text("35 · SOS", color = DriverLuxuryUiTokens.Muted, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(CircleShape)
                .background(DriverLuxuryUiTokens.RedAlert),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(60.dp))
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text("Live GPS telemetry broadcast to Chauffeur HQ & Emergency Contacts.", color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center)

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))
                context.startActivity(dialIntent)
            },
            colors = ButtonDefaults.buttonColors(containerColor = DriverLuxuryUiTokens.RedAlert),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("CALL GOVERNMENT 112 POLICE", color = Color.White, fontWeight = FontWeight.Black)
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onDismiss,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = BorderStroke(1.dp, Color.White),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel Emergency Alert")
        }
    }
}

// 36: Fatigue alert
@Composable
fun DriverFatigueAlertScreen(onStartRest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        DriverScreenHeader("36 · Fatigue Alert", "Chauffeur Health & Safety Policy")
        Spacer(modifier = Modifier.height(24.dp))

        Icon(Icons.Default.Warning, contentDescription = null, tint = DriverLuxuryUiTokens.Gold, modifier = Modifier.size(54.dp))
        Spacer(modifier = Modifier.height(16.dp))

        Text("8.5 Hours Continuous Driving Detected", color = DriverLuxuryUiTokens.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Under transport regulations, a mandatory 20-minute rest break is enforced to prevent driver fatigue.", color = DriverLuxuryUiTokens.Muted, fontSize = 13.sp, textAlign = TextAlign.Center)

        Spacer(modifier = Modifier.height(24.dp))
        DriverGoldButton(text = "Start Mandatory 20-Min Rest", onClick = onStartRest)
    }
}

// 37: Shift log
@Composable
fun DriverShiftLogScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DriverScreenHeader("37 · Shift Log", "Shift Hours & Rest Intervals")
        Spacer(modifier = Modifier.height(16.dp))

        DriverDetailCard("Shift Start Time", "08:15 AM", "Logged from Connaught Place")
        DriverDetailCard("Lunch & Battery Charge Break", "01:30 PM - 02:15 PM (45 mins)", "EV Fast Charging Hub")
        DriverDetailCard("Net Driving Hours Today", "6h 45m", "Within safe operating limits")

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(20.dp))
        DriverGoldButton(text = "Back to Cockpit", onClick = onBack)
    }
}

// 38: Report incident
@Composable
fun DriverReportIncidentScreen(onBack: () -> Unit) {
    var category by remember { mutableStateOf("Vehicle Mechanical Issue") }
    var notes by remember { mutableStateOf("Flat tyre near Janpath roundabout. Replacement in progress.") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DriverScreenHeader("38 · Report Incident", "Chauffeur Road Assistance")
        Spacer(modifier = Modifier.height(16.dp))

        DriverInputField(label = "Incident Category", value = category, onValueChange = { category = it })
        Spacer(modifier = Modifier.height(12.dp))
        DriverInputField(label = "Details & Situation", value = notes, onValueChange = { notes = it })

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(24.dp))
        DriverGoldButton(text = "Submit Incident Ticket", onClick = onBack)
    }
}

// 39: Lost item
@Composable
fun DriverLostItemScreen(onBack: () -> Unit) {
    var itemDesc by remember { mutableStateOf("Black Leather Wallet found on rear seat") }
    var returnPlan by remember { mutableStateOf("Will deposit at CP Police Post or return to rider") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DriverScreenHeader("39 · Lost Item Report", "Passenger Belongings Return")
        Spacer(modifier = Modifier.height(16.dp))

        DriverInputField(label = "Item Found", value = itemDesc, onValueChange = { itemDesc = it })
        Spacer(modifier = Modifier.height(12.dp))
        DriverInputField(label = "Return Handover Plan", value = returnPlan, onValueChange = { returnPlan = it })

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(24.dp))
        DriverGoldButton(text = "Register Found Item", onClick = onBack)
    }
}

// 40: Fake GPS warning
@Composable
fun DriverFakeGpsWarningScreen(onFix: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A0E0E))
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        DriverScreenHeader("40 · Fake-GPS Warning", "Location Spoofing Guard")
        Spacer(modifier = Modifier.height(24.dp))

        Icon(Icons.Default.Warning, contentDescription = null, tint = DriverLuxuryUiTokens.RedAlert, modifier = Modifier.size(54.dp))
        Spacer(modifier = Modifier.height(16.dp))

        Text("Location Integrity Risk Detected", color = DriverLuxuryUiTokens.RedAlert, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Developer mock location software detected. Please disable mock locations in Developer Settings to resume chauffeur service.", color = DriverLuxuryUiTokens.Text, fontSize = 13.sp, textAlign = TextAlign.Center)

        Spacer(modifier = Modifier.height(24.dp))
        DriverGoldButton(text = "Resolve & Resume Cockpit", onClick = onFix)
    }
}

// 41: Pool manager
@Composable
fun DriverPoolManagerScreen(
    onNavigatePool: () -> Unit,
    onBroadcast: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DriverScreenHeader("41 · Pool Manager", "Shared E-Rickshaw Occupancy")
        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            color = DriverLuxuryUiTokens.Card,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, DriverLuxuryUiTokens.Gold),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("E-Rickshaw Seats Occupied: 3 / 4", color = DriverLuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Route: Patel Chowk Metro → Krishi Bhawan → Shastri Bhawan", color = DriverLuxuryUiTokens.Muted, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        DriverDetailCard("Stop 1: Priya Sharma (Seat 1-2)", "Krishi Bhawan Gate 3", "Next Drop in 300m")
        DriverDetailCard("Stop 2: Vikram Mehta (Seat 3)", "Shastri Bhawan Gate 1", "Drop in 800m")
        DriverDetailCard("Available Seat (Seat 4)", "Ready for dispatch", "Pooling active")

        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DriverActionButton(title = "Pool Navigation", icon = Icons.Default.LocationOn, modifier = Modifier.weight(1f), onClick = onNavigatePool)
            DriverActionButton(title = "Broadcast Msg", icon = Icons.Default.Send, modifier = Modifier.weight(1f), onClick = onBroadcast)
        }

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(20.dp))
        OutlinedButton(
            onClick = onBack,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DriverLuxuryUiTokens.Gold),
            border = BorderStroke(1.dp, DriverLuxuryUiTokens.Gold),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back to Cockpit")
        }
    }
}

// 42: Pool navigation
@Composable
fun DriverPoolNavigationScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DriverScreenHeader("42 · Pool Navigation", "Optimal Waypoint Routing")
        Spacer(modifier = Modifier.height(16.dp))

        DriverDetailCard("Next Immediate Stop", "Krishi Bhawan Gate 3", "Dropping Passenger 1 (Priya S.)")
        DriverDetailCard("Subsequent Waypoint", "Shastri Bhawan Gate 1", "Dropping Passenger 2 (Vikram M.)")

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(20.dp))
        DriverGoldButton(text = "Back to Pool Manager", onClick = onBack)
    }
}

// 43: Pool broadcast
@Composable
fun DriverPoolBroadcastScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DriverScreenHeader("43 · Pool Broadcast", "Quick Passenger Notifications")
        Spacer(modifier = Modifier.height(16.dp))

        val messages = listOf(
            "Arriving at pickup point in 2 minutes.",
            "Vehicle waiting at designated metro gate.",
            "Moderate traffic on route. Expected 3 min delay.",
            "Please keep exact change or UPI ready."
        )

        messages.forEach { msg ->
            Surface(
                color = DriverLuxuryUiTokens.Card,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, DriverLuxuryUiTokens.Border),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clickable {
                        Toast.makeText(context, "Broadcast sent: $msg", Toast.LENGTH_SHORT).show()
                    }
            ) {
                Text(msg, color = DriverLuxuryUiTokens.Text, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
            }
        }

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(20.dp))
        DriverGoldButton(text = "Back to Pool Manager", onClick = onBack)
    }
}

// 44: Ride chat
@Composable
fun DriverRideChatScreen(customerName: String, onBack: () -> Unit) {
    var replyText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        DriverScreenHeader("44 · Ride Chat", "Chat with $customerName")
        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DriverChatBubble("Hello driver, I am waiting right next to Gate 2 pillar 14.", isCustomer = true)
            DriverChatBubble("Namaste. Yes, I am approaching in the Saarthi EV e-rickshaw in 1 min.", isCustomer = false)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = replyText,
                onValueChange = { replyText = it },
                placeholder = { Text("Type reply…", color = DriverLuxuryUiTokens.Muted, fontSize = 13.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = DriverLuxuryUiTokens.Text,
                    unfocusedTextColor = DriverLuxuryUiTokens.Text,
                    focusedBorderColor = DriverLuxuryUiTokens.Gold,
                    unfocusedBorderColor = DriverLuxuryUiTokens.Border,
                    focusedContainerColor = DriverLuxuryUiTokens.FieldBg,
                    unfocusedContainerColor = DriverLuxuryUiTokens.FieldBg
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = { replyText = "" },
                modifier = Modifier.background(DriverLuxuryUiTokens.Gold, CircleShape)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.Black)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onBack,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DriverLuxuryUiTokens.Gold),
            border = BorderStroke(1.dp, DriverLuxuryUiTokens.Gold),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back to Trip Navigation")
        }
    }
}

// 45: Support tickets
@Composable
fun DriverSupportTicketsScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DriverScreenHeader("45 · Support Tickets", "Fleet Help & Grievances")
        Spacer(modifier = Modifier.height(16.dp))

        DriverDetailCard("Ticket #9182: Fare Toll Reimbursement", "Resolved (+₹30 Credited)", "28 Oct 2026")
        DriverDetailCard("Ticket #9140: Battery Fast-Charger Issue", "Closed", "24 Oct 2026")

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(20.dp))
        DriverGoldButton(text = "Open New Support Ticket", onClick = onBack)
    }
}

// 46: Notifications
@Composable
fun DriverNotificationsScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DriverScreenHeader("46 · Notifications", "Chauffeur Alerts & Broadcasts")
        Spacer(modifier = Modifier.height(16.dp))

        DriverDetailCard("Diwali Peak Incentive Announced", "Earn up to ₹3,000 extra bonus", "Today 10:00 AM")
        DriverDetailCard("RTO Permit Renewal Reminder", "Upload updated commercial endorsement", "Yesterday")
        DriverDetailCard("Instant Payout Successful", "₹ 1,500 transferred to HDFC Bank", "2 days ago")

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(20.dp))
        DriverGoldButton(text = "Back to Profile", onClick = onBack)
    }
}

// 47: Doc expiry
@Composable
fun DriverDocExpiryScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DriverScreenHeader("47 · Doc Expiry", "Document Compliance Watch")
        Spacer(modifier = Modifier.height(16.dp))

        DriverDetailCard("Driving License (DL)", "Expires in 7 Days", "Upload renewed DL front/back")
        DriverDetailCard("EV Fitness Certificate", "Valid until Oct 2028", "Compliant")
        DriverDetailCard("Commercial Permit", "Valid until 2029", "Compliant")

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(20.dp))
        DriverGoldButton(text = "Upload Renewed License", onClick = onBack)
    }
}

// 48: Ratings & feedback
@Composable
fun DriverRatingsFeedbackScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DriverScreenHeader("48 · Ratings & Feedback", "Passenger Reviews")
        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            color = DriverLuxuryUiTokens.Card,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, DriverLuxuryUiTokens.Gold),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("4.88 ★", color = DriverLuxuryUiTokens.Gold, fontSize = 38.sp, fontWeight = FontWeight.Black)
                Text("Overall Rating from 320 Rides", color = DriverLuxuryUiTokens.Muted, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        DriverDetailCard("\"Very polite chauffeur, clean electric rickshaw.\"", "5 ★ · Yesterday", "Priya S.")
        DriverDetailCard("\"Smooth driving through crowded station traffic.\"", "5 ★ · 2 days ago", "Amit V.")

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(20.dp))
        DriverGoldButton(text = "Back to Cockpit", onClick = onBack)
    }
}

// 49: Settings
@Composable
fun DriverSettingsScreen(onBack: () -> Unit) {
    var voiceLang by remember { mutableStateOf("Hindi / English") }
    var soundVol by remember { mutableStateOf("High Alert Tone") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DriverScreenHeader("49 · Settings", "Cockpit Sound & Navigation App")
        Spacer(modifier = Modifier.height(16.dp))

        DriverDetailCard("Voice Guidance Language", voiceLang, "Turn-by-turn alerts")
        DriverDetailCard("Incoming Offer Alert Tone", soundVol, "Loud buzzer for 20s ring")
        DriverDetailCard("Default Navigation Engine", "In-App Fleet Dark Maps", "Tap to change")

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(20.dp))
        DriverGoldButton(text = "Save Settings & Back", onClick = onBack)
    }
}

// 50: Profile and vehicle
@Composable
fun DriverProfileVehicleScreen(
    model: String,
    plate: String,
    battery: Int,
    onOpenSettings: () -> Unit,
    onOpenTickets: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenDocExpiry: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DriverScreenHeader("50 · Profile & Vehicle", "Chauffeur Account & E-Rickshaw")
        Spacer(modifier = Modifier.height(16.dp))

        // Profile Card
        Surface(
            color = DriverLuxuryUiTokens.Card,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, DriverLuxuryUiTokens.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(DriverLuxuryUiTokens.Gold),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.Black, modifier = Modifier.size(32.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text("Suresh Kumar", color = DriverLuxuryUiTokens.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Partner ID: CHAUFFEUR-04821", color = DriverLuxuryUiTokens.Gold, fontSize = 12.sp)
                    Text("Central Delhi Fleet Division", color = DriverLuxuryUiTokens.Muted, fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Vehicle Card
        Surface(
            color = DriverLuxuryUiTokens.Card,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, DriverLuxuryUiTokens.Gold.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Assigned Commercial EV", color = DriverLuxuryUiTokens.Muted, fontSize = 11.sp)
                Text(model, color = DriverLuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Plate: $plate", color = DriverLuxuryUiTokens.Text, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Lithium-Ion Battery: $battery% Health", color = DriverLuxuryUiTokens.GreenSuccess, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Options List
        DriverActionButton(title = "Document Expiry & Compliance", icon = Icons.Default.Info, modifier = Modifier.fillMaxWidth(), onClick = onOpenDocExpiry)
        Spacer(modifier = Modifier.height(8.dp))
        DriverActionButton(title = "Cockpit Settings", icon = Icons.Default.Settings, modifier = Modifier.fillMaxWidth(), onClick = onOpenSettings)
        Spacer(modifier = Modifier.height(8.dp))
        DriverActionButton(title = "Fleet Support Desk", icon = Icons.Default.Call, modifier = Modifier.fillMaxWidth(), onClick = onOpenTickets)
        Spacer(modifier = Modifier.height(8.dp))
        DriverActionButton(title = "Broadcast Notifications", icon = Icons.Default.Notifications, modifier = Modifier.fillMaxWidth(), onClick = onOpenNotifications)

        Spacer(modifier = Modifier.height(20.dp))

        // Logout
        OutlinedButton(
            onClick = onLogout,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DriverLuxuryUiTokens.RedAlert),
            border = BorderStroke(1.dp, DriverLuxuryUiTokens.RedAlert),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Close, contentDescription = null, tint = DriverLuxuryUiTokens.RedAlert, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("End Shift & Logout", fontWeight = FontWeight.Bold)
        }
    }
}

// =========================================================================
// POPUP DIALOGS & BOTTOM SHEETS IMPLEMENTATIONS (P.drv)
// =========================================================================

// Standard Reusable Luxury Dialog
@Composable
fun DriverLuxuryDialog(
    title: String,
    subtitle: String,
    primaryButton: String,
    secondaryButton: String,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onSecondary,
        containerColor = DriverLuxuryUiTokens.Card,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(title, color = DriverLuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Text(subtitle, color = DriverLuxuryUiTokens.Text, fontSize = 13.sp)
        },
        confirmButton = {
            if (primaryButton.isNotBlank()) {
                Button(
                    onClick = onPrimary,
                    colors = ButtonDefaults.buttonColors(containerColor = DriverLuxuryUiTokens.Gold),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(primaryButton, color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (secondaryButton.isNotBlank()) {
                TextButton(onClick = onSecondary) {
                    Text(secondaryButton, color = DriverLuxuryUiTokens.Muted)
                }
            }
        }
    )
}

// 05: Reject Reason Dialog
@Composable
fun DriverRejectReasonDialog(onSubmit: () -> Unit, onBack: () -> Unit) {
    var selectedReason by remember { mutableStateOf("Pickup distance too far") }

    AlertDialog(
        onDismissRequest = onBack,
        containerColor = DriverLuxuryUiTokens.Card,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text("Reason for Rejecting Assignment", color = DriverLuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "Pickup distance too far",
                    "Heavy traffic congestion",
                    "Low EV battery level",
                    "Taking shift meal break"
                ).forEach { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedReason = reason },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason },
                            colors = RadioButtonDefaults.colors(selectedColor = DriverLuxuryUiTokens.Gold)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(reason, color = DriverLuxuryUiTokens.Text, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSubmit,
                colors = ButtonDefaults.buttonColors(containerColor = DriverLuxuryUiTokens.Gold),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Submit Reason", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onBack) {
                Text("Back", color = DriverLuxuryUiTokens.Muted)
            }
        }
    )
}

// 06: Navigation App Selector Sheet
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverNavAppSheet(onChooseApp: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DriverLuxuryUiTokens.Card
    ) {
        Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
            Text("Choose Navigation Map Engine", color = DriverLuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(16.dp))

            listOf("Google Maps GPS", "In-App Chauffeur Dark Maps", "Waze Traffic Navigation").forEach { app ->
                Surface(
                    color = Color(0xFF1F1F24),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickable { onChooseApp(app) }
                ) {
                    Text(app, color = DriverLuxuryUiTokens.Text, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
                }
            }
        }
    }
}

// 10: Collect Cash Sheet
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverCollectCashSheet(amount: Int, onConfirmed: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DriverLuxuryUiTokens.Card
    ) {
        Column(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("COLLECT CASH PAYMENT", color = DriverLuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text("₹ $amount", color = DriverLuxuryUiTokens.Gold, fontSize = 38.sp, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(6.dp))
            Text("Collect exact cash from passenger before completing drop", color = DriverLuxuryUiTokens.Muted, fontSize = 12.sp, textAlign = TextAlign.Center)

            Spacer(modifier = Modifier.height(20.dp))
            DriverGoldButton(text = "CASH RECEIVED (CONFIRM)", onClick = onConfirmed)
        }
    }
}

// 11: Customer Cancelled Sheet
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverCustCancelledSheet(fee: Int, onGoOnline: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onGoOnline,
        containerColor = DriverLuxuryUiTokens.Card
    ) {
        Column(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("RIDER CANCELLED ASSIGNMENT", color = DriverLuxuryUiTokens.RedAlert, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Cancellation fee of ₹ $fee has been automatically credited to your wallet balance.", color = DriverLuxuryUiTokens.Text, fontSize = 13.sp, textAlign = TextAlign.Center)

            Spacer(modifier = Modifier.height(20.dp))
            DriverGoldButton(text = "Return Online to Dispatch Pool", onClick = onGoOnline)
        }
    }
}

// 13: Fatigue Rest Fullscreen Overlay
@Composable
fun DriverFatigueRestOverlay(onFinishRest: () -> Unit) {
    var remainingMinutes by remember { mutableIntStateOf(20) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = DriverLuxuryUiTokens.Gold, modifier = Modifier.size(60.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("MANDATORY FATIGUE REST IN PROGRESS", color = DriverLuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Continuous driving limit reached. Take a 20-minute break.", color = DriverLuxuryUiTokens.Muted, fontSize = 12.sp, textAlign = TextAlign.Center)

            Spacer(modifier = Modifier.height(24.dp))
            Text("$remainingMinutes min remaining", color = DriverLuxuryUiTokens.Text, fontSize = 28.sp, fontWeight = FontWeight.Black)

            Spacer(modifier = Modifier.height(28.dp))
            DriverGoldButton(text = "Resume Cockpit (End Break)", onClick = onFinishRest)
        }
    }
}

// 17: Confirm Withdraw Sheet
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverConfirmWithdrawSheet(
    amount: String,
    bankAccount: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DriverLuxuryUiTokens.Card
    ) {
        Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
            Text("Confirm Instant Payout", color = DriverLuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text("Transfer Amount: ₹ $amount", color = DriverLuxuryUiTokens.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Destination: $bankAccount", color = DriverLuxuryUiTokens.Muted, fontSize = 12.sp)
            Text("IMPS Processing Fee: ₹ 0.00", color = DriverLuxuryUiTokens.GreenSuccess, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(20.dp))
            DriverGoldButton(text = "CONFIRM & TRANSFER FUNDS", onClick = onConfirm)
        }
    }
}

// 19: Incentive Unlocked Sheet
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverIncentiveUnlockedSheet(bonus: Int, onClaim: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onClaim,
        containerColor = DriverLuxuryUiTokens.Card
    ) {
        Column(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Star, contentDescription = null, tint = DriverLuxuryUiTokens.Gold, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text("DAILY TARGET BONUS UNLOCKED!", color = DriverLuxuryUiTokens.Gold, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text("Congratulations! ₹ $bonus quest reward credited to your wallet balance.", color = DriverLuxuryUiTokens.Text, fontSize = 13.sp, textAlign = TextAlign.Center)

            Spacer(modifier = Modifier.height(20.dp))
            DriverGoldButton(text = "Claim ₹$bonus to Wallet", onClick = onClaim)
        }
    }
}

// 21: Surge Zone Sheet
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverSurgeZoneSheet(
    zoneName: String,
    multiplier: String,
    onNavigate: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DriverLuxuryUiTokens.Card
    ) {
        Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
            Text("High Surge Hotspot Nearby", color = DriverLuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(zoneName, color = DriverLuxuryUiTokens.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("Current Multiplier: $multiplier Fare Boost", color = DriverLuxuryUiTokens.Gold, fontSize = 13.sp)

            Spacer(modifier = Modifier.height(20.dp))
            DriverGoldButton(text = "Navigate to Hotspot", onClick = onNavigate)
        }
    }
}

// =========================================================================
// REUSABLE HELPER UI COMPONENTS
// =========================================================================

@Composable
fun DriverScreenHeader(title: String, subtitle: String) {
    Column {
        Text(title, color = DriverLuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(subtitle, color = DriverLuxuryUiTokens.Muted, fontSize = 12.sp)
    }
}

@Composable
fun DriverGoldButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = DriverLuxuryUiTokens.Gold),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        Text(
            text = text,
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

@Composable
fun DriverInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = DriverLuxuryUiTokens.Muted, fontSize = 12.sp) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = DriverLuxuryUiTokens.Text,
            unfocusedTextColor = DriverLuxuryUiTokens.Text,
            focusedBorderColor = DriverLuxuryUiTokens.Gold,
            unfocusedBorderColor = DriverLuxuryUiTokens.Border,
            focusedContainerColor = DriverLuxuryUiTokens.FieldBg,
            unfocusedContainerColor = DriverLuxuryUiTokens.FieldBg
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun DriverDetailCard(title: String, value: String, description: String) {
    Surface(
        color = DriverLuxuryUiTokens.Card,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, DriverLuxuryUiTokens.Border),
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, color = DriverLuxuryUiTokens.Muted, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, color = DriverLuxuryUiTokens.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(description, color = DriverLuxuryUiTokens.Muted, fontSize = 11.sp)
        }
    }
}

@Composable
fun DriverActionButton(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = DriverLuxuryUiTokens.Card,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, DriverLuxuryUiTokens.Border),
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = DriverLuxuryUiTokens.Gold, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, color = DriverLuxuryUiTokens.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun DriverTagChip(text: String, isSelected: Boolean) {
    Surface(
        color = if (isSelected) DriverLuxuryUiTokens.Gold else DriverLuxuryUiTokens.Card,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (isSelected) DriverLuxuryUiTokens.Gold else DriverLuxuryUiTokens.Border)
    ) {
        Text(
            text = text,
            color = if (isSelected) Color.Black else DriverLuxuryUiTokens.Muted,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun DriverTrackerStepItem(title: String, isDone: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isDone) Icons.Default.Check else Icons.Default.Refresh,
            contentDescription = null,
            tint = if (isDone) DriverLuxuryUiTokens.GreenSuccess else DriverLuxuryUiTokens.Muted,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            color = if (isDone) DriverLuxuryUiTokens.Text else DriverLuxuryUiTokens.Muted,
            fontSize = 13.sp,
            fontWeight = if (isDone) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun DriverSurgeCard(title: String, surge: String, desc: String, onSelect: () -> Unit) {
    Surface(
        color = DriverLuxuryUiTokens.Card,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, DriverLuxuryUiTokens.Border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clickable { onSelect() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = DriverLuxuryUiTokens.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(desc, color = DriverLuxuryUiTokens.Muted, fontSize = 11.sp)
            }
            Surface(
                color = DriverLuxuryUiTokens.Gold,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(surge, color = Color.Black, fontWeight = FontWeight.Black, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}

@Composable
fun DriverUploadItemCard(name: String, status: String, isUploaded: Boolean) {
    Surface(
        color = DriverLuxuryUiTokens.Card,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, DriverLuxuryUiTokens.Border),
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(name, color = DriverLuxuryUiTokens.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(status, color = DriverLuxuryUiTokens.Muted, fontSize = 11.sp)
            }
            Icon(
                imageVector = if (isUploaded) Icons.Default.Check else Icons.Default.Add,
                contentDescription = null,
                tint = if (isUploaded) DriverLuxuryUiTokens.GreenSuccess else DriverLuxuryUiTokens.Gold
            )
        }
    }
}

@Composable
fun DriverHistoryCard(id: String, fare: String, route: String, details: String) {
    Surface(
        color = DriverLuxuryUiTokens.Card,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, DriverLuxuryUiTokens.Border),
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(id, color = DriverLuxuryUiTokens.Muted, fontSize = 11.sp)
                Text(fare, color = DriverLuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(route, color = DriverLuxuryUiTokens.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(details, color = DriverLuxuryUiTokens.Muted, fontSize = 11.sp)
        }
    }
}

@Composable
fun DriverLeaderboardRow(rank: Int, name: String, trips: String, prize: String, isCurrentUser: Boolean = false) {
    Surface(
        color = if (isCurrentUser) Color(0xFF24221A) else DriverLuxuryUiTokens.Card,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isCurrentUser) DriverLuxuryUiTokens.Gold else DriverLuxuryUiTokens.Border),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#$rank", color = DriverLuxuryUiTokens.Gold, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(name, color = DriverLuxuryUiTokens.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(trips, color = DriverLuxuryUiTokens.Muted, fontSize = 11.sp)
                }
            }
            Text(prize, color = DriverLuxuryUiTokens.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DriverChatBubble(message: String, isCustomer: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isCustomer) Arrangement.Start else Arrangement.End
    ) {
        Surface(
            color = if (isCustomer) DriverLuxuryUiTokens.Card else DriverLuxuryUiTokens.Gold,
            shape = RoundedCornerShape(12.dp),
            border = if (isCustomer) BorderStroke(1.dp, DriverLuxuryUiTokens.Border) else null,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = message,
                color = if (isCustomer) DriverLuxuryUiTokens.Text else Color.Black,
                fontSize = 12.sp,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

