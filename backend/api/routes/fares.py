from typing import Optional, List, Dict, Any
import uuid
from datetime import datetime, timezone, timedelta
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from backend.infrastructure.database.session import get_db
from backend.application.services.fare_service import FareService
from backend.domain.models import FarePriceLock, FareEstimate

router = APIRouter(prefix="/fares", tags=["Fare Calculation"])

class FareEstimateRequest(BaseModel):
    category_id: str
    pickup_latitude: float = Field(..., ge=-90.0, le=90.0)
    pickup_longitude: float = Field(..., ge=-180.0, le=180.0)
    destination_latitude: float = Field(..., ge=-90.0, le=90.0)
    destination_longitude: float = Field(..., ge=-180.0, le=180.0)
    city_code: Optional[str] = None

class FareCompareRequest(BaseModel):
    pickup_latitude: float = Field(..., ge=-90.0, le=90.0)
    pickup_longitude: float = Field(..., ge=-180.0, le=180.0)
    destination_latitude: float = Field(..., ge=-90.0, le=90.0)
    destination_longitude: float = Field(..., ge=-180.0, le=180.0)
    city_code: Optional[str] = None

class PriceLockRequest(BaseModel):
    ride_id: Optional[str] = None
    lock_duration_hours: int = 24
    category_id: Optional[str] = "E_AUTO"
    city_code: Optional[str] = None
    pickup_latitude: Optional[float] = None
    pickup_longitude: Optional[float] = None
    destination_latitude: Optional[float] = None
    destination_longitude: Optional[float] = None

class RouteChangeAdjustmentRequest(BaseModel):
    ride_id: str
    new_distance_km: float
    new_duration_minutes: float
    reason: str

@router.post("/estimate")
async def calculate_fare_estimate(payload: FareEstimateRequest, db: AsyncSession = Depends(get_db)):
    service = FareService(db)
    return await service.calculate_estimate(
        category_id=payload.category_id,
        pickup_lat=payload.pickup_latitude,
        pickup_lng=payload.pickup_longitude,
        dest_lat=payload.destination_latitude,
        dest_lng=payload.destination_longitude,
        city_code=payload.city_code
    )

@router.post("/compare")
async def compare_vehicle_fares(payload: FareCompareRequest, db: AsyncSession = Depends(get_db)):
    service = FareService(db)
    return await service.compare_all_classes(
        pickup_lat=payload.pickup_latitude,
        pickup_lng=payload.pickup_longitude,
        dest_lat=payload.destination_latitude,
        dest_lng=payload.destination_longitude,
        city_code=payload.city_code
    )

@router.post("/price-lock")
async def lock_fare_price(payload: PriceLockRequest, db: AsyncSession = Depends(get_db)):
    if payload.lock_duration_hours < 1 or payload.lock_duration_hours > 24:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Price lock duration must be between 1 and 24 hours per platform governance policy."
        )
    if payload.ride_id:
        from backend.application.services.settlement_advanced_service import SettlementAdvancedService
        service = SettlementAdvancedService(db)
        return await service.lock_price_for_ride(
            ride_id=payload.ride_id,
            lock_duration_hours=payload.lock_duration_hours
        )
    if (
        payload.pickup_latitude is not None
        and payload.pickup_longitude is not None
        and payload.destination_latitude is not None
        and payload.destination_longitude is not None
        and payload.category_id
    ):
        service = FareService(db)
        estimate = await service.calculate_estimate(
            category_id=payload.category_id,
            pickup_lat=payload.pickup_latitude,
            pickup_lng=payload.pickup_longitude,
            dest_lat=payload.destination_latitude,
            dest_lng=payload.destination_longitude,
            city_code=payload.city_code
        )
        expires_at = datetime.now(timezone.utc) + timedelta(hours=payload.lock_duration_hours)
        
        # Resolve vehicle category UUID from estimate
        est_obj = (await db.execute(select(FareEstimate).where(FareEstimate.id == estimate["estimate_id"]))).scalar_one_or_none()
        real_category_id = est_obj.category_id if est_obj else payload.category_id

        price_lock = FarePriceLock(
            id=str(uuid.uuid4()),
            customer_id=None,
            category_id=real_category_id,
            city_code=estimate.get("city_code", "DELHI_NCR"),
            fare_rule_id=estimate["fare_rule_id"],
            fare_rule_version=estimate.get("fare_rule_version", 1),
            pickup_latitude=payload.pickup_latitude,
            pickup_longitude=payload.pickup_longitude,
            destination_latitude=payload.destination_latitude,
            destination_longitude=payload.destination_longitude,
            distance_km=estimate.get("distance_km", 0.0),
            duration_minutes=estimate.get("estimated_duration_minutes", 0.0),
            locked_fare_cents=estimate["estimated_fare_cents"],
            surge_multiplier=estimate.get("surge_multiplier", 1.0),
            lock_duration_hours=payload.lock_duration_hours,
            status="ACTIVE",
            expires_at=expires_at
        )
        db.add(price_lock)
        await db.commit()
        await db.refresh(price_lock)

        locked_fare_rupees = round(price_lock.locked_fare_cents / 100.0, 2)
        lock_status_str = f"TARIFF_LOCKED_{price_lock.lock_duration_hours}H"
        return {
            "lock_id": price_lock.id,
            "estimate_id": estimate["estimate_id"],
            "category_id": real_category_id,
            "city_code": price_lock.city_code,
            "price_locked": True,
            "locked_fare_cents": price_lock.locked_fare_cents,
            "locked_fare_rupees": locked_fare_rupees,
            "lock_expires_at": price_lock.expires_at.isoformat(),
            "lock_duration_hours": price_lock.lock_duration_hours,
            "status": lock_status_str,
            "message": f"Tariff ₹{locked_fare_rupees:.2f} locked for {price_lock.lock_duration_hours}h against surge pricing."
        }
    raise HTTPException(
        status_code=status.HTTP_400_BAD_REQUEST,
        detail="Either ride_id or valid route coordinates (pickup, destination) and category_id must be provided to lock fare price."
    )

@router.post("/route-change-adjustment")
async def adjust_route_fare(payload: RouteChangeAdjustmentRequest, db: AsyncSession = Depends(get_db)):
    from backend.application.services.settlement_advanced_service import SettlementAdvancedService
    service = SettlementAdvancedService(db)
    return await service.explain_and_apply_route_fare_change(
        ride_id=payload.ride_id,
        new_distance_km=payload.new_distance_km,
        new_duration_minutes=payload.new_duration_minutes,
        reason=payload.reason
    )

@router.get("/rules")
async def get_active_fare_rules(db: AsyncSession = Depends(get_db)):
    from sqlalchemy import select
    from backend.domain.models import FareRule
    stmt = select(FareRule).where(FareRule.is_active == True)
    result = await db.execute(stmt)
    rules = result.scalars().all()
    return [
        {
            "id": r.id,
            "category_id": r.category_id,
            "version": r.version,
            "base_fare_rupees": round(r.base_fare_cents / 100.0, 2),
            "per_km_rupees": round(r.per_km_cents / 100.0, 2),
            "per_minute_rupees": round(r.per_minute_cents / 100.0, 2),
            "minimum_fare_rupees": round(r.minimum_fare_cents / 100.0, 2),
            "cancellation_fee_rupees": round(r.cancellation_fee_cents / 100.0, 2),
            "surge_multiplier": r.surge_multiplier,
            "grace_waiting_minutes": r.grace_waiting_minutes,
            "shared_discount_percentage": r.shared_discount_percentage,
            "tax_percentage": r.tax_percentage,
            "is_active": r.is_active
        }
        for r in rules
    ]

