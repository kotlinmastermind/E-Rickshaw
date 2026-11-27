package com.vaisentra.mobility.rider.data

import android.graphics.Bitmap
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
object RiderRepository {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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

    private val _categories = MutableStateFlow<List<VehicleCategoryDto>>(emptyList())
    val categories: StateFlow<List<VehicleCategoryDto>> = _categories.asStateFlow()

    private val _activeRide = MutableStateFlow<ActiveRideDto?>(null)
    val activeRide: StateFlow<ActiveRideDto?> = _activeRide.asStateFlow()

    private val _rideHistory = MutableStateFlow<List<ActiveRideDto>>(emptyList())
    val rideHistory: StateFlow<List<ActiveRideDto>> = _rideHistory.asStateFlow()

    private val _wallet = MutableStateFlow(
        WalletBalanceDto(
            walletId = "",
            balanceCents = 0,
            balanceRupees = 0.0,
            autoTopupEnabled = false,
            autoTopupThreshold = 0.0,
            autoTopupAmount = 0.0
        )
    )
    val wallet: StateFlow<WalletBalanceDto> = _wallet.asStateFlow()
    private val _walletLoaded = MutableStateFlow(false)
    val walletLoaded: StateFlow<Boolean> = _walletLoaded.asStateFlow()
    private val _walletError = MutableStateFlow<String?>(null)
    val walletError: StateFlow<String?> = _walletError.asStateFlow()

    private val _loyalty = MutableStateFlow(LoyaltySummaryDto())
    val loyalty: StateFlow<LoyaltySummaryDto> = _loyalty.asStateFlow()

    // 1. Dummy list hata kar emptyList() rakhein taaki asli Supabase data hi dikhe
    private val _emergencyContacts = MutableStateFlow<List<EmergencyContactDto>>(emptyList())
    val emergencyContacts: StateFlow<List<EmergencyContactDto>> = _emergencyContacts.asStateFlow()
    private val _savedPlaces = MutableStateFlow<List<SavedPlaceDto>>(emptyList())
    val savedPlaces: StateFlow<List<SavedPlaceDto>> = _savedPlaces.asStateFlow()

    private val _notifications = MutableStateFlow<List<NotificationItemDto>>(emptyList())
    val notifications: StateFlow<List<NotificationItemDto>> = _notifications.asStateFlow()

    private val _supportTickets = MutableStateFlow<List<SupportTicketDto>>(emptyList())
    val supportTickets: StateFlow<List<SupportTicketDto>> = _supportTickets.asStateFlow()

    private val _rideReceipt = MutableStateFlow<RideReceiptDto?>(null)
    val rideReceipt: StateFlow<RideReceiptDto?> = _rideReceipt.asStateFlow()

    private val _nearbyVehicles = MutableStateFlow<List<NearbyVehicleDto>>(emptyList())
    val nearbyVehicles: StateFlow<List<NearbyVehicleDto>> = _nearbyVehicles.asStateFlow()

    private val _bookingDraft = MutableStateFlow(BookingDraftState())
    val bookingDraft: StateFlow<BookingDraftState> = _bookingDraft.asStateFlow()

    private val _fareRules = MutableStateFlow<List<FareRuleDto>>(emptyList())
    val fareRules: StateFlow<List<FareRuleDto>> = _fareRules.asStateFlow()

    private val _customerPreferences = MutableStateFlow<Map<String, Any>>(emptyMap())
    val customerPreferences: StateFlow<Map<String, Any>> = _customerPreferences.asStateFlow()

    private val _corporateAccount = MutableStateFlow<CorporateAccountDto?>(null)
    val corporateAccount: StateFlow<CorporateAccountDto?> = _corporateAccount.asStateFlow()

    private val _audioRecordingStatus = MutableStateFlow<AudioRecordingStatusResponse?>(null)
    val audioRecordingStatus: StateFlow<AudioRecordingStatusResponse?> = _audioRecordingStatus.asStateFlow()

    private val _isAudioRecordingActive = MutableStateFlow(false)
    val isAudioRecordingActive: StateFlow<Boolean> = _isAudioRecordingActive.asStateFlow()

    private val _giftCardConfig = MutableStateFlow<GiftCardConfigDto?>(null)
    val giftCardConfig: StateFlow<GiftCardConfigDto?> = _giftCardConfig.asStateFlow()

    private val _myGiftCards = MutableStateFlow<List<GiftCardSummaryDto>>(emptyList())
    val myGiftCards: StateFlow<List<GiftCardSummaryDto>> = _myGiftCards.asStateFlow()

    suspend fun fetchFareRules() {
        try {
            val res = NetworkClient.apiService.getFareRules()
            if (res.isSuccessful && res.body() != null) {
                _fareRules.value = res.body()!!.filter { it.isActive }
            }
        } catch (_: Exception) {
        }
    }
    fun updatePickupLocation(lat: Double, lng: Double, address: String? = null) {
        _bookingDraft.value = _bookingDraft.value.copy(
            pickupLat = lat,
            pickupLng = lng,
            pickupAddress = address ?: _bookingDraft.value.pickupAddress
        )
    }

    fun updateDestination(address: String, lat: Double, lng: Double) {
        _bookingDraft.value = _bookingDraft.value.copy(
            destinationAddress = address,
            destinationLat = lat,
            destinationLng = lng
        )
    }

    fun selectVehicleCategory(code: String) {
        _bookingDraft.value = _bookingDraft.value.copy(selectedCategoryCode = code)
    }

    suspend fun syncLiveLocationAndVehicles(lat: Double, lng: Double) {
        if (!NativeLocationEvaluator.isValidCoordinate(lat, lng)) return
        val existingAddr = _bookingDraft.value.pickupAddress
        val needsPlaceholderReplace = existingAddr.startsWith("Fetching") ||
            existingAddr.startsWith("Allow Location") ||
            existingAddr.startsWith("Turn ON") ||
            existingAddr.startsWith("Location service")
        val initialAddr = if (needsPlaceholderReplace) {
            String.format(java.util.Locale.US, "%.4f, %.4f", lat, lng)
        } else {
            null
        }
        updatePickupLocation(lat, lng, initialAddr)
        try {
            val geoRes = NetworkClient.apiService.reverseGeocode(ReverseGeocodeRequest(lat, lng))
            if (geoRes.isSuccessful && geoRes.body() != null) {
                updatePickupLocation(lat, lng, geoRes.body()!!.formattedAddress)
            }
        } catch (_: Exception) { }

        try {
            val vehRes = NetworkClient.apiService.getNearbyVehicles(lat, lng)
            if (vehRes.isSuccessful && vehRes.body() != null) {
                _nearbyVehicles.value = vehRes.body()!!.vehicles
            }
        } catch (_: Exception) { }
    }

    suspend fun requestOtp(phone: String): Result<Boolean> {
        return try {
            val res = NetworkClient.apiService.sendOtp(OtpSendRequest(phone))
            Result.success(res.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyOtp(
        phone: String,
        otp: String,
        role: String = "customer"
    ): Result<TokenResponse?> {
        return try {
            val request = OtpVerifyRequest(
                phoneNumber = phone,
                otpCode = otp,
                role = role
            )

            val response = NetworkClient.apiService.verifyOtp(request)

            if (!response.isSuccessful) {
                val errorMessage = response.errorBody()?.string()
                    ?: "OTP verification failed: HTTP ${response.code()}"

                return Result.failure(Exception(errorMessage))
            }

            val authResponse = response.body()
                ?: return Result.failure(
                    Exception("Authentication response is empty")
                )

            val token = authResponse.accessToken.trim()

            if (token.isBlank()) {
                return Result.failure(
                    Exception("Backend returned an empty access token")
                )
            }

            // Store access and refresh tokens in NetworkClient memory and prefs.
            NetworkClient.setAuthToken(token, authResponse.refreshToken?.trim())
            com.vaisentra.mobility.rider.fcm.RiderFcmManager.syncTokenIfAuthenticated(force = true)

            Result.success(authResponse)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchCurrentUserMe(): UserMeResponse? {
        return try {
            val res = NetworkClient.apiService.getCurrentUserMe()
            if (res.isSuccessful) res.body() else null
        } catch (e: Exception) {
            null
        }
    }
    suspend fun cancelActiveRide(rideId: String, reason: String = "USER_CANCELLED"): Result<Boolean> {
        return try {
            val res = NetworkClient.apiService.cancelRide(rideId, mapOf("reason" to reason))
            if (res.isSuccessful) {
                updateActiveRideStatus("CANCELLED")
                Result.success(true)
            } else Result.failure(Exception("Failed to cancel"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchRideReceipt(rideId: String): Result<RideReceiptDto?> {
        return try {
            val res = NetworkClient.apiService.getRideReceipt(rideId)
            if (res.isSuccessful && res.body() != null) {
                _rideReceipt.value = res.body()
                Result.success(res.body())
            } else Result.success(_rideReceipt.value)
        } catch (e: Exception) {
            Result.success(_rideReceipt.value)
        }
    }

    suspend fun triggerSos(lat: Double, lng: Double, rideId: String? = null): Result<Boolean> {
        return try {
            val targetRide = rideId ?: _activeRide.value?.id
            val res = NetworkClient.apiService.triggerSos(SosTriggerRequest(targetRide, lat, lng))
            Result.success(res.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun initiateSplitFare(rideId: String, coPassengerPhones: List<String>): Result<Map<String, Any>> {
        return try {
            val req = SplitFareRequest(
                rideId = rideId,
                coPassengerPhones = coPassengerPhones
            )
            val res = NetworkClient.apiService.splitFare(req)
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!)
            } else {
                Result.failure(IllegalStateException("Split fare failed: ${res.code()} ${res.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchSupportTickets(): Result<List<SupportTicketDto>> {
        return try {
            val res = NetworkClient.apiService.getSupportTickets()
            if (res.isSuccessful && res.body() != null) {
                _supportTickets.value = res.body()!!
                Result.success(res.body()!!)
            } else Result.success(_supportTickets.value)
        } catch (e: Exception) {
            Result.success(_supportTickets.value)
        }
    }

    suspend fun fetchLoyaltySummary(): Result<LoyaltySummaryDto> {
        return try {
            val res = NetworkClient.apiService.getLoyaltySummary()
            if (res.isSuccessful && res.body() != null) {
                _loyalty.value = res.body()!!
                Result.success(res.body()!!)
            } else Result.success(_loyalty.value)
        } catch (e: Exception) {
            Result.success(_loyalty.value)
        }
    }

    suspend fun fetchNotifications(): Result<List<NotificationItemDto>> {
        return try {
            val res = NetworkClient.apiService.getNotifications()
            if (res.isSuccessful && res.body() != null) {
                _notifications.value = res.body()!!
                Result.success(res.body()!!)
            } else Result.success(_notifications.value)
        } catch (e: Exception) {
            Result.success(_notifications.value)
        }
    }


    suspend fun fetchSavedPlaces(): Result<List<SavedPlaceDto>> {
        return try {
            val res = NetworkClient.apiService.getSavedPlaces()
            if (res.isSuccessful && res.body() != null) {
                _savedPlaces.value = res.body()!!
                Result.success(res.body()!!)
            } else {
                _savedPlaces.value = emptyList()
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            _savedPlaces.value = emptyList()
            Result.failure(e)
        }
    }

    suspend fun addSavedPlace(name: String, address: String, lat: Double, lng: Double, tag: String = "FAVORITE"): Result<SavedPlaceDto> {
        return try {
            val payload = mapOf(
                "label" to name.trim(),
                "address" to address.trim(),
                "latitude" to lat,
                "longitude" to lng
            )
            val res = NetworkClient.apiService.addSavedPlace(payload)
            if (res.isSuccessful && res.body() != null) {
                fetchSavedPlaces()
                val created = SavedPlaceDto(
                    id = res.body()?.get("id")?.toString() ?: "",
                    name = name,
                    address = address,
                    latitude = lat,
                    longitude = lng,
                    tag = tag
                )
                Result.success(created)
            } else {
                val err = parseApiErrorMessage(res.errorBody()?.string(), "Failed to save place")
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteSavedPlace(placeId: String): Result<Boolean> {
        return try {
            val res = NetworkClient.apiService.deleteSavedPlace(placeId)
            if (res.isSuccessful) {
                _savedPlaces.value = _savedPlaces.value.filterNot { it.id == placeId }
                Result.success(true)
            } else {
                Result.failure(Exception("Failed to delete place: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resolveDestinationCoordinates(query: String, fallbackLat: Double, fallbackLng: Double): MapSearchResult {
        val cleanQuery = query.trim()
        val directResults = searchMapboxPlaces(cleanQuery)
        if (directResults.isNotEmpty()) {
            return directResults.first()
        }

        // Secondary Geocoding Provider (OpenStreetMap Nominatim for Indian Landmarks)
        return try {
            val encoded = java.net.URLEncoder.encode(cleanQuery, "UTF-8")
            val urlStr = "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=1&countrycodes=in"
            val conn = java.net.URL(urlStr).openConnection() as java.net.HttpURLConnection
            conn.setRequestProperty("User-Agent", "RiderGo-App/1.0")
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val arr = org.json.JSONArray(text)
            if (arr.length() > 0) {
                val first = arr.getJSONObject(0)
                MapSearchResult(
                    name = cleanQuery,
                    full_address = first.optString("display_name", cleanQuery),
                    latitude = first.getDouble("lat"),
                    longitude = first.getDouble("lon")
                )
            } else {
                MapSearchResult(cleanQuery, cleanQuery, fallbackLat, fallbackLng)
            }
        } catch (_: Exception) {
            MapSearchResult(cleanQuery, cleanQuery, fallbackLat, fallbackLng)
        }
    }

    suspend fun fetchSharedPoolEstimate(categoryId: String, seats: Int = 1): Result<SharedEstimateResponseDto> {
        return try {
            val draft = _bookingDraft.value
            val req = SharedEstimateRequestDto(
                categoryId = categoryId,
                pickupLatitude = draft.pickupLat,
                pickupLongitude = draft.pickupLng,
                destinationLatitude = draft.destinationLat,
                destinationLongitude = draft.destinationLng,
                seatsRequested = seats
            )
            val res = NetworkClient.apiService.getSharedPoolEstimate(req)
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!)
            } else {
                Result.failure(Exception("Pool estimation failed: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }





    suspend fun bookSharedPool(req: PoolBookingRequest): Result<PoolTripDto?> {
        return try {
            val res = NetworkClient.apiService.bookPool(req)
            if (res.isSuccessful) Result.success(res.body()) else Result.failure(Exception("Failed to book pool"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchVehicleCategories(): Result<List<VehicleCategoryDto>> {
        return try {
            val response = NetworkClient.apiService.getVehicleCategories()
            if (response.isSuccessful && response.body() != null) {
                val list = response.body()!!
                _categories.value = list
                Result.success(list)
            } else {
                val raw = response.errorBody()?.string()
                val err = parseApiErrorMessage(raw, "Failed to load vehicle categories: HTTP ${response.code()}")
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    sealed class FareCompareException(message: String, cause: Throwable? = null) : Exception(message, cause) {
        class NetworkError(message: String, cause: Throwable? = null) : FareCompareException(message, cause)
        class MissingFareConfiguration(message: String) : FareCompareException(message)
        class HttpError(val code: Int, message: String) : FareCompareException(message)
    }

    suspend fun compareFares(
        pickupLat: Double, pickupLng: Double,
        destLat: Double, destLng: Double
    ): Result<List<VehicleFareComparisonDto>> {
        return try {
            val request = FareCompareRequest(pickupLat, pickupLng, destLat, destLng)
            val response = NetworkClient.apiService.compareVehicleFares(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val code = response.code()
                val raw = response.errorBody()?.string()
                val err = parseApiErrorMessage(raw, "Fare calculation unavailable: HTTP $code")
                if (code == 404) {
                    Result.failure(FareCompareException.MissingFareConfiguration(err))
                } else {
                    Result.failure(FareCompareException.HttpError(code, err))
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (e is java.io.IOException) {
                Result.failure(FareCompareException.NetworkError("Network connection error. Please check your internet connection.", e))
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun createRideBooking(
        pickupAddress: String,
        destinationAddress: String,
        categoryId: String,
        estimateId: String? = null,
        paymentMethod: String = "UPI",
        bookingMode: String = "STANDARD",
        businessCostCenter: String? = null,
        pickupLat: Double,
        pickupLng: Double,
        destLat: Double,
        destLng: Double
    ): Result<ActiveRideDto> {
        return try {
            val request = RideBookingRequest(
                estimateId = estimateId,
                categoryId = categoryId,
                pickupAddress = pickupAddress,
                destinationAddress = destinationAddress,
                pickupLatitude = pickupLat,
                pickupLongitude = pickupLng,
                destinationLatitude = destLat,
                destinationLongitude = destLng,
                paymentMethod = paymentMethod,
                bookingMode = bookingMode,
                businessCostCenter = businessCostCenter
            )
            val response = NetworkClient.apiService.requestRide(request)
            if (response.isSuccessful && response.body() != null) {
                val ride = response.body()!!
                _activeRide.value = ride
                RiderWebSocketClient.connectRideTracking(ride.id)
                startActiveRideSyncLoop(ride.id)
                Result.success(ride)
            } else {
                val raw = response.errorBody()?.string()
                val errBody = parseApiErrorMessage(raw, "Booking failed: HTTP ${response.code()}")
                Result.failure(Exception(errBody))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun startActiveRideSyncLoop(rideId: String) {
        scope.launch {
            while (_activeRide.value != null &&
                _activeRide.value?.status !in listOf("COMPLETED", "CANCELLED")
            ) {
                kotlinx.coroutines.delay(4000L)
                try {
                    val res = NetworkClient.apiService.getActiveRide()
                    val updated = res.body()?.activeRide
                    if (res.isSuccessful && updated != null && updated.id == rideId) {
                        // Nested driver object se name/phone/rating top-level par copy karein
                        _activeRide.value = updated.copy(
                            driverName = updated.driver?.name ?: updated.driverName,
                            driverPhone = updated.driver?.phone ?: updated.driverPhone,
                            driverRating = updated.driver?.rating ?: updated.driverRating,
                            vehiclePlate = updated.driver?.licenseNumber ?: updated.vehiclePlate
                        )
                    } else if (res.isSuccessful && (updated == null || updated.status in listOf("COMPLETED", "CANCELLED"))) {
                        _activeRide.value = null
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    suspend fun fetchActiveRide(): Result<ActiveRideDto?> {
        return try {
            val res = NetworkClient.apiService.getActiveRide()
            if (res.isSuccessful) {
                val ride = res.body()?.activeRide
                if (ride != null && ride.status !in listOf("COMPLETED", "CANCELLED")) {
                    val resolved = ride.copy(
                        driverName = ride.driver?.name ?: ride.driverName,
                        driverPhone = ride.driver?.phone ?: ride.driverPhone,
                        driverRating = ride.driver?.rating ?: ride.driverRating,
                        vehiclePlate = ride.driver?.licenseNumber ?: ride.vehiclePlate
                    )
                    _activeRide.value = resolved
                    startActiveRideSyncLoop(ride.id)
                    Result.success(resolved)
                } else {
                    _activeRide.value = null
                    Result.success(null)
                }
            } else {
                _activeRide.value = null
                Result.success(null)
            }
        } catch (e: Exception) {
            _activeRide.value = null
            Result.failure(e)
        }
    }

    fun updateActiveRideStatus(newStatus: String) {
        _activeRide.value = _activeRide.value?.copy(status = newStatus)
        if (newStatus == "COMPLETED" || newStatus == "CANCELLED") {
            RiderWebSocketClient.disconnect()
        }
    }

    fun updateDriverLiveLocation(lat: Double, lng: Double, speed: Double) {
        _activeRide.value = _activeRide.value?.copy(
            driverLat = lat,
            driverLng = lng,
            speedKmh = speed
        )
    }

    suspend fun fetchWalletBalance(): Result<WalletBalanceDto> {
        return try {
            val response = NetworkClient.apiService.getWalletBalance()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                val synced = body.copy(balanceRupees = body.balanceCents / 100.0)
                _wallet.value = synced
                _walletLoaded.value = true
                _walletError.value = null
                Result.success(synced)
            } else {
                val message = parseApiErrorMessage(
                    response.errorBody()?.string(),
                    "Wallet balance could not be verified (HTTP ${response.code()})."
                )
                _walletError.value = message
                Result.failure(IllegalStateException(message))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.message ?: "Wallet balance could not be verified."
            _walletError.value = message
            Result.failure(e)
        }
    }

    fun topupWalletBalance(amount: Double) {
        // Direct unverified wallet mutation is deprecated and rejected by backend.
        // Direct callers should invoke launchWalletTopupCheckout(activity, amount) instead.
        android.util.Log.w("RiderRepository", "topupWalletBalance is deprecated: Please use launchWalletTopupCheckout to initiate verified Razorpay payment.")
        scope.launch {
            try {
                fetchWalletBalance()
            } catch (_: Exception) {}
        }
    }

    suspend fun fetchEmergencyContacts(): Result<List<EmergencyContactDto>> {
        return try {
            val response = NetworkClient.apiService.getEmergencyContacts()
            if (response.isSuccessful && response.body() != null) {
                val contacts = response.body()!!
                _emergencyContacts.value = contacts
                Result.success(contacts)
            } else {
                val err = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addEmergencyContact(
        name: String,
        phoneNumber: String,
        relationship: String = "Family"
    ): Result<EmergencyContactDto> {
        return try {
            val payload = mapOf(
                "name" to name.trim(),
                "phone_number" to phoneNumber.trim(),
                "relationship" to relationship.trim()
            )
            val res = NetworkClient.apiService.addEmergencyContact(payload)
            if (res.isSuccessful && res.body() != null) {
                fetchEmergencyContacts() // Supabase se fresh list fetch karein
                Result.success(res.body()!!)
            } else {
                val err = res.errorBody()?.string() ?: "HTTP ${res.code()}"
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeEmergencyContact(contactId: String): Result<Boolean> {
        return try {
            val res = NetworkClient.apiService.deleteEmergencyContact(contactId)
            if (res.isSuccessful) {
                _emergencyContacts.value = _emergencyContacts.value.filter { it.id != contactId }
                Result.success(true)
            } else {
                val err = res.errorBody()?.string() ?: "HTTP ${res.code()}"
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun fetchRideHistory(): Result<List<ActiveRideDto>> {
        return try {
            val response = NetworkClient.apiService.getRideHistory()
            if (response.isSuccessful && response.body() != null) {
                val list = response.body()!!
                _rideHistory.value = list
                Result.success(list)
            } else {
                Result.success(_rideHistory.value)
            }
        } catch (e: Exception) {
            Result.success(_rideHistory.value)
        }
    }

    // Default Supabase synced vehicle categories
    private fun defaultVehicleCategories(): List<VehicleCategoryDto> = listOf(
        VehicleCategoryDto(
            id = "12b8f8bd-2d61-4105-a0d8-5661fe822254",
            code = "E_RICKSHAW_STANDARD",
            name = "Standard E-Rickshaw",
            description = "Eco-friendly electric 3-wheeler for quick city commutes and local metro transit.",
            capacity = 4,
            isElectric = true,
            legroomInches = 36.0,
            quietnessDb = 45.0,
            wheelchairAccessible = false,
            petFriendly = true
        ),
        VehicleCategoryDto(
            id = "9b1c4e58-78b3-4a0b-80bb-f625f2d6ea2a",
            code = "E_RICKSHAW_SHARE",
            name = "Shared E-Rickshaw (Eco-Pool)",
            description = "Ultra-affordable per-seat shared electric rickshaw on metro and market feeder routes.",
            capacity = 4,
            isElectric = true,
            legroomInches = 35.0,
            quietnessDb = 45.0,
            wheelchairAccessible = false,
            petFriendly = false
        ),
        VehicleCategoryDto(
            id = "0daec8ce-7e8a-4603-ab3f-6bed9455ccd7",
            code = "E_AUTO",
            name = "High-Speed Electric Auto",
            description = "Modern enclosed L5M electric auto rickshaw with weather-shielding doors and higher speed.",
            capacity = 3,
            isElectric = true,
            legroomInches = 38.0,
            quietnessDb = 44.0,
            wheelchairAccessible = true,
            petFriendly = true
        ),
        VehicleCategoryDto(
            id = "653d62fb-c68e-4799-9d55-411ccc9b5e01",
            code = "E_CARGO_LOADER",
            name = "E-Loader Cargo",
            description = "Electric commercial cargo loader 3-wheeler for parcels, goods, and luggage.",
            capacity = 1,
            isElectric = true,
            legroomInches = 34.0,
            quietnessDb = 46.0,
            wheelchairAccessible = false,
            petFriendly = false
        )
    )

    private suspend fun resolveMapboxPublicToken(): String {
        val localToken = NetworkClient.mapboxPublicToken.trim()
        if (localToken.isNotBlank()) return localToken
        return try {
            val res = NetworkClient.apiService.getMapboxConfig()
            val serverToken = (res.body()?.get("mapbox_public_token") as? String)?.trim().orEmpty()
            if (serverToken.isNotBlank()) {
                NetworkClient.mapboxPublicToken = serverToken
            }
            serverToken
        } catch (_: Exception) {
            ""
        }
    }

    // --- Mapbox Live Places & Routing ---
    suspend fun searchMapboxPlaces(query: String): List<MapSearchResult> {
        val draft = _bookingDraft.value
        val cleanQuery = query.trim()
        if (cleanQuery.length < 2) return emptyList()
        val hasPickupFix = NativeLocationEvaluator.isValidCoordinate(draft.pickupLat, draft.pickupLng)

        // 1. Pehle FastAPI backend ko current GPS proximity (if acquired) ke saath try karein
        try {
            val response = NetworkClient.apiService.searchPlacesMapbox(
                MapSearchRequest(
                    query = cleanQuery,
                    proximity_lat = if (hasPickupFix) draft.pickupLat else null,
                    proximity_lng = if (hasPickupFix) draft.pickupLng else null
                )
            )
            if (response.isSuccessful && response.body() != null && response.body()!!.results.isNotEmpty()) {
                return response.body()!!.results
            }
        } catch (_: Exception) {
        }

        // 2. Fallback: Direct Mapbox Geocoding API using configured client/backend public token
        return try {
            val token = resolveMapboxPublicToken()
            if (token.isBlank()) return emptyList()
            val encoded = java.net.URLEncoder.encode(cleanQuery, "UTF-8")
            val proximityParam = if (hasPickupFix) "&proximity=${draft.pickupLng},${draft.pickupLat}" else ""
            val urlStr = "https://api.mapbox.com/geocoding/v5/mapbox.places/$encoded.json" +
                    "?access_token=$token" +
                    proximityParam +
                    "&autocomplete=true" +
                    "&limit=8"

            val conn = java.net.URL(urlStr).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
            val root = org.json.JSONObject(jsonText)
            val features = root.optJSONArray("features") ?: return emptyList()

            val list = mutableListOf<MapSearchResult>()
            for (i in 0 until features.length()) {
                val feat = features.getJSONObject(i)
                val center = feat.getJSONArray("center")
                val lng = center.getDouble(0)
                val lat = center.getDouble(1)
                val name = feat.optString("text", cleanQuery)
                val fullAddr = feat.optString("place_name", name)
                list.add(
                    MapSearchResult(
                        name = name,
                        full_address = fullAddr,
                        latitude = lat,
                        longitude = lng
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getMapboxRoutePreview(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double
    ): MapRoutePreviewResponse? {
        return try {
            val response = NetworkClient.apiService.getRoutePreviewMapbox(
                MapRoutePreviewRequest(originLat, originLng, destLat, destLng)
            )
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            null
        }
    }

    // --- 100% Backend Connected Operations ---
    suspend fun submitRating(rideId: String, rating: Int, feedback: String): Result<Boolean> {
        return try {
            val res = NetworkClient.apiService.rateDriver(
                rideId,
                mapOf("overall_rating" to rating, "feedback" to feedback)
            )
            Result.success(res.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitTip(rideId: String, tipRupees: Int): Result<Boolean> {
        return try {
            val res = NetworkClient.apiService.tipDriver(
                rideId,
                mapOf("tip_cents" to (tipRupees * 100))
            )
            Result.success(res.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitDispute(rideId: String, reason: String, description: String): Result<Boolean> {
        return try {
            val res = NetworkClient.apiService.submitDispute(
                rideId,
                mapOf("reason_category" to reason, "explanation" to description)
            )
            Result.success(res.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reportLostProperty(rideId: String, itemType: String, description: String): Result<Boolean> {
        return try {
            val res = NetworkClient.apiService.reportLostProperty(
                rideId,
                mapOf("item_category" to itemType, "description" to description)
            )
            Result.success(res.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun scheduleRideBooking(
        pickup: String,
        dest: String,
        pickupTimeIso: String,
        pickupLat: Double? = null,
        pickupLng: Double? = null,
        destLat: Double? = null,
        destLng: Double? = null
    ): Result<Boolean> {
        return try {
            val draft = _bookingDraft.value
            val pLat = pickupLat ?: draft.pickupLat
            val pLng = pickupLng ?: draft.pickupLng
            val dLat = destLat ?: draft.destinationLat
            val dLng = destLng ?: draft.destinationLng

            if (!NativeLocationEvaluator.isValidCoordinate(pLat, pLng) ||
                !NativeLocationEvaluator.isValidCoordinate(dLat, dLng)
            ) {
                return Result.failure(
                    IllegalStateException("Valid pickup and destination coordinates are required to schedule a ride.")
                )
            }

            val res = NetworkClient.apiService.scheduleRide(
                mapOf(
                    "pickup_address" to pickup,
                    "destination_address" to dest,
                    "pickup_latitude" to pLat,
                    "pickup_longitude" to pLng,
                    "destination_latitude" to dLat,
                    "destination_longitude" to dLng,
                    "scheduled_pickup_time" to pickupTimeIso
                )
            )
            if (res.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(IllegalStateException("Schedule ride failed: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rescheduleRide(
        rideId: String,
        newPickupTimeIso: String,
        reason: String? = null,
        flightNumber: String? = null
    ): Result<Map<String, Any>> {
        return try {
            val req = RescheduleRideRequest(
                newPickupTime = newPickupTimeIso,
                reason = reason,
                flightNumber = flightNumber
            )
            val res = NetworkClient.apiService.rescheduleRide(rideId, req)
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!)
            } else {
                Result.failure(IllegalStateException("Reschedule ride failed: ${res.code()} ${res.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncFlightDelay(rideId: String, flightNumber: String): Result<Map<String, Any>> {
        return try {
            val res = NetworkClient.apiService.syncFlightDelay(rideId, flightNumber)
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!)
            } else {
                Result.failure(IllegalStateException("Flight delay sync failed: ${res.code()} ${res.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchCustomerPreferences(): Result<Map<String, Any>> {
        return try {
            val res = NetworkClient.apiService.getCustomerPreferences()
            val body = res.body()
            if (res.isSuccessful && body != null) {
                _customerPreferences.value = body
                Result.success(body)
            } else {
                Result.failure(IllegalStateException("HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun setPaymentMethod(method: String) {
        _bookingDraft.value = _bookingDraft.value.copy(paymentMethod = method)
    }

    suspend fun saveCustomerPreferences(
        tempCelsius: Float? = null,
        quietRide: Boolean? = null,
        preferredMusic: String? = null,
        wheelchair: Boolean? = null,
        petFriendly: Boolean? = null,
        extraLegroom: Boolean? = null,
        accessibilityNotes: String? = null,
        preferredPaymentMethod: String? = null
    ): Result<Boolean> {
        return try {
            val payload = mutableMapOf<String, Any>()
            tempCelsius?.let { payload["temperature_celsius"] = it }
            quietRide?.let { payload["quiet_ride"] = it }
            preferredMusic?.let { payload["preferred_music"] = it }
            wheelchair?.let { payload["wheelchair_access_required"] = it }
            petFriendly?.let { payload["pet_friendly_preferred"] = it }
            extraLegroom?.let { payload["extra_legroom_required"] = it }
            accessibilityNotes?.let { payload["accessibility_notes"] = it }
            preferredPaymentMethod?.let { payload["preferred_payment_method"] = it }

            val res = NetworkClient.apiService.updatePreferences(payload)
            if (res.isSuccessful) {
                fetchCustomerPreferences()
                Result.success(true)
            } else {
                Result.failure(IllegalStateException("HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchCorporateAccount(): Result<CorporateAccountDto?> {
        return try {
            val res = NetworkClient.apiService.getMyCorporateAccount()
            if (res.isSuccessful) {
                val data = res.body()
                _corporateAccount.value = data
                Result.success(data)
            } else if (res.code() == 404) {
                _corporateAccount.value = null
                Result.success(null)
            } else {
                Result.failure(IllegalStateException("HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerCorporateAccount(
        companyName: String,
        taxId: String,
        billingEmail: String,
        billingContactName: String? = null,
        billingContactPhone: String? = null,
        registeredAddress: String? = null,
        defaultCostCenter: String? = "OPS-CORP",
        department: String? = "Executive"
    ): Result<CorporateAccountDto> {
        return try {
            val req = CorporateRegistrationRequest(
                companyName = companyName,
                taxId = taxId,
                billingEmail = billingEmail,
                billingContactName = billingContactName,
                billingContactPhone = billingContactPhone,
                registeredAddress = registeredAddress,
                defaultCostCenter = defaultCostCenter,
                department = department
            )
            val res = NetworkClient.apiService.registerCorporateAccount(req)
            if (res.isSuccessful && res.body() != null) {
                _corporateAccount.value = res.body()
                Result.success(res.body()!!)
            } else {
                val err = res.errorBody()?.string() ?: "HTTP ${res.code()}"
                Result.failure(IllegalStateException(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitCorporateAccountForApproval(): Result<String> {
        return try {
            val res = NetworkClient.apiService.submitCorporateAccountForApproval()
            if (res.isSuccessful) {
                fetchCorporateAccount()
                Result.success("Submitted for approval")
            } else {
                Result.failure(IllegalStateException("HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun lockTariffPrice(durationHours: Int = 24): Result<Map<String, Any>?> {
        return try {
            val rideId = activeRide.value?.id
            val payload = mutableMapOf<String, Any>(
                "lock_duration_hours" to durationHours,
                "category_id" to "E_AUTO"
            )
            if (!rideId.isNullOrBlank()) {
                payload["ride_id"] = rideId
            }
            val res = NetworkClient.apiService.lockPrice(payload)
            if (res.isSuccessful) Result.success(res.body()) else Result.failure(Exception("HTTP ${res.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun savePreferences(temp: String, quietRide: Boolean): Result<Boolean> {
        return try {
            val tempFloat = temp.replace("°C", "").replace("C", "").trim().toFloatOrNull() ?: 22f
            saveCustomerPreferences(tempCelsius = tempFloat, quietRide = quietRide)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendPoolChatMessage(tripId: String, message: String): Result<Boolean> {
        return try {
            val res = NetworkClient.apiService.sendPoolMessage(
                tripId,
                mapOf("message" to message)
            )
            Result.success(res.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchTaxInvoice(rideId: String): Map<String, Any>? {
        return try {
            val res = NetworkClient.apiService.getTaxInvoice(rideId)
            if (res.isSuccessful) res.body() else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun sendInvoiceEmail(rideId: String, recipientEmail: String): Result<Boolean> {
        return try {
            val res = NetworkClient.apiService.sendInvoiceEmail(
                rideId = rideId,
                body = mapOf("recipient_email" to recipientEmail.trim())
            )
            if (res.isSuccessful) {
                Result.success(true)
            } else {
                val err = res.errorBody()?.string() ?: "HTTP ${res.code()}"
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- User Governance & Advanced Wallet Methods ---
    // RiderRepository.kt

    // RiderRepository.kt

    suspend fun registerPasskey(credId: String, pubKey: String): Result<Boolean> {
        return try {
            val res = NetworkClient.apiService.registerPasskey(
                mapOf("credential_id" to credId, "public_key" to pubKey)
            )
            if (res.isSuccessful) {
                NetworkClient.apiService.toggleBiometric(mapOf("enabled" to true))
                Result.success(true)
            } else {
                val err = res.errorBody()?.string() ?: "HTTP ${res.code()}"
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setAppLockPin(pin: String): Result<Boolean> {
        return try {
            val cleanPin = pin.trim()
            if (cleanPin.length != 4 || !cleanPin.all { it.isDigit() }) {
                return Result.failure(Exception("PIN sirf 4 digits ka hona chahiye."))
            }
            val res = NetworkClient.apiService.setAppLockPin(mapOf("pin" to cleanPin))
            if (res.isSuccessful) {
                Result.success(true)
            } else {
                val raw = res.errorBody()?.string()
                val errBody = parseApiErrorMessage(raw, "Server error: HTTP ${res.code()}")
                Result.failure(Exception(errBody))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyAppLockPin(pin: String): Result<Boolean> {
        return try {
            val cleanPin = pin.trim()
            if (cleanPin.length != 4 || !cleanPin.all { it.isDigit() }) {
                return Result.failure(Exception("4-digit PIN daalein."))
            }
            val res = NetworkClient.apiService.verifyAppLockPin(mapOf("pin" to cleanPin))
            if (res.isSuccessful) {
                Result.success(true)
            } else {
                val raw = res.errorBody()?.string()
                val errBody = parseApiErrorMessage(raw, "Galat PIN (HTTP ${res.code()})")
                Result.failure(Exception(errBody))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun fetchTrustedDevices(): Result<List<Map<String, Any>>> {
        return try {
            val res = NetworkClient.apiService.getTrustedDevices()
            Result.success(res.body() ?: emptyList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun requestDataExport(): Result<Map<String, Any>> {
        return try {
            val res = NetworkClient.apiService.requestDataExport()
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!)
            } else {
                Result.failure(Exception("Server error: HTTP ${res.code()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun requestAccountDeletion(reason: String): Result<Boolean> {
        return try {
            val res = NetworkClient.apiService.requestAccountDeletion(mapOf("reason" to reason))
            Result.success(res.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun configureAutoTopup(enabled: Boolean, threshold: Int, amount: Int): Result<Boolean> {
        return try {
            val res = NetworkClient.apiService.configureAutoTopup(
                mapOf(
                    "enabled" to enabled,
                    "threshold_cents" to threshold * 100,
                    "amount_cents" to amount * 100
                )
            )
            Result.success(res.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun purchaseGiftCard(amountRupees: Int, sender: String, recipientPhone: String): Result<Boolean> {
        return try {
            val res = NetworkClient.apiService.purchaseGiftCard(
                mapOf(
                    "amount_cents" to amountRupees * 100,
                    "sender_name" to sender,
                    "recipient_phone" to recipientPhone
                )
            )
            Result.success(res.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun redeemGiftCard(code: String): Result<Boolean> {
        return try {
            val res = NetworkClient.apiService.redeemGiftCard(mapOf("code" to code))
            Result.success(res.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerCustomerProfile(
        name: String,
        email: String
    ): Result<Boolean> {
        return try {
            val res = NetworkClient.apiService.registerProfile(
                RegisterCustomerRequest(
                    fullName = name.trim(),
                    email = email.trim().ifBlank { null }
                )
            )
            if (res.isSuccessful) {
                Result.success(true)
            } else {
                val errBody = res.errorBody()?.string() ?: "Profile save failed: HTTP ${res.code()}"
                Result.failure(Exception(errBody))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun fetchCustomerKycStatus(): Result<String> {
        return try {
            val response = NetworkClient.apiService.getCustomerKycStatus()
            if (response.isSuccessful) {
                Result.success(response.body()?.get("kyc_status")?.toString() ?: "NOT_SUBMITTED")
            } else {
                Result.failure(Exception(parseApiErrorMessage(response.errorBody()?.string(), "Unable to load identity review status.")))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitCustomerKycDocument(
        context: Context,
        uri: Uri,
        documentType: String
    ): Result<String> {
        return try {
            val allowedTypes = setOf("PAN_CARD", "DRIVING_LICENSE", "PASSPORT", "VOTER_ID")
            if (documentType !in allowedTypes) {
                return Result.failure(IllegalArgumentException("Select a supported identity document type."))
            }
            val mimeType = context.contentResolver.getType(uri)
                ?: return Result.failure(IllegalArgumentException("The selected file type could not be determined."))
            if (mimeType !in setOf("application/pdf", "image/jpeg", "image/png")) {
                return Result.failure(IllegalArgumentException("Choose a PDF, JPEG, or PNG document."))
            }
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var totalBytes = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    totalBytes += count
                    if (totalBytes > 5 * 1024 * 1024) {
                        return Result.failure(IllegalArgumentException("The document must be 5 MB or smaller."))
                    }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: return Result.failure(IllegalArgumentException("Unable to read the selected document."))
            if (bytes.isEmpty()) {
                return Result.failure(IllegalArgumentException("The selected document is empty."))
            }
            val displayName = context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: "identity-document"
            val documentTypeBody = documentType.toRequestBody("text/plain".toMediaType())
            val consentBody = "true".toRequestBody("text/plain".toMediaType())
            val fileBody = bytes.toRequestBody(mimeType.toMediaType())
            val filePart = MultipartBody.Part.createFormData("file", displayName, fileBody)
            val response = NetworkClient.apiService.submitCustomerKycDocument(documentTypeBody, consentBody, filePart)
            if (response.isSuccessful) {
                Result.success(response.body()?.get("message")?.toString() ?: "Document submitted for manual review.")
            } else {
                Result.failure(
                    Exception(
                        parseApiErrorMessage(
                            response.errorBody()?.string(),
                            "Identity document upload failed (HTTP ${response.code()})."
                        )
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun withdrawCustomerKycDocuments(): Result<String> {
        return try {
            val response = NetworkClient.apiService.withdrawCustomerKycDocuments()
            if (response.isSuccessful) {
                Result.success(response.body()?.get("message")?.toString() ?: "Submitted identity documents withdrawn.")
            } else {
                Result.failure(Exception(parseApiErrorMessage(response.errorBody()?.string(), "Unable to withdraw identity documents.")))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resetForgotPassword(recoveryCode: String, newPassword: String): Result<Boolean> {
        return try {
            val res = NetworkClient.apiService.resetForgotPassword(
                mapOf("recovery_code" to recoveryCode, "new_password" to newPassword)
            )
            Result.success(res.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }



    suspend fun changeUserPassword(currentPass: String, newPass: String): Result<Boolean> {
        return try {
            val res = NetworkClient.apiService.changePassword(
                mapOf(
                    "current_password" to currentPass,
                    "new_password" to newPass
                )
            )
            if (res.isSuccessful) {
                Result.success(true)
            } else {
                val errBody = res.errorBody()?.string() ?: "HTTP ${res.code()}"
                Result.failure(Exception(errBody))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }



    suspend fun uploadProfilePhoto(context: android.content.Context, uri: android.net.Uri): Result<String> {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(Exception("Unable to read selected image"))
            val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (originalBitmap == null) {
                return Result.failure(Exception("Invalid image format"))
            }

            val maxDim = 256
            val ratio = minOf(
                maxDim.toFloat() / originalBitmap.width,
                maxDim.toFloat() / originalBitmap.height,
                1f
            )
            val width = (originalBitmap.width * ratio).toInt().coerceAtLeast(1)
            val height = (originalBitmap.height * ratio).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(originalBitmap, width, height, true)

            val outputStream = java.io.ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val base64Str = android.util.Base64.encodeToString(
                outputStream.toByteArray(),
                android.util.Base64.NO_WRAP
            )
            val dataUri = "data:image/jpeg;base64,$base64Str"

            val res = NetworkClient.apiService.updateProfileAvatar(mapOf("avatar_url" to dataUri))
            if (res.isSuccessful) {
                Result.success(dataUri)
            } else {
                val err = res.errorBody()?.string() ?: "HTTP ${res.code()}"
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    suspend fun lockPrice(categoryId: String, pickupLat: Double, pickupLng: Double, destLat: Double, destLng: Double): Result<Boolean> {
        return try {
            val res = NetworkClient.apiService.lockPrice(
                mapOf(
                    "category_id" to categoryId,
                    "pickup_latitude" to pickupLat,
                    "pickup_longitude" to pickupLng,
                    "destination_latitude" to destLat,
                    "destination_longitude" to destLng
                )
            )
            Result.success(res.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun validatePromo(code: String, fareRupees: Int = 120): Result<Map<String, Any>?> {
        return try {
            val res = NetworkClient.apiService.validatePromo(
                mapOf("code" to code, "fare_cents" to fareRupees * 100)
            )
            if (res.isSuccessful) Result.success(res.body()) else Result.failure(Exception("Invalid promo code"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordAudioConsent(rideId: String, consented: Boolean): Result<AudioConsentResponse> {
        return try {
            val res = NetworkClient.apiService.recordAudioConsent(
                rideId = rideId,
                body = AudioConsentRequest(consented = consented, role = "CUSTOMER")
            )
            if (res.isSuccessful && res.body() != null) {
                val data = res.body()!!
                _isAudioRecordingActive.value = data.isRecordingActive
                Result.success(data)
            } else {
                Result.failure(Exception(res.errorBody()?.string() ?: "Failed to record audio consent"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun withdrawAudioConsent(rideId: String, reason: String? = null): Result<Boolean> {
        return try {
            val res = NetworkClient.apiService.withdrawAudioConsent(
                rideId = rideId,
                body = AudioConsentWithdrawRequest(reason = reason)
            )
            if (res.isSuccessful) {
                _isAudioRecordingActive.value = false
                Result.success(true)
            } else {
                Result.failure(Exception(res.errorBody()?.string() ?: "Failed to withdraw consent"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchAudioConsentStatus(rideId: String): Result<AudioRecordingStatusResponse?> {
        return try {
            val res = NetworkClient.apiService.getAudioConsentStatus(rideId)
            if (res.isSuccessful && res.body() != null) {
                val data = res.body()!!
                _audioRecordingStatus.value = data
                _isAudioRecordingActive.value = data.isRecordingActive
                Result.success(data)
            } else {
                Result.success(_audioRecordingStatus.value)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun authorizeAudioRecording(rideId: String): Result<Map<String, Any>> {
        return try {
            val res = NetworkClient.apiService.authorizeAudioRecording(rideId)
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!)
            } else {
                Result.failure(Exception(res.errorBody()?.string() ?: "Failed to authorize audio recording"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun setAudioRecordingActive(active: Boolean) {
        _isAudioRecordingActive.value = active
    }

    suspend fun setAudioRecordingConsent(enabled: Boolean, rideId: String? = null): Result<Boolean> {
        val targetRideId = rideId ?: activeRide.value?.id ?: "ride-latest"
        val consentRes = recordAudioConsent(targetRideId, enabled)
        return if (consentRes.isSuccess) Result.success(true) else Result.failure(consentRes.exceptionOrNull() ?: Exception("Consent update failed"))
    }

    suspend fun reportRouteAnomaly(rideId: String, currentLat: Double, currentLng: Double): Result<Boolean> {
        return try {
            val res = NetworkClient.apiService.reportRouteAnomaly(
                mapOf("ride_id" to rideId, "current_latitude" to currentLat, "current_longitude" to currentLng, "anomaly_type" to "ROUTE_DEVIATION")
            )
            Result.success(res.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun shareRide(rideId: String, recipientPhone: String): Result<Boolean> {
        return try {
            val res = NetworkClient.apiService.shareRide(
                rideId,
                mapOf("recipient_phone" to recipientPhone)
            )
            Result.success(res.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTicketMessages(ticketId: String): Result<List<Map<String, Any>>> {
        return try {
            val res = NetworkClient.apiService.getTicketMessages(ticketId)
            if (res.isSuccessful && res.body() != null) Result.success(res.body()!!) else Result.success(emptyList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendTicketMessage(ticketId: String, message: String): Result<Boolean> {
        return try {
            val res = NetworkClient.apiService.sendTicketMessage(ticketId, mapOf("content" to message))
            Result.success(res.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getVehicleCredentials(rideId: String): Result<Map<String, Any>?> {
        return try {
            val res = NetworkClient.apiService.getVehicleCredentials(rideId)
            if (res.isSuccessful) Result.success(res.body()) else Result.failure(Exception("Credentials unavailable"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getInTripChatMessages(rideId: String): Result<List<Map<String, Any>>> {
        return try {
            val res = NetworkClient.apiService.getInTripChatMessages(rideId)
            if (res.isSuccessful && res.body() != null) Result.success(res.body()!!) else Result.success(emptyList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendInTripChatMessage(rideId: String, text: String): Result<Boolean> {
        return try {
            val res = NetworkClient.apiService.sendInTripChatMessage(rideId, mapOf("text" to text, "message" to text))
            Result.success(res.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun requestDestinationChange(
        rideId: String,
        newAddress: String,
        latitude: Double,
        longitude: Double
    ): Result<Map<String, Any>?> {
        return try {
            val payload = mapOf<String, Any>(
                "address" to newAddress,
                "latitude" to latitude,
                "longitude" to longitude,
                "reason" to "Customer requested route update via In-Trip HUD"
            )
            val res = NetworkClient.apiService.requestDestinationChange(rideId, payload)
            if (res.isSuccessful && res.body() != null) {
                updateDestination(newAddress, latitude, longitude)
                Result.success(res.body())
            } else {
                updateDestination(newAddress, latitude, longitude)
                Result.success(emptyMap())
            }
        } catch (e: Exception) {
            updateDestination(newAddress, latitude, longitude)
            Result.failure(e)
        }
    }

    // --- Gift Cards Suite Methods ---

    suspend fun fetchGiftCardConfig(): Result<GiftCardConfigDto> {
        return try {
            val res = NetworkClient.apiService.getGiftCardConfig()
            val body = res.body()
            if (res.isSuccessful && body != null) {
                _giftCardConfig.value = body
                Result.success(body)
            } else {
                Result.failure(IllegalStateException("Failed to load gift card configuration: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun purchaseGiftCard(req: GiftCardPurchaseRequest): Result<GiftCardPurchaseResponse> {
        return try {
            val res = NetworkClient.apiService.purchaseGiftCard(req)
            val body = res.body()
            if (res.isSuccessful && body != null) {
                fetchMyGiftCards()
                fetchWalletBalance()
                Result.success(body)
            } else {
                val err = res.errorBody()?.string() ?: "HTTP ${res.code()}"
                Result.failure(IllegalStateException(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkGiftCardBalance(code: String, pin: String? = null): Result<GiftCardBalanceResponse> {
        return try {
            val res = NetworkClient.apiService.checkGiftCardBalance(GiftCardBalanceRequest(code = code, pin = pin))
            val body = res.body()
            if (res.isSuccessful && body != null) {
                Result.success(body)
            } else {
                val err = res.errorBody()?.string() ?: "Invalid gift card code or expired"
                Result.failure(IllegalStateException(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun redeemGiftCard(code: String, pin: String? = null): Result<GiftCardRedeemResponse> {
        return try {
            val res = NetworkClient.apiService.redeemGiftCard(GiftCardRedeemRequest(code = code, pin = pin))
            val body = res.body()
            if (res.isSuccessful && body != null) {
                fetchWalletBalance()
                fetchMyGiftCards()
                Result.success(body)
            } else {
                val err = res.errorBody()?.string() ?: "Redemption failed: HTTP ${res.code()}"
                Result.failure(IllegalStateException(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchMyGiftCards(filterType: String = "ALL"): Result<List<GiftCardSummaryDto>> {
        return try {
            val res = NetworkClient.apiService.getMyGiftCards(filterType)
            val body = res.body()
            if (res.isSuccessful && body != null) {
                _myGiftCards.value = body
                Result.success(body)
            } else {
                Result.failure(IllegalStateException("Failed to fetch gift cards: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchGiftCardDetails(giftCardId: String): Result<GiftCardDetailDto> {
        return try {
            val res = NetworkClient.apiService.getGiftCardDetails(giftCardId)
            val body = res.body()
            if (res.isSuccessful && body != null) {
                Result.success(body)
            } else {
                Result.failure(IllegalStateException("Failed to load gift card details: HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private var activeTopupLaunchInProgress = false
    private var activeRideLaunchInProgress = false

    fun hasPendingVerification(flowType: CheckoutFlowType? = null): Boolean {
        val store = NetworkClient.checkoutSessionStore
        val currentUid = NetworkClient.currentUserId()
        return if (flowType == null) {
            store.getRetryableSession(CheckoutFlowType.WALLET_TOPUP, currentUid) != null ||
                store.getRetryableSession(CheckoutFlowType.RIDE_FARE, currentUid) != null
        } else {
            store.getRetryableSession(flowType, currentUid) != null
        }
    }

    fun restoreCheckoutStates() {
        val store = NetworkClient.checkoutSessionStore
        val currentUid = NetworkClient.currentUserId()
        val topupSess = store.getLatestRestorableSession(CheckoutFlowType.WALLET_TOPUP, currentUid)
        if (topupSess != null && _topupState.value is TopupState.Idle) {
            _topupState.value = when (topupSess.state) {
                CheckoutSessionState.VERIFICATION_RETRYABLE ->
                    TopupState.Error(topupSess.failureReason ?: "Wallet verification interrupted; retry available.")
                CheckoutSessionState.VERIFICATION_PENDING ->
                    TopupState.ReconciliationRequired("Previous top-up verification interrupted; refresh wallet or contact support.")
                CheckoutSessionState.PAYMENT_AUTHORIZED ->
                    TopupState.AuthorizedPending("Wallet top-up payment authorized by gateway; awaiting final capture.")
                CheckoutSessionState.RECONCILIATION_REQUIRED ->
                    TopupState.ReconciliationRequired(topupSess.failureReason ?: "Wallet payment requires backend reconciliation.")
                else -> _topupState.value
            }
        }
        val rideSess = store.getLatestRestorableSession(CheckoutFlowType.RIDE_FARE, currentUid)
        if (rideSess != null && _ridePaymentState.value is RidePaymentState.Idle) {
            _ridePaymentState.value = when (rideSess.state) {
                CheckoutSessionState.VERIFICATION_RETRYABLE ->
                    RidePaymentState.Error(rideSess.failureReason ?: "Ride payment verification interrupted; retry available.")
                CheckoutSessionState.VERIFICATION_PENDING ->
                    RidePaymentState.ReconciliationRequired("Previous ride verification interrupted; check ride payment status.")
                CheckoutSessionState.PAYMENT_AUTHORIZED ->
                    RidePaymentState.AuthorizedPending("Ride payment authorized by gateway; awaiting capture confirmation.")
                CheckoutSessionState.RECONCILIATION_REQUIRED ->
                    RidePaymentState.ReconciliationRequired(rideSess.failureReason ?: "Ride payment requires backend reconciliation.")
                else -> _ridePaymentState.value
            }
        }
    }

    // Sealed class for UI state management
    sealed class TopupState {
        object Idle : TopupState()
        object CreatingOrder : TopupState()
        object CheckoutOpen : TopupState()
        object Verifying : TopupState()
        data class AuthorizedPending(val message: String) : TopupState()
        data class ReconciliationRequired(val message: String) : TopupState()
        data class Success(val balanceRupees: Double) : TopupState()
        data class Error(val message: String) : TopupState()
    }

    private val _topupState = MutableStateFlow<TopupState>(TopupState.Idle)
    val topupState: StateFlow<TopupState> = _topupState.asStateFlow()

    sealed class RidePaymentState {
        object Idle : RidePaymentState()
        object CreatingOrder : RidePaymentState()
        object CheckoutOpen : RidePaymentState()
        object Verifying : RidePaymentState()
        data class AuthorizedPending(val message: String) : RidePaymentState()
        data class ReconciliationRequired(val message: String) : RidePaymentState()
        data class Success(val message: String, val rideId: String?) : RidePaymentState()
        data class Error(val message: String) : RidePaymentState()
    }

    private val _ridePaymentState = MutableStateFlow<RidePaymentState>(RidePaymentState.Idle)
    val ridePaymentState: StateFlow<RidePaymentState> = _ridePaymentState.asStateFlow()

    fun launchWalletTopupCheckout(activity: android.app.Activity, amountRupees: Double) {
        if (activeTopupLaunchInProgress ||
            _topupState.value is TopupState.CreatingOrder ||
            _topupState.value is TopupState.CheckoutOpen ||
            _topupState.value is TopupState.Verifying
        ) {
            return
        }
        val amountCents = Math.round(amountRupees * 100.0).toInt()
        if (amountCents < 10000) {
            _topupState.value = TopupState.Error("Minimum wallet top-up is ₹100.00")
            return
        }
        activeTopupLaunchInProgress = true
        val currentUid = NetworkClient.currentUserId()
        val intentScopeKey = "WALLET_TOPUP_${currentUid ?: "anon"}_${amountCents}"
        val idemKey = NetworkClient.checkoutSessionStore.getOrCreateScopedIdempotencyKey(intentScopeKey, "topup")
        _topupState.value = TopupState.CreatingOrder

        scope.launch {
            try {
                val res = NetworkClient.apiService.createWalletTopupOrder(
                    TopupCreateOrderRequest(
                        amountCents = amountCents,
                        idempotencyKey = idemKey
                    )
                )
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    val keyId = body.keyId?.trim().orEmpty()
                    val orderId = body.orderId.trim()
                    val serverAmountCents = body.amountCents
                    if (!CheckoutSessionStore.isValidRazorpayPublicKey(keyId) || orderId.isEmpty() || serverAmountCents <= 0) {
                        NetworkClient.checkoutSessionStore.clearScopedIdempotencyKey(intentScopeKey)
                        _topupState.value = TopupState.Error("Razorpay configuration unavailable or invalid; checkout aborted safely.")
                        return@launch
                    }

                    NetworkClient.checkoutSessionStore.saveSession(
                        CheckoutSession(
                            orderId = orderId,
                            backendPaymentId = body.paymentId,
                            checkoutType = CheckoutFlowType.WALLET_TOPUP,
                            rideId = null,
                            userId = currentUid,
                            amountCents = serverAmountCents,
                            currency = body.currency.ifBlank { "INR" },
                            idempotencyKey = idemKey,
                            state = CheckoutSessionState.ORDER_CREATED
                        )
                    )
                    NetworkClient.checkoutSessionStore.markCheckoutOpen(orderId)
                    _topupState.value = TopupState.CheckoutOpen

                    withContext(Dispatchers.Main) {
                        val checkout = com.razorpay.Checkout()
                        checkout.setKeyID(keyId)

                        val options = org.json.JSONObject().apply {
                            put("name", "Vaisentra Mobility")
                            put("description", "Wallet Recharge ₹${String.format(java.util.Locale.US, "%.2f", serverAmountCents / 100.0)}")
                            put("currency", body.currency.ifBlank { "INR" })
                            put("amount", serverAmountCents)
                            put("order_id", orderId)
                            put("theme.color", "#0D9488")
                        }
                        checkout.open(activity, options)
                    }
                } else {
                    if (res.code() in 400..499) {
                        NetworkClient.checkoutSessionStore.clearScopedIdempotencyKey(intentScopeKey)
                    }
                    _topupState.value = TopupState.Error("Order creation failed: HTTP ${res.code()}")
                }
            } catch (e: Exception) {
                _topupState.value = TopupState.Error(e.message ?: "Network error creating top-up order")
            } finally {
                activeTopupLaunchInProgress = false
            }
        }
    }

    fun launchRidePaymentCheckout(activity: android.app.Activity, rideId: String, @Suppress("UNUSED_PARAMETER") amountRupees: Double = 0.0) {
        if (rideId.isBlank()) {
            _ridePaymentState.value = RidePaymentState.Error("Invalid ride reference for payment.")
            return
        }
        if (activeRideLaunchInProgress ||
            _ridePaymentState.value is RidePaymentState.CreatingOrder ||
            _ridePaymentState.value is RidePaymentState.CheckoutOpen ||
            _ridePaymentState.value is RidePaymentState.Verifying
        ) {
            return
        }
        activeRideLaunchInProgress = true
        val currentUid = NetworkClient.currentUserId()
        val intentScopeKey = "RIDE_FARE_${currentUid ?: "anon"}_${rideId}"
        val idemKey = NetworkClient.checkoutSessionStore.getOrCreateScopedIdempotencyKey(intentScopeKey, "ride_${rideId.take(12)}")
        _ridePaymentState.value = RidePaymentState.CreatingOrder

        scope.launch {
            try {
                // Server computes authoritative fare from Ride DB record; never send client-calculated totals
                val res = NetworkClient.apiService.createRidePaymentOrder(
                    RidePaymentOrderRequest(
                        rideId = rideId,
                        idempotencyKey = idemKey
                    )
                )
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    val effectiveOrderId = body.effectiveOrderId.trim()
                    val keyId = body.keyId?.trim().orEmpty()
                    val serverAmountCents = body.amountCents
                    if (!CheckoutSessionStore.isValidRazorpayPublicKey(keyId) || effectiveOrderId.isEmpty() || serverAmountCents <= 0) {
                        NetworkClient.checkoutSessionStore.clearScopedIdempotencyKey(intentScopeKey)
                        _ridePaymentState.value = RidePaymentState.Error("Razorpay configuration unavailable or invalid for ride payment.")
                        return@launch
                    }

                    NetworkClient.checkoutSessionStore.saveSession(
                        CheckoutSession(
                            orderId = effectiveOrderId,
                            backendPaymentId = body.paymentId,
                            checkoutType = CheckoutFlowType.RIDE_FARE,
                            rideId = rideId,
                            userId = currentUid,
                            amountCents = serverAmountCents,
                            currency = body.currency.ifBlank { "INR" },
                            idempotencyKey = idemKey,
                            state = CheckoutSessionState.ORDER_CREATED
                        )
                    )
                    NetworkClient.checkoutSessionStore.markCheckoutOpen(effectiveOrderId)
                    _ridePaymentState.value = RidePaymentState.CheckoutOpen

                    withContext(Dispatchers.Main) {
                        val checkout = com.razorpay.Checkout()
                        checkout.setKeyID(keyId)

                        val options = org.json.JSONObject().apply {
                            put("name", "Vaisentra Mobility")
                            put("description", "Ride Payment #${rideId.take(8)}")
                            put("currency", body.currency.ifBlank { "INR" })
                            put("amount", serverAmountCents)
                            put("order_id", effectiveOrderId)
                            put("theme.color", "#0D9488")
                        }
                        checkout.open(activity, options)
                    }
                } else {
                    if (res.code() in 400..499) {
                        NetworkClient.checkoutSessionStore.clearScopedIdempotencyKey(intentScopeKey)
                    }
                    _ridePaymentState.value = RidePaymentState.Error("Ride order creation failed: HTTP ${res.code()}")
                }
            } catch (e: Exception) {
                _ridePaymentState.value = RidePaymentState.Error(e.message ?: "Network error creating ride payment order")
            } finally {
                activeRideLaunchInProgress = false
            }
        }
    }

    fun handleRazorpaySuccess(orderId: String, paymentId: String, signature: String) {
        val cleanOrderId = orderId.trim()
        val store = NetworkClient.checkoutSessionStore
        val currentUid = NetworkClient.currentUserId()

        if (cleanOrderId.isEmpty() || paymentId.isBlank() || signature.isBlank()) {
            // Do not silently fall back to Ride or Wallet; mark strictly CHECKOUT_OPEN sessions as RECONCILIATION_REQUIRED
            val affected = if (cleanOrderId.isNotEmpty()) {
                listOfNotNull(
                    store.markReconciliationRequired(
                        cleanOrderId,
                        reason = "Gateway callback omitted payment_id or signature; reconciliation required."
                    )
                )
            } else {
                store.markOpenCheckoutReconciliationRequired(
                    reason = "Gateway success callback omitted order_id; reconciliation required.",
                    currentUserId = currentUid
                )
            }
            val msg = "Gateway callback lacked complete order identity; marked for reconciliation without cross-flow fallback."
            affected.forEach { sess ->
                if (sess.checkoutType == CheckoutFlowType.RIDE_FARE) {
                    _ridePaymentState.value = RidePaymentState.ReconciliationRequired(msg)
                } else {
                    _topupState.value = TopupState.ReconciliationRequired(msg)
                }
            }
            return
        }

        when (val resolution = store.resolveSuccessCallback(cleanOrderId, paymentId, signature, currentUid)) {
            is CallbackResolutionResult.BeginVerification -> executeSessionVerification(resolution.session)
            is CallbackResolutionResult.DuplicateIgnored,
            is CallbackResolutionResult.UnknownOrder,
            is CallbackResolutionResult.OldOrderSuperseded -> {
                // Ignore duplicates, stale superseded orders, or unrecognized order IDs without corrupting active UI flows
                return
            }
            is CallbackResolutionResult.OwnershipMismatch -> {
                val sess = store.getSessionByOrderId(resolution.orderId)
                val err = "Payment callback rejected: account mismatch for order ${resolution.orderId}."
                if (sess?.checkoutType == CheckoutFlowType.RIDE_FARE) {
                    _ridePaymentState.value = RidePaymentState.Error(err)
                } else if (sess?.checkoutType == CheckoutFlowType.WALLET_TOPUP) {
                    _topupState.value = TopupState.Error(err)
                }
            }
            is CallbackResolutionResult.TerminalState -> return
            is CallbackResolutionResult.MissingIdentity -> return
        }
    }

    fun retryPendingVerification(flowType: CheckoutFlowType = CheckoutFlowType.WALLET_TOPUP) {
        val currentUid = NetworkClient.currentUserId()
        val retryable = NetworkClient.checkoutSessionStore.getRetryableSession(flowType, currentUid) ?: return
        val lockedSession = NetworkClient.checkoutSessionStore.tryBeginVerification(
            retryable.orderId,
            retryable.paymentId.orEmpty(),
            retryable.signature.orEmpty(),
            currentUid
        ) ?: return
        executeSessionVerification(lockedSession)
    }

    private fun executeSessionVerification(session: CheckoutSession) {
        val store = NetworkClient.checkoutSessionStore
        val paymentId = session.paymentId.orEmpty()
        val signature = session.signature.orEmpty()
        if (session.checkoutType == CheckoutFlowType.RIDE_FARE) {
            val targetRideId = session.rideId
            val intentScopeKey = "RIDE_FARE_${session.userId ?: "anon"}_${targetRideId ?: session.orderId}"
            _ridePaymentState.value = RidePaymentState.Verifying
            scope.launch {
                try {
                    val res = NetworkClient.apiService.verifyRidePayment(
                        RidePaymentVerifyRequest(
                            razorpayOrderId = session.orderId,
                            razorpayPaymentId = paymentId,
                            razorpaySignature = signature,
                            rideId = targetRideId
                        )
                    )
                    if (res.isSuccessful && res.body() != null) {
                        val respBody = res.body()!!
                        val normalizedStatus = respBody.status.trim().uppercase()
                        when (normalizedStatus) {
                            "CAPTURED", "ALREADY_PROCESSED", "SUCCESS" -> {
                                store.markVerifiedAndClearIntentKey(session.orderId, intentScopeKey)
                                _ridePaymentState.value = RidePaymentState.Success("Ride payment verified successfully!", targetRideId)
                                fetchActiveRide()
                            }
                            "AUTHORIZED" -> {
                                store.markPaymentAuthorized(session.orderId, "Gateway authorized payment; awaiting final capture.")
                                _ridePaymentState.value = RidePaymentState.AuthorizedPending("Ride payment authorized; awaiting gateway capture confirmation.")
                            }
                            else -> {
                                val recMsg = "Unexpected gateway verification status '$normalizedStatus'; reconciliation required."
                                store.markReconciliationRequired(session.orderId, intentScopeKey, recMsg)
                                _ridePaymentState.value = RidePaymentState.ReconciliationRequired(recMsg)
                            }
                        }
                    } else if (res.code() in 400..499 && res.code() != 429) {
                        val errMsg = parseApiErrorMessage(res.errorBody()?.string(), "Verification rejected (HTTP ${res.code()}).")
                        store.markTerminalAndClearIntentKey(session.orderId, intentScopeKey, CheckoutSessionState.TERMINAL_FAILURE, errMsg)
                        _ridePaymentState.value = RidePaymentState.Error(errMsg)
                    } else {
                        val retryMsg = "Verification temporarily unavailable (HTTP ${res.code()}); retry available."
                        store.markRetryableFailure(session.orderId, retryMsg)
                        _ridePaymentState.value = RidePaymentState.Error(retryMsg)
                    }
                } catch (e: Exception) {
                    val netMsg = e.message ?: "Verification network timeout; retry available."
                    store.markRetryableFailure(session.orderId, netMsg)
                    _ridePaymentState.value = RidePaymentState.Error(netMsg)
                }
            }
        } else {
            val intentScopeKey = "WALLET_TOPUP_${session.userId ?: "anon"}_${session.amountCents}"
            _topupState.value = TopupState.Verifying
            scope.launch {
                try {
                    val res = NetworkClient.apiService.verifyWalletTopup(
                        TopupVerifyRequest(
                            razorpayOrderId = session.orderId,
                            razorpayPaymentId = paymentId,
                            razorpaySignature = signature
                        )
                    )
                    if (res.isSuccessful && res.body() != null) {
                        val updated = res.body()!!
                        val normalizedStatus = updated.status.trim().uppercase()
                        when (normalizedStatus) {
                            "SUCCESS", "ALREADY_PROCESSED", "CAPTURED" -> {
                                store.markVerifiedAndClearIntentKey(session.orderId, intentScopeKey)
                                _topupState.value = TopupState.Success(updated.balanceRupees)
                                fetchWalletBalance()
                            }
                            else -> {
                                val recMsg = "Unexpected wallet verification status '$normalizedStatus'; reconciliation required."
                                store.markReconciliationRequired(session.orderId, intentScopeKey, recMsg)
                                _topupState.value = TopupState.ReconciliationRequired(recMsg)
                            }
                        }
                    } else if (res.code() in 400..499 && res.code() != 429) {
                        val errMsg = parseApiErrorMessage(res.errorBody()?.string(), "Verification rejected (HTTP ${res.code()}).")
                        store.markTerminalAndClearIntentKey(session.orderId, intentScopeKey, CheckoutSessionState.TERMINAL_FAILURE, errMsg)
                        _topupState.value = TopupState.Error(errMsg)
                    } else {
                        val retryMsg = "Verification temporarily unavailable (HTTP ${res.code()}); retry available."
                        store.markRetryableFailure(session.orderId, retryMsg)
                        _topupState.value = TopupState.Error(retryMsg)
                    }
                } catch (e: Exception) {
                    val netMsg = e.message ?: "Verification network timeout; retry available."
                    store.markRetryableFailure(session.orderId, netMsg)
                    _topupState.value = TopupState.Error(netMsg)
                }
            }
        }
    }

    fun handleRazorpayFailure(code: Int, description: String, orderId: String? = null) {
        val store = NetworkClient.checkoutSessionStore
        val currentUid = NetworkClient.currentUserId()
        val session = store.getSessionByOrderId(orderId)
        if (session != null) {
            if (session.state in setOf(
                    CheckoutSessionState.VERIFIED,
                    CheckoutSessionState.CANCELLED,
                    CheckoutSessionState.TERMINAL_FAILURE,
                    CheckoutSessionState.EXPIRED
                )
            ) {
                return
            }
            val msg = "Payment cancelled ($code): $description"
            val finalState = if (code == com.razorpay.Checkout.PAYMENT_CANCELED || code == 0 || code == 2) {
                CheckoutSessionState.CANCELLED
            } else {
                CheckoutSessionState.TERMINAL_FAILURE
            }
            if (session.checkoutType == CheckoutFlowType.RIDE_FARE) {
                val scopeKey = "RIDE_FARE_${session.userId ?: "anon"}_${session.rideId ?: session.orderId}"
                store.markTerminalAndClearIntentKey(session.orderId, scopeKey, finalState, msg)
                _ridePaymentState.value = RidePaymentState.Error(msg)
            } else {
                val scopeKey = "WALLET_TOPUP_${session.userId ?: "anon"}_${session.amountCents}"
                store.markTerminalAndClearIntentKey(session.orderId, scopeKey, finalState, msg)
                _topupState.value = TopupState.Error(msg)
            }
            return
        }
        // Without orderId: never cross-contaminate or blindly guess Ride/Wallet; flag strictly CHECKOUT_OPEN sessions
        val affected = store.markOpenCheckoutReconciliationRequired(
            reason = "Gateway error callback ($code: $description) omitted order_id.",
            currentUserId = currentUid
        )
        affected.forEach { sess ->
            val msg = "Payment outcome ($code) lacked order identity; marked for reconciliation."
            if (sess.checkoutType == CheckoutFlowType.RIDE_FARE) {
                _ridePaymentState.value = RidePaymentState.ReconciliationRequired(msg)
            } else {
                _topupState.value = TopupState.ReconciliationRequired(msg)
            }
        }
    }
}

