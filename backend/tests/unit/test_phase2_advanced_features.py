import pytest
from datetime import datetime, timezone, timedelta
from fastapi import HTTPException
from backend.application.services.wallet_service import WalletService
from backend.application.services.promo_pass_service import PromoPassService
from backend.application.services.support_chat_service import SupportChatService
from backend.application.services.dispatch_advanced_service import DispatchAdvancedService
from backend.application.services.admin_advanced_service import AdminAdvancedService
from backend.domain.models import User, CustomerProfile, DriverProfile, FareRule, Ride, PromoCode, RidePass

# ---------------- 1. WALLET LEDGER & CONCURRENCY INVARIANTS ----------------
@pytest.mark.asyncio
async def test_wallet_ledger_credit_debit_compensating(db_session):
    user = User(phone_number="+919800011122", full_name="Wallet Test User", role="customer", is_active=True, is_verified=True)
    db_session.add(user)
    await db_session.flush()

    # Initial balance should be 0
    bal = await WalletService.get_balance(db_session, user.id)
    assert bal["balance_cents"] == 0

    # Credit 10,000 cents (₹100)
    tx_cred = await WalletService.credit_wallet(
        session=db_session,
        user_id=user.id,
        amount_cents=10000,
        transaction_type="CREDIT",
        source="TOP_UP",
        description="Test Topup"
    )
    assert tx_cred.amount_cents == 10000
    assert tx_cred.balance_after_cents == 10000

    # Debit 4,000 cents (₹40)
    tx_deb = await WalletService.debit_wallet(
        session=db_session,
        user_id=user.id,
        amount_cents=4000,
        transaction_type="DEBIT",
        source="RIDE_PAYMENT",
        description="Test Ride Payment"
    )
    assert tx_deb.balance_after_cents == 6000

    # Debit exceeding balance must fail
    with pytest.raises(HTTPException) as exc:
        await WalletService.debit_wallet(
            session=db_session,
            user_id=user.id,
            amount_cents=7000,
            transaction_type="DEBIT",
            source="RIDE_PAYMENT"
        )
    assert exc.value.status_code == 400

    # Compensating entry for the 4000 debit
    comp = await WalletService.create_compensating_entry(
        session=db_session,
        original_tx_id=tx_deb.id,
        reason="Disputed Fare Reversal",
        admin_user_id="admin-123"
    )
    assert comp.transaction_type == "COMPENSATION_CREDIT"
    assert comp.balance_after_cents == 10000

    # Check history
    history = await WalletService.get_history(db_session, user.id)
    assert len(history) == 3

# ---------------- 2. PROMO CODES & ANTI-STACKING ----------------
@pytest.mark.asyncio
async def test_promo_code_validation_and_limits(db_session):
    user = User(phone_number="+919800011123", full_name="Promo Tester", role="customer", is_active=True, is_verified=True)
    db_session.add(user)
    await db_session.flush()
    prof = CustomerProfile(user_id=user.id)
    db_session.add(prof)
    await db_session.flush()

    now = datetime.now(timezone.utc)
    promo = PromoCode(
        code="LUXURY20",
        discount_type="PERCENTAGE",
        discount_value=20.0,
        max_discount_cents=40000,
        min_fare_cents=100000,  # Min ₹1,000
        total_usage_limit=50,
        per_customer_limit=1,
        starts_at=now - timedelta(days=1),
        expires_at=now + timedelta(days=10),
        is_active=True
    )
    db_session.add(promo)
    await db_session.flush()

    # Test fare below minimum
    with pytest.raises(HTTPException) as exc1:
        await PromoPassService.validate_promo_code(db_session, "LUXURY20", user.id, fare_cents=50000)
    assert "Minimum fare" in exc1.value.detail

    # Test valid promo (20% of 150000 = 30000)
    val = await PromoPassService.validate_promo_code(db_session, "LUXURY20", user.id, fare_cents=150000)
    assert val["discount_cents"] == 30000
    assert val["effective_fare_cents"] == 120000

# ---------------- 3. REFERRAL FRAUD PROTECTION ----------------
@pytest.mark.asyncio
async def test_referral_fraud_prevention(db_session):
    u1 = User(phone_number="+919800011124", full_name="Referrer", role="customer", is_active=True, is_verified=True)
    u2 = User(phone_number="+919800011125", full_name="Referred", role="customer", is_active=True, is_verified=True)
    db_session.add_all([u1, u2])
    await db_session.flush()

    p1 = CustomerProfile(user_id=u1.id, referral_code="REF-LORD1")
    p2 = CustomerProfile(user_id=u2.id, referral_code="REF-LORD2")
    db_session.add_all([p1, p2])
    await db_session.flush()

    # Self-referral must be rejected
    with pytest.raises(HTTPException) as exc:
        await PromoPassService.claim_referral(db_session, u1.id, "REF-LORD1")
    assert "Self-referrals are strictly prohibited" in exc.value.detail

    # Legitimate claim
    rec = await PromoPassService.claim_referral(db_session, u2.id, "REF-LORD1")
    assert rec.reward_status == "PENDING_FIRST_RIDE"

    # Duplicate claim must be rejected
    with pytest.raises(HTTPException) as exc2:
        await PromoPassService.claim_referral(db_session, u2.id, "REF-LORD1")
    assert "User already registered" in exc2.value.detail

    # Simulate completed trip reward attribution
    rewarded = await PromoPassService.attribute_referral_reward(db_session, u2.id)
    assert rewarded is not None
    assert rewarded.reward_status == "CREDITED"

    # Referrer's wallet should have received 50000 cents reward
    r_bal = await WalletService.get_balance(db_session, u1.id)
    assert r_bal["balance_cents"] == 50000

# ---------------- 4. SCHEDULED RIDE WINDOW & CONFLICT DETECTION ----------------
@pytest.mark.asyncio
async def test_scheduled_ride_validation_and_driver_conflict(db_session):
    now = datetime.now(timezone.utc)

    # Rejects scheduling too soon (<30 mins)
    with pytest.raises(HTTPException) as exc:
        DispatchAdvancedService.validate_schedule_window(now + timedelta(minutes=15))
    assert "at least 30 minutes" in exc.value.detail

    # Rejects scheduling too far (>7 days)
    with pytest.raises(HTTPException) as exc2:
        DispatchAdvancedService.validate_schedule_window(now + timedelta(days=8))
    assert "more than 7 days" in exc2.value.detail

    # Accepts valid future schedule
    valid_time = now + timedelta(hours=4)
    DispatchAdvancedService.validate_schedule_window(valid_time)

# ---------------- 5. SERVER-AUTHORITATIVE WAITING TIMER ----------------
@pytest.mark.asyncio
async def test_waiting_timer_grace_period_calculation(db_session):
    u_driver = User(phone_number="+919800011126", full_name="Timer Driver", role="driver", is_active=True, is_verified=True)
    db_session.add(u_driver)
    await db_session.flush()

    ride = Ride(
        customer_id="cust-123",
        vehicle_category_id="cat-1",
        fare_rule_id="rule-1",
        status="ARRIVED",
        pickup_address="Gate 1",
        pickup_latitude=19.0,
        pickup_longitude=72.8,
        destination_address="Airport",
        destination_latitude=19.1,
        destination_longitude=72.8,
        start_otp="123456",
        assigned_driver_id=u_driver.id,
        estimated_distance_km=10.0,
        estimated_duration_minutes=20.0,
        estimated_fare_cents=100000,
        final_fare_cents=100000
    )
    db_session.add(ride)
    await db_session.flush()

    # Start waiting
    start_res = await DispatchAdvancedService.start_waiting(db_session, ride.id, u_driver.id)
    assert start_res["status"] == "WAITING_ACTIVE"

    # Simulate 8 minutes of waiting (3 min grace, 5 min billable @ ₹75/min = ₹375 / 37500 cents)
    ride.waiting_started_at = datetime.now(timezone.utc) - timedelta(minutes=8)
    stop_res = await DispatchAdvancedService.stop_waiting(db_session, ride.id, u_driver.id)
    assert stop_res["grace_minutes"] == 3
    assert stop_res["billable_waiting_minutes"] >= 4.9
    assert stop_res["waiting_charge_cents"] > 0
    assert ride.final_fare_cents > 100000

# ---------------- 6. ROUTE OPTIMIZATION HEURISTIC ----------------
def test_route_optimization_heuristic():
    origin_lat, origin_lng = 19.0760, 72.8777
    dest_lat, dest_lng = 19.2183, 72.9781
    stops = [
        {"address": "Stop B (Distant)", "lat": 19.1500, "lng": 72.9300},
        {"address": "Stop A (Close)", "lat": 19.0900, "lng": 72.8900}
    ]

    opt = DispatchAdvancedService.calculate_optimized_stop_sequence(
        origin_lat, origin_lng, stops, dest_lat, dest_lng
    )
    assert opt["is_optimized"] is True
    assert len(opt["ordered_stops"]) == 2
    # Stop A is closer to origin, so it must be sequenced first
    assert opt["ordered_stops"][0]["address"] == "Stop A (Close)"
    assert opt["ordered_stops"][1]["address"] == "Stop B (Distant)"

# ---------------- 7. GEOFENCING RAY-CASTING POINT-IN-POLYGON ----------------
def test_geofencing_ray_casting():
    # Polygon around Mumbai BKC
    bkc_polygon = [
        (19.0600, 72.8600),
        (19.0700, 72.8600),
        (19.0700, 72.8800),
        (19.0600, 72.8800)
    ]
    # Point inside BKC
    assert AdminAdvancedService.is_point_in_polygon(19.0650, 72.8700, bkc_polygon) is True
    # Point outside BKC
    assert AdminAdvancedService.is_point_in_polygon(19.1200, 72.8200, bkc_polygon) is False

# ---------------- 8. SUPPORT TICKETS ROLE ISOLATION ----------------
@pytest.mark.asyncio
async def test_support_ticket_role_isolation(db_session):
    u_cust = User(phone_number="+919800011127", full_name="Customer Jack", role="customer", is_active=True, is_verified=True)
    u_other = User(phone_number="+919800011128", full_name="Other Customer", role="customer", is_active=True, is_verified=True)
    u_admin = User(phone_number="+919800011129", full_name="Staff Admin", role="admin", is_active=True, is_verified=True)
    db_session.add_all([u_cust, u_other, u_admin])
    await db_session.flush()

    ticket = await SupportChatService.create_ticket(
        session=db_session,
        user_id=u_cust.id,
        subject="Lost Champagne Glass",
        category="LOST_PROPERTY",
        description="Left glass in back seat"
    )
    assert ticket.status == "OPEN"

    # Other customer cannot view Jack's ticket
    with pytest.raises(HTTPException) as exc:
        await SupportChatService.get_ticket_with_messages(db_session, ticket.id, u_other.id, is_staff=False)
    assert exc.value.status_code == 403

    # Admin adds internal note
    await SupportChatService.post_ticket_message(
        session=db_session,
        ticket_id=ticket.id,
        sender_user_id=u_admin.id,
        message_text="Found in vehicle MH01-LX-0003, dispatching to lost & found",
        is_internal_only=True,
        is_staff=True
    )

    # Customer viewing ticket cannot see internal note
    cust_view = await SupportChatService.get_ticket_with_messages(db_session, ticket.id, u_cust.id, is_staff=False)
    assert len(cust_view["messages"]) == 1  # Only initial customer message

    # Admin viewing ticket can see both messages
    admin_view = await SupportChatService.get_ticket_with_messages(db_session, ticket.id, u_admin.id, is_staff=True)
    assert len(admin_view["messages"]) == 2
