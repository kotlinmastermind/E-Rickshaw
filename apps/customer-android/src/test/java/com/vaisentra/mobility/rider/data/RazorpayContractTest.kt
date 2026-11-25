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
                userId = "user_alpha",
                amountCents = 14500,
                currency = "INR",
                idempotencyKey = "idem_ride_100",
                state = CheckoutSessionState.ORDER_CREATED
            )
        )
        storeBeforeRecreation.markCheckoutOpen("order_ride_100")

        storeBeforeRecreation.saveSession(
            CheckoutSession(
                orderId = "order_topup_200",
                backendPaymentId = "pay_backend_topup_200",
                checkoutType = CheckoutFlowType.WALLET_TOPUP,
                rideId = null,
                userId = "user_alpha",
                amountCents = 50000,
                currency = "INR",
                idempotencyKey = "idem_topup_200",
                state = CheckoutSessionState.ORDER_CREATED
            )
        )
        storeBeforeRecreation.markCheckoutOpen("order_topup_200")

        // Simulate Activity / process death recreation
        val restoredStore = CheckoutSessionStore(backingStorage)
        assertTrue(restoredStore.resolveSuccessCallback("order_unknown_999", "pay_gw_1", "sig_1", "user_alpha") is CallbackResolutionResult.UnknownOrder)
        assertTrue(restoredStore.resolveSuccessCallback("", "pay_gw_1", "sig_1", "user_alpha") is CallbackResolutionResult.MissingIdentity)

        // Ride callback resolves strictly by orderId to RIDE_FARE + user_alpha
        val rideBegin = restoredStore.tryBeginVerification("order_ride_100", "pay_gw_ride_1", "sig_ride_1", "user_alpha")
        assertNotNull(rideBegin)
        assertEquals(CheckoutSessionState.VERIFICATION_PENDING, rideBegin!!.state)
        assertEquals(CheckoutFlowType.RIDE_FARE, rideBegin.checkoutType)
        assertEquals("ride_uuid_100", rideBegin.rideId)

        // Duplicate callback while VERIFICATION_PENDING is suppressed
        assertNull(restoredStore.tryBeginVerification("order_ride_100", "pay_gw_ride_1", "sig_ride_1", "user_alpha"))

        restoredStore.markVerifiedAndClearIntentKey("order_ride_100", "RIDE_FARE_user_alpha_ride_uuid_100")
        assertEquals(CheckoutSessionState.VERIFIED, restoredStore.getSessionByOrderId("order_ride_100")?.state)

        // Wallet Top-up session remains isolated and transitions across retryable -> verified
        val walletBegin = restoredStore.tryBeginVerification("order_topup_200", "pay_gw_topup_1", "sig_topup_1", "user_alpha")
        assertNotNull(walletBegin)
        restoredStore.markRetryableFailure("order_topup_200", "Temporary network timeout")
        val retryable = restoredStore.getRetryableSession(CheckoutFlowType.WALLET_TOPUP, "user_alpha")
        assertNotNull(retryable)
        assertEquals(CheckoutSessionState.VERIFICATION_RETRYABLE, retryable!!.state)
        assertNull(restoredStore.getRetryableSession(CheckoutFlowType.RIDE_FARE, "user_alpha"))
    }

    @Test
    fun terminal4xxFailuresNeverAppearInRetryAndOldSupersededOrdersAreRejected() {
        val storage = InMemoryCheckoutStorage()
        val store = CheckoutSessionStore(storage)

        store.saveSession(
            CheckoutSession(
                orderId = "order_ride_old",
                backendPaymentId = "pay_1",
                checkoutType = CheckoutFlowType.RIDE_FARE,
                rideId = "ride_77",
                userId = "user_alpha",
                amountCents = 12000,
                currency = "INR",
                idempotencyKey = "idem_old",
                state = CheckoutSessionState.ORDER_CREATED
            ),
            nowMs = 1000L
        )
        // New checkout order for the same ride supersedes the older open order
        store.saveSession(
            CheckoutSession(
                orderId = "order_ride_new",
                backendPaymentId = "pay_1",
                checkoutType = CheckoutFlowType.RIDE_FARE,
                rideId = "ride_77",
                userId = "user_alpha",
                amountCents = 12000,
                currency = "INR",
                idempotencyKey = "idem_new",
                state = CheckoutSessionState.ORDER_CREATED
            ),
            nowMs = 2000L
        )

        assertEquals(CheckoutSessionState.EXPIRED, store.getSessionByOrderId("order_ride_old")?.state)
        assertTrue(store.resolveSuccessCallback("order_ride_old", "pay_gw", "sig", "user_alpha") is CallbackResolutionResult.OldOrderSuperseded)

        // Start verification on new order and fail it terminally (e.g., HTTP 400 invalid signature)
        val started = store.tryBeginVerification("order_ride_new", "pay_gw_2", "bad_sig", "user_alpha", nowMs = 2500L)
        assertNotNull(started)
        store.markTerminalAndClearIntentKey("order_ride_new", "RIDE_FARE_user_alpha_ride_77", CheckoutSessionState.TERMINAL_FAILURE, "Invalid signature")
        assertNull(store.getRetryableSession(CheckoutFlowType.RIDE_FARE, "user_alpha"))
        assertTrue(store.resolveSuccessCallback("order_ride_new", "pay_gw_2", "bad_sig", "user_alpha") is CallbackResolutionResult.TerminalState)
    }

    @Test
    fun missingOrderIdAndWrongUserCallbacksEnterSafeNonContaminatingStates() {
        val storage = InMemoryCheckoutStorage()
        val store = CheckoutSessionStore(storage)

        store.saveSession(
            CheckoutSession(
                orderId = "order_wallet_open",
                backendPaymentId = "pay_w1",
                checkoutType = CheckoutFlowType.WALLET_TOPUP,
                rideId = null,
                userId = "user_owner",
                amountCents = 25000,
                currency = "INR",
                idempotencyKey = "idem_w1",
                state = CheckoutSessionState.ORDER_CREATED
            )
        )
        store.saveSession(
            CheckoutSession(
                orderId = "order_ride_waiting",
                backendPaymentId = "pay_r1",
                checkoutType = CheckoutFlowType.RIDE_FARE,
                rideId = "ride_99",
                userId = "user_owner",
                amountCents = 18000,
                currency = "INR",
                idempotencyKey = "idem_r1",
                state = CheckoutSessionState.ORDER_CREATED
            )
        )
        // Only wallet checkout was actually opened
        store.markCheckoutOpen("order_wallet_open")

        // Callback without orderId affects ONLY CHECKOUT_OPEN session -> RECONCILIATION_REQUIRED, leaving RIDE_FARE untouched
        val affected = store.markOpenCheckoutReconciliationRequired("Missing order_id", "user_owner")
        assertEquals(1, affected.size)
        assertEquals("order_wallet_open", affected.first().orderId)
        assertEquals(CheckoutSessionState.RECONCILIATION_REQUIRED, store.getSessionByOrderId("order_wallet_open")?.state)
        assertEquals(CheckoutSessionState.ORDER_CREATED, store.getSessionByOrderId("order_ride_waiting")?.state)

        // Wrong user attempting callback on user_owner's ride order becomes TERMINAL_FAILURE
        val wrongUserRes = store.resolveSuccessCallback("order_ride_waiting", "pay_gw_9", "sig_9", "attacker_user")
        assertTrue(wrongUserRes is CallbackResolutionResult.OwnershipMismatch)
        assertEquals(CheckoutSessionState.TERMINAL_FAILURE, store.getSessionByOrderId("order_ride_waiting")?.state)
        assertNull(store.getRetryableSession(CheckoutFlowType.RIDE_FARE, "user_owner"))
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
