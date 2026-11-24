import pytest
import uuid
import io
import hashlib
from datetime import datetime, timezone, timedelta
import httpx
from httpx import ASGITransport
from sqlalchemy import select

from backend.main import app
from backend.domain.models import (
    User, CustomerProfile, DriverProfile, Session, VehicleCategory, FareRule, Ride,
    AudioRecordingConsent, AudioRecordingSession, AudioRecordingSegment, AudioRecordingAuditLog
)
from backend.infrastructure.database.session import async_session_maker
from backend.infrastructure.security.crypto import create_jwt_token
from backend.api.deps import get_current_admin

async def create_test_session(db_session, user: User, role: str) -> tuple[str, dict]:
    token_str = str(uuid.uuid4())
    token_hash = hashlib.sha256(token_str.encode()).hexdigest()
    sess = Session(
        id=str(uuid.uuid4()),
        user_id=user.id,
        token_hash=token_hash,
        is_revoked=False,
        mfa_verified=True,
        expires_at=datetime.now(timezone.utc) + timedelta(days=1)
    )
    db_session.add(sess)
    await db_session.flush()

    jwt_token = create_jwt_token(user_id=user.id, role=role, session_id=sess.id)
    return jwt_token, {"Authorization": f"Bearer {jwt_token}"}

async def setup_test_ride(db_session, u_rider: User, u_driver: User):
    cat = (await db_session.execute(select(VehicleCategory))).scalars().first()
    if not cat:
        cat = VehicleCategory(id=str(uuid.uuid4()), name="E-Rickshaw", code=f"ERIK_{uuid.uuid4().hex[:4]}")
        db_session.add(cat)
        await db_session.flush()

    rule = (await db_session.execute(select(FareRule).where(FareRule.category_id == cat.id))).scalars().first()
    if not rule:
        rule = FareRule(
            id=str(uuid.uuid4()),
            category_id=cat.id,
            city_code="DELHI_NCR",
            base_fare_cents=3000,
            per_km_cents=1000,
            per_minute_cents=100,
            minimum_fare_cents=3000
        )
        db_session.add(rule)
        await db_session.flush()

    cp = (await db_session.execute(select(CustomerProfile).where(CustomerProfile.user_id == u_rider.id))).scalars().first()
    if not cp:
        cp = CustomerProfile(id=str(uuid.uuid4()), user_id=u_rider.id)
        db_session.add(cp)
        await db_session.flush()

    dp = (await db_session.execute(select(DriverProfile).where(DriverProfile.user_id == u_driver.id))).scalars().first()
    if not dp:
        dp = DriverProfile(id=str(uuid.uuid4()), user_id=u_driver.id, license_number=f"DL-{uuid.uuid4().hex[:12]}")
        db_session.add(dp)
        await db_session.flush()

    ride = Ride(
        id=str(uuid.uuid4()),
        customer_id=cp.id,
        assigned_driver_id=dp.id,
        vehicle_category_id=cat.id,
        fare_rule_id=rule.id,
        status="IN_PROGRESS",
        pickup_address="Anand Vihar ISBT",
        destination_address="Connaught Place",
        pickup_latitude=28.6469,
        pickup_longitude=77.3160,
        destination_latitude=28.6304,
        destination_longitude=77.2177,
        start_otp="1234",
        estimated_distance_km=5.0,
        estimated_duration_minutes=15.0,
        estimated_fare_cents=5000
    )
    db_session.add(ride)
    await db_session.commit()
    return ride, cp, dp


@pytest.mark.asyncio
async def test_audio_consent_dual_party_opt_in_gating(db_session):
    """
    DPDP Act 2023 Compliance Gate:
    In-cabin recording MUST NOT activate if only one party consents.
    It requires explicit mutual opt-in from both Rider and Driver.
    """
    transport = ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        # Create rider & driver users
        u_rider = User(
            id=str(uuid.uuid4()),
            phone_number=f"+9198{uuid.uuid4().hex[:8]}",
            full_name="Priya Rider",
            role="customer",
            is_active=True,
            is_verified=True
        )
        u_driver = User(
            id=str(uuid.uuid4()),
            phone_number=f"+9199{uuid.uuid4().hex[:8]}",
            full_name="Ramesh Driver",
            role="driver",
            is_active=True,
            is_verified=True
        )
        db_session.add_all([u_rider, u_driver])
        await db_session.flush()

        ride, cp, dp = await setup_test_ride(db_session, u_rider, u_driver)

        _, rider_headers = await create_test_session(db_session, u_rider, "customer")
        _, driver_headers = await create_test_session(db_session, u_driver, "driver")

        # Step 1: Rider grants consent
        res_rc = await client.post(
            f"/api/v1/rides/{ride.id}/audio-consent",
            headers=rider_headers,
            json={
                "consented": True,
                "consent_purpose": "IN_CABIN_SAFETY_DPDP_COMPLIANT"
            }
        )
        assert res_rc.status_code == 200
        data_rc = res_rc.json()
        assert data_rc["consent_status"] == "GRANTED"
        # Since driver hasn't consented yet, dual consent is NOT achieved
        assert data_rc["dual_consent_achieved"] is False

        # Status check via GET
        res_st1 = await client.get(f"/api/v1/rides/{ride.id}/audio-consent", headers=rider_headers)
        assert res_st1.status_code == 200
        st1 = res_st1.json()
        assert st1["passenger_consent"] is True
        assert st1["driver_consent"] is False
        assert st1["dual_consent_achieved"] is False

        # Step 2: Driver grants consent
        res_dc = await client.post(
            f"/api/v1/rides/{ride.id}/audio-consent",
            headers=driver_headers,
            json={
                "consented": True,
                "consent_purpose": "IN_CABIN_SAFETY_DPDP_COMPLIANT"
            }
        )
        assert res_dc.status_code == 200
        data_dc = res_dc.json()
        assert data_dc["consent_status"] == "GRANTED"
        assert data_dc["dual_consent_achieved"] is True

        # Now dual consent is complete! Authorize session
        res_auth = await client.post(f"/api/v1/rides/{ride.id}/audio-recording/authorize", headers=rider_headers)
        assert res_auth.status_code == 200
        auth_data = res_auth.json()
        assert auth_data["authorized"] is True
        assert "session_id" in auth_data
        session_id = auth_data["session_id"]

        # Verify session status is AUTHORIZED
        res_sess = await client.get(f"/api/v1/audio-recordings/{session_id}/status", headers=rider_headers)
        assert res_sess.status_code == 200
        assert res_sess.json()["status"] == "AUTHORIZED"


@pytest.mark.asyncio
async def test_audio_consent_withdrawal_stops_recording(db_session):
    """
    Rider or driver withdrawing consent must immediately stop recording and set session to WITHDRAWN.
    """
    transport = ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        u_rider = User(
            id=str(uuid.uuid4()),
            phone_number=f"+9198{uuid.uuid4().hex[:8]}",
            full_name="Withdrawal Test Rider",
            role="customer",
            is_active=True
        )
        u_driver = User(
            id=str(uuid.uuid4()),
            phone_number=f"+9199{uuid.uuid4().hex[:8]}",
            full_name="Withdrawal Test Driver",
            role="driver",
            is_active=True
        )
        db_session.add_all([u_rider, u_driver])
        await db_session.flush()

        ride, cp, dp = await setup_test_ride(db_session, u_rider, u_driver)

        _, rider_headers = await create_test_session(db_session, u_rider, "customer")
        _, driver_headers = await create_test_session(db_session, u_driver, "driver")

        # Both grant consent
        await client.post(f"/api/v1/rides/{ride.id}/audio-consent", headers=rider_headers, json={"consented": True})
        await client.post(f"/api/v1/rides/{ride.id}/audio-consent", headers=driver_headers, json={"consented": True})

        auth_res = await client.post(f"/api/v1/rides/{ride.id}/audio-recording/authorize", headers=rider_headers)
        session_id = auth_res.json()["session_id"]

        # Start recording
        await client.post(f"/api/v1/audio-recordings/{session_id}/start", headers=rider_headers)

        # Rider withdraws consent mid-trip
        res_with = await client.post(
            f"/api/v1/rides/{ride.id}/audio-consent/withdraw",
            headers=rider_headers,
            json={"reason": "Passenger withdrew consent due to privacy request"}
        )
        assert res_with.status_code == 200

        # Session should now be WITHDRAWN / STOPPED
        res_chk = await client.get(f"/api/v1/audio-recordings/{session_id}/status", headers=rider_headers)
        assert res_chk.status_code == 200
        assert res_chk.json()["status"] in ["WITHDRAWN", "STOPPED", "CANCELLED"]


@pytest.mark.asyncio
async def test_audio_recording_session_lifecycle_and_segment_upload(db_session):
    """
    Test start, heartbeat, encrypted segment upload with metadata, and stop.
    """
    transport = ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        u_rider = User(id=str(uuid.uuid4()), phone_number=f"+9198{uuid.uuid4().hex[:8]}", role="customer", is_active=True)
        u_driver = User(id=str(uuid.uuid4()), phone_number=f"+9199{uuid.uuid4().hex[:8]}", role="driver", is_active=True)
        db_session.add_all([u_rider, u_driver])
        await db_session.flush()

        ride, cp, dp = await setup_test_ride(db_session, u_rider, u_driver)

        _, rider_headers = await create_test_session(db_session, u_rider, "customer")
        _, driver_headers = await create_test_session(db_session, u_driver, "driver")

        # Dual consent
        await client.post(f"/api/v1/rides/{ride.id}/audio-consent", headers=rider_headers, json={"consented": True})
        await client.post(f"/api/v1/rides/{ride.id}/audio-consent", headers=driver_headers, json={"consented": True})

        auth_res = await client.post(f"/api/v1/rides/{ride.id}/audio-recording/authorize", headers=rider_headers)
        session_id = auth_res.json()["session_id"]

        # 1. Start recording
        res_start = await client.post(f"/api/v1/audio-recordings/{session_id}/start", headers=rider_headers)
        assert res_start.status_code == 200
        assert res_start.json()["status"] == "RECORDING"

        # 2. Heartbeat
        res_hb = await client.post(f"/api/v1/audio-recordings/{session_id}/heartbeat", headers=rider_headers)
        assert res_hb.status_code == 200
        assert res_hb.json()["alive"] is True

        # 3. Upload encrypted audio segment
        fake_audio_bytes = b"VAISENTRA_ENCRYPTED_GCM_CIPHERTEXT_1234567890"
        files = {
            "file": ("segment_1.enc", io.BytesIO(fake_audio_bytes), "application/octet-stream")
        }
        data = {
            "sequence": "1",
            "duration_seconds": "30.0",
            "encryption_nonce": "0102030405060708090a0b0c",
            "encryption_tag": "0102030405060708090a0b0c0d0e0f10"
        }

        res_seg = await client.post(
            f"/api/v1/audio-recordings/{session_id}/segments",
            headers=rider_headers,
            data=data,
            files=files
        )
        assert res_seg.status_code == 200
        seg_data = res_seg.json()
        assert seg_data["sequence"] == 1
        assert "storage_key" in seg_data
        segment_id = seg_data["segment_id"]

        # 4. Stop recording
        res_stop = await client.post(
            f"/api/v1/audio-recordings/{session_id}/stop",
            headers=rider_headers,
            json={"reason": "Trip completed successfully"}
        )
        assert res_stop.status_code == 200
        assert res_stop.json()["status"] in ["COMPLETED", "STOPPED"]

        # Verify DB segment using fresh session
        async with async_session_maker() as verify_db:
            stmt = select(AudioRecordingSegment).where(AudioRecordingSegment.id == segment_id)
            seg_row = (await verify_db.execute(stmt)).scalar_one_or_none()
            assert seg_row is not None
            assert seg_row.duration_seconds == 30.0


@pytest.mark.asyncio
async def test_admin_playback_authorization_and_audit_trail(db_session):
    """
    Forensic playback requires Admin authentication, statutory justification (min 10 chars),
    and logs an immutable entry in AudioRecordingAuditLog.
    """
    transport = ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        u_admin = User(
            id=str(uuid.uuid4()),
            phone_number=f"+9197{uuid.uuid4().hex[:8]}",
            full_name="Chief Safety Officer",
            role="admin",
            is_active=True,
            mfa_enabled=True
        )
        u_rider = User(
            id=str(uuid.uuid4()),
            phone_number=f"+9198{uuid.uuid4().hex[:8]}",
            full_name="Audited Rider",
            role="customer",
            is_active=True
        )
        u_driver = User(
            id=str(uuid.uuid4()),
            phone_number=f"+9199{uuid.uuid4().hex[:8]}",
            full_name="Audited Driver",
            role="driver",
            is_active=True
        )
        db_session.add_all([u_admin, u_rider, u_driver])
        await db_session.flush()

        _, admin_headers = await create_test_session(db_session, u_admin, "admin")
        _, rider_headers = await create_test_session(db_session, u_rider, "customer")

        ride, cp, dp = await setup_test_ride(db_session, u_rider, u_driver)

        sess_id = f"sess_{uuid.uuid4().hex[:16]}"
        sess = AudioRecordingSession(
            id=str(uuid.uuid4()),
            session_id=sess_id,
            ride_id=ride.id,
            rider_user_id=u_rider.id,
            driver_user_id=u_driver.id,
            recording_status="COMPLETED",
            authorization_status="AUTHORIZED"
        )
        db_session.add(sess)
        await db_session.flush()

        seg = AudioRecordingSegment(
            id=str(uuid.uuid4()),
            session_id=sess_id,
            ride_id=ride.id,
            segment_sequence=1,
            storage_bucket="ride-audio-recordings",
            storage_object_path=f"recordings/{sess_id}/seg_1.enc",
            encryption_nonce="0102030405060708090a0b0c",
            encryption_tag="0102030405060708090a0b0c0d0e0f10",
            duration_seconds=45.0,
            file_size_bytes=1024,
            sha256_hash="abc123hash",
            retention_expiry_at=datetime.now(timezone.utc) + timedelta(days=7)
        )
        db_session.add(seg)
        await db_session.commit()

        # 1. Non-admin cannot list audio recordings
        res_forbidden = await client.get("/api/v1/admin/audio-recordings", headers=rider_headers)
        assert res_forbidden.status_code == 403

        # Override admin dependency for admin endpoints
        app.dependency_overrides[get_current_admin] = lambda: u_admin
        try:
            # 2. Admin without minimum 10 char reason is rejected
            res_short = await client.post(
                f"/api/v1/admin/audio-recordings/{sess.session_id}/playback",
                headers=admin_headers,
                json={"segment_id": seg.id, "justification_reason": "short"}
            )
            assert res_short.status_code == 422

            # 3. Valid statutory playback authorization
            res_ok = await client.post(
                f"/api/v1/admin/audio-recordings/{sess.session_id}/playback",
                headers=admin_headers,
                json={
                    "segment_id": seg.id,
                    "justification_reason": "FIR #1029/2026 Passenger safety harassment complaint investigation"
                }
            )
            assert res_ok.status_code == 200
            pb_data = res_ok.json()
            assert pb_data["authorized"] is True
            assert "playback_url" in pb_data
            playback_url = pb_data["playback_url"]
            assert "sig=" in playback_url

            # 4. Verify audit log was recorded
            audit_stmt = select(AudioRecordingAuditLog).where(
                AudioRecordingAuditLog.session_id == sess.session_id,
                AudioRecordingAuditLog.action == "ADMIN_PLAYBACK_AUTHORIZED"
            )
            audit_row = (await db_session.execute(audit_stmt)).scalar_one_or_none()
            assert audit_row is not None
            assert audit_row.actor_id == u_admin.id
            assert "FIR #1029/2026" in audit_row.access_reason
        finally:
            app.dependency_overrides.pop(get_current_admin, None)


@pytest.mark.asyncio
async def test_audio_recording_retention_purge(db_session):
    """
    Recordings older than 7 days must be purged, while recent recordings are kept.
    """
    transport = ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        u_admin = User(
            id=str(uuid.uuid4()),
            phone_number=f"+9197{uuid.uuid4().hex[:8]}",
            full_name="Purge Admin",
            role="admin",
            is_active=True,
            mfa_enabled=True
        )
        u_rider = User(id=str(uuid.uuid4()), phone_number=f"+9198{uuid.uuid4().hex[:8]}", role="customer", is_active=True)
        u_driver = User(id=str(uuid.uuid4()), phone_number=f"+9199{uuid.uuid4().hex[:8]}", role="driver", is_active=True)
        db_session.add_all([u_admin, u_rider, u_driver])
        await db_session.flush()

        _, admin_headers = await create_test_session(db_session, u_admin, "admin")
        ride, cp, dp = await setup_test_ride(db_session, u_rider, u_driver)

        # Session 1: Expired (retention_expiry in past)
        s_old_id = f"sess_old_{uuid.uuid4().hex[:12]}"
        s_old = AudioRecordingSession(
            id=str(uuid.uuid4()),
            session_id=s_old_id,
            ride_id=ride.id,
            rider_user_id=u_rider.id,
            driver_user_id=u_driver.id,
            recording_status="COMPLETED"
        )
        # Session 2: Active retention (expires in future)
        s_recent_id = f"sess_rec_{uuid.uuid4().hex[:12]}"
        s_recent = AudioRecordingSession(
            id=str(uuid.uuid4()),
            session_id=s_recent_id,
            ride_id=ride.id,
            rider_user_id=u_rider.id,
            driver_user_id=u_driver.id,
            recording_status="COMPLETED"
        )
        db_session.add_all([s_old, s_recent])
        await db_session.flush()

        seg_old = AudioRecordingSegment(
            id=str(uuid.uuid4()),
            session_id=s_old_id,
            ride_id=ride.id,
            segment_sequence=1,
            storage_bucket="ride-audio-recordings",
            storage_object_path=f"recordings/{s_old_id}/seg_1.enc",
            encryption_nonce="0102030405060708090a0b0c",
            encryption_tag="0102030405060708090a0b0c0d0e0f10",
            duration_seconds=30.0,
            file_size_bytes=512,
            sha256_hash="hashold",
            retention_expiry_at=datetime.now(timezone.utc) - timedelta(days=1),
            is_purged=False
        )
        seg_rec = AudioRecordingSegment(
            id=str(uuid.uuid4()),
            session_id=s_recent_id,
            ride_id=ride.id,
            segment_sequence=1,
            storage_bucket="ride-audio-recordings",
            storage_object_path=f"recordings/{s_recent_id}/seg_1.enc",
            encryption_nonce="0102030405060708090a0b0c",
            encryption_tag="0102030405060708090a0b0c0d0e0f10",
            duration_seconds=30.0,
            file_size_bytes=512,
            sha256_hash="hashrec",
            retention_expiry_at=datetime.now(timezone.utc) + timedelta(days=6),
            is_purged=False
        )
        db_session.add_all([seg_old, seg_rec])
        await db_session.commit()

        app.dependency_overrides[get_current_admin] = lambda: u_admin
        try:
            res_purge = await client.post("/api/v1/admin/audio-recordings/purge", headers=admin_headers)
            assert res_purge.status_code == 200
            data_purge = res_purge.json()
            assert data_purge["purged_segments_count"] >= 1

            # Check DB: seg_old is purged using fresh session
            async with async_session_maker() as verify_db:
                stmt_old = select(AudioRecordingSegment).where(AudioRecordingSegment.id == seg_old.id)
                row_old = (await verify_db.execute(stmt_old)).scalar_one_or_none()
                assert row_old.is_purged is True

                # seg_rec is not purged
                stmt_rec = select(AudioRecordingSegment).where(AudioRecordingSegment.id == seg_rec.id)
                row_rec = (await verify_db.execute(stmt_rec)).scalar_one_or_none()
                assert row_rec.is_purged is False
        finally:
            app.dependency_overrides.pop(get_current_admin, None)
