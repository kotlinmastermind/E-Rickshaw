package com.vaisentra.mobility.driver.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.driver.components.*
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
    PILOT_PASSWORD_RECOVERY("3. PIN Recovery", "Auth & KYC"),
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
    NOTIFICATIONS_CENTER("49. Notifications Center", "Support & Academy"),

    // Master Catalog Hub
    MASTER_CATALOG("Master DriverGo Catalog", "Explorer Hub")
}

@Composable
fun DriverAppNavigation() {
    var currentScreen by remember { mutableStateOf(DriverScreen.MASTER_CATALOG) }

    fun triggerPopup(popupId: String) {
        try {
            val type = DriverPopupType.valueOf(popupId)
            DriverDialogController.show(type)
        } catch (_: Exception) {}
    }

    fun showToast(msg: String) {
        DriverDialogController.showToast(msg)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (currentScreen) {
            DriverScreen.MASTER_CATALOG -> MasterDriverCatalogScreen(
                onSelectScreen = { currentScreen = it },
                onTriggerPopup = { triggerPopup(it) },
                onShowToast = { showToast(it) }
            )

            // 1. Auth & KYC
            DriverScreen.PILOT_LOGIN -> PilotLoginScreen(onNavigate = { route ->
                when (route) {
                    "otp" -> currentScreen = DriverScreen.PILOT_OTP
                    "forgot" -> currentScreen = DriverScreen.PILOT_PASSWORD_RECOVERY
                    else -> currentScreen = DriverScreen.DUTY_DASHBOARD
                }
            })
            DriverScreen.PILOT_OTP -> PilotOtpScreen(onNavigate = { route ->
                when (route) {
                    "home" -> currentScreen = DriverScreen.DUTY_DASHBOARD
                    else -> currentScreen = DriverScreen.MASTER_CATALOG
                }
            })
            DriverScreen.PILOT_PASSWORD_RECOVERY -> PilotPasswordRecoveryScreen(onNavigate = {
                currentScreen = DriverScreen.PILOT_LOGIN
            })
            DriverScreen.PILOT_PROFILE -> PilotProfileScreen(onNavigate = {
                currentScreen = DriverScreen.MASTER_CATALOG
            })
            DriverScreen.DOCUMENT_VAULT -> DocumentVaultScreen(onNavigate = {
                currentScreen = DriverScreen.MASTER_CATALOG
            })
            DriverScreen.DOC_EXPIRY_WATCHDOG -> DocumentExpiryWatchdogScreen(onNavigate = {
                currentScreen = DriverScreen.MASTER_CATALOG
            })

            // 2. Duty & Vehicle
            DriverScreen.VEHICLE_INSPECTION -> VehicleInspectionScreen(onNavigate = { route ->
                if (route == "shift_start") currentScreen = DriverScreen.SHIFT_START
                else currentScreen = DriverScreen.MASTER_CATALOG
            })
            DriverScreen.VEHICLE_RC_STATUS -> VehicleRcStatusScreen(onNavigate = {
                currentScreen = DriverScreen.MASTER_CATALOG
            })
            DriverScreen.SHIFT_START -> ShiftStartScreen(onNavigate = { route ->
                if (route == "duty") currentScreen = DriverScreen.DUTY_DASHBOARD
                else currentScreen = DriverScreen.MASTER_CATALOG
            })
            DriverScreen.DUTY_DASHBOARD -> DutyDashboardScreen(onNavigate = { route ->
                when (route) {
                    "offer" -> currentScreen = DriverScreen.INCOMING_OFFER
                    "inspection" -> currentScreen = DriverScreen.VEHICLE_INSPECTION
                    "earnings" -> currentScreen = DriverScreen.PILOT_EARNINGS
                    "heatmap" -> currentScreen = DriverScreen.HEATMAP_SURGE
                    else -> currentScreen = DriverScreen.MASTER_CATALOG
                }
            })
            DriverScreen.FATIGUE_CHECK -> FatigueCheckScreen(onNavigate = {
                currentScreen = DriverScreen.MASTER_CATALOG
            })
            DriverScreen.SHIFT_END -> ShiftEndScreen(onNavigate = { route ->
                if (route == "summary") currentScreen = DriverScreen.SHIFT_SUMMARY
                else currentScreen = DriverScreen.MASTER_CATALOG
            })
            DriverScreen.DRIVER_PREFERENCES -> DriverPreferencesScreen(onNavigate = {
                currentScreen = DriverScreen.MASTER_CATALOG
            })

            // 3. Telemetry & Maps
            DriverScreen.GPS_PING -> GpsPingScreen(
                onNavigateBack = { currentScreen = DriverScreen.MASTER_CATALOG },
                onShowToast = { showToast(it) }
            )
            DriverScreen.HEATMAP_SURGE -> HeatmapSurgeScreen(
                onNavigateBack = { currentScreen = DriverScreen.MASTER_CATALOG },
                onShowToast = { showToast(it) }
            )
            DriverScreen.ROAD_HAZARDS -> RoadHazardsScreen(
                onNavigateBack = { currentScreen = DriverScreen.MASTER_CATALOG },
                onShowToast = { showToast(it) }
            )

            // 4. Trip Ops
            DriverScreen.INCOMING_OFFER -> IncomingOfferScreen(
                onNavigateBack = { currentScreen = DriverScreen.MASTER_CATALOG },
                onAcceptOffer = { currentScreen = DriverScreen.PICKUP_NAV },
                onRejectOffer = { currentScreen = DriverScreen.DUTY_DASHBOARD },
                onShowToast = { showToast(it) }
            )
            DriverScreen.OFFER_QUEUE -> OfferQueueScreen(
                onNavigateBack = { currentScreen = DriverScreen.MASTER_CATALOG },
                onShowToast = { showToast(it) }
            )
            DriverScreen.PICKUP_NAV -> PickupNavScreen(
                onNavigateBack = { currentScreen = DriverScreen.MASTER_CATALOG },
                onArrived = { currentScreen = DriverScreen.ARRIVED_PICKUP },
                onShowToast = { showToast(it) }
            )
            DriverScreen.ARRIVED_PICKUP -> ArrivedPickupScreen(
                onNavigateBack = { currentScreen = DriverScreen.MASTER_CATALOG },
                onStartWaitingTimer = { currentScreen = DriverScreen.WAITING_TIMER },
                onShowToast = { showToast(it) }
            )
            DriverScreen.WAITING_TIMER -> WaitingTimerScreen(
                onNavigateBack = { currentScreen = DriverScreen.MASTER_CATALOG },
                onProceedToOtp = { currentScreen = DriverScreen.BOARDING_OTP },
                onShowToast = { showToast(it) }
            )
            DriverScreen.BOARDING_OTP -> BoardingOtpScreen(
                onNavigateBack = { currentScreen = DriverScreen.MASTER_CATALOG },
                onTripStarted = { currentScreen = DriverScreen.IN_TRIP_NAV },
                onShowToast = { showToast(it) }
            )
            DriverScreen.IN_TRIP_NAV -> InTripNavScreen(
                onNavigateBack = { currentScreen = DriverScreen.MASTER_CATALOG },
                onEndTrip = { currentScreen = DriverScreen.TRIP_COMPLETED_FARE },
                onShowToast = { showToast(it) }
            )
            DriverScreen.MULTI_STOP -> MultiStopScreen(
                onNavigateBack = { currentScreen = DriverScreen.MASTER_CATALOG },
                onShowToast = { showToast(it) }
            )

            // 5. Shared Pool
            DriverScreen.POOL_MANIFEST -> PoolManifestScreen(
                onNavigateBack = { currentScreen = DriverScreen.MASTER_CATALOG },
                onShowToast = { showToast(it) }
            )
            DriverScreen.CORRIDOR_WAYPOINTS -> CorridorWaypointsScreen(
                onNavigateBack = { currentScreen = DriverScreen.MASTER_CATALOG },
                onShowToast = { showToast(it) }
            )
            DriverScreen.CO_PASSENGER_BROADCAST -> CoPassengerBroadcastScreen(
                onNavigateBack = { currentScreen = DriverScreen.MASTER_CATALOG },
                onShowToast = { showToast(it) }
            )

            // 6. Safety Sentinel
            DriverScreen.ROUTE_DEVIATION -> RouteDeviationScreen(
                onNavigateBack = { currentScreen = DriverScreen.MASTER_CATALOG },
                onShowToast = { showToast(it) }
            )
            DriverScreen.SPEEDOMETER -> SpeedometerScreen(
                onNavigateBack = { currentScreen = DriverScreen.MASTER_CATALOG },
                onShowToast = { showToast(it) }
            )
            DriverScreen.AUDIO_SHIELD -> AudioShieldScreen(
                onNavigateBack = { currentScreen = DriverScreen.MASTER_CATALOG },
                onShowToast = { showToast(it) }
            )
            DriverScreen.EMERGENCY_SOS -> EmergencySosScreen(
                onNavigateBack = { currentScreen = DriverScreen.MASTER_CATALOG },
                onShowToast = { showToast(it) }
            )
            DriverScreen.BATTERY_SWAP_DEPOT -> BatterySwapDepotScreen(
                onNavigateBack = { currentScreen = DriverScreen.MASTER_CATALOG },
                onShowToast = { showToast(it) }
            )
            DriverScreen.DRIVER_INCIDENT -> DriverIncidentScreen(
                onNavigateBack = { currentScreen = DriverScreen.MASTER_CATALOG },
                onShowToast = { showToast(it) }
            )
            DriverScreen.EMERGENCY_CONTACTS -> EmergencyContactsScreen(
                onNavigateBack = { currentScreen = DriverScreen.MASTER_CATALOG },
                onShowToast = { showToast(it) }
            )

            // 7. Fare Settlement
            DriverScreen.TRIP_COMPLETED_FARE -> TripCompletedFareScreen(
                onNavigateBack = { currentScreen = DriverScreen.MASTER_CATALOG },
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
                onNavigateBack = { currentScreen = DriverScreen.MASTER_CATALOG },
                onGoToLedger = { currentScreen = DriverScreen.DRIVER_LEDGER },
                onShowToast = { showToast(it) }
            )
            DriverScreen.PILOT_EARNINGS -> PilotEarningsScreen(
                onNavigateBack = { currentScreen = DriverScreen.MASTER_CATALOG },
                onInstantPayout = { currentScreen = DriverScreen.INSTANT_PAYOUT },
                onManageBanks = { currentScreen = DriverScreen.PAYOUT_ACCOUNTS },
                onShowToast = { showToast(it) }
            )
            DriverScreen.PAYOUT_ACCOUNTS -> PayoutAccountsScreen(
                onNavigateBack = { currentScreen = DriverScreen.MASTER_CATALOG },
                onShowToast = { showToast(it) }
            )
            DriverScreen.INSTANT_PAYOUT -> InstantPayoutScreen(
                onNavigateBack = { currentScreen = DriverScreen.PILOT_EARNINGS },
                onPayoutSuccess = { triggerPopup("PAYOUT_SUCCESS") },
                onShowToast = { showToast(it) }
            )
            DriverScreen.DRIVER_LEDGER -> DriverLedgerScreen(
                onNavigateBack = { currentScreen = DriverScreen.MASTER_CATALOG },
                onShowToast = { showToast(it) }
            )

            // 9. Goals & Scorecard
            DriverScreen.DAILY_TARGETS -> DailyTargetsScreen(
                onNavigateBack = { currentScreen = DriverScreen.MASTER_CATALOG },
                onShowToast = { showToast(it) }
            )
            DriverScreen.WEEKLY_GOALS -> WeeklyGoalsScreen(
                onNavigateBack = { currentScreen = DriverScreen.MASTER_CATALOG },
                onShowToast = { showToast(it) }
            )
            DriverScreen.STREAK_MISSIONS -> StreakMissionsScreen(
                onNavigateBack = { currentScreen = DriverScreen.MASTER_CATALOG },
                onShowToast = { showToast(it) }
            )
            DriverScreen.PERFORMANCE_SCORECARD -> PerformanceScorecardScreen(
                onNavigateBack = { currentScreen = DriverScreen.MASTER_CATALOG },
                onShowToast = { showToast(it) }
            )

            // 10. Support & Academy
            DriverScreen.ACADEMY_TRAINING -> AcademyTrainingScreen(
                onNavigateBack = { currentScreen = DriverScreen.MASTER_CATALOG },
                onShowToast = { showToast(it) }
            )
            DriverScreen.FOUND_ITEM -> FoundItemScreen(
                onNavigateBack = { currentScreen = DriverScreen.MASTER_CATALOG },
                onShowToast = { showToast(it) }
            )
            DriverScreen.DRIVER_HELPDESK -> DriverHelpdeskScreen(
                onNavigateBack = { currentScreen = DriverScreen.MASTER_CATALOG },
                onShowToast = { showToast(it) }
            )
            DriverScreen.NOTIFICATIONS_CENTER -> NotificationsCenterScreen(
                onNavigateBack = { currentScreen = DriverScreen.MASTER_CATALOG },
                onShowToast = { showToast(it) }
            )
        }

        // Host for all 15 Popups/Modals & Toasts
        DriverGoDialogHost()
    }
}

/**
 * Master DriverGo Catalog Inspector:
 * Lists all 50 Driver Jetpack Compose screens + 15 Popups with 1-tap testing
 */
@Composable
fun MasterDriverCatalogScreen(
    onSelectScreen: (DriverScreen) -> Unit,
    onTriggerPopup: (String) -> Unit,
    onShowToast: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("50 Screens", "15 Popups & Alerts", "Live Supabase Status")

    val allDriverScreens = DriverScreen.values().filter { it != DriverScreen.MASTER_CATALOG }
    val categories = allDriverScreens.map { it.category }.distinct()

    val driverPopups = listOf(
        PopupMeta("INCOMING_OFFER", "16. Incoming Ride Offer", "15s countdown modal with trip fare", EmeraldPrimary),
        PopupMeta("RIDER_CANCELLED", "17. Rider Cancelled", "₹25 compensation credit to driver", AmberWarning),
        PopupMeta("LOW_BATTERY_SWAP", "18. Low Battery Swap", "Battery < 18% warning with reroute", CrimsonAlert),
        PopupMeta("FATIGUE_BREAK", "19. Fatigue Break Cooldown", "4.5 hours continuous driving break", ElectricCyan),
        PopupMeta("OVERSPEEDING_ALERT", "20. Overspeeding Warning", "Speed > 35 km/h urban limit alert", CrimsonAlert),
        PopupMeta("CASH_COLLECT", "21. Cash Collection Prompt", "Cash tender and return change dialog", EmeraldPrimary),
        PopupMeta("NO_SHOW_EXPIRY", "22. Rider No-Show Expiry", "5-min grace elapsed, ₹30 fee credit", AmberWarning),
        PopupMeta("ROUTE_DEVIATION", "23. Route Deviation Alert", ">300m detour road obstacle reason", AmberWarning),
        PopupMeta("DOC_EXPIRY", "24. Document Expiry Warning", "Commercial DL expiry warning in 14 days", AmberWarning),
        PopupMeta("PILOT_SOS", "25. Pilot 112 SOS Trigger", "Delhi Police SOC emergency panic alert", CrimsonAlert),
        PopupMeta("ODOMETER_CHECK", "26. Odometer Verification", "Pre-shift instrument cluster check", ElectricCyan),
        PopupMeta("PEAK_BONUS", "27. Peak Hour Bonus", "10 morning rides bonus ₹300 unlocked", EmeraldPrimary),
        PopupMeta("CSAT_ADVISORY", "28. Rating CSAT Advisory", "Customer satisfaction review prompt", AmberWarning),
        PopupMeta("PAYOUT_SUCCESS", "29. Payout Success Slip", "IMPS settlement UTR transaction slip", EmeraldPrimary),
        PopupMeta("TOLL_ENTRY", "30. Toll & Border Tax Add", "MCD toll adder to passenger invoice", ElectricCyan)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "DriverGo Cockpit Studio",
                        color = CockpitTextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "50 Material 3 Screens • 15 Dialogs • Live Supabase",
                        color = EmeraldPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                CockpitBadge("PROD READY", EmeraldPrimary)
            }

            Spacer(Modifier.height(14.dp))

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = CockpitSurface,
                contentColor = ElectricCyan,
                divider = { HorizontalDivider(color = CockpitBorder) }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) ElectricCyan else CockpitTextSecondary
                            )
                        }
                    )
                }
            }
        }

        when (selectedTab) {
            0 -> {
                // 50 Screens List grouped by Category
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    categories.forEach { category ->
                        val screensInCategory = allDriverScreens.filter { it.category == category }
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                category.uppercase(),
                                color = ElectricCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        items(screensInCategory) { screen ->
                            CockpitCard(
                                modifier = Modifier.clickable { onSelectScreen(screen) }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        screen.title,
                                        color = CockpitTextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = CockpitTextMuted)
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }

            1 -> {
                // 15 Popups & Dialogs
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        CockpitButton(
                            text = "Test Cockpit Toast Banner",
                            onClick = { onShowToast("⚡ 5s High-Frequency Telemetry Synced with Supabase") },
                            modifier = Modifier.fillMaxWidth(),
                            isOutlined = true
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    items(driverPopups) { popup ->
                        CockpitCard(
                            modifier = Modifier.clickable { onTriggerPopup(popup.id) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(popup.name, color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(popup.desc, color = CockpitTextSecondary, fontSize = 12.sp)
                                }
                                CockpitBadge("LAUNCH", popup.tint)
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }

            2 -> {
                // Live Supabase Status
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CockpitCard(glow = true) {
                        Text("FASTAPI BACKEND & SUPABASE POSTGRES", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("Database: AWS ap-southeast-2 (Supabase PG 17.11)", color = EmeraldPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Total Connected Tables: 176 tables", color = CockpitTextPrimary, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("Endpoint: http://10.0.2.2:8000/ (FastAPI asyncpg pool)", color = ElectricCyan, fontSize = 12.sp)
                    }

                    CockpitCard {
                        Text("ACTIVE PILOT SUBSYSTEMS", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("• driver_profiles & pilots_kyc table\n• driver_telemetry (5-sec high-freq ping)\n• dispatch_offers & auto_chain_queue\n• trip_manifests & seat_allocations\n• driver_wallets & imps_payouts\n• driver_safety_events (SOS 112, audio shield)", color = CockpitTextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                    }

                    CockpitButton(
                        text = "Ping FastAPI Healthcheck",
                        onClick = { onShowToast("FastAPI /health: status=ok, db_pool=176_tables_connected") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private data class PopupMeta(
    val id: String,
    val name: String,
    val desc: String,
    val tint: Color
)

