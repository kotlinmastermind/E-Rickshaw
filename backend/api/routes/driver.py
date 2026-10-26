from typing import Optional, List, Dict, Any
from fastapi import APIRouter, Depends, HTTPException, status, UploadFile, File, Form
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from backend.infrastructure.database.session import get_db
from backend.api.deps import get_current_driver
from backend.domain.models import User
from backend.application.services.driver_service import DriverService

router = APIRouter(prefix="/drivers", tags=["Driver Operations"])

class AvailabilityPayload(BaseModel):
    is_online: bool

class LocationPingPayload(BaseModel):
    latitude: float
    longitude: float
    heading: Optional[float] = None

class DriverProfileUpdate(BaseModel):
    full_name: Optional[str] = None
    email: Optional[str] = None

@router.get("/profile")
async def get_driver_profile(user: User = Depends(get_current_driver), db: AsyncSession = Depends(get_db)):
    service = DriverService(db)
    driver = await service.get_or_create_profile(user.id)
    return {
        "user_id": user.id,
        "driver_id": driver.id,
        "full_name": user.full_name,
        "phone_number": user.phone_number,
        "license_number": driver.license_number,
        "approval_status": driver.approval_status,
        "rating_average": driver.rating_average,
        "rating_count": driver.rating_count,
        "total_trips": driver.total_trips,
        "wallet_balance_cents": driver.wallet_balance_cents
    }

@router.put("/profile")
async def update_driver_profile(
    payload: DriverProfileUpdate,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    if payload.full_name is not None:
        user.full_name = payload.full_name
    if payload.email is not None:
        user.email = payload.email
    await db.commit()
    return {"message": "Chauffeur profile updated."}

@router.post("/documents")
async def upload_document(
    document_type: str = Form(..., description="DRIVING_LICENSE, VEHICLE_REGISTRATION, INSURANCE, IDENTITY_PROOF"),
    file: UploadFile = File(...),
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    service = DriverService(db)
    return await service.upload_document(user.id, document_type, file)

@router.get("/documents")
async def get_documents(user: User = Depends(get_current_driver), db: AsyncSession = Depends(get_db)):
    service = DriverService(db)
    return await service.get_documents(user.id)

@router.post("/availability")
async def toggle_availability(
    payload: AvailabilityPayload,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    service = DriverService(db)
    return await service.toggle_availability(user.id, payload.is_online)

@router.post("/location")
async def update_location(
    payload: LocationPingPayload,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    service = DriverService(db)
    return await service.update_location(user.id, payload.latitude, payload.longitude, payload.heading)

@router.get("/earnings")
async def get_earnings(user: User = Depends(get_current_driver), db: AsyncSession = Depends(get_db)):
    service = DriverService(db)
    return await service.get_earnings_summary(user.id)

@router.get("/wallet")
async def get_wallet(user: User = Depends(get_current_driver), db: AsyncSession = Depends(get_db)):
    service = DriverService(db)
    summary = await service.get_earnings_summary(user.id)
    return {
        "wallet_balance_cents": summary["wallet_balance_cents"],
        "currency": "INR",
        "pending_settlement_cents": 0,
        "available_for_payout": True
    }

from backend.application.services.dispatch_advanced_service import DispatchAdvancedService

class AutoAcceptPayload(BaseModel):
    enabled: bool
    max_distance_km: float = 5.0

class RouteOptPayload(BaseModel):
    origin_lat: float
    origin_lng: float
    stops: List[dict]
    destination_lat: float
    destination_lng: float

class PayoutRequestPayload(BaseModel):
    amount_cents: int = Field(..., gt=0, json_schema_extra={"example": 1000000})
    account_number_mask: str = Field("**** 9988", json_schema_extra={"example": "**** 9988"})
    ifsc: str = Field("HDFC0001234", json_schema_extra={"example": "HDFC0001234"})

@router.get("/heat-map")
async def get_heat_map(
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    return await DispatchAdvancedService.get_privacy_preserving_heatmap(db)

@router.get("/scheduled/opportunities")
async def get_scheduled_opportunities(
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    rides = await DispatchAdvancedService.get_driver_scheduled_opportunities(db, user.id)
    return [
        {
            "id": r.id,
            "pickup_address": r.pickup_address,
            "destination_address": r.destination_address,
            "scheduled_pickup_time": r.scheduled_pickup_time.isoformat() if r.scheduled_pickup_time else None,
            "estimated_fare_cents": r.estimated_fare_cents,
            "estimated_distance_km": r.estimated_distance_km,
            "estimated_duration_minutes": r.estimated_duration_minutes
        }
        for r in rides
    ]

@router.post("/scheduled/{ride_id}/reserve")
async def reserve_scheduled_ride(
    ride_id: str,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    ride = await DispatchAdvancedService.driver_reserve_scheduled_ride(db, user.id, ride_id)
    await db.commit()
    return {
        "status": "RESERVED",
        "ride_id": ride.id,
        "scheduled_pickup_time": ride.scheduled_pickup_time.isoformat()
    }

@router.post("/auto-accept")
async def toggle_auto_accept(
    payload: AutoAcceptPayload,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    result = await DispatchAdvancedService.update_auto_accept(
        db, user.id, payload.enabled, payload.max_distance_km
    )
    await db.commit()
    return result

@router.post("/route-optimization")
async def optimize_route(
    payload: RouteOptPayload,
    user: User = Depends(get_current_driver)
):
    return DispatchAdvancedService.calculate_optimized_stop_sequence(
        origin_lat=payload.origin_lat,
        origin_lng=payload.origin_lng,
        stops=payload.stops,
        destination_lat=payload.destination_lat,
        destination_lng=payload.destination_lng
    )

@router.get("/daily-target")
async def get_daily_target(
    target_date: Optional[str] = None,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    target = await DispatchAdvancedService.get_or_create_daily_target(db, user.id, target_date)
    await db.commit()
    return target

@router.post("/payouts/request")
async def request_payout(
    payload: PayoutRequestPayload,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    payout = await DispatchAdvancedService.request_payout(
        session=db,
        driver_id=user.id,
        amount_cents=payload.amount_cents,
        bank_details={"account_number_mask": payload.account_number_mask, "ifsc": payload.ifsc}
    )
    await db.commit()
    return {
        "status": "REQUESTED",
        "payout_id": payout.id,
        "amount_cents": payout.amount_cents,
        "bank_account_mask": payout.bank_account_mask,
        "requested_at": payout.requested_at.isoformat()
    }

@router.get("/payouts/history")
async def get_payout_history(
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    from backend.domain.models import DriverPayoutRequest
    from sqlalchemy import select
    stmt = (
        select(DriverPayoutRequest)
        .where(DriverPayoutRequest.driver_id == user.id)
        .order_by(DriverPayoutRequest.requested_at.desc())
    )
    res = await db.execute(stmt)
    payouts = res.scalars().all()
    return [
        {
            "id": p.id,
            "amount_cents": p.amount_cents,
            "status": p.status,
            "bank_account_mask": p.bank_account_mask,
            "requested_at": p.requested_at.isoformat(),
            "reviewed_at": p.reviewed_at.isoformat() if p.reviewed_at else None,
            "paid_at": p.paid_at.isoformat() if p.paid_at else None,
            "rejection_reason": p.rejection_reason
        }
        for p in payouts
    ]

@router.delete("/account")
async def delete_driver_account(
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    """
    Google Play Compliance: Allows chauffeur partner to delete account and anonymize personal information.
    """
    user.is_active = False
    user.phone_number = f"deleted_driver_{user.id[:8]}_{user.phone_number[-4:]}"
    if user.email:
        user.email = f"deleted_driver_{user.id[:8]}@anonymized.local"
    await db.commit()
    return {"message": "Chauffeur account deactivated and personal details anonymized."}

class DriverDeviceTokenPayload(BaseModel):
    fcm_token: str
    device_os: str = "ANDROID"

@router.post("/device-token")
async def register_driver_device_token(
    payload: DriverDeviceTokenPayload,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    from backend.domain.models import DeviceToken
    from sqlalchemy import select
    stmt = select(DeviceToken).where(DeviceToken.fcm_token == payload.fcm_token)
    res = await db.execute(stmt)
    token_obj = res.scalar_one_or_none()
    if not token_obj:
        token_obj = DeviceToken(
            user_id=user.id,
            fcm_token=payload.fcm_token,
            device_os=payload.device_os
        )
        db.add(token_obj)
    else:
        token_obj.user_id = user.id
        token_obj.device_os = payload.device_os
    await db.commit()
    return {"message": "Driver device token registered."}


