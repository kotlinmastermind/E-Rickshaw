from typing import Optional, List, Dict, Any
from fastapi import APIRouter, Body, Depends, HTTPException, Query, Request, status, UploadFile, File, Form, Response
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from backend.infrastructure.database.session import get_db
from backend.api.deps import get_current_driver
from backend.api.routes.notifications import DeviceTokenDeactivatePayload, DeviceTokenPayload
from backend.application.services.notification_service import NotificationService
from backend.domain.models import User
from backend.infrastructure.security.rate_limiter import device_token_rate_limiter
from backend.application.services.driver_service import DriverService
from backend.application.services.driver_trip_operations_service import DriverTripOperationsService
from backend.application.services.cascading_dispatch_service import CascadingDispatchService

router = APIRouter(prefix="/drivers", tags=["Driver Operations"])

class DispatchPreferencesPayload(BaseModel):
    minimum_fare_cents: Optional[int] = Field(0, ge=0, le=100000000)
    maximum_pickup_distance_km: Optional[float] = Field(20, gt=0, le=200)
    vehicle_categories: Optional[List[str]] = Field(default_factory=list, max_length=20)
    preferred_drop_zones: Optional[List[str]] = Field(default_factory=list, max_length=50)
    voice_guidance_enabled: Optional[bool] = None
    voice_navigation: Optional[bool] = None
    reroute_on_closure_enabled: Optional[bool] = None
    auto_accept_pool: Optional[bool] = None
    high_surge_alerts: Optional[bool] = None
    preferred_corridor: Optional[str] = None
    navigation_provider: Optional[str] = Field(None, max_length=24)

class RejectOfferPayload(BaseModel):
    reason: str = Field(..., pattern="^(TOO_FAR|FARE_TOO_LOW|WRONG_CATEGORY|UNAVAILABLE|OTHER|DISPATCH_TIMEOUT|PILOT_DECLINED|PASSENGER_MISMATCH|LOW_BATTERY)$")

class VehicleUpdatePayload(BaseModel):
    model: Optional[str] = None
    license_plate: Optional[str] = None
    color: Optional[str] = None
    year: Optional[int] = None
    rc_number: Optional[str] = None
    permit_number: Optional[str] = None

class PreShiftInspectionRequestPayload(BaseModel):
    vehicle_id: Optional[str] = None
    tyre_pressure_ok: bool = True
    lights_ok: bool = True
    brake_buzzer_ok: bool = True
    notes: Optional[str] = None

@router.get("/dispatch/offers")
async def get_dispatch_offers(user: User = Depends(get_current_driver), db: AsyncSession = Depends(get_db)):
    return await DriverTripOperationsService(db).get_offers(user.id)

@router.get("/dispatch/preferences")
@router.get("/preferences")
async def get_dispatch_preferences(user: User = Depends(get_current_driver), db: AsyncSession = Depends(get_db)):
    return await DriverTripOperationsService(db).get_preferences(user.id)

@router.put("/dispatch/preferences")
@router.put("/preferences")
@router.patch("/preferences")
async def update_dispatch_preferences(payload: DispatchPreferencesPayload, user: User = Depends(get_current_driver), db: AsyncSession = Depends(get_db)):
    return await DriverTripOperationsService(db).update_preferences(user.id, payload.model_dump(exclude_unset=True))

@router.post("/dispatch/offers/{ride_id}/reject")
async def reject_dispatch_offer(ride_id: str, payload: RejectOfferPayload, user: User = Depends(get_current_driver), db: AsyncSession = Depends(get_db)):
    return await CascadingDispatchService(db).reject_offer_and_cascade(user.id, ride_id, payload.reason)

class AvailabilityPayload(BaseModel):
    is_online: bool

class LocationPingPayload(BaseModel):
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)
    heading: Optional[float] = Field(None, ge=0, le=360)
    speed_mps: Optional[float] = Field(None, ge=0, le=100)
    accuracy_m: Optional[float] = Field(None, ge=0, le=5000)
    is_mock_location: bool = False
    location_source: str = Field("GPS", pattern="^(GPS|NETWORK|PASSIVE|MANUAL|GNSS|FUSED)$")

class DriverProfileUpdate(BaseModel):
    full_name: Optional[str] = None
    email: Optional[str] = None
    phone_number: Optional[str] = None
    avatar_url: Optional[str] = None

class DriverAvatarPayload(BaseModel):
    avatar_url: str = Field(..., min_length=1)

@router.get("/profile")
async def get_driver_profile(user: User = Depends(get_current_driver), db: AsyncSession = Depends(get_db)):
    service = DriverService(db)
    driver = await service.get_or_create_profile(user.id)
    return {
        "id": driver.id,
        "driver_id": driver.id,
        "user_id": user.id,
        "full_name": user.full_name,
        "phone_number": user.phone_number,
        "email": user.email,
        "license_number": driver.license_number,
        "approval_status": driver.approval_status,
        "rating_average": driver.rating_average,
        "rating_count": driver.rating_count,
        "total_trips": driver.total_trips,
        "wallet_balance_cents": driver.wallet_balance_cents,
        "avatar_url": user.avatar_url
    }

@router.get("/scorecard")
async def get_driver_scorecard(user: User = Depends(get_current_driver), db: AsyncSession = Depends(get_db)):
    service = DriverService(db)
    return await service.get_driver_scorecard(user.id)


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
    if payload.phone_number is not None:
        user.phone_number = payload.phone_number
    if payload.avatar_url is not None:
        user.avatar_url = payload.avatar_url
    await db.commit()
    return {"message": "Driver profile updated.", "avatar_url": user.avatar_url}

@router.put("/avatar")
@router.post("/avatar")
async def update_driver_avatar(
    payload: DriverAvatarPayload,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    user.avatar_url = payload.avatar_url.strip()
    await db.commit()
    return {"message": "Profile photo updated successfully.", "avatar_url": user.avatar_url}

@router.delete("/avatar")
async def delete_driver_avatar(
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    user.avatar_url = None
    await db.commit()
    return {"message": "Profile photo removed successfully.", "avatar_url": None}

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

@router.get("/documents/{document_id}/content")
async def get_driver_document_content(
    document_id: str,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    service = DriverService(db)
    content_bytes, mime_type, filename = await service.get_document_bytes_and_meta(user.id, document_id)
    return Response(
        content=content_bytes,
        media_type=mime_type,
        headers={"Content-Disposition": f'inline; filename="{filename}"'}
    )

@router.get("/documents/{document_id}/signed-url")
async def get_driver_document_signed_url(
    document_id: str,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    service = DriverService(db)
    return await service.get_document_signed_url(user.id, document_id)

@router.get("/vehicle")
@router.get("/vehicles/active")
async def get_active_vehicle(
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    return await DriverService(db).get_active_vehicle(user.id)

@router.put("/vehicle")
@router.post("/vehicle")
async def update_active_vehicle(
    payload: VehicleUpdatePayload,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    return await DriverService(db).update_active_vehicle(user.id, payload.model_dump(exclude_unset=True))

@router.post("/duty/vehicle-inspection")
@router.post("/vehicle-inspection")
async def submit_preshift_vehicle_inspection(
    payload: PreShiftInspectionRequestPayload,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    return await DriverService(db).submit_preshift_inspection(
        user_id=user.id,
        tyre_pressure_ok=payload.tyre_pressure_ok,
        lights_ok=payload.lights_ok,
        brake_buzzer_ok=payload.brake_buzzer_ok,
        vehicle_id=payload.vehicle_id,
        notes=payload.notes
    )

@router.get("/duty/vehicle-inspection/latest")
@router.get("/vehicle-inspection/latest")
async def get_latest_preshift_inspection(
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    return await DriverService(db).get_latest_preshift_inspection(user.id)

@router.get("/availability")
async def get_driver_availability(user: User = Depends(get_current_driver), db: AsyncSession = Depends(get_db)):
    service = DriverService(db)
    return await service.get_availability(user.id)

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
    return await service.update_location(user.id, payload.latitude, payload.longitude, payload.heading,
        payload.speed_mps, payload.accuracy_m, payload.is_mock_location, payload.location_source)

class LocationBatchPayload(BaseModel):
    locations: List[LocationPingPayload] = Field(..., max_length=500)

@router.post("/location/batch")
async def update_location_batch(
    payload: LocationBatchPayload,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    service = DriverService(db)
    items = [loc.model_dump() for loc in payload.locations]
    return await service.update_location_batch(user.id, items)

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
    account_number_mask: str = Field(..., min_length=4, max_length=32, json_schema_extra={"example": "BANK *******1234"})
    ifsc: str = Field(..., min_length=2, max_length=20, json_schema_extra={"example": "ABCD0123456 or UPI"})
    idempotency_key: str = Field(..., min_length=16, max_length=64, json_schema_extra={"example": "payout_req_12345678abcdef"})

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
        driver_id_or_user_id=user.id,
        amount_cents=payload.amount_cents,
        bank_details={"account_number_mask": payload.account_number_mask, "ifsc": payload.ifsc},
        idempotency_key=payload.idempotency_key
    )
    await db.commit()
    return {
        "status": payout.status,
        "payout_id": payout.id,
        "amount_cents": payout.amount_cents,
        "bank_account_mask": payout.bank_account_masked,
        "requested_at": payout.created_at.isoformat()
    }

@router.get("/payouts/history")
async def get_payout_history(
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    from backend.domain.models import DriverPayoutRequest, DriverProfile
    from sqlalchemy import select
    driver_prof = (await db.execute(select(DriverProfile).where(DriverProfile.user_id == user.id))).scalar_one_or_none()
    if not driver_prof:
        return []
    stmt = (
        select(DriverPayoutRequest)
        .where(DriverPayoutRequest.driver_id == driver_prof.id)
        .order_by(DriverPayoutRequest.created_at.desc())
    )
    res = await db.execute(stmt)
    payouts = res.scalars().all()
    return [
        {
            "id": p.id,
            "amount_cents": p.amount_cents,
            "status": p.status,
            "bank_account_mask": getattr(p, "bank_account_masked", getattr(p, "bank_account_mask", "")),
            "requested_at": p.created_at.isoformat(),
            "reviewed_at": p.reviewed_at.isoformat() if p.reviewed_at else None,
            "paid_at": p.updated_at.isoformat() if p.status == "PAID" and p.updated_at else None,
            "rejection_reason": p.rejection_reason
        }
        for p in payouts
    ]

@router.post("/device-token")
async def register_driver_device_token(
    request: Request,
    payload: DeviceTokenPayload,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db),
):
    request.state.user = user
    await device_token_rate_limiter(request)
    return await NotificationService(db).register_token(
        user_id=user.id,
        fcm_token=payload.fcm_token,
        device_os=payload.platform or payload.device_os,
        target_user_id=payload.user_id,
        actor_role=user.role,
        device_label=payload.device_label,
    )


@router.delete("/device-token")
async def deactivate_driver_device_token(
    request: Request,
    fcm_token: Optional[str] = Query(None, max_length=512),
    payload: Optional[DeviceTokenDeactivatePayload] = Body(default=None),
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db),
):
    request.state.user = user
    await device_token_rate_limiter(request)
    token = payload.fcm_token if payload and payload.fcm_token else fcm_token
    return await NotificationService(db).deactivate_token(
        user_id=user.id,
        fcm_token=token,
        actor_role=user.role,
    )


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


