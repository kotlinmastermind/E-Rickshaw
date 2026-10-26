from typing import Optional, List, Dict, Any
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from backend.infrastructure.database.session import get_db
from backend.api.deps import get_current_user, get_current_customer, get_current_driver
from backend.domain.models import User, Ride, DriverProfile, DriverLocation
from backend.application.services.ride_service import RideService

router = APIRouter(prefix="/rides", tags=["Ride Lifecycle & Booking"])

from datetime import datetime
from backend.domain.models import RideStop
from backend.application.services.dispatch_advanced_service import DispatchAdvancedService

class IntermediateStopInput(BaseModel):
    address: str
    latitude: float
    longitude: float

class RideRequestPayload(BaseModel):
    estimate_id: str
    pickup_address: str
    destination_address: str
    payment_method: str = "UPI"
    is_scheduled: bool = False
    scheduled_pickup_time: Optional[datetime] = None
    promo_code: Optional[str] = None
    stops: Optional[List[IntermediateStopInput]] = None

class ScheduleRidePayload(BaseModel):
    estimate_id: str
    pickup_address: str
    destination_address: str
    scheduled_pickup_time: datetime
    payment_method: str = "UPI"
    promo_code: Optional[str] = None
    stops: Optional[List[IntermediateStopInput]] = None

class AddStopPayload(BaseModel):
    address: str
    latitude: float
    longitude: float
    sequence_order: int = 1

class StartRidePayload(BaseModel):
    otp_code: str = Field(..., min_length=6, max_length=6)

class FinishRidePayload(BaseModel):
    actual_distance_km: Optional[float] = None
    actual_duration_minutes: Optional[float] = None

class CancelRidePayload(BaseModel):
    reason: str = Field(..., min_length=3)

class RateDriverPayload(BaseModel):
    rating: int = Field(..., ge=1, le=5)
    feedback: Optional[str] = None

@router.post("/request")
async def create_ride_request(
    payload: RideRequestPayload,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    if payload.is_scheduled and payload.scheduled_pickup_time:
        DispatchAdvancedService.validate_schedule_window(payload.scheduled_pickup_time)

    service = RideService(db)
    stops_data = [s.model_dump() for s in payload.stops] if payload.stops else None
    return await service.create_ride_request(
        customer_user_id=user.id,
        estimate_id=payload.estimate_id,
        pickup_address=payload.pickup_address,
        destination_address=payload.destination_address,
        payment_method=payload.payment_method,
        is_scheduled=payload.is_scheduled,
        scheduled_pickup_time=payload.scheduled_pickup_time,
        promo_code=payload.promo_code,
        stops=stops_data
    )

@router.post("/schedule")
async def schedule_ride(
    payload: ScheduleRidePayload,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    DispatchAdvancedService.validate_schedule_window(payload.scheduled_pickup_time)
    service = RideService(db)
    stops_data = [s.model_dump() for s in payload.stops] if payload.stops else None
    return await service.create_ride_request(
        customer_user_id=user.id,
        estimate_id=payload.estimate_id,
        pickup_address=payload.pickup_address,
        destination_address=payload.destination_address,
        payment_method=payload.payment_method,
        is_scheduled=True,
        scheduled_pickup_time=payload.scheduled_pickup_time,
        promo_code=payload.promo_code,
        stops=stops_data
    )

@router.post("/{ride_id}/stops")
async def add_stop_to_ride(
    ride_id: str,
    payload: AddStopPayload,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    stop = await DispatchAdvancedService.add_intermediate_stop(
        session=db,
        ride_id=ride_id,
        customer_id=user.id,
        address=payload.address,
        lat=payload.latitude,
        lng=payload.longitude,
        sequence_order=payload.sequence_order
    )
    await db.commit()
    return {
        "status": "STOP_ADDED",
        "stop_id": stop.id,
        "sequence_order": stop.sequence_order,
        "address": stop.address
    }

@router.get("/{ride_id}/stops")
async def get_ride_stops(
    ride_id: str,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    stmt = select(RideStop).where(RideStop.ride_id == ride_id).order_by(RideStop.sequence_order.asc())
    res = await db.execute(stmt)
    stops = res.scalars().all()
    return [
        {
            "id": s.id,
            "sequence_order": s.sequence_order,
            "address": s.address,
            "latitude": s.latitude,
            "longitude": s.longitude
        }
        for s in stops
    ]

@router.post("/{ride_id}/waiting/start")
async def start_waiting(
    ride_id: str,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    result = await DispatchAdvancedService.start_waiting(db, ride_id, user.id)
    await db.commit()
    return result

@router.post("/{ride_id}/waiting/stop")
async def stop_waiting(
    ride_id: str,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    result = await DispatchAdvancedService.stop_waiting(db, ride_id, user.id)
    await db.commit()
    return result

@router.post("/{ride_id}/accept")
async def accept_ride(
    ride_id: str,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    service = RideService(db)
    return await service.accept_ride_atomic(driver_user_id=user.id, ride_id=ride_id)

@router.post("/{ride_id}/arrive")
async def driver_arrived(
    ride_id: str,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    service = RideService(db)
    return await service.driver_arrived(driver_user_id=user.id, ride_id=ride_id)

@router.post("/{ride_id}/start")
async def start_trip(
    ride_id: str,
    payload: StartRidePayload,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    service = RideService(db)
    return await service.start_trip_with_otp(
        driver_user_id=user.id,
        ride_id=ride_id,
        otp_code=payload.otp_code
    )

@router.post("/{ride_id}/finish")
async def finish_trip(
    ride_id: str,
    payload: FinishRidePayload,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    service = RideService(db)
    return await service.finish_trip(
        driver_user_id=user.id,
        ride_id=ride_id,
        actual_distance_km=payload.actual_distance_km,
        actual_duration_minutes=payload.actual_duration_minutes
    )

@router.post("/{ride_id}/cancel")
async def cancel_ride(
    ride_id: str,
    payload: CancelRidePayload,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = RideService(db)
    return await service.cancel_ride(
        user_id=user.id,
        role=user.role,
        ride_id=ride_id,
        reason=payload.reason
    )

@router.post("/{ride_id}/rate")
async def rate_driver(
    ride_id: str,
    payload: RateDriverPayload,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = RideService(db)
    return await service.rate_driver(
        customer_user_id=user.id,
        ride_id=ride_id,
        rating=payload.rating,
        feedback=payload.feedback
    )

@router.get("/{ride_id}")
async def get_ride_details(
    ride_id: str,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = RideService(db)
    ride = await service.get_ride_by_id(ride_id)
    
    driver_info = None
    if ride.assigned_driver_id:
        stmt = select(DriverProfile).where(DriverProfile.id == ride.assigned_driver_id)
        res = await db.execute(stmt)
        drv = res.scalar_one_or_none()
        if drv:
            stmt_u = select(User).where(User.id == drv.user_id)
            res_u = await db.execute(stmt_u)
            u = res_u.scalar_one_or_none()
            driver_info = {
                "driver_id": drv.id,
                "name": u.full_name if u else "Executive Chauffeur",
                "phone": u.phone_number if u else "",
                "license_number": drv.license_number,
                "rating": drv.rating_average,
                "total_trips": drv.total_trips
            }

    return {
        "id": ride.id,
        "status": ride.status,
        "pickup_address": ride.pickup_address,
        "pickup_latitude": ride.pickup_latitude,
        "pickup_longitude": ride.pickup_longitude,
        "destination_address": ride.destination_address,
        "destination_latitude": ride.destination_latitude,
        "destination_longitude": ride.destination_longitude,
        "estimated_fare_cents": ride.estimated_fare_cents,
        "final_fare_cents": ride.final_fare_cents,
        "payment_method": ride.payment_method,
        "start_otp": ride.start_otp if (user.role == "customer" or user.role == "admin") else None,
        "driver": driver_info,
        "requested_at": ride.requested_at.isoformat(),
        "assigned_at": ride.assigned_at.isoformat() if ride.assigned_at else None,
        "arrived_at": ride.arrived_at.isoformat() if ride.arrived_at else None,
        "started_at": ride.started_at.isoformat() if ride.started_at else None,
        "completed_at": ride.completed_at.isoformat() if ride.completed_at else None,
        "cancelled_at": ride.cancelled_at.isoformat() if ride.cancelled_at else None
    }

@router.get("/{ride_id}/track")
async def track_ride_location(
    ride_id: str,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = RideService(db)
    ride = await service.get_ride_by_id(ride_id)

    # Polling fallback for live tracking
    lat, lng, heading = ride.pickup_latitude, ride.pickup_longitude, 0.0
    if ride.assigned_driver_id:
        stmt = select(DriverLocation).where(DriverLocation.driver_id == ride.assigned_driver_id).order_by(DriverLocation.recorded_at.desc())
        res = await db.execute(stmt)
        loc = res.scalars().first()
        if loc:
            lat, lng, heading = loc.latitude, loc.longitude, (loc.heading or 0.0)

    return {
        "ride_id": ride.id,
        "status": ride.status,
        "driver_latitude": lat,
        "driver_longitude": lng,
        "heading": heading
    }
