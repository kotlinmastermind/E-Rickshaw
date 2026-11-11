package com.vaisentra.mobility.driver

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
import com.vaisentra.mobility.driver.core.designsystem.DriverColors
import com.vaisentra.mobility.driver.core.network.DriverApiClient
import com.vaisentra.mobility.core.security.TokenManager
import com.vaisentra.mobility.driver.feature.auth.DriverAuthScreen
import com.vaisentra.mobility.driver.feature.dashboard.DriverDashboardScreen
import com.vaisentra.mobility.driver.feature.documents.DocumentUploadScreen
import com.vaisentra.mobility.driver.feature.duty.DriverDutyRepository
import com.vaisentra.mobility.driver.feature.earnings.EarningsAndWalletScreen
import com.vaisentra.mobility.driver.feature.notifications.DriverNotificationScreen
import com.vaisentra.mobility.driver.feature.onboarding.DriverOnboardingScreen
import com.vaisentra.mobility.driver.feature.profile.DriverProfileScreen
import com.vaisentra.mobility.driver.feature.safety.DriverSafetySupportScreen
import com.vaisentra.mobility.driver.feature.trip.ActiveChauffeurTripScreen
import com.vaisentra.mobility.driver.feature.trip.RideHistoryScreen
import com.vaisentra.mobility.driver.feature.trip.RideSummaryScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface DriverDestination {
    object Auth : DriverDestination
    object Dashboard : DriverDestination
    data class ActiveTrip(val rideId: String, val pickupAddress: String, val destinationAddress: String) : DriverDestination
    data class RideSummary(val rideId: String, val pickupAddress: String, val destinationAddress: String) : DriverDestination
    object RideHistory : DriverDestination
    object Documents : DriverDestination
    object Onboarding : DriverDestination
    object Earnings : DriverDestination
    object Profile : DriverDestination
    object Safety : DriverDestination
    object Notifications : DriverDestination
}

@Composable
fun DriverAppNavigation(
    initialRideId: String? = null,
    initialPickup: String? = null,
    initialDest: String? = null,
    initialFare: String? = null
) {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager.getDriverInstance(context) }
    val dutyRepo = remember { DriverDutyRepository.getInstance(context) }
    val scope = rememberCoroutineScope()

    var currentDestination by remember {
        mutableStateOf<DriverDestination>(
            if (!tokenManager.isLoggedIn()) {
                DriverDestination.Auth
            } else if (!initialRideId.isNullOrBlank()) {
                DriverDestination.ActiveTrip(
                    rideId = initialRideId,
                    pickupAddress = initialPickup ?: "Commuter Pickup Station",
                    destinationAddress = initialDest ?: "Destination Station"
                )
            } else {
                DriverDestination.Dashboard
            }
        )
    }

    var isCheckingSession by remember {
        mutableStateOf(tokenManager.isLoggedIn() && initialRideId.isNullOrBlank())
    }

    // Strict Auth Verification Guard + Duty Availability Reconciliation + Active Trip Restoration
    LaunchedEffect(Unit) {
        if (tokenManager.isLoggedIn()) {
            dutyRepo.reconcileAvailability()
            if (initialRideId.isNullOrBlank()) {
                try {
                    withTimeoutOrNull(4000L) {
                        val meRes = DriverApiClient.getService().getMe()
                        if (meRes.isSuccessful && meRes.body() != null) {
                            val activeRideObj = meRes.body()!!["active_ride"] as? Map<*, *>
                            if (activeRideObj != null) {
                                val rId = activeRideObj["id"]?.toString().orEmpty()
                                val pickup = activeRideObj["pickup_address"]?.toString() ?: "Pickup Station"
                                val dest = activeRideObj["destination_address"]?.toString() ?: "Destination Point"
                                if (rId.isNotBlank()) {
                                    tokenManager.saveActiveRideId(rId)
                                    currentDestination = DriverDestination.ActiveTrip(rId, pickup, dest)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    val cachedRideId = tokenManager.getActiveRideId()
                    if (!cachedRideId.isNullOrBlank()) {
                        currentDestination = DriverDestination.ActiveTrip(
                            rideId = cachedRideId,
                            pickupAddress = "Commuter Pickup Station",
                            destinationAddress = "Destination Point"
                        )
                    }
                } finally {
                    isCheckingSession = false
                }
            }
        }
    }

    BackHandler(enabled = currentDestination !is DriverDestination.Dashboard && currentDestination !is DriverDestination.Auth) {
        when (currentDestination) {
            is DriverDestination.Documents,
            is DriverDestination.Onboarding,
            is DriverDestination.Earnings,
            is DriverDestination.Profile,
            is DriverDestination.Safety,
            is DriverDestination.Notifications -> {
                currentDestination = DriverDestination.Dashboard
            }
            is DriverDestination.RideHistory -> {
                currentDestination = DriverDestination.Profile
            }
            is DriverDestination.ActiveTrip -> {
                Toast.makeText(context, "Active passenger trip in progress. Follow route navigation.", Toast.LENGTH_SHORT).show()
            }
            is DriverDestination.RideSummary -> {
                currentDestination = DriverDestination.Dashboard
            }
            else -> {}
        }
    }

    Surface(
        color = Color(0xFF0A0D0B),
        modifier = Modifier.fillMaxSize()
    ) {
        if (isCheckingSession) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DriverColors.ChauffeurGold)
            }
        } else {
            when (val dest = currentDestination) {
                DriverDestination.Auth -> {
                    DriverAuthScreen(
                        onAuthenticationSuccess = { _ ->
                            scope.launch {
                                dutyRepo.reconcileAvailability()
                            }
                            currentDestination = DriverDestination.Dashboard
                        }
                    )
                }
                DriverDestination.Dashboard -> {
                    DriverDashboardScreen(
                        onAcceptRide = { rideId, pickup, destination ->
                            tokenManager.saveActiveRideId(rideId)
                            currentDestination = DriverDestination.ActiveTrip(rideId, pickup, destination)
                        },
                        onNavigateDocuments = {
                            currentDestination = DriverDestination.Documents
                        },
                        onNavigateOnboarding = {
                            currentDestination = DriverDestination.Onboarding
                        },
                        onNavigateEarnings = {
                            currentDestination = DriverDestination.Earnings
                        },
                        onNavigateProfile = {
                            currentDestination = DriverDestination.Profile
                        },
                        onNavigateSafety = {
                            currentDestination = DriverDestination.Safety
                        },
                        onNavigateNotifications = {
                            currentDestination = DriverDestination.Notifications
                        }
                    )
                }
                is DriverDestination.ActiveTrip -> {
                    ActiveChauffeurTripScreen(
                        rideId = dest.rideId,
                        pickupAddress = dest.pickupAddress,
                        destinationAddress = dest.destinationAddress,
                        onTripFinished = {
                            tokenManager.clearActiveRideId()
                            currentDestination = DriverDestination.RideSummary(
                                rideId = dest.rideId,
                                pickupAddress = dest.pickupAddress,
                                destinationAddress = dest.destinationAddress
                            )
                        }
                    )
                }
                is DriverDestination.RideSummary -> {
                    RideSummaryScreen(
                        rideId = dest.rideId,
                        pickupAddress = dest.pickupAddress,
                        destinationAddress = dest.destinationAddress,
                        onConfirmAndClose = { _, _ ->
                            currentDestination = DriverDestination.Dashboard
                        }
                    )
                }
                DriverDestination.RideHistory -> {
                    RideHistoryScreen(
                        onNavigateBack = {
                            currentDestination = DriverDestination.Profile
                        }
                    )
                }
                DriverDestination.Documents -> {
                    DocumentUploadScreen(
                        onNavigateBack = {
                            currentDestination = DriverDestination.Dashboard
                        }
                    )
                }
                DriverDestination.Onboarding -> {
                    DriverOnboardingScreen(
                        onBack = {
                            currentDestination = DriverDestination.Dashboard
                        }
                    )
                }
                DriverDestination.Earnings -> {
                    EarningsAndWalletScreen(
                        onNavigateBack = {
                            currentDestination = DriverDestination.Dashboard
                        }
                    )
                }
                DriverDestination.Profile -> {
                    DriverProfileScreen(
                        onNavigateBack = {
                            currentDestination = DriverDestination.Dashboard
                        },
                        onNavigateRideHistory = {
                            currentDestination = DriverDestination.RideHistory
                        },
                        onSignOut = {
                            scope.launch {
                                runCatching { dutyRepo.setDutyOffline() }
                                tokenManager.clear()
                                currentDestination = DriverDestination.Auth
                            }
                        }
                    )
                }
                DriverDestination.Safety -> {
                    DriverSafetySupportScreen(
                        onBack = {
                            currentDestination = DriverDestination.Dashboard
                        }
                    )
                }
                DriverDestination.Notifications -> {
                    DriverNotificationScreen(
                        onBack = {
                            currentDestination = DriverDestination.Dashboard
                        }
                    )
                }
            }
        }
    }
}
