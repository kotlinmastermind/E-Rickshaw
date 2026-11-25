package com.vaisentra.mobility.rider.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    @Test
    fun checkoutSessionStore_isolatesRideAndWalletAcrossProcessRecreationAndBlocksDuplicates() {
        val backingStorage = InMemoryCheckoutStorage()
        val storeBeforeRecreation = CheckoutSessionStore(backingStorage)

        storeBeforeRecreation.saveSession(
            CheckoutSession(
                orderId = "order_ride_100",
                backendPaymentId = "pay_backend_ride_100",
                checkoutType = CheckoutFlowType.RIDE_FARE,
                rideId = "ride_uuid_100",
                amountCents = 14500,
                currency = "INR",
                idempotencyKey = "idem_ride_100"
            )
        )
        storeBeforeRecreation.saveSession(
            CheckoutSession(
                orderId = "order_topup_200",
                backendPaymentId = "pay_backend_topup_200",
                checkoutType = CheckoutFlowType.WALLET_TOPUP,
                rideId = null,
                amountCents = 50000,
                currency = "INR",
                idempotencyKey = "idem_topup_200"
            )
        )

        // Simulate Activity / process recreation by instantiating a brand new store reading shared backing storage
        val restoredStore = CheckoutSessionStore(backingStorage)
        val unknown = restoredStore.tryBeginVerification("order_unknown_999", "pay_gw_1", "sig_1")
        assertNull(unknown)

        // Ride callback resolves to exact RIDE_FARE + rideId despite WALLET_TOPUP having been saved later!
        val rideBegin = restoredStore.tryBeginVerification("order_ride_100", "pay_gw_ride_1", "sig_ride_1")
        assertNotNull(rideBegin)
        assertEquals(CheckoutFlowType.RIDE_FARE, rideBegin!!.checkoutType)
        assertEquals("ride_uuid_100", rideBegin.rideId)
        assertEquals("pay_backend_ride_100", rideBegin.backendPaymentId)

        // Duplicate callback for same order_id while VERIFYING is dropped idempotently
        val dupRideBegin = restoredStore.tryBeginVerification("order_ride_100", "pay_gw_ride_1", "sig_ride_1")
        assertNull(dupRideBegin)

        restoredStore.markVerifiedAndClearIntentKey("order_ride_100", "RIDE_FARE_ride_uuid_100")

        // Wallet Top-up session remained isolated and uncorrupted
        val walletBegin = restoredStore.tryBeginVerification("order_topup_200", "pay_gw_topup_1", "sig_topup_1")
        assertNotNull(walletBegin)
        assertEquals(CheckoutFlowType.WALLET_TOPUP, walletBegin!!.checkoutType)
        assertNull(walletBegin.rideId)

        // Simulated transient failure -> retryable verification still recoverable
        restoredStore.markRetryableFailure("order_topup_200")
        val retryable = restoredStore.getRetryableSession(CheckoutFlowType.WALLET_TOPUP)
        assertNotNull(retryable)
        assertEquals("order_topup_200", retryable!!.orderId)
        assertEquals("pay_gw_topup_1", retryable.paymentId)
    }

    @Test
    fun razorpayPublicKeyValidator_rejectsMissingAndPlaceholderKeys() {
        assertFalse(CheckoutSessionStore.isValidRazorpayPublicKey(null))
        assertFalse(CheckoutSessionStore.isValidRazorpayPublicKey(""))
        assertFalse(CheckoutSessionStore.isValidRazorpayPublicKey("rzp_test_placeholder"))
        assertFalse(CheckoutSessionStore.isValidRazorpayPublicKey("rzp_short"))
        assertTrue(CheckoutSessionStore.isValidRazorpayPublicKey("rzp_test_ValidKey1234"))
        assertTrue(CheckoutSessionStore.isValidRazorpayPublicKey("rzp_live_ValidKey1234"))
    }
}
