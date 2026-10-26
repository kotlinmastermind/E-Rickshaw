import json
from datetime import datetime, timezone
from typing import Dict, Any, Optional, List
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, update, and_
from fastapi import HTTPException, status
from backend.domain.models import (
    Ride, RideStatusEvent, RideAssignment, FareEstimate, FareRule,
    CustomerProfile, DriverProfile, DriverAvailability, DriverLocation,
    Payment, DriverRating, Vehicle
)
from backend.domain.state_machine.ride_state_machine import (
    RideStateMachine, RideStatus, ActorRole, RideTransitionError
)
from backend.infrastructure.security.crypto import generate_secure_otp, verify_otp_constant_time
from backend.integrations.notifications.provider import get_notification_provider

class RideService:
    def __init__(self, db: AsyncSession):
        self.db = db
        self.notifier = get_notification_provider()

    async def create_ride_request(
        self,
        customer_user_id: str,
        estimate_id: str,
        pickup_address: str,
        destination_address: str,
        payment_method: str = "UPI",
        is_scheduled: bool = False,
        scheduled_pickup_time: Optional[datetime] = None,
        promo_code: Optional[str] = None,
        stops: Optional[List[Dict[str, Any]]] = None
    ) -> Dict[str, Any]:
        # Fetch customer profile
        stmt = select(CustomerProfile).where(CustomerProfile.user_id == customer_user_id)
        res = await self.db.execute(stmt)
        customer = res.scalar_one_or_none()
        if not customer:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Customer profile not found.")

        # Fetch fare estimate snapshot
        stmt_est = select(FareEstimate).where(FareEstimate.id == estimate_id)
        res_est = await self.db.execute(stmt_est)
        estimate = res_est.scalar_one_or_none()
        if not estimate:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid fare estimate snapshot.")

        now = datetime.now(timezone.utc)
        expires_at = estimate.expires_at if estimate.expires_at.tzinfo else estimate.expires_at.replace(tzinfo=timezone.utc)
        if now > expires_at:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Fare estimate has expired. Please recalculate.")

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
            status=RideStatus.REQUESTED.value,
            pickup_address=pickup_address,
            pickup_latitude=estimate.pickup_latitude,
            pickup_longitude=estimate.pickup_longitude,
            destination_address=destination_address,
            destination_latitude=estimate.destination_latitude,
            destination_longitude=estimate.destination_longitude,
            start_otp=start_otp,
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

        return {
            "ride_id": ride.id,
            "status": ride.status,
            "start_otp": ride.start_otp,
            "estimated_fare_cents": ride.estimated_fare_cents,
            "pickup_address": ride.pickup_address,
            "destination_address": ride.destination_address,
            "requested_at": ride.requested_at.isoformat()
        }

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

        now = datetime.now(timezone.utc)

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
        assignment = RideAssignment(
            ride_id=ride_id,
            driver_id=driver.id,
            status="ACCEPTED",
            offered_at=now,
            responded_at=now
        )
        self.db.add(assignment)

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

    async def driver_arrived(self, driver_user_id: str, ride_id: str) -> Dict[str, Any]:
        ride = await self.get_ride_by_id(ride_id)
        driver = await self._get_driver_by_user_id(driver_user_id)

        if ride.assigned_driver_id != driver.id:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="You are not the assigned chauffeur for this trip.")

        RideStateMachine.validate_transition(ride.status, RideStatus.ARRIVED.value, ActorRole.DRIVER.value)

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

        return {"ride_id": ride.id, "status": ride.status, "arrived_at": now.isoformat()}

    async def start_trip_with_otp(self, driver_user_id: str, ride_id: str, otp_code: str) -> Dict[str, Any]:
        ride = await self.get_ride_by_id(ride_id)
        driver = await self._get_driver_by_user_id(driver_user_id)

        if ride.assigned_driver_id != driver.id:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="You are not authorized for this ride.")

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

        # Update driver stats
        driver.total_trips += 1
        driver_earnings = round(ride.final_fare_cents * 0.8)
        driver.wallet_balance_cents += driver_earnings

        # Sync with WalletService and PromoPassService
        from backend.application.services.wallet_service import WalletService
        from backend.application.services.promo_pass_service import PromoPassService
        await WalletService.credit_wallet(
            session=self.db,
            user_id=driver_user_id,
            amount_cents=driver_earnings,
            transaction_type="TRIP_EARNING",
            reference_id=ride.id,
            description=f"Earnings for luxury chauffeur trip {ride.id[:8]}"
        )

        cust_stmt = select(CustomerProfile).where(CustomerProfile.id == ride.customer_id)
        c_res = await self.db.execute(cust_stmt)
        cust_profile = c_res.scalars().first()
        if cust_profile and ride.payment_method == "WALLET":
            await WalletService.debit_wallet(
                session=self.db,
                user_id=cust_profile.user_id,
                amount_cents=ride.final_fare_cents,
                transaction_type="RIDE_PAYMENT",
                reference_id=ride.id,
                description=f"Trip fare payment for ride {ride.id[:8]}"
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
            notes=f"Cancelled with reason: {reason}"
        )
        self.db.add(event)
        await self.db.commit()

        return {"ride_id": ride.id, "status": ride.status, "cancelled_at": now.isoformat()}

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
            new_count = driver.rating_count + 1
            new_avg = ((driver.rating_average * driver.rating_count) + rating) / new_count
            driver.rating_count = new_count
            driver.rating_average = round(new_avg, 2)

        await self.db.commit()
        return {"message": "Thank you for your distinguished review.", "rating": rating}

    async def get_ride_by_id(self, ride_id: str) -> Ride:
        stmt = select(Ride).where(Ride.id == ride_id)
        res = await self.db.execute(stmt)
        ride = res.scalar_one_or_none()
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
