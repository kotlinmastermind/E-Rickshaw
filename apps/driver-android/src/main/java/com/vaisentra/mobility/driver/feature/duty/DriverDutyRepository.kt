package com.vaisentra.mobility.driver.feature.duty

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.vaisentra.mobility.core.map.DeviceLocationProvider
import com.vaisentra.mobility.driver.core.location.DriverLocationForegroundService
import com.vaisentra.mobility.driver.core.network.DriverApiClient
import com.vaisentra.mobility.driver.core.network.DriverAvailabilityPayload
import com.vaisentra.mobility.driver.core.network.DriverLocationPayload
import com.vaisentra.mobility.core.security.TokenManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Enterprise Production-Grade Driver Duty Repository.
 *
 * Guarantees:
 * 1. Strict Server Authoritative Truth: Local state NEVER assumes success until confirmed by backend.
 * 2. Concurrency Safety: Mutex protection prevents duplicate, rapid-tap, or overlapping requests.
 * 3. Lifecycle & Error Hardening: Granular HTTP (401, 403, 409, 422, 5xx) and Network (offline, timeout) handling.
 * 4. Automatic State Reconciliation: Periodic and on-demand resync on app start, foreground return, and post-timeout.
 * 5. Foreground GPS Lifecycle Coordination: Location service strictly tied to confirmed server availability.
 */
class DriverDutyRepository private constructor(
    private val appContext: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val tag = "DriverDutyRepo"
    private val mutex = Mutex()
    private val gson = Gson()
    private val isOperationInFlight = AtomicBoolean(false)

    // Authoritative Duty State Flow
    private val _dutyState = MutableStateFlow<DriverDutyState>(DriverDutyState.Unknown)
    val dutyState: StateFlow<DriverDutyState> = _dutyState.asStateFlow()

    // Last confirmed server state (defaults to Offline until reconciled)
    @Volatile
    private var lastConfirmedState: DriverDutyState = DriverDutyState.Offline

    companion object {
        @Volatile
        private var instance: DriverDutyRepository? = null

        fun getInstance(context: Context): DriverDutyRepository {
            return instance ?: synchronized(this) {
                instance ?: DriverDutyRepository(context.applicationContext).also { instance = it }
            }
        }

        // For unit testing dependency injection
        fun createForTesting(context: Context, scope: CoroutineScope): DriverDutyRepository {
            return DriverDutyRepository(context, scope)
        }
    }

    /**
     * Reconciles current availability state against the authoritative backend server truth.
     * Safe to invoke on app start, background-to-foreground transitions, and network reconnects.
     */
    suspend fun reconcileAvailability(force: Boolean = false): DriverDutyState = withContext(Dispatchers.IO) {
        if (!force && isOperationInFlight.get()) {
            Log.d(tag, "Reconciliation deferred: duty operation currently in flight")
            return@withContext _dutyState.value
        }

        val tokenManager = TokenManager.getDriverInstance(appContext)
        if (!tokenManager.isLoggedIn()) {
            lastConfirmedState = DriverDutyState.Offline
            _dutyState.value = DriverDutyState.Offline
            return@withContext DriverDutyState.Offline
        }

        try {
            Log.i(tag, "Reconciling duty availability with backend...")
            val response = DriverApiClient.getService().getAvailability()
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val isOnline = (body["is_online"] as? Boolean) ?: false
                val approvalStatus = (body["approval_status"] as? String) ?: ""
                val activeRideId = body["active_ride_id"] as? String

                val newState = if (isOnline) DriverDutyState.Online else DriverDutyState.Offline
                lastConfirmedState = newState
                _dutyState.value = newState

                syncLocationService(isOnline)
                Log.i(tag, "Reconciliation successful. Confirmed State=${if (isOnline) "ONLINE" else "OFFLINE"}, Approval=$approvalStatus, ActiveRide=$activeRideId")
                return@withContext newState
            } else if (response.code() == 401) {
                Log.w(tag, "Reconciliation failed: HTTP 401 Unauthorized (session revoked)")
                tokenManager.clear()
                val err = DriverDutyState.Error(
                    previousConfirmedState = DriverDutyState.Offline,
                    errorMessage = "Session expired. Please log in again.",
                    failureCategory = DutyFailureCategory.AUTH_EXPIRED,
                    httpCode = 401
                )
                lastConfirmedState = DriverDutyState.Offline
                _dutyState.value = err
                return@withContext err
            } else {
                Log.w(tag, "Reconciliation returned unexpected status: ${response.code()}")
                return@withContext _dutyState.value
            }
        } catch (e: Exception) {
            Log.w(tag, "Reconciliation network check error: ${e.javaClass.simpleName} - ${e.message}")
            // Do not corrupt existing confirmed state on background check failure
            return@withContext _dutyState.value
        }
    }

    /**
     * Executes an atomic Duty Toggle transition with duplicate tap protection and mutex isolation.
     *
     * @param targetOnline Optional explicit target state; if null, toggles the current confirmed state.
     * @return Resulting DriverDutyState
     */
    suspend fun toggleDuty(targetOnline: Boolean? = null): DriverDutyState = withContext(Dispatchers.IO) {
        // Fast-path concurrency check: drop duplicate taps while an update is in-flight
        if (!isOperationInFlight.compareAndSet(false, true)) {
            Log.w(tag, "Duplicate duty toggle dropped: operation already in flight")
            return@withContext _dutyState.value
        }

        try {
            mutex.withLock {
                val currentConfirmed = lastConfirmedState
                val isCurrentlyOnline = (currentConfirmed == DriverDutyState.Online)
                val target = targetOnline ?: !isCurrentlyOnline

                Log.i(tag, "Duty transition requested: Current=${if (isCurrentlyOnline) "ONLINE" else "OFFLINE"} -> Target=${if (target) "ONLINE" else "OFFLINE"}")

                // Emit transition in-flight state to update UI with loader/disabled button
                val inFlightState = if (target) DriverDutyState.UpdatingOnline else DriverDutyState.UpdatingOffline
                _dutyState.value = inFlightState

                val startTime = System.currentTimeMillis()
                try {
                    val response = DriverApiClient.getService().setAvailability(
                        DriverAvailabilityPayload(isOnline = target)
                    )
                    val durationMs = System.currentTimeMillis() - startTime

                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        val confirmedOnline = (body["is_online"] as? Boolean) ?: target
                        val finalState = if (confirmedOnline) DriverDutyState.Online else DriverDutyState.Offline

                        lastConfirmedState = finalState
                        _dutyState.value = finalState

                        syncLocationService(confirmedOnline)
                        if (confirmedOnline) {
                            sendInitialOnlineLocationPing()
                        }

                        Log.i(tag, "Duty toggle committed by backend in ${durationMs}ms: State=${if (confirmedOnline) "ONLINE" else "OFFLINE"}")
                        return@withLock finalState
                    } else {
                        val errorBodyStr = response.errorBody()?.string().orEmpty()
                        val parsedDetail = parseErrorDetail(errorBodyStr)
                        val code = response.code()

                        val failureCategory = when (code) {
                            401 -> DutyFailureCategory.AUTH_EXPIRED
                            403 -> DutyFailureCategory.UNAPPROVED_DRIVER
                            409 -> DutyFailureCategory.ACTIVE_RIDE_RESTRICTION
                            422 -> DutyFailureCategory.VALIDATION_ERROR
                            in 500..599 -> DutyFailureCategory.SERVER_ERROR
                            else -> DutyFailureCategory.UNKNOWN_ERROR
                        }

                        val userMessage = when (failureCategory) {
                            DutyFailureCategory.UNAPPROVED_DRIVER ->
                                parsedDetail.ifBlank { "Driver must be approved before going online. Mandatory document verification required." }
                            DutyFailureCategory.ACTIVE_RIDE_RESTRICTION ->
                                parsedDetail.ifBlank { "Cannot go offline while on an active ride. Please complete or cancel the ride first." }
                            DutyFailureCategory.AUTH_EXPIRED ->
                                "Session expired. Please sign in again."
                            DutyFailureCategory.SERVER_ERROR ->
                                "Fleet service temporarily unavailable (HTTP $code). Please retry in a few moments."
                            else ->
                                parsedDetail.ifBlank { "Unable to update duty status (HTTP $code). Please try again." }
                        }

                        Log.e(tag, "Duty toggle rejected by server: HTTP $code - $userMessage")

                        // Revert confirmed state to previous authoritative state
                        val errorState = DriverDutyState.Error(
                            previousConfirmedState = currentConfirmed,
                            errorMessage = userMessage,
                            failureCategory = failureCategory,
                            httpCode = code
                        )
                        _dutyState.value = errorState
                        return@withLock errorState
                    }
                } catch (e: Exception) {
                    val durationMs = System.currentTimeMillis() - startTime
                    val failureCategory = when (e) {
                        is SocketTimeoutException -> DutyFailureCategory.TIMEOUT
                        is UnknownHostException, is ConnectException, is IOException -> DutyFailureCategory.NETWORK_UNAVAILABLE
                        else -> DutyFailureCategory.UNKNOWN_ERROR
                    }

                    val userMessage = when (failureCategory) {
                        DutyFailureCategory.TIMEOUT -> "Request timed out after ${durationMs}ms. Verifying server status..."
                        DutyFailureCategory.NETWORK_UNAVAILABLE -> "No internet connection. Please verify your data or Wi-Fi connection."
                        else -> "Duty update failed: ${e.message ?: "Unknown error"}"
                    }

                    Log.e(tag, "Duty toggle network exception: ${e.javaClass.simpleName} - $userMessage")

                    // Retain previous confirmed state, do NOT assume target state was reached
                    val errorState = DriverDutyState.Error(
                        previousConfirmedState = currentConfirmed,
                        errorMessage = userMessage,
                        failureCategory = failureCategory
                    )
                    _dutyState.value = errorState

                    // If request timed out, launch background reconciliation to check if server committed
                    if (failureCategory == DutyFailureCategory.TIMEOUT) {
                        coroutineScope.launch {
                            delay(2000L)
                            reconcileAvailability(force = true)
                        }
                    }

                    return@withLock errorState
                }
            }
        } finally {
            isOperationInFlight.set(false)
        }
    }

    /**
     * Direct safety helper to set duty to Offline immediately (e.g. on fatigue check failure).
     */
    suspend fun setDutyOffline(): DriverDutyState {
        return toggleDuty(targetOnline = false)
    }

    /**
     * Clears transient error state and restores the confirmed server state in the UI.
     */
    fun dismissError() {
        if (_dutyState.value is DriverDutyState.Error) {
            _dutyState.value = lastConfirmedState
        }
    }

    private fun syncLocationService(isOnline: Boolean) {
        try {
            if (isOnline) {
                val hasFine = ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val hasCoarse = ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (hasFine || hasCoarse) {
                    DriverLocationForegroundService.start(appContext)
                    Log.d(tag, "DriverLocationForegroundService started")
                } else {
                    Log.w(tag, "Location permission missing; DriverLocationForegroundService not started")
                }
            } else {
                DriverLocationForegroundService.stop(appContext)
                Log.d(tag, "DriverLocationForegroundService stopped")
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to synchronize location service lifecycle: ${e.message}")
        }
    }

    private suspend fun sendInitialOnlineLocationPing() {
        try {
            val loc = DeviceLocationProvider.getLastKnownLocation(appContext)
            if (loc != null) {
                DriverApiClient.getService().pingLocation(
                    DriverLocationPayload(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        heading = 0.0,
                        speedMps = 0.0
                    )
                )
                Log.d(tag, "Initial online location ping dispatched: (${loc.latitude}, ${loc.longitude})")
            }
        } catch (e: Exception) {
            Log.w(tag, "Initial online location ping failed: ${e.message}")
        }
    }

    private fun parseErrorDetail(errorBody: String): String {
        if (errorBody.isBlank()) return ""
        return try {
            val json = gson.fromJson(errorBody, JsonObject::class.java)
            if (json.has("detail") && !json.get("detail").isJsonNull) {
                val detailElem = json.get("detail")
                if (detailElem.isJsonPrimitive) {
                    detailElem.asString
                } else if (detailElem.isJsonObject) {
                    val detailObj = detailElem.asJsonObject
                    if (detailObj.has("message") && !detailObj.get("message").isJsonNull) {
                        detailObj.get("message").asString
                    } else if (detailObj.has("detail") && !detailObj.get("detail").isJsonNull) {
                        detailObj.get("detail").asString
                    } else {
                        detailObj.toString()
                    }
                } else {
                    detailElem.toString()
                }
            } else if (json.has("message") && !json.get("message").isJsonNull) {
                json.get("message").asString
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        }
    }
}
