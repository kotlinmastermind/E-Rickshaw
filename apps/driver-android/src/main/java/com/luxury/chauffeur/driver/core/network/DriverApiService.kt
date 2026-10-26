package com.luxury.chauffeur.driver.core.network

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.*

data class DriverOtpRequestPayload(
    @SerializedName("phone_number") val phoneNumber: String
)

data class DriverOtpVerifyPayload(
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("otp_code") val otpCode: String,
    @SerializedName("role") val role: String = "driver"
)

data class DriverTokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("user") val user: DriverUserDto
)

data class DriverUserDto(
    @SerializedName("id") val id: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("full_name") val fullName: String?,
    @SerializedName("role") val role: String
)

data class DriverProfileDto(
    @SerializedName("user_id") val userId: String,
    @SerializedName("driver_id") val driverId: String,
    @SerializedName("full_name") val fullName: String?,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("license_number") val licenseNumber: String,
    @SerializedName("approval_status") val approvalStatus: String,
    @SerializedName("rating_average") val ratingAverage: Double,
    @SerializedName("total_trips") val totalTrips: Int,
    @SerializedName("wallet_balance_cents") val walletBalanceCents: Int
)

data class DriverAvailabilityPayload(
    @SerializedName("is_online") val isOnline: Boolean
)

data class DriverLocationPayload(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("heading") val heading: Double? = null
)

data class StartTripPayload(
    @SerializedName("otp_code") val otpCode: String
)

data class FinishTripPayload(
    @SerializedName("actual_distance_km") val actualDistanceKm: Double,
    @SerializedName("actual_duration_minutes") val actualDurationMinutes: Double
)

data class DriverFirebaseVerifyPayload(
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("role") val role: String = "driver",
    @SerializedName("firebase_uid") val firebaseUid: String? = null,
    @SerializedName("id_token") val idToken: String? = null
)

interface DriverApiService {
    @POST("api/v1/auth/otp/request")
    suspend fun requestOtp(@Body payload: DriverOtpRequestPayload): Response<Map<String, Any>>

    @POST("api/v1/auth/otp/verify")
    suspend fun verifyOtp(@Body payload: DriverOtpVerifyPayload): Response<DriverTokenResponse>

    @POST("api/v1/auth/firebase/verify")
    suspend fun verifyFirebaseAuth(@Body payload: DriverFirebaseVerifyPayload): Response<DriverTokenResponse>

    @GET("api/v1/drivers/profile")
    suspend fun getProfile(): Response<DriverProfileDto>

    @POST("api/v1/drivers/availability")
    suspend fun setAvailability(@Body payload: DriverAvailabilityPayload): Response<Map<String, Any>>

    @POST("api/v1/drivers/location")
    suspend fun pingLocation(@Body payload: DriverLocationPayload): Response<Map<String, Any>>

    @POST("api/v1/rides/{id}/accept")
    suspend fun acceptRide(@Path("id") rideId: String): Response<Map<String, Any>>

    @POST("api/v1/rides/{id}/arrive")
    suspend fun notifyArrived(@Path("id") rideId: String): Response<Map<String, Any>>

    @POST("api/v1/rides/{id}/start")
    suspend fun startTrip(@Path("id") rideId: String, @Body payload: StartTripPayload): Response<Map<String, Any>>

    @POST("api/v1/rides/{id}/finish")
    suspend fun finishTrip(@Path("id") rideId: String, @Body payload: FinishTripPayload): Response<Map<String, Any>>
}
