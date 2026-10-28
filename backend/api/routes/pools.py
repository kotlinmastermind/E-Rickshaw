from typing import Optional, List, Dict, Any
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from backend.infrastructure.database.session import get_db
from backend.api.deps import get_current_user
from backend.domain.models import User, PooledBooking, PooledTrip, DriverProfile, Ride
from sqlalchemy import select
from backend.application.services.pool_service import PoolService

router = APIRouter(prefix="/pools", tags=["Shared Rides & Pooling"])

class SharedEstimateRequest(BaseModel):
    category_id: str
    pickup_latitude: float = Field(..., ge=-90.0, le=90.0)
    pickup_longitude: float = Field(..., ge=-180.0, le=180.0)
    destination_latitude: float = Field(..., ge=-90.0, le=90.0)
    destination_longitude: float = Field(..., ge=-180.0, le=180.0)
    seats_requested: int = Field(default=1, ge=1, le=4)

class MatchPoolsRequest(BaseModel):
    category_id: str
    pickup_latitude: float = Field(..., ge=-90.0, le=90.0)
    pickup_longitude: float = Field(..., ge=-180.0, le=180.0)
    destination_latitude: float = Field(..., ge=-90.0, le=90.0)
    destination_longitude: float = Field(..., ge=-180.0, le=180.0)
    seats_requested: int = Field(default=1, ge=1, le=4)
    max_detour_km: float = Field(default=5.0, ge=0.5, le=25.0)

class BookSharedRideRequest(BaseModel):
    category_id: str
    estimate_id: str
    pickup_address: str = Field(..., min_length=3)
    pickup_latitude: float = Field(..., ge=-90.0, le=90.0)
    pickup_longitude: float = Field(..., ge=-180.0, le=180.0)
    destination_address: str = Field(..., min_length=3)
    destination_latitude: float = Field(..., ge=-90.0, le=90.0)
    destination_longitude: float = Field(..., ge=-180.0, le=180.0)
    seats_reserved: int = Field(default=1, ge=1, le=4)
    target_pooled_trip_id: Optional[str] = None
    fallback_policy: str = Field(default="FAIL", pattern="^(CREATE_NEW_POOL|FAIL)$")
    share_stop_details_with_co_passengers: bool = False
    maximum_detour_km: float = Field(default=5.0, ge=1.0, le=25.0)

class OccupancyTransitionRequest(BaseModel):
    event_type: str = Field(..., description="BOARDED, ALIGHTED, NO_SHOW, CANCELLED")

class SharedTripMessageRequest(BaseModel):
    message: str = Field(min_length=1, max_length=1000)

class SharedRidePreferenceRequest(BaseModel):
    maximum_detour_km: float = Field(default=5.0, ge=1.0, le=25.0)

def require_customer(user: User) -> None:
    if user.role.lower() != "customer":
        raise HTTPException(status_code=403, detail="Customer access is required.")

@router.get("/preferences/detour")
async def get_shared_ride_preferences(current_user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    require_customer(current_user)
    return await PoolService(db).get_customer_detour_preference(current_user)

@router.put("/preferences/detour")
async def update_shared_ride_preferences(payload: SharedRidePreferenceRequest, current_user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    require_customer(current_user)
    return await PoolService(db).set_customer_detour_preference(current_user, payload.maximum_detour_km)

@router.post("/estimate", status_code=status.HTTP_200_OK)
async def get_shared_estimate(
    payload: SharedEstimateRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Returns deterministic fare estimate for shared ride option with savings breakdown,
    seat scaling, and pricing policy version snapshot.
    """
    service = PoolService(db)
    return await service.calculate_shared_fare_estimate(
        category_id=payload.category_id,
        pickup_lat=payload.pickup_latitude,
        pickup_lng=payload.pickup_longitude,
        dest_lat=payload.destination_latitude,
        dest_lng=payload.destination_longitude,
        seats_requested=payload.seats_requested
    )

@router.post("/matches", status_code=status.HTTP_200_OK)
async def find_pool_matches(
    payload: MatchPoolsRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Evaluates candidate trips for route detour compatibility, pickup ETA,
    and seat capacity with explainable rejection reasons without exposing PII.
    """
    service = PoolService(db)
    return await service.find_route_matches(
        category_id=payload.category_id,
        pickup_lat=payload.pickup_latitude,
        pickup_lng=payload.pickup_longitude,
        dest_lat=payload.destination_latitude,
        dest_lng=payload.destination_longitude,
        seats_requested=payload.seats_requested,
        max_detour_km=payload.max_detour_km
    )

@router.post("/book", status_code=status.HTTP_201_CREATED)
async def book_shared_ride(
    payload: BookSharedRideRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Atomically allocates seats in a pooled trip, creates booking and ride,
    calculates deterministic fare split, and enforces capacity invariants.
    """
    service = PoolService(db)
    return await service.book_shared_ride(
        customer_user_id=current_user.id,
        category_id=payload.category_id,
        estimate_id=payload.estimate_id,
        pickup_address=payload.pickup_address,
        pickup_lat=payload.pickup_latitude,
        pickup_lng=payload.pickup_longitude,
        dest_address=payload.destination_address,
        dest_lat=payload.destination_latitude,
        dest_lng=payload.destination_longitude,
        seats_reserved=payload.seats_reserved,
        target_pooled_trip_id=payload.target_pooled_trip_id,
        fallback_policy=payload.fallback_policy,
        share_stop_details_with_co_passengers=payload.share_stop_details_with_co_passengers,
        maximum_detour_km=payload.maximum_detour_km,
    )

@router.get("/{trip_id}", status_code=status.HTTP_200_OK)
async def get_pool_trip(
    trip_id: str,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Returns complete pooled trip manifest, scheduled pickup/dropoff stops,
    and financial reconciliation summary verifying conservation of monetary totals.
    """
    service = PoolService(db)
    return await service.get_pooled_trip_details(trip_id, current_user)

@router.get("/{trip_id}/availability", status_code=status.HTTP_200_OK)
async def get_pool_seat_availability(trip_id: str, current_user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    return await PoolService(db).get_seat_availability(trip_id)

@router.get("/ride/{ride_id}")
async def get_shared_trip_for_ride(ride_id: str, current_user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    return await PoolService(db).get_shared_trip_for_ride(ride_id, current_user)

@router.get("/{trip_id}/messages")
async def list_shared_trip_messages(trip_id: str, current_user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    return await PoolService(db).list_trip_messages(trip_id, current_user)

@router.post("/{trip_id}/messages", status_code=status.HTTP_201_CREATED)
async def send_shared_trip_message(trip_id: str, payload: SharedTripMessageRequest, current_user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    return await PoolService(db).send_trip_message(trip_id, current_user, payload.message)

@router.post("/bookings/{booking_id}/cancel")
async def cancel_shared_booking(booking_id: str, current_user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    if current_user.role.lower() != "customer":
        raise HTTPException(status_code=403, detail="Only the booking customer can cancel this shared booking.")
    return await PoolService(db).cancel_customer_booking(booking_id, current_user)

@router.post("/bookings/{booking_id}/occupancy", status_code=status.HTTP_200_OK)
async def update_occupancy(
    booking_id: str,
    payload: OccupancyTransitionRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Updates passenger occupancy state machine (BOARDED, ALIGHTED, NO_SHOW, CANCELLED)
    with transaction-safe capacity validation and audit event recording.
    """
    booking = await db.get(PooledBooking, booking_id)
    if not booking:
        raise HTTPException(status_code=404, detail="Pooled booking not found.")
    trip = await db.get(PooledTrip, booking.pooled_trip_id)
    if not trip:
        raise HTTPException(status_code=404, detail="Pooled trip not found.")
    role = current_user.role.lower()
    allowed = role == "admin"
    if role == "driver":
        driver = (await db.execute(select(DriverProfile).where(DriverProfile.user_id == current_user.id))).scalar_one_or_none()
        assigned_ride = await db.get(Ride, booking.ride_id)
        allowed = bool(driver and assigned_ride and (assigned_ride.assigned_driver_id == driver.id or trip.driver_id == driver.id))
    if not allowed:
        raise HTTPException(status_code=403, detail="Only the assigned driver or an administrator can update shared-trip occupancy.")
    if payload.event_type.upper() == "CANCELLED":
        raise HTTPException(status_code=400, detail="Customers must use the shared booking cancellation action; driver cancellation is managed through ride operations.")
    service = PoolService(db)
    return await service.update_passenger_occupancy(
        booking_id=booking_id,
        event_type=payload.event_type.upper(),
        actor_id=current_user.id,
        actor_role=current_user.role
    )
