import asyncio
import sys
import os
sys.path.insert(0, r"c:\androidAi")

from httpx import AsyncClient, ASGITransport
from backend.main import app

async def test_otp_and_fares():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        # 1. Health check
        h = await client.get("/health/live")
        print("Health Check:", h.status_code, h.json())
        
        # 2. Test OTP Request with a test phone
        phone = "+919876543210"
        otp_req = await client.post("/api/v1/auth/otp/request", json={"phone_number": phone})
        print("OTP Request Status:", otp_req.status_code, otp_req.json())
        
        # Read the OTP from Upstash Redis directly to verify it
        from backend.infrastructure.cache.redis_client import redis_client
        import json
        otp_key = f"otp_val:{phone}"
        cached_data = await redis_client.get(otp_key)
        print(f"Retrieved OTP payload from Upstash Redis: {cached_data}")
        cached_otp = None
        if cached_data:
            if isinstance(cached_data, str):
                cached_otp = json.loads(cached_data).get("otp")
            elif isinstance(cached_data, bytes):
                cached_otp = json.loads(cached_data.decode("utf-8")).get("otp")
        
        if cached_otp:
            # 3. Test OTP Verify
            verify_payload = {
                "phone_number": phone,
                "otp_code": cached_otp,
                "role": "PASSENGER"
            }
            v_res = await client.post("/api/v1/auth/otp/verify", json=verify_payload)
            print("OTP Verify Status:", v_res.status_code)
            if v_res.status_code == 200:
                data = v_res.json()
                print("OTP Verify Success! Issued tokens:")
                print(f"  Access Token (truncated): {data.get('access_token')[:25]}...")
                print(f"  User: {data.get('user')}")
            else:
                print("OTP Verify Failed:", v_res.text)
                
        # 4. Test Vehicle Categories
        vc = await client.get("/api/v1/vehicles/categories")
        print("\nVehicle Categories Status:", vc.status_code)
        categories = vc.json()
        target_category_id = None
        for cat in categories:
            print(f"  - {cat.get('code')}: {cat.get('name')} | ID: {cat.get('id')} | Capacity: {cat.get('capacity')}")
            if cat.get('code') == "E_RICKSHAW_STANDARD":
                target_category_id = cat.get('id')

        # 5. Test Fare Estimate
        if target_category_id:
            fare_payload = {
                "pickup_latitude": 28.6139,
                "pickup_longitude": 77.2090,
                "destination_latitude": 28.6250,
                "destination_longitude": 77.2180,
                "category_id": target_category_id
            }
            f_res = await client.post("/api/v1/fares/estimate", json=fare_payload)
            print("\nFare Estimate Status:", f_res.status_code)
            if f_res.status_code == 200:
                print("Fare Estimate Result:", f_res.json())
            else:
                print("Fare Estimate Response:", f_res.text)

        # 6. Test Compare All Classes
        compare_payload = {
            "pickup_latitude": 28.6139,
            "pickup_longitude": 77.2090,
            "destination_latitude": 28.6250,
            "destination_longitude": 77.2180
        }
        c_res = await client.post("/api/v1/fares/compare", json=compare_payload)
        print("\nCompare Vehicle Fares Status:", c_res.status_code)
        if c_res.status_code == 200:
            for item in c_res.json():
                est = item.get('estimate', {})
                print(f"  Class: {item.get('name')} ({item.get('code')}) -> Total: Rs {est.get('estimated_fare_rupees')} (Base: Rs {est.get('base_fare_rupees')})")
        else:
            print("Compare Fares Response:", c_res.text)

if __name__ == "__main__":
    asyncio.run(test_otp_and_fares())
