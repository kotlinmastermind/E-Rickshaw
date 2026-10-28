import math
import uuid
import secrets
from datetime import datetime, timezone, timedelta
from typing import Dict, Any, List, Optional, Tuple
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, update
from fastapi import HTTPException, status

from backend.domain.models import (
    PooledTrip, PooledBooking, PooledTripStop, PassengerOccupancyEvent, PooledTripMessage, SharedRidePreference,
    Ride, FareRule, FareEstimate, CustomerProfile, DriverProfile, VehicleCategory,
    AuditLog
)
from backend.application.services.fare_service import FareService
from backend.integrations.maps.provider import get_maps_provider

class PoolService:
    def __init__(self, db: AsyncSession):
        self.db = db
        self.maps_provider = get_maps_provider()
        self.fare_service = FareService(db)

    async def calculate_shared_fare_estimate(
        self,
        category_id: str,
        pickup_lat: float, pickup_lng: float,
        dest_lat: float, dest_lng: float,
        seats_requested: int = 1
    ) -> Dict[str, Any]:
        """
        Calculates deterministic shared ride fare estimate with explicit pooling discount
        and seat multiplier. Persists FareEstimate for auditable validation.
        """
        if seats_requested < 1 or seats_requested > 4:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Seat reservation must be between 1 and 4 seats."
            )

        category = await self.db.get(VehicleCategory, category_id)
        if not category or not category.is_active:
            raise HTTPException(status_code=404, detail="Shared-ride vehicle category is unavailable.")
        if seats_requested > category.capacity:
            raise HTTPException(status_code=400, detail=f"This vehicle category supports up to {category.capacity} passengers.")

        rule = await self.fare_service.get_active_rule_for_category(category_id)
        route = await self.maps_provider.get_route(pickup_lat, pickup_lng, dest_lat, dest_lng)

        # Baseline private solo calculation in minor units (paise/cents)
        distance_charge = round(route.distance_km * rule.per_km_cents)
        time_charge = round(route.duration_minutes * rule.per_minute_cents)
        solo_subtotal = rule.base_fare_cents + distance_charge + time_charge
        solo_before_surge = max(solo_subtotal, rule.minimum_fare_cents)
        surge_multiplier = max(rule.surge_multiplier or 1.0, await self.fare_service.get_surge_multiplier(category_id, pickup_lat, pickup_lng))
        solo_fare_cents = round(solo_before_surge * surge_multiplier)

        # Shared discount: default 25% discount off solo fare
        discount_pct = getattr(rule, "shared_discount_percentage", 25.0)
        discount_factor = max(0.0, 1.0 - (discount_pct / 100.0))

        # Additional seat pricing: 1st seat at discounted rate, subsequent seats at 70% rate
        seat_multiplier = 1.0 + (seats_requested - 1) * 0.70
        base_shared_cents = round(solo_fare_cents * discount_factor * seat_multiplier)

        # Ensure minimum fare guard is satisfied per booking
        final_shared_fare_cents = max(base_shared_cents, rule.minimum_fare_cents)
        savings_cents = max(0, (solo_fare_cents * seats_requested) - final_shared_fare_cents)

        expires_at = datetime.now(timezone.utc) + timedelta(minutes=15)
        estimate = FareEstimate(
            category_id=category_id,
            fare_rule_id=rule.id,
            pickup_latitude=pickup_lat,
            pickup_longitude=pickup_lng,
            destination_latitude=dest_lat,
            destination_longitude=dest_lng,
            distance_km=route.distance_km,
            estimated_duration_minutes=route.duration_minutes,
            estimated_fare_cents=final_shared_fare_cents,
            surge_multiplier=surge_multiplier,
            expires_at=expires_at
        )
        self.db.add(estimate)
        await self.db.commit()
        await self.db.refresh(estimate)

        return {
            "estimate_id": estimate.id,
            "fare_rule_id": rule.id,
            "pricing_policy_version": rule.version,
            "category_id": category_id,
            "is_shared": True,
            "seats_requested": seats_requested,
            "distance_km": round(route.distance_km, 2),
            "estimated_duration_minutes": round(route.duration_minutes, 1),
            "solo_fare_cents": solo_fare_cents,
            "shared_fare_cents": final_shared_fare_cents,
            "shared_discount_percentage": discount_pct,
            "surge_multiplier": surge_multiplier,
            "savings_cents": savings_cents,
            "guest_savings_cents": savings_cents,
            "total_payable_cents": final_shared_fare_cents,
            "solo_fare_per_seat_cents": solo_fare_cents,
            "reservation_expires_in_seconds": 900,
            "fare_allocation": {
                "solo_fare_cents": solo_fare_cents,
                "shared_discount_percentage": discount_pct,
                "seat_multiplier": seat_multiplier,
                "shared_fare_cents": final_shared_fare_cents,
                "savings_cents": savings_cents,
            },
            "currency": "INR",
            "expires_at": expires_at.isoformat(),
            "route_polyline": route.polyline
        }

    async def find_route_matches(
        self,
        category_id: str,
        pickup_lat: float, pickup_lng: float,
        dest_lat: float, dest_lng: float,
        seats_requested: int = 1,
        max_detour_km: float = 5.0
    ) -> Dict[str, Any]:
        """
        Evaluates candidate pooled trips for route deviation, additional detour distance,
        capacity constraints, and returns eligible trips or explainable rejection reasons.
        Does not leak sensitive customer data.
        """
        # Only not-yet-departed pools accept new matches. Stable creation ordering
        # ensures equal-detour requests do not continually favor the newest pool.
        category = await self.db.get(VehicleCategory, category_id)
        if not category or not category.is_active:
            raise HTTPException(status_code=404, detail="Shared-ride vehicle category is unavailable.")
        if seats_requested < 1 or seats_requested > category.capacity:
            raise HTTPException(status_code=400, detail=f"Requested seats exceed this category's passenger capacity ({category.capacity}).")
        stmt = select(PooledTrip).where(
            PooledTrip.vehicle_category_id == category_id,
            PooledTrip.status == "OPEN"
        ).order_by(PooledTrip.created_at.asc(), PooledTrip.id.asc())
        res = await self.db.execute(stmt)
        candidate_trips = res.scalars().all()

        eligible_candidates: List[Dict[str, Any]] = []
        rejection_logs: List[Dict[str, Any]] = []

        direct_route = await self.maps_provider.get_route(pickup_lat, pickup_lng, dest_lat, dest_lng)
        direct_dist = direct_route.distance_km

        for trip in candidate_trips:
            trip_remaining_seats = trip.max_seats - trip.reserved_seats
            if trip_remaining_seats < seats_requested:
                rejection_logs.append({
                    "trip_id": trip.id,
                    "reason_code": "INSUFFICIENT_SEATS",
                    "explanation": f"Trip has {trip_remaining_seats} seats remaining; {seats_requested} requested."
                })
                continue

            # Load active stops for this trip
            stops_stmt = select(PooledTripStop).where(
                PooledTripStop.pooled_trip_id == trip.id,
                PooledTripStop.status != "SKIPPED"
            ).order_by(PooledTripStop.sequence_order)
            stops_res = await self.db.execute(stops_stmt)
            existing_stops = stops_res.scalars().all()

            if not existing_stops:
                # Open trip with no stops yet
                eligible_candidates.append({
                    "trip_id": trip.id,
                    "detour_km": 0.0,
                    "pickup_eta_minutes": 3.0,
                    "available_seats": trip_remaining_seats,
                    "driver_assigned": trip.driver_id is not None,
                    "created_at": trip.created_at.isoformat() if trip.created_at else ""
                })
                continue

            # Heuristic detour calculation:
            # Distance from first stop to new pickup, and from new dropoff to last stop
            first_stop = existing_stops[0]
            last_stop = existing_stops[-1]

            leg1 = await self.maps_provider.get_route(first_stop.latitude, first_stop.longitude, pickup_lat, pickup_lng)
            leg2 = await self.maps_provider.get_route(dest_lat, dest_lng, last_stop.latitude, last_stop.longitude)
            
            # Detour estimate
            detour_km = round(max(0.0, (leg1.distance_km + leg2.distance_km) - (direct_dist * 0.5)), 2)
            
            if detour_km > max_detour_km:
                rejection_logs.append({
                    "trip_id": trip.id,
                    "reason_code": "EXCESSIVE_DETOUR",
                    "explanation": f"Detour of {detour_km} km exceeds maximum permitted threshold of {max_detour_km} km."
                })
                continue

            pickup_eta = round(max(2.0, leg1.duration_minutes), 1)
            eligible_candidates.append({
                "trip_id": trip.id,
                "detour_km": detour_km,
                "pickup_eta_minutes": pickup_eta,
                "available_seats": trip_remaining_seats,
                "driver_assigned": trip.driver_id is not None,
                "created_at": trip.created_at.isoformat() if trip.created_at else ""
            })

        # Rank eligible candidates by least detour distance
        eligible_candidates.sort(key=lambda c: (c["detour_km"], c["created_at"], c["trip_id"]))

        return {
            "matches_found": len(eligible_candidates),
            "candidates": eligible_candidates,
            "rejections_evaluated": len(rejection_logs),
            "rejection_reasons": rejection_logs,
            "fairness_policy": "Eligible pools rank by lowest detour, then oldest open pool, then stable trip-id tie break.",
            "maximum_detour_km": max_detour_km,
        }

    async def book_shared_ride(
        self,
        customer_user_id: str,
        category_id: str,
        estimate_id: str,
        pickup_address: str,
        pickup_lat: float, pickup_lng: float,
        dest_address: str,
        dest_lat: float, dest_lng: float,
        seats_reserved: int = 1,
        target_pooled_trip_id: Optional[str] = None,
        fallback_policy: str = "FAIL",  # CREATE_NEW_POOL, FAIL
        share_stop_details_with_co_passengers: bool = False,
        maximum_detour_km: float = 5.0,
    ) -> Dict[str, Any]:
        """
        Atomically books a shared ride, performs capacity validation, seat reservation,
        deterministic fare allocation, and records occupancy event.
        Guarantees that capacity cannot be exceeded and prevents silent conversion to private ride.
        """
        # Validate customer profile
        cust_stmt = select(CustomerProfile).where(CustomerProfile.user_id == customer_user_id)
        cust_res = await self.db.execute(cust_stmt)
        customer = cust_res.scalars().first()
        if not customer:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Customer profile not found.")

        # Validate estimate
        est_stmt = select(FareEstimate).where(FareEstimate.id == estimate_id)
        est_res = await self.db.execute(est_stmt)
        estimate = est_res.scalars().first()
        if not estimate:
            raise HTTPException(status_code=404, detail="Fare estimate not found. Recalculate the shared fare.")
        exp = estimate.expires_at
        if exp.tzinfo is None:
            exp = exp.replace(tzinfo=timezone.utc)
        if exp < datetime.now(timezone.utc):
            raise HTTPException(status_code=status.HTTP_410_GONE, detail="Fare estimate has expired. Please recalculate.")

        # Validate fare rule
        rule_stmt = select(FareRule).where(FareRule.id == estimate.fare_rule_id)
        rule_res = await self.db.execute(rule_stmt)
        fare_rule = rule_res.scalars().first()
        if not fare_rule:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Fare rule not found.")
        category = await self.db.get(VehicleCategory, category_id)
        if not category or not category.is_active or estimate.category_id != category_id:
            raise HTTPException(status_code=409, detail="The estimated vehicle category is no longer eligible for shared rides.")
        if seats_reserved < 1 or seats_reserved > 4 or seats_reserved > category.capacity:
            raise HTTPException(status_code=400, detail=f"Requested seats exceed this category's passenger capacity ({category.capacity}).")

        # Re-evaluate route compatibility on the server at commit time; the client
        # cannot force an unrelated pool by supplying an arbitrary trip id.
        pool_matches = await self.find_route_matches(
            category_id=category_id, pickup_lat=pickup_lat, pickup_lng=pickup_lng,
            dest_lat=dest_lat, dest_lng=dest_lng, seats_requested=seats_reserved,
            max_detour_km=maximum_detour_km,
        )
        compatible_ids = [str(item["trip_id"]) for item in pool_matches["candidates"]]
        if target_pooled_trip_id and target_pooled_trip_id not in compatible_ids and fallback_policy != "CREATE_NEW_POOL":
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="The selected pool no longer meets route, detour or seat requirements. Refresh matches or explicitly allow another pool.")
        ordered_ids = ([target_pooled_trip_id] if target_pooled_trip_id in compatible_ids else []) + [item for item in compatible_ids if item != target_pooled_trip_id]
        pooled_trip: Optional[PooledTrip] = None
        for candidate_id in ordered_ids:
            trip_stmt = select(PooledTrip).where(
                PooledTrip.id == candidate_id,
                PooledTrip.vehicle_category_id == category_id,
                PooledTrip.status == "OPEN",
                PooledTrip.max_seats <= category.capacity,
                (PooledTrip.max_seats - PooledTrip.reserved_seats) >= seats_reserved,
            ).with_for_update()
            candidate = (await self.db.execute(trip_stmt)).scalars().first()
            if candidate:
                pooled_trip = candidate
                break

        if not pooled_trip:
            if fallback_policy != "CREATE_NEW_POOL":
                raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="No compatible open pool is available. Explicitly allow a new shared pool or refresh matches.")
            # Create a new PooledTrip
            pooled_trip = PooledTrip(
                vehicle_category_id=category_id,
                status="OPEN",
                max_seats=category.capacity,
                reserved_seats=0,
                current_occupancy=0,
                pricing_policy_version=fare_rule.version
            )
            self.db.add(pooled_trip)
            await self.db.flush()

        # Capacity check invariant
        if pooled_trip.reserved_seats + seats_reserved > pooled_trip.max_seats:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail=f"Capacity exceeded: Requested {seats_reserved} seats but only {pooled_trip.max_seats - pooled_trip.reserved_seats} available."
            )

        # Fare allocation calculations (exact minor units / cents)
        total_fare_cents = estimate.estimated_fare_cents
        solo_subtotal = fare_rule.base_fare_cents + round(estimate.distance_km * fare_rule.per_km_cents) + round(estimate.estimated_duration_minutes * fare_rule.per_minute_cents)
        solo_per_seat_cents = round(max(solo_subtotal, fare_rule.minimum_fare_cents) * estimate.surge_multiplier)
        savings_cents = max(0, solo_per_seat_cents * seats_reserved - total_fare_cents)
        discount_factor = max(0.0, 1.0 - (getattr(fare_rule, "shared_discount_percentage", 25.0) / 100.0))
        base_split_cents = round(fare_rule.base_fare_cents * discount_factor)
        distance_time_cents = max(0, total_fare_cents - base_split_cents)

        # Platform commission: 20%, driver earnings: 80% (Conservation of monetary totals: exact cents)
        commission_rate = 0.20
        platform_commission_cents = round(total_fare_cents * commission_rate)
        driver_earnings_cents = total_fare_cents - platform_commission_cents

        # Generate OTP
        start_otp = "".join([str(secrets.randbelow(10)) for _ in range(6)])

        # Create Ride
        ride = Ride(
            customer_id=customer.id,
            vehicle_category_id=category_id,
            fare_rule_id=fare_rule.id,
            status="REQUESTED",
            pickup_address=pickup_address,
            pickup_latitude=pickup_lat,
            pickup_longitude=pickup_lng,
            destination_address=dest_address,
            destination_latitude=dest_lat,
            destination_longitude=dest_lng,
            start_otp=start_otp,
            estimated_distance_km=estimate.distance_km,
            estimated_duration_minutes=estimate.estimated_duration_minutes,
            estimated_fare_cents=total_fare_cents,
            final_fare_cents=total_fare_cents,
            payment_method=customer.preferred_payment_method,
            is_shared=True,
            seats_booked=seats_reserved,
            pooled_trip_id=pooled_trip.id
        )
        self.db.add(ride)
        await self.db.flush()

        # Create PooledBooking
        booking = PooledBooking(
            pooled_trip_id=pooled_trip.id,
            ride_id=ride.id,
            customer_id=customer.id,
            seats_reserved=seats_reserved,
            booking_status="CONFIRMED",
            allocated_fare_cents=total_fare_cents,
            base_fare_split_cents=base_split_cents,
            distance_time_fare_cents=distance_time_cents,
            discount_cents=savings_cents,
            driver_earnings_cents=driver_earnings_cents,
            platform_commission_cents=platform_commission_cents,
            pickup_eta_minutes=5.0,
            share_stop_details_with_co_passengers=share_stop_details_with_co_passengers
        )
        self.db.add(booking)
        await self.db.flush()

        # Update PooledTrip state
        old_reserved = pooled_trip.reserved_seats
        pooled_trip.reserved_seats += seats_reserved
        if pooled_trip.reserved_seats >= pooled_trip.max_seats:
            pooled_trip.status = "FULL"

        # Create stops for this booking
        # Determine sequence order based on existing stops
        stops_count_stmt = select(PooledTripStop).where(PooledTripStop.pooled_trip_id == pooled_trip.id)
        stops_count_res = await self.db.execute(stops_count_stmt)
        curr_stops_count = len(stops_count_res.scalars().all())

        pickup_stop = PooledTripStop(
            pooled_trip_id=pooled_trip.id,
            booking_id=booking.id,
            stop_type="PICKUP",
            sequence_order=curr_stops_count + 1,
            address=pickup_address,
            latitude=pickup_lat,
            longitude=pickup_lng,
            status="SCHEDULED"
        )
        dropoff_stop = PooledTripStop(
            pooled_trip_id=pooled_trip.id,
            booking_id=booking.id,
            stop_type="DROPOFF",
            sequence_order=curr_stops_count + 2,
            address=dest_address,
            latitude=dest_lat,
            longitude=dest_lng,
            status="SCHEDULED"
        )
        self.db.add(pickup_stop)
        self.db.add(dropoff_stop)

        # Record occupancy audit event
        occupancy_event = PassengerOccupancyEvent(
            pooled_trip_id=pooled_trip.id,
            booking_id=booking.id,
            event_type="SEATS_RESERVED",
            occupancy_before=old_reserved,
            occupancy_after=pooled_trip.reserved_seats,
            actor_id=customer_user_id,
            notes=f"Reserved {seats_reserved} seats for ride {ride.id}"
        )
        self.db.add(occupancy_event)

        # Record system audit log
        audit_log = AuditLog(
            actor_id=customer_user_id,
            actor_role="customer",
            action="POOL_SEAT_RESERVE",
            target_resource="pooled_bookings",
            target_id=booking.id,
            status="SUCCESS",
            metadata_json=f'{{"trip_id": "{pooled_trip.id}", "seats": {seats_reserved}, "fare_cents": {total_fare_cents}}}'
        )
        self.db.add(audit_log)

        await self.db.commit()
        await self.db.refresh(booking)
        await self.db.refresh(pooled_trip)

        return {
            "ride_id": ride.id,
            "pooled_trip_id": pooled_trip.id,
            "booking_id": booking.id,
            "seats_reserved": seats_reserved,
            "total_trip_reserved_seats": pooled_trip.reserved_seats,
            "trip_max_seats": pooled_trip.max_seats,
            "allocated_fare_cents": booking.allocated_fare_cents,
            "driver_earnings_cents": booking.driver_earnings_cents,
            "platform_commission_cents": booking.platform_commission_cents,
            "discount_cents": booking.discount_cents,
            "pickup_eta_minutes": booking.pickup_eta_minutes,
            "start_otp": start_otp,
            "trip_status": pooled_trip.status,
            "shared_fare_explanation": {
                "solo_fare_per_seat_cents": solo_per_seat_cents,
                "seats_reserved": seats_reserved,
                "shared_discount_cents": booking.discount_cents,
                "allocated_fare_cents": booking.allocated_fare_cents,
                "driver_earnings_cents": booking.driver_earnings_cents,
                "platform_commission_cents": booking.platform_commission_cents,
                "conservation_verified": booking.allocated_fare_cents == booking.driver_earnings_cents + booking.platform_commission_cents,
            }
        }

    async def update_passenger_occupancy(
        self,
        booking_id: str,
        event_type: str,  # BOARDED, ALIGHTED, NO_SHOW, CANCELLED
        actor_id: str,
        actor_role: str = "driver"
    ) -> Dict[str, Any]:
        """
        Updates passenger occupancy lifecycle state, prevents exceeding capacity,
        and logs immutable PassengerOccupancyEvent.
        """
        valid_events = ["BOARDED", "ALIGHTED", "NO_SHOW", "CANCELLED"]
        if event_type not in valid_events:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Invalid occupancy event '{event_type}'. Valid events: {valid_events}"
            )

        b_stmt = select(PooledBooking).where(PooledBooking.id == booking_id).with_for_update()
        b_res = await self.db.execute(b_stmt)
        booking = b_res.scalars().first()
        if not booking:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Pooled booking not found.")

        t_stmt = select(PooledTrip).where(PooledTrip.id == booking.pooled_trip_id).with_for_update()
        t_res = await self.db.execute(t_stmt)
        trip = t_res.scalars().first()
        if not trip:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Pooled trip not found.")

        occ_before = trip.current_occupancy
        seats = booking.seats_reserved

        if event_type == "BOARDED":
            if booking.booking_status != "CONFIRMED":
                raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=f"Cannot board a booking in {booking.booking_status} state.")
            if trip.current_occupancy + seats > trip.max_seats:
                raise HTTPException(
                    status_code=status.HTTP_409_CONFLICT,
                    detail=f"Cannot board: current occupancy ({trip.current_occupancy}) + seats ({seats}) exceeds vehicle capacity ({trip.max_seats})."
                )
            trip.current_occupancy += seats
            booking.booking_status = "BOARDED"

        elif event_type == "ALIGHTED":
            if booking.booking_status != "BOARDED":
                raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Passenger must be BOARDED before ALIGHTED.")
            trip.current_occupancy = max(0, trip.current_occupancy - seats)
            trip.reserved_seats = max(0, trip.reserved_seats - seats)
            booking.booking_status = "COMPLETED"

        elif event_type in ["NO_SHOW", "CANCELLED"]:
            if booking.booking_status != "CONFIRMED":
                raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=f"Cannot record {event_type.lower()} for a booking in {booking.booking_status} state.")
            if booking.booking_status == "BOARDED":
                trip.current_occupancy = max(0, trip.current_occupancy - seats)
            trip.reserved_seats = max(0, trip.reserved_seats - seats)
            booking.booking_status = event_type
            if trip.status == "FULL":
                trip.status = "ACTIVE" if trip.current_occupancy > 0 else "OPEN"

        # Check if trip completed
        if event_type == "ALIGHTED" and trip.reserved_seats == 0:
            trip.status = "COMPLETED"

        occ_after = trip.current_occupancy

        # Record event
        occ_event = PassengerOccupancyEvent(
            pooled_trip_id=trip.id,
            booking_id=booking.id,
            event_type=event_type,
            occupancy_before=occ_before,
            occupancy_after=occ_after,
            actor_id=actor_id,
            notes=f"Passenger status transition to {event_type} by {actor_role}"
        )
        self.db.add(occ_event)

        # Audit log
        audit = AuditLog(
            actor_id=actor_id,
            actor_role=actor_role,
            action=f"POOL_PASSENGER_{event_type}",
            target_resource="pooled_bookings",
            target_id=booking.id,
            status="SUCCESS",
            metadata_json=f'{{"trip_id": "{trip.id}", "occ_before": {occ_before}, "occ_after": {occ_after}}}'
        )
        self.db.add(audit)

        await self.db.commit()
        await self.db.refresh(booking)
        await self.db.refresh(trip)

        return {
            "booking_id": booking.id,
            "booking_status": booking.booking_status,
            "trip_id": trip.id,
            "current_occupancy": trip.current_occupancy,
            "reserved_seats": trip.reserved_seats,
            "max_seats": trip.max_seats,
            "event_type": event_type,
            "trip_status": trip.status
        }

    async def _authorize_trip(self, trip: PooledTrip, current_user: User) -> Optional[PooledBooking]:
        role = (current_user.role or "").lower()
        if role == "admin":
            return None
        if role == "customer":
            profile = (await self.db.execute(select(CustomerProfile).where(CustomerProfile.user_id == current_user.id))).scalar_one_or_none()
            if not profile:
                raise HTTPException(status_code=403, detail="Shared trip participant access required.")
            booking = (await self.db.execute(select(PooledBooking).where(PooledBooking.pooled_trip_id == trip.id, PooledBooking.customer_id == profile.id).order_by(PooledBooking.created_at.asc()))).scalars().first()
            if not booking:
                raise HTTPException(status_code=403, detail="Shared trip participant access required.")
            return booking
        if role == "driver":
            profile = (await self.db.execute(select(DriverProfile).where(DriverProfile.user_id == current_user.id))).scalar_one_or_none()
            if not profile:
                raise HTTPException(status_code=403, detail="Shared trip participant access required.")
            assigned_ride = (await self.db.execute(select(Ride.id).where(Ride.pooled_trip_id == trip.id, Ride.assigned_driver_id == profile.id).limit(1))).first()
            if trip.driver_id != profile.id and not assigned_ride:
                raise HTTPException(status_code=403, detail="Shared trip participant access required.")
            return None
        raise HTTPException(status_code=403, detail="Shared trip participant access required.")

    async def get_pooled_trip_details(self, trip_id: str, current_user: Optional[User] = None) -> Dict[str, Any]:
        """
        Retrieves complete operational pooled trip status, current occupancy manifest,
        and scheduled stops.
        """
        trip_stmt = select(PooledTrip).where(PooledTrip.id == trip_id)
        trip_res = await self.db.execute(trip_stmt)
        trip = trip_res.scalars().first()
        if not trip:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Pooled trip not found.")

        own_booking = await self._authorize_trip(trip, current_user) if current_user else None

        bookings_stmt = select(PooledBooking).where(PooledBooking.pooled_trip_id == trip_id)
        bookings_res = await self.db.execute(bookings_stmt)
        bookings = bookings_res.scalars().all()

        stops_stmt = select(PooledTripStop).where(
            PooledTripStop.pooled_trip_id == trip_id
        ).order_by(PooledTripStop.sequence_order)
        stops_res = await self.db.execute(stops_stmt)
        stops = stops_res.scalars().all()

        financial_bookings = bookings if current_user and current_user.role.lower() in {"admin", "driver"} else ([own_booking] if own_booking else bookings)
        total_fare_cents = sum(b.allocated_fare_cents for b in financial_bookings if b)
        total_driver_cents = sum(b.driver_earnings_cents for b in financial_bookings if b)
        total_commission_cents = sum(b.platform_commission_cents for b in financial_bookings if b)

        is_driver_or_admin = not current_user or current_user.role.lower() in {"admin", "driver"}

        # Conservation verification
        assert total_fare_cents == total_driver_cents + total_commission_cents, "Monetary conservation invariant violated"

        return {
            "trip_id": trip.id,
            "category_id": trip.vehicle_category_id,
            "driver_id": trip.driver_id if is_driver_or_admin else None,
            "status": trip.status,
            "max_seats": trip.max_seats,
            "reserved_seats": trip.reserved_seats,
            "current_occupancy": trip.current_occupancy,
            "pricing_policy_version": trip.pricing_policy_version,
            "manifest": [
                {
                    "booking_id": b.id if is_driver_or_admin or (own_booking and own_booking.id == b.id) else None,
                    "ride_id": b.ride_id if is_driver_or_admin or (own_booking and own_booking.id == b.id) else None,
                    "customer_id": None,
                    "seats_reserved": b.seats_reserved,
                    "booking_status": b.booking_status,
                    "is_mine": bool(own_booking and own_booking.id == b.id),
                    "allocated_fare_cents": b.allocated_fare_cents if is_driver_or_admin or (own_booking and own_booking.id == b.id) else None,
                    "driver_earnings_cents": b.driver_earnings_cents if is_driver_or_admin or (own_booking and own_booking.id == b.id) else None,
                    "platform_commission_cents": b.platform_commission_cents if is_driver_or_admin or (own_booking and own_booking.id == b.id) else None
                }
                for b in bookings
            ],
            "stops": [
                {
                    "stop_id": s.id,
                    "booking_id": s.booking_id,
                    "stop_type": s.stop_type,
                    "sequence_order": s.sequence_order,
                    "address": s.address if is_driver_or_admin or not own_booking or own_booking.id == s.booking_id or next((b.share_stop_details_with_co_passengers for b in bookings if b.id == s.booking_id), False) else f"Private {s.stop_type.lower()} stop",
                    "latitude": s.latitude if is_driver_or_admin or not own_booking or own_booking.id == s.booking_id or next((b.share_stop_details_with_co_passengers for b in bookings if b.id == s.booking_id), False) else None,
                    "longitude": s.longitude if is_driver_or_admin or not own_booking or own_booking.id == s.booking_id or next((b.share_stop_details_with_co_passengers for b in bookings if b.id == s.booking_id), False) else None,
                    "status": s.status
                }
                for s in stops
            ],
            "financial_summary": {
                "total_fare_cents": total_fare_cents,
                "total_driver_earnings_cents": total_driver_cents,
                "total_platform_commission_cents": total_commission_cents,
                "conservation_verified": True
            }
        }

    async def get_seat_availability(self, trip_id: str) -> Dict[str, Any]:
        trip = await self.db.get(PooledTrip, trip_id)
        if not trip:
            raise HTTPException(status_code=404, detail="Shared pool is no longer available.")
        return {
            "trip_id": trip.id,
            "status": trip.status,
            "max_seats": trip.max_seats,
            "reserved_seats": trip.reserved_seats,
            "available_seats": max(0, trip.max_seats - trip.reserved_seats),
            "refreshed_at": datetime.now(timezone.utc).isoformat(),
            "accepting_matches": trip.status == "OPEN",
        }

    async def get_customer_detour_preference(self, current_user: User) -> Dict[str, Any]:
        profile = (await self.db.execute(select(CustomerProfile).where(CustomerProfile.user_id == current_user.id))).scalar_one_or_none()
        if not profile:
            raise HTTPException(status_code=404, detail="Customer profile not found.")
        preference = (await self.db.execute(select(SharedRidePreference).where(SharedRidePreference.customer_id == profile.id))).scalar_one_or_none()
        return {"maximum_detour_km": preference.maximum_detour_km if preference else 5.0}

    async def set_customer_detour_preference(self, current_user: User, maximum_detour_km: float) -> Dict[str, Any]:
        profile = (await self.db.execute(select(CustomerProfile).where(CustomerProfile.user_id == current_user.id))).scalar_one_or_none()
        if not profile:
            raise HTTPException(status_code=404, detail="Customer profile not found.")
        preference = (await self.db.execute(select(SharedRidePreference).where(SharedRidePreference.customer_id == profile.id).with_for_update())).scalar_one_or_none()
        if preference:
            preference.maximum_detour_km = maximum_detour_km
        else:
            preference = SharedRidePreference(customer_id=profile.id, maximum_detour_km=maximum_detour_km)
            self.db.add(preference)
        await self.db.commit()
        return {"maximum_detour_km": preference.maximum_detour_km, "updated_at": preference.updated_at.isoformat() if preference.updated_at else None}

    async def get_shared_trip_for_ride(self, ride_id: str, current_user: User) -> Dict[str, Any]:
        ride = await self.db.get(Ride, ride_id)
        if not ride or not ride.pooled_trip_id:
            raise HTTPException(status_code=404, detail="Shared trip was not found for this ride.")
        if current_user.role.lower() == "customer":
            profile = (await self.db.execute(select(CustomerProfile).where(CustomerProfile.user_id == current_user.id))).scalar_one_or_none()
            if not profile or not (await self.db.execute(select(PooledBooking.id).where(PooledBooking.ride_id == ride.id, PooledBooking.customer_id == profile.id))).first():
                raise HTTPException(status_code=403, detail="Ride participant access required.")
        elif current_user.role.lower() == "driver":
            profile = (await self.db.execute(select(DriverProfile).where(DriverProfile.user_id == current_user.id))).scalar_one_or_none()
            if not profile or ride.assigned_driver_id != profile.id:
                raise HTTPException(status_code=403, detail="Ride participant access required.")
        details = await self.get_pooled_trip_details(ride.pooled_trip_id, current_user)
        own = next((row for row in details["manifest"] if row.get("ride_id") == ride_id), None)
        details["own_booking_id"] = own.get("booking_id") if own else None
        details["ride_id"] = ride_id
        return details

    async def list_trip_messages(self, trip_id: str, current_user: User) -> List[Dict[str, Any]]:
        trip = await self.db.get(PooledTrip, trip_id)
        if not trip:
            raise HTTPException(status_code=404, detail="Shared trip not found.")
        await self._authorize_trip(trip, current_user)
        rows = (await self.db.execute(select(PooledTripMessage).where(PooledTripMessage.pooled_trip_id == trip_id).order_by(PooledTripMessage.created_at.desc()).limit(100))).scalars().all()
        return [{"id": m.id, "sender": "You" if m.sender_user_id == current_user.id else ("Chauffeur" if m.sender_role.lower() == "driver" else "Passenger"), "message": m.message, "created_at": m.created_at.isoformat() if m.created_at else None} for m in reversed(rows)]

    async def send_trip_message(self, trip_id: str, current_user: User, message: str) -> Dict[str, Any]:
        trip = await self.db.get(PooledTrip, trip_id)
        if not trip:
            raise HTTPException(status_code=404, detail="Shared trip not found.")
        own_booking = await self._authorize_trip(trip, current_user)
        clean_message = message.strip()
        if len(clean_message) < 1 or len(clean_message) > 1000:
            raise HTTPException(status_code=400, detail="Message must contain 1 to 1000 characters.")
        row = PooledTripMessage(pooled_trip_id=trip_id, booking_id=own_booking.id if own_booking else None, sender_user_id=current_user.id, sender_role=current_user.role.upper(), message=clean_message)
        self.db.add(row)
        await self.db.commit()
        await self.db.refresh(row)
        return {"id": row.id, "sender": "You", "message": row.message, "created_at": row.created_at.isoformat()}

    async def cancel_customer_booking(self, booking_id: str, current_user: User) -> Dict[str, Any]:
        profile = (await self.db.execute(select(CustomerProfile).where(CustomerProfile.user_id == current_user.id))).scalar_one_or_none()
        if not profile:
            raise HTTPException(status_code=404, detail="Customer profile not found.")
        booking = (await self.db.execute(select(PooledBooking).where(PooledBooking.id == booking_id).with_for_update())).scalars().first()
        if not booking or booking.customer_id != profile.id:
            raise HTTPException(status_code=404, detail="Shared booking not found.")
        if booking.booking_status != "CONFIRMED":
            raise HTTPException(status_code=409, detail=f"This booking cannot be cancelled from {booking.booking_status} status.")
        ride = await self.db.get(Ride, booking.ride_id)
        trip = (await self.db.execute(select(PooledTrip).where(PooledTrip.id == booking.pooled_trip_id).with_for_update())).scalars().first()
        if not ride or not trip or ride.status not in {"REQUESTED", "ASSIGNED"}:
            raise HTTPException(status_code=409, detail="Cancellation closes when the shared ride begins.")
        before = trip.current_occupancy
        trip.reserved_seats = max(0, trip.reserved_seats - booking.seats_reserved)
        if booking.booking_status == "BOARDED":
            trip.current_occupancy = max(0, trip.current_occupancy - booking.seats_reserved)
        if trip.status == "FULL":
            trip.status = "ACTIVE" if trip.current_occupancy else "OPEN"
        booking.booking_status = "CANCELLED"
        ride.status = "CANCELLED"
        event = PassengerOccupancyEvent(pooled_trip_id=trip.id, booking_id=booking.id, event_type="CANCELLED", occupancy_before=before, occupancy_after=trip.current_occupancy, actor_id=current_user.id, notes="Customer cancelled before trip start.")
        self.db.add(event)
        self.db.add(AuditLog(actor_id=current_user.id, actor_role="customer", action="POOL_BOOKING_CANCELLED", target_resource="pooled_bookings", target_id=booking.id, status="SUCCESS", metadata_json="{}"))
        await self.db.commit()
        return {"booking_id": booking.id, "booking_status": booking.booking_status, "trip_id": trip.id, "fare_adjustment_cents": 0, "fare_adjustment_status": "NO_ADJUSTMENT_POSTED", "policy": "Cancellation was accepted before trip start. No fare adjustment is posted by this cancellation path."}
