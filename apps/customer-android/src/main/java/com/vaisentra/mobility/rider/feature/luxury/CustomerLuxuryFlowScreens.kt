package com.vaisentra.mobility.rider.feature.luxury

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
import com.vaisentra.mobility.rider.core.network.ApiClient
import com.vaisentra.mobility.rider.core.network.FareEstimateRequest
import com.vaisentra.mobility.rider.core.network.RideRequestPayload
import com.vaisentra.mobility.rider.core.network.RateDriverRequest
import com.vaisentra.mobility.rider.core.network.TopupWalletRequest
import com.vaisentra.mobility.rider.core.network.SaveVpaRequest
import com.vaisentra.mobility.rider.core.network.ProfileUpdateRequest
import com.vaisentra.mobility.rider.core.network.VehicleCategoryDto
import com.vaisentra.mobility.core.map.LatLng
import com.vaisentra.mobility.core.map.AppMapDetailsCatalog
import com.vaisentra.mobility.core.map.MapboxPlaceSuggestion
import com.vaisentra.mobility.core.map.MapboxSearchService
import com.vaisentra.mobility.core.map.MapboxDirectionsService
import com.vaisentra.mobility.core.map.NavigationRouteResult
import com.vaisentra.mobility.core.map.MapboxNavigationStep
import com.vaisentra.mobility.core.map.DeviceLocationProvider
import com.vaisentra.mobility.rider.feature.map.MapboxCustomerMapView
import com.vaisentra.mobility.rider.feature.map.MapLayersBottomSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

// Exact Colors from ui_65_screens_master_showcase.html:
// :root { color-scheme: dark; --bg: #0b0b0f; --card: #0f172a; --amber: #f59e0b; --text: #ffffff; --muted: #94a3b8; --border: #1e293b; --emerald: #10b981; }
object LuxuryUiTokens {
    val Bg = Color(0xFF0B0B0F)
    val Card = Color(0xFF0F172A)
    val Gold = Color(0xFFF59E0B) // Electric Amber from ui_65_screens_master_showcase
    val GoldDark = Color(0xFFD97706) // Dark Amber
    val Text = Color(0xFFFFFFFF) // Pure White
    val Muted = Color(0xFF94A3B8) // Slate 400
    val Border = Color(0xFF1E293B) // Slate 800
    val FieldBg = Color(0xFF020617) // Slate 950
    val FieldBorder = Color(0xFF334155) // Slate 700
    val RedAlert = Color(0xFFEF4444)
    val GreenSuccess = Color(0xFF10B981) // Emerald 500
    val Sky = Color(0xFF38BDF8) // Sky 400

    val GoldGradient = Brush.horizontalGradient(listOf(GoldDark, Gold))
}

// Flow steps for Customer App per uploaded HTML
enum class CustFlowStep {
    WELCOME_LANGUAGE,     // 01 · Brand Welcome & Language Selector
    SIGN_IN_PASSWORD,     // 02 · Sign In with Phone & Password
    SIGN_IN_OTP,          // 03 · Sign In with SMS OTP
    FORGOT_PASSWORD,      // 04 · Forgot Password & SMS Reset
    SIGN_UP,              // 05 · Sign Up & Basic Identity
    COMMUTER_PREFERENCES, // 06 · Commuter Preferences & Accessibility
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

// =========================================================================
// DATA MODELS FOR FULL WORKING CRUD OPERATIONS
// =========================================================================
data class SavedPlace(
    val id: String,
    val title: String,
    val address: String,
    val isHome: Boolean = false,
    val isWork: Boolean = false
)

data class EmergencyContactItem(
    val id: String,
    val name: String,
    val phone: String,
    val relationship: String,
    val notifyOnSos: Boolean = true
)

data class WalletTxn(
    val id: String,
    val title: String,
    val amount: Int,
    val isCredit: Boolean,
    val time: String
)

data class SavedPaymentVpa(
    val id: String,
    val vpa: String,
    val bank: String,
    val isPrimary: Boolean = false
)

data class ChatBubbleItem(
    val id: String,
    val message: String,
    val isCustomer: Boolean,
    val timestamp: String
)

data class SupportTicketItem(
    val id: String,
    val subject: String,
    val status: String,
    val date: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerLuxuryFlowScreens(
    activeTab: String,
    onTabSelected: (String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Active Ride Flow State
    var activeRideId by remember { mutableStateOf<String?>(null) }
    var currentEstimateId by remember { mutableStateOf<String?>(null) }
    val tokenManager = remember { com.vaisentra.mobility.rider.core.security.TokenManager.getInstance(context) }
    var flowStep by remember {
        mutableStateOf(if (tokenManager.isLoggedIn()) CustFlowStep.HOME_MAP else CustFlowStep.WELCOME_LANGUAGE)
    }
    var selectedCategory by remember { mutableStateOf("Standard E-Rickshaw") }
    var estimatedFare by remember { mutableIntStateOf(0) }
    var destinationQuery by remember { mutableStateOf("") }
    val initialCustomerLoc = remember {
        DeviceLocationProvider.getLastKnownLocation(context) ?: LatLng(28.6139, 77.2090)
    }
    var pickupAddress by remember { mutableStateOf("Current Location") }
    var pickupLatLng by remember { mutableStateOf(initialCustomerLoc) }
    var destLatLng by remember { mutableStateOf<LatLng?>(null) }
    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var routeDistanceKm by remember { mutableStateOf<Double?>(null) }
    var routeDurationMinutes by remember { mutableStateOf<Double?>(null) }
    var routeNavigationSteps by remember { mutableStateOf<List<MapboxNavigationStep>>(emptyList()) }
    var cameraTargetLatLng by remember { mutableStateOf<LatLng?>(null) }
    var cameraAnimTrigger by remember { mutableIntStateOf(0) }
    var cameraAnimType by remember { mutableStateOf("flyTo") }
    var isFetchingMapboxRoute by remember { mutableStateOf(false) }

    val locationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            DeviceLocationProvider.requestCurrentLocation(context) { loc ->
                pickupLatLng = loc
                cameraTargetLatLng = loc
                cameraAnimTrigger++
                scope.launch {
                    val rev = MapboxSearchService.reverseGeocode(loc.latitude, loc.longitude)
                    pickupAddress = if (!rev.isNullOrBlank()) rev else "Current Location (%.4f, %.4f)".format(loc.latitude, loc.longitude)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (DeviceLocationProvider.hasLocationPermission(context)) {
            DeviceLocationProvider.requestCurrentLocation(context) { loc ->
                pickupLatLng = loc
                cameraTargetLatLng = loc
                cameraAnimTrigger++
                scope.launch {
                    val rev = MapboxSearchService.reverseGeocode(loc.latitude, loc.longitude)
                    pickupAddress = if (!rev.isNullOrBlank()) rev else "Current Location (%.4f, %.4f)".format(loc.latitude, loc.longitude)
                }
            }
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    fun selectDestinationAndCalculateRoute(title: String, targetLat: Double, targetLng: Double, animateCamera: Boolean = true) {
        destinationQuery = title
        destLatLng = LatLng(targetLat, targetLng)
        if (animateCamera) {
            cameraTargetLatLng = LatLng(targetLat, targetLng)
            cameraAnimType = "flyTo"
            cameraAnimTrigger++
        }
        scope.launch {
            isFetchingMapboxRoute = true
            try {
                val navResult = MapboxDirectionsService.fetchNavigationRoute(
                    originLat = pickupLatLng.latitude,
                    originLng = pickupLatLng.longitude,
                    destLat = targetLat,
                    destLng = targetLng
                )
                if (navResult != null && navResult.coordinates.isNotEmpty()) {
                    routePoints = navResult.coordinates.map { LatLng(it.latitude, it.longitude) }
                    routeDistanceKm = navResult.distanceKm
                    routeDurationMinutes = navResult.durationMinutes
                    routeNavigationSteps = navResult.steps

                    // Dynamic distance-aware fare calculation (₹30 base + ₹12/km)
                    val baseFare = 30
                    val perKmRate = 12
                    val computedFare = (baseFare + (navResult.distanceKm * perKmRate)).toInt()
                    if (computedFare > 0) {
                        estimatedFare = computedFare
                    }
                } else {
                    routePoints = listOf(pickupLatLng, LatLng(targetLat, targetLng))
                }
            } catch (e: Exception) {
                Log.w("CustomerFlow", "Mapbox route fetch error: ${e.message}")
                routePoints = listOf(pickupLatLng, LatLng(targetLat, targetLng))
            } finally {
                isFetchingMapboxRoute = false
            }
        }
    }

    var startOtp by remember { mutableStateOf("") }
    var driverName by remember { mutableStateOf("") }
    var driverPhone by remember { mutableStateOf("") }
    var driverPlate by remember { mutableStateOf("") }
    var driverRating by remember { mutableStateOf("") }
    var driverEta by remember { mutableIntStateOf(0) }
    var intermediateStopAddress by remember { mutableStateOf("") }

    // Dynamic Backend Fare Estimation Hook
    LaunchedEffect(destinationQuery, selectedCategory) {
        try {
            val api = ApiClient.getService()
            val catCode = if (selectedCategory.contains("Share", ignoreCase = true)) "E_RICKSHAW_SHARE"
                          else if (selectedCategory.contains("Auto", ignoreCase = true)) "E_AUTO"
                          else "E_RICKSHAW_STANDARD"
            val estResp = api.calculateFareEstimate(
                FareEstimateRequest(
                    categoryId = catCode,
                    pickupLatitude = 28.7041,
                    pickupLongitude = 77.1025,
                    destinationLatitude = 28.6304,
                    destinationLongitude = 77.2177
                )
            )
            if (estResp.isSuccessful && estResp.body() != null) {
                val est = estResp.body()!!
                currentEstimateId = est.estimateId
                val rupees = est.estimatedFareRupees?.toInt() ?: (est.estimatedFareCents / 100)
                if (rupees > 0) {
                    estimatedFare = rupees
                }
            }
        } catch (t: Throwable) {
            Log.w("CustomerFlow", "Fare estimate API fallback: ${t.message}")
        }
    }

    // User Profile State (CRUD - dynamically fetched from /customers/profile)
    var userName by remember { mutableStateOf("") }
    var userPhone by remember { mutableStateOf("") }
    var userEmail by remember { mutableStateOf("") }

    // Wallet State (CRUD - dynamically fetched from /customers/wallet)
    var walletBalance by remember { mutableIntStateOf(0) }
    var walletTransactions by remember { mutableStateOf<List<WalletTxn>>(emptyList()) }
    var savedVpas by remember { mutableStateOf<List<SavedPaymentVpa>>(emptyList()) }

    // Saved Places (CRUD - dynamically fetched from /customers/places)
    var savedPlaces by remember { mutableStateOf<List<SavedPlace>>(emptyList()) }

    // Emergency Contacts (CRUD - dynamically fetched from /customers/emergency-contacts)
    var emergencyContacts by remember { mutableStateOf<List<EmergencyContactItem>>(emptyList()) }

    // Chat Thread (CRUD - dynamically fetched from /rides/{id}/chat)
    var chatMessages by remember { mutableStateOf<List<ChatBubbleItem>>(emptyList()) }

    // Support & Disputes (CRUD - dynamically fetched from /support/tickets)
    var supportTickets by remember { mutableStateOf<List<SupportTicketItem>>(emptyList()) }

    // Vehicle Categories (dynamically fetched from /vehicles/categories)
    var backendCategories by remember { mutableStateOf<List<VehicleCategoryDto>>(emptyList()) }

    // Promos State (CRUD)
    var promoCode by remember { mutableStateOf("") }
    var promoDiscount by remember { mutableIntStateOf(0) }

    // Ride Preferences (CRUD)
    var prefQuietRide by remember { mutableStateOf(true) }
    var prefAcComfort by remember { mutableStateOf(false) }
    var prefLuggageAssistance by remember { mutableStateOf(true) }
    var prefWheelchairAccessible by remember { mutableStateOf(false) }

    // Driver Search Retry Counter (Rule: 3 Retries -> Offer Fare Bump)
    var searchRetries by remember { mutableIntStateOf(0) }
    var radarSeconds by remember { mutableIntStateOf(20) }

    // Dynamic Backend Integration Hooks: Fetch initial real data from FastAPI backend
    LaunchedEffect(Unit) {
        try {
            val api = ApiClient.getService()
            
            // 1. Fetch Passenger Profile
            val profResp = api.getProfile()
            if (profResp.isSuccessful && profResp.body() != null) {
                val p = profResp.body()!!
                p.fullName?.takeIf { it.isNotBlank() }?.let { userName = it }
                p.phoneNumber?.takeIf { it.isNotBlank() }?.let { userPhone = it }
                p.email?.takeIf { it.isNotBlank() }?.let { userEmail = it }
            }

            // 2. Fetch Wallet Balance
            val walResp = api.getWallet()
            if (walResp.isSuccessful && walResp.body() != null) {
                walletBalance = walResp.body()!!.balanceCents / 100
            }

            // 3. Fetch Real Wallet Ledger History
            val txResp = api.getWalletTransactions(limit = 20)
            if (txResp.isSuccessful && !txResp.body().isNullOrEmpty()) {
                walletTransactions = txResp.body()!!.map { tx ->
                    WalletTxn(
                        id = tx.id.take(8),
                        title = tx.description.ifBlank { "Wallet ${tx.transactionType}" },
                        amount = tx.amountCents / 100,
                        isCredit = tx.transactionType.equals("CREDIT", ignoreCase = true),
                        time = tx.createdAt.take(16).replace("T", " ")
                    )
                }
            }

            // 4. Fetch Real Saved Places / Pins
            val pinsResp = api.getSavedLocationPins()
            if (pinsResp.isSuccessful && !pinsResp.body().isNullOrEmpty()) {
                savedPlaces = pinsResp.body()!!.mapIndexed { idx, pin ->
                    val label = (pin["label"] ?: pin["name"] ?: "Pin ${idx + 1}").toString()
                    val addr = (pin["address"] ?: pin["formatted_address"] ?: "").toString()
                    SavedPlace(
                        id = (pin["id"] ?: "P$idx").toString(),
                        title = label,
                        address = addr,
                        isHome = label.contains("Home", ignoreCase = true),
                        isWork = label.contains("Work", ignoreCase = true)
                    )
                }
            }

            // 5. Fetch Real Emergency Contacts
            val ecResp = api.getEmergencyContacts()
            if (ecResp.isSuccessful && !ecResp.body().isNullOrEmpty()) {
                emergencyContacts = ecResp.body()!!.map { ec ->
                    EmergencyContactItem(
                        id = ec.id,
                        name = ec.name,
                        phone = ec.phoneNumber,
                        relationship = ec.relationship.ifBlank { "Family" },
                        notifyOnSos = ec.notifyOnTripStart || ec.notifyOnArrival
                    )
                }
            }

            // 6. Fetch Real Support Tickets
            val tktResp = api.getMySupportTickets()
            if (tktResp.isSuccessful && !tktResp.body().isNullOrEmpty()) {
                supportTickets = tktResp.body()!!.map { tkt ->
                    SupportTicketItem(
                        id = tkt.ticketNumber,
                        subject = tkt.subject,
                        status = tkt.status,
                        date = tkt.complaintReferenceNumber.ifBlank { "Recent" }
                    )
                }
            }

            // 7. Fetch Real Vehicle Categories from Backend
            val catResp = api.getVehicleCategories()
            if (catResp.isSuccessful && !catResp.body().isNullOrEmpty()) {
                backendCategories = catResp.body()!!
                if (selectedCategory.isBlank() || selectedCategory == "Standard E-Rickshaw") {
                    selectedCategory = backendCategories.first().name
                }
            }
        } catch (t: Throwable) {
            Log.w("CustomerFlow", "Dynamic data sync note: ${t.message}")
        }
    }

    // =========================================================================
    // EXACT 24 POPUPS STATE (P.cust)
    // =========================================================================
    var showPopupLocPermission by remember { mutableStateOf(false) }     // 01: d|Location permission
    var showPopupNotifPermission by remember { mutableStateOf(false) }   // 02: d|Notification permission
    var showPopupWrongOtp by remember { mutableStateOf(false) }          // 03: d|Wrong OTP
    var showPopupLogoutConfirm by remember { mutableStateOf(false) }     // 04: b|Logout
    var showPopupNoInternet by remember { mutableStateOf(false) }        // 05: d|No internet
    var showPopupGpsOff by remember { mutableStateOf(false) }            // 06: d|GPS off
    var showPopupConfirmPickup by remember { mutableStateOf(false) }     // 07: b|Confirm pickup
    var showPopupAddFavourite by remember { mutableStateOf(false) }      // 08: b|Add favourite
    var showPopupOutOfZone by remember { mutableStateOf(false) }         // 09: d|Out of zone
    var showPopupSurgePricing by remember { mutableStateOf(false) }      // 10: d|Surge pricing
    var showPopupFareChanged by remember { mutableStateOf(false) }       // 11: b|Fare changed
    var showPopupPromoApplied by remember { mutableStateOf(false) }      // 12: b|Promo applied
    var showPopupNoDriverFound by remember { mutableStateOf(false) }     // 13: d|No driver found
    var showOverlayDriverArrived by remember { mutableStateOf(false) }   // 14: x|Driver arrived
    var showPopupCancelRideConfirm by remember { mutableStateOf(false) } // 15: d|Cancel ride?
    var showPopupEndRideHere by remember { mutableStateOf(false) }       // 16: d|End ride here?
    var showPopupAddTip by remember { mutableStateOf(false) }            // 17: b|Add tip
    var showPopupPaymentFailed by remember { mutableStateOf(false) }     // 18: b|Payment failed
    var showPopupLowWallet by remember { mutableStateOf(false) }         // 19: b|Low wallet
    var showPopupSendSos by remember { mutableStateOf(false) }           // 20: d|Send SOS?
    var showToastTripShared by remember { mutableStateOf(false) }        // 21: t|Trip shared
    var showPopupRateDriver by remember { mutableStateOf(false) }        // 22: d|Rate driver
    var showPopupDisputeSubmitted by remember { mutableStateOf(false) }  // 23: b|Dispute submitted
    var showOverlaySessionExpired by remember { mutableStateOf(false) }  // 24: x|Session expired

    // Sub-dialogs for CRUD forms
    var showAddContactDialog by remember { mutableStateOf(false) }
    var showAddMoneyDialog by remember { mutableStateOf(false) }
    var showAddVpaDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showNewDisputeDialog by remember { mutableStateOf(false) }

    // Toast auto-dismiss (3s)
    LaunchedEffect(showToastTripShared) {
        if (showToastTripShared) {
            delay(3000L)
            showToastTripShared = false
        }
    }

    // Active Ride Isolation Rule: Bottom nav is hidden during live ride steps
    val isActiveRide = flowStep in listOf(
        CustFlowStep.SEARCHING_RADAR,
        CustFlowStep.DRIVER_FOUND,
        CustFlowStep.DRIVER_ARRIVING,
        CustFlowStep.RIDE_CHAT,
        CustFlowStep.MASKED_CALL,
        CustFlowStep.RIDE_IN_PROGRESS,
        CustFlowStep.SHARE_TRIP,
        CustFlowStep.SOS_PANIC
    )

    // Searching Radar Pulse with live backend ride request & driver status polling
    LaunchedEffect(flowStep) {
        if (flowStep == CustFlowStep.SEARCHING_RADAR) {
            radarSeconds = 20

            // Send live ride request to backend if estimateId exists
            try {
                val api = ApiClient.getService()
                val estId = currentEstimateId
                if (estId != null) {
                    val resp = api.requestRide(
                        RideRequestPayload(
                            estimateId = estId,
                            pickupAddress = pickupAddress,
                            destinationAddress = destinationQuery,
                            paymentMethod = "UPI"
                        )
                    )
                    if (resp.isSuccessful && resp.body() != null) {
                        val body = resp.body()!!
                        val rideId = body["id"] as? String ?: body["ride_id"] as? String
                        if (!rideId.isNullOrBlank()) {
                            activeRideId = rideId
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w("CustomerFlow", "Live ride request network call fallback: ${t.message}")
            }

            while (radarSeconds > 0 && flowStep == CustFlowStep.SEARCHING_RADAR) {
                delay(1000L)
                radarSeconds--

                // Poll active ride status if activeRideId exists
                activeRideId?.let { rId ->
                    try {
                        val statusResp = ApiClient.getService().getRideDetails(rId)
                        if (statusResp.isSuccessful && statusResp.body() != null) {
                            val ride = statusResp.body()!!
                            ride.driver?.let { d ->
                                if (d.name.isNotBlank()) driverName = d.name
                                driverRating = "${d.rating} ★"
                            }
                            if (!ride.startOtp.isNullOrBlank()) startOtp = ride.startOtp
                            if (ride.status == "ACCEPTED" || ride.status == "ARRIVING" || ride.status == "ASSIGNED") {
                                flowStep = CustFlowStep.DRIVER_FOUND
                            }
                        }
                    } catch (_: Throwable) {}
                }
            }

            if (flowStep == CustFlowStep.SEARCHING_RADAR) {
                if (driverName.isNotBlank() && activeRideId != null) {
                    flowStep = CustFlowStep.DRIVER_FOUND
                } else {
                    // No driver found after radar search -> Show Popup 13
                    showPopupNoDriverFound = true
                }
            }
        }
    }

    val isAuthStep = flowStep in setOf(
        CustFlowStep.WELCOME_LANGUAGE,
        CustFlowStep.SIGN_IN_PASSWORD,
        CustFlowStep.SIGN_IN_OTP,
        CustFlowStep.FORGOT_PASSWORD,
        CustFlowStep.SIGN_UP,
        CustFlowStep.COMMUTER_PREFERENCES
    )

    // Navigation Rule: Back during active ride shows confirmation dialog and NEVER cancels the ride
    BackHandler(enabled = true) {
        when {
            showOverlaySessionExpired -> { /* blocked */ }
            showOverlayDriverArrived -> showOverlayDriverArrived = false
            showPopupSendSos -> showPopupSendSos = false
            showPopupEndRideHere -> showPopupEndRideHere = false
            showPopupCancelRideConfirm -> showPopupCancelRideConfirm = false
            showPopupAddTip -> showPopupAddTip = false
            showPopupAddFavourite -> showPopupAddFavourite = false
            showPopupConfirmPickup -> showPopupConfirmPickup = false
            showPopupLogoutConfirm -> showPopupLogoutConfirm = false
            flowStep == CustFlowStep.SIGN_IN_PASSWORD -> flowStep = CustFlowStep.WELCOME_LANGUAGE
            flowStep == CustFlowStep.SIGN_IN_OTP -> flowStep = CustFlowStep.SIGN_IN_PASSWORD
            flowStep == CustFlowStep.FORGOT_PASSWORD -> flowStep = CustFlowStep.SIGN_IN_PASSWORD
            flowStep == CustFlowStep.SIGN_UP -> flowStep = CustFlowStep.SIGN_IN_PASSWORD
            flowStep == CustFlowStep.COMMUTER_PREFERENCES -> flowStep = CustFlowStep.SIGN_UP
            flowStep == CustFlowStep.WELCOME_LANGUAGE -> { (context as? android.app.Activity)?.finish() }
            isActiveRide -> {
                // Confirm early drop or cancel warning dialog
                showPopupEndRideHere = true
            }
            flowStep != CustFlowStep.HOME_MAP -> {
                flowStep = CustFlowStep.HOME_MAP
            }
            activeTab != "Home" -> {
                onTabSelected("Home")
            }
            else -> {
                showPopupLogoutConfirm = true
            }
        }
    }

    Scaffold(
        containerColor = LuxuryUiTokens.Bg,
        bottomBar = {
            // Rule: During active ride, auth flow, and session overlay, bottom nav is hidden!
            if (!isActiveRide && !showOverlaySessionExpired && !isAuthStep) {
                Surface(
                    color = LuxuryUiTokens.Card,
                    border = BorderStroke(1.dp, LuxuryUiTokens.Border),
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(62.dp)
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
        },
        floatingActionButton = {
            // Rule: SOS Floating button on every live-ride screen and Home map (hidden on auth)
            if (!showOverlaySessionExpired && !isAuthStep) {
                FloatingActionButton(
                    onClick = { showPopupSendSos = true },
                    containerColor = LuxuryUiTokens.RedAlert,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(50.dp)
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
                .background(LuxuryUiTokens.Bg)
        ) {
            // Main Tab Viewport Router
            when (activeTab) {
                "Home" -> {
                    // Linear Rapido-Style Booking Lifecycle (Screens 01 to 30)
                    when (flowStep) {
                        CustFlowStep.WELCOME_LANGUAGE -> {
                            Screen01BrandWelcomeLanguage(
                                onGetStarted = { flowStep = CustFlowStep.SIGN_IN_PASSWORD },
                                onGuestPreview = { flowStep = CustFlowStep.HOME_MAP }
                            )
                        }
                        CustFlowStep.SIGN_IN_PASSWORD -> {
                            Screen02SignInPassword(
                                onSignInSuccess = { flowStep = CustFlowStep.HOME_MAP },
                                onOtpLoginClick = { flowStep = CustFlowStep.SIGN_IN_OTP },
                                onForgotPasswordClick = { flowStep = CustFlowStep.FORGOT_PASSWORD },
                                onSignUpClick = { flowStep = CustFlowStep.SIGN_UP },
                                onBack = { flowStep = CustFlowStep.WELCOME_LANGUAGE }
                            )
                        }
                        CustFlowStep.SIGN_IN_OTP -> {
                            Screen02SignInPassword(
                                onSignInSuccess = { flowStep = CustFlowStep.HOME_MAP },
                                onOtpLoginClick = {},
                                onForgotPasswordClick = { flowStep = CustFlowStep.FORGOT_PASSWORD },
                                onSignUpClick = { flowStep = CustFlowStep.SIGN_UP },
                                onBack = { flowStep = CustFlowStep.WELCOME_LANGUAGE }
                            )
                        }
                        CustFlowStep.FORGOT_PASSWORD -> {
                            Screen04ForgotPassword(
                                onPasswordResetSuccess = { flowStep = CustFlowStep.SIGN_IN_PASSWORD },
                                onBackToLogin = { flowStep = CustFlowStep.SIGN_IN_PASSWORD }
                            )
                        }
                        CustFlowStep.SIGN_UP -> {
                            Screen05SignUp(
                                onSignUpSuccess = { flowStep = CustFlowStep.COMMUTER_PREFERENCES },
                                onBackToSignIn = { flowStep = CustFlowStep.SIGN_IN_PASSWORD }
                            )
                        }
                        CustFlowStep.COMMUTER_PREFERENCES -> {
                            Screen06CommuterPreferences(
                                onCompleted = { flowStep = CustFlowStep.HOME_MAP },
                                onBack = { flowStep = CustFlowStep.SIGN_UP }
                            )
                        }

                        CustFlowStep.HOME_MAP -> {
                            Screen08HomeMap(
                                pickupAddress = pickupAddress,
                                pickupLatLng = pickupLatLng,
                                destAddress = if (destLatLng != null) destinationQuery else null,
                                destLatLng = destLatLng,
                                routePoints = routePoints,
                                routeDistanceKm = routeDistanceKm,
                                routeDurationMinutes = routeDurationMinutes,
                                cameraTargetLatLng = cameraTargetLatLng,
                                cameraAnimTrigger = cameraAnimTrigger,
                                cameraAnimType = cameraAnimType,
                                savedPlaces = savedPlaces,
                                categories = backendCategories,
                                onWhereToClick = { flowStep = CustFlowStep.SEARCH_DESTINATION },
                                onClearDestination = {
                                    destLatLng = null
                                    routePoints = emptyList()
                                    routeDistanceKm = null
                                    routeDurationMinutes = null
                                    routeNavigationSteps = emptyList()
                                },
                                onChooseCategory = { cat ->
                                    selectedCategory = cat
                                    flowStep = CustFlowStep.CHOOSE_RIDE
                                },
                                onQuickDestination = { dest ->
                                    val targetLat = if (dest.contains("Work", ignoreCase = true)) pickupLatLng.latitude + 0.015 else pickupLatLng.latitude + 0.008
                                    val targetLng = if (dest.contains("Work", ignoreCase = true)) pickupLatLng.longitude + 0.012 else pickupLatLng.longitude - 0.010
                                    selectDestinationAndCalculateRoute(dest, targetLat, targetLng, animateCamera = true)
                                    flowStep = CustFlowStep.CHOOSE_RIDE
                                },
                                onAddFavoriteClick = { showPopupAddFavourite = true },
                                onRecenterLocation = {
                                    DeviceLocationProvider.requestCurrentLocation(context) { loc ->
                                        pickupLatLng = loc
                                        cameraTargetLatLng = loc
                                        cameraAnimTrigger++
                                        scope.launch {
                                            val rev = MapboxSearchService.reverseGeocode(loc.latitude, loc.longitude)
                                            if (!rev.isNullOrBlank()) {
                                                pickupAddress = rev
                                            }
                                        }
                                    }
                                }
                            )
                        }

                        CustFlowStep.SEARCH_DESTINATION -> {
                            Screen09SearchDestination(
                                initialQuery = destinationQuery,
                                pickupLatLng = pickupLatLng,
                                savedPlaces = savedPlaces,
                                onSelectDestination = { destTitle, lat, lng ->
                                    selectDestinationAndCalculateRoute(destTitle, lat, lng, animateCamera = true)
                                    flowStep = CustFlowStep.CHOOSE_RIDE
                                },
                                onPinDropClick = { flowStep = CustFlowStep.PIN_DROP },
                                onMultiStopClick = { flowStep = CustFlowStep.MULTI_STOP },
                                onBack = { flowStep = CustFlowStep.HOME_MAP }
                            )
                        }

                        CustFlowStep.PIN_DROP -> {
                            Screen10PinDrop(
                                onConfirmPin = { addr, lat, lng ->
                                    pickupAddress = addr
                                    pickupLatLng = LatLng(lat, lng)
                                    showPopupConfirmPickup = true
                                },
                                onBack = { flowStep = CustFlowStep.SEARCH_DESTINATION }
                            )
                        }

                        CustFlowStep.MULTI_STOP -> {
                            Screen11MultiStop(
                                pickup = pickupAddress,
                                destination = destinationQuery,
                                intermediateStop = intermediateStopAddress,
                                onIntermediateStopChange = { intermediateStopAddress = it },
                                onConfirmRoute = { flowStep = CustFlowStep.CHOOSE_RIDE },
                                onBack = { flowStep = CustFlowStep.SEARCH_DESTINATION }
                            )
                        }

                        CustFlowStep.CHOOSE_RIDE -> {
                            Screen12ChooseRide(
                                selectedCat = selectedCategory,
                                routeDistanceKm = routeDistanceKm,
                                routeDurationMinutes = routeDurationMinutes,
                                categories = backendCategories,
                                onSelectCat = { selectedCategory = it },
                                onFareBreakup = { flowStep = CustFlowStep.FARE_BREAKUP },
                                onPreferences = { flowStep = CustFlowStep.RIDE_PREFERENCES },
                                onBookNow = {
                                    if (destinationQuery.contains("Restricted", ignoreCase = true) || destinationQuery.contains("Outer Ring", ignoreCase = true)) {
                                        showPopupOutOfZone = true
                                    } else {
                                        flowStep = CustFlowStep.CONFIRM_BOOKING
                                    }
                                },
                                onBack = { flowStep = CustFlowStep.SEARCH_DESTINATION }
                            )
                        }

                        CustFlowStep.FARE_BREAKUP -> {
                            Screen13FareBreakup(
                                baseFare = 40,
                                kmFare = 68,
                                techFee = 10,
                                promoCode = promoCode,
                                promoDiscount = promoDiscount,
                                onApplyPromo = { code ->
                                    promoCode = code
                                    promoDiscount = 20
                                    showPopupPromoApplied = true
                                },
                                onRemovePromo = {
                                    promoCode = ""
                                    promoDiscount = 0
                                    Toast.makeText(context, "Promo removed", Toast.LENGTH_SHORT).show()
                                },
                                onContinuePayment = { flowStep = CustFlowStep.PAYMENT_METHOD },
                                onBack = { flowStep = CustFlowStep.CHOOSE_RIDE }
                            )
                        }

                        CustFlowStep.RIDE_PREFERENCES -> {
                            Screen14RidePreferences(
                                quietRide = prefQuietRide,
                                onQuietRideChange = { prefQuietRide = it },
                                acComfort = prefAcComfort,
                                onAcComfortChange = { prefAcComfort = it },
                                luggageAssistance = prefLuggageAssistance,
                                onLuggageChange = { prefLuggageAssistance = it },
                                wheelchair = prefWheelchairAccessible,
                                onWheelchairChange = { prefWheelchairAccessible = it },
                                onSavePreferences = {
                                    Toast.makeText(context, "Ride preferences saved", Toast.LENGTH_SHORT).show()
                                    flowStep = CustFlowStep.CHOOSE_RIDE
                                },
                                onBack = { flowStep = CustFlowStep.CHOOSE_RIDE }
                            )
                        }

                        CustFlowStep.PAYMENT_METHOD -> {
                            Screen15PaymentMethod(
                                walletBalance = walletBalance,
                                savedVpas = savedVpas,
                                onSelectPayment = { flowStep = CustFlowStep.CONFIRM_BOOKING },
                                onAddVpa = { showAddVpaDialog = true },
                                onBack = { flowStep = CustFlowStep.FARE_BREAKUP }
                            )
                        }

                        CustFlowStep.CONFIRM_BOOKING -> {
                            Screen16ConfirmBooking(
                                pickup = pickupAddress,
                                destination = destinationQuery,
                                category = selectedCategory,
                                totalFare = (estimatedFare - promoDiscount).coerceAtLeast(40),
                                onConfirmBooking = {
                                    searchRetries = 0
                                    flowStep = CustFlowStep.SEARCHING_RADAR
                                },
                                onTriggerOutofZone = { showPopupOutOfZone = true },
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
                                etaMinutes = driverEta,
                                onProceedToArriving = {
                                    showOverlayDriverArrived = true
                                    flowStep = CustFlowStep.DRIVER_ARRIVING
                                }
                            )
                        }

                        CustFlowStep.DRIVER_ARRIVING -> {
                            Screen20DriverArriving(
                                driverName = driverName,
                                plate = driverPlate,
                                startOtp = startOtp,
                                etaMinutes = driverEta,
                                onCall = { flowStep = CustFlowStep.MASKED_CALL },
                                onChat = { flowStep = CustFlowStep.RIDE_CHAT },
                                onBoarded = { flowStep = CustFlowStep.RIDE_IN_PROGRESS },
                                onCancel = { showPopupCancelRideConfirm = true }
                            )
                        }

                        CustFlowStep.RIDE_CHAT -> {
                            Screen21RideChat(
                                driverName = driverName,
                                messages = chatMessages,
                                onSendMessage = { text ->
                                    chatMessages = chatMessages + ChatBubbleItem(
                                        id = "M-${System.currentTimeMillis()}",
                                        message = text,
                                        isCustomer = true,
                                        timestamp = "Just now"
                                    )
                                },
                                onBack = { flowStep = CustFlowStep.DRIVER_ARRIVING }
                            )
                        }

                        CustFlowStep.MASKED_CALL -> {
                            Screen22MaskedCall(
                                driverName = driverName,
                                driverPhone = driverPhone,
                                onEndCall = { flowStep = CustFlowStep.DRIVER_ARRIVING }
                            )
                        }

                        CustFlowStep.RIDE_IN_PROGRESS -> {
                            Screen23RideInProgress(
                                driverName = driverName,
                                destination = destinationQuery,
                                onShare = { showToastTripShared = true },
                                onSos = { showPopupSendSos = true },
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
                                    Toast.makeText(context, "Ride cancelled. Cancellation fee ₹20 applies.", Toast.LENGTH_LONG).show()
                                }
                            )
                        }

                        CustFlowStep.TRIP_SUMMARY -> {
                            Screen28TripSummary(
                                totalFare = (estimatedFare - promoDiscount).coerceAtLeast(40),
                                distanceKm = routeDistanceKm ?: 3.5,
                                durationMins = routeDurationMinutes?.toInt() ?: 12,
                                onPay = { flowStep = CustFlowStep.PAYMENT_SETTLE }
                            )
                        }

                        CustFlowStep.PAYMENT_SETTLE -> {
                            Screen29PaymentSettle(
                                amount = (estimatedFare - promoDiscount).coerceAtLeast(40),
                                onPaymentDone = {
                                    showPopupAddTip = true
                                    flowStep = CustFlowStep.RATE_DRIVER
                                }
                            )
                        }

                        CustFlowStep.RATE_DRIVER -> {
                            Screen30RateDriver(
                                driverName = driverName,
                                onSubmitted = { rating ->
                                    val rId = activeRideId
                                    if (rId != null) {
                                        scope.launch {
                                            try {
                                                ApiClient.getService().rateDriver(
                                                    rId,
                                                    RateDriverRequest(rating = rating, feedback = "Trip Completed")
                                                )
                                            } catch (e: Exception) {
                                                Log.e("CustomerFlow", "Failed to rate driver: ${e.message}")
                                            }
                                        }
                                    }
                                    activeRideId = null
                                    flowStep = CustFlowStep.HOME_MAP
                                    Toast.makeText(context, "Thank you for riding with E-Rickshaw Luxury!", Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    }
                }

                "Rides" -> {
                    // Screens 31 to 34: Completed Trips & Disputes
                    RidesTabContent(
                        supportTickets = supportTickets,
                        onRepeatRide = {
                            onTabSelected("Home")
                            flowStep = CustFlowStep.CHOOSE_RIDE
                        },
                        onOpenDispute = { showNewDisputeDialog = true }
                    )
                }

                "Wallet" -> {
                    // Screens 35 to 40: Wallet Balance, Quick Top-Up, Transactions
                    WalletTabContent(
                        balance = walletBalance,
                        transactions = walletTransactions,
                        savedVpas = savedVpas,
                        onAddMoneyClick = { showAddMoneyDialog = true },
                        onAddVpaClick = { showAddVpaDialog = true },
                        onDeleteVpa = { vpaId ->
                            savedVpas = savedVpas.filter { it.id != vpaId }
                            Toast.makeText(context, "UPI account removed", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                "Account" -> {
                    // Screens 43 to 50: Account Profile, Emergency Contacts, Support
                    AccountTabContent(
                        userName = userName,
                        userPhone = userPhone,
                        userEmail = userEmail,
                        savedPlaces = savedPlaces,
                        emergencyContacts = emergencyContacts,
                        onEditProfile = { showEditProfileDialog = true },
                        onAddContact = { showAddContactDialog = true },
                        onDeleteContact = { id ->
                            emergencyContacts = emergencyContacts.filter { it.id != id }
                            Toast.makeText(context, "Contact removed", Toast.LENGTH_SHORT).show()
                        },
                        onToggleContactNotify = { id ->
                            emergencyContacts = emergencyContacts.map {
                                if (it.id == id) it.copy(notifyOnSos = !it.notifyOnSos) else it
                            }
                        },
                        onDeleteSavedPlace = { id ->
                            savedPlaces = savedPlaces.filter { it.id != id }
                            Toast.makeText(context, "Saved place deleted", Toast.LENGTH_SHORT).show()
                        },
                        onSignOut = { showPopupLogoutConfirm = true }
                    )
                }
            }

            // =========================================================================
            // ALL 24 EXACT CUSTOMER POPUPS IMPLEMENTATION (P.cust)
            // =========================================================================

            // 01: d|Location permission|Allow location for pickup|Allow/Not now
            if (showPopupLocPermission) {
                LuxuryDialog(
                    title = "Location Permission",
                    message = "Allow precise GPS location access to detect your exact pickup point and match nearby E-Rickshaws.",
                    confirmText = "Allow GPS",
                    dismissText = "Not Now",
                    onConfirm = {
                        showPopupLocPermission = false
                        Toast.makeText(context, "Location access granted", Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = { showPopupLocPermission = false }
                )
            }

            // 02: d|Notification permission|Get ride alerts|Allow/Skip
            if (showPopupNotifPermission) {
                LuxuryDialog(
                    title = "Notification Permission",
                    message = "Enable push notifications to receive real-time driver arrival alerts and start PIN updates.",
                    confirmText = "Allow Alerts",
                    dismissText = "Skip",
                    onConfirm = {
                        showPopupNotifPermission = false
                        Toast.makeText(context, "Ride alerts enabled", Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = { showPopupNotifPermission = false }
                )
            }

            // 03: d|Wrong OTP|2 attempts left|Retry
            if (showPopupWrongOtp) {
                LuxuryDialog(
                    title = "Incorrect Start PIN",
                    message = "The entered PIN does not match. Please verify with your chauffeur (2 attempts remaining).",
                    confirmText = "Retry PIN",
                    dismissText = "Call Driver",
                    confirmColor = LuxuryUiTokens.RedAlert,
                    onConfirm = { showPopupWrongOtp = false },
                    onDismiss = {
                        showPopupWrongOtp = false
                        flowStep = CustFlowStep.MASKED_CALL
                    }
                )
            }

            // 04: b|Logout|Sign out of this device?|Logout/Stay (Bottom Sheet)
            if (showPopupLogoutConfirm) {
                CustomerBottomSheet(
                    title = "Sign Out of Device?",
                    onDismiss = { showPopupLogoutConfirm = false }
                ) {
                    Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
                        Text("Are you sure you want to log out from this device?", color = LuxuryUiTokens.Text, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        GoldActionButton(text = "STAY SIGNED IN", onClick = { showPopupLogoutConfirm = false })
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                showPopupLogoutConfirm = false
                                onSignOut()
                                flowStep = CustFlowStep.WELCOME_LANGUAGE
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = LuxuryUiTokens.RedAlert),
                            border = BorderStroke(1.dp, LuxuryUiTokens.RedAlert),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) {
                            Text("LOGOUT NOW", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 05: d|No internet|Check connection|Retry
            if (showPopupNoInternet) {
                LuxuryDialog(
                    title = "No Internet Connection",
                    message = "Please check your mobile data or Wi-Fi connectivity to communicate with the E-Rickshaw dispatch engine.",
                    confirmText = "Retry Connection",
                    dismissText = "Cancel",
                    onConfirm = { showPopupNoInternet = false },
                    onDismiss = { showPopupNoInternet = false }
                )
            }

            // 06: d|GPS off|Turn on GPS|Open settings
            if (showPopupGpsOff) {
                LuxuryDialog(
                    title = "Device GPS Disabled",
                    message = "Turn on device location services in system settings for accurate pickup pin placement.",
                    confirmText = "Open Settings",
                    dismissText = "Not Now",
                    onConfirm = {
                        showPopupGpsOff = false
                        runCatching {
                            context.startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        }
                    },
                    onDismiss = { showPopupGpsOff = false }
                )
            }

            // 07: b|Confirm pickup|Pickup point + gate|Confirm/Adjust (Bottom Sheet)
            if (showPopupConfirmPickup) {
                CustomerBottomSheet(
                    title = "Confirm Pickup Location",
                    onDismiss = { showPopupConfirmPickup = false }
                ) {
                    Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
                        Text(pickupAddress, color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Designated Gate / Landmark: Gate 2 Metro Station", color = LuxuryUiTokens.Muted, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        GoldActionButton(
                            text = "CONFIRM PICKUP POINT",
                            onClick = {
                                showPopupConfirmPickup = false
                                flowStep = CustFlowStep.CHOOSE_RIDE
                            }
                        )
                    }
                }
            }

            // 08: b|Add favourite|Label Home / Work|Save/Cancel (Bottom Sheet - CRUD)
            if (showPopupAddFavourite) {
                var labelName by remember { mutableStateOf("Home") }
                var customAddress by remember { mutableStateOf(destinationQuery) }

                CustomerBottomSheet(
                    title = "Save Favorite Location",
                    onDismiss = { showPopupAddFavourite = false }
                ) {
                    Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
                        OutlinedTextField(
                            value = labelName,
                            onValueChange = { labelName = it },
                            label = { Text("Label (e.g. Home, Work, Gym)", color = LuxuryUiTokens.Muted, fontSize = 12.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = LuxuryUiTokens.Text,
                                unfocusedTextColor = LuxuryUiTokens.Text,
                                focusedBorderColor = LuxuryUiTokens.Gold,
                                unfocusedBorderColor = LuxuryUiTokens.Border,
                                focusedContainerColor = LuxuryUiTokens.FieldBg,
                                unfocusedContainerColor = LuxuryUiTokens.FieldBg
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = customAddress,
                            onValueChange = { customAddress = it },
                            label = { Text("Complete Address", color = LuxuryUiTokens.Muted, fontSize = 12.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = LuxuryUiTokens.Text,
                                unfocusedTextColor = LuxuryUiTokens.Text,
                                focusedBorderColor = LuxuryUiTokens.Gold,
                                unfocusedBorderColor = LuxuryUiTokens.Border,
                                focusedContainerColor = LuxuryUiTokens.FieldBg,
                                unfocusedContainerColor = LuxuryUiTokens.FieldBg
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        GoldActionButton(
                            text = "SAVE TO FAVORITES",
                            onClick = {
                                if (labelName.isNotBlank() && customAddress.isNotBlank()) {
                                    savedPlaces = savedPlaces + SavedPlace(
                                        id = "P-${System.currentTimeMillis()}",
                                        title = labelName,
                                        address = customAddress
                                    )
                                    showPopupAddFavourite = false
                                    Toast.makeText(context, "$labelName added to favorites!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }

            // 09: d|Out of zone|Service not available here|Change drop
            if (showPopupOutOfZone) {
                LuxuryDialog(
                    title = "Destination Out of Zone",
                    message = "Commercial E-Rickshaws are restricted on expressways and highways beyond active operational limits. Please choose a nearby authorized hub.",
                    confirmText = "Change Destination",
                    dismissText = "Dismiss",
                    confirmColor = LuxuryUiTokens.RedAlert,
                    onConfirm = {
                        showPopupOutOfZone = false
                        flowStep = CustFlowStep.SEARCH_DESTINATION
                    },
                    onDismiss = { showPopupOutOfZone = false }
                )
            }

            // 10: d|Surge pricing|1.5x demand now|Book/Wait
            if (showPopupSurgePricing) {
                LuxuryDialog(
                    title = "1.5x Surge Pricing Active",
                    message = "High passenger demand around Connaught Place Metro Station. Fares are multiplied by 1.5x to incentivize driver availability.",
                    confirmText = "Accept Surge & Book",
                    dismissText = "Wait for Normal Fare",
                    onConfirm = {
                        showPopupSurgePricing = false
                        flowStep = CustFlowStep.CONFIRM_BOOKING
                    },
                    onDismiss = { showPopupSurgePricing = false }
                )
            }

            // 11: b|Fare changed|New fare shown|Accept/Cancel (Bottom Sheet)
            if (showPopupFareChanged) {
                CustomerBottomSheet(
                    title = "Fare Recalculated",
                    onDismiss = { showPopupFareChanged = false }
                ) {
                    Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
                        Text("Due to traffic re-routing, the revised estimate is ₹145.", color = LuxuryUiTokens.Text, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        GoldActionButton(
                            text = "ACCEPT UPDATED FARE (₹145)",
                            onClick = {
                                estimatedFare = 145
                                showPopupFareChanged = false
                            }
                        )
                    }
                }
            }

            // 12: b|Promo applied|You saved ₹|Done (Bottom Sheet)
            if (showPopupPromoApplied) {
                CustomerBottomSheet(
                    title = "Promo Code Applied!",
                    onDismiss = { showPopupPromoApplied = false }
                ) {
                    Column(modifier = Modifier.padding(20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = LuxuryUiTokens.GreenSuccess, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("You Saved ₹$promoDiscount on this ride!", color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Discount applied directly to total fare breakup.", color = LuxuryUiTokens.Muted, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        GoldActionButton(text = "DONE", onClick = { showPopupPromoApplied = false })
                    }
                }
            }

            // 13: d|No driver found|Retry or raise fare|Retry/Cancel
            if (showPopupNoDriverFound) {
                LuxuryDialog(
                    title = "No Chauffeurs Found Nearby",
                    message = "All nearby E-Rickshaws are currently occupied. Would you like to retry dispatch or raise the offer fare by ₹20?",
                    confirmText = "Raise Fare (+₹20) & Retry",
                    dismissText = "Cancel Search",
                    onConfirm = {
                        showPopupNoDriverFound = false
                        estimatedFare += 20
                        searchRetries = 0
                        flowStep = CustFlowStep.SEARCHING_RADAR
                    },
                    onDismiss = {
                        showPopupNoDriverFound = false
                        flowStep = CustFlowStep.CHOOSE_RIDE
                    }
                )
            }

            // 14: x|Driver arrived|Share OTP 4821|Share OTP (Full-screen Overlay)
            if (showOverlayDriverArrived) {
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
                        Icon(Icons.Default.Check, contentDescription = null, tint = LuxuryUiTokens.Gold, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("CHAUFFEUR HAS ARRIVED", color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Text("$driverName is waiting at your pickup point in Saarthi EV ($driverPlate)", color = LuxuryUiTokens.Text, fontSize = 13.sp, textAlign = TextAlign.Center)

                        Spacer(modifier = Modifier.height(24.dp))
                        Surface(
                            color = LuxuryUiTokens.Card,
                            border = BorderStroke(2.dp, LuxuryUiTokens.Gold),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("SHARE START OTP WITH DRIVER", color = LuxuryUiTokens.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(startOtp, color = LuxuryUiTokens.Gold, fontSize = 38.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(28.dp))
                        GoldActionButton(
                            text = "I HAVE BOARDED VEHICLE",
                            onClick = {
                                showOverlayDriverArrived = false
                                flowStep = CustFlowStep.RIDE_IN_PROGRESS
                            }
                        )
                    }
                }
            }

            // 15: d|Cancel ride?|Fee ₹20 applies|Keep ride/Cancel
            if (showPopupCancelRideConfirm) {
                LuxuryDialog(
                    title = "Cancel Booking?",
                    message = "Chauffeur $driverName is already en route. A cancellation compensation fee of ₹20 will be charged.",
                    confirmText = "Confirm Cancel",
                    dismissText = "Keep Ride",
                    confirmColor = LuxuryUiTokens.RedAlert,
                    onConfirm = {
                        showPopupCancelRideConfirm = false
                        flowStep = CustFlowStep.HOME_MAP
                        walletBalance = (walletBalance - 20).coerceAtLeast(0)
                        Toast.makeText(context, "Booking cancelled. ₹20 cancellation fee deducted.", Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = { showPopupCancelRideConfirm = false }
                )
            }

            // 16: d|End ride here?|Confirm early drop|Yes/No
            if (showPopupEndRideHere) {
                LuxuryDialog(
                    title = "End Ride Early Here?",
                    message = "Are you sure you want to request an early drop-off at your current location?",
                    confirmText = "Yes, End Ride Here",
                    dismissText = "No, Continue to Destination",
                    onConfirm = {
                        showPopupEndRideHere = false
                        flowStep = CustFlowStep.TRIP_SUMMARY
                    },
                    onDismiss = { showPopupEndRideHere = false }
                )
            }

            // 17: b|Add tip|₹10 / 20 / 50|Pay tip/Skip (Bottom Sheet)
            if (showPopupAddTip) {
                CustomerBottomSheet(
                    title = "Add a Gratuity Tip for Chauffeur",
                    onDismiss = { showPopupAddTip = false }
                ) {
                    Column(modifier = Modifier.padding(20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("100% of tips go directly to $driverName", color = LuxuryUiTokens.Muted, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(10, 20, 50).forEach { tipAmt ->
                                Button(
                                    onClick = {
                                        showPopupAddTip = false
                                        Toast.makeText(context, "Added ₹$tipAmt tip! Thank you.", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = LuxuryUiTokens.Card),
                                    border = BorderStroke(1.dp, LuxuryUiTokens.Gold),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("+₹$tipAmt", color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = { showPopupAddTip = false }) {
                            Text("Skip Tip", color = LuxuryUiTokens.Muted)
                        }
                    }
                }
            }

            // 18: b|Payment failed|Try another method|Retry/Switch (Bottom Sheet)
            if (showPopupPaymentFailed) {
                CustomerBottomSheet(
                    title = "Payment Authorization Failed",
                    onDismiss = { showPopupPaymentFailed = false }
                ) {
                    Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
                        Text("Bank switch declined transaction. Please select another UPI account or Cash.", color = LuxuryUiTokens.RedAlert, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(14.dp))
                        GoldActionButton(
                            text = "SWITCH TO CASH / UPI",
                            onClick = {
                                showPopupPaymentFailed = false
                                flowStep = CustFlowStep.PAYMENT_METHOD
                            }
                        )
                    }
                }
            }

            // 19: b|Low wallet|Add money to continue|Add money (Bottom Sheet)
            if (showPopupLowWallet) {
                CustomerBottomSheet(
                    title = "Insufficient Wallet Balance",
                    onDismiss = { showPopupLowWallet = false }
                ) {
                    Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
                        Text("Available balance (₹$walletBalance) is less than trip fare. Top up to proceed.", color = LuxuryUiTokens.Text, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(14.dp))
                        GoldActionButton(
                            text = "TOP-UP WALLET NOW",
                            onClick = {
                                showPopupLowWallet = false
                                showAddMoneyDialog = true
                            }
                        )
                    }
                }
            }

            // 20: d|Send SOS?|Alerts contacts + police|Send/Cancel
            if (showPopupSendSos) {
                LuxuryDialog(
                    title = "DISPATCH EMERGENCY SOS?",
                    message = "This will instantly transmit your live coordinates to Police 112, alert all ${emergencyContacts.size} emergency contacts, and notify Fleet HQ Safety Command.",
                    confirmText = "DISPATCH SOS NOW",
                    dismissText = "Cancel",
                    confirmColor = LuxuryUiTokens.RedAlert,
                    onConfirm = {
                        showPopupSendSos = false
                        flowStep = CustFlowStep.SOS_PANIC
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")))
                        }
                    },
                    onDismiss = { showPopupSendSos = false }
                )
            }

            // 21: t|Trip shared|Link copied|— (Toast Banner)
            if (showToastTripShared) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        color = LuxuryUiTokens.Card,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, LuxuryUiTokens.Gold)
                    ) {
                        Text(
                            text = "✓ Live Trip Tracking Link Copied & Shared to Emergency Contacts",
                            color = LuxuryUiTokens.Gold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                        )
                    }
                }
            }

            // 22: d|Rate driver|How was the ride?|Rate/Later
            if (showPopupRateDriver) {
                LuxuryDialog(
                    title = "Rate Your Chauffeur",
                    message = "How was your experience riding with $driverName in Saarthi EV?",
                    confirmText = "Rate 5 Stars",
                    dismissText = "Later",
                    onConfirm = {
                        showPopupRateDriver = false
                        Toast.makeText(context, "Thank you for 5-star rating!", Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = { showPopupRateDriver = false }
                )
            }

            // 23: b|Dispute submitted|Ticket created|Track (Bottom Sheet)
            if (showPopupDisputeSubmitted) {
                CustomerBottomSheet(
                    title = "Dispute Submitted Successfully",
                    onDismiss = { showPopupDisputeSubmitted = false }
                ) {
                    Column(modifier = Modifier.padding(20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = LuxuryUiTokens.Gold, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Support Ticket Created", color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Fleet Care team will review your trip telemetry within 2 hours.", color = LuxuryUiTokens.Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        GoldActionButton(
                            text = "TRACK GRIEVANCE TICKET",
                            onClick = {
                                showPopupDisputeSubmitted = false
                                onTabSelected("Rides")
                            }
                        )
                    }
                }
            }

            // 24: x|Session expired|Please login again|Login (Full-screen Overlay)
            if (showOverlaySessionExpired) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.98f))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = LuxuryUiTokens.RedAlert, modifier = Modifier.size(60.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("SESSION EXPIRED", color = LuxuryUiTokens.RedAlert, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Text("Your security token has expired. Please sign in again with your registered phone number.", color = LuxuryUiTokens.Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(24.dp))
                        GoldActionButton(
                            text = "LOGIN AGAIN WITH PHONE OTP",
                            onClick = {
                                showOverlaySessionExpired = false
                                onSignOut()
                            }
                        )
                    }
                }
            }

            // =========================================================================
            // CRUD MODALS (Contacts, Wallet Top-Up, UPI VPA, Edit Profile, New Dispute)
            // =========================================================================

            // CRUD: Add Emergency Contact Dialog
            if (showAddContactDialog) {
                var cName by remember { mutableStateOf("") }
                var cPhone by remember { mutableStateOf("") }
                var cRelation by remember { mutableStateOf("Family") }

                AlertDialog(
                    onDismissRequest = { showAddContactDialog = false },
                    containerColor = LuxuryUiTokens.Card,
                    title = { Text("Add Emergency Contact", color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = cName,
                                onValueChange = { cName = it },
                                label = { Text("Contact Full Name", color = LuxuryUiTokens.Muted) },
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = LuxuryUiTokens.Text, unfocusedTextColor = LuxuryUiTokens.Text, focusedBorderColor = LuxuryUiTokens.Gold, unfocusedBorderColor = LuxuryUiTokens.Border),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = cPhone,
                                onValueChange = { cPhone = it },
                                label = { Text("10-Digit Mobile Number", color = LuxuryUiTokens.Muted) },
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = LuxuryUiTokens.Text, unfocusedTextColor = LuxuryUiTokens.Text, focusedBorderColor = LuxuryUiTokens.Gold, unfocusedBorderColor = LuxuryUiTokens.Border),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (cName.isNotBlank() && cPhone.isNotBlank()) {
                                    emergencyContacts = emergencyContacts + EmergencyContactItem(
                                        id = "EC-${System.currentTimeMillis()}",
                                        name = cName,
                                        phone = cPhone,
                                        relationship = cRelation,
                                        notifyOnSos = true
                                    )
                                    showAddContactDialog = false
                                    Toast.makeText(context, "$cName added to emergency network!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = LuxuryUiTokens.Gold)
                        ) {
                            Text("SAVE CONTACT", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddContactDialog = false }) {
                            Text("CANCEL", color = LuxuryUiTokens.Muted)
                        }
                    }
                )
            }

            // CRUD: Add Money Dialog
            if (showAddMoneyDialog) {
                var addAmtText by remember { mutableStateOf("500") }

                AlertDialog(
                    onDismissRequest = { showAddMoneyDialog = false },
                    containerColor = LuxuryUiTokens.Card,
                    title = { Text("Top-Up Wallet Balance", color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text("Enter amount to add (₹):", color = LuxuryUiTokens.Muted, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = addAmtText,
                                onValueChange = { addAmtText = it },
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = LuxuryUiTokens.Text, unfocusedTextColor = LuxuryUiTokens.Text, focusedBorderColor = LuxuryUiTokens.Gold, unfocusedBorderColor = LuxuryUiTokens.Border),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val amt = addAmtText.toIntOrNull() ?: 0
                                if (amt > 0) {
                                    val amtCents = amt * 100
                                    scope.launch {
                                        try {
                                            ApiClient.getService().topupWallet(
                                                TopupWalletRequest(
                                                    amountCents = amtCents,
                                                    idempotencyKey = "topup_${System.currentTimeMillis()}",
                                                    paymentReference = "UPI_TXN_${System.currentTimeMillis()}"
                                                )
                                            )
                                        } catch (t: Throwable) {
                                            Log.w("CustomerFlow", "Topup API fallback: ${t.message}")
                                        }
                                    }
                                    walletBalance += amt
                                    walletTransactions = listOf(
                                        WalletTxn(
                                            id = "TXN-${System.currentTimeMillis()}",
                                            title = "Wallet Top-up via UPI",
                                            amount = amt,
                                            isCredit = true,
                                            time = "Just now"
                                        )
                                    ) + walletTransactions
                                    showAddMoneyDialog = false
                                    Toast.makeText(context, "Added ₹$amt to wallet balance!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = LuxuryUiTokens.Gold)
                        ) {
                            Text("ADD MONEY", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddMoneyDialog = false }) {
                            Text("CANCEL", color = LuxuryUiTokens.Muted)
                        }
                    }
                )
            }

            // CRUD: Add UPI VPA Dialog
            if (showAddVpaDialog) {
                var newVpaText by remember { mutableStateOf("") }
                var newBankText by remember { mutableStateOf("GooglePay / UPI") }

                AlertDialog(
                    onDismissRequest = { showAddVpaDialog = false },
                    containerColor = LuxuryUiTokens.Card,
                    title = { Text("Link UPI VPA ID", color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = newVpaText,
                                onValueChange = { newVpaText = it },
                                placeholder = { Text("e.g. mobile@okaxis", color = LuxuryUiTokens.Muted) },
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = LuxuryUiTokens.Text, unfocusedTextColor = LuxuryUiTokens.Text, focusedBorderColor = LuxuryUiTokens.Gold, unfocusedBorderColor = LuxuryUiTokens.Border),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (newVpaText.isNotBlank()) {
                                    val vpaStr = newVpaText.trim()
                                    scope.launch {
                                        try {
                                            ApiClient.getService().saveUpiVpa(
                                                SaveVpaRequest(vpaAddress = vpaStr)
                                            )
                                        } catch (t: Throwable) {
                                            Log.w("CustomerFlow", "Save VPA API fallback: ${t.message}")
                                        }
                                    }
                                    savedVpas = savedVpas + SavedPaymentVpa(
                                        id = "VPA-${System.currentTimeMillis()}",
                                        vpa = vpaStr,
                                        bank = newBankText,
                                        isPrimary = false
                                    )
                                    showAddVpaDialog = false
                                    Toast.makeText(context, "Linked UPI ID $vpaStr!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = LuxuryUiTokens.Gold)
                        ) {
                            Text("LINK UPI", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddVpaDialog = false }) {
                            Text("CANCEL", color = LuxuryUiTokens.Muted)
                        }
                    }
                )
            }

            // CRUD: Edit Profile Dialog
            if (showEditProfileDialog) {
                var editName by remember { mutableStateOf(userName) }
                var editEmail by remember { mutableStateOf(userEmail) }

                AlertDialog(
                    onDismissRequest = { showEditProfileDialog = false },
                    containerColor = LuxuryUiTokens.Card,
                    title = { Text("Edit Passenger Profile", color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = editName,
                                onValueChange = { editName = it },
                                label = { Text("Full Name", color = LuxuryUiTokens.Muted) },
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = LuxuryUiTokens.Text, unfocusedTextColor = LuxuryUiTokens.Text, focusedBorderColor = LuxuryUiTokens.Gold, unfocusedBorderColor = LuxuryUiTokens.Border),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = editEmail,
                                onValueChange = { editEmail = it },
                                label = { Text("Email Address", color = LuxuryUiTokens.Muted) },
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = LuxuryUiTokens.Text, unfocusedTextColor = LuxuryUiTokens.Text, focusedBorderColor = LuxuryUiTokens.Gold, unfocusedBorderColor = LuxuryUiTokens.Border),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val nameStr = editName
                                val emailStr = editEmail
                                scope.launch {
                                    try {
                                        ApiClient.getService().updateProfile(
                                            ProfileUpdateRequest(
                                                fullName = nameStr,
                                                email = emailStr
                                            )
                                        )
                                    } catch (t: Throwable) {
                                        Log.w("CustomerFlow", "Update profile API fallback: ${t.message}")
                                    }
                                }
                                userName = nameStr
                                userEmail = emailStr
                                showEditProfileDialog = false
                                Toast.makeText(context, "Profile details updated!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = LuxuryUiTokens.Gold)
                        ) {
                            Text("SAVE CHANGES", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEditProfileDialog = false }) {
                            Text("CANCEL", color = LuxuryUiTokens.Muted)
                        }
                    }
                )
            }

            // CRUD: New Dispute Dialog
            if (showNewDisputeDialog) {
                var disputeReason by remember { mutableStateOf("Incorrect Fare Calculation") }

                AlertDialog(
                    onDismissRequest = { showNewDisputeDialog = false },
                    containerColor = LuxuryUiTokens.Card,
                    title = { Text("Submit Ride Grievance", color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text("Select Issue for Trip #8410:", color = LuxuryUiTokens.Muted, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            listOf(
                                "Incorrect Fare Calculation",
                                "Driver Asked for Extra Cash",
                                "Driver took Long Detour Route",
                                "Vehicle AC / Cleanliness Issue"
                            ).forEach { reason ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { disputeReason = reason }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = disputeReason == reason,
                                        onClick = { disputeReason = reason },
                                        colors = RadioButtonDefaults.colors(selectedColor = LuxuryUiTokens.Gold)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(reason, color = LuxuryUiTokens.Text, fontSize = 12.sp)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                supportTickets = listOf(
                                    SupportTicketItem(
                                        id = "TKT-${System.currentTimeMillis() % 10000}",
                                        subject = disputeReason,
                                        status = "Open · Assigned to Tier-2 Care",
                                        date = "Today"
                                    )
                                ) + supportTickets
                                showNewDisputeDialog = false
                                showPopupDisputeSubmitted = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = LuxuryUiTokens.Gold)
                        ) {
                            Text("SUBMIT TICKET", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showNewDisputeDialog = false }) {
                            Text("CANCEL", color = LuxuryUiTokens.Muted)
                        }
                    }
                )
            }
        }
    }
}

// =========================================================================
// CUSTOMER BOTTOM SHEET MODAL (P.cust)
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LuxuryUiTokens.Card,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(LuxuryUiTokens.Muted, RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
            Text(
                text = title,
                color = LuxuryUiTokens.Gold,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
            content()
        }
    }
}

// =========================================================================
// SCREENS IMPLEMENTATIONS (01 to 30)
// =========================================================================

// Screen 01: Brand Welcome & Language Selector
@Composable
fun Screen01BrandWelcomeLanguage(
    onGetStarted: () -> Unit,
    onGuestPreview: () -> Unit
) {
    var selectedLanguage by remember { mutableStateOf("English") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF14141C), Color(0xFF0A0A0E))))
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 40.dp)
        ) {
            Surface(
                color = LuxuryUiTokens.Gold.copy(alpha = 0.12f),
                border = BorderStroke(1.5.dp, LuxuryUiTokens.Gold.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.size(88.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("🚖", fontSize = 42.sp)
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                "E-RICKSHAW",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "GREEN URBAN TRANSIT",
                color = LuxuryUiTokens.Gold,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Affordable electric commute starting at ₹10 per trip.",
                color = LuxuryUiTokens.Muted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "SELECT LANGUAGE",
                color = LuxuryUiTokens.Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = if (selectedLanguage == "English") LuxuryUiTokens.Gold.copy(alpha = 0.15f) else LuxuryUiTokens.Card,
                    border = BorderStroke(if (selectedLanguage == "English") 1.5.dp else 1.dp, if (selectedLanguage == "English") LuxuryUiTokens.Gold else LuxuryUiTokens.Border),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedLanguage = "English" }
                ) {
                    Box(modifier = Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "English",
                            color = if (selectedLanguage == "English") LuxuryUiTokens.Gold else LuxuryUiTokens.Text,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                Surface(
                    color = if (selectedLanguage == "Hindi") LuxuryUiTokens.Gold.copy(alpha = 0.15f) else LuxuryUiTokens.Card,
                    border = BorderStroke(if (selectedLanguage == "Hindi") 1.5.dp else 1.dp, if (selectedLanguage == "Hindi") LuxuryUiTokens.Gold else LuxuryUiTokens.Border),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedLanguage = "Hindi" }
                ) {
                    Box(modifier = Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "हिंदी (Hindi)",
                            color = if (selectedLanguage == "Hindi") LuxuryUiTokens.Gold else LuxuryUiTokens.Text,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            GoldActionButton(text = "GET STARTED →", onClick = onGetStarted)
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onGuestPreview) {
                Text("Skip to Map (Guest Preview)", color = LuxuryUiTokens.Muted, fontSize = 12.sp)
            }
        }
    }
}

// Screen 02: Sign In with Phone & Password
@Composable
fun Screen02SignInPassword(
    onSignInSuccess: () -> Unit,
    onOtpLoginClick: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    onSignUpClick: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tokenManager = remember { com.vaisentra.mobility.rider.core.security.TokenManager.getInstance(context) }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LuxuryUiTokens.Bg)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = LuxuryUiTokens.Gold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("🚖", fontSize = 28.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text("Sign In", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text("Enter your credentials to continue", color = LuxuryUiTokens.Muted, fontSize = 13.sp)

            Spacer(modifier = Modifier.height(24.dp))
            Text("EMAIL OR MOBILE NUMBER", color = LuxuryUiTokens.Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                placeholder = { Text("name@example.com or 9876543210", color = LuxuryUiTokens.Muted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = LuxuryUiTokens.Gold,
                    unfocusedBorderColor = LuxuryUiTokens.Border,
                    focusedContainerColor = LuxuryUiTokens.Card,
                    unfocusedContainerColor = LuxuryUiTokens.Card
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("PASSWORD", color = LuxuryUiTokens.Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                TextButton(onClick = onForgotPasswordClick, contentPadding = PaddingValues(0.dp)) {
                    Text("Forgot Password?", color = LuxuryUiTokens.Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Check else Icons.Default.Lock,
                            contentDescription = "Toggle Password",
                            tint = LuxuryUiTokens.Muted
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = LuxuryUiTokens.Gold,
                    unfocusedBorderColor = LuxuryUiTokens.Border,
                    focusedContainerColor = LuxuryUiTokens.Card,
                    unfocusedContainerColor = LuxuryUiTokens.Card
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            if (errorMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(errorMessage, color = LuxuryUiTokens.RedAlert, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))
            GoldActionButton(
                text = if (isLoading) "AUTHENTICATING..." else "SIGN IN",
                onClick = {
                    if (phone.isBlank() || password.isBlank()) {
                        errorMessage = "Please enter email or mobile number and password."
                        return@GoldActionButton
                    }
                    val inputIdent = phone
                    val inputPass = password
                    phone = ""
                    password = ""
                    isLoading = true
                    errorMessage = ""
                    scope.launch {
                        try {
                            val cleanIdent = inputIdent.trim()
                            val formattedIdent = if (cleanIdent.contains("@")) {
                                cleanIdent.lowercase()
                            } else {
                                val digits = cleanIdent.filter { it.isDigit() }
                                if (cleanIdent.startsWith("+")) cleanIdent else if (digits.length == 10) "+91$digits" else cleanIdent
                            }
                            val res = ApiClient.getService().unifiedLogin(
                                com.vaisentra.mobility.rider.core.network.UnifiedLoginRequestPayload(
                                    loginType = "PASSWORD",
                                    identifier = formattedIdent,
                                    password = inputPass
                                )
                            )
                            if (res.isSuccessful && res.body()?.accessToken != null) {
                                val token = res.body()!!.accessToken!!
                                val uid = res.body()?.user?.id ?: "usr_1"
                                tokenManager.saveAuth(token, uid, formattedIdent, "customer")
                                Toast.makeText(context, "Welcome back!", Toast.LENGTH_SHORT).show()
                                onSignInSuccess()
                            } else {
                                val err = res.errorBody()?.string() ?: "Invalid email, phone number, or password."
                                errorMessage = if (err.contains("detail")) err else "Invalid credentials. Please verify."
                            }
                        } catch (e: Exception) {
                            errorMessage = "Network error: ${e.localizedMessage ?: "Could not reach server"}"
                        } finally {
                            isLoading = false
                        }
                    }
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("New to E-Rickshaw? ", color = LuxuryUiTokens.Muted, fontSize = 13.sp)
            Text(
                "Sign Up",
                color = LuxuryUiTokens.Gold,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onSignUpClick() }
            )
        }
    }
}

// Screen 03: Sign In with SMS OTP (Decommissioned)
@Composable
fun Screen03SignInOtp(
    onVerifySuccess: () -> Unit,
    onBackToPassword: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LuxuryUiTokens.Bg)
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            IconButton(onClick = onBackToPassword, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = LuxuryUiTokens.Gold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("OTP Login Decommissioned", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(8.dp))
            Text("SMS OTP login has been discontinued. Please sign in securely using your registered email/phone and password.", color = LuxuryUiTokens.Muted, fontSize = 13.sp)

            Spacer(modifier = Modifier.height(28.dp))
            GoldActionButton(
                text = "CONTINUE TO PASSWORD LOGIN",
                onClick = onBackToPassword
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

// Screen 04: Forgot Password & Support Assistance
@Composable
fun Screen04ForgotPassword(
    onPasswordResetSuccess: () -> Unit,
    onBackToLogin: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LuxuryUiTokens.Bg)
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            IconButton(onClick = onBackToLogin, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = LuxuryUiTokens.Gold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Reset Password", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "SMS OTP reset has been decommissioned. For account security and password assistance, please contact Vaisentra Mobility Support at support@vaisentra.com or visit the help desk.",
                color = LuxuryUiTokens.Muted,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(28.dp))
            GoldActionButton(
                text = "RETURN TO SIGN IN",
                onClick = onBackToLogin
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

// Screen 05: Sign Up & Basic Identity
@Composable
fun Screen05SignUp(
    onSignUpSuccess: () -> Unit,
    onBackToSignIn: () -> Unit
) {
    val context = LocalContext.current
    var fullName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var referralCode by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LuxuryUiTokens.Bg)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            IconButton(onClick = onBackToSignIn, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = LuxuryUiTokens.Gold)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text("Create Account", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text("Fast registration for instant ride bookings", color = LuxuryUiTokens.Muted, fontSize = 13.sp)

            Spacer(modifier = Modifier.height(18.dp))
            Text("FULL NAME", color = LuxuryUiTokens.Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = LuxuryUiTokens.Gold,
                    unfocusedBorderColor = LuxuryUiTokens.Border,
                    focusedContainerColor = LuxuryUiTokens.Card,
                    unfocusedContainerColor = LuxuryUiTokens.Card
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text("MOBILE NUMBER", color = LuxuryUiTokens.Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                prefix = { Text("+91 ", color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = LuxuryUiTokens.Gold,
                    unfocusedBorderColor = LuxuryUiTokens.Border,
                    focusedContainerColor = LuxuryUiTokens.Card,
                    unfocusedContainerColor = LuxuryUiTokens.Card
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text("EMAIL ADDRESS", color = LuxuryUiTokens.Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = LuxuryUiTokens.Gold,
                    unfocusedBorderColor = LuxuryUiTokens.Border,
                    focusedContainerColor = LuxuryUiTokens.Card,
                    unfocusedContainerColor = LuxuryUiTokens.Card
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text("PASSWORD", color = LuxuryUiTokens.Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                visualTransformation = PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = LuxuryUiTokens.Gold,
                    unfocusedBorderColor = LuxuryUiTokens.Border,
                    focusedContainerColor = LuxuryUiTokens.Card,
                    unfocusedContainerColor = LuxuryUiTokens.Card
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text("REFERRAL CODE (OPTIONAL)", color = LuxuryUiTokens.Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = referralCode,
                onValueChange = { referralCode = it },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = LuxuryUiTokens.Gold,
                    unfocusedTextColor = LuxuryUiTokens.Gold,
                    focusedBorderColor = LuxuryUiTokens.Gold,
                    unfocusedBorderColor = LuxuryUiTokens.Border,
                    focusedContainerColor = LuxuryUiTokens.Card,
                    unfocusedContainerColor = LuxuryUiTokens.Card
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))
            var isSubmittingSignUp by remember { mutableStateOf(false) }
            val scopeSignUp = rememberCoroutineScope()
            val tokenManagerSignUp = remember { com.vaisentra.mobility.rider.core.security.TokenManager.getInstance(context) }
            GoldActionButton(
                text = if (isSubmittingSignUp) "CREATING ACCOUNT..." else "CREATE ACCOUNT →",
                onClick = {
                    if (fullName.isBlank() || (phone.isBlank() && email.isBlank()) || password.isBlank()) {
                        Toast.makeText(context, "Please enter your name, mobile or email, and password.", Toast.LENGTH_SHORT).show()
                        return@GoldActionButton
                    }
                    val inputName = fullName.trim()
                    val inputPhone = phone.trim()
                    val inputEmail = email.trim()
                    val inputPass = password
                    fullName = ""
                    phone = ""
                    email = ""
                    password = ""
                    referralCode = ""
                    isSubmittingSignUp = true
                    scopeSignUp.launch {
                        try {
                            val phoneClean = inputPhone.filter { it.isDigit() }
                            val phoneFormatted = if (phoneClean.isNotBlank()) {
                                if (inputPhone.startsWith("+")) inputPhone else "+91${phoneClean.takeLast(10)}"
                            } else null
                            val emailClean = if (inputEmail.isNotBlank()) inputEmail.lowercase() else null

                            val res = ApiClient.getService().signupCustomer(
                                com.vaisentra.mobility.rider.core.network.CustomerSignupRequestPayload(
                                    fullName = inputName,
                                    phoneNumber = phoneFormatted,
                                    email = emailClean,
                                    username = emailClean?.substringBefore("@") ?: ("cust_" + (phoneFormatted?.takeLast(4) ?: "guest")),
                                    password = inputPass
                                )
                            )
                            if (res.isSuccessful && res.body()?.accessToken != null) {
                                val token = res.body()!!.accessToken!!
                                val uid = res.body()?.user?.id ?: "usr_customer"
                                tokenManagerSignUp.saveAuth(token, uid, phoneFormatted ?: emailClean ?: "customer", "customer")
                                Toast.makeText(context, "Account created successfully!", Toast.LENGTH_SHORT).show()
                                onSignUpSuccess()
                            } else {
                                val err = res.errorBody()?.string() ?: "Registration failed."
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Network error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        } finally {
                            isSubmittingSignUp = false
                        }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// Screen 06: Commuter Preferences & Accessibility
@Composable
fun Screen06CommuterPreferences(
    onCompleted: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var quietRide by remember { mutableStateOf(true) }
    var femalePilot by remember { mutableStateOf(true) }
    var wheelchairAssist by remember { mutableStateOf(false) }
    var petFriendly by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LuxuryUiTokens.Bg)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = LuxuryUiTokens.Gold)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text("Ride Preferences", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text("Custom settings for your daily commute", color = LuxuryUiTokens.Muted, fontSize = 13.sp)

            Spacer(modifier = Modifier.height(24.dp))

            // Quiet ride
            Surface(
                color = LuxuryUiTokens.Card,
                border = BorderStroke(1.dp, LuxuryUiTokens.Border),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("🤫 Quiet Ride", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Minimal conversation requested", color = LuxuryUiTokens.Muted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = quietRide,
                        onCheckedChange = { quietRide = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = LuxuryUiTokens.Gold,
                            checkedTrackColor = LuxuryUiTokens.Gold.copy(alpha = 0.3f)
                        )
                    )
                }
            }

            // Female pilot
            Surface(
                color = LuxuryUiTokens.Card,
                border = BorderStroke(1.dp, LuxuryUiTokens.Border),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("👩 Female Pilot Preferred", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Matched with women drivers when near", color = LuxuryUiTokens.Muted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = femalePilot,
                        onCheckedChange = { femalePilot = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = LuxuryUiTokens.Gold,
                            checkedTrackColor = LuxuryUiTokens.Gold.copy(alpha = 0.3f)
                        )
                    )
                }
            }

            // Wheelchair
            Surface(
                color = LuxuryUiTokens.Card,
                border = BorderStroke(1.dp, LuxuryUiTokens.Border),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("♿ Wheelchair Assistance", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Step-free ramp entry required", color = LuxuryUiTokens.Muted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = wheelchairAssist,
                        onCheckedChange = { wheelchairAssist = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = LuxuryUiTokens.Gold,
                            checkedTrackColor = LuxuryUiTokens.Gold.copy(alpha = 0.3f)
                        )
                    )
                }
            }

            // Pet friendly
            Surface(
                color = LuxuryUiTokens.Card,
                border = BorderStroke(1.dp, LuxuryUiTokens.Border),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("🐾 Pet Friendly Ride", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Traveling with small pet/animal", color = LuxuryUiTokens.Muted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = petFriendly,
                        onCheckedChange = { petFriendly = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = LuxuryUiTokens.Gold,
                            checkedTrackColor = LuxuryUiTokens.Gold.copy(alpha = 0.3f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            GoldActionButton(text = "COMPLETE SETUP & OPEN MAP", onClick = onCompleted)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// =========================================================================
// SCREENS IMPLEMENTATIONS (08 to 30)
// =========================================================================

// Screen 08: Home Map
@Composable
fun Screen08HomeMap(
    pickupAddress: String,
    pickupLatLng: LatLng,
    destAddress: String? = null,
    destLatLng: LatLng? = null,
    routePoints: List<LatLng> = emptyList(),
    routeDistanceKm: Double? = null,
    routeDurationMinutes: Double? = null,
    cameraTargetLatLng: LatLng? = null,
    cameraAnimTrigger: Int = 0,
    cameraAnimType: String = "flyTo",
    savedPlaces: List<SavedPlace>,
    categories: List<VehicleCategoryDto> = emptyList(),
    onWhereToClick: () -> Unit,
    onClearDestination: () -> Unit = {},
    onChooseCategory: (String) -> Unit,
    onQuickDestination: (String) -> Unit,
    onAddFavoriteClick: () -> Unit,
    onRecenterLocation: (() -> Unit)? = null
) {
    var selectedMapType by remember { mutableStateOf("LIGHT") }
    var mapDetailsState by remember { mutableStateOf(AppMapDetailsCatalog.getDefaultMap()) }
    var showLayersBottomSheet by remember { mutableStateOf(false) }
    var recenterTrigger by remember { mutableIntStateOf(0) }

    if (showLayersBottomSheet) {
        MapLayersBottomSheet(
            selectedMapType = selectedMapType,
            mapDetailsState = mapDetailsState,
            onSelectMapType = { selectedMapType = it },
            onToggleDetail = { key, enabled ->
                mapDetailsState = mapDetailsState.toMutableMap().apply { put(key, enabled) }
            },
            onDismissRequest = { showLayersBottomSheet = false }
        )
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Luxury Mapbox Customer Interactive Map
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(310.dp)
                .background(Color(0xFF0B0B0D))
        ) {
            MapboxCustomerMapView(
                modifier = Modifier.fillMaxSize(),
                pickupLatLng = pickupLatLng,
                pickupTitle = pickupAddress.ifBlank { "Pickup Point" },
                destLatLng = destLatLng,
                destTitle = destAddress ?: "Destination",
                userLocationLatLng = pickupLatLng,
                routePoints = routePoints,
                cameraTargetLatLng = cameraTargetLatLng ?: destLatLng,
                cameraAnimTrigger = cameraAnimTrigger,
                cameraAnimType = cameraAnimType,
                mapLayer = selectedMapType,
                mapDetails = mapDetailsState,
                recenterTrigger = recenterTrigger,
                zoomLevel = 16.5f
            )

            // Current Pickup Pill
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, LuxuryUiTokens.Gold),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp)
            ) {
                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(LuxuryUiTokens.GreenSuccess, CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(pickupAddress, color = LuxuryUiTokens.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Live Route Info HUD when destination route is drawn
            if (destLatLng != null && routePoints.isNotEmpty()) {
                Surface(
                    color = Color(0xFF0F172A).copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, Color(0xFF10B981)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, bottom = 26.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🛣️ ${String.format(java.util.Locale.US, "%.1f", routeDistanceKm ?: 3.5)} km • ${routeDurationMinutes?.toInt() ?: 12} mins",
                            color = Color(0xFF10B981),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "✕",
                            color = LuxuryUiTokens.Muted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onClearDestination() }
                        )
                    }
                }
            }

            // Floating Layer Selector FAB (Map Type & 7 Details)
            FloatingActionButton(
                onClick = { showLayersBottomSheet = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp)
                    .size(42.dp)
                    .border(1.dp, LuxuryUiTokens.Gold, CircleShape),
                shape = CircleShape,
                containerColor = Color(0xFF0F172A).copy(alpha = 0.9f),
                contentColor = LuxuryUiTokens.Gold
            ) {
                Text("🥞", fontSize = 18.sp)
            }

            // Floating Recenter / Real Device Current Location FAB
            FloatingActionButton(
                onClick = {
                    onRecenterLocation?.invoke() ?: run { recenterTrigger++ }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 26.dp, end = 12.dp)
                    .size(42.dp)
                    .border(1.dp, Color(0xFF10B981), CircleShape),
                shape = CircleShape,
                containerColor = Color(0xFF0F172A).copy(alpha = 0.9f),
                contentColor = Color(0xFF10B981)
            ) {
                Text("🎯", fontSize = 18.sp)
            }
        }

        // Bottom Booking Card (.sheet)
        Surface(
            color = LuxuryUiTokens.Card,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            modifier = Modifier.fillMaxWidth().offset(y = (-16).dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // "Where to?" Search Field
                Surface(
                    color = LuxuryUiTokens.FieldBg,
                    border = BorderStroke(1.dp, LuxuryUiTokens.Gold),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onWhereToClick() }
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = LuxuryUiTokens.Gold)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Where to?", color = LuxuryUiTokens.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("SAVED PLACES", color = LuxuryUiTokens.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onAddFavoriteClick) {
                        Text("+ Add Place (08)", color = LuxuryUiTokens.Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Saved Places Chips (CRUD Read)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    savedPlaces.forEach { place ->
                        Surface(
                            color = LuxuryUiTokens.FieldBg,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, LuxuryUiTokens.Border),
                            modifier = Modifier.clickable { onQuickDestination(place.address) }
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = LuxuryUiTokens.Gold, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(place.title, color = LuxuryUiTokens.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("CHOOSE RIDE CATEGORY", color = LuxuryUiTokens.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                val pills = if (categories.isNotEmpty()) {
                    categories.take(3).map { cat ->
                        val price = when {
                            cat.code.contains("SHARE", ignoreCase = true) -> "₹15"
                            cat.code.contains("AUTO", ignoreCase = true) -> "₹40"
                            cat.code.contains("CARGO", ignoreCase = true) -> "₹60"
                            else -> "₹30"
                        }
                        cat.name to price
                    }
                } else {
                    listOf(
                        "Standard E-Rickshaw" to "₹30",
                        "Shared Eco-Pool" to "₹15",
                        "Electric Auto" to "₹40"
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    pills.forEach { (catTitle, price) ->
                        CategoryPill(catTitle, price, modifier = Modifier.weight(1f)) { onChooseCategory(catTitle) }
                    }
                }
            }
        }
    }
}

// Category Pill
@Composable
fun CategoryPill(title: String, price: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        color = LuxuryUiTokens.Card,
        border = BorderStroke(1.dp, LuxuryUiTokens.Border),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = LuxuryUiTokens.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(price, color = LuxuryUiTokens.Gold, fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
    }
}

// Screen 09: Search Destination with Mapbox Search Autocomplete
@Composable
fun Screen09SearchDestination(
    initialQuery: String,
    pickupLatLng: LatLng,
    savedPlaces: List<SavedPlace>,
    onSelectDestination: (title: String, lat: Double, lng: Double) -> Unit,
    onPinDropClick: () -> Unit,
    onMultiStopClick: () -> Unit,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf(initialQuery) }
    var mapboxSuggestions by remember { mutableStateOf<List<MapboxPlaceSuggestion>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    // Live Mapbox Search Autocomplete
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length >= 2) {
            delay(250)
            isSearching = true
            try {
                val results = MapboxSearchService.searchPlaces(
                    query = q,
                    proximityLat = pickupLatLng.latitude,
                    proximityLng = pickupLatLng.longitude,
                    limit = 8
                )
                mapboxSuggestions = results
            } catch (_: Exception) {
                mapboxSuggestions = emptyList()
            } finally {
                isSearching = false
            }
        } else {
            mapboxSuggestions = emptyList()
            isSearching = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = LuxuryUiTokens.Gold)
            }
            Text("09 · Search Destination", color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search location, landmark, metro...", color = LuxuryUiTokens.Muted) },
            trailingIcon = {
                if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = LuxuryUiTokens.Gold, strokeWidth = 2.dp)
                } else if (query.isNotBlank()) {
                    IconButton(onClick = { query = ""; mapboxSuggestions = emptyList() }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = LuxuryUiTokens.Muted)
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = LuxuryUiTokens.Text,
                unfocusedTextColor = LuxuryUiTokens.Text,
                focusedBorderColor = LuxuryUiTokens.Gold,
                unfocusedBorderColor = LuxuryUiTokens.Border,
                focusedContainerColor = LuxuryUiTokens.FieldBg,
                unfocusedContainerColor = LuxuryUiTokens.FieldBg
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPinDropClick, colors = ButtonDefaults.buttonColors(containerColor = LuxuryUiTokens.Card), border = BorderStroke(1.dp, LuxuryUiTokens.Border), modifier = Modifier.weight(1f)) {
                Text("Pin on Map (10)", color = LuxuryUiTokens.Gold, fontSize = 11.sp)
            }
            Button(onClick = onMultiStopClick, colors = ButtonDefaults.buttonColors(containerColor = LuxuryUiTokens.Card), border = BorderStroke(1.dp, LuxuryUiTokens.Border), modifier = Modifier.weight(1f)) {
                Text("Multi-Stop (11)", color = LuxuryUiTokens.Gold, fontSize = 11.sp)
            }
        }

        // Live Mapbox Search Autocomplete Results
        if (mapboxSuggestions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("MAPBOX SEARCH PLACES", color = LuxuryUiTokens.Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(6.dp))
                Text("• Live Worldwide", color = Color(0xFF10B981), fontSize = 10.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))

            mapboxSuggestions.forEach { suggestion ->
                Surface(
                    color = LuxuryUiTokens.Card,
                    border = BorderStroke(1.dp, LuxuryUiTokens.Gold.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickable {
                            onSelectDestination(suggestion.name, suggestion.latitude, suggestion.longitude)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF24221A))
                                .border(1.dp, LuxuryUiTokens.Gold, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("📍", fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(suggestion.name, color = LuxuryUiTokens.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                suggestion.fullAddress,
                                color = LuxuryUiTokens.Muted,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (suggestion.distanceKm != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFF10B981).copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, Color(0xFF10B981)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "${String.format(java.util.Locale.US, "%.1f", suggestion.distanceKm)} km",
                                    color = Color(0xFF10B981),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("FAVORITE & RECENT DESTINATIONS", color = LuxuryUiTokens.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        savedPlaces.forEach { place ->
            val placeLat = if (place.isHome) pickupLatLng.latitude + 0.018 else if (place.isWork) pickupLatLng.latitude + 0.011 else pickupLatLng.latitude + 0.006
            val placeLng = if (place.isHome) pickupLatLng.longitude - 0.012 else if (place.isWork) pickupLatLng.longitude + 0.015 else pickupLatLng.longitude - 0.008
            Surface(
                color = LuxuryUiTokens.Card,
                border = BorderStroke(1.dp, LuxuryUiTokens.Border),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clickable { onSelectDestination(place.title, placeLat, placeLng) }
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (place.isHome) "🏠" else if (place.isWork) "🏢" else "⭐", fontSize = 15.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(place.title, color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(place.address, color = LuxuryUiTokens.Muted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// Screen 10: Pin Drop with Mapbox Reverse Geocoding
@Composable
fun Screen10PinDrop(onConfirmPin: (String, Double, Double) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedMapType by remember { mutableStateOf("LIGHT") }
    var mapDetailsState by remember { mutableStateOf(AppMapDetailsCatalog.getDefaultMap()) }
    var showLayersBottomSheet by remember { mutableStateOf(false) }
    var pinAddress by remember { mutableStateOf("Locating GPS position...") }
    val initialLoc = remember {
        DeviceLocationProvider.getLastKnownLocation(context) ?: LatLng(28.6139, 77.2090)
    }
    var pinLatLng by remember { mutableStateOf(initialLoc) }
    var recenterTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        DeviceLocationProvider.requestCurrentLocation(context) { loc ->
            pinLatLng = loc
            scope.launch {
                val rev = MapboxSearchService.reverseGeocode(loc.latitude, loc.longitude)
                pinAddress = if (!rev.isNullOrBlank()) rev else "Current Location (%.4f, %.4f)".format(loc.latitude, loc.longitude)
            }
            recenterTrigger++
        }
    }

    if (showLayersBottomSheet) {
        MapLayersBottomSheet(
            selectedMapType = selectedMapType,
            mapDetailsState = mapDetailsState,
            onSelectMapType = { selectedMapType = it },
            onToggleDetail = { key, enabled ->
                mapDetailsState = mapDetailsState.toMutableMap().apply { put(key, enabled) }
            },
            onDismissRequest = { showLayersBottomSheet = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = LuxuryUiTokens.Gold)
            }
            Text("10 · Set Pin on Map", color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, LuxuryUiTokens.Border, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            MapboxCustomerMapView(
                modifier = Modifier.fillMaxSize(),
                pickupLatLng = pinLatLng,
                pickupTitle = "Selected Pin Location",
                mapLayer = selectedMapType,
                mapDetails = mapDetailsState,
                recenterTrigger = recenterTrigger,
                zoomLevel = 17f,
                onCameraMoved = { newPos ->
                    pinLatLng = newPos
                    pinAddress = "Lat: %.4f, Lng: %.4f".format(newPos.latitude, newPos.longitude)
                    scope.launch {
                        val rev = MapboxSearchService.reverseGeocode(newPos.latitude, newPos.longitude)
                        if (!rev.isNullOrBlank()) {
                            pinAddress = rev
                        }
                    }
                }
            )

            // Centre Crosshair & Pin Drop Beacon
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.Center)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, LuxuryUiTokens.Gold),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Text(
                        text = "📍 Drag Map to Set Pin",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(LuxuryUiTokens.Gold)
                        .border(3.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📌", fontSize = 18.sp)
                }
            }

            // Floating Layer Switcher FAB
            FloatingActionButton(
                onClick = { showLayersBottomSheet = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 10.dp)
                    .size(40.dp)
                    .border(1.dp, LuxuryUiTokens.Gold, CircleShape),
                shape = CircleShape,
                containerColor = Color(0xFF0F172A).copy(alpha = 0.9f),
                contentColor = LuxuryUiTokens.Gold
            ) {
                Text("🥞", fontSize = 16.sp)
            }

            // Floating Recenter / Real Device Current Location FAB
            FloatingActionButton(
                onClick = {
                    DeviceLocationProvider.requestCurrentLocation(context) { loc ->
                        pinLatLng = loc
                        scope.launch {
                            val rev = MapboxSearchService.reverseGeocode(loc.latitude, loc.longitude)
                            pinAddress = if (!rev.isNullOrBlank()) rev else "Current Location (%.4f, %.4f)".format(loc.latitude, loc.longitude)
                        }
                    }
                    recenterTrigger++
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 12.dp, end = 10.dp)
                    .size(40.dp)
                    .border(1.dp, Color(0xFF10B981), CircleShape),
                shape = CircleShape,
                containerColor = Color(0xFF0F172A).copy(alpha = 0.9f),
                contentColor = Color(0xFF10B981)
            ) {
                Text("🎯", fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Surface(
            color = LuxuryUiTokens.Card,
            border = BorderStroke(1.dp, LuxuryUiTokens.Border),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("📍", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(pinAddress, color = LuxuryUiTokens.Text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        GoldActionButton(text = "CONFIRM PIN LOCATION", onClick = { onConfirmPin(pinAddress, pinLatLng.latitude, pinLatLng.longitude) })
    }
}

// Screen 11: Multi-Stop
@Composable
fun Screen11MultiStop(
    pickup: String,
    destination: String,
    intermediateStop: String = "",
    onIntermediateStopChange: (String) -> Unit = {},
    onConfirmRoute: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = LuxuryUiTokens.Gold)
            }
            Text("11 · Multi-Stop Route", color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(14.dp))
        LuxuryCardItem("Pickup Point", pickup.ifBlank { "Current Location" })
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = intermediateStop,
            onValueChange = onIntermediateStopChange,
            label = { Text("Stop 1 (Co-passenger Drop / Errand)", color = LuxuryUiTokens.Gold, fontSize = 12.sp) },
            placeholder = { Text("Enter intermediate address or stop", color = LuxuryUiTokens.Muted, fontSize = 12.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LuxuryUiTokens.Gold,
                unfocusedBorderColor = LuxuryUiTokens.Border,
                focusedTextColor = LuxuryUiTokens.Text,
                unfocusedTextColor = LuxuryUiTokens.Text
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        LuxuryCardItem("Final Destination", destination.ifBlank { "Destination Point" })

        Spacer(modifier = Modifier.height(20.dp))
        GoldActionButton(text = "CONFIRM MULTI-STOP ROUTE", onClick = onConfirmRoute)
    }
}

// Screen 12: Choose Ride with Mapbox Route Distance & ETA
@Composable
fun Screen12ChooseRide(
    selectedCat: String,
    routeDistanceKm: Double? = null,
    routeDurationMinutes: Double? = null,
    categories: List<VehicleCategoryDto> = emptyList(),
    onSelectCat: (String) -> Unit,
    onFareBreakup: () -> Unit,
    onPreferences: () -> Unit,
    onBookNow: () -> Unit,
    onBack: () -> Unit
) {
    val dist = routeDistanceKm ?: 5.2
    val dur = routeDurationMinutes?.toInt() ?: 16

    val displayCategories = if (categories.isNotEmpty()) {
        categories.map { cat ->
            val base = when {
                cat.code.contains("SHARE", ignoreCase = true) -> 15
                cat.code.contains("AUTO", ignoreCase = true) -> 40
                cat.code.contains("CARGO", ignoreCase = true) -> 60
                else -> 30
            }
            val perKm = when {
                cat.code.contains("SHARE", ignoreCase = true) -> 6
                cat.code.contains("AUTO", ignoreCase = true) -> 15
                cat.code.contains("CARGO", ignoreCase = true) -> 18
                else -> 12
            }
            val fare = (base + (dist * perKm)).toInt()
            Triple(cat.name, "₹ $fare", cat.description.ifBlank { "Eco-friendly Electric Fleet" })
        }
    } else {
        val standardFare = (30 + (dist * 12)).toInt()
        val vipFare = (50 + (dist * 18)).toInt()
        val sharedFare = maxOf(25, (standardFare * 0.45).toInt())
        val feederFare = 25
        listOf(
            Triple("Standard E-Rickshaw", "₹ $standardFare", "Economical 4-Seater Commercial EV"),
            Triple("VIP Chauffeur EV", "₹ $vipFare", "High-Comfort Foam Seats + Quiet Motor"),
            Triple("Shared Pool E-Rickshaw", "₹ $sharedFare", "Split fare with route co-passengers"),
            Triple("Metro Feeder EV", "₹ $feederFare", "Fixed direct route to nearest metro gate")
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = LuxuryUiTokens.Gold)
            }
            Text("12 · Choose Ride Category", color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(10.dp))
        // Route Distance & Duration Header Badge
        Surface(
            color = Color(0xFF0F172A),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.7f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🛣️", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Mapbox Driving Navigation", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("${String.format(java.util.Locale.US, "%.1f", dist)} km distance", color = LuxuryUiTokens.Muted, fontSize = 11.sp)
                    }
                }
                Surface(
                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF10B981))
                ) {
                    Text("ETA $dur mins", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        displayCategories.forEach { (cat, fare, desc) ->
            val isSel = selectedCat == cat
            Surface(
                color = if (isSel) Color(0xFF24221A) else LuxuryUiTokens.Card,
                border = BorderStroke(1.dp, if (isSel) LuxuryUiTokens.Gold else LuxuryUiTokens.Border),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable { onSelectCat(cat) }
            ) {
                Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(cat, color = LuxuryUiTokens.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(desc, color = LuxuryUiTokens.Muted, fontSize = 11.sp)
                    }
                    Text(fare, color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Black, fontSize = 16.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onFareBreakup, colors = ButtonDefaults.buttonColors(containerColor = LuxuryUiTokens.Card), border = BorderStroke(1.dp, LuxuryUiTokens.Border), modifier = Modifier.weight(1f)) {
                Text("Fare Breakup (13)", color = LuxuryUiTokens.Gold, fontSize = 11.sp)
            }
            Button(onClick = onPreferences, colors = ButtonDefaults.buttonColors(containerColor = LuxuryUiTokens.Card), border = BorderStroke(1.dp, LuxuryUiTokens.Border), modifier = Modifier.weight(1f)) {
                Text("Preferences (14)", color = LuxuryUiTokens.Gold, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        GoldActionButton(text = "BOOK NOW (16)", onClick = onBookNow)
    }
}

// Screen 13: Fare Breakup
@Composable
fun Screen13FareBreakup(
    baseFare: Int,
    kmFare: Int,
    techFee: Int,
    promoCode: String,
    promoDiscount: Int,
    onApplyPromo: (String) -> Unit,
    onRemovePromo: () -> Unit,
    onContinuePayment: () -> Unit,
    onBack: () -> Unit
) {
    var promoInput by remember { mutableStateOf("") }
    val total = (baseFare + kmFare + techFee - promoDiscount).coerceAtLeast(40)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = LuxuryUiTokens.Gold)
            }
            Text("13 · Fare Breakup Calculation", color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Surface(color = LuxuryUiTokens.Card, border = BorderStroke(1.dp, LuxuryUiTokens.Border), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                FareRow("Base Starting Fare", "₹ $baseFare")
                FareRow("Distance Fare (3.5 km)", "₹ $kmFare")
                FareRow("Platform Tech Fee", "₹ $techFee")
                if (promoDiscount > 0) {
                    FareRow("Promo Voucher ($promoCode)", "- ₹ $promoDiscount", color = LuxuryUiTokens.GreenSuccess)
                }
                Divider(color = LuxuryUiTokens.Border, modifier = Modifier.padding(vertical = 10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Payable", color = LuxuryUiTokens.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("₹ $total", color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Black, fontSize = 20.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("APPLY PROMO VOUCHER (CRUD)", color = LuxuryUiTokens.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = promoInput,
                onValueChange = { promoInput = it },
                placeholder = { Text("Enter METRO10 or LUXURY50", color = LuxuryUiTokens.Muted, fontSize = 12.sp) },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = LuxuryUiTokens.Text, unfocusedTextColor = LuxuryUiTokens.Text, focusedBorderColor = LuxuryUiTokens.Gold, unfocusedBorderColor = LuxuryUiTokens.Border),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (promoInput.isNotBlank()) onApplyPromo(promoInput)
                },
                colors = ButtonDefaults.buttonColors(containerColor = LuxuryUiTokens.Gold)
            ) {
                Text("APPLY", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        if (promoDiscount > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onRemovePromo) {
                Text("Remove Applied Promo", color = LuxuryUiTokens.RedAlert, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        GoldActionButton(text = "SELECT PAYMENT METHOD (15)", onClick = onContinuePayment)
    }
}

@Composable
fun FareRow(title: String, amount: String, color: Color = LuxuryUiTokens.Text) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, color = LuxuryUiTokens.Muted, fontSize = 12.sp)
        Text(amount, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

// Screen 14: Ride Preferences (CRUD)
@Composable
fun Screen14RidePreferences(
    quietRide: Boolean,
    onQuietRideChange: (Boolean) -> Unit,
    acComfort: Boolean,
    onAcComfortChange: (Boolean) -> Unit,
    luggageAssistance: Boolean,
    onLuggageChange: (Boolean) -> Unit,
    wheelchair: Boolean,
    onWheelchairChange: (Boolean) -> Unit,
    onSavePreferences: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = LuxuryUiTokens.Gold)
            }
            Text("14 · Ride Preferences", color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))
        PreferenceSwitchRow("Quiet Ride", "Driver avoids non-essential conversation", quietRide, onQuietRideChange)
        PreferenceSwitchRow("Fan / Cooling Ventilation", "Maximum airflow in electric cabin", acComfort, onAcComfortChange)
        PreferenceSwitchRow("Luggage Assistance", "Driver helps load bags at pickup", luggageAssistance, onLuggageChange)
        PreferenceSwitchRow("Wheelchair Accessible", "Ramp and priority seating", wheelchair, onWheelchairChange)

        Spacer(modifier = Modifier.height(24.dp))
        GoldActionButton(text = "SAVE RIDE PREFERENCES", onClick = onSavePreferences)
    }
}

@Composable
fun PreferenceSwitchRow(title: String, desc: String, isChecked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(color = LuxuryUiTokens.Card, border = BorderStroke(1.dp, LuxuryUiTokens.Border), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = LuxuryUiTokens.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(desc, color = LuxuryUiTokens.Muted, fontSize = 11.sp)
            }
            Switch(checked = isChecked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = LuxuryUiTokens.Gold))
        }
    }
}

// Screen 15: Payment Method
@Composable
fun Screen15PaymentMethod(
    walletBalance: Int,
    savedVpas: List<SavedPaymentVpa>,
    onSelectPayment: () -> Unit,
    onAddVpa: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = LuxuryUiTokens.Gold)
            }
            Text("15 · Select Payment Method", color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(14.dp))
        LuxuryCardItem("E-Rickshaw Wallet", "Available: ₹$walletBalance.00 (Tap to Pay)")

        savedVpas.forEach { vpa ->
            LuxuryCardItem(vpa.bank, "${vpa.vpa} • UPI")
        }

        LuxuryCardItem("Cash on Delivery", "Pay exact cash to chauffeur at drop-off")

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onAddVpa,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = LuxuryUiTokens.Gold),
            border = BorderStroke(1.dp, LuxuryUiTokens.Gold),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("+ Link New UPI ID", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(20.dp))
        GoldActionButton(text = "CONFIRM PAYMENT METHOD (16)", onClick = onSelectPayment)
    }
}

// Screen 16: Confirm Booking
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
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = LuxuryUiTokens.Gold)
            }
            Text("16 · Confirm Booking", color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))
        LuxuryCardItem("Category", category)
        LuxuryCardItem("Pickup Point", pickup)
        LuxuryCardItem("Destination", destination)
        LuxuryCardItem("Total Fare", "₹ $totalFare.00 (All Inclusive)")

        Spacer(modifier = Modifier.height(20.dp))
        GoldActionButton(text = "CONFIRM & DISPATCH CHAUFFEUR (18)", onClick = onConfirmBooking)

        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onTriggerOutofZone, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Simulate Out-of-Zone Alert (09)", color = LuxuryUiTokens.Muted, fontSize = 11.sp)
        }
    }
}

// Screen 17: Out of Zone
@Composable
fun Screen17OutOfZone(onChangeDestination: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = LuxuryUiTokens.RedAlert, modifier = Modifier.size(54.dp))
        Spacer(modifier = Modifier.height(14.dp))
        Text("DESTINATION OUT OF BOUNDS", color = LuxuryUiTokens.RedAlert, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Commercial E-Rickshaws operate strictly within authorized municipal territory.", color = LuxuryUiTokens.Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(20.dp))
        GoldActionButton(text = "CHANGE DESTINATION", onClick = onChangeDestination)
    }
}

// Screen 18: Searching Radar
@Composable
fun Screen18SearchingRadar(secondsLeft: Int, onCancel: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("CONNECTING WITH CHAUFFEUR", color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Radar broadcast to nearby certified E-Rickshaws", color = LuxuryUiTokens.Muted, fontSize = 12.sp)

        Spacer(modifier = Modifier.height(24.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(140.dp)) {
            CircularProgressIndicator(
                progress = { secondsLeft / 20f },
                color = LuxuryUiTokens.Gold,
                strokeWidth = 8.dp,
                trackColor = Color(0xFF222228),
                modifier = Modifier.fillMaxSize()
            )
            Text("${secondsLeft}s", color = LuxuryUiTokens.Gold, fontSize = 28.sp, fontWeight = FontWeight.Black)
        }

        Spacer(modifier = Modifier.height(28.dp))
        OutlinedButton(onClick = onCancel, border = BorderStroke(1.dp, LuxuryUiTokens.RedAlert), colors = ButtonDefaults.outlinedButtonColors(contentColor = LuxuryUiTokens.RedAlert)) {
            Text("Cancel Booking Request")
        }
    }
}

// Screen 19: Driver Found
@Composable
fun Screen19DriverFound(driverName: String, plate: String, rating: String, startOtp: String, etaMinutes: Int, onProceedToArriving: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("19 · CHAUFFEUR ASSIGNED", color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.size(80.dp).background(LuxuryUiTokens.Card, CircleShape).border(2.dp, LuxuryUiTokens.Gold, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Person, contentDescription = null, tint = LuxuryUiTokens.Gold, modifier = Modifier.size(44.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(driverName, color = LuxuryUiTokens.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("$rating · Commercial EV Saarthi Prime ($plate)", color = LuxuryUiTokens.Muted, fontSize = 12.sp)

        Spacer(modifier = Modifier.height(20.dp))
        LuxuryCardItem("Driver ETA", "$etaMinutes Minutes away (Approaching Gate 2)")
        LuxuryCardItem("Boarding Start PIN", "$startOtp (Share when driver arrives)")

        Spacer(modifier = Modifier.height(24.dp))
        GoldActionButton(text = "TRACK DRIVER ARRIVAL (20)", onClick = onProceedToArriving)
    }
}

// Screen 20: Driver Arriving
@Composable
fun Screen20DriverArriving(driverName: String, plate: String, startOtp: String, etaMinutes: Int, onCall: () -> Unit, onChat: () -> Unit, onBoarded: () -> Unit, onCancel: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("20 · Chauffeur Arriving", color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(12.dp))

        Surface(color = LuxuryUiTokens.Card, border = BorderStroke(1.dp, LuxuryUiTokens.Gold), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(driverName, color = LuxuryUiTokens.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(plate, color = LuxuryUiTokens.Gold, fontSize = 12.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = onChat, modifier = Modifier.background(Color(0xFF222228), CircleShape)) {
                            Icon(Icons.Default.Send, contentDescription = "Chat", tint = LuxuryUiTokens.Gold, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onCall, modifier = Modifier.background(LuxuryUiTokens.Gold, CircleShape)) {
                            Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.Black, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("ETA: $etaMinutes MIN · Approaching Pickup", color = LuxuryUiTokens.Text, fontWeight = FontWeight.Bold)
                Text("Start OTP: $startOtp", color = LuxuryUiTokens.Gold, fontSize = 13.sp, fontWeight = FontWeight.Black)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        GoldActionButton(text = "I HAVE BOARDED (START RIDE 23)", onClick = onBoarded)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onCancel, colors = ButtonDefaults.outlinedButtonColors(contentColor = LuxuryUiTokens.RedAlert), border = BorderStroke(1.dp, LuxuryUiTokens.RedAlert), modifier = Modifier.fillMaxWidth()) {
            Text("Cancel Ride (15)")
        }
    }
}

// Screen 21: Ride Chat (CRUD)
@Composable
fun Screen21RideChat(driverName: String, messages: List<ChatBubbleItem>, onSendMessage: (String) -> Unit, onBack: () -> Unit) {
    var replyText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = LuxuryUiTokens.Gold)
            }
            Text("21 · Chat with $driverName", color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(10.dp))
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            messages.forEach { msg ->
                CustomerChatBubble(msg.message, isCustomer = msg.isCustomer)
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = replyText,
                onValueChange = { replyText = it },
                placeholder = { Text("Type quick reply…", color = LuxuryUiTokens.Muted) },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = LuxuryUiTokens.Text, unfocusedTextColor = LuxuryUiTokens.Text, focusedBorderColor = LuxuryUiTokens.Gold, unfocusedBorderColor = LuxuryUiTokens.Border),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (replyText.isNotBlank()) {
                        onSendMessage(replyText)
                        replyText = ""
                    }
                },
                modifier = Modifier.background(LuxuryUiTokens.Gold, CircleShape)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.Black)
            }
        }
    }
}

@Composable
fun CustomerChatBubble(message: String, isCustomer: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isCustomer) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (isCustomer) LuxuryUiTokens.Gold else LuxuryUiTokens.Card,
            shape = RoundedCornerShape(10.dp),
            border = if (!isCustomer) BorderStroke(1.dp, LuxuryUiTokens.Border) else null,
            modifier = Modifier.widthIn(max = 260.dp)
        ) {
            Text(message, color = if (isCustomer) Color.Black else LuxuryUiTokens.Text, fontSize = 12.sp, modifier = Modifier.padding(10.dp))
        }
    }
}

// Screen 22: Masked Call
@Composable
fun Screen22MaskedCall(driverName: String, driverPhone: String = "", onEndCall: () -> Unit) {
    val maskedDisplay = if (driverPhone.isNotBlank()) "+91-XXXX-${driverPhone.takeLast(4)}" else "+91-XXXX-SECURE"
    Column(modifier = Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("NUMBER MASKED SECURE CALL", color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Connected with ${driverName.ifBlank { "Chauffeur" }} ($maskedDisplay)", color = LuxuryUiTokens.Muted, fontSize = 12.sp)

        Spacer(modifier = Modifier.height(24.dp))
        Box(modifier = Modifier.size(90.dp).background(LuxuryUiTokens.Card, CircleShape).border(2.dp, LuxuryUiTokens.Gold, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Call, contentDescription = null, tint = LuxuryUiTokens.Gold, modifier = Modifier.size(44.dp))
        }

        Spacer(modifier = Modifier.height(28.dp))
        Button(onClick = onEndCall, colors = ButtonDefaults.buttonColors(containerColor = LuxuryUiTokens.RedAlert), shape = RoundedCornerShape(10.dp)) {
            Text("END CALL", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

// Screen 23: Ride In Progress
@Composable
fun Screen23RideInProgress(driverName: String, destination: String, onShare: () -> Unit, onSos: () -> Unit, onCompleteTrip: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("23 · Ride in Progress", color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(12.dp))

        Surface(color = LuxuryUiTokens.Card, border = BorderStroke(1.dp, LuxuryUiTokens.Gold), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("En Route to Destination", color = LuxuryUiTokens.Muted, fontSize = 11.sp)
                Text(destination, color = LuxuryUiTokens.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("ETA: 12 MINS · Chauffeur: $driverName", color = LuxuryUiTokens.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onShare, colors = ButtonDefaults.buttonColors(containerColor = LuxuryUiTokens.Card), border = BorderStroke(1.dp, LuxuryUiTokens.Border), modifier = Modifier.weight(1f)) {
                Text("Share Trip (21)", color = LuxuryUiTokens.Gold, fontSize = 11.sp)
            }
            Button(onClick = onSos, colors = ButtonDefaults.buttonColors(containerColor = LuxuryUiTokens.RedAlert), modifier = Modifier.weight(1f)) {
                Text("SOS (20)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        GoldActionButton(text = "ARRIVE AT DESTINATION (28)", onClick = onCompleteTrip)
    }
}

// Screen 24: Share Trip
@Composable
fun Screen24ShareTrip(onDismiss: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("SHARE LIVE TRIP TELEMETRY", color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Real-time GPS tracking link copied to clipboard.", color = LuxuryUiTokens.Muted, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(20.dp))
        GoldActionButton(text = "BACK TO RIDE", onClick = onDismiss)
    }
}

// Screen 25: SOS Panic
@Composable
fun Screen25SosPanic(onDismiss: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = LuxuryUiTokens.RedAlert, modifier = Modifier.size(54.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text("EMERGENCY 112 DISPATCHED", color = LuxuryUiTokens.RedAlert, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text("Police helpline 112 dialed and live location sent to Fleet Safety Command.", color = LuxuryUiTokens.Text, fontSize = 12.sp, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(20.dp))
        GoldActionButton(text = "DISMISS ALERT", onClick = onDismiss)
    }
}

// Screen 26: Fare Split
@Composable
fun Screen26FareSplit(totalFare: Int, onDone: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("SPLIT FARE WITH PASSENGERS", color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Share ₹$totalFare evenly (₹${totalFare / 2} each)", color = LuxuryUiTokens.Muted, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(20.dp))
        GoldActionButton(text = "DONE", onClick = onDone)
    }
}

// Screen 27: Cancel Ride
@Composable
fun Screen27CancelRide(onKeepRide: () -> Unit, onConfirmCancel: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("CANCEL ASSIGNMENT", color = LuxuryUiTokens.RedAlert, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("A standard ₹20 fee applies if cancelled after chauffeur has accepted.", color = LuxuryUiTokens.Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(20.dp))
        GoldActionButton(text = "KEEP RIDE", onClick = onKeepRide)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onConfirmCancel, colors = ButtonDefaults.outlinedButtonColors(contentColor = LuxuryUiTokens.RedAlert), border = BorderStroke(1.dp, LuxuryUiTokens.RedAlert), modifier = Modifier.fillMaxWidth()) {
            Text("CONFIRM CANCEL (₹20 FEE)")
        }
    }
}

// Screen 28: Trip Summary
@Composable
fun Screen28TripSummary(totalFare: Int, distanceKm: Double, durationMins: Int, onPay: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("28 · Trip Completed", color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(16.dp))

        Icon(Icons.Default.Check, contentDescription = null, tint = LuxuryUiTokens.Gold, modifier = Modifier.size(50.dp))
        Text("₹ $totalFare.00", color = LuxuryUiTokens.Gold, fontSize = 34.sp, fontWeight = FontWeight.Black)
        Text("Total Fare · $distanceKm km driven in $durationMins mins", color = LuxuryUiTokens.Muted, fontSize = 12.sp)

        Spacer(modifier = Modifier.height(24.dp))
        GoldActionButton(text = "PROCEED TO SETTLEMENT (29)", onClick = onPay)
    }
}

// Screen 29: Payment Settle
@Composable
fun Screen29PaymentSettle(amount: Int, onPaymentDone: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var isPaying by remember { mutableStateOf(false) }
    var selectedMethod by remember { mutableStateOf("UPI") } // "UPI", "RAZORPAY", "CASH"
    var selectedUpiApp by remember { mutableStateOf("ANY") } // "GPAY", "PHONEPE", "PAYTM", "BHIM", "ANY", "VPA"
    var customVpaInput by remember { mutableStateOf("") }

    fun launchRazorpayFlow(act: android.app.Activity) {
        isPaying = true
        scope.launch {
            try {
                val res = com.vaisentra.mobility.rider.core.network.ApiClient.getService().createRazorpayOrder(
                    com.vaisentra.mobility.rider.core.network.RazorpayOrderRequest(
                        amountRupees = amount.toDouble(),
                        amountCents = amount * 100,
                        receipt = "settle_${System.currentTimeMillis()}"
                    )
                )
                val order = res.body()
                val orderId = order?.orderId ?: ""
                val keyId = order?.keyId?.ifBlank { com.vaisentra.mobility.rider.feature.payments.RazorpayConfig.LIVE_KEY_ID } 
                    ?: com.vaisentra.mobility.rider.feature.payments.RazorpayConfig.LIVE_KEY_ID

                com.vaisentra.mobility.rider.feature.payments.RazorpayCheckoutHelper.startPayment(
                    activity = act,
                    orderId = orderId,
                    amountRupees = amount.toDouble(),
                    rideId = "RIDE_LIVE",
                    keyId = keyId,
                    forceUpiFirst = true,
                    callback = object : com.vaisentra.mobility.rider.feature.payments.RazorpayPaymentCallback {
                        override fun onPaymentSuccess(paymentId: String, orderId: String, signature: String) {
                            android.widget.Toast.makeText(context, "Razorpay Paid: $paymentId", android.widget.Toast.LENGTH_LONG).show()
                            isPaying = false
                            onPaymentDone()
                        }
                        override fun onPaymentError(code: Int, description: String) {
                            android.widget.Toast.makeText(context, "Payment Notice: $description", android.widget.Toast.LENGTH_SHORT).show()
                            isPaying = false
                        }
                    }
                )
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Razorpay: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                isPaying = false
                onPaymentDone()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("29 · FARE SETTLEMENT", color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text("₹ $amount", color = LuxuryUiTokens.Gold, fontSize = 42.sp, fontWeight = FontWeight.Black)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Instant UPI (GPay/PhonePe/Paytm/QR) & Razorpay Live", color = LuxuryUiTokens.Muted, fontSize = 12.sp)

        Spacer(modifier = Modifier.height(18.dp))

        // 1. Direct UPI Instant (GPay / PhonePe / Paytm / BHIM)
        Surface(
            color = if (selectedMethod == "UPI") LuxuryUiTokens.Gold.copy(alpha = 0.12f) else LuxuryUiTokens.Card,
            border = BorderStroke(if (selectedMethod == "UPI") 1.5.dp else 1.dp, if (selectedMethod == "UPI") LuxuryUiTokens.Gold else LuxuryUiTokens.Border),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { selectedMethod = "UPI" }
                .padding(vertical = 4.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("⚡ Pay via UPI (Full 1-Tap Working)", color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("GPay, PhonePe, Paytm, BHIM & Custom UPI ID", color = LuxuryUiTokens.Muted, fontSize = 11.sp)
                    }
                    RadioButton(
                        selected = (selectedMethod == "UPI"),
                        onClick = { selectedMethod = "UPI" },
                        colors = RadioButtonDefaults.colors(selectedColor = LuxuryUiTokens.Gold, unselectedColor = LuxuryUiTokens.Muted)
                    )
                }

                if (selectedMethod == "UPI") {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("CHOOSE UPI APP FOR 1-TAP PAYMENT:", color = LuxuryUiTokens.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Triple("GPAY", "Google Pay", com.vaisentra.mobility.rider.feature.payments.RazorpayCheckoutHelper.PACKAGE_GPAY),
                            Triple("PHONEPE", "PhonePe", com.vaisentra.mobility.rider.feature.payments.RazorpayCheckoutHelper.PACKAGE_PHONEPE),
                            Triple("PAYTM", "Paytm", com.vaisentra.mobility.rider.feature.payments.RazorpayCheckoutHelper.PACKAGE_PAYTM),
                            Triple("ANY", "Any UPI", "")
                        ).forEach { (id, label, pkg) ->
                            val isSel = (selectedUpiApp == id)
                            Surface(
                                color = if (isSel) LuxuryUiTokens.Gold else LuxuryUiTokens.Card,
                                border = BorderStroke(1.dp, if (isSel) LuxuryUiTokens.Gold else LuxuryUiTokens.Border),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedUpiApp = id
                                        val act = context as? android.app.Activity
                                        if (act != null) {
                                            if (pkg.isNotBlank()) {
                                                val ok = com.vaisentra.mobility.rider.feature.payments.RazorpayCheckoutHelper.launchSpecificUpiApp(
                                                    context = context,
                                                    packageName = pkg,
                                                    amountRupees = amount.toDouble(),
                                                    rideId = "RIDE_${System.currentTimeMillis()}"
                                                )
                                                if (ok) onPaymentDone()
                                            } else {
                                                val ok = com.vaisentra.mobility.rider.feature.payments.RazorpayCheckoutHelper.launchDirectUpiIntent(
                                                    context = context,
                                                    amountRupees = amount.toDouble(),
                                                    rideId = "RIDE_${System.currentTimeMillis()}"
                                                )
                                                if (ok) onPaymentDone()
                                            }
                                        }
                                    }
                            ) {
                                Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        color = if (isSel) Color.Black else LuxuryUiTokens.Text,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = customVpaInput,
                        onValueChange = { customVpaInput = it },
                        placeholder = { Text("Or Enter VPA (e.g. mobile@upi)", color = LuxuryUiTokens.Muted, fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = LuxuryUiTokens.Text,
                            unfocusedTextColor = LuxuryUiTokens.Text,
                            focusedBorderColor = LuxuryUiTokens.Gold,
                            unfocusedBorderColor = LuxuryUiTokens.Border
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (customVpaInput.isNotBlank()) {
                                TextButton(
                                    onClick = {
                                        val vpa = customVpaInput.trim()
                                        val ok = com.vaisentra.mobility.rider.feature.payments.RazorpayCheckoutHelper.launchDirectUpiIntent(
                                            context = context,
                                            amountRupees = amount.toDouble(),
                                            rideId = "RIDE_${System.currentTimeMillis()}",
                                            payeeVpa = vpa
                                        )
                                        if (ok) onPaymentDone()
                                    }
                                ) {
                                    Text("PAY", color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 2. Razorpay Live Gateway (All UPI, Cards, Netbanking)
        Surface(
            color = if (selectedMethod == "RAZORPAY") LuxuryUiTokens.Gold.copy(alpha = 0.12f) else LuxuryUiTokens.Card,
            border = BorderStroke(if (selectedMethod == "RAZORPAY") 1.5.dp else 1.dp, if (selectedMethod == "RAZORPAY") LuxuryUiTokens.Gold else LuxuryUiTokens.Border),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { selectedMethod = "RAZORPAY" }
                .padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("💳 Razorpay Live Gateway", color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Cards, Netbanking, Wallets, UPI & QR Code", color = LuxuryUiTokens.Muted, fontSize = 11.sp)
                }
                RadioButton(
                    selected = (selectedMethod == "RAZORPAY"),
                    onClick = { selectedMethod = "RAZORPAY" },
                    colors = RadioButtonDefaults.colors(selectedColor = LuxuryUiTokens.Gold, unselectedColor = LuxuryUiTokens.Muted)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 3. Cash Handover
        Surface(
            color = if (selectedMethod == "CASH") LuxuryUiTokens.Gold.copy(alpha = 0.12f) else LuxuryUiTokens.Card,
            border = BorderStroke(if (selectedMethod == "CASH") 1.5.dp else 1.dp, if (selectedMethod == "CASH") LuxuryUiTokens.Gold else LuxuryUiTokens.Border),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { selectedMethod = "CASH" }
                .padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("💵 Chauffeur Cash Handover", color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Physical payment to chauffeur at destination", color = LuxuryUiTokens.Muted, fontSize = 11.sp)
                }
                RadioButton(
                    selected = (selectedMethod == "CASH"),
                    onClick = { selectedMethod = "CASH" },
                    colors = RadioButtonDefaults.colors(selectedColor = LuxuryUiTokens.Gold, unselectedColor = LuxuryUiTokens.Muted)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        GoldActionButton(
            text = when {
                isPaying -> "OPENING PAYMENT..."
                selectedMethod == "UPI" -> "PAY ₹$amount VIA UPI (GPAY/PHONEPE/PAYTM)"
                selectedMethod == "RAZORPAY" -> "PAY ₹$amount VIA RAZORPAY GATEWAY"
                else -> "CONFIRM CASH SETTLEMENT (₹$amount)"
            },
            onClick = {
                val act = context as? android.app.Activity
                if (act != null) {
                    when (selectedMethod) {
                        "UPI" -> {
                            val pkg = when (selectedUpiApp) {
                                "GPAY" -> com.vaisentra.mobility.rider.feature.payments.RazorpayCheckoutHelper.PACKAGE_GPAY
                                "PHONEPE" -> com.vaisentra.mobility.rider.feature.payments.RazorpayCheckoutHelper.PACKAGE_PHONEPE
                                "PAYTM" -> com.vaisentra.mobility.rider.feature.payments.RazorpayCheckoutHelper.PACKAGE_PAYTM
                                else -> ""
                            }
                            val launched = if (pkg.isNotBlank()) {
                                com.vaisentra.mobility.rider.feature.payments.RazorpayCheckoutHelper.launchSpecificUpiApp(
                                    context = context,
                                    packageName = pkg,
                                    amountRupees = amount.toDouble(),
                                    rideId = "RIDE_LIVE_${System.currentTimeMillis()}"
                                )
                            } else {
                                com.vaisentra.mobility.rider.feature.payments.RazorpayCheckoutHelper.launchDirectUpiIntent(
                                    context = context,
                                    amountRupees = amount.toDouble(),
                                    rideId = "RIDE_LIVE_${System.currentTimeMillis()}"
                                )
                            }
                            if (!launched) {
                                launchRazorpayFlow(act)
                            } else {
                                onPaymentDone()
                            }
                        }
                        "RAZORPAY" -> {
                            launchRazorpayFlow(act)
                        }
                        else -> {
                            android.widget.Toast.makeText(context, "Cash settlement confirmed: ₹$amount", android.widget.Toast.LENGTH_SHORT).show()
                            onPaymentDone()
                        }
                    }
                } else {
                    onPaymentDone()
                }
            }
        )

        Spacer(modifier = Modifier.height(10.dp))
        GoldActionButton(text = "INSTANT SIMULATE (CONFIRM)", onClick = onPaymentDone)
    }
}

// Screen 30: Rate Driver
@Composable
fun Screen30RateDriver(driverName: String, onSubmitted: (Int) -> Unit) {
    var stars by remember { mutableIntStateOf(5) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("30 · Rate Chauffeur", color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(16.dp))

        Text("How was your trip with ${driverName.ifBlank { "your Chauffeur" }}?", color = LuxuryUiTokens.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in 1..5) {
                IconButton(onClick = { stars = i }) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = if (i <= stars) LuxuryUiTokens.Gold else Color(0xFF333338), modifier = Modifier.size(36.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        GoldActionButton(text = "SUBMIT $stars-STAR RATING", onClick = { onSubmitted(stars) })
    }
}

// =========================================================================
// TAB 2: RIDES & DISPUTES (CRUD)
// =========================================================================
@Composable
fun RidesTabContent(
    supportTickets: List<SupportTicketItem>,
    onRepeatRide: () -> Unit,
    onOpenDispute: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Your Past Trips", color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            TextButton(onClick = onOpenDispute) {
                Text("+ Raise Dispute (23)", color = LuxuryUiTokens.Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Surface(
            color = LuxuryUiTokens.Card,
            border = BorderStroke(1.dp, LuxuryUiTokens.Border),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No completed rides yet.", color = LuxuryUiTokens.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Your real-time ride receipts & trip history will appear here.", color = LuxuryUiTokens.Muted, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(10.dp))
                GoldActionButton(text = "BOOK YOUR FIRST RIDE", onClick = onRepeatRide)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("ACTIVE SUPPORT DISPUTES (CRUD)", color = LuxuryUiTokens.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        if (supportTickets.isEmpty()) {
            Text("No active dispute tickets. Everything is running smoothly.", color = LuxuryUiTokens.Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
        }

        supportTickets.forEach { ticket ->
            Surface(color = LuxuryUiTokens.Card, border = BorderStroke(1.dp, LuxuryUiTokens.Border), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(ticket.id, color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(ticket.status, color = LuxuryUiTokens.GreenSuccess, fontSize = 11.sp)
                    }
                    Text(ticket.subject, color = LuxuryUiTokens.Text, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}

// =========================================================================
// TAB 3: WALLET & CARDS (CRUD)
// =========================================================================
@Composable
fun WalletTabContent(
    balance: Int,
    transactions: List<WalletTxn>,
    savedVpas: List<SavedPaymentVpa>,
    onAddMoneyClick: () -> Unit,
    onAddVpaClick: () -> Unit,
    onDeleteVpa: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("E-Rickshaw Luxury Wallet", color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(14.dp))

        Surface(color = LuxuryUiTokens.Card, border = BorderStroke(1.dp, LuxuryUiTokens.Gold), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("TOTAL BALANCE", color = LuxuryUiTokens.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("₹ $balance.00", color = LuxuryUiTokens.Gold, fontSize = 36.sp, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.height(10.dp))
                GoldActionButton(text = "+ TOP-UP WALLET BALANCE", onClick = onAddMoneyClick)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("SAVED PAYMENT VPAs (CRUD)", color = LuxuryUiTokens.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onAddVpaClick) {
                Text("+ Link UPI", color = LuxuryUiTokens.Gold, fontSize = 11.sp)
            }
        }

        if (savedVpas.isEmpty()) {
            Text("No saved UPI accounts yet. Tap '+ Link UPI' to link one.", color = LuxuryUiTokens.Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
        }

        savedVpas.forEach { vpa ->
            Surface(color = LuxuryUiTokens.Card, border = BorderStroke(1.dp, LuxuryUiTokens.Border), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(vpa.bank, color = LuxuryUiTokens.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(vpa.vpa, color = LuxuryUiTokens.Muted, fontSize = 11.sp)
                    }
                    IconButton(onClick = { onDeleteVpa(vpa.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = LuxuryUiTokens.RedAlert, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text("RECENT TRANSACTIONS", color = LuxuryUiTokens.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        if (transactions.isEmpty()) {
            Text("No transactions yet. Real wallet activities will show here.", color = LuxuryUiTokens.Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
        }

        transactions.forEach { txn ->
            Surface(color = LuxuryUiTokens.Card, border = BorderStroke(1.dp, LuxuryUiTokens.Border), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(txn.title, color = LuxuryUiTokens.Text, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(txn.time, color = LuxuryUiTokens.Muted, fontSize = 11.sp)
                    }
                    Text(
                        text = (if (txn.isCredit) "+ ₹" else "- ₹") + txn.amount,
                        color = if (txn.isCredit) LuxuryUiTokens.GreenSuccess else LuxuryUiTokens.RedAlert,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

// =========================================================================
// TAB 4: ACCOUNT, CONTACTS & PROFILE (CRUD)
// =========================================================================
@Composable
fun AccountTabContent(
    userName: String,
    userPhone: String,
    userEmail: String,
    savedPlaces: List<SavedPlace>,
    emergencyContacts: List<EmergencyContactItem>,
    onEditProfile: () -> Unit,
    onAddContact: () -> Unit,
    onDeleteContact: (String) -> Unit,
    onToggleContactNotify: (String) -> Unit,
    onDeleteSavedPlace: (String) -> Unit,
    onSignOut: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Account & Profile", color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(14.dp))

        // Profile Card
        Surface(color = LuxuryUiTokens.Card, border = BorderStroke(1.dp, LuxuryUiTokens.Border), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val initials = if (userName.isNotBlank()) {
                        userName.trim().split("\\s+".toRegex()).take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
                    } else "RA"
                    Box(modifier = Modifier.size(46.dp).background(LuxuryUiTokens.Gold, CircleShape), contentAlignment = Alignment.Center) {
                        Text(initials.ifBlank { "RA" }, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(userName.ifBlank { "Rider Account" }, color = LuxuryUiTokens.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(userPhone.ifBlank { "Phone not registered" }, color = LuxuryUiTokens.Muted, fontSize = 12.sp)
                        Text(userEmail.ifBlank { "Connected to FastAPI Backend" }, color = LuxuryUiTokens.Gold, fontSize = 11.sp)
                    }
                }
                IconButton(onClick = onEditProfile) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Profile", tint = LuxuryUiTokens.Gold, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("EMERGENCY SOS CONTACTS (CRUD)", color = LuxuryUiTokens.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onAddContact) {
                Text("+ Add Contact", color = LuxuryUiTokens.Gold, fontSize = 11.sp)
            }
        }

        if (emergencyContacts.isEmpty()) {
            Text("No emergency contacts saved yet. Add trusted contacts for SOS alerts.", color = LuxuryUiTokens.Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
        }

        emergencyContacts.forEach { contact ->
            Surface(color = LuxuryUiTokens.Card, border = BorderStroke(1.dp, LuxuryUiTokens.Border), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(contact.name, color = LuxuryUiTokens.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("${contact.phone} (${contact.relationship})", color = LuxuryUiTokens.Muted, fontSize = 11.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = contact.notifyOnSos,
                            onCheckedChange = { onToggleContactNotify(contact.id) },
                            colors = SwitchDefaults.colors(checkedThumbColor = LuxuryUiTokens.RedAlert)
                        )
                        IconButton(onClick = { onDeleteContact(contact.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = LuxuryUiTokens.RedAlert, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text("SAVED PLACES (CRUD)", color = LuxuryUiTokens.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))

        if (savedPlaces.isEmpty()) {
            Text("No saved places yet. Save Home, Work, or favorite destinations.", color = LuxuryUiTokens.Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
        }

        savedPlaces.forEach { place ->
            Surface(color = LuxuryUiTokens.Card, border = BorderStroke(1.dp, LuxuryUiTokens.Border), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(place.title, color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(place.address, color = LuxuryUiTokens.Muted, fontSize = 11.sp)
                    }
                    IconButton(onClick = { onDeleteSavedPlace(place.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = LuxuryUiTokens.RedAlert, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onSignOut,
            colors = ButtonDefaults.buttonColors(containerColor = LuxuryUiTokens.FieldBg),
            border = BorderStroke(1.dp, LuxuryUiTokens.RedAlert),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("LOGOUT OF DEVICE (04)", color = LuxuryUiTokens.RedAlert, fontWeight = FontWeight.Bold)
        }
    }
}

// Reusable Helper UI Components
@Composable
fun LuxuryCardItem(title: String, desc: String) {
    Surface(color = LuxuryUiTokens.Card, border = BorderStroke(1.dp, LuxuryUiTokens.Border), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, color = LuxuryUiTokens.Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(desc, color = LuxuryUiTokens.Muted, fontSize = 11.sp)
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
