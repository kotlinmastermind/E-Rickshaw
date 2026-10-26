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
}
