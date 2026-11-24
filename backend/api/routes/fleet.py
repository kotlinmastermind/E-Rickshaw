import uuid
from datetime import datetime, timezone, timedelta
from typing import Optional, List
from fastapi import APIRouter, Depends, Query, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy import select, desc, func, and_, or_, update
from sqlalchemy.ext.asyncio import AsyncSession

from backend.infrastructure.database.session import get_db
from backend.api.deps import get_current_admin
from backend.domain.models import (
    Vehicle, VehicleCategory, DriverVehicleAssignment,
    DriverProfile, User, AdministrativeAuditLog
)

router = APIRouter(prefix="/admin/fleet", tags=["Fleet Inventory"])

# ─── Pydantic Schemas ──────────────────────────────────────────────────────────

class VehicleCreate(BaseModel):
    category_id: str
    make: str = Field(..., min_length=1, max_length=64)
    model: str = Field(..., min_length=1, max_length=64)
    year: int = Field(..., ge=2015, le=2030)
    license_plate: str = Field(..., min_length=4, max_length=32)
    color: str = Field(default="Obsidian Black", max_length=32)
    rc_number: Optional[str] = None
    permit_number: Optional[str] = None
    insurance_policy_number: Optional[str] = None
    puc_expiry_date: Optional[datetime] = None
    fitness_expiry_date: Optional[datetime] = None

class VehicleUpdate(BaseModel):
    make: Optional[str] = None
    model: Optional[str] = None
    year: Optional[int] = None
    color: Optional[str] = None
    rc_number: Optional[str] = None
    permit_number: Optional[str] = None
    insurance_policy_number: Optional[str] = None
    puc_expiry_date: Optional[datetime] = None
    fitness_expiry_date: Optional[datetime] = None
    is_active: Optional[bool] = None

class AssignDriverRequest(BaseModel):
    driver_id: str

# ─── Helpers ──────────────────────────────────────────────────────────────────

def _doc_status(v: Vehicle) -> str:
    now = datetime.now(timezone.utc)
    warn = now + timedelta(days=30)
    if v.fitness_expiry_date and v.fitness_expiry_date < now:
        return "EXPIRED"
    if v.puc_expiry_date and v.puc_expiry_date < now:
        return "EXPIRED"
    if v.fitness_expiry_date and v.fitness_expiry_date < warn:
        return "EXPIRING_SOON"
    if v.puc_expiry_date and v.puc_expiry_date < warn:
        return "EXPIRING_SOON"
    return "VALID"

def _vehicle_to_dict(v: Vehicle, active_driver=None, days_to_expiry=None) -> dict:
    return {
        "id": v.id,
        "category_id": v.category_id,
        "make": v.make,
        "model": v.model,
        "year": v.year,
        "license_plate": v.license_plate,
        "color": v.color,
        "is_active": v.is_active,
        "rc_number": v.rc_number,
        "permit_number": v.permit_number,
        "insurance_policy_number": v.insurance_policy_number,
        "puc_expiry_date": v.puc_expiry_date.isoformat() if v.puc_expiry_date else None,
        "fitness_expiry_date": v.fitness_expiry_date.isoformat() if v.fitness_expiry_date else None,
        "created_at": v.created_at.isoformat(),
        "updated_at": v.updated_at.isoformat(),
        "active_driver": active_driver,
        "days_to_fitness_expiry": days_to_expiry,
        "doc_status": _doc_status(v),
    }

async def _get_active_driver_for_vehicle(db: AsyncSession, vehicle_id: str):
    stmt = (
        select(DriverVehicleAssignment, DriverProfile, User)
        .join(DriverProfile, DriverVehicleAssignment.driver_id == DriverProfile.id)
        .join(User, DriverProfile.user_id == User.id)
        .where(
            DriverVehicleAssignment.vehicle_id == vehicle_id,
            DriverVehicleAssignment.is_active == True,
        )
    )
    res = await db.execute(stmt)
    row = res.first()
    if not row:
        return None
    assignment, driver, user = row
    return {
        "driver_id": driver.id,
        "driver_name": user.full_name,
        "phone": user.phone_number,
        "assigned_at": assignment.assigned_at.isoformat(),
    }

# ─── Endpoints ────────────────────────────────────────────────────────────────

@router.get("/stats")
async def fleet_stats(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db),
):
    total = (await db.execute(select(func.count()).select_from(Vehicle))).scalar_one()
    active_count = (await db.execute(
        select(func.count()).select_from(Vehicle).where(Vehicle.is_active == True)
    )).scalar_one()
    assigned_count = (await db.execute(
        select(func.count()).select_from(DriverVehicleAssignment)
        .where(DriverVehicleAssignment.is_active == True)
    )).scalar_one()
    warn_date = datetime.now(timezone.utc) + timedelta(days=30)
    expiring_count = (await db.execute(
        select(func.count()).select_from(Vehicle).where(
            Vehicle.is_active == True,
            or_(
                and_(Vehicle.fitness_expiry_date != None, Vehicle.fitness_expiry_date < warn_date),
                and_(Vehicle.puc_expiry_date != None, Vehicle.puc_expiry_date < warn_date),
            )
        )
    )).scalar_one()
    return {
        "total_vehicles": total,
        "active_vehicles": active_count,
        "inactive_vehicles": total - active_count,
        "assigned_to_driver": assigned_count,
        "expiring_docs_within_30_days": expiring_count,
    }

@router.get("/vehicles")
async def list_vehicles(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db),
    search: Optional[str] = Query(None),
    is_active: Optional[bool] = Query(None),
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=100),
):
    stmt = select(Vehicle)
    if search:
        q = f"%{search.strip().upper()}%"
        stmt = stmt.where(
            or_(
                Vehicle.license_plate.ilike(q),
                Vehicle.make.ilike(q),
                Vehicle.model.ilike(q),
                Vehicle.rc_number.ilike(q)
            )
        )
    if is_active is not None:
        stmt = stmt.where(Vehicle.is_active == is_active)
    stmt = stmt.order_by(desc(Vehicle.created_at))
    total = (await db.execute(select(func.count()).select_from(stmt.subquery()))).scalar_one()
    stmt = stmt.offset((page - 1) * page_size).limit(page_size)
    vehicles = (await db.execute(stmt)).scalars().all()
    now = datetime.now(timezone.utc)
    result = []
    for v in vehicles:
        active_driver = await _get_active_driver_for_vehicle(db, v.id)
        days_to_expiry = (v.fitness_expiry_date - now).days if v.fitness_expiry_date else None
        result.append(_vehicle_to_dict(v, active_driver, days_to_expiry))
    return {"total": total, "page": page, "page_size": page_size, "items": result}

@router.post("/vehicles", status_code=201)
async def create_vehicle(
    body: VehicleCreate,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db),
):
    clean_plate = body.license_plate.strip().upper().replace(" ", "")
    existing = (await db.execute(
        select(Vehicle).where(Vehicle.license_plate == clean_plate)
    )).scalar_one_or_none()
    if existing:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=f"Vehicle with registration plate '{clean_plate}' already exists.",
        )
    cat = await db.get(VehicleCategory, body.category_id)
    if not cat:
        raise HTTPException(status_code=404, detail="VehicleCategory not found.")

    vehicle = Vehicle(
        id=str(uuid.uuid4()),
        category_id=body.category_id,
        make=body.make.strip(),
        model=body.model.strip(),
        year=body.year,
        license_plate=clean_plate,
        color=body.color,
        rc_number=body.rc_number.strip() if body.rc_number else None,
        permit_number=body.permit_number.strip() if body.permit_number else None,
        insurance_policy_number=body.insurance_policy_number.strip() if body.insurance_policy_number else None,
        puc_expiry_date=body.puc_expiry_date,
        fitness_expiry_date=body.fitness_expiry_date,
        is_active=True,
    )
    db.add(vehicle)
    db.add(AdministrativeAuditLog(
        id=str(uuid.uuid4()),
        admin_user_id=admin.id,
        action="FLEET_VEHICLE_CREATE",
        target_entity="Vehicle",
        target_entity_id=vehicle.id,
        details_json=f'{{"license_plate":"{vehicle.license_plate}"}}'
    ))
    await db.commit()
    await db.refresh(vehicle)
    return _vehicle_to_dict(vehicle)

@router.patch("/vehicles/{vehicle_id}")
async def update_vehicle(
    vehicle_id: str,
    body: VehicleUpdate,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db),
):
    vehicle = await db.get(Vehicle, vehicle_id)
    if not vehicle:
        raise HTTPException(status_code=404, detail="Vehicle not found.")
    for field, value in body.model_dump(exclude_none=True).items():
        setattr(vehicle, field, value)
    db.add(AdministrativeAuditLog(
        id=str(uuid.uuid4()),
        admin_user_id=admin.id,
        action="FLEET_VEHICLE_UPDATE",
        target_entity="Vehicle",
        target_entity_id=vehicle_id,
        details_json=str(body.model_dump(exclude_none=True))
    ))
    await db.commit()
    await db.refresh(vehicle)
    return _vehicle_to_dict(vehicle, await _get_active_driver_for_vehicle(db, vehicle_id))

@router.delete("/vehicles/{vehicle_id}", status_code=204)
async def deactivate_vehicle(
    vehicle_id: str,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db),
):
    vehicle = await db.get(Vehicle, vehicle_id)
    if not vehicle:
        raise HTTPException(status_code=404, detail="Vehicle not found.")
    await db.execute(
        update(DriverVehicleAssignment)
        .where(DriverVehicleAssignment.vehicle_id == vehicle_id,
               DriverVehicleAssignment.is_active == True)
        .values(is_active=False, unassigned_at=datetime.now(timezone.utc))
    )
    vehicle.is_active = False
    db.add(AdministrativeAuditLog(
        id=str(uuid.uuid4()),
        admin_user_id=admin.id,
        action="FLEET_VEHICLE_DEACTIVATE",
        target_entity="Vehicle",
        target_entity_id=vehicle_id,
        details_json='{}'
    ))
    await db.commit()

@router.post("/vehicles/{vehicle_id}/assign")
async def assign_driver(
    vehicle_id: str,
    body: AssignDriverRequest,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db),
):
    vehicle = await db.get(Vehicle, vehicle_id)
    if not vehicle or not vehicle.is_active:
        raise HTTPException(status_code=404, detail="Active vehicle not found.")
    driver = await db.get(DriverProfile, body.driver_id)
    if not driver:
        raise HTTPException(status_code=404, detail="Driver profile not found.")

    # 1. Driver ke purane active assignment ko close karo
    await db.execute(
        update(DriverVehicleAssignment)
        .where(DriverVehicleAssignment.driver_id == body.driver_id,
               DriverVehicleAssignment.is_active == True)
        .values(is_active=False, unassigned_at=datetime.now(timezone.utc))
    )
    # 2. Is vehicle ke purane driver ko unassign karo
    await db.execute(
        update(DriverVehicleAssignment)
        .where(DriverVehicleAssignment.vehicle_id == vehicle_id,
               DriverVehicleAssignment.is_active == True)
        .values(is_active=False, unassigned_at=datetime.now(timezone.utc))
    )
    # 3. Naya active assignment create karo
    new_assignment = DriverVehicleAssignment(
        id=str(uuid.uuid4()),
        driver_id=body.driver_id,
        vehicle_id=vehicle_id,
        is_active=True,
        assigned_at=datetime.now(timezone.utc),
    )
    db.add(new_assignment)
    db.add(AdministrativeAuditLog(
        id=str(uuid.uuid4()),
        admin_user_id=admin.id,
        action="FLEET_DRIVER_ASSIGN",
        target_entity="Vehicle",
        target_entity_id=vehicle_id,
        details_json=f'{{"driver_id":"{body.driver_id}"}}'
    ))
    await db.commit()
    return {"message": "Driver assigned successfully.", "assignment_id": new_assignment.id}

@router.post("/vehicles/{vehicle_id}/unassign")
async def unassign_driver(
    vehicle_id: str,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db),
):
    result = await db.execute(
        update(DriverVehicleAssignment)
        .where(DriverVehicleAssignment.vehicle_id == vehicle_id,
               DriverVehicleAssignment.is_active == True)
        .values(is_active=False, unassigned_at=datetime.now(timezone.utc))
    )
    if result.rowcount == 0:
        raise HTTPException(status_code=404, detail="No active driver assignment found for this vehicle.")
    db.add(AdministrativeAuditLog(
        id=str(uuid.uuid4()),
        admin_user_id=admin.id,
        action="FLEET_DRIVER_UNASSIGN",
        target_entity="Vehicle",
        target_entity_id=vehicle_id,
        details_json='{}'
    ))
    await db.commit()
    return {"message": "Driver unassigned successfully."}