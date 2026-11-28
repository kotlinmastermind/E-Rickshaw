"""PostgreSQL concurrency integration tests for Service Area deactivation vs Ride & Pool creation.

Requires POSTGRES_TEST_DATABASE_URL pointing to a dedicated non-production PostgreSQL test database.
If POSTGRES_TEST_DATABASE_URL is not configured, tests skip cleanly without touching production DB.
"""
import asyncio
import json
import os
import uuid
from datetime import datetime, timedelta, timezone
from unittest.mock import AsyncMock, patch

import pytest
from fastapi import HTTPException
from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine

from backend.application.services.admin_governance_service import AdminGovernanceService
from backend.application.services.pool_service import PoolService
from backend.application.services.ride_service import RideService
from backend.application.services.service_area_service import ServiceAreaService
from backend.domain.models import (
    AuditLog,
    Base,
    CustomerProfile,
    FareEstimate,
    FareRule,
    GeofenceZone,
    GeofenceZoneAudit,
    PassengerOccupancyEvent,
    PooledBooking,
    PooledTrip,
    PooledTripStop,
    Ride,
    RideAssignment,
    RideStatusEvent,
    User,
    VehicleCategory,
)

POSTGRES_TEST_URL = os.getenv("POSTGRES_TEST_DATABASE_URL", "").strip()

pytestmark = pytest.mark.skipif(
    not POSTGRES_TEST_URL or not POSTGRES_TEST_URL.startswith("postgresql"),
    reason="BLOCKED: POSTGRES_TEST_DATABASE_URL is not configured; PostgreSQL concurrency integration tests not executed.",
)


def _isolated_polygon(lat_base: float, lng_base: float) -> list[dict[str, float]]:
    """Return a non-overlapping polygon in an uninhabited test grid away from seeded cities."""
    return [
        {"lat": lat_base, "lng": lng_base},
        {"lat": lat_base + 0.5, "lng": lng_base},
        {"lat": lat_base + 0.5, "lng": lng_base + 0.5},
        {"lat": lat_base, "lng": lng_base + 0.5},
    ]


async def _seed_area_and_customer(session_factory, *, lat_base: float, lng_base: float):
    city_code = f"PG_RACE_{uuid.uuid4().hex[:8].upper()}"
    cat_code = f"PGCAT_{uuid.uuid4().hex[:8].upper()}"
    coords = _isolated_polygon(lat_base, lng_base)

    async with session_factory() as db:
        admin_user = User(
            phone_number=f"+9191{uuid.uuid4().int % 100000000:08d}",
            full_name="PG Race Admin",
            role="admin",
            is_active=True,
            is_verified=True,
        )
        cust_user = User(
            phone_number=f"+9192{uuid.uuid4().int % 100000000:08d}",
            full_name="PG Race Customer",
            role="customer",
            is_active=True,
            is_verified=True,
        )
        cat = VehicleCategory(
            code=cat_code,
            name="PG Race Category",
            description="Postgres concurrency test category",
            capacity=4,
            is_active=True,
        )
        db.add_all([admin_user, cust_user, cat])
        await db.flush()

        cust_profile = CustomerProfile(user_id=cust_user.id)
        zone = GeofenceZone(
            name=f"PG Race Zone {city_code}",
            zone_type="SERVICE_AREA",
            city_code=city_code,
            coordinates_json=json.dumps(coords),
            surge_multiplier=1.0,
            is_active=True,
        )
        rule = FareRule(
            category_id=cat.id,
            city_code=city_code,
            base_fare_cents=4000,
            per_km_cents=1500,
            per_minute_cents=200,
            minimum_fare_cents=5000,
            is_active=True,
            workflow_status="ACTIVE",
        )
        db.add_all([cust_profile, zone, rule])
        await db.flush()

        est = FareEstimate(
            category_id=cat.id,
            fare_rule_id=rule.id,
            pickup_latitude=lat_base + 0.1,
            pickup_longitude=lng_base + 0.1,
            destination_latitude=lat_base + 0.3,
            destination_longitude=lng_base + 0.3,
            distance_km=4.2,
            estimated_duration_minutes=12.0,
            estimated_fare_cents=9500,
            expires_at=datetime.now(timezone.utc) + timedelta(minutes=15),
        )
        db.add(est)
        await db.commit()
        return {
            "admin_id": admin_user.id,
            "customer_user_id": cust_user.id,
            "customer_profile_id": cust_profile.id,
            "category_id": cat.id,
            "rule_id": rule.id,
            "zone_id": zone.id,
            "zone_name": zone.name,
            "city_code": city_code,
            "coords": coords,
            "estimate_id": est.id,
            "pickup_lat": lat_base + 0.1,
            "pickup_lng": lng_base + 0.1,
            "dest_lat": lat_base + 0.3,
            "dest_lng": lng_base + 0.3,
        }


async def _cleanup_seeded_data(session_factory, seed: dict):
    async with session_factory() as db:
        zone_id = seed["zone_id"]
        cat_id = seed["category_id"]
        rule_id = seed["rule_id"]
        admin_id = seed["admin_id"]
        cust_user_id = seed["customer_user_id"]
        cust_profile_id = seed["customer_profile_id"]

        ride_ids = list(
            (await db.execute(select(Ride.id).where(Ride.service_area_id == zone_id))).scalars().all()
        )
        trip_ids = list(
            (await db.execute(select(PooledTrip.id).where(PooledTrip.service_area_id == zone_id))).scalars().all()
        )

        if trip_ids:
            await db.execute(
                delete(PassengerOccupancyEvent).where(PassengerOccupancyEvent.pooled_trip_id.in_(trip_ids))
            )
            await db.execute(delete(PooledTripStop).where(PooledTripStop.pooled_trip_id.in_(trip_ids)))
            await db.execute(delete(PooledBooking).where(PooledBooking.pooled_trip_id.in_(trip_ids)))
        if ride_ids:
            await db.execute(delete(RideAssignment).where(RideAssignment.ride_id.in_(ride_ids)))
            await db.execute(delete(RideStatusEvent).where(RideStatusEvent.ride_id.in_(ride_ids)))
            await db.execute(delete(Ride).where(Ride.id.in_(ride_ids)))
        if trip_ids:
            await db.execute(delete(PooledTrip).where(PooledTrip.id.in_(trip_ids)))

        await db.execute(delete(FareEstimate).where(FareEstimate.fare_rule_id == rule_id))
        await db.execute(delete(GeofenceZoneAudit).where(GeofenceZoneAudit.zone_id == zone_id))
        await db.execute(delete(GeofenceZone).where(GeofenceZone.id == zone_id))
        await db.execute(delete(FareRule).where(FareRule.id == rule_id))
        await db.execute(delete(VehicleCategory).where(VehicleCategory.id == cat_id))
        await db.execute(delete(CustomerProfile).where(CustomerProfile.id == cust_profile_id))
        await db.execute(delete(AuditLog).where(AuditLog.actor_id.in_([admin_id, cust_user_id])))
        await db.execute(delete(User).where(User.id.in_([admin_id, cust_user_id])))
        await db.commit()


@pytest.mark.asyncio
async def test_postgres_concurrent_ride_creation_vs_service_area_deactivation():
    """Verify PostgreSQL FOR SHARE / FOR UPDATE prevents write skew in BOTH orderings for Ride creation."""
    engine = create_async_engine(POSTGRES_TEST_URL, echo=False, pool_size=10, max_overflow=10)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

    session_factory = async_sessionmaker(engine, expire_on_commit=False)
    seed_a = await _seed_area_and_customer(session_factory, lat_base=-14.0, lng_base=114.0)
    seed_b = await _seed_area_and_customer(session_factory, lat_base=-16.0, lng_base=116.0)

    try:
        # 1. Booking acquires FOR SHARE first -> Admin DELETE blocks until booking commits, then fails 409
        events_a: list[str] = []
        booking_ready = asyncio.Event()

        async def _booking_first():
            async with session_factory() as s_book:
                orig_commit = s_book.commit

                async def _delayed_commit():
                    booking_ready.set()
                    await asyncio.sleep(0.20)
                    await orig_commit()
                    events_a.append("booking_committed")

                s_book.commit = _delayed_commit  # type: ignore[method-assign]
                return await RideService(s_book).create_ride_request(
                    customer_user_id=seed_a["customer_user_id"],
                    estimate_id=seed_a["estimate_id"],
                    category_id=seed_a["category_id"],
                    pickup_address="PG Test Pickup A",
                    destination_address="PG Test Drop A",
                    payment_method="CASH",
                )

        async def _admin_second():
            await asyncio.wait_for(booking_ready.wait(), timeout=5.0)
            events_a.append("admin_started")
            async with session_factory() as s_admin:
                try:
                    await AdminGovernanceService(s_admin).delete_geofence(seed_a["admin_id"], seed_a["zone_id"])
                    events_a.append("admin_succeeded")
                    return ("ok", 200)
                except HTTPException as exc:
                    await s_admin.rollback()
                    events_a.append("admin_blocked")
                    return ("err", exc.status_code)

        with patch.object(RideService, "_background_dispatch", new=AsyncMock()):
            ride_res, admin_res = await asyncio.gather(_booking_first(), _admin_second())
        assert ride_res["ride_id"]
        assert admin_res == ("err", 409)
        # Proves admin started while booking held FOR SHARE, blocked in PostgreSQL, and only resolved AFTER commit
        assert events_a == ["admin_started", "booking_committed", "admin_blocked"]

        async with session_factory() as check_db:
            z_a = await check_db.get(GeofenceZone, seed_a["zone_id"])
            r_a = await check_db.get(Ride, ride_res["ride_id"])
            assert z_a is not None and z_a.is_active is True
            assert r_a is not None and r_a.status == "REQUESTED" and r_a.service_area_id == seed_a["zone_id"]

        # 2. Admin PUT(is_active=False) acquires FOR UPDATE first while booking already saw active zone ->
        #    booking blocks on FOR SHARE, reloads is_active=False via populate_existing=True, and fails cleanly
        events_b: list[str] = []
        admin_locked = asyncio.Event()
        booking_about_to_lock = asyncio.Event()
        orig_lock_method = ServiceAreaService.lock_active_service_area_for_booking

        async def _spied_lock(self_sas: ServiceAreaService, service_area_id: str):
            booking_about_to_lock.set()
            return await orig_lock_method(self_sas, service_area_id)

        async def _admin_first():
            async with session_factory() as s_admin:
                orig_commit = s_admin.commit

                async def _delayed_admin_commit():
                    admin_locked.set()
                    await asyncio.wait_for(booking_about_to_lock.wait(), timeout=5.0)
                    await asyncio.sleep(0.15)
                    events_b.append("admin_committing")
                    await orig_commit()
                    events_b.append("admin_committed")

                s_admin.commit = _delayed_admin_commit  # type: ignore[method-assign]
                return await AdminGovernanceService(s_admin).save_geofence(
                    seed_b["admin_id"],
                    seed_b["zone_id"],
                    {
                        "name": seed_b["zone_name"],
                        "zone_type": "SERVICE_AREA",
                        "city_code": seed_b["city_code"],
                        "coordinates": seed_b["coords"],
                        "surge_multiplier": 1.0,
                        "is_active": False,
                    },
                )

        async def _booking_second():
            await asyncio.wait_for(admin_locked.wait(), timeout=5.0)
            async with session_factory() as s_book:
                try:
                    await RideService(s_book).create_ride_request(
                        customer_user_id=seed_b["customer_user_id"],
                        estimate_id=seed_b["estimate_id"],
                        category_id=seed_b["category_id"],
                        pickup_address="PG Test Pickup B",
                        destination_address="PG Test Drop B",
                        payment_method="CASH",
                    )
                    events_b.append("booking_succeeded")
                    return ("ok", 200)
                except HTTPException as exc:
                    await s_book.rollback()
                    events_b.append("booking_blocked")
                    return ("err", exc.status_code)

        with patch.object(ServiceAreaService, "lock_active_service_area_for_booking", _spied_lock):
            admin_out, booking_out = await asyncio.gather(_admin_first(), _booking_second())

        assert admin_out["is_active"] is False
        assert booking_out == ("err", 409)
        assert events_b == ["admin_committing", "admin_committed", "booking_blocked"]

        async with session_factory() as check_db:
            z_b = await check_db.get(GeofenceZone, seed_b["zone_id"])
            rides_b = (
                await check_db.execute(select(Ride).where(Ride.service_area_id == seed_b["zone_id"]))
            ).scalars().all()
            assert z_b is not None and z_b.is_active is False
            assert len(rides_b) == 0
    finally:
        await _cleanup_seeded_data(session_factory, seed_a)
        await _cleanup_seeded_data(session_factory, seed_b)
        await engine.dispose()


@pytest.mark.asyncio
async def test_postgres_concurrent_pool_creation_vs_service_area_deactivation():
    """Verify PostgreSQL FOR SHARE / FOR UPDATE prevents write skew in BOTH orderings for PooledTrip + Ride creation."""
    engine = create_async_engine(POSTGRES_TEST_URL, echo=False, pool_size=10, max_overflow=10)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

    session_factory = async_sessionmaker(engine, expire_on_commit=False)
    seed_a = await _seed_area_and_customer(session_factory, lat_base=-18.0, lng_base=118.0)
    seed_b = await _seed_area_and_customer(session_factory, lat_base=-20.0, lng_base=120.0)

    try:
        # 1. Pool booking acquires FOR SHARE first -> Admin PUT(is_active=False) blocks and fails 409
        events_a: list[str] = []
        pool_ready = asyncio.Event()

        async with session_factory() as est_db:
            est_a = await PoolService(est_db).calculate_shared_fare_estimate(
                category_id=seed_a["category_id"],
                pickup_lat=seed_a["pickup_lat"],
                pickup_lng=seed_a["pickup_lng"],
                dest_lat=seed_a["dest_lat"],
                dest_lng=seed_a["dest_lng"],
                seats_requested=1,
            )

        async def _pool_first():
            async with session_factory() as s_pool:
                orig_commit = s_pool.commit

                async def _delayed_commit():
                    pool_ready.set()
                    await asyncio.sleep(0.20)
                    await orig_commit()
                    events_a.append("pool_committed")

                s_pool.commit = _delayed_commit  # type: ignore[method-assign]
                return await PoolService(s_pool).book_shared_ride(
                    customer_user_id=seed_a["customer_user_id"],
                    category_id=seed_a["category_id"],
                    estimate_id=est_a["estimate_id"],
                    pickup_address="PG Pool Pickup A",
                    pickup_lat=seed_a["pickup_lat"],
                    pickup_lng=seed_a["pickup_lng"],
                    dest_address="PG Pool Drop A",
                    dest_lat=seed_a["dest_lat"],
                    dest_lng=seed_a["dest_lng"],
                    seats_reserved=1,
                    fallback_policy="CREATE_NEW_POOL",
                )

        async def _admin_second():
            await asyncio.wait_for(pool_ready.wait(), timeout=5.0)
            events_a.append("admin_started")
            async with session_factory() as s_admin:
                try:
                    await AdminGovernanceService(s_admin).save_geofence(
                        seed_a["admin_id"],
                        seed_a["zone_id"],
                        {
                            "name": seed_a["zone_name"],
                            "zone_type": "SERVICE_AREA",
                            "city_code": seed_a["city_code"],
                            "coordinates": seed_a["coords"],
                            "surge_multiplier": 1.0,
                            "is_active": False,
                        },
                    )
                    events_a.append("admin_succeeded")
                    return ("ok", 200)
                except HTTPException as exc:
                    await s_admin.rollback()
                    events_a.append("admin_blocked")
                    return ("err", exc.status_code)

        pool_res, admin_res = await asyncio.gather(_pool_first(), _admin_second())
        assert pool_res["pooled_trip_id"]
        assert admin_res == ("err", 409)
        assert events_a == ["admin_started", "pool_committed", "admin_blocked"]

        async with session_factory() as check_db:
            z_a = await check_db.get(GeofenceZone, seed_a["zone_id"])
            t_a = await check_db.get(PooledTrip, pool_res["pooled_trip_id"])
            assert z_a is not None and z_a.is_active is True
            assert t_a is not None and t_a.status == "OPEN" and t_a.service_area_id == seed_a["zone_id"]

        # 2. Admin DELETE acquires FOR UPDATE first while pool booking is mid-flight -> pool fails cleanly
        async with session_factory() as est_db:
            est_b = await PoolService(est_db).calculate_shared_fare_estimate(
                category_id=seed_b["category_id"],
                pickup_lat=seed_b["pickup_lat"],
                pickup_lng=seed_b["pickup_lng"],
                dest_lat=seed_b["dest_lat"],
                dest_lng=seed_b["dest_lng"],
                seats_requested=1,
            )

        events_b: list[str] = []
        admin_locked = asyncio.Event()
        pool_about_to_lock = asyncio.Event()
        orig_lock_method = ServiceAreaService.lock_active_service_area_for_booking

        async def _spied_pool_lock(self_sas: ServiceAreaService, service_area_id: str):
            pool_about_to_lock.set()
            return await orig_lock_method(self_sas, service_area_id)

        async def _admin_first():
            async with session_factory() as s_admin:
                orig_commit = s_admin.commit

                async def _delayed_admin_commit():
                    admin_locked.set()
                    await asyncio.wait_for(pool_about_to_lock.wait(), timeout=5.0)
                    await asyncio.sleep(0.15)
                    events_b.append("admin_committing")
                    await orig_commit()
                    events_b.append("admin_committed")

                s_admin.commit = _delayed_admin_commit  # type: ignore[method-assign]
                return await AdminGovernanceService(s_admin).delete_geofence(seed_b["admin_id"], seed_b["zone_id"])

        async def _pool_second():
            await asyncio.wait_for(admin_locked.wait(), timeout=5.0)
            async with session_factory() as s_pool:
                try:
                    await PoolService(s_pool).book_shared_ride(
                        customer_user_id=seed_b["customer_user_id"],
                        category_id=seed_b["category_id"],
                        estimate_id=est_b["estimate_id"],
                        pickup_address="PG Pool Pickup B",
                        pickup_lat=seed_b["pickup_lat"],
                        pickup_lng=seed_b["pickup_lng"],
                        dest_address="PG Pool Drop B",
                        dest_lat=seed_b["dest_lat"],
                        dest_lng=seed_b["dest_lng"],
                        seats_reserved=1,
                        fallback_policy="CREATE_NEW_POOL",
                    )
                    events_b.append("pool_succeeded")
                    return ("ok", 200)
                except HTTPException as exc:
                    await s_pool.rollback()
                    events_b.append("pool_blocked")
                    return ("err", exc.status_code)

        with patch.object(ServiceAreaService, "lock_active_service_area_for_booking", _spied_pool_lock):
            admin_out, pool_out = await asyncio.gather(_admin_first(), _pool_second())

        assert admin_out["is_active"] is False
        assert pool_out == ("err", 409)
        assert events_b == ["admin_committing", "admin_committed", "pool_blocked"]

        async with session_factory() as check_db:
            z_b = await check_db.get(GeofenceZone, seed_b["zone_id"])
            trips_b = (
                await check_db.execute(select(PooledTrip).where(PooledTrip.service_area_id == seed_b["zone_id"]))
            ).scalars().all()
            rides_b = (
                await check_db.execute(select(Ride).where(Ride.service_area_id == seed_b["zone_id"]))
            ).scalars().all()
            assert z_b is not None and z_b.is_active is False
            assert len(trips_b) == 0
            assert len(rides_b) == 0
    finally:
        await _cleanup_seeded_data(session_factory, seed_a)
        await _cleanup_seeded_data(session_factory, seed_b)
        await engine.dispose()
