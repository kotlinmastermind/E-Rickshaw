package com.vaisentra.mobility.driver.data

import com.google.gson.Gson
import com.vaisentra.mobility.driver.fcm.DriverFcmDeepLinkResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeLocationAndFcmTest {

    private val gson = Gson()

    @Test
    fun driverLocationPingRequest_defaultsToNativeGpsSource() {
        val req = DriverLocationPingRequest(latitude = 28.6139, longitude = 77.2090)
        assertEquals("GPS", req.locationSource)
        val json = gson.toJson(req)
        assertTrue(json.contains("\"location_source\":\"GPS\""))
        assertFalse(json.contains("FUSED"))
    }

    @Test
    fun nativeLocationEvaluator_validatesAccuracyTimestampAndProviderPriority() {
        val now = 1_700_000_000_000L

        assertFalse(NativeLocationEvaluator.isValidCoordinate(0.0, 0.0))
        assertFalse(NativeLocationEvaluator.isValidCoordinate(Double.NaN, 77.2))
        assertFalse(NativeLocationEvaluator.isValidCoordinate(95.0, 77.2))
        assertTrue(NativeLocationEvaluator.isValidCoordinate(28.6139, 77.2090))

        val networkFix = LocationSample(
            latitude = 28.6139,
            longitude = 77.2090,
            provider = "network",
            accuracyMeters = 40f,
            timestampMs = now - 5_000L
        )
        assertTrue(NativeLocationEvaluator.shouldAcceptLocationUpdate(null, networkFix, nowMs = now))

        // Reject overly stale fix (> 2 min)
        val staleGpsFix = LocationSample(
            latitude = 28.6200,
            longitude = 77.2100,
            provider = "gps",
            accuracyMeters = 5f,
            timestampMs = now - 200_000L
        )
        assertFalse(NativeLocationEvaluator.shouldAcceptLocationUpdate(networkFix, staleGpsFix, nowMs = now))

        // Reject wildly inaccurate fix (> 500m)
        val inaccurateFix = LocationSample(
            latitude = 28.6200,
            longitude = 77.2100,
            provider = "network",
            accuracyMeters = 900f,
            timestampMs = now - 1_000L
        )
        assertFalse(NativeLocationEvaluator.shouldAcceptLocationUpdate(networkFix, inaccurateFix, nowMs = now))

        // Prefer accurate recent GPS fix over network fix
        val gpsFix = LocationSample(
            latitude = 28.6145,
            longitude = 77.2095,
            provider = "gps",
            accuracyMeters = 6f,
            timestampMs = now - 1_000L
        )
        assertTrue(NativeLocationEvaluator.shouldAcceptLocationUpdate(networkFix, gpsFix, nowMs = now))

        val bestLast = NativeLocationEvaluator.selectBestLastKnown(listOf(staleGpsFix, networkFix, gpsFix), nowMs = now)
        assertNotNull(bestLast)
        assertEquals("gps", bestLast!!.provider)
        assertEquals("GPS", NativeLocationEvaluator.normalizeLocationSource(bestLast.provider))
        assertEquals("NETWORK", NativeLocationEvaluator.normalizeLocationSource("network"))
    }

    @Test
    fun driverFcmDeepLinkResolver_mapsActionUrlsAndEventsCorrectly() {
        assertEquals(
            "incoming_offer",
            DriverFcmDeepLinkResolver.resolveRoute(mapOf("event_type" to "DISPATCH_OFFER", "ride_id" to "r-1"))
        )
        assertEquals(
            "active_ride_or_offer",
            DriverFcmDeepLinkResolver.resolveRoute(mapOf("action_url" to "/rides/r-42"))
        )
        assertEquals(
            "emergency_sos",
            DriverFcmDeepLinkResolver.resolveRoute(mapOf("action_url" to "/safety/incidents"))
        )
        assertEquals(
            "helpdesk",
            DriverFcmDeepLinkResolver.resolveRoute(mapOf("action_url" to "/support/t-99"))
        )
        assertEquals(
            "notifications",
            DriverFcmDeepLinkResolver.resolveRoute(mapOf("event_type" to "ADMIN_BROADCAST"))
        )
    }
}
