import json
import math
from datetime import datetime, timezone, timedelta
from typing import Any, Dict, List, Optional

from fastapi import HTTPException
from sqlalchemy import and_, select, update
from sqlalchemy.ext.asyncio import AsyncSession

from backend.domain.models import (
    DriverAvailability, DriverDispatchPreferences, DriverLocation, DriverProfile,
    Ride, RideAssignment, RideDestinationChangeRequest, RideOperationalEvent,
    RideStop, VehicleCategory, User,
)
from backend.domain.models.models import utc_now
from backend.application.services.fare_service import FareService
from backend.domain.state_machine.ride_state_machine import RideStateMachine

OFFER_SECONDS = 25
NO_SHOW_GRACE_MINUTES = 5
DISPATCH_REASONS = {"TOO_FAR", "FARE_TOO_LOW", "WRONG_CATEGORY", "UNAVAILABLE", "OTHER"}


def distance_km(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    r = 6371.0
    dlat, dlng = math.radians(lat2 - lat1), math.radians(lng2 - lng1)
    a = math.sin(dlat / 2) ** 2 + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dlng / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


class DriverTripOperationsService:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def _driver(self, user_id: str) -> DriverProfile:
        result = await self.db.execute(select(DriverProfile).where(DriverProfile.user_id == user_id))
        driver = result.scalar_one_or_none()
        if driver is None:
            raise HTTPException(404, "Driver profile not found.")
        return driver

    async def _preferences(self, driver_id: str) -> DriverDispatchPreferences:
        result = await self.db.execute(select(DriverDispatchPreferences).where(DriverDispatchPreferences.driver_id == driver_id))
        pref = result.scalar_one_or_none()
        if pref is None:
            pref = DriverDispatchPreferences(driver_id=driver_id)
            self.db.add(pref)
            await self.db.flush()
        return pref

    async def get_preferences(self, user_id: str) -> Dict[str, Any]:
        driver = await self._driver(user_id)
        pref = await self._preferences(driver.id)
        return self._preferences_dict(pref)

    @staticmethod
    def _preferences_dict(pref: DriverDispatchPreferences) -> Dict[str, Any]:
        try:
            categories = json.loads(pref.vehicle_categories_json or "[]")
        except (TypeError, json.JSONDecodeError):
            categories = []
        return {"minimum_fare_cents": pref.minimum_fare_cents,
                "maximum_pickup_distance_km": pref.maximum_pickup_distance_km,
                "vehicle_categories": categories,
                "navigation_provider": pref.navigation_provider,
                "voice_guidance_enabled": pref.voice_guidance_enabled,
                "reroute_on_closure_enabled": pref.reroute_on_closure_enabled}

    async def update_preferences(self, user_id: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        driver = await self._driver(user_id)
        pref = await self._preferences(driver.id)
        pref.minimum_fare_cents = payload["minimum_fare_cents"]
        pref.maximum_pickup_distance_km = payload["maximum_pickup_distance_km"]
        pref.vehicle_categories_json = json.dumps(payload["vehicle_categories"])
        pref.navigation_provider = payload["navigation_provider"]
        pref.voice_guidance_enabled = payload["voice_guidance_enabled"]
        pref.reroute_on_closure_enabled = payload["reroute_on_closure_enabled"]
        await self.db.commit()
        return self._preferences_dict(pref)

    async def _latest_location(self, driver_id: str) -> Optional[DriverLocation]:
        result = await self.db.execute(select(DriverLocation).where(DriverLocation.driver_id == driver_id).order_by(DriverLocation.recorded_at.desc()).limit(1))
        location = result.scalar_one_or_none()
        if location and (utc_now() - (location.recorded_at.replace(tzinfo=timezone.utc) if location.recorded_at.tzinfo is None else location.recorded_at)).total_seconds() <= 180:
            return location
        return None

    async def get_offers(self, user_id: str) -> List[Dict[str, Any]]:
        driver = await self._driver(user_id)
        if driver.approval_status != "APPROVED":
            raise HTTPException(403, "Driver approval is required before receiving ride offers.")
        availability_result = await self.db.execute(select(DriverAvailability).where(DriverAvailability.driver_id == driver.id))
        availability = availability_result.scalar_one_or_none()
        if not availability or not availability.is_online or availability.is_busy:
            return []
        pref = await self._preferences(driver.id)
        now = utc_now()
        expired_result = await self.db.execute(select(RideAssignment).where(RideAssignment.driver_id == driver.id, RideAssignment.status == "OFFERED", RideAssignment.expires_at <= now))
        expired = expired_result.scalars().all()
        for item in expired:
            item.status = "EXPIRED"
            item.responded_at = now
        location = await self._latest_location(driver.id)
        if location is None:
            await self.db.commit()
            return []
        offered_ids_result = await self.db.execute(select(RideAssignment.ride_id).where(RideAssignment.driver_id == driver.id))
        previously_offered = set(offered_ids_result.scalars().all())
        rides_result = await self.db.execute(select(Ride).where(Ride.status == "REQUESTED", Ride.assigned_driver_id.is_(None), Ride.is_scheduled.is_(False)).order_by(Ride.requested_at.asc()).limit(50))
        category_ids = set(json.loads(pref.vehicle_categories_json or "[]"))
        candidates = []
        for ride in rides_result.scalars().all():
            fare_cents = ride.final_fare_cents if ride.final_fare_cents is not None else ride.estimated_fare_cents
            if fare_cents < pref.minimum_fare_cents:
                continue
            if category_ids and ride.vehicle_category_id not in category_ids:
                cat_result = await self.db.execute(select(VehicleCategory.code).where(VehicleCategory.id == ride.vehicle_category_id))
                cat_code = cat_result.scalar_one_or_none()
                if not cat_code or cat_code not in category_ids:
                    continue
            pickup_distance = distance_km(location.latitude, location.longitude, ride.pickup_latitude, ride.pickup_longitude) if location else None
            if pickup_distance is None or pickup_distance > pref.maximum_pickup_distance_km:
                continue
            candidates.append((ride, pickup_distance))

        # Offer to a small nearest-first wave. The next wave opens after current
        # offers are declined or expire, with a larger wave at each escalation.
        for ride, pickup_distance in candidates:
            active_result = await self.db.execute(select(RideAssignment.id).where(RideAssignment.ride_id == ride.id, RideAssignment.status == "OFFERED", RideAssignment.expires_at > now).limit(1))
            if active_result.scalar_one_or_none():
                continue
            attempts_result = await self.db.execute(select(RideAssignment).where(RideAssignment.ride_id == ride.id))
            attempts = attempts_result.scalars().all()
            prior_driver_ids = {a.driver_id for a in attempts}
            level = len(attempts) // 3
            wave_size = min(8, 2 + level * 2)
            fare_cents = ride.final_fare_cents if ride.final_fare_cents is not None else ride.estimated_fare_cents
            available_result = await self.db.execute(
                select(DriverProfile, DriverAvailability)
                .join(DriverAvailability, DriverAvailability.driver_id == DriverProfile.id)
                .where(DriverProfile.approval_status == "APPROVED", DriverAvailability.is_online.is_(True), DriverAvailability.is_busy.is_(False))
            )
            ranked = []
            for candidate_driver, candidate_availability in available_result.all():
                if candidate_driver.id in prior_driver_ids:
                    continue
                candidate_pref = await self._preferences(candidate_driver.id)
                if fare_cents < candidate_pref.minimum_fare_cents:
                    continue
                candidate_categories = json.loads(candidate_pref.vehicle_categories_json or "[]")
                if candidate_categories and ride.vehicle_category_id not in candidate_categories:
                    category_code_result = await self.db.execute(select(VehicleCategory.code).where(VehicleCategory.id == ride.vehicle_category_id))
                    if category_code_result.scalar_one_or_none() not in candidate_categories:
                        continue
                candidate_loc = await self._latest_location(candidate_driver.id)
                candidate_distance = distance_km(candidate_loc.latitude, candidate_loc.longitude, ride.pickup_latitude, ride.pickup_longitude) if candidate_loc else None
                if candidate_distance is not None and candidate_distance > candidate_pref.maximum_pickup_distance_km:
                    continue
                if candidate_distance is None and candidate_driver.id != driver.id:
                    continue
                ranked.append((candidate_driver, candidate_distance))
            ranked.sort(key=lambda row: row[1] if row[1] is not None else 1e9)
            for candidate_driver, _distance in ranked[:wave_size]:
                self.db.add(RideAssignment(ride_id=ride.id, driver_id=candidate_driver.id, status="OFFERED", offered_at=now, expires_at=now + timedelta(seconds=OFFER_SECONDS), escalation_level=level))
            if ranked:
                await self.db.flush()

        await self.db.flush()
        # Return only persisted active offers addressed to this authenticated driver.
        offer_result = await self.db.execute(select(RideAssignment, Ride).join(Ride, Ride.id == RideAssignment.ride_id).where(RideAssignment.driver_id == driver.id, RideAssignment.status == "OFFERED", RideAssignment.expires_at > now, Ride.status == "REQUESTED").order_by(RideAssignment.offered_at.asc()))
        category_cache: Dict[str, str] = {}
        response = []
        for assignment, ride in offer_result.all():
            if ride.vehicle_category_id not in category_cache:
                result = await self.db.execute(select(VehicleCategory.name).where(VehicleCategory.id == ride.vehicle_category_id))
                category_cache[ride.vehicle_category_id] = result.scalar_one_or_none() or "Vehicle category"
            this_distance = distance_km(location.latitude, location.longitude, ride.pickup_latitude, ride.pickup_longitude) if location else None
            dest_parts = [part.strip() for part in ride.destination_address.split(",") if part.strip()]
            response.append({"ride_id": ride.id, "pickup_address": ride.pickup_address,
                             "destination_area": ", ".join(dest_parts[-2:]) if dest_parts else "Area unavailable",
                             "vehicle_category": category_cache[ride.vehicle_category_id],
                             "estimated_fare_cents": ride.final_fare_cents if ride.final_fare_cents is not None else ride.estimated_fare_cents,
                             "estimated_driver_earnings_cents": round((ride.final_fare_cents if ride.final_fare_cents is not None else ride.estimated_fare_cents) * 0.8),
                             "pickup_distance_km": round(this_distance, 1) if this_distance is not None else None,
                             "estimated_trip_distance_km": ride.estimated_distance_km,
                             "expires_at": assignment.expires_at.isoformat(), "escalation_level": assignment.escalation_level})
        return response

    async def reject_offer(self, user_id: str, ride_id: str, reason: str) -> Dict[str, Any]:
        from backend.application.services.cascading_dispatch_service import CascadingDispatchService
        return await CascadingDispatchService(self.db).reject_offer_and_cascade(user_id, ride_id, reason)

    async def record_event(self, ride_id: str, user_id: str, action: str, details: Dict[str, Any]) -> Ride:
        driver = await self._driver(user_id)
        ride = await self.db.get(Ride, ride_id)
        if ride is None:
            raise HTTPException(404, "Ride not found.")
        if ride.assigned_driver_id != driver.id:
            raise HTTPException(403, "Only the assigned driver can perform this trip action.")
        self.db.add(RideOperationalEvent(ride_id=ride.id, actor_user_id=user_id, action=action, details_json=json.dumps(details, ensure_ascii=False)))
        return ride

    async def prepare_trip(self, ride_id: str, user_id: str, checklist: Dict[str, bool]) -> Dict[str, Any]:
        ride = await self.record_event(ride_id, user_id, "START_CHECKLIST", checklist)
        required = {"passenger_present", "vehicle_safe", "belongings_checked", "route_confirmed"}
        if set(checklist) != required or not all(checklist.values()):
            raise HTTPException(400, "Confirm all four pre-departure checks before starting the trip.")
        if ride.status != "ARRIVED":
            raise HTTPException(409, "Pre-departure checks are available after arrival at pickup.")
        await self.db.commit()
        return {"status": "READY_TO_START", "message": "Pre-departure checks recorded. Verify the passenger with their trip OTP."}

    async def mark_stop(self, ride_id: str, stop_id: str, user_id: str, action: str) -> Dict[str, Any]:
        ride = await self.record_event(ride_id, user_id, f"STOP_{action}", {"stop_id": stop_id})
        if ride.status not in ("IN_PROGRESS", "PAUSED"):
            raise HTTPException(409, "Stops can only be updated during an active trip.")
        stop = await self.db.get(RideStop, stop_id)
        if stop is None or stop.ride_id != ride_id:
            raise HTTPException(404, "Trip stop not found.")
        if action == "ARRIVED":
            if stop.status != "PENDING":
                raise HTTPException(409, "This stop is not awaiting arrival.")
            stop.status = "ARRIVED"
            stop.arrived_at = utc_now()
        else:
            if stop.status != "ARRIVED":
                raise HTTPException(409, "Mark arrival before departing this stop.")
            stop.status = "COMPLETED"
            stop.completed_at = utc_now()
        await self.db.commit()
        return {"stop_id": stop.id, "status": stop.status, "action_at": (stop.arrived_at if action == "ARRIVED" else stop.completed_at).isoformat()}

    async def pause_or_resume(self, ride_id: str, user_id: str, pause: bool, reason: str) -> Dict[str, Any]:
        ride = await self.record_event(ride_id, user_id, "TRIP_PAUSED" if pause else "TRIP_RESUMED", {"reason": reason})
        expected = "IN_PROGRESS" if pause else "PAUSED"
        if ride.status != expected:
            raise HTTPException(409, f"Trip must be {expected.lower().replace('_', ' ')} for this action.")
        previous = ride.status
        ride.status = "PAUSED" if pause else "IN_PROGRESS"
        event = __import__("backend.domain.models", fromlist=["RideStatusEvent"]).RideStatusEvent(
            ride_id=ride.id, previous_status=previous, new_status=ride.status,
            triggered_by_user_id=user_id, trigger_actor="DRIVER", notes=reason
        )
        self.db.add(event)
        await self.db.commit()
        return {"ride_id": ride.id, "status": ride.status}

    async def report_no_show(self, ride_id: str, user_id: str, evidence_note: str) -> Dict[str, Any]:
        ride = await self.record_event(ride_id, user_id, "PASSENGER_NO_SHOW", {"evidence_note": evidence_note})
        now = utc_now()
        arrived = ride.arrived_at.replace(tzinfo=timezone.utc) if ride.arrived_at and ride.arrived_at.tzinfo is None else ride.arrived_at
        if ride.status != "ARRIVED" or not arrived or now - arrived < timedelta(minutes=NO_SHOW_GRACE_MINUTES):
            raise HTTPException(409, "Record an arrival and complete the five-minute no-show wait before filing.")
        ride.status = "CANCELLED"
        ride.cancelled_at = now
        ride.cancellation_reason = f"PASSENGER_NO_SHOW: {evidence_note}"
        ride.cancelled_by_role = "DRIVER"
        from backend.domain.models import RideStatusEvent
        self.db.add(RideStatusEvent(ride_id=ride.id, previous_status="ARRIVED", new_status="CANCELLED", triggered_by_user_id=user_id, trigger_actor="DRIVER", notes="Passenger no-show after five-minute wait; no fee applied."))
        availability_result = await self.db.execute(select(DriverAvailability).where(DriverAvailability.driver_id == ride.assigned_driver_id))
        availability = availability_result.scalar_one_or_none()
        if availability:
            availability.is_busy = False
            availability.updated_at = now
        await self.db.commit()
        return {"ride_id": ride.id, "status": ride.status, "no_show_recorded_at": now.isoformat(), "fee_applied": False, "message": "No-show recorded with arrival time and driver evidence note. No fee is applied without a configured policy."}

    async def submit_completion_checklist(self, ride_id: str, user_id: str, checklist: Dict[str, bool]) -> Dict[str, Any]:
        required = {"passenger_belongings_returned", "vehicle_cabin_checked", "dropoff_safe", "fare_explained"}
        if set(checklist) != required or not all(checklist.values()):
            raise HTTPException(400, "Confirm all trip completion checks before finishing.")
        ride = await self.record_event(ride_id, user_id, "COMPLETION_CHECKLIST", checklist)
        if ride.status != "IN_PROGRESS":
            raise HTTPException(409, "A paused or inactive ride cannot be completed.")
        await self.db.commit()
        return {"status": "READY_TO_COMPLETE"}

    async def report_issue(self, ride_id: str, user_id: str, category: str, description: str) -> Dict[str, Any]:
        ride = await self.record_event(ride_id, user_id, "TRIP_ISSUE", {"category": category, "description": description})
        if ride.status not in {"ASSIGNED", "ARRIVED", "IN_PROGRESS", "PAUSED"}:
            raise HTTPException(409, "Issues can only be reported for an active trip.")
        await self.db.commit()
        return {"ride_id": ride_id, "status": "ISSUE_RECORDED", "message": "Trip issue saved for operations review."}

    async def submit_destination_change(self, ride_id: str, user: User, address: str, lat: float, lng: float) -> Dict[str, Any]:
        ride = await self.db.get(Ride, ride_id)
        if ride is None:
            raise HTTPException(404, "Ride not found.")
        from backend.domain.models import CustomerProfile
        customer_result = await self.db.execute(select(CustomerProfile).where(CustomerProfile.id == ride.customer_id))
        customer = customer_result.scalar_one_or_none()
        if user.role != "admin" and (customer is None or customer.user_id != user.id):
            raise HTTPException(403, "Only the passenger for this ride may propose a destination change.")
        if ride.status not in {"ASSIGNED", "ARRIVED", "IN_PROGRESS"}:
            raise HTTPException(409, "Destination changes are not available for this ride status.")
        existing_result = await self.db.execute(select(RideDestinationChangeRequest).where(RideDestinationChangeRequest.ride_id == ride.id, RideDestinationChangeRequest.status.in_(["PENDING_CUSTOMER_CONFIRMATION", "PENDING_DRIVER_APPROVAL"])))
        if existing_result.scalar_one_or_none():
            raise HTTPException(409, "A destination change is already awaiting approval.")
        estimate = await FareService(self.db).calculate_estimate(ride.vehicle_category_id, ride.pickup_latitude, ride.pickup_longitude, lat, lng)
        proposed = RideDestinationChangeRequest(ride_id=ride.id, requested_by_user_id=user.id,
            destination_address=address, destination_latitude=lat, destination_longitude=lng,
            proposed_fare_cents=estimate["estimated_fare_cents"], proposed_distance_km=estimate["distance_km"],
            proposed_duration_minutes=estimate["estimated_duration_minutes"],
            fare_difference_cents=estimate["estimated_fare_cents"] - (ride.final_fare_cents or ride.estimated_fare_cents))
        self.db.add(proposed)
        await self.db.commit()
        await self.db.refresh(proposed)
        return self._change_dict(proposed)

    @staticmethod
    def _change_dict(change: RideDestinationChangeRequest) -> Dict[str, Any]:
        return {"request_id": change.id, "ride_id": change.ride_id, "status": change.status,
                "destination_address": change.destination_address, "proposed_fare_cents": change.proposed_fare_cents,
                "fare_difference_cents": change.fare_difference_cents, "proposed_distance_km": change.proposed_distance_km,
                "proposed_duration_minutes": change.proposed_duration_minutes, "rejection_reason": change.rejection_reason}

    async def customer_confirm_change(self, request_id: str, user: User, accept: bool) -> Dict[str, Any]:
        change = await self.db.get(RideDestinationChangeRequest, request_id)
        if change is None:
            raise HTTPException(404, "Destination change request not found.")
        from backend.domain.models import CustomerProfile
        ride = await self.db.get(Ride, change.ride_id)
        customer_result = await self.db.execute(select(CustomerProfile).where(CustomerProfile.id == ride.customer_id))
        customer = customer_result.scalar_one_or_none()
        if user.role != "admin" and (customer is None or customer.user_id != user.id):
            raise HTTPException(403, "Only the passenger for this ride can approve its fare quote.")
        if change.status != "PENDING_CUSTOMER_CONFIRMATION":
            raise HTTPException(409, "This fare quote has already been answered.")
        change.status = "PENDING_DRIVER_APPROVAL" if accept else "REJECTED_BY_CUSTOMER"
        change.customer_responded_at = utc_now()
        await self.db.commit()
        return self._change_dict(change)

    async def driver_resolve_change(self, ride_id: str, request_id: str, user_id: str, accept: bool, reason: str = "") -> Dict[str, Any]:
        driver = await self._driver(user_id)
        change = await self.db.get(RideDestinationChangeRequest, request_id)
        ride = await self.db.get(Ride, ride_id)
        if change is None or ride is None or change.ride_id != ride_id:
            raise HTTPException(404, "Destination change request not found.")
        if ride.assigned_driver_id != driver.id:
            raise HTTPException(403, "Only the assigned driver can respond to this destination change.")
        if change.status != "PENDING_DRIVER_APPROVAL":
            raise HTTPException(409, "Passenger approval is required before the driver can accept this change.")
        change.driver_responded_at = utc_now()
        if accept:
            old_fare = ride.final_fare_cents or ride.estimated_fare_cents
            ride.destination_address = change.destination_address
            ride.destination_latitude = change.destination_latitude
            ride.destination_longitude = change.destination_longitude
            ride.estimated_fare_cents = change.proposed_fare_cents
            ride.final_fare_cents = change.proposed_fare_cents
            ride.estimated_distance_km = change.proposed_distance_km
            ride.estimated_duration_minutes = change.proposed_duration_minutes
            ride.fare_difference_cents = change.proposed_fare_cents - old_fare
            ride.fare_change_reason = "Passenger-approved destination change; driver accepted revised route and fare."
            change.status = "APPROVED"
        else:
            change.status = "REJECTED_BY_DRIVER"
            change.rejection_reason = reason
        await self.db.commit()
        return self._change_dict(change)

    async def pending_driver_destination_change(self, ride_id: str, user_id: str) -> Optional[Dict[str, Any]]:
        driver = await self._driver(user_id)
        ride = await self.db.get(Ride, ride_id)
        if ride is None:
            raise HTTPException(404, "Ride not found.")
        if ride.assigned_driver_id != driver.id:
            raise HTTPException(403, "Only the assigned driver can view trip change requests.")
        result = await self.db.execute(select(RideDestinationChangeRequest).where(
            RideDestinationChangeRequest.ride_id == ride_id,
            RideDestinationChangeRequest.status == "PENDING_DRIVER_APPROVAL"
        ).order_by(RideDestinationChangeRequest.created_at.desc()).limit(1))
        change = result.scalar_one_or_none()
        return self._change_dict(change) if change else None
