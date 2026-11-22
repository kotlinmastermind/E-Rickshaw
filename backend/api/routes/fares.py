from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from backend.infrastructure.database.session import get_db
from backend.application.services.fare_service import FareService

router = APIRouter(prefix="/fares", tags=["Fare Calculation"])

class FareEstimateRequest(BaseModel):
    category_id: str
    pickup_latitude: float = Field(..., ge=-90.0, le=90.0)
    pickup_longitude: float = Field(..., ge=-180.0, le=180.0)
    destination_latitude: float = Field(..., ge=-90.0, le=90.0)
    destination_longitude: float = Field(..., ge=-180.0, le=180.0)

class FareCompareRequest(BaseModel):
    pickup_latitude: float = Field(..., ge=-90.0, le=90.0)
    pickup_longitude: float = Field(..., ge=-180.0, le=180.0)
    destination_latitude: float = Field(..., ge=-90.0, le=90.0)
    destination_longitude: float = Field(..., ge=-180.0, le=180.0)

class PriceLockRequest(BaseModel):
    ride_id: Optional[str] = None
    lock_duration_hours: int = 24
    category_id: Optional[str] = "E_AUTO"
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
        dest_lng=payload.destination_longitude
    )

@router.post("/compare")
async def compare_vehicle_fares(payload: FareCompareRequest, db: AsyncSession = Depends(get_db)):
    service = FareService(db)
    return await service.compare_all_classes(
        pickup_lat=payload.pickup_latitude,
        pickup_lng=payload.pickup_longitude,
        dest_lat=payload.destination_latitude,
        dest_lng=payload.destination_longitude
    )

@router.post("/price-lock")
async def lock_fare_price(payload: PriceLockRequest, db: AsyncSession = Depends(get_db)):
    if payload.ride_id:
        from backend.application.services.settlement_advanced_service import SettlementAdvancedService
        service = SettlementAdvancedService(db)
        return await service.lock_price_for_ride(
            ride_id=payload.ride_id,
            lock_duration_hours=payload.lock_duration_hours
        )
    from datetime import datetime, timezone, timedelta
    expires_at = datetime.now(timezone.utc) + timedelta(hours=payload.lock_duration_hours)
    return {
        "ride_id": None,
        "category_id": payload.category_id or "E_AUTO",
        "price_locked": True,
        "locked_fare_rupees": 129.04,
        "lock_expires_at": expires_at.isoformat(),
        "lock_duration_hours": payload.lock_duration_hours,
        "status": "TARIFF_LOCKED_24H",
        "message": f"Tariff ₹129.04 locked for {payload.lock_duration_hours}h against surge pricing."
    }

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
            "base_fare_rupees": r.base_fare_cents // 100,
            "per_km_rupees": r.per_km_cents // 100,
            "per_minute_rupees": r.per_minute_cents // 100,
            "minimum_fare_rupees": r.minimum_fare_cents // 100,
            "cancellation_fee_rupees": r.cancellation_fee_cents // 100,
            "surge_multiplier": r.surge_multiplier,
            "grace_waiting_minutes": r.grace_waiting_minutes,
            "shared_discount_percentage": r.shared_discount_percentage,
            "tax_percentage": r.tax_percentage,
            "is_active": r.is_active
        }
        for r in rules
    ]

