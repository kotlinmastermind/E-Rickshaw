package com.vaisentra.mobility.driver.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.vaisentra.mobility.driver.components.DriverDialogController
import com.vaisentra.mobility.driver.components.DriverPopupType
import java.io.ByteArrayOutputStream
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DriverGo Central Repository bridging UI Screens and Popups with FastAPI + Supabase Backend
 */
object DriverRepository {

    private const val TAG = "DriverRepository"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun parseApiErrorMessage(raw: String?, defaultMessage: String = "An error occurred"): String {
        if (raw.isNullOrBlank()) return defaultMessage
        return try {
            val json = org.json.JSONObject(raw)
            if (json.has("error")) {
                val errorObj = json.optJSONObject("error")
                if (errorObj != null && errorObj.has("message")) {
                    errorObj.getString("message")
                } else if (errorObj != null && errorObj.has("detail")) {
                    errorObj.getString("detail")
                } else {
                    json.optString("error", defaultMessage)
                }
            } else if (json.has("detail")) {
                json.getString("detail")
            } else if (json.has("message")) {
                json.getString("message")
            } else {
                defaultMessage
            }
        } catch (_: Exception) {
            if (raw.length < 120 && !raw.trim().startsWith("{")) raw else defaultMessage
        }
    }

    private val _isBackendOnline = MutableStateFlow(true)
    val isBackendOnline: StateFlow<Boolean> = _isBackendOnline.asStateFlow()

    init {
        DriverNetworkClient.onAuthExpired = {
            logout()
        }
    }

    suspend fun checkBackendHealth(): Result<BackendHealthDto> {
        return try {
            val res = DriverNetworkClient.apiService.healthCheck()
            if (res.isSuccessful && res.body() != null) {
                _isBackendOnline.value = true
                Result.success(res.body()!!)
            } else {
                _isBackendOnline.value = false
                Result.failure(IllegalStateException("Backend health check failed: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            _isBackendOnline.value = false
            Log.w(TAG, "checkBackendHealth exception: ${e.message}")
            Result.failure(e)
        }
    }

    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _isShiftActive = MutableStateFlow(false)
    val isShiftActive: StateFlow<Boolean> = _isShiftActive.asStateFlow()

    fun setShiftActive(active: Boolean) {
        _isShiftActive.value = active
    }

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

    private val _driverLocation = MutableStateFlow(DriverLiveLocation())
    val driverLocation: StateFlow<DriverLiveLocation> = _driverLocation.asStateFlow()

    fun updateCurrentLocation(lat: Double, lng: Double, heading: Double = 0.0, speedMps: Double = 0.0, address: String? = null) {
        _driverLocation.value = _driverLocation.value.copy(
            latitude = lat,
            longitude = lng,
            heading = heading,
            speedKmh = speedMps * 3.6,
            address = address ?: _driverLocation.value.address,
            isLiveGps = true,
            lastUpdatedTimestamp = System.currentTimeMillis()
        )
    }

    fun initProfilePhoto() {
        val cached = DriverNetworkClient.getProfilePhotoUri()
        if (!cached.isNullOrBlank()) {
            _profilePhotoUri.value = cached
        }
    }

    fun updateProfilePhoto(uri: String?) {
        _profilePhotoUri.value = uri
        if (uri != null) {
            DriverNetworkClient.saveProfilePhotoUri(uri)
        } else {
            DriverNetworkClient.clearProfilePhotoUri()
        }
    }

    suspend fun loadAvatarBitmap(context: Context, source: String?): Bitmap? = withContext(Dispatchers.IO) {
        if (source.isNullOrBlank()) return@withContext null
        try {
            when {
                source.startsWith("data:image") -> {
                    val base64Part = source.substringAfter("base64,")
                    val decodedBytes = Base64.decode(base64Part, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                }
                source.startsWith("http://") || source.startsWith("https://") -> {
                    val url = java.net.URL(source)
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.connectTimeout = 8000
                    connection.readTimeout = 8000
                    connection.doInput = true
                    connection.connect()
                    connection.inputStream.use { input ->
                        BitmapFactory.decodeStream(input)
                    }
                }
                source.startsWith("content://") || source.startsWith("file://") -> {
                    context.contentResolver.openInputStream(Uri.parse(source))?.use { input ->
                        BitmapFactory.decodeStream(input)
                    }
                }
                else -> {
                    try {
                        val decodedBytes = Base64.decode(source, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                    } catch (_: Exception) {
                        context.contentResolver.openInputStream(Uri.parse(source))?.use { input ->
                            BitmapFactory.decodeStream(input)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode avatar bitmap: ${e.message}")
            null
        }
    }

    suspend fun uploadProfilePhotoBitmap(context: Context, bitmap: Bitmap): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val maxDim = 320
                val scale = minOf(1f, maxDim.toFloat() / maxOf(bitmap.width, bitmap.height))
                val scaledWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
                val scaledHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
                val scaledBitmap = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true) else bitmap

                val stream = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                val bytes = stream.toByteArray()
                val base64DataUri = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)

                _profilePhotoUri.value = base64DataUri
                DriverNetworkClient.saveProfilePhotoUri(base64DataUri)
                _profile.value = _profile.value.copy(avatarUrl = base64DataUri)

                val updateRes = DriverNetworkClient.apiService.updateDriverProfile(
                    DriverProfileUpdateRequest(avatarUrl = base64DataUri)
                )
                if (updateRes.isSuccessful) {
                    Log.i(TAG, "Profile photo synced to database successfully via PUT /drivers/profile")
                    Result.success(true)
                } else {
                    val avatarRes = DriverNetworkClient.apiService.updateDriverAvatar(
                        AvatarUpdateRequest(avatarUrl = base64DataUri)
                    )
                    if (avatarRes.isSuccessful) {
                        Log.i(TAG, "Profile photo synced to database successfully via /drivers/avatar")
                        Result.success(true)
                    } else {
                        val authRes = DriverNetworkClient.apiService.updateAuthAvatar(
                            AvatarUpdateRequest(avatarUrl = base64DataUri)
                        )
                        if (authRes.isSuccessful) {
                            Log.i(TAG, "Profile photo synced to database successfully via /auth/avatar")
                            Result.success(true)
                        } else {
                            Result.failure(IllegalStateException("Server update failed: HTTP ${updateRes.code()}"))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "uploadProfilePhotoBitmap failed: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    suspend fun removeProfilePhoto(): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                _profilePhotoUri.value = null
                DriverNetworkClient.clearProfilePhotoUri()
                _profile.value = _profile.value.copy(avatarUrl = null)
                try {
                    DriverNetworkClient.apiService.deleteDriverAvatar()
                } catch (_: Exception) {}
                try {
                    DriverNetworkClient.apiService.updateDriverProfile(
                        DriverProfileUpdateRequest(avatarUrl = "")
                    )
                } catch (_: Exception) {}
                Result.success(true)
            } catch (e: Exception) {
                Result.failure(e)
            }
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

    private val _vehicles = MutableStateFlow<List<DriverVehicleDto>>(emptyList())
    val vehicles: StateFlow<List<DriverVehicleDto>> = _vehicles.asStateFlow()

    private val _assignedVehicle = MutableStateFlow<DriverVehicleDto?>(null)
    val assignedVehicle: StateFlow<DriverVehicleDto?> = _assignedVehicle.asStateFlow()

    private val _vehicleCategories = MutableStateFlow<List<VehicleCategoryDto>>(emptyList())
    val vehicleCategories: StateFlow<List<VehicleCategoryDto>> = _vehicleCategories.asStateFlow()

    private val _ledger = MutableStateFlow<List<LedgerItemDto>>(emptyList())
    val ledger: StateFlow<List<LedgerItemDto>> = _ledger.asStateFlow()

    private val _activeOffer = MutableStateFlow<DispatchOfferDto?>(null)
    val activeOffer: StateFlow<DispatchOfferDto?> = _activeOffer.asStateFlow()

    private val _emergencyContacts = MutableStateFlow<List<EmergencyContactDto>>(emptyList())
    val emergencyContacts: StateFlow<List<EmergencyContactDto>> = _emergencyContacts.asStateFlow()

    private val _driverIncidents = MutableStateFlow<List<DriverIncidentDto>>(emptyList())
    val driverIncidents: StateFlow<List<DriverIncidentDto>> = _driverIncidents.asStateFlow()

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

    private val _driverPreferences = MutableStateFlow(DriverPreferencesState())
    val driverPreferences: StateFlow<DriverPreferencesState> = _driverPreferences.asStateFlow()

    private val _payoutAccounts = MutableStateFlow<List<PayoutAccountDto>>(emptyList())
    val payoutAccounts: StateFlow<List<PayoutAccountDto>> = _payoutAccounts.asStateFlow()

    private val _payoutHistory = MutableStateFlow<List<PayoutHistoryItemDto>>(emptyList())
    val payoutHistory: StateFlow<List<PayoutHistoryItemDto>> = _payoutHistory.asStateFlow()

    private val _weeklyGoal = MutableStateFlow(WeeklyGoalDto())
    val weeklyGoal: StateFlow<WeeklyGoalDto> = _weeklyGoal.asStateFlow()

    private val _incentiveMilestones = MutableStateFlow<List<IncentiveMilestoneDto>>(emptyList())
    val incentiveMilestones: StateFlow<List<IncentiveMilestoneDto>> = _incentiveMilestones.asStateFlow()

    private val _rideBreakdown = MutableStateFlow<RideBreakdownDto?>(null)
    val rideBreakdown: StateFlow<RideBreakdownDto?> = _rideBreakdown.asStateFlow()

    private val _financialSupportRequests = MutableStateFlow<List<FinancialSupportDto>>(emptyList())
    val financialSupportRequests: StateFlow<List<FinancialSupportDto>> = _financialSupportRequests.asStateFlow()

    private val _driverRatings = MutableStateFlow<List<DriverRatingItemDto>>(emptyList())
    val driverRatings: StateFlow<List<DriverRatingItemDto>> = _driverRatings.asStateFlow()

    private val _ratingAppeals = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val ratingAppeals: StateFlow<List<Map<String, Any>>> = _ratingAppeals.asStateFlow()

    private val _audioRecordingStatus = MutableStateFlow<AudioRecordingStatusResponse?>(null)
    val audioRecordingStatus: StateFlow<AudioRecordingStatusResponse?> = _audioRecordingStatus.asStateFlow()

    private val _isAudioRecordingActive = MutableStateFlow(false)
    val isAudioRecordingActive: StateFlow<Boolean> = _isAudioRecordingActive.asStateFlow()

    fun setAudioRecordingActive(active: Boolean) {
        _isAudioRecordingActive.value = active
    }

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
            com.vaisentra.mobility.driver.fcm.DriverFcmManager.syncTokenIfAuthenticated(force = true)
            scope.launch {
                fetchDriverProfile()
                fetchDriverVehicles()
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

    suspend fun registerDriver(
        phone: String,
        fullName: String,
        password: String,
        licenseNumber: String,
        vehicleType: String
    ): Result<Boolean> {
        return try {
            val digits = phone.filter { it.isDigit() }
            val normPhone = if (phone.startsWith("+")) phone else if (digits.length == 10) "+91$digits" else "+$digits"
            val res = DriverNetworkClient.apiService.registerDriver(
                DriverSignupRequest(
                    phoneNumber = normPhone,
                    fullName = fullName,
                    password = password,
                    licenseNumber = licenseNumber,
                    vehicleType = vehicleType
                )
            )
            val body = res.body()
            val token = body?.accessToken
            if (res.isSuccessful && !token.isNullOrBlank()) {
                DriverNetworkClient.setAuthToken(token, body.refreshToken)
            com.vaisentra.mobility.driver.fcm.DriverFcmManager.syncTokenIfAuthenticated(force = true)
                scope.launch {
                    launch { fetchDriverProfile() }
                    launch { fetchDriverVehicles() }
                    launch { fetchEarningsSummary() }
                    launch { fetchDriverDocuments() }
                    launch { fetchPerformanceScorecard() }
                    launch { fetchDailyTargets() }
                    launch { fetchActiveRide() }
                    launch { fetchUnreadNotificationsCount() }
                }
                Result.success(true)
            } else {
                val errMsg = res.errorBody()?.string() ?: "Registration failed: HTTP ${res.code()}"
                Log.w(TAG, "registerDriver failed: $errMsg")
                Result.failure(IllegalStateException(errMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "registerDriver exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun fetchVehicleCategories(): Result<List<VehicleCategoryDto>> {
        return try {
            val res = DriverNetworkClient.apiService.getVehicleCategories()
            if (res.isSuccessful && res.body() != null) {
                val list = res.body()!!.filter { it.isActive }
                _vehicleCategories.value = list
                Result.success(list)
            } else {
                Result.failure(Exception("Failed to load vehicle categories: ${res.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchVehicleCategories error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun driverLogin(phone: String, pinOrOtp: String, loginType: String = "PASSWORD"): Result<Boolean> {
        val digits = phone.filter { it.isDigit() }
        val normalizedPhone = if (phone.startsWith("+")) phone else if (digits.length == 10) "+91$digits" else "+$digits"
        if (loginType.equals("OTP", ignoreCase = true)) {
            val verifyRes = verifyOtp(normalizedPhone, pinOrOtp)
            return if (verifyRes.isSuccess) {
                Result.success(true)
            } else {
                Result.failure(verifyRes.exceptionOrNull() ?: IllegalStateException("OTP verification failed"))
            }
        }
        return try {
            val res = DriverNetworkClient.apiService.login(
                DriverLoginRequest(
                    identifier = normalizedPhone,
                    password = pinOrOtp,
                    loginType = loginType
                )
            )
            val body = res.body()
            val token = body?.accessToken
            if (res.isSuccessful && !token.isNullOrBlank()) {
                DriverNetworkClient.setAuthToken(token, body.refreshToken)
            com.vaisentra.mobility.driver.fcm.DriverFcmManager.syncTokenIfAuthenticated(force = true)
                scope.launch {
                    launch { fetchDriverProfile() }
                    launch { fetchDriverVehicles() }
                    launch { fetchEarningsSummary() }
                    launch { fetchDriverDocuments() }
                    launch { fetchPerformanceScorecard() }
                    launch { fetchDailyTargets() }
                    launch { fetchActiveRide() }
                    launch { fetchUnreadNotificationsCount() }
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
        DriverLocationManager.stopTracking()
        DriverWebSocketClient.disconnectAll()
        com.vaisentra.mobility.driver.fcm.DriverFcmManager.deactivateAndLogoutAsync()
    }

    suspend fun fetchActiveRide(): Result<ActiveRideDto?> {
        return try {
            val res = DriverNetworkClient.apiService.getActiveRide()
            if (res.isSuccessful) {
                val ride = res.body()?.activeRide
                val status = ride?.status?.uppercase() ?: ""
                val isActive = ride != null && ride.id.isNotBlank() && status in listOf("ASSIGNED", "ARRIVED", "IN_PROGRESS", "PAUSED", "STARTED")
                _activeRide.value = if (isActive) ride else null
                if (isActive) {
                    _currentRideId.value = ride!!.id
                    DriverWebSocketClient.connectRideTracking(ride!!.id)
                } else {
                    _currentRideId.value = ""
                    DriverWebSocketClient.disconnectRideTracking()
                }
                Result.success(_activeRide.value)
            } else {
                _activeRide.value = null
                _currentRideId.value = ""
                Result.failure(IllegalStateException("getActiveRide failed: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            _activeRide.value = null
            _currentRideId.value = ""
            Result.failure(e)
        }
    }

    fun clearActiveRide() {
        _activeRide.value = null
        _currentRideId.value = ""
    }

    fun updateActiveRideStatus(rideId: String, status: String) {
        val normalizedStatus = status.trim().uppercase()
        if (rideId.isBlank() || normalizedStatus.isBlank()) return
        val currentRide = _activeRide.value
        if (currentRide == null || currentRide.id != rideId) {
            // The WebSocket snapshot may arrive before the REST active-ride fetch.
            scope.launch { fetchActiveRide() }
            return
        }
        if (normalizedStatus in setOf("COMPLETED", "CANCELLED", "EXPIRED")) {
            clearActiveRide()
            DriverWebSocketClient.disconnectRideTracking()
            return
        }
        if (normalizedStatus in setOf("ASSIGNED", "ARRIVED", "IN_PROGRESS", "PAUSED")) {
            _activeRide.value = currentRide.copy(status = normalizedStatus)
            _currentRideId.value = rideId
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
            val targetVehicleId = vehicleId ?: _assignedVehicle.value?.vehicleId
            val res = DriverNetworkClient.apiService.startShift(
                StartShiftRequest(vehicleId = targetVehicleId, startOdometerKm = km)
            )
            if (res.isSuccessful) {
                setDutyStatus(true)
                Result.success(true)
            } else {
                val err = res.errorBody()?.string() ?: "HTTP ${res.code()}"
                Result.failure(IllegalStateException("startShift failed: $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun endShiftOdometer(km: Double, soc: Int = 85): Result<Boolean> {
        return try {
            val res = DriverNetworkClient.apiService.endShift(EndShiftRequest(endOdometerKm = km))
            if (res.isSuccessful) {
                setDutyStatus(false)
                Result.success(true)
            } else {
                val err = res.errorBody()?.string() ?: "HTTP ${res.code()}"
                Result.failure(IllegalStateException("endShift failed: $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchShiftStatus(): Result<Map<String, Any>> {
        return try {
            val res = DriverNetworkClient.apiService.getShiftStatus()
            val body = res.body()
            if (res.isSuccessful && body != null) {
                Result.success(body)
            } else {
                Result.failure(IllegalStateException("getShiftStatus HTTP ${res.code()}"))
            }
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

    suspend fun uploadDriverDocument(
        documentType: String,
        uri: Uri,
        context: Context
    ): Result<Boolean> {
        return try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
            val inputStream = contentResolver.openInputStream(uri)
                ?: return Result.failure(IllegalStateException("Cannot read selected file."))
            val bytes = inputStream.use { it.readBytes() }
            if (bytes.isEmpty()) {
                return Result.failure(IllegalStateException("Selected file is empty."))
            }

            var fileName = "${documentType.lowercase()}_${System.currentTimeMillis()}"
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        val retrievedName = it.getString(nameIndex)
                        if (!retrievedName.isNullOrBlank()) {
                            fileName = retrievedName
                        }
                    }
                }
            }
            if (!fileName.contains(".")) {
                val ext = when {
                    mimeType.contains("pdf", ignoreCase = true) -> "pdf"
                    mimeType.contains("png", ignoreCase = true) -> "png"
                    mimeType.contains("webp", ignoreCase = true) -> "webp"
                    else -> "jpg"
                }
                fileName = "$fileName.$ext"
            }

            val requestFile = bytes.toRequestBody(mimeType.toMediaTypeOrNull(), 0, bytes.size)
            val filePart = MultipartBody.Part.createFormData("file", fileName, requestFile)
            val docTypeBody = documentType.toRequestBody("text/plain".toMediaTypeOrNull())

            val res = DriverNetworkClient.apiService.uploadDocument(docTypeBody, filePart)
            if (res.isSuccessful) {
                fetchDriverDocuments()
                Result.success(true)
            } else {
                val err = res.errorBody()?.string() ?: "HTTP ${res.code()}"
                Result.failure(IllegalStateException(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchDocumentContentBytes(documentId: String): Result<ByteArray> {
        return try {
            val res = DriverNetworkClient.apiService.getDocumentContent(documentId)
            val body = res.body()
            if (res.isSuccessful && body != null) {
                val bytes = body.bytes()
                Result.success(bytes)
            } else {
                Result.failure(IllegalStateException("Failed to load document content: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchDocumentSignedUrl(documentId: String): Result<String> {
        return try {
            val res = DriverNetworkClient.apiService.getDocumentSignedUrl(documentId)
            val body = res.body()
            val signedUrl = body?.get("signed_url") as? String
            if (res.isSuccessful && !signedUrl.isNullOrBlank()) {
                val fullUrl = if (signedUrl.startsWith("http")) signedUrl else "${DriverNetworkClient.baseUrl.removeSuffix("/")}$signedUrl"
                Result.success(fullUrl)
            } else {
                val fallback = "${DriverNetworkClient.baseUrl.removeSuffix("/")}/api/v1/drivers/documents/$documentId/content"
                Result.success(fallback)
            }
        } catch (e: Exception) {
            val fallback = "${DriverNetworkClient.baseUrl.removeSuffix("/")}/api/v1/drivers/documents/$documentId/content"
            Result.success(fallback)
        }
    }

    suspend fun fetchDriverProfile(): Result<DriverProfileDto> {
        return try {
            val res = DriverNetworkClient.apiService.getProfile()
            val body = res.body()
            if (res.isSuccessful && body != null) {
                _profile.value = body
                if (!body.avatarUrl.isNullOrBlank()) {
                    _profilePhotoUri.value = body.avatarUrl
                    DriverNetworkClient.saveProfilePhotoUri(body.avatarUrl)
                }
                Result.success(body)
            } else {
                Result.failure(IllegalStateException("getProfile failed: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchDriverVehicles(): Result<List<DriverVehicleDto>> {
        return try {
            val res = DriverNetworkClient.apiService.getDriverVehicles()
            val body = res.body()
            if (res.isSuccessful && !body.isNullOrEmpty()) {
                _vehicles.value = body
                val activeVeh = body.firstOrNull { it.isActiveVehicle || it.isActive } ?: body.firstOrNull()
                _assignedVehicle.value = activeVeh
                if (activeVeh != null) {
                    val currentProf = _profile.value
                    val updatedPlate = currentProf.assignedVehiclePlate.ifBlank { activeVeh.licensePlate }
                    val updatedModel = currentProf.assignedVehicleModel.ifBlank { activeVeh.model }
                    _profile.value = currentProf.copy(
                        assignedVehiclePlate = updatedPlate,
                        assignedVehicleModel = updatedModel
                    )
                }
                Result.success(body)
            } else {
                // Try fetching assigned vehicle directly
                val vRes = DriverNetworkClient.apiService.getActiveVehicle()
                val vBody = vRes.body()
                if (vRes.isSuccessful && vBody != null) {
                    _vehicles.value = listOf(vBody)
                    _assignedVehicle.value = vBody
                    val currentProf = _profile.value
                    _profile.value = currentProf.copy(
                        assignedVehiclePlate = vBody.licensePlate,
                        assignedVehicleModel = vBody.model
                    )
                    Result.success(listOf(vBody))
                } else {
                    Result.success(_vehicles.value)
                }
            }
        } catch (e: Exception) {
            // Fallback attempt
            try {
                val vRes = DriverNetworkClient.apiService.getActiveVehicle()
                val vBody = vRes.body()
                if (vRes.isSuccessful && vBody != null) {
                    _vehicles.value = listOf(vBody)
                    _assignedVehicle.value = vBody
                    Result.success(listOf(vBody))
                } else {
                    Result.failure(e)
                }
            } catch (ex: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun updateActiveVehicle(
        plate: String,
        model: String,
        rc: String,
        permit: String,
        color: String = "Electric Green",
        year: Int = 2025
    ): Result<Boolean> {
        return try {
            val res = DriverNetworkClient.apiService.updateActiveVehicle(
                VehicleUpdateDto(
                    licensePlate = plate.trim().uppercase(),
                    model = model.trim(),
                    rcNumber = rc.trim().uppercase(),
                    permitNumber = permit.trim().uppercase(),
                    color = color.trim(),
                    year = year
                )
            )
            if (res.isSuccessful) {
                fetchDriverVehicles()
                fetchDriverProfile()
                Result.success(true)
            } else {
                val err = res.errorBody()?.string() ?: "HTTP ${res.code()}"
                Result.failure(IllegalStateException(err))
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
                _activeOffer.value = firstOffer
                Result.success(body)
            } else {
                _activeOffer.value = null
                Result.failure(IllegalStateException("getActiveOffers failed: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            _activeOffer.value = null
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
        val assignmentId = _activeOffer.value
            ?.takeIf { it.rideId == rideId }
            ?.assignmentId
            ?.takeIf { it.isNotBlank() }
        scope.launch {
            try {
                val res = DriverNetworkClient.apiService.acceptRide(rideId, assignmentId)
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
        val assignmentId = _activeOffer.value
            ?.takeIf { it.rideId == rideId }
            ?.assignmentId
            ?.takeIf { it.isNotBlank() }
        _activeOffer.value = null
        if (rideId.isBlank()) {
            onComplete(false)
            return
        }
        scope.launch {
            try {
                val res = DriverNetworkClient.apiService.rejectOffer(
                    rideId,
                    assignmentId,
                    RejectOfferRequest(reason = reason)
                )
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
                val payload = CoordinatePayloadRequest(latitude = lat, longitude = lng)
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
                val res = DriverNetworkClient.apiService.startTripWithOtp(rideId, StartTripOtpRequest(otpCode = otp))
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
                val body = FinishTripRequest(
                    actualDistanceKm = actualDistanceKm,
                    actualDurationMinutes = actualDurationMinutes
                )
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
        payoutAccountId: String,
        idempotencyKey: String? = null,
        onResult: (Boolean) -> Unit = {}
    ) {
        val amountCents = (amountRupees * 100).toLong()
        if (amountCents <= 0L || payoutAccountId.isBlank()) {
            onResult(false)
            return
        }
        val resolvedKey = idempotencyKey?.takeIf { it.isNotBlank() } ?: "payout_${java.util.UUID.randomUUID()}"
        scope.launch {
            try {
                val res = DriverNetworkClient.apiService.requestInstantPayout(
                    PayoutRequestDto(
                        amountCents = amountCents,
                        payoutAccountId = payoutAccountId,
                        idempotencyKey = resolvedKey
                    )
                )
                if (res.isSuccessful) {
                    fetchEarningsSummary()
                    fetchPayoutHistory()
                    onResult(true)
                } else {
                    val err = res.errorBody()?.string() ?: "HTTP ${res.code()}"
                    Log.w(TAG, "requestInstantPayout failed: $err")
                    onResult(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "requestInstantWithdrawal error: ${e.message}")
                onResult(false)
            }
        }
    }

    suspend fun reverseGeocodeLocation(lat: Double, lng: Double): String? {
        if (!DriverNetworkClient.isAuthenticated()) return null
        return try {
            val res = DriverNetworkClient.apiService.reverseGeocode(
                DriverReverseGeocodeRequest(latitude = lat, longitude = lng)
            )
            if (res.isSuccessful) {
                res.body()?.formattedAddress?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "reverseGeocodeLocation error: ${e.message}")
            null
        }
    }

    fun pingLocation(
        lat: Double,
        lng: Double,
        heading: Double = 0.0,
        speedMps: Double = 0.0,
        accuracyM: Double? = null,
        isMockLocation: Boolean = false,
        locationSource: String = "GPS"
    ) {
        if (!DriverNetworkClient.isAuthenticated()) return
        scope.launch {
            try {
                DriverNetworkClient.apiService.pingLocation(
                    DriverLocationPingRequest(
                        latitude = lat,
                        longitude = lng,
                        heading = heading,
                        speedMps = speedMps,
                        accuracyM = accuracyM,
                        isMockLocation = isMockLocation,
                        locationSource = locationSource
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

    suspend fun fetchRoadRestrictions(lat: Double? = null, lng: Double? = null): Result<List<RoadRestrictionDto>> {
        return try {
            val liveLoc = _driverLocation.value
            val hasValidLive = NativeLocationEvaluator.isValidCoordinate(liveLoc.latitude, liveLoc.longitude)
            val resolvedLat = lat?.takeIf { lng != null && NativeLocationEvaluator.isValidCoordinate(it, lng) }
                ?: if (hasValidLive) liveLoc.latitude else null
            val resolvedLng = lng?.takeIf { lat != null && NativeLocationEvaluator.isValidCoordinate(lat, it) }
                ?: if (hasValidLive) liveLoc.longitude else null
            val res = DriverNetworkClient.apiService.getRoadRestrictions(resolvedLat, resolvedLng)
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
            val res = DriverNetworkClient.apiService.rerouteRide(rideId, RerouteRideRequest(reason = reason))
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
                AddPayoutAccountRequest(
                    accountType = "BANK_ACCOUNT",
                    accountNumber = accountNo.trim(),
                    ifscCode = ifsc.trim().uppercase(),
                    routingNumber = ifsc.trim().uppercase(),
                    beneficiaryName = holderName.trim(),
                    accountHolderName = holderName.trim()
                )
            )
            if (res.isSuccessful) {
                fetchPayoutAccounts()
                Result.success(true)
            } else {
                val err = res.errorBody()?.string() ?: "HTTP ${res.code()}"
                Result.failure(IllegalStateException(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addPayoutUpiAccount(upiVpa: String, holderName: String): Result<Boolean> {
        val cleanVpa = upiVpa.trim()
        val cleanHolder = holderName.trim()
        if (cleanVpa.isBlank() || cleanHolder.isBlank()) {
            return Result.failure(IllegalArgumentException("UPI ID and beneficiary name are required."))
        }
        return try {
            val res = DriverNetworkClient.apiService.addPayoutAccount(
                AddPayoutAccountRequest(
                    accountType = "UPI",
                    upiVpa = cleanVpa,
                    beneficiaryName = cleanHolder,
                    accountHolderName = cleanHolder
                )
            )
            if (res.isSuccessful) {
                fetchPayoutAccounts()
                Result.success(true)
            } else {
                val err = res.errorBody()?.string() ?: "HTTP ${res.code()}"
                Result.failure(IllegalStateException(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reportRouteDeviation(
        rideId: String,
        lat: Double,
        lng: Double,
        reason: String? = null,
        comments: String? = null
    ): Result<Boolean> {
        if (rideId.isBlank()) return Result.failure(IllegalStateException("No active ride"))
        return try {
            val res = DriverNetworkClient.apiService.reportRouteAnomaly(
                RouteAnomalyRequest(
                    rideId = rideId,
                    currentLat = lat,
                    currentLng = lng,
                    deviationReason = reason,
                    comments = comments
                )
            )
            if (res.isSuccessful) Result.success(true)
            else Result.failure(IllegalStateException("reportRouteAnomaly failed: HTTP ${res.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitRideToll(
        rideId: String,
        amountRupees: Double,
        tollName: String,
        lat: Double? = null,
        lng: Double? = null
    ): Result<Map<String, Any>> {
        if (rideId.isBlank()) return Result.failure(IllegalStateException("No active ride"))
        if (amountRupees <= 0.0) return Result.failure(IllegalArgumentException("Toll amount must be greater than zero"))
        return try {
            val req = SubmitTollRequest(
                tollAmountRupees = amountRupees,
                tollName = tollName,
                latitude = lat,
                longitude = lng
            )
            val res = DriverNetworkClient.apiService.submitRideToll(rideId, req)
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!)
            } else {
                Result.failure(IllegalStateException("Submit toll failed: HTTP ${res.code()} ${res.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateDynamicUpi(rideId: String, amountRupees: Double): Result<String> {
        if (rideId.isBlank()) return Result.failure(IllegalStateException("No active ride"))
        return try {
            val res = DriverNetworkClient.apiService.generateUpiCheckout(
                GenerateUpiRequest(rideId = rideId, amountCents = (amountRupees * 100).toInt())
            )
            val body = res.body()
            val url = (
                body?.get("upi_intent_uri")
                    ?: body?.get("upi_intent_url")
                    ?: body?.get("qr_payload")
                    ?: body?.get("qr_code_payload")
            ) as? String
            if (res.isSuccessful && !url.isNullOrBlank()) Result.success(url)
            else Result.failure(IllegalStateException("generateDynamicUpi failed: HTTP ${res.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyRideUpiPaymentStatus(rideId: String): Result<Boolean> {
        if (rideId.isBlank()) return Result.failure(IllegalStateException("No active ride"))
        return try {
            val res = DriverNetworkClient.apiService.getRidePaymentStatus(rideId)
            val body = res.body()
            if (res.isSuccessful && body != null) {
                val gatewayVerified = body["gateway_verified"] as? Boolean ?: false
                val statusStr = (body["status"] as? String ?: "UNPAID").uppercase()
                val settled = gatewayVerified || statusStr in setOf("CAPTURED", "PAID", "SETTLED", "PARTIALLY_REFUNDED", "REFUNDED")
                Result.success(settled)
            } else {
                Result.failure(IllegalStateException("Payment status check failed: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reportFoundItem(rideId: String, category: String, notes: String): Result<Boolean> {
        return try {
            val cleanRide = rideId.trim()
            val req = SafetyIncidentRequest(
                rideId = if (cleanRide.isNotBlank() && cleanRide != "general" && cleanRide.length > 10) cleanRide else null,
                category = "FOUND_LOST_ITEM",
                severity = "LOW",
                description = "Found item in cab ($category): $notes"
            )
            val res = DriverNetworkClient.apiService.submitDriverSafetyIncident(req)
            if (res.isSuccessful) {
                fetchDriverIncidents()
                Result.success(true)
            } else {
                val err = res.errorBody()?.string() ?: "HTTP ${res.code()}"
                Log.w(TAG, "reportFoundItem failed: HTTP ${res.code()} - $err")
                Result.failure(IllegalStateException(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun startOrGetDraftInspection(vehicleId: String? = null): Result<InspectionDraftDto> {
        return try {
            val targetVehicleId = vehicleId?.ifBlank { null } ?: _assignedVehicle.value?.vehicleId?.ifBlank { null }
            val res = DriverNetworkClient.apiService.startVehicleInspection(StartInspectionRequest(vehicleId = targetVehicleId))
            val body = res.body()
            if (res.isSuccessful && body != null) {
                Result.success(body)
            } else {
                val err = res.errorBody()?.string() ?: "HTTP ${res.code()}"
                Result.failure(IllegalStateException(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchCurrentInspection(): Result<InspectionDraftDto> {
        return try {
            val res = DriverNetworkClient.apiService.getCurrentVehicleInspection()
            val body = res.body()
            if (res.isSuccessful && body != null) {
                Result.success(body)
            } else {
                val err = res.errorBody()?.string() ?: "HTTP ${res.code()}"
                Result.failure(IllegalStateException(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadInspectionPhoto(
        inspectionId: String,
        category: String,
        imageBytes: ByteArray,
        filename: String = "inspection_photo.jpg"
    ): Result<InspectionPhotoUploadResponseDto> {
        return try {
            val mediaType = "image/jpeg".toMediaTypeOrNull()
            val requestFile = imageBytes.toRequestBody(mediaType)
            val filePart = MultipartBody.Part.createFormData("file", filename, requestFile)
            val catBody = category.toRequestBody("text/plain".toMediaTypeOrNull())

            val res = DriverNetworkClient.apiService.uploadVehicleInspectionPhoto(
                inspectionId = inspectionId,
                category = catBody,
                file = filePart
            )
            val body = res.body()
            if (res.isSuccessful && body != null) {
                Result.success(body)
            } else {
                val err = res.errorBody()?.string() ?: "HTTP ${res.code()}"
                Result.failure(IllegalStateException(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitFullVehicleInspection(
        inspectionId: String,
        odometerKm: Double,
        checklist: Map<String, Any>,
        remarks: String? = null
    ): Result<Map<String, Any>> {
        return try {
            val res = DriverNetworkClient.apiService.submitFullVehicleInspection(
                inspectionId = inspectionId,
                body = SubmitInspectionRequest(
                    odometerReading = odometerKm,
                    checklistResults = checklist,
                    driverRemarks = remarks
                )
            )
            val body = res.body()
            if (res.isSuccessful && body != null) {
                Result.success(body)
            } else {
                val err = res.errorBody()?.string() ?: "HTTP ${res.code()}"
                Result.failure(IllegalStateException(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitVehicleInspectionCheck(
        vehicleId: String,
        tyrePressureOk: Boolean,
        lightsOk: Boolean,
        brakeOk: Boolean,
        notes: String? = null
    ): Result<Boolean> {
        val targetVehicleId = vehicleId.ifBlank {
            _assignedVehicle.value?.vehicleId ?: _profile.value.assignedVehiclePlate
        }
        return try {
            val res = DriverNetworkClient.apiService.submitPreShiftInspection(
                PreShiftInspectionRequest(
                    vehicleId = targetVehicleId.ifBlank { null },
                    tyrePressureOk = tyrePressureOk,
                    lightsOk = lightsOk,
                    brakeBuzzerOk = brakeOk,
                    notes = notes ?: "Pre-shift inspection signed off"
                )
            )
            if (res.isSuccessful) {
                Result.success(true)
            } else {
                val err = res.errorBody()?.string() ?: "HTTP ${res.code()}"
                Result.failure(IllegalStateException(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchLatestPreShiftInspection(): Result<Map<String, Any>> {
        return try {
            val res = DriverNetworkClient.apiService.getLatestPreShiftInspection()
            val body = res.body()
            if (res.isSuccessful && body != null) {
                Result.success(body)
            } else {
                Result.failure(IllegalStateException("getLatestPreShiftInspection failed: HTTP ${res.code()}"))
            }
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
            val liveLoc = _driverLocation.value
            val ride = _activeRide.value
            val pingCoords = when {
                NativeLocationEvaluator.isValidCoordinate(liveLoc.latitude, liveLoc.longitude) ->
                    liveLoc.latitude to liveLoc.longitude
                ride?.pickupLatitude != null && ride.pickupLongitude != null &&
                    NativeLocationEvaluator.isValidCoordinate(ride.pickupLatitude, ride.pickupLongitude) ->
                    ride.pickupLatitude to ride.pickupLongitude
                else -> null
            }
            if (pingCoords == null) {
                return Result.failure(IllegalStateException("Live GPS location unavailable; cannot dispatch coordinate SOS ping"))
            }
            val pingRes = DriverNetworkClient.apiService.sendEmergencyPing(
                EmergencyPingRequest(latitude = pingCoords.first, longitude = pingCoords.second, note = notes)
            )
            if (pingRes.isSuccessful) Result.success("Emergency SOS ping dispatched!")
            else Result.failure(IllegalStateException("SOS failed: HTTP ${pingRes.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchDriverIncidents(): Result<List<DriverIncidentDto>> {
        return try {
            val res = DriverNetworkClient.apiService.getDriverIncidents()
            val body = res.body()
            if (res.isSuccessful && body != null) {
                _driverIncidents.value = body
                Result.success(body)
            } else {
                Result.failure(IllegalStateException("getDriverIncidents failed: HTTP ${res.code()}"))
            }
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
            val cleanRide = rideId?.trim()
            val validRideId = if (!cleanRide.isNullOrBlank() && cleanRide != "general" && cleanRide.length > 10) cleanRide else null
            val res = DriverNetworkClient.apiService.submitDriverSafetyIncident(
                SafetyIncidentRequest(
                    rideId = validRideId,
                    category = category,
                    severity = severity,
                    description = description.trim()
                )
            )
            if (res.isSuccessful) {
                val ticket = res.body()?.get("ticket_number") as? String ?: "Submitted"
                fetchDriverIncidents()
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
        if (contactId.isBlank()) {
            return Result.failure(IllegalArgumentException("Contact ID cannot be blank"))
        }
        return try {
            val res = DriverNetworkClient.apiService.deleteEmergencyContact(contactId)
            if (res.isSuccessful) {
                _emergencyContacts.value = _emergencyContacts.value.filter { it.id != contactId }
                fetchEmergencyContacts()
                Result.success(true)
            } else {
                val err = res.errorBody()?.string() ?: "HTTP ${res.code()}"
                Log.w(TAG, "deleteEmergencyContact failed: HTTP ${res.code()} - $err")
                Result.failure(IllegalStateException(err))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception calling deleteEmergencyContact: ${e.message}")
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
                val res = DriverNetworkClient.apiService.sendPoolMessage(tripId, ChatMessagePayloadRequest(message = text, messageText = text))
                if (res.isSuccessful) return Result.success(true)
            }
            if (rideId.isNotBlank()) {
                val res = DriverNetworkClient.apiService.sendRideChatMessage(rideId, ChatMessagePayloadRequest(message = text, messageText = text))
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

    suspend fun fetchDriverServiceAreaRequests(): Result<List<DriverServiceAreaRequestDto>> {
        return try {
            val response = DriverNetworkClient.apiService.getDriverServiceAreaRequests()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                Result.success(body)
            } else {
                Result.failure(IllegalStateException("Could not load service-area requests: HTTP ${response.code()}"))
            }
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun submitDriverServiceAreaRequest(
        stateName: String,
        districtName: String,
        localityName: String,
        postalCode: String?,
        latitude: Double,
        longitude: Double
    ): Result<DriverServiceAreaRequestSubmitResponse> {
        return try {
            val response = DriverNetworkClient.apiService.submitDriverServiceAreaRequest(
                CreateDriverServiceAreaRequest(
                    stateName = stateName.trim(),
                    districtName = districtName.trim(),
                    localityName = localityName.trim(),
                    postalCode = postalCode?.trim()?.takeIf { it.isNotEmpty() },
                    latitude = latitude,
                    longitude = longitude
                )
            )
            val body = response.body()
            if (response.isSuccessful && body != null) {
                Result.success(body)
            } else {
                val detail = response.errorBody()?.string()?.take(240)
                Result.failure(IllegalStateException(detail ?: "Locality request failed: HTTP ${response.code()}"))
            }
        } catch (error: Exception) {
            Result.failure(error)
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

    fun fetchPayoutHistory() {
        scope.launch {
            try {
                val res = DriverNetworkClient.apiService.getPayoutHistory()
                val body = res.body()
                if (res.isSuccessful && body != null) {
                    _payoutHistory.value = body
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchPayoutHistory error: ${e.message}")
            }
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

    suspend fun fetchDriverPreferences(): Result<DriverPreferencesState> {
        return try {
            val res = DriverNetworkClient.apiService.getDriverPreferences()
            val body = res.body()
            if (res.isSuccessful && body != null) {
                val rawNav = (body["navigation_provider"] as? String)?.trim().orEmpty()
                val safeNav = when {
                    rawNav.isBlank() || rawNav.equals("GOOGLE_MAPS", ignoreCase = true) -> "MAPBOX"
                    else -> rawNav.uppercase()
                }
                val prefs = DriverPreferencesState(
                    autoAcceptPool = (body["auto_accept_pool"] as? Boolean) ?: true,
                    highSurgeAlerts = (body["high_surge_alerts"] as? Boolean) ?: true,
                    voiceNavigation = (body["voice_guidance_enabled"] as? Boolean)
                        ?: (body["voice_navigation"] as? Boolean) ?: true,
                    preferredCorridor = (body["preferred_corridor"] as? String) ?: "Anand Vihar / CP",
                    navigationProvider = safeNav
                )
                _driverPreferences.value = prefs
                Result.success(prefs)
            } else {
                Result.failure(IllegalStateException("getDriverPreferences HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateDriverPreferences(
        autoAcceptPool: Boolean? = null,
        highSurgeAlerts: Boolean? = null,
        voiceNavigation: Boolean? = null,
        preferredCorridor: String? = null,
        navigationProvider: String? = null
    ): Result<Boolean> {
        return try {
            val current = _driverPreferences.value
            val sanitizedNav = navigationProvider?.trim()?.let { requested ->
                if (requested.isBlank() || requested.equals("GOOGLE_MAPS", ignoreCase = true)) "MAPBOX"
                else requested.uppercase()
            }
            val updated = current.copy(
                autoAcceptPool = autoAcceptPool ?: current.autoAcceptPool,
                highSurgeAlerts = highSurgeAlerts ?: current.highSurgeAlerts,
                voiceNavigation = voiceNavigation ?: current.voiceNavigation,
                preferredCorridor = preferredCorridor ?: current.preferredCorridor,
                navigationProvider = sanitizedNav ?: current.navigationProvider
            )
            _driverPreferences.value = updated

            val req = DriverPreferencesRequest(
                autoAcceptPool = autoAcceptPool,
                autoAcceptShuttle = autoAcceptPool,
                highDemandSurgeAlerts = highSurgeAlerts,
                highSurgeAlerts = highSurgeAlerts,
                voiceGuidanceEnabled = voiceNavigation,
                voiceNavigation = voiceNavigation,
                preferredCorridor = preferredCorridor,
                navigationProvider = sanitizedNav
            )

            val res = DriverNetworkClient.apiService.updateDriverPreferences(req)
            if (res.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(IllegalStateException("updateDriverPreferences HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchFinancialSupportRequests(): Result<List<FinancialSupportDto>> {
        return try {
            val res = DriverNetworkClient.apiService.getFinancialSupportRequests()
            val body = res.body()
            if (res.isSuccessful && body != null) {
                _financialSupportRequests.value = body
                Result.success(body)
            } else {
                Result.failure(IllegalStateException("getFinancialSupportRequests HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitFinancialSupportRequest(
        supportType: String,
        amountRupees: Double,
        reason: String,
        repaymentTermsWeeks: Int = 4
    ): Result<Map<String, Any>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val req = SubmitFinancialSupportRequest(
                supportType = supportType,
                amountRupees = amountRupees,
                reason = reason,
                repaymentTermsWeeks = repaymentTermsWeeks
            )
            val res = DriverNetworkClient.apiService.submitFinancialSupportRequest(req)
            val body = res.body()
            if (res.isSuccessful && body != null) {
                fetchFinancialSupportRequests()
                Result.success(body)
            } else {
                val rawErr = res.errorBody()?.string()
                val err = parseApiErrorMessage(rawErr, "Failed to submit welfare request (HTTP ${res.code()})")
                Result.failure(IllegalStateException(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchSafetyOverviewAndRatings(): Result<Map<String, Any>> {
        return try {
            val res = DriverNetworkClient.apiService.getSafetyOverview()
            val body = res.body()
            if (res.isSuccessful && body != null) {
                @Suppress("UNCHECKED_CAST")
                val ratingsRaw = body["ratings"] as? List<Map<String, Any>> ?: emptyList()
                @Suppress("UNCHECKED_CAST")
                val appealsRaw = body["rating_appeals"] as? List<Map<String, Any>> ?: emptyList()
                _ratingAppeals.value = appealsRaw

                val parsedRatings = ratingsRaw.map { r ->
                    DriverRatingItemDto(
                        id = r["id"] as? String ?: "",
                        rating = (r["rating"] as? Number)?.toFloat() ?: 5f,
                        cleanliness = (r["cleanliness"] as? Number)?.toFloat(),
                        comfort = (r["comfort"] as? Number)?.toFloat(),
                        tags = r["tags"] as? String,
                        feedback = r["feedback"] as? String,
                        createdAt = r["created_at"] as? String
                    )
                }
                _driverRatings.value = parsedRatings
                Result.success(body)
            } else {
                Result.failure(IllegalStateException("getSafetyOverview HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun appealDriverRating(ratingId: String, reason: String): Result<Map<String, Any>> {
        return try {
            val req = RatingAppealRequest(reason = reason)
            val res = DriverNetworkClient.apiService.appealDriverRating(ratingId, req)
            val body = res.body()
            if (res.isSuccessful && body != null) {
                fetchSafetyOverviewAndRatings()
                Result.success(body)
            } else {
                val err = res.errorBody()?.string() ?: "HTTP ${res.code()}"
                Result.failure(IllegalStateException(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordAudioConsent(
        rideId: String,
        granted: Boolean,
        consentText: String = "Driver voluntarily provides in-cabin safety audio recording consent under DPDP Act 2023."
    ): Result<AudioConsentResponse> {
        return try {
            val req = AudioConsentRequest(
                consented = granted,
                role = "DRIVER"
            )
            val res = DriverNetworkClient.apiService.recordAudioConsent(rideId, req)
            val body = res.body()
            if (res.isSuccessful && body != null) {
                fetchAudioConsentStatus(rideId)
                Result.success(body)
            } else {
                val err = res.errorBody()?.string() ?: "HTTP ${res.code()}"
                Result.failure(IllegalStateException(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun withdrawAudioConsent(rideId: String, reason: String = "Revoked by pilot"): Result<Boolean> {
        return try {
            val req = AudioConsentWithdrawRequest(reason = reason)
            val res = DriverNetworkClient.apiService.withdrawAudioConsent(rideId, req)
            if (res.isSuccessful) {
                fetchAudioConsentStatus(rideId)
                Result.success(true)
            } else {
                Result.failure(IllegalStateException("HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchAudioConsentStatus(rideId: String): Result<AudioRecordingStatusResponse> {
        return try {
            val res = DriverNetworkClient.apiService.getAudioConsentStatus(rideId)
            val body = res.body()
            if (res.isSuccessful && body != null) {
                _audioRecordingStatus.value = body
                Result.success(body)
            } else {
                Result.failure(IllegalStateException("HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun authorizeAudioRecording(rideId: String): Result<Map<String, Any>> {
        return try {
            val res = DriverNetworkClient.apiService.authorizeAudioRecording(rideId)
            val body = res.body()
            if (res.isSuccessful && body != null) {
                fetchAudioConsentStatus(rideId)
                Result.success(body)
            } else {
                val err = res.errorBody()?.string() ?: "HTTP ${res.code()}"
                Result.failure(IllegalStateException(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkGiftCardBalance(code: String, pin: String? = null): Result<DriverGiftCardBalanceResponse> {
        return try {
            val res = DriverNetworkClient.apiService.checkGiftCardBalance(
                DriverGiftCardBalanceRequest(code = code.trim().uppercase(), pin = pin?.trim()?.takeIf { it.isNotBlank() })
            )
            val body = res.body()
            if (res.isSuccessful && body != null) {
                Result.success(body)
            } else {
                val err = res.errorBody()?.string() ?: "HTTP ${res.code()}"
                Result.failure(IllegalStateException(parseApiErrorMessage(err, "Invalid or expired gift card code")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class DriverPreferencesState(
    val autoAcceptPool: Boolean = true,
    val highSurgeAlerts: Boolean = true,
    val voiceNavigation: Boolean = true,
    val preferredCorridor: String = "Anand Vihar / CP",
    val navigationProvider: String = "MAPBOX"
)