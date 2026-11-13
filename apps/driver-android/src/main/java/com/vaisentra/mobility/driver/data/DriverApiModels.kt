package com.vaisentra.mobility.driver.data

import com.google.gson.annotations.SerializedName

// --- Driver Auth & Profile (Supabase: users, driver_profiles, driver_documents) ---
data class DriverLoginRequest(
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("password") val password: String? = null
)

data class DriverProfileDto(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("license_number") val licenseNumber: String,
    @SerializedName("badge_number") val badgeNumber: String,
    @SerializedName("assigned_vehicle_plate") val assignedVehiclePlate: String = "DL-1ER-4921",
    @SerializedName("assigned_vehicle_model") val assignedVehicleModel: String = "Mahindra Treo Electric",
    @SerializedName("rating") val rating: Double = 4.95,
    @SerializedName("completed_trips") val completedTrips: Int = 2420,
    @SerializedName("acceptance_rate") val acceptanceRate: Double = 96.5,
    @SerializedName("onboarding_status") val onboardingStatus: String = "ACTIVE_VERIFIED"
)

data class DriverDocumentDto(
    @SerializedName("id") val id: String,
    @SerializedName("document_type") val documentType: String,
    @SerializedName("status") val status: String = "APPROVED",
    @SerializedName("expiry_date") val expiryDate: String,
    @SerializedName("days_until_expiry") val daysUntilExpiry: Int = 45
)

// --- Duty & Location Telemetry (Supabase: driver_availability, driver_locations, driver_shifts) ---
data class DutyStatusUpdateRequest(
    @SerializedName("is_online") val isOnline: Boolean,
    @SerializedName("status") val status: String = "AVAILABLE"
)

data class DriverLocationPingRequest(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("heading") val heading: Double? = 90.0,
    @SerializedName("speed_mps") val speedMps: Double? = 7.8, // ~28 km/h
    @SerializedName("accuracy_m") val accuracyM: Double? = 2.5,
    @SerializedName("is_mock_location") val isMockLocation: Boolean = false,
    @SerializedName("active_ride_id") val activeRideId: String? = null
)

data class OdometerLogRequest(
    @SerializedName("reading_km") val readingKm: Double,
    @SerializedName("battery_soc_pct") val batterySocPct: Int,
    @SerializedName("photo_url") val photoUrl: String? = null
)

// --- Ride Dispatch & Turn-by-Turn Ops (Supabase: rides, ride_assignments) ---
data class DispatchOfferDto(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("assignment_id") val assignmentId: String,
    @SerializedName("passenger_name") val passengerName: String,
    @SerializedName("passenger_phone") val passengerPhone: String,
    @SerializedName("passenger_rating") val passengerRating: Double = 4.90,
    @SerializedName("pickup_address") val pickupAddress: String,
    @SerializedName("destination_address") val destinationAddress: String,
    @SerializedName("distance_km") val distanceKm: Double,
    @SerializedName("pickup_distance_meters") val pickupDistanceMeters: Int,
    @SerializedName("estimated_fare_rupees") val estimatedFareRupees: Double,
    @SerializedName("payment_mode") val paymentMode: String = "UPI",
    @SerializedName("timeout_seconds") val timeoutSeconds: Int = 15
)

data class OfferResponseRequest(
    @SerializedName("assignment_id") val assignmentId: String,
    @SerializedName("action") val action: String, // ACCEPT or DECLINE
    @SerializedName("reason") val reason: String? = null
)

data class VerifyOtpRequest(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("otp_code") val otpCode: String
)

data class CashCollectRequest(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("collected_amount_rupees") val collectedAmountRupees: Double,
    @SerializedName("tendered_cash") val tenderedCash: Double
)

// --- Financials, Payouts & Ledger (Supabase: driver_ledger_entries, driver_payout_accounts) ---
data class DriverEarningsSummaryDto(
    @SerializedName("today_gross_rupees", alternate = ["today_earnings_cents"]) val todayGrossRupees: Double = 1420.0,
    @SerializedName("today_net_rupees", alternate = ["weekly_earnings_cents"]) val todayNetRupees: Double = 1278.0,
    @SerializedName("completed_trips_today", alternate = ["completed_trips"]) val completedTripsToday: Int = 11,
    @SerializedName("online_hours_today") val onlineHoursToday: Double = 6.4,
    @SerializedName("wallet_balance_rupees", alternate = ["wallet_balance_cents"]) val walletBalanceRupees: Double = 3840.50,
    @SerializedName("pending_payout_rupees") val pendingPayoutRupees: Double = 800.0
) {
    val displayTodayNetRupees: Double
        get() = if (todayGrossRupees > 10000.0) (todayGrossRupees / 100.0) else todayGrossRupees

    val displayWalletBalanceRupees: Double
        get() = if (walletBalanceRupees > 10000.0) (walletBalanceRupees / 100.0) else walletBalanceRupees
}

data class PayoutRequestDto(
    @SerializedName("amount_rupees") val amountRupees: Double,
    @SerializedName("payout_method") val payoutMethod: String = "UPI_IMPS",
    @SerializedName("destination_vpa") val destinationVpa: String = "ramlal@upi"
)

data class LedgerItemDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("amount_rupees") val amountRupees: Double,
    @SerializedName("type") val type: String, // CREDIT or DEBIT
    @SerializedName("timestamp") val timestamp: String
)

// --- Performance Scorecard & Incentives (Supabase: driver_weekly_goals, driver_daily_targets) ---
data class ScorecardDto(
    @SerializedName("rating") val rating: Double = 4.95,
    @SerializedName("acceptance_rate") val acceptanceRate: Double = 96.5,
    @SerializedName("cancellation_rate") val cancellationRate: Double = 1.2,
    @SerializedName("safety_incident_count") val safetyIncidentCount: Int = 0,
    @SerializedName("cleanliness_score") val cleanlinessScore: Double = 4.98
)

data class DailyIncentiveDto(
    @SerializedName("target_rides") val targetRides: Int = 12,
    @SerializedName("completed_rides") val completedRides: Int = 9,
    @SerializedName("reward_rupees") val rewardRupees: Double = 300.0,
    @SerializedName("expires_in_hours") val expiresInHours: Double = 3.5
)

