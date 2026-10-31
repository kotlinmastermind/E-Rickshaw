package com.luxury.chauffeur.customer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.messaging.FirebaseMessaging
import com.luxury.chauffeur.customer.core.designsystem.*
import com.luxury.chauffeur.customer.core.network.*
import com.luxury.chauffeur.customer.core.notification.CustomerFcmManager
import com.luxury.chauffeur.customer.core.security.TokenManager
import com.luxury.chauffeur.customer.feature.auth.CustomerAuthenticationDialog
import com.luxury.chauffeur.customer.feature.governance.AccountGovernanceScreen
import com.luxury.chauffeur.customer.feature.location.LocationScreen
import com.luxury.chauffeur.customer.feature.loyalty.*
import com.luxury.chauffeur.customer.feature.notifications.NotificationScreen
import com.luxury.chauffeur.customer.feature.permissions.PermissionManager
import com.luxury.chauffeur.customer.feature.permissions.PermissionOnboardingScreen
import com.luxury.chauffeur.customer.feature.auth.UnifiedAuthScreen
import com.luxury.chauffeur.customer.feature.tracking.LiveTrackingScreen
import com.luxury.chauffeur.customer.feature.payments.PaymentScreen
import com.luxury.chauffeur.customer.feature.payments.VipWalletConsoleDialog
import com.luxury.chauffeur.customer.feature.profile.ProfileEditScreen
import com.luxury.chauffeur.customer.feature.security.AppLockOverlay
import com.luxury.chauffeur.customer.feature.sharedride.SharedTripConsoleScreen
import com.luxury.chauffeur.customer.feature.splash.SplashScreen
import com.luxury.chauffeur.customer.feature.support.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class CustomerScreenRoute {
    SPLASH,
    PERMISSION_ONBOARDING,
    AUTH_OTP,
    HOME_LOCATION,
    PROFILE_EDIT,
    GOVERNANCE_SETTINGS,
    LIVE_TRACKING,
    PAYMENT_SETTLEMENT,
    NOTIFICATIONS,
    SHARED_TRIP
}

enum class CustomerTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    TRIPS("Trips", Icons.Default.List),
    WALLET("Wallet", Icons.Default.AccountBox),
    ME("Me", Icons.Default.Person)
}

@Composable
fun CustomerAppNavigation() {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager.getInstance(context) }
    val scope = rememberCoroutineScope()

    var currentScreen by remember {
        mutableStateOf(
            when {
                !PermissionManager.isPermissionOnboardingCompleted(context) -> CustomerScreenRoute.PERMISSION_ONBOARDING
                !tokenManager.isLoggedIn() -> CustomerScreenRoute.AUTH_OTP
                else -> CustomerScreenRoute.HOME_LOCATION
            }
        )
    }
    var currentTab by remember { mutableStateOf(CustomerTab.HOME) }
    var activeRideId by remember { mutableStateOf("") }
    var activeOtp by remember { mutableStateOf("849201") }
    var settlementAmount by remember { mutableIntStateOf(1600) }
    var activePooledTripId by remember { mutableStateOf("") }
    var activeSharedBookingId by remember { mutableStateOf("") }

    // App Lock state
    var isAppLocked by remember { mutableStateOf(tokenManager.isAppLockEnabled()) }

    // Sub-dialogs opened from Trips or Me tabs
    var showWalletConsoleDialog by remember { mutableStateOf(false) }
    var showLoyaltySanctuaryDialog by remember { mutableStateOf(false) }
    var showReferralSanctuaryDialog by remember { mutableStateOf(false) }
    var showSupportDeskDialog by remember { mutableStateOf(false) }
    var showSignOutConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        CustomerFcmManager.syncToken(context)
    }

    // Back handling conforming to NAV.r
    BackHandler(enabled = true) {
        when {
            showWalletConsoleDialog -> showWalletConsoleDialog = false
            showLoyaltySanctuaryDialog -> showLoyaltySanctuaryDialog = false
            showReferralSanctuaryDialog -> showReferralSanctuaryDialog = false
            showSupportDeskDialog -> showSupportDeskDialog = false
            showSignOutConfirmDialog -> showSignOutConfirmDialog = false
            currentScreen == CustomerScreenRoute.LIVE_TRACKING -> {
                // NAV.r: Active ride minimizes; back cannot leave active trip
                Toast.makeText(context, "Driver trip is currently active", Toast.LENGTH_SHORT).show()
            }
            currentScreen == CustomerScreenRoute.PROFILE_EDIT ||
            currentScreen == CustomerScreenRoute.GOVERNANCE_SETTINGS ||
            currentScreen == CustomerScreenRoute.NOTIFICATIONS -> {
                currentScreen = CustomerScreenRoute.HOME_LOCATION
            }
            currentTab != CustomerTab.HOME -> {
                currentTab = CustomerTab.HOME
            }
            else -> {
                (context as? Activity)?.finish()
            }
        }
    }

    Scaffold(
        containerColor = Color(0xFF0D0D0F),
        bottomBar = {
            // NAV.r: Bottom nav 4 tab: Home, Trips, Wallet, Me — sirf top-level par; booking/trip me hide.
            val isTopLevel = currentScreen == CustomerScreenRoute.HOME_LOCATION
            if (isTopLevel) {
                Surface(
                    color = Color(0xFF17171A),
                    border = BorderStroke(1.dp, Color(0xFF2C2C30)),
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(62.dp)
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CustomerTab.values().forEach { tab ->
                            val isSelected = currentTab == tab
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (currentTab == tab) {
                                            // Active tab dobara tap = root / reset sub-sheets
                                            showWalletConsoleDialog = false
                                            showLoyaltySanctuaryDialog = false
                                            showReferralSanctuaryDialog = false
                                            showSupportDeskDialog = false
                                        } else {
                                            currentTab = tab
                                        }
                                    }
                                    .padding(vertical = 6.dp, horizontal = 16.dp)
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label,
                                    tint = if (isSelected) Color(0xFFC9A24B) else Color(0xFF9A968C),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tab.label,
                                    style = TextStyle(
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color(0xFFC9A24B) else Color(0xFF9A968C)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
        ) {
            when (currentScreen) {
                CustomerScreenRoute.SPLASH -> {
                    SplashScreen(
                        onNavigateNext = { isLoggedIn ->
                            currentScreen = when {
                                !PermissionManager.isPermissionOnboardingCompleted(context) -> CustomerScreenRoute.PERMISSION_ONBOARDING
                                !isLoggedIn -> CustomerScreenRoute.AUTH_OTP
                                else -> CustomerScreenRoute.HOME_LOCATION
                            }
                        }
                    )
                }
                CustomerScreenRoute.PERMISSION_ONBOARDING -> {
                    PermissionOnboardingScreen(
                        onPermissionsCompleted = {
                            currentScreen = if (tokenManager.isLoggedIn()) {
                                CustomerScreenRoute.HOME_LOCATION
                            } else {
                                CustomerScreenRoute.AUTH_OTP
                            }
                        }
                    )
                }
                CustomerScreenRoute.AUTH_OTP -> {
                    UnifiedAuthScreen(
                        onAuthSuccess = {
                            CustomerFcmManager.syncToken(context)
                            isAppLocked = tokenManager.isAppLockEnabled()
                            currentScreen = CustomerScreenRoute.HOME_LOCATION
                            currentTab = CustomerTab.HOME
                        }
                    )
                }
                CustomerScreenRoute.HOME_LOCATION -> {
                    when (currentTab) {
                        CustomerTab.HOME -> {
                            LocationScreen(
                                onRideRequested = { rideId, otp ->
                                    activeRideId = rideId
                                    activeOtp = otp
                                    currentScreen = CustomerScreenRoute.LIVE_TRACKING
                                },
                                onNavigateProfile = { currentScreen = CustomerScreenRoute.PROFILE_EDIT },
                                onNavigateNotifications = { currentScreen = CustomerScreenRoute.NOTIFICATIONS },
                                onNavigateGovernance = { currentScreen = CustomerScreenRoute.GOVERNANCE_SETTINGS },
                                onSharedRideBooked = { rideId, otp, poolId, bookingId ->
                                    activeRideId = rideId
                                    activeOtp = otp
                                    activePooledTripId = poolId
                                    activeSharedBookingId = bookingId
                                    currentScreen = CustomerScreenRoute.SHARED_TRIP
                                }
                            )
                        }
                        CustomerTab.TRIPS -> {
                            CustomerTripsTabContent(
                                onRepeatTrip = { _, _ ->
                                    currentTab = CustomerTab.HOME
                                }
                            )
                        }
                        CustomerTab.WALLET -> {
                            CustomerWalletTabContent(
                                onOpenWalletConsole = { showWalletConsoleDialog = true },
                                onOpenLoyalty = { showLoyaltySanctuaryDialog = true },
                                onOpenReferrals = { showReferralSanctuaryDialog = true }
                            )
                        }
                        CustomerTab.ME -> {
                            CustomerMeTabContent(
                                onNavigateProfile = { currentScreen = CustomerScreenRoute.PROFILE_EDIT },
                                onNavigateGovernance = { currentScreen = CustomerScreenRoute.GOVERNANCE_SETTINGS },
                                onNavigateNotifications = { currentScreen = CustomerScreenRoute.NOTIFICATIONS },
                                onOpenSupport = { showSupportDeskDialog = true },
                                onSignOutRequested = { showSignOutConfirmDialog = true }
                            )
                        }
                    }
                }
                CustomerScreenRoute.PROFILE_EDIT -> {
                    ProfileEditScreen(
                        onNavigateBack = { currentScreen = CustomerScreenRoute.HOME_LOCATION },
                        onNavigateGovernance = { currentScreen = CustomerScreenRoute.GOVERNANCE_SETTINGS }
                    )
                }
                CustomerScreenRoute.GOVERNANCE_SETTINGS -> {
                    AccountGovernanceScreen(
                        onNavigateBack = { currentScreen = CustomerScreenRoute.HOME_LOCATION }
                    )
                }
                CustomerScreenRoute.LIVE_TRACKING -> {
                    LiveTrackingScreen(
                        rideId = activeRideId,
                        startOtp = activeOtp,
                        onRideCancelled = {
                            activeRideId = ""
                            currentScreen = CustomerScreenRoute.HOME_LOCATION
                            currentTab = CustomerTab.HOME
                        },
                        onProceedPayment = { _: String, amount: Int ->
                            settlementAmount = amount
                            currentScreen = CustomerScreenRoute.PAYMENT_SETTLEMENT
                        }
                    )
                }
                CustomerScreenRoute.PAYMENT_SETTLEMENT -> {
                    PaymentScreen(
                        rideId = activeRideId,
                        amountRupees = settlementAmount,
                        onPaymentAndReviewCompleted = {
                            activeRideId = ""
                            currentScreen = CustomerScreenRoute.HOME_LOCATION
                            currentTab = CustomerTab.HOME
                        }
                    )
                }
                CustomerScreenRoute.NOTIFICATIONS -> {
                    NotificationScreen(
                        onNavigateBack = { currentScreen = CustomerScreenRoute.HOME_LOCATION },
                        onOpenAction = { path ->
                            val rideId = path.removePrefix("/rides/").substringBefore('/')
                            if (rideId.isNotBlank() && rideId.matches(Regex("[A-Za-z0-9_-]{1,80}"))) {
                                activeRideId = rideId
                                currentScreen = CustomerScreenRoute.LIVE_TRACKING
                            }
                        }
                    )
                }
                CustomerScreenRoute.SHARED_TRIP -> {
                    SharedTripConsoleScreen(
                        tripId = activePooledTripId,
                        rideId = activeRideId,
                        bookingId = activeSharedBookingId,
                        onBack = { currentScreen = CustomerScreenRoute.HOME_LOCATION },
                        onOpenLiveTracking = { currentScreen = CustomerScreenRoute.LIVE_TRACKING }
                    )
                }
            }

            // Modal dialogs invoked from tabs
            if (showWalletConsoleDialog) {
                VipWalletConsoleDialog(
                    onDismiss = { showWalletConsoleDialog = false }
                )
            }
            if (showLoyaltySanctuaryDialog) {
                RoyalLoyaltyHubDialog(
                    onDismiss = { showLoyaltySanctuaryDialog = false }
                )
            }
            if (showReferralSanctuaryDialog) {
                ReferralSanctuaryDialog(
                    onDismiss = { showReferralSanctuaryDialog = false }
                )
            }
            if (showSupportDeskDialog) {
                HelpCenterDialog(
                    initialTag = null,
                    onDismiss = { showSupportDeskDialog = false },
                    onOpenTickets = {},
                    onOpenSelfService = {}
                )
            }
            if (showSignOutConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showSignOutConfirmDialog = false },
                    containerColor = Color(0xFF17171A),
                    title = {
                        Text(
                            text = "Sign Out",
                            color = Color(0xFFC9A24B),
                            fontFamily = FontFamily.Serif,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = "Are you sure you wish to sign out of your sovereign luxury chauffeur profile?",
                            color = Color(0xFFF2EFE8),
                            fontSize = 14.sp
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                tokenManager.clear()
                                showSignOutConfirmDialog = false
                                currentScreen = CustomerScreenRoute.AUTH_OTP
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFC9A24B),
                                contentColor = Color(0xFF111111)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Sign Out", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSignOutConfirmDialog = false }) {
                            Text("Cancel", color = Color(0xFF9A968C))
                        }
                    }
                )
            }

            // App Lock Overlay
            if (isAppLocked && currentScreen != CustomerScreenRoute.SPLASH && currentScreen != CustomerScreenRoute.AUTH_OTP) {
                AppLockOverlay(
                    onUnlocked = { isAppLocked = false }
                )
            }
        }
    }
}

// =========================================================================
// TAB 2: TRIPS (Screens 38, 39, 59, 64, 67 per NAV.r)
// =========================================================================
@Composable
private fun CustomerTripsTabContent(
    onRepeatTrip: (pickup: String, drop: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var statusFilter by remember { mutableStateOf<String?>("COMPLETED") }
    var rides by remember { mutableStateOf<List<FilteredRideDto>>(emptyList()) }
    var statement by remember { mutableStateOf<MonthlyStatementResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedRideDetail by remember { mutableStateOf<FilteredRideDto?>(null) }

    fun loadData() {
        scope.launch {
            isLoading = true
            try {
                val res = ApiClient.getService().getFilteredRideHistory(
                    status = if (statusFilter == "ALL") null else statusFilter,
                    limit = 20
                )
                if (res.isSuccessful) rides = res.body() ?: emptyList()

                val sRes = ApiClient.getService().getMonthlyRideStatement(2026, 10)
                if (sRes.isSuccessful) statement = sRes.body()
            } catch (_: Exception) {
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(statusFilter) { loadData() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0F))
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Journals & Driver Trips",
            fontFamily = FontFamily.Serif,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFC9A24B)
        )
        Text(
            text = "Audit records, statements & concierge assistance",
            fontSize = 12.sp,
            color = Color(0xFF9A968C)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Filters row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("COMPLETED" to "Completed", "CANCELLED" to "Cancelled", "ALL" to "All Rides").forEach { (code, label) ->
                val isSelected = statusFilter == code
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) Color(0xFFC9A24B) else Color(0xFF17171A),
                    border = BorderStroke(1.dp, if (isSelected) Color(0xFFC9A24B) else Color(0xFF2C2C30)),
                    modifier = Modifier.clickable { statusFilter = code }
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color(0xFF111111) else Color(0xFFF2EFE8),
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Monthly statement card
        if (statement != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF17171A),
                border = BorderStroke(1.dp, Color(0xFFC9A24B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "October 2026 Statement",
                            fontFamily = FontFamily.Serif,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC9A24B)
                        )
                        Text(
                            text = "${statement!!.totalRides} trips • ₹${statement!!.totalSpendRupees.toInt()} spent",
                            fontSize = 12.sp,
                            color = Color(0xFFF2EFE8)
                        )
                    }
                    Button(
                        onClick = {
                            val cleanBase = ApiClient.baseUrl.removeSuffix("/")
                            val url = if (statement!!.csvDownloadUrl.startsWith("http")) statement!!.csvDownloadUrl else cleanBase + statement!!.csvDownloadUrl
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFC9A24B),
                            contentColor = Color(0xFF111111)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Download CSV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFC9A24B))
            }
        } else if (rides.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No rides matching filter in current billing period.",
                    color = Color(0xFF9A968C),
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(rides) { ride ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF17171A),
                        border = BorderStroke(1.dp, Color(0xFF2C2C30)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedRideDetail = ride }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Executive Chauffeur Ride",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFFC9A24B)
                                )
                                Text(
                                    text = "₹${ride.fareRupees.toInt()}",
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color(0xFFF2EFE8)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "From: ${ride.pickupAddress}",
                                fontSize = 12.sp,
                                color = Color(0xFFF2EFE8),
                                maxLines = 1
                            )
                            Text(
                                text = "To: ${ride.destinationAddress}",
                                fontSize = 12.sp,
                                color = Color(0xFF9A968C),
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = ride.createdAt.take(16).replace("T", " "),
                                    fontSize = 11.sp,
                                    color = Color(0xFF9A968C)
                                )
                                Text(
                                    text = ride.status,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (ride.status == "COMPLETED") Color(0xFF10B981) else Color(0xFFEF4444)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Trip detail bottom sheet / modal (Screen 39)
    if (selectedRideDetail != null) {
        val ride = selectedRideDetail!!
        AlertDialog(
            onDismissRequest = { selectedRideDetail = null },
            containerColor = Color(0xFF17171A),
            title = {
                Text(
                    text = "Trip Details",
                    fontFamily = FontFamily.Serif,
                    color = Color(0xFFC9A24B),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Ride ID: ${ride.rideId}", fontSize = 11.sp, color = Color(0xFF9A968C))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Pickup: ${ride.pickupAddress}", fontSize = 13.sp, color = Color(0xFFF2EFE8))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Drop: ${ride.destinationAddress}", fontSize = 13.sp, color = Color(0xFFF2EFE8))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Total Fare: ₹${ride.fareRupees.toInt()}", fontFamily = FontFamily.Serif, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC9A24B))
                    Text("Status: ${ride.status}", fontSize = 12.sp, color = Color(0xFF10B981))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedRideDetail = null
                        onRepeatTrip(ride.pickupAddress, ride.destinationAddress)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFC9A24B),
                        contentColor = Color(0xFF111111)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Rebook Trip", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedRideDetail = null }) {
                    Text("Close", color = Color(0xFF9A968C))
                }
            }
        )
    }
}

// =========================================================================
// TAB 3: WALLET (Screens 43, 44, 45, 46, 66 per NAV.r)
// =========================================================================
@Composable
private fun CustomerWalletTabContent(
    onOpenWalletConsole: () -> Unit,
    onOpenLoyalty: () -> Unit,
    onOpenReferrals: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var balanceRupees by remember { mutableDoubleStateOf(0.0) }
    var isLoading by remember { mutableStateOf(true) }

    fun fetchWallet() {
        scope.launch {
            isLoading = true
            try {
                val res = ApiClient.getService().getWallet()
                if (res.isSuccessful && res.body() != null) {
                    balanceRupees = res.body()!!.balanceCents / 100.0
                }
            } catch (_: Exception) {
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { fetchWallet() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0F))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Executive Wallet & Credits",
            fontFamily = FontFamily.Serif,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFC9A24B)
        )
        Text(
            text = "Instant 1-tap bookings, automatic refunds & reward points",
            fontSize = 12.sp,
            color = Color(0xFF9A968C)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Main Balance Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF17171A),
            border = BorderStroke(1.5.dp, Color(0xFFC9A24B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("TOTAL AVAILABLE BALANCE", fontSize = 11.sp, color = Color(0xFF9A968C))
                Spacer(modifier = Modifier.height(6.dp))
                if (isLoading) {
                    CircularProgressIndicator(color = Color(0xFFC9A24B), modifier = Modifier.size(24.dp))
                } else {
                    Text(
                        text = "₹${"%.2f".format(balanceRupees)}",
                        fontFamily = FontFamily.Serif,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC9A24B)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onOpenWalletConsole,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFC9A24B),
                            contentColor = Color(0xFF111111)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Add Money", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onOpenWalletConsole,
                        border = BorderStroke(1.dp, Color(0xFFC9A24B)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Auto Top-Up", color = Color(0xFFC9A24B), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Features rows (Cards, Rewards, Refer)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF17171A),
            border = BorderStroke(1.dp, Color(0xFF2C2C30)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenLoyalty() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFC9A24B))
                    Column {
                        Text("Royal Loyalty & Points", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFF2EFE8))
                        Text("Redeem points for complimentary rides", fontSize = 11.sp, color = Color(0xFF9A968C))
                    }
                }
                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color(0xFFC9A24B), modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF17171A),
            border = BorderStroke(1.dp, Color(0xFF2C2C30)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenReferrals() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFFC9A24B))
                    Column {
                        Text("Referral Sanctuary", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFF2EFE8))
                        Text("Gift prestige, earn sovereign credits", fontSize = 11.sp, color = Color(0xFF9A968C))
                    }
                }
                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color(0xFFC9A24B), modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF17171A),
            border = BorderStroke(1.dp, Color(0xFF2C2C30)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenWalletConsole() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFC9A24B))
                    Column {
                        Text("Saved Payment Cards & UPI", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFF2EFE8))
                        Text("RBI Tokenised cards & Instant UPI", fontSize = 11.sp, color = Color(0xFF9A968C))
                    }
                }
                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color(0xFFC9A24B), modifier = Modifier.size(18.dp))
            }
        }
    }
}

// =========================================================================
// TAB 4: ME (Screens 06, 07, 47, 48, 49, 50, 51, 52, 53, 54, 61, 62, 65, 68)
// =========================================================================
@Composable
private fun CustomerMeTabContent(
    onNavigateProfile: () -> Unit,
    onNavigateGovernance: () -> Unit,
    onNavigateNotifications: () -> Unit,
    onOpenSupport: () -> Unit,
    onSignOutRequested: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var profileData by remember { mutableStateOf<CustomerProfileDto?>(null) }
    var membershipTier by remember { mutableStateOf("ROYAL VIP") }

    LaunchedEffect(Unit) {
        try {
            val res = ApiClient.getService().getProfile()
            if (res.isSuccessful && res.body() != null) {
                profileData = res.body()
            }
            val mRes = ApiClient.getService().getMembershipProfile()
            if (mRes.isSuccessful && mRes.body() != null) {
                membershipTier = mRes.body()!!.tier
            }
        } catch (_: Exception) {}
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0F))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Executive Account",
            fontFamily = FontFamily.Serif,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFC9A24B)
        )
        Text(
            text = "Personal details, security governance & concierge hotline",
            fontSize = 12.sp,
            color = Color(0xFF9A968C)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Profile card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF17171A),
            border = BorderStroke(1.dp, Color(0xFF2C2C30)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFC9A24B)),
                    contentAlignment = Alignment.Center
                ) {
                    val initials = (profileData?.fullName ?: "VIP").split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")
                    Text(
                        text = initials.ifBlank { "V" },
                        color = Color(0xFF111111),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profileData?.fullName ?: "Guest",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF2EFE8)
                    )
                    Text(
                        text = profileData?.phoneNumber ?: "+91 ••••• •••••",
                        fontSize = 12.sp,
                        color = Color(0xFF9A968C)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFC9A24B).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color(0xFFC9A24B))
                    ) {
                        Text(
                            text = membershipTier.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC9A24B),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = Color(0xFFC9A24B),
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onNavigateProfile() }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Navigation Menu rows
        listOf(
            "Account Governance, Passkey & MFA" to onNavigateGovernance,
            "Executive Notifications & Alerts" to onNavigateNotifications,
            "Personal Assistant & 24x7 Help Desk" to onOpenSupport,
            "Driver Preferences & Notes" to onNavigateProfile
        ).forEach { (label, onClick) ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF17171A),
                border = BorderStroke(1.dp, Color(0xFF2C2C30)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick() }
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = label, fontSize = 13.sp, color = Color(0xFFF2EFE8), fontWeight = FontWeight.Medium)
                    Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color(0xFFC9A24B), modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Sign Out Button
        Button(
            onClick = onSignOutRequested,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFEF4444).copy(alpha = 0.15f),
                contentColor = Color(0xFFEF4444)
            ),
            border = BorderStroke(1.dp, Color(0xFFEF4444)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("" +
                    "Sign Out", fontWeight = FontWeight.Bold)
        }
    }
}
