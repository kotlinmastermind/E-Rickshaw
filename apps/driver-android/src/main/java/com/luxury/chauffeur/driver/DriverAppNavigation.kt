package com.luxury.chauffeur.driver

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.luxury.chauffeur.driver.core.security.DriverTokenManager
import com.luxury.chauffeur.driver.feature.auth.DriverAuthenticationDialog
import com.luxury.chauffeur.driver.feature.dashboard.DriverDashboardScreen
import com.luxury.chauffeur.driver.feature.documents.DocumentUploadScreen
import com.luxury.chauffeur.driver.feature.trip.ActiveChauffeurTripScreen
import com.luxury.chauffeur.driver.feature.earnings.EarningsAndWalletScreen
import com.luxury.chauffeur.driver.feature.profile.DriverProfileScreen
import com.luxury.chauffeur.driver.feature.onboarding.DriverOnboardingScreen
import com.luxury.chauffeur.driver.feature.safety.DriverSafetySupportScreen
import com.luxury.chauffeur.driver.feature.notifications.DriverNotificationScreen

import com.luxury.chauffeur.driver.feature.auth.DriverAuthScreen
import com.luxury.chauffeur.driver.feature.dashboard.DriverDashboardScreen
import com.luxury.chauffeur.driver.feature.trip.RideHistoryScreen

enum class DriverScreenRoute {
    AUTH,
    DASHBOARD,
    DOCUMENTS,
    ACTIVE_TRIP,
    EARNINGS_WALLET,
    PROFILE,
    RIDE_HISTORY,
    ONBOARDING,
    SAFETY_SUPPORT,
    NOTIFICATIONS
}

@Composable
fun DriverAppNavigation(
    initialRideId: String? = null,
    initialPickup: String? = null,
    initialDest: String? = null,
    initialFare: String? = null
) {
    val context = LocalContext.current
    val tokenManager = remember { DriverTokenManager.getInstance(context) }

    var currentScreen by remember {
        mutableStateOf(
            if (tokenManager.isLoggedIn()) {
                if (!initialRideId.isNullOrBlank()) DriverScreenRoute.ACTIVE_TRIP else DriverScreenRoute.DASHBOARD
            } else DriverScreenRoute.AUTH
        )
    }
    var activeTripId by remember { mutableStateOf(initialRideId ?: "") }
    var activePickup by remember { mutableStateOf(initialPickup ?: "") }
    var activeDest by remember { mutableStateOf(initialDest ?: "") }

    when (currentScreen) {
        DriverScreenRoute.AUTH -> {
            DriverAuthScreen(
                onAuthSuccess = {
                    currentScreen = DriverScreenRoute.DASHBOARD
                }
            )
        }
        DriverScreenRoute.DASHBOARD -> {
            DriverDashboardScreen(
                onAcceptRide = { rideId, pickup, dest ->
                    activeTripId = rideId
                    activePickup = pickup
                    activeDest = dest
                    currentScreen = DriverScreenRoute.ACTIVE_TRIP
                },
                onNavigateDocuments = { currentScreen = DriverScreenRoute.DOCUMENTS },
                onNavigateOnboarding = { currentScreen = DriverScreenRoute.ONBOARDING },
                onNavigateEarnings = { currentScreen = DriverScreenRoute.EARNINGS_WALLET },
                onNavigateProfile = { currentScreen = DriverScreenRoute.PROFILE },
                onNavigateSafety = { currentScreen = DriverScreenRoute.SAFETY_SUPPORT },
                onNavigateNotifications = { currentScreen = DriverScreenRoute.NOTIFICATIONS }
            )
        }
        DriverScreenRoute.DOCUMENTS -> {
            DocumentUploadScreen(
                onNavigateBack = { currentScreen = DriverScreenRoute.DASHBOARD }
            )
        }
        DriverScreenRoute.ACTIVE_TRIP -> {
            ActiveChauffeurTripScreen(
                rideId = activeTripId,
                pickupAddress = activePickup,
                destinationAddress = activeDest,
                onTripFinished = {
                    currentScreen = DriverScreenRoute.DASHBOARD
                    activeTripId = ""
                }
            )
        }
        DriverScreenRoute.EARNINGS_WALLET -> {
            EarningsAndWalletScreen(
                onNavigateBack = { currentScreen = DriverScreenRoute.DASHBOARD }
            )
        }
        DriverScreenRoute.PROFILE -> {
            DriverProfileScreen(
                onNavigateBack = { currentScreen = DriverScreenRoute.DASHBOARD },
                onNavigateRideHistory = { currentScreen = DriverScreenRoute.RIDE_HISTORY }
            )
        }
        DriverScreenRoute.RIDE_HISTORY -> {
            RideHistoryScreen(
                onNavigateBack = { currentScreen = DriverScreenRoute.PROFILE },
                onSelectRide = { rideId ->
                    activeTripId = rideId
                    currentScreen = DriverScreenRoute.ACTIVE_TRIP
                }
            )
        }
        DriverScreenRoute.ONBOARDING -> DriverOnboardingScreen(onBack = { currentScreen = DriverScreenRoute.DASHBOARD })
        DriverScreenRoute.SAFETY_SUPPORT -> DriverSafetySupportScreen(onBack = { currentScreen = DriverScreenRoute.DASHBOARD }, rideId = activeTripId.takeIf { it.isNotBlank() }, pickupAddress = activePickup, destinationAddress = activeDest, onNavigateTraining = { currentScreen = DriverScreenRoute.ONBOARDING })
        DriverScreenRoute.NOTIFICATIONS -> DriverNotificationScreen(onBack = { currentScreen = DriverScreenRoute.DASHBOARD }, onOpenRide = { rideId -> activeTripId = rideId; currentScreen = DriverScreenRoute.ACTIVE_TRIP })
    }
}
