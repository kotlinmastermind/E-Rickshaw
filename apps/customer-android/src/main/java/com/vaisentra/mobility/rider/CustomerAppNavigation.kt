package com.vaisentra.mobility.rider

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.vaisentra.mobility.rider.core.network.ApiClient
import com.vaisentra.mobility.core.security.TokenManager
import com.vaisentra.mobility.rider.feature.auth.CustomerAuthScreen
import com.vaisentra.mobility.rider.feature.governance.AccountGovernanceScreen
import com.vaisentra.mobility.rider.feature.location.LocationScreen
import com.vaisentra.mobility.rider.feature.notifications.NotificationScreen
import com.vaisentra.mobility.rider.feature.payments.PaymentScreen
import com.vaisentra.mobility.rider.feature.profile.ProfileEditScreen
import com.vaisentra.mobility.rider.feature.security.AppLockOverlay
import com.vaisentra.mobility.rider.feature.splash.SplashScreen
import com.vaisentra.mobility.rider.feature.tracking.LiveTrackingScreen
import com.vaisentra.mobility.core.designsystem.ElectricNeoTokens
import com.vaisentra.mobility.rider.feature.demo.CustomerDemoInspectorOverlay
import kotlinx.coroutines.withTimeoutOrNull

sealed interface CustomerDestination {
    object Auth : CustomerDestination
    object Home : CustomerDestination
    data class Tracking(val rideId: String, val startOtp: String) : CustomerDestination
    data class Payment(val rideId: String, val amountRupees: Int) : CustomerDestination
    object Profile : CustomerDestination
    object Notifications : CustomerDestination
    object Governance : CustomerDestination
}

@Composable
fun CustomerAppNavigation() {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager.getInstance(context) }

    var showSplash by remember { mutableStateOf(true) }
    var activeDemoScreenId by remember { mutableStateOf<Int?>(null) }
    var isAppLocked by remember {
        mutableStateOf(tokenManager.isLoggedIn() && tokenManager.isAppLockEnabled())
    }

    var currentDestination by remember {
        mutableStateOf<CustomerDestination>(
            if (tokenManager.isLoggedIn()) CustomerDestination.Home else CustomerDestination.Auth
        )
    }

    var isCheckingSession by remember {
        mutableStateOf(tokenManager.isLoggedIn())
    }

    // Strict Auth Verification Guard + Active Ride Restoration on Rider Launch
    LaunchedEffect(Unit) {
        if (tokenManager.isLoggedIn()) {
            if (tokenManager.isAppLockEnabled()) {
                isAppLocked = true
            }
            try {
                withTimeoutOrNull(4000L) {
                    val res = ApiClient.getService().getCurrentActiveRide()
                    if (res.isSuccessful && res.body() != null) {
                        val activeObj = res.body()!!["active_ride"] as? Map<*, *>
                        if (activeObj != null) {
                            val rId = activeObj["id"]?.toString().orEmpty()
                            val st = activeObj["status"]?.toString()?.uppercase().orEmpty()
                            val otp = activeObj["start_otp"]?.toString().orEmpty().ifBlank { tokenManager.getActiveRideOtp().orEmpty() }
                            if (rId.isNotBlank() && st in listOf("REQUESTED", "ASSIGNED", "ARRIVED", "IN_PROGRESS")) {
                                tokenManager.saveActiveRideId(rId)
                                tokenManager.saveActiveRideOtp(otp)
                                currentDestination = CustomerDestination.Tracking(rId, otp)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                val cachedRideId = tokenManager.getActiveRideId()
                val cachedOtp = tokenManager.getActiveRideOtp().orEmpty()
                if (!cachedRideId.isNullOrBlank()) {
                    currentDestination = CustomerDestination.Tracking(cachedRideId, cachedOtp)
                }
            } finally {
                isCheckingSession = false
            }
        } else {
            currentDestination = CustomerDestination.Auth
            isCheckingSession = false
        }
    }

    BackHandler(enabled = !showSplash && !isAppLocked && currentDestination !is CustomerDestination.Home && currentDestination !is CustomerDestination.Auth) {
        when (currentDestination) {
            is CustomerDestination.Profile,
            is CustomerDestination.Notifications,
            is CustomerDestination.Governance -> {
                currentDestination = CustomerDestination.Home
            }
            is CustomerDestination.Tracking -> {
                Toast.makeText(context, "Ride is currently active. Use cancel or emergency options on screen.", Toast.LENGTH_SHORT).show()
            }
            is CustomerDestination.Payment -> {
                currentDestination = CustomerDestination.Home
            }
            else -> {}
        }
    }

    CustomerDemoInspectorOverlay(
        activeScreenId = activeDemoScreenId,
        onSelectScreen = { activeDemoScreenId = it }
    ) {
        Surface(
            color = ElectricNeoTokens.CyberVoid,
            modifier = Modifier.fillMaxSize()
        ) {
            if (showSplash) {
                SplashScreen(
                    onNavigateNext = {
                        showSplash = false
                    }
                )
            } else if (isAppLocked) {
                AppLockOverlay(
                    onUnlocked = {
                        isAppLocked = false
                    }
                )
            } else if (isCheckingSession) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ElectricNeoTokens.ElectricCyan)
                }
            } else {
                when (val dest = currentDestination) {
                    CustomerDestination.Auth -> {
                        CustomerAuthScreen(
                            onAuthSuccess = {
                                if (tokenManager.isAppLockEnabled()) {
                                    isAppLocked = true
                                }
                                currentDestination = CustomerDestination.Home
                            }
                        )
                    }
                    CustomerDestination.Home -> {
                        LocationScreen(
                            onRideRequested = { rideId, otp ->
                                tokenManager.saveActiveRideId(rideId)
                                tokenManager.saveActiveRideOtp(otp)
                                currentDestination = CustomerDestination.Tracking(rideId, otp)
                            },
                            onNavigateProfile = {
                                currentDestination = CustomerDestination.Profile
                            },
                            onNavigateNotifications = {
                                currentDestination = CustomerDestination.Notifications
                            },
                            onNavigateGovernance = {
                                currentDestination = CustomerDestination.Governance
                            },
                            onSharedRideBooked = { rideId, otp, _, _ ->
                                tokenManager.saveActiveRideId(rideId)
                                tokenManager.saveActiveRideOtp(otp)
                                currentDestination = CustomerDestination.Tracking(rideId, otp)
                            }
                        )
                    }
                    is CustomerDestination.Tracking -> {
                        LiveTrackingScreen(
                            rideId = dest.rideId,
                            startOtp = dest.startOtp,
                            onRideCancelled = {
                                tokenManager.clearActiveRideId()
                                currentDestination = CustomerDestination.Home
                            },
                            onProceedPayment = { rId, amt ->
                                tokenManager.clearActiveRideId()
                                currentDestination = CustomerDestination.Payment(rId, amt)
                            }
                        )
                    }
                    is CustomerDestination.Payment -> {
                        PaymentScreen(
                            rideId = dest.rideId,
                            amountRupees = dest.amountRupees,
                            onPaymentAndReviewCompleted = {
                                tokenManager.clearActiveRideId()
                                currentDestination = CustomerDestination.Home
                            }
                        )
                    }
                    CustomerDestination.Profile -> {
                        ProfileEditScreen(
                            onNavigateBack = {
                                currentDestination = CustomerDestination.Home
                            },
                            onNavigateGovernance = {
                                currentDestination = CustomerDestination.Governance
                            },
                            onSignOut = {
                                tokenManager.clear()
                                isAppLocked = false
                                currentDestination = CustomerDestination.Auth
                            }
                        )
                    }
                    CustomerDestination.Notifications -> {
                        NotificationScreen(
                            onNavigateBack = {
                                currentDestination = CustomerDestination.Home
                            }
                        )
                    }
                    CustomerDestination.Governance -> {
                        AccountGovernanceScreen(
                            onNavigateBack = {
                                currentDestination = CustomerDestination.Home
                            }
                        )
                    }
                }
            }
        }
    }
}
