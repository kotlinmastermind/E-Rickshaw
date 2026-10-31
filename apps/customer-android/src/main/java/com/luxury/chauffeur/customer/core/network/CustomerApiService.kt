package com.luxury.chauffeur.customer.core.network

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

data class OtpRequestPayload(
    @SerializedName("phone_number") val phoneNumber: String
)

data class OtpVerifyPayload(
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("otp_code") val otpCode: String,
    @SerializedName("role") val role: String = "customer"
)

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("user") val user: UserDto
)

data class UserDto(
    @SerializedName("id") val id: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("full_name") val fullName: String?,
    @SerializedName("email") val email: String?,
    @SerializedName("role") val role: String,
    @SerializedName("email_verified") val emailVerified: Boolean = false,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    @SerializedName("biometric_enabled") val biometricEnabled: Boolean = false,
    @SerializedName("mfa_enabled") val mfaEnabled: Boolean = false,
    @SerializedName("has_pin") val hasPin: Boolean = false,
    @SerializedName("has_passkey") val hasPasskey: Boolean = false
)

data class PasswordRegisterPayload(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("full_name") val fullName: String? = null,
    @SerializedName("phone_number") val phoneNumber: String? = null
)

data class PasswordLoginPayload(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

data class PasswordLoginResponse(
    @SerializedName("access_token") val accessToken: String?,
    @SerializedName("token_type") val tokenType: String?,
    @SerializedName("user") val user: UserDto?,
    @SerializedName("mfa_required") val mfaRequired: Boolean = false,
    @SerializedName("mfa_challenge_token") val mfaChallengeToken: String? = null
)

data class GoogleOAuthPayload(
    @SerializedName("id_token") val idToken: String,
    @SerializedName("email") val email: String? = null,
    @SerializedName("full_name") val fullName: String? = null
)

data class AppleOAuthPayload(
    @SerializedName("identity_token") val identityToken: String,
    @SerializedName("apple_user_id") val appleUserId: String,
    @SerializedName("email") val email: String? = null,
    @SerializedName("full_name") val fullName: String? = null
)

data class PasskeyChallengeResponse(
    @SerializedName("challenge") val challenge: String,
    @SerializedName("rp_id") val rpId: String,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("user_name") val userName: String? = null
)

data class PasskeyLoginVerifyPayload(
    @SerializedName("email") val email: String,
    @SerializedName("credential_id") val credentialId: String,
    @SerializedName("client_data_json") val clientDataJson: String = "",
    @SerializedName("authenticator_data") val authenticatorData: String = "",
    @SerializedName("signature") val signature: String = ""
)

data class MfaChallengeVerifyPayload(
    @SerializedName("challenge_token") val challengeToken: String,
    @SerializedName("totp_code") val totpCode: String
)

data class EmailVerifyPayload(
    @SerializedName("code") val code: String
)

data class AccountRecoveryRequestPayload(
    @SerializedName("email") val email: String
)

data class AccountRecoveryResetPayload(
    @SerializedName("recovery_token") val recoveryToken: String,
    @SerializedName("new_password") val newPassword: String
)

data class BiometricTogglePayload(
    @SerializedName("enabled") val enabled: Boolean
)

data class AppLockPinPayload(
    @SerializedName("pin") val pin: String
)

data class AppLockVerifyResponse(
    @SerializedName("valid") val valid: Boolean
)

data class PasskeyRegisterPayload(
    @SerializedName("credential_id") val credentialId: String,
    @SerializedName("public_key") val publicKey: String
)

data class MfaSetupResponse(
    @SerializedName("mfa_secret") val mfaSecret: String,
    @SerializedName("otpauth_url") val otpauthUrl: String
)

data class MfaEnablePayload(
    @SerializedName("totp_code") val code: String
)

data class TrustedDeviceDto(
    @SerializedName("id") val id: String,
    @SerializedName("device_fingerprint") val deviceFingerprint: String,
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("device_type") val deviceType: String,
    @SerializedName("operating_system") val operatingSystem: String,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("last_active_at") val lastActiveAt: String?
)

data class AddTrustedDevicePayload(
    @SerializedName("device_fingerprint") val deviceFingerprint: String,
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("device_type") val deviceType: String = "mobile",
    @SerializedName("operating_system") val operatingSystem: String = "Android"
)

data class SessionDto(
    @SerializedName("id") val id: String,
    @SerializedName("ip_address") val ipAddress: String,
    @SerializedName("user_agent") val userAgent: String,
    @SerializedName("is_current") val isCurrent: Boolean,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("expires_at") val expiresAt: String?
)

data class SavedPhoneDto(
    @SerializedName("id") val id: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("label") val label: String,
    @SerializedName("is_primary") val isPrimary: Boolean,
    @SerializedName("created_at") val createdAt: String?
)

data class AddSavedPhonePayload(
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("label") val label: String,
    @SerializedName("is_primary") val isPrimary: Boolean = false
)

data class UserPreferencesDto(
    @SerializedName("preferred_language") val preferredLanguage: String = "en",
    @SerializedName("currency_code") val currencyCode: String = "INR",
    @SerializedName("number_format") val numberFormat: String = "en_IN",
    @SerializedName("text_scale") val textScale: String = "standard",
    @SerializedName("high_contrast") val highContrast: Boolean = false,
    @SerializedName("reduce_motion") val reduceMotion: Boolean = false,
    @SerializedName("screen_reader_labels") val screenReaderLabels: Boolean = true,
    @SerializedName("analytics_consent") val analyticsConsent: Boolean = true,
    @SerializedName("crash_reports_consent") val crashReportsConsent: Boolean = true,
    @SerializedName("location_sharing_consent") val locationSharingConsent: Boolean = true,
    @SerializedName("third_party_sharing_consent") val thirdPartySharingConsent: Boolean = false,
    @SerializedName("marketing_push") val marketingPush: Boolean = true,
    @SerializedName("marketing_email") val marketingEmail: Boolean = true,
    @SerializedName("marketing_sms") val marketingSms: Boolean = false
)

data class CustomerMembershipDto(
    @SerializedName("user_id") val userId: String,
    @SerializedName("full_name") val fullName: String?,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("email") val email: String?,
    @SerializedName("avatar_url") val avatarUrl: String?,
    @SerializedName("tier") val tier: String,
    @SerializedName("rating_average") val ratingAverage: Double,
    @SerializedName("rating_count") val ratingCount: Int,
    @SerializedName("preferred_payment_method") val preferredPaymentMethod: String,
    @SerializedName("emergency_contact_phone") val emergencyContactPhone: String?,
    @SerializedName("temperature_celsius") val temperatureCelsius: Double,
    @SerializedName("quiet_ride") val quietRide: Boolean,
    @SerializedName("preferred_music") val preferredMusic: String?,
    @SerializedName("preferred_water") val preferredWater: String?,
    @SerializedName("luggage_assistance") val luggageAssistance: Boolean,
    @SerializedName("email_verified") val emailVerified: Boolean,
    @SerializedName("biometric_enabled") val biometricEnabled: Boolean,
    @SerializedName("app_lock_enabled") val appLockEnabled: Boolean,
    @SerializedName("mfa_enabled") val mfaEnabled: Boolean,
    @SerializedName("passkey_registered") val passkeyRegistered: Boolean
)

data class CustomerMembershipUpdatePayload(
    @SerializedName("full_name") val fullName: String? = null,
    @SerializedName("emergency_contact_phone") val emergencyContactPhone: String? = null,
    @SerializedName("preferred_payment_method") val preferredPaymentMethod: String? = null,
    @SerializedName("tier") val tier: String? = null,
    @SerializedName("temperature_celsius") val temperatureCelsius: Double? = null,
    @SerializedName("quiet_ride") val quietRide: Boolean? = null,
    @SerializedName("preferred_music") val preferredMusic: String? = null,
    @SerializedName("preferred_water") val preferredWater: String? = null,
    @SerializedName("luggage_assistance") val luggageAssistance: Boolean? = null
)

data class DataExportArchiveDto(
    @SerializedName("id") val id: String,
    @SerializedName("status") val status: String,
    @SerializedName("file_path") val filePath: String?,
    @SerializedName("file_size_bytes") val fileSizeBytes: Int?,
    @SerializedName("expires_at") val expiresAt: String?,
    @SerializedName("created_at") val createdAt: String?
)

data class AccountDeletionRequestDto(
    @SerializedName("id") val id: String,
    @SerializedName("status") val status: String,
    @SerializedName("reason") val reason: String?,
    @SerializedName("requested_at") val requestedAt: String?,
    @SerializedName("scheduled_purge_at") val scheduledPurgeAt: String?,
    @SerializedName("grace_period_days") val gracePeriodDays: Int,
    @SerializedName("can_cancel") val canCancel: Boolean
)

data class AccountDeletionRequestPayload(
    @SerializedName("reason") val reason: String = "Requested by user"
)

data class CustomerProfileDto(
    @SerializedName("user_id") val userId: String = "",
    @SerializedName("full_name") val fullName: String? = null,
    @SerializedName("phone_number") val phoneNumber: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    @SerializedName("is_verified") val isVerified: Boolean? = false,
    @SerializedName("role") val role: String? = "customer",
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("preferred_payment_method") val preferredPaymentMethod: String? = "CASH",
    @SerializedName("emergency_contact_phone") val emergencyContactPhone: String? = null,
    @SerializedName("rating_average") val ratingAverage: Double? = 5.0,
    @SerializedName("rating_count") val ratingCount: Int? = 0
)

data class ProfileUpdateRequest(
    @SerializedName("full_name") val fullName: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    @SerializedName("emergency_contact_phone") val emergencyContactPhone: String? = null,
    @SerializedName("preferred_payment_method") val preferredPaymentMethod: String? = null
)

data class VehicleCategoryDto(
    @SerializedName("id") val id: String = "cat_1",
    @SerializedName("code") val code: String = "SEDAN",
    @SerializedName("name") val name: String = "Executive Sedan",
    @SerializedName("description") val description: String = "Chauffeur Driven",
    @SerializedName("icon_url") val iconUrl: String = "",
    @SerializedName("capacity") val capacity: Int = 4,
    @SerializedName("legroom_inches") val legroomInches: Double? = null,
    @SerializedName("quietness_db") val quietnessDb: Double? = null,
    @SerializedName("luggage_large_capacity") val luggageLargeCapacity: Int? = null,
    @SerializedName("luggage_small_capacity") val luggageSmallCapacity: Int? = null,
    @SerializedName("has_wifi") val hasWifi: Boolean? = null,
    @SerializedName("has_massage_seats") val hasMassageSeats: Boolean? = null,
    @SerializedName("has_champagne_cooler") val hasChampagneCooler: Boolean? = null,
    @SerializedName("has_reclining_seats") val hasRecliningSeats: Boolean? = null,
    @SerializedName("is_electric") val isElectric: Boolean? = null,
    @SerializedName("emissions_g_per_km") val emissionsGPerKm: Double? = null,
    @SerializedName("wheelchair_accessible") val wheelchairAccessible: Boolean? = null,
    @SerializedName("step_free_height_cm") val stepFreeHeightCm: Double? = null,
    @SerializedName("pet_friendly") val petFriendly: Boolean? = null,
    @SerializedName("child_seat_available") val childSeatAvailable: Boolean? = null,
    @SerializedName("available_colors") val availableColors: List<String>? = null,
    @SerializedName("available_amenities") val availableAmenities: List<String>? = null,
    @SerializedName("photos_gallery") val photosGallery: List<String>? = null
)

// Part 3 Bespoke Customization & Preferences DTOs (Features 51-75)
data class CustomerPreferencesDto(
    @SerializedName("quiet_ride") val quietRide: Boolean = true,
    @SerializedName("temperature_celsius") val temperatureCelsius: Double = 21.0,
    @SerializedName("preferred_music") val preferredMusic: String? = "Classical Chamber",
    @SerializedName("preferred_water") val preferredWater: String? = "Chilled Sparkling",
    @SerializedName("no_phone_call_ride") val noPhoneCallRide: Boolean = false,
    @SerializedName("preferred_driver_language") val preferredDriverLanguage: String? = "English",
    @SerializedName("female_driver_preferred") val femaleDriverPreferred: Boolean = false,
    @SerializedName("vehicle_color_preference") val vehicleColorPreference: String? = "Obsidian Black",
    @SerializedName("premium_amenities") val premiumAmenities: String? = "Evian Mineral Water, Braided Charger",
    @SerializedName("electric_vehicle_preferred") val electricVehiclePreferred: Boolean = false,
    @SerializedName("low_emission_filter") val lowEmissionFilter: Boolean = false,
    @SerializedName("wheelchair_access_required") val wheelchairAccessRequired: Boolean = false,
    @SerializedName("step_free_pickup_required") val stepFreePickupRequired: Boolean = false,
    @SerializedName("assistance_animal_present") val assistanceAnimalPresent: Boolean = false,
    @SerializedName("extra_legroom_required") val extraLegroomRequired: Boolean = false,
    @SerializedName("pet_friendly_preferred") val petFriendlyPreferred: Boolean = false,
    @SerializedName("accessibility_notes") val accessibilityNotes: String? = null
)

data class CustomerPreferencesUpdatePayload(
    @SerializedName("quiet_ride") val quietRide: Boolean? = null,
    @SerializedName("temperature_celsius") val temperatureCelsius: Double? = null,
    @SerializedName("preferred_music") val preferredMusic: String? = null,
    @SerializedName("preferred_water") val preferredWater: String? = null,
    @SerializedName("no_phone_call_ride") val noPhoneCallRide: Boolean? = null,
    @SerializedName("preferred_driver_language") val preferredDriverLanguage: String? = null,
    @SerializedName("female_driver_preferred") val femaleDriverPreferred: Boolean? = null,
    @SerializedName("vehicle_color_preference") val vehicleColorPreference: String? = null,
    @SerializedName("premium_amenities") val premiumAmenities: String? = null,
    @SerializedName("electric_vehicle_preferred") val electricVehiclePreferred: Boolean? = null,
    @SerializedName("low_emission_filter") val lowEmissionFilter: Boolean? = null,
    @SerializedName("wheelchair_access_required") val wheelchairAccessRequired: Boolean? = null,
    @SerializedName("step_free_pickup_required") val stepFreePickupRequired: Boolean? = null,
    @SerializedName("assistance_animal_present") val assistanceAnimalPresent: Boolean? = null,
    @SerializedName("extra_legroom_required") val extraLegroomRequired: Boolean? = null,
    @SerializedName("pet_friendly_preferred") val petFriendlyPreferred: Boolean? = null,
    @SerializedName("accessibility_notes") val accessibilityNotes: String? = null
)

data class PrebookingPreferenceSummaryDto(
    @SerializedName("passenger_name") val passengerName: String,
    @SerializedName("cabin_atmosphere") val cabinAtmosphere: List<String>,
    @SerializedName("service_etiquette") val serviceEtiquette: List<String>,
    @SerializedName("accessibility_compliance") val accessibilityCompliance: List<String>,
    @SerializedName("amenities_and_fleet") val amenitiesAndFleet: List<String>,
    @SerializedName("ready_for_dispatch") val readyForDispatch: Boolean
)

data class VehicleComparisonMatrixDto(
    @SerializedName("categories") val categories: List<VehicleComparisonCategoryDto>,
    @SerializedName("dimensions") val dimensions: List<String>
)

data class VehicleComparisonCategoryDto(
    @SerializedName("code") val code: String,
    @SerializedName("name") val name: String,
    @SerializedName("capacity") val capacity: Int,
    @SerializedName("luggage") val luggage: String,
    @SerializedName("legroom_inches") val legroomInches: Double,
    @SerializedName("quietness_db") val quietnessDb: Double,
    @SerializedName("is_electric") val isElectric: Boolean,
    @SerializedName("emissions_g_per_km") val emissionsGPerKm: Double,
    @SerializedName("wheelchair_accessible") val wheelchairAccessible: Boolean,
    @SerializedName("signature_amenities") val signatureAmenities: List<String>,
    @SerializedName("icon_url") val iconUrl: String
)

data class VehicleGalleryDto(
    @SerializedName("category_code") val categoryCode: String,
    @SerializedName("category_name") val categoryName: String,
    @SerializedName("description") val description: String,
    @SerializedName("gallery_images") val galleryImages: List<GalleryPhotoItemDto>
)

data class GalleryPhotoItemDto(
    @SerializedName("angle") val angle: String,
    @SerializedName("label") val label: String,
    @SerializedName("url") val url: String
)

data class VehicleAccessibilityDetailsDto(
    @SerializedName("category_code") val categoryCode: String,
    @SerializedName("category_name") val categoryName: String,
    @SerializedName("accessibility_specs") val accessibilitySpecs: Map<String, Any>,
    @SerializedName("assistance_guidelines") val assistanceGuidelines: List<String>
)

data class FareEstimateRequest(
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("pickup_latitude") val pickupLatitude: Double,
    @SerializedName("pickup_longitude") val pickupLongitude: Double,
    @SerializedName("destination_latitude") val destinationLatitude: Double,
    @SerializedName("destination_longitude") val destinationLongitude: Double
)

data class FareEstimateResponse(
    @SerializedName("estimate_id") val estimateId: String,
    @SerializedName("fare_rule_id") val fareRuleId: String,
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("distance_km") val distanceKm: Double,
    @SerializedName("estimated_duration_minutes") val estimatedDurationMinutes: Double,
    @SerializedName("estimated_fare_cents") val estimatedFareCents: Int,
    @SerializedName("estimated_fare_rupees") val estimatedFareRupees: Double? = null,
    @SerializedName("base_fare_rupees") val baseFareRupees: Double? = null,
    @SerializedName("distance_charge_rupees") val distanceChargeRupees: Double? = null,
    @SerializedName("time_charge_rupees") val timeChargeRupees: Double? = null,
    @SerializedName("tax_rupees") val taxRupees: Double? = null,
    @SerializedName("amenities_fee_rupees") val amenitiesFeeRupees: Double? = null,
    @SerializedName("fare_min_rupees") val fareMinRupees: Double? = null,
    @SerializedName("fare_max_rupees") val fareMaxRupees: Double? = null,
    @SerializedName("confidence_level") val confidenceLevel: String? = "HIGH",
    @SerializedName("confidence_percentage") val confidencePercentage: Int? = 95,
    @SerializedName("is_capped") val isCapped: Boolean? = false,
    @SerializedName("cap_discount_rupees") val capDiscountRupees: Double? = 0.0,
    @SerializedName("seconds_remaining") val secondsRemaining: Int? = 900,
    @SerializedName("currency") val currency: String,
    @SerializedName("expires_at") val expiresAt: String,
    @SerializedName("route_polyline") val routePolyline: String? = null
)

data class RideRequestPayload(
    @SerializedName("estimate_id") val estimateId: String,
    @SerializedName("pickup_address") val pickupAddress: String,
    @SerializedName("destination_address") val destinationAddress: String,
    @SerializedName("payment_method") val paymentMethod: String
)

data class IntermediateStopInputDto(
    @SerializedName("address") val address: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("dwell_time_minutes") val dwellTimeMinutes: Int = 0,
    @SerializedName("passenger_instructions") val passengerInstructions: String? = null,
    @SerializedName("entrance_landmark") val entranceLandmark: String? = null
)

data class AdvancedRideRequestPayload(
    @SerializedName("estimate_id") val estimateId: String,
    @SerializedName("pickup_address") val pickupAddress: String,
    @SerializedName("destination_address") val destinationAddress: String,
    @SerializedName("payment_method") val paymentMethod: String = "UPI",
    @SerializedName("is_scheduled") val isScheduled: Boolean = false,
    @SerializedName("scheduled_pickup_time") val scheduledPickupTime: String? = null,
    @SerializedName("promo_code") val promoCode: String? = null,
    @SerializedName("booking_for_self") val bookingForSelf: Boolean = true,
    @SerializedName("passenger_name") val passengerName: String? = null,
    @SerializedName("passenger_phone") val passengerPhone: String? = null,
    @SerializedName("passenger_relationship") val passengerRelationship: String? = null,
    @SerializedName("is_return_trip") val isReturnTrip: Boolean = false,
    @SerializedName("return_pickup_offset_hours") val returnPickupOffsetHours: Int? = null,
    @SerializedName("return_scheduled_time") val returnScheduledTime: String? = null,
    @SerializedName("is_round_trip") val isRoundTrip: Boolean = false,
    @SerializedName("pickup_notes") val pickupNotes: String? = null,
    @SerializedName("destination_notes") val destinationNotes: String? = null,
    @SerializedName("entrance_guidance") val entranceGuidance: String? = null,
    @SerializedName("landmark_guidance") val landmarkGuidance: String? = null,
    @SerializedName("airport_code") val airportCode: String? = null,
    @SerializedName("terminal_name") val terminalName: String? = null,
    @SerializedName("airline_code") val airlineCode: String? = null,
    @SerializedName("flight_number") val flightNumber: String? = null,
    @SerializedName("booking_mode") val bookingMode: String = "STANDARD",
    @SerializedName("hotel_room_number") val hotelRoomNumber: String? = null,
    @SerializedName("hotel_guest_name") val hotelGuestName: String? = null,
    @SerializedName("business_cost_center") val businessCostCenter: String? = null,
    @SerializedName("business_project_code") val businessProjectCode: String? = null,
    @SerializedName("business_tax_id") val businessTaxId: String? = null,
    @SerializedName("dependent_id") val dependentId: String? = null,
    @SerializedName("shared_location_source") val sharedLocationSource: String? = null,
    @SerializedName("stops") val stops: List<IntermediateStopInputDto>? = null,
    // Part 3 Bespoke Customization Fields (Features 53-75)
    @SerializedName("child_seat_requested") val childSeatRequested: Boolean = false,
    @SerializedName("child_seat_type") val childSeatType: String? = null,
    @SerializedName("luggage_large_count") val luggageLargeCount: Int = 0,
    @SerializedName("luggage_small_count") val luggageSmallCount: Int = 0,
    @SerializedName("wheelchair_requested") val wheelchairRequested: Boolean = false,
    @SerializedName("step_free_pickup") val stepFreePickup: Boolean = false,
    @SerializedName("assistance_animal") val assistanceAnimal: Boolean = false,
    @SerializedName("extra_legroom_requested") val extraLegroomRequested: Boolean = false,
    @SerializedName("quiet_ride_requested") val quietRideRequested: Boolean = true,
    @SerializedName("cabin_temp_requested") val cabinTempRequested: Double? = null,
    @SerializedName("music_requested") val musicRequested: String? = null,
    @SerializedName("no_phone_call") val noPhoneCall: Boolean = false,
    @SerializedName("driver_language_requested") val driverLanguageRequested: String? = null,
    @SerializedName("female_driver_requested") val femaleDriverRequested: Boolean = false,
    @SerializedName("vehicle_color_requested") val vehicleColorRequested: String? = null,
    @SerializedName("amenities_requested") val amenitiesRequested: String? = null,
    @SerializedName("ev_ride_requested") val evRideRequested: Boolean = false,
    @SerializedName("pet_friendly_requested") val petFriendlyRequested: Boolean = false,
    @SerializedName("special_assistance_notes") val specialAssistanceNotes: String? = null
)

data class RecommendPickupTimeRequest(
    @SerializedName("pickup_latitude") val pickupLatitude: Double,
    @SerializedName("pickup_longitude") val pickupLongitude: Double,
    @SerializedName("destination_latitude") val destinationLatitude: Double,
    @SerializedName("destination_longitude") val destinationLongitude: Double,
    @SerializedName("target_arrival_time") val targetArrivalTime: String
)

data class RecommendPickupTimeResponse(
    @SerializedName("distance_km") val distanceKm: Double,
    @SerializedName("target_arrival_time") val targetArrivalTime: String,
    @SerializedName("recommended_departure_time") val recommendedDepartureTime: String,
    @SerializedName("traffic_condition") val trafficCondition: String,
    @SerializedName("traffic_note") val trafficNote: String,
    @SerializedName("estimated_transit_minutes") val estimatedTransitMinutes: Int,
    @SerializedName("luxury_buffer_minutes") val luxuryBufferMinutes: Int,
    @SerializedName("total_recommended_lead_minutes") val totalRecommendedLeadMinutes: Int,
    @SerializedName("countdown_minutes") val countdownMinutes: Int
)

data class FlightStatusResponse(
    @SerializedName("flight_number") val flightNumber: String,
    @SerializedName("airline_name") val airlineName: String,
    @SerializedName("status") val status: String,
    @SerializedName("delay_minutes") val delayMinutes: Int,
    @SerializedName("scheduled_arrival") val scheduledArrival: String,
    @SerializedName("estimated_arrival") val estimatedArrival: String,
    @SerializedName("terminal") val terminal: String,
    @SerializedName("baggage_belt") val baggageBelt: String,
    @SerializedName("delay_mitigated") val delayMitigated: Boolean
)

data class SharedLocationParseRequest(
    @SerializedName("raw_link") val rawLink: String
)

data class SharedLocationParseResponse(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("label") val label: String,
    @SerializedName("source") val source: String
)

data class RecentDestinationDto(
    @SerializedName("address") val address: String? = "Curated Landmark",
    @SerializedName("latitude") val latitude: Double = 19.0760,
    @SerializedName("longitude") val longitude: Double = 72.8777,
    @SerializedName("visit_count") val visitCount: Int = 1,
    @SerializedName("last_visited") val lastVisited: String? = null
)

data class RecurringScheduleDto(
    @SerializedName("id") val id: String,
    @SerializedName("frequency") val frequency: String,
    @SerializedName("days_of_week") val daysOfWeek: String,
    @SerializedName("pickup_time_str") val pickupTimeStr: String,
    @SerializedName("pickup_address") val pickupAddress: String,
    @SerializedName("destination_address") val destinationAddress: String,
    @SerializedName("vehicle_category_id") val vehicleCategoryId: String,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("created_at") val createdAt: String
)

data class CreateRecurringScheduleRequest(
    @SerializedName("frequency") val frequency: String,
    @SerializedName("days_of_week") val daysOfWeek: String,
    @SerializedName("pickup_time_str") val pickupTimeStr: String,
    @SerializedName("pickup_address") val pickupAddress: String,
    @SerializedName("pickup_latitude") val pickupLatitude: Double,
    @SerializedName("pickup_longitude") val pickupLongitude: Double,
    @SerializedName("destination_address") val destinationAddress: String,
    @SerializedName("destination_latitude") val destinationLatitude: Double,
    @SerializedName("destination_longitude") val destinationLongitude: Double,
    @SerializedName("vehicle_category_id") val vehicleCategoryId: String
)

data class TripTemplateDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("pickup_address") val pickupAddress: String,
    @SerializedName("pickup_latitude") val pickupLatitude: Double,
    @SerializedName("pickup_longitude") val pickupLongitude: Double,
    @SerializedName("destination_address") val destinationAddress: String,
    @SerializedName("destination_latitude") val destinationLatitude: Double,
    @SerializedName("destination_longitude") val destinationLongitude: Double,
    @SerializedName("notes") val notes: String?,
    @SerializedName("cabin_temperature") val cabinTemperature: Double?,
    @SerializedName("created_at") val createdAt: String
)

data class CreateTripTemplateRequest(
    @SerializedName("title") val title: String,
    @SerializedName("pickup_address") val pickupAddress: String,
    @SerializedName("pickup_latitude") val pickupLatitude: Double,
    @SerializedName("pickup_longitude") val pickupLongitude: Double,
    @SerializedName("destination_address") val destinationAddress: String,
    @SerializedName("destination_latitude") val destinationLatitude: Double,
    @SerializedName("destination_longitude") val destinationLongitude: Double,
    @SerializedName("intermediate_stops_json") val intermediateStopsJson: String? = null,
    @SerializedName("notes") val notes: String? = null,
    @SerializedName("cabin_temperature") val cabinTemperature: Double? = 21.0
)

data class UserDependentDto(
    @SerializedName("id") val id: String,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("relationship") val relationship: String,
    @SerializedName("emergency_notes") val emergencyNotes: String?,
    @SerializedName("created_at") val createdAt: String
)

data class CreateDependentRequest(
    @SerializedName("full_name") val fullName: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("relationship") val relationship: String = "Family",
    @SerializedName("emergency_notes") val emergencyNotes: String? = null
)

data class AddStopRequest(
    @SerializedName("address") val address: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("sequence_order") val sequenceOrder: Int = 1,
    @SerializedName("dwell_time_minutes") val dwellTimeMinutes: Int = 0,
    @SerializedName("passenger_instructions") val passengerInstructions: String? = null,
    @SerializedName("entrance_landmark") val entranceLandmark: String? = null
)

data class UpdateStopRequest(
    @SerializedName("address") val address: String? = null,
    @SerializedName("dwell_time_minutes") val dwellTimeMinutes: Int? = null,
    @SerializedName("passenger_instructions") val passengerInstructions: String? = null,
    @SerializedName("entrance_landmark") val entranceLandmark: String? = null
)

data class RideStopDto(
    @SerializedName("id") val id: String,
    @SerializedName("address") val address: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("sequence_order") val sequenceOrder: Int = 1,
    @SerializedName("dwell_time_minutes") val dwellTimeMinutes: Int = 0,
    @SerializedName("passenger_instructions") val passengerInstructions: String? = null,
    @SerializedName("entrance_landmark") val entranceLandmark: String? = null,
    @SerializedName("status") val status: String? = null
)

data class RideResponseDto(
    @SerializedName("id") val id: String,
    @SerializedName("status") val status: String,
    @SerializedName("pickup_address") val pickupAddress: String,
    @SerializedName("pickup_latitude") val pickupLatitude: Double,
    @SerializedName("pickup_longitude") val pickupLongitude: Double,
    @SerializedName("destination_address") val destinationAddress: String,
    @SerializedName("destination_latitude") val destinationLatitude: Double,
    @SerializedName("destination_longitude") val destinationLongitude: Double,
    @SerializedName("estimated_fare_cents") val estimatedFareCents: Int,
    @SerializedName("final_fare_cents") val finalFareCents: Int?,
    @SerializedName("payment_method") val paymentMethod: String,
    @SerializedName("start_otp") val startOtp: String?,
    @SerializedName("driver") val driver: AssignedDriverDto?,
    @SerializedName("booking_for_self") val bookingForSelf: Boolean? = true,
    @SerializedName("passenger_name") val passengerName: String? = null,
    @SerializedName("passenger_phone") val passengerPhone: String? = null,
    @SerializedName("passenger_relationship") val passengerRelationship: String? = null,
    @SerializedName("pickup_notes") val pickupNotes: String? = null,
    @SerializedName("destination_notes") val destinationNotes: String? = null,
    @SerializedName("entrance_guidance") val entranceGuidance: String? = null,
    @SerializedName("landmark_guidance") val landmarkGuidance: String? = null,
    @SerializedName("airport_code") val airportCode: String? = null,
    @SerializedName("terminal_name") val terminalName: String? = null,
    @SerializedName("airline_code") val airlineCode: String? = null,
    @SerializedName("flight_number") val flightNumber: String? = null,
    @SerializedName("flight_delay_minutes") val flightDelayMinutes: Int? = 0,
    @SerializedName("flight_status") val flightStatus: String? = null,
    @SerializedName("is_return_trip") val isReturnTrip: Boolean? = false,
    @SerializedName("is_round_trip") val isRoundTrip: Boolean? = false,
    @SerializedName("booking_mode") val bookingMode: String? = "STANDARD",
    @SerializedName("hotel_room_number") val hotelRoomNumber: String? = null,
    @SerializedName("hotel_guest_name") val hotelGuestName: String? = null,
    @SerializedName("business_cost_center") val businessCostCenter: String? = null,
    @SerializedName("business_project_code") val businessProjectCode: String? = null,
    @SerializedName("business_tax_id") val businessTaxId: String? = null,
    @SerializedName("stops") val stops: List<RideStopDto>? = emptyList()
)

data class AssignedDriverDto(
    @SerializedName("driver_id") val driverId: String,
    @SerializedName("name") val name: String,
    @SerializedName("phone") val phone: String,
    @SerializedName("license_number") val licenseNumber: String,
    @SerializedName("rating") val rating: Double,
    @SerializedName("total_trips") val totalTrips: Int
)

data class TrackingDto(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("status") val status: String,
    @SerializedName("driver_latitude") val driverLatitude: Double,
    @SerializedName("driver_longitude") val driverLongitude: Double,
    @SerializedName("heading") val heading: Double
)

data class PaymentIntentRequest(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("idempotency_key") val idempotencyKey: String
)

data class PaymentIntentResponse(
    @SerializedName("payment_id") val paymentId: String,
    @SerializedName("order_id") val orderId: String,
    @SerializedName("amount_cents") val amountCents: Int,
    @SerializedName("currency") val currency: String,
    @SerializedName("client_token") val clientToken: String,
    @SerializedName("provider") val provider: String
)

data class RateDriverRequest(
    @SerializedName("rating") val rating: Int,
    @SerializedName("feedback") val feedback: String?
)

data class NotificationDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("body") val body: String,
    @SerializedName("event_type") val eventType: String,
    @SerializedName("is_read") val isRead: Boolean,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("delivery_status") val deliveryStatus: String? = null,
    @SerializedName("action_url") val actionUrl: String? = null
)

data class NotificationInboxDto(
    val items: List<NotificationDto> = emptyList(),
    @SerializedName("unread_count") val unreadCount: Int = 0,
    val total: Int = 0
)
data class CustomerDeviceTokenPayload(@SerializedName("fcm_token") val fcmToken: String, @SerializedName("device_os") val deviceOs: String = "ANDROID")

data class SharedEstimateRequest(
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("pickup_latitude") val pickupLatitude: Double,
    @SerializedName("pickup_longitude") val pickupLongitude: Double,
    @SerializedName("destination_latitude") val destinationLatitude: Double,
    @SerializedName("destination_longitude") val destinationLongitude: Double,
    @SerializedName("seats_requested") val seatsRequested: Int = 1
)

data class SharedEstimateResponse(
    @SerializedName("estimate_id") val estimateId: String,
    @SerializedName("solo_fare_cents") val soloFareCents: Int,
    @SerializedName("shared_discount_percentage") val sharedDiscountPercentage: Double,
    @SerializedName("shared_fare_cents") val sharedFareCents: Int,
    @SerializedName("savings_cents") val guestSavingsCents: Int,
    @SerializedName("currency") val currency: String,
    @SerializedName("seats_requested") val seatsRequested: Int,
    @SerializedName("total_payable_cents") val totalPayableCents: Int,
    @SerializedName("distance_km") val distanceKm: Double,
    @SerializedName("estimated_duration_minutes") val estimatedDurationMinutes: Double,
    @SerializedName("reservation_expires_in_seconds") val reservationExpiresInSeconds: Int,
    @SerializedName("expires_at") val expiresAt: String
)

data class SharedMatchRequest(
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("pickup_latitude") val pickupLatitude: Double,
    @SerializedName("pickup_longitude") val pickupLongitude: Double,
    @SerializedName("destination_latitude") val destinationLatitude: Double,
    @SerializedName("destination_longitude") val destinationLongitude: Double,
    @SerializedName("seats_requested") val seatsRequested: Int,
    @SerializedName("max_detour_km") val maxDetourKm: Double
)

data class SharedRidePreferenceRequest(@SerializedName("maximum_detour_km") val maximumDetourKm: Double)

data class BookSharedRideRequest(
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("estimate_id") val estimateId: String,
    @SerializedName("pickup_address") val pickupAddress: String,
    @SerializedName("pickup_latitude") val pickupLatitude: Double,
    @SerializedName("pickup_longitude") val pickupLongitude: Double,
    @SerializedName("destination_address") val destinationAddress: String,
    @SerializedName("destination_latitude") val destinationLatitude: Double,
    @SerializedName("destination_longitude") val destinationLongitude: Double,
    @SerializedName("seats_reserved") val seatsReserved: Int = 1,
    @SerializedName("target_pooled_trip_id") val targetPooledTripId: String? = null,
    @SerializedName("fallback_policy") val fallbackPolicy: String = "FAIL",
    @SerializedName("share_stop_details_with_co_passengers") val shareStopDetailsWithCoPassengers: Boolean = false,
    @SerializedName("maximum_detour_km") val maximumDetourKm: Double = 5.0
)

data class WalletDto(
    @SerializedName("user_id") val userId: String,
    @SerializedName("balance_cents") val balanceCents: Int,
    @SerializedName("currency") val currency: String,
    @SerializedName("is_frozen") val isFrozen: Boolean
)

data class TopupWalletRequest(
    @SerializedName("amount_cents") val amountCents: Int,
    @SerializedName("idempotency_key") val idempotencyKey: String,
    @SerializedName("payment_reference") val paymentReference: String? = null
)

data class PromoValidateRequest(
    @SerializedName("code") val code: String,
    @SerializedName("fare_cents") val fareCents: Int
)

data class PromoValidateResponse(
    @SerializedName("valid") val valid: Boolean,
    @SerializedName("code") val code: String,
    @SerializedName("discount_cents") val discountCents: Int,
    @SerializedName("final_fare_cents") val finalFareCents: Int
)

data class FirebaseVerifyPayload(
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("role") val role: String = "customer",
    @SerializedName("firebase_uid") val firebaseUid: String? = null,
    @SerializedName("id_token") val idToken: String? = null
)

data class NearbyVehicleDto(
    @SerializedName("id") val id: String,
    @SerializedName("driver_name") val driverName: String,
    @SerializedName("driver_phone") val driverPhone: String? = null,
    @SerializedName("rating") val rating: Double = 4.8,
    @SerializedName("category_code") val categoryCode: String,
    @SerializedName("vehicle_type_name") val vehicleTypeName: String,
    @SerializedName("model_name") val modelName: String,
    @SerializedName("license_plate") val licensePlate: String,
    @SerializedName("icon_type") val iconType: String = "e_rickshaw",
    @SerializedName("capacity") val capacity: Int = 4,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("bearing") val bearing: Float = 0f,
    @SerializedName("battery_level") val batteryLevel: Int = 85,
    @SerializedName("distance_km") val distanceKm: Double = 0.5,
    @SerializedName("eta_minutes") val etaMinutes: Int = 3,
    @SerializedName("status") val status: String = "AVAILABLE"
)

data class NearbyVehiclesResponse(
    @SerializedName("customer_latitude") val customerLatitude: Double,
    @SerializedName("customer_longitude") val customerLongitude: Double,
    @SerializedName("radius_km") val radiusKm: Double,
    @SerializedName("total_available") val totalAvailable: Int,
    @SerializedName("vehicles") val vehicles: List<NearbyVehicleDto>
)

data class RoutePointDto(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double
)

data class RouteDetailsDto(
    @SerializedName("distance_km") val distanceKm: Double,
    @SerializedName("duration_minutes") val durationMinutes: Double,
    @SerializedName("polyline") val polyline: String? = null,
    @SerializedName("provider") val provider: String? = null,
    @SerializedName("points") val points: List<RoutePointDto>? = null,
    @SerializedName("route_label") val routeLabel: String? = null
)

data class IdentityLookupRequest(
    @SerializedName("identifier") val identifier: String,
    @SerializedName("role") val role: String = "customer"
)

data class IdentityLookupResponse(
    @SerializedName("exists") val exists: Boolean,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("role") val role: String? = null,
    @SerializedName("full_name") val fullName: String? = null,
    @SerializedName("phone_number") val phoneNumber: String? = null,
    @SerializedName("phone_masked") val phoneMasked: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("email_masked") val emailMasked: String? = null,
    @SerializedName("is_active") val isActive: Boolean = true
)

interface CustomerApiService {
    @POST("api/v1/auth/lookup")
    suspend fun lookupIdentity(@Body payload: IdentityLookupRequest): Response<IdentityLookupResponse>

    @POST("api/v1/auth/otp/request")
    suspend fun requestOtp(@Body payload: OtpRequestPayload): Response<Map<String, Any>>

    @POST("api/v1/auth/otp/verify")
    suspend fun verifyOtp(@Body payload: OtpVerifyPayload): Response<TokenResponse>

    @POST("api/v1/auth/firebase/verify")
    suspend fun verifyFirebaseAuth(@Body payload: FirebaseVerifyPayload): Response<TokenResponse>

    @GET("api/v1/customers/profile")
    suspend fun getProfile(): Response<CustomerProfileDto>

    @PUT("api/v1/customers/profile")
    suspend fun updateProfile(@Body payload: ProfileUpdateRequest): Response<Map<String, Any>>

    @GET("api/v1/vehicles/categories")
    suspend fun getVehicleCategories(
        @Query("passengers") passengers: Int? = null,
        @Query("luggage_large") luggageLarge: Int? = null,
        @Query("wheelchair_accessible") wheelchairAccessible: Boolean? = null,
        @Query("electric_only") electricOnly: Boolean? = null,
        @Query("low_emissions_only") lowEmissionsOnly: Boolean? = null,
        @Query("pet_friendly") petFriendly: Boolean? = null
    ): Response<List<VehicleCategoryDto>>

    @GET("api/v1/vehicles/nearby")
    suspend fun getNearbyVehicles(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("radius_km") radiusKm: Double = 5.0,
        @Query("category") category: String? = null
    ): Response<NearbyVehiclesResponse>

    @GET("api/v1/location/route")
    suspend fun getRouteDetails(
        @Query("origin_lat") originLat: Double,
        @Query("origin_lng") originLng: Double,
        @Query("dest_lat") destLat: Double,
        @Query("dest_lng") destLng: Double
    ): Response<RouteDetailsDto>

    @GET("api/v1/tourism/places")
    suspend fun getTourismPlaces(
        @Query("category") category: String? = null,
        @Query("current_lat") currentLat: Double? = null,
        @Query("current_lng") currentLng: Double? = null,
        @Query("week_offset") weekOffset: Int = 0
    ): Response<TourismWeeklyResponse>


    @GET("api/v1/vehicles/categories/{code}/gallery")
    suspend fun getCategoryGallery(@Path("code") code: String): Response<VehicleGalleryDto>

    @GET("api/v1/vehicles/compare")
    suspend fun compareVehicleCategories(): Response<VehicleComparisonMatrixDto>

    @GET("api/v1/vehicles/categories/{code}/accessibility")
    suspend fun getCategoryAccessibility(@Path("code") code: String): Response<VehicleAccessibilityDetailsDto>

    @GET("api/v1/customers/preferences")
    suspend fun getCustomerPreferences(): Response<CustomerPreferencesDto>

    @PUT("api/v1/customers/preferences")
    suspend fun updateCustomerPreferences(@Body payload: CustomerPreferencesUpdatePayload): Response<Map<String, Any>>

    @GET("api/v1/customers/preferences/summary")
    suspend fun getPrebookingPreferenceSummary(): Response<PrebookingPreferenceSummaryDto>

    @GET("api/v1/location/autocomplete")
    suspend fun autocompletePlaces(@Query("q") query: String): Response<Map<String, Any>>

    @GET("api/v1/location/provider-health")
    suspend fun getMapProviderHealth(): Response<Map<String, Any>>

    @POST("api/v1/location/place-details")
    suspend fun verifyPlaceDetails(@Body payload: Map<String, String>): Response<Map<String, Any>>

    @POST("api/v1/location/snap-to-road")
    suspend fun snapToRoad(@Body payload: Map<String, Double>): Response<Map<String, Any>>

    @POST("api/v1/location/isochrone")
    suspend fun getIsochrone(@Body payload: IsochroneRequest): Response<IsochroneResponse>

    @POST("api/v1/location/validate-address")
    suspend fun validateAddress(@Body payload: AddressValidationRequest): Response<AddressValidationResponse>

    @GET("api/v1/location/navigation-guidance")
    suspend fun getNavigationGuidance(
        @Query("origin_lat") originLat: Double,
        @Query("origin_lng") originLng: Double,
        @Query("dest_lat") destLat: Double,
        @Query("dest_lng") destLng: Double
    ): Response<NavigationGuidanceResponse>

    @POST("api/v1/location/eligibility")
    suspend fun checkLocationEligibility(@Body payload: Map<String, Double>): Response<Map<String, Any>>

    @GET("api/v1/location/saved-pins")
    suspend fun getSavedLocationPins(): Response<List<Map<String, Any>>>

    @POST("api/v1/location/saved-pins")
    suspend fun saveLocationPin(@Body payload: Map<String, Any>): Response<Map<String, Any>>

    @DELETE("api/v1/location/saved-pins/{pinId}")
    suspend fun deleteLocationPin(@Path("pinId") pinId: String): Response<Map<String, Any>>

    @POST("api/v1/location/address-quality-feedback")
    suspend fun submitAddressQualityFeedback(@Body payload: Map<String, Any>): Response<Map<String, Any>>

    @GET("api/v1/location/route-alternatives")
    suspend fun getRouteAlternatives(@Query("origin_lat") originLat: Double, @Query("origin_lng") originLng: Double,
        @Query("dest_lat") destLat: Double, @Query("dest_lng") destLng: Double): Response<Map<String, Any>>

    @GET("api/v1/location/airport-pickup-guidance")
    suspend fun getAirportGuidance(@Query("airport_code") airportCode: String = "DEL", @Query("terminal") terminal: String? = "T3"): Response<Map<String, Any>>

    @GET("api/v1/location/eta-refresh")
    suspend fun refreshTrafficEta(
        @Query("origin_lat") originLat: Double,
        @Query("origin_lng") originLng: Double,
        @Query("dest_lat") destLat: Double,
        @Query("dest_lng") destLng: Double,
        @Query("ride_id") rideId: String? = null
    ): Response<Map<String, Any>>

    @GET("api/v1/location/road-restrictions")
    suspend fun getRoadRestrictions(
        @Query("latitude") lat: Double,
        @Query("longitude") lng: Double,
        @Query("radius_km") radiusKm: Double = 25.0
    ): Response<List<Map<String, Any>>>

    @GET("api/v1/location/accuracy-advice")
    suspend fun getAccuracyAdvice(@Query("accuracy_m") accuracyM: Double): Response<Map<String, Any>>

    @GET("api/v1/location/gps-recovery-guide")
    suspend fun getGpsRecoveryGuide(): Response<Map<String, Any>>

    @GET("api/v1/location/attribution")
    suspend fun getMapAttribution(): Response<Map<String, Any>>

    @GET("api/v1/location/offline-cache-package")
    suspend fun getOfflineCachePackage(@Query("region_code") regionCode: String = "DELHI_NCR"): Response<Map<String, Any>>

    @GET("api/v1/location/building-entrances")
    suspend fun getBuildingEntrances(
        @Query("latitude") lat: Double,
        @Query("longitude") lng: Double,
        @Query("place_name") placeName: String? = null
    ): Response<List<Map<String, Any>>>

    @POST("api/v1/location/reverse-geocode")
    suspend fun reverseGeocode(@Body payload: Map<String, Double>): Response<Map<String, Any>>

    @POST("api/v1/fares/estimate")
    suspend fun calculateFareEstimate(@Body payload: FareEstimateRequest): Response<FareEstimateResponse>

    @POST("api/v1/pools/estimate")
    suspend fun getSharedEstimate(@Body payload: SharedEstimateRequest): Response<SharedEstimateResponse>

    @POST("api/v1/pools/matches")
    suspend fun findSharedRideMatches(@Body payload: SharedMatchRequest): Response<Map<String, Any>>

    @GET("api/v1/pools/preferences/detour")
    suspend fun getSharedRidePreferences(): Response<Map<String, Any>>

    @PUT("api/v1/pools/preferences/detour")
    suspend fun updateSharedRidePreferences(@Body payload: SharedRidePreferenceRequest): Response<Map<String, Any>>

    @GET("api/v1/pools/{tripId}")
    suspend fun getSharedTripDetails(@Path("tripId") tripId: String): Response<Map<String, Any>>

    @GET("api/v1/pools/{tripId}/availability")
    suspend fun getPoolSeatAvailability(@Path("tripId") tripId: String): Response<Map<String, Any>>

    @GET("api/v1/pools/ride/{rideId}")
    suspend fun getSharedTripForRide(@Path("rideId") rideId: String): Response<Map<String, Any>>

    @GET("api/v1/pools/{tripId}/messages")
    suspend fun getSharedTripMessages(@Path("tripId") tripId: String): Response<List<Map<String, Any>>>

    @POST("api/v1/pools/{tripId}/messages")
    suspend fun sendSharedTripMessage(@Path("tripId") tripId: String, @Body payload: Map<String, String>): Response<Map<String, Any>>

    @POST("api/v1/pools/bookings/{bookingId}/cancel")
    suspend fun cancelSharedRide(@Path("bookingId") bookingId: String): Response<Map<String, Any>>

    @POST("api/v1/rides/request")
    suspend fun requestRide(@Body payload: RideRequestPayload): Response<Map<String, Any>>

    @POST("api/v1/pools/book")
    suspend fun bookSharedRide(@Body payload: BookSharedRideRequest): Response<Map<String, Any>>

    @GET("api/v1/rides/{id}")
    suspend fun getRideDetails(@Path("id") rideId: String): Response<RideResponseDto>

    @POST("api/v1/rides/{id}/destination-change") suspend fun requestDestinationChange(@Path("id") rideId: String, @Body payload: Map<String, Any>): Response<Map<String, Any>>
    @POST("api/v1/rides/destination-change/{requestId}/customer-response") suspend fun respondDestinationQuote(@Path("requestId") requestId: String, @Body payload: Map<String, Any>): Response<Map<String, Any>>

    @GET("api/v1/rides/{id}/track")
    suspend fun trackRide(@Path("id") rideId: String): Response<TrackingDto>

    @POST("api/v1/rides/{id}/cancel")
    suspend fun cancelRide(@Path("id") rideId: String, @Body payload: Map<String, String>): Response<Map<String, Any>>

    @POST("api/v1/payments/intent")
    suspend fun createPaymentIntent(@Body payload: PaymentIntentRequest): Response<PaymentIntentResponse>

    @POST("api/v1/rides/{id}/rate")
    suspend fun rateDriver(@Path("id") rideId: String, @Body payload: RateDriverRequest): Response<Map<String, Any>>

    @GET("api/v1/customers/notifications")
    suspend fun getNotifications(): Response<List<NotificationDto>>

    @GET("api/v1/notifications/inbox")
    suspend fun getNotificationInbox(@Query("unread_only") unreadOnly: Boolean = false, @Query("event_type") eventType: String? = null): Response<NotificationInboxDto>

    @GET("api/v1/notifications/preferences")
    suspend fun getNotificationPreferences(): Response<Map<String, Any>>

    @POST("api/v1/customers/device-token")
    suspend fun registerCustomerDeviceToken(@Body payload: CustomerDeviceTokenPayload): Response<Map<String, Any>>

    @PUT("api/v1/notifications/preferences")
    suspend fun updateNotificationPreferences(@Body payload: Map<String, Any>): Response<Map<String, Any>>

    @PATCH("api/v1/notifications/inbox/{notificationId}/read")
    suspend fun markNotificationRead(@Path("notificationId") id: String): Response<Map<String, Any>>

    @PATCH("api/v1/notifications/inbox/{notificationId}/unread")
    suspend fun markNotificationUnread(@Path("notificationId") id: String): Response<Map<String, Any>>

    @GET("api/v1/notifications/inbox/{notificationId}/receipts")
    suspend fun getNotificationReceipts(@Path("notificationId") id: String): Response<List<Map<String, Any>>>

    @POST("api/v1/notifications/inbox/read-all")
    suspend fun markAllNotificationsRead(): Response<Map<String, Any>>

    @GET("api/v1/wallets/me")
    suspend fun getWallet(): Response<WalletDto>

    @POST("api/v1/wallets/topup")
    suspend fun topupWallet(@Body payload: TopupWalletRequest): Response<Map<String, Any>>

    @POST("api/v1/promos/validate")
    suspend fun validatePromo(@Body payload: PromoValidateRequest): Response<PromoValidateResponse>

    // --- Part 1: Auth & Identity Enhancements ---
    @POST("api/v1/auth/password/register")
    suspend fun registerWithPassword(@Body payload: PasswordRegisterPayload): Response<TokenResponse>

    @POST("api/v1/auth/password/login")
    suspend fun loginWithPassword(@Body payload: PasswordLoginPayload): Response<PasswordLoginResponse>

    @POST("api/v1/auth/oauth/google")
    suspend fun loginWithGoogle(@Body payload: GoogleOAuthPayload): Response<TokenResponse>

    @POST("api/v1/auth/oauth/apple")
    suspend fun loginWithApple(@Body payload: AppleOAuthPayload): Response<TokenResponse>

    @POST("api/v1/auth/passkey/login-challenge")
    suspend fun getPasskeyLoginChallenge(@Body payload: Map<String, String>): Response<PasskeyChallengeResponse>

    @POST("api/v1/auth/passkey/login-verify")
    suspend fun verifyPasskeyLogin(@Body payload: PasskeyLoginVerifyPayload): Response<TokenResponse>

    @POST("api/v1/auth/mfa/challenge-verify")
    suspend fun verifyMfaChallenge(@Body payload: MfaChallengeVerifyPayload): Response<TokenResponse>

    @POST("api/v1/auth/email/send-verification")
    suspend fun sendEmailVerification(): Response<Map<String, Any>>

    @POST("api/v1/auth/email/verify")
    suspend fun verifyEmail(@Body payload: EmailVerifyPayload): Response<Map<String, Any>>

    @POST("api/v1/auth/recovery/request")
    suspend fun requestAccountRecovery(@Body payload: AccountRecoveryRequestPayload): Response<Map<String, Any>>

    @POST("api/v1/auth/recovery/reset-password")
    suspend fun resetPasswordWithRecoveryToken(@Body payload: AccountRecoveryResetPayload): Response<Map<String, Any>>

    // --- Part 1: User Governance, Security & Preferences ---
    @POST("api/v1/user/biometric/toggle")
    suspend fun toggleBiometric(@Body payload: BiometricTogglePayload): Response<Map<String, Any>>

    @POST("api/v1/user/app-lock/pin")
    suspend fun setAppLockPin(@Body payload: AppLockPinPayload): Response<Map<String, Any>>

    @POST("api/v1/user/app-lock/verify")
    suspend fun verifyAppLockPin(@Body payload: AppLockPinPayload): Response<AppLockVerifyResponse>

    @POST("api/v1/user/passkey/register-challenge")
    suspend fun getPasskeyRegisterChallenge(): Response<PasskeyChallengeResponse>

    @POST("api/v1/user/passkey/register")
    suspend fun registerPasskey(@Body payload: PasskeyRegisterPayload): Response<Map<String, Any>>

    @POST("api/v1/user/mfa/setup")
    suspend fun setupMfa(): Response<MfaSetupResponse>

    @POST("api/v1/user/mfa/enable")
    suspend fun enableMfa(@Body payload: MfaEnablePayload): Response<Map<String, Any>>

    @POST("api/v1/user/mfa/disable")
    suspend fun disableMfa(@Body payload: MfaEnablePayload): Response<Map<String, Any>>

    @GET("api/v1/user/trusted-devices")
    suspend fun getTrustedDevices(): Response<List<TrustedDeviceDto>>

    @POST("api/v1/user/trusted-devices")
    suspend fun addTrustedDevice(@Body payload: AddTrustedDevicePayload): Response<TrustedDeviceDto>

    @DELETE("api/v1/user/trusted-devices/{deviceId}")
    suspend fun revokeTrustedDevice(@Path("deviceId") deviceId: String): Response<Map<String, Any>>

    @GET("api/v1/user/sessions")
    suspend fun getSessions(): Response<List<SessionDto>>

    @POST("api/v1/user/sessions/revoke-all-others")
    suspend fun revokeOtherSessions(): Response<Map<String, Any>>

    @GET("api/v1/auth/me")
    suspend fun getMe(): Response<Map<String, Any>>

    @Multipart
    @POST("api/v1/user/avatar")
    suspend fun uploadAvatar(@Part file: MultipartBody.Part): Response<Map<String, Any>>

    @DELETE("api/v1/user/avatar")
    suspend fun deleteAvatar(): Response<Map<String, Any>>

    @GET("api/v1/user/phones")
    suspend fun getSavedPhones(): Response<List<SavedPhoneDto>>

    @POST("api/v1/user/phones")
    suspend fun addSavedPhone(@Body payload: AddSavedPhonePayload): Response<SavedPhoneDto>

    @DELETE("api/v1/user/phones/{phoneId}")
    suspend fun deleteSavedPhone(@Path("phoneId") phoneId: String): Response<Map<String, Any>>

    @GET("api/v1/user/preferences")
    suspend fun getUserPreferences(): Response<UserPreferencesDto>

    @PUT("api/v1/user/preferences")
    suspend fun updateUserPreferences(@Body payload: UserPreferencesDto): Response<UserPreferencesDto>

    @GET("api/v1/user/membership")
    suspend fun getMembershipProfile(): Response<CustomerMembershipDto>

    @PUT("api/v1/user/membership")
    suspend fun updateMembershipProfile(@Body payload: CustomerMembershipUpdatePayload): Response<CustomerMembershipDto>

    @POST("api/v1/user/data-export")
    suspend fun requestDataExport(): Response<DataExportArchiveDto>

    @GET("api/v1/user/data-export/latest")
    suspend fun getLatestDataExport(): Response<DataExportArchiveDto>

    @GET("api/v1/user/data-export/{archiveId}/download")
    suspend fun downloadDataExportArchive(@Path("archiveId") archiveId: String): Response<ResponseBody>

    @POST("api/v1/user/deletion/request")
    suspend fun requestAccountDeletion(@Body payload: AccountDeletionRequestPayload): Response<AccountDeletionRequestDto>

    @GET("api/v1/user/deletion/status")
    suspend fun getAccountDeletionStatus(): Response<AccountDeletionRequestDto?>

    @POST("api/v1/user/deletion/cancel")
    suspend fun cancelAccountDeletion(): Response<Map<String, Any>>

    // --- Part 2: Advanced Booking, Multi-Stop, Schedule & Luxury Profiles ---
    @POST("api/v1/rides/advanced-request")
    suspend fun requestAdvancedRide(@Body payload: AdvancedRideRequestPayload): Response<Map<String, Any>>

    @POST("api/v1/rides/recommend-pickup-time")
    suspend fun recommendPickupTime(@Body payload: RecommendPickupTimeRequest): Response<RecommendPickupTimeResponse>

    @GET("api/v1/rides/flight-status")
    suspend fun getFlightStatus(
        @Query("flight_number") flightNumber: String,
        @Query("airline_code") airlineCode: String? = null
    ): Response<FlightStatusResponse>

    @POST("api/v1/rides/{ride_id}/sync-flight")
    suspend fun syncFlightDelay(@Path("ride_id") rideId: String): Response<Map<String, Any>>

    @GET("api/v1/rides/{ride_id}/calendar-invite")
    suspend fun getRideCalendarInvite(@Path("ride_id") rideId: String): Response<ResponseBody>

    @POST("api/v1/rides/{ride_id}/stops")
    suspend fun addRideStop(
        @Path("ride_id") rideId: String,
        @Body payload: AddStopRequest
    ): Response<Map<String, Any>>

    @PATCH("api/v1/rides/{ride_id}/stops/{stop_id}")
    suspend fun updateRideStop(
        @Path("ride_id") rideId: String,
        @Path("stop_id") stopId: String,
        @Body payload: UpdateStopRequest
    ): Response<Map<String, Any>>

    @DELETE("api/v1/rides/{ride_id}/stops/{stop_id}")
    suspend fun deleteRideStop(
        @Path("ride_id") rideId: String,
        @Path("stop_id") stopId: String
    ): Response<Map<String, Any>>

    @GET("api/v1/rides/recent-destinations")
    suspend fun getRecentDestinations(): Response<List<RecentDestinationDto>>

    @POST("api/v1/rides/parse-shared-location")
    suspend fun parseSharedLocation(@Body payload: SharedLocationParseRequest): Response<SharedLocationParseResponse>

    @GET("api/v1/rides/recurring")
    suspend fun getRecurringSchedules(): Response<List<RecurringScheduleDto>>

    @POST("api/v1/rides/recurring")
    suspend fun createRecurringSchedule(@Body payload: CreateRecurringScheduleRequest): Response<RecurringScheduleDto>

    @PATCH("api/v1/rides/recurring/{schedule_id}/toggle")
    suspend fun toggleRecurringSchedule(@Path("schedule_id") scheduleId: String): Response<RecurringScheduleDto>

    @DELETE("api/v1/rides/recurring/{schedule_id}")
    suspend fun deleteRecurringSchedule(@Path("schedule_id") scheduleId: String): Response<Map<String, Any>>

    @GET("api/v1/rides/templates")
    suspend fun getTripTemplates(): Response<List<TripTemplateDto>>

    @POST("api/v1/rides/templates")
    suspend fun createTripTemplate(@Body payload: CreateTripTemplateRequest): Response<TripTemplateDto>

    @DELETE("api/v1/rides/templates/{template_id}")
    suspend fun deleteTripTemplate(@Path("template_id") templateId: String): Response<Map<String, Any>>

    @GET("api/v1/rides/dependents")
    suspend fun getDependents(): Response<List<UserDependentDto>>

    @POST("api/v1/rides/dependents")
    suspend fun addDependent(@Body payload: CreateDependentRequest): Response<UserDependentDto>

    @DELETE("api/v1/rides/dependents/{dependent_id}")
    suspend fun deleteDependent(@Path("dependent_id") dependentId: String): Response<Map<String, Any>>

    // =========================================================================
    // Part 4 Fares & Settlement APIs (Features 76 - 100)
    // =========================================================================

    @POST("api/v1/fares/compare")
    suspend fun compareVehicleFares(@Body payload: FareCompareRequest): Response<List<VehicleFareComparisonDto>>

    @POST("api/v1/fares/price-lock")
    suspend fun lockFarePrice(@Body payload: PriceLockRequest): Response<PriceLockResponse>

    @POST("api/v1/fares/route-change-adjustment")
    suspend fun adjustRouteChangeFare(@Body payload: RouteChangeAdjustmentRequest): Response<RouteChangeAdjustmentResponse>

    @GET("api/v1/rides/{ride_id}/fare-breakdown")
    suspend fun getRideFareBreakdown(@Path("ride_id") rideId: String): Response<RideFareBreakdownResponse>

    @GET("api/v1/rides/{ride_id}/route-change-explanation")
    suspend fun getRouteChangeExplanation(@Path("ride_id") rideId: String): Response<RouteChangeExplanationResponse>

    @POST("api/v1/rides/{ride_id}/split-fare")
    suspend fun initiateSplitFare(
        @Path("ride_id") rideId: String,
        @Body payload: SplitFareInitiateRequest
    ): Response<List<SplitFareItemDto>>

    @POST("api/v1/rides/split-fare/{split_id}/respond")
    suspend fun respondToSplit(
        @Path("split_id") splitId: String,
        @Body payload: SplitFareRespondRequest
    ): Response<Map<String, Any>>

    @POST("api/v1/rides/split-fare/{split_id}/pay")
    suspend fun paySplitShare(
        @Path("split_id") splitId: String,
        @Body payload: SplitFarePayRequest
    ): Response<Map<String, Any>>

    @GET("api/v1/payments/saved-methods")
    suspend fun getSavedPaymentMethods(): Response<List<SavedPaymentMethodDto>>

    @POST("api/v1/payments/tokenize-card")
    suspend fun tokenizeCard(@Body payload: TokenizeCardRequest): Response<SavedPaymentMethodDto>

    @POST("api/v1/payments/save-vpa")
    suspend fun saveUpiVpa(@Body payload: SaveVpaRequest): Response<SavedPaymentMethodDto>

    @DELETE("api/v1/payments/saved-methods/{method_id}")
    suspend fun deleteSavedPaymentMethod(@Path("method_id") methodId: String): Response<Map<String, Any>>

    @POST("api/v1/payments/upi-intent")
    suspend fun generateUpiIntent(@Body payload: UpiIntentRequest): Response<UpiIntentResponse>

    @POST("api/v1/payments/upi-retry")
    suspend fun retryUpiIntent(@Body payload: UpiRetryRequest): Response<UpiRetryResponse>

    @POST("api/v1/payments/corporate-settlement")
    suspend fun settleCorporateRide(@Body payload: CorporateSettlementRequest): Response<Map<String, Any>>

    @POST("api/v1/wallets/auto-topup/config")
    suspend fun configureAutoTopup(@Body payload: AutoTopupConfigRequest): Response<Map<String, Any>>

    @GET("api/v1/wallets/alerts/low-balance")
    suspend fun checkWalletLowBalance(): Response<LowBalanceAlertResponse>

    @POST("api/v1/wallets/gift-cards/purchase")
    suspend fun purchaseGiftCard(@Body payload: GiftCardPurchaseRequest): Response<GiftCardResponse>

    @POST("api/v1/wallets/gift-cards/redeem")
    suspend fun redeemGiftCard(@Body payload: GiftCardRedeemRequest): Response<Map<String, Any>>

    @POST("api/v1/payments/refunds/partial")
    suspend fun requestPartialRefund(@Body payload: PartialRefundRequest): Response<RefundStatusResponse>

    @GET("api/v1/payments/refunds/{refund_id}/status")
    suspend fun getRefundStatus(@Path("refund_id") refundId: String): Response<RefundStatusResponse>

    @GET("api/v1/payments/refunds/my")
    suspend fun getMyRefunds(): Response<List<RefundItemDto>>

    @GET("api/v1/rides/{ride_id}/tax-invoice")
    suspend fun getTaxInvoice(@Path("ride_id") rideId: String): Response<TaxInvoiceResponse>

    @POST("api/v1/rides/{ride_id}/send-invoice-email")
    suspend fun sendInvoiceEmail(
        @Path("ride_id") rideId: String,
        @Body payload: SendInvoiceEmailRequest
    ): Response<Map<String, Any>>

    @POST("api/v1/promos/check-eligibility")
    suspend fun checkPromoEligibility(@Body payload: PromoEligibilityRequest): Response<PromoEligibilityResponse>

    @GET("api/v1/promos/expiring-soon")
    suspend fun getExpiringPromos(): Response<List<ExpiringPromoDto>>

    @POST("api/v1/rides/{ride_id}/disputes")
    suspend fun submitFareDispute(
        @Path("ride_id") rideId: String,
        @Body payload: SubmitDisputeRequest
    ): Response<Map<String, Any>>

    @GET("api/v1/rides/disputes/my")
    suspend fun getMyDisputes(): Response<List<DisputeDto>>

    // =========================================================================
    // Part 5 Advanced Tracking, Communication & Route Governance (101 - 125)
    // =========================================================================

    @GET("api/v1/rides/{ride_id}/telemetry")
    suspend fun getRideTelemetry(
        @Path("ride_id") rideId: String
    ): Response<AdvancedTelemetryResponse>

    @POST("api/v1/rides/{ride_id}/share")
    suspend fun createTripShareLink(
        @Path("ride_id") rideId: String,
        @Body payload: TripShareRequest
    ): Response<TripShareResponse>

    @GET("api/v1/rides/share/{token}")
    suspend fun getSharedTrip(
        @Path("token") token: String
    ): Response<SharedTripViewResponse>

    @POST("api/v1/rides/{ride_id}/arrival-photo")
    suspend fun uploadArrivalPhoto(
        @Path("ride_id") rideId: String,
        @Body payload: ArrivalPhotoRequest
    ): Response<Map<String, Any>>

    @GET("api/v1/rides/{ride_id}/route-preview")
    suspend fun getRoutePreview(
        @Path("ride_id") rideId: String
    ): Response<RoutePreviewResponse>

    @POST("api/v1/rides/{ride_id}/route-preference")
    suspend fun setRoutePreference(
        @Path("ride_id") rideId: String,
        @Body payload: RoutePreferenceRequest
    ): Response<Map<String, Any>>

    @GET("api/v1/rides/{ride_id}/receipt")
    suspend fun getCompletionReceipt(
        @Path("ride_id") rideId: String
    ): Response<TripReceiptResponse>

    @POST("api/v1/rides/{ride_id}/lost-item")
    suspend fun reportLostItem(
        @Path("ride_id") rideId: String,
        @Body payload: LostItemRequest
    ): Response<LostItemResponse>

    @GET("api/v1/rides/{ride_id}/driver-contact")
    suspend fun getPostTripDriverContact(
        @Path("ride_id") rideId: String
    ): Response<DriverContactResponse>

    @GET("api/v1/rides/{ride_id}/chat/quick-replies")
    suspend fun getQuickReplies(
        @Path("ride_id") rideId: String,
        @Query("role") role: String = "customer"
    ): Response<List<String>>

    @POST("api/v1/rides/{ride_id}/chat/translate")
    suspend fun translateChatMessage(
        @Path("ride_id") rideId: String,
        @Body payload: TranslateChatRequest
    ): Response<TranslateChatResponse>

    @POST("api/v1/rides/{ride_id}/chat/meeting-point")
    suspend fun sendMeetingPoint(
        @Path("ride_id") rideId: String,
        @Body payload: MeetingPointRequest
    ): Response<Map<String, Any>>

    // =========================================================================
    // Part 6 Safety, Emergency Response & Assurance (126 - 150)
    // =========================================================================

    @POST("api/v1/safety/sos")
    suspend fun triggerEmergencySos(@Body payload: SosTriggerRequest): Response<SosTriggerResponse>

    @GET("api/v1/safety/contacts")
    suspend fun getEmergencyContacts(): Response<List<EmergencyContactDto>>

    @POST("api/v1/safety/contacts")
    suspend fun addEmergencyContact(@Body payload: AddEmergencyContactRequest): Response<EmergencyContactDto>

    @DELETE("api/v1/safety/contacts/{contact_id}")
    suspend fun deleteEmergencyContact(@Path("contact_id") contactId: String): Response<Map<String, Any>>

    @POST("api/v1/safety/checkin/schedule")
    suspend fun scheduleSafetyCheckin(@Body payload: ScheduleCheckinRequest): Response<Map<String, Any>>

    @POST("api/v1/safety/checkin/respond")
    suspend fun respondSafetyCheckin(@Body payload: RespondCheckinRequest): Response<Map<String, Any>>

    @GET("api/v1/safety/verification-code/{ride_id}")
    suspend fun getPickupVerificationCode(@Path("ride_id") rideId: String): Response<PickupVerificationCodeResponse>

    @GET("api/v1/safety/driver-credentials/{ride_id}")
    suspend fun getDriverCredentials(@Path("ride_id") rideId: String): Response<DriverCredentialsResponse>

    @GET("api/v1/safety/vehicle-credentials/{ride_id}")
    suspend fun getVehicleCredentials(@Path("ride_id") rideId: String): Response<VehicleCredentialsResponse>

    @GET("api/v1/safety/safety-center")
    suspend fun getSafetyCenterInfo(): Response<SafetyCenterResponse>

    @POST("api/v1/safety/anomaly/route")
    suspend fun checkRouteAnomaly(@Body payload: RouteAnomalyCheckRequest): Response<RouteAnomalyCheckResponse>

    @POST("api/v1/safety/anomaly/stop")
    suspend fun checkUnexpectedStop(@Body payload: UnexpectedStopCheckRequest): Response<UnexpectedStopCheckResponse>

    @POST("api/v1/safety/recording/consent")
    suspend fun updateRecordingConsent(@Body payload: RecordingConsentRequest): Response<RecordingConsentResponse>

    @GET("api/v1/safety/recording/status/{ride_id}")
    suspend fun getRecordingStatus(@Path("ride_id") rideId: String): Response<RecordingStatusResponse>

    @POST("api/v1/safety/incidents")
    suspend fun lodgeIncidentReport(@Body payload: IncidentReportRequest): Response<IncidentReportResponse>

    @GET("api/v1/safety/incidents/{ticket_number}")
    suspend fun getIncidentTracking(@Path("ticket_number") ticketNumber: String): Response<IncidentTrackingResponse>

    @POST("api/v1/safety/drivers/block")
    suspend fun blockDriver(@Body payload: BlockDriverRequest): Response<BlockDriverResponse>

    @POST("api/v1/safety/incidents/unsafe-vehicle")
    suspend fun reportUnsafeVehicle(@Body payload: UnsafeVehicleRequest): Response<IncidentReportResponse>

    @POST("api/v1/safety/incidents/harassment")
    suspend fun reportHarassment(@Body payload: HarassmentReportRequest): Response<HarassmentReportResponse>

    @POST("api/v1/safety/notify-contacts/start/{ride_id}")
    suspend fun notifyContactsTripStart(@Path("ride_id") rideId: String): Response<Map<String, Any>>

    @POST("api/v1/safety/notify-contacts/arrival/{ride_id}")
    suspend fun notifyContactsArrival(@Path("ride_id") rideId: String): Response<Map<String, Any>>

    @GET("api/v1/safety/high-risk-status/{ride_id}")
    suspend fun checkHighRiskStatus(@Path("ride_id") rideId: String): Response<HighRiskStatusResponse>

    @POST("api/v1/safety/verify-pickup-pin")
    suspend fun verifyPickupPin(@Body payload: VerifyPickupPinRequest): Response<Map<String, Any>>

    @GET("api/v1/safety/safe-zones")
    suspend fun getSafePickupZones(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double
    ): Response<List<SafePickupZoneDto>>

    @GET("api/v1/safety/restricted-zone-check")
    suspend fun checkRestrictedPickupWarning(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double
    ): Response<RestrictedZoneWarningResponse>

    @GET("api/v1/safety/education-cards")
    suspend fun getSafetyEducationCards(): Response<List<SafetyEducationCardDto>>

    // =========================================================================
    // Part 7 Loyalty, Driver Rating & Tipping, Referral Sanctuary & Offers (151 - 175)
    // =========================================================================

    @POST("api/v1/loyalty-feedback/rides/{ride_id}/rating")
    suspend fun submitComprehensiveDriverRating(
        @Path("ride_id") rideId: String,
        @Body payload: SubmitDriverRatingRequest
    ): Response<Map<String, Any>>

    @POST("api/v1/loyalty-feedback/rides/{ride_id}/tip")
    suspend fun submitRideTip(
        @Path("ride_id") rideId: String,
        @Body payload: SubmitTipRequest
    ): Response<TipResponse>

    @GET("api/v1/loyalty-feedback/loyalty/summary")
    suspend fun getLoyaltySummary(): Response<LoyaltySummaryResponse>

    @GET("api/v1/loyalty-feedback/loyalty/ledger")
    suspend fun getLoyaltyLedger(): Response<List<LoyaltyLedgerItemDto>>

    @POST("api/v1/loyalty-feedback/loyalty/redeem")
    suspend fun redeemLoyaltyPoints(
        @Body payload: RedeemLoyaltyPointsRequest
    ): Response<RedeemLoyaltyPointsResponse>

    @GET("api/v1/loyalty-feedback/milestones")
    suspend fun getCustomerMilestones(): Response<List<MilestoneBadgeDto>>

    @GET("api/v1/loyalty-feedback/referrals/dashboard")
    suspend fun getReferralDashboard(): Response<ReferralDashboardResponse>

    @GET("api/v1/loyalty-feedback/offers")
    suspend fun getPersonalizedOffers(): Response<OffersResponse>

    @POST("api/v1/loyalty-feedback/offers/preferences")
    suspend fun updateOfferPreferences(
        @Body payload: OfferPreferencesRequest
    ): Response<Map<String, Any>>

    @GET("api/v1/loyalty-feedback/rides/history/filtered")
    suspend fun getFilteredRideHistory(
        @Query("status") status: String? = null,
        @Query("date_from") dateFrom: String? = null,
        @Query("date_to") dateTo: String? = null,
        @Query("limit") limit: Int = 50
    ): Response<List<FilteredRideDto>>

    @POST("api/v1/loyalty-feedback/rides/{ride_id}/repeat")
    suspend fun repeatPastTrip(
        @Path("ride_id") rideId: String
    ): Response<RepeatTripResponse>

    @GET("api/v1/loyalty-feedback/reports/monthly-statement")
    suspend fun getMonthlyRideStatement(
        @Query("year") year: Int,
        @Query("month") month: Int
    ): Response<MonthlyStatementResponse>

    @POST("api/v1/loyalty-feedback/surveys/submit")
    suspend fun submitCustomerSurvey(
        @Body payload: CustomerSurveyRequest
    ): Response<Map<String, Any>>

    // =========================================================================
    // Part 8 Support, Concierge & Help Center (Features 176 - 200)
    // =========================================================================
    @GET("api/v1/support/articles")
    suspend fun getHelpArticles(
        @Query("query") query: String? = null,
        @Query("category") category: String? = null,
        @Query("tag") tag: String? = null
    ): Response<List<HelpArticleDto>>

    @POST("api/v1/support/articles/{article_id}/rate")
    suspend fun rateHelpArticle(
        @Path("article_id") articleId: String,
        @Body payload: ArticleRatingRequestDto
    ): Response<Map<String, Any>>

    @GET("api/v1/support/incidents/active")
    suspend fun getActiveIncidentBanners(): Response<List<ServiceIncidentDto>>

    @GET("api/v1/support/service-status")
    suspend fun getPlatformServiceStatus(): Response<PlatformServiceStatusDto>

    @GET("api/v1/support/rides/{ride_id}/cancellation-quote")
    suspend fun getCancellationQuote(
        @Path("ride_id") rideId: String
    ): Response<CancellationQuoteDto>

    @POST("api/v1/support/rides/{ride_id}/guided-cancel")
    suspend fun executeGuidedCancellation(
        @Path("ride_id") rideId: String,
        @Body payload: GuidedCancellationRequestDto
    ): Response<GuidedCancellationResponseDto>

    @POST("api/v1/support/self-service/payment-troubleshooting")
    suspend fun troubleshootPayment(
        @Body payload: PaymentTroubleshootRequestDto
    ): Response<DiagnosticResultDto>

    @POST("api/v1/support/self-service/account-recovery")
    suspend fun requestAccountRecovery(
        @Body payload: AccountRecoveryRequestDto
    ): Response<AccountRecoveryResponseDto>

    @POST("api/v1/support/self-service/refund-request")
    suspend fun submitRefundRequest(
        @Body payload: RefundClaimRequestDto
    ): Response<RefundClaimResponseDto>

    @POST("api/v1/support/self-service/fare-review")
    suspend fun submitFareReview(
        @Body payload: FareReviewRequestDto
    ): Response<Map<String, Any>>

    @POST("api/v1/support/self-service/lost-item")
    suspend fun reportLostItem(
        @Body payload: LostItemClaimRequestDto
    ): Response<LostItemResponseDto>

    @POST("api/v1/support/callbacks")
    suspend fun requestCallback(
        @Body payload: CallbackRequestDto
    ): Response<CallbackResponseDto>

    @GET("api/v1/support/tickets")
    suspend fun getMySupportTickets(): Response<List<CustomerSupportTicketDto>>

    @POST("api/v1/support/tickets")
    suspend fun createSupportTicket(
        @Body payload: CreateSupportTicketRequestDto
    ): Response<CreateTicketResponseDto>

    @GET("api/v1/support/tickets/{ticket_id}/timeline")
    suspend fun getTicketTimeline(
        @Path("ticket_id") ticketId: String
    ): Response<TicketTimelineDto>

    @POST("api/v1/support/tickets/{ticket_id}/escalate")
    suspend fun escalateSupportTicket(
        @Path("ticket_id") ticketId: String,
        @Body payload: TicketEscalateRequestDto
    ): Response<Map<String, Any>>

    @POST("api/v1/support/tickets/{ticket_id}/survey")
    suspend fun submitCsatSurvey(
        @Path("ticket_id") ticketId: String,
        @Body payload: TicketCsatRequestDto
    ): Response<Map<String, Any>>

    @POST("api/v1/support/tickets/{ticket_id}/read")
    suspend fun markTicketMessagesRead(
        @Path("ticket_id") ticketId: String
    ): Response<Map<String, Any>>

    @GET("api/v1/support/notifications")
    suspend fun getSupportNotifications(): Response<List<SupportNotificationDto>>

    @GET("api/v1/support/retention-policy")
    suspend fun getDataRetentionPolicy(): Response<DataRetentionPolicyDto>

    @POST("api/v1/privacy/dsar-requests")
    suspend fun submitPrivacyDsar(
        @Body payload: PrivacyDsarSubmissionDto
    ): Response<PrivacyDsarResponseDto>

    @GET("api/v1/localization/translations/{locale}")
    suspend fun getLocalizationTranslations(
        @Path("locale") locale: String = "en"
    ): Response<Map<String, Any>>

    @POST("api/v1/auth/signup/customer")
    suspend fun signupCustomer(@Body payload: CustomerSignupRequestPayload): Response<TokenResponse>

    @POST("api/v1/auth/login/unified")
    suspend fun unifiedLogin(@Body payload: UnifiedLoginRequestPayload): Response<TokenResponse>

    @POST("api/v1/auth/forgot-password/request-otp")
    suspend fun requestForgotPasswordOtp(@Body payload: ForgotPasswordOtpRequestPayload): Response<Map<String, Any>>

    @POST("api/v1/auth/forgot-password/reset")
    suspend fun resetForgotPassword(@Body payload: ForgotPasswordResetRequestPayload): Response<TokenResponse>

    // Part 19: Infrastructure, Resilience & Developer Platform (Features 451-475)
    @GET("api/v1/infra/openapi-client-spec")
    suspend fun getOpenApiClientSpec(@Query("app_type") appType: String = "CUSTOMER"): Response<Map<String, Any>>

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

    // =========================================================================
    // Part 20 Enterprise, Corporate, Subscriptions & Products (Features 476 - 500)
    // =========================================================================
    @GET("api/v1/enterprise/corporate/my-profile")
    suspend fun getMyCorporateProfile(): Response<Map<String, Any>>

    @GET("api/v1/enterprise/subscriptions/plans")
    suspend fun getSubscriptionPlans(): Response<List<Map<String, Any>>>

    @GET("api/v1/enterprise/subscriptions/bundles")
    suspend fun getPrepaidBundles(): Response<List<Map<String, Any>>>

    @POST("api/v1/enterprise/subscriptions/subscribe")
    suspend fun subscribeToPlan(@Body payload: Map<String, Any>): Response<Map<String, Any>>

    @POST("api/v1/enterprise/subscriptions/bundles/purchase")
    suspend fun purchasePrepaidBundle(@Body payload: Map<String, Any>): Response<Map<String, Any>>

    @GET("api/v1/enterprise/loyalty-catalog")
    suspend fun getLoyaltyCatalog(): Response<List<Map<String, Any>>>

    @POST("api/v1/enterprise/loyalty-catalog/{id}/redeem")
    suspend fun redeemLoyaltyOffer(@Path("id") id: String, @Body payload: Map<String, Any>): Response<Map<String, Any>>

    @GET("api/v1/enterprise/products/airport-transfers")
    suspend fun getAirportPackages(): Response<List<Map<String, Any>>>

    @POST("api/v1/enterprise/products/airport-transfers/book")
    suspend fun bookAirportPackage(@Body payload: Map<String, Any>): Response<Map<String, Any>>

    @GET("api/v1/enterprise/products/intercity")
    suspend fun getIntercityProducts(): Response<List<Map<String, Any>>>

    @POST("api/v1/enterprise/products/intercity")
    suspend fun bookIntercityTrip(@Body payload: Map<String, Any>): Response<Map<String, Any>>

    @GET("api/v1/enterprise/products/hourly-bookings")
    suspend fun getHourlyBookings(): Response<List<Map<String, Any>>>

    @POST("api/v1/enterprise/products/hourly-bookings")
    suspend fun createHourlyBooking(@Body payload: Map<String, Any>): Response<Map<String, Any>>

    @GET("api/v1/enterprise/products/add-ons")
    suspend fun getChauffeurAddons(): Response<List<Map<String, Any>>>

    @GET("api/v1/enterprise/growth/feature-requests")
    suspend fun getFeatureRequests(): Response<List<Map<String, Any>>>

    @POST("api/v1/enterprise/growth/feature-requests/{key}/vote")
    suspend fun voteFeatureRequest(@Path("key") key: String): Response<Map<String, Any>>

    @POST("api/v1/enterprise/growth/feedback-analytics")
    suspend fun submitProductFeedback(@Body payload: Map<String, Any>): Response<Map<String, Any>>
}

data class CustomerSignupRequestPayload(
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("email") val email: String,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("password") val password: String,
    @SerializedName("username") val username: String,
    @SerializedName("otp_code") val otpCode: String
)

data class UnifiedLoginRequestPayload(
    @SerializedName("login_type") val loginType: String = "PASSWORD",
    @SerializedName("identifier") val identifier: String,
    @SerializedName("password") val password: String? = null,
    @SerializedName("otp_code") val otpCode: String? = null
)

data class ForgotPasswordOtpRequestPayload(
    @SerializedName("identifier") val identifier: String
)

data class ForgotPasswordResetRequestPayload(
    @SerializedName("identifier") val identifier: String,
    @SerializedName("otp_code") val otpCode: String,
    @SerializedName("new_password") val newPassword: String
)

data class PrivacyDsarSubmissionDto(
    @SerializedName("request_type") val requestType: String,
    @SerializedName("details") val details: String? = null
)

data class PrivacyDsarResponseDto(
    @SerializedName("id") val id: String,
    @SerializedName("request_number") val requestNumber: String,
    @SerializedName("request_type") val requestType: String,
    @SerializedName("status") val status: String,
    @SerializedName("message") val message: String
)


// ----------------- Part 4 DTOs -----------------
data class FareCompareRequest(
    @SerializedName("pickup_latitude") val pickupLatitude: Double,
    @SerializedName("pickup_longitude") val pickupLongitude: Double,
    @SerializedName("destination_latitude") val destinationLatitude: Double,
    @SerializedName("destination_longitude") val destinationLongitude: Double
)

data class VehicleFareComparisonDto(
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("code") val code: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String,
    @SerializedName("capacity") val capacity: Int,
    @SerializedName("icon_url") val iconUrl: String,
    @SerializedName("estimate") val estimate: FareEstimateResponse
)

data class PriceLockRequest(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("lock_duration_hours") val lockDurationHours: Int = 24
)

data class PriceLockResponse(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("price_locked") val priceLocked: Boolean,
    @SerializedName("locked_fare_rupees") val lockedFareRupees: Double,
    @SerializedName("lock_expires_at") val lockExpiresAt: String,
    @SerializedName("guarantee") val guarantee: String
)

data class RouteChangeAdjustmentRequest(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("new_distance_km") val newDistanceKm: Double,
    @SerializedName("new_duration_minutes") val newDurationMinutes: Double,
    @SerializedName("reason") val reason: String
)

data class RouteChangeAdjustmentResponse(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("original_fare_rupees") val originalFareRupees: Double,
    @SerializedName("revised_fare_rupees") val revisedFareRupees: Double,
    @SerializedName("fare_difference_rupees") val fareDifferenceRupees: Double,
    @SerializedName("fare_change_reason") val fareChangeReason: String
)

data class RideFareBreakdownResponse(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("currency") val currency: String,
    @SerializedName("total_fare_rupees") val totalFareRupees: Double,
    @SerializedName("distance_km") val distanceKm: Double,
    @SerializedName("duration_minutes") val durationMinutes: Double,
    @SerializedName("base_fare_rupees") val baseFareRupees: Double,
    @SerializedName("distance_charge_rupees") val distanceChargeRupees: Double,
    @SerializedName("time_charge_rupees") val timeChargeRupees: Double,
    @SerializedName("tax_rupees") val taxRupees: Double,
    @SerializedName("amenities_fee_rupees") val amenitiesFeeRupees: Double,
    @SerializedName("price_locked") val priceLocked: Boolean,
    @SerializedName("fare_cap_applied") val fareCapApplied: Boolean
)

data class RouteChangeExplanationResponse(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("original_fare_rupees") val originalFareRupees: Double,
    @SerializedName("final_fare_rupees") val finalFareRupees: Double,
    @SerializedName("fare_difference_rupees") val fareDifferenceRupees: Double,
    @SerializedName("fare_change_reason") val fareChangeReason: String,
    @SerializedName("has_changed") val hasChanged: Boolean
)

data class SplitFareInitiateRequest(
    @SerializedName("splits") val splits: List<Map<String, Any>>
)

data class SplitFareRespondRequest(
    @SerializedName("action") val action: String
)

data class SplitFarePayRequest(
    @SerializedName("payment_method") val paymentMethod: String = "WALLET"
)

data class SplitFareItemDto(
    @SerializedName("split_id") val splitId: String,
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("payer_name") val payerName: String,
    @SerializedName("payer_phone") val payerPhone: String,
    @SerializedName("share_amount_rupees") val shareAmountRupees: Double,
    @SerializedName("status") val status: String
)

data class SavedPaymentMethodDto(
    @SerializedName("id") val id: String,
    @SerializedName("method_type") val methodType: String,
    @SerializedName("brand") val brand: String,
    @SerializedName("last4") val last4: String,
    @SerializedName("exp_month") val expMonth: Int?,
    @SerializedName("exp_year") val expYear: Int?,
    @SerializedName("vpa_address") val vpaAddress: String?,
    @SerializedName("cardholder_name_mask") val cardholderNameMask: String?,
    @SerializedName("issuer_bank") val issuerBank: String?,
    @SerializedName("is_default") val isDefault: Boolean,
    @SerializedName("token_preview") val tokenPreview: String?
)

data class TokenizeCardRequest(
    @SerializedName("card_number") val cardNumber: String,
    @SerializedName("exp_month") val expMonth: Int,
    @SerializedName("exp_year") val expYear: Int,
    @SerializedName("cardholder_name") val cardholderName: String,
    @SerializedName("issuer_bank") val issuerBank: String = "HDFC Bank",
    @SerializedName("is_default") val isDefault: Boolean = false
)

data class SaveVpaRequest(
    @SerializedName("vpa_address") val vpaAddress: String,
    @SerializedName("is_default") val isDefault: Boolean = false
)

data class UpiIntentRequest(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("amount_cents") val amountCents: Int,
    @SerializedName("custom_vpa") val customVpa: String? = null
)

data class UpiIntentResponse(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("amount_rupees") val amountRupees: Double,
    @SerializedName("merchant_vpa") val merchantVpa: String,
    @SerializedName("merchant_name") val merchantName: String,
    @SerializedName("upi_intent_uri") val upiIntentUri: String,
    @SerializedName("qr_payload") val qrPayload: String,
    @SerializedName("supported_apps") val supportedApps: List<String>
)

data class UpiRetryRequest(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("retry_attempt") val retryAttempt: Int,
    @SerializedName("failure_code") val failureCode: String? = "U30_PAYMENT_FAILED"
)

data class UpiRetryResponse(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("retry_attempt") val retryAttempt: Int,
    @SerializedName("can_retry") val canRetry: Boolean,
    @SerializedName("recommended_fallback") val recommendedFallback: String,
    @SerializedName("message") val message: String
)

data class CorporateSettlementRequest(
    @SerializedName("ride_id") val rideId: String
)

data class AutoTopupConfigRequest(
    @SerializedName("enabled") val enabled: Boolean,
    @SerializedName("threshold_cents") val thresholdCents: Int = 50000,
    @SerializedName("amount_cents") val amountCents: Int = 200000,
    @SerializedName("method_id") val methodId: String? = null
)

data class LowBalanceAlertResponse(
    @SerializedName("is_low") val isLow: Boolean,
    @SerializedName("balance_rupees") val balanceRupees: Double,
    @SerializedName("threshold_rupees") val thresholdRupees: Double,
    @SerializedName("auto_topup_active") val autoTopupActive: Boolean,
    @SerializedName("alert_message") val alertMessage: String
)

data class GiftCardPurchaseRequest(
    @SerializedName("amount_cents") val amountCents: Int,
    @SerializedName("sender_name") val senderName: String,
    @SerializedName("recipient_email") val recipientEmail: String? = null,
    @SerializedName("recipient_phone") val recipientPhone: String? = null,
    @SerializedName("message") val message: String? = null
)

data class GiftCardRedeemRequest(
    @SerializedName("code") val code: String
)

data class GiftCardResponse(
    @SerializedName("gift_card_id") val giftCardId: String,
    @SerializedName("code") val code: String,
    @SerializedName("amount_rupees") val amountRupees: Double,
    @SerializedName("sender_name") val senderName: String,
    @SerializedName("expires_at") val expiresAt: String,
    @SerializedName("status") val status: String
)

data class PartialRefundRequest(
    @SerializedName("payment_id") val paymentId: String,
    @SerializedName("amount_cents") val amountCents: Int,
    @SerializedName("reason") val reason: String,
    @SerializedName("is_partial") val isPartial: Boolean = true
)

data class RefundTrackerStepDto(
    @SerializedName("step") val step: String,
    @SerializedName("completed") val completed: Boolean,
    @SerializedName("date") val date: String? = null,
    @SerializedName("arn") val arn: String? = null,
    @SerializedName("est") val est: String? = null
)

data class RefundStatusResponse(
    @SerializedName("refund_id") val refundId: String,
    @SerializedName("amount_rupees") val amountRupees: Double,
    @SerializedName("status") val status: String,
    @SerializedName("arn_reference") val arnReference: String?,
    @SerializedName("reason") val reason: String,
    @SerializedName("tracker_steps") val trackerSteps: List<RefundTrackerStepDto>? = null
)

data class RefundItemDto(
    @SerializedName("refund_id") val refundId: String,
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("amount_rupees") val amountRupees: Double,
    @SerializedName("is_partial") val isPartial: Boolean,
    @SerializedName("status") val status: String,
    @SerializedName("arn_reference") val arnReference: String?,
    @SerializedName("reason") val reason: String,
    @SerializedName("created_at") val createdAt: String
)

data class TaxInvoiceResponse(
    @SerializedName("invoice_number") val invoiceNumber: String,
    @SerializedName("invoice_date") val invoiceDate: String,
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("platform_name") val platformName: String,
    @SerializedName("platform_gstin") val platformGstin: String,
    @SerializedName("sac_code") val sacCode: String,
    @SerializedName("sac_description") val sacDescription: String,
    @SerializedName("customer_name") val customerName: String,
    @SerializedName("customer_gstin") val customerGstin: String,
    @SerializedName("cost_center") val costCenter: String,
    @SerializedName("pickup_address") val pickupAddress: String,
    @SerializedName("destination_address") val destinationAddress: String,
    @SerializedName("subtotal_rupees") val subtotalRupees: Double,
    @SerializedName("cgst_rate_pct") val cgstRatePct: Double,
    @SerializedName("cgst_rupees") val cgstRupees: Double,
    @SerializedName("sgst_rate_pct") val sgstRatePct: Double,
    @SerializedName("sgst_rupees") val sgstRupees: Double,
    @SerializedName("total_rupees") val totalRupees: Double,
    @SerializedName("payment_method") val paymentMethod: String,
    @SerializedName("compliance_note") val complianceNote: String
)

data class SendInvoiceEmailRequest(
    @SerializedName("recipient_email") val recipientEmail: String
)

data class PromoEligibilityRequest(
    @SerializedName("code") val code: String,
    @SerializedName("fare_cents") val fareCents: Int,
    @SerializedName("category_id") val categoryId: String? = null
)

data class PromoEligibilityResponse(
    @SerializedName("eligible") val eligible: Boolean,
    @SerializedName("code") val code: String? = null,
    @SerializedName("discount_rupees") val discountRupees: Double? = null,
    @SerializedName("reason") val reason: String? = null,
    @SerializedName("explanation") val explanation: String? = null
)

data class ExpiringPromoDto(
    @SerializedName("type") val type: String,
    @SerializedName("title") val title: String? = null,
    @SerializedName("code") val code: String? = null,
    @SerializedName("rides_remaining") val ridesRemaining: Int? = null,
    @SerializedName("discount_value") val discountValue: Double? = null,
    @SerializedName("expires_at") val expiresAt: String,
    @SerializedName("days_remaining") val daysRemaining: Int,
    @SerializedName("reminder_text") val reminderText: String
)

data class SubmitDisputeRequest(
    @SerializedName("dispute_reason") val disputeReason: String,
    @SerializedName("claimed_amount_cents") val claimedAmountCents: Int,
    @SerializedName("customer_comment") val customerComment: String
)

data class DisputeDto(
    @SerializedName("dispute_id") val disputeId: String,
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("dispute_reason") val disputeReason: String,
    @SerializedName("claimed_amount_rupees") val claimedAmountRupees: Double,
    @SerializedName("status") val status: String,
    @SerializedName("customer_comment") val customerComment: String?,
    @SerializedName("admin_resolution_notes") val adminResolutionNotes: String?,
    @SerializedName("created_at") val createdAt: String
)

// =========================================================================
// Part 5 DTOs: Advanced Tracking, Communication & Route Governance
// =========================================================================

data class DriverTelemetryDto(
    @SerializedName("driver_id") val driverId: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("license_number") val licenseNumber: String?,
    @SerializedName("rating") val rating: Double?,
    @SerializedName("total_trips") val totalTrips: Int?,
    @SerializedName("phone") val phone: String?
)

data class TripTimelineStepDto(
    @SerializedName("step") val step: String,
    @SerializedName("label") val label: String,
    @SerializedName("completed") val completed: Boolean,
    @SerializedName("timestamp") val timestamp: String?,
    @SerializedName("description") val description: String
)

data class AdvancedTelemetryResponse(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("status") val status: String,
    @SerializedName("driver_latitude") val driverLatitude: Double,
    @SerializedName("driver_longitude") val driverLongitude: Double,
    @SerializedName("heading") val heading: Double,
    @SerializedName("speed_kmh") val speedKmh: Double,
    @SerializedName("pickup_latitude") val pickupLatitude: Double,
    @SerializedName("pickup_longitude") val pickupLongitude: Double,
    @SerializedName("pickup_address") val pickupAddress: String,
    @SerializedName("destination_latitude") val destinationLatitude: Double,
    @SerializedName("destination_longitude") val destinationLongitude: Double,
    @SerializedName("destination_address") val destinationAddress: String,
    @SerializedName("start_otp") val startOtp: String?,
    @SerializedName("eta_minutes") val etaMinutes: Int,
    @SerializedName("eta_range_min") val etaRangeMin: Int,
    @SerializedName("eta_range_max") val etaRangeMax: Int,
    @SerializedName("eta_confidence_percentage") val etaConfidencePercentage: Int,
    @SerializedName("pickup_countdown_seconds") val pickupCountdownSeconds: Int,
    @SerializedName("freshness_seconds") val freshnessSeconds: Int,
    @SerializedName("freshness_status") val freshnessStatus: String,
    @SerializedName("location_recorded_at") val locationRecordedAt: String,
    @SerializedName("driver_arrival_photo_url") val driverArrivalPhotoUrl: String?,
    @SerializedName("vehicle_model") val vehicleModel: String,
    @SerializedName("vehicle_plate_number") val vehiclePlateNumber: String,
    @SerializedName("vehicle_color") val vehicleColor: String,
    @SerializedName("driver") val driver: DriverTelemetryDto?,
    @SerializedName("masked_virtual_number") val maskedVirtualNumber: String,
    @SerializedName("masked_virtual_ext") val maskedVirtualExt: String,
    @SerializedName("route_preference") val routePreference: String,
    @SerializedName("route_deviation_detected") val routeDeviationDetected: Boolean,
    @SerializedName("route_deviation_reason") val routeDeviationReason: String?,
    @SerializedName("traffic_delay_minutes") val trafficDelayMinutes: Int,
    @SerializedName("traffic_note") val trafficNote: String,
    @SerializedName("accessibility_announcement") val accessibilityAnnouncement: String,
    @SerializedName("timeline") val timeline: List<TripTimelineStepDto>
)

data class TripShareRequest(
    @SerializedName("recipient_name") val recipientName: String? = null,
    @SerializedName("recipient_phone") val recipientPhone: String? = null,
    @SerializedName("hide_exact_address") val hideExactAddress: Boolean = false,
    @SerializedName("expiry_hours") val expiryHours: Int = 4
)

data class TripShareResponse(
    @SerializedName("share_token") val shareToken: String,
    @SerializedName("share_url") val shareUrl: String,
    @SerializedName("hide_exact_address") val hideExactAddress: Boolean,
    @SerializedName("expires_at") val expiresAt: String,
    @SerializedName("recipient_name") val recipientName: String?,
    @SerializedName("recipient_phone") val recipientPhone: String?,
    @SerializedName("instructions") val instructions: String
)

data class SharedTripViewResponse(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("status") val status: String,
    @SerializedName("pickup_label") val pickupLabel: String,
    @SerializedName("destination_label") val destinationLabel: String,
    @SerializedName("driver_latitude") val driverLatitude: Double,
    @SerializedName("driver_longitude") val driverLongitude: Double,
    @SerializedName("vehicle_model") val vehicleModel: String,
    @SerializedName("vehicle_plate_number") val vehiclePlateNumber: String,
    @SerializedName("vehicle_color") val vehicleColor: String,
    @SerializedName("privacy_shield_active") val privacyShieldActive: Boolean,
    @SerializedName("link_expires_at") val linkExpiresAt: String
)

data class ArrivalPhotoRequest(
    @SerializedName("photo_url") val photoUrl: String
)

data class RouteWaypointDto(
    @SerializedName("lat") val lat: Double,
    @SerializedName("lng") val lng: Double,
    @SerializedName("label") val label: String
)

data class RoutePreviewResponse(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("route_preference") val routePreference: String,
    @SerializedName("primary_corridor") val primaryCorridor: String,
    @SerializedName("distance_km") val distanceKm: Double,
    @SerializedName("estimated_minutes") val estimatedMinutes: Double,
    @SerializedName("has_tolls") val hasTolls: Boolean,
    @SerializedName("pothole_index") val potholeIndex: String,
    @SerializedName("waypoints") val waypoints: List<RouteWaypointDto>
)

data class RoutePreferenceRequest(
    @SerializedName("route_preference") val routePreference: String
)

data class ReceiptFareBreakdownDto(
    @SerializedName("base_fare_rupees") val baseFareRupees: Double,
    @SerializedName("distance_fare_rupees") val distanceFareRupees: Double,
    @SerializedName("duration_fare_rupees") val durationFareRupees: Double,
    @SerializedName("gst_tax_rupees") val gstTaxRupees: Double,
    @SerializedName("total_charged_rupees") val totalChargedRupees: Double
)

data class TripReceiptResponse(
    @SerializedName("receipt_id") val receiptId: String,
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("status") val status: String,
    @SerializedName("date") val date: String,
    @SerializedName("passenger_name") val passengerName: String,
    @SerializedName("chauffeur_name") val chauffeurName: String,
    @SerializedName("vehicle_model") val vehicleModel: String,
    @SerializedName("license_plate") val licensePlate: String,
    @SerializedName("pickup_address") val pickupAddress: String,
    @SerializedName("destination_address") val destinationAddress: String,
    @SerializedName("distance_km") val distanceKm: Double,
    @SerializedName("duration_minutes") val durationMinutes: Double,
    @SerializedName("payment_method") val paymentMethod: String,
    @SerializedName("fare_breakdown") val fareBreakdown: ReceiptFareBreakdownDto,
    @SerializedName("sac_code") val sacCode: String,
    @SerializedName("download_url") val downloadUrl: String
)

data class LostItemRequest(
    @SerializedName("item_type") val itemType: String,
    @SerializedName("description") val description: String,
    @SerializedName("seat_location") val seatLocation: String,
    @SerializedName("contact_phone") val contactPhone: String
)

data class LostItemResponse(
    @SerializedName("report_id") val reportId: String,
    @SerializedName("ticket_number") val ticketNumber: String,
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("item_type") val itemType: String,
    @SerializedName("status") val status: String,
    @SerializedName("estimated_resolution") val estimatedResolution: String,
    @SerializedName("concierge_hotline") val conciergeHotline: String
)

data class DriverContactResponse(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("can_contact_driver") val canContactDriver: Boolean,
    @SerializedName("masked_phone_number") val maskedPhoneNumber: String,
    @SerializedName("extension") val extension: String,
    @SerializedName("contact_expires_at") val contactExpiresAt: String,
    @SerializedName("time_remaining_hours") val timeRemainingHours: Int,
    @SerializedName("policy") val policy: String
)

data class TranslateChatRequest(
    @SerializedName("text") val text: String,
    @SerializedName("target_language") val targetLanguage: String
)

data class TranslateChatResponse(
    @SerializedName("original_text") val originalText: String,
    @SerializedName("target_language") val targetLanguage: String,
    @SerializedName("translated_text") val translatedText: String
)

data class MeetingPointRequest(
    @SerializedName("point_name") val pointName: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double
)

// ----------------- Part 6 DTOs (Features 126 - 150) -----------------
data class SosTriggerRequest(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("alert_type") val alertType: String = "SOS_BUTTON",
    @SerializedName("notes") val notes: String? = null
)

data class SosTriggerResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("alert_id") val alertId: String,
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("status") val status: String,
    @SerializedName("alert_type") val alertType: String,
    @SerializedName("triggered_at") val triggeredAt: String,
    @SerializedName("contacts_notified_count") val contactsNotifiedCount: Int,
    @SerializedName("emergency_share_url") val emergencyShareUrl: String,
    @SerializedName("soc_hotline") val socHotline: String,
    @SerializedName("police_hotline") val policeHotline: String,
    @SerializedName("message") val message: String
)

data class EmergencyContactDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("relationship") val relationship: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("notify_on_trip_start") val notifyOnTripStart: Boolean,
    @SerializedName("notify_on_arrival") val notifyOnArrival: Boolean,
    @SerializedName("created_at") val createdAt: String? = null
)

data class AddEmergencyContactRequest(
    @SerializedName("name") val name: String,
    @SerializedName("relationship") val relationship: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("notify_on_trip_start") val notifyOnTripStart: Boolean = true,
    @SerializedName("notify_on_arrival") val notifyOnArrival: Boolean = true
)

data class ScheduleCheckinRequest(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("minutes_from_now") val minutesFromNow: Int = 15
)

data class RespondCheckinRequest(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("response_type") val responseType: String
)

data class PickupVerificationCodeResponse(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("verification_code") val verificationCode: String,
    @SerializedName("status") val status: String,
    @SerializedName("ride_status") val rideStatus: String,
    @SerializedName("instructions") val instructions: String,
    @SerializedName("requires_pin_confirmation") val requiresPinConfirmation: Boolean
)

data class DriverCredentialsResponse(
    @SerializedName("driver_id") val driverId: String,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("photo_url") val photoUrl: String,
    @SerializedName("security_badge_id") val securityBadgeId: String,
    @SerializedName("background_check_status") val backgroundCheckStatus: String,
    @SerializedName("background_check_authority") val backgroundCheckAuthority: String,
    @SerializedName("background_check_date") val backgroundCheckDate: String,
    @SerializedName("academy_certified") val academyCertified: Boolean,
    @SerializedName("academy_level") val academyLevel: String,
    @SerializedName("rating") val rating: Double,
    @SerializedName("total_trips") val totalTrips: Int,
    @SerializedName("experience_years") val experienceYears: Int,
    @SerializedName("languages") val languages: List<String>,
    @SerializedName("confidentiality_clearance") val confidentialityClearance: String
)

data class VehicleCredentialsResponse(
    @SerializedName("vehicle_id") val vehicleId: String,
    @SerializedName("model") val model: String,
    @SerializedName("license_plate") val licensePlate: String,
    @SerializedName("color") val color: String,
    @SerializedName("registration_certificate_rc") val registrationCertificateRc: String,
    @SerializedName("commercial_permit_number") val commercialPermitNumber: String,
    @SerializedName("permit_type") val permitType: String,
    @SerializedName("puc_certificate_status") val pucCertificateStatus: String,
    @SerializedName("puc_expiry_date") val pucExpiryDate: String,
    @SerializedName("fitness_certificate_status") val fitnessCertificateStatus: String,
    @SerializedName("fitness_expiry_date") val fitnessExpiryDate: String,
    @SerializedName("insurance_policy_number") val insurancePolicyNumber: String,
    @SerializedName("insurance_provider") val insuranceProvider: String,
    @SerializedName("ev_battery_health") val evBatteryHealth: String,
    @SerializedName("safety_equipment") val safetyEquipment: List<String>
)

data class HelplineDto(
    @SerializedName("name") val name: String,
    @SerializedName("number") val number: String,
    @SerializedName("category") val category: String,
    @SerializedName("description") val description: String
)

data class SocCommandCenterDto(
    @SerializedName("title") val title: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("response_sla") val responseSla: String,
    @SerializedName("description") val description: String
)

data class SafetyCenterResponse(
    @SerializedName("soc_command_center") val socCommandCenter: SocCommandCenterDto,
    @SerializedName("emergency_helplines") val emergencyHelplines: List<HelplineDto>,
    @SerializedName("safety_commitments") val safetyCommitments: List<String>
)

data class RouteAnomalyCheckRequest(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("current_lat") val currentLat: Double,
    @SerializedName("current_lng") val currentLng: Double
)

data class RouteAnomalyCheckResponse(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("anomaly_detected") val anomalyDetected: Boolean,
    @SerializedName("anomaly_type") val anomalyType: String,
    @SerializedName("status") val status: String,
    @SerializedName("recommended_action") val recommendedAction: String
)

data class UnexpectedStopCheckRequest(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("current_lat") val currentLat: Double,
    @SerializedName("current_lng") val currentLng: Double,
    @SerializedName("stationary_duration_seconds") val stationaryDurationSeconds: Int
)

data class UnexpectedStopCheckResponse(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("unexpected_stop_detected") val unexpectedStopDetected: Boolean,
    @SerializedName("stationary_duration_seconds") val stationaryDurationSeconds: Int,
    @SerializedName("distance_to_destination_km") val distanceToDestinationKm: Double,
    @SerializedName("safety_prompt_required") val safetyPromptRequired: Boolean,
    @SerializedName("message") val message: String
)

data class RecordingConsentRequest(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("role") val role: String = "CUSTOMER",
    @SerializedName("consented") val consented: Boolean
)

data class RecordingConsentResponse(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("passenger_consent") val passengerConsent: Boolean,
    @SerializedName("driver_consent") val driverConsent: Boolean,
    @SerializedName("is_recording_active") val isRecordingActive: Boolean,
    @SerializedName("recording_session_id") val recordingSessionId: String?,
    @SerializedName("legal_disclosure") val legalDisclosure: String,
    @SerializedName("message") val message: String
)

data class RecordingStatusResponse(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("passenger_consent") val passengerConsent: Boolean,
    @SerializedName("driver_consent") val driverConsent: Boolean,
    @SerializedName("is_recording_active") val isRecordingActive: Boolean,
    @SerializedName("session_id") val sessionId: String?,
    @SerializedName("encryption_algorithm") val encryptionAlgorithm: String,
    @SerializedName("retention_period_days") val retentionPeriodDays: Int
)

data class IncidentReportRequest(
    @SerializedName("ride_id") val rideId: String? = null,
    @SerializedName("category") val category: String,
    @SerializedName("severity") val severity: String = "MEDIUM",
    @SerializedName("description") val description: String,
    @SerializedName("photo_url") val photoUrl: String? = null,
    @SerializedName("role") val role: String = "CUSTOMER"
)

data class IncidentReportResponse(
    @SerializedName("ticket_number") val ticketNumber: String,
    @SerializedName("status") val status: String,
    @SerializedName("category") val category: String,
    @SerializedName("severity") val severity: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("message") val message: String
)

data class IncidentTimelineStep(
    @SerializedName("step") val step: String,
    @SerializedName("timestamp") val timestamp: String?,
    @SerializedName("completed") val completed: Boolean
)

data class IncidentTrackingResponse(
    @SerializedName("ticket_number") val ticketNumber: String,
    @SerializedName("ride_id") val rideId: String?,
    @SerializedName("category") val category: String,
    @SerializedName("severity") val severity: String,
    @SerializedName("status") val status: String,
    @SerializedName("description") val description: String,
    @SerializedName("resolution_notes") val resolutionNotes: String?,
    @SerializedName("follow_up_action") val followUpAction: String?,
    @SerializedName("timeline") val timeline: List<IncidentTimelineStep>,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("resolved_at") val resolvedAt: String?
)

data class BlockDriverRequest(
    @SerializedName("driver_id") val driverId: String,
    @SerializedName("ride_id") val rideId: String? = null,
    @SerializedName("reason") val reason: String,
    @SerializedName("report_incident") val reportIncident: Boolean = false,
    @SerializedName("incident_description") val incidentDescription: String? = null
)

data class BlockDriverResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("driver_id") val driverId: String,
    @SerializedName("block_id") val blockId: String,
    @SerializedName("incident_ticket_number") val incidentTicketNumber: String?,
    @SerializedName("message") val message: String
)

data class UnsafeVehicleRequest(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("defect_type") val defectType: String,
    @SerializedName("description") val description: String
)

data class HarassmentReportRequest(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("perpetrator_role") val perpetratorRole: String = "DRIVER",
    @SerializedName("description") val description: String,
    @SerializedName("request_supervisor_call") val requestSupervisorCall: Boolean = true
)

data class HarassmentReportResponse(
    @SerializedName("ticket_number") val ticketNumber: String,
    @SerializedName("status") val status: String,
    @SerializedName("severity") val severity: String,
    @SerializedName("supervisor_call_requested") val supervisorCallRequested: Boolean,
    @SerializedName("soc_direct_line") val socDirectLine: String,
    @SerializedName("message") val message: String
)

data class HighRiskStatusResponse(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("high_risk_pickup") val highRiskPickup: Boolean,
    @SerializedName("reason") val reason: String,
    @SerializedName("mandatory_pin_required") val mandatoryPinRequired: Boolean,
    @SerializedName("verification_pin") val verificationPin: String
)

data class VerifyPickupPinRequest(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("pin") val pin: String
)

data class SafePickupZoneDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("address") val address: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("zone_type") val zoneType: String,
    @SerializedName("amenities") val amenities: List<String>,
    @SerializedName("safety_rating") val safetyRating: Double,
    @SerializedName("distance_km") val distanceKm: Double,
    @SerializedName("walking_minutes") val walkingMinutes: Int
)

data class RestrictedAlternativeDto(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("distance_meters") val distanceMeters: Int
)

data class RestrictedZoneWarningResponse(
    @SerializedName("is_restricted") val isRestricted: Boolean,
    @SerializedName("zone_name") val zoneName: String?,
    @SerializedName("warning_title") val warningTitle: String?,
    @SerializedName("warning_message") val warningMessage: String?,
    @SerializedName("suggested_alternative") val suggestedAlternative: RestrictedAlternativeDto?
)

data class SafetyEducationCardDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("subtitle") val subtitle: String,
    @SerializedName("category") val category: String,
    @SerializedName("icon") val icon: String,
    @SerializedName("content") val content: String,
    @SerializedName("badge") val badge: String
)

// ----------------- Part 7 DTOs -----------------
data class SubmitDriverRatingRequest(
    @SerializedName("overall_score") val overallScore: Double,
    @SerializedName("cleanliness_score") val cleanlinessScore: Double = 5.0,
    @SerializedName("comfort_score") val comfortScore: Double = 5.0,
    @SerializedName("service_quality_score") val serviceQualityScore: Double = 5.0,
    @SerializedName("navigation_score") val navigationScore: Double = 5.0,
    @SerializedName("feedback_tags") val feedbackTags: List<String> = emptyList(),
    @SerializedName("private_feedback") val privateFeedback: String? = null,
    @SerializedName("public_comment") val publicComment: String? = null
)

data class SubmitTipRequest(
    @SerializedName("tip_cents") val tipCents: Int,
    @SerializedName("idempotency_key") val idempotencyKey: String? = null
)

data class TipResponse(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("tip_cents") val tipCents: Int,
    @SerializedName("tip_amount_rupees") val tipAmountRupees: Double,
    @SerializedName("tip_status") val tipStatus: String,
    @SerializedName("adjustment_window_open") val adjustmentWindowOpen: Boolean,
    @SerializedName("adjustment_expires_at") val adjustmentExpiresAt: String?
)

data class LoyaltySummaryResponse(
    @SerializedName("loyalty_points_balance") val pointsBalance: Int,
    @SerializedName("lifetime_points_earned") val lifetimePoints: Int,
    @SerializedName("membership_tier") val membershipTier: String,
    @SerializedName("tier_expires_at") val tierExpiresAt: String?,
    @SerializedName("ride_streak_count") val rideStreakCount: Int,
    @SerializedName("tier_benefits") val tierBenefits: List<String>,
    @SerializedName("renewal_reminder") val renewalReminder: RenewalReminderDto?
)

data class RenewalReminderDto(
    @SerializedName("tier") val tier: String,
    @SerializedName("message") val message: String,
    @SerializedName("reminder_type") val reminderType: String
)

data class LoyaltyLedgerItemDto(
    @SerializedName("id") val id: String,
    @SerializedName("transaction_type") val transactionType: String,
    @SerializedName("points_change") val pointsChange: Int,
    @SerializedName("balance_after") val balanceAfter: Int,
    @SerializedName("description") val description: String,
    @SerializedName("ride_id") val rideId: String?,
    @SerializedName("created_at") val createdAt: String
)

data class RedeemLoyaltyPointsRequest(
    @SerializedName("points_to_redeem") val pointsToRedeem: Int
)

data class RedeemLoyaltyPointsResponse(
    @SerializedName("redeemed_points") val redeemedPoints: Int,
    @SerializedName("credit_amount_cents") val creditAmountCents: Int,
    @SerializedName("credit_amount_rupees") val creditAmountRupees: Double,
    @SerializedName("remaining_points_balance") val remainingPointsBalance: Int,
    @SerializedName("wallet_balance_cents") val walletBalanceCents: Int
)

data class MilestoneBadgeDto(
    @SerializedName("badge_code") val badgeCode: String,
    @SerializedName("badge_name") val badgeName: String,
    @SerializedName("badge_tier") val badgeTier: String,
    @SerializedName("description") val description: String,
    @SerializedName("icon_url") val iconUrl: String,
    @SerializedName("unlocked_at") val unlockedAt: String
)

data class ReferralDashboardResponse(
    @SerializedName("referral_code") val referralCode: String,
    @SerializedName("total_referrals") val totalReferrals: Int,
    @SerializedName("successful_referrals") val successfulReferrals: Int,
    @SerializedName("total_bonus_earned_cents") val totalBonusEarnedCents: Int,
    @SerializedName("eligibility_terms") val eligibilityTerms: String,
    @SerializedName("recent_referrals") val recentReferrals: List<RecentReferralDto>
)

data class RecentReferralDto(
    @SerializedName("referral_id") val referralId: String,
    @SerializedName("referred_name") val referredName: String,
    @SerializedName("reward_status") val rewardStatus: String,
    @SerializedName("reward_amount_rupees") val rewardAmountRupees: Double,
    @SerializedName("created_at") val createdAt: String
)

data class OffersResponse(
    @SerializedName("notification_frequency") val notificationFrequency: String,
    @SerializedName("birthday_date") val birthdayDate: String?,
    @SerializedName("anniversary_date") val anniversaryDate: String?,
    @SerializedName("offers") val offers: List<PersonalizedOfferDto>
)

data class PersonalizedOfferDto(
    @SerializedName("id") val id: String,
    @SerializedName("offer_type") val offerType: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("discount_type") val discountType: String,
    @SerializedName("discount_value") val discountValue: Double,
    @SerializedName("promo_code") val promoCode: String,
    @SerializedName("valid_until") val validUntil: String?,
    @SerializedName("is_redeemed") val isRedeemed: Boolean
)

data class OfferPreferencesRequest(
    @SerializedName("frequency") val frequency: String? = null,
    @SerializedName("birthday_date") val birthdayDate: String? = null,
    @SerializedName("anniversary_date") val anniversaryDate: String? = null
)

data class FilteredRideDto(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("status") val status: String,
    @SerializedName("pickup_address") val pickupAddress: String,
    @SerializedName("destination_address") val destinationAddress: String,
    @SerializedName("fare_rupees") val fareRupees: Double,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("tip_cents") val tipCents: Int,
    @SerializedName("tip_status") val tipStatus: String?,
    @SerializedName("points_earned") val pointsEarned: Int
)

data class RepeatTripResponse(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("status") val status: String,
    @SerializedName("pickup_address") val pickupAddress: String,
    @SerializedName("destination_address") val destinationAddress: String,
    @SerializedName("estimated_fare_rupees") val estimatedFareRupees: Double,
    @SerializedName("message") val message: String
)

data class MonthlyStatementResponse(
    @SerializedName("customer_id") val customerId: String,
    @SerializedName("statement_period") val statementPeriod: String,
    @SerializedName("total_rides") val totalRides: Int,
    @SerializedName("total_spend_rupees") val totalSpendRupees: Double,
    @SerializedName("total_tips_rupees") val totalTipsRupees: Double,
    @SerializedName("total_distance_km") val totalDistanceKm: Double,
    @SerializedName("csv_download_url") val csvDownloadUrl: String,
    @SerializedName("rides") val rides: List<FilteredRideDto>
)

data class CustomerSurveyRequest(
    @SerializedName("ride_id") val rideId: String? = null,
    @SerializedName("csat_score") val csatScore: Int,
    @SerializedName("nps_score") val npsScore: Int,
    @SerializedName("survey_category") val surveyCategory: String = "OVERALL_SATISFACTION",
    @SerializedName("feedback_text") val feedbackText: String? = null
)

// =============================================================================
// Part 8 DTOs: Support, Concierge & Help Center (Features 176 - 200)
// =============================================================================
data class HelpArticleDto(
    @SerializedName("id") val id: String,
    @SerializedName("slug") val slug: String,
    @SerializedName("title") val title: String,
    @SerializedName("category") val category: String,
    @SerializedName("summary") val summary: String,
    @SerializedName("content_markdown") val contentMarkdown: String,
    @SerializedName("helpful_votes") val helpfulVotes: Int,
    @SerializedName("unhelpful_votes") val unhelpfulVotes: Int,
    @SerializedName("context_tags") val contextTags: String? = null
)

data class ArticleRatingRequestDto(
    @SerializedName("was_helpful") val wasHelpful: Boolean,
    @SerializedName("feedback_text") val feedbackText: String? = null
)

data class ServiceIncidentDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("title") val title: String? = "System Advisory",
    @SerializedName("severity") val severity: String? = "INFO",
    @SerializedName("service_affected") val serviceAffected: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("action_recommendation") val actionRecommendation: String? = null,
    @SerializedName("is_resolved") val isResolved: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null
) {
    val displayMessage: String get() = message ?: description ?: "Operational advisory in effect."
}

data class ServiceHealthComponentDto(
    @SerializedName("name") val name: String,
    @SerializedName("status") val status: String,
    @SerializedName("latency_ms") val latencyMs: Int,
    @SerializedName("uptime_90d") val uptime90d: String
)

data class PlatformServiceStatusDto(
    @SerializedName("overall_status") val overallStatus: String,
    @SerializedName("summary") val summary: String,
    @SerializedName("checked_at") val checkedAt: String,
    @SerializedName("services") val services: List<ServiceHealthComponentDto>
)

data class CancellationQuoteDto(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("ride_status") val rideStatus: String,
    @SerializedName("elapsed_minutes") val elapsedMinutes: Double,
    @SerializedName("grace_period_minutes") val gracePeriodMinutes: Int,
    @SerializedName("is_within_grace_period") val isWithinGracePeriod: Boolean,
    @SerializedName("cancellation_fee_cents") val cancellationFeeCents: Int,
    @SerializedName("cancellation_fee_rupees") val cancellationFeeRupees: Double,
    @SerializedName("fee_explanation") val feeExplanation: String,
    @SerializedName("suggested_reasons") val suggestedReasons: List<String>,
    @SerializedName("can_request_waiver") val canRequestWaiver: Boolean
)

data class GuidedCancellationRequestDto(
    @SerializedName("reason_code") val reasonCode: String,
    @SerializedName("notes") val notes: String? = null,
    @SerializedName("waiver_requested") val waiverRequested: Boolean = false
)

data class GuidedCancellationResponseDto(
    @SerializedName("status") val status: String,
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("fee_charged_cents") val feeChargedCents: Int,
    @SerializedName("fee_charged_rupees") val feeChargedRupees: Double,
    @SerializedName("waiver_granted") val waiverGranted: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("support_ticket_number") val supportTicketNumber: String? = null,
    @SerializedName("complaint_reference_number") val complaintReferenceNumber: String? = null
)

data class PaymentTroubleshootRequestDto(
    @SerializedName("error_code") val errorCode: String? = null,
    @SerializedName("payment_method") val paymentMethod: String? = null,
    @SerializedName("ride_id") val rideId: String? = null
)

data class DiagnosticResultDto(
    @SerializedName("status") val status: String,
    @SerializedName("error_code") val errorCode: String,
    @SerializedName("human_explanation") val humanExplanation: String,
    @SerializedName("recommended_actions") val recommendedActions: List<String>,
    @SerializedName("payment_method_status") val paymentMethodStatus: String,
    @SerializedName("auto_retry_supported") val autoRetrySupported: Boolean
)

data class AccountRecoveryRequestDto(
    @SerializedName("recovery_type") val recoveryType: String,
    @SerializedName("details") val details: String? = null
)

data class AccountRecoveryResponseDto(
    @SerializedName("status") val status: String,
    @SerializedName("ticket_number") val ticketNumber: String,
    @SerializedName("complaint_reference_number") val complaintReferenceNumber: String,
    @SerializedName("instructions") val instructions: String,
    @SerializedName("security_challenge") val securityChallenge: String
)

data class RefundClaimRequestDto(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("reason") val reason: String,
    @SerializedName("requested_cents") val requestedCents: Int? = null,
    @SerializedName("explanation") val explanation: String = ""
)

data class RefundClaimResponseDto(
    @SerializedName("status") val status: String,
    @SerializedName("refund_cents") val refundCents: Int,
    @SerializedName("refund_rupees") val refundRupees: Double,
    @SerializedName("message") val message: String,
    @SerializedName("complaint_reference_number") val complaintReferenceNumber: String? = null
)

data class FareReviewRequestDto(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("disputed_amount_cents") val disputedAmountCents: Int,
    @SerializedName("evidence_text") val evidenceText: String,
    @SerializedName("evidence_url") val evidenceUrl: String? = null
)

data class LostItemClaimRequestDto(
    @SerializedName("ride_id") val rideId: String,
    @SerializedName("item_category") val itemCategory: String,
    @SerializedName("item_description") val itemDescription: String,
    @SerializedName("seat_location") val seatLocation: String? = null
)

data class LostItemResponseDto(
    @SerializedName("status") val status: String,
    @SerializedName("ticket_number") val ticketNumber: String,
    @SerializedName("complaint_reference_number") val complaintReferenceNumber: String,
    @SerializedName("assigned_squad") val assignedSquad: String,
    @SerializedName("next_steps") val nextSteps: String
)

data class CallbackRequestDto(
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("preferred_time_window") val preferredTimeWindow: String = "IMMEDIATE",
    @SerializedName("subject") val subject: String = "VIP Concierge Inquiry",
    @SerializedName("ticket_id") val ticketId: String? = null
)

data class CallbackResponseDto(
    @SerializedName("status") val status: String,
    @SerializedName("request_id") val requestId: String,
    @SerializedName("estimated_wait_minutes") val estimatedWaitMinutes: Int,
    @SerializedName("message") val message: String
)

data class CustomerSupportTicketDto(
    @SerializedName("id") val id: String,
    @SerializedName("ticket_number") val ticketNumber: String,
    @SerializedName("complaint_reference_number") val complaintReferenceNumber: String,
    @SerializedName("subject") val subject: String,
    @SerializedName("category") val category: String,
    @SerializedName("status") val status: String,
    @SerializedName("priority") val priority: String,
    @SerializedName("assigned_squad") val assignedSquad: String?,
    @SerializedName("language_preference") val languagePreference: String?,
    @SerializedName("is_escalated") val isEscalated: Boolean = false,
    @SerializedName("lost_item_status") val lostItemStatus: String? = null,
    @SerializedName("csat_score") val csatScore: Int? = null,
    @SerializedName("data_retention_days") val dataRetentionDays: Int = 365,
    @SerializedName("ride_id") val rideId: String? = null,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String
)

data class CreateSupportTicketRequestDto(
    @SerializedName("subject") val subject: String,
    @SerializedName("category") val category: String = "RIDE_INQUIRY",
    @SerializedName("description") val description: String,
    @SerializedName("ride_id") val rideId: String? = null,
    @SerializedName("priority") val priority: String = "MEDIUM",
    @SerializedName("language_preference") val languagePreference: String = "en",
    @SerializedName("data_retention_days") val dataRetentionDays: Int = 365,
    @SerializedName("allow_duplicate") val allowDuplicate: Boolean = false
)

data class CreateTicketResponseDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("ticket_number") val ticketNumber: String? = null,
    @SerializedName("complaint_reference_number") val complaintReferenceNumber: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("assigned_squad") val assignedSquad: String? = null,
    @SerializedName("is_duplicate_warning") val isDuplicateWarning: Boolean = false,
    @SerializedName("existing_ticket_id") val existingTicketId: String? = null,
    @SerializedName("existing_ticket_number") val existingTicketNumber: String? = null,
    @SerializedName("message") val message: String? = null
)

data class TicketTimelineEventDto(
    @SerializedName("stage") val stage: String,
    @SerializedName("label") val label: String,
    @SerializedName("timestamp") val timestamp: String?,
    @SerializedName("completed") val completed: Boolean,
    @SerializedName("detail") val detail: String
)

data class TicketTimelineDto(
    @SerializedName("ticket_id") val ticketId: String,
    @SerializedName("ticket_number") val ticketNumber: String,
    @SerializedName("complaint_reference_number") val complaintReferenceNumber: String,
    @SerializedName("status") val status: String,
    @SerializedName("priority") val priority: String,
    @SerializedName("assigned_squad") val assignedSquad: String,
    @SerializedName("is_escalated") val isEscalated: Boolean,
    @SerializedName("response_sla_target") val responseSlaTarget: String,
    @SerializedName("events") val events: List<TicketTimelineEventDto>
)

data class TicketEscalateRequestDto(
    @SerializedName("reason") val reason: String
)

data class TicketCsatRequestDto(
    @SerializedName("score") val score: Int,
    @SerializedName("comment") val comment: String? = null
)

data class SupportNotificationDto(
    @SerializedName("id") val id: String,
    @SerializedName("ticket_id") val ticketId: String,
    @SerializedName("complaint_reference_number") val complaintReferenceNumber: String,
    @SerializedName("notification_type") val notificationType: String,
    @SerializedName("title") val title: String,
    @SerializedName("preview") val preview: String,
    @SerializedName("is_read") val isRead: Boolean,
    @SerializedName("created_at") val createdAt: String
)

data class DataRetentionPolicyDto(
    @SerializedName("policy_version") val policyVersion: String,
    @SerializedName("jurisdiction") val jurisdiction: String,
    @SerializedName("default_retention_days") val defaultRetentionDays: Int,
    @SerializedName("minimum_statutory_days") val minimumStatutoryDays: Int,
    @SerializedName("maximum_allowed_days") val maximumAllowedDays: Int,
    @SerializedName("user_customizable") val userCustomizable: Boolean,
    @SerializedName("description") val description: String,
    @SerializedName("data_types_covered") val dataTypesCovered: List<String>
)

data class TourismPlaceDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("category") val category: String,
    @SerializedName("short_description") val shortDescription: String,
    @SerializedName("photo_url") val photoUrl: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("address") val address: String,
    @SerializedName("city") val city: String,
    @SerializedName("rating") val rating: Double = 4.8,
    @SerializedName("review_count") val reviewCount: Int = 1500,
    @SerializedName("entry_fee") val entryFee: String = "Free",
    @SerializedName("timings") val timings: String = "06:00 AM – 06:00 PM",
    @SerializedName("tag") val tag: String = "Famous Heritage",
    @SerializedName("week_rotation_group") val weekRotationGroup: Int = 0,
    @SerializedName("is_featured") val isFeatured: Boolean = true,
    @SerializedName("distance_km") val distanceKm: Double? = null,
    @SerializedName("approx_fare_rupees") val approxFareRupees: Int? = null
)

data class TourismWeeklyResponse(
    @SerializedName("current_week_rotation") val currentWeekRotation: Int,
    @SerializedName("days_until_next_rotation") val daysUntilNextRotation: Int,
    @SerializedName("rotation_interval_days") val rotationIntervalDays: Int = 7,
    @SerializedName("rotation_theme") val rotationTheme: String,
    @SerializedName("featured_places") val featuredPlaces: List<TourismPlaceDto>,
    @SerializedName("all_places") val allPlaces: List<TourismPlaceDto>
)

data class IsochroneRequest(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("travel_time_minutes") val travelTimeMinutes: Int = 15
)

data class LatLngPointDto(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double
)

data class IsochroneResponse(
    @SerializedName("origin") val origin: LatLngPointDto,
    @SerializedName("travel_time_minutes") val travelTimeMinutes: Int,
    @SerializedName("reachability_radius_km") val reachabilityRadiusKm: Double,
    @SerializedName("polygon") val polygon: List<LatLngPointDto>,
    @SerializedName("provider_mode") val providerMode: String
)

data class AddressValidationRequest(
    @SerializedName("address_line") val addressLine: String,
    @SerializedName("locality") val locality: String? = null,
    @SerializedName("postal_code") val postalCode: String? = null
)

data class AddressValidationResponse(
    @SerializedName("valid") val valid: Boolean,
    @SerializedName("verdict") val verdict: String,
    @SerializedName("formatted_address") val formattedAddress: String,
    @SerializedName("confidence") val confidence: String? = null,
    @SerializedName("provider_mode") val providerMode: String? = null
)

data class NavigationStepDto(
    @SerializedName("instruction") val instruction: String,
    @SerializedName("maneuver") val maneuver: String,
    @SerializedName("distance") val distance: String,
    @SerializedName("duration") val duration: String? = null
)

data class NavigationGuidanceResponse(
    @SerializedName("distance_km") val distanceKm: Double,
    @SerializedName("duration_minutes") val durationMinutes: Double,
    @SerializedName("steps") val steps: List<NavigationStepDto>,
    @SerializedName("polyline") val polyline: String? = null,
    @SerializedName("provider_mode") val providerMode: String? = null
)



