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

    @POST("api/v1/customers/profile")
    suspend fun registerProfile(@Body request: RegisterCustomerRequest): Response<UserProfileDto>

    @GET("api/v1/customers/profile")
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
    suspend fun cancelRide(@Path("ride_id") rideId: String, @Body body: Map<String, String>): Response<Map<String, Any>>

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

    @DELETE("api/v1/safety/contacts/{contact_id}")
    suspend fun deleteEmergencyContact(@Path("contact_id") contactId: String): Response<Map<String, Any>>

    @POST("api/v1/safety/incident")
    suspend fun reportIncident(@Body request: IncidentReportRequest): Response<Map<String, Any>>

    // --- Support & Tickets ---
    @GET("api/v1/support/tickets")
    suspend fun getSupportTickets(): Response<List<SupportTicketDto>>

    // --- Loyalty & Notifications ---
    @GET("api/v1/loyalty-feedback/loyalty/summary")
    suspend fun getLoyaltySummary(): Response<LoyaltySummaryDto>

    @GET("api/v1/notifications")
    suspend fun getNotifications(): Response<List<NotificationItemDto>>

    @GET("api/v1/favorites")
    suspend fun getSavedPlaces(): Response<List<SavedPlaceDto>>

    // --- Mapbox Location & Routes Integration ---
    @GET("api/v1/location/config")
    suspend fun getMapboxConfig(): Response<Map<String, Any>>

    @POST("api/v1/location/search")
    suspend fun searchPlacesMapbox(@Body request: MapSearchRequest): Response<MapSearchResponse>

    @POST("api/v1/location/route-preview")
    suspend fun getRoutePreviewMapbox(@Body request: MapRoutePreviewRequest): Response<MapRoutePreviewResponse>

    // --- Post-Trip Rating, Tip, Disputes, Schedule, Lost Property, Invoices ---
    @POST("api/v1/loyalty-feedback/rides/{ride_id}/rate")
    suspend fun rateDriver(
        @Path("ride_id") rideId: String,
        @Body body: Map<String, Any>
    ): Response<Map<String, Any>>

    @POST("api/v1/loyalty-feedback/rides/{ride_id}/tip")
    suspend fun tipDriver(
        @Path("ride_id") rideId: String,
        @Body body: Map<String, Int>
    ): Response<Map<String, Any>>

    @POST("api/v1/rides/{ride_id}/disputes")
    suspend fun submitDispute(
        @Path("ride_id") rideId: String,
        @Body body: Map<String, Any>
    ): Response<Map<String, Any>>

    @POST("api/v1/rides/{ride_id}/lost-property")
    suspend fun reportLostProperty(
        @Path("ride_id") rideId: String,
        @Body body: Map<String, Any>
    ): Response<Map<String, Any>>

    @POST("api/v1/rides/schedule")
    suspend fun scheduleRide(
        @Body body: Map<String, Any>
    ): Response<Map<String, Any>>

    @PUT("api/v1/customers/preferences")
    suspend fun updatePreferences(
        @Body body: Map<String, Any>
    ): Response<Map<String, Any>>

    @POST("api/v1/pools/{trip_id}/messages")
    suspend fun sendPoolMessage(
        @Path("trip_id") tripId: String,
        @Body body: Map<String, String>
    ): Response<Map<String, Any>>

    @GET("api/v1/rides/{ride_id}/tax-invoice")
    suspend fun getTaxInvoice(
        @Path("ride_id") rideId: String
    ): Response<Map<String, Any>>

    // --- User Governance, Password Reset & Security ---
    @POST("api/v1/auth/recovery/reset-password")
    suspend fun resetForgotPassword(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("api/v1/user/passkey/register")
    suspend fun registerPasskey(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("api/v1/user/app-lock/pin")
    suspend fun setAppLockPin(@Body body: Map<String, String>): Response<Map<String, Any>>

    @GET("api/v1/user/trusted-devices")
    suspend fun getTrustedDevices(): Response<List<Map<String, Any>>>

    @POST("api/v1/user/data-export")
    suspend fun requestDataExport(): Response<Map<String, Any>>

    @POST("api/v1/user/deletion/request")
    suspend fun requestAccountDeletion(@Body body: Map<String, String>): Response<Map<String, Any>>

    // --- Disconnected Features API Hooks ---
    @POST("api/v1/fares/price-lock")
    suspend fun lockPrice(@Body body: Map<String, Any>): Response<Map<String, Any>>

    @POST("api/v1/promos/validate")
    suspend fun validatePromo(@Body body: Map<String, Any>): Response<Map<String, Any>>

    @POST("api/v1/safety/recording/consent")
    suspend fun setAudioRecordingConsent(@Body body: Map<String, Any>): Response<Map<String, Any>>

    @POST("api/v1/safety/anomaly/route")
    suspend fun reportRouteAnomaly(@Body body: Map<String, Any>): Response<Map<String, Any>>

    @POST("api/v1/rides/{ride_id}/share")
    suspend fun shareRide(@Path("ride_id") rideId: String, @Body body: Map<String, Any>): Response<Map<String, Any>>

    @GET("api/v1/support/tickets/{ticket_id}/messages")
    suspend fun getTicketMessages(@Path("ticket_id") ticketId: String): Response<List<Map<String, Any>>>

    @POST("api/v1/support/tickets/{ticket_id}/messages")
    suspend fun sendTicketMessage(@Path("ticket_id") ticketId: String, @Body body: Map<String, String>): Response<Map<String, Any>>

    @GET("api/v1/safety/vehicle-credentials/{ride_id}")
    suspend fun getVehicleCredentials(@Path("ride_id") rideId: String): Response<Map<String, Any>>

    @GET("api/v1/chat/rides/{ride_id}/messages")
    suspend fun getInTripChatMessages(@Path("ride_id") rideId: String): Response<List<Map<String, Any>>>

    @POST("api/v1/chat/rides/{ride_id}/messages")
    suspend fun sendInTripChatMessage(@Path("ride_id") rideId: String, @Body body: Map<String, String>): Response<Map<String, Any>>

    // --- Advanced Wallets & Fintech ---
    @POST("api/v1/wallets/auto-topup/config")
    suspend fun configureAutoTopup(@Body body: Map<String, Any>): Response<Map<String, Any>>

    @POST("api/v1/wallets/gift-cards/purchase")
    suspend fun purchaseGiftCard(@Body body: Map<String, Any>): Response<Map<String, Any>>

    @POST("api/v1/wallets/gift-cards/redeem")
    suspend fun redeemGiftCard(@Body body: Map<String, String>): Response<Map<String, Any>>

    @GET("api/v1/wallets/export")
    suspend fun exportWalletCsv(): Response<okhttp3.ResponseBody>
}

data class MapSearchRequest(
    val query: String,
    val proximity_lat: Double? = 28.6139,
    val proximity_lng: Double? = 77.2090
)

data class MapSearchResult(
    val name: String,
    val full_address: String,
    val latitude: Double,
    val longitude: Double,
    val place_type: String? = null
)

data class MapSearchResponse(
    val results: List<MapSearchResult>,
    val count: Int
)

data class MapRoutePreviewRequest(
    val origin_lat: Double,
    val origin_lng: Double,
    val destination_lat: Double,
    val destination_lng: Double
)

data class MapRoutePreviewResponse(
    val success: Boolean = true,
    val distance_km: Double,
    val duration_minutes: Double,
    val polyline: String? = null,
    val coordinates: List<List<Double>>? = null
)

