package com.vaisentra.mobility.rider.data

import com.vaisentra.mobility.rider.fcm.FcmTokenSyncPolicy
import com.vaisentra.mobility.rider.fcm.RiderFcmDeepLinkResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderLocationAndFcmTest {

    @Test
    fun riderNativeLocationEvaluator_filtersStaleAndLowAccuracyFixes() {
        val now = 1_700_000_000_000L

        val stale = RiderLocationSample(
            latitude = 28.5000,
            longitude = 77.1000,
            provider = "gps",
            accuracyMeters = 5f,
            timestampMs = now - 800_000L
        )
        val network = RiderLocationSample(
            latitude = 28.6139,
            longitude = 77.2090,
            provider = "network",
            accuracyMeters = 45f,
            timestampMs = now - 10_000L
        )
        val gps = RiderLocationSample(
            latitude = 28.6140,
            longitude = 77.2092,
            provider = "gps",
            accuracyMeters = 8f,
            timestampMs = now - 2_000L
        )
        val zeroCoord = RiderLocationSample(
            latitude = 0.0,
            longitude = 0.0,
            provider = "passive",
            accuracyMeters = 10f,
            timestampMs = now
        )

        val chosen = NativeLocationEvaluator.selectBestLastKnown(
            listOf(stale, zeroCoord, network, gps),
            nowMs = now
        )
        assertNotNull(chosen)
        assertEquals(gps.latitude, chosen!!.latitude, 1e-6)
        assertTrue(NativeLocationEvaluator.isValidCoordinate(19.0760, 72.8777))
    }

    @Test
    fun riderFcmDeepLinkResolver_mapsNotificationActionsCorrectly() {
        assertEquals(
            "pilot_assigned",
            RiderFcmDeepLinkResolver.resolveRoute(mapOf("action_url" to "/rides/ride-101"))
        )
        assertEquals(
            "emergency_sos",
            RiderFcmDeepLinkResolver.resolveRoute(mapOf("event_type" to "EMERGENCY_ANNOUNCEMENT"))
        )
        assertEquals(
            "wallet_hub",
            RiderFcmDeepLinkResolver.resolveRoute(mapOf("action_url" to "/payments/receipt"))
        )
        assertEquals(
            "support_tickets",
            RiderFcmDeepLinkResolver.resolveRoute(mapOf("action_url" to "/support/ticket-1"))
        )
        assertEquals(
            "notifications",
            RiderFcmDeepLinkResolver.resolveRoute(mapOf("event_type" to "ADMIN_BROADCAST"))
        )
    }

    @Test
    fun bookingDraftAndMapSearch_haveNoFabricatedDelhiCoordinates() {
        val draft = BookingDraftState()
        assertFalse(NativeLocationEvaluator.isValidCoordinate(draft.pickupLat, draft.pickupLng))
        assertFalse(NativeLocationEvaluator.isValidCoordinate(draft.destinationLat, draft.destinationLng))
        assertEquals(0.0, draft.pickupLat, 1e-6)
        assertEquals(0.0, draft.pickupLng, 1e-6)

        val searchReq = MapSearchRequest(query = "Kempegowda International Airport")
        assertEquals(null, searchReq.proximity_lat)
        assertEquals(null, searchReq.proximity_lng)
    }

    @Test
    fun riderFcmTokenSyncPolicy_preventsDuplicateSyncAndTracksAccountChanges() {
        val b64User1 = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"sub":"cust-101"}""".toByteArray(Charsets.UTF_8))
        val jwtUser1 = "eyJhbGciOiJIUzI1NiJ9.$b64User1.sig"
        assertEquals("cust-101", FcmTokenSyncPolicy.extractUserSubject(jwtUser1))

        assertTrue(
            FcmTokenSyncPolicy.shouldRegisterWithBackend(
                fcmToken = "fcm_token_rider_xyz12345",
                lastSyncedToken = null,
                currentUserKey = "cust-101",
                lastSyncedUserKey = null
            )
        )
        assertFalse(
            FcmTokenSyncPolicy.shouldRegisterWithBackend(
                fcmToken = "fcm_token_rider_xyz12345",
                lastSyncedToken = "fcm_token_rider_xyz12345",
                currentUserKey = "cust-101",
                lastSyncedUserKey = "cust-101"
            )
        )
        assertTrue(
            FcmTokenSyncPolicy.shouldRegisterWithBackend(
                fcmToken = "fcm_token_rider_xyz12345",
                lastSyncedToken = "fcm_token_rider_xyz12345",
                currentUserKey = "cust-202",
                lastSyncedUserKey = "cust-101"
            )
        )
        assertTrue(
            FcmTokenSyncPolicy.shouldRegisterWithBackend(
                fcmToken = "fcm_token_rider_xyz12345",
                lastSyncedToken = "fcm_token_rider_xyz12345",
                currentUserKey = "cust-101",
                lastSyncedUserKey = "cust-101",
                force = true
            )
        )
    }
}
