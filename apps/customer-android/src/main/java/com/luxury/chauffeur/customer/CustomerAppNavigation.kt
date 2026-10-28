package com.luxury.chauffeur.customer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.luxury.chauffeur.customer.core.security.TokenManager
import com.luxury.chauffeur.customer.feature.splash.SplashScreen
import com.luxury.chauffeur.customer.feature.auth.CustomerAuthenticationDialog
import com.luxury.chauffeur.customer.feature.location.LocationScreen
import com.luxury.chauffeur.customer.feature.profile.ProfileEditScreen
import com.luxury.chauffeur.customer.feature.tracking.LiveTrackingScreen
import com.luxury.chauffeur.customer.feature.payments.PaymentScreen
import com.luxury.chauffeur.customer.feature.notifications.NotificationScreen
import com.luxury.chauffeur.customer.feature.governance.AccountGovernanceScreen
import com.luxury.chauffeur.customer.feature.security.AppLockOverlay
import com.luxury.chauffeur.customer.feature.sharedride.SharedTripConsoleScreen
import com.google.firebase.messaging.FirebaseMessaging
import com.luxury.chauffeur.customer.core.network.CustomerDeviceTokenPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class CustomerScreenRoute {
    SPLASH,
    AUTH_OTP,
    HOME_LOCATION,
    PROFILE_EDIT,
    GOVERNANCE_SETTINGS,
    LIVE_TRACKING,
    PAYMENT_SETTLEMENT,
    NOTIFICATIONS,
    SHARED_TRIP
}

@Composable
fun CustomerAppNavigation() {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager.getInstance(context) }

    var currentScreen by remember {
        mutableStateOf(
            if (tokenManager.isLoggedIn()) CustomerScreenRoute.HOME_LOCATION else CustomerScreenRoute.AUTH_OTP
        )
    }
    var activeRideId by remember { mutableStateOf("") }
    var activeOtp by remember { mutableStateOf("849201") }
    var settlementAmount by remember { mutableIntStateOf(1600) }
    var activePooledTripId by remember { mutableStateOf("") }
    var activeSharedBookingId by remember { mutableStateOf("") }

    // App Lock state: active if PIN is configured in TokenManager
    var isAppLocked by remember { mutableStateOf(tokenManager.isAppLockEnabled()) }

    fun syncNotificationToken() {
        if (!tokenManager.isLoggedIn()) return
        runCatching {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful && !task.result.isNullOrBlank()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        runCatching {
                            com.luxury.chauffeur.customer.core.network.ApiClient.getService()
                                .registerCustomerDeviceToken(CustomerDeviceTokenPayload(task.result))
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) { syncNotificationToken() }

    Box(modifier = Modifier.fillMaxSize()) {
        when (currentScreen) {
            CustomerScreenRoute.SPLASH -> {
                SplashScreen(
                    onNavigateNext = { isLoggedIn ->
                        currentScreen = if (isLoggedIn) CustomerScreenRoute.HOME_LOCATION else CustomerScreenRoute.AUTH_OTP
                    }
                )
            }
            CustomerScreenRoute.AUTH_OTP -> {
                CustomerAuthenticationDialog(
                    onDismiss = {
                        if (tokenManager.isLoggedIn()) {
                            currentScreen = CustomerScreenRoute.HOME_LOCATION
                        }
                    },
                    onAuthenticationSuccess = { _ ->
                        syncNotificationToken()
                        isAppLocked = tokenManager.isAppLockEnabled()
                        currentScreen = CustomerScreenRoute.HOME_LOCATION
                    }
                )
            }
            CustomerScreenRoute.HOME_LOCATION -> {
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
                    onRideCancelled = { currentScreen = CustomerScreenRoute.HOME_LOCATION },
                    onProceedPayment = { _, amount ->
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
                        currentScreen = CustomerScreenRoute.HOME_LOCATION
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

        // App Lock Overlay
        if (isAppLocked && currentScreen != CustomerScreenRoute.SPLASH && currentScreen != CustomerScreenRoute.AUTH_OTP) {
            AppLockOverlay(
                onUnlocked = { isAppLocked = false }
            )
        }
    }
}
