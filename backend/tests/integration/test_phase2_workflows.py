import pytest
import httpx
from httpx import ASGITransport
from backend.main import app
from backend.integrations.sms.provider import get_sms_provider

@pytest.mark.asyncio
async def test_phase2_customer_and_admin_workflows():
    transport = ASGITransport(app=app)
    sms_mock = get_sms_provider()

    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        # 1. Authenticate Customer
        cust_phone = "+919876543999"
        await client.post("/api/v1/auth/otp/request", json={"phone_number": cust_phone})
        cust_otp = sms_mock.get_latest_otp(cust_phone)
        cust_auth = await client.post("/api/v1/auth/otp/verify", json={
            "phone_number": cust_phone,
            "otp_code": cust_otp,
            "role": "customer"
        })
        assert cust_auth.status_code == 200
        cust_token = cust_auth.json()["access_token"]
        cust_headers = {"Authorization": f"Bearer {cust_token}"}

        # 2. Customer Favorites CRUD
        fav_resp = await client.post("/api/v1/favorites", json={
            "label": "Penthouse HQ",
            "address": "Bandra Kurla Complex, Mumbai",
            "latitude": 19.0657,
            "longitude": 72.8688
        }, headers=cust_headers)
        assert fav_resp.status_code == 200
        fav_id = fav_resp.json()["id"]

        list_favs = await client.get("/api/v1/favorites", headers=cust_headers)
        assert list_favs.status_code == 200
        assert len(list_favs.json()) >= 1

        del_fav = await client.delete(f"/api/v1/favorites/{fav_id}", headers=cust_headers)
        assert del_fav.status_code == 200

        # 3. Customer Wallet Topup & History
        topup_resp = await client.post("/api/v1/wallets/top-up", json={
            "amount_cents": 500000,
            "payment_method": "UPI"
        }, headers=cust_headers)
        assert topup_resp.status_code == 200
        assert topup_resp.json()["balance_after_cents"] >= 500000

        bal_resp = await client.get("/api/v1/wallets/me", headers=cust_headers)
        assert bal_resp.status_code == 200
        assert bal_resp.json()["balance_cents"] >= 500000

        hist_resp = await client.get("/api/v1/wallets/history", headers=cust_headers)
        assert hist_resp.status_code == 200
        assert len(hist_resp.json()) >= 1

        # 4. Ride Pass Catalog & Purchase
        passes_resp = await client.get("/api/v1/passes/catalog")
        assert passes_resp.status_code == 200

        # 5. Authenticate Admin
        admin_phone = "+919999900000"
        await client.post("/api/v1/auth/otp/request", json={"phone_number": admin_phone})
        admin_otp = sms_mock.get_latest_otp(admin_phone)
        admin_auth = await client.post("/api/v1/auth/otp/verify", json={
            "phone_number": admin_phone,
            "otp_code": admin_otp,
            "role": "admin"
        })
        assert admin_auth.status_code == 200
        admin_headers = {"Authorization": f"Bearer {admin_auth.json()['access_token']}"}

        # 6. Admin Geofence CRUD & Point Verification
        geo_resp = await client.post("/api/v1/admin/geofences", json={
            "name": "South Mumbai VIP Corridor",
            "zone_type": "SERVICE_AREA",
            "coordinates": [
                {"lat": 18.90, "lng": 72.80},
                {"lat": 19.00, "lng": 72.80},
                {"lat": 19.00, "lng": 72.90},
                {"lat": 18.90, "lng": 72.90}
            ],
            "surge_multiplier": 1.25
        }, headers=admin_headers)
        assert geo_resp.status_code == 200
        assert geo_resp.json()["status"] == "CREATED"

        verify_pt = await client.post("/api/v1/admin/geofences/verify-point", json={
            "latitude": 18.95,
            "longitude": 72.85
        }, headers=admin_headers)
        assert verify_pt.status_code == 200
        assert verify_pt.json()["is_serviceable"] is True
        assert verify_pt.json()["surge_multiplier"] >= 1.25

        # 7. Admin Surge Rules & Commission Rules
        surge_resp = await client.post("/api/v1/admin/surge/rules", json={
            "zone_name": "South Mumbai VIP Corridor",
            "multiplier": 1.4,
            "reason": "Late night high-demand gala"
        }, headers=admin_headers)
        assert surge_resp.status_code == 200

        comm_resp = await client.post("/api/v1/admin/commissions", json={
            "rule_name": "Standard Fleet Take-Rate",
            "commission_percentage": 20.0,
            "fixed_cut_cents": 0
        }, headers=admin_headers)
        assert comm_resp.status_code == 200

        # 8. Admin Broadcast Announcement
        bc_resp = await client.post("/api/v1/admin/broadcasts", json={
            "title": "VIP Fleet Priority Alert",
            "message": "All chauffeurs prepare for high volume at Jio World Convention Centre",
            "target_role": "DRIVER"
        }, headers=admin_headers)
        assert bc_resp.status_code == 200
        assert bc_resp.json()["status"] == "SENT"
