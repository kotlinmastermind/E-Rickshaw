"""Authenticated driver safety, coaching, and support surfaces."""
import json
import secrets
from datetime import datetime, timezone, timedelta
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, status, UploadFile, File
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from backend.api.deps import get_current_user
from backend.domain.models import (DriverProfile, DriverSafetyPreferences, DriverFatigueCheck,
    DriverRating, DriverRatingAppeal, IncidentReport, Ride, SupportTicket, User, Vehicle,
    DriverVehicleAssignment, DriverVehicleInspection, HelpArticle, SupportMessage, IncidentEvidence, Notification)
from backend.infrastructure.database.session import get_db
from backend.integrations.storage.provider import get_storage_provider
from backend.application.services.safety_advanced_service import SafetyAdvancedService

router = APIRouter(prefix="/drivers/safety", tags=["Driver safety & support"])

async def driver_for(user: User, db: AsyncSession) -> DriverProfile:
    if user.role.lower() != "driver":
        raise HTTPException(status_code=403, detail="Driver account required")
    result = await db.execute(select(DriverProfile).where(DriverProfile.user_id == user.id))
    driver = result.scalar_one_or_none()
    if not driver:
        raise HTTPException(status_code=404, detail="Driver profile not found")
    return driver

class FatiguePayload(BaseModel):
    result: str = Field(pattern="^(FIT|NEEDS_REST|UNFIT)$")
    ride_id: Optional[str] = None
    note: Optional[str] = Field(default=None, max_length=1000)

class PreferencesPayload(BaseModel):
    share_trip_location_with_customer: bool
    privacy_notice_acknowledged: bool

class IncidentPayload(BaseModel):
    ride_id: Optional[str] = None
    category: str = Field(min_length=2, max_length=64)
    severity: str = Field(default="MEDIUM", pattern="^(LOW|MEDIUM|HIGH|CRITICAL)$")
    description: str = Field(min_length=10, max_length=5000)

class TicketPayload(BaseModel):
    subject: str = Field(min_length=4, max_length=255)
    message: str = Field(min_length=10, max_length=5000)
    category: str = Field(default="SAFETY", max_length=50)

class CallbackPayload(BaseModel):
    subject: str = Field(min_length=4, max_length=255)
    preferred_time_window: str = Field(default="WITHIN_1_HOUR", pattern="^(IMMEDIATE|WITHIN_1_HOUR|EVENING_WINDOW)$")

class AppealPayload(BaseModel):
    reason: str = Field(min_length=20, max_length=2000)

@router.get("/overview")
async def overview(user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    driver = await driver_for(user, db)
    prefs = (await db.execute(select(DriverSafetyPreferences).where(DriverSafetyPreferences.driver_id == driver.id))).scalar_one_or_none()
    ratings = (await db.execute(select(DriverRating).where(DriverRating.driver_id == driver.id).order_by(DriverRating.created_at.desc()).limit(50))).scalars().all()
    tickets = (await db.execute(select(SupportTicket).where(SupportTicket.user_id == user.id).order_by(SupportTicket.created_at.desc()).limit(30))).scalars().all()
    appeals = (await db.execute(select(DriverRatingAppeal).where(DriverRatingAppeal.driver_id == driver.id).order_by(DriverRatingAppeal.created_at.desc()).limit(30))).scalars().all()
    vehicles = (await db.execute(select(Vehicle).join(DriverVehicleAssignment, DriverVehicleAssignment.vehicle_id == Vehicle.id).where(DriverVehicleAssignment.driver_id == driver.id, DriverVehicleAssignment.is_active == True))).scalars().all()
    vehicle_ids = [v.id for v in vehicles]
    inspections = (await db.execute(select(DriverVehicleInspection).where(DriverVehicleInspection.driver_id == driver.id, DriverVehicleInspection.vehicle_id.in_(vehicle_ids)).order_by(DriverVehicleInspection.inspected_at.desc()))).scalars().all() if vehicle_ids else []
    latest_inspection = {}
    for inspection in inspections:
        latest_inspection.setdefault(inspection.vehicle_id, inspection)
    incidents = (await db.execute(select(IncidentReport).where(IncidentReport.reporter_user_id == user.id).order_by(IncidentReport.created_at.desc()).limit(30))).scalars().all()
    notifications = (await db.execute(select(Notification).where(Notification.user_id == user.id, Notification.event_type == "SAFETY_CASE_OUTCOME").order_by(Notification.created_at.desc()).limit(20))).scalars().all()
    return {
        "safety_preferences": {"share_trip_location_with_customer": prefs.share_trip_location_with_customer if prefs else False,
            "privacy_notice_acknowledged": bool(prefs and prefs.privacy_notice_acknowledged_at),
            "notice": "Trip location is shared only while an active trip is tracked and only when enabled. This setting does not control emergency services."},
        "fatigue_check_required": True,
        "ratings": [{"id": r.id, "rating": r.rating, "cleanliness": r.cleanliness_score, "comfort": r.comfort_score,
            "service_quality": r.service_quality_score, "navigation": r.navigation_score, "created_at": r.created_at.isoformat(),
            "feedback": r.feedback} for r in ratings],
        "rating_average": round(sum(r.rating for r in ratings) / len(ratings), 2) if ratings else None,
        "rating_count": len(ratings),
        "coaching": _coaching(ratings),
        "tickets": [{"id": t.id, "ticket_number": t.ticket_number, "subject": t.subject, "status": t.status, "category": t.category, "created_at": t.created_at.isoformat() if t.created_at else None} for t in tickets],
        "incident_cases": [{"ticket_number": r.ticket_number, "category": r.category, "severity": r.severity, "status": r.status, "created_at": r.created_at.isoformat() if r.created_at else None, "resolved_at": r.resolved_at.isoformat() if r.resolved_at else None, "resolution_notes": r.resolution_notes, "follow_up_action": r.follow_up_action} for r in incidents],
        "safety_outcome_notifications": [{"title": n.title, "body": n.body, "is_read": n.is_read, "created_at": n.created_at.isoformat() if n.created_at else None} for n in notifications],
        "rating_appeals": [{"rating_id": a.rating_id, "status": a.status, "created_at": a.created_at.isoformat(), "reviewer_notes": a.reviewer_notes} for a in appeals],
        "maintenance": [{"vehicle_id": v.id, "license_plate": v.license_plate,
            "puc_expiry_date": v.puc_expiry_date.isoformat() if v.puc_expiry_date else None,
            "fitness_expiry_date": v.fitness_expiry_date.isoformat() if v.fitness_expiry_date else None} for v in vehicles],
        "inspections": [{"vehicle_id": v.id, "license_plate": v.license_plate,
            "last_submitted_at": latest_inspection[v.id].inspected_at.isoformat() if v.id in latest_inspection else None,
            "last_status": latest_inspection[v.id].inspection_status if v.id in latest_inspection else None,
            "pre_trip_check_due": v.id not in latest_inspection or latest_inspection[v.id].inspected_at.date() != datetime.now(timezone.utc).date()} for v in vehicles],
        "inspection_interval_status": "DAILY_PRE_TRIP_REMINDER",
        "inspection_due_alerts": [{"vehicle_id": v.id, "license_plate": v.license_plate, "reason": "Submit the vehicle pre-trip inspection before the next shift."} for v in vehicles if v.id not in latest_inspection or latest_inspection[v.id].inspected_at.date() != datetime.now(timezone.utc).date()],
        "weather": {"provider_configured": False, "status": "NOT_CONFIGURED", "alerts": [], "guidance": "Check official local weather and road authority alerts before setting out."},
        "emergency": {"number": "112", "dispatch_delivery": "NOT_CONFIGURED", "message": "For immediate danger, call 112. An in-app safety report is not an emergency dispatch."}
    }

class CheckinPayload(BaseModel):
    ride_id: str
    minutes_from_now: int = Field(default=30, ge=1, le=480)

class CheckinResponsePayload(BaseModel):
    ride_id: str
    response_type: str = Field(pattern="^(ALL_GOOD|NEED_HELP|SNOOZE_10_MIN)$")

class EmergencyAlertPayload(BaseModel):
    ride_id: str
    notes: Optional[str] = Field(default=None, max_length=1000)

@router.post("/checkin/schedule")
async def schedule_checkin(payload: CheckinPayload, user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    driver = await driver_for(user, db)
    ride = await db.get(Ride, payload.ride_id)
    if not ride or ride.driver_id != driver.id:
        raise HTTPException(status_code=404, detail="Active ride not found")
    return await SafetyAdvancedService(db).schedule_safety_checkin(ride.id, payload.minutes_from_now, user)

@router.post("/checkin/respond")
async def respond_checkin(payload: CheckinResponsePayload, user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    driver = await driver_for(user, db)
    ride = await db.get(Ride, payload.ride_id)
    if not ride or ride.driver_id != driver.id:
        raise HTTPException(status_code=404, detail="Active ride not found")
    return await SafetyAdvancedService(db).respond_to_safety_checkin(ride.id, user, payload.response_type)

@router.post("/emergency-alert", status_code=201)
async def emergency_alert(payload: EmergencyAlertPayload, user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    driver = await driver_for(user, db)
    ride = await db.get(Ride, payload.ride_id)
    if not ride or ride.driver_id != driver.id:
        raise HTTPException(status_code=404, detail="Active ride not found")
    if ride.pickup_latitude is None or ride.pickup_longitude is None:
        raise HTTPException(status_code=409, detail="Trip location is unavailable; call 112 directly")
    result = await SafetyAdvancedService(db).trigger_emergency_sos(ride.id, user, ride.pickup_latitude, ride.pickup_longitude, "DRIVER_EMERGENCY", payload.notes)
    result["location_source"] = "TRIP_PICKUP_REFERENCE"
    result["dispatch_delivery"] = "NOT_CONFIGURED"
    result["message"] = "Safety alert recorded with the trip pickup reference. Emergency services were not contacted; call 112 now if needed."
    return result

def _coaching(ratings):
    if not ratings:
        return ["Complete trips to receive feedback-based coaching recommendations."]
    recent = ratings[:10]
    labels = {"cleanliness_score": "cabin cleanliness", "comfort_score": "ride comfort", "service_quality_score": "service etiquette", "navigation_score": "navigation"}
    averages = {k: sum(getattr(r, k) for r in recent) / len(recent) for k in labels}
    weak = sorted(averages, key=averages.get)
    return [f"Focus on {labels[k]}: recent category average {averages[k]:.1f}/5." for k in weak[:2] if averages[k] < 4.5] or ["Recent feedback is consistently strong. Maintain your current service standards."]

@router.post("/fatigue-check", status_code=201)
async def fatigue(payload: FatiguePayload, user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    driver = await driver_for(user, db)
    if payload.ride_id:
        ride = await db.get(Ride, payload.ride_id)
        if not ride or ride.driver_id != driver.id:
            raise HTTPException(status_code=404, detail="Active ride not found")
    record = DriverFatigueCheck(driver_id=driver.id, ride_id=payload.ride_id, result=payload.result, note=payload.note)
    db.add(record); await db.commit()
    guidance = "You are responsible for deciding whether it is safe to drive."
    if record.result in {"NEEDS_REST", "UNFIT"}:
        guidance = "Do not accept trips while tired or impaired. Go offline, park safely, and rest. Call 112 if anyone is in immediate danger."
    return {"id": record.id, "result": record.result, "created_at": record.created_at.isoformat(), "guidance": guidance}

@router.put("/preferences")
async def preferences(payload: PreferencesPayload, user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    driver = await driver_for(user, db)
    if not payload.privacy_notice_acknowledged:
        raise HTTPException(status_code=400, detail="Review and acknowledge the tracking privacy notice before changing this setting")
    pref = (await db.execute(select(DriverSafetyPreferences).where(DriverSafetyPreferences.driver_id == driver.id))).scalar_one_or_none()
    if not pref:
        pref = DriverSafetyPreferences(driver_id=driver.id); db.add(pref)
    pref.share_trip_location_with_customer = payload.share_trip_location_with_customer
    pref.privacy_notice_acknowledged_at = datetime.now(timezone.utc)
    await db.commit()
    return {"share_trip_location_with_customer": pref.share_trip_location_with_customer, "privacy_notice_acknowledged": True}

@router.post("/incidents", status_code=201)
async def incident(payload: IncidentPayload, user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    driver = await driver_for(user, db)
    if payload.category in {"UNSAFE_PICKUP_LOCATION", "CUSTOMER_BEHAVIOR", "PASSENGER_NO_SHOW_DISPUTE"} and not payload.ride_id:
        raise HTTPException(status_code=400, detail="Select the related ride before filing this report")
    if payload.ride_id:
        ride = await db.get(Ride, payload.ride_id)
        if not ride or ride.driver_id != driver.id:
            raise HTTPException(status_code=404, detail="Ride not found")
    report = IncidentReport(ticket_number=f"INC-{datetime.now(timezone.utc):%Y%m%d}-{secrets.token_hex(3).upper()}",
        ride_id=payload.ride_id, reporter_user_id=user.id, reporter_role="DRIVER", category=payload.category,
        severity=payload.severity, description=payload.description, status="OPEN")
    db.add(report); await db.commit()
    return {"ticket_number": report.ticket_number, "status": report.status, "message": "Report saved for review. For immediate danger call 112."}

@router.post("/incidents/{ticket_number}/evidence", status_code=201)
async def upload_incident_evidence(ticket_number: str, file: UploadFile = File(...),
        user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    await driver_for(user, db)
    report = (await db.execute(select(IncidentReport).where(IncidentReport.ticket_number == ticket_number))).scalar_one_or_none()
    if not report:
        raise HTTPException(status_code=404, detail="Incident report not found")
    if report.reporter_user_id != user.id:
        raise HTTPException(status_code=403, detail="Only the report owner may attach evidence")
    path, mime, size, digest = await get_storage_provider().save_driver_document(user.id, f"incident-{report.id[:8]}", file)
    evidence = IncidentEvidence(incident_report_id=report.id, uploaded_by_user_id=user.id,
        storage_path=path, mime_type=mime, file_size_bytes=size, file_hash_sha256=digest)
    db.add(evidence); await db.commit()
    return {"id": evidence.id, "mime_type": mime, "size_bytes": size, "message": "Evidence stored in private incident storage."}

@router.post("/tickets", status_code=201)
async def create_ticket(payload: TicketPayload, user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    await driver_for(user, db)
    ticket = SupportTicket(ticket_number=f"DRV-{secrets.token_hex(4).upper()}", user_id=user.id,
        category=payload.category, subject=payload.subject, priority="MEDIUM")
    db.add(ticket); await db.flush()
    db.add(SupportMessage(ticket_id=ticket.id, sender_user_id=user.id, sender_role="DRIVER", message=payload.message))
    await db.commit()
    return {"ticket_number": ticket.ticket_number, "id": ticket.id, "status": ticket.status, "message": "Support ticket created."}

@router.post("/callback", status_code=201)
async def callback(payload: CallbackPayload, user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    await driver_for(user, db)
    from backend.domain.models import SupportCallbackRequest
    callback_request = SupportCallbackRequest(user_id=user.id, phone_number=user.phone_number,
        preferred_time_window=payload.preferred_time_window, subject=payload.subject, status="PENDING")
    db.add(callback_request); await db.commit()
    return {"id": callback_request.id, "status": "PENDING", "delivery": "REQUEST_RECORDED", "message": "Callback request recorded; a callback provider is not configured, so contact is not yet confirmed."}

@router.post("/ratings/{rating_id}/appeal", status_code=201)
async def appeal(rating_id: str, payload: AppealPayload, user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    driver = await driver_for(user, db)
    rating = await db.get(DriverRating, rating_id)
    if not rating or rating.driver_id != driver.id:
        raise HTTPException(status_code=404, detail="Rating not found")
    existing = (await db.execute(select(DriverRatingAppeal).where(DriverRatingAppeal.rating_id == rating_id))).scalar_one_or_none()
    if existing:
        raise HTTPException(status_code=409, detail="An appeal already exists for this rating")
    appeal = DriverRatingAppeal(rating_id=rating_id, driver_id=driver.id, reason=payload.reason)
    db.add(appeal); await db.commit()
    return {"id": appeal.id, "status": appeal.status, "message": "Appeal submitted for review."}

@router.get("/articles")
async def articles(user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    await driver_for(user, db)
    result = await db.execute(select(HelpArticle).where(HelpArticle.is_published == True).order_by(HelpArticle.updated_at.desc()).limit(50))
    rows = result.scalars().all()
    return [{"id": a.id, "title": a.title, "content": a.content_markdown, "category": a.category} for a in rows if "DRIVER" in ((a.category or "") + " " + (a.context_tags or "")).upper()]

