package com.vaisentra.mobility.driver.data

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/**
 * DriverGo Retrofit API Service connected to FastAPI + Supabase Backend
 */
@JvmSuppressWildcards
interface DriverApiService {

    // --- Live Turn-by-Turn Navigation, Geocoding & Guidance ---
    @GET("api/v1/location/navigation-guidance")
    suspend fun getNavigationGuidance(
        @Query("origin_lat") originLat: Double,
        @Query("origin_lng") originLng: Double,
        @Query("dest_lat") destLat: Double,
        @Query("dest_lng") destLng: Double
    ): Response<NavigationGuidanceResponseDto>

    @POST("api/v1/location/reverse-geocode")
    suspend fun reverseGeocode(@Body request: DriverReverseGeocodeRequest): Response<DriverReverseGeocodeResponse>

    // --- Backend Health & Status ---
    @GET("health")
    suspend fun healthCheck(): Response<BackendHealthDto>

    // --- Pilot Authentication ---
    @POST("api/v1/auth/logout")
    suspend fun logoutSession(@Body request: DriverLogoutRequest = DriverLogoutRequest()): Response<Map<String, Any>>

    @POST("api/v1/drivers/device-token")
    suspend fun registerDeviceToken(@Body request: DeviceTokenRegisterRequest): Response<Map<String, Any>>

    @HTTP(method = "DELETE", path = "api/v1/drivers/device-token", hasBody = true)
    suspend fun deactivateDeviceToken(@Body request: DeviceTokenDeactivateRequest): Response<Map<String, Any>>
    @POST("api/v1/auth/token/refresh")
    suspend fun refreshToken(@Body request: TokenRefreshRequest): Response<DriverTokenResponse>

    @POST("api/v1/auth/otp/send")
    suspend fun sendOtp(@Body request: OtpSendRequest): Response<Map<String, Any>>

    @POST("api/v1/auth/otp/request")
    suspend fun requestOtp(@Body request: OtpSendRequest): Response<Map<String, Any>>

    @POST("api/v1/auth/otp/verify")
    suspend fun verifyOtp(@Body request: OtpVerifyRequest): Response<DriverTokenResponse>

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: DriverLoginRequest): Response<DriverLoginResponse>

    @POST("api/v1/auth/signup/driver")
    suspend fun registerDriver(@Body request: DriverSignupRequest): Response<DriverLoginResponse>

    @GET("api/v1/vehicles/categories")
    suspend fun getVehicleCategories(): Response<List<VehicleCategoryDto>>

    @POST("api/v1/auth/change-password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Response<Map<String, Any>>

    @POST("api/v1/auth/recovery/request")
    suspend fun requestRecovery(@Body request: RecoveryRequest): Response<Map<String, Any>>

    @POST("api/v1/auth/recovery/reset-password")
    suspend fun resetPassword(@Body request: PasswordResetRequest): Response<Map<String, Any>>

    @GET("api/v1/drivers/profile")
    suspend fun getProfile(): Response<DriverProfileDto>

    @GET("api/v1/drivers/documents")
    suspend fun getDocuments(): Response<List<DriverDocumentDto>>

    @Multipart
    @POST("api/v1/drivers/documents")
    suspend fun uploadDocument(
        @Part("document_type") documentType: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<Map<String, Any>>

    @GET("api/v1/drivers/documents/{document_id}/signed-url")
    suspend fun getDocumentSignedUrl(@Path("document_id") documentId: String): Response<Map<String, Any>>

    @Streaming
    @GET("api/v1/drivers/documents/{document_id}/content")
    suspend fun getDocumentContent(@Path("document_id") documentId: String): Response<ResponseBody>

    @GET("api/v1/driver-onboarding/vehicles")
    suspend fun getDriverVehicles(): Response<List<DriverVehicleDto>>

    @GET("api/v1/drivers/vehicle")
    suspend fun getActiveVehicle(): Response<DriverVehicleDto>

    @PUT("api/v1/drivers/vehicle")
    suspend fun updateActiveVehicle(@Body body: VehicleUpdateDto): Response<Map<String, Any>>

    // --- Duty, Availability & Telemetry ---
    @POST("api/v1/drivers/availability")
    suspend fun toggleAvailability(@Body payload: DutyStatusUpdateRequest): Response<Map<String, Any>>

    @POST("api/v1/drivers/location")
    suspend fun pingLocation(@Body request: DriverLocationPingRequest): Response<Map<String, Any>>

    @POST("api/v1/drivers/financials/shifts/start")
    suspend fun startShift(@Body request: StartShiftRequest): Response<Map<String, Any>>

    @POST("api/v1/drivers/financials/shifts/end")
    suspend fun endShift(@Body request: EndShiftRequest): Response<Map<String, Any>>

    @GET("api/v1/drivers/financials/shifts/status")
    suspend fun getShiftStatus(): Response<Map<String, Any>>

    @POST("api/v1/drivers/duty/vehicle-inspection")
    suspend fun submitPreShiftInspection(@Body body: PreShiftInspectionRequest): Response<Map<String, Any>>

    @GET("api/v1/drivers/duty/vehicle-inspection/latest")
    suspend fun getLatestPreShiftInspection(): Response<Map<String, Any>>

    @POST("api/v1/drivers/vehicle-inspections/start")
    suspend fun startVehicleInspection(@Body body: StartInspectionRequest = StartInspectionRequest()): Response<InspectionDraftDto>

    @GET("api/v1/drivers/vehicle-inspections/current")
    suspend fun getCurrentVehicleInspection(): Response<InspectionDraftDto>

    @GET("api/v1/drivers/vehicle-inspections/{inspection_id}")
    suspend fun getVehicleInspectionDetails(@Path("inspection_id") inspectionId: String): Response<InspectionDraftDto>

    @Multipart
    @POST("api/v1/drivers/vehicle-inspections/{inspection_id}/photos")
    suspend fun uploadVehicleInspectionPhoto(
        @Path("inspection_id") inspectionId: String,
        @Part("category") category: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<InspectionPhotoUploadResponseDto>

    @POST("api/v1/drivers/vehicle-inspections/{inspection_id}/submit")
    suspend fun submitFullVehicleInspection(
        @Path("inspection_id") inspectionId: String,
        @Body body: SubmitInspectionRequest
    ): Response<Map<String, Any>>

    // --- Dispatch Offers, Active Ride & Notifications ---
    @GET("api/v1/drivers/dispatch/offers")
    suspend fun getActiveOffers(): Response<List<DispatchOfferDto>>

    @GET("api/v1/rides/active")
    suspend fun getActiveRide(): Response<ActiveRideResponseDto>

    @GET("api/v1/notifications/inbox")
    suspend fun getNotificationsInbox(
        @Query("unread_only") unreadOnly: Boolean = false,
        @Query("limit") limit: Int = 20
    ): Response<NotificationInboxResponseDto>

    @POST("api/v1/rides/{ride_id}/accept")
    suspend fun acceptRide(@Path("ride_id") rideId: String): Response<Map<String, Any>>

    @POST("api/v1/drivers/dispatch/offers/{ride_id}/reject")
    suspend fun rejectOffer(@Path("ride_id") rideId: String, @Body body: RejectOfferRequest = RejectOfferRequest()): Response<Map<String, Any>>

    @POST("api/v1/rides/{ride_id}/arrived")
    suspend fun markArrived(@Path("ride_id") rideId: String, @Body body: CoordinatePayloadRequest = CoordinatePayloadRequest()): Response<Map<String, Any>>

    @POST("api/v1/rides/{ride_id}/start")
    suspend fun startTripWithOtp(@Path("ride_id") rideId: String, @Body body: StartTripOtpRequest): Response<Map<String, Any>>

    @POST("api/v1/rides/{ride_id}/finish")
    suspend fun finishTrip(@Path("ride_id") rideId: String, @Body body: FinishTripRequest = FinishTripRequest()): Response<Map<String, Any>>

    @POST("api/v1/rides/{ride_id}/actions/no-show")
    suspend fun claimNoShow(@Path("ride_id") rideId: String, @Body body: ClaimNoShowRequest = ClaimNoShowRequest()): Response<Map<String, Any>>

    @POST("api/v1/payments/cash-collect")
    suspend fun collectCash(@Body request: CashCollectRequest): Response<Map<String, Any>>

    // --- Financials, Payouts & Goals ---
    @GET("api/v1/drivers/earnings")
    suspend fun getEarningsSummary(): Response<DriverEarningsSummaryDto>

    @POST("api/v1/drivers/payouts/request")
    suspend fun requestInstantPayout(@Body request: PayoutRequestDto): Response<Map<String, Any>>

    @GET("api/v1/drivers/financials/ledger")
    suspend fun getAccountLedger(): Response<DriverLedgerResponseDto>

    @GET("api/v1/drivers/scorecard")
    suspend fun getPerformanceScorecard(): Response<ScorecardDto>

    @GET("api/v1/drivers/daily-target")
    suspend fun getDailyTargets(): Response<DailyIncentiveDto>

    // --- Heatmap, Waiting, Payout Accounts, Anomaly & UPI QR ---
    @GET("api/v1/drivers/heat-map")
    suspend fun getHeatMap(): Response<HeatMapResponseDto>

    @POST("api/v1/rides/{ride_id}/waiting/start")
    suspend fun startWaiting(@Path("ride_id") rideId: String): Response<Map<String, Any>>

    @POST("api/v1/rides/{ride_id}/waiting/stop")
    suspend fun stopWaiting(@Path("ride_id") rideId: String): Response<Map<String, Any>>

    @POST("api/v1/drivers/financials/payout-accounts")
    suspend fun addPayoutAccount(@Body body: AddPayoutAccountRequest): Response<Map<String, Any>>

    @GET("api/v1/drivers/financials/payout-accounts")
    suspend fun getPayoutAccounts(): Response<List<PayoutAccountDto>>

    @POST("api/v1/drivers/financials/rides/{ride_id}/tolls")
    suspend fun submitRideToll(
        @Path("ride_id") rideId: String,
        @Body body: SubmitTollRequest
    ): Response<Map<String, Any>>

    @GET("api/v1/drivers/financials/rides/{ride_id}/tolls")
    suspend fun getRideTolls(
        @Path("ride_id") rideId: String
    ): Response<List<Map<String, Any>>>

    @POST("api/v1/safety/anomaly/route")
    suspend fun reportRouteAnomaly(@Body body: RouteAnomalyRequest): Response<Map<String, Any>>

    @POST("api/v1/payments/upi-checkout")
    suspend fun generateUpiCheckout(@Body body: GenerateUpiRequest): Response<Map<String, Any>>

    // --- Safety & Onboarding ---
    @POST("api/v1/driver-onboarding/vehicle-inspection")
    suspend fun submitVehicleInspection(@Body body: VehicleInspectionRequest): Response<Map<String, Any>>

    @POST("api/v1/drivers/safety/incidents")
    suspend fun reportSafetyIncident(@Body body: SafetyIncidentRequest): Response<Map<String, Any>>

    @POST("api/v1/safety/recording/consent")
    suspend fun updateRecordingConsent(@Body body: RecordingConsentRequest): Response<Map<String, Any>>

    @GET("api/v1/safety/recording/status/{ride_id}")
    suspend fun getRecordingStatus(@Path("ride_id") rideId: String): Response<Map<String, Any>>

    @POST("api/v1/drivers/safety/emergency-alert")
    suspend fun triggerDriverEmergencyAlert(@Body body: EmergencyAlertRequest): Response<Map<String, Any>>

    @POST("api/v1/location/emergency-ping")
    suspend fun sendEmergencyPing(@Body body: EmergencyPingRequest): Response<Map<String, Any>>

    @GET("api/v1/drivers/safety/incidents")
    suspend fun getDriverIncidents(): Response<List<DriverIncidentDto>>

    @POST("api/v1/drivers/safety/incidents")
    suspend fun submitDriverSafetyIncident(@Body body: SafetyIncidentRequest): Response<Map<String, Any>>

    @GET("api/v1/safety/contacts")
    suspend fun getEmergencyContacts(): Response<List<EmergencyContactDto>>

    @POST("api/v1/safety/contacts")
    suspend fun addEmergencyContact(@Body body: EmergencyContactCreateRequest): Response<EmergencyContactDto>

    @DELETE("api/v1/safety/contacts/{contact_id}")
    suspend fun deleteEmergencyContact(@Path("contact_id") contactId: String): Response<Map<String, Any>>

    @GET("api/v1/pools/ride/{ride_id}")
    suspend fun getSharedTripForRide(@Path("ride_id") rideId: String): Response<SharedPoolTripDto>

    @POST("api/v1/pools/{trip_id}/messages")
    suspend fun sendPoolMessage(@Path("trip_id") tripId: String, @Body body: ChatMessagePayloadRequest): Response<Map<String, Any>>

    @GET("api/v1/rides/{ride_id}/stops")
    suspend fun getRideStops(@Path("ride_id") rideId: String): Response<List<RideStopDto>>

    @POST("api/v1/rides/{ride_id}/stops/{stop_id}/arrive")
    suspend fun arriveAtRideStop(@Path("ride_id") rideId: String, @Path("stop_id") stopId: String): Response<Map<String, Any>>

    @POST("api/v1/rides/{ride_id}/chat/messages")
    suspend fun sendRideChatMessage(@Path("ride_id") rideId: String, @Body body: ChatMessagePayloadRequest): Response<Map<String, Any>>

    @GET("api/v1/driver-onboarding/training/curriculum")
    suspend fun getTrainingCurriculum(): Response<List<TrainingModuleDto>>

    @GET("api/v1/driver-onboarding/tickets")
    suspend fun getDriverTickets(): Response<List<DriverTicketDto>>

    @POST("api/v1/driver-onboarding/tickets")
    suspend fun createDriverTicket(@Body body: CreateDriverTicketRequest): Response<Map<String, Any>>

    @PATCH("api/v1/notifications/inbox/{notification_id}/read")
    suspend fun markNotificationRead(@Path("notification_id") notificationId: String): Response<Map<String, Any>>

    @GET("api/v1/location/road-restrictions")
    suspend fun getRoadRestrictions(
        @Query("latitude") latitude: Double? = null,
        @Query("longitude") longitude: Double? = null,
        @Query("radius_km") radiusKm: Double = 25.0
    ): Response<List<RoadRestrictionDto>>

    @DELETE("api/v1/location/road-restrictions/{restriction_id}")
    suspend fun clearRoadRestriction(@Path("restriction_id") restrictionId: String): Response<Map<String, Any>>

    @POST("api/v1/rides/{ride_id}/actions/reroute")
    suspend fun rerouteRide(@Path("ride_id") rideId: String, @Body body: RerouteRideRequest = RerouteRideRequest()): Response<Map<String, Any>>

    @POST("api/v1/drivers/financials/payout-accounts/{account_id}/primary")
    suspend fun setPrimaryPayoutAccount(@Path("account_id") accountId: String): Response<Map<String, Any>>

    @GET("api/v1/drivers/financials/goals/weekly")
    suspend fun getWeeklyGoal(): Response<WeeklyGoalDto>

    @GET("api/v1/drivers/financials/incentives")
    suspend fun getIncentiveOverview(): Response<IncentiveOverviewDto>

    @GET("api/v1/drivers/financials/rides/{ride_id}/breakdown")
    suspend fun getRideBreakdown(@Path("ride_id") rideId: String): Response<RideBreakdownDto>

    @POST("api/v1/drivers/auto-accept")
    suspend fun setAutoAccept(@Body body: AutoAcceptRequest): Response<Map<String, Any>>

    @GET("api/v1/drivers/preferences")
    suspend fun getDriverPreferences(): Response<Map<String, Any>>

    @PUT("api/v1/drivers/preferences")
    suspend fun updateDriverPreferences(@Body body: DriverPreferencesRequest): Response<Map<String, Any>>

    @PUT("api/v1/drivers/profile")
    suspend fun updateDriverProfile(@Body body: DriverProfileUpdateRequest): Response<Map<String, Any>>

    @PUT("api/v1/drivers/avatar")
    suspend fun updateDriverAvatar(@Body body: AvatarUpdateRequest): Response<Map<String, Any>>

    @DELETE("api/v1/drivers/avatar")
    suspend fun deleteDriverAvatar(): Response<Map<String, Any>>

    @POST("api/v1/auth/avatar")
    suspend fun updateAuthAvatar(@Body body: AvatarUpdateRequest): Response<Map<String, Any>>

    @GET("api/v1/drivers/financials/support-requests")
    suspend fun getFinancialSupportRequests(): Response<List<FinancialSupportDto>>

    @POST("api/v1/drivers/financials/support-requests")
    suspend fun submitFinancialSupportRequest(@Body body: SubmitFinancialSupportRequest): Response<Map<String, Any>>

    @GET("api/v1/drivers/safety/overview")
    suspend fun getSafetyOverview(): Response<Map<String, Any>>

    @POST("api/v1/drivers/safety/ratings/{rating_id}/appeal")
    suspend fun appealDriverRating(
        @Path("rating_id") ratingId: String,
        @Body body: RatingAppealRequest
    ): Response<Map<String, Any>>

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

    @Multipart
    @POST("api/v1/audio-recordings/{session_id}/segments")
    suspend fun uploadAudioSegment(
        @Path("session_id") sessionId: String,
        @Part("sequence") sequence: RequestBody,
        @Part("duration_seconds") durationSeconds: RequestBody,
        @Part("encryption_nonce") encryptionNonce: RequestBody,
        @Part("encryption_tag") encryptionTag: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<AudioSegmentUploadResponse>

    // --- Gift Cards & Commuter Credits ---
    @POST("api/v1/gift-cards/check-balance")
    suspend fun checkGiftCardBalance(
        @Body request: DriverGiftCardBalanceRequest
    ): Response<DriverGiftCardBalanceResponse>

    // --- Authoritative Backend Payment Capture Verification ---
    @GET("api/v1/payments/ride/{ride_id}/status")
    suspend fun getRidePaymentStatus(
        @Path("ride_id") rideId: String
    ): Response<Map<String, Any>>
}