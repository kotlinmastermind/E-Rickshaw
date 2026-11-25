"""
Comprehensive tests verifying FCM-only push notification architecture,
device-token API security/lifecycle, and removal of non-FCM Google integrations.
"""
import json
import logging
import uuid
from unittest.mock import MagicMock, patch

import httpx
import pytest
from httpx import ASGITransport, AsyncClient
from sqlalchemy import select

from backend.api.deps import get_current_admin
from backend.application.services.notification_service import NotificationService
from backend.domain.models import (
    DeviceToken,
    DriverProfile,
    Notification,
    Session,
    User,
    UserPreferences,
)
from backend.infrastructure.security.crypto import create_jwt_token, hash_secret
from backend.infrastructure.security.rate_limiter import device_token_rate_limiter
from backend.integrations.notifications.provider import StandardNotificationProvider
from backend.main import app


@pytest.mark.asyncio
async def test_fcm_v1_oauth_refresh_transient_retry_and_log_masking(caplog):
    provider = StandardNotificationProvider()
    provider._FCM_RETRY_BASE_SECONDS = 0.01

    mock_creds = MagicMock()
    mock_creds.token = "expired_token"
    mock_creds.expired = True
    mock_creds.valid = False

    def _refresh(_req):
        mock_creds.token = "fresh_oauth_access_token_xyz"
        mock_creds.expired = False
        mock_creds.valid = True

    mock_creds.refresh.side_effect = _refresh

    resp_503 = MagicMock()
    resp_503.status_code = 503
    resp_503.text = '{"error": {"status": "UNAVAILABLE"}}'

    resp_200 = MagicMock()
    resp_200.status_code = 200
    resp_200.json.return_value = {"name": "projects/fcm-proj/messages/msg-retry-ok"}

    raw_token = "fcm_secret_device_token_1234567890_ABCDEF"
    with caplog.at_level(logging.INFO, logger="notifications_provider"):
        with patch.object(provider, "_get_fcm_credentials", return_value=(mock_creds, "fcm-proj")), \
             patch("httpx.AsyncClient.post", side_effect=[resp_503, resp_200]) as mock_post:
            sent = await provider.send_push_notification(raw_token, "Ride Alert", "Driver arrived")

    assert sent is True
    assert mock_creds.refresh.call_count == 1
    assert mock_post.call_count == 2
    assert provider.last_message_id == "projects/fcm-proj/messages/msg-retry-ok"
    # Verify full device token is NEVER leaked into logs
    assert raw_token not in caplog.text
    assert "fcm_se...CDEF" in caplog.text


@pytest.mark.asyncio
async def test_fcm_v1_no_retry_on_non_transient_400_and_retry_once_on_401():
    provider = StandardNotificationProvider()
    provider._FCM_RETRY_BASE_SECONDS = 0.01

    mock_creds = MagicMock()
    mock_creds.token = "stale_oauth_tok"
    mock_creds.expired = False
    mock_creds.valid = True
    mock_creds.refresh.side_effect = lambda _r: setattr(mock_creds, "token", "reloaded_oauth_tok")

    resp_401 = MagicMock()
    resp_401.status_code = 401
    resp_401.text = '{"error": {"status": "UNAUTHENTICATED"}}'

    resp_400 = MagicMock()
    resp_400.status_code = 400
    resp_400.text = '{"error": {"status": "INVALID_ARGUMENT", "message": "Invalid argument"}}'

    # 401 refreshes token once, then 400 terminates immediately without transient retries
    with patch.object(provider, "_get_fcm_credentials", return_value=(mock_creds, "fcm-proj")), \
         patch("httpx.AsyncClient.post", side_effect=[resp_401, resp_400]) as mock_post:
        sent = await provider.send_push_notification("fcm_token_valid_shape_12345", "Test", "Body")

    assert sent is False
    assert mock_creds.refresh.call_count == 1
    assert mock_post.call_count == 2
    assert provider.last_error_code == "INVALID_TOKEN"


@pytest.mark.asyncio
async def test_fcm_credentials_json_env_support(monkeypatch):
    provider = StandardNotificationProvider()
    sample_info = {"type": "service_account", "project_id": "erickshaw-fcm-only"}
    monkeypatch.setenv("FIREBASE_SERVICE_ACCOUNT_JSON", json.dumps(sample_info))

    fake_creds = MagicMock()
    fake_creds.project_id = "erickshaw-fcm-only"
    with patch("google.oauth2.service_account.Credentials.from_service_account_info", return_value=fake_creds) as mock_from_info:
        creds, project_id = provider._get_fcm_credentials()

    assert creds is fake_creds
    assert project_id == "erickshaw-fcm-only"
    mock_from_info.assert_called_once()
    called_scopes = mock_from_info.call_args.kwargs.get("scopes")
    assert called_scopes == ["https://www.googleapis.com/auth/firebase.messaging"]


@pytest.mark.asyncio
async def test_device_token_api_security_multi_device_and_logout_lifecycle(db_session):
    suffix = uuid.uuid4().hex[:8]
    customer = User(
        phone_number=f"+9198{int(suffix[:6], 16) % 100000000:08d}",
        email=f"rider_{suffix}@erickshaw.in",
        full_name="Test Rider",
        role="customer",
        is_active=True,
        is_verified=True,
    )
    other_customer = User(
        phone_number=f"+9197{int(suffix[:6], 16) % 100000000:08d}",
        email=f"other_{suffix}@erickshaw.in",
        full_name="Other Rider",
        role="customer",
        is_active=True,
        is_verified=True,
    )
    pending_driver_user = User(
        phone_number=f"+9196{int(suffix[:6], 16) % 100000000:08d}",
        email=f"drv_{suffix}@erickshaw.in",
        full_name="Pending Driver",
        role="driver",
        is_active=True,
        is_verified=False,
    )
    db_session.add_all([customer, other_customer, pending_driver_user])
    await db_session.flush()

    db_session.add(DriverProfile(
        user_id=pending_driver_user.id,
        license_number=f"DL-FCM-{suffix}",
        approval_status="PENDING",
    ))

    cust_sess = Session(
        id=str(uuid.uuid4()),
        user_id=customer.id,
        token_hash=hash_secret(f"s1:{suffix}"),
        is_revoked=False,
        expires_at=__import__("datetime").datetime.now(__import__("datetime").timezone.utc) + __import__("datetime").timedelta(hours=1),
    )
    other_sess = Session(
        id=str(uuid.uuid4()),
        user_id=other_customer.id,
        token_hash=hash_secret(f"s2:{suffix}"),
        is_revoked=False,
        expires_at=__import__("datetime").datetime.now(__import__("datetime").timezone.utc) + __import__("datetime").timedelta(hours=1),
    )
    drv_sess = Session(
        id=str(uuid.uuid4()),
        user_id=pending_driver_user.id,
        token_hash=hash_secret(f"s3:{suffix}"),
        is_revoked=False,
        expires_at=__import__("datetime").datetime.now(__import__("datetime").timezone.utc) + __import__("datetime").timedelta(hours=1),
    )
    db_session.add_all([cust_sess, other_sess, drv_sess])
    await db_session.commit()

    cust_hdr = {"Authorization": f"Bearer {create_jwt_token(customer.id, 'customer', cust_sess.id)}"}
    other_hdr = {"Authorization": f"Bearer {create_jwt_token(other_customer.id, 'customer', other_sess.id)}"}
    drv_hdr = {"Authorization": f"Bearer {create_jwt_token(pending_driver_user.id, 'driver', drv_sess.id)}"}
    customer_id = customer.id
    other_customer_id = other_customer.id

    token_phone_1 = f"fcm_phone_1_{suffix}_abcdef"
    token_phone_2 = f"fcm_phone_2_{suffix}_ghijkl"
    token_driver = f"fcm_driver_{suffix}_mnopqr"

    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        # 1. Invalid platform/token rejected cleanly
        bad_plat = await ac.post(
            "/api/v1/customers/device-token",
            json={"fcm_token": token_phone_1, "device_os": "WINDOWS_PHONE"},
            headers=cust_hdr,
        )
        assert bad_plat.status_code == 422

        bad_tok = await ac.post(
            "/api/v1/customers/device-token",
            json={"fcm_token": "x", "device_os": "ANDROID"},
            headers=cust_hdr,
        )
        assert bad_tok.status_code == 422

        # 2. Cross-user target registration blocked with 403
        forged = await ac.post(
            "/api/v1/customers/device-token",
            json={"fcm_token": token_phone_1, "user_id": customer.id},
            headers=other_hdr,
        )
        assert forged.status_code == 403

        # 3. Valid customer registration (never echoes full token)
        reg1 = await ac.post(
            "/api/v1/customers/device-token",
            json={"fcm_token": token_phone_1, "device_os": "ANDROID"},
            headers=cust_hdr,
        )
        assert reg1.status_code == 200, reg1.text
        assert token_phone_1 not in reg1.text
        assert reg1.json()["is_active"] is True

        # 4. Duplicate / refresh avoids creating duplicate DB row
        reg1_refresh = await ac.post(
            "/api/v1/customers/device-token",
            json={"fcm_token": token_phone_1, "device_os": "IOS"},
            headers=cust_hdr,
        )
        assert reg1_refresh.status_code == 200
        assert reg1_refresh.json()["action"] == "TOKEN_REFRESHED"

        # 5. Multiple devices per user supported
        reg2 = await ac.post(
            "/api/v1/notifications/device-token",
            json={"fcm_token": token_phone_2, "device_os": "ANDROID"},
            headers=cust_hdr,
        )
        assert reg2.status_code == 200

        rows = (await db_session.execute(select(DeviceToken).where(DeviceToken.user_id == customer_id))).scalars().all()
        assert len(rows) == 2
        assert all(r.is_active for r in rows)

        # 6. Cross-user active token conflict rejected (cannot steal active device token)
        steal = await ac.post(
            "/api/v1/customers/device-token",
            json={"fcm_token": token_phone_1, "device_os": "ANDROID"},
            headers=other_hdr,
        )
        assert steal.status_code == 403

        # 7. Listing registrations never exposes raw fcm_token
        listed = await ac.get("/api/v1/notifications/device-tokens", headers=cust_hdr)
        assert listed.status_code == 200
        assert token_phone_1 not in listed.text
        assert token_phone_2 not in listed.text

        # 8. Pending onboarding driver can register token via /drivers/device-token
        drv_reg = await ac.post(
            "/api/v1/drivers/device-token",
            json={"fcm_token": token_driver, "device_os": "ANDROID"},
            headers=drv_hdr,
        )
        assert drv_reg.status_code == 200, drv_reg.text
        assert token_driver not in drv_reg.text

        # 9. Customer logout with fcm_token deactivates that token
        lo = await ac.post("/api/v1/auth/logout", json={"fcm_token": token_phone_1}, headers=cust_hdr)
        assert lo.status_code == 200
        db_session.expire_all()
        row_p1 = (await db_session.execute(select(DeviceToken).where(DeviceToken.fcm_token == token_phone_1))).scalar_one()
        assert row_p1.is_active is False

        # Once logged out/deactivated, another user logging in on the physical phone can re-bind it
        rebind = await ac.post(
            "/api/v1/customers/device-token",
            json={"fcm_token": token_phone_1, "device_os": "ANDROID"},
            headers=other_hdr,
        )
        assert rebind.status_code == 200
        db_session.expire_all()
        row_p1_rebound = (await db_session.execute(select(DeviceToken).where(DeviceToken.fcm_token == token_phone_1))).scalar_one()
        assert row_p1_rebound.user_id == other_customer_id
        assert row_p1_rebound.is_active is True

        # 10. Driver DELETE endpoint deactivates driver token
        drv_del = await ac.request(
            "DELETE",
            "/api/v1/drivers/device-token",
            json={"fcm_token": token_driver},
            headers=drv_hdr,
        )
        assert drv_del.status_code == 200
        assert drv_del.json()["deactivated"] is True


@pytest.mark.asyncio
async def test_device_token_endpoint_rate_limiting_and_google_oauth_removed(db_session):
    suffix = uuid.uuid4().hex[:8]
    user = User(
        phone_number=f"+9195{int(suffix[:6], 16) % 100000000:08d}",
        email=f"rl_{suffix}@erickshaw.in",
        full_name="Rate Limit User",
        role="customer",
        is_active=True,
    )
    db_session.add(user)
    await db_session.flush()
    sess = Session(
        id=str(uuid.uuid4()),
        user_id=user.id,
        token_hash=hash_secret(f"rl:{suffix}"),
        is_revoked=False,
        expires_at=__import__("datetime").datetime.now(__import__("datetime").timezone.utc) + __import__("datetime").timedelta(hours=1),
    )
    db_session.add(sess)
    await db_session.commit()

    hdr = {"Authorization": f"Bearer {create_jwt_token(user.id, 'customer', sess.id)}"}
    orig_max = device_token_rate_limiter.max_requests
    device_token_rate_limiter.max_requests = 2
    try:
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
            # Verify /oauth/google is removed
            google_oauth = await ac.post("/api/v1/auth/oauth/google", json={"email": "x@y.com", "token": "t"})
            assert google_oauth.status_code in (404, 405)

            r1 = await ac.post("/api/v1/customers/device-token", json={"fcm_token": f"rl_tok_{suffix}_1"}, headers=hdr)
            r2 = await ac.post("/api/v1/customers/device-token", json={"fcm_token": f"rl_tok_{suffix}_1"}, headers=hdr)
            r3 = await ac.post("/api/v1/customers/device-token", json={"fcm_token": f"rl_tok_{suffix}_1"}, headers=hdr)
            assert r1.status_code == 200
            assert r2.status_code == 200
            assert r3.status_code == 429
    finally:
        device_token_rate_limiter.max_requests = orig_max


@pytest.mark.asyncio
async def test_notification_service_deactivates_unregistered_token_and_admin_push_broadcast(db_session):
    suffix = uuid.uuid4().hex[:8]
    customer = User(
        phone_number=f"+9194{int(suffix[:6], 16) % 100000000:08d}",
        email=f"push_{suffix}@erickshaw.in",
        full_name="Push Rider",
        role="customer",
        is_active=True,
    )
    admin = User(
        phone_number=f"+9193{int(suffix[:6], 16) % 100000000:08d}",
        email=f"adm_push_{suffix}@erickshaw.in",
        full_name="Push Admin",
        role="admin",
        is_active=True,
        mfa_enabled=True,
    )
    db_session.add_all([customer, admin])
    await db_session.flush()
    db_session.add(UserPreferences(user_id=customer.id, marketing_push_opt_in=True, preferred_language="en"))
    db_session.add(DeviceToken(
        user_id=customer.id,
        fcm_token=f"unreg_tok_{suffix}_12345",
        device_os="ANDROID",
        is_active=True,
    ))
    await db_session.commit()

    # Mock UNREGISTERED response on FCM HTTP v1
    provider = StandardNotificationProvider()
    provider._FCM_RETRY_BASE_SECONDS = 0.01
    mock_creds = MagicMock()
    mock_creds.token = "oauth_ok"
    mock_creds.expired = False
    mock_creds.valid = True
    resp_404 = MagicMock()
    resp_404.status_code = 404
    resp_404.text = '{"error": {"status": "NOT_FOUND", "details": [{"errorCode": "UNREGISTERED"}]}}'

    orig_post = httpx.AsyncClient.post

    async def _selective_post(self_client, url, *args, **kwargs):
        if "fcm.googleapis.com" in str(url):
            return resp_404
        return await orig_post(self_client, url, *args, **kwargs)

    app.dependency_overrides[get_current_admin] = lambda: admin
    try:
        with patch("backend.application.services.notification_service.get_notification_provider", return_value=provider), \
             patch.object(provider, "_get_fcm_credentials", return_value=(mock_creds, "fcm-proj")), \
             patch("httpx.AsyncClient.post", new=_selective_post):
            async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
                res = await ac.post(
                    "/api/v1/admin/broadcasts",
                    json={
                        "title": "Push Test Broadcast",
                        "message": "Dispatched over FCM HTTP v1",
                        "target_role": "CUSTOMER",
                        "channel": "PUSH",
                    },
                )
                assert res.status_code == 200, res.text
                assert res.json()["channel"] == "PUSH"
    finally:
        app.dependency_overrides.pop(get_current_admin, None)

    db_session.expire_all()
    tok = (await db_session.execute(select(DeviceToken).where(DeviceToken.fcm_token == f"unreg_tok_{suffix}_12345"))).scalar_one()
    assert tok.is_active is False
    assert tok.last_error_at is not None
