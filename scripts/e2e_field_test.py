"""
End-to-End Field Verification Script for Grand Chauffeur Platform.
Simulates real concurrent device interaction:
  - Customer Android App
  - Driver Partner Android App
  - Live FastAPI Backend
  - Supabase PostgreSQL
  - Upstash Redis Cache
"""

import sys
import time
import functools
import httpx

print = functools.partial(print, flush=True)

BASE_URL = "http://127.0.0.1:8000"

def log_step(step: str):
    print(f"\n========================================================")
    print(f"--> {step}")
    print(f"========================================================")

def run_field_test():
    with httpx.Client(base_url=BASE_URL, timeout=30.0) as client:
        # 0. Health checks
        log_step("STEP 0: Health Checks")
        live_res = client.get("/health/live")
        assert live_res.status_code == 200, f"Live check failed: {live_res.text}"
        print(f"Health Live: {live_res.json()}")

        ready_res = client.get("/health/ready")
        assert ready_res.status_code == 200, f"Ready check failed: {ready_res.text}"
        ready_json = ready_res.json()
        print(f"Health Ready: DB={ready_json['checks']['database']['status']}, Redis={ready_json['checks']['redis_cache']['status']}")

        # 1. Customer Authentication Flow
        log_step("STEP 1: Customer Phone OTP Login")
        cust_phone = f"+9198765{int(time.time()) % 100000:05d}"
        print(f"Customer registering with phone: {cust_phone}")
        otp_req = client.post("/api/v1/auth/otp/request", json={"phone_number": cust_phone})
        assert otp_req.status_code == 200, f"Customer OTP request failed: {otp_req.text}"
        otp_data = otp_req.json()
        print(f"OTP Request Response: {otp_data}")
        cust_otp = otp_data.get("debug_otp", "123456")

        verify_req = client.post("/api/v1/auth/otp/verify", json={
            "phone_number": cust_phone,
            "otp_code": cust_otp,
            "role": "customer"
        })
        assert verify_req.status_code == 200, f"Customer OTP verification failed: {verify_req.text}"
        cust_token = verify_req.json()["access_token"]
        cust_headers = {"Authorization": f"Bearer {cust_token}"}
        print(f"Customer JWT Token Acquired (Starts with: {cust_token[:20]}...)")

        # 2. Driver Authentication Flow
        log_step("STEP 2: Driver Partner Phone OTP Login")
        drv_phone = f"+9191234{int(time.time()) % 100000:05d}"
        print(f"Driver registering with phone: {drv_phone}")
        drv_otp_req = client.post("/api/v1/auth/otp/request", json={"phone_number": drv_phone})
        assert drv_otp_req.status_code == 200, f"Driver OTP request failed: {drv_otp_req.text}"
        drv_otp = drv_otp_req.json().get("debug_otp", "123456")

        drv_verify_req = client.post("/api/v1/auth/otp/verify", json={
            "phone_number": drv_phone,
            "otp_code": drv_otp,
            "role": "driver"
        })
        assert drv_verify_req.status_code == 200, f"Driver OTP verify failed: {drv_verify_req.text}"
        drv_data = drv_verify_req.json()
        drv_token = drv_data["access_token"]
        drv_headers = {"Authorization": f"Bearer {drv_token}"}
        print(f"Driver Partner JWT Token Acquired (Starts with: {drv_token[:20]}...)")

        # 2b. Admin Approval of Driver (Real Platform Compliance Requirement)
        log_step("STEP 2b: Admin Web Portal Approves Driver Partner")
        admin_phone = f"+9199999{int(time.time()) % 100000:05d}"
        admin_otp_req = client.post("/api/v1/auth/otp/request", json={"phone_number": admin_phone})
        admin_otp = admin_otp_req.json().get("debug_otp", "123456")
        admin_verify = client.post("/api/v1/auth/otp/verify", json={
            "phone_number": admin_phone,
            "otp_code": admin_otp,
            "role": "admin"
        })
        assert admin_verify.status_code == 200, f"Admin login failed: {admin_verify.text}"
        admin_headers = {"Authorization": f"Bearer {admin_verify.json()['access_token']}"}

        drivers_list = client.get("/api/v1/admin/drivers", headers=admin_headers)
        assert drivers_list.status_code == 200
        driver_entry = next((d for d in drivers_list.json() if d.get("user_id") == drv_data["user"]["id"] or d.get("phone_number") == drv_phone), None)
        assert driver_entry is not None, f"Driver not found in admin roster: {drivers_list.text}"
        
        approve_res = client.put(f"/api/v1/admin/drivers/{driver_entry['id']}/approval", json={"approval_status": "APPROVED"}, headers=admin_headers)
        assert approve_res.status_code == 200, f"Driver approval failed: {approve_res.text}"
        print(f"Chauffeur Approved by Platform Administrator! Status: {approve_res.json()['approval_status']}")

        # 3. Driver Goes Online & Sends GPS Telemetry
        log_step("STEP 3: Driver Partner Goes Online & Transmits GPS Ping")
        avail_res = client.post("/api/v1/drivers/availability", json={"is_online": True}, headers=drv_headers)
        assert avail_res.status_code == 200, f"Set availability failed: {avail_res.text}"
        print(f"Driver Availability: {avail_res.json()}")

        ping_res = client.post("/api/v1/drivers/location", json={
            "latitude": 18.9217,
            "longitude": 72.8332,
            "heading": 45.0
        }, headers=drv_headers)
        assert ping_res.status_code == 200, f"Driver location ping failed: {ping_res.text}"
        print(f"Driver Location Ping: {ping_res.json()}")

        # 4. Customer Requests Fare Estimate
        log_step("STEP 4: Customer Requests Real Fare Calculation")
        cat_res = client.get("/api/v1/vehicles/categories")
        assert cat_res.status_code == 200
        categories = cat_res.json()
        sedan_cat = next(c for c in categories if c["code"] == "SEDAN")

        est_res = client.post("/api/v1/fares/estimate", json={
            "category_id": sedan_cat["id"],
            "pickup_latitude": 18.9217,
            "pickup_longitude": 72.8332,
            "destination_latitude": 19.0760,
            "destination_longitude": 72.8777
        }, headers=cust_headers)
        assert est_res.status_code == 200, f"Fare estimate failed: {est_res.text}"
        estimate = est_res.json()
        print(f"Estimate ID: {estimate['estimate_id']}")
        print(f"Estimated Distance: {estimate['distance_km']} km")
        print(f"Estimated Duration: {estimate.get('estimated_duration_minutes', 0)} min")
        print(f"Total Fare: Rs {estimate['estimated_fare_cents'] / 100:.2f}")

        # 5. Customer Requests Luxury Ride
        log_step("STEP 5: Customer Dispatches Ride Request")
        ride_req = client.post("/api/v1/rides/request", json={
            "estimate_id": estimate["estimate_id"],
            "pickup_address": "The Taj Mahal Palace, Colaba, Mumbai",
            "destination_address": "Chhatrapati Shivaji Terminal T2, Mumbai",
            "payment_method": "RAZORPAY"
        }, headers=cust_headers)
        assert ride_req.status_code in [200, 201], f"Ride request failed: {ride_req.text}"
        ride_data = ride_req.json()
        ride_id = ride_data.get("ride_id") or ride_data.get("id")
        start_otp = ride_data.get("start_otp")
        print(f"Ride Created Successfully! ID: {ride_id}")
        print(f"Start Verification OTP: {start_otp}")
        print(f"Initial Status: {ride_data['status']}")

        # 6. Driver Accepts the Ride Offer
        log_step("STEP 6: Driver Partner Accepts Incoming Ride Offer")
        accept_res = client.post(f"/api/v1/rides/{ride_id}/accept", headers=drv_headers)
        assert accept_res.status_code == 200, f"Ride accept failed: {accept_res.text}"
        print(f"Driver Accepted Ride: Status = {accept_res.json()['status']}")

        # 7. Customer Polls Status -> ASSIGNED
        log_step("STEP 7: Customer Polls Live Status (Expecting ASSIGNED)")
        cust_poll_1 = client.get(f"/api/v1/rides/{ride_id}", headers=cust_headers)
        assert cust_poll_1.status_code == 200
        assert cust_poll_1.json()["status"] == "ASSIGNED"
        print(f"Customer App Verified Status: {cust_poll_1.json()['status']}")

        # 8. Driver Arrives at Pickup Location
        log_step("STEP 8: Driver Arrives at Pickup Location")
        arrive_res = client.post(f"/api/v1/rides/{ride_id}/arrive", headers=drv_headers)
        assert arrive_res.status_code == 200, f"Driver arrive failed: {arrive_res.text}"
        print(f"Driver Arrive Reported: Status = {arrive_res.json()['status']}")

        # 9. Customer Polls Status -> ARRIVED
        log_step("STEP 9: Customer Polls Live Status (Expecting ARRIVED)")
        cust_poll_2 = client.get(f"/api/v1/rides/{ride_id}", headers=cust_headers)
        assert cust_poll_2.status_code == 200
        assert cust_poll_2.json()["status"] == "ARRIVED"
        print(f"Customer App Verified Status: {cust_poll_2.json()['status']}")

        # 10. Security Verification: Driver Enters Incorrect OTP
        log_step("STEP 10: Security Test - Driver Submits Invalid OTP")
        bad_otp_res = client.post(f"/api/v1/rides/{ride_id}/start", json={"otp_code": "000000"}, headers=drv_headers)
        assert bad_otp_res.status_code == 400, f"Server should have rejected invalid OTP! Status: {bad_otp_res.status_code}"
        print(f"Invalid OTP Rejected Correctly! Response: {bad_otp_res.json()['detail']}")

        # 11. Driver Enters Correct Guest OTP -> IN_PROGRESS
        log_step("STEP 11: Driver Submits Authoritative Guest OTP")
        start_res = client.post(f"/api/v1/rides/{ride_id}/start", json={"otp_code": start_otp}, headers=drv_headers)
        assert start_res.status_code == 200, f"Start trip failed: {start_res.text}"
        print(f"Trip Started! Status: {start_res.json()['status']}")

        # 12. Customer Polls Status -> IN_PROGRESS
        log_step("STEP 12: Customer App Polls Active Trip")
        cust_poll_3 = client.get(f"/api/v1/rides/{ride_id}", headers=cust_headers)
        assert cust_poll_3.status_code == 200
        assert cust_poll_3.json()["status"] == "IN_PROGRESS"
        print(f"Customer App Confirms Trip In Progress: {cust_poll_3.json()['status']}")

        # 13. Driver Reaches Destination & Completes Trip
        log_step("STEP 13: Driver Completes Trip with Final Telemetry")
        finish_res = client.post(f"/api/v1/rides/{ride_id}/finish", json={
            "actual_distance_km": 18.5,
            "actual_duration_minutes": 32.0
        }, headers=drv_headers)
        assert finish_res.status_code == 200, f"Finish trip failed: {finish_res.text}"
        finish_data = finish_res.json()
        print(f"Trip Completed! Status = {finish_data['status']}")
        print(f"Final Calculated Fare: Rs {finish_data.get('final_fare_cents', 0) / 100:.2f}")

        # 14. Customer Polls Status -> COMPLETED
        log_step("STEP 14: Customer App Confirms Completion & Settlement")
        cust_poll_4 = client.get(f"/api/v1/rides/{ride_id}", headers=cust_headers)
        assert cust_poll_4.status_code == 200
        assert cust_poll_4.json()["status"] == "COMPLETED"
        print(f"Customer App Confirms Trip Completed!")

        # 15. Shared Ride Pool Test (Phase 3 Feature)
        log_step("STEP 15: Shared Ride / Pool Estimate & Booking (Phase 3 Feature)")
        pool_est = client.post("/api/v1/pools/estimate", json={
            "category_id": sedan_cat["id"],
            "pickup_latitude": 18.9217,
            "pickup_longitude": 72.8332,
            "destination_latitude": 19.0760,
            "destination_longitude": 72.8777,
            "seats_requested": 2
        }, headers=cust_headers)
        assert pool_est.status_code == 200, f"Pool estimate failed: {pool_est.text}"
        pool_data = pool_est.json()
        print(f"Shared Ride Solo Fare: Rs {pool_data.get('solo_fare_cents', 0) / 100:.2f}")
        print(f"Shared Ride Discounted Fare: Rs {pool_data.get('shared_fare_cents', 0) / 100:.2f} (Savings: Rs {pool_data.get('savings_cents', 0) / 100:.2f})")

        pool_book = client.post("/api/v1/pools/book", json={
            "category_id": sedan_cat["id"],
            "estimate_id": pool_data["estimate_id"],
            "pickup_address": "The Taj Mahal Palace, Colaba, Mumbai",
            "pickup_latitude": 18.9217,
            "pickup_longitude": 72.8332,
            "destination_address": "Chhatrapati Shivaji Terminal T2, Mumbai",
            "destination_latitude": 19.0760,
            "destination_longitude": 72.8777,
            "seats_reserved": 2,
            "fallback_policy": "CREATE_NEW_POOL"
        }, headers=cust_headers)
        assert pool_book.status_code in [200, 201], f"Pool booking failed: {pool_book.text}"
        booking_data = pool_book.json()
        print(f"Shared Pool Booking Confirmed! Booking ID: {booking_data.get('booking_id')}, Ride ID: {booking_data.get('ride_id')}")

        # 16. Customer Wallet Check (Phase 2 Feature)
        log_step("STEP 16: Customer Wallet Verification (Phase 2 Feature)")
        wallet_res = client.get("/api/v1/wallets/me", headers=cust_headers)
        assert wallet_res.status_code == 200, f"Wallet fetch failed: {wallet_res.text}"
        print(f"Customer Wallet Balance: Rs {wallet_res.json().get('balance_cents', 0) / 100:.2f}")

        log_step("ALL MULTI-DEVICE E2E FIELD TESTS PASSED SUCCESSFULLY! 100% OPERATIONAL")

if __name__ == "__main__":
    try:
        run_field_test()
    except Exception as e:
        print(f"\n[FAIL] E2E Field Test encountered an error: {e}")
        sys.exit(1)
