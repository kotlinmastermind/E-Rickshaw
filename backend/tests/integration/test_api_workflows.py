import pytest
import hmac
import hashlib
import json
import httpx
from httpx import ASGITransport
from backend.main import app
from backend.integrations.sms.provider import get_sms_provider
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
        assert "Taj Mahal" in geo_resp.json()["formatted_address"]

@pytest.mark.asyncio
async def test_full_auth_and_ride_lifecycle_with_concurrency():
    transport = ASGITransport(app=app)
    sms_mock = get_sms_provider()

    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        # 1. Customer OTP Auth
        cust_phone = "+919876543210"
        req_resp = await client.post("/api/v1/auth/otp/request", json={"phone_number": cust_phone})
        assert req_resp.status_code == 200

        cust_otp = sms_mock.get_latest_otp(cust_phone)
        assert cust_otp is not None

        verify_resp = await client.post("/api/v1/auth/otp/verify", json={
            "phone_number": cust_phone,
            "otp_code": cust_otp,
            "role": "customer"
        })
        assert verify_resp.status_code == 200
        cust_token = verify_resp.json()["access_token"]
        cust_headers = {"Authorization": f"Bearer {cust_token}"}

        # 2. Driver 1 OTP Auth
        drv1_phone = "+919876500001"
        await client.post("/api/v1/auth/otp/request", json={"phone_number": drv1_phone})
        drv1_otp = sms_mock.get_latest_otp(drv1_phone)
        drv1_verify = await client.post("/api/v1/auth/otp/verify", json={
            "phone_number": drv1_phone,
            "otp_code": drv1_otp,
            "role": "driver"
        })
        drv1_token = drv1_verify.json()["access_token"]
        drv1_headers = {"Authorization": f"Bearer {drv1_token}"}

        # 3. Driver 2 OTP Auth (for concurrency test)
        drv2_phone = "+919876500002"
        await client.post("/api/v1/auth/otp/request", json={"phone_number": drv2_phone})
        drv2_otp = sms_mock.get_latest_otp(drv2_phone)
        drv2_verify = await client.post("/api/v1/auth/otp/verify", json={
            "phone_number": drv2_phone,
            "otp_code": drv2_otp,
            "role": "driver"
        })
        drv2_token = drv2_verify.json()["access_token"]
        drv2_headers = {"Authorization": f"Bearer {drv2_token}"}

        # Approve Driver 2 via Admin so they are eligible to accept
        admin_resp = await client.post("/api/v1/auth/otp/request", json={"phone_number": "+919999900000"})
        admin_otp = sms_mock.get_latest_otp("+919999900000")
        admin_auth = await client.post("/api/v1/auth/otp/verify", json={
            "phone_number": "+919999900000",
            "otp_code": admin_otp,
            "role": "admin"
        })
        admin_headers = {"Authorization": f"Bearer {admin_auth.json()['access_token']}"}

        drivers_list = await client.get("/api/v1/admin/drivers", headers=admin_headers)
        drv2_profile = next(d for d in drivers_list.json() if d["license_number"] == f"DL-{drv2_verify.json()['user']['id'][:8].upper()}")
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

        # 6. ATOMIC CONCURRENCY TEST: Two drivers attempt to accept simultaneously
        accept_resp1 = await client.post(f"/api/v1/rides/{ride_id}/accept", headers=drv1_headers)
        accept_resp2 = await client.post(f"/api/v1/rides/{ride_id}/accept", headers=drv2_headers)

        # Exactly ONE driver must succeed (200), the other MUST receive 409 Conflict!
        statuses = [accept_resp1.status_code, accept_resp2.status_code]
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
        secret = settings.RAZORPAY_WEBHOOK_SECRET
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
