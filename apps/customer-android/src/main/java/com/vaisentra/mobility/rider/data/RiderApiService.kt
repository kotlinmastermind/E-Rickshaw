package com.vaisentra.mobility.rider.data

import retrofit2.Response
import retrofit2.http.*

interface RiderApiService {

    // --- Authentication & KYC ---
    @POST("api/v1/auth/otp/send")
    suspend fun sendOtp(@Body request: OtpSendRequest): Response<Map<String, Any>>

    @POST("api/v1/auth/otp/verify")
    suspend fun verifyOtp(@Body request: OtpVerifyRequest): Response<TokenResponse>

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<TokenResponse>

    @POST("api/v1/customer/profile")
    suspend fun registerProfile(@Body request: RegisterCustomerRequest): Response<UserProfileDto>

    @GET("api/v1/customer/profile")
    suspend fun getProfile(): Response<UserProfileDto>

    // --- Vehicle Categories & Fares ---
    @GET("api/v1/vehicles/categories")
    suspend fun getVehicleCategories(): Response<List<VehicleCategoryDto>>

    @POST("api/v1/fares/estimate")
    suspend fun getFareEstimate(@Body request: FareEstimateRequest): Response<FareEstimateDetailDto>

    @POST("api/v1/fares/compare")
    suspend fun compareVehicleFares(@Body request: FareCompareRequest): Response<List<VehicleFareComparisonDto>>

    // --- Rides & Active Booking ---
    @POST("api/v1/rides/request")
    suspend fun requestRide(@Body request: RideBookingRequest): Response<ActiveRideDto>

    @GET("api/v1/rides/active")
    suspend fun getActiveRide(): Response<ActiveRideDto>

    @POST("api/v1/rides/{ride_id}/cancel")
    suspend fun cancelRide(@Path("ride_id") rideId: String, @Query("reason") reason: String): Response<Map<String, Any>>

    @GET("api/v1/rides/history")
    suspend fun getRideHistory(): Response<List<ActiveRideDto>>

    @GET("api/v1/rides/{ride_id}/receipt")
    suspend fun getRideReceipt(@Path("ride_id") rideId: String): Response<RideReceiptDto>

    // --- Eco-Pool Shared Transit ---
    @POST("api/v1/pools/book")
    suspend fun bookPool(@Body request: PoolBookingRequest): Response<PoolTripDto>

    @GET("api/v1/pools/active")
    suspend fun getActivePool(): Response<PoolTripDto>

    // --- Wallet & Fintech ---
    @GET("api/v1/wallets/me")
    suspend fun getWalletBalance(): Response<WalletBalanceDto>

    @POST("api/v1/wallets/topup")
    suspend fun topupWallet(@Body request: TopupRequest): Response<WalletBalanceDto>

    @POST("api/v1/payments/split")
    suspend fun splitFare(@Body request: SplitFareRequest): Response<Map<String, Any>>

    // --- Safety & Emergency SOS ---
    @POST("api/v1/safety/sos")
    suspend fun triggerSos(@Body request: SosTriggerRequest): Response<Map<String, Any>>

    @GET("api/v1/safety/contacts")
    suspend fun getEmergencyContacts(): Response<List<EmergencyContactDto>>

    @POST("api/v1/safety/contacts")
    suspend fun addEmergencyContact(@Body contact: EmergencyContactDto): Response<EmergencyContactDto>

    @POST("api/v1/safety/incident")
    suspend fun reportIncident(@Body request: IncidentReportRequest): Response<Map<String, Any>>

    // --- Support & Tickets ---
    @GET("api/v1/support/tickets")
    suspend fun getSupportTickets(): Response<List<SupportTicketDto>>

    // --- Loyalty & Notifications ---
    @GET("api/v1/loyalty/summary")
    suspend fun getLoyaltySummary(): Response<LoyaltySummaryDto>

    @GET("api/v1/notifications")
    suspend fun getNotifications(): Response<List<NotificationItemDto>>

    @GET("api/v1/favorites")
    suspend fun getSavedPlaces(): Response<List<SavedPlaceDto>>
}

