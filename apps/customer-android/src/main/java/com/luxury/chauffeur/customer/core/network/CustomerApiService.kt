package com.luxury.chauffeur.customer.core.network

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.*

data class OtpRequestPayload(
    @SerializedName("phone_number") val phoneNumber: String
)

data class OtpVerifyPayload(
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("otp_code") val otpCode: String,
    @SerializedName("role") val role: String = "customer"
)

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("user") val user: UserDto
)

data class UserDto(
    @SerializedName("id") val id: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("full_name") val fullName: String?,
    @SerializedName("email") val email: String?,
    @SerializedName("role") val role: String
)

data class CustomerProfileDto(
    @SerializedName("user_id") val userId: String,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("email") val email: String?,
    @SerializedName("preferred_payment_method") val preferredPaymentMethod: String,
    @SerializedName("emergency_contact_phone") val emergencyContactPhone: String?,
    @SerializedName("rating_average") val ratingAverage: Double,
    @SerializedName("rating_count") val ratingCount: Int
)

data class ProfileUpdateRequest(
    @SerializedName("full_name") val fullName: String?,
    @SerializedName("email") val email: String?,
    @SerializedName("emergency_contact_phone") val emergencyContactPhone: String?,
    @SerializedName("preferred_payment_method") val preferredPaymentMethod: String?
)

data class VehicleCategoryDto(
    @SerializedName("id") val id: String,
    @SerializedName("code") val code: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String,
    @SerializedName("icon_url") val iconUrl: String,
    @SerializedName("capacity") val capacity: Int
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
    @SerializedName("fare_rule_id") val fareRuleId: String,
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("distance_km") val distanceKm: Double,
    @SerializedName("estimated_duration_minutes") val estimatedDurationMinutes: Double,
    @SerializedName("estimated_fare_cents") val estimatedFareCents: Int,
    @SerializedName("currency") val currency: String,
    @SerializedName("expires_at") val expiresAt: String,
    @SerializedName("route_polyline") val routePolyline: String
)

data class RideRequestPayload(
    @SerializedName("estimate_id") val estimateId: String,
    @SerializedName("pickup_address") val pickupAddress: String,
    @SerializedName("destination_address") val destinationAddress: String,
    @SerializedName("payment_method") val paymentMethod: String
)

data class RideResponseDto(
    @SerializedName("id") val id: String,
    @SerializedName("status") val status: String,
    @SerializedName("pickup_address") val pickupAddress: String,
    @SerializedName("pickup_latitude") val pickupLatitude: Double,
    @SerializedName("pickup_longitude") val pickupLongitude: Double,
    @SerializedName("destination_address") val destinationAddress: String,
    @SerializedName("destination_latitude") val destinationLatitude: Double,
    @SerializedName("destination_longitude") val destinationLongitude: Double,
    @SerializedName("estimated_fare_cents") val estimatedFareCents: Int,
    @SerializedName("final_fare_cents") val finalFareCents: Int?,
    @SerializedName("payment_method") val paymentMethod: String,
    @SerializedName("start_otp") val startOtp: String?,
    @SerializedName("driver") val driver: AssignedDriverDto?
)

data class AssignedDriverDto(
    @SerializedName("driver_id") val driverId: String,
    @SerializedName("name") val name: String,
    @SerializedName("phone") val phone: String,
    @SerializedName("license_number") val licenseNumber: String,
    @SerializedName("rating") val rating: Double,
    @SerializedName("total_trips") val totalTrips: Int
)

data class TrackingDto(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("status") val status: String,
    @SerializedName("driver_latitude") val driverLatitude: Double,
    @SerializedName("driver_longitude") val driverLongitude: Double,
    @SerializedName("heading") val heading: Double
)

data class PaymentIntentRequest(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("idempotency_key") val idempotencyKey: String
)

data class PaymentIntentResponse(
    @SerializedName("payment_id") val paymentId: String,
    @SerializedName("order_id") val orderId: String,
    @SerializedName("amount_cents") val amountCents: Int,
    @SerializedName("currency") val currency: String,
    @SerializedName("client_token") val clientToken: String,
    @SerializedName("provider") val provider: String
)

data class RateDriverRequest(
    @SerializedName("rating") val rating: Int,
    @SerializedName("feedback") val feedback: String?
)

data class NotificationDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("body") val body: String,
    @SerializedName("event_type") val eventType: String,
    @SerializedName("is_read") val isRead: Boolean,
    @SerializedName("created_at") val createdAt: String
)

data class SharedEstimateRequest(
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("pickup_latitude") val pickupLatitude: Double,
    @SerializedName("pickup_longitude") val pickupLongitude: Double,
    @SerializedName("destination_latitude") val destinationLatitude: Double,
    @SerializedName("destination_longitude") val destinationLongitude: Double,
    @SerializedName("seats_requested") val seatsRequested: Int = 1
)

data class SharedEstimateResponse(
    @SerializedName("estimate_id") val estimateId: String,
    @SerializedName("solo_fare_cents") val soloFareCents: Int,
    @SerializedName("shared_discount_percentage") val sharedDiscountPercentage: Double,
    @SerializedName("shared_fare_cents") val sharedFareCents: Int,
    @SerializedName("guest_savings_cents") val guestSavingsCents: Int,
    @SerializedName("currency") val currency: String,
    @SerializedName("seats_requested") val seatsRequested: Int,
    @SerializedName("total_payable_cents") val totalPayableCents: Int,
    @SerializedName("expires_at") val expiresAt: String
)

data class BookSharedRideRequest(
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("estimate_id") val estimateId: String,
    @SerializedName("pickup_address") val pickupAddress: String,
    @SerializedName("pickup_latitude") val pickupLatitude: Double,
    @SerializedName("pickup_longitude") val pickupLongitude: Double,
    @SerializedName("destination_address") val destinationAddress: String,
    @SerializedName("destination_latitude") val destinationLatitude: Double,
    @SerializedName("destination_longitude") val destinationLongitude: Double,
    @SerializedName("seats_reserved") val seatsReserved: Int = 1,
    @SerializedName("fallback_policy") val fallbackPolicy: String = "CREATE_NEW_POOL"
)

data class WalletDto(
    @SerializedName("user_id") val userId: String,
    @SerializedName("balance_cents") val balanceCents: Int,
    @SerializedName("currency") val currency: String,
    @SerializedName("is_frozen") val isFrozen: Boolean
)

data class TopupWalletRequest(
    @SerializedName("amount_cents") val amountCents: Int,
    @SerializedName("idempotency_key") val idempotencyKey: String,
    @SerializedName("payment_reference") val paymentReference: String? = null
)

data class PromoValidateRequest(
    @SerializedName("code") val code: String,
    @SerializedName("fare_cents") val fareCents: Int
)

data class PromoValidateResponse(
    @SerializedName("valid") val valid: Boolean,
    @SerializedName("code") val code: String,
    @SerializedName("discount_cents") val discountCents: Int,
    @SerializedName("final_fare_cents") val finalFareCents: Int
)

interface CustomerApiService {
    @POST("api/v1/auth/otp/request")
    suspend fun requestOtp(@Body payload: OtpRequestPayload): Response<Map<String, Any>>

    @POST("api/v1/auth/otp/verify")
    suspend fun verifyOtp(@Body payload: OtpVerifyPayload): Response<TokenResponse>

    @GET("api/v1/customers/profile")
    suspend fun getProfile(): Response<CustomerProfileDto>

    @PUT("api/v1/customers/profile")
    suspend fun updateProfile(@Body payload: ProfileUpdateRequest): Response<Map<String, Any>>

    @GET("api/v1/vehicles/categories")
    suspend fun getVehicleCategories(): Response<List<VehicleCategoryDto>>

    @GET("api/v1/location/autocomplete")
    suspend fun autocompletePlaces(@Query("q") query: String): Response<Map<String, Any>>

    @POST("api/v1/fares/estimate")
    suspend fun calculateFareEstimate(@Body payload: FareEstimateRequest): Response<FareEstimateResponse>

    @POST("api/v1/pools/estimate")
    suspend fun getSharedEstimate(@Body payload: SharedEstimateRequest): Response<SharedEstimateResponse>

    @POST("api/v1/rides/request")
    suspend fun requestRide(@Body payload: RideRequestPayload): Response<Map<String, Any>>

    @POST("api/v1/pools/book")
    suspend fun bookSharedRide(@Body payload: BookSharedRideRequest): Response<Map<String, Any>>

    @GET("api/v1/rides/{id}")
    suspend fun getRideDetails(@Path("id") rideId: String): Response<RideResponseDto>

    @GET("api/v1/rides/{id}/track")
    suspend fun trackRide(@Path("id") rideId: String): Response<TrackingDto>

    @POST("api/v1/rides/{id}/cancel")
    suspend fun cancelRide(@Path("id") rideId: String, @Body payload: Map<String, String>): Response<Map<String, Any>>

    @POST("api/v1/payments/intent")
    suspend fun createPaymentIntent(@Body payload: PaymentIntentRequest): Response<PaymentIntentResponse>

    @POST("api/v1/rides/{id}/rate")
    suspend fun rateDriver(@Path("id") rideId: String, @Body payload: RateDriverRequest): Response<Map<String, Any>>

    @GET("api/v1/customers/notifications")
    suspend fun getNotifications(): Response<List<NotificationDto>>

    @GET("api/v1/wallets/me")
    suspend fun getWallet(): Response<WalletDto>

    @POST("api/v1/wallets/topup")
    suspend fun topupWallet(@Body payload: TopupWalletRequest): Response<Map<String, Any>>

    @POST("api/v1/promos/validate")
    suspend fun validatePromo(@Body payload: PromoValidateRequest): Response<PromoValidateResponse>
}
