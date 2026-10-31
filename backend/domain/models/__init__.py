from backend.domain.models.models import (
    Base,
    User,
    CustomerProfile,
    DriverProfile,
    DriverDocument,
    VehicleCategory,
    Vehicle,
    DriverVehicleAssignment,
    DriverAvailability,
    DriverLocation,
    FareRule,
    FareEstimate,
    Ride,
    RideStop,
    FavoriteLocation,
    ReferralRecord,
    PromoCode,
    PromoRedemption,
    Wallet,
    WalletTransaction,
    SupportTicket,
    SupportMessage,
    RideChat,
    RideChatMessage,
    DriverDailyTarget,
    DriverPayoutRequest,
    GeofenceZone,
    SavedLocationPin,
    RoadRestriction,
    SurgeRule,
    BroadcastNotification,
    RideStatusEvent,
    RideAssignment,
    Payment,
    DriverRating,
    DeviceToken,
    Notification,
    NotificationTemplate,
    Session,
    AdminRole,
    AdminPermission,
    AdminRolePermission,
    AdminUserRole,
    SupportEscalationPolicy,
    BroadcastDelivery,
    PooledTrip,
    PooledBooking,
    PooledTripStop,
    PooledTripMessage,
    SharedRidePreference,
    AuditLog,
    SavedPaymentMethod,
    RideFareSplit,
    PaymentRefund,
    FareDispute,
    TripShareLink,
    LostItemReport,
    EmergencyContact,
    IncidentReport,
    EmergencyAlert,
    DriverOnboardingTicket,
    DriverFatigueCheck,
    IncidentEvidence,
    DriverShift,
    DriverWeeklyGoal,
    DriverPayoutAccount,
    DriverLedgerEntry,
    DriverIncentiveProgress,
    RouteLatencyMetric
)

import uuid

class DroppedModelStub:
    """Stub placeholder for purged enterprise bloat models."""
    def __init__(self, *args, **kwargs):
        self.id = kwargs.get("id", str(uuid.uuid4()))
        for k, v in kwargs.items():
            setattr(self, k, v)
    def __repr__(self):
        return f"<DroppedModelStub {self.__class__.__name__}>"

# Stub assignments for purged models
ABTestExperiment = DroppedModelStub
AccountDeletionRequest = DroppedModelStub
AddressQualityFeedback = DroppedModelStub
AdminActionJustification = DroppedModelStub
AdminCalendarEvent = DroppedModelStub
AdminRecordNote = DroppedModelStub
AdminReviewTemplate = DroppedModelStub
AdminWalletAdjustmentRequest = DroppedModelStub
AdministrativeAuditLog = DroppedModelStub
AirportTransferPackage = DroppedModelStub
ApiErrorCatalogEntry = DroppedModelStub
ApiVersionRecord = DroppedModelStub
BackgroundJob = DroppedModelStub
BackupPolicy = DroppedModelStub
BusinessApprovalRequest = DroppedModelStub
ChauffeurServiceAddon = DroppedModelStub
CommissionRule = DroppedModelStub
CorporateAccount = DroppedModelStub
CorporateInvoice = DroppedModelStub
CorporateTravelPolicy = DroppedModelStub
CustomReportConfig = DroppedModelStub
CustomerMilestoneBadge = DroppedModelStub
CustomerRidePass = DroppedModelStub
CustomerSubscription = DroppedModelStub
CustomerSurvey = DroppedModelStub
DataExportArchive = DroppedModelStub
DataRetentionPolicy = DroppedModelStub
DatabaseMigrationVerification = DroppedModelStub
DependencyVulnerabilityReport = DroppedModelStub
DisasterRecoveryPlan = DroppedModelStub
DistributedTraceSpan = DroppedModelStub
DriverBatteryOptimizationSetting = DroppedModelStub
DriverBlock = DroppedModelStub
DriverDispatchPreferences = DroppedModelStub
DriverFinancialSupportRequest = DroppedModelStub
DriverOperatingExpense = DroppedModelStub
DriverPayoutReconciliation = DroppedModelStub
DriverPayoutSchedule = DroppedModelStub
DriverRatingAppeal = DroppedModelStub
DriverSafetyPreferences = DroppedModelStub
DriverScheduleAvailability = DroppedModelStub
DriverTrainingModule = DroppedModelStub
DriverTrainingProgress = DroppedModelStub
DriverVehicleInspectionPhoto = DroppedModelStub
DriverVehicleInspection = DroppedModelStub
EmployeeProfile = DroppedModelStub
EventTransportWorkspace = DroppedModelStub
FeatureFlag = DroppedModelStub
FeatureRequestVote = DroppedModelStub
FleetSubscriptionPlan = DroppedModelStub
GeofenceZoneAudit = DroppedModelStub
GiftCard = DroppedModelStub
HelpArticleRating = DroppedModelStub
HelpArticle = DroppedModelStub
HourlyChauffeurBooking = DroppedModelStub
IdempotencyRecord = DroppedModelStub
IntercityProduct = DroppedModelStub
InvoiceCorrectionRequest = DroppedModelStub
LocalizationCatalog = DroppedModelStub
LocationRetentionSetting = DroppedModelStub
LoyaltyPartnerCatalog = DroppedModelStub
LoyaltyPointsLedger = DroppedModelStub
MobileCrashReport = DroppedModelStub
MultiCityOperatingConfig = DroppedModelStub
NotificationConsentAudit = DroppedModelStub
NotificationDeliveryAttempt = DroppedModelStub
PartnerAccount = DroppedModelStub
PartnerBooking = DroppedModelStub
PassengerOccupancyEvent = DroppedModelStub
PaymentEvent = DroppedModelStub
PaymentWebhookInbox = DroppedModelStub
PersonalizedOffer = DroppedModelStub
PrepaidRideBundle = DroppedModelStub
PrivacyDataSubjectRequest = DroppedModelStub
ProductFeedbackAnalytics = DroppedModelStub
ProviderHealthConfig = DroppedModelStub
RecurringRideSchedule = DroppedModelStub
RedisNamespaceConfig = DroppedModelStub
RestoreDrillSchedule = DroppedModelStub
RideDestinationChangeRequest = DroppedModelStub
RideOperationalEvent = DroppedModelStub
RidePassRedemption = DroppedModelStub
RidePass = DroppedModelStub
ScheduledJobMonitor = DroppedModelStub
ScheduledReportDelivery = DroppedModelStub
SecretRotationWorkflow = DroppedModelStub
SensitiveRecordAccessLog = DroppedModelStub
ServiceIncidentAdvisory = DroppedModelStub
ServiceRecoveryCredit = DroppedModelStub
SupportCallbackRequest = DroppedModelStub
SupportResponseTemplate = DroppedModelStub
TourismPlace = DroppedModelStub
TripTemplate = DroppedModelStub
TrustedDevice = DroppedModelStub
UserDependent = DroppedModelStub
UserNotificationSettings = DroppedModelStub
UserPreferences = DroppedModelStub
UserSavedPhone = DroppedModelStub
WhiteLabelTenantConfig = DroppedModelStub

__all__ = [
    "Base",
    "User",
    "CustomerProfile",
    "DriverProfile",
    "DriverDocument",
    "VehicleCategory",
    "Vehicle",
    "DriverVehicleAssignment",
    "DriverAvailability",
    "DriverLocation",
    "FareRule",
    "FareEstimate",
    "Ride",
    "RideStop",
    "FavoriteLocation",
    "ReferralRecord",
    "PromoCode",
    "PromoRedemption",
    "Wallet",
    "WalletTransaction",
    "SupportTicket",
    "SupportMessage",
    "RideChat",
    "RideChatMessage",
    "DriverDailyTarget",
    "DriverPayoutRequest",
    "GeofenceZone",
    "SavedLocationPin",
    "RoadRestriction",
    "SurgeRule",
    "BroadcastNotification",
    "RideStatusEvent",
    "RideAssignment",
    "Payment",
    "DriverRating",
    "DeviceToken",
    "Notification",
    "NotificationTemplate",
    "Session",
    "AdminRole",
    "AdminPermission",
    "AdminRolePermission",
    "AdminUserRole",
    "SupportEscalationPolicy",
    "BroadcastDelivery",
    "PooledTrip",
    "PooledBooking",
    "PooledTripStop",
    "PooledTripMessage",
    "SharedRidePreference",
    "AuditLog",
    "SavedPaymentMethod",
    "RideFareSplit",
    "PaymentRefund",
    "FareDispute",
    "TripShareLink",
    "LostItemReport",
    "EmergencyContact",
    "IncidentReport",
    "EmergencyAlert",
    "DriverOnboardingTicket",
    "DriverFatigueCheck",
    "IncidentEvidence",
    "DriverShift",
    "DriverWeeklyGoal",
    "DriverPayoutAccount",
    "DriverLedgerEntry",
    "DriverIncentiveProgress",
    "RouteLatencyMetric",
    "ABTestExperiment",
    "AccountDeletionRequest",
    "AddressQualityFeedback",
    "AdminActionJustification",
    "AdminCalendarEvent",
    "AdminRecordNote",
    "AdminReviewTemplate",
    "AdminWalletAdjustmentRequest",
    "AdministrativeAuditLog",
    "AirportTransferPackage",
    "ApiErrorCatalogEntry",
    "ApiVersionRecord",
    "BackgroundJob",
    "BackupPolicy",
    "BusinessApprovalRequest",
    "ChauffeurServiceAddon",
    "CommissionRule",
    "CorporateAccount",
    "CorporateInvoice",
    "CorporateTravelPolicy",
    "CustomReportConfig",
    "CustomerMilestoneBadge",
    "CustomerRidePass",
    "CustomerSubscription",
    "CustomerSurvey",
    "DataExportArchive",
    "DataRetentionPolicy",
    "DatabaseMigrationVerification",
    "DependencyVulnerabilityReport",
    "DisasterRecoveryPlan",
    "DistributedTraceSpan",
    "DriverBatteryOptimizationSetting",
    "DriverBlock",
    "DriverDispatchPreferences",
    "DriverFinancialSupportRequest",
    "DriverOperatingExpense",
    "DriverPayoutReconciliation",
    "DriverPayoutSchedule",
    "DriverRatingAppeal",
    "DriverSafetyPreferences",
    "DriverScheduleAvailability",
    "DriverTrainingModule",
    "DriverTrainingProgress",
    "DriverVehicleInspectionPhoto",
    "DriverVehicleInspection",
    "EmployeeProfile",
    "EventTransportWorkspace",
    "FeatureFlag",
    "FeatureRequestVote",
    "FleetSubscriptionPlan",
    "GeofenceZoneAudit",
    "GiftCard",
    "HelpArticleRating",
    "HelpArticle",
    "HourlyChauffeurBooking",
    "IdempotencyRecord",
    "IntercityProduct",
    "InvoiceCorrectionRequest",
    "LocalizationCatalog",
    "LocationRetentionSetting",
    "LoyaltyPartnerCatalog",
    "LoyaltyPointsLedger",
    "MobileCrashReport",
    "MultiCityOperatingConfig",
    "NotificationConsentAudit",
    "NotificationDeliveryAttempt",
    "PartnerAccount",
    "PartnerBooking",
    "PassengerOccupancyEvent",
    "PaymentEvent",
    "PaymentWebhookInbox",
    "PersonalizedOffer",
    "PrepaidRideBundle",
    "PrivacyDataSubjectRequest",
    "ProductFeedbackAnalytics",
    "ProviderHealthConfig",
    "RecurringRideSchedule",
    "RedisNamespaceConfig",
    "RestoreDrillSchedule",
    "RideDestinationChangeRequest",
    "RideOperationalEvent",
    "RidePassRedemption",
    "RidePass",
    "ScheduledJobMonitor",
    "ScheduledReportDelivery",
    "SecretRotationWorkflow",
    "SensitiveRecordAccessLog",
    "ServiceIncidentAdvisory",
    "ServiceRecoveryCredit",
    "SupportCallbackRequest",
    "SupportResponseTemplate",
    "TourismPlace",
    "TripTemplate",
    "TrustedDevice",
    "UserDependent",
    "UserNotificationSettings",
    "UserPreferences",
    "UserSavedPhone",
    "WhiteLabelTenantConfig"
]
