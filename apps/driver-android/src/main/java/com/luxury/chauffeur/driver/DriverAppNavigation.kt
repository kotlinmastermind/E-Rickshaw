package com.luxury.chauffeur.driver

import androidx.compose.runtime.*
import com.luxury.chauffeur.driver.feature.dashboard.DriverDashboardScreen
import com.luxury.chauffeur.driver.feature.documents.DocumentUploadScreen
import com.luxury.chauffeur.driver.feature.trip.ActiveChauffeurTripScreen
import com.luxury.chauffeur.driver.feature.earnings.EarningsAndWalletScreen
import com.luxury.chauffeur.driver.feature.profile.DriverProfileScreen

enum class DriverScreenRoute {
    DASHBOARD,
    DOCUMENTS,
    ACTIVE_TRIP,
    EARNINGS_WALLET,
    PROFILE
}

@Composable
fun DriverAppNavigation() {
    var currentScreen by remember { mutableStateOf(DriverScreenRoute.DASHBOARD) }
    var activeTripId by remember { mutableStateOf("ride_lux_9941") }
    var activePickup by remember { mutableStateOf("The Taj Mahal Palace, Colaba") }
    var activeDest by remember { mutableStateOf("CSM T2 VIP Private Terminal") }

    when (currentScreen) {
        DriverScreenRoute.DASHBOARD -> {
            DriverDashboardScreen(
                onAcceptRide = { rideId, pickup, dest ->
                    activeTripId = rideId
                    activePickup = pickup
                    activeDest = dest
                    currentScreen = DriverScreenRoute.ACTIVE_TRIP
                },
                onNavigateDocuments = { currentScreen = DriverScreenRoute.DOCUMENTS },
                onNavigateEarnings = { currentScreen = DriverScreenRoute.EARNINGS_WALLET },
                onNavigateProfile = { currentScreen = DriverScreenRoute.PROFILE }
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
                onNavigateBack = { currentScreen = DriverScreenRoute.DASHBOARD }
            )
        }
    }
}
