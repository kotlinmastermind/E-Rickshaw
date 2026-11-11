import pytest
import uuid
from datetime import datetime, timezone
from httpx import AsyncClient, ASGITransport

from backend.main import app
from backend.domain.models import User, DriverProfile, Session
from backend.infrastructure.security.crypto import create_jwt_token, hash_secret

@pytest.mark.asyncio
async def test_driver_profile_returns_canonical_and_aliased_ids(db_session):
    """
    ON-02 Contract Verification:
    Ensures GET /api/v1/drivers/profile returns 'id', 'driver_id', and 'user_id'
    with identical values for 'id' and 'driver_id', matching DriverProfile.id.
    """
    suffix = uuid.uuid4().hex[:6]

    driver_user = User(
        phone_number=f"+9198765{suffix[:5]}",
        email=f"profile_test_{suffix}@vaisentra.com",
        full_name=f"Chauffeur {suffix}",
        role="driver",
        is_active=True,
        is_verified=True
    )
    db_session.add(driver_user)
    await db_session.flush()

    driver_profile = DriverProfile(
        user_id=driver_user.id,
        license_number=f"DL-PROF-{suffix.upper()}",
        approval_status="APPROVED"
    )
    db_session.add(driver_profile)
    await db_session.flush()

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
        res = await client.get("/api/v1/drivers/profile", headers=headers)
        assert res.status_code == 200, f"Profile fetch failed: {res.text}"
        data = res.json()

        # Verify key presence and canonical mapping
        assert "id" in data, "Response must include 'id'"
        assert "driver_id" in data, "Response must include 'driver_id'"
        assert "user_id" in data, "Response must include 'user_id'"

        assert data["id"] == driver_profile.id
        assert data["driver_id"] == driver_profile.id
        assert data["id"] == data["driver_id"]
        assert data["user_id"] == driver_user.id
        assert data["full_name"] == driver_user.full_name
        assert data["license_number"] == driver_profile.license_number
        assert data["approval_status"] == "APPROVED"

