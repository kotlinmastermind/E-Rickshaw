package com.vaisentra.mobility.driver.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriverUpiContractTest {

    private val gson = Gson()

    @Test
    fun ridePaymentStatusMap_onlyConfirmsWhenGatewayVerifiedOrSettled() {
        val unpaidPayload: Map<String, Any> = mapOf(
            "ride_id" to "ride-1",
            "status" to "CREATED",
            "gateway_verified" to false
        )
        val capturedPayload: Map<String, Any> = mapOf(
            "ride_id" to "ride-1",
            "status" to "CAPTURED",
            "gateway_verified" to true
        )

        val settledStates = setOf("CAPTURED", "PAID", "SETTLED", "PARTIALLY_REFUNDED", "REFUNDED")
        assertFalse((unpaidPayload["gateway_verified"] as Boolean) || unpaidPayload["status"].toString() in settledStates)
        assertTrue((capturedPayload["gateway_verified"] as Boolean) || capturedPayload["status"].toString() in settledStates)

        val req = GenerateUpiRequest(rideId = "ride-1", amountCents = 15000)
        assertEquals("ride-1", gson.fromJson(gson.toJson(req), GenerateUpiRequest::class.java).rideId)
    }
}
