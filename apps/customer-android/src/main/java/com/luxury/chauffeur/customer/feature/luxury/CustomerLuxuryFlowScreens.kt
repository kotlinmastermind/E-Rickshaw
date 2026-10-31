package com.luxury.chauffeur.customer.feature.luxury

import android.content.Context
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Exact Colors from uploaded HTML spec:
// :root{color-scheme:dark;--bg:#0b0b0d;--c:#16161a;--g:#d4af37;--t:#f2ead3;--m:#8d8672}
object LuxuryUiTokens {
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

// Flow steps for Customer App per uploaded HTML
enum class CustFlowStep {
    HOME_MAP,            // 08
    SEARCH_DESTINATION,  // 09
    PIN_DROP,            // 10
    MULTI_STOP,          // 11
    CHOOSE_RIDE,         // 12
    FARE_BREAKUP,        // 13
    RIDE_PREFERENCES,    // 14
    PAYMENT_METHOD,      // 15
    CONFIRM_BOOKING,     // 16
    OUT_OF_ZONE,         // 17
    SEARCHING_RADAR,     // 18
    DRIVER_FOUND,        // 19
    DRIVER_ARRIVING,     // 20
    RIDE_CHAT,           // 21
    MASKED_CALL,         // 22
    RIDE_IN_PROGRESS,    // 23
    SHARE_TRIP,          // 24
    SOS_PANIC,           // 25
    FARE_SPLIT,          // 26
    CANCEL_RIDE,         // 27
    TRIP_SUMMARY,        // 28
    PAYMENT_SETTLE,      // 29
    RATE_DRIVER          // 30
}

@Composable
fun CustomerLuxuryFlowScreens(
    activeTab: String,
    onTabSelected: (String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Active Flow State
    var flowStep by remember { mutableStateOf(CustFlowStep.HOME_MAP) }
    var selectedCategory by remember { mutableStateOf("Standard E-Rickshaw") }
    var estimatedFare by remember { mutableIntStateOf(128) }
    var destinationQuery by remember { mutableStateOf("Connaught Place Outer Circle, New Delhi") }
    var pickupAddress by remember { mutableStateOf("Model Town Metro Station Gate 2") }
    var startOtp by remember { mutableStateOf("4821") }
    var driverName by remember { mutableStateOf("Ramesh Kumar") }
    var driverPlate by remember { mutableStateOf("DL-1ER-4821") }
    var driverRating by remember { mutableStateOf("4.9 ★") }
    var walletBalance by remember { mutableIntStateOf(450) }
    var promoCode by remember { mutableStateOf("") }
    var promoDiscount by remember { mutableIntStateOf(0) }

    // Dialog & Overlay States
    var showSosDialog by remember { mutableStateOf(false) }
    var showEarlyDropDialog by remember { mutableStateOf(false) }
    var showCancelConfirmDialog by remember { mutableStateOf(false) }
    var showOutofZoneDialog by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showTipSheet by remember { mutableStateOf(false) }

    // Searching Radar countdown
    var radarSeconds by remember { mutableIntStateOf(20) }

    // Is Active Ride Mode? (Screens 18 to 25)
    // Rule: During active ride, bottom nav is hidden; floating SOS button & persistent driver card rendered.
    val isActiveRide = flowStep == CustFlowStep.SEARCHING_RADAR ||
            flowStep == CustFlowStep.DRIVER_FOUND ||
            flowStep == CustFlowStep.DRIVER_ARRIVING ||
            flowStep == CustFlowStep.RIDE_CHAT ||
            flowStep == CustFlowStep.MASKED_CALL ||
            flowStep == CustFlowStep.RIDE_IN_PROGRESS ||
            flowStep == CustFlowStep.SHARE_TRIP ||
            flowStep == CustFlowStep.SOS_PANIC

    // Radar pulse timer simulation
    LaunchedEffect(flowStep) {
        if (flowStep == CustFlowStep.SEARCHING_RADAR) {
            radarSeconds = 20
            while (radarSeconds > 0) {
                delay(1000)
                radarSeconds--
                if (radarSeconds == 15) {
                    // Driver found after 5 seconds!
                    flowStep = CustFlowStep.DRIVER_FOUND
                    break
                }
            }
        }
    }

    // Back button protection during active ride
    BackHandler(enabled = true) {
        when {
            showSosDialog -> showSosDialog = false
            showEarlyDropDialog -> showEarlyDropDialog = false
            showCancelConfirmDialog -> showCancelConfirmDialog = false
            showShareSheet -> showShareSheet = false
            showTipSheet -> showTipSheet = false
            isActiveRide -> {
                // Rule: Back during ride shows a confirm dialog and NEVER cancels the ride.
                showEarlyDropDialog = true
            }
            flowStep != CustFlowStep.HOME_MAP -> {
                flowStep = when (flowStep) {
                    CustFlowStep.SEARCH_DESTINATION -> CustFlowStep.HOME_MAP
                    CustFlowStep.PIN_DROP -> CustFlowStep.SEARCH_DESTINATION
                    CustFlowStep.MULTI_STOP -> CustFlowStep.SEARCH_DESTINATION
                    CustFlowStep.CHOOSE_RIDE -> CustFlowStep.SEARCH_DESTINATION
                    CustFlowStep.FARE_BREAKUP -> CustFlowStep.CHOOSE_RIDE
                    CustFlowStep.RIDE_PREFERENCES -> CustFlowStep.CHOOSE_RIDE
                    CustFlowStep.PAYMENT_METHOD -> CustFlowStep.FARE_BREAKUP
                    CustFlowStep.CONFIRM_BOOKING -> CustFlowStep.CHOOSE_RIDE
                    CustFlowStep.OUT_OF_ZONE -> CustFlowStep.CHOOSE_RIDE
                    CustFlowStep.CANCEL_RIDE -> CustFlowStep.RIDE_IN_PROGRESS
                    CustFlowStep.TRIP_SUMMARY -> CustFlowStep.HOME_MAP
                    CustFlowStep.PAYMENT_SETTLE -> CustFlowStep.TRIP_SUMMARY
                    CustFlowStep.RATE_DRIVER -> CustFlowStep.HOME_MAP
                    else -> CustFlowStep.HOME_MAP
                }
            }
        }
    }

    Scaffold(
        containerColor = LuxuryUiTokens.Bg,
        bottomBar = {
            // Rule: During active ride bottom nav is hidden!
            if (!isActiveRide) {
                Surface(
                    color = LuxuryUiTokens.Card,
                    border = BorderStroke(1.dp, LuxuryUiTokens.Border),
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("Home", "Rides", "Wallet", "Account").forEach { tab ->
                            val isSelected = activeTab == tab
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onTabSelected(tab) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = when (tab) {
                                        "Home" -> Icons.Default.Home
                                        "Rides" -> Icons.Default.List
                                        "Wallet" -> Icons.Default.AccountBox
                                        else -> Icons.Default.Person
                                    },
                                    contentDescription = tab,
                                    tint = if (isSelected) LuxuryUiTokens.Gold else LuxuryUiTokens.Muted,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tab,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) LuxuryUiTokens.Gold else LuxuryUiTokens.Muted
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .background(LuxuryUiTokens.Bg)
        ) {
            when (activeTab) {
                "Home" -> {
                    // Linear Rapido-style Booking Flow (Screens 08 to 30)
                    when (flowStep) {
                        CustFlowStep.HOME_MAP -> {
                            Screen08HomeMap(
                                pickupAddress = pickupAddress,
                                onWhereToClick = { flowStep = CustFlowStep.SEARCH_DESTINATION },
                                onChooseCategory = { cat ->
                                    selectedCategory = cat
                                    flowStep = CustFlowStep.CHOOSE_RIDE
                                },
                                onQuickDestination = { dest ->
                                    destinationQuery = dest
                                    flowStep = CustFlowStep.CHOOSE_RIDE
                                }
                            )
                        }
                        CustFlowStep.SEARCH_DESTINATION -> {
                            Screen09SearchDestination(
                                initialQuery = destinationQuery,
                                onSelectDestination = { dest ->
                                    destinationQuery = dest
                                    flowStep = CustFlowStep.CHOOSE_RIDE
                                },
                                onPinDropClick = { flowStep = CustFlowStep.PIN_DROP },
                                onMultiStopClick = { flowStep = CustFlowStep.MULTI_STOP },
                                onBack = { flowStep = CustFlowStep.HOME_MAP }
                            )
                        }
                        CustFlowStep.PIN_DROP -> {
                            Screen10PinDrop(
                                onConfirmPin = { addr ->
                                    pickupAddress = addr
                                    flowStep = CustFlowStep.CHOOSE_RIDE
                                },
                                onBack = { flowStep = CustFlowStep.SEARCH_DESTINATION }
                            )
                        }
                        CustFlowStep.MULTI_STOP -> {
                            Screen11MultiStop(
                                pickup = pickupAddress,
                                destination = destinationQuery,
                                onConfirmRoute = { flowStep = CustFlowStep.CHOOSE_RIDE },
                                onBack = { flowStep = CustFlowStep.SEARCH_DESTINATION }
                            )
                        }
                        CustFlowStep.CHOOSE_RIDE -> {
                            Screen12ChooseRide(
                                selectedCat = selectedCategory,
                                onSelectCat = { selectedCategory = it },
                                onFareBreakup = { flowStep = CustFlowStep.FARE_BREAKUP },
                                onPreferences = { flowStep = CustFlowStep.RIDE_PREFERENCES },
                                onBookNow = { flowStep = CustFlowStep.CONFIRM_BOOKING },
                                onBack = { flowStep = CustFlowStep.SEARCH_DESTINATION }
                            )
                        }
                        CustFlowStep.FARE_BREAKUP -> {
                            Screen13FareBreakup(
                                baseFare = 20,
                                kmFare = 28,
                                techFee = 5,
                                promoCode = promoCode,
                                promoDiscount = promoDiscount,
                                onApplyPromo = { code ->
                                    promoCode = code
                                    promoDiscount = 20
                                    Toast.makeText(context, "Promo $code Applied! Saved ₹20", Toast.LENGTH_SHORT).show()
                                },
                                onContinuePayment = { flowStep = CustFlowStep.PAYMENT_METHOD },
                                onBack = { flowStep = CustFlowStep.CHOOSE_RIDE }
                            )
                        }
                        CustFlowStep.RIDE_PREFERENCES -> {
                            Screen14RidePreferences(
                                onSavePreferences = { flowStep = CustFlowStep.CHOOSE_RIDE },
                                onBack = { flowStep = CustFlowStep.CHOOSE_RIDE }
                            )
                        }
                        CustFlowStep.PAYMENT_METHOD -> {
                            Screen15PaymentMethod(
                                walletBalance = walletBalance,
                                onSelectPayment = { flowStep = CustFlowStep.CONFIRM_BOOKING },
                                onBack = { flowStep = CustFlowStep.FARE_BREAKUP }
                            )
                        }
                        CustFlowStep.CONFIRM_BOOKING -> {
                            Screen16ConfirmBooking(
                                pickup = pickupAddress,
                                destination = destinationQuery,
                                category = selectedCategory,
                                totalFare = estimatedFare - promoDiscount,
                                onConfirmBooking = {
                                    // Start radar pulse search
                                    flowStep = CustFlowStep.SEARCHING_RADAR
                                },
                                onTriggerOutofZone = { showOutofZoneDialog = true },
                                onBack = { flowStep = CustFlowStep.CHOOSE_RIDE }
                            )
                        }
                        CustFlowStep.OUT_OF_ZONE -> {
                            Screen17OutOfZone(
                                onChangeDestination = { flowStep = CustFlowStep.SEARCH_DESTINATION }
                            )
                        }
                        CustFlowStep.SEARCHING_RADAR -> {
                            Screen18SearchingRadar(
                                secondsLeft = radarSeconds,
                                onCancel = {
                                    flowStep = CustFlowStep.CHOOSE_RIDE
                                    Toast.makeText(context, "Search cancelled", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        CustFlowStep.DRIVER_FOUND -> {
                            Screen19DriverFound(
                                driverName = driverName,
                                plate = driverPlate,
                                rating = driverRating,
                                startOtp = startOtp,
                                etaMinutes = 2,
                                onProceedToArriving = { flowStep = CustFlowStep.DRIVER_ARRIVING }
                            )
                        }
                        CustFlowStep.DRIVER_ARRIVING -> {
                            Screen20DriverArriving(
                                driverName = driverName,
                                plate = driverPlate,
                                startOtp = startOtp,
                                etaMinutes = 2,
                                onCall = { flowStep = CustFlowStep.MASKED_CALL },
                                onChat = { flowStep = CustFlowStep.RIDE_CHAT },
                                onBoarded = { flowStep = CustFlowStep.RIDE_IN_PROGRESS },
                                onCancel = { flowStep = CustFlowStep.CANCEL_RIDE }
                            )
                        }
                        CustFlowStep.RIDE_CHAT -> {
                            Screen21RideChat(
                                driverName = driverName,
                                onBack = { flowStep = CustFlowStep.DRIVER_ARRIVING }
                            )
                        }
                        CustFlowStep.MASKED_CALL -> {
                            Screen22MaskedCall(
                                driverName = driverName,
                                onEndCall = { flowStep = CustFlowStep.DRIVER_ARRIVING }
                            )
                        }
                        CustFlowStep.RIDE_IN_PROGRESS -> {
                            Screen23RideInProgress(
                                driverName = driverName,
                                destination = destinationQuery,
                                onShare = { showShareSheet = true },
                                onSos = { showSosDialog = true },
                                onCompleteTrip = { flowStep = CustFlowStep.TRIP_SUMMARY }
                            )
                        }
                        CustFlowStep.SHARE_TRIP -> {
                            Screen24ShareTrip(
                                onDismiss = { flowStep = CustFlowStep.RIDE_IN_PROGRESS }
                            )
                        }
                        CustFlowStep.SOS_PANIC -> {
                            Screen25SosPanic(
                                onDismiss = { flowStep = CustFlowStep.RIDE_IN_PROGRESS }
                            )
                        }
                        CustFlowStep.FARE_SPLIT -> {
                            Screen26FareSplit(
                                totalFare = estimatedFare,
                                onDone = { flowStep = CustFlowStep.RIDE_IN_PROGRESS }
                            )
                        }
                        CustFlowStep.CANCEL_RIDE -> {
                            Screen27CancelRide(
                                onKeepRide = { flowStep = CustFlowStep.DRIVER_ARRIVING },
                                onConfirmCancel = {
                                    flowStep = CustFlowStep.HOME_MAP
                                    Toast.makeText(context, "Ride cancelled. Fee ₹20 charged to wallet.", Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                        CustFlowStep.TRIP_SUMMARY -> {
                            Screen28TripSummary(
                                totalFare = estimatedFare,
                                distanceKm = 3.5,
                                durationMins = 12,
                                onPay = { flowStep = CustFlowStep.PAYMENT_SETTLE }
                            )
                        }
                        CustFlowStep.PAYMENT_SETTLE -> {
                            Screen29PaymentSettle(
                                amount = estimatedFare,
                                onPaymentDone = {
                                    showTipSheet = true
                                    flowStep = CustFlowStep.RATE_DRIVER
                                }
                            )
                        }
                        CustFlowStep.RATE_DRIVER -> {
                            Screen30RateDriver(
                                driverName = driverName,
                                onSubmitted = {
                                    flowStep = CustFlowStep.HOME_MAP
                                    Toast.makeText(context, "Thank you for riding with E-Rickshaw Luxury!", Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    }
                }
                "Rides" -> {
                    // Screens 31 to 34 (Rides History, Detail, Dispute, Refund)
                    RidesTabContent(
                        onSelectRideDetail = { /* open detail */ },
                        onRepeatRide = {
                            onTabSelected("Home")
                            flowStep = CustFlowStep.CHOOSE_RIDE
                        }
                    )
                }
                "Wallet" -> {
                    // Screens 35 to 40 (Wallet Home, Add money, Txns, Cards, Promos, Refer)
                    WalletTabContent(
                        balance = walletBalance,
                        onAddMoney = { added ->
                            walletBalance += added
                            Toast.makeText(context, "Added ₹$added to Wallet!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                "Account" -> {
                    // Screens 43 to 50 (Profile, Loyalty, Emergency, Incidents, Support, Logout)
                    AccountTabContent(
                        onSignOut = onSignOut
                    )
                }
            }

            // POPUPS & DIALOGS (Obeying strict popup priority queue)

            // Priority 2: Emergency SOS Dialog
            if (showSosDialog) {
                LuxuryDialog(
                    title = "TRIGGER EMERGENCY SOS?",
                    message = "This will instantly dispatch your live GPS location to local Police (112), our 24/7 Security Operations Command, and your trusted emergency contacts.",
                    confirmText = "CONFIRM SOS DISPATCH",
                    confirmColor = LuxuryUiTokens.RedAlert,
                    onConfirm = {
                        showSosDialog = false
                        Toast.makeText(context, "🚨 SOS Alert Dispatched to Police & SOC Command!", Toast.LENGTH_LONG).show()
                    },
                    onDismiss = { showSosDialog = false }
                )
            }

            // Priority 5: Back during ride early drop confirm dialog
            if (showEarlyDropDialog) {
                LuxuryDialog(
                    title = "End ride here?",
                    message = "Confirm early drop. The ongoing ride remains active on our dispatch servers.",
                    confirmText = "Exit to Home",
                    dismissText = "Stay in Ride",
                    onConfirm = {
                        showEarlyDropDialog = false
                        flowStep = CustFlowStep.HOME_MAP
                    },
                    onDismiss = { showEarlyDropDialog = false }
                )
            }

            // Out-of-zone alert
            if (showOutofZoneDialog) {
                LuxuryDialog(
                    title = "Out of Service Zone",
                    message = "Drop location falls on a highway/flyover restricted for E-Rickshaws. Please adjust your drop pin to a nearby local street.",
                    confirmText = "Change Drop Pin",
                    onConfirm = {
                        showOutofZoneDialog = false
                        flowStep = CustFlowStep.SEARCH_DESTINATION
                    },
                    onDismiss = { showOutofZoneDialog = false }
                )
            }
        }
    }
}

// -------------------------------------------------------------
// SCREEN 08: HOME MAP (Where to?, Home/Work, Nearby Rickshaws)
// -------------------------------------------------------------
@Composable
fun Screen08HomeMap(
    pickupAddress: String,
    onWhereToClick: () -> Unit,
    onChooseCategory: (String) -> Unit,
    onQuickDestination: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Map Canvas Area (Luxury repeating grid simulation with pins)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF111116))
        ) {
            // Simulated Map Grid
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = 32.dp.toPx()
                for (x in 0..size.width.toInt() step step.toInt()) {
                    drawLine(Color(0xFF1E1E26), androidx.compose.ui.geometry.Offset(x.toFloat(), 0f), androidx.compose.ui.geometry.Offset(x.toFloat(), size.height), 1f)
                }
                for (y in 0..size.height.toInt() step step.toInt()) {
                    drawLine(Color(0xFF1E1E26), androidx.compose.ui.geometry.Offset(0f, y.toFloat()), androidx.compose.ui.geometry.Offset(size.width, y.toFloat()), 1f)
                }
            }

            // Center Gold Pin
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.Center)
            ) {
                Surface(
                    color = LuxuryUiTokens.Card,
                    border = BorderStroke(1.dp, LuxuryUiTokens.Gold),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Text(
                        text = "PICKUP HERE • 2 MINS AWAY",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = LuxuryUiTokens.Gold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Pin",
                    tint = LuxuryUiTokens.Gold,
                    modifier = Modifier.size(34.dp)
                )
            }

            // Header Pill
            Surface(
                color = Color(0xDD0B0B0D),
                border = BorderStroke(1.dp, LuxuryUiTokens.Border),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Box(modifier = Modifier.size(8.dp).background(LuxuryUiTokens.GreenSuccess, CircleShape))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("14 E-Rickshaws Active Nearby", fontSize = 11.sp, color = LuxuryUiTokens.Text, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Bottom Sheet: Where to? and Categories
        Surface(
            color = LuxuryUiTokens.Card,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            border = BorderStroke(1.dp, LuxuryUiTokens.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Where to? Search Bar
                Surface(
                    onClick = onWhereToClick,
                    color = LuxuryUiTokens.FieldBg,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, LuxuryUiTokens.FieldBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = LuxuryUiTokens.Gold, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Where to commute?", color = LuxuryUiTokens.Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quick Saved Locations
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickLocationChip("🏠 Home", "Model Town", onQuickDestination, Modifier.weight(1f))
                    QuickLocationChip("💼 Work", "Connaught Place", onQuickDestination, Modifier.weight(1f))
                    QuickLocationChip("🚇 Metro", "Rajiv Chowk Gate 2", onQuickDestination, Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Fast Vehicle Category Selectors
                Text("POPULAR RIDES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Muted)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CategoryPill("Standard", "₹20", "2 min", { onChooseCategory("Standard E-Rickshaw") }, Modifier.weight(1f))
                    CategoryPill("Eco-Pool", "₹10", "4 min", { onChooseCategory("Shared Eco-Pool") }, Modifier.weight(1f))
                    CategoryPill("Metro Feeder", "₹15", "1 min", { onChooseCategory("Metro Feeder") }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun QuickLocationChip(label: String, address: String, onClick: (String) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = { onClick(address) },
        color = LuxuryUiTokens.FieldBg,
        border = BorderStroke(1.dp, LuxuryUiTokens.Border),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Gold)
            Text(address, fontSize = 10.sp, color = LuxuryUiTokens.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun CategoryPill(name: String, fare: String, eta: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        color = LuxuryUiTokens.FieldBg,
        border = BorderStroke(1.dp, LuxuryUiTokens.Gold.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
        ) {
            Text(name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Text, maxLines = 1)
            Text(fare, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = LuxuryUiTokens.Gold)
            Text(eta, fontSize = 9.sp, color = LuxuryUiTokens.Muted)
        }
    }
}

// -------------------------------------------------------------
// SCREEN 09: SEARCH DESTINATION (Search bar, Recents, Favorites)
// -------------------------------------------------------------
@Composable
fun Screen09SearchDestination(
    initialQuery: String,
    onSelectDestination: (String) -> Unit,
    onPinDropClick: () -> Unit,
    onMultiStopClick: () -> Unit,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf(initialQuery) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Back & Title Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = LuxuryUiTokens.Gold)
            }
            Text("Search Destination", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Gold)
        }

        // Search Input Field
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search station, market, address...", color = LuxuryUiTokens.Muted, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = LuxuryUiTokens.Gold) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = LuxuryUiTokens.Muted)
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = LuxuryUiTokens.FieldBg,
                unfocusedContainerColor = LuxuryUiTokens.FieldBg,
                focusedBorderColor = LuxuryUiTokens.Gold,
                unfocusedBorderColor = LuxuryUiTokens.FieldBorder,
                focusedTextColor = LuxuryUiTokens.Text,
                unfocusedTextColor = LuxuryUiTokens.Text
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Action shortcuts
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onPinDropClick,
                colors = ButtonDefaults.buttonColors(containerColor = LuxuryUiTokens.Card),
                border = BorderStroke(1.dp, LuxuryUiTokens.Border),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = LuxuryUiTokens.Gold, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Set on Map", fontSize = 11.sp, color = LuxuryUiTokens.Text)
            }
            Button(
                onClick = onMultiStopClick,
                colors = ButtonDefaults.buttonColors(containerColor = LuxuryUiTokens.Card),
                border = BorderStroke(1.dp, LuxuryUiTokens.Border),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = LuxuryUiTokens.Gold, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Multi-Stop", fontSize = 11.sp, color = LuxuryUiTokens.Text)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Recents List
        Text("RECENT LOCATIONS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Muted)
        Spacer(modifier = Modifier.height(8.dp))

        listOf(
            "Connaught Place Outer Circle, New Delhi",
            "Rajiv Chowk Metro Station Gate 2",
            "Model Town 2 Market, Delhi",
            "Vishwa Vidyalaya Metro Hub, North Campus",
            "Chandni Chowk Main Road Corridor"
        ).forEach { dest ->
            Surface(
                onClick = { onSelectDestination(dest) },
                color = LuxuryUiTokens.Card,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, LuxuryUiTokens.Border),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Icon(Icons.Default.Place, contentDescription = null, tint = LuxuryUiTokens.Gold, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(dest, fontSize = 13.sp, color = LuxuryUiTokens.Text)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SCREEN 10: PIN DROP (Centre pin, Address, Gate notes)
// -------------------------------------------------------------
@Composable
fun Screen10PinDrop(onConfirmPin: (String) -> Unit, onBack: () -> Unit) {
    var gateNotes by remember { mutableStateOf("Gate 2 near ATM booth") }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f).background(Color(0xFF14141A))) {
            Icon(Icons.Default.LocationOn, contentDescription = "Pin", tint = LuxuryUiTokens.Gold, modifier = Modifier.size(44.dp).align(Alignment.Center))
            IconButton(onClick = onBack, modifier = Modifier.padding(16.dp).align(Alignment.TopStart)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = LuxuryUiTokens.Gold)
            }
        }
        Surface(
            color = LuxuryUiTokens.Card,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("CONFIRM PIN LOCATION", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Gold)
                Text("Connaught Place Block B Outer Circle", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = LuxuryUiTokens.Text, modifier = Modifier.padding(vertical = 6.dp))
                OutlinedTextField(
                    value = gateNotes,
                    onValueChange = { gateNotes = it },
                    label = { Text("Gate / Landmark Notes", color = LuxuryUiTokens.Muted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = LuxuryUiTokens.FieldBg,
                        unfocusedContainerColor = LuxuryUiTokens.FieldBg,
                        focusedTextColor = LuxuryUiTokens.Text,
                        unfocusedTextColor = LuxuryUiTokens.Text
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                GoldActionButton(text = "CONFIRM PICKUP PIN", onClick = { onConfirmPin("Connaught Place Block B ($gateNotes)") })
            }
        }
    }
}

// -------------------------------------------------------------
// SCREEN 11: MULTI-STOP ROUTE (Pickup, Stop 1, Add stop, Drop)
// -------------------------------------------------------------
@Composable
fun Screen11MultiStop(pickup: String, destination: String, onConfirmRoute: () -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = LuxuryUiTokens.Gold) }
            Text("Multi-Stop E-Rickshaw Route", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Gold)
        }
        Spacer(modifier = Modifier.height(12.dp))
        LuxuryCardItem("A", "Pickup: $pickup")
        LuxuryCardItem("1", "Stop 1: Model Town Metro Station")
        LuxuryCardItem("+", "+ Add Another Via Stop")
        LuxuryCardItem("B", "Drop: $destination")
        Spacer(modifier = Modifier.weight(1f))
        GoldActionButton(text = "CONFIRM MULTI-STOP ROUTE", onClick = onConfirmRoute)
    }
}

// -------------------------------------------------------------
// SCREEN 12: CHOOSE RIDE (Standard, Shared, Cargo, Metro Feeder)
// -------------------------------------------------------------
@Composable
fun Screen12ChooseRide(
    selectedCat: String,
    onSelectCat: (String) -> Unit,
    onFareBreakup: () -> Unit,
    onPreferences: () -> Unit,
    onBookNow: () -> Unit,
    onBack: () -> Unit
) {
    val categories = listOf(
        Triple("Standard E-Rickshaw", "₹ 128", "4 Seats • 100% Electric EV • Quiet Ride"),
        Triple("Shared Eco-Pool", "₹ 40", "Per Seat • Direct Metro Route • Eco-Saver"),
        Triple("Metro Feeder", "₹ 60", "3 Seats • Direct to Metro Gate 2"),
        Triple("Cargo E-Loader", "₹ 210", "400 kg Capacity • Heavy Parcel Deck")
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = LuxuryUiTokens.Gold) }
                Text("Choose E-Rickshaw", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Gold)
            }
            TextButton(onClick = onPreferences) {
                Text("Preferences ⚙", color = LuxuryUiTokens.Muted, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Categories List
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(categories) { (name, fare, desc) ->
                val isSelected = selectedCat == name
                Surface(
                    onClick = { onSelectCat(name) },
                    color = LuxuryUiTokens.Card,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) LuxuryUiTokens.Gold else LuxuryUiTokens.Border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isSelected) LuxuryUiTokens.Gold else LuxuryUiTokens.Text)
                            Text(desc, fontSize = 11.sp, color = LuxuryUiTokens.Muted)
                        }
                        Text(fare, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = LuxuryUiTokens.Gold)
                    }
                }
            }
        }

        // Fare Breakdown Link
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Estimated Total: ₹ 128", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Text)
            TextButton(onClick = onFareBreakup) {
                Text("Fare Breakup >", color = LuxuryUiTokens.Gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        GoldActionButton(text = "CONTINUE TO CONFIRM", onClick = onBookNow)
    }
}

// -------------------------------------------------------------
// SCREEN 13: FARE BREAKUP (Total ₹, Base+km+time, Promo code)
// -------------------------------------------------------------
@Composable
fun Screen13FareBreakup(
    baseFare: Int,
    kmFare: Int,
    techFee: Int,
    promoCode: String,
    promoDiscount: Int,
    onApplyPromo: (String) -> Unit,
    onContinuePayment: () -> Unit,
    onBack: () -> Unit
) {
    var promoInput by remember { mutableStateOf(promoCode) }
    val total = (baseFare + kmFare + techFee) - promoDiscount

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = LuxuryUiTokens.Gold) }
            Text("Fare Breakup", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Gold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Big ₹ Total Display matching uploaded HTML (.big)
        Text("₹ $total", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = LuxuryUiTokens.Gold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))

        LuxuryFareRow("Base Fare (Flagfall)", "₹ $baseFare")
        LuxuryFareRow("Distance Fare (3.5 km @ ₹8/km)", "₹ $kmFare")
        LuxuryFareRow("Platform & Clean Air Tech Fee", "₹ $techFee")
        if (promoDiscount > 0) {
            LuxuryFareRow("Promo Discount Applied", "- ₹ $promoDiscount", textColor = LuxuryUiTokens.GreenSuccess)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Promo code input
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = promoInput,
                onValueChange = { promoInput = it },
                placeholder = { Text("Enter Promo Code (e.g. GOLD100)", color = LuxuryUiTokens.Muted, fontSize = 12.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = LuxuryUiTokens.FieldBg,
                    unfocusedContainerColor = LuxuryUiTokens.FieldBg,
                    focusedTextColor = LuxuryUiTokens.Text,
                    unfocusedTextColor = LuxuryUiTokens.Text
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { onApplyPromo(promoInput) },
                colors = ButtonDefaults.buttonColors(containerColor = LuxuryUiTokens.Gold),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("APPLY", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        GoldActionButton(text = "SELECT PAYMENT METHOD", onClick = onContinuePayment)
    }
}

@Composable
fun LuxuryFareRow(label: String, amount: String, textColor: Color = LuxuryUiTokens.Text) {
    Surface(
        color = LuxuryUiTokens.Card,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, LuxuryUiTokens.Border),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 12.sp, color = LuxuryUiTokens.Muted)
            Text(amount, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor)
        }
    }
}

// -------------------------------------------------------------
// SCREEN 14: RIDE PREFERENCES (Quiet ride, AC temp, Luggage, Accessibility)
// -------------------------------------------------------------
@Composable
fun Screen14RidePreferences(onSavePreferences: () -> Unit, onBack: () -> Unit) {
    var quietRide by remember { mutableStateOf(true) }
    var luggageHelp by remember { mutableStateOf(false) }
    var wheelchairReq by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = LuxuryUiTokens.Gold) }
            Text("Ride Preferences", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Gold)
        }
        Spacer(modifier = Modifier.height(16.dp))

        PreferenceToggle("Quiet Ride", "Pilot maintains silent commute", quietRide) { quietRide = it }
        PreferenceToggle("Luggage Assistance", "Assistance loading bags onto rear deck", luggageHelp) { luggageHelp = it }
        PreferenceToggle("Step-Free / Accessibility", "Lowered kerb boarding assistance", wheelchairReq) { wheelchairReq = it }

        Spacer(modifier = Modifier.weight(1f))
        GoldActionButton(text = "SAVE PREFERENCES", onClick = onSavePreferences)
    }
}

@Composable
fun PreferenceToggle(title: String, desc: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(
        color = LuxuryUiTokens.Card,
        border = BorderStroke(1.dp, LuxuryUiTokens.Border),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Text)
                Text(desc, fontSize = 11.sp, color = LuxuryUiTokens.Muted)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedThumbColor = LuxuryUiTokens.Gold, checkedTrackColor = LuxuryUiTokens.GoldDark)
            )
        }
    }
}

// -------------------------------------------------------------
// SCREEN 15: PAYMENT METHOD (UPI, Card, Wallet, Cash)
// -------------------------------------------------------------
@Composable
fun Screen15PaymentMethod(walletBalance: Int, onSelectPayment: () -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = LuxuryUiTokens.Gold) }
            Text("Payment Method", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Gold)
        }
        Spacer(modifier = Modifier.height(16.dp))

        LuxuryPaymentItem("UPI (Google Pay / PhonePe / Paytm)", "Instant zero-fee digital settlement", onSelectPayment)
        LuxuryPaymentItem("E-Wallet Balance", "Available: ₹ $walletBalance", onSelectPayment)
        LuxuryPaymentItem("Credit / Debit Card", "Visa •••• 4821", onSelectPayment)
        LuxuryPaymentItem("Cash to Pilot", "Pay exact cash upon ride completion", onSelectPayment)
    }
}

@Composable
fun LuxuryPaymentItem(title: String, desc: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = LuxuryUiTokens.Card,
        border = BorderStroke(1.dp, LuxuryUiTokens.Border),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Text)
                Text(desc, fontSize = 11.sp, color = LuxuryUiTokens.Muted)
            }
            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = LuxuryUiTokens.Gold)
        }
    }
}

// -------------------------------------------------------------
// SCREEN 16: CONFIRM BOOKING (Route, Fare, Book now)
// -------------------------------------------------------------
@Composable
fun Screen16ConfirmBooking(
    pickup: String,
    destination: String,
    category: String,
    totalFare: Int,
    onConfirmBooking: () -> Unit,
    onTriggerOutofZone: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = LuxuryUiTokens.Gold) }
            Text("Confirm E-Rickshaw Booking", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Gold)
        }
        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            color = LuxuryUiTokens.Card,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, LuxuryUiTokens.Border),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("PICKUP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Gold)
                Text(pickup, fontSize = 13.sp, color = LuxuryUiTokens.Text, modifier = Modifier.padding(bottom = 8.dp))
                Divider(color = LuxuryUiTokens.Border)
                Text("DROP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Gold, modifier = Modifier.padding(top = 8.dp))
                Text(destination, fontSize = 13.sp, color = LuxuryUiTokens.Text)
            }
        }

        LuxuryFareRow("Vehicle Category", category)
        LuxuryFareRow("Total Estimated Fare", "₹ $totalFare", LuxuryUiTokens.Gold)
        LuxuryFareRow("Payment Mode", "UPI / Cash to Pilot")

        Spacer(modifier = Modifier.weight(1f))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            GoldActionButton(text = "CONFIRM AND BOOK RIDE", modifier = Modifier.weight(1f), onClick = onConfirmBooking)
        }
    }
}

// -------------------------------------------------------------
// SCREEN 17: OUT-OF-ZONE ALERT (Restricted road, Change drop)
// -------------------------------------------------------------
@Composable
fun Screen17OutOfZone(onChangeDestination: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = LuxuryUiTokens.RedAlert, modifier = Modifier.size(54.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text("RESTRICTED OPERATIONAL ZONE", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.RedAlert)
        Text(
            "Selected destination falls along an elevated highway or restricted expressway where E-Rickshaws are not legally permitted to ply.",
            fontSize = 12.sp,
            color = LuxuryUiTokens.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 12.dp)
        )
        GoldActionButton(text = "CHANGE DROP LOCATION", onClick = onChangeDestination)
    }
}

// -------------------------------------------------------------
// SCREEN 18: SEARCHING DRIVER (Radar pulse, Offer timer, Cancel)
// -------------------------------------------------------------
@Composable
fun Screen18SearchingRadar(secondsLeft: Int, onCancel: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse)
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Radar Pulse Ring
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(160.dp)
        ) {
            Box(
                modifier = Modifier
                    .size((140 * pulseScale).dp)
                    .background(LuxuryUiTokens.Gold.copy(alpha = 0.15f), CircleShape)
                    .border(2.dp, LuxuryUiTokens.Gold.copy(alpha = 0.4f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(LuxuryUiTokens.Card, CircleShape)
                    .border(2.dp, LuxuryUiTokens.Gold, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("${secondsLeft}s", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Gold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("LOCATING NEAREST PILOT", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Gold)
        Text("Broadcasting dispatch offer to 14 active E-Rickshaw pilots nearby...", fontSize = 12.sp, color = LuxuryUiTokens.Muted, textAlign = TextAlign.Center)

        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onCancel,
            colors = ButtonDefaults.buttonColors(containerColor = LuxuryUiTokens.Card),
            border = BorderStroke(1.dp, LuxuryUiTokens.RedAlert),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("CANCEL SEARCH", color = LuxuryUiTokens.RedAlert, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// -------------------------------------------------------------
// SCREEN 19: DRIVER FOUND & SCREEN 20: DRIVER ARRIVING
// -------------------------------------------------------------
@Composable
fun Screen19DriverFound(
    driverName: String,
    plate: String,
    rating: String,
    startOtp: String,
    etaMinutes: Int,
    onProceedToArriving: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = LuxuryUiTokens.GreenSuccess, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(10.dp))
        Text("PILOT ASSIGNED", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Gold)

        Surface(
            color = LuxuryUiTokens.Card,
            border = BorderStroke(1.dp, LuxuryUiTokens.Border),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(driverName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Text)
                Text("Saarthi Electric EV • $plate • $rating", fontSize = 12.sp, color = LuxuryUiTokens.Muted)
                Spacer(modifier = Modifier.height(14.dp))
                Text("START RIDE OTP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Muted)
                Text(startOtp, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 6.sp, color = LuxuryUiTokens.Gold)
                Text("Share this 4-digit code with the pilot when boarding", fontSize = 10.sp, color = LuxuryUiTokens.Muted)
            }
        }

        Text("Pilot is $etaMinutes mins away from your pickup point", fontSize = 12.sp, color = LuxuryUiTokens.Gold)
        Spacer(modifier = Modifier.height(16.dp))
        GoldActionButton(text = "TRACK ARRIVAL", onClick = onProceedToArriving)
    }
}

@Composable
fun Screen20DriverArriving(
    driverName: String,
    plate: String,
    startOtp: String,
    etaMinutes: Int,
    onCall: () -> Unit,
    onChat: () -> Unit,
    onBoarded: () -> Unit,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Map Tracking Area
        Box(modifier = Modifier.fillMaxWidth().weight(1f).background(Color(0xFF131318))) {
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("PILOT IS ARRIVING", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Gold)
                Text("ETA $etaMinutes MINS", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = LuxuryUiTokens.Text)
            }
        }

        // Bottom Driver & OTP Card
        Surface(
            color = LuxuryUiTokens.Card,
            border = BorderStroke(1.dp, LuxuryUiTokens.Border),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(driverName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Text)
                        Text("$plate • White E-Rickshaw", fontSize = 11.sp, color = LuxuryUiTokens.Muted)
                    }
                    Surface(color = LuxuryUiTokens.FieldBg, border = BorderStroke(1.dp, LuxuryUiTokens.Gold), shape = RoundedCornerShape(6.dp)) {
                        Text("OTP: $startOtp", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Gold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onCall, colors = ButtonDefaults.buttonColors(containerColor = LuxuryUiTokens.FieldBg), border = BorderStroke(1.dp, LuxuryUiTokens.Border), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = LuxuryUiTokens.Gold, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Call", color = LuxuryUiTokens.Text, fontSize = 12.sp)
                    }
                    Button(onClick = onChat, colors = ButtonDefaults.buttonColors(containerColor = LuxuryUiTokens.FieldBg), border = BorderStroke(1.dp, LuxuryUiTokens.Border), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Email, contentDescription = null, tint = LuxuryUiTokens.Gold, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Chat", color = LuxuryUiTokens.Text, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                GoldActionButton(text = "BOARDED - START TRIP", onClick = onBoarded)
            }
        }
    }
}

// -------------------------------------------------------------
// SCREEN 21: RIDE CHAT & SCREEN 22: MASKED CALL
// -------------------------------------------------------------
@Composable
fun Screen21RideChat(driverName: String, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = LuxuryUiTokens.Gold) }
            Text("Chat with $driverName", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Gold)
        }
        Spacer(modifier = Modifier.height(12.dp))
        LuxuryCardItem("Ramesh Kumar (Pilot)", "I am at Gate 2 near the auto stand.")
        LuxuryCardItem("You", "Yes, reaching in 1 minute in black jacket.")
        Spacer(modifier = Modifier.weight(1f))
        Text("QUICK REPLIES", fontSize = 10.sp, color = LuxuryUiTokens.Muted, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = LuxuryUiTokens.Card)) {
                Text("I am here", fontSize = 11.sp, color = LuxuryUiTokens.Gold)
            }
            Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = LuxuryUiTokens.Card)) {
                Text("Waiting at Gate 2", fontSize = 11.sp, color = LuxuryUiTokens.Gold)
            }
        }
    }
}

@Composable
fun Screen22MaskedCall(driverName: String, onEndCall: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Phone, contentDescription = null, tint = LuxuryUiTokens.Gold, modifier = Modifier.size(54.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text("NUMBER PRIVACY PROTECTED", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Muted)
        Text(driverName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Text)
        Text("Calling via Secure Relay Server...", fontSize = 12.sp, color = LuxuryUiTokens.Gold, modifier = Modifier.padding(vertical = 8.dp))
        Spacer(modifier = Modifier.height(30.dp))
        Button(
            onClick = onEndCall,
            colors = ButtonDefaults.buttonColors(containerColor = LuxuryUiTokens.RedAlert),
            shape = CircleShape,
            modifier = Modifier.size(60.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "End Call", tint = Color.White)
        }
    }
}

// -------------------------------------------------------------
// SCREEN 23: RIDE IN PROGRESS (Active Ride Isolation, SOS button)
// -------------------------------------------------------------
@Composable
fun Screen23RideInProgress(
    driverName: String,
    destination: String,
    onShare: () -> Unit,
    onSos: () -> Unit,
    onCompleteTrip: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Active Map View
        Box(modifier = Modifier.fillMaxWidth().weight(1f).background(Color(0xFF101015))) {
            // Floating Red/Gold SOS Panic Button (Rule 4: Always accessible during live ride)
            Surface(
                onClick = onSos,
                color = LuxuryUiTokens.RedAlert,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color.White),
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            ) {
                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = "SOS", tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("EMERGENCY SOS", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
            }

            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("RIDE IN PROGRESS", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Gold)
                Text("Dropping at: $destination", fontSize = 12.sp, color = LuxuryUiTokens.Text)
                Text("Speed: 22 km/h • 100% Electric", fontSize = 11.sp, color = LuxuryUiTokens.Muted)
            }
        }

        // Persistent Ride Card with ETA & Driver
        Surface(
            color = LuxuryUiTokens.Card,
            border = BorderStroke(1.dp, LuxuryUiTokens.Border),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Pilot: $driverName", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Text)
                        Text("ETA to Destination: 7 mins", fontSize = 12.sp, color = LuxuryUiTokens.Gold)
                    }
                    Button(onClick = onShare, colors = ButtonDefaults.buttonColors(containerColor = LuxuryUiTokens.FieldBg), border = BorderStroke(1.dp, LuxuryUiTokens.Border)) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = LuxuryUiTokens.Gold, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share", color = LuxuryUiTokens.Text, fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                GoldActionButton(text = "DROP AND COMPLETE RIDE", onClick = onCompleteTrip)
            }
        }
    }
}

// -------------------------------------------------------------
// SCREEN 24: SHARE TRIP & SCREEN 25: SOS PANIC & SCREEN 26: FARE SPLIT
// -------------------------------------------------------------
@Composable
fun Screen24ShareTrip(onDismiss: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = LuxuryUiTokens.Gold) }
            Text("Share Live Journey", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Gold)
        }
        Spacer(modifier = Modifier.height(16.dp))
        LuxuryCardItem("WhatsApp Share", "Send live tracking link to family & trusted contacts")
        LuxuryCardItem("SMS Live Link", "https://e-rickshaw.in/live/ride-4821")
        LuxuryCardItem("Hide Exact Address", "Only displays vehicle plate and route corridor")
    }
}

@Composable
fun Screen25SosPanic(onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = LuxuryUiTokens.RedAlert, modifier = Modifier.size(60.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("EMERGENCY POLICE & SOC DESK", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.RedAlert)
        Text("Hold to confirm. Local PCR Van (112) and 24x7 HQ command will be immediately alerted with your live GPS location.", fontSize = 12.sp, color = LuxuryUiTokens.Muted, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = LuxuryUiTokens.RedAlert), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("DISPATCH EMERGENCY SOS", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun Screen26FareSplit(totalFare: Int, onDone: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Split Fare with Co-Passengers", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Gold)
        Spacer(modifier = Modifier.height(12.dp))
        LuxuryCardItem("Total Fare", "₹ $totalFare")
        LuxuryCardItem("Passenger 1 (You)", "₹ ${totalFare / 2}")
        LuxuryCardItem("Passenger 2", "₹ ${totalFare / 2} • Request Sent")
        Spacer(modifier = Modifier.weight(1f))
        GoldActionButton(text = "CONFIRM SPLIT", onClick = onDone)
    }
}

// -------------------------------------------------------------
// SCREEN 27: CANCEL RIDE (Reasons, Fee notice, Confirm)
// -------------------------------------------------------------
@Composable
fun Screen27CancelRide(onKeepRide: () -> Unit, onConfirmCancel: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Cancel Ride?", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Gold)
        Text("A cancellation fee of ₹20 applies if pilot has already arrived.", fontSize = 12.sp, color = LuxuryUiTokens.Muted, modifier = Modifier.padding(vertical = 8.dp))

        listOf(
            "Pilot is taking too long / not moving",
            "Booked by mistake",
            "Found alternate transit (Metro)",
            "Pilot asked to cancel"
        ).forEach { reason ->
            Surface(
                onClick = onConfirmCancel,
                color = LuxuryUiTokens.Card,
                border = BorderStroke(1.dp, LuxuryUiTokens.Border),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text(reason, fontSize = 12.sp, color = LuxuryUiTokens.Text, modifier = Modifier.padding(12.dp))
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        GoldActionButton(text = "KEEP MY RIDE", onClick = onKeepRide)
    }
}

// -------------------------------------------------------------
// SCREEN 28: TRIP SUMMARY & SCREEN 29: PAYMENT & SCREEN 30: RATE DRIVER
// -------------------------------------------------------------
@Composable
fun Screen28TripSummary(totalFare: Int, distanceKm: Double, durationMins: Int, onPay: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = LuxuryUiTokens.GreenSuccess, modifier = Modifier.size(54.dp))
        Spacer(modifier = Modifier.height(10.dp))
        Text("YOU HAVE ARRIVED!", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Gold)
        Text("Thank you for riding electric and reducing urban carbon footprint", fontSize = 11.sp, color = LuxuryUiTokens.Muted)

        Text("₹ $totalFare", fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, color = LuxuryUiTokens.Gold, modifier = Modifier.padding(vertical = 16.dp))

        LuxuryFareRow("Total Distance", "$distanceKm km")
        LuxuryFareRow("Ride Time", "$durationMins mins")
        LuxuryFareRow("Tailpipe CO₂ Saved", "420 grams")

        Spacer(modifier = Modifier.weight(1f))
        GoldActionButton(text = "PROCEED TO PAY", onClick = onPay)
    }
}

@Composable
fun Screen29PaymentSettle(amount: Int, onPaymentDone: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Settle Ride Fare", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Gold)
        Text("Amount Payable: ₹ $amount", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = LuxuryUiTokens.Text, modifier = Modifier.padding(vertical = 12.dp))

        LuxuryPaymentItem("Pay via UPI (GPay / PhonePe)", "Instant online zero-fee settlement", onPaymentDone)
        LuxuryPaymentItem("Pay Cash to Pilot", "Hand ₹ $amount cash to pilot", onPaymentDone)
        LuxuryPaymentItem("Deduct from E-Wallet", "Auto-debit from available balance", onPaymentDone)
    }
}

@Composable
fun Screen30RateDriver(driverName: String, onSubmitted: () -> Unit) {
    var stars by remember { mutableIntStateOf(5) }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("How was your ride with $driverName?", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Gold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))

        // 5 Gold Stars
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in 1..5) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = if (i <= stars) LuxuryUiTokens.Gold else LuxuryUiTokens.Border,
                    modifier = Modifier.size(36.dp).clickable { stars = i }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text("WHAT MADE IT GREAT?", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Muted)
        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("Clean EV")
            Chip("Polite Pilot")
            Chip("Safe Route")
        }

        Spacer(modifier = Modifier.weight(1f))
        GoldActionButton(text = "SUBMIT RATING", onClick = onSubmitted)
    }
}

@Composable
fun Chip(text: String) {
    Surface(color = LuxuryUiTokens.Card, border = BorderStroke(1.dp, LuxuryUiTokens.Gold), shape = RoundedCornerShape(12.dp)) {
        Text(text, fontSize = 11.sp, color = LuxuryUiTokens.Gold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

// -------------------------------------------------------------
// RIDES TAB: SCREENS 31 TO 34
// -------------------------------------------------------------
@Composable
fun RidesTabContent(onSelectRideDetail: (String) -> Unit, onRepeatRide: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Ride History", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Gold)
        Spacer(modifier = Modifier.height(12.dp))

        listOf(
            Triple("Today, 2:15 PM", "Model Town -> Rajiv Chowk Gate 2", "₹ 128 • Completed"),
            Triple("Yesterday, 9:30 AM", "Chandni Chowk -> Old Delhi Stn", "₹ 40 • Completed"),
            Triple("28 Oct, 6:00 PM", "Vishwa Vidyalaya -> Mall Road", "₹ 20 • Cancelled")
        ).forEach { (time, route, status) ->
            Surface(
                color = LuxuryUiTokens.Card,
                border = BorderStroke(1.dp, LuxuryUiTokens.Border),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(time, fontSize = 11.sp, color = LuxuryUiTokens.Muted)
                        Text(status, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (status.contains("Completed")) LuxuryUiTokens.Gold else LuxuryUiTokens.RedAlert)
                    }
                    Text(route, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LuxuryUiTokens.Text, modifier = Modifier.padding(vertical = 4.dp))
                    Button(onClick = onRepeatRide, colors = ButtonDefaults.buttonColors(containerColor = LuxuryUiTokens.FieldBg), border = BorderStroke(1.dp, LuxuryUiTokens.Gold), modifier = Modifier.align(Alignment.End)) {
                        Text("Repeat Ride", color = LuxuryUiTokens.Gold, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// WALLET TAB: SCREENS 35 TO 40
// -------------------------------------------------------------
@Composable
fun WalletTabContent(balance: Int, onAddMoney: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("E-Rickshaw Wallet", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Gold)
        Spacer(modifier = Modifier.height(14.dp))

        // Gold Wallet Card matching .big
        Surface(
            color = LuxuryUiTokens.Card,
            border = BorderStroke(1.dp, LuxuryUiTokens.Gold),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("AVAILABLE BALANCE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Muted)
                Text("₹ $balance.00", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = LuxuryUiTokens.Gold, modifier = Modifier.padding(vertical = 6.dp))
                Text("Auto top-up enabled • Linked UPI VPA", fontSize = 11.sp, color = LuxuryUiTokens.Muted)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("QUICK TOP-UP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Muted)
        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(100, 250, 500, 1000).forEach { amt ->
                Button(
                    onClick = { onAddMoney(amt) },
                    colors = ButtonDefaults.buttonColors(containerColor = LuxuryUiTokens.Card),
                    border = BorderStroke(1.dp, LuxuryUiTokens.Gold),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("+₹$amt", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Gold)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text("PROMOS & REFERRALS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Muted)
        Spacer(modifier = Modifier.height(8.dp))
        LuxuryCardItem("Refer & Earn", "Share code RICKSHAW100 with friends and earn ₹50 each")
        LuxuryCardItem("Active Offers", "METRO10: Flat ₹10 Off on Metro Feeder rides")
    }
}

// -------------------------------------------------------------
// ACCOUNT TAB: SCREENS 43 TO 50
// -------------------------------------------------------------
@Composable
fun AccountTabContent(onSignOut: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Account & Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Gold)
        Spacer(modifier = Modifier.height(14.dp))

        // Profile Avatar Card
        Surface(color = LuxuryUiTokens.Card, border = BorderStroke(1.dp, LuxuryUiTokens.Border), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(46.dp).background(LuxuryUiTokens.Gold, CircleShape), contentAlignment = Alignment.Center) {
                    Text("SS", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Sunil Sharma", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Text)
                    Text("+91 98765 43210 • Gold Tier Rider", fontSize = 12.sp, color = LuxuryUiTokens.Muted)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        LuxuryCardItem("Emergency Contacts", "2 Contacts designated for SOS alerts")
        LuxuryCardItem("Help Centre & FAQs", "24x7 Customer Support Desk & Tickets")
        LuxuryCardItem("Report Lost Item", "Report item left in E-Rickshaw")
        LuxuryCardItem("Language", "English / Hindi")
        LuxuryCardItem("Loyalty Sanctuary", "1,450 Points • 12 Ride Streak")

        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onSignOut,
            colors = ButtonDefaults.buttonColors(containerColor = LuxuryUiTokens.FieldBg),
            border = BorderStroke(1.dp, LuxuryUiTokens.RedAlert),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("SIGN OUT OF DEVICE", color = LuxuryUiTokens.RedAlert, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// -------------------------------------------------------------
// HELPER COMPONENTS
// -------------------------------------------------------------
@Composable
fun LuxuryCardItem(title: String, desc: String) {
    Surface(
        color = LuxuryUiTokens.Card,
        border = BorderStroke(1.dp, LuxuryUiTokens.Border),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = LuxuryUiTokens.Gold)
            Text(desc, fontSize = 11.sp, color = LuxuryUiTokens.Muted)
        }
    }
}

@Composable
fun GoldActionButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(LuxuryUiTokens.GoldGradient, RoundedCornerShape(10.dp))
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
    }
}

@Composable
fun LuxuryDialog(
    title: String,
    message: String,
    confirmText: String = "CONFIRM",
    dismissText: String = "CANCEL",
    confirmColor: Color = LuxuryUiTokens.Gold,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LuxuryUiTokens.Card,
        titleContentColor = LuxuryUiTokens.Gold,
        textContentColor = LuxuryUiTokens.Text,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(message, fontSize = 12.sp, color = LuxuryUiTokens.Muted) },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = confirmColor)) {
                Text(confirmText, color = if (confirmColor == LuxuryUiTokens.Gold) Color.Black else Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText, color = LuxuryUiTokens.Muted)
            }
        }
    )
}
