package com.vaisentra.mobility.rider.data

import retrofit2.Response
import retrofit2.http.*

@JvmSuppressWildcards
interface RiderApiService {

    // --- Authentication & KYC ---
    @POST("api/v1/auth/otp/send")
    suspend fun sendOtp(@Body request: OtpSendRequest): Response<Map<String, Any>>

    @POST("api/v1/auth/otp/verify")
    suspend fun verifyOtp(@Body request: OtpVerifyRequest): Response<TokenResponse>

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<TokenResponse>

    // RiderApiService.kt mein add karein:
   // @POST("api/v1/user/biometric/toggle")
    //suspend fun toggleBiometric(@Body body: Map<String, Boolean>): Response<Boolean>

    // Fix return type (Backend returns {"biometric_enabled": true})
    @POST("api/v1/user/biometric/toggle")
    suspend fun toggleBiometric(@Body body: Map<String, Boolean>): Response<Map<String, Boolean>>

    // RiderApiService.kt mein add karein:
    @GET("api/v1/fares/rules")
    suspend fun getFareRules(): Response<List<FareRuleDto>>
    @GET("api/v1/vehicles/nearby")
    suspend fun getNearbyVehicles(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("radius_km") radiusKm: Double = 5.0
    ): Response<NearbyVehiclesResponse>

    // Live Reverse Geocode (GPS -> Address)
    @POST("api/v1/location/reverse-geocode")
    suspend fun reverseGeocode(@Body request: ReverseGeocodeRequest): Response<ReverseGeocodeResponse>

    //@POST("api/v1/customers/profile")
    //suspend fun registerProfile(@Body request: RegisterCustomerRequest): Response<UserProfileDto>

    @POST("api/v1/auth/kyc/customer")
    suspend fun registerProfile(@Body request: RegisterCustomerRequest): Response<UserProfileDto>

    @GET("api/v1/customers/kyc")
    suspend fun getCustomerKycStatus(): Response<Map<String, Any>>

    @Multipart
    @POST("api/v1/customers/kyc/documents")
    suspend fun submitCustomerKycDocument(
        @Part("document_type") documentType: okhttp3.RequestBody,
        @Part("consent_to_processing") consentToProcessing: okhttp3.RequestBody,
        @Part file: okhttp3.MultipartBody.Part
    ): Response<Map<String, Any>>

    @DELETE("api/v1/customers/kyc/documents")
    suspend fun withdrawCustomerKycDocuments(): Response<Map<String, Any>>

    @GET("api/v1/customers/profile")
    suspend fun getProfile(): Response<UserProfileDto>

    @GET("api/v1/auth/me")
    suspend fun getCurrentUserMe(): Response<UserMeResponse>

    @PUT("api/v1/auth/avatar")
    suspend fun updateProfileAvatar(@Body body: Map<String, String>): Response<okhttp3.ResponseBody>

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
    suspend fun getActiveRide(): Response<ActiveRideWrapperDto>

    @POST("api/v1/rides/{ride_id}/cancel")
    suspend fun cancelRide(@Path("ride_id") rideId: String, @Body body: Map<String, String>): Response<Map<String, Any>>

    @GET("api/v1/rides/history")
    suspend fun getRideHistory(): Response<List<ActiveRideDto>>

    @GET("api/v1/rides/{ride_id}/receipt")
    suspend fun getRideReceipt(@Path("ride_id") rideId: String): Response<RideReceiptDto>

    @POST("api/v1/rides/{ride_id}/reschedule")
    suspend fun rescheduleRide(
        @Path("ride_id") rideId: String,
        @Body request: RescheduleRideRequest
    ): Response<Map<String, Any>>

    @POST("api/v1/rides/{ride_id}/sync-flight")
    suspend fun syncFlightDelay(
        @Path("ride_id") rideId: String,
        @Query("flight_number") flightNumber: String
    ): Response<Map<String, Any>>

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

        // --- Razorpay Verified Wallet Top-up ---
    @POST("api/v1/wallets/topup/create-order")
    suspend fun createWalletTopupOrder(@Body request: TopupCreateOrderRequest): Response<TopupOrderResponse>

    @POST("api/v1/wallets/topup/verify")
    suspend fun verifyWalletTopup(@Body request: TopupVerifyRequest): Response<TopupVerifyResponse>

    // --- Razorpay Verified Ride Payment ---
    @POST("api/v1/payments/razorpay/create-order")
    suspend fun createRidePaymentOrder(@Body request: RidePaymentOrderRequest): Response<RidePaymentOrderResponse>

    @POST("api/v1/payments/razorpay/verify")
    suspend fun verifyRidePayment(@Body request: RidePaymentVerifyRequest): Response<RidePaymentVerifyResponse>

    @GET("api/v1/payments/ride/{ride_id}/status")
    suspend fun getRidePaymentStatus(@Path("ride_id") rideId: String): Response<RidePaymentStatusDto>
    // --- Safety & Emergency SOS ---
    @POST("api/v1/safety/sos")
    suspend fun triggerSos(@Body request: SosTriggerRequest): Response<Map<String, Any>>

    @GET("api/v1/safety/contacts")
    suspend fun getEmergencyContacts(): Response<List<EmergencyContactDto>>

    @POST("api/v1/safety/contacts")
    suspend fun addEmergencyContact(@Body contact: Map<String, String>): Response<EmergencyContactDto>
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

    @POST("api/v1/customers/device-token")
    suspend fun registerDeviceToken(@Body request: DeviceTokenRegisterRequest): Response<Map<String, Any>>

    @HTTP(method = "DELETE", path = "api/v1/customers/device-token", hasBody = true)
    suspend fun deactivateDeviceToken(@Body request: DeviceTokenDeactivateRequest): Response<Map<String, Any>>

    @POST("api/v1/auth/logout")
    suspend fun logoutSession(@Body request: RiderLogoutRequest = RiderLogoutRequest()): Response<Map<String, Any>>

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
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<Map<String, Any>>

    @POST("api/v1/loyalty-feedback/rides/{ride_id}/tip")
    suspend fun tipDriver(
        @Path("ride_id") rideId: String,
        @Body body: Map<String, Int>
    ): Response<Map<String, Any>>

    @POST("api/v1/rides/{ride_id}/disputes")
    suspend fun submitDispute(
        @Path("ride_id") rideId: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<Map<String, Any>>

    @POST("api/v1/rides/{ride_id}/lost-property")
    suspend fun reportLostProperty(
        @Path("ride_id") rideId: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<Map<String, Any>>

    @POST("api/v1/rides/schedule")
    suspend fun scheduleRide(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<Map<String, Any>>

    @GET("api/v1/customers/preferences")
    suspend fun getCustomerPreferences(): Response<Map<String, Any>>

    @PUT("api/v1/customers/preferences")
    suspend fun updatePreferences(
        @Body body: Map<String, @JvmSuppressWildcards Any>
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

    @POST("api/v1/rides/{ride_id}/send-invoice-email")
    suspend fun sendInvoiceEmail(
        @Path("ride_id") rideId: String,
        @Body body: Map<String, String>
    ): Response<Map<String, Any>>

    // --- User Governance, Password Reset & Security ---
    @POST("api/v1/auth/recovery/reset-password")
    suspend fun resetForgotPassword(@Body body: Map<String, String>): Response<Map<String, Any>>

    @PUT("api/v1/auth/change-password")
    suspend fun changePassword(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("api/v1/user/passkey/register")
    suspend fun registerPasskey(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("api/v1/user/app-lock/pin")
    suspend fun setAppLockPin(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("api/v1/user/app-lock/verify")
    suspend fun verifyAppLockPin(@Body body: Map<String, String>): Response<Map<String, Any>>

    @GET("api/v1/user/trusted-devices")
    suspend fun getTrustedDevices(): Response<List<Map<String, Any>>>

    @POST("api/v1/user/data-export")
    suspend fun requestDataExport(): Response<Map<String, Any>>

    @POST("api/v1/user/deletion/request")
    suspend fun requestAccountDeletion(@Body body: Map<String, String>): Response<Map<String, Any>>

    // --- Disconnected Features API Hooks ---
    @POST("api/v1/fares/price-lock")
    suspend fun lockPrice(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<Map<String, Any>>

    @POST("api/v1/promos/validate")
    suspend fun validatePromo(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<Map<String, Any>>

    @POST("api/v1/safety/recording/consent")
    suspend fun setAudioRecordingConsent(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<Map<String, Any>>

    @POST("api/v1/safety/anomaly/route")
    suspend fun reportRouteAnomaly(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<Map<String, Any>>

    @POST("api/v1/rides/{ride_id}/share")
    suspend fun shareRide(@Path("ride_id") rideId: String, @Body body: Map<String, @JvmSuppressWildcards Any>): Response<Map<String, Any>>

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
    suspend fun configureAutoTopup(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<Map<String, Any>>

    @POST("api/v1/wallets/gift-cards/purchase")
    suspend fun purchaseGiftCard(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<Map<String, Any>>

    @POST("api/v1/wallets/gift-cards/redeem")
    suspend fun redeemGiftCard(@Body body: Map<String, String>): Response<Map<String, Any>>

    @GET("api/v1/wallets/export")
    suspend fun exportWalletCsv(): Response<okhttp3.ResponseBody>

    @POST("api/v1/rides/{ride_id}/destination-change")
    suspend fun requestDestinationChange(
        @Path("ride_id") rideId: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<Map<String, Any>>

    // --- Corporate B2B Billing & Enterprise Accounts ---
    @POST("api/v1/corporate/accounts")
    suspend fun registerCorporateAccount(@Body req: CorporateRegistrationRequest): Response<CorporateAccountDto>

    @GET("api/v1/corporate/accounts/me")
    suspend fun getMyCorporateAccount(): Response<CorporateAccountDto>

    @PUT("api/v1/corporate/accounts/me")
    suspend fun updateMyCorporateAccount(@Body body: Map<String, String>): Response<CorporateAccountDto>

    @POST("api/v1/corporate/accounts/me/submit")
    suspend fun submitCorporateAccountForApproval(): Response<Map<String, Any>>

    @GET("api/v1/corporate/accounts/me/employees")
    suspend fun listCorporateEmployees(): Response<List<CorporateEmployeeDto>>

    @GET("api/v1/corporate/accounts/me/invoices")
    suspend fun listCorporateInvoices(): Response<List<CorporateInvoiceDto>>

    @GET("api/v1/corporate/accounts/me/invoices/{invoice_id}")
    suspend fun getCorporateInvoiceDetail(@Path("invoice_id") invoiceId: String): Response<CorporateInvoiceDetailDto>

    // --- Audio Safety Recording Endpoints ---
    @POST("api/v1/rides/{ride_id}/audio-consent")
    suspend fun recordAudioConsent(
        @Path("ride_id") rideId: String,
        @Body body: AudioConsentRequest
    ): Response<AudioConsentResponse>

    @GET("api/v1/rides/{ride_id}/audio-consent")
    suspend fun getAudioConsentStatus(
        @Path("ride_id") rideId: String
    ): Response<AudioRecordingStatusResponse>

    @POST("api/v1/rides/{ride_id}/audio-consent/withdraw")
    suspend fun withdrawAudioConsent(
        @Path("ride_id") rideId: String,
        @Body body: AudioConsentWithdrawRequest
    ): Response<Map<String, Any>>

    @POST("api/v1/rides/{ride_id}/audio-recording/authorize")
    suspend fun authorizeAudioRecording(
        @Path("ride_id") rideId: String
    ): Response<Map<String, Any>>

    @POST("api/v1/audio-recordings/{session_id}/start")
    suspend fun startAudioRecording(
        @Path("session_id") sessionId: String
    ): Response<Map<String, Any>>

    @POST("api/v1/audio-recordings/{session_id}/heartbeat")
    suspend fun sendAudioRecordingHeartbeat(
        @Path("session_id") sessionId: String
    ): Response<Map<String, Any>>

    @POST("api/v1/audio-recordings/{session_id}/stop")
    suspend fun stopAudioRecording(
        @Path("session_id") sessionId: String,
        @Body body: Map<String, String>
    ): Response<Map<String, Any>>

    @retrofit2.http.Multipart
    @POST("api/v1/audio-recordings/{session_id}/segments")
    suspend fun uploadAudioSegment(
        @Path("session_id") sessionId: String,
        @retrofit2.http.Part("sequence") sequence: okhttp3.RequestBody,
        @retrofit2.http.Part("duration_seconds") durationSeconds: okhttp3.RequestBody,
        @retrofit2.http.Part("encryption_nonce") encryptionNonce: okhttp3.RequestBody,
        @retrofit2.http.Part("encryption_tag") encryptionTag: okhttp3.RequestBody,
        @retrofit2.http.Part file: okhttp3.MultipartBody.Part
    ): Response<AudioSegmentUploadResponse>

    // --- Gift Cards Suite ---
    @GET("api/v1/gift-cards/config")
    suspend fun getGiftCardConfig(): Response<GiftCardConfigDto>

    @POST("api/v1/gift-cards/purchase")
    suspend fun purchaseGiftCard(@Body req: GiftCardPurchaseRequest): Response<GiftCardPurchaseResponse>

    @POST("api/v1/gift-cards/check-balance")
    suspend fun checkGiftCardBalance(@Body req: GiftCardBalanceRequest): Response<GiftCardBalanceResponse>

    @POST("api/v1/gift-cards/redeem")
    suspend fun redeemGiftCard(@Body req: GiftCardRedeemRequest): Response<GiftCardRedeemResponse>

    @GET("api/v1/gift-cards/my")
    suspend fun getMyGiftCards(@Query("filter_type") filterType: String = "ALL"): Response<List<GiftCardSummaryDto>>

    @GET("api/v1/gift-cards/{id}")
    suspend fun getGiftCardDetails(@Path("id") id: String): Response<GiftCardDetailDto>

        // --- Saved Places (Favorites) Full CRUD ---
    @GET("api/v1/favorites")
    suspend fun getSavedPlaces(): Response<List<SavedPlaceDto>>

    @POST("api/v1/favorites")
    suspend fun addSavedPlace(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<Map<String, Any>>

    @PUT("api/v1/favorites/{fav_id}")
    suspend fun updateSavedPlace(
        @Path("fav_id") favId: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<Map<String, Any>>

    @DELETE("api/v1/favorites/{fav_id}")
    suspend fun deleteSavedPlace(
        @Path("fav_id") favId: String
    ): Response<Map<String, Any>>

    // --- Live Shared Pool Estimate ---
    @POST("api/v1/pools/estimate")
    suspend fun getSharedPoolEstimate(
        @Body request: SharedEstimateRequestDto
    ): Response<SharedEstimateResponseDto>
}

data class MapSearchRequest(
    val query: String,
    val proximity_lat: Double? = null,
    val proximity_lng: Double? = null
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

