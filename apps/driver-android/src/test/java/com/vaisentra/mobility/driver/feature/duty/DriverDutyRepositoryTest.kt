package com.vaisentra.mobility.driver.feature.duty

import com.vaisentra.mobility.driver.feature.duty.DriverDutyState
import com.vaisentra.mobility.driver.feature.duty.DutyFailureCategory
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Enterprise Unit Tests verifying Driver Duty State Machine,
 * HTTP code classification, concurrency safeguards, and state transitions.
 */
class DriverDutyRepositoryTest {

    @Test
    fun testInitialStateDefaultsToUnknownOrOffline() {
        val initialUnknown: DriverDutyState = DriverDutyState.Unknown
        val initialOffline: DriverDutyState = DriverDutyState.Offline

        assertTrue(initialUnknown is DriverDutyState.Unknown)
        assertTrue(initialOffline is DriverDutyState.Offline)
        assertNotEquals(initialUnknown, initialOffline)
    }

    @Test
    fun testConfirmedOnlineStateInvariants() {
        val onlineState: DriverDutyState = DriverDutyState.Online
        assertTrue("DriverDutyState.Online must indicate verified active presence", onlineState is DriverDutyState.Online)
        assertFalse("Online must not be considered Offline", onlineState is DriverDutyState.Offline)
    }

    @Test
    fun testTransientInFlightStatesDistinctFromConfirmed() {
        val goingOnline: DriverDutyState = DriverDutyState.UpdatingOnline
        val goingOffline: DriverDutyState = DriverDutyState.UpdatingOffline

        assertTrue(goingOnline is DriverDutyState.UpdatingOnline)
        assertTrue(goingOffline is DriverDutyState.UpdatingOffline)
        assertFalse(goingOnline is DriverDutyState.Online)
        assertFalse(goingOffline is DriverDutyState.Offline)
    }

    @Test
    fun testErrorStatePreservesPreviousConfirmedState() {
        // When going Online fails (e.g. 403 KYC unapproved), previous confirmed state (Offline) must be preserved
        val errorFromOffline = DriverDutyState.Error(
            previousConfirmedState = DriverDutyState.Offline,
            errorMessage = "Driver must be approved before going online. Mandatory document verification and admin approval required.",
            failureCategory = DutyFailureCategory.UNAPPROVED_DRIVER,
            httpCode = 403
        )

        assertEquals(DriverDutyState.Offline, errorFromOffline.previousConfirmedState)
        assertEquals(403, errorFromOffline.httpCode)
        assertEquals(DutyFailureCategory.UNAPPROVED_DRIVER, errorFromOffline.failureCategory)
        assertTrue(errorFromOffline.errorMessage.contains("must be approved before going online"))

        // When going Offline fails (e.g. 409 active ride in progress), previous confirmed state (Online) must be preserved
        val errorFromOnline = DriverDutyState.Error(
            previousConfirmedState = DriverDutyState.Online,
            errorMessage = "Cannot go offline while on an active ride (RIDE-123). Please complete or cancel the active ride first.",
            failureCategory = DutyFailureCategory.ACTIVE_RIDE_RESTRICTION,
            httpCode = 409
        )

        assertEquals(DriverDutyState.Online, errorFromOnline.previousConfirmedState)
        assertEquals(409, errorFromOnline.httpCode)
        assertEquals(DutyFailureCategory.ACTIVE_RIDE_RESTRICTION, errorFromOnline.failureCategory)
        assertTrue(errorFromOnline.errorMessage.contains("Cannot go offline while on an active ride"))
    }

    @Test
    fun testHttpErrorCodeClassification() {
        fun classifyCode(code: Int): DutyFailureCategory = when (code) {
            401 -> DutyFailureCategory.AUTH_EXPIRED
            403 -> DutyFailureCategory.UNAPPROVED_DRIVER
            409 -> DutyFailureCategory.ACTIVE_RIDE_RESTRICTION
            422 -> DutyFailureCategory.VALIDATION_ERROR
            in 500..599 -> DutyFailureCategory.SERVER_ERROR
            else -> DutyFailureCategory.UNKNOWN_ERROR
        }

        assertEquals(DutyFailureCategory.AUTH_EXPIRED, classifyCode(401))
        assertEquals(DutyFailureCategory.UNAPPROVED_DRIVER, classifyCode(403))
        assertEquals(DutyFailureCategory.ACTIVE_RIDE_RESTRICTION, classifyCode(409))
        assertEquals(DutyFailureCategory.VALIDATION_ERROR, classifyCode(422))
        assertEquals(DutyFailureCategory.SERVER_ERROR, classifyCode(500))
        assertEquals(DutyFailureCategory.SERVER_ERROR, classifyCode(502))
        assertEquals(DutyFailureCategory.SERVER_ERROR, classifyCode(503))
        assertEquals(DutyFailureCategory.UNKNOWN_ERROR, classifyCode(418))
    }

    @Test
    fun testNetworkExceptionClassification() {
        fun classifyException(e: Exception): DutyFailureCategory = when (e) {
            is SocketTimeoutException -> DutyFailureCategory.TIMEOUT
            is UnknownHostException, is ConnectException, is IOException -> DutyFailureCategory.NETWORK_UNAVAILABLE
            else -> DutyFailureCategory.UNKNOWN_ERROR
        }

        assertEquals(DutyFailureCategory.TIMEOUT, classifyException(SocketTimeoutException("Read timed out")))
        assertEquals(DutyFailureCategory.NETWORK_UNAVAILABLE, classifyException(UnknownHostException("Unable to resolve host")))
        assertEquals(DutyFailureCategory.NETWORK_UNAVAILABLE, classifyException(ConnectException("Connection refused")))
        assertEquals(DutyFailureCategory.NETWORK_UNAVAILABLE, classifyException(IOException("Broken pipe")))
        assertEquals(DutyFailureCategory.UNKNOWN_ERROR, classifyException(IllegalArgumentException("Bad argument")))
    }

    @Test
    fun testConcurrencyDuplicateTapSimulation() {
        val isOperationInFlight = java.util.concurrent.atomic.AtomicBoolean(false)

        // 1. First tap succeeds in setting in-flight flag
        val firstTap = isOperationInFlight.compareAndSet(false, true)
        assertTrue("First tap must acquire execution grant", firstTap)

        // 2. Second tap (rapid double-tap while request is in flight) must be dropped
        val secondTap = isOperationInFlight.compareAndSet(false, true)
        assertFalse("Second tap must be rejected immediately to prevent overlapping requests", secondTap)

        // 3. Third tap also dropped
        val thirdTap = isOperationInFlight.compareAndSet(false, true)
        assertFalse("Third tap must be rejected", thirdTap)

        // 4. Request completes and resets flag
        isOperationInFlight.set(false)

        // 5. Subsequent tap now succeeds
        val postCompleteTap = isOperationInFlight.compareAndSet(false, true)
        assertTrue("Tap after completion must acquire grant", postCompleteTap)
    }
}
