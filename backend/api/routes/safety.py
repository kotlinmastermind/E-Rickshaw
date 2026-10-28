from typing import Optional, List, Dict, Any
from fastapi import APIRouter, Depends, HTTPException, status, Query
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession

from backend.infrastructure.database.session import get_db
from backend.api.deps import get_current_user
from backend.domain.models import User
from backend.application.services.safety_advanced_service import SafetyAdvancedService

router = APIRouter(prefix="/safety", tags=["Safety, Emergency & Regulatory Assurance"])


# --- Pydantic Request Models ---
class SosTriggerPayload(BaseModel):
    ride_id: str
    latitude: float
    longitude: float
    alert_type: str = "SOS_BUTTON"
    notes: Optional[str] = None

class EmergencyContactCreatePayload(BaseModel):
    name: str = Field(..., min_length=2, max_length=128)
    relationship: str = Field(default="Family", max_length=64)
    phone_number: str = Field(..., min_length=10, max_length=32)
    notify_on_trip_start: bool = True
    notify_on_arrival: bool = True

class SafetyCheckinSchedulePayload(BaseModel):
    ride_id: str
    minutes_from_now: int = 15

class SafetyCheckinResponsePayload(BaseModel):
    ride_id: str
    response_type: str  # ALL_GOOD, NEED_HELP, SNOOZE_10_MIN

class RecordingConsentPayload(BaseModel):
    ride_id: str
    role: str = "CUSTOMER"  # CUSTOMER or DRIVER
    consented: bool

class IncidentReportPayload(BaseModel):
    ride_id: Optional[str] = None
    category: str
    severity: str = "MEDIUM"
    description: str
    photo_url: Optional[str] = None
    role: str = "CUSTOMER"

class DriverBlockPayload(BaseModel):
    driver_id: str
    ride_id: Optional[str] = None
    reason: str
    report_incident: bool = False
    incident_description: Optional[str] = None

class UnsafeVehiclePayload(BaseModel):
    ride_id: str
    defect_type: str
    description: str

class HarassmentReportPayload(BaseModel):
    ride_id: str
    perpetrator_role: str = "DRIVER"
    description: str
    request_supervisor_call: bool = True

class VerifyPickupPinPayload(BaseModel):
    ride_id: str
    pin: str

class RouteAnomalyCheckPayload(BaseModel):
    ride_id: str
    current_lat: float
    current_lng: float

class UnexpectedStopCheckPayload(BaseModel):
    ride_id: str
    current_lat: float
    current_lng: float
    stationary_duration_seconds: int

class AdminResolveCasePayload(BaseModel):
    resolution_notes: str
    follow_up_action: str


# --- Endpoints ---

# Feature 126 & 129: In-Trip Emergency SOS Button
@router.post("/sos", status_code=status.HTTP_201_CREATED)
async def trigger_emergency_sos(
    payload: SosTriggerPayload,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SafetyAdvancedService(db)
    return await service.trigger_emergency_sos(
        ride_id=payload.ride_id,
        current_user=current_user,
        latitude=payload.latitude,
        longitude=payload.longitude,
        alert_type=payload.alert_type,
        notes=payload.notes
    )


# Feature 127: Emergency Contacts Setup
@router.get("/contacts")
async def list_emergency_contacts(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SafetyAdvancedService(db)
    return await service.get_emergency_contacts(current_user.id)


@router.post("/contacts", status_code=status.HTTP_201_CREATED)
async def add_emergency_contact(
    payload: EmergencyContactCreatePayload,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SafetyAdvancedService(db)
    return await service.add_emergency_contact(current_user.id, payload.model_dump())


@router.delete("/contacts/{contact_id}")
async def delete_emergency_contact(
    contact_id: str,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SafetyAdvancedService(db)
    return await service.delete_emergency_contact(current_user.id, contact_id)


# Feature 128: Safety Check-in Timer
@router.post("/checkin/schedule")
async def schedule_safety_checkin(
    payload: SafetyCheckinSchedulePayload,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SafetyAdvancedService(db)
    return await service.schedule_safety_checkin(payload.ride_id, payload.minutes_from_now, current_user)


@router.post("/checkin/respond")
async def respond_to_safety_checkin(
    payload: SafetyCheckinResponsePayload,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SafetyAdvancedService(db)
    return await service.respond_to_safety_checkin(payload.ride_id, current_user, payload.response_type)


# Feature 130: Pickup Verification Code
@router.get("/verification-code/{ride_id}")
async def get_pickup_verification_code(
    ride_id: str,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SafetyAdvancedService(db)
    return await service.get_pickup_verification_code(ride_id, current_user)


# Feature 131: Driver Identity Verification Display
@router.get("/driver-credentials/{ride_id}")
async def get_driver_credentials(
    ride_id: str,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SafetyAdvancedService(db)
    return await service.get_driver_credentials(ride_id)


# Feature 132: Vehicle Registration Verification Display
@router.get("/vehicle-credentials/{ride_id}")
async def get_vehicle_credentials(
    ride_id: str,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SafetyAdvancedService(db)
    return await service.get_vehicle_credentials(ride_id)


# Feature 133: Safety Center with Local Emergency Numbers
@router.get("/safety-center")
async def get_safety_center(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SafetyAdvancedService(db)
    return await service.get_safety_center_info(current_user)


# Feature 134: Route Anomaly Detection Check
@router.post("/anomaly/route")
async def check_route_anomaly(
    payload: RouteAnomalyCheckPayload,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SafetyAdvancedService(db)
    return await service.check_route_anomaly(payload.ride_id, payload.current_lat, payload.current_lng)


# Feature 135: Unexpected Stop Detection Check
@router.post("/anomaly/stop")
async def check_unexpected_stop(
    payload: UnexpectedStopCheckPayload,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SafetyAdvancedService(db)
    return await service.check_unexpected_stop(
        payload.ride_id,
        payload.current_lat,
        payload.current_lng,
        payload.stationary_duration_seconds
    )


# Feature 136 & 137: Audio Recording Consent & Status
@router.post("/recording/consent")
async def update_recording_consent(
    payload: RecordingConsentPayload,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SafetyAdvancedService(db)
    return await service.update_recording_consent(
        payload.ride_id,
        current_user,
        payload.role,
        payload.consented
    )


@router.get("/recording/status/{ride_id}")
async def get_recording_status(
    ride_id: str,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SafetyAdvancedService(db)
    return await service.get_recording_status(ride_id)


# Feature 138 & 139: Incident Reporting (Customer & Driver)
@router.post("/incidents", status_code=status.HTTP_201_CREATED)
async def lodge_incident_report(
    payload: IncidentReportPayload,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SafetyAdvancedService(db)
    return await service.lodge_incident_report(
        current_user=current_user,
        reporter_role=payload.role,
        data=payload.model_dump()
    )


# Feature 140: Incident Follow-up Tracking
@router.get("/incidents/{ticket_number}")
async def get_incident_tracking(
    ticket_number: str,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SafetyAdvancedService(db)
    return await service.get_incident_tracking(ticket_number, current_user)


# Feature 141: Block Driver
@router.post("/drivers/block")
async def block_driver(
    payload: DriverBlockPayload,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SafetyAdvancedService(db)
    return await service.block_driver(
        customer_user=current_user,
        driver_id=payload.driver_id,
        ride_id=payload.ride_id,
        reason=payload.reason,
        report_incident=payload.report_incident,
        incident_description=payload.incident_description
    )


# Feature 142: Report Unsafe Vehicle Condition
@router.post("/incidents/unsafe-vehicle")
async def report_unsafe_vehicle(
    payload: UnsafeVehiclePayload,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SafetyAdvancedService(db)
    return await service.report_unsafe_vehicle(
        current_user=current_user,
        ride_id=payload.ride_id,
        defect_type=payload.defect_type,
        description=payload.description
    )


# Feature 143: Report Harassment or Misconduct
@router.post("/incidents/harassment")
async def report_harassment(
    payload: HarassmentReportPayload,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SafetyAdvancedService(db)
    return await service.report_harassment(
        current_user=current_user,
        ride_id=payload.ride_id,
        perpetrator_role=payload.perpetrator_role,
        description=payload.description,
        request_supervisor_call=payload.request_supervisor_call
    )


# Feature 144: Notify Trusted Contacts on Trip Start
@router.post("/notify-contacts/start/{ride_id}")
async def notify_contacts_trip_start(
    ride_id: str,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SafetyAdvancedService(db)
    return await service.notify_trusted_contacts_on_trip_start(ride_id)


# Feature 145: Notify Trusted Contacts on Arrival
@router.post("/notify-contacts/arrival/{ride_id}")
async def notify_contacts_arrival(
    ride_id: str,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SafetyAdvancedService(db)
    return await service.notify_trusted_contacts_on_arrival(ride_id)


# Feature 146: PIN Confirmation for High-Risk Pickups
@router.get("/high-risk-status/{ride_id}")
async def check_high_risk_status(
    ride_id: str,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SafetyAdvancedService(db)
    return await service.check_and_flag_high_risk_pickup(ride_id)


@router.post("/verify-pickup-pin")
async def verify_pickup_pin(
    payload: VerifyPickupPinPayload,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SafetyAdvancedService(db)
    return await service.verify_pickup_pin(payload.ride_id, payload.pin, current_user)


# Feature 147: Safe Pickup Zone Suggestions
@router.get("/safe-zones")
async def get_safe_zones(
    latitude: float = Query(..., ge=-90.0, le=90.0),
    longitude: float = Query(..., ge=-180.0, le=180.0),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SafetyAdvancedService(db)
    return await service.get_safe_pickup_zones(latitude, longitude)


# Feature 148: Geofenced Restricted Pickup Warning
@router.get("/restricted-zone-check")
async def check_restricted_pickup_warning(
    latitude: float = Query(..., ge=-90.0, le=90.0),
    longitude: float = Query(..., ge=-180.0, le=180.0),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SafetyAdvancedService(db)
    return await service.check_restricted_pickup_warning(latitude, longitude)


# Feature 149: Safety Education Cards
@router.get("/education-cards")
async def get_safety_education_cards(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SafetyAdvancedService(db)
    return await service.get_safety_education_cards()


# Feature 150: Safety Case Audit Trail for Authorized Staff (Admin)
@router.get("/admin/cases")
async def list_safety_cases_admin(
    status_filter: Optional[str] = Query(None),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SafetyAdvancedService(db)
    return await service.list_safety_cases_for_admin(current_user, status_filter)


@router.post("/admin/cases/{ticket_number}/resolve")
async def resolve_safety_case_admin(
    ticket_number: str,
    payload: AdminResolveCasePayload,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SafetyAdvancedService(db)
    return await service.resolve_safety_case(
        ticket_number=ticket_number,
        resolution_notes=payload.resolution_notes,
        follow_up_action=payload.follow_up_action,
        current_user=current_user
    )
