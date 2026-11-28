package com.vaisentra.mobility.rider.data

/**
 * Rejects callbacks from a superseded WebSocket or a different ride.
 * Socket identity is checked as well as ride identity because reconnects can
 * reuse the same ride ID while the previous socket is still closing.
 */
internal fun isCurrentRideTrackingCallback(
    activeSocket: Any?,
    callbackSocket: Any,
    activeRideId: String?,
    callbackRideId: String
): Boolean = activeSocket === callbackSocket && activeRideId == callbackRideId
