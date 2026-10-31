"""
Unit Tests for Part 11: Driver Financials, Shift Tracking, Incentives & Payouts (Features 251 to 275)
"""
import pytest
import uuid
from datetime import datetime, timezone, timedelta
from backend.domain.models import User, DriverProfile, Ride
from backend.application.services.driver_financial_service import DriverFinancialService


@pytest.mark.asyncio
async def test_part11_earnings_dashboard_and_ride_breakdown(db_session):
    u_suffix = uuid.uuid4().hex[:6]
    driver_user = User(
        phone_number=f"+91987{u_suffix}1",
        full_name="Chauffeur Arjun Rathore",
        role="driver",
        is_active=True,
        is_verified=True
    )
    db_session.add(driver_user)
    await db_session.flush()

    driver_profile = DriverProfile(
        user_id=driver_user.id,
        license_number=f"DL01-{u_suffix}",
        rating_average=4.96,
        total_trips=85,
        wallet_balance_cents=520000
    )
    db_session.add(driver_profile)
    await db_session.flush()

    # Create completed rides
    now = datetime.now(timezone.utc)
    ride1 = Ride(
        customer_id="cust-part11-1",
        assigned_driver_id=driver_profile.id,
        vehicle_category_id="cat-sedan",
        fare_rule_id="rule-sedan",
        status="COMPLETED",
        pickup_address="The Taj Mahal Hotel, Colaba",
        pickup_latitude=18.9220,
        pickup_longitude=72.8347,
        destination_address="CSM International Airport T2",
        destination_latitude=19.0896,
        destination_longitude=72.8656,
        start_otp="4821",
        estimated_distance_km=24.5,
        estimated_duration_minutes=48.0,
        estimated_fare_cents=200000,
        final_fare_cents=200000, # ₹2,000
        tip_cents=30000,         # ₹300 tip
        completed_at=now - timedelta(seconds=30)
    )
    ride2 = Ride(
        customer_id="cust-part11-2",
        assigned_driver_id=driver_profile.id,
        vehicle_category_id="cat-suv",
        fare_rule_id="rule-suv",
        status="COMPLETED",
        pickup_address="Bandra Kurla Complex",
        pickup_latitude=19.0660,
        pickup_longitude=72.8680,
        destination_address="Worli Sea Face",
        destination_latitude=19.0068,
        destination_longitude=72.8142,
        start_otp="9912",
        estimated_distance_km=14.2,
        estimated_duration_minutes=28.0,
        estimated_fare_cents=150000,
        final_fare_cents=150000, # ₹1,500
        tip_cents=0,
        completed_at=now - timedelta(days=1)
    )
    db_session.add_all([ride1, ride2])
    await db_session.commit()

    service = DriverFinancialService(db_session)

    # Feature 251 & 253: Earnings dashboard & net earnings after commission
    dash = await service.get_earnings_dashboard(driver_user.id, days=7)
    assert dash["driver_id"] == driver_profile.id
    assert dash["currency"] == "INR"
    assert dash["today"]["trips_count"] >= 1
    # 20% platform commission on ₹2,000 is ₹400; net is ₹1,600 + ₹300 tip = ₹1,900
    assert dash["today"]["gross_fare_cents"] >= 200000
    assert dash["today"]["commission_cents"] >= 40000
    assert dash["today"]["tips_cents"] >= 30000
    assert dash["today"]["net_earnings_cents"] >= 190000
    assert len(dash["daily_history"]) == 7

    # Feature 252: Itemized ride breakdown
    breakdown = await service.get_ride_earnings_breakdown(driver_user.id, ride1.id)
    assert breakdown["ride_id"] == ride1.id
    assert breakdown["fare_breakdown"]["gross_fare_cents"] == 200000
    assert breakdown["deductions_and_supplements"]["platform_commission_cents"] == 40000
    assert breakdown["deductions_and_supplements"]["customer_tip_cents"] == 30000
    assert breakdown["deductions_and_supplements"]["toll_reimbursement_cents"] == 15000
    assert breakdown["net_driver_earnings_cents"] == 205000

    # Feature 270: CSV Export
    csv_text = await service.export_earnings_csv(driver_user.id)
    assert "Ride ID" in csv_text
    assert "Gross Fare (INR)" in csv_text
    assert "The Taj Mahal Hotel, Colaba" in csv_text


@pytest.mark.asyncio
async def test_part11_shift_tracking_forecast_and_compliance(db_session):
    u_suffix = uuid.uuid4().hex[:6]
    driver_user = User(
        phone_number=f"+91987{u_suffix}2",
        full_name="Chauffeur Balraj Singh",
        role="driver",
        is_active=True,
        is_verified=True
    )
    db_session.add(driver_user)
    await db_session.flush()

    service = DriverFinancialService(db_session)

    # Feature 256: Shift Clock-in (Start Shift)
    shift_res = await service.start_shift(driver_user.id, start_odometer_km=52000.0)
    assert shift_res["status"] == "ACTIVE"
    assert "shift_id" in shift_res

    # Feature 254, 271, 272: Shift Status, Forecast & Compliance Reminders
    status_res = await service.get_current_shift_status(driver_user.id)
    assert status_res["has_active_shift"] is True
    assert "forecast" in status_res
    assert status_res["forecast"]["projected_shift_total_cents"] > 0
    assert "compliance_reminders" in status_res

    # Feature 272: Record break
    brk_res = await service.record_shift_break(driver_user.id, duration_minutes=20)
    assert brk_res["total_break_minutes"] == 20
    assert brk_res["continuous_driving_minutes_reset"] == 0

    # Feature 255: Utilization report
    util = await service.get_utilization_report(driver_user.id, days=7)
    assert "utilization_percentage" in util
    assert "total_online_hours" in util

    # Feature 256: Shift Clock-out (End Shift)
    end_res = await service.end_shift(driver_user.id, end_odometer_km=52085.0)
    assert end_res["status"] == "COMPLETED"
    assert end_res["distance_km"] == 85.0


@pytest.mark.asyncio
async def test_part11_weekly_goal_and_incentive_milestones(db_session):
    u_suffix = uuid.uuid4().hex[:6]
    driver_user = User(
        phone_number=f"+91987{u_suffix}3",
        full_name="Chauffeur Devendra Vyas",
        role="driver",
        is_active=True,
        is_verified=True
    )
    db_session.add(driver_user)
    await db_session.flush()

    service = DriverFinancialService(db_session)

    # Feature 257: Weekly earnings goal
    goal = await service.get_weekly_goal(driver_user.id)
    assert goal["target_earnings_rupees"] == 25000.0
    assert "progress_percentage" in goal

    # Update goal
    upd_goal = await service.update_weekly_goal(driver_user.id, target_earnings_rupees=35000.0, target_trips=45)
    assert upd_goal["target_earnings_rupees"] == 35000.0
    assert upd_goal["target_trips"] == 45

    # Features 258, 259, 260: Incentive tracker, milestones & bonus history
    inc = await service.get_incentive_overview(driver_user.id)
    assert inc["overall_incentive_eligibility"] is True
    assert len(inc["eligibility_checklist"]) >= 4
    assert len(inc["active_milestones"]) >= 3
    assert len(inc["bonus_history"]) >= 1

    # Feature 261: Driver wallet ledger
    ledger = await service.get_wallet_ledger(driver_user.id)
    assert "current_balance_rupees" in ledger
    assert len(ledger["ledger"]) >= 3
    assert any(e["entry_type"] == "CREDIT" for e in ledger["ledger"])


@pytest.mark.asyncio
async def test_part11_payout_accounts_schedules_and_reconciliation(db_session):
    u_suffix = uuid.uuid4().hex[:6]
    driver_user = User(
        phone_number=f"+91987{u_suffix}4",
        full_name="Chauffeur Harishankar Joshi",
        role="driver",
        is_active=True,
        is_verified=True
    )
    db_session.add(driver_user)
    await db_session.flush()

    service = DriverFinancialService(db_session)

    # Features 262 & 263: Multiple payout accounts & penny-drop verification
    accounts = await service.get_payout_accounts(driver_user.id)
    assert len(accounts) >= 1
    assert accounts[0]["verification_status"] == "VERIFIED"

    # Add secondary UPI account
    new_acc = await service.add_payout_account(driver_user.id, {
        "account_type": "UPI",
        "upi_vpa": "harishankar@okhdfcbank",
        "bank_name": "HDFC UPI Portal",
        "is_primary": False
    })
    assert new_acc["account_type"] == "UPI"
    assert new_acc["verification_status"] == "VERIFIED"

    # Set as primary
    prim_res = await service.set_primary_payout_account(driver_user.id, new_acc["id"])
    assert prim_res["success"] is True

    # Feature 264: Automated payout schedule
    sched = await service.get_payout_schedule(driver_user.id)
    assert sched["schedule_type"] == "WEEKLY"
    assert "Monday" in sched["payout_timing_disclosure"]

    # Features 265 & 266: Reconciliation statements & failure explanation
    recon = await service.get_payout_reconciliation(driver_user.id)
    assert len(recon) >= 2
    failed_item = next((r for r in recon if r["status"] == "FAILED"), None)
    assert failed_item is not None
    assert "failure_explanation" in failed_item
    assert "IFSC" in failed_item["failure_explanation"]

    # Feature 267: Tax documents (TDS Section 194C / Form 16A)
    tax_doc = await service.get_tax_documents(driver_user.id, financial_year="2025-26")
    assert tax_doc["deductor"]["tan"] == "MUMB12345C"
    assert tax_doc["tax_deductions_summary"]["applicable_tds_rate_percentage"] == 1.0
    assert len(tax_doc["available_forms"]) >= 2


@pytest.mark.asyncio
async def test_part11_expenses_forecast_and_financial_support(db_session):
    u_suffix = uuid.uuid4().hex[:6]
    driver_user = User(
        phone_number=f"+91987{u_suffix}5",
        full_name="Chauffeur Surendra Yadav",
        role="driver",
        is_active=True,
        is_verified=True
    )
    db_session.add(driver_user)
    await db_session.flush()

    service = DriverFinancialService(db_session)

    # Features 268 & 269: Log expense & EV charging / fuel capture
    exp_res = await service.log_expense(driver_user.id, {
        "category": "EV_CHARGING",
        "amount_rupees": 1250.0,
        "liters_or_kwh": 48.5,
        "unit_rate_rupees": 25.77,
        "station_or_vendor": "Tata Power EZ Charge - Bandra West",
        "odometer_km": 43210.0,
        "notes": "Full high-voltage rapid charging session prior to airport shifts."
    })
    assert exp_res["category"] == "EV_CHARGING"
    assert exp_res["amount_rupees"] == 1250.0

    exp_list = await service.get_expenses(driver_user.id)
    assert exp_list["total_expenses_rupees"] == 1250.0
    assert len(exp_list["items"]) == 1

    # Features 273 & 274: Availability forecast & zone earnings comparison
    fc_zones = await service.get_forecast_and_zones(driver_user.id)
    assert len(fc_zones["weekly_demand_forecast"]) == 7
    assert len(fc_zones["service_zones_comparison"]) == 4
    assert any("Airport" in z["zone_name"] for z in fc_zones["service_zones_comparison"])

    # Feature 275: Driver emergency financial support request
    supp_res = await service.request_financial_support(driver_user.id, {
        "support_type": "EMERGENCY_REPAIR",
        "amount_rupees": 15000.0,
        "reason": "Air conditioning compressor maintenance and coolant replenishment.",
        "repayment_terms_weeks": 4
    })
    assert supp_res["status"] == "REQUESTED"
    assert supp_res["amount_rupees"] == 15000.0

    supp_list = await service.list_financial_support_requests(driver_user.id)
    assert len(supp_list) == 1
    assert supp_list[0]["amount_rupees"] == 15000.0
