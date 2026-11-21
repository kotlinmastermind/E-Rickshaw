package com.vaisentra.mobility.driver.data

import android.util.Log
import com.vaisentra.mobility.driver.components.DriverDialogController
import com.vaisentra.mobility.driver.components.DriverPopupType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * DriverGo Central Repository bridging UI Screens and Popups with FastAPI + Supabase Backend
 */
object DriverRepository {

    private const val TAG = "DriverRepository"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _currentRideId = MutableStateFlow("")
    val currentRideId: StateFlow<String> = _currentRideId.asStateFlow()

    private val _activeRide = MutableStateFlow<ActiveRideDto?>(null)
    val activeRide: StateFlow<ActiveRideDto?> = _activeRide.asStateFlow()

    private val _unreadNotificationCount = MutableStateFlow(0)
    val unreadNotificationCount: StateFlow<Int> = _unreadNotificationCount.asStateFlow()

    private val _profile = MutableStateFlow(DriverProfileDto())
    val profile: StateFlow<DriverProfileDto> = _profile.asStateFlow()

    private val _profilePhotoUri = MutableStateFlow<String?>(null)
    val profilePhotoUri: StateFlow<String?> = _profilePhotoUri.asStateFlow()

    fun initProfilePhoto() {
        _profilePhotoUri.value = DriverNetworkClient.getProfilePhotoUri()
    }

    fun updateProfilePhoto(uri: String?) {
        _profilePhotoUri.value = uri
        if (uri != null) {
            DriverNetworkClient.saveProfilePhotoUri(uri)
        } else {
            DriverNetworkClient.clearProfilePhotoUri()
        }
    }

    private val _earnings = MutableStateFlow(DriverEarningsSummaryDto())
    val earnings: StateFlow<DriverEarningsSummaryDto> = _earnings.asStateFlow()

    private val _scorecard = MutableStateFlow(ScorecardDto())
    val scorecard: StateFlow<ScorecardDto> = _scorecard.asStateFlow()

    private val _dailyTarget = MutableStateFlow(DailyIncentiveDto())
    val dailyTarget: StateFlow<DailyIncentiveDto> = _dailyTarget.asStateFlow()

    private val _documents = MutableStateFlow<List<DriverDocumentDto>>(emptyList())
    val documents: StateFlow<List<DriverDocumentDto>> = _documents.asStateFlow()

    private val _ledger = MutableStateFlow<List<LedgerItemDto>>(emptyList())
    val ledger: StateFlow<List<LedgerItemDto>> = _ledger.asStateFlow()

    private val _activeOffer = MutableStateFlow<DispatchOfferDto?>(null)
    val activeOffer: StateFlow<DispatchOfferDto?> = _activeOffer.asStateFlow()

    private val _emergencyContacts = MutableStateFlow<List<EmergencyContactDto>>(emptyList())
    val emergencyContacts: StateFlow<List<EmergencyContactDto>> = _emergencyContacts.asStateFlow()

    private val _sharedPoolTrip = MutableStateFlow<SharedPoolTripDto?>(null)
    val sharedPoolTrip: StateFlow<SharedPoolTripDto?> = _sharedPoolTrip.asStateFlow()

    private val _rideStops = MutableStateFlow<List<RideStopDto>>(emptyList())
    val rideStops: StateFlow<List<RideStopDto>> = _rideStops.asStateFlow()

    private val _trainingModules = MutableStateFlow<List<TrainingModuleDto>>(emptyList())
    val trainingModules: StateFlow<List<TrainingModuleDto>> = _trainingModules.asStateFlow()

    private val _driverTickets = MutableStateFlow<List<DriverTicketDto>>(emptyList())
    val driverTickets: StateFlow<List<DriverTicketDto>> = _driverTickets.asStateFlow()

    private val _notifications = MutableStateFlow<List<NotificationItemDto>>(emptyList())
    val notifications: StateFlow<List<NotificationItemDto>> = _notifications.asStateFlow()

    private val _heatMapClusters = MutableStateFlow<List<HeatMapClusterDto>>(emptyList())
    val heatMapClusters: StateFlow<List<HeatMapClusterDto>> = _heatMapClusters.asStateFlow()

    private val _roadRestrictions = MutableStateFlow<List<RoadRestrictionDto>>(emptyList())
    val roadRestrictions: StateFlow<List<RoadRestrictionDto>> = _roadRestrictions.asStateFlow()

    private val _payoutAccounts = MutableStateFlow<List<PayoutAccountDto>>(emptyList())
    val payoutAccounts: StateFlow<List<PayoutAccountDto>> = _payoutAccounts.asStateFlow()

    private val _weeklyGoal = MutableStateFlow(WeeklyGoalDto())
    val weeklyGoal: StateFlow<WeeklyGoalDto> = _weeklyGoal.asStateFlow()

    private val _incentiveMilestones = MutableStateFlow<List<IncentiveMilestoneDto>>(emptyList())
    val incentiveMilestones: StateFlow<List<IncentiveMilestoneDto>> = _incentiveMilestones.asStateFlow()

    private val _rideBreakdown = MutableStateFlow<RideBreakdownDto?>(null)
    val rideBreakdown: StateFlow<RideBreakdownDto?> = _rideBreakdown.asStateFlow()

    @Volatile
    var pendingAuthPhone: String = ""

    suspend fun requestOtp(phone: String): Result<Boolean> {
        return try {
            val digits = phone.filter { it.isDigit() }
            val normalizedPhone = if (phone.startsWith("+")) phone else if (digits.length == 10) "+91$digits" else "+$digits"
            val res = DriverNetworkClient.apiService.sendOtp(
                OtpSendRequest(phoneNumber = normalizedPhone, role = "driver")
            )
            if (res.isSuccessful) {
                Result.success(true)
            } else {
                val errMsg = res.errorBody()?.string() ?: "Failed to send OTP (HTTP ${res.code()})"
                Log.w(TAG, "requestOtp failed: $errMsg")
                Result.failure(IllegalStateException(errMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "requestOtp exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun verifyOtp(phone: String, otp: String): Result<DriverTokenResponse> {
        return try {
            val digits = phone.filter { it.isDigit() }
            val normalizedPhone = if (phone.startsWith("+")) phone else if (digits.length == 10) "+91$digits" else "+$digits"
            val res = DriverNetworkClient.apiService.verifyOtp(
                OtpVerifyRequest(
                    phoneNumber = normalizedPhone,
                    otpCode = otp.trim(),
                    role = "driver"
                )
            )
            if (!res.isSuccessful) {
                val errMsg = res.errorBody()?.string() ?: "OTP verification failed (HTTP ${res.code()})"
                Log.w(TAG, "verifyOtp failed: $errMsg")
                return Result.failure(IllegalStateException(errMsg))
            }
            val body = res.body() ?: return Result.failure(IllegalStateException("Empty verification response from server"))
            val token = body.accessToken.trim()
            if (token.isBlank()) {
                return Result.failure(IllegalStateException("Backend returned an empty access token"))
            }

            DriverNetworkClient.setAuthToken(token, body.refreshToken)
            scope.launch {
                fetchDriverProfile()
                fetchEarningsSummary()
                fetchDriverDocuments()
                fetchPerformanceScorecard()
                fetchDailyTargets()
                fetchActiveRide()
                fetchUnreadNotificationsCount()
            }
            Result.success(body)
        } catch (e: Exception) {
            Log.e(TAG, "verifyOtp exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun resetPasswordWithOtp(phone: String, otp: String, newPassword: String): Result<Boolean> {
        return try {
            val verifyRes = verifyOtp(phone, otp)
            if (verifyRes.isFailure) {
                return Result.failure(verifyRes.exceptionOrNull() ?: IllegalStateException("OTP verification failed"))
            }
            val changeRes = DriverNetworkClient.apiService.changePassword(
                ChangePasswordRequest(currentPassword = null, newPassword = newPassword)
            )
            if (changeRes.isSuccessful) {
                Result.success(true)
            } else {
                val errMsg = changeRes.errorBody()?.string() ?: "Password update failed (HTTP ${changeRes.code()})"
                Log.w(TAG, "resetPasswordWithOtp changePassword failed: $errMsg")
                Result.failure(IllegalStateException(errMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "resetPasswordWithOtp exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun driverLogin(phone: String, pinOrOtp: String, loginType: String = "PASSWORD"): Result<Boolean> {
        if (loginType.equals("OTP", ignoreCase = true)) {
            val verifyRes = verifyOtp(phone, pinOrOtp)
            return if (verifyRes.isSuccess) {
                Result.success(true)
            } else {
                Result.failure(verifyRes.exceptionOrNull() ?: IllegalStateException("OTP verification failed"))
            }
        }
        return try {
            val res = DriverNetworkClient.apiService.login(
                DriverLoginRequest(
                    identifier = phone.trim(),
                    password = pinOrOtp,
                    loginType = loginType
                )
            )
            val body = res.body()
            val token = body?.accessToken
            if (res.isSuccessful && !token.isNullOrBlank()) {
                DriverNetworkClient.setAuthToken(token, body.refreshToken)
                scope.launch {
                    fetchDriverProfile()
                    fetchEarningsSummary()
                    fetchDriverDocuments()
                    fetchPerformanceScorecard()
                    fetchDailyTargets()
                    fetchActiveRide()
                    fetchUnreadNotificationsCount()
                }
                Result.success(true)
            } else {
                val errMsg = res.errorBody()?.string() ?: "Login failed (HTTP ${res.code()})"
                Log.w(TAG, "driverLogin failed: $errMsg")
                Result.failure(IllegalStateException(errMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "driverLogin exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun logout() {
        _isOnline.value = false
        _currentRideId.value = ""
        _activeRide.value = null
        _activeOffer.value = null
        _unreadNotificationCount.value = 0
        DriverWebSocketClient.disconnectAll()
        DriverNetworkClient.clearAuthToken()
    }

    suspend fun fetchActiveRide(): Result<ActiveRideDto?> {
        return try {
            val res = DriverNetworkClient.apiService.getActiveRide()
            val ride = res.body()?.activeRide
            if (res.isSuccessful) {
                _activeRide.value = ride
                if (ride != null && ride.id.isNotBlank()) {
                    _currentRideId.value = ride.id
                }
                Result.success(ride)
            } else {
                Result.failure(IllegalStateException("getActiveRide failed: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchUnreadNotificationsCount(): Result<Int> {
        return try {
            val res = DriverNetworkClient.apiService.getNotificationsInbox(unreadOnly = true, limit = 1)
            val body = res.body()
            if (res.isSuccessful && body != null) {
                _unreadNotificationCount.value = body.unreadCount
                Result.success(body.unreadCount)
            } else {
                Result.failure(IllegalStateException("getNotificationsInbox failed: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun startShiftOdometer(km: Double, soc: Int = 92, vehicleId: String? = null): Result<Boolean> {
        return try {
            val res = DriverNetworkClient.apiService.startShift(
                StartShiftRequest(vehicleId = vehicleId, startOdometerKm = km)
            )
            if (res.isSuccessful) Result.success(true)
            else Result.failure(IllegalStateException("startShift failed: HTTP ${res.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun endShiftOdometer(km: Double, soc: Int = 85): Result<Boolean> {
        return try {
            val res = DriverNetworkClient.apiService.endShift(EndShiftRequest(endOdometerKm = km))
            if (res.isSuccessful) Result.success(true)
            else Result.failure(IllegalStateException("endShift failed: HTTP ${res.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchDriverDocuments(): Result<List<DriverDocumentDto>> {
        return try {
            val res = DriverNetworkClient.apiService.getDocuments()
            val body = res.body()
            if (res.isSuccessful && body != null) {
                _documents.value = body
                Result.success(body)
            } else {
                Result.failure(IllegalStateException("getDocuments failed: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchDriverProfile(): Result<DriverProfileDto> {
        return try {
            val res = DriverNetworkClient.apiService.getProfile()
            val body = res.body()
            if (res.isSuccessful && body != null) {
                _profile.value = body
                Result.success(body)
            } else {
                Result.failure(IllegalStateException("getProfile failed: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchAccountLedger(): Result<List<LedgerItemDto>> {
        return try {
            val res = DriverNetworkClient.apiService.getAccountLedger()
            val body = res.body()
            if (res.isSuccessful && body != null) {
                _ledger.value = body.ledger
                Result.success(body.ledger)
            } else {
                Result.failure(IllegalStateException("getAccountLedger failed: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchPerformanceScorecard(): Result<ScorecardDto> {
        return try {
            val res = DriverNetworkClient.apiService.getPerformanceScorecard()
            val body = res.body()
            if (res.isSuccessful && body != null) {
                _scorecard.value = body
                Result.success(body)
            } else {
                Result.failure(IllegalStateException("getPerformanceScorecard failed: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchDailyTargets(): Result<DailyIncentiveDto> {
        return try {
            val res = DriverNetworkClient.apiService.getDailyTargets()
            val body = res.body()
            if (res.isSuccessful && body != null) {
                _dailyTarget.value = body
                Result.success(body)
            } else {
                Result.failure(IllegalStateException("getDailyTargets failed: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchActiveOffers(): Result<List<DispatchOfferDto>> {
        return try {
            val res = DriverNetworkClient.apiService.getActiveOffers()
            val body = res.body()
            if (res.isSuccessful && body != null) {
                val firstOffer = body.firstOrNull()
                if (firstOffer != null) {
                    onNewDispatchOfferReceived(firstOffer)
                }
                Result.success(body)
            } else {
                Result.failure(IllegalStateException("getActiveOffers failed: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun setDutyStatus(online: Boolean, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        scope.launch {
            try {
                val res = DriverNetworkClient.apiService.toggleAvailability(DutyStatusUpdateRequest(isOnline = online))
                if (res.isSuccessful) {
                    _isOnline.value = online
                    if (online) {
                        DriverWebSocketClient.connectDispatchChannel()
                        fetchEarningsSummary()
                        fetchActiveOffers()
                        fetchActiveRide()
                    } else {
                        DriverWebSocketClient.disconnectAll()
                    }
                    onResult(true, null)
                } else {
                    val err = res.errorBody()?.string() ?: "HTTP ${res.code()}"
                    Log.w(TAG, "toggleAvailability returned error: $err")
                    onResult(false, err)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update duty status online: ${e.message}")
                onResult(false, e.message)
            }
        }
    }

    suspend fun fetchEarningsSummary(): Result<DriverEarningsSummaryDto> {
        return try {
            val response = DriverNetworkClient.apiService.getEarningsSummary()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                _earnings.value = body
                Result.success(body)
            } else {
                Result.failure(IllegalStateException("getEarningsSummary failed: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchEarningsSummary failed: ${e.message}")
            Result.failure(e)
        }
    }

    fun dismissOffer() {
        _activeOffer.value = null
    }

    fun acceptOffer(rideId: String, onComplete: (Boolean) -> Unit = {}) {
        if (rideId.isBlank()) {
            onComplete(false)
            return
        }
        scope.launch {
            try {
                val res = DriverNetworkClient.apiService.acceptRide(rideId)
                if (res.isSuccessful) {
                    _currentRideId.value = rideId
                    _activeOffer.value = null
                    DriverWebSocketClient.connectRideTracking(rideId)
                    fetchActiveRide()
                    onComplete(true)
                } else {
                    Log.w(TAG, "acceptRide failed with HTTP ${res.code()}: ${res.errorBody()?.string()}")
                    onComplete(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception calling acceptRide: ${e.message}")
                onComplete(false)
            }
        }
    }

    fun rejectOffer(rideId: String, reason: String = "PILOT_DECLINED", onComplete: (Boolean) -> Unit = {}) {
        _activeOffer.value = null
        if (rideId.isBlank()) {
            onComplete(false)
            return
        }
        scope.launch {
            try {
                val res = DriverNetworkClient.apiService.rejectOffer(rideId, mapOf("reason" to reason))
                if (!res.isSuccessful) {
                    Log.w(TAG, "rejectOffer failed with HTTP ${res.code()}")
                }
                onComplete(res.isSuccessful)
            } catch (e: Exception) {
                Log.e(TAG, "Exception calling rejectOffer: ${e.message}")
                onComplete(false)
            }
        }
    }

    fun markArrived(rideId: String, lat: Double? = null, lng: Double? = null, onComplete: (Boolean) -> Unit = {}) {
        if (rideId.isBlank()) {
            onComplete(false)
            return
        }
        scope.launch {
            try {
                val payload = if (lat != null && lng != null) mapOf("latitude" to lat, "longitude" to lng) else emptyMap()
                val res = DriverNetworkClient.apiService.markArrived(rideId, payload)
                if (!res.isSuccessful) {
                    Log.w(TAG, "markArrived failed with HTTP ${res.code()}: ${res.errorBody()?.string()}")
                }
                onComplete(res.isSuccessful)
            } catch (e: Exception) {
                Log.e(TAG, "Exception calling markArrived: ${e.message}")
                onComplete(false)
            }
        }
    }

    fun verifyStartOtp(rideId: String, otp: String, onSuccess: () -> Unit, onFailure: () -> Unit) {
        if (rideId.isBlank()) {
            onFailure()
            return
        }
        scope.launch {
            try {
                val res = DriverNetworkClient.apiService.startTripWithOtp(rideId, mapOf("otp_code" to otp))
                if (res.isSuccessful) {
                    fetchActiveRide()
                    onSuccess()
                } else {
                    val err = res.errorBody()?.string() ?: "Invalid OTP"
                    Log.w(TAG, "startTripWithOtp failed: HTTP ${res.code()} - $err")
                    onFailure()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception calling startTripWithOtp: ${e.message}")
                onFailure()
            }
        }
    }

    fun finishTrip(
        rideId: String,
        actualDistanceKm: Double? = null,
        actualDurationMinutes: Double? = null,
        onComplete: (Boolean) -> Unit = {}
    ) {
        if (rideId.isBlank()) {
            onComplete(false)
            return
        }
        scope.launch {
            try {
                val body = buildMap {
                    actualDistanceKm?.let { put("actual_distance_km", it) }
                    actualDurationMinutes?.let { put("actual_duration_minutes", it) }
                }
                val res = DriverNetworkClient.apiService.finishTrip(rideId, body)
                if (res.isSuccessful) {
                    DriverWebSocketClient.disconnectRideTracking()
                    _activeRide.value = null
                    fetchEarningsSummary()
                    fetchDailyTargets()
                    onComplete(true)
                } else {
                    Log.w(TAG, "finishTrip failed with HTTP ${res.code()}: ${res.errorBody()?.string()}")
                    onComplete(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception calling finishTrip: ${e.message}")
                onComplete(false)
            }
        }
    }

    fun claimNoShow(rideId: String, onComplete: (Boolean) -> Unit = {}) {
        if (rideId.isBlank()) {
            onComplete(false)
            return
        }
        scope.launch {
            try {
                val res = DriverNetworkClient.apiService.claimNoShow(rideId)
                if (res.isSuccessful) {
                    DriverWebSocketClient.disconnectRideTracking()
                    _currentRideId.value = ""
                    _activeRide.value = null
                }
                onComplete(res.isSuccessful)
            } catch (e: Exception) {
                Log.e(TAG, "claimNoShow error: ${e.message}")
                onComplete(false)
            }
        }
    }

    fun collectCash(rideId: String, amountTendered: Double = 0.0, onComplete: (Boolean) -> Unit = {}) {
        if (rideId.isBlank()) {
            onComplete(false)
            return
        }
        scope.launch {
            try {
                val res = DriverNetworkClient.apiService.collectCash(CashCollectRequest(rideId = rideId))
                if (res.isSuccessful) {
                    _currentRideId.value = ""
                    _activeRide.value = null
                    fetchEarningsSummary()
                } else {
                    Log.w(TAG, "collectCash failed with HTTP ${res.code()}: ${res.errorBody()?.string()}")
                }
                onComplete(res.isSuccessful)
            } catch (e: Exception) {
                Log.e(TAG, "Exception calling collectCash: ${e.message}")
                onComplete(false)
            }
        }
    }

    fun onNewDispatchOfferReceived(offer: DispatchOfferDto) {
        _activeOffer.value = offer
        DriverDialogController.show(DriverPopupType.INCOMING_OFFER)
    }

    fun handleRideCancelledByPassenger() {
        _activeOffer.value = null
        _currentRideId.value = ""
        _activeRide.value = null
        DriverWebSocketClient.disconnectRideTracking()
        DriverDialogController.show(DriverPopupType.RIDER_CANCELLED)
    }

    fun requestInstantWithdrawal(
        amountRupees: Double,
        accountNumberMask: String = "**** 9988",
        ifsc: String = "HDFC0001234",
        onResult: (Boolean) -> Unit = {}
    ) {
        val current = _earnings.value
        val amountCents = (amountRupees * 100).toLong()
        if (amountCents <= 0L || current.walletBalanceCents < amountCents) {
            onResult(false)
            return
        }
        scope.launch {
            try {
                val res = DriverNetworkClient.apiService.requestInstantPayout(
                    PayoutRequestDto(
                        amountCents = amountCents,
                        accountNumberMask = accountNumberMask,
                        ifsc = ifsc
                    )
                )
                if (res.isSuccessful) {
                    fetchEarningsSummary()
                    onResult(true)
                } else {
                    onResult(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "requestInstantWithdrawal error: ${e.message}")
                onResult(false)
            }
        }
    }

    fun pingLocation(lat: Double, lng: Double, heading: Double = 0.0, speedMps: Double = 0.0) {
        if (!DriverNetworkClient.isAuthenticated()) return
        scope.launch {
            try {
                DriverNetworkClient.apiService.pingLocation(
                    DriverLocationPingRequest(
                        latitude = lat,
                        longitude = lng,
                        heading = heading,
                        speedMps = speedMps
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "pingLocation error: ${e.message}")
            }
        }
    }

    suspend fun fetchHeatMap(): Result<List<HeatMapClusterDto>> {
        return try {
            val res = DriverNetworkClient.apiService.getHeatMap()
            val body = res.body()
            if (res.isSuccessful && body != null) {
                _heatMapClusters.value = body.clusters
                Result.success(body.clusters)
            } else {
                Result.failure(IllegalStateException("getHeatMap failed: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchRoadRestrictions(lat: Double = 28.6139, lng: Double = 77.2090): Result<List<RoadRestrictionDto>> {
        return try {
            val res = DriverNetworkClient.apiService.getRoadRestrictions(lat, lng)
            val body = res.body()
            if (res.isSuccessful && body != null) {
                _roadRestrictions.value = body
                Result.success(body)
            } else {
                Result.failure(IllegalStateException("getRoadRestrictions failed: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearRoadRestriction(restrictionId: String): Result<Boolean> {
        return try {
            val res = DriverNetworkClient.apiService.clearRoadRestriction(restrictionId)
            if (res.isSuccessful) {
                fetchRoadRestrictions()
                Result.success(true)
            } else {
                Result.failure(IllegalStateException("clearRoadRestriction failed: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rerouteActiveRide(rideId: String, reason: String): Result<Boolean> {
        if (rideId.isBlank()) return Result.failure(IllegalStateException("No active ride"))
        return try {
            val res = DriverNetworkClient.apiService.rerouteRide(rideId, mapOf("reason" to reason))
            if (res.isSuccessful) Result.success(true)
            else Result.failure(IllegalStateException("rerouteRide failed: HTTP ${res.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun startTripWaiting(rideId: String, onDone: (Boolean) -> Unit = {}) {
        if (rideId.isBlank()) {
            onDone(false)
            return
        }
        scope.launch {
            try {
                val res = DriverNetworkClient.apiService.startWaiting(rideId)
                onDone(res.isSuccessful)
            } catch (e: Exception) {
                Log.e(TAG, "startWaiting error: ${e.message}")
                onDone(false)
            }
        }
    }

    fun stopTripWaiting(rideId: String, onDone: (Boolean) -> Unit = {}) {
        if (rideId.isBlank()) {
            onDone(false)
            return
        }
        scope.launch {
            try {
                val res = DriverNetworkClient.apiService.stopWaiting(rideId)
                onDone(res.isSuccessful)
            } catch (e: Exception) {
                Log.e(TAG, "stopWaiting error: ${e.message}")
                onDone(false)
            }
        }
    }

    suspend fun addPayoutBankAccount(accountNo: String, ifsc: String, holderName: String): Result<Boolean> {
        return try {
            val res = DriverNetworkClient.apiService.addPayoutAccount(
                mapOf(
                    "account_type" to "BANK_ACCOUNT",
                    "account_number" to accountNo,
                    "ifsc_code" to ifsc,
                    "beneficiary_name" to holderName
                )
            )
            if (res.isSuccessful) {
                fetchPayoutAccounts()
                Result.success(true)
            } else Result.failure(IllegalStateException("addPayoutAccount failed: HTTP ${res.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reportFatigueState(hoursDriven: Double): Result<Boolean> {
        val resultStatus = if (hoursDriven >= 4.5) "NEEDS_REST" else "FIT"
        return submitFatigueCheckResult(resultStatus, "Continuous driving: $hoursDriven hrs")
    }

    suspend fun reportRouteDeviation(rideId: String, lat: Double, lng: Double): Result<Boolean> {
        if (rideId.isBlank()) return Result.failure(IllegalStateException("No active ride"))
        return try {
            val res = DriverNetworkClient.apiService.reportRouteAnomaly(
                RouteAnomalyRequest(rideId = rideId, currentLat = lat, currentLng = lng)
            )
            if (res.isSuccessful) Result.success(true)
            else Result.failure(IllegalStateException("reportRouteAnomaly failed: HTTP ${res.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateDynamicUpi(rideId: String, amountRupees: Double): Result<String> {
        if (rideId.isBlank()) return Result.failure(IllegalStateException("No active ride"))
        return try {
            val res = DriverNetworkClient.apiService.generateUpiCheckout(
                mapOf("ride_id" to rideId, "amount_cents" to (amountRupees * 100).toInt())
            )
            val body = res.body()
            val url = (body?.get("upi_intent_url") ?: body?.get("qr_code_payload")) as? String
            if (res.isSuccessful && !url.isNullOrBlank()) Result.success(url)
            else Result.failure(IllegalStateException("generateDynamicUpi failed: HTTP ${res.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitFatigueCheckResult(result: String, note: String = "Shift pause logged"): Result<Boolean> {
        return try {
            val res = DriverNetworkClient.apiService.submitFatigueCheck(
                FatigueCheckRequest(
                    result = result,
                    rideId = _currentRideId.value.ifBlank { null },
                    note = note
                )
            )
            if (res.isSuccessful) Result.success(true)
            else Result.failure(IllegalStateException("submitFatigueCheck failed: HTTP ${res.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reportFoundItem(rideId: String, category: String, notes: String): Result<Boolean> {
        return try {
            val res = DriverNetworkClient.apiService.reportSafetyIncident(
                mapOf(
                    "category" to "OTHER",
                    "ride_id" to rideId.ifBlank { "general" },
                    "description" to "Found item ($category): $notes"
                )
            )
            if (res.isSuccessful) Result.success(true)
            else Result.failure(IllegalStateException("reportSafetyIncident failed: HTTP ${res.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitVehicleInspectionCheck(
        vehicleId: String,
        tyrePressureOk: Boolean,
        lightsOk: Boolean,
        brakeOk: Boolean,
        photos: Map<String, String> = mapOf("front_url" to "pending-capture")
    ): Result<Boolean> {
        return try {
            val checklist = mapOf(
                "TIRES_RIMS" to if (tyrePressureOk) "PASS" else "FAIL",
                "BRAKES" to if (brakeOk) "PASS" else "FAIL",
                "LIGHTS" to if (lightsOk) "PASS" else "FAIL"
            )
            val res = DriverNetworkClient.apiService.submitVehicleInspection(
                VehicleInspectionRequest(
                    vehicleId = vehicleId,
                    checklistResults = checklist,
                    photos = photos,
                    notes = "Pre-shift inspection"
                )
            )
            if (res.isSuccessful) Result.success(true)
            else Result.failure(IllegalStateException("submitVehicleInspection failed: HTTP ${res.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateAudioShieldConsent(rideId: String, consented: Boolean): Result<Boolean> {
        if (rideId.isBlank()) return Result.failure(IllegalStateException("Active ride required for Audio Shield"))
        return try {
            val res = DriverNetworkClient.apiService.updateRecordingConsent(
                RecordingConsentRequest(rideId = rideId, role = "DRIVER", consented = consented)
            )
            if (res.isSuccessful) Result.success(true)
            else Result.failure(IllegalStateException("Recording consent failed: HTTP ${res.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun triggerEmergencySos(rideId: String, notes: String = "Pilot triggered SOS 112"): Result<String> {
        return try {
            if (rideId.isNotBlank()) {
                val res = DriverNetworkClient.apiService.triggerDriverEmergencyAlert(
                    EmergencyAlertRequest(rideId = rideId, notes = notes)
                )
                if (res.isSuccessful) {
                    val msg = res.body()?.get("message") as? String ?: "Emergency SOS alert recorded!"
                    return Result.success(msg)
                }
            }
            val pingRes = DriverNetworkClient.apiService.sendEmergencyPing(
                mapOf("latitude" to 28.6139, "longitude" to 77.2090, "note" to notes)
            )
            if (pingRes.isSuccessful) Result.success("Emergency SOS ping dispatched!")
            else Result.failure(IllegalStateException("SOS failed: HTTP ${pingRes.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitDriverIncident(
        rideId: String?,
        category: String,
        description: String,
        severity: String = "MEDIUM"
    ): Result<String> {
        return try {
            val res = DriverNetworkClient.apiService.submitDriverSafetyIncident(
                SafetyIncidentRequest(
                    rideId = rideId?.takeIf { it.isNotBlank() },
                    category = category,
                    severity = severity,
                    description = description.trim()
                )
            )
            if (res.isSuccessful) {
                val ticket = res.body()?.get("ticket_number") as? String ?: "Submitted"
                Result.success(ticket)
            } else {
                val err = res.errorBody()?.string() ?: "HTTP ${res.code()}"
                Result.failure(IllegalStateException(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchEmergencyContacts(): Result<List<EmergencyContactDto>> {
        return try {
            val res = DriverNetworkClient.apiService.getEmergencyContacts()
            val body = res.body()
            if (res.isSuccessful && body != null) {
                _emergencyContacts.value = body
                Result.success(body)
            } else {
                Result.failure(IllegalStateException("getEmergencyContacts failed: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addEmergencyContact(name: String, relation: String, phone: String): Result<Boolean> {
        return try {
            val res = DriverNetworkClient.apiService.addEmergencyContact(
                EmergencyContactCreateRequest(
                    name = name.trim(),
                    relationship = relation.trim().ifBlank { "Family" },
                    phoneNumber = phone.trim()
                )
            )
            if (res.isSuccessful) {
                fetchEmergencyContacts()
                Result.success(true)
            } else {
                Result.failure(IllegalStateException("addEmergencyContact failed: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteEmergencyContact(contactId: String): Result<Boolean> {
        return try {
            val res = DriverNetworkClient.apiService.deleteEmergencyContact(contactId)
            if (res.isSuccessful) {
                fetchEmergencyContacts()
                Result.success(true)
            } else {
                Result.failure(IllegalStateException("deleteEmergencyContact failed: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchSharedTripForRide(rideId: String): Result<SharedPoolTripDto?> {
        if (rideId.isBlank()) {
            _sharedPoolTrip.value = null
            return Result.success(null)
        }
        return try {
            val res = DriverNetworkClient.apiService.getSharedTripForRide(rideId)
            if (res.isSuccessful) {
                val body = res.body()
                _sharedPoolTrip.value = body
                Result.success(body)
            } else {
                _sharedPoolTrip.value = null
                Result.failure(IllegalStateException("getSharedTripForRide failed: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchRideStops(rideId: String): Result<List<RideStopDto>> {
        if (rideId.isBlank()) return Result.success(emptyList())
        return try {
            val res = DriverNetworkClient.apiService.getRideStops(rideId)
            val body = res.body()
            if (res.isSuccessful && body != null) {
                _rideStops.value = body
                Result.success(body)
            } else {
                Result.failure(IllegalStateException("getRideStops failed: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markRideStopArrived(rideId: String, stopId: String): Result<Boolean> {
        if (rideId.isBlank() || stopId.isBlank()) return Result.failure(IllegalStateException("Missing ride or stop ID"))
        return try {
            val res = DriverNetworkClient.apiService.arriveAtRideStop(rideId, stopId)
            if (res.isSuccessful) {
                fetchRideStops(rideId)
                fetchSharedTripForRide(rideId)
                Result.success(true)
            } else {
                Result.failure(IllegalStateException("arriveAtRideStop failed: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendCoPassengerBroadcast(tripId: String?, rideId: String, message: String): Result<Boolean> {
        val text = message.trim()
        if (text.isBlank()) return Result.failure(IllegalStateException("Message cannot be empty"))
        return try {
            if (!tripId.isNullOrBlank()) {
                val res = DriverNetworkClient.apiService.sendPoolMessage(tripId, mapOf("message" to text))
                if (res.isSuccessful) return Result.success(true)
            }
            if (rideId.isNotBlank()) {
                val res = DriverNetworkClient.apiService.sendRideChatMessage(rideId, mapOf("message_text" to text))
                if (res.isSuccessful) return Result.success(true)
                return Result.failure(IllegalStateException("sendRideChatMessage failed: HTTP ${res.code()}"))
            }
            Result.failure(IllegalStateException("No active shared trip or ride found"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchTrainingCurriculum(): Result<List<TrainingModuleDto>> {
        return try {
            val res = DriverNetworkClient.apiService.getTrainingCurriculum()
            val body = res.body()
            if (res.isSuccessful && body != null) {
                _trainingModules.value = body
                Result.success(body)
            } else {
                Result.failure(IllegalStateException("getTrainingCurriculum failed: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchDriverTickets(): Result<List<DriverTicketDto>> {
        return try {
            val res = DriverNetworkClient.apiService.getDriverTickets()
            val body = res.body()
            if (res.isSuccessful && body != null) {
                _driverTickets.value = body
                Result.success(body)
            } else {
                Result.failure(IllegalStateException("getDriverTickets failed: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createDriverTicket(subject: String, message: String): Result<Boolean> {
        return try {
            val res = DriverNetworkClient.apiService.createDriverTicket(
                CreateDriverTicketRequest(subject = subject.trim(), message = message.trim())
            )
            if (res.isSuccessful) {
                fetchDriverTickets()
                Result.success(true)
            } else {
                Result.failure(IllegalStateException("createDriverTicket failed: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchNotifications(): Result<List<NotificationItemDto>> {
        return try {
            val res = DriverNetworkClient.apiService.getNotificationsInbox(unreadOnly = false, limit = 30)
            val body = res.body()
            if (res.isSuccessful && body != null) {
                _notifications.value = body.items
                _unreadNotificationCount.value = body.unreadCount
                Result.success(body.items)
            } else {
                Result.failure(IllegalStateException("getNotifications failed: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markNotificationAsRead(notificationId: String): Result<Boolean> {
        return try {
            val res = DriverNetworkClient.apiService.markNotificationRead(notificationId)
            if (res.isSuccessful) {
                fetchNotifications()
                Result.success(true)
            } else {
                Result.failure(IllegalStateException("markNotificationRead failed: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchPayoutAccounts(): Result<List<PayoutAccountDto>> {
        return try {
            val res = DriverNetworkClient.apiService.getPayoutAccounts()
            val body = res.body()
            if (res.isSuccessful && body != null) {
                _payoutAccounts.value = body
                Result.success(body)
            } else {
                Result.failure(IllegalStateException("getPayoutAccounts failed: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setPrimaryPayoutAccount(accountId: String): Result<Boolean> {
        return try {
            val res = DriverNetworkClient.apiService.setPrimaryPayoutAccount(accountId)
            if (res.isSuccessful) {
                fetchPayoutAccounts()
                Result.success(true)
            } else {
                Result.failure(IllegalStateException("setPrimaryPayoutAccount failed: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchWeeklyGoal(): Result<WeeklyGoalDto> {
        return try {
            val res = DriverNetworkClient.apiService.getWeeklyGoal()
            val body = res.body()
            if (res.isSuccessful && body != null) {
                _weeklyGoal.value = body
                Result.success(body)
            } else {
                Result.failure(IllegalStateException("getWeeklyGoal failed: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchIncentiveMilestones(): Result<List<IncentiveMilestoneDto>> {
        return try {
            val res = DriverNetworkClient.apiService.getIncentiveOverview()
            val body = res.body()
            if (res.isSuccessful && body != null) {
                _incentiveMilestones.value = body.milestones
                Result.success(body.milestones)
            } else {
                Result.failure(IllegalStateException("getIncentiveOverview failed: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchRideBreakdown(rideId: String): Result<RideBreakdownDto> {
        if (rideId.isBlank()) return Result.failure(IllegalStateException("No ride ID"))
        return try {
            val res = DriverNetworkClient.apiService.getRideBreakdown(rideId)
            val body = res.body()
            if (res.isSuccessful && body != null) {
                _rideBreakdown.value = body
                Result.success(body)
            } else {
                Result.failure(IllegalStateException("getRideBreakdown failed: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateAutoAccept(enabled: Boolean, maxDistanceKm: Double = 5.0): Result<Boolean> {
        return try {
            val res = DriverNetworkClient.apiService.setAutoAccept(
                AutoAcceptRequest(enabled = enabled, maxDistanceKm = maxDistanceKm)
            )
            if (res.isSuccessful) Result.success(true)
            else Result.failure(IllegalStateException("setAutoAccept failed: HTTP ${res.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateDriverProfile(fullName: String, email: String? = null, phoneNumber: String? = null): Result<Boolean> {
        return try {
            val res = DriverNetworkClient.apiService.updateDriverProfile(
                DriverProfileUpdateRequest(
                    fullName = fullName.trim().takeIf { it.isNotBlank() },
                    email = email?.trim()?.takeIf { it.isNotBlank() },
                    phoneNumber = phoneNumber?.trim()?.takeIf { it.isNotBlank() }
                )
            )
            if (res.isSuccessful) {
                fetchDriverProfile()
                Result.success(true)
            } else {
                Result.failure(IllegalStateException("updateDriverProfile failed: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}