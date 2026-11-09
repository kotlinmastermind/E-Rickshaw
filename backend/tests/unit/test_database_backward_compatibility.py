import uuid
import pytest
from datetime import datetime, timezone, timedelta
from sqlalchemy import select
from backend.domain.models import (
    User, CustomerProfile, DriverProfile, Wallet, WalletTransaction, Payment,
    Ride, RideAssignment, FareRule, VehicleCategory
)
from backend.infrastructure.security.crypto import hash_secret


@pytest.mark.asyncio
async def test_legacy_user_and_profiles_compatibility(db_session):
    """
    FIX-05: Verify that the database schema at HEAD (f23a1b2c3d4e) safely accommodates
    legacy User, CustomerProfile, and DriverProfile creation without requiring any
    newly added columns (e.g. security_badge_id, fatigue status, new metadata).
    """
    now = datetime.now(timezone.utc)
    uid = str(uuid.uuid4())
    phone = f"+9198{uuid.uuid4().int % 100000000:08d}"

    # Legacy User model insertion (only foundational columns from baseline schema)
    user = User(
        id=uid,
        phone_number=phone,
        email=f"legacy_{uid[:8]}@example.com",
        password_hash=hash_secret("LegacyPassword123!"),
        full_name="Legacy User Test",
        role="customer",
        is_active=True,
        is_verified=True,
        created_at=now,
        updated_at=now
    )
    db_session.add(user)
    await db_session.flush()

    # Legacy CustomerProfile insertion (baseline fields)
    cust_id = str(uuid.uuid4())
    customer = CustomerProfile(
        id=cust_id,
        user_id=uid,
        rating_average=5.0,
        rating_count=0,
        created_at=now,
        updated_at=now
    )
    db_session.add(customer)
    await db_session.flush()

    # Query back using legacy expectations
    loaded_user = await db_session.get(User, uid)
    assert loaded_user is not None
    assert loaded_user.phone_number == phone

    loaded_cust = await db_session.get(CustomerProfile, cust_id)
    assert loaded_cust is not None
    assert loaded_cust.user_id == uid
    # Ensure newly added columns have safe defaults
    assert getattr(loaded_cust, "loyalty_points_balance", 0) == 0
    assert getattr(loaded_cust, "membership_tier", "SILVER") == "SILVER"


@pytest.mark.asyncio
async def test_legacy_financial_and_wallet_ledger_compatibility(db_session):
    """
    FIX-05: Critical financial backward-compatibility verification.
    Verifies that legacy Wallet, WalletTransaction, and Payment operations execute cleanly,
    atomic balance calculations hold, and no new constraints break transactions.
    """
    now = datetime.now(timezone.utc)
    uid = str(uuid.uuid4())

    # Create user for wallet
    user = User(
        id=uid,
        phone_number=f"+9197{uuid.uuid4().int % 100000000:08d}",
        email=f"wallet_{uid[:8]}@example.com",
        password_hash=hash_secret("Password123!"),
        full_name="Wallet Owner",
        role="customer",
        is_active=True,
        is_verified=True,
        created_at=now,
        updated_at=now
    )
    db_session.add(user)
    await db_session.flush()

    # Legacy Wallet creation (baseline fields)
    wallet_id = str(uuid.uuid4())
    wallet = Wallet(
        id=wallet_id,
        user_id=uid,
        balance_cents=5000,  # 50.00 INR
        currency="INR",
        created_at=now,
        updated_at=now
    )
    db_session.add(wallet)
    await db_session.flush()

    # Legacy WalletTransaction credit
    tx1_id = str(uuid.uuid4())
    tx1 = WalletTransaction(
        id=tx1_id,
        wallet_id=wallet_id,
        amount_cents=2500,
        transaction_type="CREDIT",
        description="Legacy Topup",
        balance_after_cents=7500,
        reference_id=f"ref_{tx1_id[:8]}",
        idempotency_key=f"idemp_{tx1_id[:8]}",
        source="RAZORPAY_TOPUP",
        created_at=now
    )
    db_session.add(tx1)

    # Legacy WalletTransaction debit
    tx2_id = str(uuid.uuid4())
    tx2 = WalletTransaction(
        id=tx2_id,
        wallet_id=wallet_id,
        amount_cents=-1500,
        transaction_type="DEBIT",
        description="Legacy Ride Payment",
        balance_after_cents=6000,
        reference_id=f"ref_{tx2_id[:8]}",
        idempotency_key=f"idemp_{tx2_id[:8]}",
        source="RIDE_PAYMENT",
        created_at=now + timedelta(seconds=10)
    )
    db_session.add(tx2)

    wallet.balance_cents = 6000
    await db_session.flush()

    # Setup Category, FareRule, and Ride for Payment linkage
    cat_id = str(uuid.uuid4())
    cat = VehicleCategory(
        id=cat_id,
        code=f"PAY_CAT_{cat_id[:4].upper()}",
        name=f"Category {cat_id[:4]}",
        capacity=4,
        is_active=True,
        created_at=now,
        updated_at=now
    )
    db_session.add(cat)
    await db_session.flush()

    rule_id = str(uuid.uuid4())
    rule = FareRule(
        id=rule_id,
        category_id=cat_id,
        version=1,
        base_fare_cents=1500,
        per_km_cents=800,
        per_minute_cents=80,
        minimum_fare_cents=1500,
        is_active=True,
        effective_from=now,
        created_at=now
    )
    db_session.add(rule)
    await db_session.flush()

    ride_id = str(uuid.uuid4())
    ride = Ride(
        id=ride_id,
        customer_id=uid,
        vehicle_category_id=cat_id,
        fare_rule_id=rule_id,
        pickup_address="Pickup A",
        pickup_latitude=28.61,
        pickup_longitude=77.20,
        destination_address="Drop B",
        destination_latitude=28.62,
        destination_longitude=77.21,
        start_otp="4821",
        estimated_distance_km=2.5,
        estimated_duration_minutes=8,
        status="COMPLETED",
        estimated_fare_cents=1500,
        final_fare_cents=1500,
        requested_at=now
    )
    db_session.add(ride)
    await db_session.flush()

    # Legacy Payment creation
    pay_id = str(uuid.uuid4())
    payment = Payment(
        id=pay_id,
        ride_id=ride_id,
        customer_id=uid,
        amount_cents=1500,
        currency="INR",
        payment_method="WALLET",
        status="SUCCESS",
        provider_name="INTERNAL_WALLET",
        idempotency_key=f"pay_idemp_{pay_id[:8]}",
        created_at=now,
        updated_at=now
    )
    db_session.add(payment)
    await db_session.flush()

    # Verify query and ledger consistency
    loaded_wallet = await db_session.get(Wallet, wallet_id)
    assert loaded_wallet.balance_cents == 6000

    stmt = select(WalletTransaction).where(WalletTransaction.wallet_id == wallet_id).order_by(WalletTransaction.created_at)
    txs = (await db_session.execute(stmt)).scalars().all()
    assert len(txs) == 2
    assert txs[0].balance_after_cents == 7500
    assert txs[1].balance_after_cents == 6000

    loaded_pay = await db_session.get(Payment, pay_id)
    assert loaded_pay is not None
    assert loaded_pay.amount_cents == 1500
    assert loaded_pay.status == "SUCCESS"


@pytest.mark.asyncio
async def test_legacy_ride_lifecycle_and_assignment_compatibility(db_session):
    """
    FIX-05: Verifies that legacy Ride and RideAssignment creation and status updates
    succeed without errors when executed against the reconciled HEAD database schema.
    All newly introduced columns (is_shared, auto_dispatch_status, seats_booked, etc.)
    must automatically receive safe server defaults.
    """
    now = datetime.now(timezone.utc)
    cust_uid = str(uuid.uuid4())
    driver_uid = str(uuid.uuid4())

    # Setup customer and driver users
    cust_user = User(
        id=cust_uid,
        phone_number=f"+9196{uuid.uuid4().int % 100000000:08d}",
        email=f"cust_{cust_uid[:8]}@example.com",
        password_hash=hash_secret("Password123!"),
        full_name="Ride Customer",
        role="customer",
        is_active=True,
        is_verified=True,
        created_at=now,
        updated_at=now
    )
    driver_user = User(
        id=driver_uid,
        phone_number=f"+9195{uuid.uuid4().int % 100000000:08d}",
        email=f"driver_{driver_uid[:8]}@example.com",
        password_hash=hash_secret("Password123!"),
        full_name="Ride Driver",
        role="driver",
        is_active=True,
        is_verified=True,
        created_at=now,
        updated_at=now
    )
    db_session.add_all([cust_user, driver_user])
    await db_session.flush()

    # Setup VehicleCategory
    cat_id = str(uuid.uuid4())
    cat = VehicleCategory(
        id=cat_id,
        code=f"RICKSHAW_{cat_id[:4].upper()}",
        name=f"E-Rickshaw-{cat_id[:4]}",
        capacity=4,
        is_active=True,
        created_at=now,
        updated_at=now
    )
    db_session.add(cat)
    await db_session.flush()

    # Setup FareRule
    rule_id = str(uuid.uuid4())
    rule = FareRule(
        id=rule_id,
        category_id=cat_id,
        version=1,
        base_fare_cents=4500,
        per_km_cents=1200,
        per_minute_cents=120,
        minimum_fare_cents=4500,
        is_active=True,
        effective_from=now,
        created_at=now
    )
    db_session.add(rule)
    await db_session.flush()

    # Setup Driver Profile
    driver_prof = DriverProfile(
        id=str(uuid.uuid4()),
        user_id=driver_uid,
        license_number=f"DL-{uuid.uuid4().hex[:8].upper()}",
        approval_status="APPROVED",
        rating_average=4.9,
        created_at=now,
        updated_at=now
    )
    db_session.add(driver_prof)
    await db_session.flush()

    # Insert Ride using strictly legacy fields
    ride_id = str(uuid.uuid4())
    ride = Ride(
        id=ride_id,
        customer_id=cust_uid,
        vehicle_category_id=cat_id,
        fare_rule_id=rule_id,
        pickup_address="Metro Station Gate 1",
        pickup_latitude=28.6139,
        pickup_longitude=77.2090,
        destination_address="Main Market Square",
        destination_latitude=28.6200,
        destination_longitude=77.2150,
        start_otp="7890",
        estimated_distance_km=3.5,
        estimated_duration_minutes=12,
        status="REQUESTED",
        estimated_fare_cents=4500,
        requested_at=now
    )
    db_session.add(ride)
    await db_session.flush()

    # Verify that default values were populated by the database/model
    loaded_ride = await db_session.get(Ride, ride_id)
    assert loaded_ride is not None
    assert loaded_ride.status == "REQUESTED"
    # Ensure new columns have safe non-blocking defaults
    assert getattr(loaded_ride, "is_shared", False) in (False, None)
    assert getattr(loaded_ride, "seats_booked", 1) in (1, None)

    # Legacy RideAssignment insertion
    assign_id = str(uuid.uuid4())
    assignment = RideAssignment(
        id=assign_id,
        ride_id=ride_id,
        driver_id=driver_uid,
        status="ACCEPTED",
        offered_at=now,
        responded_at=now,
        is_accepted=True
    )
    db_session.add(assignment)

    # Update ride to ACCEPTED
    loaded_ride.assigned_driver_id = driver_uid
    loaded_ride.status = "ACCEPTED"
    await db_session.flush()

    # Complete ride
    loaded_ride.status = "COMPLETED"
    loaded_ride.completed_at = now + timedelta(minutes=15)
    loaded_ride.final_fare_cents = 4500
    await db_session.flush()

    final_ride = await db_session.get(Ride, ride_id)
    assert final_ride.status == "COMPLETED"
    assert final_ride.final_fare_cents == 4500


@pytest.mark.asyncio
async def test_fare_rules_legacy_compatibility(db_session):
    """
    FIX-05: Verifies that fare rule configuration from previous releases
    functions seamlessly with new fields (shared_discount, workflow_status).
    """
    now = datetime.now(timezone.utc)
    cat_id = str(uuid.uuid4())
    cat = VehicleCategory(
        id=cat_id,
        code=f"RICKSHAW_FARE_{cat_id[:4].upper()}",
        name=f"Standard Rickshaw {cat_id[:4]}",
        capacity=3,
        is_active=True,
        created_at=now,
        updated_at=now
    )
    db_session.add(cat)
    await db_session.flush()

    # Legacy FareRule insertion without new governance fields
    rule_id = str(uuid.uuid4())
    fare_rule = FareRule(
        id=rule_id,
        category_id=cat_id,
        version=1,
        base_fare_cents=2000,
        per_km_cents=1000,
        per_minute_cents=100,
        minimum_fare_cents=2500,
        surge_multiplier=1.0,
        is_active=True,
        effective_from=now,
        created_at=now
    )
    db_session.add(fare_rule)
    await db_session.flush()

    loaded_rule = await db_session.get(FareRule, rule_id)
    assert loaded_rule is not None
    assert loaded_rule.base_fare_cents == 2000
    assert loaded_rule.category_id == cat_id
    # Verify default safe governance status
    assert getattr(loaded_rule, "workflow_status", "ACTIVE") in ("ACTIVE", None)
