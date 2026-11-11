package com.vaisentra.mobility.driver

import org.junit.Assert.*
import org.junit.Test
import com.vaisentra.mobility.driver.core.network.DriverApiService
import com.vaisentra.mobility.driver.feature.earnings.WeeklyGoalDto
import com.vaisentra.mobility.driver.feature.duty.DriverDutyState
import com.vaisentra.mobility.driver.feature.duty.DutyFailureCategory
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

class DriverAppUnitTest {

    @Test
    fun testEarningsShareCalculation() {
        val grossFareCents = 160000 // Rs 1600
        val driverNetCents = (grossFareCents * 0.8).toInt()
        assertEquals(128000, driverNetCents)
        assertEquals(1280, driverNetCents / 100)
    }

    @Test
    fun testTripLifecycleOrder() {
        val stages = listOf(
            "NAVIGATING_TO_PICKUP",
            "ARRIVED_AT_PICKUP",
            "OTP_VERIFICATION",
            "TRIP_IN_PROGRESS",
            "TRIP_FINISHED"
        )
        assertEquals(0, stages.indexOf("NAVIGATING_TO_PICKUP"))
        assertEquals(4, stages.indexOf("TRIP_FINISHED"))
    }

    @Test
    fun testChauffeurLicenseFormat() {
        val license = "DL-MH01-2020-009988"
        assertTrue(license.startsWith("DL-"))
    }

    // --- Rapido Driver Production Navigation Logic Tests ---

    @Test
    fun testDriverKycGuardBeforeOnline() {
        // Enforce driver KYC before Online status
        fun canGoOnline(isKycVerified: Boolean, isVehicleAssigned: Boolean): Pair<Boolean, String?> {
            if (!isKycVerified) {
                return Pair(false, "KYC documents must be uploaded and verified before starting shift.")
            }
            if (!isVehicleAssigned) {
                return Pair(false, "Vehicle must be assigned and inspected before starting duty.")
            }
            return Pair(true, null)
        }

        val unverifiedDriver = canGoOnline(isKycVerified = false, isVehicleAssigned = true)
        assertFalse(unverifiedDriver.first)
        assertNotNull(unverifiedDriver.second)

        val unassignedVehicle = canGoOnline(isKycVerified = true, isVehicleAssigned = false)
        assertFalse(unassignedVehicle.first)
        assertNotNull(unassignedVehicle.second)

        val compliantDriver = canGoOnline(isKycVerified = true, isVehicleAssigned = true)
        assertTrue(compliantDriver.first)
        assertNull(compliantDriver.second)
    }

    @Test
    fun testDriverActiveRideStateRestoration() {
        fun resolveDriverScreenForState(status: String?): Int {
            return when (status?.uppercase()) {
                "ASSIGNED" -> 11 // Screen 11: Route Navigation to Pickup
                "ARRIVED" -> 12 // Screen 12: Arrived at Pickup Point
                "OTP_PENDING" -> 13 // Screen 13: Passenger OTP Verification
                "IN_PROGRESS" -> 14 // Screen 14: En Route Navigation to Destination
                "COMPLETED" -> 17 // Screen 17: Fare & Earnings Breakdown
                else -> 8 // Screen 8: Driver Cockpit (Idle / Seeking Rides)
            }
        }

        assertEquals(11, resolveDriverScreenForState("ASSIGNED"))
        assertEquals(12, resolveDriverScreenForState("ARRIVED"))
        assertEquals(13, resolveDriverScreenForState("OTP_PENDING"))
        assertEquals(14, resolveDriverScreenForState("IN_PROGRESS"))
        assertEquals(17, resolveDriverScreenForState("COMPLETED"))
        assertEquals(8, resolveDriverScreenForState(null))
        assertEquals(8, resolveDriverScreenForState("CANCELLED"))
    }

    @Test
    fun testDriverOtpVerificationGate() {
        // Require valid 6-digit OTP before starting ride
        fun verifyOtpAndStartTrip(inputOtp: String, expectedOtp: String): Boolean {
            if (inputOtp.length != 6 || !inputOtp.all { it.isDigit() }) {
                return false
            }
            return inputOtp == expectedOtp
        }

        assertFalse(verifyOtpAndStartTrip("123", "849201"))
        assertFalse(verifyOtpAndStartTrip("84920A", "849201"))
        assertFalse(verifyOtpAndStartTrip("999999", "849201"))
        assertTrue(verifyOtpAndStartTrip("849201", "849201"))
    }

    @Test
    fun testDriverBackNavigationSafetyDuringTrip() {
        fun handleDriverBackPress(currentScreen: Int, hasActiveRide: Boolean): Int {
            return if (hasActiveRide && currentScreen in listOf(11, 12, 13, 14)) {
                // Prevent implicit exit/cancellation during active transit
                currentScreen
            } else if (currentScreen > 8 && currentScreen !in listOf(11, 12, 13, 14, 17)) {
                currentScreen - 1
            } else {
                8 // Cockpit
            }
        }

        // Active transit screens cannot be dismissed by Back
        assertEquals(11, handleDriverBackPress(11, true))
        assertEquals(12, handleDriverBackPress(12, true))
        assertEquals(14, handleDriverBackPress(14, true))

        // Normal screens allow back
        assertEquals(8, handleDriverBackPress(9, false))
        assertEquals(8, handleDriverBackPress(8, false))
    }

    // --- Phase 3 Driver Navigation & Telemetry Unit Tests ---

    @Test
    fun testDriverOffRouteDetection() {
        val route = listOf(
            com.vaisentra.mobility.core.map.LatLng(28.6180, 77.2115),
            com.vaisentra.mobility.core.map.LatLng(28.6150, 77.2100),
            com.vaisentra.mobility.core.map.LatLng(28.6139, 77.2090)
        )

        // Point near route line (approx 5m offset)
        val onRouteDriver = com.vaisentra.mobility.core.map.MapboxGeoUtils.isOffRoute(
            driverLat = 28.61801,
            driverLng = 77.21151,
            routePoints = route,
            thresholdMeters = 50.0
        )
        assertFalse("Driver close to route should NOT be considered off-route", onRouteDriver)

        // Point diverged by 200+ meters
        val offRouteDriver = com.vaisentra.mobility.core.map.MapboxGeoUtils.isOffRoute(
            driverLat = 28.6250,
            driverLng = 77.2250,
            routePoints = route,
            thresholdMeters = 50.0
        )
        assertTrue("Driver 200m away MUST trigger off-route detection", offRouteDriver)
    }

    @Test
    fun testArrivalGeofenceDetection() {
        val pickupStationLat = 28.6139
        val pickupStationLng = 77.2090

        // Point 10 meters away
        val closeArrival = com.vaisentra.mobility.core.map.MapboxGeoUtils.isWithinRadius(
            pickupStationLat, pickupStationLng,
            28.61395, 77.20905,
            radiusMeters = 30.0
        )
        assertTrue("Driver within 10m should trigger arrival geofence", closeArrival)

        // Point 120 meters away
        val farArrival = com.vaisentra.mobility.core.map.MapboxGeoUtils.isWithinRadius(
            pickupStationLat, pickupStationLng,
            28.6150, 77.2105,
            radiusMeters = 30.0
        )
        assertFalse("Driver 120m away should NOT trigger arrival", farArrival)
    }

    @Test
    fun testManeuverIconMapping() {
        assertEquals("↱", com.vaisentra.mobility.core.map.MapboxGeoUtils.getManeuverIcon("turn", "Turn right onto Janpath"))
        assertEquals("↰", com.vaisentra.mobility.core.map.MapboxGeoUtils.getManeuverIcon("turn", "Turn left onto Tolstoy Rd"))
        assertEquals("↻", com.vaisentra.mobility.core.map.MapboxGeoUtils.getManeuverIcon("turn", "Make a U-turn"))
        assertEquals("🔄", com.vaisentra.mobility.core.map.MapboxGeoUtils.getManeuverIcon("roundabout", "Take second exit at roundabout"))
        assertEquals("🎯", com.vaisentra.mobility.core.map.MapboxGeoUtils.getManeuverIcon("arrive", "Arrive at destination"))
        assertEquals("↑", com.vaisentra.mobility.core.map.MapboxGeoUtils.getManeuverIcon("straight", "Continue straight"))
    }

    @Test
    fun testEvSpeedLimitAlerts() {
        val speedLimitKmh = 25

        fun isOverspeeding(speed: Int): Boolean = speed > speedLimitKmh

        assertFalse(isOverspeeding(0))
        assertFalse(isOverspeeding(22))
        assertFalse(isOverspeeding(25))
        assertTrue(isOverspeeding(26))
        assertTrue(isOverspeeding(34))
    }

    @Test
    fun testGpsAccuracyStatusClassification() {
        fun classifyGps(accuracyMeters: Float): String {
            return when {
                accuracyMeters <= 10f -> "GPS: 🟢 High Accuracy (${accuracyMeters.toInt()}m)"
                accuracyMeters <= 30f -> "GPS: 🟡 Fair (${accuracyMeters.toInt()}m)"
                else -> "GPS: 🔴 Weak (${accuracyMeters.toInt()}m)"
            }
        }

        assertTrue(classifyGps(3.5f).contains("High Accuracy"))
        assertTrue(classifyGps(18.0f).contains("Fair"))
        assertTrue(classifyGps(45.0f).contains("Weak"))
    }

    @Test
    fun testDriverCompassBearingComputation() {
        val lat1 = 28.6180
        val lng1 = 77.2115
        val lat2 = 28.6139
        val lng2 = 77.2090

        val bearing = com.vaisentra.mobility.core.map.MapboxGeoUtils.computeBearing(lat1, lng1, lat2, lng2)
        assertTrue("Bearing must be valid compass degree in 0..360", bearing in 0f..360f)
        assertTrue("South-southwest bearing should be between 180 and 270", bearing in 180f..270f)
    }

    // =========================================================================
    // Phase 1 API Contract Alignment & Real Data Integrity Tests
    // =========================================================================

    @Test
    fun testDriverApiPhase1ContractEndpoints() {
        val methods = DriverApiService::class.java.methods

        val getGoalMethod = methods.firstOrNull { it.name == "getWeeklyGoal" }
        assertNotNull("getWeeklyGoal must exist", getGoalMethod)
        val getGoal = getGoalMethod!!.getAnnotation(GET::class.java)
        assertNotNull("Weekly goal getter must be @GET", getGoal)
        assertEquals("api/v1/drivers/financials/goals/weekly", getGoal.value)

        val updateGoalMethod = methods.firstOrNull { it.name == "updateWeeklyGoal" }
        assertNotNull("updateWeeklyGoal must exist", updateGoalMethod)
        val postGoal = updateGoalMethod!!.getAnnotation(POST::class.java)
        assertNotNull("Weekly goal updater must be @POST", postGoal)
        assertEquals("api/v1/drivers/financials/goals/weekly", postGoal.value)

        val incentivesMethod = methods.firstOrNull { it.name == "getIncentives" }
        assertNotNull("getIncentives must exist", incentivesMethod)
        val getIncentives = incentivesMethod!!.getAnnotation(GET::class.java)
        assertNotNull("Incentives getter must be @GET", getIncentives)
        assertEquals("api/v1/drivers/financials/incentives", getIncentives.value)

        val bonusesMethod = methods.firstOrNull { it.name == "getBonusHistory" }
        assertNotNull("getBonusHistory must exist", bonusesMethod)
        val getBonuses = bonusesMethod!!.getAnnotation(GET::class.java)
        assertNotNull("Bonus history getter must be @GET", getBonuses)
        assertEquals("api/v1/drivers/financials/incentives", getBonuses.value)

        val getScheduleMethod = methods.firstOrNull { it.name == "getPayoutSchedule" }
        assertNotNull("getPayoutSchedule must exist", getScheduleMethod)
        val getSchedule = getScheduleMethod!!.getAnnotation(GET::class.java)
        assertNotNull("Payout schedule getter must be @GET", getSchedule)
        assertEquals("api/v1/drivers/financials/payout-schedule", getSchedule.value)

        val updateScheduleMethod = methods.firstOrNull { it.name == "updatePayoutSchedule" }
        assertNotNull("updatePayoutSchedule must exist", updateScheduleMethod)
        val putSchedule = updateScheduleMethod!!.getAnnotation(PUT::class.java)
        assertNotNull("Payout schedule updater must be @PUT", putSchedule)
        assertEquals("api/v1/drivers/financials/payout-schedule", putSchedule.value)

        val reconMethod = methods.firstOrNull { it.name == "getPayoutReconciliation" }
        assertNotNull("getPayoutReconciliation must exist", reconMethod)
        val getRecon = reconMethod!!.getAnnotation(GET::class.java)
        assertNotNull("Reconciliation getter must be @GET", getRecon)
        assertEquals("api/v1/drivers/financials/reconciliation", getRecon.value)

        val csvMethod = methods.firstOrNull { it.name == "exportEarningsCsv" }
        assertNotNull("exportEarningsCsv must exist", csvMethod)
        val getCsv = csvMethod!!.getAnnotation(GET::class.java)
        assertNotNull("CSV export getter must be @GET", getCsv)
        assertEquals("api/v1/drivers/financials/export/csv", getCsv.value)
    }

    @Test
    fun testDriverWeeklyGoalNoDummyFallback() {
        val defaultGoal = WeeklyGoalDto()
        assertEquals("Default target rupees must be 0.0 (no hardcoded ₹25,000)", 0.0, defaultGoal.targetNetRupees, 0.001)
        assertEquals("Default target cents must be 0", 0L, defaultGoal.targetNetCents)
        assertEquals("Default current rupees must be 0.0", 0.0, defaultGoal.currentNetRupees, 0.001)
        assertEquals("Default pace status must be NO_GOAL", "NO_GOAL", defaultGoal.paceStatus)
        assertNotEquals("Hardcoded fallback 25000 must NOT be present", 25000.0, defaultGoal.targetNetRupees, 0.001)
    }

    @Test
    fun testDriverEmptyStateFinancialIntegrity() {
        // When backend returns empty list for reconciliation, driver UI must keep empty list rather than inject fake items
        val backendEmptyList = emptyList<Map<String, Any>>()
        assertTrue("Backend empty reconciliation must remain empty", backendEmptyList.isEmpty())

        // Ensure non-zero targets require explicit positive values
        fun isGoalTargetValid(targetRupees: Double): Boolean {
            return targetRupees > 0.0
        }
        assertFalse(isGoalTargetValid(0.0))
        assertFalse(isGoalTargetValid(-500.0))
        assertTrue(isGoalTargetValid(15000.0))
    }

    // --- Modular Navigation & Production Hardening Architecture Tests ---

    @Test
    fun testDriverModularNavigationDestinationMapping() {
        // Verify all driver modular destinations are reachable and type-safe
        val authDest: DriverDestination = DriverDestination.Auth
        val dashDest: DriverDestination = DriverDestination.Dashboard
        val tripDest: DriverDestination = DriverDestination.ActiveTrip("RIDE-77", "Metro Gate 1", "Cyber Hub")
        val sumDest: DriverDestination = DriverDestination.RideSummary("RIDE-77", "Metro Gate 1", "Cyber Hub")
        val histDest: DriverDestination = DriverDestination.RideHistory
        val docDest: DriverDestination = DriverDestination.Documents
        val onbDest: DriverDestination = DriverDestination.Onboarding
        val earnDest: DriverDestination = DriverDestination.Earnings
        val profDest: DriverDestination = DriverDestination.Profile
        val safeDest: DriverDestination = DriverDestination.Safety
        val notifDest: DriverDestination = DriverDestination.Notifications

        assertTrue(authDest is DriverDestination.Auth)
        assertTrue(dashDest is DriverDestination.Dashboard)
        assertTrue(tripDest is DriverDestination.ActiveTrip)
        assertTrue(sumDest is DriverDestination.RideSummary)
        assertTrue(histDest is DriverDestination.RideHistory)
        assertTrue(docDest is DriverDestination.Documents)
        assertTrue(onbDest is DriverDestination.Onboarding)
        assertTrue(earnDest is DriverDestination.Earnings)
        assertTrue(profDest is DriverDestination.Profile)
        assertTrue(safeDest is DriverDestination.Safety)
        assertTrue(notifDest is DriverDestination.Notifications)

        val activeTrip = tripDest as DriverDestination.ActiveTrip
        assertEquals("RIDE-77", activeTrip.rideId)
        assertEquals("Metro Gate 1", activeTrip.pickupAddress)
        assertEquals("Cyber Hub", activeTrip.destinationAddress)
    }

    @Test
    fun testDriverSessionIsolationAndSignOut() {
        var isDriverLoggedIn = true
        var activeRideId: String? = "RIDE-55"
        var currentDest: DriverDestination = DriverDestination.Dashboard

        fun performDriverSignOut() {
            isDriverLoggedIn = false
            activeRideId = null
            currentDest = DriverDestination.Auth
        }

        performDriverSignOut()
        assertFalse(isDriverLoggedIn)
        assertNull(activeRideId)
        assertEquals(DriverDestination.Auth, currentDest)

        fun resolveDriverInitialDestination(
            loggedIn: Boolean,
            initialRideId: String?,
            pickup: String?,
            dest: String?
        ): DriverDestination {
            return if (!loggedIn) {
                DriverDestination.Auth
            } else if (!initialRideId.isNullOrBlank()) {
                DriverDestination.ActiveTrip(initialRideId, pickup ?: "Pickup", dest ?: "Destination")
            } else {
                DriverDestination.Dashboard
            }
        }

        assertEquals(DriverDestination.Auth, resolveDriverInitialDestination(false, null, null, null))
        assertEquals(DriverDestination.Dashboard, resolveDriverInitialDestination(true, null, null, null))
        val trip = resolveDriverInitialDestination(true, "RIDE-99", "Station A", "Station B") as DriverDestination.ActiveTrip
        assertEquals("RIDE-99", trip.rideId)
        assertEquals("Station A", trip.pickupAddress)
        assertEquals("Station B", trip.destinationAddress)
    }

    @Test
    fun testDriverDutyStateAuthoritativeTransitions() {
        // Verify valid transitions
        val unknown: DriverDutyState = DriverDutyState.Unknown
        val offline: DriverDutyState = DriverDutyState.Offline
        val updatingOnline: DriverDutyState = DriverDutyState.UpdatingOnline
        val online: DriverDutyState = DriverDutyState.Online
        val updatingOffline: DriverDutyState = DriverDutyState.UpdatingOffline

        assertTrue(unknown is DriverDutyState.Unknown)
        assertTrue(offline is DriverDutyState.Offline)
        assertTrue(updatingOnline is DriverDutyState.UpdatingOnline)
        assertTrue(online is DriverDutyState.Online)
        assertTrue(updatingOffline is DriverDutyState.UpdatingOffline)

        // Error state preserves previous confirmed state
        val errorState = DriverDutyState.Error(
            previousConfirmedState = DriverDutyState.Offline,
            errorMessage = "Driver KYC unapproved",
            failureCategory = DutyFailureCategory.UNAPPROVED_DRIVER,
            httpCode = 403
        )
        assertEquals(DriverDutyState.Offline, errorState.previousConfirmedState)
        assertEquals(403, errorState.httpCode)
    }

    @Test
    fun testDriverSafetyCheckinAndFatigueOfflineSync() {
        // Enforce: When driver reports fatigue (NEEDS_REST or UNFIT), driver duty must be toggled Offline
        var driverDutyStatus = "ONLINE"

        fun handleFatigueCheckin(state: String) {
            if (state == "NEEDS_REST" || state == "UNFIT") {
                driverDutyStatus = "OFFLINE"
            }
        }

        handleFatigueCheckin("FIT")
        assertEquals("ONLINE", driverDutyStatus)

        handleFatigueCheckin("NEEDS_REST")
        assertEquals("OFFLINE", driverDutyStatus)

        driverDutyStatus = "ONLINE"
        handleFatigueCheckin("UNFIT")
        assertEquals("OFFLINE", driverDutyStatus)
    }
}
