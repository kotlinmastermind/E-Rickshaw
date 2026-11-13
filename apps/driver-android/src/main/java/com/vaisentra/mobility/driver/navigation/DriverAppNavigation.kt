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
    NOTIFICATIONS_CENTER("49. Notifications Center", "Support & Academy")
}

@Composable
fun DriverAppNavigation() {
    var currentScreen by remember { mutableStateOf(DriverScreen.PILOT_LOGIN) }

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
                    else -> currentScreen = DriverScreen.DUTY_DASHBOARD
                }
            })
            DriverScreen.PILOT_PASSWORD_RECOVERY -> PilotPasswordRecoveryScreen(onNavigate = {
                currentScreen = DriverScreen.PILOT_LOGIN
            })
            DriverScreen.PILOT_PROFILE -> PilotProfileScreen(onNavigate = {
                currentScreen = DriverScreen.DUTY_DASHBOARD
            })
            DriverScreen.DOCUMENT_VAULT -> DocumentVaultScreen(onNavigate = {
                currentScreen = DriverScreen.DUTY_DASHBOARD
            })
            DriverScreen.DOC_EXPIRY_WATCHDOG -> DocumentExpiryWatchdogScreen(onNavigate = {
                currentScreen = DriverScreen.DUTY_DASHBOARD
            })

            // 2. Duty & Vehicle
            DriverScreen.VEHICLE_INSPECTION -> VehicleInspectionScreen(onNavigate = { route ->
                if (route == "shift_start") currentScreen = DriverScreen.SHIFT_START
                else currentScreen = DriverScreen.DUTY_DASHBOARD
            })
            DriverScreen.VEHICLE_RC_STATUS -> VehicleRcStatusScreen(onNavigate = {
                currentScreen = DriverScreen.DUTY_DASHBOARD
            })
            DriverScreen.SHIFT_START -> ShiftStartScreen(onNavigate = { route ->
                if (route == "duty") currentScreen = DriverScreen.DUTY_DASHBOARD
                else currentScreen = DriverScreen.DUTY_DASHBOARD
            })
            DriverScreen.DUTY_DASHBOARD -> DutyDashboardScreen(onNavigate = { route ->
                when (route) {
                    "offer" -> currentScreen = DriverScreen.INCOMING_OFFER
                    "pickup_nav" -> currentScreen = DriverScreen.PICKUP_NAV
                    "inspection", "vehicle_inspect" -> currentScreen = DriverScreen.VEHICLE_INSPECTION
                    "earnings" -> currentScreen = DriverScreen.PILOT_EARNINGS
                    "heatmap" -> currentScreen = DriverScreen.HEATMAP_SURGE
                    "catalog" -> currentScreen = DriverScreen.DUTY_DASHBOARD
                    "pilot_profile" -> currentScreen = DriverScreen.PILOT_PROFILE
                    else -> currentScreen = DriverScreen.DUTY_DASHBOARD
                }
            })
            DriverScreen.FATIGUE_CHECK -> FatigueCheckScreen(onNavigate = {
                currentScreen = DriverScreen.DUTY_DASHBOARD
            })
            DriverScreen.SHIFT_END -> ShiftEndScreen(onNavigate = { route ->
                if (route == "summary") currentScreen = DriverScreen.SHIFT_SUMMARY
                else currentScreen = DriverScreen.DUTY_DASHBOARD
            })
            DriverScreen.DRIVER_PREFERENCES -> DriverPreferencesScreen(onNavigate = {
                currentScreen = DriverScreen.DUTY_DASHBOARD
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
                onPayoutSuccess = { triggerPopup("PAYOUT_SUCCESS") },
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

            // 10. Support & Academy
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

        // Host for all 15 Popups/Modals & Toasts
        DriverGoDialogHost()
    }
}

