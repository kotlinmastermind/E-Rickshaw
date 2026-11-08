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
async def test_otp_and_forgot_password_endpoints_decommissioned(db_session):
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        r1 = await ac.post("/api/v1/auth/otp/request", json={"phone_number": "+919876543210"})
        assert r1.status_code in (404, 405)
        r2 = await ac.post("/api/v1/auth/otp/verify", json={"phone_number": "+919876543210", "otp_code": "123456"})
        assert r2.status_code in (404, 405)
        r3 = await ac.post("/api/v1/auth/forgot-password/request-otp", json={"phone_number": "+919876543210"})
        assert r3.status_code in (404, 405)
        r4 = await ac.post("/api/v1/auth/forgot-password/reset", json={"phone_number": "+919876543210", "passcode": "123456", "new_password": "NewPassword123!"})
        assert r4.status_code in (404, 405)


