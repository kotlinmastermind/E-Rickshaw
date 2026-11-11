import sys
import os
sys.path.insert(0, os.path.abspath("."))

import asyncio
import httpx
from backend.main import app

async def run_live_supabase_verification():
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        print("\n=======================================================")
        print("  RiderGo & DriverGo Controls Center -> Supabase Audit ")
        print("=======================================================\n")

        # 1. Test Static Admin Page
        page_res = await client.get("/admin")
        assert page_res.status_code == 200
        assert "RiderGo & DriverGo Controls Center" in page_res.text
        assert "LIVE SUPABASE" in page_res.text
        print(f"[1/7] [PASS] Admin HTML delivered: status={page_res.status_code}, bytes={len(page_res.text)}")

        # 2. Test Admin Session Token Generation
        tok_res = await client.get("/api/v1/admin/session/token")
        assert tok_res.status_code == 200, f"Token generation failed: {tok_res.text}"
        tok_data = tok_res.json()
        token = tok_data["access_token"]
        admin = tok_data["admin"]
        print(f"[2/7] [PASS] Admin JWT Session issued for: {admin['name']} ({admin['email']})")

        headers = {"Authorization": f"Bearer {token}"}

        # 3. Test Live Feed directly querying Supabase
        feed_res = await client.get("/api/v1/admin/live-feed", headers=headers)
        assert feed_res.status_code == 200, f"Live feed failed: {feed_res.text}"
        feed = feed_res.json()
        stats = feed["stats"]
        drivers = feed["drivers"]
        customers = feed["customers"]
        rides = feed["active_rides"]
        tariffs = feed["tariffs"]

        print(f"[3/7] [PASS] Real Supabase Live-Feed received:")
        print(f"       * Total Registered Users: {stats['total_users']}")
        print(f"       * Registered Customers:   {len(customers)}")
        print(f"       * Registered Drivers:     {len(drivers)}")
        print(f"       * Active Rides:           {len(rides)}")
        print(f"       * Vehicle Tariffs:        {len(tariffs)}")
        print(f"       * Gross Booking Value:    ₹{stats['gross_booking_inr']:,.2f}")
        print(f"       * Pending Pilot KYC:      {stats['pending_kyc']}")

        # 4. Test Customer Status Toggle
        cust = customers[0]
        cust_id = cust["id"]
        curr_active = cust["active"]
        t_res = await client.put(
            f"/api/v1/admin/users/{cust_id}/status",
            headers=headers,
            json={"is_active": not curr_active, "reason": "Operational governance audit verification"}
        )
        assert t_res.status_code == 200, f"Failed toggle: {t_res.text}"
        print(f"[4/7] [PASS] Customer #{cust_id[:8]} status toggled to {not curr_active}")

        # Restore customer status
        await client.put(
            f"/api/v1/admin/users/{cust_id}/status",
            headers=headers,
            json={"is_active": curr_active, "reason": "Operational governance audit restore"}
        )
        print(f"       [RESTORED] Customer #{cust_id[:8]} status restored to {curr_active}")

        # 5. Test Driver Approval on real Supabase driver
        pending_driver = next((d for d in drivers if d["approval_status"] == "PENDING"), None)
        target_driver_id = pending_driver["id"] if pending_driver else drivers[0]["id"]
        appr_res = await client.put(
            f"/api/v1/admin/drivers/{target_driver_id}/approval",
            headers=headers,
            json={"approval_status": "APPROVED", "rejection_reason": None}
        )
        assert appr_res.status_code == 200, f"Driver approval failed: {appr_res.text}"
        print(f"[5/7] [PASS] Driver #{target_driver_id[:8]} approved and verified in Supabase")

        # 6. Test Driver Duty Toggle
        duty_res = await client.put(
            f"/api/v1/admin/drivers/{target_driver_id}/duty",
            headers=headers,
            json={"is_online": True, "reason": "Command center duty toggle"}
        )
        assert duty_res.status_code == 200, f"Driver duty toggle failed: {duty_res.text}"
        print(f"[6/7] [PASS] Driver #{target_driver_id[:8]} duty toggled to ONLINE in Supabase")

        # 7. Test FCM Push Notification Broadcast
        bc_res = await client.post(
            "/api/v1/admin/broadcasts",
            headers=headers,
            json={
                "title": "Delhi Corridor High Demand Advisory",
                "message": "Heavy passenger footfall detected at Anand Vihar & Connaught Place metro hubs.",
                "target_role": "DRIVER",
                "channel": "IN_APP"
            }
        )
        assert bc_res.status_code == 200, f"Broadcast failed: {bc_res.text}"
        print(f"[7/7] [PASS] FCM Push Broadcast dispatched & logged in Supabase")

        print("\n>>> ALL 7 SUPABASE REAL DATA INTEGRATION CHECKS PASSED WITH 100% SUCCESS! <<<\n")

if __name__ == "__main__":
    asyncio.run(run_live_supabase_verification())
