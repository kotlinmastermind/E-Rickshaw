import asyncio
import sys
sys.path.insert(0, r"c:\androidAi")

from httpx import AsyncClient, ASGITransport
from backend.main import app

async def test_backend_live():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        # 1. Health check
        h = await client.get("/health/live")
        print("Health Check:", h.status_code, h.json())
        
        # 2. Vehicle Categories from Supabase
        vc = await client.get("/api/v1/vehicles/categories")
        print("Vehicle Categories Status:", vc.status_code)
        for cat in vc.json():
            print(f"  Category: {cat.get('code')} -> {cat.get('name')} [Capacity: {cat.get('capacity')}]")
            
        # 3. OTP Request (Verify no debug_otp leak)
        otp_res = await client.post("/api/v1/auth/otp/request", json={"phone_number": "+919876543210"})
        print("OTP Request Status:", otp_res.status_code, otp_res.json())

if __name__ == "__main__":
    asyncio.run(test_backend_live())
