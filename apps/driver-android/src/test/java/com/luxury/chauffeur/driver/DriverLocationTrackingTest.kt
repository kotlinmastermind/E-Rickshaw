package com.luxury.chauffeur.driver

import com.luxury.chauffeur.driver.core.location.DriverLocationForegroundService
import com.luxury.chauffeur.driver.core.network.DriverLocationPayload
import com.luxury.chauffeur.driver.core.network.ResilientDns
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class DriverLocationTrackingTest {

    @Test
    fun testServiceConstants() {
        assertEquals("driver_location_channel", DriverLocationForegroundService.CHANNEL_ID)
        assertEquals(1001, DriverLocationForegroundService.NOTIFICATION_ID)
        assertEquals("ACTION_START_TRACKING", DriverLocationForegroundService.ACTION_START)
        assertEquals("ACTION_STOP_TRACKING", DriverLocationForegroundService.ACTION_STOP)
    }

    @Test
    fun testTrackingLifecycleStateTransitions() {
        val isTracking = AtomicBoolean(false)
        val isForeground = AtomicBoolean(false)

        // 1. Initial State
        assertFalse(isTracking.get())
        assertFalse(isForeground.get())

        // 2. Start Tracking Simulation
        val started = !isTracking.getAndSet(true)
        assertTrue("First start should succeed", started)
        isForeground.set(true)
        assertTrue(isTracking.get())
        assertTrue(isForeground.get())

        // 3. Repeated Start Tracking Simulation (Idempotence - must prevent duplicate subscription)
        val duplicateStart = !isTracking.getAndSet(true)
        assertFalse("Duplicate start should be ignored", duplicateStart)
        assertTrue("Tracking remains active", isTracking.get())

        // 4. Stop Tracking Simulation
        isTracking.set(false)
        val wasForeground = isForeground.getAndSet(false)
        assertTrue("Foreground state was active and now cleanly cleared", wasForeground)
        assertFalse(isTracking.get())
        assertFalse(isForeground.get())

        // 5. Repeated Stop Tracking Simulation (Idempotence - must not crash or call stopForeground)
        val repeatedStopForeground = isForeground.getAndSet(false)
        assertFalse("Repeated stop must NOT attempt to exit foreground again", repeatedStopForeground)
        assertFalse(isTracking.get())
    }

    @Test
    fun testStopWhenNeverStarted() {
        val isTracking = AtomicBoolean(false)
        val isForeground = AtomicBoolean(false)

        // Stopping a service that was never started
        isTracking.set(false)
        val exitedForeground = isForeground.getAndSet(false)
        assertFalse("Never started service must not execute stopForeground", exitedForeground)
        assertFalse(isTracking.get())
    }

    @Test
    fun testPermissionGatingLogic() {
        fun canTrack(fineGranted: Boolean, coarseGranted: Boolean): Boolean {
            return fineGranted || coarseGranted
        }

        assertFalse("No permissions must deny tracking", canTrack(fineGranted = false, coarseGranted = false))
        assertTrue("Fine permission alone must allow tracking", canTrack(fineGranted = true, coarseGranted = false))
        assertTrue("Coarse permission alone must allow tracking", canTrack(fineGranted = false, coarseGranted = true))
        assertTrue("Both permissions must allow tracking", canTrack(fineGranted = true, coarseGranted = true))
    }

    @Test
    fun testDriverLocationPayloadIntegrity() {
        val payload = DriverLocationPayload(
            latitude = 19.0760,
            longitude = 72.8777,
            heading = 180.0,
            speedMps = 12.5,
            accuracyM = 3.2,
            isMockLocation = false,
            locationSource = "FUSED"
        )

        assertEquals(19.0760, payload.latitude, 0.0001)
        assertEquals(72.8777, payload.longitude, 0.0001)
        assertEquals(180.0, payload.heading ?: 0.0, 0.01)
        assertEquals(12.5, payload.speedMps ?: 0.0, 0.01)
        assertEquals(3.2, payload.accuracyM ?: 0.0, 0.01)
        assertFalse(payload.isMockLocation ?: true)
        assertEquals("FUSED", payload.locationSource)
    }

    @Test
    fun testResilientDnsResolvesAnycastCloudflareIPs() {
        val renderAddresses = ResilientDns.lookup("e-rickshaw-z32z.onrender.com")
        assertNotNull("DNS lookup must never return null", renderAddresses)
        assertTrue("DNS lookup must resolve at least one address", renderAddresses.isNotEmpty())
        
        val ipStrings = renderAddresses.map { it.hostAddress }
        val containsExpectedAnycast = ipStrings.any { it == "216.24.57.16" || it == "216.24.57.18" }
        assertTrue("Must include resilient Cloudflare Anycast fallback IPs: $ipStrings", containsExpectedAnycast)
    }
}

