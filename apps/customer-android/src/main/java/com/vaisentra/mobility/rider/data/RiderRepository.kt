package com.vaisentra.mobility.rider.data

import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    private val _wallet = MutableStateFlow<WalletBalanceDto>(
        WalletBalanceDto(
            walletId = "w-default",
            balanceCents = 48500,
            balanceRupees = 485.00,
            autoTopupEnabled = true,
            autoTopupThreshold = 100.0,
            autoTopupAmount = 500.0
        )
    )
    val wallet: StateFlow<WalletBalanceDto> = _wallet.asStateFlow()

    private val _loyalty = MutableStateFlow(LoyaltySummaryDto())
    val loyalty: StateFlow<LoyaltySummaryDto> = _loyalty.asStateFlow()

    // 1. Dummy list hata kar emptyList() rakhein taaki asli Supabase data hi dikhe
    private val _emergencyContacts = MutableStateFlow<List<EmergencyContactDto>>(emptyList())
    val emergencyContacts: StateFlow<List<EmergencyContactDto>> = _emergencyContacts.asStateFlow()
    private val _savedPlaces = MutableStateFlow<List<SavedPlaceDto>>(
        listOf(
            SavedPlaceDto("p1", "Home", "Flat 402, Cyber Heights, Sector 62, Noida", 28.6289, 77.3710, "HOME"),
            SavedPlaceDto("p2", "Office", "Building 9A, DLF Cyber City, Gurugram", 28.4900, 77.0880, "WORK"),
            SavedPlaceDto("p3", "IGI Airport T3", "Indira Gandhi International Airport, New Delhi", 28.5562, 77.1000, "AIRPORT"),
            SavedPlaceDto("p4", "Rajiv Chowk Metro", "Connaught Place Gate 4, New Delhi", 28.6328, 77.2197, "METRO")
        )
    )
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
        updatePickupLocation(lat, lng)
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

            // Store token in NetworkClient memory.
            NetworkClient.setAuthToken(token)

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

    suspend fun triggerSos(lat: Double, lng: Double): Result<Boolean> {
        return try {
            val currentRide = _activeRide.value?.id ?: "ride-live"
            val res = NetworkClient.apiService.triggerSos(SosTriggerRequest(currentRide, lat, lng))
            Result.success(res.isSuccessful)
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
            } else Result.success(_savedPlaces.value)
        } catch (e: Exception) {
            Result.success(_savedPlaces.value)
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
                // Fallback to pre-seeded live categories if network fails
                val fallback = defaultVehicleCategories()
                _categories.value = fallback
                Result.success(fallback)
            }
        } catch (e: Exception) {
            val fallback = defaultVehicleCategories()
            _categories.value = fallback
            Result.success(fallback)
        }
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
                Result.success(defaultFareComparisons())
            }
        } catch (e: Exception) {
            Result.success(defaultFareComparisons())
        }
    }

    suspend fun createRideBooking(
        pickupAddress: String,
        destinationAddress: String,
        categoryId: String,
        estimateId: String? = null,
        paymentMethod: String = "UPI",
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
                paymentMethod = paymentMethod
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
            if (response.isSuccessful && response.body() != null) {
                val bal = response.body()!!
                val computedRupees = if (bal.balanceRupees > 0.0) bal.balanceRupees else (bal.balanceCents / 100.0)
                val synced = bal.copy(balanceRupees = computedRupees)
                _wallet.value = synced
                Result.success(synced)
            } else {
                Result.success(_wallet.value)
            }
        } catch (e: Exception) {
            Result.success(_wallet.value)
        }
    }

    fun topupWalletBalance(amount: Double) {
        val current = _wallet.value
        val newBal = current.balanceRupees + amount
        _wallet.value = current.copy(
            balanceRupees = newBal,
            balanceCents = (newBal * 100).toInt()
        )
        scope.launch {
            try {
                val res = NetworkClient.apiService.topupWallet(
                    TopupRequest.fromRupees(amount)
                )
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    val finalRupees = if (body.balanceRupees > 0.0) body.balanceRupees else (body.balanceCents / 100.0)
                    _wallet.value = body.copy(balanceRupees = finalRupees)
                }
            } catch (e: Exception) {
                android.util.Log.e("RiderRepository", "topupWallet failed: ${e.message}")
            }
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

    private fun defaultFareComparisons(): List<VehicleFareComparisonDto> = listOf(
        VehicleFareComparisonDto(
            categoryId = "12b8f8bd-2d61-4105-a0d8-5661fe822254",
            code = "E_RICKSHAW_STANDARD",
            name = "Standard E-Rickshaw",
            capacity = 4,
            description = "Eco-friendly electric 3-wheeler",
            iconUrl = null,
            estimate = FareEstimateDetailDto("est-1", 4.2, 14.0, 12900, 129.00, 20.0, 1.0)
        ),
        VehicleFareComparisonDto(
            categoryId = "9b1c4e58-78b3-4a0b-80bb-f625f2d6ea2a",
            code = "E_RICKSHAW_SHARE",
            name = "Shared E-Rickshaw (Eco-Pool)",
            capacity = 4,
            description = "Per-seat commuter transit",
            iconUrl = null,
            estimate = FareEstimateDetailDto("est-2", 4.2, 14.0, 9500, 95.00, 10.0, 1.0)
        ),
        VehicleFareComparisonDto(
            categoryId = "0daec8ce-7e8a-4603-ab3f-6bed9455ccd7",
            code = "E_AUTO",
            name = "High-Speed Electric Auto",
            capacity = 3,
            description = "Enclosed high-speed cabin",
            iconUrl = null,
            estimate = FareEstimateDetailDto("est-3", 4.2, 12.0, 15500, 155.00, 30.0, 1.0)
        ),
        VehicleFareComparisonDto(
            categoryId = "653d62fb-c68e-4799-9d55-411ccc9b5e01",
            code = "E_CARGO_LOADER",
            name = "E-Loader Cargo",
            capacity = 1,
            description = "Heavy goods and parcels",
            iconUrl = null,
            estimate = FareEstimateDetailDto("est-4", 4.2, 16.0, 19500, 195.00, 50.0, 1.0)
        )
    )

    // --- Mapbox Live Places & Routing ---
    suspend fun searchMapboxPlaces(query: String): List<MapSearchResult> {
        val draft = _bookingDraft.value
        val cleanQuery = query.trim()
        if (cleanQuery.length < 2) return emptyList()

        // 1. Pehle aapke FastAPI backend ko current GPS proximity ke saath try karein
        try {
            val response = NetworkClient.apiService.searchPlacesMapbox(
                MapSearchRequest(
                    query = cleanQuery,
                    proximity_lat = draft.pickupLat,
                    proximity_lng = draft.pickupLng
                )
            )
            if (response.isSuccessful && response.body() != null && response.body()!!.results.isNotEmpty()) {
                return response.body()!!.results
            }
        } catch (_: Exception) {
        }

        // 2. Fallback: Direct Mapbox Geocoding API (India bias + user GPS proximity)
        return try {
            val token = "pk.eyJ1Ijoia290bGlubWFzdGVybWluZCIsImEiOiJjbXZ5c205bWswZnV4MnhzOXNzdm02bDB0In0.XZPhcU3_Et2F5raRUkVtxQ"
            val encoded = java.net.URLEncoder.encode(cleanQuery, "UTF-8")
            val urlStr = "https://api.mapbox.com/geocoding/v5/mapbox.places/$encoded.json" +
                    "?access_token=$token" +
                    "&proximity=${draft.pickupLng},${draft.pickupLat}" +
                    "&country=IN" +
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

    suspend fun scheduleRideBooking(pickup: String, dest: String, pickupTimeIso: String): Result<Boolean> {
        return try {
            val res = NetworkClient.apiService.scheduleRide(
                mapOf(
                    "pickup_address" to pickup,
                    "destination_address" to dest,
                    "pickup_latitude" to 28.6289,
                    "pickup_longitude" to 77.3710,
                    "destination_latitude" to 28.6328,
                    "destination_longitude" to 77.2197,
                    "scheduled_pickup_time" to pickupTimeIso
                )
            )
            Result.success(res.isSuccessful)
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

    suspend fun saveCustomerPreferences(
        tempCelsius: Float? = null,
        quietRide: Boolean? = null,
        preferredMusic: String? = null,
        wheelchair: Boolean? = null,
        petFriendly: Boolean? = null,
        extraLegroom: Boolean? = null,
        corporateNotes: String? = null
    ): Result<Boolean> {
        return try {
            val payload = mutableMapOf<String, Any>()
            tempCelsius?.let { payload["temperature_celsius"] = it }
            quietRide?.let { payload["quiet_ride"] = it }
            preferredMusic?.let { payload["preferred_music"] = it }
            wheelchair?.let { payload["wheelchair_access_required"] = it }
            petFriendly?.let { payload["pet_friendly_preferred"] = it }
            extraLegroom?.let { payload["extra_legroom_required"] = it }
            corporateNotes?.let { payload["accessibility_notes"] = it }

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
        email: String,
        phone: String? = null,
        govtId: String? = null
    ): Result<Boolean> {
        return try {
            val res = NetworkClient.apiService.registerProfile(
                RegisterCustomerRequest(
                    phoneNumber = phone?.trim(),
                    fullName = name.trim(),
                    email = email.trim(),
                    govtIdNumber = govtId?.trim()
                )
            )
            if (res.isSuccessful) {
                Result.success(true)
            } else {
                val errBody = res.errorBody()?.string() ?: "KYC sync failed: HTTP ${res.code()}"
                Result.failure(Exception(errBody))
            }
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

    suspend fun setAudioRecordingConsent(enabled: Boolean, rideId: String? = null): Result<Boolean> {
        return try {
            val res = NetworkClient.apiService.setAudioRecordingConsent(
                mapOf("audio_consent" to enabled, "ride_id" to (rideId ?: activeRide.value?.id ?: "ride-latest"))
            )
            Result.success(res.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
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
}

