package com.vaisentra.mobility.rider.data

import com.google.gson.annotations.SerializedName

// --- Authentication & User Profile (Supabase: users, customer_profiles) ---
data class OtpSendRequest(
    @SerializedName("phone_number") val phoneNumber: String
)

data class OtpVerifyRequest(
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("code") val code: String
)

data class LoginRequest(
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("password") val password: String? = null
)

data class RegisterCustomerRequest(
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("email") val email: String? = null,
    @SerializedName("preferred_language") val preferredLanguage: String = "en"
)

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("token_type") val tokenType: String = "bearer",
    @SerializedName("user_id") val userId: String? = null
)

data class UserProfileDto(
    @SerializedName("id") val id: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("full_name") val fullName: String?,
    @SerializedName("email") val email: String?,
    @SerializedName("role") val role: String = "customer",
    @SerializedName("is_verified") val isVerified: Boolean = true,
    @SerializedName("mfa_enabled") val mfaEnabled: Boolean = false
)

// --- Vehicle Categories & Fares (Supabase: vehicle_categories, fare_rules) ---
data class VehicleCategoryDto(
    @SerializedName("id") val id: String,
    @SerializedName("code") val code: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String?,
    @SerializedName("capacity") val capacity: Int,
    @SerializedName("is_electric") val isElectric: Boolean = true,
    @SerializedName("icon_url") val iconUrl: String? = null,
    @SerializedName("legroom_inches") val legroomInches: Double? = 36.0,
    @SerializedName("quietness_db") val quietnessDb: Double? = 45.0,
    @SerializedName("wheelchair_accessible") val wheelchairAccessible: Boolean = false,
    @SerializedName("pet_friendly") val petFriendly: Boolean = true,
    @SerializedName("child_seat_available") val childSeatAvailable: Boolean = false
)

data class FareEstimateRequest(
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("pickup_latitude") val pickupLatitude: Double,
    @SerializedName("pickup_longitude") val pickupLongitude: Double,
    @SerializedName("destination_latitude") val destinationLatitude: Double,
    @SerializedName("destination_longitude") val destinationLongitude: Double
)

data class FareCompareRequest(
    @SerializedName("pickup_latitude") val pickupLatitude: Double,
    @SerializedName("pickup_longitude") val pickupLongitude: Double,
    @SerializedName("destination_latitude") val destinationLatitude: Double,
    @SerializedName("destination_longitude") val destinationLongitude: Double
)

data class FareEstimateDetailDto(
    @SerializedName("estimate_id") val estimateId: String,
    @SerializedName("distance_km") val distanceKm: Double,
    @SerializedName("estimated_duration_minutes") val estimatedDurationMinutes: Double,
    @SerializedName("estimated_fare_cents") val estimatedFareCents: Int,
    @SerializedName("estimated_fare_rupees") val estimatedFareRupees: Double,
    @SerializedName("base_fare_rupees") val baseFareRupees: Double = 0.0,
    @SerializedName("surge_multiplier") val surgeMultiplier: Double = 1.0,
    @SerializedName("currency") val currency: String = "INR",
    @SerializedName("route_polyline") val routePolyline: String? = null
)

data class VehicleFareComparisonDto(
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("code") val code: String,
    @SerializedName("name") val name: String,
    @SerializedName("capacity") val capacity: Int,
    @SerializedName("description") val description: String?,
    @SerializedName("icon_url") val iconUrl: String?,
    @SerializedName("estimate") val estimate: FareEstimateDetailDto?
)

// --- Ride Lifecycle & Booking (Supabase: rides, ride_assignments, ride_status_events) ---
data class IntermediateStopInput(
    @SerializedName("address") val address: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("dwell_time_minutes") val dwellTimeMinutes: Int = 0
)

data class RideBookingRequest(
    @SerializedName("estimate_id") val estimateId: String? = null,
    @SerializedName("pickup_address") val pickupAddress: String,
    @SerializedName("destination_address") val destinationAddress: String,
    @SerializedName("pickup_latitude") val pickupLatitude: Double,
    @SerializedName("pickup_longitude") val pickupLongitude: Double,
    @SerializedName("destination_latitude") val destinationLatitude: Double,
    @SerializedName("destination_longitude") val destinationLongitude: Double,
    @SerializedName("payment_method") val paymentMethod: String = "UPI",
    @SerializedName("promo_code") val promoCode: String? = null,
    @SerializedName("booking_for_self") val bookingForSelf: Boolean = true,
    @SerializedName("passenger_name") val passengerName: String? = null,
    @SerializedName("passenger_phone") val passengerPhone: String? = null,
    @SerializedName("stops") val stops: List<IntermediateStopInput>? = null
)

data class ActiveRideDto(
    @SerializedName("id") val id: String,
    @SerializedName("status") val status: String,
    @SerializedName("pickup_address") val pickupAddress: String,
    @SerializedName("destination_address") val destinationAddress: String,
    @SerializedName("total_fare_cents") val totalFareCents: Int? = null,
    @SerializedName("otp_code") val otpCode: String? = "4921",
    @SerializedName("driver_name") val driverName: String? = "Bhishma Chauffeur",
    @SerializedName("driver_phone") val driverPhone: String? = "+91 75620 84184",
    @SerializedName("driver_rating") val driverRating: Double? = 4.95,
    @SerializedName("vehicle_plate") val vehiclePlate: String? = "DL-1ER-2026",
    @SerializedName("vehicle_model") val vehicleModel: String? = "Mahindra Treo Plus (Electric)",
    @SerializedName("driver_lat") val driverLat: Double? = null,
    @SerializedName("driver_lng") val driverLng: Double? = null,
    @SerializedName("speed_kmh") val speedKmh: Double? = 28.0,
    @SerializedName("created_at") val createdAt: String? = null
)

data class RideReceiptDto(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("invoice_number") val invoiceNumber: String,
    @SerializedName("total_amount_rupees") val totalAmountRupees: Double,
    @SerializedName("base_fare") val baseFare: Double,
    @SerializedName("distance_charge") val distanceCharge: Double,
    @SerializedName("time_charge") val timeCharge: Double,
    @SerializedName("tax_amount") val taxAmount: Double,
    @SerializedName("discount_amount") val discountAmount: Double = 0.0,
    @SerializedName("distance_km") val distanceKm: Double,
    @SerializedName("duration_minutes") val durationMinutes: Double,
    @SerializedName("completed_at") val completedAt: String
)

// --- Shared Eco-Pool (Supabase: pooled_trips, pooled_bookings) ---
data class PoolBookingRequest(
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("pickup_address") val pickupAddress: String,
    @SerializedName("destination_address") val destinationAddress: String,
    @SerializedName("pickup_latitude") val pickupLatitude: Double,
    @SerializedName("pickup_longitude") val pickupLongitude: Double,
    @SerializedName("destination_latitude") val destinationLatitude: Double,
    @SerializedName("destination_longitude") val destinationLongitude: Double,
    @SerializedName("seats_reserved") val seatsReserved: Int = 1
)

data class PoolTripDto(
    @SerializedName("id") val id: String,
    @SerializedName("corridor_name") val corridorName: String = "Anand Vihar - Connaught Place Metro Express",
    @SerializedName("occupied_seats") val occupiedSeats: Int = 2,
    @SerializedName("max_seats") val maxSeats: Int = 4,
    @SerializedName("status") val status: String = "ACTIVE_BOARDING",
    @SerializedName("per_seat_fare_rupees") val perSeatFareRupees: Double = 35.0
)

data class PoolMessageDto(
    @SerializedName("id") val id: String,
    @SerializedName("sender_name") val senderName: String,
    @SerializedName("message") val message: String,
    @SerializedName("timestamp") val timestamp: String
)

// --- Wallet & Fintech (Supabase: wallets, wallet_transactions, payments) ---
data class WalletBalanceDto(
    @SerializedName("wallet_id") val walletId: String,
    @SerializedName("balance_cents") val balanceCents: Int,
    @SerializedName("balance_rupees") val balanceRupees: Double,
    @SerializedName("currency") val currency: String = "INR",
    @SerializedName("auto_topup_enabled") val autoTopupEnabled: Boolean = false,
    @SerializedName("auto_topup_threshold") val autoTopupThreshold: Double = 100.0,
    @SerializedName("auto_topup_amount") val autoTopupAmount: Double = 500.0
)

data class TopupRequest(
    @SerializedName("amount_rupees") val amountRupees: Double,
    @SerializedName("payment_method") val paymentMethod: String = "UPI"
)

data class SplitFareRequest(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("co_passenger_phones") val coPassengerPhones: List<String>,
    @SerializedName("split_type") val splitType: String = "EQUAL"
)

// --- Safety & Emergency (Supabase: emergency_alerts, incident_reports, emergency_contacts) ---
data class SosTriggerRequest(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("alert_type") val alertType: String = "SOS_112_BUTTON"
)

data class EmergencyContactDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("relationship") val relationship: String = "Family"
)

data class IncidentReportRequest(
    @SerializedName("ride_id") val rideId: String? = null,
    @SerializedName("category") val category: String,
    @SerializedName("severity") val severity: String = "HIGH",
    @SerializedName("description") val description: String
)

// --- Support, Loyalty & Settings ---
data class SupportTicketDto(
    @SerializedName("id") val id: String,
    @SerializedName("subject") val subject: String,
    @SerializedName("category") val category: String,
    @SerializedName("status") val status: String,
    @SerializedName("created_at") val createdAt: String
)

data class LoyaltySummaryDto(
    @SerializedName("tier_name") val tierName: String = "Green Club Gold",
    @SerializedName("points_balance") val pointsBalance: Int = 1450,
    @SerializedName("co2_offset_kg") val co2OffsetKg: Double = 84.5,
    @SerializedName("referral_code") val referralCode: String = "ELECTRIC100"
)

data class NotificationItemDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("body") val body: String,
    @SerializedName("category") val category: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("is_read") val isRead: Boolean = false
)

data class SavedPlaceDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("address") val address: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("tag") val tag: String = "HOME"
)

