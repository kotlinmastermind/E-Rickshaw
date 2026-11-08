import pytest
import uuid
from httpx import AsyncClient, ASGITransport
from sqlalchemy import select
from backend.main import app
from backend.domain.models import User, DriverProfile, DriverDocument, DriverAvailability
from backend.infrastructure.security.crypto import (
    hash_password, create_jwt_token, verify_otp_constant_time,
    generate_secure_otp, verify_totp, generate_mfa_secret
)
from backend.application.services.driver_service import DriverService
from backend.application.services.ride_service import RideService
from backend.application.services.driver_trip_operations_service import DriverTripOperationsService
from fastapi import HTTPException

@pytest.mark.asyncio
async def test_driver_signup_defaults_to_pending_and_unverified(db_session):
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        unique_suffix = uuid.uuid4().int % 100000000
        driver_phone = f"+9198{str(unique_suffix)[-8:]}"
        driver_email = f"pending_driver_{unique_suffix}@erickshaw.in"

        res = await ac.post("/api/v1/auth/signup/driver", json={
            "full_name": "Applicant Pilot",
            "phone_number": driver_phone,
            "email": driver_email,
            "password": "StrongPassword123!",
            "vehicle_name": "Mahindra Treo",
            "vehicle_no": f"DL-01-PT-{str(unique_suffix)[-4:]}"
        })
        assert res.status_code == 200, res.text
        data = res.json()
        driver_user_id = data["user"]["id"]

        # Verify in DB: User flags must be unverified
        user_res = await db_session.execute(select(User).where(User.id == driver_user_id))
        user = user_res.scalar_one_or_none()
        assert user is not None
        assert user.is_verified is False
        assert user.email_verified is False

        # Verify in DB: DriverProfile must be PENDING
        prof_res = await db_session.execute(select(DriverProfile).where(DriverProfile.user_id == driver_user_id))
        prof = prof_res.scalar_one_or_none()
        assert prof is not None
        assert prof.approval_status == "PENDING"
        assert prof.onboarding_step == "DOCUMENTS_PENDING"


@pytest.mark.asyncio
async def test_customer_signup_defaults_to_unverified(db_session):
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        unique_suffix = uuid.uuid4().int % 100000000
        cust_phone = f"+9197{str(unique_suffix)[-8:]}"
        cust_email = f"new_customer_{unique_suffix}@erickshaw.in"

        res = await ac.post("/api/v1/auth/signup/customer", json={
            "full_name": "New Customer",
            "phone_number": cust_phone,
            "email": cust_email,
            "password": "StrongPassword123!"
        })
        assert res.status_code == 200, res.text
        data = res.json()
        cust_user_id = data["user"]["id"]

        user_res = await db_session.execute(select(User).where(User.id == cust_user_id))
        user = user_res.scalar_one_or_none()
        assert user is not None
        assert user.is_verified is False
        assert user.email_verified is False


@pytest.mark.asyncio
async def test_pending_driver_cannot_toggle_online(db_session):
    unique_suffix = uuid.uuid4().int % 100000000
    user = User(
        phone_number=f"+9196{str(unique_suffix)[-8:]}",
        email=f"pending_online_{unique_suffix}@erickshaw.in",
        role="driver",
        is_active=True,
        is_verified=False
    )
    db_session.add(user)
    await db_session.flush()

    profile = DriverProfile(
        user_id=user.id,
        license_number=f"DL-PENDING-{str(unique_suffix)[-6:]}",
        approval_status="PENDING"
    )
    db_session.add(profile)
    await db_session.commit()

    service = DriverService(db_session)
    with pytest.raises(HTTPException) as exc_info:
        await service.toggle_availability(user.id, is_online=True)
    assert exc_info.value.status_code == 403
    assert "approved before going online" in exc_info.value.detail.lower() or "not active" in exc_info.value.detail.lower()


@pytest.mark.asyncio
async def test_pending_driver_cannot_accept_ride_or_get_offers(db_session):
    unique_suffix = uuid.uuid4().int % 100000000
    user = User(
        phone_number=f"+9195{str(unique_suffix)[-8:]}",
        email=f"pending_ops_{unique_suffix}@erickshaw.in",
        role="driver",
        is_active=True
    )
    db_session.add(user)
    await db_session.flush()

    profile = DriverProfile(
        user_id=user.id,
        license_number=f"DL-OPS-{str(unique_suffix)[-6:]}",
        approval_status="PENDING"
    )
    db_session.add(profile)
    await db_session.commit()

    ride_service = RideService(db_session)
    with pytest.raises(HTTPException) as exc_info:
        await ride_service.accept_ride_atomic(driver_user_id=user.id, ride_id="some-ride-id")
    assert exc_info.value.status_code == 403

    trip_service = DriverTripOperationsService(db_session)
    with pytest.raises(HTTPException) as exc_info2:
        await trip_service.get_offers(user_id=user.id)
    assert exc_info2.value.status_code == 403


@pytest.mark.asyncio
async def test_admin_approval_lifecycle_and_non_admin_rejection(db_session):
    unique_suffix = uuid.uuid4().int % 100000000
    # Create driver
    driver_user = User(
        phone_number=f"+9194{str(unique_suffix)[-8:]}",
        email=f"candidate_driver_{unique_suffix}@erickshaw.in",
        role="driver",
        is_active=True
    )
    db_session.add(driver_user)
    await db_session.flush()

    driver_profile = DriverProfile(
        user_id=driver_user.id,
        license_number=f"DL-CANDIDATE-{str(unique_suffix)[-6:]}",
        approval_status="PENDING"
    )
    db_session.add(driver_profile)
    await db_session.flush()

    # Add verified document required for admin approval
    doc = DriverDocument(
        driver_id=driver_profile.id,
        document_type="DRIVING_LICENSE",
        storage_path="/docs/dl.pdf",
        file_name="dl.pdf",
        mime_type="application/pdf",
        file_size_bytes=1024,
        file_hash_sha256="abc123hash",
        verification_status="VERIFIED"
    )
    db_session.add(doc)

    # Create admin user and session
    from datetime import datetime, timezone, timedelta
    from backend.domain.models import Session
    from backend.infrastructure.security.crypto import hash_secret

    admin_user = User(
        phone_number=f"+9193{str(unique_suffix)[-8:]}",
        email=f"admin_approver_{unique_suffix}@erickshaw.in",
        role="admin",
        is_active=True,
        mfa_enabled=True
    )
    db_session.add(admin_user)
    await db_session.flush()

    admin_sess = Session(
        user_id=admin_user.id,
        token_hash=hash_secret(f"{admin_user.id}:admin_token:{generate_secure_otp(12)}"),
        is_revoked=False,
        mfa_verified=True,
        expires_at=datetime.now(timezone.utc) + timedelta(minutes=60)
    )
    db_session.add(admin_sess)
    await db_session.commit()

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        admin_token = create_jwt_token(user_id=admin_user.id, role="admin", session_id=admin_sess.id, token_type="access")
        cust_token = create_jwt_token(user_id=driver_user.id, role="driver", session_id="driver-sess-1", token_type="access")

        # Non-admin attempt must be rejected (no admin role/override)
        res_unauth = await ac.put(
            f"/api/v1/admin/drivers/{driver_profile.id}/approval",
            json={"approval_status": "APPROVED"},
            headers={"Authorization": f"Bearer {cust_token}"}
        )
        assert res_unauth.status_code in (401, 403)

        # Authorized Admin approval must succeed
        from backend.api.deps import get_current_admin
        app.dependency_overrides[get_current_admin] = lambda: admin_user
        try:
            res_auth = await ac.put(
                f"/api/v1/admin/drivers/{driver_profile.id}/approval",
                json={"approval_status": "APPROVED"},
                headers={"Authorization": f"Bearer {admin_token}"}
            )
            assert res_auth.status_code == 200, res_auth.text
            assert res_auth.json()["approval_status"] == "APPROVED"
        finally:
            app.dependency_overrides.pop(get_current_admin, None)

        # Verify DB status updated
        await db_session.refresh(driver_profile)
        assert driver_profile.approval_status == "APPROVED"


@pytest.mark.asyncio
async def test_existing_approved_driver_retains_status_and_goes_online(db_session):
    unique_suffix = uuid.uuid4().int % 100000000
    user = User(
        phone_number=f"+9192{str(unique_suffix)[-8:]}",
        email=f"approved_driver_{unique_suffix}@erickshaw.in",
        role="driver",
        is_active=True
    )
    db_session.add(user)
    await db_session.flush()

    profile = DriverProfile(
        user_id=user.id,
        license_number=f"DL-APPROVED-{str(unique_suffix)[-6:]}",
        approval_status="APPROVED"
    )
    db_session.add(profile)
    await db_session.flush()
    avail = DriverAvailability(driver_id=profile.id, is_online=False, is_busy=False)
    db_session.add(avail)
    await db_session.commit()

    service = DriverService(db_session)
    result = await service.toggle_availability(user.id, is_online=True)
    assert result["is_online"] is True


@pytest.mark.asyncio
async def test_password_policy_minimum_12_characters(db_session):
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        unique_suffix = uuid.uuid4().int % 100000000

        # Customer signup with short password (< 12 chars) rejected
        res_short = await ac.post("/api/v1/auth/signup/customer", json={
            "full_name": "Short Password User",
            "email": f"short_{unique_suffix}@erickshaw.in",
            "phone_number": f"+9191{str(unique_suffix)[-8:]}",
            "password": "Short123!" # 9 chars
        })
        assert res_short.status_code in (400, 422)

        # Driver signup with short password (< 12 chars) rejected
        res_short_drv = await ac.post("/api/v1/auth/signup/driver", json={
            "full_name": "Short Driver",
            "email": f"short_drv_{unique_suffix}@erickshaw.in",
            "phone_number": f"+9190{str(unique_suffix)[-8:]}",
            "password": "Pass4" # 5 chars
        })
        assert res_short_drv.status_code in (400, 422)

        # Valid 12+ character password accepted
        res_ok = await ac.post("/api/v1/auth/signup/customer", json={
            "full_name": "Valid Password User",
            "email": f"valid_pw_{unique_suffix}@erickshaw.in",
            "phone_number": f"+9189{str(unique_suffix)[-8:]}",
            "password": "ValidPass1234!" # 14 chars
        })
        assert res_ok.status_code == 200, res_ok.text


@pytest.mark.asyncio
async def test_existing_account_with_legacy_hash_can_still_authenticate(db_session):
    unique_suffix = uuid.uuid4().int % 100000000
    legacy_password = "old4"  # 4 characters legacy password
    user = User(
        phone_number=f"+9188{str(unique_suffix)[-8:]}",
        email=f"legacy_user_{unique_suffix}@erickshaw.in",
        full_name="Legacy Member",
        password_hash=hash_password(legacy_password),
        role="customer",
        is_active=True
    )
    db_session.add(user)
    await db_session.commit()

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        res = await ac.post("/api/v1/auth/login", json={
            "identifier": user.email,
            "password": legacy_password
        })
        assert res.status_code == 200, res.text
        assert "access_token" in res.json()


@pytest.mark.asyncio
async def test_password_recovery_endpoints_fail_safely_and_honestly(db_session):
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        # Request recovery: must return 503 with honest explanation
        res_req = await ac.post("/api/v1/auth/recovery/request", json={
            "identifier": "commuter@example.com"
        })
        assert res_req.status_code == 503
        assert "unavailable" in res_req.json()["detail"].lower()
        assert "support@vaisentra.com" in res_req.json()["detail"]

        # Reset password: must return 503 with honest explanation
        res_reset = await ac.post("/api/v1/auth/recovery/reset-password", json={
            "user_id": "usr-123",
            "recovery_code": "123456",
            "new_password": "NewCompliantPassword123!"
        })
        assert res_reset.status_code == 503
        assert "unavailable" in res_reset.json()["detail"].lower()


@pytest.mark.asyncio
async def test_obsolete_fcm_endpoints_are_unregistered(db_session):
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        # 1. Customer device-token
        r1 = await ac.post("/api/v1/customers/device-token", json={"fcm_token": "token123"})
        assert r1.status_code in (404, 405)

        # 2. Driver device-token
        r2 = await ac.post("/api/v1/drivers/device-token", json={"fcm_token": "token123"})
        assert r2.status_code in (404, 405)

        # 3. Notification device-token DELETE
        r3 = await ac.delete("/api/v1/notifications/device-token")
        assert r3.status_code in (404, 405)


def test_ride_boarding_pin_and_totp_mfa_systems_remain_functional():
    # Verify secure passenger boarding PIN verification logic
    pin = "4819"
    assert verify_otp_constant_time(pin, "4819") is True
    assert verify_otp_constant_time(pin, "0000") is False

    # Verify RFC 6238 TOTP verification logic
    secret = generate_mfa_secret()
    assert len(secret) >= 20
    # Dummy code verification fails safely
    assert verify_totp(secret, "000000") is False
