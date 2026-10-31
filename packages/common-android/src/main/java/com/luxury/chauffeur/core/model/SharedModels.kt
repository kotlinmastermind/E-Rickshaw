package com.luxury.chauffeur.core.model

import com.google.gson.annotations.SerializedName

/**
 * Standardized Shared DTOs and API Contracts across Android apps and Backend.
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
    @SerializedName("email") val email: String? = null,
    @SerializedName("full_name") val fullName: String? = null,
    @SerializedName("role") val role: String = "customer",
    @SerializedName("is_active") val isActive: Boolean = true,
    @SerializedName("tier") val tier: String? = "STANDARD"
)

data class AuthResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String = "bearer",
    @SerializedName("expires_in_minutes") val expiresInMinutes: Int = 10080,
    @SerializedName("user") val user: UserDto,
    @SerializedName("requires_profile_completion") val requiresProfileCompletion: Boolean = false
)

data class DeviceTokenPayload(
    @SerializedName("fcm_token") val fcmToken: String,
    @SerializedName("device_os") val deviceOs: String = "ANDROID",
    @SerializedName("device_model") val deviceModel: String? = null
)

data class VehicleCategoryDto(
    @SerializedName("id") val id: String,
    @SerializedName("code") val code: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("icon_url") val iconUrl: String? = null,
    @SerializedName("capacity") val capacity: Int = 4,
    @SerializedName("sort_order") val sortOrder: Int = 1,
    @SerializedName("is_electric") val isElectric: Boolean = true,
    @SerializedName("emissions_g_per_km") val emissionsGPerKm: Double = 0.0,
    @SerializedName("legroom_inches") val legroomInches: Double = 36.0,
    @SerializedName("quietness_db") val quietnessDb: Double = 45.0
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

