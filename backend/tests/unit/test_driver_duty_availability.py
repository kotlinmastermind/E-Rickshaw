import pytest
import uuid
from datetime import datetime, timezone
from httpx import AsyncClient, ASGITransport
from sqlalchemy import select

from backend.main import app
from backend.domain.models import User, DriverProfile, DriverAvailability, Ride, VehicleCategory, Session
from backend.infrastructure.security.crypto import create_jwt_token, hash_secret
from backend.application.services.driver_service import DriverService

@pytest.mark.asyncio
async def test_driver_availability_full_lifecycle(db_session):
    """
    Comprehensive tests for Driver Duty Availability:
    1. GET /api/v1/drivers/availability returns authoritative initial state (is_online=False)
    2. Unapproved driver rejected from going ONLINE (403 Forbidden)
    3. Approved driver transitions to ONLINE (200 OK)
    4. Active ride prevents going OFFLINE (409 Conflict)
    5. Once ride is COMPLETED, going OFFLINE succeeds (200 OK)
    6. GET /api/v1/drivers/availability reflects the updated authoritative state
    """
    suffix = uuid.uuid4().hex[:6]

    # Create Driver User
    driver_user = User(
        phone_number=f"+9198755{suffix[:5]}",
        email=f"pilot_{suffix}@vaisentra.com",
        full_name=f"Verified Pilot {suffix}",
        role="driver",
        is_active=True,
        is_verified=True
    )
    db_session.add(driver_user)
    await db_session.flush()

    # Create Driver Profile (Initially PENDING)
    driver_profile = DriverProfile(
        user_id=driver_user.id,
        license_number=f"DL-TEST-{suffix.upper()}",
        approval_status="PENDING"
    )
    db_session.add(driver_profile)
    await db_session.flush()

    # Create Session
    session_id = str(uuid.uuid4())
    raw_token = str(uuid.uuid4())
    token_obj = Session(
        id=session_id,
        user_id=driver_user.id,
        token_hash=hash_secret(raw_token),
        is_revoked=False,
        expires_at=datetime.now(timezone.utc)
    )
    db_session.add(token_obj)
    await db_session.commit()

    token = create_jwt_token(user_id=driver_user.id, role="driver", session_id=session_id)
    headers = {"Authorization": f"Bearer {token}"}

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:

        # 1. GET /api/v1/drivers/availability before any toggle
        res_get = await client.get("/api/v1/drivers/availability", headers=headers)
        assert res_get.status_code == 200, f"GET availability failed: {res_get.text}"
        data_get = res_get.json()
        assert data_get["driver_id"] == driver_profile.id
        assert data_get["is_online"] is False
        assert data_get["approval_status"] == "PENDING"
        assert data_get["active_ride_id"] is None

        # 2. PENDING driver attempts to go ONLINE -> 403 Forbidden
        res_pending_online = await client.post(
            "/api/v1/drivers/availability",
            headers=headers,
            json={"is_online": True}
        )
        assert res_pending_online.status_code == 403
        assert "must be approved before going online" in res_pending_online.json()["detail"].lower()

        # Approve the driver in DB
        driver_profile.approval_status = "APPROVED"
        await db_session.commit()

        # 3. Approved driver goes ONLINE -> 200 OK
        res_online = await client.post(
            "/api/v1/drivers/availability",
            headers=headers,
            json={"is_online": True}
        )
        assert res_online.status_code == 200, f"Online toggle failed: {res_online.text}"
        data_online = res_online.json()
        assert data_online["is_online"] is True
        assert data_online["approval_status"] == "APPROVED"

        # Verify DB persisted state
        avail_db = (await db_session.execute(
            select(DriverAvailability).where(DriverAvailability.driver_id == driver_profile.id)
        )).scalar_one()
        assert avail_db.is_online is True

        # 4. Create an active IN_PROGRESS ride for this driver
        from backend.domain.models import CustomerProfile, FareRule

        customer_user = User(
            phone_number=f"+9198111{suffix[:5]}",
            email=f"rider_{suffix}@vaisentra.com",
            full_name=f"Commuter {suffix}",
            role="customer",
            is_active=True
        )
        db_session.add(customer_user)
        await db_session.flush()

        customer_profile = CustomerProfile(user_id=customer_user.id)
        db_session.add(customer_profile)
        await db_session.flush()

        cat = VehicleCategory(
            id=str(uuid.uuid4()),
            code=f"CAT_{suffix}",
            name=f"Category {suffix}"
        )
        db_session.add(cat)
        await db_session.flush()

        fare_rule = FareRule(
            category_id=cat.id,
            version=1,
            base_fare_cents=2000,
            per_km_cents=1000,
            per_minute_cents=100,
            minimum_fare_cents=2000,
            cancellation_fee_cents=1000,
            surge_multiplier=1.0,
            is_active=True
        )
        db_session.add(fare_rule)
        await db_session.flush()

        active_ride = Ride(
            customer_id=customer_profile.id,
            assigned_driver_id=driver_profile.id,
            vehicle_category_id=cat.id,
            fare_rule_id=fare_rule.id,
            pickup_address="Metro Station Gate 1",
            pickup_latitude=28.6139,
            pickup_longitude=77.2090,
            destination_address="City Mall",
            destination_latitude=28.6250,
            destination_longitude=77.2150,
            estimated_distance_km=3.5,
            estimated_duration_minutes=12.0,
            estimated_fare_cents=2500,
            start_otp="1234",
            status="IN_PROGRESS"
        )
        db_session.add(active_ride)
        await db_session.commit()

        # 5. Attempt to toggle OFFLINE while on active ride -> 409 Conflict
        res_offline_blocked = await client.post(
            "/api/v1/drivers/availability",
            headers=headers,
            json={"is_online": False}
        )
        assert res_offline_blocked.status_code == 409, f"Expected 409 Conflict, got: {res_offline_blocked.status_code}"
        assert "cannot go offline while on an active ride" in res_offline_blocked.json()["detail"].lower()

        # Verify driver is STILL online in DB (not corrupted by rejected request)
        avail_db_after_block = (await db_session.execute(
            select(DriverAvailability).where(DriverAvailability.driver_id == driver_profile.id)
        )).scalar_one()
        assert avail_db_after_block.is_online is True

        # Complete the ride
        active_ride.status = "COMPLETED"
        await db_session.commit()

        # 6. Now toggle OFFLINE -> 200 OK
        res_offline_success = await client.post(
            "/api/v1/drivers/availability",
            headers=headers,
            json={"is_online": False}
        )
        assert res_offline_success.status_code == 200, f"Offline toggle failed: {res_offline_success.text}"
        data_offline = res_offline_success.json()
        assert data_offline["is_online"] is False

        # Verify DB updated
        avail_db_final = (await db_session.execute(
            select(DriverAvailability)
            .where(DriverAvailability.driver_id == driver_profile.id)
            .execution_options(populate_existing=True)
        )).scalar_one()
        assert avail_db_final.is_online is False

        # 7. GET /api/v1/drivers/availability reflects final OFFLINE state
        res_final_get = await client.get("/api/v1/drivers/availability", headers=headers)
        assert res_final_get.status_code == 200
        assert res_final_get.json()["is_online"] is False


@pytest.mark.asyncio
async def test_admin_set_driver_duty_endpoint(db_session):
    """
    Verify that an administrator can operational override and toggle driver duty
    via PUT /api/v1/admin/drivers/{driver_id}/duty with audit logging.
    """
    suffix = uuid.uuid4().hex[:6]

    # Create Admin User
    admin_user = User(
        phone_number=f"+9199881{suffix[:5]}",
        email=f"admin_{suffix}@vaisentra.com",
        full_name=f"Admin {suffix}",
        role="admin",
        is_active=True,
        is_verified=True
    )
    # Create Driver User
    driver_user = User(
        phone_number=f"+9198771{suffix[:5]}",
        email=f"driver_duty_{suffix}@vaisentra.com",
        full_name=f"Chauffeur {suffix}",
        role="driver",
        is_active=True,
        is_verified=True
    )
    db_session.add_all([admin_user, driver_user])
    await db_session.flush()

    driver_profile = DriverProfile(
        user_id=driver_user.id,
        license_number=f"DL-ADM-{suffix.upper()}",
        approval_status="APPROVED"
    )
    db_session.add(driver_profile)
    await db_session.flush()

    # Create Admin Session
    admin_sess_id = str(uuid.uuid4())
    admin_token_obj = Session(
        id=admin_sess_id,
        user_id=admin_user.id,
        token_hash=hash_secret(str(uuid.uuid4())),
        is_revoked=False,
        expires_at=datetime.now(timezone.utc)
    )
    db_session.add(admin_token_obj)
    await db_session.commit()

    admin_token = create_jwt_token(user_id=admin_user.id, role="admin", session_id=admin_sess_id)
    admin_headers = {"Authorization": f"Bearer {admin_token}"}

    transport = ASGITransport(app=app)
    from backend.api.deps import get_current_admin
    app.dependency_overrides[get_current_admin] = lambda: admin_user
    try:
        async with AsyncClient(transport=transport, base_url="http://test") as client:
            # 1. Admin sets driver online
            res_online = await client.put(
                f"/api/v1/admin/drivers/{driver_profile.id}/duty",
                headers=admin_headers,
                json={"is_online": True, "reason": "Admin desk dispatch activation"}
            )
            assert res_online.status_code == 200, res_online.text
            assert res_online.json()["is_online"] is True

            # 2. Admin forces driver offline
            res_offline = await client.put(
                f"/api/v1/admin/drivers/{driver_profile.id}/duty",
                headers=admin_headers,
                json={"is_online": False, "reason": "Admin shift termination"}
            )
            assert res_offline.status_code == 200, res_offline.text
            assert res_offline.json()["is_online"] is False
    finally:
        app.dependency_overrides.pop(get_current_admin, None)
