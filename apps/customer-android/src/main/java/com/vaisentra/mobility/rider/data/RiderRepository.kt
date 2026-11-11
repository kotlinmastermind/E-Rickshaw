package com.vaisentra.mobility.rider.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object RiderRepository {

    private val _categories = MutableStateFlow<List<VehicleCategoryDto>>(emptyList())
    val categories: StateFlow<List<VehicleCategoryDto>> = _categories.asStateFlow()

    private val _activeRide = MutableStateFlow<ActiveRideDto?>(null)
    val activeRide: StateFlow<ActiveRideDto?> = _activeRide.asStateFlow()

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

    private val _emergencyContacts = MutableStateFlow<List<EmergencyContactDto>>(
        listOf(
            EmergencyContactDto("c1", "Pooja Sharma", "+91 98765 43210", "Sister"),
            EmergencyContactDto("c2", "Vikram Malhotra", "+91 99887 76655", "Colleague"),
            EmergencyContactDto("c3", "Delhi Police Women Helpline", "1091", "Emergency Service")
        )
    )
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
        paymentMethod: String = "UPI"
    ): Result<ActiveRideDto> {
        val ride = ActiveRideDto(
            id = "ride-" + System.currentTimeMillis().toString().takeLast(6),
            status = "REQUESTED",
            pickupAddress = pickupAddress,
            destinationAddress = destinationAddress,
            totalFareCents = 12900,
            otpCode = "7294",
            driverName = "Ram Lal (EV Pilot)",
            driverPhone = "+91 98112 34567",
            driverRating = 4.92,
            vehiclePlate = "DL-1ER-4921",
            vehicleModel = "Standard E-Rickshaw (Zero Emissions)",
            speedKmh = 24.5
        )
        _activeRide.value = ride
        return Result.success(ride)
    }

    fun updateActiveRideStatus(newStatus: String) {
        _activeRide.value = _activeRide.value?.copy(status = newStatus)
    }

    fun topupWalletBalance(amount: Double) {
        val current = _wallet.value
        val newBal = current.balanceRupees + amount
        _wallet.value = current.copy(
            balanceRupees = newBal,
            balanceCents = (newBal * 100).toInt()
        )
    }

    fun addEmergencyContact(contact: EmergencyContactDto) {
        _emergencyContacts.value = _emergencyContacts.value + contact
    }

    fun removeEmergencyContact(contactId: String) {
        _emergencyContacts.value = _emergencyContacts.value.filter { it.id != contactId }
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
}

