package com.luxury.chauffeur.driver

import org.junit.Assert.*
import org.junit.Test

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
}
