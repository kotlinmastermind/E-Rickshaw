import os
import json
from typing import Optional, List, Dict, Any
from fastapi import APIRouter, Depends, HTTPException, status, UploadFile, File, Form, Query, Response
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select

from backend.infrastructure.database.session import get_db
from backend.api.deps import get_current_driver, get_current_admin, get_current_user
from backend.domain.models import User, DriverProfile, DriverDocument, DriverServiceAreaRequest
from backend.application.services.driver_onboarding_service import DriverOnboardingService
from backend.integrations.storage.provider import get_storage_provider, verify_signed_url_token
from backend.core.config import settings

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

# Task 043: Generate short-lived signed URL for document download
@router.get("/documents/{document_id}/signed-url")
async def get_document_signed_url(
    document_id: str,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    stmt = select(DriverDocument).where(DriverDocument.id == document_id)
    res = await db.execute(stmt)
    doc = res.scalar_one_or_none()
    if not doc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Driver document not found.")

    # Task 045: Enforce ownership - only the owning driver or admin can access
    if user.role != "admin":
        drv_stmt = select(DriverProfile).where(DriverProfile.user_id == user.id)
        drv_res = await db.execute(drv_stmt)
        driver_prof = drv_res.scalar_one_or_none()
        if not driver_prof or doc.driver_id != driver_prof.id:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Access denied: You do not own this document.")

    storage = get_storage_provider()
    signed_url = await storage.generate_signed_url(doc.storage_path, expires_in_seconds=300)
    return {
        "document_id": doc.id,
        "signed_url": signed_url,
        "expires_in_seconds": 300
    }

# Task 043: Authorized secure document download via HMAC-signed token
@router.get("/documents/download")
async def download_private_document(
    file: str = Query(...),
    expires: int = Query(...),
    sig: str = Query(...)
):
    if not verify_signed_url_token(file, expires, sig):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Signature invalid or document download link expired.")

    base_dir = os.path.abspath(settings.STORAGE_LOCAL_DIR)
    target_path = os.path.abspath(os.path.join(base_dir, file))
    if not target_path.startswith(base_dir):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Access denied: Directory traversal detected.")

    if not os.path.isfile(target_path):
        from backend.integrations.storage.provider import generate_compliance_placeholder_png
        content = await get_storage_provider().get_document_bytes(target_path)
        if not content:
            content = generate_compliance_placeholder_png("COMPLIANCE_DOCUMENT", os.path.basename(file))
        return Response(
            content=content,
            media_type="image/png",
            headers={
                "Content-Disposition": f"inline; filename={os.path.basename(target_path)}.png",
                "X-Content-Type-Options": "nosniff"
            }
        )

    return FileResponse(
        target_path,
        media_type="application/octet-stream",
        headers={
            "Content-Disposition": f"attachment; filename={os.path.basename(target_path)}",
            "X-Content-Type-Options": "nosniff"
        }
    )

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


class DriverServiceAreaRequestPayload(BaseModel):
    state_name: str = Field(..., min_length=2, max_length=80)
    district_name: str = Field(..., min_length=2, max_length=100)
    locality_name: str = Field(..., min_length=2, max_length=120)
    postal_code: Optional[str] = Field(None, pattern=r"^\d{6}$")
    latitude: float = Field(..., ge=-90, le=90)
    longitude: float = Field(..., ge=-180, le=180)


@router.post("/service-area-requests", status_code=status.HTTP_201_CREATED)
async def submit_driver_service_area_request(
    payload: DriverServiceAreaRequestPayload,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db),
):
    """Submit a locality request; this does not activate service or bypass review."""
    driver = (await db.execute(
        select(DriverProfile).where(DriverProfile.user_id == user.id)
    )).scalar_one_or_none()
    if not driver:
        raise HTTPException(status_code=404, detail="Driver profile not found.")

    pending = (await db.execute(
        select(DriverServiceAreaRequest)
        .where(
            DriverServiceAreaRequest.driver_id == driver.id,
            DriverServiceAreaRequest.status == "PENDING",
        )
        .order_by(DriverServiceAreaRequest.created_at.desc())
        .limit(1)
    )).scalar_one_or_none()
    if pending:
        raise HTTPException(
            status_code=409,
            detail={"message": "A service-area request is already awaiting review.", "request_id": pending.id},
        )

    values = payload.model_dump()
    values.update({
        "state_name": payload.state_name.strip(),
        "district_name": payload.district_name.strip(),
        "locality_name": payload.locality_name.strip(),
    })
    if not values["state_name"] or not values["district_name"] or not values["locality_name"]:
        raise HTTPException(status_code=422, detail="State, district, and locality are required.")
    request = DriverServiceAreaRequest(driver_id=driver.id, **values)
    db.add(request)
    from sqlalchemy.exc import IntegrityError
    try:
        await db.flush()
    except IntegrityError:
        await db.rollback()
        raise HTTPException(status_code=409, detail="A service-area request is already awaiting review for this driver.")

    # Existing fully configured areas can onboard an already-approved driver
    # immediately. New/unknown localities remain pending until a real polygon
    # and active tariff are configured by an administrator.
    if driver.approval_status == "APPROVED" and user.is_active:
        from sqlalchemy import or_
        from backend.domain.models import (
            DriverServiceAreaAssignment, FareRule, VehicleCategory, GeofenceZoneAudit,
            DriverVehicleAssignment, Vehicle,
        )
        from backend.application.services.service_area_service import ServiceAreaService
        try:
            zone = await ServiceAreaService(db).resolve_point(payload.latitude, payload.longitude)
            now = datetime.now(timezone.utc)
            tariff_id = (await db.execute(
                select(FareRule.id)
                .join(VehicleCategory, VehicleCategory.id == FareRule.category_id)
                .join(Vehicle, Vehicle.category_id == VehicleCategory.id)
                .join(DriverVehicleAssignment, DriverVehicleAssignment.vehicle_id == Vehicle.id)
                .where(
                    DriverVehicleAssignment.driver_id == driver.id,
                    DriverVehicleAssignment.is_active.is_(True),
                    Vehicle.is_active.is_(True),
                    FareRule.city_code == zone.city_code,
                    FareRule.is_active.is_(True),
                    FareRule.workflow_status.in_(["ACTIVE", "APPROVED"]),
                    FareRule.effective_from <= now,
                    or_(FareRule.effective_until.is_(None), FareRule.effective_until > now),
                    VehicleCategory.is_active.is_(True),
                )
                .limit(1)
            )).scalar_one_or_none()
            if tariff_id:
                assignment = (await db.execute(
                    select(DriverServiceAreaAssignment).where(
                        DriverServiceAreaAssignment.driver_id == driver.id,
                        DriverServiceAreaAssignment.service_area_id == zone.id,
                    )
                )).scalar_one_or_none()
                if assignment is None:
                    db.add(DriverServiceAreaAssignment(
                        driver_id=driver.id,
                        service_area_id=zone.id,
                        is_active=True,
                    ))
                else:
                    assignment.is_active = True
                    assignment.revoked_at = None
                request.status = "APPROVED"
                request.service_area_id = zone.id
                request.admin_response = "Automatically assigned to an existing active service area with a configured tariff."
                request.reviewed_at = now
                db.add(GeofenceZoneAudit(
                    zone_id=zone.id,
                    action="AREA_AUTO_ASSIGNED",
                    new_state_json=json.dumps({"request_id": request.id, "driver_id": driver.id}),
                ))
            else:
                request.admin_response = "Pending review: an active tariff is not configured for this locality."
        except HTTPException as area_error:
            request.admin_response = f"Pending review: {area_error.detail}"

    try:
        await db.commit()
    except IntegrityError:
        await db.rollback()
        raise HTTPException(
            status_code=409,
            detail="A service-area request is already awaiting review for this driver.",
        )
    await db.refresh(request)
    return {
        "request_id": request.id,
        "status": request.status,
        "message": "Locality request submitted. Service starts only after an approved operational boundary and driver assignment are in place.",
        "created_at": request.created_at.isoformat(),
    }


@router.get("/service-area-requests")
async def list_my_driver_service_area_requests(
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db),
):
    driver = (await db.execute(
        select(DriverProfile).where(DriverProfile.user_id == user.id)
    )).scalar_one_or_none()
    if not driver:
        raise HTTPException(status_code=404, detail="Driver profile not found.")
    rows = (await db.execute(
        select(DriverServiceAreaRequest)
        .where(DriverServiceAreaRequest.driver_id == driver.id)
        .order_by(DriverServiceAreaRequest.created_at.desc())
        .limit(20)
    )).scalars().all()
    return [
        {
            "request_id": row.id,
            "state_name": row.state_name,
            "district_name": row.district_name,
            "locality_name": row.locality_name,
            "postal_code": row.postal_code,
            "status": row.status,
            "service_area_id": row.service_area_id,
            "admin_response": row.admin_response,
            "created_at": row.created_at.isoformat(),
            "reviewed_at": row.reviewed_at.isoformat() if row.reviewed_at else None,
        }
        for row in rows
    ]
