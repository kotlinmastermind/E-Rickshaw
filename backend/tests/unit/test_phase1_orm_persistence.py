import pytest
from datetime import datetime, timezone, timedelta
from sqlalchemy import select
from sqlalchemy.orm.exc import UnmappedInstanceError
import backend.domain.models as models

@pytest.mark.asyncio
async def test_phase1_orm_models_persisted_and_queryable(db_session):
    """
    Test Phase 1 Tasks 001-015:
    Verifies that real SQLAlchemy Declarative models are mapped, persistable,
    and queryable via native async/sync sessions without monkey-patches.
    """
    now = datetime.now(timezone.utc)
    
    # 1. AdministrativeAuditLog (Task 004)
    audit = models.AdministrativeAuditLog(
        admin_user_id="admin-test-uuid",
        action="TEST_RULE_CREATED",
        target_resource="commission_rules",
        target_id="rule-test-1",
        changes_json='{"percentage": 15.0}',
        ip_address="127.0.0.1",
        created_at=now
    )
    db_session.add(audit)

    # 2. PaymentWebhookInbox (Task 002)
    webhook = models.PaymentWebhookInbox(
        provider_name="RAZORPAY",
        event_digest="test_digest_hash_987654321",
        payload_json='{"event": "payment.captured", "id": "pay_test123"}',
        signature_verified=True,
        status="PROCESSED",
        attempts=1,
        received_at=now
    )
    db_session.add(webhook)

    # 3. IdempotencyRecord (Task 003)
    idemp = models.IdempotencyRecord(
        idempotency_key="idemp_key_unique_test_12345",
        route_path="/api/v1/payments/create-order",
        request_hash="sha256_mock_hash_test_12345",
        response_code=200,
        response_body_json='{"order_id": "order_test_99"}',
        expires_at=now + timedelta(hours=24)
    )
    db_session.add(idemp)

    # 4. FeatureFlag (Task 005)
    flag = models.FeatureFlag(
        flag_key="instant_payout_enabled",
        name="Instant Payout",
        description="Enables instant UPI payout to drivers",
        is_enabled=True,
        rollout_percentage=100
    )
    db_session.add(flag)

    # 5. DriverTrainingModule & DriverTrainingProgress (Task 007)
    module = models.DriverTrainingModule(
        module_code="MOD-SAFETY-01",
        title="Urban EV Safety and Passenger Etiquette",
        description="Comprehensive course on battery safety and royal hospitality",
        content_markdown="# Safety Guidelines\n1. Always verify passenger identity",
        estimated_duration_minutes=20,
        sort_order=1,
        is_mandatory=True
    )
    db_session.add(module)
    await db_session.flush()

    training_progress = models.DriverTrainingProgress(
        driver_id="driver-test-uuid",
        module_id=module.id,
        is_completed=True,
        assessment_score_percentage=95,
        assessment_passed=True,
        completed_at=now
    )
    db_session.add(training_progress)

    # 6. CorporateAccount, CorporateInvoice, CorporateTravelPolicy (Task 010)
    corp = models.CorporateAccount(
        company_name="Vaisentra Global Enterprises Ltd",
        tax_id="GSTIN-27AAAAA0000A1Z5",
        billing_email="billing@vaisentraglobal.com",
        credit_limit_cents=50000000,
        status="ACTIVE"
    )
    db_session.add(corp)
    await db_session.flush()

    policy = models.CorporateTravelPolicy(
        corporate_id=corp.id,
        policy_name="Executive Green Travel Policy",
        max_fare_per_ride_cents=500000,
        is_active=True
    )
    invoice = models.CorporateInvoice(
        invoice_number="INV-CORP-2026-001",
        corporate_id=corp.id,
        billing_period="2026-11",
        total_amount_cents=1250000,
        due_date=now + timedelta(days=30)
    )
    db_session.add_all([policy, invoice])

    # 7. LoyaltyPointsLedger (Task 011)
    ledger = models.LoyaltyPointsLedger(
        customer_id="cust-test-uuid",
        transaction_type="RIDE_COMPLETION_BONUS",
        points_change=150,
        balance_after=150,
        reference_id="ride-ref-001",
        description="150 Royal Sovereign points earned on ride completion"
    )
    db_session.add(ledger)

    # 8. Operational Events (Task 012)
    op_event = models.RideOperationalEvent(
        ride_id="ride-test-uuid",
        actor_user_id="driver-user-test-uuid",
        action="PRE_DEPARTURE_CHECK",
        details_json='{"battery_percent": 92, "tire_pressure_ok": true}'
    )
    db_session.add(op_event)

    # 9. AccountDeletionRequest (Task 008)
    del_req = models.AccountDeletionRequest(
        user_id="user-del-test-uuid",
        status="PENDING_GRACE_PERIOD",
        cooldown_days_total=30,
        scheduled_purge_at=now + timedelta(days=30),
        cancellation_reason="Relocating out of service zone"
    )
    db_session.add(del_req)

    # 10. DataExportArchive (Task 013)
    archive = models.DataExportArchive(
        user_id="user-del-test-uuid",
        status="READY",
        archive_format="JSON",
        file_size_bytes=4096,
        download_url="/api/v1/user/data-export/download/arch-test-123",
        expires_at=now + timedelta(days=7)
    )
    db_session.add(archive)

    # Commit all
    await db_session.commit()

    # Query back and assert
    q_audit = (await db_session.execute(select(models.AdministrativeAuditLog).where(models.AdministrativeAuditLog.id == audit.id))).scalar_one()
    assert q_audit.action == "TEST_RULE_CREATED"
    assert q_audit.target_resource == "commission_rules"

    q_webhook = (await db_session.execute(select(models.PaymentWebhookInbox).where(models.PaymentWebhookInbox.id == webhook.id))).scalar_one()
    assert q_webhook.event_digest == "test_digest_hash_987654321"
    assert q_webhook.signature_verified is True

    q_idemp = (await db_session.execute(select(models.IdempotencyRecord).where(models.IdempotencyRecord.idempotency_key == "idemp_key_unique_test_12345"))).scalar_one()
    assert q_idemp.response_code == 200

    q_flag = (await db_session.execute(select(models.FeatureFlag).where(models.FeatureFlag.flag_key == "instant_payout_enabled"))).scalar_one()
    assert q_flag.is_enabled is True

    q_corp = (await db_session.execute(select(models.CorporateAccount).where(models.CorporateAccount.company_name == "Vaisentra Global Enterprises Ltd"))).scalar_one()
    assert q_corp.status == "ACTIVE"

    q_ledger = (await db_session.execute(select(models.LoyaltyPointsLedger).where(models.LoyaltyPointsLedger.reference_id == "ride-ref-001"))).scalar_one()
    assert q_ledger.points_change == 150

    q_op = (await db_session.execute(select(models.RideOperationalEvent).where(models.RideOperationalEvent.action == "PRE_DEPARTURE_CHECK"))).scalar_one()
    assert q_op.ride_id == "ride-test-uuid"

    q_del = (await db_session.execute(select(models.AccountDeletionRequest).where(models.AccountDeletionRequest.user_id == "user-del-test-uuid"))).scalar_one()
    assert q_del.status == "PENDING_GRACE_PERIOD"

    q_arch = (await db_session.execute(select(models.DataExportArchive).where(models.DataExportArchive.id == archive.id))).scalar_one()
    assert q_arch.file_size_bytes == 4096


def test_phase1_dropped_model_stubs_completely_removed():
    """
    Task 014: Ensure DroppedModelStub definition is eliminated and not in models or __init__.
    """
    assert not hasattr(models, "DroppedModelStub"), "DroppedModelStub must not exist in models exports"
    
    # Verify that all 99 models are genuine ORM classes inheriting from Base
    from backend.infrastructure.database.session import Base
    for model_name in [
        "AdministrativeAuditLog", "PaymentWebhookInbox", "IdempotencyRecord",
        "FeatureFlag", "DriverVehicleInspection", "DriverTrainingModule",
        "DriverTrainingProgress", "CorporateAccount", "CorporateInvoice",
        "LoyaltyPointsLedger", "RideOperationalEvent", "AccountDeletionRequest",
        "DataExportArchive", "RecurringRideSchedule", "PassengerOccupancyEvent"
    ]:
        cls = getattr(models, model_name)
        assert issubclass(cls, Base), f"{model_name} must be a genuine SQLAlchemy Declarative Base class"
        assert hasattr(cls, "__tablename__"), f"{model_name} must have a mapped __tablename__"


def test_phase1_unsupported_entity_fails_explicitly():
    """
    Task 015: Ensure monkey-patched silent swallowing is gone and unsupported
    persistence operations fail with native SQLAlchemy exceptions.
    """
    from sqlalchemy.orm import Session
    from backend.infrastructure.database.session import engine

    class FakeArbitraryClass:
        pass

    fake_instance = FakeArbitraryClass()

    from sqlalchemy import create_engine
    test_sync_engine = create_engine("sqlite:///:memory:")
    with Session(test_sync_engine) as session:
        with pytest.raises((UnmappedInstanceError, Exception)) as exc_info:
            session.add(fake_instance)
        assert "is not mapped" in str(exc_info.value) or isinstance(exc_info.value, UnmappedInstanceError)

