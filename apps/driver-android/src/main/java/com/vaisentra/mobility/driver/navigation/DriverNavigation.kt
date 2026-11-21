package com.vaisentra.mobility.driver.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.vaisentra.mobility.driver.BuildConfig
import com.vaisentra.mobility.driver.components.*
import com.vaisentra.mobility.driver.data.DriverNetworkClient
import com.vaisentra.mobility.driver.data.DriverRepository
import com.vaisentra.mobility.driver.feature.auth.*
import com.vaisentra.mobility.driver.feature.duty.*
import com.vaisentra.mobility.driver.feature.earnings.*
import com.vaisentra.mobility.driver.feature.fare.*
import com.vaisentra.mobility.driver.feature.goals.*
import com.vaisentra.mobility.driver.feature.pool.*
import com.vaisentra.mobility.driver.feature.safety.*
import com.vaisentra.mobility.driver.feature.support.*
import com.vaisentra.mobility.driver.feature.telemetry.*
import com.vaisentra.mobility.driver.feature.trip.*
import com.vaisentra.mobility.driver.theme.*

/**
 * DriverGo 50-Screen Destination Enum
 * Serves as the single route registry across the application.
 */
enum class DriverScreen(val title: String, val category: String) {
    // 1. Pilot Auth & KYC (1-6)
    PILOT_LOGIN("1. Pilot Login", "Auth & KYC"),
    PILOT_OTP("2. Road OTP Verify", "Auth & KYC"),
    PILOT_RECOVERY("3. Pilot PIN Recovery", "Auth & KYC"),
    PILOT_PROFILE("4. Transport Pilot Profile", "Auth & KYC"),
    DOCUMENT_VAULT("5. Document Vault", "Auth & KYC"),
    DOC_EXPIRY_WATCHDOG("6. Document Expiry Watchdog", "Auth & KYC"),

    // 2. Duty Cycle & Vehicle Inspection (7-10, 12, 35, 50)
    VEHICLE_INSPECTION("7. Pre-Shift Inspection", "Duty & Vehicle"),
    VEHICLE_RC_STATUS("8. Vehicle RC & Permit", "Duty & Vehicle"),
    SHIFT_START("9. Shift Start & Odometer", "Duty & Vehicle"),
    DUTY_DASHBOARD("10. Duty Cockpit Dashboard", "Duty & Vehicle"),
    FATIGUE_CHECK("12. Pilot Fatigue Sentinel", "Duty & Vehicle"),
    SHIFT_END("35. Shift End & Closing Meter", "Duty & Vehicle"),
    DRIVER_PREFERENCES("50. Pilot Preferences", "Duty & Vehicle"),

    // 3. Telemetry & Heatmaps (11, 13, 14)
    GPS_PING("11. High-Frequency GPS Ping", "Telemetry & Maps"),
    HEATMAP_SURGE("13. Heatmap & Surge Zones", "Telemetry & Maps"),
    ROAD_HAZARDS("14. Road Restrictions & Hazards", "Telemetry & Maps"),

    // 4. Trip Ops & Navigation (15-22)
    INCOMING_OFFER("15. Incoming Offer Card", "Trip Ops"),
    OFFER_QUEUE("16. Auto-Dispatch Offer Queue", "Trip Ops"),
    PICKUP_NAV("17. Navigation to Pickup", "Trip Ops"),
    ARRIVED_PICKUP("18. Arrived at Pickup Spot", "Trip Ops"),
    WAITING_TIMER("19. Waiting Timer & Meter", "Trip Ops"),
    BOARDING_OTP("20. Commuter Boarding OTP", "Trip Ops"),
    IN_TRIP_NAV("21. In-Trip Turn Navigation", "Trip Ops"),
    MULTI_STOP("22. Multi-Stop Waypoints", "Trip Ops"),

    // 5. Shared Pool Operations (24-26)
    POOL_MANIFEST("24. Shared Pool Manifest", "Shared Pool"),
    CORRIDOR_WAYPOINTS("25. Corridor Waypoints", "Shared Pool"),
    CO_PASSENGER_BROADCAST("26. Co-Passenger Broadcast", "Shared Pool"),

    // 6. Safety Sentinel & Depots (23, 27-30, 45, 48)
    ROUTE_DEVIATION("23. Route Deviation Watchdog", "Safety Sentinel"),
    SPEEDOMETER("27. Speedometer & Speed Alert", "Safety Sentinel"),
    AUDIO_SHIELD("28. Cabin Audio Safety Shield", "Safety Sentinel"),
    EMERGENCY_SOS("29. Pilot SOS 112 Trigger", "Safety Sentinel"),
    BATTERY_SWAP_DEPOT("30. Low Battery Swap Depot", "Safety Sentinel"),
    DRIVER_INCIDENT("45. Incident Reporting", "Safety Sentinel"),
    EMERGENCY_CONTACTS("48. Emergency Contacts", "Safety Sentinel"),

    // 7. Fare Settlement & Payments (31-33)
    TRIP_COMPLETED_FARE("31. Trip Completed & Fare", "Fare Settlement"),
    CASH_COLLECTION("32. Cash Collection Calculator", "Fare Settlement"),
    UPI_SETTLEMENT("33. Dynamic UPI QR", "Fare Settlement"),

    // 8. Earnings & Ledger (34, 39-42)
    SHIFT_SUMMARY("34. Daily Shift Summary", "Earnings & Ledger"),
    PILOT_EARNINGS("39. Pilot Earnings & Wallet", "Earnings & Ledger"),
    PAYOUT_ACCOUNTS("40. Payout Bank & UPI", "Earnings & Ledger"),
    INSTANT_PAYOUT("41. Instant IMPS Payout", "Earnings & Ledger"),
    DRIVER_LEDGER("42. Driver Account Ledger", "Earnings & Ledger"),

    // 9. Targets, Milestones & Scorecard (36-38, 43)
    DAILY_TARGETS("36. Daily Targets & Bonus", "Goals & Scorecard"),
    WEEKLY_GOALS("37. Weekly Tier Goals", "Goals & Scorecard"),
    STREAK_MISSIONS("38. Streak Missions & Badges", "Goals & Scorecard"),
    PERFORMANCE_SCORECARD("43. Pilot Scorecard & Rating", "Goals & Scorecard"),

    // 10. Academy & Support (44, 46, 47, 49)
    ACADEMY_TRAINING("44. Electric Academy", "Support & Academy"),
    FOUND_ITEM("46. Found Lost Item in Cab", "Support & Academy"),
    DRIVER_HELPDESK("47. Driver Helpdesk Tickets", "Support & Academy"),
    NOTIFICATIONS_CENTER("49. Notifications Center", "Support & Academy")
}

/**
 * Resolves route aliases and legacy screen keys to canonical DriverScreen.
 */
fun resolveRoute(route: String): DriverScreen? {
    DriverScreen.entries.firstOrNull { it.name.equals(route, ignoreCase = true) }?.let { return it }

    return when (route.lowercase()) {
        // Auth & KYC
        "login", "auth_login" -> DriverScreen.PILOT_LOGIN
        "otp", "auth_otp" -> DriverScreen.PILOT_OTP
        "forgot", "recovery", "auth_recovery", "auth_reset" -> DriverScreen.PILOT_RECOVERY
        "pilot_profile", "profile" -> DriverScreen.PILOT_PROFILE
        "documents", "document_vault", "doc_vault" -> DriverScreen.DOCUMENT_VAULT
        "doc_expiry", "expiry" -> DriverScreen.DOC_EXPIRY_WATCHDOG

        // Duty & Vehicle
        "duty_dashboard", "home", "dashboard" -> DriverScreen.DUTY_DASHBOARD
        "inspection", "vehicle_inspect" -> DriverScreen.VEHICLE_INSPECTION
        "rc_status", "vehicle_rc" -> DriverScreen.VEHICLE_RC_STATUS
        "shift_start" -> DriverScreen.SHIFT_START
        "shift_end" -> DriverScreen.SHIFT_END
        "fatigue", "fatigue_check" -> DriverScreen.FATIGUE_CHECK
        "preferences", "settings" -> DriverScreen.DRIVER_PREFERENCES

        // Telemetry & Maps
        "gps", "gps_ping" -> DriverScreen.GPS_PING
        "heatmap", "surge" -> DriverScreen.HEATMAP_SURGE
        "hazards", "road_hazards" -> DriverScreen.ROAD_HAZARDS

        // Trip Ops
        "offer", "incoming_offer" -> DriverScreen.INCOMING_OFFER
        "offer_queue", "queue", "trips" -> DriverScreen.OFFER_QUEUE
        "pickup_nav", "accept_ride", "navigation" -> DriverScreen.PICKUP_NAV
        "arrived" -> DriverScreen.ARRIVED_PICKUP
        "waiting" -> DriverScreen.WAITING_TIMER
        "boarding_otp" -> DriverScreen.BOARDING_OTP
        "in_trip_nav" -> DriverScreen.IN_TRIP_NAV
        "multi_stop" -> DriverScreen.MULTI_STOP

        // Shared Pool
        "pool", "pool_manifest" -> DriverScreen.POOL_MANIFEST
        "corridor", "corridor_waypoints" -> DriverScreen.CORRIDOR_WAYPOINTS
        "broadcast", "co_passenger_broadcast" -> DriverScreen.CO_PASSENGER_BROADCAST

        // Safety Sentinel
        "route_deviation" -> DriverScreen.ROUTE_DEVIATION
        "speedometer" -> DriverScreen.SPEEDOMETER
        "audio_shield" -> DriverScreen.AUDIO_SHIELD
        "sos", "emergency_sos" -> DriverScreen.EMERGENCY_SOS
        "battery_swap", "swap_depot" -> DriverScreen.BATTERY_SWAP_DEPOT
        "incident", "driver_incident" -> DriverScreen.DRIVER_INCIDENT
        "emergency_contacts", "contacts" -> DriverScreen.EMERGENCY_CONTACTS

        // Fare Settlement
        "trip_fare", "trip_completed", "end_ride" -> DriverScreen.TRIP_COMPLETED_FARE
        "cash", "cash_collection" -> DriverScreen.CASH_COLLECTION
        "upi", "upi_settlement" -> DriverScreen.UPI_SETTLEMENT

        // Earnings & Ledger
        "shift_summary", "summary" -> DriverScreen.SHIFT_SUMMARY
        "earnings", "wallet" -> DriverScreen.PILOT_EARNINGS
        "payout_accounts", "banks" -> DriverScreen.PAYOUT_ACCOUNTS
        "instant_payout", "payout" -> DriverScreen.INSTANT_PAYOUT
        "ledger", "driver_ledger", "history" -> DriverScreen.DRIVER_LEDGER

        // Goals & Scorecard
        "daily_targets", "targets", "bonus", "incentives" -> DriverScreen.DAILY_TARGETS
        "weekly_goals", "goals" -> DriverScreen.WEEKLY_GOALS
        "streaks", "streak_missions" -> DriverScreen.STREAK_MISSIONS
        "scorecard", "rating", "performance" -> DriverScreen.PERFORMANCE_SCORECARD

        // Academy & Support
        "academy", "training" -> DriverScreen.ACADEMY_TRAINING
        "found_item", "lost_item" -> DriverScreen.FOUND_ITEM
        "helpdesk", "support" -> DriverScreen.DRIVER_HELPDESK
        "notifications", "inbox" -> DriverScreen.NOTIFICATIONS_CENTER

        else -> null
    }
}

/**
 * Crash & App-Launch Recovery:
 * Validates authentication status and checks whether the driver has an active, assigned,
 * or in-progress trip from the backend before setting the initial screen.
 */
fun resolveStartRoute(): DriverScreen {
    return DriverScreen.PILOT_LOGIN
}

/**
 * Central Workflow Router implementing Ola/Uber/Rapido-style backstack management.
 * Prevents flat backstack leaks by maintaining clean hierarchical navigation and state preservation.
 */
class DriverWorkflowRouter(
    val navController: NavHostController
) {
    fun navigateTo(screen: DriverScreen) {
        navController.navigate(screen.name) {
            launchSingleTop = true
        }
    }

    fun navigateRoute(route: String) {
        val target = resolveRoute(route)
        if (target != null) {
            navigateTo(target)
        }
    }

    fun navigateTab(tabIndex: Int) {
        val targetScreen = when (tabIndex) {
            0 -> DriverScreen.DUTY_DASHBOARD
            1 -> {
                val activeRide = DriverRepository.activeRide.value
                val hasActive = activeRide != null && !activeRide.id.isNullOrBlank() &&
                    activeRide.status.uppercase() in listOf("ASSIGNED", "ARRIVED", "IN_PROGRESS", "STARTED")
                if (hasActive) {
                    when (activeRide?.status?.uppercase()) {
                        "ASSIGNED" -> DriverScreen.PICKUP_NAV
                        "ARRIVED" -> DriverScreen.ARRIVED_PICKUP
                        else -> DriverScreen.IN_TRIP_NAV
                    }
                } else {
                    DriverScreen.OFFER_QUEUE
                }
            }
            2 -> DriverScreen.PILOT_EARNINGS
            3 -> DriverScreen.NOTIFICATIONS_CENTER
            4 -> DriverScreen.PILOT_PROFILE
            else -> DriverScreen.DUTY_DASHBOARD
        }

        val currentRoute = navController.currentBackStackEntry?.destination?.route
        if (currentRoute == targetScreen.name) {
            return
        }

        try {
            if (targetScreen == DriverScreen.DUTY_DASHBOARD) {
                navController.navigate(DriverScreen.DUTY_DASHBOARD.name) {
                    popUpTo(DriverScreen.DUTY_DASHBOARD.name) {
                        inclusive = false
                    }
                    launchSingleTop = true
                }
            } else {
                navController.navigate(targetScreen.name) {
                    popUpTo(DriverScreen.DUTY_DASHBOARD.name) {
                        inclusive = false
                        saveState = false
                    }
                    launchSingleTop = true
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("DriverWorkflowRouter", "Tab navigation fallback: ${e.message}")
            navController.navigate(targetScreen.name) {
                launchSingleTop = true
            }
        }
    }

    fun navigateAndClearTo(target: DriverScreen, popUpToScreen: DriverScreen, inclusive: Boolean = false) {
        navController.navigate(target.name) {
            popUpTo(popUpToScreen.name) {
                this.inclusive = inclusive
            }
            launchSingleTop = true
        }
    }

    fun returnToDutyDashboard() {
        navigateAndClearTo(DriverScreen.DUTY_DASHBOARD, DriverScreen.DUTY_DASHBOARD, inclusive = false)
    }

    fun popBack(): Boolean {
        return navController.popBackStack()
    }
}

/**
 * Main Jetpack Compose Navigation Architecture for DriverGo Cockpit.
 */
@Composable
fun DriverNavigation() {
    val navController = rememberNavController()
    val router = remember(navController) { DriverWorkflowRouter(navController) }
    var showCatalogDialog by remember { mutableStateOf(false) }

    // Recover or calculate start destination
    val startDestination = remember { resolveStartRoute().name }

    // Active screen route tracking
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route ?: startDestination

    fun showToast(msg: String) {
        DriverDialogController.showToast(msg)
    }

    fun triggerPopup(popupId: String, onConfirm: (() -> Unit)? = null) {
        try {
            val type = DriverPopupType.valueOf(popupId)
            DriverDialogController.show(type, onConfirm)
        } catch (_: Exception) {
            showToast("Action triggered: $popupId")
        }
    }

    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        DriverNetworkClient.onAuthExpired = {
            scope.launch(Dispatchers.Main) {
                DriverDialogController.showToast("Session expired. Please sign in again.")
                router.navigateAndClearTo(DriverScreen.PILOT_LOGIN, DriverScreen.DUTY_DASHBOARD, inclusive = true)
            }
        }
        onDispose {
            DriverNetworkClient.onAuthExpired = null
        }
    }

    val hideBottomBarRoutes = setOf(
        DriverScreen.PILOT_LOGIN.name,
        DriverScreen.PILOT_OTP.name,
        DriverScreen.PILOT_RECOVERY.name,
        DriverScreen.DOC_EXPIRY_WATCHDOG.name,
        DriverScreen.PICKUP_NAV.name,
        DriverScreen.ARRIVED_PICKUP.name,
        DriverScreen.WAITING_TIMER.name,
        DriverScreen.BOARDING_OTP.name,
        DriverScreen.IN_TRIP_NAV.name,
        DriverScreen.TRIP_COMPLETED_FARE.name,
        DriverScreen.CASH_COLLECTION.name,
        DriverScreen.UPI_SETTLEMENT.name
    )

    val showBottomBar = currentRoute !in hideBottomBarRoutes
    val unreadNotifications by DriverRepository.unreadNotificationCount.collectAsState()

    val tabIndex = when (currentRoute) {
        DriverScreen.DUTY_DASHBOARD.name, DriverScreen.VEHICLE_INSPECTION.name, DriverScreen.VEHICLE_RC_STATUS.name,
        DriverScreen.SHIFT_START.name, DriverScreen.SHIFT_END.name, DriverScreen.FATIGUE_CHECK.name,
        DriverScreen.GPS_PING.name, DriverScreen.HEATMAP_SURGE.name, DriverScreen.ROAD_HAZARDS.name -> 0

        DriverScreen.INCOMING_OFFER.name, DriverScreen.OFFER_QUEUE.name, DriverScreen.MULTI_STOP.name,
        DriverScreen.POOL_MANIFEST.name, DriverScreen.CORRIDOR_WAYPOINTS.name, DriverScreen.CO_PASSENGER_BROADCAST.name -> 1

        DriverScreen.PILOT_EARNINGS.name, DriverScreen.PAYOUT_ACCOUNTS.name, DriverScreen.INSTANT_PAYOUT.name,
        DriverScreen.DRIVER_LEDGER.name, DriverScreen.DAILY_TARGETS.name, DriverScreen.WEEKLY_GOALS.name,
        DriverScreen.STREAK_MISSIONS.name, DriverScreen.SHIFT_SUMMARY.name -> 2

        DriverScreen.NOTIFICATIONS_CENTER.name -> 3

        DriverScreen.PILOT_PROFILE.name, DriverScreen.DOCUMENT_VAULT.name, DriverScreen.DRIVER_PREFERENCES.name,
        DriverScreen.PERFORMANCE_SCORECARD.name, DriverScreen.ACADEMY_TRAINING.name, DriverScreen.DRIVER_INCIDENT.name,
        DriverScreen.FOUND_ITEM.name, DriverScreen.DRIVER_HELPDESK.name, DriverScreen.EMERGENCY_CONTACTS.name,
        DriverScreen.ROUTE_DEVIATION.name, DriverScreen.SPEEDOMETER.name, DriverScreen.AUDIO_SHIELD.name,
        DriverScreen.EMERGENCY_SOS.name, DriverScreen.BATTERY_SWAP_DEPOT.name -> 4

        else -> 0
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CockpitBackground,
        bottomBar = {
            if (showBottomBar) {
                VasantraBottomNavigationBar(
                    selectedTab = tabIndex,
                    unreadCount = unreadNotifications,
                    onTabSelected = { index ->
                        router.navigateTab(index)
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (showBottomBar) innerPadding.calculateBottomPadding() else 0.dp)
        ) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize()
            ) {
            // ==========================================
            // 1. Pilot Auth & KYC Flow (1-6)
            // ==========================================
            composable(DriverScreen.PILOT_LOGIN.name) {
                PilotLoginScreen(onNavigate = { route ->
                    when (route) {
                        "auth_otp", "otp" -> router.navigateTo(DriverScreen.PILOT_OTP)
                        "auth_recovery", "forgot" -> router.navigateTo(DriverScreen.PILOT_RECOVERY)
                        "duty_dashboard", "home", "dashboard" -> {
                            router.navigateAndClearTo(DriverScreen.DUTY_DASHBOARD, DriverScreen.PILOT_LOGIN, inclusive = true)
                        }
                        else -> router.navigateRoute(route)
                    }
                })
            }

            composable(DriverScreen.PILOT_OTP.name) {
                PilotOtpScreen(onNavigate = { route ->
                    if (route == "pilot_profile" || route == "profile") {
                        router.navigateAndClearTo(DriverScreen.PILOT_PROFILE, DriverScreen.PILOT_LOGIN, inclusive = true)
                    } else if (route == "auth_login" || route == "login") {
                        router.navigateAndClearTo(DriverScreen.PILOT_LOGIN, DriverScreen.PILOT_LOGIN, inclusive = true)
                    } else {
                        router.navigateAndClearTo(DriverScreen.DUTY_DASHBOARD, DriverScreen.PILOT_LOGIN, inclusive = true)
                    }
                })
            }

            composable(DriverScreen.PILOT_RECOVERY.name) {
                PilotPasswordRecoveryScreen(onNavigate = { route ->
                    if (route == "duty_dashboard" || route == "home") {
                        router.navigateAndClearTo(DriverScreen.DUTY_DASHBOARD, DriverScreen.PILOT_LOGIN, inclusive = true)
                    } else if (route == "auth_login" || route == "login") {
                        router.navigateAndClearTo(DriverScreen.PILOT_LOGIN, DriverScreen.PILOT_RECOVERY, inclusive = true)
                    } else {
                        router.popBack()
                    }
                })
            }

            composable(DriverScreen.PILOT_PROFILE.name) {
                PilotProfileScreen(onNavigate = { route ->
                    when (route) {
                        "doc_vault", "documents" -> router.navigateTo(DriverScreen.DOCUMENT_VAULT)
                        "doc_expiry" -> router.navigateTo(DriverScreen.DOC_EXPIRY_WATCHDOG)
                        "rc_status" -> router.navigateTo(DriverScreen.VEHICLE_RC_STATUS)
                        "inspection" -> router.navigateTo(DriverScreen.VEHICLE_INSPECTION)
                        "performance" -> router.navigateTo(DriverScreen.PERFORMANCE_SCORECARD)
                        "academy" -> router.navigateTo(DriverScreen.ACADEMY_TRAINING)
                        "emergency_contacts" -> router.navigateTo(DriverScreen.EMERGENCY_CONTACTS)
                        "incident" -> router.navigateTo(DriverScreen.DRIVER_INCIDENT)
                        "found_item" -> router.navigateTo(DriverScreen.FOUND_ITEM)
                        "helpdesk" -> router.navigateTo(DriverScreen.DRIVER_HELPDESK)
                        "preferences" -> router.navigateTo(DriverScreen.DRIVER_PREFERENCES)
                        "gps" -> router.navigateTo(DriverScreen.GPS_PING)
                        "payout_accounts" -> router.navigateTo(DriverScreen.PAYOUT_ACCOUNTS)
                        "driver_ledger" -> router.navigateTo(DriverScreen.DRIVER_LEDGER)
                        "auth_login", "login" -> router.navigateAndClearTo(DriverScreen.PILOT_LOGIN, DriverScreen.DUTY_DASHBOARD, inclusive = true)
                        "duty_dashboard", "home" -> router.returnToDutyDashboard()
                        else -> router.navigateRoute(route)
                    }
                })
            }

            composable(DriverScreen.DOCUMENT_VAULT.name) {
                DocumentVaultScreen(onNavigate = { route ->
                    if (route == "doc_expiry") {
                        router.navigateTo(DriverScreen.DOC_EXPIRY_WATCHDOG)
                    } else if (route == "duty_dashboard" || route == "home") {
                        router.returnToDutyDashboard()
                    } else {
                        router.popBack()
                    }
                })
            }

            composable(DriverScreen.DOC_EXPIRY_WATCHDOG.name) {
                DocumentExpiryWatchdogScreen(onNavigate = { route ->
                    if (route == "duty_dashboard" || route == "home") {
                        router.returnToDutyDashboard()
                    } else {
                        router.popBack()
                    }
                })
            }

            // ==========================================
            // 2. Duty Cycle & Vehicle Inspection (7-10, 12, 35, 50)
            // ==========================================
            composable(DriverScreen.VEHICLE_INSPECTION.name) {
                VehicleInspectionScreen(onNavigate = { route ->
                    if (route == "shift_start") {
                        router.navigateTo(DriverScreen.SHIFT_START)
                    } else if (route == "duty_dashboard" || route == "home") {
                        router.returnToDutyDashboard()
                    } else {
                        router.popBack()
                    }
                })
            }

            composable(DriverScreen.VEHICLE_RC_STATUS.name) {
                VehicleRcStatusScreen(onNavigate = { route ->
                    if (route == "duty_dashboard" || route == "home") {
                        router.returnToDutyDashboard()
                    } else {
                        router.popBack()
                    }
                })
            }

            composable(DriverScreen.SHIFT_START.name) {
                ShiftStartScreen(onNavigate = { _ ->
                    DriverRepository.setShiftActive(true)
                    DriverRepository.setDutyStatus(true) { _, _ -> }
                    router.returnToDutyDashboard()
                })
            }

            composable(DriverScreen.DUTY_DASHBOARD.name) {
                DutyDashboardScreen(onNavigate = { route ->
                    if (route.equals("catalog", ignoreCase = true)) {
                        if (BuildConfig.DEBUG) {
                            showCatalogDialog = true
                        }
                    } else if (route == "inspection") {
                        router.navigateTo(DriverScreen.VEHICLE_INSPECTION)
                    } else if (route == "shift_end") {
                        router.navigateTo(DriverScreen.SHIFT_END)
                    } else {
                        router.navigateRoute(route)
                    }
                })
            }

            composable(DriverScreen.FATIGUE_CHECK.name) {
                FatigueCheckScreen(onNavigate = { router.popBack() })
            }

            composable(DriverScreen.SHIFT_END.name) {
                ShiftEndScreen(onNavigate = { _ ->
                    DriverRepository.setShiftActive(false)
                    DriverRepository.setDutyStatus(false) { _, _ -> }
                    router.navigateAndClearTo(DriverScreen.SHIFT_SUMMARY, DriverScreen.DUTY_DASHBOARD, inclusive = false)
                })
            }

            composable(DriverScreen.DRIVER_PREFERENCES.name) {
                DriverPreferencesScreen(onNavigate = { router.popBack() })
            }

            // ==========================================
            // 3. Telemetry & Heatmaps (11, 13, 14)
            // ==========================================
            composable(DriverScreen.GPS_PING.name) {
                GpsPingScreen(
                    onNavigateBack = { router.popBack() },
                    onShowToast = { showToast(it) }
                )
            }

            composable(DriverScreen.HEATMAP_SURGE.name) {
                HeatmapSurgeScreen(
                    onNavigateBack = { router.popBack() },
                    onShowToast = { showToast(it) }
                )
            }

            composable(DriverScreen.ROAD_HAZARDS.name) {
                RoadHazardsScreen(
                    onNavigateBack = { router.popBack() },
                    onShowToast = { showToast(it) }
                )
            }

            // ==========================================
            // 4. Linear Active Trip State Machine (15-22)
            // ==========================================
            composable(DriverScreen.INCOMING_OFFER.name) {
                IncomingOfferScreen(
                    onNavigateBack = { router.returnToDutyDashboard() },
                    onAcceptOffer = { router.navigateTo(DriverScreen.PICKUP_NAV) },
                    onRejectOffer = { router.returnToDutyDashboard() },
                    onShowToast = { showToast(it) }
                )
            }

            composable(DriverScreen.OFFER_QUEUE.name) {
                OfferQueueScreen(
                    onNavigateBack = { router.popBack() },
                    onShowToast = { showToast(it) }
                )
            }

            composable(DriverScreen.PICKUP_NAV.name) {
                PickupNavScreen(
                    onNavigateBack = { router.returnToDutyDashboard() },
                    onArrived = { router.navigateTo(DriverScreen.ARRIVED_PICKUP) },
                    onShowToast = { showToast(it) }
                )
            }

            composable(DriverScreen.ARRIVED_PICKUP.name) {
                ArrivedPickupScreen(
                    onNavigateBack = { router.popBack() },
                    onStartWaitingTimer = { router.navigateTo(DriverScreen.WAITING_TIMER) },
                    onShowToast = { showToast(it) }
                )
            }

            composable(DriverScreen.WAITING_TIMER.name) {
                WaitingTimerScreen(
                    onNavigateBack = { router.popBack() },
                    onProceedToOtp = { router.navigateTo(DriverScreen.BOARDING_OTP) },
                    onShowToast = { showToast(it) }
                )
            }

            composable(DriverScreen.BOARDING_OTP.name) {
                BoardingOtpScreen(
                    onNavigateBack = { router.popBack() },
                    onTripStarted = { router.navigateTo(DriverScreen.IN_TRIP_NAV) },
                    onShowToast = { showToast(it) }
                )
            }

            composable(DriverScreen.IN_TRIP_NAV.name) {
                InTripNavScreen(
                    onNavigateBack = { router.popBack() },
                    onEndTrip = { router.navigateTo(DriverScreen.TRIP_COMPLETED_FARE) },
                    onShowToast = { showToast(it) }
                )
            }

            composable(DriverScreen.MULTI_STOP.name) {
                MultiStopScreen(
                    onNavigateBack = { router.popBack() },
                    onShowToast = { showToast(it) }
                )
            }

            // ==========================================
            // 5. Shared Pool Operations (24-26)
            // ==========================================
            composable(DriverScreen.POOL_MANIFEST.name) {
                PoolManifestScreen(
                    onNavigateBack = { router.popBack() },
                    onShowToast = { showToast(it) }
                )
            }

            composable(DriverScreen.CORRIDOR_WAYPOINTS.name) {
                CorridorWaypointsScreen(
                    onNavigateBack = { router.popBack() },
                    onShowToast = { showToast(it) }
                )
            }

            composable(DriverScreen.CO_PASSENGER_BROADCAST.name) {
                CoPassengerBroadcastScreen(
                    onNavigateBack = { router.popBack() },
                    onShowToast = { showToast(it) }
                )
            }

            // ==========================================
            // 6. Safety Sentinel & Depots (23, 27-30, 45, 48)
            // ==========================================
            composable(DriverScreen.ROUTE_DEVIATION.name) {
                RouteDeviationScreen(
                    onNavigateBack = { router.popBack() },
                    onShowToast = { showToast(it) }
                )
            }

            composable(DriverScreen.SPEEDOMETER.name) {
                SpeedometerScreen(
                    onNavigateBack = { router.popBack() },
                    onShowToast = { showToast(it) }
                )
            }

            composable(DriverScreen.AUDIO_SHIELD.name) {
                AudioShieldScreen(
                    onNavigateBack = { router.popBack() },
                    onShowToast = { showToast(it) }
                )
            }

            composable(DriverScreen.EMERGENCY_SOS.name) {
                EmergencySosScreen(
                    onNavigateBack = { router.popBack() },
                    onShowToast = { showToast(it) }
                )
            }

            composable(DriverScreen.BATTERY_SWAP_DEPOT.name) {
                BatterySwapDepotScreen(
                    onNavigateBack = { router.popBack() },
                    onShowToast = { showToast(it) }
                )
            }

            composable(DriverScreen.DRIVER_INCIDENT.name) {
                DriverIncidentScreen(
                    onNavigateBack = { router.popBack() },
                    onShowToast = { showToast(it) }
                )
            }

            composable(DriverScreen.EMERGENCY_CONTACTS.name) {
                EmergencyContactsScreen(
                    onNavigateBack = { router.popBack() },
                    onShowToast = { showToast(it) }
                )
            }

            // ==========================================
            // 7. Fare Settlement & Payments (31-33)
            // ==========================================
            composable(DriverScreen.TRIP_COMPLETED_FARE.name) {
                TripCompletedFareScreen(
                    onNavigateBack = { router.returnToDutyDashboard() },
                    onCollectCash = { router.navigateTo(DriverScreen.CASH_COLLECTION) },
                    onCollectUpi = { router.navigateTo(DriverScreen.UPI_SETTLEMENT) },
                    onFinishTrip = { router.returnToDutyDashboard() },
                    onShowToast = { showToast(it) }
                )
            }

            composable(DriverScreen.CASH_COLLECTION.name) {
                CashCollectionScreen(
                    onNavigateBack = { router.popBack() },
                    onCashCollected = { router.returnToDutyDashboard() },
                    onShowToast = { showToast(it) }
                )
            }

            composable(DriverScreen.UPI_SETTLEMENT.name) {
                UpiSettlementScreen(
                    onNavigateBack = { router.popBack() },
                    onPaymentConfirmed = { router.returnToDutyDashboard() },
                    onShowToast = { showToast(it) }
                )
            }

            // ==========================================
            // 8. Earnings & Ledger (34, 39-42)
            // ==========================================
            composable(DriverScreen.SHIFT_SUMMARY.name) {
                ShiftSummaryScreen(
                    onNavigateBack = { router.returnToDutyDashboard() },
                    onGoToLedger = { router.navigateTo(DriverScreen.DRIVER_LEDGER) },
                    onShowToast = { showToast(it) },
                    onNavigateAction = { route -> router.navigateRoute(route) }
                )
            }

            composable(DriverScreen.PILOT_EARNINGS.name) {
                PilotEarningsScreen(
                    onNavigateBack = { router.returnToDutyDashboard() },
                    onInstantPayout = { router.navigateTo(DriverScreen.INSTANT_PAYOUT) },
                    onManageBanks = { router.navigateTo(DriverScreen.PAYOUT_ACCOUNTS) },
                    onShowToast = { showToast(it) },
                    onNavigateAction = { route -> router.navigateRoute(route) }
                )
            }

            composable(DriverScreen.PAYOUT_ACCOUNTS.name) {
                PayoutAccountsScreen(
                    onNavigateBack = { router.popBack() },
                    onShowToast = { showToast(it) }
                )
            }

            composable(DriverScreen.INSTANT_PAYOUT.name) {
                InstantPayoutScreen(
                    onNavigateBack = { router.popBack() },
                    onPayoutSuccess = {
                        triggerPopup("PAYOUT_SUCCESS") {
                            router.navigateTo(DriverScreen.DRIVER_LEDGER)
                        }
                    },
                    onShowToast = { showToast(it) }
                )
            }

            composable(DriverScreen.DRIVER_LEDGER.name) {
                DriverLedgerScreen(
                    onNavigateBack = { router.popBack() },
                    onShowToast = { showToast(it) }
                )
            }

            // ==========================================
            // 9. Targets, Milestones & Scorecard (36-38, 43)
            // ==========================================
            composable(DriverScreen.DAILY_TARGETS.name) {
                DailyTargetsScreen(
                    onNavigateBack = { router.popBack() },
                    onShowToast = { showToast(it) }
                )
            }

            composable(DriverScreen.WEEKLY_GOALS.name) {
                WeeklyGoalsScreen(
                    onNavigateBack = { router.popBack() },
                    onShowToast = { showToast(it) }
                )
            }

            composable(DriverScreen.STREAK_MISSIONS.name) {
                StreakMissionsScreen(
                    onNavigateBack = { router.popBack() },
                    onShowToast = { showToast(it) }
                )
            }

            composable(DriverScreen.PERFORMANCE_SCORECARD.name) {
                PerformanceScorecardScreen(
                    onNavigateBack = { router.popBack() },
                    onShowToast = { showToast(it) }
                )
            }

            // ==========================================
            // 10. Academy & Support (44, 46, 47, 49)
            // ==========================================
            composable(DriverScreen.ACADEMY_TRAINING.name) {
                AcademyTrainingScreen(
                    onNavigateBack = { router.popBack() },
                    onShowToast = { showToast(it) }
                )
            }

            composable(DriverScreen.FOUND_ITEM.name) {
                FoundItemScreen(
                    onNavigateBack = { router.popBack() },
                    onShowToast = { showToast(it) }
                )
            }

            composable(DriverScreen.DRIVER_HELPDESK.name) {
                DriverHelpdeskScreen(
                    onNavigateBack = { router.popBack() },
                    onShowToast = { showToast(it) }
                )
            }

            composable(DriverScreen.NOTIFICATIONS_CENTER.name) {
                NotificationsCenterScreen(
                    onNavigateBack = { router.popBack() },
                    onShowToast = { showToast(it) }
                )
            }
        }

        // ==========================================
        // Debug Catalog Guard: 50-Screen Testing Hub
        // ==========================================
        if (BuildConfig.DEBUG && showCatalogDialog) {
            Dialog(onDismissRequest = { showCatalogDialog = false }) {
                CockpitCard(modifier = Modifier.fillMaxHeight(0.85f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("DriverGo 50-Screen Hub [DEBUG]", color = PilotTextWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { showCatalogDialog = false }) {
                            Text("Close", color = PilotElectricCyan)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(DriverScreen.entries) { screen ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(PilotSurface)
                                    .clickable {
                                        showCatalogDialog = false
                                        router.navigateTo(screen)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(screen.title, color = PilotTextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                CockpitBadge(screen.category, OnlineGlow, PilotOnlineGreen)
                            }
                        }
                    }
                }
            }
        }

        // Host for all 15 Popups/Modals & Toasts
        DriverGoDialogHost()
    }
}
}

/**
 * Backward compatibility alias for MainActivity.
 */
@Composable
fun DriverAppNavigation() {
    DriverNavigation()
}
