package com.vaisentra.mobility.driver.feature.duty

/**
 * Authoritative Duty State Machine governing driver online/offline availability.
 * Follows strict state transitions:
 * UNKNOWN -> OFFLINE or ONLINE (via server reconciliation)
 * OFFLINE -> UPDATING_ONLINE -> ONLINE (on success) or ERROR/OFFLINE (on failure)
 * ONLINE -> UPDATING_OFFLINE -> OFFLINE (on success) or ERROR/ONLINE (on failure)
 */
sealed interface DriverDutyState {
    /**
     * Initial startup or background reconciliation state while server truth is fetched.
     */
    object Unknown : DriverDutyState

    /**
     * Confirmed server state: Driver is Offline and not eligible for dispatch.
     */
    object Offline : DriverDutyState

    /**
     * Transition in flight: Driver requested to go Online; awaiting authoritative server commit.
     */
    object UpdatingOnline : DriverDutyState

    /**
     * Confirmed server state: Driver is Online, indexed in dispatch, and actively available.
     */
    object Online : DriverDutyState

    /**
     * Transition in flight: Driver requested to go Offline; awaiting authoritative server commit.
     */
    object UpdatingOffline : DriverDutyState

    /**
     * Transition failure: Error occurred. Contains previous confirmed state to restore UI truth.
     */
    data class Error(
        val previousConfirmedState: DriverDutyState,
        val errorMessage: String,
        val failureCategory: DutyFailureCategory,
        val httpCode: Int? = null,
        val timestampMs: Long = System.currentTimeMillis()
    ) : DriverDutyState
}

/**
 * Categorized failure reasons for granular UI display and remediation.
 */
enum class DutyFailureCategory {
    UNAPPROVED_DRIVER,       // HTTP 403: Documents/KYC pending or rejected
    ACTIVE_RIDE_RESTRICTION, // HTTP 409: In-progress/assigned ride prevents going offline
    AUTH_EXPIRED,            // HTTP 401: Token expired or session revoked
    NETWORK_UNAVAILABLE,     // Offline, DNS resolution error, no route to host
    TIMEOUT,                 // SocketTimeoutException, gateway timeout
    SERVER_ERROR,            // HTTP 5xx: Backend internal error
    VALIDATION_ERROR,        // HTTP 422: Malformed payload or precondition failed
    CONCURRENCY_BLOCKED,     // Request dropped due to in-flight operation (double-tap)
    UNKNOWN_ERROR
}

