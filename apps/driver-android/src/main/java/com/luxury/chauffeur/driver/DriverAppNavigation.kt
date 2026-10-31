package com.luxury.chauffeur.driver

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luxury.chauffeur.driver.core.designsystem.DriverColors
import com.luxury.chauffeur.driver.core.security.DriverTokenManager
import com.luxury.chauffeur.driver.feature.auth.DriverAuthScreen
import com.luxury.chauffeur.driver.feature.dashboard.DriverDashboardScreen
import com.luxury.chauffeur.driver.feature.documents.DocumentUploadScreen
import com.luxury.chauffeur.driver.feature.earnings.EarningsAndWalletScreen
import com.luxury.chauffeur.driver.feature.notifications.DriverNotificationScreen
import com.luxury.chauffeur.driver.feature.onboarding.DriverOnboardingScreen
import com.luxury.chauffeur.driver.feature.permissions.DriverPermissionManager
import com.luxury.chauffeur.driver.feature.permissions.DriverPermissionOnboardingScreen
import com.luxury.chauffeur.driver.feature.profile.DriverProfileScreen
import com.luxury.chauffeur.driver.feature.safety.DriverSafetySupportScreen
import com.luxury.chauffeur.driver.feature.trip.ActiveChauffeurTripScreen
import com.luxury.chauffeur.driver.feature.trip.RideHistoryScreen

enum class DriverScreenRoute {
    PERMISSION_ONBOARDING,
    AUTH,
    DASHBOARD,
    DOCUMENTS,
    ACTIVE_TRIP,
    ONBOARDING,
    SAFETY_SUPPORT,
    NOTIFICATIONS
}

enum class DriverTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    EARNINGS("Earnings", Icons.Default.Star),
    TRIPS("Trips", Icons.Default.List),
    ME("Me", Icons.Default.Person)
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
            when {
                !DriverPermissionManager.isOnboarded(context) -> DriverScreenRoute.PERMISSION_ONBOARDING
                tokenManager.isLoggedIn() -> {
                    if (!initialRideId.isNullOrBlank()) DriverScreenRoute.ACTIVE_TRIP else DriverScreenRoute.DASHBOARD
                }
                else -> DriverScreenRoute.AUTH
            }
        )
    }
    var currentTab by remember { mutableStateOf(DriverTab.HOME) }
    var activeTripId by remember { mutableStateOf(initialRideId ?: "") }
    var activePickup by remember { mutableStateOf(initialPickup ?: "") }
    var activeDest by remember { mutableStateOf(initialDest ?: "") }
    var showEmergency112Dialog by remember { mutableStateOf(false) }

    // NAV.d BackHandler rules
    BackHandler(enabled = true) {
        when {
            showEmergency112Dialog -> showEmergency112Dialog = false
            currentScreen == DriverScreenRoute.ACTIVE_TRIP -> {
                // NAV.d: Active trip me Go offline blocked, app resume par live screen. Back does not cancel trip.
                Toast.makeText(context, "Active chauffeur assignment in progress.", Toast.LENGTH_SHORT).show()
            }
            currentScreen == DriverScreenRoute.AUTH ||
            currentScreen == DriverScreenRoute.PERMISSION_ONBOARDING -> {
                (context as? Activity)?.finish()
            }
            currentScreen != DriverScreenRoute.DASHBOARD -> {
                currentScreen = DriverScreenRoute.DASHBOARD
            }
            currentTab != DriverTab.HOME -> {
                currentTab = DriverTab.HOME
            }
            else -> {
                (context as? Activity)?.finish()
            }
        }
    }

    Scaffold(
        containerColor = Color(0xFF090A0F),
        bottomBar = {
            // NAV.d: Bottom nav 4 tab: Home, Earnings, Trips, Me; trip ke dauran hide.
            val isTopLevel = currentScreen == DriverScreenRoute.DASHBOARD
            if (isTopLevel) {
                Surface(
                    color = Color(0xFF13151F),
                    border = BorderStroke(1.dp, Color(0xFF282C42)),
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
                        DriverTab.values().forEach { tab ->
                            val isSelected = currentTab == tab
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        currentTab = tab
                                    }
                                    .padding(vertical = 6.dp, horizontal = 16.dp)
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label,
                                    tint = if (isSelected) Color(0xFFD4AF37) else Color(0xFF9CA3AF),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tab.label,
                                    style = TextStyle(
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color(0xFFD4AF37) else Color(0xFF9CA3AF)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            // NAV.d Screen 67: Offline 112 floating button sab screens par
            if (currentScreen != DriverScreenRoute.AUTH) {
                FloatingActionButton(
                    onClick = { showEmergency112Dialog = true },
                    containerColor = Color(0xFFEF4444),
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(50.dp)
                ) {
                    Text("112", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                DriverScreenRoute.PERMISSION_ONBOARDING -> {
                    DriverPermissionOnboardingScreen(
                        onPermissionsCompleted = {
                            currentScreen = if (tokenManager.isLoggedIn()) {
                                DriverScreenRoute.DASHBOARD
                            } else {
                                DriverScreenRoute.AUTH
                            }
                        }
                    )
                }
                DriverScreenRoute.AUTH -> {
                    DriverAuthScreen(
                        onAuthSuccess = {
                            currentScreen = DriverScreenRoute.DASHBOARD
                            currentTab = DriverTab.HOME
                        }
                    )
                }
                DriverScreenRoute.DASHBOARD -> {
                    when (currentTab) {
                        DriverTab.HOME -> {
                            DriverDashboardScreen(
                                onAcceptRide = { rideId, pickup, dest ->
                                    activeTripId = rideId
                                    activePickup = pickup
                                    activeDest = dest
                                    currentScreen = DriverScreenRoute.ACTIVE_TRIP
                                },
                                onNavigateDocuments = { currentScreen = DriverScreenRoute.DOCUMENTS },
                                onNavigateOnboarding = { currentScreen = DriverScreenRoute.ONBOARDING },
                                onNavigateEarnings = { currentTab = DriverTab.EARNINGS },
                                onNavigateProfile = { currentTab = DriverTab.ME },
                                onNavigateSafety = { currentScreen = DriverScreenRoute.SAFETY_SUPPORT },
                                onNavigateNotifications = { currentScreen = DriverScreenRoute.NOTIFICATIONS }
                            )
                        }
                        DriverTab.EARNINGS -> {
                            EarningsAndWalletScreen(
                                onNavigateBack = { currentTab = DriverTab.HOME }
                            )
                        }
                        DriverTab.TRIPS -> {
                            RideHistoryScreen(
                                onNavigateBack = { currentTab = DriverTab.HOME },
                                onSelectRide = { rideId ->
                                    activeTripId = rideId
                                    currentScreen = DriverScreenRoute.ACTIVE_TRIP
                                }
                            )
                        }
                        DriverTab.ME -> {
                            DriverProfileScreen(
                                onNavigateBack = { currentTab = DriverTab.HOME },
                                onNavigateRideHistory = { currentTab = DriverTab.TRIPS }
                            )
                        }
                    }
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
                            currentTab = DriverTab.HOME
                            activeTripId = ""
                        }
                    )
                }
                DriverScreenRoute.ONBOARDING -> {
                    DriverOnboardingScreen(
                        onBack = { currentScreen = DriverScreenRoute.DASHBOARD }
                    )
                }
                DriverScreenRoute.SAFETY_SUPPORT -> {
                    DriverSafetySupportScreen(
                        onBack = { currentScreen = DriverScreenRoute.DASHBOARD },
                        rideId = activeTripId.takeIf { it.isNotBlank() },
                        pickupAddress = activePickup,
                        destinationAddress = activeDest,
                        onNavigateTraining = { currentScreen = DriverScreenRoute.ONBOARDING }
                    )
                }
                DriverScreenRoute.NOTIFICATIONS -> {
                    DriverNotificationScreen(
                        onBack = { currentScreen = DriverScreenRoute.DASHBOARD },
                        onOpenRide = { rideId ->
                            activeTripId = rideId
                            currentScreen = DriverScreenRoute.ACTIVE_TRIP
                        }
                    )
                }
            }

            // Screen 67: Emergency SOS 112 Confirmation Dialog
            if (showEmergency112Dialog) {
                AlertDialog(
                    onDismissRequest = { showEmergency112Dialog = false },
                    containerColor = Color(0xFF1B1E2E),
                    title = {
                        Text(
                            text = "EMERGENCY 112 DISPATCH",
                            color = Color(0xFFEF4444),
                            fontFamily = FontFamily.Serif,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = "Initiate instant connection with Government Emergency Helpline 112 and notify Fleet Safety Operations with your live GPS location?",
                            color = Color(0xFFFFFFFF),
                            fontSize = 13.sp
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showEmergency112Dialog = false
                                runCatching {
                                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))
                                    context.startActivity(dialIntent)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFEF4444),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Call 112 Now", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEmergency112Dialog = false }) {
                            Text("Cancel", color = Color(0xFF9CA3AF))
                        }
                    }
                )
            }
        }
    }
}
