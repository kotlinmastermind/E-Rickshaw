import pytest
import uuid
from datetime import datetime, timezone, timedelta
import httpx
from httpx import ASGITransport
from sqlalchemy import select

from backend.main import app
from backend.domain.models import (
    User, CustomerProfile, VehicleCategory, FareRule, Ride, Session,
    CorporateAccount, CorporateEmployee, CorporateTravelPolicy, CorporateInvoice, CorporateInvoiceItem
)
from backend.infrastructure.security.crypto import create_jwt_token

@pytest.mark.asyncio
async def test_corporate_account_registration_and_validation(db_session):
    transport = ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        # Create test customer
        phone = f"+9198{uuid.uuid4().hex[:8]}"
        u_cust = User(
            id=str(uuid.uuid4()),
            phone_number=phone,
            full_name="Corporate Test Rider",
            role="customer",
            is_active=True,
            is_verified=True,
            email="corp.rider@company.in"
        )
        sess = Session(
            id=str(uuid.uuid4()),
            user_id=u_cust.id,
            token_hash=str(uuid.uuid4()),
            is_revoked=False,
            expires_at=datetime.now(timezone.utc) + timedelta(days=1)
        )
        db_session.add_all([u_cust, sess])
        await db_session.commit()

        # Generate auth token
        token = create_jwt_token(user_id=u_cust.id, role="customer", session_id=sess.id)
        headers = {"Authorization": f"Bearer {token}"}

        # 1. Invalid GSTIN test
        res_bad = await client.post(
            "/api/v1/corporate/accounts",
            headers=headers,
            json={
                "company_name": "Acme Corp",
                "tax_id": "INVALID_GST",
                "billing_email": "billing@acme.in"
            }
        )
        assert res_bad.status_code in [400, 422]

        # 2. Valid Registration
        valid_gstin = "07AAACG1234F1Z5"
        res_reg = await client.post(
            "/api/v1/corporate/accounts",
            headers=headers,
            json={
                "company_name": "Acme Technologies Private Limited",
                "tax_id": valid_gstin,
                "billing_email": "billing@acme.in",
                "billing_contact_name": "Corporate Test Rider",
                "default_cost_center": "OPS-NCR",
                "department": "Engineering"
            }
        )
        assert res_reg.status_code == 201
        data = res_reg.json()
        assert data["company_name"] == "Acme Technologies Private Limited"
        assert data["tax_id"] == valid_gstin
        assert data["status"] == "PENDING_APPROVAL"
        assert data["membership"]["cost_center"] == "OPS-NCR"
        assert len(data["policies"]) >= 1

        # 3. Verify Database Persistence
        stmt_corp = select(CorporateAccount).where(CorporateAccount.tax_id == valid_gstin)
        corp = (await db_session.execute(stmt_corp)).scalar_one()
        assert corp.admin_user_id == u_cust.id
        assert corp.credit_limit_cents == 50000000

        # 4. Fetch /accounts/me
        res_me = await client.get("/api/v1/corporate/accounts/me", headers=headers)
        assert res_me.status_code == 200
        assert res_me.json()["id"] == corp.id


@pytest.mark.asyncio
async def test_corporate_ride_booking_policy_enforcement(db_session):
    transport = ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        # Setup test entities
        phone = f"+9198{uuid.uuid4().hex[:8]}"
        u_cust = User(
            id=str(uuid.uuid4()),
            phone_number=phone,
            full_name="Executive Employee",
            role="customer",
            is_active=True,
            is_verified=True,
            email="executive@globalcorp.in"
        )
        db_session.add(u_cust)

        # Vehicle Category & FareRule
        cat = VehicleCategory(
            id=str(uuid.uuid4()),
            code=f"CORP_SEDAN_{uuid.uuid4().hex[:4]}",
            name="Executive Sedan",
            is_active=True
        )
        db_session.add(cat)
        await db_session.flush()

        rule = FareRule(
            id=str(uuid.uuid4()),
            category_id=cat.id,
            city_code="DELHI_NCR",
            base_fare_cents=5000,
            per_km_cents=1500,
            per_minute_cents=200,
            minimum_fare_cents=6000,
            cancellation_fee_cents=3000,
            surge_multiplier=1.0,
            is_active=True
        )
        db_session.add(rule)

        # Setup Corporate Account in PENDING_APPROVAL state initially
        corp = CorporateAccount(
            id=str(uuid.uuid4()),
            company_name="Global Enterprise Inc",
            tax_id="06AAAAA0000A1Z5",
            billing_email="accounts@globalcorp.in",
            status="PENDING_APPROVAL",
            credit_limit_cents=20000000,
            balance_due_cents=0,
            monthly_spending_limit_cents=10000000,
            default_cost_center="EXEC-HQ"
        )
        db_session.add(corp)
        await db_session.flush()

        emp = CorporateEmployee(
            id=str(uuid.uuid4()),
            corporate_id=corp.id,
            user_id=u_cust.id,
            employee_id="EMP-EXEC-99",
            employee_name="Executive Employee",
            employee_email="executive@globalcorp.in",
            department="Executive",
            cost_center="EXEC-HQ",
            status="ACTIVE",
            per_ride_limit_cents=200000,  # ₹2,000 max per ride
            approval_required=False
        )
        policy = CorporateTravelPolicy(
            id=str(uuid.uuid4()),
            corporate_id=corp.id,
            policy_name="Executive Travel Policy",
            max_fare_per_ride_cents=300000,
            is_active=True
        )
        cust_profile = CustomerProfile(
            id=str(uuid.uuid4()),
            user_id=u_cust.id
        )
        sess = Session(
            id=str(uuid.uuid4()),
            user_id=u_cust.id,
            token_hash=str(uuid.uuid4()),
            is_revoked=False,
            expires_at=datetime.now(timezone.utc) + timedelta(days=1)
        )
        db_session.add_all([cust_profile, emp, policy, sess])
        await db_session.commit()

        token = create_jwt_token(user_id=u_cust.id, role="customer", session_id=sess.id)
        headers = {"Authorization": f"Bearer {token}"}

        # Obtain fare estimate
        est_resp = await client.post("/api/v1/fares/estimate", json={
            "category_id": cat.id,
            "pickup_latitude": 28.6304,
            "pickup_longitude": 77.2177,
            "destination_latitude": 28.5480,
            "destination_longitude": 77.1200
        })
        assert est_resp.status_code == 200
        est_id = est_resp.json()["estimate_id"]

        # 1. Booking must fail because account is PENDING_APPROVAL
        res_pending = await client.post(
            "/api/v1/rides/advanced-request",
            headers=headers,
            json={
                "estimate_id": est_id,
                "category_id": cat.id,
                "pickup_address": "CP Inner Circle, New Delhi",
                "destination_address": "Aerocity, New Delhi",
                "pickup_latitude": 28.6304,
                "pickup_longitude": 77.2177,
                "destination_latitude": 28.5480,
                "destination_longitude": 77.1200,
                "payment_method": "CORPORATE_BILLING",
                "booking_mode": "CORPORATE_BILLING"
            }
        )
        assert res_pending.status_code == 403
        assert "PENDING_APPROVAL" in res_pending.json()["detail"]

        # 2. Approve Corporate Account
        corp.status = "APPROVED"
        await db_session.commit()

        # Generate a fresh estimate for valid booking
        est_resp2 = await client.post("/api/v1/fares/estimate", json={
            "category_id": cat.id,
            "pickup_latitude": 28.6304,
            "pickup_longitude": 77.2177,
            "destination_latitude": 28.5480,
            "destination_longitude": 77.1200
        })
        assert est_resp2.status_code == 200
        est_id2 = est_resp2.json()["estimate_id"]

        # 3. Booking must now succeed with valid corporate billing
        res_book = await client.post(
            "/api/v1/rides/advanced-request",
            headers=headers,
            json={
                "estimate_id": est_id2,
                "category_id": cat.id,
                "pickup_address": "CP Inner Circle, New Delhi",
                "destination_address": "Aerocity, New Delhi",
                "pickup_latitude": 28.6304,
                "pickup_longitude": 77.2177,
                "destination_latitude": 28.5480,
                "destination_longitude": 77.1200,
                "payment_method": "CORPORATE_BILLING",
                "booking_mode": "CORPORATE_BILLING"
            }
        )
        assert res_book.status_code == 200
        ride_data = res_book.json()
        assert ride_data["corporate_id"] == corp.id
        assert ride_data["corporate_billing_ref"] == f"CORP-{corp.id[:8].upper()}-EMP-EXEC-99"
        assert ride_data["booking_mode"] == "CORPORATE_BILLING"

        # Verify Database ride record
        stmt_ride = select(Ride).where(Ride.id == ride_data["ride_id"])
        saved_ride = (await db_session.execute(stmt_ride)).scalar_one()
        assert saved_ride.corporate_id == corp.id
        assert saved_ride.business_cost_center == "EXEC-HQ"
        assert saved_ride.business_tax_id == "06AAAAA0000A1Z5"
        assert saved_ride.payment_method == "CORPORATE_BILLING"


@pytest.mark.asyncio
async def test_monthly_consolidated_invoice_generation(db_session):
    # Setup approved corporate account
    corp = CorporateAccount(
        id=str(uuid.uuid4()),
        company_name="Vaisentra Global Logistics Ltd",
        tax_id="07BBBBB1111B1Z9",
        billing_email="invoices@vaisentraglobal.com",
        status="APPROVED",
        credit_limit_cents=50000000,
        balance_due_cents=0,
        default_cost_center="LOGISTICS-NORTH"
    )
    db_session.add(corp)
    await db_session.flush()

    # Create 2 completed rides for this corporate account
    now = datetime.now(timezone.utc)
    ride1 = Ride(
        id=str(uuid.uuid4()),
        customer_id=str(uuid.uuid4()),
        vehicle_category_id="cat-sedan",
        fare_rule_id="rule-test",
        status="COMPLETED",
        pickup_address="Anand Vihar ISBT",
        destination_address="Preet Vihar",
        pickup_latitude=28.6469,
        pickup_longitude=77.3160,
        destination_latitude=28.6300,
        destination_longitude=77.2900,
        estimated_distance_km=5.0,
        estimated_duration_minutes=15.0,
        estimated_fare_cents=10000,  # ₹100
        final_fare_cents=10000,
        payment_method="CORPORATE_BILLING",
        start_otp="1234",
        booking_mode="CORPORATE_BILLING",
        corporate_id=corp.id,
        corporate_employee_id="EMP-101",
        business_cost_center="LOGISTICS-NORTH",
        completed_at=now - timedelta(days=2)
    )
    ride2 = Ride(
        id=str(uuid.uuid4()),
        customer_id=str(uuid.uuid4()),
        vehicle_category_id="cat-sedan",
        fare_rule_id="rule-test",
        status="COMPLETED",
        pickup_address="Noida Sec 62",
        destination_address="Mayur Vihar",
        pickup_latitude=28.6280,
        pickup_longitude=77.3670,
        destination_latitude=28.6080,
        destination_longitude=77.2990,
        estimated_distance_km=8.0,
        estimated_duration_minutes=25.0,
        start_otp="1234",
        estimated_fare_cents=20000,  # ₹200
        final_fare_cents=20000,
        payment_method="CORPORATE_BILLING",
        booking_mode="CORPORATE_BILLING",
        corporate_id=corp.id,
        corporate_employee_id="EMP-102",
        business_cost_center="LOGISTICS-NORTH",
        completed_at=now - timedelta(days=1)
    )
    db_session.add_all([ride1, ride2])
    await db_session.commit()

    # Generate consolidated invoice via service
    from backend.application.services.corporate_billing_service import CorporateBillingService
    service = CorporateBillingService(db_session)
    invoice = await service.generate_consolidated_invoice(corp.id, "2026-11")

    assert invoice["billing_period"] == "2026-11"
    assert invoice["company_name"] == "Vaisentra Global Logistics Ltd"
    # Subtotal: ₹300 (30,000 cents)
    assert invoice["subtotal_rupees"] == 300.0
    # 5% GST: ₹15.00
    assert invoice["tax_rupees"] == 15.0
    # Total: ₹300 + ₹15 + 2% platform fee (₹6) = ₹321.00
    assert invoice["total_rupees"] == 321.0
    assert len(invoice["line_items"]) == 2

    # Check that rides received invoice_number
    await db_session.refresh(ride1)
    await db_session.refresh(ride2)
    assert ride1.invoice_number == invoice["invoice_number"]
    assert ride2.invoice_number == invoice["invoice_number"]

    # Verify duplicate prevention: calling again returns the existing invoice
    inv_dup = await service.generate_consolidated_invoice(corp.id, "2026-11")
    assert inv_dup["id"] == invoice["id"]
