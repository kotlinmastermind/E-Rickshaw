import pytest
from httpx import AsyncClient, ASGITransport
from datetime import datetime, timezone
from sqlalchemy import select

from backend.main import app
from backend.domain.models import User, CustomerProfile, DriverProfile, Ride, VehicleCategory, FareRule
from backend.infrastructure.security.crypto import hash_password

@pytest.mark.asyncio
async def test_phase1_customer_and_driver_contract_alignments(db_session):
    cat = (await db_session.execute(select(VehicleCategory))).scalars().first()
    rule = (await db_session.execute(select(FareRule).where(FareRule.category_id == cat.id))).scalars().first()

    # 1. Setup Customer User & Profile
    cust_user = User(
        email="customer.phase1@vaisentra.com",
        phone_number="+919876543201",
        password_hash=hash_password("Phase1CustPass!"),
        role="customer",
        is_active=True
    )
    db_session.add(cust_user)
    await db_session.flush()

    cust_profile = CustomerProfile(user_id=cust_user.id)
    db_session.add(cust_profile)

    # 2. Setup Driver User & Profile
    driver_user = User(
        email="driver.phase1@vaisentra.com",
        phone_number="+919876543202",
        password_hash=hash_password("Phase1DriverPass!"),
        role="driver",
        is_active=True
    )
    db_session.add(driver_user)
    await db_session.flush()

    driver_profile = DriverProfile(
        user_id=driver_user.id,
        license_number="DL-PHASE1-9988",
        approval_status="APPROVED"
    )
    db_session.add(driver_profile)
    await db_session.flush()

    # 3. Setup Completed Ride
    ride = Ride(
        customer_id=cust_profile.id,
        assigned_driver_id=driver_profile.id,
        vehicle_category_id=cat.id,
        fare_rule_id=rule.id,
        start_otp="1234",
        status="COMPLETED",
        pickup_address="Aerocity Terminal 3",
        destination_address="Cyber City Tower A",
        pickup_latitude=28.5562,
        pickup_longitude=77.0878,
        destination_latitude=28.4952,
        destination_longitude=77.0891,
        estimated_fare_cents=120000,
        final_fare_cents=120000,
        estimated_distance_km=10.0,
        estimated_duration_minutes=20.0,
        tip_cents=20000,
        requested_at=datetime.now(timezone.utc),
        completed_at=datetime.now(timezone.utc)
    )
    db_session.add(ride)
    await db_session.commit()

    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        # A. Verify JWT Authentication Enforcement (Unauthenticated 401)
        unauth_resp = await client.get("/api/v1/loyalty-feedback/statements/monthly?year=2026&month=11")
        assert unauth_resp.status_code == 401

        unauth_driver_resp = await client.get("/api/v1/drivers/financials/goals/weekly")
        assert unauth_driver_resp.status_code == 401

        # B. Customer Authentication
        cust_login = await client.post(
            "/api/v1/auth/password/login",
            json={"email": "customer.phase1@vaisentra.com", "password": "Phase1CustPass!"}
        )
        assert cust_login.status_code == 200, cust_login.text
        cust_token = cust_login.json()["access_token"]
        cust_headers = {"Authorization": f"Bearer {cust_token}"}

        # C. Driver Authentication
        driver_login = await client.post(
            "/api/v1/auth/password/login",
            json={"email": "driver.phase1@vaisentra.com", "password": "Phase1DriverPass!"}
        )
        assert driver_login.status_code == 200, driver_login.text
        driver_token = driver_login.json()["access_token"]
        driver_headers = {"Authorization": f"Bearer {driver_token}"}

        # -------------------------------------------------------------
        # 1. Customer: Driver Rating (/rate and /rating)
        # -------------------------------------------------------------
        rating_payload_android = {
            "overall_score": 5.0,
            "overall_rating": 5,
            "cleanliness_score": 5,
            "comfort_score": 5,
            "service_quality_score": 5,
            "navigation_score": 5,
            "public_comment": "Flawless ride and executive presentation."
        }
        res_rate = await client.post(
            f"/api/v1/loyalty-feedback/rides/{ride.id}/rate",
            json=rating_payload_android,
            headers=cust_headers
        )
        assert res_rate.status_code == 200, res_rate.text
        assert res_rate.json().get("status") in ["SUCCESS", "SUBMITTED", "UPDATED", "PROCESSED"] or "message" in res_rate.json()

        # Test alias /rating
        res_rating_alias = await client.post(
            f"/api/v1/loyalty-feedback/rides/{ride.id}/rating",
            json=rating_payload_android,
            headers=cust_headers
        )
        assert res_rating_alias.status_code in [200, 400], res_rating_alias.text

        # -------------------------------------------------------------
        # 2. Customer: Offer Preferences (/profile/offer-preferences and /offers/preferences)
        # -------------------------------------------------------------
        pref_payload = {
            "frequency": "WEEKLY",
            "birthday_date": "1990-10-15",
            "anniversary_date": "2018-12-05"
        }
        res_pref = await client.post(
            "/api/v1/loyalty-feedback/profile/offer-preferences",
            json=pref_payload,
            headers=cust_headers
        )
        assert res_pref.status_code == 200, res_pref.text

        res_pref_alias = await client.post(
            "/api/v1/loyalty-feedback/offers/preferences",
            json=pref_payload,
            headers=cust_headers
        )
        assert res_pref_alias.status_code == 200, res_pref_alias.text

        # -------------------------------------------------------------
        # 3. Customer: Monthly Statement (/statements/monthly and /reports/monthly-statement)
        # -------------------------------------------------------------
        now_dt = datetime.now(timezone.utc)
        res_stmt = await client.get(
            f"/api/v1/loyalty-feedback/statements/monthly?year={now_dt.year}&month={now_dt.month}",
            headers=cust_headers
        )
        assert res_stmt.status_code == 200, res_stmt.text
        stmt_data = res_stmt.json()
        assert "statement_period" in stmt_data or "period" in stmt_data
        assert "grand_total_rupees" in stmt_data or "total_spend_rupees" in stmt_data
        assert "items" in stmt_data or "rides" in stmt_data

        res_stmt_alias = await client.get(
            f"/api/v1/loyalty-feedback/reports/monthly-statement?year={now_dt.year}&month={now_dt.month}",
            headers=cust_headers
        )
        assert res_stmt_alias.status_code == 200, res_stmt_alias.text

        # -------------------------------------------------------------
        # 4. Customer: Survey Submission (/surveys and /surveys/submit)
        # -------------------------------------------------------------
        survey_payload = {
            "ride_id": ride.id,
            "csat_score": 5,
            "nps_score": 10,
            "feedback_text": "Exceptional service by the chauffeur."
        }
        res_survey = await client.post(
            "/api/v1/loyalty-feedback/surveys",
            json=survey_payload,
            headers=cust_headers
        )
        assert res_survey.status_code == 200, res_survey.text

        res_survey_alias = await client.post(
            "/api/v1/loyalty-feedback/surveys/submit",
            json=survey_payload,
            headers=cust_headers
        )
        assert res_survey_alias.status_code == 200, res_survey_alias.text

        # -------------------------------------------------------------
        # 5. Customer: Filtered Ride History (/rides/history and /rides/history/filtered)
        # -------------------------------------------------------------
        res_hist = await client.get(
            "/api/v1/loyalty-feedback/rides/history",
            headers=cust_headers
        )
        assert res_hist.status_code == 200, res_hist.text
        hist_items = res_hist.json()
        assert isinstance(hist_items, list)
        if hist_items:
            first_ride = hist_items[0]
            assert "created_at" in first_ride
            assert "tip_cents" in first_ride
            assert "fare_rupees" in first_ride

        res_hist_alias = await client.get(
            "/api/v1/loyalty-feedback/rides/history/filtered",
            headers=cust_headers
        )
        assert res_hist_alias.status_code == 200, res_hist_alias.text

        # -------------------------------------------------------------
        # 6. Driver: Weekly Goal (/goals/weekly and /weekly-goal, GET and POST)
        # -------------------------------------------------------------
        res_goal = await client.get(
            "/api/v1/drivers/financials/goals/weekly",
            headers=driver_headers
        )
        assert res_goal.status_code == 200, res_goal.text
        goal_data = res_goal.json()
        assert "target_earnings_rupees" in goal_data or "target_net_rupees" in goal_data
        assert "current_earnings_rupees" in goal_data or "current_net_rupees" in goal_data

        # Update weekly goal using Android payload
        update_goal_payload = {
            "target_earnings_rupees": 30000.0,
            "target_net_cents": 3000000
        }
        res_up_goal = await client.post(
            "/api/v1/drivers/financials/goals/weekly",
            json=update_goal_payload,
            headers=driver_headers
        )
        assert res_up_goal.status_code == 200, res_up_goal.text

        # Test aliases (/weekly-goal with PUT)
        res_put_alias = await client.put(
            "/api/v1/drivers/financials/weekly-goal",
            json=update_goal_payload,
            headers=driver_headers
        )
        assert res_put_alias.status_code == 200, res_put_alias.text

        # -------------------------------------------------------------
        # 7. Driver: Incentives & Bonuses (/incentives and /bonuses)
        # -------------------------------------------------------------
        res_inc = await client.get(
            "/api/v1/drivers/financials/incentives",
            headers=driver_headers
        )
        assert res_inc.status_code == 200, res_inc.text

        res_bon_alias = await client.get(
            "/api/v1/drivers/financials/bonuses",
            headers=driver_headers
        )
        assert res_bon_alias.status_code == 200, res_bon_alias.text

        # -------------------------------------------------------------
        # 8. Driver: Payout Schedule (/payout-schedule and /payout-schedules)
        # -------------------------------------------------------------
        res_sched = await client.get(
            "/api/v1/drivers/financials/payout-schedule",
            headers=driver_headers
        )
        assert res_sched.status_code == 200, res_sched.text

        res_sched_alias = await client.get(
            "/api/v1/drivers/financials/payout-schedules",
            headers=driver_headers
        )
        assert res_sched_alias.status_code == 200, res_sched_alias.text

        # -------------------------------------------------------------
        # 9. Driver: Payout Reconciliation (/reconciliation and /payouts/reconciliation)
        # -------------------------------------------------------------
        res_rec = await client.get(
            "/api/v1/drivers/financials/reconciliation",
            headers=driver_headers
        )
        assert res_rec.status_code == 200, res_rec.text

        res_rec_alias = await client.get(
            "/api/v1/drivers/financials/payouts/reconciliation",
            headers=driver_headers
        )
        assert res_rec_alias.status_code == 200, res_rec_alias.text

        # -------------------------------------------------------------
        # 10. Driver: CSV Export (/export/csv and /export-csv)
        # -------------------------------------------------------------
        res_csv = await client.get(
            "/api/v1/drivers/financials/export/csv",
            headers=driver_headers
        )
        assert res_csv.status_code == 200, res_csv.text
        assert "text/csv" in res_csv.headers.get("content-type", "")

        res_csv_alias = await client.get(
            "/api/v1/drivers/financials/export-csv",
            headers=driver_headers
        )
        assert res_csv_alias.status_code == 200, res_csv_alias.text
