"""
Smart Dispatch API Endpoints for Vasantra Mobility.
Exposes high-speed H3 telemetry ingestion, nearby driver discovery,
intelligent ride matching, offer acceptance/rejection, and dispatch status monitoring.
"""
import json
from typing import Optional
from datetime import datetime, timezone
from fastapi import APIRouter, Depends, HTTPException, status, Query
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select

from backend.infrastructure.database.session import get_db
from backend.api.deps import get_current_user, get_current_driver
from backend.domain.models import User, Ride, RideAssignment, DriverProfile, DriverAvailability, CustomerProfile
from backend.application.services.live_location_service import LiveLocationService
from backend.application.services.smart_dispatch_service import SmartDispatchService
from backend.application.services.driver_eligibility_service import DriverEligibilityService
from backend.application.services.road_eta_service import road_eta_service
from backend.application.services.h3_geospatial_service import h3_service

router = APIRouter(prefix="/dispatch", tags=["Smart Dispatch Engine"])


def ensure_utc(dt: Optional[datetime]) -> Optional[datetime]:
    if dt is None:
        return None
    if dt.tzinfo is None:
        return dt.replace(tzinfo=timezone.utc)
    return dt


class DriverLocationPing(BaseModel):
    latitude: float = Field(..., ge=-90.0, le=90.0)
    longitude: float = Field(..., ge=-180.0, le=180.0)
    heading: Optional[float] = Field(None, ge=0.0, le=360.0)
    speed_mps: Optional[float] = Field(None, ge=0.0, le=100.0)
    accuracy_m: Optional[float] = Field(None, ge=0.0, le=5000.0)
    is_mock_location: bool = False
    location_source: str = Field("GPS", pattern="^(GPS|NETWORK|PASSIVE|MANUAL|GNSS|FUSED)$")
    active_ride_id: Optional[str] = None


class DispatchSearchRequest(BaseModel):
    ride_id: str = Field(..., min_length=1)
    max_rings: int = Field(3, ge=1, le=5)


class OfferRejectionRequest(BaseModel):
    reason: str = Field(
        ...,
        pattern=(
            "^(TOO_FAR|FARE_TOO_LOW|WRONG_CATEGORY|UNAVAILABLE|"
            "OTHER|DISPATCH_TIMEOUT|PILOT_DECLINED|PASSENGER_MISMATCH|LOW_BATTERY)$"
        )
    )


@router.post("/driver/location")
async def ingest_driver_telemetry(
    payload: DriverLocationPing,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    """
    High-frequency driver telemetry ingestion with instant H3 indexing.
    Updates Redis spatial sets in O(1) time without blocking database transactions.
    """
    stmt = (
        select(DriverProfile.id, DriverAvailability.is_online, DriverAvailability.is_busy)
        .outerjoin(DriverAvailability, DriverAvailability.driver_id == DriverProfile.id)
        .where(DriverProfile.user_id == user.id)
    )
    res = await db.execute(stmt)
    row = res.first()
    if not row:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Driver profile not found.")

    driver_id, is_online, is_busy = row[0], bool(row[1]), bool(row[2])

    # The device may report its current ride for compatibility, but it is
    # never authoritative. Resolve the assigned active ride from PostgreSQL so
    # a driver cannot publish GPS telemetry into another ride's WebSocket channel.
    active_ride_stmt = (
        select(Ride.id)
        .where(
            Ride.assigned_driver_id == driver_id,
            Ride.status.in_(("ASSIGNED", "ARRIVED", "IN_PROGRESS", "PAUSED"))
        )
        .limit(2)
    )
    active_ride_ids = (await db.execute(active_ride_stmt)).scalars().all()
    if len(active_ride_ids) > 1:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Multiple active rides are assigned to this driver; live ride telemetry is paused pending reconciliation."
        )
    authoritative_active_ride_id = active_ride_ids[0] if active_ride_ids else None

    service = LiveLocationService(db=db)
    result = await service.ingest_driver_location(
        driver_id=driver_id,
        latitude=payload.latitude,
        longitude=payload.longitude,
        heading=payload.heading,
        speed_mps=payload.speed_mps,
        accuracy_m=payload.accuracy_m,
        is_mock_location=payload.is_mock_location,
        location_source=payload.location_source,
        is_online=is_online,
        is_busy=is_busy,
        active_ride_id=authoritative_active_ride_id
    )

    if result.get("status") == "REJECTED":
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=result.get("reason"))

    return result


@router.get("/nearby-drivers")
async def get_nearby_drivers(
    latitude: float = Query(..., ge=-90.0, le=90.0),
    longitude: float = Query(..., ge=-180.0, le=180.0),
    max_rings: int = Query(2, ge=0, le=5),
    category_id: Optional[str] = Query(None),
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Discovers nearby available E-rickshaw drivers using concentric H3 rings.
    Returns real road distance and estimated pickup ETA.
    """
    if not h3_service.validate_coordinates(latitude, longitude):
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid coordinates.")

    from backend.application.services.service_area_service import ServiceAreaService
    service_area = await ServiceAreaService(db).resolve_point(latitude, longitude)
    eligibility = DriverEligibilityService(db=db)
    candidates = await eligibility.discover_eligible_candidates(
        pickup_lat=latitude,
        pickup_lng=longitude,
        max_rings=max_rings,
        required_category_id=category_id,
        required_service_area_id=service_area.id,
        max_candidates=10
    )

    if not candidates:
        return {"drivers": [], "total_count": 0, "pickup_cell": h3_service.get_h3_cell(latitude, longitude)}

    # Enrich with road ETA
    enriched = await road_eta_service.batch_compute_candidates_eta(
        pickup_lat=latitude,
        pickup_lng=longitude,
        candidates=candidates
    )

    results = []
    for c in enriched:
        results.append({
            "driver_id": c.driver_id,
            "latitude": c.latitude,
            "longitude": c.longitude,
            "heading": c.heading,
            "h3_cell": c.h3_cell,
            "ring_index": c.ring_index,
            "rating": c.rating_average,
            "haversine_distance_km": c.haversine_distance_km,
            "road_distance_km": getattr(c, "road_distance_km", c.haversine_distance_km * 1.25),
            "road_eta_minutes": getattr(c, "road_eta_minutes", round((c.haversine_distance_km / 20.0) * 60, 1))
        })

    return {
        "pickup_cell": h3_service.get_h3_cell(latitude, longitude),
        "total_count": len(results),
        "drivers": results
    }


@router.post("/search")
async def trigger_dispatch_search(
    payload: DispatchSearchRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Triggers smart dispatch search for a requested ride.
    Finds nearest optimal candidate, acquires atomic lock, and issues offer.
    """
    svc = SmartDispatchService(db)
    assignment = await svc.search_and_dispatch(
        ride_id=payload.ride_id,
        max_rings=payload.max_rings
    )

    if not assignment:
        return {
            "ride_id": payload.ride_id,
            "status": "NO_DRIVERS_AVAILABLE",
            "message": "No available chauffeurs found within search rings."
        }

    return {
        "ride_id": payload.ride_id,
        "assignment_id": assignment.id,
        "driver_id": assignment.driver_id,
        "status": assignment.status,
        "expires_at": assignment.expires_at.isoformat() if assignment.expires_at else None,
        "metadata": json.loads(assignment.metadata_json) if assignment.metadata_json else {}
    }


@router.post("/offer/{ride_id}/accept")
async def accept_dispatch_offer(
    ride_id: str,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    """Driver accepts an active dispatch offer atomically."""
    svc = SmartDispatchService(db)
    return await svc.accept_offer(driver_user_id=user.id, ride_id=ride_id)


@router.post("/offer/{ride_id}/reject")
async def reject_dispatch_offer(
    ride_id: str,
    payload: OfferRejectionRequest,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    """Driver declines an offer; triggers immediate cascading dispatch to next candidate."""
    svc = SmartDispatchService(db)
    return await svc.reject_offer(driver_user_id=user.id, ride_id=ride_id, reason=payload.reason)


@router.get("/status/{ride_id}")
async def get_dispatch_status(
    ride_id: str,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """Returns the live dispatch and offer status for a ride."""
    ride_stmt = select(Ride).where(Ride.id == ride_id)
    r_res = await db.execute(ride_stmt)
    ride = r_res.scalar_one_or_none()

    if not ride:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Ride not found.")

    # A dispatch status lookup is scoped to the ride's customer, an involved
    # driver, or an administrator; possession of a ride UUID is not authorization.
    role = str(user.role or "").strip().lower()
    if role == "customer":
        customer_stmt = select(CustomerProfile.id).where(CustomerProfile.user_id == user.id)
        customer_id = (await db.execute(customer_stmt)).scalar_one_or_none()
        if not customer_id or ride.customer_id != customer_id:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Ride not found.")
    elif role == "driver":
        driver_stmt = select(DriverProfile.id).where(DriverProfile.user_id == user.id)
        driver_id = (await db.execute(driver_stmt)).scalar_one_or_none()
        if not driver_id:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Driver profile is required.")
        involved_stmt = select(RideAssignment.id).where(
            RideAssignment.ride_id == ride_id,
            RideAssignment.driver_id == driver_id,
        ).limit(1)
        involved_assignment_id = (await db.execute(involved_stmt)).scalar_one_or_none()
        if ride.assigned_driver_id != driver_id and not involved_assignment_id:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Ride not found.")
    elif role not in ("admin", "super_admin", "superadmin", "super administrator"):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Not authorized to view dispatch status.")

    # Fetch latest assignment
    assign_stmt = (
        select(RideAssignment)
        .where(RideAssignment.ride_id == ride_id)
        .order_by(RideAssignment.offered_at.desc())
        .limit(1)
    )
    a_res = await db.execute(assign_stmt)
    latest_assign = a_res.scalar_one_or_none()

    now = datetime.now(timezone.utc)
    remaining_sec = 0
    offer_is_live = False
    if latest_assign and latest_assign.expires_at:
        exp_utc = ensure_utc(latest_assign.expires_at)
        if exp_utc and exp_utc > now:
            remaining_sec = int((exp_utc - now).total_seconds())
            offer_is_live = (
                latest_assign.status == "OFFERED"
                and ride.status == "REQUESTED"
                and ride.assigned_driver_id is None
            )

    return {
        "ride_id": ride.id,
        "ride_status": ride.status,
        "assigned_driver_id": ride.assigned_driver_id,
        "current_offer": {
            "assignment_id": latest_assign.id,
            "driver_id": latest_assign.driver_id,
            "status": latest_assign.status,
            "expires_at": ensure_utc(latest_assign.expires_at).isoformat() if latest_assign.expires_at else None,
            "expires_in_seconds": remaining_sec,
            "metadata": json.loads(latest_assign.metadata_json) if latest_assign.metadata_json else {}
        } if latest_assign and offer_is_live else None,
        "latest_assignment": {
            "assignment_id": latest_assign.id,
            "driver_id": latest_assign.driver_id,
            "status": latest_assign.status,
            "expires_at": ensure_utc(latest_assign.expires_at).isoformat() if latest_assign.expires_at else None,
        } if latest_assign else None
    }
