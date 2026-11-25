package com.vaisentra.mobility.rider.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RazorpayContractTest {

    private val gson = Gson()

    @Test
    fun ridePaymentOrderResponse_decodesAmountAndAmountCentsAlternates() {
        val legacyJson = """{"order_id":"order_abc123","amount":18500,"currency":"INR","key_id":"rzp_test_123"}"""
        val modernJson = """{"order_id":"order_xyz789","amount_cents":24500,"currency":"INR","key_id":"rzp_live_123"}"""

        val legacyParsed = gson.fromJson(legacyJson, RidePaymentOrderResponse::class.java)
        val modernParsed = gson.fromJson(modernJson, RidePaymentOrderResponse::class.java)

        assertEquals(18500, legacyParsed.amountCents)
        assertEquals("order_abc123", legacyParsed.effectiveOrderId)
        assertEquals(24500, modernParsed.amountCents)
        assertEquals("order_xyz789", modernParsed.effectiveOrderId)
    }

    @Test
    fun topupAndRideRequests_serializeIdempotencyKeyWithoutClientFareOverride() {
        val topupReq = TopupCreateOrderRequest(amountCents = 25000, idempotencyKey = "idem_topup_001")
        val rideReq = RidePaymentOrderRequest(rideId = "ride-uuid-1", idempotencyKey = "idem_ride_001")

        val topupJson = gson.toJson(topupReq)
        val rideJson = gson.toJson(rideReq)

        assertTrue(topupJson.contains("idem_topup_001"))
        assertTrue(rideJson.contains("idem_ride_001"))
        assertNull(rideReq.amountCents)
        assertNull(rideReq.amountRupees)
    }
}
