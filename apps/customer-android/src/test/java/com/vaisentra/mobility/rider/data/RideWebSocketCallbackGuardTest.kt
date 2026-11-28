package com.vaisentra.mobility.rider.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideWebSocketCallbackGuardTest {
    @Test
    fun acceptsOnlyTheCurrentSocketForTheCurrentRide() {
        val currentSocket = Any()
        assertTrue(isCurrentRideTrackingCallback(currentSocket, currentSocket, "ride-2", "ride-2"))
    }

    @Test
    fun rejectsCallbackFromPreviousSocketEvenWhenRideIdMatches() {
        assertFalse(isCurrentRideTrackingCallback(Any(), Any(), "ride-2", "ride-2"))
    }

    @Test
    fun rejectsCallbackForDifferentRide() {
        val currentSocket = Any()
        assertFalse(isCurrentRideTrackingCallback(currentSocket, currentSocket, "ride-2", "ride-1"))
    }

    @Test
    fun rejectsCallbackAfterDisconnect() {
        assertFalse(isCurrentRideTrackingCallback(null, Any(), null, "ride-2"))
    }
}
