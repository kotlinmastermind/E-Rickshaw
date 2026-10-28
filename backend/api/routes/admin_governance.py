from datetime import datetime, timezone
from pathlib import Path
from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException, Query, Request
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from backend.api.deps import get_current_admin
from backend.core.config import settings
from backend.domain.models import AdminRole, DriverDocument, User
from backend.infrastructure.database.session import get_db
from backend.infrastructure.security.crypto import decode_jwt_token
from backend.application.services.admin_governance_service import AdminGovernanceService

router = APIRouter(prefix="/admin", tags=["Admin Governance"])


class CustomerStatusPayload(BaseModel):
    is_active: bool
    reason: str = Field(min_length=10, max_length=1000)

class BulkCustomerStatusPayload(BaseModel):
    user_ids: List[str] = Field(min_length=1, max_length=50)
    is_active: bool
    reason: str = Field(min_length=10, max_length=1000)

class RolePayload(BaseModel):
    name: str = Field(min_length=3, max_length=80)
    description: str = Field(default="", max_length=255)
    permissions: List[str] = Field(min_length=1, max_length=40)

class AssignRolePayload(BaseModel):
    role_id: str

class DocumentReviewPayload(BaseModel):
    verification_status: str
    reason: str = Field(default="", max_length=1000)

class RideAssignmentPayload(BaseModel):
    driver_id: str
    reason: str = Field(min_length=10, max_length=1000)

class RideCancellationPayload(BaseModel):
    reason: str = Field(min_length=10, max_length=1000)

class FareDraftPayload(BaseModel):
    category_id: str
    base_fare_cents: int = Field(ge=0, le=100_000_000)
    per_km_cents: int = Field(ge=0, le=10_000_000)
    per_minute_cents: int = Field(ge=0, le=10_000_000)
    minimum_fare_cents: int = Field(ge=0, le=100_000_000)
    surge_multiplier: float = Field(default=1.0, ge=1.0, le=5.0)
    fare_cap_cents: Optional[int] = Field(default=None, ge=0, le=100_000_000)
    effective_from: datetime

class ApprovalReasonPayload(BaseModel):
    reason: str = Field(min_length=10, max_length=1000)

class ActivationPayload(BaseModel):
    effective_from: datetime

class CategoryPayload(BaseModel):
    code: str = Field(min_length=2, max_length=30, pattern="^[A-Za-z0-9_-]+$")
    name: str = Field(min_length=2, max_length=80)
    description: str = Field(default="", max_length=1000)
    capacity: int = Field(ge=1, le=24)
    sort_order: int = Field(default=10, ge=1, le=1000)
    is_active: bool = True

class NotePayload(BaseModel):
    note: str = Field(min_length=3, max_length=4000)

class CalendarPayload(BaseModel):
    title: str = Field(min_length=2, max_length=120)
    event_date: datetime
    event_type: str = Field(default="HOLIDAY", pattern="^(HOLIDAY|LOCAL_EVENT|SERVICE_NOTICE)$")
    zone_id: Optional[str] = None
    notes: Optional[str] = Field(default=None, max_length=500)

class GeofencePayload(BaseModel):
    name: str = Field(min_length=2, max_length=128)
    zone_type: str = Field(pattern="^(RESTRICTED|SERVICE_AREA|HIGH_DEMAND|SURGE_ZONE)$")
    coordinates: List[dict] = Field(min_length=3, max_length=200)
    surge_multiplier: float = Field(default=1.0, ge=1.0, le=5.0)
    is_active: bool = True

class GeoJsonImportPayload(BaseModel):
    type: str = Field(pattern="^FeatureCollection$")
    features: List[dict] = Field(min_length=1, max_length=50)

class SurgePreviewPayload(BaseModel):
    zone_id: Optional[str] = None
    category_id: Optional[str] = None
    multiplier: float = Field(ge=1.0, le=5.0)
    starts_at: datetime
    ends_at: datetime
    reason: str = Field(min_length=5, max_length=255)

class RejectionTemplatePayload(BaseModel):
    title: str = Field(min_length=3, max_length=80)
    reason: str = Field(min_length=8, max_length=500)


@router.get("/roles/permissions")
async def list_admin_permissions(admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await AdminGovernanceService(db).role_permissions()

@router.get("/roles")
async def list_admin_roles(admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await AdminGovernanceService(db).list_roles()

@router.post("/roles")
async def create_admin_role(payload: RolePayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await AdminGovernanceService(db).save_role(admin.id, None, payload.name, payload.description, payload.permissions)

@router.put("/roles/{role_id}")
async def update_admin_role(role_id: str, payload: RolePayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await AdminGovernanceService(db).save_role(admin.id, role_id, payload.name, payload.description, payload.permissions)

@router.post("/users/{user_id}/roles")
async def assign_admin_role(user_id: str, payload: AssignRolePayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await AdminGovernanceService(db).assign_role(admin.id, user_id, payload.role_id)

@router.post("/users/bulk-status")
async def bulk_customer_status(payload: BulkCustomerStatusPayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await AdminGovernanceService(db).bulk_customer_status(admin.id, payload.user_ids, payload.is_active, payload.reason)

@router.get("/sessions")
async def list_my_admin_sessions(admin: User = Depends(get_current_admin), request: Request = None, db: AsyncSession = Depends(get_db)):
    token = request.headers.get("authorization", "").removeprefix("Bearer ") if request else ""
    claims = decode_jwt_token(token)
    session_id = claims.get("session_id", "")
    return await AdminGovernanceService(db).list_sessions(admin.id, session_id)

@router.delete("/sessions/{session_id}")
async def revoke_my_admin_session(session_id: str, admin: User = Depends(get_current_admin), request: Request = None, db: AsyncSession = Depends(get_db)):
    token = request.headers.get("authorization", "").removeprefix("Bearer ") if request else ""
    claims = decode_jwt_token(token)
    return await AdminGovernanceService(db).revoke_session(admin.id, admin.id, claims.get("session_id", ""), session_id)

@router.get("/drivers/{driver_id}/documents")
async def get_driver_documents(driver_id: str, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await AdminGovernanceService(db).list_driver_documents(driver_id)

@router.get("/driver-documents/{document_id}/content")
async def get_driver_document_content(document_id: str, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    path, mime, name = await AdminGovernanceService(db).driver_document_path(document_id)
    return FileResponse(path, media_type=mime, filename=name, content_disposition_type="inline")

@router.put("/driver-documents/{document_id}/review")
async def review_driver_document(document_id: str, payload: DocumentReviewPayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await AdminGovernanceService(db).review_document(admin.id, document_id, payload.verification_status, payload.reason)

@router.get("/rejection-templates")
async def list_rejection_templates(admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await AdminGovernanceService(db).list_rejection_templates()

@router.post("/rejection-templates")
async def add_rejection_template(payload: RejectionTemplatePayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await AdminGovernanceService(db).create_rejection_template(admin.id, payload.title, payload.reason)

@router.post("/rides/{ride_id}/assign")
async def manually_assign_ride(ride_id: str, payload: RideAssignmentPayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await AdminGovernanceService(db).assign_ride(admin.id, ride_id, payload.driver_id, payload.reason)

@router.post("/rides/{ride_id}/cancel")
async def admin_cancel_ride(ride_id: str, payload: RideCancellationPayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await AdminGovernanceService(db).cancel_ride(admin.id, ride_id, payload.reason)

@router.get("/fare-drafts")
async def list_fare_drafts(admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await AdminGovernanceService(db).list_fare_rules(include_drafts=True)

@router.post("/fare-drafts")
async def create_fare_draft(payload: FareDraftPayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await AdminGovernanceService(db).create_fare_draft(admin.id, payload.model_dump())

@router.post("/fare-drafts/{rule_id}/approve")
async def approve_fare_draft(rule_id: str, payload: ApprovalReasonPayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await AdminGovernanceService(db).approve_fare_draft(admin.id, rule_id, payload.reason)

@router.post("/fare-drafts/{rule_id}/activate")
async def activate_fare_draft(rule_id: str, payload: ActivationPayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await AdminGovernanceService(db).activate_fare_draft(admin.id, rule_id, payload.effective_from)

@router.get("/vehicle-categories")
async def get_vehicle_categories(admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await AdminGovernanceService(db).list_categories()

@router.post("/vehicle-categories")
async def create_vehicle_category(payload: CategoryPayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await AdminGovernanceService(db).save_category(admin.id, None, payload.model_dump())

@router.put("/vehicle-categories/{category_id}")
async def update_vehicle_category(category_id: str, payload: CategoryPayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await AdminGovernanceService(db).save_category(admin.id, category_id, payload.model_dump())

@router.get("/calendar")
async def get_admin_calendar(start: Optional[datetime] = Query(None), end: Optional[datetime] = Query(None), admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await AdminGovernanceService(db).list_calendar(start, end)

@router.post("/calendar")
async def create_admin_calendar_event(payload: CalendarPayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await AdminGovernanceService(db).create_calendar_event(admin.id, payload.title, payload.event_date, payload.event_type, payload.zone_id, payload.notes)

@router.delete("/calendar/{event_id}")
async def delete_admin_calendar_event(event_id: str, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await AdminGovernanceService(db).remove_calendar_event(admin.id, event_id)

@router.post("/geofences/preview")
async def preview_geofence_overlap(payload: GeofencePayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return {"overlap_warnings": await AdminGovernanceService(db).geofence_preview(payload.coordinates)}

@router.get("/geofences/export")
async def export_geofences(admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    zones = await AdminGovernanceService(db).list_geofences()
    features = []
    for z in zones:
        ring = [[p["lng"], p["lat"]] for p in z["coordinates"]]
        if ring:
            ring.append(ring[0])
        features.append({"type": "Feature", "id": z["id"],
            "properties": {"name": z["name"], "zone_type": z["zone_type"], "is_active": z["is_active"], "surge_multiplier": z["surge_multiplier"]},
            "geometry": {"type": "Polygon", "coordinates": [ring]}})
    return {"type": "FeatureCollection", "features": features}

@router.post("/geofences/import")
async def import_geofences(payload: GeoJsonImportPayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    parsed = []
    for feature in payload.features:
        props = feature.get("properties") or {}
        geometry = feature.get("geometry") or {}
        coords = geometry.get("coordinates")
        if geometry.get("type") != "Polygon" or not isinstance(coords, list) or not coords or not isinstance(coords[0], list):
            raise HTTPException(422, "Each imported feature must contain a GeoJSON Polygon with an exterior ring.")
        ring = coords[0]
        if len(ring) > 201 or any(not isinstance(p, (list, tuple)) or len(p) < 2 for p in ring):
            raise HTTPException(422, "Imported boundary coordinates are malformed or exceed 200 vertices.")
        try:
            points = [{"lat": float(p[1]), "lng": float(p[0])} for p in ring[:-1]]
            multiplier = float(props.get("surge_multiplier", 1.0))
        except (TypeError, ValueError, IndexError):
            raise HTTPException(422, "Imported boundary coordinates must be numeric longitude/latitude pairs.")
        zone_type = props.get("zone_type", "SERVICE_AREA")
        if zone_type not in {"RESTRICTED", "SERVICE_AREA", "HIGH_DEMAND", "SURGE_ZONE"} or not 1 <= multiplier <= 5:
            raise HTTPException(422, "Imported boundary has unsupported zone properties.")
        parsed.append({"name": str(props.get("name", "Imported boundary"))[:128], "zone_type": zone_type,
                       "coordinates": points, "surge_multiplier": multiplier, "is_active": False})
    service = AdminGovernanceService(db)
    try:
        result = [await service.save_geofence(admin.id, None, value, commit=False) for value in parsed]
        await db.commit()
        return {"imported_count": len(result), "zones": result, "activation_note": "Imported boundaries are inactive until reviewed and activated in the editor."}
    except Exception:
        await db.rollback()
        raise

@router.get("/geofences/audit")
async def geofence_audit(zone_id: Optional[str] = None, limit: int = Query(100, ge=1, le=500), admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await AdminGovernanceService(db).list_geofence_audits(zone_id, limit)

@router.put("/geofences/{zone_id}")
async def update_geofence(zone_id: str, payload: GeofencePayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await AdminGovernanceService(db).save_geofence(admin.id, zone_id, payload.model_dump())

@router.delete("/geofences/{zone_id}")
async def deactivate_geofence(zone_id: str, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await AdminGovernanceService(db).delete_geofence(admin.id, zone_id)

@router.post("/geofences")
async def create_governance_geofence(payload: GeofencePayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await AdminGovernanceService(db).save_geofence(admin.id, None, payload.model_dump())

@router.get("/records/{subject_type}/{subject_id}/notes")
async def get_record_notes(subject_type: str, subject_id: str, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await AdminGovernanceService(db).list_notes(subject_type, subject_id)

@router.post("/records/{subject_type}/{subject_id}/notes")
async def add_record_note(subject_type: str, subject_id: str, payload: NotePayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await AdminGovernanceService(db).add_note(admin.id, subject_type, subject_id, payload.note)

@router.post("/surge/preview")
async def preview_surge_rule(payload: SurgePreviewPayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    if payload.ends_at <= payload.starts_at:
        raise HTTPException(422, "Surge end time must be after its start time.")
    if payload.zone_id and not await db.get(__import__("backend.domain.models", fromlist=["GeofenceZone"]).GeofenceZone, payload.zone_id):
        raise HTTPException(404, "Service zone not found.")
    if payload.category_id:
        from backend.domain.models import VehicleCategory
        if not await db.get(VehicleCategory, payload.category_id):
            raise HTTPException(404, "Vehicle category not found.")
    return {"preview_only": True, "multiplier": payload.multiplier, "starts_at": payload.starts_at.isoformat(),
            "ends_at": payload.ends_at.isoformat(), "zone_id": payload.zone_id, "category_id": payload.category_id,
            "notice": "Preview is configuration-only; it does not forecast demand or change live fares. Confirm through surge rule creation to persist the rule."}
