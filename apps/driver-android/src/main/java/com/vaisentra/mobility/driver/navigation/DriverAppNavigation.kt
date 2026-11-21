package com.vaisentra.mobility.driver.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.vaisentra.mobility.driver.components.*
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
 */
enum class DriverScreen(val title: String, val category: String) {
    // 1. Pilot Auth & KYC (1-6)
    PILOT_LOGIN("1. Pilot Login", "Auth & KYC"),
    PILOT_OTP("2. Road OTP Verify", "Auth & KYC"),
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

private fun resolveDashboardRoute(route: String): DriverScreen {
    DriverScreen.entries.firstOrNull { it.name.equals(route, ignoreCase = true) }?.let { return it }

    return when (route.lowercase()) {
        // Auth & KYC
        "login", "auth_login", "forgot", "recovery", "auth_reset" -> DriverScreen.PILOT_LOGIN
        "otp", "auth_otp" -> DriverScreen.PILOT_OTP
        "pilot_profile", "profile" -> DriverScreen.PILOT_PROFILE
        "documents", "document_vault", "doc_vault" -> DriverScreen.DOCUMENT_VAULT
        "doc_expiry", "expiry" -> DriverScreen.DOC_EXPIRY_WATCHDOG

        // Duty & Vehicle
        "duty_dashboard", "home" -> DriverScreen.DUTY_DASHBOARD
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

        else -> DriverScreen.DUTY_DASHBOARD
    }
}

@Composable
fun DriverAppNavigation() {
    var currentScreen by remember { mutableStateOf(DriverScreen.PILOT_LOGIN) }
    var showCatalogDialog by remember { mutableStateOf(false) }

    fun triggerPopup(popupId: String, onConfirm: (() -> Unit)? = null) {
        try {
            val type = DriverPopupType.valueOf(popupId)
            DriverDialogController.show(type, onConfirm)
        } catch (_: Exception) {}
    }

    fun showToast(msg: String) {
        DriverDialogController.showToast(msg)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (currentScreen) {
            // 1. Auth & KYC
            DriverScreen.PILOT_LOGIN -> PilotLoginScreen(onNavigate = { route ->
                currentScreen = resolveDashboardRoute(route)
            })
            DriverScreen.PILOT_OTP -> PilotOtpScreen(onNavigate = { route ->
                currentScreen = resolveDashboardRoute(route)
            })
            DriverScreen.PILOT_PROFILE -> PilotProfileScreen(onNavigate = { route ->
                currentScreen = resolveDashboardRoute(route)
            })
            DriverScreen.DOCUMENT_VAULT -> DocumentVaultScreen(onNavigate = { route ->
                currentScreen = resolveDashboardRoute(route)
            })
            DriverScreen.DOC_EXPIRY_WATCHDOG -> DocumentExpiryWatchdogScreen(onNavigate = { route ->
                currentScreen = resolveDashboardRoute(route)
            })

            // 2. Duty & Vehicle
            DriverScreen.VEHICLE_INSPECTION -> VehicleInspectionScreen(onNavigate = { route ->
                currentScreen = resolveDashboardRoute(route)
            })
            DriverScreen.VEHICLE_RC_STATUS -> VehicleRcStatusScreen(onNavigate = { route ->
                currentScreen = resolveDashboardRoute(route)
            })
            DriverScreen.SHIFT_START -> ShiftStartScreen(onNavigate = { route ->
                currentScreen = resolveDashboardRoute(route)
            })
            DriverScreen.DUTY_DASHBOARD -> DutyDashboardScreen(onNavigate = { route ->
                if (route.equals("catalog", ignoreCase = true)) {
                    showCatalogDialog = true
                } else {
                    currentScreen = resolveDashboardRoute(route)
                }
            })
            DriverScreen.FATIGUE_CHECK -> FatigueCheckScreen(onNavigate = { route ->
                currentScreen = resolveDashboardRoute(route)
            })
            DriverScreen.SHIFT_END -> ShiftEndScreen(onNavigate = { route ->
                currentScreen = if (route == "summary") DriverScreen.SHIFT_SUMMARY else resolveDashboardRoute(route)
            })
            DriverScreen.DRIVER_PREFERENCES -> DriverPreferencesScreen(onNavigate = { route ->
                currentScreen = resolveDashboardRoute(route)
            })

            // 3. Telemetry & Maps
            DriverScreen.GPS_PING -> GpsPingScreen(
                onNavigateBack = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onShowToast = { showToast(it) }
            )
            DriverScreen.HEATMAP_SURGE -> HeatmapSurgeScreen(
                onNavigateBack = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onShowToast = { showToast(it) }
            )
            DriverScreen.ROAD_HAZARDS -> RoadHazardsScreen(
                onNavigateBack = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onShowToast = { showToast(it) }
            )

            // 4. Trip Ops
            DriverScreen.INCOMING_OFFER -> IncomingOfferScreen(
                onNavigateBack = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onAcceptOffer = { currentScreen = DriverScreen.PICKUP_NAV },
                onRejectOffer = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onShowToast = { showToast(it) }
            )
            DriverScreen.OFFER_QUEUE -> OfferQueueScreen(
                onNavigateBack = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onShowToast = { showToast(it) }
            )
            DriverScreen.PICKUP_NAV -> PickupNavScreen(
                onNavigateBack = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onArrived = { currentScreen = DriverScreen.ARRIVED_PICKUP },
                onShowToast = { showToast(it) }
            )
            DriverScreen.ARRIVED_PICKUP -> ArrivedPickupScreen(
                onNavigateBack = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onStartWaitingTimer = { currentScreen = DriverScreen.WAITING_TIMER },
                onShowToast = { showToast(it) }
            )
            DriverScreen.WAITING_TIMER -> WaitingTimerScreen(
                onNavigateBack = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onProceedToOtp = { currentScreen = DriverScreen.BOARDING_OTP },
                onShowToast = { showToast(it) }
            )
            DriverScreen.BOARDING_OTP -> BoardingOtpScreen(
                onNavigateBack = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onTripStarted = { currentScreen = DriverScreen.IN_TRIP_NAV },
                onShowToast = { showToast(it) }
            )
            DriverScreen.IN_TRIP_NAV -> InTripNavScreen(
                onNavigateBack = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onEndTrip = { currentScreen = DriverScreen.TRIP_COMPLETED_FARE },
                onShowToast = { showToast(it) }
            )
            DriverScreen.MULTI_STOP -> MultiStopScreen(
                onNavigateBack = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onShowToast = { showToast(it) }
            )

            // 5. Shared Pool
            DriverScreen.POOL_MANIFEST -> PoolManifestScreen(
                onNavigateBack = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onShowToast = { showToast(it) }
            )
            DriverScreen.CORRIDOR_WAYPOINTS -> CorridorWaypointsScreen(
                onNavigateBack = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onShowToast = { showToast(it) }
            )
            DriverScreen.CO_PASSENGER_BROADCAST -> CoPassengerBroadcastScreen(
                onNavigateBack = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onShowToast = { showToast(it) }
            )

            // 6. Safety Sentinel
            DriverScreen.ROUTE_DEVIATION -> RouteDeviationScreen(
                onNavigateBack = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onShowToast = { showToast(it) }
            )
            DriverScreen.SPEEDOMETER -> SpeedometerScreen(
                onNavigateBack = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onShowToast = { showToast(it) }
            )
            DriverScreen.AUDIO_SHIELD -> AudioShieldScreen(
                onNavigateBack = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onShowToast = { showToast(it) }
            )
            DriverScreen.EMERGENCY_SOS -> EmergencySosScreen(
                onNavigateBack = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onShowToast = { showToast(it) }
            )
            DriverScreen.BATTERY_SWAP_DEPOT -> BatterySwapDepotScreen(
                onNavigateBack = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onShowToast = { showToast(it) }
            )
            DriverScreen.DRIVER_INCIDENT -> DriverIncidentScreen(
                onNavigateBack = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onShowToast = { showToast(it) }
            )
            DriverScreen.EMERGENCY_CONTACTS -> EmergencyContactsScreen(
                onNavigateBack = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onShowToast = { showToast(it) }
            )

            // 7. Fare Settlement
            DriverScreen.TRIP_COMPLETED_FARE -> TripCompletedFareScreen(
                onNavigateBack = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onCollectCash = { currentScreen = DriverScreen.CASH_COLLECTION },
                onCollectUpi = { currentScreen = DriverScreen.UPI_SETTLEMENT },
                onFinishTrip = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onShowToast = { showToast(it) }
            )
            DriverScreen.CASH_COLLECTION -> CashCollectionScreen(
                onNavigateBack = { currentScreen = DriverScreen.TRIP_COMPLETED_FARE },
                onCashCollected = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onShowToast = { showToast(it) }
            )
            DriverScreen.UPI_SETTLEMENT -> UpiSettlementScreen(
                onNavigateBack = { currentScreen = DriverScreen.TRIP_COMPLETED_FARE },
                onPaymentConfirmed = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onShowToast = { showToast(it) }
            )

            // 8. Earnings & Ledger
            DriverScreen.SHIFT_SUMMARY -> ShiftSummaryScreen(
                onNavigateBack = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onGoToLedger = { currentScreen = DriverScreen.DRIVER_LEDGER },
                onShowToast = { showToast(it) }
            )
            DriverScreen.PILOT_EARNINGS -> PilotEarningsScreen(
                onNavigateBack = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onInstantPayout = { currentScreen = DriverScreen.INSTANT_PAYOUT },
                onManageBanks = { currentScreen = DriverScreen.PAYOUT_ACCOUNTS },
                onShowToast = { showToast(it) }
            )
            DriverScreen.PAYOUT_ACCOUNTS -> PayoutAccountsScreen(
                onNavigateBack = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onShowToast = { showToast(it) }
            )
            DriverScreen.INSTANT_PAYOUT -> InstantPayoutScreen(
                onNavigateBack = { currentScreen = DriverScreen.PILOT_EARNINGS },
                onPayoutSuccess = {
                    triggerPopup("PAYOUT_SUCCESS") {
                        currentScreen = DriverScreen.DRIVER_LEDGER
                    }
                },
                onShowToast = { showToast(it) }
            )
            DriverScreen.DRIVER_LEDGER -> DriverLedgerScreen(
                onNavigateBack = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onShowToast = { showToast(it) }
            )

            // 9. Goals & Scorecard
            DriverScreen.DAILY_TARGETS -> DailyTargetsScreen(
                onNavigateBack = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onShowToast = { showToast(it) }
            )
            DriverScreen.WEEKLY_GOALS -> WeeklyGoalsScreen(
                onNavigateBack = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onShowToast = { showToast(it) }
            )
            DriverScreen.STREAK_MISSIONS -> StreakMissionsScreen(
                onNavigateBack = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onShowToast = { showToast(it) }
            )
            DriverScreen.PERFORMANCE_SCORECARD -> PerformanceScorecardScreen(
                onNavigateBack = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onShowToast = { showToast(it) }
            )

            // 10. Academy & Support
            DriverScreen.ACADEMY_TRAINING -> AcademyTrainingScreen(
                onNavigateBack = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onShowToast = { showToast(it) }
            )
            DriverScreen.FOUND_ITEM -> FoundItemScreen(
                onNavigateBack = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onShowToast = { showToast(it) }
            )
            DriverScreen.DRIVER_HELPDESK -> DriverHelpdeskScreen(
                onNavigateBack = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onShowToast = { showToast(it) }
            )
            DriverScreen.NOTIFICATIONS_CENTER -> NotificationsCenterScreen(
                onNavigateBack = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onShowToast = { showToast(it) }
            )
        }

        if (showCatalogDialog) {
            Dialog(onDismissRequest = { showCatalogDialog = false }) {
                CockpitCard(modifier = Modifier.fillMaxHeight(0.85f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("DriverGo 50-Screen Hub", color = PilotTextWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold)
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
                                        currentScreen = screen
                                        showCatalogDialog = false
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

        // Persistent Cockpit Bottom Navigation Bar (Shown on all pages except auth/KYC)
        val showBottomBar = currentScreen !in listOf(
            DriverScreen.PILOT_LOGIN,
            DriverScreen.PILOT_OTP,
            DriverScreen.DOC_EXPIRY_WATCHDOG
        )
        if (showBottomBar) {
            DriverBottomBar(
                currentScreen = currentScreen,
                onNavigate = { currentScreen = it },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // Host for all 15 Popups/Modals & Toasts
        DriverGoDialogHost()
    }
}

@Composable
fun DriverBottomBar(
    currentScreen: DriverScreen,
    onNavigate: (DriverScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val unreadNotifications by DriverRepository.unreadNotificationCount.collectAsState()

    val tabIndex = when (currentScreen) {
        DriverScreen.DUTY_DASHBOARD, DriverScreen.VEHICLE_INSPECTION, DriverScreen.VEHICLE_RC_STATUS,
        DriverScreen.SHIFT_START, DriverScreen.SHIFT_END, DriverScreen.FATIGUE_CHECK,
        DriverScreen.GPS_PING, DriverScreen.HEATMAP_SURGE, DriverScreen.ROAD_HAZARDS -> 0

        DriverScreen.INCOMING_OFFER, DriverScreen.OFFER_QUEUE, DriverScreen.PICKUP_NAV,
        DriverScreen.ARRIVED_PICKUP, DriverScreen.WAITING_TIMER, DriverScreen.BOARDING_OTP,
        DriverScreen.IN_TRIP_NAV, DriverScreen.MULTI_STOP, DriverScreen.POOL_MANIFEST,
        DriverScreen.CORRIDOR_WAYPOINTS, DriverScreen.CO_PASSENGER_BROADCAST -> 1

        DriverScreen.TRIP_COMPLETED_FARE, DriverScreen.CASH_COLLECTION, DriverScreen.UPI_SETTLEMENT,
        DriverScreen.SHIFT_SUMMARY, DriverScreen.PILOT_EARNINGS, DriverScreen.PAYOUT_ACCOUNTS,
        DriverScreen.INSTANT_PAYOUT, DriverScreen.DRIVER_LEDGER, DriverScreen.DAILY_TARGETS,
        DriverScreen.WEEKLY_GOALS, DriverScreen.STREAK_MISSIONS -> 2

        DriverScreen.NOTIFICATIONS_CENTER, DriverScreen.ROUTE_DEVIATION, DriverScreen.SPEEDOMETER,
        DriverScreen.AUDIO_SHIELD, DriverScreen.EMERGENCY_SOS, DriverScreen.BATTERY_SWAP_DEPOT,
        DriverScreen.DRIVER_INCIDENT, DriverScreen.EMERGENCY_CONTACTS, DriverScreen.DRIVER_HELPDESK -> 3

        DriverScreen.PILOT_PROFILE, DriverScreen.DOCUMENT_VAULT, DriverScreen.DRIVER_PREFERENCES,
        DriverScreen.PERFORMANCE_SCORECARD, DriverScreen.ACADEMY_TRAINING, DriverScreen.FOUND_ITEM -> 4

        else -> 0
    }

    VasantraBottomNavigationBar(
        modifier = modifier,
        selectedTab = tabIndex,
        unreadCount = unreadNotifications,
        onTabSelected = { index ->
            val targetScreen = when (index) {
                0 -> DriverScreen.DUTY_DASHBOARD
                1 -> DriverScreen.OFFER_QUEUE
                2 -> DriverScreen.PILOT_EARNINGS
                3 -> DriverScreen.NOTIFICATIONS_CENTER
                4 -> DriverScreen.PILOT_PROFILE
                else -> DriverScreen.DUTY_DASHBOARD
            }
            onNavigate(targetScreen)
        }
    )
}

@Composable
private fun DriverBottomNavItem(
    icon: String,
    label: String,
    screen: DriverScreen,
    activeTab: DriverScreen,
    onNavigate: (DriverScreen) -> Unit,
    isWarning: Boolean = false
) {
    val isSelected = activeTab == screen
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onNavigate(screen) }
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text = icon, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) {
                if (isWarning) CrimsonAlert else ElectricCyan
            } else CockpitTextMuted
        )
    }
}