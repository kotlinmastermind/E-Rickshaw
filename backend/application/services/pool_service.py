import math
import uuid
import secrets
from datetime import datetime, timezone, timedelta
from typing import Dict, Any, List, Optional, Tuple
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, update
from fastapi import HTTPException, status

from backend.domain.models import (
    PooledTrip, PooledBooking, PooledTripStop, PassengerOccupancyEvent,
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

        rule = await self.fare_service.get_active_rule_for_category(category_id)
        route = await self.maps_provider.get_route(pickup_lat, pickup_lng, dest_lat, dest_lng)

        # Baseline private solo calculation in minor units (paise/cents)
        distance_charge = round(route.distance_km * rule.per_km_cents)
        time_charge = round(route.duration_minutes * rule.per_minute_cents)
        solo_subtotal = rule.base_fare_cents + distance_charge + time_charge
        solo_before_surge = max(solo_subtotal, rule.minimum_fare_cents)
        solo_fare_cents = round(solo_before_surge * rule.surge_multiplier)

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
            surge_multiplier=rule.surge_multiplier,
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
            "savings_cents": savings_cents,
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
        # Fetch candidate pooled trips that are OPEN or ACTIVE
        stmt = select(PooledTrip).where(
            PooledTrip.vehicle_category_id == category_id,
            PooledTrip.status.in_(["OPEN", "ACTIVE"])
        )
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
                    "driver_assigned": trip.driver_id is not None
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
                "driver_assigned": trip.driver_id is not None
            })

        # Rank eligible candidates by least detour distance
        eligible_candidates.sort(key=lambda c: c["detour_km"])

        return {
            "matches_found": len(eligible_candidates),
            "candidates": eligible_candidates,
            "rejections_evaluated": len(rejection_logs),
            "rejection_reasons": rejection_logs
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
        fallback_policy: str = "CREATE_NEW_POOL"  # CREATE_NEW_POOL, FAIL
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

        # Determine target pooled trip atomically
        pooled_trip: Optional[PooledTrip] = None
        if target_pooled_trip_id:
            trip_stmt = select(PooledTrip).where(
                PooledTrip.id == target_pooled_trip_id
            ).with_for_update()
            trip_res = await self.db.execute(trip_stmt)
            candidate = trip_res.scalars().first()
            if candidate and candidate.status in ["OPEN", "ACTIVE"]:
                if candidate.reserved_seats + seats_reserved <= candidate.max_seats:
                    pooled_trip = candidate
                elif fallback_policy == "FAIL":
                    raise HTTPException(
                        status_code=status.HTTP_409_CONFLICT,
                        detail="Selected pool capacity is fully booked. Fallback policy prevented new pool creation without customer consent."
                    )

        if not pooled_trip:
            # Check for any available open pool
            open_stmt = select(PooledTrip).where(
                PooledTrip.vehicle_category_id == category_id,
                PooledTrip.status == "OPEN",
                (PooledTrip.max_seats - PooledTrip.reserved_seats) >= seats_reserved
            ).with_for_update().limit(1)
            open_res = await self.db.execute(open_stmt)
            pooled_trip = open_res.scalars().first()

        if not pooled_trip:
            # Create a new PooledTrip
            pooled_trip = PooledTrip(
                vehicle_category_id=category_id,
                status="OPEN",
                max_seats=4,
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
            discount_cents=round(total_fare_cents * 0.25),
            driver_earnings_cents=driver_earnings_cents,
            platform_commission_cents=platform_commission_cents,
            pickup_eta_minutes=5.0
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
            "pickup_eta_minutes": booking.pickup_eta_minutes,
            "start_otp": start_otp,
            "trip_status": pooled_trip.status
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
            if booking.booking_status == "BOARDED":
                raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Passenger already boarded.")
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

    async def get_pooled_trip_details(self, trip_id: str) -> Dict[str, Any]:
        """
        Retrieves complete operational pooled trip status, current occupancy manifest,
        and scheduled stops.
        """
        trip_stmt = select(PooledTrip).where(PooledTrip.id == trip_id)
        trip_res = await self.db.execute(trip_stmt)
        trip = trip_res.scalars().first()
        if not trip:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Pooled trip not found.")

        bookings_stmt = select(PooledBooking).where(PooledBooking.pooled_trip_id == trip_id)
        bookings_res = await self.db.execute(bookings_stmt)
        bookings = bookings_res.scalars().all()

        stops_stmt = select(PooledTripStop).where(
            PooledTripStop.pooled_trip_id == trip_id
        ).order_by(PooledTripStop.sequence_order)
        stops_res = await self.db.execute(stops_stmt)
        stops = stops_res.scalars().all()

        total_fare_cents = sum(b.allocated_fare_cents for b in bookings)
        total_driver_cents = sum(b.driver_earnings_cents for b in bookings)
        total_commission_cents = sum(b.platform_commission_cents for b in bookings)

        # Conservation verification
        assert total_fare_cents == total_driver_cents + total_commission_cents, "Monetary conservation invariant violated"

        return {
            "trip_id": trip.id,
            "category_id": trip.vehicle_category_id,
            "driver_id": trip.driver_id,
            "status": trip.status,
            "max_seats": trip.max_seats,
            "reserved_seats": trip.reserved_seats,
            "current_occupancy": trip.current_occupancy,
            "pricing_policy_version": trip.pricing_policy_version,
            "manifest": [
                {
                    "booking_id": b.id,
                    "ride_id": b.ride_id,
                    "customer_id": b.customer_id,
                    "seats_reserved": b.seats_reserved,
                    "booking_status": b.booking_status,
                    "allocated_fare_cents": b.allocated_fare_cents,
                    "driver_earnings_cents": b.driver_earnings_cents,
                    "platform_commission_cents": b.platform_commission_cents
                }
                for b in bookings
            ],
            "stops": [
                {
                    "stop_id": s.id,
                    "booking_id": s.booking_id,
                    "stop_type": s.stop_type,
                    "sequence_order": s.sequence_order,
                    "address": s.address,
                    "latitude": s.latitude,
                    "longitude": s.longitude,
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
