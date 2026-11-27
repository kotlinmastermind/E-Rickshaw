import hashlib
import hmac
import json
import uuid
from datetime import datetime, timedelta, timezone
from unittest.mock import AsyncMock, MagicMock, patch

import httpx
import pytest
from fastapi import HTTPException
from sqlalchemy import select

from backend.api.deps import get_current_admin
from backend.application.services.admin_advanced_service import AdminAdvancedService
from backend.application.services.commission_service import CommissionService
from backend.application.services.dispatch_advanced_service import DispatchAdvancedService
from backend.application.services.driver_financial_service import DriverFinancialService
from backend.application.services.payout_execution_service import PayoutExecutionService
from backend.core.config import settings
from backend.domain.models import (
    AdministrativeAuditLog,
    DriverCommissionSettlement,
    DriverLedgerEntry,
    DriverPayoutAccount,
    DriverPayoutRequest,
    DriverProfile,
    DriverWallet,
    PaymentWebhookInbox,
    Session,
    User,
)
from backend.infrastructure.security.crypto import create_jwt_token
from backend.infrastructure.security.payout_encryption import (
    decrypt_payout_destination,
    encrypt_payout_destination,
)
from backend.main import app


async def _seed_verified_driver_and_admin(db_session, *, balance_cents: int = 100000, commission_due_cents: int = 15000):
    suffix = uuid.uuid4().hex[:8]
    driver_user = User(
        id=str(uuid.uuid4()),
        phone_number=f"+9196{uuid.uuid4().int % 10**8:08d}",
        email=f"drv_{suffix}@example.test",
        full_name=f"Driver {suffix}",
        role="driver",
        is_active=True,
        is_verified=True,
    )
    admin_user = User(
        id=str(uuid.uuid4()),
        phone_number=f"+9195{uuid.uuid4().int % 10**8:08d}",
        email=f"adm_{suffix}@example.test",
        full_name=f"Admin {suffix}",
        role="admin",
        is_active=True,
        is_verified=True,
    )
    db_session.add_all([driver_user, admin_user])
    await db_session.flush()

    driver = DriverProfile(
        id=str(uuid.uuid4()),
        user_id=driver_user.id,
        license_number=f"DL-EXEC-{suffix}",
        approval_status="APPROVED",
    )
    db_session.add(driver)
    await db_session.flush()

    wallet = DriverWallet(driver_id=driver.id, balance_cents=balance_cents, currency="INR")
    db_session.add(wallet)

    if commission_due_cents > 0:
        db_session.add(DriverLedgerEntry(
            driver_id=driver.id,
            entry_type="DEBIT",
            category="COMMISSION_PAYABLE",
            amount_cents=-commission_due_cents,
            balance_after_cents=balance_cents,
            reference_id=f"ride_{suffix}",
            description="Deferred commission for test",
        ))

    sess = Session(
        id=str(uuid.uuid4()),
        user_id=driver_user.id,
        token_hash=f"tok_{uuid.uuid4().hex}",
        expires_at=datetime.now(timezone.utc) + timedelta(days=1),
    )
    db_session.add(sess)
    await db_session.commit()

    svc = DriverFinancialService(db_session)
    bank_res = await svc.add_payout_account(
        driver_user.id,
        {
            "account_type": "BANK_ACCOUNT",
            "account_number": "445566778899",
            "ifsc_code": "HDFC0001234",
            "beneficiary_name": driver_user.full_name,
        },
    )
    await AdminAdvancedService.verify_payout_account(
        db_session,
        account_id=bank_res["id"],
        admin_user_id=admin_user.id,
        verification_reference=f"HDFC-CHQ-{suffix}",
        verification_notes="Verified for execution workflow test",
    )
    await db_session.commit()
    token = create_jwt_token(user_id=driver_user.id, role="driver", session_id=sess.id)
    return driver_user, driver, wallet, bank_res["id"], admin_user, token


@pytest.mark.asyncio
async def test_encryption_key_rotation_and_fail_closed_and_no_secret_leakage(
    client, db_session, monkeypatch
):
    driver_user, driver, wallet, account_id, admin_user, driver_token = await _seed_verified_driver_and_admin(
        db_session
    )
    # 1. Key rotation: encrypt with old key, rotate to new primary + previous, decrypt works
    old_key = "old-payout-encryption-key-at-least-32-chars!"
    new_key = "new-payout-encryption-key-at-least-32-chars!"
    monkeypatch.setattr(settings, "PAYOUT_DESTINATION_ENCRYPTION_KEY", old_key)
    monkeypatch.setattr(settings, "PAYOUT_DESTINATION_ENCRYPTION_KEY_PREVIOUS", None)
    ct = encrypt_payout_destination("445566778899")
    monkeypatch.setattr(settings, "PAYOUT_DESTINATION_ENCRYPTION_KEY", new_key)
    monkeypatch.setattr(settings, "PAYOUT_DESTINATION_ENCRYPTION_KEY_PREVIOUS", old_key)
    assert decrypt_payout_destination(ct) == "445566778899"

    # 2. Missing/invalid key fails closed (never falls back to plaintext)
    monkeypatch.setattr(settings, "PAYOUT_DESTINATION_ENCRYPTION_KEY", "")
    monkeypatch.setattr(settings, "PAYOUT_DESTINATION_ENCRYPTION_KEY_PREVIOUS", None)
    with pytest.raises(RuntimeError):
        decrypt_payout_destination(ct)

    # Restore valid key
    monkeypatch.setattr(settings, "PAYOUT_DESTINATION_ENCRYPTION_KEY", "test-only-payout-destination-key-32-bytes-minimum")

    # 3. Register a UPI account and ensure ordinary Driver listing & Admin listing NEVER return decrypted secrets
    svc = DriverFinancialService(db_session)
    upi_res = await svc.add_payout_account(
        driver_user.id,
        {"account_type": "UPI", "upi_vpa": "secretpilot@okhdfcbank", "beneficiary_name": driver_user.full_name},
    )
    driver_accounts = await svc.get_payout_accounts(driver_user.id)
    assert "445566778899" not in json.dumps(driver_accounts)
    assert "secretpilot@okhdfcbank" not in json.dumps(driver_accounts)
    for row in driver_accounts:
        assert row["upi_vpa"] is None

    # Create payout request
    payout = await DispatchAdvancedService.request_payout(
        session=db_session,
        driver_id_or_user_id=driver.id,
        amount_cents=50000,
        payout_account_id=account_id,
        idempotency_key=f"exec_sec_{uuid.uuid4().hex[:12]}",
    )
    await db_session.commit()

    # Unauthorized destination retrieval rejected (driver user cannot retrieve)
    with pytest.raises(HTTPException) as exc_unauth:
        await PayoutExecutionService.retrieve_destination_for_authorized_transfer(
            db_session, payout_request_id=payout.id, admin_user_id=driver_user.id
        )
    assert exc_unauth.value.status_code == 403

    # REQUESTED status cannot retrieve destination yet (must be APPROVED first)
    with pytest.raises(HTTPException) as exc_state:
        await PayoutExecutionService.retrieve_destination_for_authorized_transfer(
            db_session, payout_request_id=payout.id, admin_user_id=admin_user.id
        )
    assert exc_state.value.status_code == 409

    # Cross-driver destination access rejected
    other_user = User(phone_number=f"+9194{uuid.uuid4().int % 10**8:08d}", full_name="Other", role="driver")
    db_session.add(other_user)
    await db_session.flush()
    other_driver = DriverProfile(user_id=other_user.id, license_number=f"DL-OTH-{uuid.uuid4().hex[:6]}")
    db_session.add(other_driver)
    await db_session.flush()
    await AdminAdvancedService.approve_payout(db_session, payout.id, admin_user.id)
    acct_obj = await db_session.get(DriverPayoutAccount, account_id)
    orig_driver_id = acct_obj.driver_id
    acct_obj.driver_id = other_driver.id
    await db_session.flush()
    with pytest.raises(HTTPException) as exc_cross:
        await PayoutExecutionService.retrieve_destination_for_authorized_transfer(
            db_session, payout_request_id=payout.id, admin_user_id=admin_user.id
        )
    assert exc_cross.value.status_code == 403
    acct_obj.driver_id = orig_driver_id
    await db_session.commit()

    # Check Admin listing and Driver history never expose decrypted destination
    app.dependency_overrides[get_current_admin] = lambda: admin_user
    try:
        admin_accts = await client.get("/api/v1/admin/payout-accounts")
        assert admin_accts.status_code == 200
        assert "445566778899" not in admin_accts.text
        assert "secretpilot@okhdfcbank" not in admin_accts.text

        admin_payouts = await client.get("/api/v1/admin/payouts")
        assert admin_payouts.status_code == 200
        assert "445566778899" not in admin_payouts.text

        drv_history = await client.get("/api/v1/drivers/payouts/history", headers={"Authorization": f"Bearer {driver_token}"})
        assert drv_history.status_code == 200
        assert "445566778899" not in drv_history.text
    finally:
        app.dependency_overrides.pop(get_current_admin, None)


@pytest.mark.asyncio
async def test_manual_transfer_two_step_workflow_and_illegal_transitions(client, db_session):
    driver_user, driver, wallet, account_id, admin_user, driver_token = await _seed_verified_driver_and_admin(
        db_session, balance_cents=100000, commission_due_cents=15000
    )

    req_res = await client.post(
        "/api/v1/drivers/payouts/request",
        json={
            "amount_cents": 50000,
            "payout_account_id": account_id,
            "idempotency_key": f"manual_flow_{uuid.uuid4().hex[:12]}",
        },
        headers={"Authorization": f"Bearer {driver_token}"},
    )
    assert req_res.status_code == 200
    payout_id = req_res.json()["payout_id"]
    await db_session.refresh(wallet)
    assert wallet.balance_cents == 35000  # 100000 - 15000 commission - 50000 hold

    app.dependency_overrides[get_current_admin] = lambda: admin_user
    try:
        # Illegal transition: cannot confirm manual transfer or record manual transfer before approval
        early_mt = await client.post(
            f"/api/v1/admin/payouts/{payout_id}/manual-transfer",
            json={"settlement_reference": "UTR9988776655"},
        )
        assert early_mt.status_code == 400

        # Approve (REQUESTED -> APPROVED)
        app_res = await client.post(f"/api/v1/admin/payouts/{payout_id}/approve")
        assert app_res.status_code == 200
        assert app_res.json()["status"] == "APPROVED"
        assert app_res.json()["execution_mode"] == "MANUAL_TRANSFER"

        # Duplicate approval rejected
        dup_app = await client.post(f"/api/v1/admin/payouts/{payout_id}/approve")
        assert dup_app.status_code == 400

        # Cannot confirm PAID directly from APPROVED without recording manual transfer
        early_confirm = await client.post(f"/api/v1/admin/payouts/{payout_id}/confirm-manual-transfer", json={})
        assert early_confirm.status_code == 400

        # Record manual transfer (APPROVED -> AWAITING_MANUAL_TRANSFER)
        mt_res = await client.post(
            f"/api/v1/admin/payouts/{payout_id}/manual-transfer",
            json={"settlement_reference": "IMPS202611270001", "evidence_notes": "NEFT batch #42"},
        )
        assert mt_res.status_code == 200
        assert mt_res.json()["status"] == "AWAITING_MANUAL_TRANSFER"

        # The initiating admin cannot self-confirm a manual transfer.
        self_confirm = await client.post(
            f"/api/v1/admin/payouts/{payout_id}/confirm-manual-transfer",
            json={"confirmation_notes": "Bank statement independently checked"},
        )
        assert self_confirm.status_code == 403

        # A different active admin must independently confirm the transfer.
        confirmer = User(
            id=str(uuid.uuid4()),
            phone_number=f"+9193{uuid.uuid4().int % 10**8:08d}",
            email=f"confirm_{uuid.uuid4().hex[:8]}@example.test",
            full_name="Independent payout confirmer",
            role="admin",
            is_active=True,
            is_verified=True,
        )
        db_session.add(confirmer)
        await db_session.commit()
        app.dependency_overrides[get_current_admin] = lambda: confirmer
        conf_res = await client.post(
            f"/api/v1/admin/payouts/{payout_id}/confirm-manual-transfer",
            json={"confirmation_notes": "Bank statement independently verified"},
        )
        assert conf_res.status_code == 200
        assert conf_res.json()["status"] == "PAID"

        # Duplicate manual confirmation rejected
        dup_conf = await client.post(f"/api/v1/admin/payouts/{payout_id}/confirm-manual-transfer", json={})
        assert dup_conf.status_code == 400

        # Illegal transition from terminal PAID -> REJECTED / FAILED
        bad_rej = await client.post(
            f"/api/v1/admin/payouts/{payout_id}/reject",
            json={"rejection_reason": "Cannot reject paid payout"},
        )
        assert bad_rej.status_code == 400

        # Verify wallet & commission invariants
        inv = await PayoutExecutionService.reconcile_payout_invariants(db_session, payout_id)
        assert inv["is_balanced"] is True
        assert inv["commission_settlement_count"] == 1
        assert inv["wallet_balance_cents"] == 35000

        # Ensure audit log never recorded decrypted account number
        audits = (
            await db_session.execute(
                select(AdministrativeAuditLog).where(AdministrativeAuditLog.target_id == payout_id)
            )
        ).scalars().all()
        assert len(audits) >= 3
        for a in audits:
            assert "445566778899" not in (a.changes_json or "")
    finally:
        app.dependency_overrides.pop(get_current_admin, None)


@pytest.mark.asyncio
async def test_provider_execution_webhooks_timeout_reconciliation_and_retry(client, db_session, monkeypatch):
    driver_user, driver, wallet, account_id, admin_user, _ = await _seed_verified_driver_and_admin(
        db_session, balance_cents=100000, commission_due_cents=10000
    )
    monkeypatch.setattr(settings, "PAYOUT_EXECUTION_MODE", "RAZORPAYX")
    monkeypatch.setattr(settings, "RAZORPAYX_ACCOUNT_NUMBER", "2323230041626905")
    monkeypatch.setattr(settings, "RAZORPAY_KEY_ID", "rzp_live_testkey123456")
    monkeypatch.setattr(settings, "RAZORPAY_KEY_SECRET", "live_secret_value_123456")
    wh_secret = "whsec_payout_test_secret_2026"
    monkeypatch.setattr(settings, "RAZORPAY_WEBHOOK_SECRET", wh_secret)

    payout = await DispatchAdvancedService.request_payout(
        session=db_session,
        driver_id_or_user_id=driver.id,
        amount_cents=50000,
        payout_account_id=account_id,
        idempotency_key=f"prov_exec_{uuid.uuid4().hex[:12]}",
    )
    payout_id = payout.id
    admin_user_id = admin_user.id
    driver_id = driver.id
    await AdminAdvancedService.approve_payout(db_session, payout_id, admin_user_id)
    await db_session.commit()

    # 1. Provider timeout leaves uncertain_outcome=True without reversing hold
    with patch("httpx.AsyncClient.post", new=AsyncMock(side_effect=httpx.TimeoutException("timeout"))):
        with pytest.raises(HTTPException) as exc_to:
            await PayoutExecutionService.execute_provider_payout(
                db_session, payout_id=payout_id, admin_user_id=admin_user_id
            )
        assert exc_to.value.status_code == 504
        await db_session.commit()

    await db_session.refresh(wallet)
    assert wallet.balance_cents == 40000

    # An operator must not fail/reverse an ambiguous provider transfer.
    with pytest.raises(HTTPException) as uncertain_fail:
        await PayoutExecutionService.fail_payout(
            db_session,
            payout_id=payout_id,
            actor_user_id=admin_user_id,
            failure_reason="Admin attempted failure before reconciliation",
        )
    assert uncertain_fail.value.status_code == 409
    await db_session.rollback()
    await db_session.refresh(wallet)
    assert wallet.balance_cents == 40000

    # If provider lookup has no result yet, do not submit a second POST.
    empty_lookup = MagicMock(status_code=200)
    empty_lookup.json.return_value = {"items": []}
    with patch("httpx.AsyncClient.get", new=AsyncMock(return_value=empty_lookup)), \
         patch("httpx.AsyncClient.post", new=AsyncMock(side_effect=AssertionError("Must not re-POST while outcome is unknown!"))):
        with pytest.raises(HTTPException) as still_unknown:
            await PayoutExecutionService.execute_provider_payout(
                db_session, payout_id=payout_id, admin_user_id=admin_user_id
            )
        assert still_unknown.value.status_code == 409
        await db_session.rollback()

    # 2. Re-executing after timeout reconciles uncertain outcome first (no duplicate POST)
    reconcile_resp = MagicMock(status_code=200)
    reconcile_resp.json.return_value = {
        "items": [{"id": "pout_rx_001", "status": "queued", "reference_id": payout_id}]
    }
    with patch("httpx.AsyncClient.get", new=AsyncMock(return_value=reconcile_resp)), \
         patch("httpx.AsyncClient.post", new=AsyncMock(side_effect=AssertionError("Must not re-POST before reconciling!"))):
        res_rec = await PayoutExecutionService.execute_provider_payout(
            db_session, payout_id=payout_id, admin_user_id=admin_user_id
        )
        assert res_rec.status == "PROCESSING"
        assert res_rec.provider_payout_id == "pout_rx_001"
        await db_session.commit()

    # 3. Invalid webhook signature rejected with 401 and persisted in PaymentWebhookInbox
    bad_body = json.dumps({"event": "payout.processed", "payload": {"payout": {"entity": {"id": "pout_rx_001"}}}}).encode()
    bad_wh = await client.post(
        "/api/v1/payments/payouts/webhook",
        content=bad_body,
        headers={"X-Razorpay-Signature": "forged_sig"},
    )
    assert bad_wh.status_code == 401
    bad_digest = hashlib.sha256(bad_body).hexdigest()
    bad_inbox = (await db_session.execute(select(PaymentWebhookInbox).where(PaymentWebhookInbox.event_digest == bad_digest))).scalar_one()
    assert bad_inbox.signature_verified is False
    assert bad_inbox.status == "FAILED"

    # 4. payout.failed webhook transitions PROCESSING -> FAILED, reverses hold ONCE, preserves commission settlement
    fail_body = json.dumps({
        "event": "payout.failed",
        "payload": {"payout": {"entity": {"id": "pout_rx_001", "reference_id": payout_id, "failure_reason": "Beneficiary bank offline"}}},
    }).encode()
    fail_sig = hmac.new(wh_secret.encode(), fail_body, hashlib.sha256).hexdigest()
    fail_wh = await client.post("/api/v1/payments/payouts/webhook", content=fail_body, headers={"X-Razorpay-Signature": fail_sig})
    assert fail_wh.status_code == 200
    assert fail_wh.json()["payout_status"] == "FAILED"

    # Duplicate webhook is idempotent
    dup_wh = await client.post("/api/v1/payments/payouts/webhook", content=fail_body, headers={"X-Razorpay-Signature": fail_sig})
    assert dup_wh.status_code == 200
    assert dup_wh.json()["status"] == "ALREADY_PROCESSED"

    await db_session.refresh(wallet)
    assert wallet.balance_cents == 90000  # 40000 + 50000 hold reversed; 10000 commission remains settled!
    assert await CommissionService.get_driver_commission_outstanding_cents(db_session, driver_id) == 0
    settlements = (await db_session.execute(select(DriverCommissionSettlement).where(DriverCommissionSettlement.driver_id == driver_id))).scalars().all()
    assert len(settlements) == 1

    # 5. Safe retry (FAILED -> APPROVED) re-reserves 50000 without duplicating commission settlement
    retried = await PayoutExecutionService.retry_failed_payout(db_session, payout_id=payout_id, admin_user_id=admin_user_id)
    assert retried.status == "APPROVED"
    assert retried.retry_count == 1
    await db_session.commit()
    await db_session.refresh(wallet)
    assert wallet.balance_cents == 40000

    # Execute provider payout on retry (APPROVED -> PROCESSING, NOT PAID until webhook)
    ok_post = MagicMock(status_code=201)
    ok_post.json.return_value = {"id": "pout_rx_002", "status": "processing", "reference_id": payout_id}
    with patch("httpx.AsyncClient.post", new=AsyncMock(return_value=ok_post)):
        proc = await PayoutExecutionService.execute_provider_payout(
            db_session, payout_id=payout_id, admin_user_id=admin_user_id
        )
        assert proc.status == "PROCESSING"
        assert proc.provider_payout_id == "pout_rx_002"
        await db_session.commit()

    # 6. payout.processed webhook transitions PROCESSING -> PAID
    paid_body = json.dumps({
        "event": "payout.processed",
        "payload": {"payout": {"entity": {"id": "pout_rx_002", "reference_id": payout_id, "utr": "UTR_RX_99887766"}}},
    }).encode()
    paid_sig = hmac.new(wh_secret.encode(), paid_body, hashlib.sha256).hexdigest()
    paid_wh = await client.post("/api/v1/payments/payouts/webhook", content=paid_body, headers={"X-Razorpay-Signature": paid_sig})
    assert paid_wh.status_code == 200
    assert paid_wh.json()["payout_status"] == "PAID"

    # 7. Out-of-order late payout.queued webhook after PAID does not regress state
    late_body = json.dumps({
        "event": "payout.queued",
        "payload": {"payout": {"entity": {"id": "pout_rx_002", "reference_id": payout_id}}},
    }).encode()
    late_sig = hmac.new(wh_secret.encode(), late_body, hashlib.sha256).hexdigest()
    late_wh = await client.post("/api/v1/payments/payouts/webhook", content=late_body, headers={"X-Razorpay-Signature": late_sig})
    assert late_wh.status_code == 200
    assert late_wh.json()["status"] == "IGNORED_OUT_OF_ORDER"
    assert late_wh.json()["payout_status"] == "PAID"

    inv = await PayoutExecutionService.reconcile_payout_invariants(db_session, payout_id)
    assert inv["is_balanced"] is True
    assert inv["commission_settlement_count"] == 1
    assert inv["wallet_balance_cents"] == 40000
