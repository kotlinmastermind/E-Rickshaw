import pytest
import uuid
from datetime import datetime, timezone, timedelta
from fastapi import HTTPException
from backend.application.services.loyalty_feedback_service import LoyaltyFeedbackService
from backend.domain.models import (
    User, CustomerProfile, DriverProfile, Ride, DriverRating,
    Wallet, WalletTransaction, ReferralRecord,
    LoyaltyPointsLedger, CustomerMilestoneBadge, PersonalizedOffer,
    CustomerSurvey, ServiceRecoveryCredit
)

@pytest.mark.asyncio
async def test_part7_rating_categories_and_private_feedback(db_session):
    u_suffix = uuid.uuid4().hex[:6]
    user = User(phone_number=f"+91984{u_suffix}1", full_name="Sir Arthur", role="customer", is_active=True, is_verified=True)
    db_session.add(user)
    await db_session.flush()

    customer = CustomerProfile(user_id=user.id, tier="GOLD", loyalty_points_balance=1200, lifetime_points_earned=1200)
    driver_user = User(phone_number=f"+91984{u_suffix}2", full_name="Chauffeur Rajiv", role="driver", is_active=True, is_verified=True)
    db_session.add_all([customer, driver_user])
    await db_session.flush()

    driver = DriverProfile(user_id=driver_user.id, license_number=f"DL-01-{u_suffix}", rating_average=4.9, rating_count=10)
    db_session.add(driver)
    await db_session.flush()

    ride = Ride(
        customer_id=customer.id,
        assigned_driver_id=driver.id,
        vehicle_category_id="cat-vip",
        fare_rule_id="rule-vip",
        status="COMPLETED",
        pickup_address="The Taj Mahal Palace, Mumbai",
        pickup_latitude=18.9217,
        pickup_longitude=72.8332,
        destination_address="Chhatrapati Shivaji Maharaj Terminus",
        destination_latitude=18.9400,
        destination_longitude=72.8353,
        estimated_distance_km=4.2,
        estimated_duration_minutes=12.0,
        estimated_fare_cents=120000,
        final_fare_cents=120000,
        start_otp="4921",
        completed_at=datetime.now(timezone.utc)
    )
    db_session.add(ride)
    await db_session.commit()

    service = LoyaltyFeedbackService(db_session)

    # Features 151, 152, 153, 154, 155: Submit Rating with category scores, private feedback and tags
    res = await service.submit_driver_rating(
        customer_user_id=user.id,
        ride_id=ride.id,
        overall_rating=5,
        cleanliness_score=5,
        comfort_score=5,
        service_quality_score=5,
        navigation_score=4,
        feedback="Flawless driving and distinguished demeanor.",
        private_feedback="Chauffeur offered pristine bottled Evian and adjusted cabin temp seamlessly.",
        feedback_tags=["Immaculate Cabin", "Courteous Chauffeur", "Punctual Arrival"],
        tip_cents=10000  # ₹100 tip
    )

    assert res["success"] is True
    assert res["overall_rating"] == 5
    assert res["category_scores"]["cleanliness"] == 5
    assert res["category_scores"]["navigation"] == 4
    assert "Immaculate Cabin" in res["tags"]
    assert res["tip_processed"] is not None

    # Verify DriverProfile rating updated
    await db_session.refresh(driver)
    assert driver.rating_count == 11
    assert driver.rating_average >= 4.9

@pytest.mark.asyncio
async def test_part7_tipping_and_adjustment_window(db_session):
    u_suffix = uuid.uuid4().hex[:6]
    user = User(phone_number=f"+91985{u_suffix}1", full_name="Countess Victoria", role="customer", is_active=True, is_verified=True)
    db_session.add(user)
    await db_session.flush()

    customer = CustomerProfile(user_id=user.id)
    driver_user = User(phone_number=f"+91985{u_suffix}2", full_name="Chauffeur Anand", role="driver", is_active=True, is_verified=True)
    db_session.add_all([customer, driver_user])
    await db_session.flush()

    driver = DriverProfile(user_id=driver_user.id, license_number=f"DL-02-{u_suffix}", wallet_balance_cents=50000)
    db_session.add(driver)
    await db_session.flush()

    now = datetime.now(timezone.utc)
    ride = Ride(
        customer_id=customer.id,
        assigned_driver_id=driver.id,
        vehicle_category_id="cat-vip",
        fare_rule_id="rule-vip",
        status="COMPLETED",
        pickup_address="Four Seasons Hotel, Worli",
        pickup_latitude=18.9950,
        pickup_longitude=72.8240,
        destination_address="Bandra-Kurla Complex",
        destination_latitude=19.0650,
        destination_longitude=72.8680,
        estimated_distance_km=11.0,
        estimated_duration_minutes=25.0,
        estimated_fare_cents=180000,
        final_fare_cents=180000,
        start_otp="9921",
        completed_at=now - timedelta(minutes=30),
        tip_adjustment_expires_at=now + timedelta(hours=1, minutes=30)
    )
    db_session.add(ride)
    await db_session.commit()

    service = LoyaltyFeedbackService(db_session)

    # Feature 156: Tip Chauffeur after ride (₹200 = 20000 cents)
    tip_res = await service.tip_driver(
        customer_user_id=user.id,
        ride_id=ride.id,
        tip_cents=20000
    )
    assert tip_res["tip_rupees"] == 200
    assert tip_res["status"] in ("CHARGED", "ADJUSTED")

    # Verify Driver wallet received tip
    await db_session.refresh(driver)
    assert driver.wallet_balance_cents == 70000

    # Feature 157: Tip Adjustment Window (adjust tip to ₹350 = 35000 cents)
    adj_res = await service.tip_driver(
        customer_user_id=user.id,
        ride_id=ride.id,
        tip_cents=35000
    )
    assert adj_res["tip_rupees"] == 350
    assert adj_res["status"] == "ADJUSTED"

    await db_session.refresh(driver)
    assert driver.wallet_balance_cents == 85000

    # Expire the window and assert rejection
    ride.tip_adjustment_expires_at = now - timedelta(minutes=10)
    await db_session.commit()

    with pytest.raises(HTTPException) as exc:
        await service.tip_driver(
            customer_user_id=user.id,
            ride_id=ride.id,
            tip_cents=40000
        )
    assert exc.value.status_code == 400
    assert "window has expired" in exc.value.detail

@pytest.mark.asyncio
async def test_part7_loyalty_points_ledger_and_tier_benefits(db_session):
    u_suffix = uuid.uuid4().hex[:6]
    user = User(phone_number=f"+91986{u_suffix}1", full_name="Lord Sterling", role="customer", is_active=True, is_verified=True)
    db_session.add(user)
    await db_session.flush()

    customer = CustomerProfile(
        user_id=user.id,
        loyalty_points_balance=6000,
        lifetime_points_earned=6000,
        membership_tier="PLATINUM"
    )
    wallet = Wallet(user_id=user.id, balance_cents=25000)
    db_session.add_all([customer, wallet])
    await db_session.commit()

    service = LoyaltyFeedbackService(db_session)

    # Feature 158 & 160: Loyalty Summary & Tier Benefits
    summary = await service.get_loyalty_summary(customer_user_id=user.id)
    assert summary["tier_code"] == "PLATINUM"
    assert summary["points_balance"] == 6000
    assert summary["multiplier"] == 1.5
    assert summary["credit_value_rupees"] == 3000.0
    assert len(summary["tier_perks"]) >= 4

    # Feature 159: Award points for ride
    pts_awarded = await service.award_points(
        customer=customer,
        points=120,
        transaction_type="EARNED_RIDE",
        description="Points earned for Executive Maybach Journey"
    )
    assert pts_awarded == 180  # 120 * 1.5x Platinum multiplier
    assert customer.loyalty_points_balance == 6180

    # Feature 158: Check Loyalty Points Ledger
    ledger = await service.get_loyalty_ledger(customer_user_id=user.id)
    assert len(ledger) >= 1
    assert ledger[0]["transaction_type"] == "EARNED_RIDE"

    # Feature 162: Points redemption for ride credit (1000 pts -> ₹500 credit)
    redeem_res = await service.redeem_points_for_credit(
        customer_user_id=user.id,
        points_to_redeem=1000
    )
    assert redeem_res["success"] is True
    assert redeem_res["points_redeemed"] == 1000
    assert redeem_res["credit_rupees"] == 500
    assert redeem_res["new_points_balance"] == 5180

    await db_session.refresh(wallet)
    assert wallet.balance_cents == 75000  # 25000 + 50000 cents

@pytest.mark.asyncio
async def test_part7_referrals_and_fraud_queue(db_session):
    u_suffix = uuid.uuid4().hex[:6]
    referrer_user = User(phone_number=f"+91987{u_suffix}1", full_name="Marquis Belmont", role="customer", is_active=True, is_verified=True)
    admin_user = User(phone_number=f"+91987{u_suffix}2", full_name="Tribunal Officer", role="admin", is_active=True, is_verified=True)
    db_session.add_all([referrer_user, admin_user])
    await db_session.flush()

    referrer_prof = CustomerProfile(user_id=referrer_user.id, referral_code=f"ROYAL-{u_suffix}")
    referred_prof = CustomerProfile(user_id=admin_user.id)  # placeholder user for referred
    db_session.add_all([referrer_prof, referred_prof])
    await db_session.flush()

    ref_record = ReferralRecord(
        referrer_id=referrer_prof.id,
        referred_id=referred_prof.id,
        reward_status="PENDING_FIRST_RIDE",
        referrer_reward_cents=50000,
        referred_reward_cents=50000,
        fraud_review_status="FLAGGED",
        fraud_risk_score=0.45,
        fraud_reason="Device IP similarity check required"
    )
    db_session.add(ref_record)
    await db_session.commit()

    service = LoyaltyFeedbackService(db_session)

    # Feature 163 & 164: Referral Dashboard & Eligibility Details
    dash = await service.get_referral_dashboard(customer_user_id=referrer_user.id)
    assert dash["referral_code"] == f"ROYAL-{u_suffix}"
    assert "https://grandchauffeur.com/invite/" in dash["share_url"]
    assert len(dash["eligibility_rules"]) >= 3
    assert dash["total_invites"] == 1

    # Feature 165: Referral Fraud Review Queue (Admin)
    queue = await service.get_admin_referral_fraud_queue()
    assert len(queue) >= 1
    found_rec = [q for q in queue if q["referral_id"] == ref_record.id][0]
    assert found_rec["fraud_review_status"] == "FLAGGED"

    # Admin reviews and approves referral
    review_res = await service.review_referral_fraud(
        admin_user_id=admin_user.id,
        referral_id=ref_record.id,
        decision="APPROVE",
        notes="Identity verified by Concierge Desk."
    )
    assert review_res["success"] is True
    assert review_res["status"] == "APPROVED"

@pytest.mark.asyncio
async def test_part7_milestones_and_streak_rewards(db_session):
    u_suffix = uuid.uuid4().hex[:6]
    user = User(phone_number=f"+91988{u_suffix}1", full_name="Baroness Camille", role="customer", is_active=True, is_verified=True)
    db_session.add(user)
    await db_session.flush()

    customer = CustomerProfile(
        user_id=user.id,
        ride_streak_count=3,
        rating_average=5.0
    )
    db_session.add(customer)
    await db_session.flush()

    # Add 2 completed rides
    r1 = Ride(
        customer_id=customer.id, vehicle_category_id="cat-vip", fare_rule_id="r1",
        status="COMPLETED", pickup_address="Taj Lands End, Bandra", pickup_latitude=19.043, pickup_longitude=72.819,
        destination_address="BKC", destination_latitude=19.065, destination_longitude=72.868,
        estimated_distance_km=8.0, estimated_duration_minutes=20.0, estimated_fare_cents=120000, final_fare_cents=120000, start_otp="1122"
    )
    db_session.add(r1)
    await db_session.commit()

    service = LoyaltyFeedbackService(db_session)

    # Feature 166 & 167: Customer Milestones, Badges & Streaks
    milestones_res = await service.get_customer_milestones(customer_user_id=user.id)
    assert milestones_res["total_unlocked"] >= 1
    assert any(m["code"] == "FIRST_VOYAGE" and m["is_unlocked"] for m in milestones_res["milestones"])
    assert any(m["code"] == "STREAK_MASTER" and m["is_unlocked"] for m in milestones_res["milestones"])
    assert milestones_res["streak"]["current_count"] == 3
    assert milestones_res["streak"]["progress_percentage"] == 100

@pytest.mark.asyncio
async def test_part7_personalized_offers_and_notification_frequency(db_session):
    u_suffix = uuid.uuid4().hex[:6]
    user = User(phone_number=f"+91989{u_suffix}1", full_name="Duke of Westminster", role="customer", is_active=True, is_verified=True)
    db_session.add(user)
    await db_session.flush()

    customer = CustomerProfile(user_id=user.id)
    db_session.add(customer)
    await db_session.commit()

    service = LoyaltyFeedbackService(db_session)

    # Feature 168: Birthday or anniversary offer
    bday_res = await service.update_celebration_dates(
        customer_user_id=user.id,
        birthday_date="1988-11-15",
        anniversary_date="2018-12-20"
    )
    assert bday_res["success"] is True
    assert "JUBILEE-" in bday_res["celebration_voucher_code"]

    # Feature 169: Personalized offer feed
    offers = await service.get_personalized_offers(customer_user_id=user.id)
    assert len(offers) >= 1
    assert any("Jubilee" in o["title"] or "Celebration" in o["title"] for o in offers)

    # Feature 170: Offer notification frequency control
    freq_res = await service.update_offer_notification_frequency(
        customer_user_id=user.id,
        frequency="EXCLUSIVE_ONLY"
    )
    assert freq_res["success"] is True
    assert freq_res["frequency"] == "EXCLUSIVE_ONLY"

@pytest.mark.asyncio
async def test_part7_ride_history_repeat_and_statements(db_session):
    u_suffix = uuid.uuid4().hex[:6]
    user = User(phone_number=f"+91990{u_suffix}1", full_name="Archduke Franz", role="customer", is_active=True, is_verified=True)
    db_session.add(user)
    await db_session.flush()

    customer = CustomerProfile(user_id=user.id)
    db_session.add(customer)
    await db_session.flush()

    ride = Ride(
        customer_id=customer.id,
        vehicle_category_id="cat-vip",
        fare_rule_id="rule-vip",
        status="COMPLETED",
        pickup_address="St. Regis Mumbai, Lower Parel",
        pickup_latitude=18.993,
        pickup_longitude=72.825,
        destination_address="Domestic Airport Terminal 1B",
        destination_latitude=19.098,
        destination_longitude=72.853,
        estimated_distance_km=14.5,
        estimated_duration_minutes=32.0,
        estimated_fare_cents=220000,
        final_fare_cents=220000,
        tip_cents=30000,
        start_otp="5521",
        completed_at=datetime(2026, 10, 15, 14, 30, tzinfo=timezone.utc)
    )
    db_session.add(ride)
    await db_session.commit()

    service = LoyaltyFeedbackService(db_session)

    # Feature 171: Ride history filters
    history = await service.get_filtered_ride_history(
        customer_user_id=user.id,
        status_filter="COMPLETED"
    )
    assert len(history) == 1
    assert history[0]["fare_rupees"] == 2200
    assert history[0]["can_repeat"] is True

    # Feature 172: Repeat a past trip
    repeat_data = await service.repeat_past_trip(
        customer_user_id=user.id,
        past_ride_id=ride.id
    )
    assert repeat_data["pickup_address"] == "St. Regis Mumbai, Lower Parel"
    assert repeat_data["estimated_fare_rupees"] == 2200
    assert repeat_data["repeat_source_ride_id"] == ride.id

    # Feature 173: Download monthly ride statement
    statement = await service.generate_monthly_statement(
        customer_user_id=user.id,
        year=2026,
        month=10
    )
    assert statement["total_rides"] == 1
    assert statement["total_fare_rupees"] == 2200.0
    assert statement["total_tip_rupees"] == 300.0
    assert statement["grand_total_rupees"] == 2500.0
    assert "download.csv" in statement["csv_download_url"]

@pytest.mark.asyncio
async def test_part7_surveys_and_service_recovery(db_session):
    u_suffix = uuid.uuid4().hex[:6]
    customer_user = User(phone_number=f"+91991{u_suffix}1", full_name="Earl of Oxford", role="customer", is_active=True, is_verified=True)
    admin_user = User(phone_number=f"+91991{u_suffix}2", full_name="Concierge Admin", role="admin", is_active=True, is_verified=True)
    db_session.add_all([customer_user, admin_user])
    await db_session.flush()

    customer = CustomerProfile(user_id=customer_user.id, loyalty_points_balance=400)
    wallet = Wallet(user_id=customer_user.id, balance_cents=10000)
    db_session.add_all([customer, wallet])
    await db_session.commit()

    service = LoyaltyFeedbackService(db_session)

    # Feature 174: Customer satisfaction survey (CSAT/NPS)
    survey_res = await service.submit_survey(
        customer_user_id=customer_user.id,
        ride_id=None,
        survey_type="QUARTERLY_NPS",
        score=10,
        dimensions={"booking_ease": 10, "comfort": 10, "cleanliness": 10, "chauffeur": 10},
        comments="The pinnacle of luxury mobility in South Asia."
    )
    assert survey_res["success"] is True
    assert survey_res["score"] == 10
    assert survey_res["points_awarded"] == 100

    # Feature 175: Service recovery credit issuance flow (Admin)
    rec_res = await service.issue_service_recovery_credit(
        admin_user_id=admin_user.id,
        customer_id=customer.id,
        amount_cents=50000,  # ₹500
        reason_category="LATE_ARRIVAL",
        notes="Chauffeur arrived 8 minutes late due to unexpected VIP motorcade roadblock."
    )
    assert rec_res["success"] is True
    assert rec_res["amount_rupees"] == 500
    assert rec_res["new_wallet_balance_rupees"] == 600.0

    history = await service.get_service_recovery_history()
    assert len(history) >= 1
    assert history[0]["reason_category"] == "LATE_ARRIVAL"
