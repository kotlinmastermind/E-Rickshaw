from typing import Optional, List, Dict, Any
from fastapi import APIRouter, Depends, HTTPException, status, UploadFile, File, Form, Query
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession

from backend.infrastructure.database.session import get_db
from backend.api.deps import get_current_driver, get_current_admin
from backend.domain.models import User
from backend.application.services.driver_onboarding_service import DriverOnboardingService

router = APIRouter(prefix="/driver-onboarding", tags=["Driver Onboarding & Fleet Compliance"])

# Pydantic Request Models
class IdentityVerificationPayload(BaseModel):
    id_type: str = Field("AADHAAR", pattern="^(AADHAAR|PASSPORT|DRIVING_LICENSE|VOTER_ID)$")
    id_number: str = Field(..., min_length=6, max_length=32, pattern="^[A-Za-z0-9-]+$")
    selfie_notes: Optional[str] = Field(None, max_length=500)

class KnowledgeAssessmentPayload(BaseModel):
    module_id: str
    selected_answers: List[int] = Field(..., json_schema_extra={"example": [1, 1]})

class VehicleInspectionPayload(BaseModel):
    vehicle_id: str
    checklist_results: Dict[str, str] = Field(..., json_schema_extra={"example": {"EXT_PAINT": "PASS", "TIRES_RIMS": "PASS"}})
    photos: Dict[str, str] = Field(..., json_schema_extra={"example": {"photo_front_url": "https://assets.luxurychauffeur.com/vehicles/front.jpg"}})
    notes: Optional[str] = None

class AvailabilityCalendarUpdatePayload(BaseModel):
    schedules: List[Dict[str, Any]]

class ZonesAndCategoriesPayload(BaseModel):
    operating_zones: List[str] = Field(..., min_length=1, max_length=10, json_schema_extra={"example": ["AIRPORT_CORRIDOR", "SOUTH_MUMBAI"]})
    vehicle_categories: List[str] = Field(..., min_length=1, max_length=10, json_schema_extra={"example": ["EXECUTIVE_SEDAN", "PRESTIGE_SUV"]})

class ServicePreferencesPayload(BaseModel):
    languages_spoken: str = Field("English, Hindi, Marathi", max_length=128, json_schema_extra={"example": "English, Hindi, Marathi"})
    service_etiquette_level: str = Field("Black Sovereign Executive", min_length=2, max_length=64, json_schema_extra={"example": "Black Sovereign Executive"})
    cabin_music_preference: str = Field("Classical & Soft Jazz", max_length=64, json_schema_extra={"example": "Classical & Soft Jazz"})
    cabin_temp_celsius: int = Field(21, ge=16, le=28, json_schema_extra={"example": 21})

class EmergencyContactPayload(BaseModel):
    name: str = Field(..., min_length=2, max_length=128, json_schema_extra={"example": "Ananya Varma"})
    phone: str = Field(..., min_length=8, max_length=20, pattern=r"^\+?[0-9][0-9\s-]{6,18}[0-9]$", json_schema_extra={"example": "+91 98200 12345"})
    relationship: str = Field("Emergency contact", max_length=64, json_schema_extra={"example": "Spouse"})

class RecoveryInitiatePayload(BaseModel):
    phone_number: str = Field(..., json_schema_extra={"example": "+919876500001"})

class RecoveryVerifyPayload(BaseModel):
    phone_number: str = Field(..., json_schema_extra={"example": "+919876500001"})
    recovery_code: str = Field(..., json_schema_extra={"example": "123456"})

class OnboardingTicketPayload(BaseModel):
    subject: str = Field(..., min_length=4, max_length=120, json_schema_extra={"example": "License badge endorsement query"})
    message: str = Field(..., min_length=10, max_length=2000, json_schema_extra={"example": "Uploaded Maharashtra transport badge with high resolution."})

class OnboardingTicketReviewPayload(BaseModel):
    status: str = Field(..., pattern="^(OPEN|IN_REVIEW|RESOLVED)$")
    admin_response: str = Field(..., min_length=5, max_length=2000)


# 201, 216, 224: Checklist, Completeness Meter & Application Tracker
@router.get("/overview")
async def get_onboarding_overview(
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    service = DriverOnboardingService(db)
    return await service.get_onboarding_overview(user.id)

# 202: Driver Identity Verification Status
@router.get("/identity")
async def get_identity_status(
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    service = DriverOnboardingService(db)
    return await service.get_identity_status(user.id)

# 202 & 223: Submit Identity Verification or Re-verification
@router.post("/identity/verify")
async def submit_identity_verification(
    payload: IdentityVerificationPayload,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    service = DriverOnboardingService(db)
    return await service.submit_identity_verification(
        user_id=user.id,
        id_type=payload.id_type,
        id_number=payload.id_number,
        selfie_notes=payload.selfie_notes
    )

# 203: Document Expiry Reminders
@router.get("/documents/expiries")
async def get_document_expiry_reminders(
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    service = DriverOnboardingService(db)
    return await service.get_document_expiry_reminders(user.id)

@router.get("/documents")
async def get_onboarding_documents(user: User = Depends(get_current_driver), db: AsyncSession = Depends(get_db)):
    return await DriverOnboardingService(db).get_onboarding_documents(user.id)

# 204, 205, 206: Document Resubmission with rejection reason, guided capture, automated quality checks
@router.post("/documents/resubmit")
async def resubmit_document(
    document_type: str = Form(..., description="DRIVING_LICENSE, VEHICLE_REGISTRATION, INSURANCE, IDENTITY_PROOF"),
    guidance_confirmed: bool = Form(True),
    file: UploadFile = File(...),
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    service = DriverOnboardingService(db)
    return await service.resubmit_document(
        user_id=user.id,
        document_type=document_type,
        file=file,
        guidance_confirmed=guidance_confirmed
    )

# 207: Background Verification Status
@router.get("/background-check")
async def get_background_check_status(
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    service = DriverOnboardingService(db)
    return await service.get_background_check_details(user.id)

# 208: Driver Training Lessons
@router.get("/training/curriculum")
async def get_training_curriculum(
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    service = DriverOnboardingService(db)
    return await service.get_training_curriculum(user.id)

# 209: Driver Knowledge Assessment
@router.post("/training/assessment")
async def submit_training_assessment(
    payload: KnowledgeAssessmentPayload,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    service = DriverOnboardingService(db)
    return await service.submit_assessment(
        user_id=user.id,
        module_id=payload.module_id,
        selected_answers=payload.selected_answers
    )

# 210: Vehicle Inspection Checklist Template
@router.get("/vehicle-inspection/template")
async def get_inspection_template(
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    service = DriverOnboardingService(db)
    return await service.get_inspection_template()

@router.post("/vehicle-inspection/{vehicle_id}/photos/{photo_type}")
async def upload_vehicle_inspection_photo(
    vehicle_id: str,
    photo_type: str,
    file: UploadFile = File(...),
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db),
):
    return await DriverOnboardingService(db).upload_vehicle_inspection_photo(user.id, vehicle_id, photo_type, file)

# 210 & 211: Vehicle Inspection & 6-Angle Photo Submission
@router.post("/vehicle-inspection")
async def submit_vehicle_inspection(
    payload: VehicleInspectionPayload,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    service = DriverOnboardingService(db)
    return await service.submit_vehicle_inspection(
        user_id=user.id,
        vehicle_id=payload.vehicle_id,
        checklist_results=payload.checklist_results,
        photos=payload.photos,
        notes=payload.notes
    )

# 212: Multiple Vehicle Management
@router.get("/vehicles")
async def get_driver_vehicles(
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    service = DriverOnboardingService(db)
    return await service.get_driver_vehicles(user.id)

@router.post("/vehicles/{vehicle_id}/activate")
async def activate_vehicle(
    vehicle_id: str,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    service = DriverOnboardingService(db)
    return await service.switch_active_vehicle(user.id, vehicle_id)

# 213: Driver Availability Calendar
@router.get("/schedule")
async def get_driver_schedule(
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    service = DriverOnboardingService(db)
    return await service.get_availability_calendar(user.id)

@router.put("/schedule")
async def update_driver_schedule(
    payload: AvailabilityCalendarUpdatePayload,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    service = DriverOnboardingService(db)
    return await service.update_availability_calendar(user.id, payload.schedules)

# 214 & 215: Preferred Operating Zones & Vehicle Categories
@router.put("/zones-and-categories")
async def update_zones_and_categories(
    payload: ZonesAndCategoriesPayload,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    service = DriverOnboardingService(db)
    return await service.update_zones_and_categories(
        user_id=user.id,
        zones=payload.operating_zones,
        categories=payload.vehicle_categories
    )

# 217: Driver Public Profile Preview
@router.get("/public-preview")
async def get_public_profile_preview(
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    service = DriverOnboardingService(db)
    return await service.get_public_profile_preview(user.id)

# 218: Driver Language and Service Preferences
@router.put("/service-preferences")
async def update_service_preferences(
    payload: ServicePreferencesPayload,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    service = DriverOnboardingService(db)
    return await service.update_service_preferences(
        user_id=user.id,
        languages=payload.languages_spoken,
        etiquette_level=payload.service_etiquette_level,
        music=payload.cabin_music_preference,
        temp_celsius=payload.cabin_temp_celsius
    )

# 219: Emergency Contact Management
@router.put("/emergency-contact")
async def update_emergency_contact(
    payload: EmergencyContactPayload,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    service = DriverOnboardingService(db)
    return await service.update_emergency_contact(
        user_id=user.id,
        name=payload.name,
        phone=payload.phone,
        relationship=payload.relationship
    )

# 220: Driver Account Recovery
@router.post("/account-recovery/initiate")
async def initiate_account_recovery(
    payload: RecoveryInitiatePayload,
    db: AsyncSession = Depends(get_db)
):
    service = DriverOnboardingService(db)
    return await service.initiate_account_recovery(payload.phone_number)

@router.post("/account-recovery/verify")
async def verify_account_recovery(
    payload: RecoveryVerifyPayload,
    db: AsyncSession = Depends(get_db)
):
    service = DriverOnboardingService(db)
    return await service.verify_account_recovery(payload.phone_number, payload.recovery_code)

# 221: Consent & Policy Acknowledgments
@router.post("/consent/acknowledge")
async def acknowledge_policies(
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    service = DriverOnboardingService(db)
    return await service.acknowledge_policies(user.id)

# 222: Driver Account Deletion Request
@router.post("/account-deletion/request")
async def request_account_deletion(
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    service = DriverOnboardingService(db)
    return await service.request_account_deletion(user.id)

# 225: Driver Onboarding Support Ticket
@router.post("/tickets")
async def create_onboarding_ticket(
    payload: OnboardingTicketPayload,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    service = DriverOnboardingService(db)
    return await service.create_onboarding_ticket(
        user_id=user.id,
        subject=payload.subject,
        message=payload.message
    )

@router.get("/tickets")
async def get_onboarding_tickets(
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    service = DriverOnboardingService(db)
    return await service.get_onboarding_tickets(user.id)

@router.get("/admin/tickets")
async def list_driver_onboarding_tickets(user: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await DriverOnboardingService(db).get_admin_onboarding_tickets()

@router.patch("/admin/tickets/{ticket_id}")
async def review_driver_onboarding_ticket(ticket_id: str, payload: OnboardingTicketReviewPayload, user: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await DriverOnboardingService(db).review_onboarding_ticket(ticket_id, payload.status, payload.admin_response)
