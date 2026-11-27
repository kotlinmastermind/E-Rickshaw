package com.vaisentra.mobility.driver.data

import com.google.gson.annotations.SerializedName

// --- Driver Auth & Profile (FastAPI: /api/v1/auth/login, /api/v1/auth/otp/*, /api/v1/drivers/profile, /documents) ---
data class OtpSendRequest(
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("role") val role: String = "driver"
)

data class OtpVerifyRequest(
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("otp_code") val otpCode: String,
    @SerializedName("role") val role: String = "driver",
    @SerializedName("full_name") val fullName: String? = null
)

data class ChangePasswordRequest(
    @SerializedName("current_password") val currentPassword: String? = null,
    @SerializedName("new_password") val newPassword: String
)

data class RecoveryRequest(
    @SerializedName("identifier") val identifier: String
)

data class PasswordResetRequest(
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("recovery_code") val recoveryCode: String,
    @SerializedName("new_password") val newPassword: String
)

data class TokenRefreshRequest(
    @SerializedName("refresh_token") val refreshToken: String
)

data class BackendHealthDto(
    @SerializedName("status") val status: String = "",
    @SerializedName("service") val service: String = "",
    @SerializedName("timestamp") val timestamp: String = ""
)

data class DriverTokenResponse(
    @SerializedName("message") val message: String? = null,
    @SerializedName("access_token") val accessToken: String = "",
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("token_type") val tokenType: String? = "bearer",
    @SerializedName("expires_in") val expiresIn: Int? = null,
    @SerializedName("is_new_user") val isNewUser: Boolean = false,
    @SerializedName("user") val user: LoginUserDto? = null
)

data class DriverLoginRequest(
    @SerializedName("identifier") val identifier: String,
    @SerializedName("password") val password: String? = null,
    @SerializedName("login_type") val loginType: String = "PASSWORD"
)

data class DriverSignupRequest(
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("email") val email: String? = null,
    @SerializedName("full_name") val fullName: String = "Driver Partner",
    @SerializedName("password") val password: String = "DriverPass123456!",
    @SerializedName("username") val username: String? = null,
    @SerializedName("vehicle_type") val vehicleType: String = "E_RICKSHAW_STANDARD",
    @SerializedName("vehicle_name") val vehicleName: String = "Mahindra Treo",
    @SerializedName("vehicle_no") val vehicleNo: String = "DL-01-ER-1001"
)

data class VehicleCategoryDto(
    @SerializedName("id") val id: String,
    @SerializedName("code") val code: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("capacity") val capacity: Int = 4,
    @SerializedName("is_active") val isActive: Boolean = true,
    @SerializedName("sort_order") val sortOrder: Int = 0
)

data class LoginUserDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("phone_number") val phoneNumber: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("full_name") val fullName: String? = null,
    @SerializedName("role") val role: String? = null
)

data class DriverLoginResponse(
    @SerializedName("message") val message: String? = null,
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("token_type") val tokenType: String? = null,
    @SerializedName("expires_in") val expiresIn: Int? = null,
    @SerializedName("user") val user: LoginUserDto? = null
)

data class DriverProfileDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("driver_id") val driverId: String = "",
    @SerializedName("user_id") val userId: String = "",
    @SerializedName("full_name") val fullName: String = "",
    @SerializedName("phone_number") val phoneNumber: String = "",
    @SerializedName("email") val email: String = "",
    @SerializedName("license_number") val licenseNumber: String = "",
    @SerializedName("approval_status") val approvalStatus: String = "PENDING",
    @SerializedName("rating_average", alternate = ["rating"]) val rating: Double = 0.0,
    @SerializedName("rating_count") val ratingCount: Int = 0,
    @SerializedName("total_trips", alternate = ["completed_trips"]) val completedTrips: Int = 0,
    @SerializedName("wallet_balance_cents") val walletBalanceCents: Long = 0L,
    @SerializedName("badge_number") val badgeNumber: String = "",
    @SerializedName("assigned_vehicle_plate") val assignedVehiclePlate: String = "",
    @SerializedName("assigned_vehicle_model") val assignedVehicleModel: String = "",
    @SerializedName("acceptance_rate") val acceptanceRate: Double = 0.0,
    @SerializedName("avatar_url", alternate = ["avatarUrl", "profile_picture", "profilePicture"]) val avatarUrl: String? = null
) {
    val onboardingStatus: String get() = approvalStatus
    val walletBalanceRupees: Double get() = walletBalanceCents / 100.0
}

data class DriverDocumentDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("document_type") val documentType: String = "",
    @SerializedName("file_name") val fileName: String = "",
    @SerializedName("verification_status", alternate = ["status"]) val status: String = "PENDING",
    @SerializedName("rejection_reason") val rejectionReason: String? = null,
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("expiry_date", alternate = ["expires_at"]) val expiryDate: String = "",
    @SerializedName("days_until_expiry") val daysUntilExpiry: Int = 0,
    @SerializedName("document_number", alternate = ["documentNumber", "number"]) val documentNumber: String? = null,
    @SerializedName("view_url", alternate = ["document_url", "url"]) val viewUrl: String? = null,
    @SerializedName("file_size_bytes") val fileSizeBytes: Long = 0L,
    @SerializedName("mime_type") val mimeType: String? = null
)

data class DriverVehicleDto(
    @SerializedName("vehicle_id", alternate = ["id"]) val vehicleId: String = "",
    @SerializedName("model") val model: String = "",
    @SerializedName("license_plate") val licensePlate: String = "",
    @SerializedName("color") val color: String = "",
    @SerializedName("year") val year: Int = 2025,
    @SerializedName("rc_number") val rcNumber: String? = null,
    @SerializedName("permit_number") val permitNumber: String? = null,
    @SerializedName("is_active_vehicle") val isActiveVehicle: Boolean = false,
    @SerializedName("is_active") val isActive: Boolean = false
)

// --- Active Ride & Notifications ---
data class ActiveRideDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("status") val status: String = "",
    @SerializedName("pickup_address") val pickupAddress: String = "",
    @SerializedName("pickup_latitude") val pickupLatitude: Double? = null,
    @SerializedName("pickup_longitude") val pickupLongitude: Double? = null,
    @SerializedName("destination_address") val destinationAddress: String = "",
    @SerializedName("destination_latitude") val destinationLatitude: Double? = null,
    @SerializedName("destination_longitude") val destinationLongitude: Double? = null,
    @SerializedName("estimated_fare_cents") val estimatedFareCents: Long = 0L,
    @SerializedName("final_fare_cents") val finalFareCents: Long? = null,
    @SerializedName("payment_method") val paymentMethod: String = "CASH"
)

data class AutoAcceptRequest(
    @SerializedName("enabled") val enabled: Boolean,
    @SerializedName("max_distance_km") val maxDistanceKm: Double = 5.0
)

data class ActiveRideResponseDto(
    @SerializedName("active_ride") val activeRide: ActiveRideDto? = null
)

data class NotificationInboxResponseDto(
    @SerializedName("items") val items: List<NotificationItemDto> = emptyList(),
    @SerializedName("unread_count") val unreadCount: Int = 0,
    @SerializedName("total") val total: Int = 0
)

// --- Duty & Location Telemetry ---
data class DutyStatusUpdateRequest(
    @SerializedName("is_online") val isOnline: Boolean
)

data class DriverLocationPingRequest(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("heading") val heading: Double? = null,
    @SerializedName("speed_mps") val speedMps: Double? = null,
    @SerializedName("accuracy_m") val accuracyM: Double? = null,
    @SerializedName("is_mock_location") val isMockLocation: Boolean = false,
    @SerializedName("location_source") val locationSource: String = "GPS"
)

data class DriverReverseGeocodeRequest(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double
)

data class DriverReverseGeocodeResponse(
    @SerializedName("latitude") val latitude: Double = 0.0,
    @SerializedName("longitude") val longitude: Double = 0.0,
    @SerializedName("formatted_address") val formattedAddress: String = ""
)

data class DeviceTokenRegisterRequest(
    @SerializedName("fcm_token") val fcmToken: String,
    @SerializedName("device_os") val deviceOs: String = "ANDROID",
    @SerializedName("device_label") val deviceLabel: String? = null
)

data class DeviceTokenDeactivateRequest(
    @SerializedName("fcm_token") val fcmToken: String? = null
)

data class DriverLogoutRequest(
    @SerializedName("fcm_token") val fcmToken: String? = null
)

data class StartShiftRequest(
    @SerializedName("vehicle_id") val vehicleId: String? = null,
    @SerializedName("start_odometer_km") val startOdometerKm: Double? = null
)

data class EndShiftRequest(
    @SerializedName("end_odometer_km") val endOdometerKm: Double? = null
)

// --- Ride Dispatch & Turn-by-Turn Ops ---
data class DispatchOfferDto(
    @SerializedName("ride_id") val rideId: String = "",
    @SerializedName("assignment_id") val assignmentId: String = "",
    @SerializedName("passenger_name") val passengerName: String = "Commuter Passenger",
    @SerializedName("passenger_phone") val passengerPhone: String = "",
    @SerializedName("passenger_rating") val passengerRating: Double = 0.0,
    @SerializedName("pickup_address") val pickupAddress: String = "",
    @SerializedName("destination_area", alternate = ["destination_address"]) val destinationAddress: String = "",
    @SerializedName("vehicle_category") val vehicleCategory: String = "",
    @SerializedName("estimated_trip_distance_km", alternate = ["distance_km"]) val distanceKm: Double = 0.0,
    @SerializedName("pickup_distance_km") val pickupDistanceKm: Double? = null,
    @SerializedName("pickup_duration_minutes", alternate = ["pickup_eta_minutes"]) val pickupDurationMinutes: Int? = null,
    @SerializedName("trip_duration_minutes", alternate = ["estimated_duration_minutes"]) val tripDurationMinutes: Int? = null,
    @SerializedName("estimated_fare_cents") val estimatedFareCents: Long = 0L,
    @SerializedName("estimated_driver_earnings_cents") val estimatedDriverEarningsCents: Long = 0L,
    @SerializedName("estimated_commission_cents") val estimatedCommissionCents: Long? = null,
    @SerializedName("estimated_commission_percentage") val estimatedCommissionPercentage: Double? = null,
    @SerializedName("commission_classification") val commissionClassification: String? = null,
    @SerializedName("commission_estimate_available") val commissionEstimateAvailable: Boolean = false,
    @SerializedName("payment_mode") val paymentMode: String = "UPI",
    @SerializedName("expires_at") val expiresAt: String? = null,
    @SerializedName("escalation_level") val escalationLevel: Int = 0,
    @SerializedName("timeout_seconds") val timeoutSeconds: Int = 25
) {
    val estimatedFareRupees: Double
        get() = estimatedFareCents / 100.0

    val estimatedDriverEarningsRupees: Double
        get() = if (estimatedDriverEarningsCents > 0L) estimatedDriverEarningsCents / 100.0 else estimatedFareRupees

    val pickupDistanceMeters: Int
        get() = ((pickupDistanceKm ?: 0.0) * 1000.0).toInt()

    val effectivePickupDistanceKm: Double
        get() = pickupDistanceKm ?: 0.8

    val effectivePickupDurationMinutes: Int
        get() = pickupDurationMinutes ?: maxOf(1, kotlin.math.round((effectivePickupDistanceKm / 20.0) * 60).toInt())

    val effectiveTripDistanceKm: Double
        get() = if (distanceKm > 0.0) distanceKm else 3.5

    val effectiveTripDurationMinutes: Int
        get() = tripDurationMinutes ?: maxOf(2, kotlin.math.round((effectiveTripDistanceKm / 22.0) * 60).toInt())
}

data class CashCollectRequest(
    @SerializedName("ride_id") val rideId: String
)

// --- Financials, Payouts & Ledger ---
data class DriverEarningsSummaryDto(
    @SerializedName("total_earnings_cents") val totalEarningsCents: Long = 0L,
    @SerializedName("today_earnings_cents") val todayEarningsCents: Long = 0L,
    @SerializedName("today_gross_rupees") val directTodayGrossRupees: Double? = null,
    @SerializedName("weekly_earnings_cents") val weeklyEarningsCents: Long = 0L,
    @SerializedName("currency") val currency: String = "INR",
    @SerializedName("completed_trips") val totalCompletedTrips: Int = 0,
    @SerializedName("today_completed_trips") val todayCompletedTrips: Int = 0,
    @SerializedName("today_distance_km") val todayDistanceKm: Double = 0.0,
    @SerializedName("wallet_balance_cents") val walletBalanceCents: Long = 0L,
    @SerializedName("wallet_balance_rupees") val directWalletBalanceRupees: Double? = null,
    @SerializedName("commission_outstanding_cents") val commissionOutstandingCents: Long = 0L,
    @SerializedName("withdrawable_balance_cents") val withdrawableBalanceCents: Long = 0L,
    @SerializedName("withdrawable_balance_rupees") val directWithdrawableBalanceRupees: Double? = null,
    @SerializedName("rating_average") val ratingAverage: Double = 0.0,
    @SerializedName("rating_count") val ratingCount: Int = 0,
    @SerializedName("commission_accrued_cents") val commissionAccruedCents: Long = 0L,
    @SerializedName("today_commission_cents") val todayCommissionCents: Long = 0L,
    @SerializedName("weekly_commission_cents") val weeklyCommissionCents: Long = 0L,
    @SerializedName("commission_payable_cents") val commissionPayableCents: Long = 0L,
    @SerializedName("commission_receivable_cents") val commissionReceivableCents: Long = 0L,
    @SerializedName("commission_snapshot_missing_count") val commissionSnapshotMissingCount: Int = 0,
    @SerializedName("recent_trips") val recentTrips: List<DriverRecentTripEarningDto> = emptyList(),
    val onlineHoursToday: Double = 0.0,
    val pendingPayoutRupees: Double = 0.0
) {
    val todayGrossRupees: Double get() = directTodayGrossRupees ?: (todayEarningsCents / 100.0)
    @Deprecated("Driver earnings are shown as gross; commission is reported separately.")
    val todayNetRupees: Double get() = todayGrossRupees
    val weeklyRupees: Double get() = weeklyEarningsCents / 100.0
    val weeklyCommissionRupees: Double get() = weeklyCommissionCents / 100.0
    val totalGrossRupees: Double get() = totalEarningsCents / 100.0
    val commissionAccruedRupees: Double get() = commissionAccruedCents / 100.0
    val todayCommissionRupees: Double get() = todayCommissionCents / 100.0
    val commissionPayableRupees: Double get() = commissionPayableCents / 100.0
    val commissionReceivableRupees: Double get() = commissionReceivableCents / 100.0
    val walletBalanceRupees: Double get() = directWalletBalanceRupees ?: (walletBalanceCents / 100.0)
    val commissionOutstandingRupees: Double get() = commissionOutstandingCents / 100.0
    val withdrawableBalanceRupees: Double get() = directWithdrawableBalanceRupees ?: (withdrawableBalanceCents / 100.0)
    val displayTodayNetRupees: Double get() = todayGrossRupees
    val displayWalletBalanceRupees: Double get() = walletBalanceRupees
    val displayWithdrawableBalanceRupees: Double get() = withdrawableBalanceRupees
    val completedTripsToday: Int get() = totalCompletedTrips
}

data class DriverRecentTripEarningDto(
    @SerializedName("ride_id") val rideId: String = "",
    @SerializedName("date") val completedAt: String? = null,
    @SerializedName("gross_fare_cents") val grossFareCents: Long = 0L,
    @SerializedName("gross_fare_rupees") val grossFareRupees: Double = 0.0,
    @SerializedName("commission_cents") val commissionCents: Long? = null,
    @SerializedName("commission_percentage") val commissionPercentage: Double? = null,
    @SerializedName("commission_classification") val commissionClassification: String? = null,
    @SerializedName("commission_snapshot_available") val commissionSnapshotAvailable: Boolean = false,
    @SerializedName("pickup") val pickup: String = "",
    @SerializedName("destination") val destination: String = ""
)

data class PayoutRequestDto(
    @SerializedName("amount_cents") val amountCents: Long,
    @SerializedName("payout_account_id") val payoutAccountId: String,
    @SerializedName("idempotency_key") val idempotencyKey: String
)

data class PayoutHistoryItemDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("amount_cents") val amountCents: Long = 0L,
    @SerializedName("status") val status: String = "REQUESTED",
    @SerializedName("execution_mode") val executionMode: String? = null,
    @SerializedName("bank_account_mask") val bankAccountMask: String = "",
    @SerializedName("account_type") val accountType: String? = null,
    @SerializedName("bank_name") val bankName: String? = null,
    @SerializedName("ifsc_code") val ifscCode: String? = null,
    @SerializedName("payout_reference") val payoutReference: String? = null,
    @SerializedName("settlement_reference") val settlementReference: String? = null,
    @SerializedName("requested_at") val requestedAt: String = "",
    @SerializedName("reviewed_at") val reviewedAt: String? = null,
    @SerializedName("paid_at") val paidAt: String? = null,
    @SerializedName("failed_at") val failedAt: String? = null,
    @SerializedName("failure_reason") val failureReason: String? = null,
    @SerializedName("retry_count") val retryCount: Int = 0,
    @SerializedName("rejection_reason") val rejectionReason: String? = null
)

data class LedgerItemDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("entry_type") val type: String = "CREDIT",
    @SerializedName("category") val category: String = "",
    @SerializedName("amount_cents") val amountCents: Long = 0L,
    @SerializedName("amount_rupees") val amountRupees: Double = 0.0,
    @SerializedName("balance_after_cents") val balanceAfterCents: Long = 0L,
    @SerializedName("balance_after_rupees") val balanceAfterRupees: Double = 0.0,
    @SerializedName("reference_id") val referenceId: String? = null,
    @SerializedName("description") val title: String = "",
    @SerializedName("created_at") val timestamp: String = ""
)

data class DriverLedgerResponseDto(
    @SerializedName("driver_id") val driverId: String = "",
    @SerializedName("current_balance_cents") val currentBalanceCents: Long = 0L,
    @SerializedName("current_balance_rupees") val currentBalanceRupees: Double = 0.0,
    @SerializedName("entries_count") val entriesCount: Int = 0,
    @SerializedName("ledger") val ledger: List<LedgerItemDto> = emptyList()
)

// --- Performance Scorecard & Incentives ---
data class ScorecardDto(
    @SerializedName("rating") val rating: Double = 0.0,
    @SerializedName("acceptance_rate") val acceptanceRate: Double = 0.0,
    @SerializedName("cancellation_rate") val cancellationRate: Double = 0.0,
    @SerializedName("safety_incident_count") val safetyIncidentCount: Int = 0,
    @SerializedName("cleanliness_score") val cleanlinessScore: Double = 0.0,
    @SerializedName("total_trips") val totalTrips: Int = 0,
    @SerializedName("rating_count") val ratingCount: Int = 0,
    @SerializedName("total_offers") val totalOffers: Int = 0,
    @SerializedName("accepted_offers") val acceptedOffers: Int = 0
)

data class DailyIncentiveDto(
    @SerializedName("target_date") val targetDate: String = "",
    @SerializedName("target_trips") val targetRides: Int = 0,
    @SerializedName("completed_trips") val completedRides: Int = 0,
    @SerializedName("target_earnings_cents") val targetEarningsCents: Long = 0L,
    @SerializedName("achieved_earnings_cents") val achievedEarningsCents: Long = 0L,
    @SerializedName("bonus_cents") val bonusCents: Long = 0L,
    @SerializedName("is_achieved") val isAchieved: Boolean = false,
    @SerializedName("trips_progress_percentage") val tripsProgressPercentage: Double = 0.0,
    @SerializedName("earnings_progress_percentage") val earningsProgressPercentage: Double = 0.0
) {
    val rewardRupees: Double get() = bonusCents / 100.0
}

// --- Safety & Onboarding Payloads ---
data class RouteAnomalyRequest(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("current_lat") val currentLat: Double,
    @SerializedName("current_lng") val currentLng: Double,
    @SerializedName("deviation_reason") val deviationReason: String? = null,
    @SerializedName("comments") val comments: String? = null
)

data class SubmitTollRequest(
    @SerializedName("toll_amount_rupees") val tollAmountRupees: Double,
    @SerializedName("toll_name") val tollName: String,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,
    @SerializedName("receipt_url") val receiptUrl: String? = null
)

data class StartInspectionRequest(
    @SerializedName("vehicle_id") val vehicleId: String? = null
)

data class SubmitInspectionRequest(
    @SerializedName("odometer_reading") val odometerReading: Double,
    @SerializedName("checklist_results") val checklistResults: Map<String, Any>,
    @SerializedName("driver_remarks") val driverRemarks: String? = null
)

data class InspectionPhotoItemDto(
    @SerializedName("photo_id") val photoId: String = "",
    @SerializedName("category") val category: String = "",
    @SerializedName("storage_path") val storagePath: String = "",
    @SerializedName("signed_url") val signedUrl: String? = null,
    @SerializedName("mime_type") val mimeType: String? = null,
    @SerializedName("file_size_bytes") val fileSizeBytes: Long = 0L,
    @SerializedName("uploaded_at") val uploadedAt: String? = null,
    @SerializedName("upload_status") val uploadStatus: String = "COMPLETED"
)

data class InspectionDraftDto(
    @SerializedName("has_inspection") val hasInspection: Boolean = false,
    @SerializedName("id") val id: String? = null,
    @SerializedName("inspection_id") val inspectionId: String? = null,
    @SerializedName("driver_id") val driverId: String? = null,
    @SerializedName("vehicle_id") val vehicleId: String? = null,
    @SerializedName("vehicle_plate") val vehiclePlate: String? = null,
    @SerializedName("vehicle_model") val vehicleModel: String? = null,
    @SerializedName("inspection_date") val inspectionDate: String? = null,
    @SerializedName("inspection_status") val inspectionStatus: String = "DRAFT",
    @SerializedName("overall_result") val overallResult: String? = null,
    @SerializedName("verification_status") val verificationStatus: String? = null,
    @SerializedName("can_go_online") val canGoOnline: Boolean = false,
    @SerializedName("odometer_reading") val odometerReading: Double? = null,
    @SerializedName("previous_odometer_reading") val previousOdometerReading: Double? = null,
    @SerializedName("odometer_unit") val odometerUnit: String = "KM",
    @SerializedName("checklist") val checklist: Map<String, String>? = null,
    @SerializedName("failed_items") val failedItems: List<String>? = null,
    @SerializedName("driver_remarks") val driverRemarks: String? = null,
    @SerializedName("rejection_reason") val rejectionReason: String? = null,
    @SerializedName("photos") val photos: List<InspectionPhotoItemDto> = emptyList(),
    @SerializedName("photos_count") val photosCount: Int = 0,
    @SerializedName("submitted_at") val submittedAt: String? = null,
    @SerializedName("message") val message: String? = null
) {
    val activeInspectionId: String get() = inspectionId ?: id ?: ""
}

data class InspectionPhotoUploadResponseDto(
    @SerializedName("photo_id") val photoId: String = "",
    @SerializedName("inspection_id") val inspectionId: String = "",
    @SerializedName("category") val category: String = "",
    @SerializedName("storage_path") val storagePath: String = "",
    @SerializedName("signed_url") val signedUrl: String? = null,
    @SerializedName("mime_type") val mimeType: String = "",
    @SerializedName("file_size_bytes") val fileSizeBytes: Long = 0L,
    @SerializedName("file_hash_sha256") val fileHashSha256: String = "",
    @SerializedName("uploaded_at") val uploadedAt: String = "",
    @SerializedName("message") val message: String = ""
)

data class VehicleInspectionRequest(
    @SerializedName("vehicle_id") val vehicleId: String,
    @SerializedName("checklist_results") val checklistResults: Map<String, String>,
    @SerializedName("photos") val photos: Map<String, String>,
    @SerializedName("notes") val notes: String? = null
)

data class PreShiftInspectionRequest(
    @SerializedName("vehicle_id") val vehicleId: String? = null,
    @SerializedName("tyre_pressure_ok") val tyrePressureOk: Boolean = true,
    @SerializedName("lights_ok") val lightsOk: Boolean = true,
    @SerializedName("brake_buzzer_ok") val brakeBuzzerOk: Boolean = true,
    @SerializedName("notes") val notes: String? = null
)

data class VehicleUpdateDto(
    @SerializedName("model") val model: String? = null,
    @SerializedName("license_plate") val licensePlate: String? = null,
    @SerializedName("color") val color: String? = null,
    @SerializedName("year") val year: Int? = null,
    @SerializedName("rc_number") val rcNumber: String? = null,
    @SerializedName("permit_number") val permitNumber: String? = null
)

data class RecordingConsentRequest(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("role") val role: String = "DRIVER",
    @SerializedName("consented") val consented: Boolean
)

data class EmergencyAlertRequest(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("notes") val notes: String? = null
)

data class SafetyIncidentRequest(
    @SerializedName("ride_id") val rideId: String? = null,
    @SerializedName("category") val category: String,
    @SerializedName("severity") val severity: String = "MEDIUM",
    @SerializedName("description") val description: String
)

data class EmergencyContactDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("relationship") val relationship: String = "Family",
    @SerializedName("phone_number") val phoneNumber: String = "",
    @SerializedName("notify_on_trip_start") val notifyOnTripStart: Boolean = true,
    @SerializedName("notify_on_arrival") val notifyOnArrival: Boolean = true
)

data class EmergencyContactCreateRequest(
    @SerializedName("name") val name: String,
    @SerializedName("relationship") val relationship: String = "Family",
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("notify_on_trip_start") val notifyOnTripStart: Boolean = true,
    @SerializedName("notify_on_arrival") val notifyOnArrival: Boolean = true
)

data class DriverIncidentDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("ticket_number") val ticketNumber: String = "",
    @SerializedName("ride_id") val rideId: String? = null,
    @SerializedName("category") val category: String = "",
    @SerializedName("severity") val severity: String = "MEDIUM",
    @SerializedName("description") val description: String = "",
    @SerializedName("status") val status: String = "OPEN",
    @SerializedName("created_at") val createdAt: String? = null
)

data class PoolManifestEntryDto(
    @SerializedName("booking_id") val bookingId: String? = null,
    @SerializedName("ride_id") val rideId: String? = null,
    @SerializedName("seats_reserved") val seatsReserved: Int = 1,
    @SerializedName("booking_status") val bookingStatus: String = "CONFIRMED",
    @SerializedName("allocated_fare_cents") val allocatedFareCents: Long? = null,
    @SerializedName("driver_earnings_cents") val driverEarningsCents: Long? = null
)

data class PoolStopDto(
    @SerializedName("stop_id") val stopId: String = "",
    @SerializedName("booking_id") val bookingId: String = "",
    @SerializedName("stop_type") val stopType: String = "PICKUP",
    @SerializedName("sequence_order") val sequenceOrder: Int = 0,
    @SerializedName("address") val address: String = "",
    @SerializedName("status") val status: String = "PENDING"
)

data class PoolFinancialSummaryDto(
    @SerializedName("total_fare_cents") val totalFareCents: Long = 0L,
    @SerializedName("total_driver_earnings_cents") val totalDriverEarningsCents: Long = 0L
)

data class SharedPoolTripDto(
    @SerializedName("trip_id") val tripId: String = "",
    @SerializedName("status") val status: String = "",
    @SerializedName("max_seats") val maxSeats: Int = 4,
    @SerializedName("reserved_seats") val reservedSeats: Int = 0,
    @SerializedName("manifest") val manifest: List<PoolManifestEntryDto> = emptyList(),
    @SerializedName("stops") val stops: List<PoolStopDto> = emptyList(),
    @SerializedName("financial_summary") val financialSummary: PoolFinancialSummaryDto? = null
)

data class RideStopDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("sequence_order") val sequenceOrder: Int = 0,
    @SerializedName("address") val address: String = "",
    @SerializedName("status") val status: String = "PENDING"
)

data class TrainingModuleDto(
    @SerializedName("module_id") val moduleId: String = "",
    @SerializedName("module_code") val moduleCode: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("duration_minutes") val durationMinutes: Int = 5,
    @SerializedName("is_completed") val isCompleted: Boolean = false,
    @SerializedName("assessment_passed") val assessmentPassed: Boolean = false
)

data class DriverTicketDto(
    @SerializedName("ticket_id") val ticketId: String = "",
    @SerializedName("subject") val subject: String = "",
    @SerializedName("message") val message: String = "",
    @SerializedName("status") val status: String = "OPEN",
    @SerializedName("admin_response") val adminResponse: String? = null,
    @SerializedName("created_at") val createdAt: String = ""
)

data class CreateDriverTicketRequest(
    @SerializedName("subject") val subject: String,
    @SerializedName("message") val message: String
)

data class NotificationItemDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("body") val body: String = "",
    @SerializedName("event_type") val eventType: String = "",
    @SerializedName("is_read") val isRead: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null
)

data class HeatMapClusterDto(
    @SerializedName("latitude") val latitude: Double = 0.0,
    @SerializedName("longitude") val longitude: Double = 0.0,
    @SerializedName("count") val count: Int = 0,
    @SerializedName("intensity") val intensity: Double = 0.0
)

data class HeatMapResponseDto(
    @SerializedName("clusters") val clusters: List<HeatMapClusterDto> = emptyList(),
    @SerializedName("metric") val metric: String = "demand",
    @SerializedName("window_hours") val windowHours: Int = 2
)

data class RoadRestrictionDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("restriction_type") val restrictionType: String = "ROAD_CLOSURE",
    @SerializedName("latitude") val latitude: Double = 0.0,
    @SerializedName("longitude") val longitude: Double = 0.0,
    @SerializedName("radius_meters") val radiusMeters: Double = 500.0
)

data class PayoutAccountDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("account_type") val accountType: String = "BANK_ACCOUNT",
    @SerializedName("bank_name") val bankName: String? = null,
    @SerializedName("account_number_mask") val accountNumberMask: String? = null,
    @SerializedName("ifsc_code") val ifscCode: String? = null,
    @SerializedName("upi_vpa") val upiVpa: String? = null,
    @SerializedName("beneficiary_name") val beneficiaryName: String? = null,
    @SerializedName("verification_status") val verificationStatus: String = "PENDING_VERIFICATION",
    @SerializedName("verification_method") val verificationMethod: String? = null,
    @SerializedName("verified_at") val verifiedAt: String? = null,
    @SerializedName("rejection_reason") val rejectionReason: String? = null,
    @SerializedName("is_primary") val isPrimary: Boolean = false
)

data class WeeklyGoalDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("week_start_date") val weekStartDate: String = "",
    @SerializedName("target_earnings_rupees") val targetEarningsRupees: Double = 0.0,
    @SerializedName("current_earnings_rupees") val currentEarningsRupees: Double = 0.0,
    @SerializedName("progress_percentage") val progressPercentage: Double = 0.0,
    @SerializedName("target_trips") val targetTrips: Int = 30,
    @SerializedName("current_trips") val currentTrips: Int = 0,
    @SerializedName("is_achieved") val isAchieved: Boolean = false,
    @SerializedName("pace_message") val paceMessage: String = ""
)

data class IncentiveMilestoneDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("program_name") val programName: String = "",
    @SerializedName("tier_name") val tierName: String = "",
    @SerializedName("target_trips") val targetTrips: Int = 0,
    @SerializedName("current_trips") val currentTrips: Int = 0,
    @SerializedName("reward_rupees") val rewardRupees: Double = 0.0,
    @SerializedName("is_unlocked") val isUnlocked: Boolean = false,
    @SerializedName("eligibility_notes") val eligibilityNotes: String? = null
)

data class IncentiveOverviewDto(
    @SerializedName("milestones") val milestones: List<IncentiveMilestoneDto> = emptyList()
)

data class FareBreakdownInnerDto(
    @SerializedName("gross_fare_rupees") val grossFareRupees: Double = 0.0,
    @SerializedName("components_available") val componentsAvailable: Boolean = false
)

data class RideBreakdownDto(
    @SerializedName("ride_id") val rideId: String = "",
    @SerializedName("status") val status: String = "",
    @SerializedName("fare_breakdown") val fareBreakdown: FareBreakdownInnerDto? = null,
    @SerializedName("driver_gross_earnings_rupees") val driverGrossEarningsRupees: Double? = null,
    @SerializedName("commission_cents") val commissionCents: Long? = null,
    @SerializedName("commission_percentage") val commissionPercentage: Double? = null,
    @SerializedName("commission_classification") val commissionClassification: String? = null,
    @SerializedName("commission_snapshot_available") val commissionSnapshotAvailable: Boolean = false,
    @SerializedName("net_driver_earnings_rupees") val netDriverEarningsRupees: Double = 0.0
)

data class DriverProfileUpdateRequest(
    @SerializedName("full_name") val fullName: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("phone_number") val phoneNumber: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null
)

data class AvatarUpdateRequest(
    @SerializedName("avatar_url") val avatarUrl: String
)

data class DriverPreferencesRequest(
    @SerializedName("auto_accept_pool") val autoAcceptPool: Boolean? = null,
    @SerializedName("auto_accept_shuttle") val autoAcceptShuttle: Boolean? = null,
    @SerializedName("high_demand_surge_alerts") val highDemandSurgeAlerts: Boolean? = null,
    @SerializedName("high_surge_alerts") val highSurgeAlerts: Boolean? = null,
    @SerializedName("voice_guidance_enabled") val voiceGuidanceEnabled: Boolean? = null,
    @SerializedName("voice_navigation") val voiceNavigation: Boolean? = null,
    @SerializedName("preferred_corridor") val preferredCorridor: String? = null,
    @SerializedName("minimum_fare_cents") val minimumFareCents: Int? = null,
    @SerializedName("maximum_pickup_distance_km") val maximumPickupDistanceKm: Float? = null,
    @SerializedName("vehicle_categories") val vehicleCategories: List<String>? = null,
    @SerializedName("preferred_drop_zones") val preferredDropZones: List<String>? = null,
    @SerializedName("reroute_on_closure_enabled") val rerouteOnClosureEnabled: Boolean? = null,
    @SerializedName("navigation_provider") val navigationProvider: String? = null
)

data class ClaimNoShowRequest(
    @SerializedName("reason") val reason: String = "PASSENGER_NO_SHOW",
    @SerializedName("notes") val notes: String? = null
)

data class AddPayoutAccountRequest(
    @SerializedName("account_type") val accountType: String = "BANK_ACCOUNT",
    @SerializedName("account_number") val accountNumber: String,
    @SerializedName("ifsc_code") val ifscCode: String,
    @SerializedName("routing_number") val routingNumber: String = ifscCode,
    @SerializedName("beneficiary_name") val beneficiaryName: String,
    @SerializedName("account_holder_name") val accountHolderName: String = beneficiaryName,
    @SerializedName("bank_name") val bankName: String? = null
)

data class GenerateUpiRequest(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("amount_cents") val amountCents: Int
)

data class EmergencyPingRequest(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("note") val note: String? = null
)

data class RerouteRideRequest(
    @SerializedName("reason") val reason: String = "TRAFFIC"
)

data class RejectOfferRequest(
    @SerializedName("reason") val reason: String = "PILOT_DECLINED"
)

data class CoordinatePayloadRequest(
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null
)

data class FinishTripRequest(
    @SerializedName("actual_distance_km") val actualDistanceKm: Double? = null,
    @SerializedName("actual_duration_minutes") val actualDurationMinutes: Double? = null,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null
)

data class StartTripOtpRequest(
    @SerializedName("otp_code") val otpCode: String
)

data class ChatMessagePayloadRequest(
    @SerializedName("message_text") val messageText: String? = null,
    @SerializedName("message") val message: String? = null
)

data class FinancialSupportDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("support_type") val supportType: String = "EMERGENCY_REPAIR",
    @SerializedName("amount_rupees") val amountRupees: Double = 0.0,
    @SerializedName("repayment_terms_weeks") val repaymentTermsWeeks: Int = 4,
    @SerializedName("reason") val reason: String = "",
    @SerializedName("status") val status: String = "PENDING",
    @SerializedName("created_at") val createdAt: String? = null
)

data class SubmitFinancialSupportRequest(
    @SerializedName("support_type") val supportType: String,
    @SerializedName("amount_rupees") val amountRupees: Double,
    @SerializedName("reason") val reason: String,
    @SerializedName("repayment_terms_weeks") val repaymentTermsWeeks: Int = 4
)

data class DriverRatingItemDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("rating") val rating: Float = 5f,
    @SerializedName("cleanliness") val cleanliness: Float? = null,
    @SerializedName("comfort") val comfort: Float? = null,
    @SerializedName("tags") val tags: String? = null,
    @SerializedName("feedback") val feedback: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("appeal_status") val appealStatus: String? = null
)

data class RatingAppealRequest(
    @SerializedName("reason") val reason: String
)

// --- Audio Safety Recording System DTOs ---

data class AudioConsentRequest(
    @SerializedName("consented") val consented: Boolean = true,
    @SerializedName("role") val role: String = "DRIVER",
    @SerializedName("consent_purpose") val consentPurpose: String = "IN_CABIN_SAFETY_DPDP_COMPLIANT",
    @SerializedName("consent_version") val consentVersion: String = "v1.0"
)

data class AudioConsentWithdrawRequest(
    @SerializedName("reason") val reason: String? = null
)

data class AudioConsentResponse(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("session_id") val sessionId: String? = null,
    @SerializedName("consent_status") val consentStatus: String,
    @SerializedName("passenger_consent") val passengerConsent: Boolean = false,
    @SerializedName("driver_consent") val driverConsent: Boolean = false,
    @SerializedName("dual_consent_achieved") val dualConsentAchieved: Boolean = false,
    @SerializedName("is_recording_active") val isRecordingActive: Boolean = false,
    @SerializedName("legal_notice") val legalNotice: String? = null
)

data class AudioRecordingSessionDto(
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("recording_status") val recordingStatus: String,
    @SerializedName("authorization_status") val authorizationStatus: String,
    @SerializedName("started_at") val startedAt: String? = null,
    @SerializedName("stopped_at") val stoppedAt: String? = null,
    @SerializedName("total_segments") val totalSegments: Int = 0,
    @SerializedName("total_duration_seconds") val totalDurationSeconds: Double = 0.0
)

data class AudioRecordingStatusResponse(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("passenger_consent") val passengerConsent: Boolean = false,
    @SerializedName("driver_consent") val driverConsent: Boolean = false,
    @SerializedName("passenger_consent_status") val passengerConsentStatus: String = "NOT_REQUESTED",
    @SerializedName("driver_consent_status") val driverConsentStatus: String = "NOT_REQUESTED",
    @SerializedName("dual_consent_achieved") val dualConsentAchieved: Boolean = false,
    @SerializedName("is_recording_active") val isRecordingActive: Boolean = false,
    @SerializedName("session") val session: AudioRecordingSessionDto? = null
)

data class AudioSegmentUploadResponse(
    @SerializedName("segment_id") val segmentId: String,
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("sequence") val sequence: Int,
    @SerializedName("size_bytes") val sizeBytes: Int,
    @SerializedName("sha256_hash") val sha256Hash: String,
    @SerializedName("retention_expiry") val retentionExpiry: String,
    @SerializedName("status") val status: String
)

// --- Gift Card & Commuter Credit Models ---
data class DriverGiftCardBalanceRequest(
    @SerializedName("code") val code: String,
    @SerializedName("pin") val pin: String? = null
)

data class DriverGiftCardBalanceResponse(
    @SerializedName("gift_card_id") val giftCardId: String,
    @SerializedName("masked_code") val maskedCode: String,
    @SerializedName("initial_amount_cents") val initialAmountCents: Int,
    @SerializedName("initial_amount_rupees") val initialAmountRupees: Double,
    @SerializedName("remaining_balance_cents") val remainingBalanceCents: Int,
    @SerializedName("remaining_balance_rupees") val remainingBalanceRupees: Double,
    @SerializedName("currency") val currency: String,
    @SerializedName("status") val status: String,
    @SerializedName("is_redeemable") val isRedeemable: Boolean,
    @SerializedName("card_design") val cardDesign: String? = null,
    @SerializedName("expires_at") val expiresAt: String? = null,
    @SerializedName("requires_pin") val requiresPin: Boolean = false
)




// In DriverApiModels.kt

data class NavigationStepDto(
    @SerializedName("instruction") val instruction: String,
    @SerializedName("maneuver") val maneuver: String = "STRAIGHT", // STRAIGHT, RIGHT, LEFT, UTURN, ARRIVE
    @SerializedName("distance_meters") val distanceMeters: Double = 100.0,
    @SerializedName("duration_seconds") val durationSeconds: Double = 20.0
)

data class RoutePointDto(
    @SerializedName("latitude", alternate = ["lat"]) val latitude: Double,
    @SerializedName("longitude", alternate = ["lng"]) val longitude: Double
)

data class NavigationGuidanceResponseDto(
    @SerializedName("distance_km") val distanceKm: Double = 0.0,
    @SerializedName("duration_minutes") val durationMinutes: Double = 0.0,
    @SerializedName("polyline") val polyline: String? = null,
    @SerializedName("points") val points: List<RoutePointDto> = emptyList(),
    @SerializedName("steps") val steps: List<NavigationStepDto> = emptyList()
)