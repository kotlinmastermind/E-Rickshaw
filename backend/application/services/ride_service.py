import json
import os
import uuid
from datetime import datetime, timezone, timedelta
from typing import Dict, Any, Optional, List
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, update, and_, or_
from sqlalchemy.orm import joinedload
from fastapi import HTTPException, status
from backend.domain.models import (
    Ride, RideStatusEvent, RideAssignment, FareEstimate, FarePriceLock, FareRule, VehicleCategory,
    CustomerProfile, DriverProfile, DriverAvailability, DriverLocation,
    Payment, DriverRating, Vehicle, DriverWallet
)
from backend.domain.state_machine.ride_state_machine import (
    RideStateMachine, RideStatus, ActorRole, RideTransitionError, ACTIVE_RIDE_STATUSES
)
from backend.infrastructure.security.crypto import generate_secure_otp, verify_otp_constant_time
from backend.integrations.notifications.provider import get_notification_provider
from backend.application.services.promo_pass_service import PromoPassService

class RideService:
    ROUTE_ESTIMATE_COORDINATE_TOLERANCE_KM = 0.1
    PRICE_LOCK_COORDINATE_TOLERANCE_KM = 2.0

    def __init__(self, db: AsyncSession):
        self.db = db
        self.notifier = get_notification_provider()

    @staticmethod
    def _coordinate_distance_km(
        latitude_a: float,
        longitude_a: float,
        latitude_b: float,
        longitude_b: float,
    ) -> float:
        """Return great-circle distance between two WGS84 coordinate pairs."""
        import math

        lat_a, lon_a, lat_b, lon_b = map(
            math.radians,
            (latitude_a, longitude_a, latitude_b, longitude_b),
        )
        delta_lat = lat_b - lat_a
        delta_lon = lon_b - lon_a
        haversine = (
            math.sin(delta_lat / 2) ** 2
            + math.cos(lat_a) * math.cos(lat_b) * math.sin(delta_lon / 2) ** 2
        )
        haversine = min(1.0, max(0.0, haversine))
        return 6371.0 * 2 * math.atan2(math.sqrt(haversine), math.sqrt(1 - haversine))

    @classmethod
    def _validate_estimate_route_coordinates(
        cls,
        estimate: FareEstimate,
        pickup_latitude: Optional[float],
        pickup_longitude: Optional[float],
        destination_latitude: Optional[float],
        destination_longitude: Optional[float],
    ) -> None:
        supplied = (
            pickup_latitude,
            pickup_longitude,
            destination_latitude,
            destination_longitude,
        )
        if not any(value is not None for value in supplied):
            return
        if any(value is None for value in supplied):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Pickup and destination coordinates must be supplied together when booking with a fare estimate.",
            )

        for label, requested_lat, requested_lng, estimated_lat, estimated_lng in (
            ("Pickup", pickup_latitude, pickup_longitude, estimate.pickup_latitude, estimate.pickup_longitude),
            ("Destination", destination_latitude, destination_longitude, estimate.destination_latitude, estimate.destination_longitude),
        ):
            deviation_km = cls._coordinate_distance_km(
                requested_lat, requested_lng, estimated_lat, estimated_lng
            )
            if deviation_km > cls.ROUTE_ESTIMATE_COORDINATE_TOLERANCE_KM:
                raise HTTPException(
                    status_code=status.HTTP_409_CONFLICT,
                    detail=(
                        f"{label} location no longer matches the fare estimate "
                        f"({deviation_km * 1000:.0f}m deviation). Recalculate the fare before booking."
                    ),
                )

    async def create_ride_request(
        self,
        customer_user_id: str,
        estimate_id: Optional[str] = None,
        category_id: Optional[str] = None,
        pickup_address: str = "Current Location Pickup",
        destination_address: str = "Destination",
        pickup_latitude: Optional[float] = None,
        pickup_longitude: Optional[float] = None,
        destination_latitude: Optional[float] = None,
        destination_longitude: Optional[float] = None,
        payment_method: str = "UPI",
        is_scheduled: bool = False,
        scheduled_pickup_time: Optional[datetime] = None,
        promo_code: Optional[str] = None,
        stops: Optional[List[Dict[str, Any]]] = None,
        booking_idempotency_key: Optional[str] = None,
        booking_request_hash: Optional[str] = None
    ) -> Dict[str, Any]:
        # Fetch customer profile
        stmt = select(CustomerProfile).where(CustomerProfile.user_id == customer_user_id)
        res = await self.db.execute(stmt)
        customer = res.scalar_one_or_none()
        if not customer:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Customer profile not found.")

        # 1. Mandatory Vehicle Category validation
        clean_cat_id = str(category_id).strip() if category_id and str(category_id).strip() else None
        if not clean_cat_id:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Vehicle category_id is mandatory. Please select a valid active vehicle category."
            )

        stmt_cat = select(VehicleCategory).where(
            or_(VehicleCategory.id == clean_cat_id, VehicleCategory.code == clean_cat_id)
        )
        cat = (await self.db.execute(stmt_cat)).scalars().first()
        if not cat:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=f"Vehicle category '{clean_cat_id}' not found."
            )
        if not cat.is_active:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Vehicle category '{cat.name}' is currently inactive."
            )

        # 2. Fetch fare estimate snapshot or price lock if provided
        estimate = None
        matched_price_lock = None
        if estimate_id:
            stmt_est = select(FareEstimate).where(FareEstimate.id == estimate_id)
            res_est = await self.db.execute(stmt_est)
            candidate_est = res_est.scalar_one_or_none()
            if candidate_est:
                if candidate_est.category_id != cat.id:
                    raise HTTPException(
                        status_code=status.HTTP_400_BAD_REQUEST,
                        detail=f"Vehicle category mismatch: Estimate belongs to category '{candidate_est.category_id}' but ride requested '{cat.name}'."
                    )
                self._validate_estimate_route_coordinates(
                    candidate_est,
                    pickup_latitude,
                    pickup_longitude,
                    destination_latitude,
                    destination_longitude,
                )
                estimate = candidate_est
            else:
                # Check persisted 24h price lock with row-level lock to prevent concurrent double-booking
                stmt_lock = select(FarePriceLock).where(FarePriceLock.id == estimate_id).with_for_update()
                candidate_lock = (await self.db.execute(stmt_lock)).scalar_one_or_none()
                if candidate_lock:
                    if candidate_lock.customer_id and candidate_lock.customer_id != customer.id:
                        raise HTTPException(
                            status_code=status.HTTP_403_FORBIDDEN,
                            detail="Price lock belongs to another customer account and cannot be used."
                        )
                    if candidate_lock.category_id != cat.id:
                        raise HTTPException(
                            status_code=status.HTTP_400_BAD_REQUEST,
                            detail=f"Vehicle category mismatch: Price lock belongs to category '{candidate_lock.category_id}' but ride requested '{cat.name}'."
                        )
                    if candidate_lock.status != "ACTIVE":
                        raise HTTPException(
                            status_code=status.HTTP_400_BAD_REQUEST,
                            detail=f"Price lock '{candidate_lock.id}' cannot be used because its status is '{candidate_lock.status}'."
                        )
                    lock_exp = candidate_lock.expires_at if candidate_lock.expires_at.tzinfo else candidate_lock.expires_at.replace(tzinfo=timezone.utc)
                    if datetime.now(timezone.utc) > lock_exp:
                        raise HTTPException(
                            status_code=status.HTTP_400_BAD_REQUEST,
                            detail="Price lock has expired. Please recalculate fare."
                        )

                    # Bind both route endpoints to the stored price lock. A pickup-only
                    # check allowed a changed destination to reuse an unrelated fare.
                    supplied_coordinates = (
                        pickup_latitude, pickup_longitude,
                        destination_latitude, destination_longitude,
                    )
                    if any(value is not None for value in supplied_coordinates) and any(
                        value is None for value in supplied_coordinates
                    ):
                        raise HTTPException(
                            status_code=status.HTTP_400_BAD_REQUEST,
                            detail="Pickup and destination coordinates must be supplied together when booking with a price lock.",
                        )
                    for label, requested_lat, requested_lng, locked_lat, locked_lng in (
                        ("Pickup", pickup_latitude, pickup_longitude, candidate_lock.pickup_latitude, candidate_lock.pickup_longitude),
                        ("Destination", destination_latitude, destination_longitude, candidate_lock.destination_latitude, candidate_lock.destination_longitude),
                    ):
                        if requested_lat is None or requested_lng is None:
                            continue
                        deviation_km = self._coordinate_distance_km(
                            requested_lat, requested_lng, locked_lat, locked_lng
                        )
                        if deviation_km > self.PRICE_LOCK_COORDINATE_TOLERANCE_KM:
                            raise HTTPException(
                                status_code=status.HTTP_400_BAD_REQUEST,
                                detail=(
                                    f"{label} location deviates {deviation_km:.1f}km from the locked price route corridor "
                                    f"(maximum allowed: {self.PRICE_LOCK_COORDINATE_TOLERANCE_KM:.1f}km). "
                                    "Please generate a live fare estimate."
                                ),
                            )

                    matched_price_lock = candidate_lock
                    # Map price lock into estimate snapshot representation
                    estimate = FareEstimate(
                        id=candidate_lock.id,
                        category_id=candidate_lock.category_id,
                        fare_rule_id=candidate_lock.fare_rule_id,
                        pickup_latitude=candidate_lock.pickup_latitude,
                        pickup_longitude=candidate_lock.pickup_longitude,
                        destination_latitude=candidate_lock.destination_latitude,
                        destination_longitude=candidate_lock.destination_longitude,
                        distance_km=candidate_lock.distance_km,
                        estimated_duration_minutes=candidate_lock.duration_minutes,
                        estimated_fare_cents=candidate_lock.locked_fare_cents,
                        surge_multiplier=candidate_lock.surge_multiplier,
                        expires_at=candidate_lock.expires_at
                    )
                elif pickup_latitude is None or pickup_longitude is None or destination_latitude is None or destination_longitude is None:
                    raise HTTPException(
                        status_code=status.HTTP_404_NOT_FOUND,
                        detail=f"Fare estimate or price lock '{estimate_id}' was not found or has expired. Please calculate a live fare."
                    )

        if not estimate:
            if pickup_latitude is None or pickup_longitude is None or destination_latitude is None or destination_longitude is None:
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail="Valid pickup and destination coordinates are required to create a ride request when estimate_id is omitted."
                )
            from backend.application.services.fare_service import FareService
            fare_svc = FareService(self.db)
            est_data = await fare_svc.calculate_estimate(
                category_id=cat.id,
                pickup_lat=pickup_latitude,
                pickup_lng=pickup_longitude,
                dest_lat=destination_latitude,
                dest_lng=destination_longitude
            )
            stmt_est = select(FareEstimate).where(FareEstimate.id == est_data["estimate_id"])
            estimate = (await self.db.execute(stmt_est)).scalar_one()

        from backend.application.services.service_area_service import ServiceAreaService
        service_areas = ServiceAreaService(self.db)
        service_area = await service_areas.resolve_route(
            pickup_lat=estimate.pickup_latitude,
            pickup_lng=estimate.pickup_longitude,
            destination_lat=estimate.destination_latitude,
            destination_lng=estimate.destination_longitude,
        )
        for stop_data in stops or []:
            try:
                stop_lat = float(stop_data["latitude"])
                stop_lng = float(stop_data["longitude"])
            except (KeyError, TypeError, ValueError):
                raise HTTPException(status_code=400, detail="Each intermediate stop requires valid latitude and longitude coordinates.")
            if not (-90 <= stop_lat <= 90 and -180 <= stop_lng <= 180):
                raise HTTPException(status_code=400, detail="Intermediate stop coordinates are outside valid geographic ranges.")
            await service_areas.require_point_in_area(service_area.id, stop_lat, stop_lng)

        now = datetime.now(timezone.utc)

        expires_at = estimate.expires_at if estimate.expires_at.tzinfo else estimate.expires_at.replace(tzinfo=timezone.utc)
        if now > expires_at:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Fare estimate has expired. Please recalculate.")

        # Check active ride to prevent duplicate concurrent bookings
        active_ride_stmt = select(Ride).where(
            Ride.customer_id == customer.id,
            Ride.status.in_(ACTIVE_RIDE_STATUSES)
        )
        existing_active = (await self.db.execute(active_ride_stmt)).scalars().first()
        if existing_active:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail=f"You already have an active journey (#{existing_active.id[:8]}) in status '{existing_active.status}'. Please complete or cancel it before requesting a new ride."
            )

        # Generate secure trip OTP
        start_otp = generate_secure_otp(6)

        # Apply promo code discount if provided
        discount_cents = 0
        promo_id = None
        if promo_code:
            from backend.application.services.promo_pass_service import PromoPassService
            val = await PromoPassService.validate_promo_code(
                session=self.db,
                code_str=promo_code,
                customer_user_id=customer_user_id,
                category_id=estimate.category_id,
                fare_cents=estimate.estimated_fare_cents
            )
            discount_cents = val["discount_cents"]
            promo_id = val["promo_id"]

        ride = Ride(
            customer_id=customer.id,
            vehicle_category_id=estimate.category_id,
            fare_rule_id=estimate.fare_rule_id,
            service_area_id=service_area.id,
            status=RideStatus.REQUESTED.value,
            pickup_address=pickup_address,
            pickup_latitude=estimate.pickup_latitude,
            pickup_longitude=estimate.pickup_longitude,
            destination_address=destination_address,
            destination_latitude=estimate.destination_latitude,
            destination_longitude=estimate.destination_longitude,
            start_otp=start_otp,
            booking_idempotency_key=booking_idempotency_key,
            booking_request_hash=booking_request_hash,
            estimated_distance_km=estimate.distance_km,
            estimated_duration_minutes=estimate.estimated_duration_minutes,
            estimated_fare_cents=estimate.estimated_fare_cents,
            final_fare_cents=max(0, estimate.estimated_fare_cents - discount_cents),
            payment_method=payment_method,
            is_scheduled=is_scheduled,
            scheduled_pickup_time=scheduled_pickup_time,
            promo_code_id=promo_id,
            discount_cents=discount_cents
        )
        self.db.add(ride)
        await self.db.flush()

        # If booking was made using a persisted 24h price lock, mark lock consumed
        if matched_price_lock:
            matched_price_lock.status = "CONSUMED"
            matched_price_lock.consumed_at = datetime.now(timezone.utc)
            matched_price_lock.consumed_ride_id = ride.id

        # Add intermediate stops if provided
        if stops:
            from backend.domain.models import RideStop
            for idx, stop_data in enumerate(stops, start=1):
                r_stop = RideStop(
                    ride_id=ride.id,
                    sequence_order=idx,
                    address=stop_data.get("address", ""),
                    latitude=stop_data.get("latitude", 0.0),
                    longitude=stop_data.get("longitude", 0.0)
                )
                self.db.add(r_stop)

        # Record initial status event
        event = RideStatusEvent(
            ride_id=ride.id,
            previous_status=None,
            new_status=RideStatus.REQUESTED.value,
            triggered_by_user_id=customer_user_id,
            trigger_actor=ActorRole.CUSTOMER.value,
            notes="Scheduled ride requested" if is_scheduled else "Ride requested by guest"
        )
        self.db.add(event)
        await self.db.commit()
        await self.db.refresh(ride)

        # Persist a generic dispatch wake-up notice. The delivery worker applies the
        # driver's consent, quiet hours, rate caps and provider retry policy.
        try:
            from backend.domain.models import DriverAvailability, DriverProfile, Notification
            stmt_drivers = select(DriverProfile.user_id).join(
                DriverAvailability, DriverAvailability.driver_id == DriverProfile.id
            ).where(DriverAvailability.is_online.is_(True)).distinct()
            driver_user_ids = (await self.db.execute(stmt_drivers)).scalars().all()
            for driver_user_id in driver_user_ids:
                self.db.add(Notification(
                    user_id=driver_user_id,
                    title="New dispatch activity",
                    body="Open the driver app to check for matching offers.",
                    event_type="DISPATCH_REFRESH",
                    data_json=json.dumps({"type": "DISPATCH_REFRESH"}),
                    dedupe_key=f"dispatch-refresh:{ride.id}:{driver_user_id}",
                    delivery_status="QUEUED",
                ))
            await self.db.commit()
        except Exception:
            await self.db.rollback()

        # Realtime message is a wake-up hint only; API polling returns eligible offers.
        try:
            from backend.api.routes.ws import ws_manager
            dispatch_ws = {"type": "DISPATCH_REFRESH", "timestamp": ride.requested_at.isoformat()}
            for d_uid in list(ws_manager.driver_channels.keys()):
                await ws_manager.dispatch_to_driver(d_uid, dispatch_ws)
        except Exception:
            pass

        # Trigger Smart Dispatch Engine immediately for on-demand ride booking
        if not is_scheduled and not os.environ.get("TESTING"):
            try:
                import asyncio
                asyncio.create_task(self._background_dispatch(ride.id))
            except RuntimeError:
                pass

        return {
            "ride_id": ride.id,
            "status": ride.status,
            "category_id": ride.vehicle_category_id,
            "start_otp": ride.start_otp,
            "estimated_fare_cents": ride.estimated_fare_cents,
            "pickup_address": ride.pickup_address,
            "destination_address": ride.destination_address,
            "requested_at": ride.requested_at.isoformat()
        }

    @staticmethod
    async def _background_dispatch(ride_id: str) -> None:
        """Invokes SmartDispatchService in an isolated async database session."""
        try:
            from backend.infrastructure.database.session import async_session_maker
            from backend.application.services.smart_dispatch_service import SmartDispatchService
            async with async_session_maker() as session:
                svc = SmartDispatchService(session)
                await svc.search_and_dispatch(ride_id)
        except Exception:
            pass

    async def accept_ride_atomic(self, driver_user_id: str, ride_id: str) -> Dict[str, Any]:
        """
        Guarantees strict atomic concurrency control:
        Only ONE driver can successfully assign a requested ride.
        Uses a conditional UPDATE matching status == 'REQUESTED' and assigned_driver_id IS NULL.
        """
        # Fetch driver profile & check approval
        stmt = select(DriverProfile).where(DriverProfile.user_id == driver_user_id)
        res = await self.db.execute(stmt)
        driver = res.scalar_one_or_none()
        if not driver:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Driver profile not found.")
        
        if driver.approval_status != "APPROVED":
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Chauffeur account is pending verification approval.")

        # Re-validate ride existence and vehicle category match
        stmt_ride = select(Ride).where(Ride.id == ride_id)
        ride_res = await self.db.execute(stmt_ride)
        ride_obj = ride_res.scalar_one_or_none()
        if not ride_obj:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Ride not found.")
        if not ride_obj.service_area_id:
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Ride has no validated operational service area.")
        from backend.application.services.service_area_service import ServiceAreaService
        service_areas = ServiceAreaService(self.db)
        resolved_area = await service_areas.resolve_route(
            ride_obj.pickup_latitude, ride_obj.pickup_longitude,
            ride_obj.destination_latitude, ride_obj.destination_longitude,
        )
        if resolved_area.id != ride_obj.service_area_id:
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Ride service area no longer matches its route.")
        await service_areas.require_driver_assignment(driver.id, ride_obj.service_area_id)

        from backend.domain.models import DriverVehicleAssignment, Vehicle
        stmt_veh = (
            select(Vehicle)
            .join(DriverVehicleAssignment, DriverVehicleAssignment.vehicle_id == Vehicle.id)
            .where(
                DriverVehicleAssignment.driver_id == driver.id,
                DriverVehicleAssignment.is_active.is_(True),
                Vehicle.is_active.is_(True)
            )
        )
        driver_veh = (await self.db.execute(stmt_veh)).scalars().first()
        if not driver_veh or not driver_veh.category_id:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Driver does not have an active assigned vehicle."
            )
        if driver_veh.category_id != ride_obj.vehicle_category_id:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Driver vehicle category mismatch. Expected category '{ride_obj.vehicle_category_id}', but driver vehicle is '{driver_veh.category_id}'."
            )

        await service_areas.require_fresh_driver_inside(driver.id, ride_obj.service_area_id)

        now = datetime.now(timezone.utc)
        now_naive = now.replace(tzinfo=None)

        from backend.infrastructure.cache.redis_client import redis_client
        async with redis_client.distributed_lock(f"lock:ride:{ride_id}", timeout_seconds=10):
            # An authenticated driver can accept only their own live, persisted offer.
            # Handles both timezone-aware (PostgreSQL) and naive ISO strings (SQLite test environments)
            offer_result = await self.db.execute(
                select(RideAssignment).where(
                    RideAssignment.ride_id == ride_id,
                    RideAssignment.driver_id == driver.id,
                    RideAssignment.status == "OFFERED",
                    or_(
                        RideAssignment.expires_at.is_(None),
                        RideAssignment.expires_at > now,
                        RideAssignment.expires_at > now_naive
                    )
                )
            )
            offer = offer_result.scalar_one_or_none()
            if offer is None:
                raise HTTPException(status_code=409, detail="This dispatch offer expired or is no longer assigned to you.")

            # Atomic conditional update
            update_stmt = (
                update(Ride)
                .where(
                    and_(
                        Ride.id == ride_id,
                        Ride.status == RideStatus.REQUESTED.value,
                        Ride.assigned_driver_id.is_(None)
                    )
                )
                .values(
                    status=RideStatus.ASSIGNED.value,
                    assigned_driver_id=driver.id,
                    vehicle_id=driver_veh.id,
                    assigned_at=now
                )
            )
            result = await self.db.execute(update_stmt)
            if result.rowcount == 0:
                raise HTTPException(
                    status_code=status.HTTP_409_CONFLICT,
                    detail="This luxury ride has already been accepted by another chauffeur or is no longer available."
                )

            # Mark driver availability as busy
            avail_stmt = (
                update(DriverAvailability)
                .where(DriverAvailability.driver_id == driver.id)
                .values(is_busy=True, updated_at=now)
            )
            await self.db.execute(avail_stmt)

            # Record assignment & state event
            offer.status = "ACCEPTED"
            offer.is_accepted = True
            offer.responded_at = now

            # Clear Redis offer safety key & active-offer index
            try:
                await redis_client.delete(f"dispatch:offer:{ride_id}:{driver.id}")
                await redis_client.delete(f"driver:active_offer:{driver.id}")
                await redis_client.delete(f"driver:active_offer:{driver_user_id}")
                from backend.application.services.live_location_service import LiveLocationService
                await LiveLocationService(self.db).remove_driver_from_h3_index(driver.id)
            except Exception:
                pass

            event = RideStatusEvent(
                ride_id=ride_id,
                previous_status=RideStatus.REQUESTED.value,
                new_status=RideStatus.ASSIGNED.value,
                triggered_by_user_id=driver_user_id,
                trigger_actor=ActorRole.DRIVER.value,
                notes=f"Accepted by chauffeur {driver.id}"
            )
            self.db.add(event)
            await self.db.commit()

        # Broadcast live status update to WebSocket subscribers
        await self._broadcast_ride_update(ride_id, RideStatus.ASSIGNED.value)

        # Fetch updated ride
        ride = await self.get_ride_by_id(ride_id)
        return {
            "ride_id": ride.id,
            "status": ride.status,
            "assigned_driver_id": driver.id,
            "assigned_at": now.isoformat(),
            "pickup_address": ride.pickup_address,
            "destination_address": ride.destination_address
        }

    async def driver_arrived(
        self,
        driver_user_id: str,
        ride_id: str,
        driver_latitude: Optional[float] = None,
        driver_longitude: Optional[float] = None
    ) -> Dict[str, Any]:
        ride = await self.get_ride_by_id(ride_id)
        driver = await self._get_driver_by_user_id(driver_user_id)

        if ride.assigned_driver_id != driver.id:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="You are not the assigned chauffeur for this trip.")

        RideStateMachine.validate_transition(ride.status, RideStatus.ARRIVED.value, ActorRole.DRIVER.value)

        # Task 062: Proximity verification (500m radius)
        if driver_latitude is None or driver_longitude is None:
            loc_stmt = (
                select(DriverLocation)
                .where(DriverLocation.driver_id == driver.id)
                .order_by(DriverLocation.recorded_at.desc())
            )
            loc_res = await self.db.execute(loc_stmt)
            latest_loc = loc_res.scalars().first()
            if latest_loc:
                driver_latitude = latest_loc.latitude
                driver_longitude = latest_loc.longitude

        if driver_latitude is not None and driver_longitude is not None and ride.pickup_latitude is not None and ride.pickup_longitude is not None:
            import math
            dlat = math.radians(driver_latitude - ride.pickup_latitude)
            dlon = math.radians(driver_longitude - ride.pickup_longitude)
            a = math.sin(dlat / 2)**2 + math.cos(math.radians(ride.pickup_latitude)) * math.cos(math.radians(driver_latitude)) * math.sin(dlon / 2)**2
            c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
            distance_meters = 6371000 * c
            MAX_ARRIVAL_PROXIMITY_METERS = 500
            if distance_meters > MAX_ARRIVAL_PROXIMITY_METERS:
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail=f"Arrival verification failed: You are {round(distance_meters)}m from pickup point. Must be within {MAX_ARRIVAL_PROXIMITY_METERS}m to mark arrived."
                )

        now = datetime.now(timezone.utc)
        ride.status = RideStatus.ARRIVED.value
        ride.arrived_at = now

        event = RideStatusEvent(
            ride_id=ride.id,
            previous_status=RideStatus.ASSIGNED.value,
            new_status=RideStatus.ARRIVED.value,
            triggered_by_user_id=driver_user_id,
            trigger_actor=ActorRole.DRIVER.value,
            notes="Chauffeur arrived at pickup location"
        )
        self.db.add(event)
        await self.db.commit()

        await self._broadcast_ride_update(ride.id, RideStatus.ARRIVED.value)

        return {"ride_id": ride.id, "status": ride.status, "arrived_at": now.isoformat()}

    async def start_trip_with_otp(self, driver_user_id: str, ride_id: str, otp_code: str) -> Dict[str, Any]:
        ride = await self.get_ride_by_id(ride_id)
        driver = await self._get_driver_by_user_id(driver_user_id)

        if ride.assigned_driver_id != driver.id:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="You are not authorized for this ride.")

        from backend.domain.models import RideOperationalEvent
        start_check_result = await self.db.execute(select(RideOperationalEvent).where(
            RideOperationalEvent.ride_id == ride.id,
            RideOperationalEvent.actor_user_id == driver_user_id,
            RideOperationalEvent.action == "START_CHECKLIST"
        ).order_by(RideOperationalEvent.created_at.desc()).limit(1))
        start_check = start_check_result.scalar_one_or_none()
        if start_check:
            try:
                checks = json.loads(start_check.details_json) if start_check else {}
            except (TypeError, json.JSONDecodeError):
                checks = {}
            if not all(checks.get(key) is True for key in ("passenger_present", "vehicle_safe", "belongings_checked", "route_confirmed")):
                raise HTTPException(status_code=409, detail="Complete and save all pre-departure checks before passenger verification.")

        RideStateMachine.validate_transition(ride.status, RideStatus.IN_PROGRESS.value, ActorRole.DRIVER.value)

        if not verify_otp_constant_time(otp_code.strip(), ride.start_otp):
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid authorization passcode. Please verify with passenger.")

        now = datetime.now(timezone.utc)
        ride.status = RideStatus.IN_PROGRESS.value
        ride.started_at = now
        ride.otp_verified = True

        event = RideStatusEvent(
            ride_id=ride.id,
            previous_status=RideStatus.ARRIVED.value,
            new_status=RideStatus.IN_PROGRESS.value,
            triggered_by_user_id=driver_user_id,
            trigger_actor=ActorRole.DRIVER.value,
            notes="Trip commenced after successful passenger passcode verification"
        )
        self.db.add(event)
        await self.db.commit()

        await self._broadcast_ride_update(ride.id, RideStatus.IN_PROGRESS.value)

        return {"ride_id": ride.id, "status": ride.status, "started_at": now.isoformat()}

    async def finish_trip(
        self,
        driver_user_id: str,
        ride_id: str,
        actual_distance_km: Optional[float] = None,
        actual_duration_minutes: Optional[float] = None
    ) -> Dict[str, Any]:
        ride = await self.get_ride_by_id(ride_id)
        driver = await self._get_driver_by_user_id(driver_user_id)

        if ride.assigned_driver_id != driver.id:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="You are not authorized for this ride.")

        from backend.domain.models import RideOperationalEvent
        checklist_result = await self.db.execute(select(RideOperationalEvent).where(
            RideOperationalEvent.ride_id == ride.id,
            RideOperationalEvent.actor_user_id == driver_user_id,
            RideOperationalEvent.action == "COMPLETION_CHECKLIST"
        ).order_by(RideOperationalEvent.created_at.desc()).limit(1))
        completion_checklist = checklist_result.scalar_one_or_none()
        if completion_checklist:
            try:
                checks = json.loads(completion_checklist.details_json) if completion_checklist else {}
            except (TypeError, json.JSONDecodeError):
                checks = {}
            if not all(checks.get(key) is True for key in ("passenger_belongings_returned", "vehicle_cabin_checked", "dropoff_safe", "fare_explained")):
                raise HTTPException(status_code=409, detail="Complete and save all trip completion checks before finishing.")

        if ride.status != RideStatus.IN_PROGRESS.value:
            raise HTTPException(status_code=409, detail="Only an active, unpaused trip can be completed.")

        RideStateMachine.validate_transition(ride.status, RideStatus.COMPLETED.value, ActorRole.DRIVER.value)

        now = datetime.now(timezone.utc)
        ride.status = RideStatus.COMPLETED.value
        ride.completed_at = now
        ride.actual_distance_km = actual_distance_km or ride.estimated_distance_km
        ride.actual_duration_minutes = actual_duration_minutes or ride.estimated_duration_minutes
        # Incorporate waiting charges and promo discounts into final_fare_cents
        ride.final_fare_cents = max(0, ride.estimated_fare_cents + ride.waiting_charge_cents - ride.discount_cents)

        # Free driver availability
        avail_stmt = (
            update(DriverAvailability)
            .where(DriverAvailability.driver_id == driver.id)
            .values(is_busy=False, updated_at=now)
        )
        await self.db.execute(avail_stmt)

        # Update driver stats and driver_wallets table
        driver.total_trips += 1
        from backend.application.services.commission_service import CommissionService
        fare_cents = int(ride.final_fare_cents or ride.estimated_fare_cents or 0)
        snap = await CommissionService.calculate_for_ride(
            self.db,
            gross_cents=fare_cents,
            category_id=getattr(ride, "vehicle_category_id", None) or getattr(ride, "category_id", None),
            effective_at=getattr(ride, "completed_at", None) or getattr(ride, "requested_at", None) or now,
            calculation_basis="RIDE_COMPLETION",
        )
        commission_cents = snap.commission_cents
        await CommissionService.record_ride_snapshot(
            self.db,
            ride_id=ride.id,
            snapshot=snap,
            category_id=getattr(ride, "vehicle_category_id", None) or getattr(ride, "category_id", None),
            commission_classification="RECEIVABLE" if ride.payment_method == "CASH" else "PAYABLE",
            currency="INR",
        )

        stmt_w = select(DriverWallet).where(DriverWallet.driver_id == driver.id).with_for_update()
        d_wallet = (await self.db.execute(stmt_w)).scalar_one_or_none()
        if not d_wallet:
            d_wallet = DriverWallet(driver_id=driver.id, balance_cents=0, currency="INR")
            self.db.add(d_wallet)
            await self.db.flush()

        if ride.payment_method == "CASH":
            # For CASH rides: Record gross cash earning and commission receivable separately.
            # Do NOT immediately deduct commission from the driver's wallet.
            existing_comm = (await self.db.execute(select(DriverLedgerEntry).where(
                DriverLedgerEntry.driver_id == driver.id,
                DriverLedgerEntry.reference_id == ride.id,
                DriverLedgerEntry.category.in_(["COMMISSION_RECEIVABLE", "COMMISSION"])
            ))).scalar_one_or_none()
            if not existing_comm:
                self.db.add(DriverLedgerEntry(
                    id=str(uuid.uuid4()),
                    driver_id=driver.id,
                    entry_type="CREDIT",
                    category="CASH_TRIP_EARNING",
                    amount_cents=fare_cents,
                    balance_after_cents=d_wallet.balance_cents,
                    reference_id=ride.id,
                    description=f"Gross cash earning for ride #{ride.id[:8]}",
                    created_at=now
                ))
                if commission_cents > 0:
                    self.db.add(DriverLedgerEntry(
                        id=str(uuid.uuid4()),
                        driver_id=driver.id,
                        entry_type="DEBIT",
                        category="COMMISSION_RECEIVABLE",
                        amount_cents=-commission_cents,
                        balance_after_cents=d_wallet.balance_cents,
                        reference_id=ride.id,
                        description=f"Platform commission receivable ({snap.percentage}% v{snap.version}) for cash ride #{ride.id[:8]}",
                        created_at=now
                    ))
        elif ride.payment_method == "WALLET":
            # For WALLET rides: Credit gross earning and record commission payable separately.
            existing_earn = (await self.db.execute(select(DriverLedgerEntry).where(
                DriverLedgerEntry.driver_id == driver.id,
                DriverLedgerEntry.reference_id == ride.id,
                DriverLedgerEntry.category == "TRIP_EARNING"
            ))).scalar_one_or_none()
            if not existing_earn:
                d_wallet.balance_cents += fare_cents
                d_wallet.updated_at = now
                self.db.add(DriverLedgerEntry(
                    id=str(uuid.uuid4()),
                    driver_id=driver.id,
                    entry_type="CREDIT",
                    category="TRIP_EARNING",
                    amount_cents=fare_cents,
                    balance_after_cents=d_wallet.balance_cents,
                    reference_id=ride.id,
                    description=f"Gross earnings for wallet-paid ride #{ride.id[:8]}",
                    created_at=now
                ))
                existing_comm = (await self.db.execute(select(DriverLedgerEntry).where(
                    DriverLedgerEntry.driver_id == driver.id,
                    DriverLedgerEntry.reference_id == ride.id,
                    DriverLedgerEntry.category.in_(["COMMISSION_PAYABLE", "COMMISSION"])
                ))).scalar_one_or_none()
                if not existing_comm and commission_cents > 0:
                    self.db.add(DriverLedgerEntry(
                        id=str(uuid.uuid4()),
                        driver_id=driver.id,
                        entry_type="DEBIT",
                        category="COMMISSION_PAYABLE",
                        amount_cents=-commission_cents,
                        balance_after_cents=d_wallet.balance_cents,
                        reference_id=ride.id,
                        description=f"Platform commission ({snap.percentage}% v{snap.version}) for wallet-paid ride #{ride.id[:8]}",
                        created_at=now
                    ))

        # Debit commuter wallet if paid via WALLET
        cust_stmt = select(CustomerProfile).where(CustomerProfile.id == ride.customer_id)
        c_res = await self.db.execute(cust_stmt)
        cust_profile = c_res.scalars().first()
        if cust_profile and ride.payment_method == "WALLET":
            from backend.application.services.wallet_service import WalletService
            await WalletService.debit_wallet(
                session=self.db,
                user_id=cust_profile.user_id,
                amount_cents=fare_cents,
                transaction_type="RIDE_PAYMENT",
                reference_id=ride.id,
                description=f"Trip fare payment for ride #{ride.id[:8]}",
                idempotency_key=f"ride_wallet_deb_{ride.id}"
            )

        if cust_profile:
            await PromoPassService.attribute_referral_reward(self.db, cust_profile.user_id)

        # Create Payment entity
        payment = Payment(
            ride_id=ride.id,
            customer_id=ride.customer_id,
            driver_id=driver.id,
            amount_cents=ride.final_fare_cents,
            payment_method=ride.payment_method,
            status="PENDING" if ride.payment_method != "WALLET" else "PAID",
            idempotency_key=f"pay_ride_{ride.id}"
        )
        self.db.add(payment)

        event = RideStatusEvent(
            ride_id=ride.id,
            previous_status=RideStatus.IN_PROGRESS.value,
            new_status=RideStatus.COMPLETED.value,
            triggered_by_user_id=driver_user_id,
            trigger_actor=ActorRole.DRIVER.value,
            notes="Trip completed safely by chauffeur"
        )
        self.db.add(event)
        await self.db.commit()

        await self._broadcast_ride_update(ride.id, RideStatus.COMPLETED.value, ride.final_fare_cents)

        return {
            "ride_id": ride.id,
            "status": ride.status,
            "final_fare_cents": ride.final_fare_cents,
            "completed_at": now.isoformat(),
            "payment_id": payment.id,
            "payment_status": payment.status
        }

    async def cancel_ride(
        self,
        user_id: str,
        role: str,
        ride_id: str,
        reason: str
    ) -> Dict[str, Any]:
        ride = await self.get_ride_by_id(ride_id)
        actor_role = ActorRole.CUSTOMER.value if role == "customer" else (
            ActorRole.DRIVER.value if role == "driver" else ActorRole.ADMIN.value
        )

        RideStateMachine.validate_transition(ride.status, RideStatus.CANCELLED.value, actor_role)

        now = datetime.now(timezone.utc)
        prev_status = ride.status
        ride.status = RideStatus.CANCELLED.value
        ride.cancelled_at = now
        ride.cancellation_reason = reason
        ride.cancelled_by_role = actor_role

        # Task 061: Cancellation fee calculation based on grace window
        cancellation_fee_cents = 0
        if actor_role == ActorRole.CUSTOMER.value and prev_status in (RideStatus.ASSIGNED.value, RideStatus.ARRIVED.value):
            # Grace window: 2 minutes (120 seconds) from assignment
            GRACE_PERIOD_SECONDS = 120
            assigned_time = ride.assigned_at
            if assigned_time:
                if assigned_time.tzinfo is None:
                    assigned_time = assigned_time.replace(tzinfo=timezone.utc)
                elapsed_seconds = (now - assigned_time).total_seconds()
                if elapsed_seconds > GRACE_PERIOD_SECONDS:
                    fare_rule = (await self.db.execute(select(FareRule).where(FareRule.id == ride.fare_rule_id))).scalar_one_or_none()
                    cancellation_fee_cents = fare_rule.cancellation_fee_cents if fare_rule else 1000

        # If a driver was assigned, free them
        if ride.assigned_driver_id:
            avail_stmt = (
                update(DriverAvailability)
                .where(DriverAvailability.driver_id == ride.assigned_driver_id)
                .values(is_busy=False, updated_at=now)
            )
            await self.db.execute(avail_stmt)

        event = RideStatusEvent(
            ride_id=ride.id,
            previous_status=prev_status,
            new_status=RideStatus.CANCELLED.value,
            triggered_by_user_id=user_id,
            trigger_actor=actor_role,
            notes=f"Cancelled with reason: {reason}. Cancellation fee: {cancellation_fee_cents} paise"
        )
        self.db.add(event)
        await self.db.commit()

        await self._broadcast_ride_update(ride.id, RideStatus.CANCELLED.value)

        return {
            "ride_id": ride.id,
            "status": ride.status,
            "cancelled_at": now.isoformat(),
            "cancellation_fee_cents": cancellation_fee_cents
        }

    async def rate_driver(
        self,
        customer_user_id: str,
        ride_id: str,
        rating: int,
        feedback: Optional[str] = None
    ) -> Dict[str, Any]:
        if rating < 1 or rating > 5:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Rating must be an integer between 1 and 5.")

        ride = await self.get_ride_by_id(ride_id)
        if ride.status != RideStatus.COMPLETED.value:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Only completed journeys can be reviewed.")

        stmt = select(CustomerProfile).where(CustomerProfile.user_id == customer_user_id)
        res = await self.db.execute(stmt)
        customer = res.scalar_one_or_none()
        if not customer or customer.id != ride.customer_id:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="You can only rate your own journeys.")

        # Check duplicate rating
        stmt_rate = select(DriverRating).where(DriverRating.ride_id == ride.id)
        res_rate = await self.db.execute(stmt_rate)
        if res_rate.scalar_one_or_none():
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="You have already submitted a review for this journey.")

        driver_rating = DriverRating(
            ride_id=ride.id,
            customer_id=customer.id,
            driver_id=ride.assigned_driver_id,
            rating=rating,
            feedback=feedback
        )
        self.db.add(driver_rating)

        # Update driver average rating
        stmt_drv = select(DriverProfile).where(DriverProfile.id == ride.assigned_driver_id)
        res_drv = await self.db.execute(stmt_drv)
        driver = res_drv.scalar_one_or_none()
        if driver:
            previous_count = int(driver.rating_count or 0)
            previous_average = float(driver.rating_average) if driver.rating_average is not None else 0.0
            new_count = previous_count + 1
            new_avg = ((previous_average * previous_count) + rating) / new_count
            driver.rating_count = new_count
            driver.rating_average = round(new_avg, 2)

        await self.db.commit()
        return {"message": "Thank you for your distinguished review.", "rating": rating}

    async def get_ride_by_id(self, ride_id: str) -> Ride:
        stmt = (
            select(Ride)
            .options(
                joinedload(Ride.driver).joinedload(DriverProfile.user),
                joinedload(Ride.customer).joinedload(CustomerProfile.user)
            )
            .where(Ride.id == ride_id)
        )
        res = await self.db.execute(stmt)
        ride = res.unique().scalar_one_or_none()
        if not ride:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Ride not found.")
        return ride

    async def _get_driver_by_user_id(self, user_id: str) -> DriverProfile:
        stmt = select(DriverProfile).where(DriverProfile.user_id == user_id)
        res = await self.db.execute(stmt)
        driver = res.scalar_one_or_none()
        if not driver:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Driver profile not found.")
        return driver

    async def _broadcast_ride_update(self, ride_id: str, status: str, final_fare_cents: Optional[int] = None):
        try:
            from backend.api.routes.ws import ws_manager
            event_map = {
                RideStatus.REQUESTED.value: "RIDE_REQUESTED",
                RideStatus.ASSIGNED.value: "RIDE_ACCEPTED",
                RideStatus.ARRIVED.value: "DRIVER_ARRIVED",
                RideStatus.IN_PROGRESS.value: "TRIP_STARTED",
                RideStatus.COMPLETED.value: "TRIP_COMPLETED",
                RideStatus.CANCELLED.value: "RIDE_CANCELLED",
            }
            canonical_event = event_map.get(status, f"RIDE_{status}")
            payload_data = {
                "ride_id": ride_id,
                "status": status,
                "final_fare_cents": final_fare_cents
            }
            message = {
                "event_type": canonical_event,
                "status": status,
                "ride_id": ride_id,
                "payload": payload_data
            }
            await ws_manager.broadcast_ride_update(ride_id, message)
        except Exception:
            pass

