"""
Integration verification for Driver GPS Telemetry Ingestion,
H3 Cell Migration, Redis Eviction/Restart Recovery, and Index Rebuilding.
"""
import uuid
import pytest
from datetime import datetime, timezone, timedelta
from sqlalchemy import select

from backend.domain.models import User, DriverProfile, DriverAvailability, DriverLocation
from backend.application.services.h3_geospatial_service import h3_service
from backend.application.services.live_location_service import LiveLocationService
from backend.infrastructure.cache.redis_client import redis_client


@pytest.mark.asyncio
async def test_driver_gps_ingestion_and_postgres_persistence(db_session):
    """
    Task 1: Verify driver GPS telemetry reaches:
    - H3 conversion service
    - Redis location cache
    - H3 cell membership set
    - PostgreSQL DriverLocation table
    """
    suffix = uuid.uuid4().hex[:6]

    user = User(
        phone_number=f"+91987{suffix}01",
        full_name="Ramesh Chauffeur",
        role="driver",
        is_active=True,
        is_verified=True
    )
    db_session.add(user)
    await db_session.flush()

    profile = DriverProfile(
        id=str(uuid.uuid4()),
        user_id=user.id,
        license_number=f"DL-GPS-{suffix}",
        approval_status="APPROVED"
    )
    db_session.add(profile)
    await db_session.flush()

    avail = DriverAvailability(
        driver_id=profile.id,
        is_online=True,
        is_busy=False
    )
    db_session.add(avail)
    await db_session.commit()

    live_loc = LiveLocationService(db=db_session)
    lat, lng = 28.6139, 77.2090  # New Delhi
    expected_cell = h3_service.get_h3_cell(lat, lng)

    # 1. Ingest GPS update
    res = await live_loc.ingest_driver_location(
        driver_id=profile.id,
        latitude=lat,
        longitude=lng,
        heading=120.0,
        speed_mps=8.5,
        accuracy_m=3.5,
        location_source="GPS",
        is_online=True,
        is_busy=False
    )

    assert res["status"] == "RECORDED"
    assert res["h3_cell"] == expected_cell

    # 2. Verify Redis telemetry cache
    cached = await live_loc.get_driver_live_location(profile.id)
    assert cached is not None
    assert cached["latitude"] == round(lat, 6)
    assert cached["longitude"] == round(lng, 6)
    assert cached["heading"] == 120.0
    assert cached["h3_cell"] == expected_cell

    # 3. Verify Redis H3 cell membership set
    drivers_in_cell = await redis_client.smembers(f"h3:drivers:{expected_cell}")
    assert profile.id in drivers_in_cell

    # 4. Verify PostgreSQL persistence
    pg_loc_stmt = (
        select(DriverLocation)
        .where(DriverLocation.driver_id == profile.id)
        .order_by(DriverLocation.recorded_at.desc())
        .limit(1)
    )
    pg_res = await db_session.execute(pg_loc_stmt)
    loc_record = pg_res.scalar_one_or_none()
    assert loc_record is not None
    assert loc_record.latitude == lat
    assert loc_record.longitude == lng
    assert loc_record.heading == 120.0


@pytest.mark.asyncio
async def test_driver_cell_migration_on_movement(db_session):
    """
    Task 1: Verify atomic cell migration when a driver moves from Cell A to Cell B.
    Old cell must not retain the driver; new cell must contain the driver.
    """
    driver_id = f"migrating_driver_{uuid.uuid4().hex[:6]}"
    live_loc = LiveLocationService(db=db_session)

    # Position 1: Central Delhi (Cell A)
    res_a = await live_loc.ingest_driver_location(
        driver_id=driver_id,
        latitude=28.6139,
        longitude=77.2090,
        is_online=True,
        is_busy=False
    )
    cell_a = res_a["h3_cell"]

    assert driver_id in await redis_client.smembers(f"h3:drivers:{cell_a}")

    # Position 2: Gurgaon (~25 km away, Cell B)
    res_b = await live_loc.ingest_driver_location(
        driver_id=driver_id,
        latitude=28.4595,
        longitude=77.0266,
        is_online=True,
        is_busy=False
    )
    cell_b = res_b["h3_cell"]
    assert cell_a != cell_b

    # Atomic migration verification
    members_a = await redis_client.smembers(f"h3:drivers:{cell_a}")
    members_b = await redis_client.smembers(f"h3:drivers:{cell_b}")

    assert driver_id not in members_a, "Driver was not removed from old H3 cell"
    assert driver_id in members_b, "Driver was not added to new H3 cell"


@pytest.mark.asyncio
async def test_redis_recovery_and_rebuild_from_authoritative_postgres(db_session):
    """
    Task 2: Verify disaster recovery when Redis is restarted or keys are expired:
    1. Populate driver records in PostgreSQL.
    2. Simulate complete Redis data loss (delete keys / cold start).
    3. Run rebuild_h3_index_from_db.
    4. Verify H3 cells and driver locations are fully reconstructed.
    """
    suffix = uuid.uuid4().hex[:6]

    user = User(
        phone_number=f"+91987{suffix}02",
        full_name="Recoverable Driver",
        role="driver",
        is_active=True,
        is_verified=True
    )
    db_session.add(user)
    await db_session.flush()

    profile = DriverProfile(
        id=str(uuid.uuid4()),
        user_id=user.id,
        license_number=f"DL-RECOVER-{suffix}",
        approval_status="APPROVED",
        rating_average=4.8
    )
    db_session.add(profile)
    await db_session.flush()

    avail = DriverAvailability(
        driver_id=profile.id,
        is_online=True,
        is_busy=False
    )
    db_session.add(avail)

    now = datetime.now(timezone.utc)
    lat, lng = 28.5355, 77.3910  # Noida
    expected_cell = h3_service.get_h3_cell(lat, lng)

    loc = DriverLocation(
        driver_id=profile.id,
        latitude=lat,
        longitude=lng,
        heading=90.0,
        speed=5.0,
        accuracy=4.0,
        recorded_at=now
    )
    db_session.add(loc)
    await db_session.commit()

    live_loc = LiveLocationService(db=db_session)

    # Simulate complete Redis wipeout (Cold Start / Restart)
    await redis_client.delete(f"driver:location:{profile.id}")
    await redis_client.delete(f"driver:last_seen:{profile.id}")
    await redis_client.delete(f"driver:h3_cell:{profile.id}")
    await redis_client.delete(f"h3:drivers:{expected_cell}")

    # Verify Redis is completely cold
    assert await redis_client.get(f"driver:location:{profile.id}") is None
    assert profile.id not in await redis_client.smembers(f"h3:drivers:{expected_cell}")

    # Rebuild from PostgreSQL authoritative tables
    reindexed = await live_loc.rebuild_h3_index_from_db(max_age_minutes=120)
    assert reindexed >= 1

    # Verify reconstructed state in Redis
    recovered_loc = await live_loc.get_driver_live_location(profile.id)
    assert recovered_loc is not None
    assert recovered_loc["latitude"] == round(lat, 6)
    assert recovered_loc["h3_cell"] == expected_cell

    recovered_cell_members = await redis_client.smembers(f"h3:drivers:{expected_cell}")
    assert profile.id in recovered_cell_members


@pytest.mark.asyncio
async def test_offline_cleanup_removes_from_h3_index(db_session):
    """
    Task 2: Verify that when a driver goes offline, they are immediately
    purged from the H3 spatial sets to prevent phantom dispatches.
    """
    driver_id = f"offline_test_{uuid.uuid4().hex[:6]}"
    live_loc = LiveLocationService(db=db_session)

    res = await live_loc.ingest_driver_location(
        driver_id=driver_id,
        latitude=28.6139,
        longitude=77.2090,
        is_online=True,
        is_busy=False
    )
    cell = res["h3_cell"]
    assert driver_id in await redis_client.smembers(f"h3:drivers:{cell}")

    # Clean up (driver goes offline)
    await live_loc.remove_driver_from_h3_index(driver_id)

    assert driver_id not in await redis_client.smembers(f"h3:drivers:{cell}")
    assert await redis_client.get(f"driver:h3_cell:{driver_id}") is None

