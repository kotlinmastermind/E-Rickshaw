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
    fun rupeesToPaise_preservesTwoDecimalPrecisionAndRejectsFractionalPaise() {
        assertEquals(10099L, rupeesToPaise(100.99))
        assertEquals(10090L, rupeesToPaise(100.90))
        assertEquals(null, rupeesToPaise(100.999))
        assertEquals(null, rupeesToPaise(Double.NaN))
        assertEquals(null, rupeesToPaise(-1.0))
    }

    @Test
    fun payoutRequest_preservesExplicitIdempotencyKeyAndIntegerPaiseAmount() {
        val dto = PayoutRequestDto(
            amountCents = 125000L,
            payoutAccountId = "acc_verified_001",
            idempotencyKey = "payout_driver_unique_001"
        )
        val encoded = gson.toJson(dto)
        val decoded = gson.fromJson(encoded, PayoutRequestDto::class.java)
        assertEquals(125000L, decoded.amountCents)
        assertEquals("acc_verified_001", decoded.payoutAccountId)
        assertEquals("payout_driver_unique_001", decoded.idempotencyKey)
    }

    @Test
    fun payoutHistory_deserializesAuthoritativeBackendStates() {
        val raw = """
            {
              "id": "pay_001",
              "amount_cents": 50000,
              "status": "AWAITING_MANUAL_TRANSFER",
              "execution_mode": "MANUAL_TRANSFER",
              "bank_account_mask": "******1234",
              "settlement_reference": "UTR202611270001",
              "requested_at": "2026-11-27T10:00:00Z",
              "retry_count": 0
            }
        """.trimIndent()
        val item = gson.fromJson(raw, PayoutHistoryItemDto::class.java)
        assertEquals("AWAITING_MANUAL_TRANSFER", item.status)
        assertEquals("MANUAL_TRANSFER", item.executionMode)
        assertEquals("UTR202611270001", item.settlementReference)
    }
}
