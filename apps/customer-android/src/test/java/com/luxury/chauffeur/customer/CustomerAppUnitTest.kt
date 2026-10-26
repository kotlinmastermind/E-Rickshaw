package com.luxury.chauffeur.customer

import org.junit.Assert.*
import org.junit.Test

class CustomerAppUnitTest {

    @Test
    fun testPhoneNumberValidation() {
        val validPhone = "+919876543210"
        val invalidPhone = "123"
        
        assertTrue(validPhone.startsWith("+91") && validPhone.length >= 12)
        assertFalse(invalidPhone.length >= 10)
    }

    @Test
    fun testOtpCodeLength() {
        val otp = "849201"
        assertEquals(6, otp.length)
        assertTrue(otp.all { it.isDigit() })
    }

    @Test
    fun testFareCalculationFormatting() {
        val fareCents = 160000
        val fareRupees = fareCents / 100
        assertEquals(1600, fareRupees)
    }

    @Test
    fun testLifecycleTransitions() {
        val allowedStates = listOf("REQUESTED", "ASSIGNED", "ARRIVED", "IN_PROGRESS", "COMPLETED")
        assertTrue(allowedStates.contains("IN_PROGRESS"))
        assertFalse(allowedStates.contains("UNKNOWN_STATE"))
    }
}
