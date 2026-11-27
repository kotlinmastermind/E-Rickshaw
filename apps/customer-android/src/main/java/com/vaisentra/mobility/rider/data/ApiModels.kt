package com.vaisentra.mobility.rider.data

import com.google.gson.annotations.SerializedName

// --- Authentication & User Profile (Supabase: users, customer_profiles) ---
data class OtpSendRequest(
    @SerializedName("phone_number") val phoneNumber: String
)

data class OtpVerifyRequest(
    @SerializedName("phone_number")
    val phoneNumber: String,

    @SerializedName("otp_code")
    val otpCode: String,

    @SerializedName("role")
    val role: String = "customer"
)

data class LoginRequest(
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("password") val password: String? = null
)

data class RegisterCustomerRequest(
    @SerializedName("full_name") val fullName: String,
    @SerializedName("email") val email: String? = null
)

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("token_type") val tokenType: String = "bearer",
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("is_new_user") val isNewUser: Boolean? = null
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
data class UserMeResponse(
    @SerializedName("id") val id: String,
    @SerializedName("phone_number") val phoneNumber: String?,
    @SerializedName("email") val email: String?,
    @SerializedName("full_name") val fullName: String?,
    @SerializedName("role") val role: String = "customer",
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    @SerializedName("has_pin") val hasPin: Boolean = false,
    @SerializedName("has_passkey") val hasPasskey: Boolean = false,
    @SerializedName("biometric_enabled") val biometricEnabled: Boolean = false
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
    @SerializedName("category_id") val categoryId: String? = null,
    @SerializedName("pickup_address") val pickupAddress: String,
    @SerializedName("destination_address") val destinationAddress: String,
    @SerializedName("pickup_latitude") val pickupLatitude: Double,
    @SerializedName("pickup_longitude") val pickupLongitude: Double,
    @SerializedName("destination_latitude") val destinationLatitude: Double,
    @SerializedName("destination_longitude") val destinationLongitude: Double,
    @SerializedName("payment_method") val paymentMethod: String = "UPI",
    @SerializedName("booking_mode") val bookingMode: String = "STANDARD",
    @SerializedName("business_cost_center") val businessCostCenter: String? = null,
    @SerializedName("promo_code") val promoCode: String? = null,
    @SerializedName("booking_for_self") val bookingForSelf: Boolean = true,
    @SerializedName("passenger_name") val passengerName: String? = null,
    @SerializedName("passenger_phone") val passengerPhone: String? = null,
    @SerializedName("stops") val stops: List<IntermediateStopInput>? = null
)

data class ActiveRideDto(
    @SerializedName("id", alternate = ["ride_id"]) val id: String,
    @SerializedName("status") val status: String,
    @SerializedName("pickup_address") val pickupAddress: String,
    @SerializedName("destination_address") val destinationAddress: String,
    @SerializedName("total_fare_cents", alternate = ["estimated_fare_cents", "final_fare_cents"]) val totalFareCents: Int? = null,
    @SerializedName("otp_code", alternate = ["start_otp"]) val otpCode: String? = null,
    @SerializedName("driver") val driver: AssignedDriverNestedDto? = null,
    @SerializedName("driver_name") val driverName: String? = null,
    @SerializedName("driver_phone") val driverPhone: String? = null,
    @SerializedName("driver_rating") val driverRating: Double? = null,
    @SerializedName("vehicle_plate") val vehiclePlate: String? = null,
    @SerializedName("vehicle_model") val vehicleModel: String? = null,
    @SerializedName("driver_lat") val driverLat: Double? = null,
    @SerializedName("driver_lng") val driverLng: Double? = null,
    @SerializedName("pickup_latitude") val pickupLatitude: Double? = null,
    @SerializedName("pickup_longitude") val pickupLongitude: Double? = null,
    @SerializedName("flight_number") val flightNumber: String? = null,
    @SerializedName("speed_kmh") val speedKmh: Double? = null,
    @SerializedName("created_at") val createdAt: String? = null
) {
    val fare: Double get() = (totalFareCents ?: 12900) / 100.0
}

data class ActiveRideWrapperDto(
    @SerializedName("active_ride") val activeRide: ActiveRideDto?
)

data class AssignedDriverNestedDto(
    @SerializedName("driver_id") val driverId: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("license_number") val licenseNumber: String? = null,
    @SerializedName("rating") val rating: Double? = null
)

data class RideReceiptDto(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("invoice_number", alternate = ["receipt_id"]) val invoiceNumber: String = "",
    @SerializedName("total_amount_rupees", alternate = ["total_charged_rupees"]) val totalAmountRupees: Double = 0.0,
    @SerializedName("base_fare", alternate = ["base_fare_rupees"]) val baseFare: Double = 0.0,
    @SerializedName("distance_charge", alternate = ["distance_fare_rupees"]) val distanceCharge: Double = 0.0,
    @SerializedName("time_charge", alternate = ["duration_fare_rupees"]) val timeCharge: Double = 0.0,
    @SerializedName("tax_amount", alternate = ["gst_tax_rupees"]) val taxAmount: Double = 0.0,
    @SerializedName("discount_amount", alternate = ["discount_rupees"]) val discountAmount: Double = 0.0,
    @SerializedName("distance_km") val distanceKm: Double = 0.0,
    @SerializedName("duration_minutes") val durationMinutes: Double = 0.0,
    @SerializedName("completed_at", alternate = ["date"]) val completedAt: String = "",
    @SerializedName("payment_method") val paymentMethod: String? = null,
    @SerializedName("driver_name", alternate = ["chauffeur_name"]) val driverName: String? = null,
    @SerializedName("vehicle_model") val vehicleModel: String? = null,
    @SerializedName("license_plate") val licensePlate: String? = null
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
    @SerializedName("balance_cents") val balanceCents: Int = 0,
    @SerializedName("balance_rupees") val balanceRupees: Double = (balanceCents / 100.0),
    @SerializedName("currency") val currency: String = "INR",
    @SerializedName("auto_topup_enabled") val autoTopupEnabled: Boolean = false,
    @SerializedName("auto_topup_threshold") val autoTopupThreshold: Double = 100.0,
    @SerializedName("auto_topup_amount") val autoTopupAmount: Double = 500.0
) {
    val displayBalanceRupees: Double
        get() = if (balanceRupees > 0.0) balanceRupees else (balanceCents / 100.0)
}

data class TopupCreateOrderRequest(
    @SerializedName("amount_cents") val amountCents: Int,
    @SerializedName("currency") val currency: String = "INR",
    @SerializedName("idempotency_key") val idempotencyKey: String? = null
)

data class TopupOrderResponse(
    @SerializedName("order_id") val orderId: String,
    @SerializedName("payment_id") val paymentId: String? = null,
    @SerializedName(value = "amount_cents", alternate = ["amount"]) val amountCents: Int = 0,
    @SerializedName("amount_rupees") val amountRupees: Double = 0.0,
    @SerializedName("currency") val currency: String = "INR",
    @SerializedName("key_id") val keyId: String? = null,
    @SerializedName("receipt") val receipt: String? = null,
    @SerializedName("reused_existing_order") val reusedExistingOrder: Boolean = false
)

data class TopupVerifyRequest(
    @SerializedName("razorpay_order_id") val razorpayOrderId: String,
    @SerializedName("razorpay_payment_id") val razorpayPaymentId: String,
    @SerializedName("razorpay_signature") val razorpaySignature: String
)

data class TopupVerifyResponse(
    @SerializedName("status") val status: String,
    @SerializedName("message") val message: String,
    @SerializedName("balance_rupees") val balanceRupees: Double
)

data class RidePaymentOrderRequest(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("idempotency_key") val idempotencyKey: String? = null,
    @SerializedName("amount_cents") val amountCents: Int? = null,
    @SerializedName("amount_rupees") val amountRupees: Double? = null
)

data class RidePaymentOrderResponse(
    @SerializedName("id") val id: String? = null,
    @SerializedName("order_id") val orderId: String? = null,
    @SerializedName("payment_id") val paymentId: String? = null,
    @SerializedName(value = "amount_cents", alternate = ["amount"]) val amountCents: Int = 0,
    @SerializedName("currency") val currency: String = "INR",
    @SerializedName("key_id") val keyId: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("reused_existing_order") val reusedExistingOrder: Boolean = false
) {
    val effectiveOrderId: String
        get() = orderId ?: id ?: ""
}

data class RidePaymentStatusDto(
    @SerializedName("ride_id") val rideId: String = "",
    @SerializedName("payment_id") val paymentId: String? = null,
    @SerializedName("status") val status: String = "UNPAID",
    @SerializedName("amount_cents") val amountCents: Int = 0,
    @SerializedName("refunded_amount_cents") val refundedAmountCents: Int = 0,
    @SerializedName("provider_order_id") val providerOrderId: String? = null,
    @SerializedName("provider_payment_id") val providerPaymentId: String? = null,
    @SerializedName("gateway_verified") val gatewayVerified: Boolean = false
)

data class RidePaymentVerifyRequest(
    @SerializedName("razorpay_order_id") val razorpayOrderId: String,
    @SerializedName("razorpay_payment_id") val razorpayPaymentId: String,
    @SerializedName("razorpay_signature") val razorpaySignature: String,
    @SerializedName("ride_id") val rideId: String? = null
)

data class RidePaymentVerifyResponse(
    @SerializedName("success") val success: Boolean = true,
    @SerializedName("status") val status: String = "CAPTURED",
    @SerializedName("message") val message: String = "",
    @SerializedName("payment_id") val paymentId: String = "",
    @SerializedName("order_id") val orderId: String = "",
    @SerializedName("ride_id") val rideId: String? = null
)

data class TopupRequest(
    @SerializedName("amount_cents", alternate = ["amount_rupees"]) val amountCents: Int,
    @SerializedName("payment_method") val paymentMethod: String = "UPI",
    @SerializedName("idempotency_key") val idempotencyKey: String? = null
) {
    companion object {
        fun fromRupees(amountRupees: Double, paymentMethod: String = "UPI", idempotencyKey: String? = null): TopupRequest {
            return TopupRequest(
                amountCents = (amountRupees * 100).toInt(),
                paymentMethod = paymentMethod,
                idempotencyKey = idempotencyKey
            )
        }
    }
}

data class SplitFareRequest(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("co_passenger_phones") val coPassengerPhones: List<String>,
    @SerializedName("split_type") val splitType: String = "EQUAL"
)

data class RescheduleRideRequest(
    @SerializedName("new_pickup_time") val newPickupTime: String,
    @SerializedName("reason") val reason: String? = null,
    @SerializedName("flight_number") val flightNumber: String? = null
)

// --- Safety & Emergency (Supabase: emergency_alerts, incident_reports, emergency_contacts) ---
data class SosTriggerRequest(
    @SerializedName("ride_id") val rideId: String? = null,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("alert_type") val alertType: String = "SOS_112_BUTTON",
    @SerializedName("notes") val notes: String? = null
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

// 1. Updated SavedPlaceDto supporting both 'name' and backend's 'label' column
data class SavedPlaceDto(
    @SerializedName("id") val id: String,
    @SerializedName("name", alternate = ["label"]) val name: String,
    @SerializedName("address") val address: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("tag", alternate = ["category"]) val tag: String = "HOME"
)

// 2. Live Shared Eco-Pool Estimate Request & Response (Supabase: pooled_trips, fare_rules)
data class SharedEstimateRequestDto(
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("pickup_latitude") val pickupLatitude: Double,
    @SerializedName("pickup_longitude") val pickupLongitude: Double,
    @SerializedName("destination_latitude") val destinationLatitude: Double,
    @SerializedName("destination_longitude") val destinationLongitude: Double,
    @SerializedName("seats_requested") val seatsRequested: Int = 1
)

data class SharedEstimateResponseDto(
    @SerializedName("estimate_id") val estimateId: String,
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("is_shared") val isShared: Boolean = true,
    @SerializedName("seats_requested") val seatsRequested: Int = 1,
    @SerializedName("distance_km") val distanceKm: Double = 0.0,
    @SerializedName("estimated_duration_minutes") val estimatedDurationMinutes: Double = 0.0,
    @SerializedName("solo_fare_cents") val soloFareCents: Int = 0,
    @SerializedName("shared_fare_cents") val sharedFareCents: Int = 0,
    @SerializedName("shared_discount_percentage") val sharedDiscountPercentage: Double = 25.0,
    @SerializedName("savings_cents") val savingsCents: Int = 0,
    @SerializedName("total_payable_cents") val totalPayableCents: Int = 0
) {
    val soloFareRupees: Double get() = soloFareCents / 100.0
    val sharedFareRupees: Double get() = sharedFareCents / 100.0
    val savingsRupees: Double get() = savingsCents / 100.0
}
data class FareRuleDto(
    @SerializedName("id") val id: String,
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("base_fare_rupees") val baseFareRupees: Int,
    @SerializedName("per_km_rupees") val perKmRupees: Int,
    @SerializedName("minimum_fare_rupees") val minimumFareRupees: Int,
    @SerializedName("is_active") val isActive: Boolean = true
)

data class NearbyVehicleDto(
    @SerializedName("id") val id: String,
    @SerializedName("driver_name") val driverName: String? = null,
    @SerializedName("driver_phone") val driverPhone: String? = null,
    @SerializedName("rating") val rating: Double? = null,
    @SerializedName("category_code") val categoryCode: String? = null,
    @SerializedName("vehicle_type_name") val vehicleTypeName: String? = null,
    @SerializedName("model_name") val modelName: String? = null,
    @SerializedName("license_plate") val licensePlate: String? = null,
    @SerializedName("icon_type") val iconType: String? = null,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("bearing") val bearing: Double? = null,
    @SerializedName("battery_level") val batteryLevel: Int? = null,
    @SerializedName("distance_km") val distanceKm: Double? = null,
    @SerializedName("eta_minutes") val etaMinutes: Int? = null
)

data class NearbyVehiclesResponse(
    @SerializedName("total_available") val totalAvailable: Int = 0,
    @SerializedName("vehicles") val vehicles: List<NearbyVehicleDto> = emptyList()
)

data class ReverseGeocodeRequest(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double
)

data class ReverseGeocodeResponse(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("formatted_address") val formattedAddress: String
)

data class DeviceTokenRegisterRequest(
    @SerializedName("fcm_token") val fcmToken: String,
    @SerializedName("device_os") val deviceOs: String = "ANDROID",
    @SerializedName("device_label") val deviceLabel: String? = null
)

data class DeviceTokenDeactivateRequest(
    @SerializedName("fcm_token") val fcmToken: String? = null
)

data class RiderLogoutRequest(
    @SerializedName("fcm_token") val fcmToken: String? = null
)

data class BookingDraftState(
    val pickupAddress: String = "Fetching current location...",
    val pickupLat: Double = 0.0,
    val pickupLng: Double = 0.0,
    val destinationAddress: String = "",
    val destinationLat: Double = 0.0,
    val destinationLng: Double = 0.0,
    val selectedCategoryCode: String = "E_RICKSHAW_STANDARD",
    val paymentMethod: String = "UPI"
)

// --- B2B Corporate Billing & Enterprise DTOs ---
data class CorporateRegistrationRequest(
    @SerializedName("company_name") val companyName: String,
    @SerializedName("tax_id") val taxId: String,
    @SerializedName("billing_email") val billingEmail: String,
    @SerializedName("billing_contact_name") val billingContactName: String? = null,
    @SerializedName("billing_contact_phone") val billingContactPhone: String? = null,
    @SerializedName("trade_name") val tradeName: String? = null,
    @SerializedName("registered_address") val registeredAddress: String? = null,
    @SerializedName("billing_address") val billingAddress: String? = null,
    @SerializedName("default_cost_center") val defaultCostCenter: String? = "OPS-CORP",
    @SerializedName("employee_id") val employeeId: String? = "EMP-001",
    @SerializedName("department") val department: String? = "Executive"
)

data class CorporateMembershipDto(
    @SerializedName("employee_id") val employeeId: String? = null,
    @SerializedName("department") val department: String? = null,
    @SerializedName("cost_center") val costCenter: String? = null,
    @SerializedName("designation") val designation: String? = null,
    @SerializedName("status") val status: String = "ACTIVE",
    @SerializedName("monthly_ride_limit") val monthlyRideLimit: Int = 50,
    @SerializedName("per_ride_limit_rupees") val perRideLimitRupees: Double = 2000.0,
    @SerializedName("approval_required") val approvalRequired: Boolean = false
)

data class CorporatePolicyDto(
    @SerializedName("id") val id: String,
    @SerializedName("policy_name") val policyName: String? = null,
    @SerializedName("allowed_categories") val allowedCategories: String? = null,
    @SerializedName("max_fare_rupees") val maxFareRupees: Double = 5000.0,
    @SerializedName("curfew_start") val curfewStart: String? = null,
    @SerializedName("curfew_end") val curfewEnd: String? = null,
    @SerializedName("approval_required") val approvalRequired: Boolean = false
)

data class CorporateAccountDto(
    @SerializedName("id") val id: String,
    @SerializedName("company_name") val companyName: String,
    @SerializedName("trade_name") val tradeName: String? = null,
    @SerializedName("tax_id") val taxId: String,
    @SerializedName("pan") val pan: String? = null,
    @SerializedName("billing_email") val billingEmail: String,
    @SerializedName("billing_contact_name") val billingContactName: String? = null,
    @SerializedName("billing_contact_phone") val billingContactPhone: String? = null,
    @SerializedName("registered_address") val registeredAddress: String? = null,
    @SerializedName("billing_address") val billingAddress: String? = null,
    @SerializedName("status") val status: String = "PENDING_APPROVAL",
    @SerializedName("rejection_reason") val rejectionReason: String? = null,
    @SerializedName("credit_limit_rupees") val creditLimitRupees: Double = 500000.0,
    @SerializedName("balance_due_rupees") val balanceDueRupees: Double = 0.0,
    @SerializedName("monthly_spending_limit_rupees") val monthlySpendingLimitRupees: Double = 100000.0,
    @SerializedName("currency") val currency: String = "INR",
    @SerializedName("default_cost_center") val defaultCostCenter: String? = "OPS-CORP",
    @SerializedName("is_admin") val isAdmin: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("membership") val membership: CorporateMembershipDto? = null,
    @SerializedName("policies") val policies: List<CorporatePolicyDto> = emptyList()
)

data class CorporateEmployeeDto(
    @SerializedName("id") val id: String,
    @SerializedName("employee_id") val employeeId: String,
    @SerializedName("employee_name") val employeeName: String,
    @SerializedName("employee_email") val employeeEmail: String,
    @SerializedName("employee_phone") val employeePhone: String? = null,
    @SerializedName("department") val department: String? = null,
    @SerializedName("cost_center") val costCenter: String? = null,
    @SerializedName("designation") val designation: String? = null,
    @SerializedName("status") val status: String = "ACTIVE",
    @SerializedName("monthly_ride_limit") val monthlyRideLimit: Int = 50,
    @SerializedName("per_ride_limit_rupees") val perRideLimitRupees: Double = 2000.0,
    @SerializedName("approval_required") val approvalRequired: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null
)

data class CorporateInvoiceItemDto(
    @SerializedName("id") val id: String,
    @SerializedName("ride_id") val rideId: String? = null,
    @SerializedName("employee_id") val employeeId: String? = null,
    @SerializedName("passenger_name") val passengerName: String? = null,
    @SerializedName("cost_center") val costCenter: String? = null,
    @SerializedName("fare_rupees") val fareRupees: Double = 0.0,
    @SerializedName("tax_rupees") val taxRupees: Double = 0.0,
    @SerializedName("total_rupees") val totalRupees: Double = 0.0
)

data class CorporateInvoiceDto(
    @SerializedName("id") val id: String,
    @SerializedName("invoice_number") val invoiceNumber: String,
    @SerializedName("billing_period") val billingPeriod: String,
    @SerializedName("subtotal_rupees") val subtotalRupees: Double = 0.0,
    @SerializedName("tax_rupees") val taxRupees: Double = 0.0,
    @SerializedName("total_rupees") val totalRupees: Double = 0.0,
    @SerializedName("amount_paid_rupees") val amountPaidRupees: Double = 0.0,
    @SerializedName("status") val status: String = "ISSUED",
    @SerializedName("pdf_url") val pdfUrl: String? = null,
    @SerializedName("due_date") val dueDate: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class CorporateInvoiceDetailDto(
    @SerializedName("id") val id: String,
    @SerializedName("invoice_number") val invoiceNumber: String,
    @SerializedName("corporate_id") val corporateId: String,
    @SerializedName("company_name") val companyName: String,
    @SerializedName("tax_id") val taxId: String,
    @SerializedName("billing_address") val billingAddress: String? = null,
    @SerializedName("billing_period") val billingPeriod: String,
    @SerializedName("subtotal_rupees") val subtotalRupees: Double = 0.0,
    @SerializedName("tax_rupees") val taxRupees: Double = 0.0,
    @SerializedName("platform_fee_rupees") val platformFeeRupees: Double = 0.0,
    @SerializedName("total_rupees") val totalRupees: Double = 0.0,
    @SerializedName("amount_paid_rupees") val amountPaidRupees: Double = 0.0,
    @SerializedName("outstanding_rupees") val outstandingRupees: Double = 0.0,
    @SerializedName("status") val status: String = "ISSUED",
    @SerializedName("pdf_url") val pdfUrl: String? = null,
    @SerializedName("due_date") val dueDate: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("line_items") val lineItems: List<CorporateInvoiceItemDto> = emptyList()
)

// --- Audio Safety Recording System DTOs ---

data class AudioConsentRequest(
    @SerializedName("consented") val consented: Boolean = true,
    @SerializedName("role") val role: String = "CUSTOMER",
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

// --- Gift Cards Suite DTOs ---

data class GiftCardDesignDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String,
    @SerializedName("primary_color") val primaryColor: String,
    @SerializedName("accent_color") val accentColor: String,
    @SerializedName("background_gradient") val backgroundGradient: List<String> = emptyList(),
    @SerializedName("badge_text") val badgeText: String = "VIP"
)

data class DeliveryMethodDto(
    @SerializedName("id") val id: String,
    @SerializedName("label") val label: String,
    @SerializedName("description") val description: String
)

data class GiftCardConfigDto(
    @SerializedName("currency") val currency: String = "INR",
    @SerializedName("currency_symbol") val currencySymbol: String = "₹",
    @SerializedName("min_amount_rupees") val minAmountRupees: Double = 100.0,
    @SerializedName("min_amount_cents") val minAmountCents: Int = 10000,
    @SerializedName("max_amount_rupees") val maxAmountRupees: Double = 10000.0,
    @SerializedName("max_amount_cents") val maxAmountCents: Int = 1000000,
    @SerializedName("preset_denominations_rupees") val presetDenominationsRupees: List<Int> = listOf(250, 500, 1000, 2000, 5000),
    @SerializedName("preset_denominations_cents") val presetDenominationsCents: List<Int> = listOf(25000, 50000, 100000, 200000, 500000),
    @SerializedName("validity_days") val validityDays: Int = 365,
    @SerializedName("convenience_fee_cents") val convenienceFeeCents: Int = 0,
    @SerializedName("tax_percentage") val taxPercentage: Double = 0.0,
    @SerializedName("available_designs") val availableDesigns: List<GiftCardDesignDto> = emptyList(),
    @SerializedName("delivery_methods") val deliveryMethods: List<DeliveryMethodDto> = emptyList(),
    @SerializedName("terms_summary") val termsSummary: List<String> = emptyList()
)

data class GiftCardPurchaseRequest(
    @SerializedName("amount_cents") val amountCents: Int,
    @SerializedName("card_design") val cardDesign: String = "ROYAL_GOLD",
    @SerializedName("recipient_name") val recipientName: String? = null,
    @SerializedName("recipient_email") val recipientEmail: String? = null,
    @SerializedName("recipient_phone") val recipientPhone: String? = null,
    @SerializedName("sender_name") val senderName: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("delivery_method") val deliveryMethod: String = "IN_APP",
    @SerializedName("payment_method") val paymentMethod: String = "UPI",
    @SerializedName("idempotency_key") val idempotencyKey: String? = null
)

data class GiftCardPurchaseResponse(
    @SerializedName("gift_card_id") val giftCardId: String,
    @SerializedName("code") val code: String,
    @SerializedName("masked_code") val maskedCode: String,
    @SerializedName("pin") val pin: String? = null,
    @SerializedName("initial_value_cents") val initialValueCents: Int,
    @SerializedName("initial_value_rupees") val initialValueRupees: Double,
    @SerializedName("remaining_value_cents") val remainingValueCents: Int,
    @SerializedName("remaining_value_rupees") val remainingValueRupees: Double,
    @SerializedName("card_design") val cardDesign: String,
    @SerializedName("sender_name") val senderName: String? = null,
    @SerializedName("recipient_name") val recipientName: String? = null,
    @SerializedName("recipient_email") val recipientEmail: String? = null,
    @SerializedName("recipient_phone") val recipientPhone: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("status") val status: String,
    @SerializedName("expires_at") val expiresAt: String,
    @SerializedName("payment_status") val paymentStatus: String,
    @SerializedName("created_at") val createdAt: String
)

data class GiftCardBalanceRequest(
    @SerializedName("code") val code: String,
    @SerializedName("pin") val pin: String? = null
)

data class GiftCardBalanceResponse(
    @SerializedName("gift_card_id") val giftCardId: String,
    @SerializedName("masked_code") val maskedCode: String,
    @SerializedName("card_design") val cardDesign: String,
    @SerializedName("initial_value_rupees") val initialValueRupees: Double,
    @SerializedName("remaining_value_cents") val remainingValueCents: Int,
    @SerializedName("remaining_value_rupees") val remainingValueRupees: Double,
    @SerializedName("status") val status: String,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("sender_name") val senderName: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("expires_at") val expiresAt: String
)

data class GiftCardRedeemRequest(
    @SerializedName("code") val code: String,
    @SerializedName("pin") val pin: String? = null,
    @SerializedName("idempotency_key") val idempotencyKey: String? = null
)

data class GiftCardRedeemResponse(
    @SerializedName("status") val status: String,
    @SerializedName("gift_card_id") val giftCardId: String,
    @SerializedName("masked_code") val maskedCode: String,
    @SerializedName("redeemed_amount_cents") val redeemedAmountCents: Int,
    @SerializedName("redeemed_amount_rupees") val redeemedAmountRupees: Double,
    @SerializedName("new_wallet_balance_rupees") val newWalletBalanceRupees: Double,
    @SerializedName("sender_name") val senderName: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("card_design") val cardDesign: String,
    @SerializedName("redeemed_at") val redeemedAt: String
)

data class GiftCardSummaryDto(
    @SerializedName("gift_card_id") val giftCardId: String,
    @SerializedName("masked_code") val maskedCode: String,
    @SerializedName("initial_value_cents") val initialValueCents: Int,
    @SerializedName("initial_value_rupees") val initialValueRupees: Double,
    @SerializedName("remaining_value_cents") val remainingValueCents: Int,
    @SerializedName("remaining_value_rupees") val remainingValueRupees: Double,
    @SerializedName("card_design") val cardDesign: String,
    @SerializedName("sender_name") val senderName: String? = null,
    @SerializedName("recipient_name") val recipientName: String? = null,
    @SerializedName("recipient_email") val recipientEmail: String? = null,
    @SerializedName("recipient_phone") val recipientPhone: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("status") val status: String,
    @SerializedName("is_purchased_by_me") val isPurchasedByMe: Boolean = true,
    @SerializedName("expires_at") val expiresAt: String,
    @SerializedName("created_at") val createdAt: String
)

data class GiftCardTxDto(
    @SerializedName("id") val id: String,
    @SerializedName("transaction_type") val transactionType: String,
    @SerializedName("amount_cents") val amountCents: Int,
    @SerializedName("amount_rupees") val amountRupees: Double,
    @SerializedName("balance_before_rupees") val balanceBeforeRupees: Double,
    @SerializedName("balance_after_rupees") val balanceAfterRupees: Double,
    @SerializedName("reference_id") val referenceId: String? = null,
    @SerializedName("notes") val notes: String? = null,
    @SerializedName("created_at") val createdAt: String
)

data class GiftCardDetailDto(
    @SerializedName("gift_card_id") val giftCardId: String,
    @SerializedName("masked_code") val maskedCode: String,
    @SerializedName("unmasked_code") val unmaskedCode: String? = null,
    @SerializedName("pin") val pin: String? = null,
    @SerializedName("card_design") val cardDesign: String,
    @SerializedName("initial_value_cents") val initialValueCents: Int,
    @SerializedName("initial_value_rupees") val initialValueRupees: Double,
    @SerializedName("remaining_value_cents") val remainingValueCents: Int,
    @SerializedName("remaining_value_rupees") val remainingValueRupees: Double,
    @SerializedName("status") val status: String,
    @SerializedName("sender_name") val senderName: String? = null,
    @SerializedName("recipient_name") val recipientName: String? = null,
    @SerializedName("recipient_email") val recipientEmail: String? = null,
    @SerializedName("recipient_phone") val recipientPhone: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("delivery_method") val deliveryMethod: String,
    @SerializedName("payment_status") val paymentStatus: String,
    @SerializedName("expires_at") val expiresAt: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("redeemed_at") val redeemedAt: String? = null,
    @SerializedName("transactions") val transactions: List<GiftCardTxDto> = emptyList()
)

