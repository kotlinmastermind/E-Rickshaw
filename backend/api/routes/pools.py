from typing import Optional, List, Dict, Any
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from backend.infrastructure.database.session import get_db
from backend.api.deps import get_current_user
from backend.domain.models import User
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
    fallback_policy: str = Field(default="CREATE_NEW_POOL")  # CREATE_NEW_POOL, FAIL

class OccupancyTransitionRequest(BaseModel):
    event_type: str = Field(..., description="BOARDED, ALIGHTED, NO_SHOW, CANCELLED")

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
        fallback_policy=payload.fallback_policy
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
    return await service.get_pooled_trip_details(trip_id)

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
    service = PoolService(db)
    return await service.update_passenger_occupancy(
        booking_id=booking_id,
        event_type=payload.event_type.upper(),
        actor_id=current_user.id,
        actor_role=current_user.role
    )
