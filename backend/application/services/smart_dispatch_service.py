"""
Smart Dispatch Engine & Lifecycle Manager for Vasantra Mobility.
Integrates H3 Geospatial Indexing, Road ETA Computation, Multi-Factor Scoring,
Atomic Redis Locking, and Cascading Dispatch with WebSocket Broadcasting.
"""
import asyncio
import json
import logging
from datetime import datetime, timezone, timedelta
from typing import Optional, Dict, Any

from fastapi import HTTPException, status
from sqlalchemy import select, or_
from sqlalchemy.ext.asyncio import AsyncSession

from backend.domain.models import (
    Ride, RideAssignment, DriverProfile, DriverAvailability, RideStatusEvent,
    User, Vehicle, VehicleCategory, DriverVehicleAssignment
)
from backend.domain.state_machine.ride_state_machine import RideStateMachine, RideStatus, ActorRole
from backend.application.services.live_location_service import LiveLocationService
from backend.application.services.driver_eligibility_service import DriverEligibilityService
from backend.application.services.road_eta_service import road_eta_service
from backend.application.services.intelligent_matching_service import matching_service
from backend.application.services.batch_dispatch_optimizer import batch_optimizer
from backend.infrastructure.cache.redis_client import redis_client
from backend.api.routes.ws import ws_manager

logger = logging.getLogger("backend.dispatch.smart_engine")

DEFAULT_OFFER_TIMEOUT_SECONDS: int = 20
SAFETY_WATCHDOG_OFFSET_SECONDS: int = 2
COOLDOWN_TTL_SECONDS: int = 60


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


def ensure_utc(dt: Optional[datetime]) -> Optional[datetime]:
    if dt is None:
        return None
    if dt.tzinfo is None:
        return dt.replace(tzinfo=timezone.utc)
    return dt


class SmartDispatchService:
    def __init__(self, db: AsyncSession):
        self.db = db
        self.live_loc = LiveLocationService(db=db)
        self.eligibility = DriverEligibilityService(db=db, live_loc_service=self.live_loc)

    async def _resolve_driver(self, driver_id_or_user_id: str) -> Optional[DriverProfile]:
        """Resolves DriverProfile by either profile primary key or user primary key."""
        stmt = select(DriverProfile).where(
            or_(DriverProfile.id == driver_id_or_user_id, DriverProfile.user_id == driver_id_or_user_id)
        )
        res = await self.db.execute(stmt)
        return res.scalar_one_or_none()

    async def search_and_dispatch(
        self,
        ride_id: str,
        max_rings: int = 3,
        offer_timeout_sec: int = DEFAULT_OFFER_TIMEOUT_SECONDS
    ) -> Optional[RideAssignment]:
        """
        Executes the intelligent H3 road-network dispatch workflow:
        1. Row-level locks the Ride record.
        2. Discovers eligible candidates in progressive H3 hexagonal rings.
        3. Computes road network distances and ETAs.
        4. Ranks candidates with multi-dimensional scoring.
        5. Acquires atomic distributed Redis lock on top driver.
        6. Creates RideAssignment and dispatches offer with WebSocket notifications.
        7. Automatically falls back to EXPIRED if all candidate drivers/rings are exhausted.
        """
        # 1. Row-level lock on Ride
        ride_stmt = select(Ride).where(Ride.id == ride_id).with_for_update()
        res = await self.db.execute(ride_stmt)
        ride = res.scalar_one_or_none()

        if not ride:
            logger.warning(f"Ride {ride_id} not found during smart dispatch.")
            return None

        if ride.status != RideStatus.REQUESTED.value or ride.assigned_driver_id is not None:
            logger.info(f"Ride {ride_id} is in status {ride.status} (assigned={ride.assigned_driver_id}). Skipping dispatch.")
            return None

        now = utc_now()

        # 2. Check if an active unexpired offer already exists in flight
        active_stmt = select(RideAssignment).where(
            RideAssignment.ride_id == ride_id,
            RideAssignment.status == "OFFERED",
            RideAssignment.expires_at > now
        )
        active_res = await self.db.execute(active_stmt)
        active_assignment = active_res.scalar_one_or_none()
        if active_assignment:
            logger.info(f"Ride {ride_id} already has an active offer to driver {active_assignment.driver_id}.")
            return active_assignment

        # 3. Retrieve prior assignees to prevent repeated offers
        prior_stmt = select(RideAssignment.driver_id).where(RideAssignment.ride_id == ride_id)
        prior_res = await self.db.execute(prior_stmt)
        prior_driver_ids = set(prior_res.scalars().all())

        # A ride without a persisted operational area must never enter dispatch.
        if not ride.service_area_id:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Ride has no validated service area and cannot be dispatched.",
            )
        from backend.application.services.service_area_service import ServiceAreaService
        resolved_area = await ServiceAreaService(self.db).resolve_route(
            ride.pickup_latitude, ride.pickup_longitude,
            ride.destination_latitude, ride.destination_longitude,
        )
        if resolved_area.id != ride.service_area_id:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Ride service-area assignment no longer matches its route. Revalidate before dispatch.",
            )

        # 4. Discover eligible drivers only inside the ride's operational area.
        candidates = await self.eligibility.discover_eligible_candidates(
            pickup_lat=ride.pickup_latitude,
            pickup_lng=ride.pickup_longitude,
            max_rings=max_rings,
            ride_id=ride_id,
            required_category_id=ride.vehicle_category_id,
            required_service_area_id=ride.service_area_id,
            max_candidates=15
        )

        # Exclude drivers who already received an offer for this ride
        fresh_candidates = [c for c in candidates if c.driver_id not in prior_driver_ids]

        if not fresh_candidates:
            logger.info(f"No eligible candidates found for ride {ride_id} across {max_rings} H3 rings.")
            return await self._transition_ride_to_exhausted(ride)

        # 5. Compute real road network distance and ETAs
        enriched_candidates = await road_eta_service.batch_compute_candidates_eta(
            pickup_lat=ride.pickup_latitude,
            pickup_lng=ride.pickup_longitude,
            candidates=fresh_candidates
        )

        # 6. Rank candidates using multi-factor intelligent scoring
        ranked = matching_service.rank_candidates(
            candidates=enriched_candidates,
            pickup_lat=ride.pickup_latitude,
            pickup_lng=ride.pickup_longitude
        )

        # 7. Execute Hungarian Bipartite Matching & Secure Atomic Lock
        from backend.application.services.batch_dispatch_optimizer import (
            RideRequestItem, DriverCandidateItem
        )
        ride_item = RideRequestItem(
            ride_id=ride.id,
            pickup_lat=ride.pickup_latitude,
            pickup_lng=ride.pickup_longitude,
            required_category_id=ride.vehicle_category_id
        )
        driver_items = [
            DriverCandidateItem(
                driver_id=s.candidate.driver_id,
                user_id=s.candidate.user_id,
                latitude=s.candidate.latitude,
                longitude=s.candidate.longitude,
                road_eta_minutes=getattr(s.candidate, "road_eta_minutes", 5.0),
                total_score=s.total_score,
                ring_index=s.candidate.ring_index
            )
            for s in ranked
        ]
        eta_matrix = [[getattr(s.candidate, "road_eta_minutes", 5.0) for s in ranked]]

        optimized_matches = await batch_optimizer.optimize_and_assign(
            rides=[ride_item],
            drivers=driver_items,
            eta_matrix=eta_matrix
        )

        # 8. Create RideAssignment for optimal matched candidate
        selected_assignment: Optional[RideAssignment] = None
        for match in optimized_matches:
            if not match.lock_acquired:
                continue

            # Find matching scored candidate
            matching_scored = next(
                (s for s in ranked if s.candidate.driver_id == match.driver_id),
                ranked[0]
            )
            candidate = matching_scored.candidate

            expires_at = now + timedelta(seconds=offer_timeout_sec)
            assignment = RideAssignment(
                ride_id=ride.id,
                driver_id=match.driver_id,
                status="OFFERED",
                offered_at=now,
                expires_at=expires_at,
                escalation_level=len(prior_driver_ids),
                is_accepted=None,
                metadata_json=json.dumps({
                    "road_eta_minutes": match.road_eta_minutes,
                    "road_distance_km": getattr(candidate, "road_distance_km", 1.5),
                    "match_score": match.match_score,
                    "explanation": matching_scored.explanation,
                    "h3_cell": candidate.h3_cell,
                    "ring_index": candidate.ring_index,
                    "optimization_algorithm": "HUNGARIAN_KUHN_MUNKRES"
                })
            )
            self.db.add(assignment)

            safety_ttl = offer_timeout_sec + SAFETY_WATCHDOG_OFFSET_SECONDS
            await redis_client.set(f"dispatch:offer:{ride.id}:{candidate.driver_id}", "ACTIVE", ex=safety_ttl)
            if candidate.user_id:
                await redis_client.set(f"driver:active_offer:{candidate.user_id}", ride.id, ex=safety_ttl)
            await redis_client.set(f"driver:active_offer:{candidate.driver_id}", ride.id, ex=safety_ttl)

            await self.db.commit()
            await self.db.refresh(assignment)
            selected_assignment = assignment

            # Broadcast WebSocket offer events
            await self._broadcast_offer(ride, candidate, matching_scored, assignment, offer_timeout_sec)

            # Schedule background watchdog for automated timeout cascade
            try:
                asyncio.create_task(
                    self._schedule_timeout_watchdog(ride.id, candidate.driver_id, safety_ttl)
                )
            except RuntimeError:
                pass

            logger.info(
                f"Dispatched ride {ride.id} to driver {candidate.driver_id} "
                f"(ETA: {match.road_eta_minutes:.1f}m, Score: {match.match_score:.3f}, Algo: Hungarian)"
            )
            break

        if not selected_assignment:
            # All candidates in this batch were locked by concurrent rides
            return await self._transition_ride_to_exhausted(ride)

        return selected_assignment

    async def _broadcast_offer(
        self,
        ride: Ride,
        candidate: Any,
        scored: Any,
        assignment: RideAssignment,
        timeout_seconds: int
    ) -> None:
        """Publishes one canonical, persisted offer event to the driver's cross-worker channels."""
        fare_cents = ride.final_fare_cents if ride.final_fare_cents is not None else (ride.estimated_fare_cents or 0)
        road_eta = getattr(candidate, "road_eta_minutes", None)
        from backend.application.services.commission_service import CommissionService
        commission_estimate = await CommissionService.estimate_for_ride(self.db, ride)
        expires_at = ensure_utc(assignment.expires_at)
        remaining_seconds = max(0, int((expires_at - utc_now()).total_seconds())) if expires_at else timeout_seconds

        # Keep this event name aligned with ConnectionManager's Pub/Sub allowlist
        # and the driver's durable reconnect reconciliation payload.
        driver_payload = {
            "event": "DISPATCH_OFFER",
            "ride_id": ride.id,
            "assignment_id": assignment.id,
            "pickup_address": ride.pickup_address,
            "pickup_latitude": ride.pickup_latitude,
            "pickup_longitude": ride.pickup_longitude,
            "destination_address": ride.destination_address,
            "destination_latitude": ride.destination_latitude,
            "destination_longitude": ride.destination_longitude,
            "estimated_fare_cents": fare_cents,
            "estimated_trip_distance_km": ride.estimated_distance_km,
            "trip_duration_minutes": ride.estimated_duration_minutes,
            "pickup_distance_km": getattr(candidate, "road_distance_km", None),
            "pickup_duration_minutes": road_eta,
            **commission_estimate,
            "timeout_seconds": remaining_seconds,
            "expires_at": expires_at.isoformat() if expires_at else None,
            "timestamp": utc_now().isoformat()
        }

        # Publish to both identifiers because clients may authenticate with either
        # the DriverProfile ID or the owning User ID. Each worker's subscriber
        # forwards the event to its locally connected socket.
        await redis_client.publish(f"driver:dispatch:{candidate.driver_id}", driver_payload)
        if candidate.user_id and candidate.user_id != candidate.driver_id:
            await redis_client.publish(f"driver:dispatch:{candidate.user_id}", driver_payload)

        # 2. Event to passenger ride channel
        passenger_payload = {
            "event": "DISPATCH_UPDATE",
            "ride_id": ride.id,
            "status": "OFFERED_TO_DRIVER",
            "estimated_pickup_minutes": road_eta,
            "timestamp": utc_now().isoformat()
        }
        await ws_manager.broadcast_to_ride(ride.id, passenger_payload)

    async def _transition_ride_to_exhausted(self, ride: Ride) -> None:
        """Transitions ride to EXPIRED when all drivers/rings have been exhausted."""
        RideStateMachine.validate_transition(ride.status, RideStatus.EXPIRED.value, ActorRole.SYSTEM.value)
        ride.status = RideStatus.EXPIRED.value

        event = RideStatusEvent(
            ride_id=ride.id,
            previous_status="REQUESTED",
            new_status=RideStatus.EXPIRED.value,
            trigger_actor="SYSTEM",
            notes="NO_DRIVERS_AVAILABLE",
            created_at=utc_now()
        )
        self.db.add(event)
        await self.db.commit()

        await ws_manager.broadcast_to_ride(ride.id, {
            "event": "RIDE_EXPIRED",
            "ride_id": ride.id,
            "status": "EXPIRED",
            "reason": "All available chauffeurs are currently engaged. Please retry shortly.",
            "timestamp": utc_now().isoformat()
        })
        return None

    async def accept_offer(self, driver_user_id: str, ride_id: str) -> Dict[str, Any]:
        """
        Processes driver acceptance of a dispatch offer:
        1. Validates assignment state and prevents double acceptance.
        2. Validates state transition using RideStateMachine.
        3. Updates Ride status to ASSIGNED and assigns driver.
        4. Sets driver availability to busy and removes from H3 discovery index.
        5. Releases dispatch lock and clears offer keys.
        """
        driver = await self._resolve_driver(driver_user_id)
        if not driver:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Driver profile not found.")
        driver_user = await self.db.get(User, driver.user_id)
        if not driver_user or not driver_user.is_active:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Driver account is inactive and cannot accept dispatch offers.")
        if driver.approval_status != "APPROVED":
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Driver approval is no longer active; this offer cannot be accepted.")

        # Row-level lock on assignment
        assign_stmt = (
            select(RideAssignment)
            .where(RideAssignment.ride_id == ride_id, RideAssignment.driver_id == driver.id)
            .with_for_update()
        )
        res = await self.db.execute(assign_stmt)
        assignment = res.scalar_one_or_none()

        if not assignment:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Dispatch offer not found.")

        if assignment.status == "ACCEPTED":
            return {"status": "ACCEPTED", "message": "Offer already accepted.", "ride_id": ride_id}

        if assignment.status != "OFFERED":
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail=f"Offer is no longer available (current status: {assignment.status})."
            )

        now = utc_now()
        if assignment.expires_at and ensure_utc(assignment.expires_at) < now:
            raise HTTPException(status_code=status.HTTP_410_GONE, detail="Dispatch offer has expired.")

        # Row-level lock on Ride
        ride_stmt = select(Ride).where(Ride.id == ride_id).with_for_update()
        ride_res = await self.db.execute(ride_stmt)
        ride = ride_res.scalar_one_or_none()

        if not ride:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Ride not found.")
        if not ride.service_area_id:
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Ride has no validated service area.")
        from backend.application.services.service_area_service import ServiceAreaService
        service_areas = ServiceAreaService(self.db)
        resolved_area = await service_areas.resolve_route(
            ride.pickup_latitude, ride.pickup_longitude,
            ride.destination_latitude, ride.destination_longitude,
        )
        if resolved_area.id != ride.service_area_id:
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Ride is no longer valid for its recorded service area.")
        await service_areas.require_driver_assignment(driver.id, ride.service_area_id)
        await service_areas.require_fresh_driver_inside(driver.id, ride.service_area_id)

        # Revalidate the exact operational vehicle at acceptance time. An offer
        # may outlive a vehicle deactivation, reassignment, or category change.
        vehicle_stmt = (
            select(Vehicle)
            .join(DriverVehicleAssignment, DriverVehicleAssignment.vehicle_id == Vehicle.id)
            .join(VehicleCategory, VehicleCategory.id == Vehicle.category_id)
            .where(
                DriverVehicleAssignment.driver_id == driver.id,
                DriverVehicleAssignment.is_active.is_(True),
                Vehicle.is_active.is_(True),
                Vehicle.category_id == ride.vehicle_category_id,
                VehicleCategory.is_active.is_(True),
            )
            .with_for_update()
            .limit(1)
        )
        driver_vehicle = (await self.db.execute(vehicle_stmt)).scalars().first()
        if not driver_vehicle:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Driver no longer has an active assigned vehicle matching this ride's category.",
            )

        # The driver must still be online and free; an offer is not a reservation
        # of availability and can become stale while it is in flight.
        avail_stmt = (
            select(DriverAvailability)
            .where(DriverAvailability.driver_id == driver.id)
            .with_for_update()
        )
        avail = (await self.db.execute(avail_stmt)).scalars().first()
        if not avail or not avail.is_online or avail.is_busy:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Driver is no longer online and available to accept this ride.",
            )

        # Validate state transition via authoritative state machine
        RideStateMachine.validate_transition(ride.status, RideStatus.ASSIGNED.value, ActorRole.DRIVER.value)

        # Update assignment
        assignment.status = "ACCEPTED"
        assignment.is_accepted = True
        assignment.responded_at = now

        # Update ride
        ride.status = RideStatus.ASSIGNED.value
        ride.assigned_driver_id = driver.id
        ride.assigned_at = now

        # Update the already locked availability row to busy.
        avail.is_busy = True
        avail.updated_at = now

        # Remove from H3 search index so driver is not candidate for other rides
        await self.live_loc.remove_driver_from_h3_index(driver.id)

        # Release atomic dispatch lock
        await batch_optimizer.release_driver_lock(driver.id, ride_id)

        # Clear Redis offer keys
        await redis_client.delete(f"dispatch:offer:{ride_id}:{driver.id}")
        await redis_client.delete(f"driver:active_offer:{driver.id}")
        await redis_client.delete(f"driver:active_offer:{driver_user_id}")

        # Add event log
        event = RideStatusEvent(
            ride_id=ride.id,
            previous_status=RideStatus.REQUESTED.value,
            new_status=RideStatus.ASSIGNED.value,
            triggered_by_user_id=driver_user_id,
            trigger_actor="DRIVER",
            notes="DISPATCH_OFFER_ACCEPTED",
            created_at=now
        )
        self.db.add(event)
        await self.db.commit()

        # Broadcast acceptance
        accept_payload = {
            "event": "RIDE_ASSIGNED",
            "ride_id": ride.id,
            "status": "ASSIGNED",
            "driver_id": driver.id,
            "driver_rating": driver.rating_average,
            "timestamp": now.isoformat()
        }
        await ws_manager.broadcast_to_ride(ride.id, accept_payload)

        return {
            "status": "ACCEPTED",
            "ride_id": ride.id,
            "driver_id": driver.id,
            "assigned_at": now.isoformat()
        }

    async def reject_offer(
        self,
        driver_user_id: str,
        ride_id: str,
        reason: str
    ) -> Dict[str, Any]:
        """
        Handles driver rejection (or timeout):
        1. Records rejection on RideAssignment.
        2. Sets Redis cooldown (60s) for this driver on this ride.
        3. Releases atomic lock.
        4. Triggers immediate cascading dispatch to the next best driver.
        """
        driver = await self._resolve_driver(driver_user_id)
        if not driver:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Driver profile not found.")

        # Row-level lock on assignment
        assign_stmt = (
            select(RideAssignment)
            .where(RideAssignment.ride_id == ride_id, RideAssignment.driver_id == driver.id)
            .with_for_update()
        )
        res = await self.db.execute(assign_stmt)
        assignment = res.scalar_one_or_none()

        if not assignment:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Dispatch offer not found.")

        if assignment.status == "ACCEPTED":
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Offer already accepted.")

        now = utc_now()
        assignment.status = "REJECTED"
        assignment.is_accepted = False
        assignment.rejection_reason = reason
        assignment.responded_at = now

        # Place driver on cooldown for this ride
        await redis_client.set(f"cooldown:{driver.id}:{ride_id}", "1", ex=COOLDOWN_TTL_SECONDS)

        # Release lock and clear offer keys
        await batch_optimizer.release_driver_lock(driver.id, ride_id)
        await redis_client.delete(f"dispatch:offer:{ride_id}:{driver.id}")
        await redis_client.delete(f"driver:active_offer:{driver.id}")
        await redis_client.delete(f"driver:active_offer:{driver_user_id}")

        await self.db.commit()

        # Cascade immediately to next available driver
        try:
            asyncio.create_task(self._background_cascade(ride_id))
        except RuntimeError:
            await self.search_and_dispatch(ride_id)

        return {
            "status": "REJECTED",
            "ride_id": ride_id,
            "message": "Offer declined. Cascading to next candidate."
        }

    async def _schedule_timeout_watchdog(self, ride_id: str, driver_id: str, delay_sec: int) -> None:
        """Background task that triggers auto-expiration and cascade if offer isn't answered."""
        await asyncio.sleep(delay_sec)
        await self._handle_timeout(ride_id, driver_id)

    async def _handle_timeout(self, ride_id: str, driver_id: str) -> None:
        """Inspects if offer is still pending OFFERED; if so, marks REJECTED and cascades."""
        try:
            from backend.infrastructure.database.session import async_session_maker
            async with async_session_maker() as session:
                assign_stmt = select(RideAssignment).where(
                    RideAssignment.ride_id == ride_id,
                    RideAssignment.driver_id == driver_id
                ).with_for_update()
                res = await session.execute(assign_stmt)
                assignment = res.scalar_one_or_none()

                if assignment and assignment.status == "OFFERED":
                    now = utc_now()
                    assignment.status = "REJECTED"
                    assignment.is_accepted = False
                    assignment.rejection_reason = "DISPATCH_TIMEOUT"
                    assignment.responded_at = now

                    # Cooldown and release lock
                    await redis_client.set(f"cooldown:{driver_id}:{ride_id}", "1", ex=COOLDOWN_TTL_SECONDS)
                    await batch_optimizer.release_driver_lock(driver_id, ride_id)
                    await redis_client.delete(f"dispatch:offer:{ride_id}:{driver_id}")
                    await redis_client.delete(f"driver:active_offer:{driver_id}")

                    await session.commit()
                    logger.info(f"Offer for ride {ride_id} to driver {driver_id} timed out. Cascading...")

                    # Run next cascade
                    svc = SmartDispatchService(session)
                    await svc.search_and_dispatch(ride_id)
        except Exception as e:
            logger.error(f"Error handling timeout for ride {ride_id}: {e}", exc_info=True)

    async def _background_cascade(self, ride_id: str) -> None:
        """Helper to run cascading dispatch in an isolated async session."""
        try:
            from backend.infrastructure.database.session import async_session_maker
            async with async_session_maker() as session:
                svc = SmartDispatchService(session)
                await svc.search_and_dispatch(ride_id)
        except Exception as e:
            logger.error(f"Background cascade failed for ride {ride_id}: {e}", exc_info=True)
