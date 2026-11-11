package com.vaisentra.mobility.core.model

import com.google.gson.annotations.SerializedName

/**
 * Standardized Shared DTOs and API Contracts across Android apps (Customer & Driver) and Backend.
 * Eliminates duplicate data classes and ensures strict type consistency across client modules.
 */

data class IdentityLookupRequest(
    @SerializedName("identifier") val identifier: String,
    @SerializedName("role") val role: String = "customer"
)

data class IdentityLookupResponse(
    @SerializedName("status") val status: String,
    @SerializedName("message") val message: String? = null,
    @SerializedName("user_exists") val userExists: Boolean = false,
    @SerializedName("auth_methods") val authMethods: List<String> = emptyList(),
    @SerializedName("role") val role: String? = null
)

data class OtpRequest(
    @SerializedName("phone_number") val phoneNumber: String
)

data class OtpResponse(
    @SerializedName("message") val message: String? = null,
    @SerializedName("phone_masked") val phoneMasked: String? = null,
    @SerializedName("expires_in_seconds") val expiresInSeconds: Int = 300,
    @SerializedName("otp") val otp: String? = null,
    @SerializedName("debug_otp") val debugOtp: String? = null
)

data class OtpVerifyRequest(
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("otp_code") val otpCode: String,
    @SerializedName("role") val role: String = "customer"
)

data class PasswordLoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

data class PasswordRegisterRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("role") val role: String = "customer"
)

data class UserDto(
    @SerializedName("id") val id: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("full_name") val fullName: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("role") val role: String = "customer",
    @SerializedName("is_active") val isActive: Boolean = true,
    @SerializedName("tier") val tier: String? = "STANDARD",
    @SerializedName("email_verified") val emailVerified: Boolean = false,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    @SerializedName("biometric_enabled") val biometricEnabled: Boolean = false,
    @SerializedName("mfa_enabled") val mfaEnabled: Boolean = false,
    @SerializedName("has_pin") val hasPin: Boolean = false,
    @SerializedName("has_passkey") val hasPasskey: Boolean = false
)

data class AuthResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String = "bearer",
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("expires_in_minutes") val expiresInMinutes: Int = 10080,
    @SerializedName("user") val user: UserDto,
    @SerializedName("requires_profile_completion") val requiresProfileCompletion: Boolean = false
)

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String = "bearer",
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("user") val user: UserDto
)

data class PasswordLoginResponse(
    @SerializedName("access_token") val accessToken: String?,
    @SerializedName("token_type") val tokenType: String?,
    @SerializedName("user") val user: UserDto?,
    @SerializedName("mfa_required") val mfaRequired: Boolean = false,
    @SerializedName("mfa_challenge_token") val mfaChallengeToken: String? = null
)

data class VehicleCategoryDto(
    @SerializedName("id") val id: String = "cat_1",
    @SerializedName("code") val code: String = "SEDAN",
    @SerializedName("name") val name: String = "Executive Sedan",
    @SerializedName("description") val description: String? = "Chauffeur Driven",
    @SerializedName("icon_url") val iconUrl: String? = null,
    @SerializedName("capacity") val capacity: Int = 4,
    @SerializedName("sort_order") val sortOrder: Int = 1,
    @SerializedName("is_electric") val isElectric: Boolean = true,
    @SerializedName("emissions_g_per_km") val emissionsGPerKm: Double = 0.0,
    @SerializedName("legroom_inches") val legroomInches: Double? = 36.0,
    @SerializedName("quietness_db") val quietnessDb: Double? = 45.0,
    @SerializedName("luggage_large_capacity") val luggageLargeCapacity: Int? = null,
    @SerializedName("luggage_small_capacity") val luggageSmallCapacity: Int? = null,
    @SerializedName("has_wifi") val hasWifi: Boolean? = null,
    @SerializedName("has_massage_seats") val hasMassageSeats: Boolean? = null,
    @SerializedName("has_champagne_cooler") val hasChampagneCooler: Boolean? = null,
    @SerializedName("has_reclining_seats") val hasRecliningSeats: Boolean? = null,
    @SerializedName("step_free_height_cm") val stepFreeHeightCm: Double? = null,
    @SerializedName("pet_friendly") val petFriendly: Boolean? = null,
    @SerializedName("child_seat_available") val childSeatAvailable: Boolean? = null,
    @SerializedName("available_colors") val availableColors: List<String>? = null,
    @SerializedName("available_amenities") val availableAmenities: List<String>? = null,
    @SerializedName("photos_gallery") val photosGallery: List<String>? = null,
    @SerializedName("wheelchair_accessible") val wheelchairAccessible: Boolean = false,
    @SerializedName("air_purifier") val airPurifier: Boolean = true,
    @SerializedName("wifi_available") val wifiAvailable: Boolean = true,
    @SerializedName("beverage_service") val beverageService: Boolean = false,
    @SerializedName("climate_control") val climateControl: Boolean = true,
    @SerializedName("display_order") val displayOrder: Int = 1,
    @SerializedName("is_active") val isActive: Boolean = true,
    @SerializedName("base_fare_rupees") val baseFareRupees: Double? = null,
    @SerializedName("per_km_rate_rupees") val perKmRateRupees: Double? = null,
    @SerializedName("per_minute_rate_rupees") val perMinuteRateRupees: Double? = null,
    @SerializedName("minimum_fare_rupees") val minimumFareRupees: Double? = null,
    @SerializedName("surge_multiplier") val surgeMultiplier: Double? = 1.0,
    @SerializedName("estimated_fare_rupees") val estimatedFareRupees: Double? = null,
    @SerializedName("eta_minutes") val etaMinutes: Int? = 3
)

data class FareEstimateRequest(
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("pickup_latitude") val pickupLatitude: Double,
    @SerializedName("pickup_longitude") val pickupLongitude: Double,
    @SerializedName("destination_latitude") val destinationLatitude: Double,
    @SerializedName("destination_longitude") val destinationLongitude: Double
)

data class FareEstimateResponse(
    @SerializedName("estimate_id") val estimateId: String,
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("distance_km") val distanceKm: Double,
    @SerializedName("estimated_duration_minutes") val estimatedDurationMinutes: Double,
    @SerializedName("base_fare_rupees") val baseFareRupees: Double,
    @SerializedName("distance_charge_rupees") val distanceChargeRupees: Double,
    @SerializedName("time_charge_rupees") val timeChargeRupees: Double,
    @SerializedName("tax_rupees") val taxRupees: Double,
    @SerializedName("estimated_fare_rupees") val estimatedFareRupees: Double,
    @SerializedName("estimated_fare_cents") val estimatedFareCents: Long,
    @SerializedName("currency") val currency: String = "INR",
    @SerializedName("route_polyline") val routePolyline: String? = null,
    @SerializedName("confidence_level") val confidenceLevel: String = "HIGH",
    @SerializedName("seconds_remaining") val secondsRemaining: Int = 900
)

data class GenericApiResponse<T>(
    @SerializedName("success") val success: Boolean = true,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: T? = null,
    @SerializedName("error") val error: String? = null
)

// Shared Chat & Messaging DTOs
data class ChatMessageDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("sender_user_id") val senderUserId: String? = null,
    @SerializedName("sender_role") val senderRole: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("message_text") val messageText: String? = null,
    @SerializedName("sent_at") val sentAt: String? = null
)

data class SendChatMessagePayload(
    @SerializedName("message_text") val messageText: String
)

// Shared Safety, Incident & SOS DTOs
data class EmergencySosPayload(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("alert_type") val alertType: String = "SOS",
    @SerializedName("notes") val notes: String? = null
)

data class IncidentReportPayload(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("category") val category: String,
    @SerializedName("severity") val severity: String = "MEDIUM",
    @SerializedName("description") val description: String,
    @SerializedName("involved_party_role") val involvedPartyRole: String = "USER"
)

// Shared Privacy & Compliance DTOs
data class PrivacyDsarPayload(
    @SerializedName("request_type") val requestType: String,
    @SerializedName("reason") val reason: String
)

// Shared Telemetry & Location DTOs
data class LocationPingPayload(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("heading") val heading: Double? = null,
    @SerializedName("speed_mps") val speedMps: Double? = null,
    @SerializedName("accuracy_m") val accuracyM: Double? = null,
    @SerializedName("is_mock_location") val isMockLocation: Boolean = false,
    @SerializedName("location_source") val locationSource: String = "FUSED"
)

data class LocationBatchPayload(
    @SerializedName("locations") val locations: List<LocationPingPayload>
)

// Shared Driver & Chauffeur DTOs
data class DriverProfileDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("user_id") val userId: String,
    @SerializedName("driver_id") val driverId: String,
    @SerializedName("full_name") val fullName: String?,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("license_number") val licenseNumber: String,
    @SerializedName("approval_status") val approvalStatus: String,
    @SerializedName("rating_average") val ratingAverage: Double,
    @SerializedName("total_trips") val totalTrips: Int,
    @SerializedName("wallet_balance_cents") val walletBalanceCents: Int
) {
    val canonicalId: String get() = id ?: driverId
}

data class DriverAvailabilityPayload(
    @SerializedName("is_online") val isOnline: Boolean
)

data class DriverDocumentDto(
    @SerializedName("id") val id: String,
    @SerializedName("document_type") val documentType: String,
    @SerializedName("file_name") val fileName: String,
    @SerializedName("verification_status") val verificationStatus: String,
    @SerializedName("rejection_reason") val rejectionReason: String?,
    @SerializedName("created_at") val createdAt: String
)
