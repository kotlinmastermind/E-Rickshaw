import pytest
import uuid
from httpx import AsyncClient, ASGITransport
from backend.main import app

@pytest.mark.asyncio
async def test_customer_signup_and_login_with_email_and_mobile(db_session):
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        # 1. Customer Sign Up with Email and Password
        uid = uuid.uuid4().hex[:8]
        cust_email = f"test_commuter_{uid}@erickshaw.in"
        cust_phone = "+919876599999"
        signup_res = await ac.post("/api/v1/auth/signup/customer", json={
            "full_name": "Rohan Verma",
            "email": cust_email,
            "phone_number": cust_phone,
            "password": "Password1234"
        })
        assert signup_res.status_code == 200, signup_res.text
        data = signup_res.json()
        assert "access_token" in data
        assert data["user"]["role"] == "customer"

        # 2. Login using Email + Password
        login_email_res = await ac.post("/api/v1/auth/login", json={
            "identifier": cust_email,
            "password": "Password1234"
        })
        assert login_email_res.status_code == 200, login_email_res.text
        assert "access_token" in login_email_res.json()

        # 3. Login using Mobile Phone + Password (10 digits without +91)
        login_phone_res = await ac.post("/api/v1/auth/login", json={
            "identifier": "9876599999",
            "password": "Password1234"
        })
        assert login_phone_res.status_code == 200, login_phone_res.text
        assert "access_token" in login_phone_res.json()

        # 4. Login with invalid password fails
        bad_pw_res = await ac.post("/api/v1/auth/login", json={
            "identifier": cust_email,
            "password": "WrongPassword"
        })
        assert bad_pw_res.status_code == 401

@pytest.mark.asyncio
async def test_driver_signup_and_login_with_mobile(db_session):
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        driver_phone = "+919999888877"
        uid = uuid.uuid4().hex[:8]
        driver_email = f"driver_{uid}@erickshaw.in"
        
        signup_res = await ac.post("/api/v1/auth/signup/driver", json={
            "full_name": "Sunil Pilot",
            "phone_number": driver_phone,
            "email": driver_email,
            "password": "PilotPassword2026",
            "vehicle_name": "Piaggio Ape E-City",
            "vehicle_no": "DL-01-AB-9988"
        })
        assert signup_res.status_code == 200, signup_res.text
        data = signup_res.json()
        assert "access_token" in data
        assert data["user"]["role"] == "driver"

        # Login using mobile
        login_res = await ac.post("/api/v1/auth/login", json={
            "identifier": "9999888877",
            "password": "PilotPassword2026"
        })
        assert login_res.status_code == 200, login_res.text
        assert login_res.json()["user"]["role"] == "driver"

@pytest.mark.asyncio
async def test_otp_endpoints_active_and_bypass_deleted(db_session, monkeypatch):
    """
    Verifies that:
    1. /api/v1/auth/otp/request and /api/v1/auth/otp/verify endpoints are active.
    2. Old hardcoded OTP bypass (123456) is completely deleted (returns 401 unauthorized).
    3. Proper Twilio verification check works seamlessly.
    """
    from unittest.mock import AsyncMock
    from backend.integrations.sms.provider import get_sms_provider

    provider = get_sms_provider()
    # Mock Twilio Verify response
    monkeypatch.setattr(provider, "send_verification_otp", AsyncMock(return_value={"success": True, "status": "pending"}))
    monkeypatch.setattr(provider, "verify_otp", AsyncMock(return_value=False))

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        # Request OTP
        r1 = await ac.post("/api/v1/auth/otp/request", json={"phone_number": "+919876543210"})
        assert r1.status_code == 200, r1.text
        assert "Verification passcode dispatched" in r1.json()["message"]

        # Verify that hardcoded bypass 123456 FAILS (verify_otp returns False)
        r2 = await ac.post("/api/v1/auth/otp/verify", json={"phone_number": "+919876543210", "otp_code": "123456"})
        assert r2.status_code == 401, "Expected 401 Unauthorized because hardcoded 123456 bypass must be deleted!"

        # Now mock successful Twilio approval
        monkeypatch.setattr(provider, "verify_otp", AsyncMock(return_value=True))
        r3 = await ac.post("/api/v1/auth/otp/verify", json={"phone_number": "+919876543210", "otp_code": "981240", "full_name": "Test Twilio Rider"})
        assert r3.status_code == 200, r3.text
        data = r3.json()
        assert "access_token" in data
        assert data["user"]["full_name"] == "Test Twilio Rider"


