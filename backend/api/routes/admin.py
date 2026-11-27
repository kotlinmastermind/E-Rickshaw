import uuid
from typing import Optional, List, Dict, Any
from datetime import datetime, timezone, timedelta
from fastapi import APIRouter, Depends, Query, HTTPException, status, Request
from pydantic import BaseModel, Field, ConfigDict, model_validator
from sqlalchemy import select, desc, func, or_, update
from sqlalchemy.ext.asyncio import AsyncSession
from backend.infrastructure.database.session import get_db
from backend.api.deps import get_current_admin
from backend.domain.models import (
    User, Session, DriverProfile, CustomerProfile, Ride, VehicleCategory,
    DriverAvailability, DriverLocation, DriverVehicleAssignment, FareRule, Vehicle, DriverDocument,
    AdministrativeAuditLog, CustomerKycDocument, CustomerKycAuditLog
)
from backend.infrastructure.security.crypto import create_jwt_token, decode_jwt_token,hash_secret , verify_password
from backend.application.services.admin_service import AdminService
from backend.application.services.shared_ride_analytics_service import SharedRideAnalyticsService
from backend.application.services.admin_governance_service import AdminGovernanceService

router = APIRouter(prefix="/admin", tags=["Platform Administration"])

async def get_admin_actor(
    user: User = Depends(get_current_admin)
) -> User:
    """
    Resolves the administrator actor for controls center actions.
    Enforces strict JWT authentication, active session verification, and administrator RBAC.
    """
    return user



# REPLACE lines 31-79 in backend/api/routes/admin.py with the following secure handler:

class AdminLoginRequest(BaseModel):
    email: str
    password: str

@router.post("/session/login")
async def admin_secure_login(
    payload: AdminLoginRequest,
    db: AsyncSession = Depends(get_db)
):
    """
    Secure Administrator Authentication. Requires valid bcrypt password verification.
    Anonymous token minting is strictly removed.
    """
    from backend.infrastructure.security.crypto import verify_password
    
    stmt = select(User).where(
        User.email == payload.email.strip().lower(),
        User.role.in_(["admin", "superadmin", "super administrator"]),
        User.is_active == True
    )
    res = await db.execute(stmt)
    admin_user = res.scalar_one_or_none()
    
    if not admin_user or not verify_password(payload.password, admin_user.hashed_password or ""):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid administrative credentials."
        )

    session_id = str(uuid.uuid4())
    active_sess = Session(
        id=session_id,
        user_id=admin_user.id,
        token_hash=hash_secret(session_id),
        is_revoked=False,
        expires_at=datetime.now(timezone.utc) + timedelta(hours=12),
        created_at=datetime.now(timezone.utc)
    )
    db.add(active_sess)
    await db.commit()

    token = create_jwt_token(
        user_id=admin_user.id,
        role=admin_user.role,
        session_id=session_id,
        token_type="access",
        expires_delta=timedelta(hours=12)
    )

    return {
        "access_token": token,
        "token_type": "bearer",
        "expires_in_hours": 12,
        "admin": {
            "id": admin_user.id,
            "email": admin_user.email,
            "role": admin_user.role
        }
    }
@router.get("/live-feed")
async def get_live_mobility_feed(
    admin: User = Depends(get_admin_actor),
    db: AsyncSession = Depends(get_db)
):
    """Return only persisted operational records; unavailable telemetry stays null."""
    total_users_count = (await db.execute(select(func.count(User.id)))).scalar_one() or 0
    total_customers_count = (
        await db.execute(select(func.count(User.id)).where(User.role == "customer"))
    ).scalar_one() or 0
    total_drivers_count = (
        await db.execute(select(func.count(User.id)).where(User.role == "driver"))
    ).scalar_one() or 0

    pending_kyc_count = (
        await db.execute(
            select(func.count(DriverProfile.id)).where(
                or_(
                    DriverProfile.approval_status.in_(["PENDING", "pending"]),
                    DriverProfile.approval_status.is_(None),
                )
            )
        )
    ).scalar_one() or 0

    active_rides_count = (
        await db.execute(
            select(func.count(Ride.id)).where(
                Ride.status.in_(["REQUESTED", "ASSIGNED", "ARRIVED", "IN_PROGRESS"])
            )
        )
    ).scalar_one() or 0

    gross_revenue_cents = (
        await db.execute(
            select(func.coalesce(func.sum(Ride.final_fare_cents), 0)).where(
                Ride.status == "COMPLETED",
                Ride.final_fare_cents.is_not(None),
            )
        )
    ).scalar_one() or 0
    gross_booking_inr = round(int(gross_revenue_cents) / 100.0, 2)

    online_drivers_count = (
        await db.execute(
            select(func.count(DriverAvailability.id)).where(DriverAvailability.is_online.is_(True))
        )
    ).scalar_one() or 0

    driver_rows = (
        await db.execute(
            select(DriverProfile, User)
            .join(User, DriverProfile.user_id == User.id)
            .order_by(DriverProfile.created_at.desc())
        )
    ).all()

    availability_rows = (await db.execute(select(DriverAvailability))).scalars().all()
    availabilities = {item.driver_id: item for item in availability_rows}

    category_rows = (await db.execute(select(VehicleCategory))).scalars().all()
    categories_by_id = {item.id: item for item in category_rows}

    assignment_rows = (
        await db.execute(
            select(DriverVehicleAssignment, Vehicle)
            .join(Vehicle, DriverVehicleAssignment.vehicle_id == Vehicle.id)
            .where(
                DriverVehicleAssignment.is_active.is_(True),
                Vehicle.is_active.is_(True),
            )
            .order_by(DriverVehicleAssignment.assigned_at.desc())
        )
    ).all()
    vehicles_by_driver = {}
    for assignment, vehicle in assignment_rows:
        vehicles_by_driver.setdefault(assignment.driver_id, vehicle)

    location_rows = (
        await db.execute(
            select(DriverLocation).order_by(DriverLocation.recorded_at.desc())
        )
    ).scalars().all()
    locations_by_driver = {}
    for location in location_rows:
        # Results are newest-first: keep the first location for each driver.
        locations_by_driver.setdefault(location.driver_id, location)

    formatted_drivers = []
    users_by_driver_id = {}
    for profile, user in driver_rows:
        users_by_driver_id[profile.id] = user
        availability = availabilities.get(profile.id)
        is_online = bool(availability.is_online) if availability else False
        is_busy = bool(availability.is_busy) if availability else False

        approval = str(profile.approval_status or "PENDING").strip().upper()
        if approval in {"APPROVED", "YES", "TRUE", "1"}:
            canonical_approval = "APPROVED"
            status_value = "BUSY" if is_online and is_busy else ("AVAILABLE" if is_online else "OFFLINE")
        elif approval in {"REJECTED", "REJECT"}:
            canonical_approval = "REJECTED"
            status_value = "OFFLINE"
        elif approval == "SUSPENDED":
            canonical_approval = "SUSPENDED"
            status_value = "OFFLINE"
        else:
            canonical_approval = "PENDING"
            status_value = "PENDING"

        location = locations_by_driver.get(profile.id)
        vehicle = vehicles_by_driver.get(profile.id)
        category = categories_by_id.get(vehicle.category_id) if vehicle else None

        formatted_drivers.append({
            "id": str(profile.id),
            "user_id": str(user.id),
            "name": user.full_name,
            "phone": user.phone_number,
            "email": user.email,
            "plate": vehicle.license_plate if vehicle else None,
            "model": f"{vehicle.make} {vehicle.model}".strip() if vehicle else None,
            "category": category.name if category else None,
            "status": status_value,
            "approval_status": canonical_approval,
            "lat": float(location.latitude) if location and location.latitude is not None else None,
            "lng": float(location.longitude) if location and location.longitude is not None else None,
            "battery": None,
            "speed": float(location.speed) if location and location.speed is not None else None,
            "heading": float(location.heading) if location and location.heading is not None else None,
            "location_recorded_at": location.recorded_at.isoformat() if location and location.recorded_at else None,
            "rating": float(profile.rating_average) if profile.rating_count else None,
            "trips": int(profile.total_trips or 0),
            "dl": profile.license_number,
        })

    customer_rows = (
        await db.execute(
            select(CustomerProfile, User)
            .join(User, CustomerProfile.user_id == User.id)
            .order_by(User.created_at.desc())
        )
    ).all()
    customers_by_profile_id = {profile.id: (profile, user) for profile, user in customer_rows}
    customers_by_user_id = {user.id: (profile, user) for profile, user in customer_rows}

    customer_activity_rows = (
        await db.execute(
            select(
                Ride.customer_id,
                func.count(Ride.id),
                func.coalesce(func.sum(Ride.final_fare_cents), 0),
            )
            .where(Ride.status == "COMPLETED", Ride.final_fare_cents.is_not(None))
            .group_by(Ride.customer_id)
        )
    ).all()
    customer_activity = {
        customer_id: {"rides": int(ride_count or 0), "spend_cents": int(spend_cents or 0)}
        for customer_id, ride_count, spend_cents in customer_activity_rows
    }

    all_customer_users = (
        await db.execute(
            select(User).where(User.role == "customer").order_by(User.created_at.desc())
        )
    ).scalars().all()
    formatted_customers = []
    for user in all_customer_users:
        profile_and_user = customers_by_user_id.get(user.id)
        profile = profile_and_user[0] if profile_and_user else None
        activity = customer_activity.get(profile.id, {"rides": 0, "spend_cents": 0}) if profile else {"rides": 0, "spend_cents": 0}
        formatted_customers.append({
            "id": str(user.id),
            "name": user.full_name,
            "phone": user.phone_number,
            "email": user.email,
            "rides": activity["rides"],
            "spend": f"₹{activity['spend_cents'] / 100.0:,.2f}",
            "rating": float(profile.rating_average) if profile and profile.rating_count else None,
            "active": bool(user.is_active),
        })

    requested_rides = (
        await db.execute(
            select(Ride)
            .where(Ride.status == "REQUESTED")
            .order_by(Ride.requested_at.asc())
            .limit(20)
        )
    ).scalars().all()
    now = datetime.now(timezone.utc)
    formatted_riders = []
    for ride in requested_rides:
        customer_record = customers_by_profile_id.get(ride.customer_id)
        customer_profile, customer_user = customer_record if customer_record else (None, None)
        requested_at = ride.requested_at
        if requested_at is not None and requested_at.tzinfo is None:
            requested_at = requested_at.replace(tzinfo=timezone.utc)
        wait_minutes = max(0.0, (now - requested_at).total_seconds() / 60.0) if requested_at else None
        formatted_riders.append({
            "id": str(ride.id),
            "name": customer_user.full_name if customer_user else ride.passenger_name,
            "phone": customer_user.phone_number if customer_user else None,
            "lat": float(ride.pickup_latitude) if ride.pickup_latitude is not None else None,
            "lng": float(ride.pickup_longitude) if ride.pickup_longitude is not None else None,
            "status": ride.status,
            "pickup": ride.pickup_address,
            "targetDrop": ride.destination_address,
            "waitMins": round(wait_minutes, 1) if wait_minutes is not None else None,
            "rating": float(customer_profile.rating_average) if customer_profile and customer_profile.rating_count else None,
        })

    rides_rows = (
        await db.execute(
            select(Ride).order_by(Ride.requested_at.desc()).limit(15)
        )
    ).scalars().all()
    formatted_rides = []
    for ride in rides_rows:
        customer_record = customers_by_profile_id.get(ride.customer_id)
        customer_user = customer_record[1] if customer_record else None
        driver_user = users_by_driver_id.get(ride.assigned_driver_id) if ride.assigned_driver_id else None
        vehicle = vehicles_by_driver.get(ride.assigned_driver_id) if ride.assigned_driver_id else None
        fare_cents = ride.final_fare_cents if ride.final_fare_cents is not None else ride.estimated_fare_cents
        formatted_rides.append({
            "id": str(ride.id),
            "rider": ride.passenger_name or (customer_user.full_name if customer_user else None),
            "driver": driver_user.full_name if driver_user else None,
            "plate": vehicle.license_plate if vehicle else None,
            "pickup": ride.pickup_address,
            "drop": ride.destination_address,
            "fare": f"₹{fare_cents / 100.0:.2f}" if fare_cents is not None else None,
            "payment": ride.payment_method,
            "status": ride.status,
        })

    fare_rows = (
        await db.execute(
            select(FareRule).where(FareRule.is_active.is_(True))
        )
    ).scalars().all()
    fares_by_category = {rule.category_id: rule for rule in fare_rows}
    icons_by_code = {
        "E_RICKSHAW_STANDARD": "🛺",
        "E_RICKSHAW_SHARE": "👥",
        "E_AUTO": "⚡",
        "E_CARGO_LOADER": "📦",
        "BIKE": "🛵",
    }

    formatted_tariffs = []
    for category in category_rows:
        if category.code == "BIKE":
            continue
        rule = fares_by_category.get(category.id)
        base_fare = rule.base_fare_cents / 100.0 if rule else None
        per_km = rule.per_km_cents / 100.0 if rule else None
        per_minute = rule.per_minute_cents / 100.0 if rule else None
        minimum_fare = rule.minimum_fare_cents / 100.0 if rule else None
        formatted_tariffs.append({
            "id": category.id,
            "ruleId": rule.id if rule else None,
            "code": category.code,
            "name": category.name,
            "desc": category.description,
            "base": f"₹{base_fare:.0f}" if base_fare is not None else None,
            "perKm": f"₹{per_km:.1f}/km" if per_km is not None else None,
            "perMin": f"₹{per_minute:.1f}/min" if per_minute is not None else None,
            "minFare": f"₹{minimum_fare:.0f}" if minimum_fare is not None else None,
            "baseFareRupees": base_fare,
            "perKmRupees": per_km,
            "perMinuteRupees": per_minute,
            "minimumFareRupees": minimum_fare,
            "surgeMultiplier": rule.surge_multiplier if rule else None,
            "icon": icons_by_code.get(category.code, "🛺"),
            "active": bool(rule and rule.is_active),
        })

    return {
        "stats": {
            "online_drivers": int(online_drivers_count),
            "active_rides": int(active_rides_count),
            "waiting_riders": len(formatted_riders),
            "gross_booking_inr": gross_booking_inr,
            "pending_kyc": int(pending_kyc_count),
            "total_customers": int(total_customers_count),
            "total_drivers": int(total_drivers_count),
            "total_users": int(total_users_count),
            "platform_status": "OPERATIONAL",
        },
        "drivers": formatted_drivers,
        "riders": formatted_riders,
        "active_rides": formatted_rides,
        "customers": formatted_customers,
        "tariffs": formatted_tariffs,
    }

@router.get("/shared-rides/analytics")
async def get_shared_ride_operational_analytics(
    days: int = Query(30, ge=1, le=365),
    admin: User = Depends(get_admin_actor),
    db: AsyncSession = Depends(get_db),
):
    return await SharedRideAnalyticsService(db).get_operational_analytics(days)

class UserStatusUpdate(BaseModel):
    is_active: bool
    reason: str = Field(min_length=10, max_length=1000)

class DriverApprovalUpdate(BaseModel):
    model_config = ConfigDict(extra="forbid")

    approval_status: Optional[str] = Field(None, json_schema_extra={"example": "APPROVED"})  # APPROVED, REJECTED, SUSPENDED
    status: Optional[str] = None
    rejection_reason: Optional[str] = None

    @model_validator(mode="after")
    def validate_statuses(self) -> "DriverApprovalUpdate":
        norm_approval = self.approval_status.strip().upper() if self.approval_status and self.approval_status.strip() else None
        norm_status = self.status.strip().upper() if self.status and self.status.strip() else None

        if norm_approval and norm_status and norm_approval != norm_status:
            raise ValueError("Conflicting approval status values provided in payload.")

        effective = norm_approval or norm_status
        if not effective:
            raise ValueError("Approval status is required.")

        VALID_STATUSES = {"APPROVED", "REJECTED", "SUSPENDED"}
        if effective not in VALID_STATUSES:
            raise ValueError(f"Invalid approval status. Must be one of: {', '.join(sorted(VALID_STATUSES))}")

        return self

    @property
    def target_status(self) -> str:
        norm_approval = self.approval_status.strip().upper() if self.approval_status and self.approval_status.strip() else None
        norm_status = self.status.strip().upper() if self.status and self.status.strip() else None
        return norm_approval or norm_status or ""

class DriverDocumentReviewPayload(BaseModel):
    model_config = ConfigDict(extra="forbid")

    verification_status: str = Field(..., json_schema_extra={"example": "VERIFIED"})
    review_notes: Optional[str] = None
    reason: Optional[str] = None
    revocation_justification: Optional[str] = None

class CreateFareRulePayload(BaseModel):
    category_id: str
    base_fare_cents: int = Field(..., ge=0)
    per_km_cents: int = Field(..., ge=0)
    per_minute_cents: int = Field(..., ge=0)
    minimum_fare_cents: int = Field(..., ge=0)
    surge_multiplier: float = Field(default=1.0, ge=1.0)
    fare_cap_cents: Optional[int] = Field(default=None, ge=0)

class UpdateFareRulePayload(BaseModel):
    city_code: Optional[str] = "DELHI_NCR"
    base_fare_rupees: Optional[float] = Field(None, ge=0.0)
    per_km_rupees: Optional[float] = Field(None, ge=0.0)
    per_minute_rupees: Optional[float] = Field(None, ge=0.0)
    minimum_fare_rupees: Optional[float] = Field(None, ge=0.0)
    surge_multiplier: Optional[float] = Field(None, ge=1.0, le=5.0)
    cancellation_fee_rupees: Optional[float] = Field(None, ge=0.0)
    fare_cap_rupees: Optional[float] = Field(None, ge=0.0)
    base_fare_cents: Optional[int] = Field(None, ge=0)
    per_km_cents: Optional[int] = Field(None, ge=0)
    per_minute_cents: Optional[int] = Field(None, ge=0)
    minimum_fare_cents: Optional[int] = Field(None, ge=0)
    reason: Optional[str] = "Admin runtime tariff adjustment"

class UpdateSurgePayload(BaseModel):
    surge_multiplier: float = Field(..., ge=1.0, le=5.0)
    category_id: Optional[str] = None
    reason: Optional[str] = "Corridor dynamic surge adjustment"

class ApproveFareDraftPayload(BaseModel):
    reason: str = Field(..., min_length=10)

class ActivateFareDraftPayload(BaseModel):
    effective_from: Optional[datetime] = None

@router.get("/dashboard/stats")
async def get_dashboard_stats(admin: User = Depends(get_admin_actor), db: AsyncSession = Depends(get_db)):
    service = AdminService(db)
    return await service.get_dashboard_metrics()

@router.get("/users")
async def list_users(
    role: Optional[str] = Query(None),
    search: Optional[str] = Query(None),
    page: int = Query(1, ge=1),
    limit: int = Query(20, ge=1, le=100),
    active: Optional[bool] = Query(None),
    admin: User = Depends(get_admin_actor),
    db: AsyncSession = Depends(get_db)
):
    return await AdminGovernanceService(db).list_users(role=role or "", search=search or "", active=active, page=page, limit=limit)

@router.put("/users/{user_id}/status")
async def update_user_status(
    user_id: str,
    payload: UserStatusUpdate,
    admin: User = Depends(get_admin_actor),
    db: AsyncSession = Depends(get_db)
):
    service = AdminService(db)
    return await AdminGovernanceService(db).set_customer_status(admin.id, user_id, payload.is_active, payload.reason)

@router.get("/drivers")
async def list_drivers(
    approval_status: Optional[str] = Query(None),
    admin: User = Depends(get_admin_actor),
    db: AsyncSession = Depends(get_db)
):
    return await AdminGovernanceService(db).list_drivers(approval_status or "")

@router.put("/drivers/{driver_id}/approval")
async def review_driver(
    driver_id: str,
    payload: DriverApprovalUpdate,
    admin: User = Depends(get_admin_actor),
    db: AsyncSession = Depends(get_db)
):
    target_status = payload.target_status
    if not target_status:
        raise HTTPException(status_code=422, detail="Approval status is required.")

    from backend.domain.models import DriverProfile, DriverDocument
    from sqlalchemy import select

    driver = await db.get(DriverProfile, driver_id)
    if not driver:
        raise HTTPException(status_code=404, detail="Driver record not found.")

    if target_status == "APPROVED":
        documents = (await db.execute(select(DriverDocument).where(DriverDocument.driver_id == driver_id))).scalars().all()
        unverified_docs = [doc for doc in documents if doc.verification_status != "VERIFIED"]
        if unverified_docs:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail=f"Cannot approve chauffeur: {len(unverified_docs)} document(s) have not been verified."
            )

    service = AdminService(db)
    return await service.review_driver_approval(
        admin_id=admin.id,
        driver_id=driver_id,
        status=target_status,
        rejection_reason=payload.rejection_reason
    )

class DriverDutyAdminPayload(BaseModel):
    is_online: bool
    reason: Optional[str] = "Admin operational override"

@router.put("/drivers/{driver_id}/duty")
async def admin_set_driver_duty(
    driver_id: str,
    payload: DriverDutyAdminPayload,
    admin: User = Depends(get_admin_actor),
    db: AsyncSession = Depends(get_db)
):
    from backend.domain.models import DriverProfile, AdministrativeAuditLog
    import json
    driver = await db.get(DriverProfile, driver_id)
    if not driver:
        raise HTTPException(status_code=404, detail="Driver record not found.")

    from backend.application.services.driver_service import DriverService
    result = await DriverService(db).toggle_availability(driver.user_id, payload.is_online)

    audit = AdministrativeAuditLog(
        admin_user_id=admin.id,
        action="ADMIN_SET_DRIVER_DUTY",
        target_resource="driver_availability",
        target_id=driver_id,
        changes_json=json.dumps({"is_online": payload.is_online, "reason": payload.reason})
    )
    db.add(audit)
    await db.commit()
    return result

    audit = AdministrativeAuditLog(
        admin_user_id=admin.id,
        action="ADMIN_SET_DRIVER_DUTY",
        target_resource="driver_availability",
        target_id=driver_id,
        changes_json=json.dumps({"is_online": payload.is_online, "reason": payload.reason})
    )
    db.add(audit)
    await db.commit()
    return result

@router.get("/drivers/{driver_id}/documents")
async def list_driver_documents(
    driver_id: str,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    return await AdminGovernanceService(db).list_driver_documents(driver_id)

@router.put("/drivers/{driver_id}/documents/{document_id}")
async def review_driver_document(
    driver_id: str,
    document_id: str,
    payload: DriverDocumentReviewPayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    notes = (payload.review_notes or payload.reason or "").strip()
    return await AdminGovernanceService(db).review_document(
        admin_id=admin.id,
        document_id=document_id,
        status=payload.verification_status,
        reason=notes,
        driver_id=driver_id,
        revocation_justification=payload.revocation_justification,
    )

@router.get("/driver-documents/{document_id}/content")
async def download_driver_document(
    document_id: str,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from fastapi.responses import FileResponse
    path, mime_type, filename = await AdminGovernanceService(db).driver_document_path(document_id)
    return FileResponse(path=path, media_type=mime_type, filename=filename)

@router.put("/driver-documents/{document_id}/review")
async def review_driver_document_legacy(
    document_id: str,
    payload: DriverDocumentReviewPayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    notes = (payload.review_notes or payload.reason or "").strip()
    return await AdminGovernanceService(db).review_document(
        admin_id=admin.id,
        document_id=document_id,
        status=payload.verification_status,
        reason=notes,
        revocation_justification=payload.revocation_justification,
    )

@router.get("/rides")
async def list_rides(
    ride_id: Optional[str] = Query(None, max_length=64),
    status: Optional[str] = Query(None),
    page: int = Query(1, ge=1),
    limit: int = Query(20, ge=1, le=100),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    return await AdminGovernanceService(db).list_rides(ride_id or "", status or "", page, limit)

@router.get("/rides/{ride_id}")
async def get_ride_admin_details(
    ride_id: str,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    return await AdminGovernanceService(db).ride_timeline(ride_id)

@router.get("/fares")
async def list_fare_rules(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    return await AdminGovernanceService(db).list_fare_rules()

@router.post("/fares")
async def create_fare_rule(
    payload: CreateFareRulePayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    service = AdminGovernanceService(db)
    return await service.create_fare_draft(admin.id, {
        "category_id": payload.category_id, "base_fare_cents": payload.base_fare_cents,
        "per_km_cents": payload.per_km_cents, "per_minute_cents": payload.per_minute_cents,
        "minimum_fare_cents": payload.minimum_fare_cents, "surge_multiplier": payload.surge_multiplier,
        "fare_cap_cents": getattr(payload, "fare_cap_cents", None), "effective_from": datetime.now(timezone.utc),
    })

@router.put("/fares/category/{category_id}")
async def update_runtime_fare_rule(
    category_id: str,
    payload: UpdateFareRulePayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """
    Admin Runtime Fare Configuration Endpoint:
    Allows real-time updates to live base fare, per km, per minute, min fare, and surge rates
    for any vehicle category directly from backend without creating or releasing a new mobile APK.
    """
    service = AdminGovernanceService(db)
    return await service.update_runtime_fare_rule(
        admin_id=admin.id,
        category_identifier=category_id,
        values=payload.model_dump(exclude_unset=True)
    )

@router.post("/fares/surge")
async def update_runtime_corridor_surge(
    payload: UpdateSurgePayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """
    Admin Dynamic Surge Configuration Endpoint:
    Instantly updates surge multipliers across vehicle categories in real time.
    """
    service = AdminGovernanceService(db)
    return await service.update_runtime_surge(
        admin_id=admin.id,
        surge_multiplier=payload.surge_multiplier,
        category_id=payload.category_id,
        reason=payload.reason or "Corridor surge adjustment"
    )

@router.post("/fares/{rule_id}/approve")
async def approve_fare_draft(
    rule_id: str,
    payload: ApproveFareDraftPayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    service = AdminGovernanceService(db)
    return await service.approve_fare_draft(admin.id, rule_id, payload.reason)

@router.post("/fares/{rule_id}/activate")
async def activate_fare_draft(
    rule_id: str,
    payload: ActivateFareDraftPayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    service = AdminGovernanceService(db)
    effective_from = payload.effective_from or datetime.now(timezone.utc)
    return await service.activate_fare_draft(admin.id, rule_id, effective_from)

# ==================== PHASE 2 ADMIN FEATURES ====================
from backend.application.services.admin_advanced_service import AdminAdvancedService
from backend.application.services.support_chat_service import SupportChatService
from backend.application.services.dispatch_advanced_service import DispatchAdvancedService

class BroadcastPayload(BaseModel):
    title: str = Field(..., json_schema_extra={"example": "Monsoon Fleet Operations Notice"})
    message: str = Field(..., json_schema_extra={"example": "Expect increased surge multipliers across Mumbai South."})
    target_role: str = "ALL"  # ALL, CUSTOMER, DRIVER
    channel: str = "IN_APP"

class SurgeRulePayload(BaseModel):
    zone_name: Optional[str] = None
    zone_id: Optional[str] = None
    category_id: Optional[str] = None
    multiplier: float = Field(..., ge=1.0, le=5.0)
    reason: str
    starts_at: Optional[datetime] = None
    ends_at: Optional[datetime] = None

class GeofencePayload(BaseModel):
    name: str
    zone_type: str = "RESTRICTED"  # RESTRICTED, SERVICE_AREA, HIGH_DEMAND
    coordinates: List[dict]  # [{"lat": 19.0, "lng": 72.8}, ...]
    surge_multiplier: float = 1.0

class VerifyPointPayload(BaseModel):
    latitude: float
    longitude: float

class CommissionRulePayload(BaseModel):
    rule_name: str
    category_id: Optional[str] = None
    commission_percentage: float = Field(..., ge=0.0, le=100.0)
    fixed_cut_cents: int = 0
    reason: str = Field(..., min_length=12, max_length=1000)

class PayoutRejectPayload(BaseModel):
    rejection_reason: str = Field(..., min_length=3)

class PayoutSettlePayload(BaseModel):
    settlement_reference: str = Field(..., min_length=3)

# 1. Support Tickets
@router.get("/support/tickets")
async def list_all_support_tickets(
    status: Optional[str] = None,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.domain.models import SupportTicket
    from sqlalchemy import select
    stmt = select(SupportTicket).order_by(SupportTicket.created_at.desc())
    if status:
        stmt = stmt.where(SupportTicket.status == status)
    res = await db.execute(stmt)
    tickets = res.scalars().all()
    return [
        {
            "id": t.id,
            "ticket_number": t.ticket_number,
            "complaint_reference_number": t.complaint_reference_number,
            "user_id": t.user_id,
            "ride_id": t.ride_id,
            "subject": t.subject,
            "category": t.category,
            "status": t.status,
            "priority": t.priority,
            "assigned_squad": t.assigned_squad,
            "language_preference": t.language_preference,
            "is_escalated": t.is_escalated,
            "escalation_reason": t.escalation_reason,
            "lost_item_category": t.lost_item_category,
            "lost_item_status": t.lost_item_status,
            "csat_score": t.csat_score,
            "data_retention_days": t.data_retention_days,
            "refund_amount_cents": t.refund_amount_cents,
            "assigned_to": t.assigned_admin_id,
            "created_at": t.created_at.isoformat()
        }
        for t in tickets
    ]

@router.put("/support/tickets/{ticket_id}/status")
async def update_support_ticket_status(
    ticket_id: str,
    status_val: str,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    ticket = await SupportChatService.admin_update_ticket(db, ticket_id, status_val=status_val, assigned_to=admin.id)
    await db.commit()
    return {"status": "UPDATED", "ticket_id": ticket.id, "ticket_status": ticket.status}

@router.post("/support/tickets/{ticket_id}/internal-note")
async def add_internal_support_note(
    ticket_id: str,
    note: str,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    msg = await SupportChatService.post_ticket_message(
        session=db,
        ticket_id=ticket_id,
        sender_user_id=admin.id,
        message_text=note,
        is_internal_only=True,
        is_staff=True
    )
    await db.commit()
    return {"status": "NOTE_ADDED", "message_id": msg.id}

# 2. Broadcast Notifications
@router.post("/broadcasts")
async def send_broadcast_notification(
    payload: BroadcastPayload,
    admin: User = Depends(get_admin_actor),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.admin_finance_support_service import AdminFinanceSupportService
    result = await AdminFinanceSupportService(db).create_broadcast(
        admin.id, payload.title, payload.message, payload.target_role, None, {}, None, False, channel=payload.channel)
    return {**result, "broadcast_id": result["id"], "recipient_count": result["eligible_count"]}

@router.get("/broadcasts")
async def list_broadcast_notifications(
    admin: User = Depends(get_admin_actor),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.admin_finance_support_service import AdminFinanceSupportService
    return await AdminFinanceSupportService(db).broadcasts()

class LocationRetentionUpdatePayload(BaseModel):
    category: str
    retention_ttl_days: int = Field(..., ge=7, le=1095)

class LocationRetentionPurgePayload(BaseModel):
    category: str = "DRIVER_RAW_TELEMETRY"
    dry_run: bool = True

class AdminRoadRestrictionPayload(BaseModel):
    title: str = Field(..., min_length=3, max_length=128)
    description: str = Field(..., min_length=5, max_length=512)
    restriction_type: str = Field("ROAD_CLOSURE", pattern="^(ROAD_CLOSURE|VIP_CORRIDOR|MONSOON_WATERLOGGING|ZERO_EMISSION_ONLY)$")
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)
    radius_meters: float = Field(500.0, ge=50, le=10000)
    starts_at: Optional[datetime] = None
    ends_at: Optional[datetime] = None

# 3. Heat Map Monitor (Feature 440: Time Filtered Demand & Supply)
@router.get("/heat-map/monitor")
async def get_admin_heat_map(
    window_hours: int = Query(2, ge=1, le=72),
    metric: str = Query("demand", pattern="^(demand|supply)$"),
    minimum_count: int = Query(5, ge=5, le=100),
    time_filter: str = Query("ALL_DAY", pattern="^(ALL_DAY|MORNING_RUSH|AFTERNOON_LULL|EVENING_GALA|LATE_NIGHT)$"),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.location_ops_service import LocationOpsService
    return await LocationOpsService(db).get_demand_heatmap_filtered(time_filter, window_hours, metric, minimum_count)

# Feature 441. Driver density map with privacy aggregation
@router.get("/location-ops/driver-density")
async def driver_density_map(
    resolution_km: float = Query(2.0, ge=0.5, le=10.0),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.location_ops_service import LocationOpsService
    return await LocationOpsService(db).get_driver_density_privacy_grid(resolution_km)

# Feature 444. Driver GPS spoofing risk signals
@router.get("/location-ops/spoof-signals")
async def get_spoofing_signals(
    driver_id: Optional[str] = Query(None),
    limit: int = Query(50, ge=1, le=200),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.location_ops_service import LocationOpsService
    return await LocationOpsService(db).get_driver_spoofing_signals(driver_id, limit)

# Feature 447. Location data retention controls
@router.get("/location-ops/retention-controls")
async def get_location_retention_controls(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.location_ops_service import LocationOpsService
    return await LocationOpsService(db).get_location_retention_controls()

@router.put("/location-ops/retention-controls")
async def update_location_retention_controls(
    payload: LocationRetentionUpdatePayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.location_ops_service import LocationOpsService
    return await LocationOpsService(db).update_location_retention_controls(payload.category, payload.retention_ttl_days)

@router.post("/location-ops/retention-purge")
async def purge_location_records(
    payload: LocationRetentionPurgePayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.location_ops_service import LocationOpsService
    return await LocationOpsService(db).run_location_retention_purge(payload.category, payload.dry_run)

# Feature 448. Location event quality dashboard
@router.get("/location-ops/quality")
async def location_quality_dashboard(
    window_hours: int = Query(24, ge=1, le=168),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.location_ops_service import LocationOpsService
    return await LocationOpsService(db).admin_location_quality(window_hours)

# Feature 426. Live map provider health check
@router.get("/location-ops/provider-health")
async def location_provider_health(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.location_ops_service import LocationOpsService
    return await LocationOpsService(db).get_live_map_provider_health()

# Feature 436. Road restrictions management
@router.get("/location-ops/road-restrictions")
async def list_admin_road_restrictions(
    latitude: float = Query(28.6139, ge=-90, le=90),
    longitude: float = Query(77.2090, ge=-180, le=180),
    radius_km: float = Query(50.0, ge=1.0, le=200.0),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.location_ops_service import LocationOpsService
    return await LocationOpsService(db).list_road_restrictions(latitude, longitude, radius_km)

@router.post("/location-ops/road-restrictions")
async def create_road_restriction(
    payload: AdminRoadRestrictionPayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.domain.models import RoadRestriction
    row = RoadRestriction(
        title=payload.title,
        description=payload.description,
        restriction_type=payload.restriction_type,
        latitude=payload.latitude,
        longitude=payload.longitude,
        radius_meters=payload.radius_meters,
        starts_at=payload.starts_at or datetime.now(timezone.utc),
        ends_at=payload.ends_at
    )
    db.add(row)
    await db.commit()
    await db.refresh(row)
    return {"created": True, "id": row.id, "title": row.title}

# 4. Surge Pricing Config
@router.post("/surge/rules")
async def create_surge_rule(
    payload: SurgeRulePayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    zone_id = payload.zone_id
    if zone_id is None and payload.zone_name:
        from sqlalchemy import select
        from backend.domain.models import GeofenceZone
        zone = (await db.execute(select(GeofenceZone).where(GeofenceZone.name == payload.zone_name))).scalar_one_or_none()
        zone_id = zone.id if zone else None
    rule = await AdminAdvancedService.create_surge_rule(
        session=db,
        zone_id=zone_id,
        category_id=payload.category_id,
        multiplier=payload.multiplier,
        reason=payload.reason,
        admin_user_id=admin.id,
        starts_at=payload.starts_at,
        ends_at=payload.ends_at,
    )
    await db.commit()
    return {"status": "CREATED", "rule_id": rule.id, "multiplier": rule.multiplier}

@router.get("/surge/rules")
async def list_surge_rules(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    rules = await AdminAdvancedService.list_surge_rules(db)
    return [
        {
            "id": r.id,
            "zone_id": r.zone_id,
            "category_id": r.category_id,
            "multiplier": r.multiplier,
            "max_multiplier": r.max_multiplier,
            "reason": r.reason,
            "is_active": r.is_active
        }
        for r in rules
    ]

# 5. Geo-Fencing
@router.post("/geofences")
async def create_geofence(
    payload: GeofencePayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    result = await AdminGovernanceService(db).save_geofence(admin.id, None, {
        "name": payload.name, "zone_type": payload.zone_type, "coordinates": payload.coordinates,
        "surge_multiplier": payload.surge_multiplier, "is_active": True,
    })
    return {**result, "status": "CREATED", "zone_id": result["id"]}

@router.get("/geofences")
async def list_geofences(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    zones = await AdminAdvancedService.list_geofences(db)
    import json
    return [
        {
            "id": z.id,
            "name": z.name,
            "zone_type": z.zone_type,
            "coordinates": json.loads(z.coordinates_json),
            "is_active": z.is_active,
            "surge_multiplier": z.surge_multiplier
        }
        for z in zones
    ]

@router.post("/geofences/verify-point")
async def verify_point_in_geofence(
    payload: VerifyPointPayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    return await AdminAdvancedService.check_location_geofence(db, payload.latitude, payload.longitude)

# 6. Driver Payouts Approval & Settlement
@router.get("/payouts")
async def list_payouts(
    status: Optional[str] = None,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    payouts = await AdminAdvancedService.list_payout_requests(db, status_filter=status)
    return [
        {
            "id": p.id,
            "driver_id": p.driver_id,
            "amount_cents": p.amount_cents,
            "status": p.status,
            "bank_account_mask": p.bank_account_masked,
            "bank_account_masked": p.bank_account_masked,
            "requested_at": p.created_at.isoformat(),
            "reviewed_at": p.reviewed_at.isoformat() if p.reviewed_at else None,
            "rejection_reason": p.rejection_reason,
            "payout_reference": p.payout_reference or p.settlement_reference,
            "settlement_reference": p.settlement_reference or p.payout_reference
        }
        for p in payouts
    ]

@router.post("/payouts/{payout_id}/approve")
async def approve_payout(
    payout_id: str,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    payout = await AdminAdvancedService.approve_payout(db, payout_id, admin.id)
    await db.commit()
    return {"status": "APPROVED", "payout_id": payout.id}

@router.post("/payouts/{payout_id}/reject")
async def reject_payout(
    payout_id: str,
    payload: PayoutRejectPayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    payout = await AdminAdvancedService.reject_payout(db, payout_id, admin.id, payload.rejection_reason)
    await db.commit()
    return {"status": "REJECTED", "payout_id": payout.id}

@router.post("/payouts/{payout_id}/settle")
async def settle_payout(
    payout_id: str,
    payload: PayoutSettlePayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    payout = await AdminAdvancedService.mark_payout_paid(db, payout_id, admin.id, payload.settlement_reference)
    await db.commit()
    return {"status": "PAID", "payout_id": payout.id, "settlement_reference": payout.payout_reference}

# 7. Commission Settings
@router.post("/commissions")
async def create_commission_rule(
    payload: CommissionRulePayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.admin_finance_support_service import AdminFinanceSupportService
    rule = await AdminFinanceSupportService(db).create_commission_draft(
        admin.id, payload.category_id, payload.commission_percentage, None, payload.reason)
    return {"status": "PENDING_APPROVAL", "rule_id": rule["id"], "version": rule["version"], "commission_percentage": payload.commission_percentage}

@router.get("/commissions")
async def list_commission_rules(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.admin_finance_support_service import AdminFinanceSupportService
    rules = await AdminFinanceSupportService(db).commission_rules()
    return [{**r, "commission_percentage": r["commission_percent"]} for r in rules]

# 8. Phase 3: Analytics Reports
@router.get("/reports")
async def get_admin_reports(
    start_date: Optional[str] = Query(None),
    end_date: Optional[str] = Query(None),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    service = AdminService(db)
    return await service.get_analytics_reports(start_date=start_date, end_date=end_date)

# 9. Phase 3: Audit Logs Viewer
@router.get("/audit-logs")
async def get_admin_audit_logs(
    actor_role: Optional[str] = Query(None),
    action: Optional[str] = Query(None),
    target_resource: Optional[str] = Query(None),
    page: int = Query(1, ge=1),
    limit: int = Query(50, ge=1, le=200),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    service = AdminService(db)
    return await service.get_audit_logs(
        actor_role=actor_role,
        action=action,
        target_resource=target_resource,
        page=page,
        limit=limit
    )

# 10. Phase 3: Database Backup & Recovery Control
@router.get("/backups")
async def list_database_backups(
    admin: User = Depends(get_current_admin)
):
    from backend.application.services.backup_service import DatabaseBackupService
    service = DatabaseBackupService()
    return service.list_backups()

@router.post("/backups/trigger")
async def trigger_database_backup(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.backup_service import DatabaseBackupService
    from backend.domain.models import AuditLog

    service = DatabaseBackupService(db)
    snapshot_meta = service.generate_snapshot(trigger_source=f"ADMIN:{admin.id}")

    # Audit log
    audit = AuditLog(
        actor_id=admin.id,
        actor_role="admin",
        action="BACKUP_TRIGGERED",
        target_resource="database_backups",
        target_id=snapshot_meta["backup_id"],
        status="SUCCESS" if snapshot_meta["test_restore_passed"] else "FAILED",
        metadata_json=f'{{"filename": "{snapshot_meta["filename"]}", "sha256": "{snapshot_meta["sha256_checksum"][:16]}..."}}'
    )
    db.add(audit)
    await db.commit()

    return snapshot_meta

# ----------------- Part 4 Disputes, Refunds & Corporate (Features 89, 94, 95, 100) -----------------
class ResolveDisputePayload(BaseModel):
    resolution: str  # APPROVE_REFUND or REJECT
    resolution_notes: str
    refund_amount_cents: Optional[int] = None

@router.get("/disputes")
async def list_all_disputes(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.domain.models import FareDispute
    stmt = select(FareDispute).order_by(desc(FareDispute.created_at))
    res = await db.execute(stmt)
    disputes = res.scalars().all()
    return [
        {
            "id": d.id,
            "ride_id": d.ride_id,
            "customer_id": d.customer_id,
            "dispute_reason": d.dispute_reason,
            "claimed_amount_rupees": round(d.claimed_amount_cents / 100, 2),
            "customer_comment": d.customer_comment,
            "status": d.status,
            "admin_resolution_notes": d.admin_resolution_notes,
            "created_at": d.created_at.isoformat()
        }
        for d in disputes
    ]

@router.post("/disputes/{dispute_id}/resolve")
async def resolve_fare_dispute(
    dispute_id: str,
    payload: ResolveDisputePayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.settlement_advanced_service import SettlementAdvancedService
    service = SettlementAdvancedService(db)
    return await service.admin_resolve_dispute(
        dispute_id=dispute_id,
        admin_id=admin.id,
        resolution=payload.resolution,
        resolution_notes=payload.resolution_notes,
        refund_amount_cents=payload.refund_amount_cents
    )

@router.get("/refunds")
async def list_all_refunds(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.domain.models import PaymentRefund
    stmt = select(PaymentRefund).order_by(desc(PaymentRefund.created_at))
    res = await db.execute(stmt)
    refunds = res.scalars().all()
    return [
        {
            "id": r.id,
            "payment_id": r.payment_id,
            "ride_id": r.ride_id,
            "customer_id": r.customer_id,
            "amount_rupees": round(r.amount_cents / 100, 2),
            "is_partial": r.is_partial,
            "status": r.status,
            "arn_reference": r.arn_reference,
            "reason": r.reason,
            "created_at": r.created_at.isoformat()
        }
        for r in refunds
    ]

@router.get("/corporate-billings")
async def list_corporate_billings(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.domain.models import Ride
    stmt = select(Ride).where(Ride.payment_method == "CORPORATE_BILLING").order_by(desc(Ride.requested_at))
    res = await db.execute(stmt)
    rides = res.scalars().all()
    return [
        {
            "ride_id": r.id,
            "cost_center": r.business_cost_center or "CC-CORP-EXEC",
            "project_code": r.business_project_code or "PRJ-GLOBAL",
            "tax_id": r.business_tax_id or "27AAACG1234F1Z8",
            "billing_ref": r.corporate_billing_ref or f"CORP-{r.id[:8].upper()}",
            "amount_rupees": round((r.final_fare_cents or r.estimated_fare_cents) / 100, 2),
            "passenger_name": r.passenger_name or "Executive Passenger",
            "date": r.requested_at.isoformat()
        }
        for r in rides
    ]

class AdminCorporateDecisionPayload(BaseModel):
    reason: Optional[str] = None

class AdminCorporateInvoicePayload(BaseModel):
    corporate_id: str
    billing_period: str

@router.get("/corporate/accounts")
async def admin_list_corporate_accounts(
    status_filter: Optional[str] = None,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.domain.models import CorporateAccount
    stmt = select(CorporateAccount)
    if status_filter:
        stmt = stmt.where(CorporateAccount.status == status_filter.upper())
    stmt = stmt.order_by(desc(CorporateAccount.created_at))
    res = await db.execute(stmt)
    accounts = res.scalars().all()
    return [
        {
            "id": a.id,
            "company_name": a.company_name,
            "trade_name": a.trade_name,
            "tax_id": a.tax_id,
            "pan": a.pan,
            "billing_email": a.billing_email,
            "billing_contact_name": a.billing_contact_name,
            "billing_contact_phone": a.billing_contact_phone,
            "status": a.status,
            "credit_limit_rupees": round((a.credit_limit_cents or 0) / 100, 2),
            "balance_due_rupees": round((a.balance_due_cents or 0) / 100, 2),
            "created_at": a.created_at.isoformat() if a.created_at else None
        }
        for a in accounts
    ]

@router.get("/corporate/accounts/{account_id}")
async def admin_get_corporate_account(
    account_id: str,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.corporate_billing_service import CorporateBillingService
    service = CorporateBillingService(db)
    return await service.get_account_details(account_id, admin.id)

@router.post("/corporate/accounts/{account_id}/approve")
async def admin_approve_corporate_account(
    account_id: str,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.domain.models import CorporateAccount
    stmt = select(CorporateAccount).where(CorporateAccount.id == account_id)
    corp = (await db.execute(stmt)).scalar_one_or_none()
    if not corp:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Corporate account not found.")

    corp.status = "APPROVED"
    corp.rejection_reason = None
    corp.approved_by = admin.id
    corp.approved_at = datetime.now(timezone.utc)
    corp.updated_at = datetime.now(timezone.utc)
    await db.commit()
    return {"message": f"Corporate account '{corp.company_name}' approved successfully.", "status": "APPROVED"}

@router.post("/corporate/accounts/{account_id}/reject")
async def admin_reject_corporate_account(
    account_id: str,
    payload: AdminCorporateDecisionPayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.domain.models import CorporateAccount
    stmt = select(CorporateAccount).where(CorporateAccount.id == account_id)
    corp = (await db.execute(stmt)).scalar_one_or_none()
    if not corp:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Corporate account not found.")

    corp.status = "REJECTED"
    corp.rejection_reason = payload.reason or "Statutory verification failed or incomplete documentation."
    corp.updated_at = datetime.now(timezone.utc)
    await db.commit()
    return {"message": f"Corporate account '{corp.company_name}' rejected.", "status": "REJECTED", "reason": corp.rejection_reason}

@router.post("/corporate/accounts/{account_id}/suspend")
async def admin_suspend_corporate_account(
    account_id: str,
    payload: AdminCorporateDecisionPayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.domain.models import CorporateAccount
    stmt = select(CorporateAccount).where(CorporateAccount.id == account_id)
    corp = (await db.execute(stmt)).scalar_one_or_none()
    if not corp:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Corporate account not found.")

    corp.status = "SUSPENDED"
    corp.suspension_reason = payload.reason or "Account suspended by platform administration."
    corp.updated_at = datetime.now(timezone.utc)
    await db.commit()
    return {"message": f"Corporate account '{corp.company_name}' suspended.", "status": "SUSPENDED"}

@router.post("/corporate/accounts/{account_id}/reactivate")
async def admin_reactivate_corporate_account(
    account_id: str,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.domain.models import CorporateAccount
    stmt = select(CorporateAccount).where(CorporateAccount.id == account_id)
    corp = (await db.execute(stmt)).scalar_one_or_none()
    if not corp:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Corporate account not found.")

    corp.status = "APPROVED"
    corp.suspension_reason = None
    corp.updated_at = datetime.now(timezone.utc)
    await db.commit()
    return {"message": f"Corporate account '{corp.company_name}' reactivated.", "status": "APPROVED"}

@router.post("/corporate/invoices/generate")
async def admin_generate_corporate_invoice(
    payload: AdminCorporateInvoicePayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.corporate_billing_service import CorporateBillingService
    service = CorporateBillingService(db)
    return await service.generate_consolidated_invoice(
        corporate_id=payload.corporate_id,
        billing_period=payload.billing_period
    )

@router.get("/roles")
async def list_admin_roles(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.domain.models import AdminRole
    stmt = select(AdminRole).order_by(AdminRole.name)
    res = await db.execute(stmt)
    roles = res.scalars().all()
    return [
        {
            "id": r.id,
            "name": r.name,
            "description": r.description,
            "is_superuser": r.is_superuser,
            "created_at": r.created_at.isoformat() if r.created_at else None
        }
        for r in roles
    ]

@router.get("/permissions")
async def list_admin_permissions(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.domain.models import AdminPermission
    stmt = select(AdminPermission).order_by(AdminPermission.category, AdminPermission.permission_key)
    res = await db.execute(stmt)
    perms = res.scalars().all()
    return [
        {
            "id": p.id,
            "permission_key": p.permission_key,
            "description": p.description,
            "category": p.category
        }
        for p in perms
    ]


# --- In-Cabin Audio Safety Recording Admin Governance ---

class AdminAudioPlaybackRequest(BaseModel):
    segment_id: str
    justification_reason: str = Field(..., min_length=10)


@router.get("/audio-recordings")
async def admin_list_audio_recordings(
    status: Optional[str] = None,
    limit: int = 50,
    offset: int = 0,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.audio_recording_service import AudioRecordingService
    service = AudioRecordingService(db)
    return await service.admin_list_sessions(status_filter=status, limit=limit, offset=offset)


@router.get("/audio-recordings/{session_id}")
async def admin_get_audio_recording_details(
    session_id: str,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.audio_recording_service import AudioRecordingService
    service = AudioRecordingService(db)
    return await service.admin_get_session_details(session_id=session_id, admin_user=admin)


@router.post("/audio-recordings/{session_id}/playback")
async def admin_authorize_audio_playback(
    session_id: str,
    payload: AdminAudioPlaybackRequest,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.audio_recording_service import AudioRecordingService
    service = AudioRecordingService(db)
    return await service.admin_authorize_segment_playback(
        session_id=session_id,
        segment_id=payload.segment_id,
        admin_user=admin,
        justification_reason=payload.justification_reason
    )


@router.post("/audio-recordings/purge")
async def admin_purge_expired_audio(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.audio_recording_service import AudioRecordingService
    service = AudioRecordingService(db)
    return await service.purge_expired_recordings()


# --- Financial Operations: Payments Search, Refunds, Webhooks & Reconciliation ---

class AdminInitiateRefundPayload(BaseModel):
    amount_cents: int = Field(..., gt=0)
    reason: str = Field(..., min_length=5, max_length=255)
    is_partial: bool = True
    idempotency_key: Optional[str] = Field(None, min_length=8, max_length=128)


class AdminReviewRefundPayload(BaseModel):
    approve: bool
    reason: str = Field(..., min_length=10, max_length=500)


class AdminResolveAnomalyPayload(BaseModel):
    resolution_notes: str = Field(..., min_length=5, max_length=500)


@router.get("/payments")
async def admin_list_payments(
    q: Optional[str] = None,
    status_filter: Optional[str] = None,
    purpose: Optional[str] = None,
    limit: int = Query(100, ge=1, le=500),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db),
):
    from backend.application.services.admin_finance_support_service import AdminFinanceSupportService
    return await AdminFinanceSupportService(db).search_payments(
        query=q,
        status_filter=status_filter,
        purpose_filter=purpose,
        limit=limit,
    )


@router.post("/payments/{payment_id}/refund")
async def admin_initiate_refund(
    payment_id: str,
    payload: AdminInitiateRefundPayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db),
):
    from backend.application.services.settlement_advanced_service import SettlementAdvancedService
    result = await SettlementAdvancedService(db).create_refund(
        payment_id=payment_id,
        amount_cents=payload.amount_cents,
        reason=payload.reason,
        is_partial=payload.is_partial,
        idempotency_key=payload.idempotency_key,
        allow_debt_clawback=True,
        actor_admin_id=admin.id,
    )
    db.add(
        AdministrativeAuditLog(
            admin_user_id=admin.id,
            action="ADMIN_REFUND_INITIATED",
            target_resource="payments",
            target_id=payment_id,
            changes_json=f'{{"amount_cents": {payload.amount_cents}, "status": "{result.get("status")}"}}',
        )
    )
    await db.commit()
    return result


@router.get("/refund-requests")
async def admin_list_refund_requests(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db),
):
    from backend.application.services.admin_finance_support_service import AdminFinanceSupportService
    return await AdminFinanceSupportService(db).refund_requests()


@router.post("/refunds/{refund_id}/review")
async def admin_review_refund(
    refund_id: str,
    payload: AdminReviewRefundPayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db),
):
    from backend.application.services.admin_finance_support_service import AdminFinanceSupportService
    return await AdminFinanceSupportService(db).review_refund(
        admin_id=admin.id,
        refund_id=refund_id,
        approve=payload.approve,
        reason=payload.reason,
    )


@router.get("/webhooks")
async def admin_list_webhooks(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db),
):
    from backend.application.services.admin_finance_support_service import AdminFinanceSupportService
    return await AdminFinanceSupportService(db).webhook_inbox()


@router.post("/webhooks/{inbox_id}/replay")
async def admin_replay_webhook(
    inbox_id: str,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db),
):
    from backend.application.services.admin_finance_support_service import AdminFinanceSupportService
    return await AdminFinanceSupportService(db).replay_webhook(admin_id=admin.id, inbox_id=inbox_id)


@router.get("/reconciliation/anomalies")
async def admin_list_reconciliation_anomalies(
    status_filter: Optional[str] = None,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db),
):
    from backend.application.services.admin_finance_support_service import AdminFinanceSupportService
    return await AdminFinanceSupportService(db).list_reconciliation_anomalies(status_filter=status_filter)


@router.post("/reconciliation/anomalies/{anomaly_id}/resolve")
async def admin_resolve_reconciliation_anomaly(
    anomaly_id: str,
    payload: AdminResolveAnomalyPayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db),
):
    from backend.application.services.admin_finance_support_service import AdminFinanceSupportService
    return await AdminFinanceSupportService(db).resolve_reconciliation_anomaly(
        admin_id=admin.id,
        anomaly_id=anomaly_id,
        notes=payload.resolution_notes,
    )


@router.post("/reconciliation/run")
async def admin_run_reconciliation(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db),
):
    from backend.application.services.payment_reconciliation_service import PaymentReconciliationService
    lost = await PaymentReconciliationService.reconcile_lost_orders()
    await PaymentReconciliationService.reconcile_pending_payments()
    refunds = await PaymentReconciliationService.reconcile_pending_refunds()
    liabilities = await PaymentReconciliationService.reconcile_refund_liabilities()
    db.add(
        AdministrativeAuditLog(
            admin_user_id=admin.id,
            action="RECONCILIATION_TRIGGERED",
            target_resource="payments",
            target_id="batch",
            changes_json='{"source": "admin_console"}',
        )
    )
    await db.commit()
    return {
        "status": "COMPLETED",
        "lost_orders": lost,
        "refunds": refunds,
        "liabilities": liabilities,
    }





# --- Customer KYC: restricted manual review and audited document access ---

class AdminCustomerKycReviewPayload(BaseModel):
    approved: bool
    reason: Optional[str] = Field(None, max_length=500)


@router.get("/customer-kyc/documents")
async def admin_list_customer_kyc_documents(
    status_filter: Optional[str] = Query(None),
    limit: int = Query(50, ge=1, le=200),
    offset: int = Query(0, ge=0),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db),
):
    allowed_statuses = {"PENDING_REVIEW", "APPROVED", "REJECTED", "WITHDRAWN"}
    normalized_status = status_filter.strip().upper() if status_filter else None
    if normalized_status and normalized_status not in allowed_statuses:
        raise HTTPException(status_code=400, detail="Invalid customer KYC status filter.")

    stmt = (
        select(CustomerKycDocument, User)
        .join(User, User.id == CustomerKycDocument.user_id)
        .order_by(desc(CustomerKycDocument.submitted_at))
        .limit(limit)
        .offset(offset)
    )
    if normalized_status:
        stmt = stmt.where(CustomerKycDocument.status == normalized_status)
    rows = (await db.execute(stmt)).all()
    return [
        {
            "id": document.id,
            "customer_id": customer.id,
            "customer_name": customer.full_name,
            "customer_phone": customer.phone_number,
            "customer_email": customer.email,
            "document_type": document.document_type,
            "status": document.status,
            "submitted_at": document.submitted_at.isoformat() if document.submitted_at else None,
            "reviewed_at": document.reviewed_at.isoformat() if document.reviewed_at else None,
            "review_notes": document.review_notes,
        }
        for document, customer in rows
    ]


@router.get("/customer-kyc/documents/{document_id}/download")
async def admin_get_customer_kyc_document_download(
    document_id: str,
    request: Request,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db),
):
    document = (
        await db.execute(select(CustomerKycDocument).where(CustomerKycDocument.id == document_id))
    ).scalar_one_or_none()
    if not document or document.status == "WITHDRAWN":
        raise HTTPException(status_code=404, detail="Customer KYC document not found.")
    from backend.integrations.storage.customer_kyc import customer_kyc_storage
    signed_url = await customer_kyc_storage.create_signed_url(document.storage_path, expires_in_seconds=60)
    db.add(
        CustomerKycAuditLog(
            document_id=document.id,
            actor_id=admin.id,
            actor_role="ADMIN",
            action="DOCUMENT_SIGNED_URL_ISSUED",
            metadata_json='{"expires_in_seconds":60}',
            ip_address=request.client.host if request.client else None,
            user_agent=(request.headers.get("user-agent") or "")[:255] or None,
        )
    )
    await db.commit()
    return {"url": signed_url, "expires_in_seconds": 60}


@router.post("/customer-kyc/documents/{document_id}/review")
async def admin_review_customer_kyc_document(
    document_id: str,
    payload: AdminCustomerKycReviewPayload,
    request: Request,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db),
):
    document = (
        await db.execute(
            select(CustomerKycDocument)
            .where(CustomerKycDocument.id == document_id)
            .with_for_update()
        )
    ).scalar_one_or_none()
    if not document:
        raise HTTPException(status_code=404, detail="Customer KYC document not found.")
    if document.status != "PENDING_REVIEW":
        raise HTTPException(status_code=409, detail="Only pending customer KYC documents can be reviewed.")

    reason = (payload.reason or "").strip()
    if not payload.approved and len(reason) < 5:
        raise HTTPException(status_code=422, detail="A rejection reason of at least 5 characters is required.")

    now = datetime.now(timezone.utc)
    document.status = "APPROVED" if payload.approved else "REJECTED"
    document.reviewed_by_admin_id = admin.id
    document.reviewed_at = now
    document.review_notes = reason[:500] or None
    db.add(
        CustomerKycAuditLog(
            document_id=document.id,
            actor_id=admin.id,
            actor_role="ADMIN",
            action="DOCUMENT_APPROVED" if payload.approved else "DOCUMENT_REJECTED",
            reason=reason[:500] or None,
            metadata_json='{"manual_review":true,"user_is_verified_changed":false}',
            ip_address=request.client.host if request.client else None,
            user_agent=(request.headers.get("user-agent") or "")[:255] or None,
        )
    )
    await db.commit()
    return {
        "document_id": document.id,
        "status": document.status,
        "reviewed_at": now.isoformat(),
        "message": "Customer document review saved. Account-wide verification status was not changed.",
    }
