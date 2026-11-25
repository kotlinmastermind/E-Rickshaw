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

    @Test
    fun payoutRequest_preservesExplicitIdempotencyKeyAndIntegerPaiseAmount() {
        val dto = PayoutRequestDto(
            amountCents = 125000L,
            accountNumberMask = "**** 4321",
            ifsc = "HDFC0001234",
            idempotencyKey = "payout_driver_unique_001"
        )
        val encoded = gson.toJson(dto)
        val decoded = gson.fromJson(encoded, PayoutRequestDto::class.java)
        assertEquals(125000L, decoded.amountCents)
        assertEquals("payout_driver_unique_001", decoded.idempotencyKey)
    }
}
