package com.luxury.chauffeur.customer

import androidx.compose.runtime.*
import com.luxury.chauffeur.customer.feature.splash.SplashScreen
import com.luxury.chauffeur.customer.feature.auth.OtpAuthScreen
import com.luxury.chauffeur.customer.feature.location.LocationScreen
import com.luxury.chauffeur.customer.feature.profile.ProfileEditScreen
import com.luxury.chauffeur.customer.feature.tracking.LiveTrackingScreen
import com.luxury.chauffeur.customer.feature.payments.PaymentScreen
import com.luxury.chauffeur.customer.feature.notifications.NotificationScreen

enum class CustomerScreenRoute {
    SPLASH,
    AUTH_OTP,
    HOME_LOCATION,
    PROFILE_EDIT,
    LIVE_TRACKING,
    PAYMENT_SETTLEMENT,
    NOTIFICATIONS
}

@Composable
fun CustomerAppNavigation() {
    var currentScreen by remember { mutableStateOf(CustomerScreenRoute.SPLASH) }
    var activeRideId by remember { mutableStateOf("ride_lux_001") }
    var activeOtp by remember { mutableStateOf("849201") }
    var settlementAmount by remember { mutableIntStateOf(1600) }

    when (currentScreen) {
        CustomerScreenRoute.SPLASH -> {
            SplashScreen(
                onNavigateNext = { isLoggedIn ->
                    currentScreen = if (isLoggedIn) CustomerScreenRoute.HOME_LOCATION else CustomerScreenRoute.AUTH_OTP
                }
            )
        }
        CustomerScreenRoute.AUTH_OTP -> {
            OtpAuthScreen(
                onAuthSuccess = {
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
                onNavigateNotifications = { currentScreen = CustomerScreenRoute.NOTIFICATIONS }
            )
        }
        CustomerScreenRoute.PROFILE_EDIT -> {
            ProfileEditScreen(
                onNavigateBack = { currentScreen = CustomerScreenRoute.HOME_LOCATION }
            )
        }
        CustomerScreenRoute.LIVE_TRACKING -> {
            LiveTrackingScreen(
                rideId = activeRideId,
                startOtp = activeOtp,
                onRideCancelled = { currentScreen = CustomerScreenRoute.HOME_LOCATION },
                onProceedPayment = { rideId, amount ->
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
                onNavigateBack = { currentScreen = CustomerScreenRoute.HOME_LOCATION }
            )
        }
    }
}
