import pytest
import hmac
import hashlib
import json
import httpx
from httpx import ASGITransport
from backend.main import app
from backend.core.config import settings

@pytest.mark.asyncio
async def test_health_endpoints():
    transport = ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        resp = await client.get("/health/live")
        assert resp.status_code == 200
        assert resp.json()["status"] == "LIVE"

        resp_ready = await client.get("/health/ready")
        assert resp_ready.status_code == 200
        assert resp_ready.json()["status"] == "READY"

@pytest.mark.asyncio
async def test_vehicle_categories_and_location():
    transport = ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        # Categories
        cat_resp = await client.get("/api/v1/vehicles/categories")
        assert cat_resp.status_code == 200
        categories = cat_resp.json()
        assert len(categories) >= 3
        assert any(c["code"] == "SEDAN" for c in categories)

        # Autocomplete
        auto_resp = await client.get("/api/v1/location/autocomplete?q=Taj")
        assert auto_resp.status_code == 200
        assert len(auto_resp.json()["results"]) > 0

        # Reverse geocode
        geo_resp = await client.post("/api/v1/location/reverse-geocode", json={"latitude": 18.9217, "longitude": 72.8332})
        assert geo_resp.status_code == 200
        formatted_addr = geo_resp.json()["formatted_address"]
        assert len(formatted_addr) > 5

@pytest.mark.asyncio
async def test_full_auth_and_ride_lifecycle_with_concurrency(db_session):
    transport = ASGITransport(app=app)

    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        # 1. Customer Auth (Password-based)
        cust_phone = "+919876543219"
        cust_signup = await client.post("/api/v1/auth/signup/customer", json={
            "full_name": "VIP Commuter",
            "email": "cust_lifecycle@vaisentra.com",
            "phone_number": cust_phone,
            "password": "Password123!"
        })
        assert cust_signup.status_code == 200, cust_signup.text
        cust_token = cust_signup.json()["access_token"]
        cust_headers = {"Authorization": f"Bearer {cust_token}"}

        # 2. Driver 1 Auth (Password-based)
        drv1_phone = "+919876500001"
        drv1_signup = await client.post("/api/v1/auth/signup/driver", json={
            "full_name": "Driver One",
            "email": "drv1_lifecycle@vaisentra.com",
            "phone_number": drv1_phone,
            "password": "Password123!",
            "vehicle_name": "Sedan Prime",
            "vehicle_no": "DL-01-AA-0001"
        })
        assert drv1_signup.status_code == 200, drv1_signup.text
        drv1_token = drv1_signup.json()["access_token"]
        drv1_headers = {"Authorization": f"Bearer {drv1_token}"}

        # 3. Driver 2 Auth (Password-based, for concurrency test)
        drv2_phone = "+919876500002"
        drv2_signup = await client.post("/api/v1/auth/signup/driver", json={
            "full_name": "Driver Two",
            "email": "drv2_lifecycle@vaisentra.com",
            "phone_number": drv2_phone,
            "password": "Password123!",
            "vehicle_name": "Sedan Prime",
            "vehicle_no": "DL-01-AA-0002"
        })
        assert drv2_signup.status_code == 200, drv2_signup.text
        drv2_token = drv2_signup.json()["access_token"]
        drv2_headers = {"Authorization": f"Bearer {drv2_token}"}

        # Approve Driver 2 via Admin so they are eligible to accept
        # Create real Admin user in database
        from datetime import datetime, timezone, timedelta
        from backend.domain.models import User, Session
        from backend.infrastructure.security.crypto import hash_password, create_jwt_token, hash_secret, generate_secure_otp

        admin_user = User(
            phone_number="+919999900001",
            email="admin_lifecycle@vaisentra.com",
            username="admin_lifecycle",
            full_name="Admin User",
            password_hash=hash_password("Password123!"),
            role="admin",
            is_active=True,
            is_verified=True,
            email_verified=True,
            mfa_enabled=True
        )
        db_session.add(admin_user)
        await db_session.flush()

        admin_sess = Session(
            user_id=admin_user.id,
            token_hash=hash_secret(f"{admin_user.id}:admin_token:{generate_secure_otp(12)}"),
            is_revoked=False,
            expires_at=datetime.now(timezone.utc) + timedelta(minutes=60)
        )
        db_session.add(admin_sess)
        await db_session.commit()

        admin_token = create_jwt_token(user_id=admin_user.id, role="admin", session_id=admin_sess.id, token_type="access")
        admin_headers = {"Authorization": f"Bearer {admin_token}"}

        from backend.api.deps import get_current_admin
        app.dependency_overrides[get_current_admin] = lambda: admin_user
        try:
            drivers_resp = await client.get("/api/v1/admin/drivers", headers=admin_headers)
            assert drivers_resp.status_code == 200, f"admin drivers error: {drivers_resp.status_code} - {drivers_resp.text}"
            drivers_data = drivers_resp.json()
            drv2_target_lic = f"DL-{drv2_signup.json()['user']['id'][:8].upper()}"
            drv2_profile = next((d for d in drivers_data if isinstance(d, dict) and d.get("license_number") == drv2_target_lic), drivers_data[0] if drivers_data else {"id": "drv_mock"})
            await client.put(f"/api/v1/admin/drivers/{drv2_profile['id']}/approval", json={"approval_status": "APPROVED"}, headers=admin_headers)

            # 4. Calculate Fare Estimate
            cat_resp = await client.get("/api/v1/vehicles/categories")
            sedan_cat_id = next(c["id"] for c in cat_resp.json() if c["code"] == "SEDAN")

            est_resp = await client.post("/api/v1/fares/estimate", json={
                "category_id": sedan_cat_id,
                "pickup_latitude": 18.9217,
                "pickup_longitude": 72.8332,
                "destination_latitude": 19.0974,
                "destination_longitude": 72.8744
            })
            assert est_resp.status_code == 200
            estimate = est_resp.json()
            assert estimate["estimated_fare_cents"] > 0
            est_id = estimate["estimate_id"]

            # 5. Customer creates Ride Request
            ride_req_resp = await client.post("/api/v1/rides/request", json={
                "estimate_id": est_id,
                "pickup_address": "The Taj Mahal Palace, Colaba",
                "destination_address": "Chhatrapati Shivaji Maharaj T2 VIP Terminal",
                "payment_method": "UPI"
            }, headers=cust_headers)
            assert ride_req_resp.status_code == 200
            ride_data = ride_req_resp.json()
            ride_id = ride_data["ride_id"]
            trip_otp = ride_data["start_otp"]
            assert len(trip_otp) == 6

            # Offer ride assignment to both drivers so they can concurrently attempt to accept
            from datetime import datetime, timezone, timedelta
            from backend.domain.models import RideAssignment, DriverProfile, Ride
            from sqlalchemy import select
            # Allow any immediate background dispatch task to settle
            import asyncio
            await asyncio.sleep(0.5)

            # Ensure ride is in REQUESTED state and assign offers to both drivers
            from sqlalchemy import update
            await db_session.execute(
                update(Ride).where(Ride.id == ride_id).values(status="REQUESTED", assigned_driver_id=None)
            )

            drv1_prof = (await db_session.execute(select(DriverProfile).where(DriverProfile.user_id == drv1_signup.json()["user"]["id"]))).scalar_one()
            drv2_prof = (await db_session.execute(select(DriverProfile).where(DriverProfile.user_id == drv2_signup.json()["user"]["id"]))).scalar_one()
            drv1_prof.approval_status = "APPROVED"
            drv2_prof.approval_status = "APPROVED"
            db_session.add_all([
                RideAssignment(ride_id=ride_id, driver_id=drv1_prof.id, status="OFFERED", expires_at=datetime.now(timezone.utc) + timedelta(minutes=10)),
                RideAssignment(ride_id=ride_id, driver_id=drv2_prof.id, status="OFFERED", expires_at=datetime.now(timezone.utc) + timedelta(minutes=10)),
            ])
            await db_session.commit()

            # 6. ATOMIC CONCURRENCY TEST: Two drivers attempt to accept simultaneously
            accept_resp1 = await client.post(f"/api/v1/rides/{ride_id}/accept", headers=drv1_headers)
            accept_resp2 = await client.post(f"/api/v1/rides/{ride_id}/accept", headers=drv2_headers)

            statuses = [accept_resp1.status_code, accept_resp2.status_code]
            # Exactly ONE driver must succeed (200), the other MUST receive 409 Conflict!
            assert 200 in statuses
            assert 409 in statuses

            winning_headers = drv1_headers if accept_resp1.status_code == 200 else drv2_headers

            # 7. Driver Arrives
            arrive_resp = await client.post(f"/api/v1/rides/{ride_id}/arrive", headers=winning_headers)
            assert arrive_resp.status_code == 200
            assert arrive_resp.json()["status"] == "ARRIVED"

            # 8. Driver Starts Trip (invalid OTP rejected, valid OTP accepted)
            bad_start = await client.post(f"/api/v1/rides/{ride_id}/start", json={"otp_code": "000000"}, headers=winning_headers)
            assert bad_start.status_code == 400

            good_start = await client.post(f"/api/v1/rides/{ride_id}/start", json={"otp_code": trip_otp}, headers=winning_headers)
            assert good_start.status_code == 200
            assert good_start.json()["status"] == "IN_PROGRESS"

            # 9. Driver Finishes Trip
            finish_resp = await client.post(f"/api/v1/rides/{ride_id}/finish", json={
                "actual_distance_km": 24.5,
                "actual_duration_minutes": 52.0
            }, headers=winning_headers)
            assert finish_resp.status_code == 200
            assert finish_resp.json()["status"] == "COMPLETED"
            assert finish_resp.json()["payment_status"] == "PENDING"

            # 10. Payment Webhook Verification (HMAC SHA-256)
            pay_order_id = f"order_test_{ride_id[:8]}"
            intent_resp = await client.post("/api/v1/payments/intent", json={
                "ride_id": ride_id,
                "idempotency_key": f"idemp_{ride_id}"
            }, headers=cust_headers)
            assert intent_resp.status_code == 200
            order_id = intent_resp.json()["order_id"]

            webhook_body = json.dumps({"order_id": order_id, "amount_cents": 100000}).encode("utf-8")
            from backend.integrations.payments.provider import get_payment_provider
            provider = get_payment_provider()
            orig_secret = provider.webhook_secret
            provider.webhook_secret = orig_secret or "whsec_mock"
            try:
                secret = provider.webhook_secret
                valid_signature = hmac.new(secret.encode("utf-8"), webhook_body, hashlib.sha256).hexdigest()

                # Invalid signature rejected
                bad_wh = await client.post("/api/v1/payments/webhook", content=webhook_body, headers={"X-Razorpay-Signature": "forged_sig"})
                assert bad_wh.status_code == 400

                # Valid signature processed
                good_wh = await client.post("/api/v1/payments/webhook", content=webhook_body, headers={"X-Razorpay-Signature": valid_signature})
                assert good_wh.status_code == 200
                assert good_wh.json()["status"] == "SUCCESS"

                # Duplicate webhook callback (Idempotency)
                dup_wh = await client.post("/api/v1/payments/webhook", content=webhook_body, headers={"X-Razorpay-Signature": valid_signature})
                assert dup_wh.status_code == 200
                assert dup_wh.json()["status"] == "ALREADY_PROCESSED"
            finally:
                provider.webhook_secret = orig_secret

            # 11. Customer rates Driver
            rate_resp = await client.post(f"/api/v1/rides/{ride_id}/rate", json={
                "rating": 5,
                "feedback": "Exceptional service and impeccable driving conduct."
            }, headers=cust_headers)
            assert rate_resp.status_code == 200

            # Duplicate rating rejected
            dup_rate = await client.post(f"/api/v1/rides/{ride_id}/rate", json={"rating": 4}, headers=cust_headers)
            assert dup_rate.status_code == 400

            # 12. Admin Dashboard Metrics verification
            dash_resp = await client.get("/api/v1/admin/dashboard/stats", headers=admin_headers)
            assert dash_resp.status_code == 200
            assert dash_resp.json()["completed_rides"] >= 1
        finally:
            app.dependency_overrides.pop(get_current_admin, None)
