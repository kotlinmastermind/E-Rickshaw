import pytest
import asyncio
import uuid
from datetime import datetime, timezone, timedelta
from backend.application.services.pool_service import PoolService
from backend.domain.models import PooledTrip, PooledBooking, PooledTripStop, FareRule, VehicleCategory, User, CustomerProfile
from backend.infrastructure.database.session import async_session_maker

@pytest.mark.asyncio
async def test_shared_fare_estimate_and_savings_calculation():
    async with async_session_maker() as session:
        service = PoolService(session)
        # Fetch Sedan category
        rule = (await session.execute(FareRule.__table__.select().where(FareRule.is_active == True))).first()
        assert rule is not None
        cat_id = rule.category_id

        # Calculate shared estimate for 1 seat
        est_1 = await service.calculate_shared_fare_estimate(
            category_id=cat_id,
            pickup_lat=18.9217, pickup_lng=72.8332,
            dest_lat=19.0657, dest_lng=72.8687,
            seats_requested=1
        )
        assert est_1["is_shared"] is True
        assert est_1["seats_requested"] == 1
        assert est_1["shared_fare_cents"] < est_1["solo_fare_cents"]
        assert est_1["savings_cents"] > 0
        assert est_1["shared_discount_percentage"] == 25.0

        # Calculate shared estimate for 2 seats
        est_2 = await service.calculate_shared_fare_estimate(
            category_id=cat_id,
            pickup_lat=18.9217, pickup_lng=72.8332,
            dest_lat=19.0657, dest_lng=72.8687,
            seats_requested=2
        )
        assert est_2["seats_requested"] == 2
        assert est_2["shared_fare_cents"] > est_1["shared_fare_cents"]

@pytest.mark.asyncio
async def test_atomic_seat_reservation_and_overbooking_prevention():
    async with async_session_maker() as session:
        service = PoolService(session)
        rule = (await session.execute(FareRule.__table__.select().where(FareRule.is_active == True))).first()
        cat_id = rule.category_id

        # Create test customer
        cust_user = User(
            phone_number=f"+9198{uuid.uuid4().int % 100000000:08d}",
            role="customer",
            full_name="Pool Test Passenger 1"
        )
        session.add(cust_user)
        await session.flush()
        cust_profile = CustomerProfile(user_id=cust_user.id)
        session.add(cust_profile)
        await session.commit()

        # Generate estimate
        est = await service.calculate_shared_fare_estimate(
            category_id=cat_id,
            pickup_lat=18.9217, pickup_lng=72.8332,
            dest_lat=19.0657, dest_lng=72.8687,
            seats_requested=3
        )

        # Book 3 seats in a new pool (capacity = 4)
        booking1 = await service.book_shared_ride(
            customer_user_id=cust_user.id,
            category_id=cat_id,
            estimate_id=est["estimate_id"],
            pickup_address="Apollo Bunder, Colaba",
            pickup_lat=18.9217, pickup_lng=72.8332,
            dest_address="BKC G Block, Mumbai",
            dest_lat=19.0657, dest_lng=72.8687,
            seats_reserved=3
        )
        assert booking1["seats_reserved"] == 3
        assert booking1["total_trip_reserved_seats"] == 3
        pool_id = booking1["pooled_trip_id"]

        # Attempt to book 2 more seats in the same pool (3 + 2 = 5 > max 4 seats) with FAIL fallback
        est_fail = await service.calculate_shared_fare_estimate(
            category_id=cat_id,
            pickup_lat=18.9217, pickup_lng=72.8332,
            dest_lat=19.0657, dest_lng=72.8687,
            seats_requested=2
        )
        from fastapi import HTTPException
        with pytest.raises(HTTPException) as exc_info:
            await service.book_shared_ride(
                customer_user_id=cust_user.id,
                category_id=cat_id,
                estimate_id=est_fail["estimate_id"],
                pickup_address="Apollo Bunder, Colaba",
                pickup_lat=18.9217, pickup_lng=72.8332,
                dest_address="BKC G Block, Mumbai",
                dest_lat=19.0657, dest_lng=72.8687,
                seats_reserved=2,
                target_pooled_trip_id=pool_id,
                fallback_policy="FAIL"
            )
        assert exc_info.value.status_code == 409
        assert "capacity is fully booked" in exc_info.value.detail or "Capacity exceeded" in exc_info.value.detail

@pytest.mark.asyncio
async def test_route_matching_detour_and_rejection_reasons():
    async with async_session_maker() as session:
        service = PoolService(session)
        rule = (await session.execute(FareRule.__table__.select().where(FareRule.is_active == True))).first()
        cat_id = rule.category_id

        # Match routes with a strict 1.0 km detour limit
        matches = await service.find_route_matches(
            category_id=cat_id,
            pickup_lat=18.9217, pickup_lng=72.8332,
            dest_lat=19.0657, dest_lng=72.8687,
            seats_requested=1,
            max_detour_km=1.0
        )
        assert "matches_found" in matches
        assert "candidates" in matches
        assert "rejections_evaluated" in matches
        assert isinstance(matches["rejection_reasons"], list)

@pytest.mark.asyncio
async def test_conservation_of_monetary_totals_in_fare_split():
    async with async_session_maker() as session:
        service = PoolService(session)
        rule = (await session.execute(FareRule.__table__.select().where(FareRule.is_active == True))).first()
        cat_id = rule.category_id

        # Create passenger
        user = User(phone_number=f"+9198{uuid.uuid4().int % 100000000:08d}", role="customer")
        session.add(user)
        await session.flush()
        cust = CustomerProfile(user_id=user.id)
        session.add(cust)
        await session.commit()

        est = await service.calculate_shared_fare_estimate(
            category_id=cat_id,
            pickup_lat=18.9217, pickup_lng=72.8332,
            dest_lat=19.0657, dest_lng=72.8687,
            seats_requested=2
        )

        booking = await service.book_shared_ride(
            customer_user_id=user.id,
            category_id=cat_id,
            estimate_id=est["estimate_id"],
            pickup_address="Apollo Bunder",
            pickup_lat=18.9217, pickup_lng=72.8332,
            dest_address="BKC G Block",
            dest_lat=19.0657, dest_lng=72.8687,
            seats_reserved=2
        )

        # Invariant: allocated fare == driver earnings + platform commission (exact integer cents)
        allocated = booking["allocated_fare_cents"]
        driver = booking["driver_earnings_cents"]
        commission = booking["platform_commission_cents"]
        assert allocated == driver + commission
        assert allocated > 0
        assert driver > 0
        assert commission > 0

        # Query pooled trip details and verify conservation
        trip_details = await service.get_pooled_trip_details(booking["pooled_trip_id"])
        fin = trip_details["financial_summary"]
        assert fin["conservation_verified"] is True
        assert fin["total_fare_cents"] == fin["total_driver_earnings_cents"] + fin["total_platform_commission_cents"]

@pytest.mark.asyncio
async def test_passenger_occupancy_lifecycle_state_machine():
    async with async_session_maker() as session:
        service = PoolService(session)
        rule = (await session.execute(FareRule.__table__.select().where(FareRule.is_active == True))).first()
        cat_id = rule.category_id

        import uuid
        user = User(phone_number=f"+9198{uuid.uuid4().int % 100000000:08d}", role="customer")
        session.add(user)
        await session.flush()
        cust = CustomerProfile(user_id=user.id)
        session.add(cust)
        await session.commit()

        est = await service.calculate_shared_fare_estimate(
            category_id=cat_id,
            pickup_lat=18.9217, pickup_lng=72.8332,
            dest_lat=19.0657, dest_lng=72.8687,
            seats_requested=1
        )

        booking = await service.book_shared_ride(
            customer_user_id=user.id,
            category_id=cat_id,
            estimate_id=est["estimate_id"],
            pickup_address="Apollo Bunder",
            pickup_lat=18.9217, pickup_lng=72.8332,
            dest_address="BKC",
            dest_lat=19.0657, dest_lng=72.8687,
            seats_reserved=1
        )

        b_id = booking["booking_id"]
        # 1. Transition: BOARDED
        board_res = await service.update_passenger_occupancy(
            booking_id=b_id,
            event_type="BOARDED",
            actor_id="test_driver_id",
            actor_role="driver"
        )
        assert board_res["booking_status"] == "BOARDED"
        assert board_res["current_occupancy"] >= 1

        # 2. Transition: ALIGHTED
        alight_res = await service.update_passenger_occupancy(
            booking_id=b_id,
            event_type="ALIGHTED",
            actor_id="test_driver_id",
            actor_role="driver"
        )
        assert alight_res["booking_status"] == "COMPLETED"
        assert alight_res["current_occupancy"] == 0
