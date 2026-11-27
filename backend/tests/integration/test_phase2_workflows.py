import pytest
import httpx
from httpx import ASGITransport
from backend.main import app

@pytest.mark.asyncio
async def test_phase2_customer_and_admin_workflows(db_session):
    transport = ASGITransport(app=app)

    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        # 1. Authenticate Customer (Password-based)
        cust_phone = "+919876543999"
        cust_signup = await client.post("/api/v1/auth/signup/customer", json={
            "full_name": "VIP Customer Phase2",
            "email": "cust_phase2@vaisentra.com",
            "phone_number": cust_phone,
            "password": "Password123!"
        })
        assert cust_signup.status_code == 200, cust_signup.text
        cust_token = cust_signup.json()["access_token"]
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

        # 3. Customer Wallet Topup & History via Authoritative Verified Flow
        import hmac
        import hashlib
        from backend.core.config import settings
        from backend.integrations.payments.provider import get_payment_provider

        # A: Deprecated direct topup must return 410 Gone
        dep_resp = await client.post("/api/v1/wallets/top-up", json={
            "amount_cents": 500000,
            "payment_method": "UPI"
        }, headers=cust_headers)
        assert dep_resp.status_code == 410

        # B: Verified top-up via create-order and verify
        topup_order = await client.post("/api/v1/wallets/topup/create-order", json={
            "amount_cents": 500000,
            "currency": "INR"
        }, headers=cust_headers)
        assert topup_order.status_code == 200
        order_data = topup_order.json()

        secret = "test_phase2_secret_key_123"
        provider = get_payment_provider()
        orig_secret = provider.key_secret
        provider.key_secret = secret

        try:
            msg = f"{order_data['order_id']}|pay_test_topup_123"
            sig = hmac.new(secret.encode("utf-8"), msg.encode("utf-8"), hashlib.sha256).hexdigest()
            verify_resp = await client.post("/api/v1/wallets/topup/verify", json={
                "razorpay_order_id": order_data["order_id"],
                "razorpay_payment_id": "pay_test_topup_123",
                "razorpay_signature": sig
            }, headers=cust_headers)
            assert verify_resp.status_code == 200, verify_resp.text
            assert verify_resp.json()["balance_cents"] >= 500000
        finally:
            provider.key_secret = orig_secret

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
        from datetime import datetime, timezone, timedelta
        from backend.domain.models import User, Session
        from backend.infrastructure.security.crypto import hash_password, create_jwt_token, hash_secret, generate_secure_otp
        from backend.api.deps import get_current_admin

        admin_user = User(
            phone_number="+919999900002",
            email="admin_phase2@vaisentra.com",
            username="admin_phase2",
            full_name="Admin Phase2",
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
        app.dependency_overrides[get_current_admin] = lambda: admin_user

        try:
            # 6. Admin Geofence CRUD & Point Verification
            geo_resp = await client.post("/api/v1/admin/geofences", json={
                "name": "Jaipur Test Service Corridor",
                "city_code": "MUMBAI",
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
                "zone_name": "Jaipur Test Service Corridor",
                "multiplier": 1.4,
                "reason": "Late night high-demand gala"
            }, headers=admin_headers)
            assert surge_resp.status_code == 200

            comm_resp = await client.post("/api/v1/admin/commissions", json={
                "rule_name": "Standard Fleet Take-Rate",
                "commission_percentage": 20.0,
                "fixed_cut_cents": 0,
                "reason": "Standard fleet commission update for city operation"
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
        finally:
            app.dependency_overrides.pop(get_current_admin, None)
