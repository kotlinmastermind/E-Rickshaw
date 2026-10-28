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
    @SerializedName("heading") val heading: Double? = null,
    @SerializedName("speed_mps") val speedMps: Double? = null,
    @SerializedName("accuracy_m") val accuracyM: Double? = null,
    @SerializedName("is_mock_location") val isMockLocation: Boolean = false,
    @SerializedName("location_source") val locationSource: String = "FUSED"
)

data class StartTripPayload(
    @SerializedName("otp_code") val otpCode: String
)

data class FinishTripPayload(
    @SerializedName("actual_distance_km") val actualDistanceKm: Double? = null,
    @SerializedName("actual_duration_minutes") val actualDurationMinutes: Double? = null
)

data class DriverFirebaseVerifyPayload(
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("role") val role: String = "driver",
    @SerializedName("firebase_uid") val firebaseUid: String? = null,
    @SerializedName("id_token") val idToken: String? = null
)

interface DriverApiService {
    @GET("api/v1/notifications/inbox") suspend fun getNotificationInbox(@Query("unread_only") unreadOnly: Boolean = false, @Query("event_type") eventType: String? = null): Response<Map<String, Any>>
    @GET("api/v1/notifications/preferences") suspend fun getNotificationPreferences(): Response<Map<String, Any>>
    @PUT("api/v1/notifications/preferences") suspend fun updateNotificationPreferences(@Body payload: Map<String, Any>): Response<Map<String, Any>>
    @PATCH("api/v1/notifications/inbox/{id}/read") suspend fun markNotificationRead(@Path("id") id: String): Response<Map<String, Any>>
    @PATCH("api/v1/notifications/inbox/{id}/unread") suspend fun markNotificationUnread(@Path("id") id: String): Response<Map<String, Any>>
    @POST("api/v1/notifications/inbox/read-all") suspend fun markAllNotificationsRead(): Response<Map<String, Any>>
    @GET("api/v1/drivers/safety/overview") suspend fun getSafetyOverview(): Response<Map<String, Any>>
    @POST("api/v1/drivers/safety/checkin/schedule") suspend fun scheduleDriverSafetyCheckin(@Body payload: Map<String, Any>): Response<Map<String, Any>>
    @POST("api/v1/drivers/safety/checkin/respond") suspend fun respondDriverSafetyCheckin(@Body payload: Map<String, String>): Response<Map<String, Any>>
    @POST("api/v1/drivers/safety/emergency-alert") suspend fun sendDriverEmergencyAlert(@Body payload: Map<String, String>): Response<Map<String, Any>>
    @POST("api/v1/drivers/safety/fatigue-check") suspend fun submitFatigueCheck(@Body payload: Map<String, Any?>): Response<Map<String, Any>>
    @PUT("api/v1/drivers/safety/preferences") suspend fun updateSafetyPreferences(@Body payload: Map<String, Any>): Response<Map<String, Any>>
    @POST("api/v1/drivers/safety/incidents") suspend fun createDriverIncident(@Body payload: Map<String, Any?>): Response<Map<String, Any>>
    @POST("api/v1/drivers/safety/tickets") suspend fun createDriverSupportTicket(@Body payload: Map<String, String>): Response<Map<String, Any>>
    @POST("api/v1/drivers/safety/callback") suspend fun requestDriverCallback(@Body payload: Map<String, String>): Response<Map<String, Any>>
    @POST("api/v1/drivers/safety/ratings/{ratingId}/appeal") suspend fun appealRating(@Path("ratingId") ratingId: String, @Body payload: Map<String, String>): Response<Map<String, Any>>
    @GET("api/v1/drivers/safety/articles") suspend fun getDriverHelpArticles(): Response<List<Map<String, Any>>>
    @Multipart @POST("api/v1/drivers/safety/incidents/{ticketNumber}/evidence") suspend fun uploadIncidentEvidence(@Path("ticketNumber") ticketNumber: String, @Part file: okhttp3.MultipartBody.Part): Response<Map<String, Any>>
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

    @GET("api/v1/drivers/dispatch/offers") suspend fun getDispatchOffers(): Response<List<Map<String, Any>>>
    @GET("api/v1/rides/{id}") suspend fun getRideDetails(@Path("id") rideId: String): Response<Map<String, Any>>
    @GET("api/v1/rides/{id}/stops") suspend fun getRideStops(@Path("id") rideId: String): Response<List<Map<String, Any>>>
    @GET("api/v1/drivers/dispatch/preferences") suspend fun getDispatchPreferences(): Response<Map<String, Any>>
    @GET("api/v1/vehicles/categories") suspend fun getVehicleCategories(): Response<List<Map<String, Any>>>
    @PUT("api/v1/drivers/dispatch/preferences") suspend fun updateDispatchPreferences(@Body payload: Map<String, Any>): Response<Map<String, Any>>
    @POST("api/v1/drivers/dispatch/offers/{rideId}/reject") suspend fun rejectDispatchOffer(@Path("rideId") rideId: String, @Body payload: Map<String, String>): Response<Map<String, Any>>
    @GET("api/v1/enterprise/products/add-ons") suspend fun getChauffeurAddons(): Response<List<Map<String, Any>>>

    @POST("api/v1/rides/{id}/arrive")
    suspend fun notifyArrived(@Path("id") rideId: String): Response<Map<String, Any>>

    @POST("api/v1/rides/{id}/start")
    suspend fun startTrip(@Path("id") rideId: String, @Body payload: StartTripPayload): Response<Map<String, Any>>

    @POST("api/v1/rides/{id}/finish")
    suspend fun finishTrip(@Path("id") rideId: String, @Body payload: FinishTripPayload): Response<Map<String, Any>>

    @POST("api/v1/rides/{id}/actions/start-checklist") suspend fun submitStartChecklist(@Path("id") rideId: String, @Body payload: Map<String, Any>): Response<Map<String, Any>>
    @POST("api/v1/rides/{id}/actions/pickup-pin-correction") suspend fun correctPickupPin(@Path("id") rideId: String, @Body payload: Map<String, Any>): Response<Map<String, Any>>
    @POST("api/v1/rides/{id}/actions/reroute") suspend fun recordReroute(@Path("id") rideId: String, @Body payload: Map<String, String>): Response<Map<String, Any>>
    @POST("api/v1/rides/{id}/actions/pause") suspend fun pauseTrip(@Path("id") rideId: String, @Body payload: Map<String, String>): Response<Map<String, Any>>
    @POST("api/v1/rides/{id}/actions/resume") suspend fun resumeTrip(@Path("id") rideId: String, @Body payload: Map<String, String>): Response<Map<String, Any>>
    @POST("api/v1/rides/{id}/actions/no-show") suspend fun reportPassengerNoShow(@Path("id") rideId: String, @Body payload: Map<String, String>): Response<Map<String, Any>>
    @POST("api/v1/rides/{id}/actions/completion-checklist") suspend fun submitCompletionChecklist(@Path("id") rideId: String, @Body payload: Map<String, Any>): Response<Map<String, Any>>
    @POST("api/v1/rides/{id}/actions/issues") suspend fun reportTripIssue(@Path("id") rideId: String, @Body payload: Map<String, String>): Response<Map<String, Any>>
    @GET("api/v1/rides/{id}/destination-change/pending") suspend fun getPendingDestinationChange(@Path("id") rideId: String): Response<Map<String, Any>>
    @POST("api/v1/rides/{id}/destination-change/{requestId}/driver-response") suspend fun respondDestinationChange(@Path("id") rideId: String, @Path("requestId") requestId: String, @Body payload: Map<String, Any>): Response<Map<String, Any>>
    @POST("api/v1/rides/{id}/stops/{stopId}/arrive") suspend fun arriveAtStop(@Path("id") rideId: String, @Path("stopId") stopId: String): Response<Map<String, Any>>
    @POST("api/v1/rides/{id}/stops/{stopId}/depart") suspend fun departStop(@Path("id") rideId: String, @Path("stopId") stopId: String): Response<Map<String, Any>>
    @GET("api/v1/pools/ride/{rideId}") suspend fun getSharedTripForRide(@Path("rideId") rideId: String): Response<Map<String, Any>>
    @POST("api/v1/pools/bookings/{bookingId}/occupancy") suspend fun updateSharedRideOccupancy(@Path("bookingId") bookingId: String, @Body payload: Map<String, String>): Response<Map<String, Any>>
    @GET("api/v1/pools/{tripId}/messages") suspend fun getSharedTripMessages(@Path("tripId") tripId: String): Response<List<Map<String, Any>>>
    @POST("api/v1/pools/{tripId}/messages") suspend fun sendSharedTripMessage(@Path("tripId") tripId: String, @Body payload: Map<String, String>): Response<Map<String, Any>>

    @GET("api/v1/drivers/earnings")
    suspend fun getEarnings(): Response<Map<String, Any>>

    @GET("api/v1/drivers/wallet")
    suspend fun getWallet(): Response<Map<String, Any>>

    @GET("api/v1/location/battery-optimization-config")
    suspend fun getBatteryOptimizationConfig(): Response<Map<String, Any>>

    @GET("api/v1/location/road-restrictions")
    suspend fun getDriverRoadRestrictions(
        @Query("latitude") lat: Double,
        @Query("longitude") lng: Double,
        @Query("radius_km") radiusKm: Double = 25.0
    ): Response<List<Map<String, Any>>>

    @GET("api/v1/location/offline-cache-package")
    suspend fun getDriverOfflineCachePackage(@Query("region_code") regionCode: String = "DELHI_NCR"): Response<Map<String, Any>>

    @GET("api/v1/drivers/documents")
    suspend fun getDocuments(): Response<List<DriverDocumentDto>>

    @Multipart
    @POST("api/v1/drivers/documents")
    suspend fun uploadDocument(
        @Part("document_type") documentType: okhttp3.RequestBody,
        @Part file: okhttp3.MultipartBody.Part
    ): Response<Map<String, Any>>

    @POST("api/v1/drivers/device-token")
    suspend fun registerDeviceToken(@Body payload: DriverDeviceTokenPayload): Response<Map<String, Any>>

    @GET("api/v1/auth/me")
    suspend fun getMe(): Response<Map<String, Any>>

    @Multipart
    @POST("api/v1/user/avatar")
    suspend fun uploadAvatar(@Part file: okhttp3.MultipartBody.Part): Response<Map<String, Any>>

    @DELETE("api/v1/user/avatar")
    suspend fun deleteAvatar(): Response<Map<String, Any>>

    @POST("api/v1/safety/sos")
    suspend fun triggerEmergencySos(@Body payload: DriverSosPayload): Response<Map<String, Any>>

    @POST("api/v1/safety/incidents")
    suspend fun reportIncident(@Body payload: DriverIncidentReportPayload): Response<Map<String, Any>>

    @GET("api/v1/driver-onboarding/overview") suspend fun getOnboardingOverview(): Response<Map<String, Any>>
    @GET("api/v1/driver-onboarding/identity") suspend fun getIdentityStatus(): Response<Map<String, Any>>
    @POST("api/v1/driver-onboarding/identity/verify") suspend fun submitIdentity(@Body payload: IdentityVerificationPayload): Response<Map<String, Any>>
    @GET("api/v1/driver-onboarding/documents/expiries") suspend fun getDocumentExpiries(): Response<List<Map<String, Any>>>
    @GET("api/v1/driver-onboarding/documents") suspend fun getOnboardingDocuments(): Response<List<Map<String, Any>>>
    @Multipart @POST("api/v1/driver-onboarding/documents/resubmit") suspend fun resubmitOnboardingDocument(@Part("document_type") type: okhttp3.RequestBody, @Part("guidance_confirmed") confirmed: okhttp3.RequestBody, @Part file: okhttp3.MultipartBody.Part): Response<Map<String, Any>>
    @GET("api/v1/driver-onboarding/background-check") suspend fun getBackgroundCheck(): Response<Map<String, Any>>
    @GET("api/v1/driver-onboarding/training/curriculum") suspend fun getTrainingCurriculum(): Response<List<Map<String, Any>>>
    @POST("api/v1/driver-onboarding/training/assessment") suspend fun submitAssessment(@Body payload: KnowledgeAssessmentPayload): Response<Map<String, Any>>
    @GET("api/v1/driver-onboarding/vehicle-inspection/template") suspend fun getInspectionTemplate(): Response<Map<String, Any>>
    @Multipart @POST("api/v1/driver-onboarding/vehicle-inspection/{vehicleId}/photos/{photoType}") suspend fun uploadInspectionPhoto(@Path("vehicleId") vehicleId: String, @Path("photoType") photoType: String, @Part file: okhttp3.MultipartBody.Part): Response<Map<String, Any>>
    @POST("api/v1/driver-onboarding/vehicle-inspection") suspend fun submitVehicleInspection(@Body payload: VehicleInspectionPayload): Response<Map<String, Any>>
    @GET("api/v1/driver-onboarding/vehicles") suspend fun getOnboardingVehicles(): Response<List<Map<String, Any>>>
    @POST("api/v1/driver-onboarding/vehicles/{id}/activate") suspend fun activateOnboardingVehicle(@Path("id") id: String): Response<Map<String, Any>>
    @GET("api/v1/driver-onboarding/schedule") suspend fun getSchedule(): Response<List<Map<String, Any>>>
    @PUT("api/v1/driver-onboarding/schedule") suspend fun updateSchedule(@Body payload: SchedulePayload): Response<Map<String, Any>>
    @PUT("api/v1/driver-onboarding/zones-and-categories") suspend fun updateZones(@Body payload: ZonesPayload): Response<Map<String, Any>>
    @GET("api/v1/driver-onboarding/public-preview") suspend fun getPublicPreview(): Response<Map<String, Any>>
    @PUT("api/v1/driver-onboarding/service-preferences") suspend fun updateServicePreferences(@Body payload: ServicePreferencesPayload): Response<Map<String, Any>>
    @PUT("api/v1/driver-onboarding/emergency-contact") suspend fun updateEmergencyContact(@Body payload: EmergencyContactPayload): Response<Map<String, Any>>
    @POST("api/v1/driver-onboarding/consent/acknowledge") suspend fun acknowledgePolicies(): Response<Map<String, Any>>
    @POST("api/v1/driver-onboarding/account-deletion/request") suspend fun requestAccountDeletion(): Response<Map<String, Any>>
    @GET("api/v1/driver-onboarding/tickets") suspend fun getOnboardingTickets(): Response<List<Map<String, Any>>>
    @POST("api/v1/driver-onboarding/tickets") suspend fun createOnboardingTicket(@Body payload: OnboardingTicketPayload): Response<Map<String, Any>>

    // Part 11: Driver Financials & Welfare Endpoints
    @GET("api/v1/drivers/financials/dashboard") suspend fun getFinancialDashboard(@Query("days") days: Int = 14): Response<Map<String, Any>>
    @GET("api/v1/drivers/financials/rides/{ride_id}/breakdown") suspend fun getRideFinancialBreakdown(@Path("ride_id") rideId: String): Response<Map<String, Any>>
    @GET("api/v1/drivers/financials/shift/status") suspend fun getCurrentShift(): Response<Map<String, Any>>
    @POST("api/v1/drivers/financials/shift/start") suspend fun startShift(@Body payload: Map<String, Any>): Response<Map<String, Any>>
    @POST("api/v1/drivers/financials/shift/end") suspend fun endShift(@Body payload: Map<String, Any>): Response<Map<String, Any>>
    @POST("api/v1/drivers/financials/shift/break") suspend fun recordShiftBreak(@Body payload: Map<String, Any>): Response<Map<String, Any>>
    @GET("api/v1/drivers/financials/utilization") suspend fun getUtilizationReport(): Response<Map<String, Any>>
    @GET("api/v1/drivers/financials/weekly-goal") suspend fun getWeeklyGoal(): Response<Map<String, Any>>
    @PUT("api/v1/drivers/financials/weekly-goal") suspend fun updateWeeklyGoal(@Body payload: Map<String, Any>): Response<Map<String, Any>>
    @GET("api/v1/drivers/financials/incentives") suspend fun getIncentives(): Response<Map<String, Any>>
    @GET("api/v1/drivers/financials/bonuses") suspend fun getBonusHistory(): Response<Map<String, Any>>
    @GET("api/v1/drivers/financials/ledger") suspend fun getWalletLedger(): Response<Map<String, Any>>
    @GET("api/v1/drivers/financials/payout-accounts") suspend fun getPayoutAccounts(): Response<Map<String, Any>>
    @POST("api/v1/drivers/financials/payout-accounts") suspend fun addPayoutAccount(@Body payload: Map<String, Any>): Response<Map<String, Any>>
    @POST("api/v1/drivers/financials/payout-accounts/{account_id}/primary") suspend fun setPrimaryPayoutAccount(@Path("account_id") accountId: String): Response<Map<String, Any>>
    @GET("api/v1/drivers/financials/payout-schedules") suspend fun getPayoutSchedule(): Response<Map<String, Any>>
    @PUT("api/v1/drivers/financials/payout-schedules") suspend fun updatePayoutSchedule(@Body payload: Map<String, Any>): Response<Map<String, Any>>
    @GET("api/v1/drivers/financials/payouts/reconciliation") suspend fun getPayoutReconciliation(): Response<Map<String, Any>>
    @GET("api/v1/drivers/financials/tax-documents") suspend fun getTaxDocuments(): Response<Map<String, Any>>
    @GET("api/v1/drivers/financials/expenses") suspend fun getOperatingExpenses(): Response<Map<String, Any>>
    @POST("api/v1/drivers/financials/expenses") suspend fun logOperatingExpense(@Body payload: Map<String, Any>): Response<Map<String, Any>>
    @Streaming @GET("api/v1/drivers/financials/export-csv") suspend fun exportEarningsCsv(): Response<okhttp3.ResponseBody>
    @GET("api/v1/drivers/financials/forecast-and-zones") suspend fun getAvailabilityForecastAndZones(): Response<Map<String, Any>>
    @GET("api/v1/drivers/financials/support-requests") suspend fun getFinancialSupportRequests(): Response<Map<String, Any>>
    @POST("api/v1/drivers/financials/support-requests") suspend fun submitFinancialSupportRequest(@Body payload: Map<String, Any>): Response<Map<String, Any>>

    // Part 16: DPDP & GDPR DSAR Privacy Rights
    @POST("api/v1/privacy/dsar-requests") suspend fun submitPrivacyDsar(@Body payload: DriverPrivacyDsarPayload): Response<Map<String, Any>>

    @GET("api/v1/localization/translations/{locale}")
    suspend fun getLocalizationTranslations(@Path("locale") locale: String = "en"): Response<Map<String, Any>>

    @POST("api/v1/auth/signup/driver")
    suspend fun signupDriver(@Body payload: DriverSignupRequestPayload): Response<Map<String, Any>>

    @POST("api/v1/auth/login/unified")
    suspend fun unifiedLogin(@Body payload: DriverUnifiedLoginRequestPayload): Response<Map<String, Any>>

    @POST("api/v1/auth/forgot-password/request-otp")
    suspend fun requestForgotPasswordOtp(@Body payload: DriverForgotPasswordOtpRequestPayload): Response<Map<String, Any>>

    @POST("api/v1/auth/forgot-password/reset")
    suspend fun resetForgotPassword(@Body payload: DriverForgotPasswordResetRequestPayload): Response<Map<String, Any>>

    // Part 19: Infrastructure, Resilience & Developer Platform (Features 451-475)
    @GET("api/v1/infra/openapi-client-spec")
    suspend fun getOpenApiClientSpec(@Query("app_type") appType: String = "DRIVER"): Response<Map<String, Any>>

    @GET("api/v1/infra/api-versions")
    suspend fun getApiVersions(): Response<List<Map<String, Any>>>

    @POST("api/v1/infra/idempotency/check")
    suspend fun checkIdempotency(
        @Header("X-Idempotency-Key") idempotencyKey: String,
        @Body payload: Map<String, Any>
    ): Response<Map<String, Any>>

    @GET("api/v1/infra/error-catalog")
    suspend fun getErrorCatalog(): Response<List<Map<String, Any>>>

    @POST("api/v1/infra/mobile-crashes")
    suspend fun reportMobileCrash(@Body payload: Map<String, Any>): Response<Map<String, Any>>

    @GET("api/v1/infra/feature-flags")
    suspend fun getFeatureFlags(): Response<List<Map<String, Any>>>
}

data class DriverSignupRequestPayload(
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("email") val email: String,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("password") val password: String,
    @SerializedName("username") val username: String,
    @SerializedName("otp_code") val otpCode: String,
    @SerializedName("vehicle_type") val vehicleType: String = "SEDAN",
    @SerializedName("vehicle_name") val vehicleName: String,
    @SerializedName("vehicle_no") val vehicleNo: String
)

data class DriverUnifiedLoginRequestPayload(
    @SerializedName("login_type") val loginType: String = "PASSWORD",
    @SerializedName("identifier") val identifier: String,
    @SerializedName("password") val password: String? = null,
    @SerializedName("otp_code") val otpCode: String? = null
)

data class DriverForgotPasswordOtpRequestPayload(
    @SerializedName("identifier") val identifier: String
)

data class DriverForgotPasswordResetRequestPayload(
    @SerializedName("identifier") val identifier: String,
    @SerializedName("otp_code") val otpCode: String,
    @SerializedName("new_password") val newPassword: String
)

data class DriverPrivacyDsarPayload(
    @SerializedName("request_type") val requestType: String,
    @SerializedName("reason") val reason: String
)

data class IdentityVerificationPayload(val id_type: String, val id_number: String, val selfie_notes: String? = null)
data class KnowledgeAssessmentPayload(val module_id: String, val selected_answers: List<Int>)
data class VehicleInspectionPayload(val vehicle_id: String, val checklist_results: Map<String, String>, val photos: Map<String, String>, val notes: String? = null)
data class SchedulePayload(val schedules: List<Map<String, Any>>)
data class ZonesPayload(val operating_zones: List<String>, val vehicle_categories: List<String>)
data class ServicePreferencesPayload(val languages_spoken: String, val service_etiquette_level: String, val cabin_music_preference: String, val cabin_temp_celsius: Int)
data class EmergencyContactPayload(val name: String, val phone: String, val relationship: String)
data class OnboardingTicketPayload(val subject: String, val message: String)

data class DriverDocumentDto(
    @SerializedName("id") val id: String,
    @SerializedName("document_type") val documentType: String,
    @SerializedName("file_name") val fileName: String,
    @SerializedName("verification_status") val verificationStatus: String,
    @SerializedName("rejection_reason") val rejectionReason: String?,
    @SerializedName("created_at") val createdAt: String
)

data class DriverDeviceTokenPayload(
    @SerializedName("fcm_token") val fcmToken: String,
    @SerializedName("device_os") val deviceOs: String = "ANDROID"
)

data class DriverSosPayload(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("alert_type") val alertType: String = "DRIVER_SOS",
    @SerializedName("notes") val notes: String? = null
)

data class DriverIncidentReportPayload(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("category") val category: String,
    @SerializedName("severity") val severity: String = "MEDIUM",
    @SerializedName("description") val description: String,
    @SerializedName("involved_party_role") val involvedPartyRole: String = "DRIVER"
)
