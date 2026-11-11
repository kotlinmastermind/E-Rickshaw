package com.vaisentra.mobility.core.model

import com.google.gson.annotations.SerializedName

/**
 * Standardized Shared API Data Transfer Objects (DTOs) across Customer and Driver applications.
 * Enforces DRY principles by centralizing authentication, user profiles, vehicle categories,
 * telemetry, chat messaging, safety/SOS alerts, and platform notifications.
 */

// ============================================================================
// NEARBY VEHICLES & TELEMETRY
// ============================================================================

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
    @SerializedName("speed_kmh") val speedKmh: Double = 0.0,
    @SerializedName("battery_level") val batteryLevel: Int = 85,
    @SerializedName("eta_minutes") val etaMinutes: Int = 3,
    @SerializedName("distance_km") val distanceKm: Double = 0.8,
    @SerializedName("status") val status: String = "AVAILABLE"
)

data class NearbyVehiclesResponse(
    @SerializedName("customer_latitude") val customerLatitude: Double,
    @SerializedName("customer_longitude") val customerLongitude: Double,
    @SerializedName("radius_km") val radiusKm: Double,
    @SerializedName("total_available") val totalAvailable: Int,
    @SerializedName("vehicles") val vehicles: List<NearbyVehicleDto>
)

// ============================================================================
// CHAT MESSAGING
// ============================================================================

data class RideChatMessageDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("ride_id") val rideId: String? = null,
    @SerializedName("sender_user_id") val senderUserId: String? = null,
    @SerializedName("sender_id") val senderId: String? = null,
    @SerializedName("sender_role") val senderRole: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("message_text") val messageText: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("sent_at") val sentAt: String? = null
)

data class SendChatMessageRequest(
    @SerializedName("message_text") val messageText: String,
    @SerializedName("recipient_role") val recipientRole: String? = null
)

// ============================================================================
// NOTIFICATIONS & INBOX
// ============================================================================

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
    @SerializedName("notifications") val notifications: List<NotificationDto> = emptyList(),
    @SerializedName("items") val items: List<NotificationDto> = emptyList(),
    @SerializedName("unread_count") val unreadCount: Int = 0,
    @SerializedName("total") val total: Int = 0
)

// ============================================================================
// SAFETY, SOS & INCIDENTS
// ============================================================================

data class SosTriggerRequest(
    @SerializedName("ride_id") val rideId: String? = null,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("alert_type") val alertType: String = "SOS_BUTTON",
    @SerializedName("notes") val notes: String? = null
)

data class SosTriggerResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("alert_id") val alertId: String,
    @SerializedName("ride_id") val rideId: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("alert_type") val alertType: String? = null,
    @SerializedName("triggered_at") val triggeredAt: String? = null,
    @SerializedName("contacts_notified_count") val contactsNotifiedCount: Int? = null,
    @SerializedName("emergency_share_url") val emergencyShareUrl: String? = null,
    @SerializedName("soc_hotline") val socHotline: String? = null,
    @SerializedName("police_hotline") val policeHotline: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("pcr_dispatched") val pcrDispatched: Boolean = true,
    @SerializedName("ambulance_dispatched") val ambulanceDispatched: Boolean = false,
    @SerializedName("response_eta_minutes") val responseEtaMinutes: Int = 4
)

data class EmergencyContactDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("phone_number") val phoneNumber: String = phone ?: "",
    @SerializedName("relationship") val relationship: String? = null,
    @SerializedName("is_primary") val isPrimary: Boolean = false,
    @SerializedName("notify_on_trip_start") val notifyOnTripStart: Boolean = false,
    @SerializedName("notify_on_arrival") val notifyOnArrival: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null
)

data class IncidentReportRequest(
    @SerializedName("ride_id") val rideId: String? = null,
    @SerializedName("incident_type") val incidentType: String? = null,
    @SerializedName("category") val category: String = incidentType ?: "GENERAL",
    @SerializedName("severity") val severity: String = "MEDIUM",
    @SerializedName("description") val description: String,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,
    @SerializedName("photo_url") val photoUrl: String? = null,
    @SerializedName("role") val role: String = "CUSTOMER",
    @SerializedName("involved_party_role") val involvedPartyRole: String? = role
)

data class IncidentReportResponse(
    @SerializedName("success") val success: Boolean = true,
    @SerializedName("ticket_number") val ticketNumber: String,
    @SerializedName("assigned_team") val assignedTeam: String = "SAFETY_OPERATIONS_CENTER",
    @SerializedName("status") val status: String = "INVESTIGATING",
    @SerializedName("category") val category: String? = null,
    @SerializedName("severity") val severity: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("message") val message: String? = null
)

// ============================================================================
// PRIVACY & COMPLIANCE
// ============================================================================

data class PrivacyDsarSubmissionDto(
    @SerializedName("request_type") val requestType: String,
    @SerializedName("reason") val reason: String? = null,
    @SerializedName("details") val details: String? = reason
)

data class PrivacyDsarResponseDto(
    @SerializedName("success") val success: Boolean = true,
    @SerializedName("id") val id: String = "",
    @SerializedName("request_id") val requestId: String = id,
    @SerializedName("request_number") val requestNumber: String = requestId,
    @SerializedName("request_type") val requestType: String = "",
    @SerializedName("status") val status: String = "PENDING",
    @SerializedName("message") val message: String = "",
    @SerializedName("estimated_completion_days") val estimatedCompletionDays: Int = 30
)

// ============================================================================
// RIDE DETAILS & TRIP STATE
// ============================================================================

data class RideDetailsDto(
    @SerializedName("id") val id: String,
    @SerializedName("customer_id") val customerId: String? = null,
    @SerializedName("driver_id") val driverId: String? = null,
    @SerializedName("status") val status: String = "REQUESTED",
    @SerializedName("pickup_address") val pickupAddress: String = "",
    @SerializedName("pickup_latitude") val pickupLatitude: Double = 0.0,
    @SerializedName("pickup_longitude") val pickupLongitude: Double = 0.0,
    @SerializedName("destination_address") val destinationAddress: String = "",
    @SerializedName("destination_latitude") val destinationLatitude: Double = 0.0,
    @SerializedName("destination_longitude") val destinationLongitude: Double = 0.0,
    @SerializedName("fare") val fare: Double = 0.0,
    @SerializedName("final_fare") val finalFare: Double? = null,
    @SerializedName("currency") val currency: String = "INR",
    @SerializedName("otp") val otp: String? = null,
    @SerializedName("category_code") val categoryCode: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("completed_at") val completedAt: String? = null,
    @SerializedName("cancellation_reason") val cancellationReason: String? = null
)
