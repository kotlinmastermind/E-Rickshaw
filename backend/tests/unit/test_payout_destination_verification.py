import json
import pathlib
import uuid

import pytest
from sqlalchemy import select

from backend.api.deps import get_current_admin
from backend.application.services.driver_financial_service import DriverFinancialService
from backend.domain.models import (
    AdministrativeAuditLog,
    DriverPayoutAccount,
    DriverPayoutRequest,
    DriverProfile,
    DriverWallet,
    User,
)
from backend.main import app


async def _seed_driver(db_session, *, balance_cents: int = 100000):
    suffix = uuid.uuid4().hex[:8]
    user = User(
        id=str(uuid.uuid4()),
        phone_number=f"+9198{uuid.uuid4().int % 10**8:08d}",
        email=f"driver_{suffix}@example.test",
        full_name=f"Pilot {suffix}",
        role="driver",
        is_active=True,
        is_verified=True,
    )
    db_session.add(user)
    await db_session.flush()
    driver = DriverProfile(
        id=str(uuid.uuid4()),
        user_id=user.id,
        license_number=f"DL-PAY-{suffix}",
        approval_status="APPROVED",
    )
    db_session.add(driver)
    await db_session.flush()
    wallet = DriverWallet(driver_id=driver.id, balance_cents=balance_cents, currency="INR")
    db_session.add(wallet)
    await db_session.commit()
    return user, driver, wallet


@pytest.mark.asyncio
async def test_add_payout_account_is_pending_without_fake_penny_or_ifsc(db_session):
    user, driver, _ = await _seed_driver(db_session)
    svc = DriverFinancialService(db_session)

    bank_res = await svc.add_payout_account(
        user.id,
        {
            "account_type": "BANK_ACCOUNT",
            "account_number": "123456789012",
            "ifsc_code": "HDFC0001234",
            "beneficiary_name": "Pilot Bank",
            "is_primary": True,
        },
    )
    assert bank_res["verification_status"] == "PENDING_VERIFICATION"
    assert bank_res["is_primary"] is False

    upi_res = await svc.add_payout_account(
        user.id,
        {
            "account_type": "UPI",
            "upi_vpa": "pilot@okaxis",
            "beneficiary_name": "Pilot UPI",
        },
    )
    assert upi_res["verification_status"] == "PENDING_VERIFICATION"
    assert upi_res["is_primary"] is False

    bank_acc = await db_session.get(DriverPayoutAccount, bank_res["id"])
    upi_acc = await db_session.get(DriverPayoutAccount, upi_res["id"])
    assert bank_acc.penny_drop_reference is None
    assert bank_acc.verification_method is None
    assert bank_acc.verified_by is None
    assert upi_acc.penny_drop_reference is None
    assert upi_acc.ifsc_code is None


@pytest.mark.asyncio
async def test_admin_payout_account_verification_rejection_and_payout_enforcement(
    client, db_session, customer_token
):
    from backend.domain.models import Session
    from backend.infrastructure.security.crypto import create_jwt_token
    from datetime import datetime, timedelta, timezone

    driver_user, driver, wallet = await _seed_driver(db_session, balance_cents=100000)
    driver_sess = Session(
        id=str(uuid.uuid4()),
        user_id=driver_user.id,
        token_hash=f"td_{uuid.uuid4().hex}",
        expires_at=datetime.now(timezone.utc) + timedelta(days=1),
    )
    db_session.add(driver_sess)
    await db_session.commit()
    driver_token = create_jwt_token(user_id=driver_user.id, role="driver", session_id=driver_sess.id)

    svc = DriverFinancialService(db_session)
    created = await svc.add_payout_account(
        driver_user.id,
        {
            "account_type": "BANK_ACCOUNT",
            "account_number": "987654321098",
            "ifsc_code": "ICIC0004321",
            "beneficiary_name": "Verified Pilot",
        },
    )
    account_id = created["id"]

    # 1. Pending account cannot be set primary or used for payout.
    primary_fail = await client.post(
        f"/api/v1/drivers/financials/payout-accounts/{account_id}/primary",
        headers={"Authorization": f"Bearer {driver_token}"},
    )
    assert primary_fail.status_code == 400

    pending_payout = await client.post(
        "/api/v1/drivers/payouts/request",
        json={
            "amount_cents": 25000,
            "payout_account_id": account_id,
            "idempotency_key": f"payout_pending_{uuid.uuid4().hex[:12]}",
        },
        headers={"Authorization": f"Bearer {driver_token}"},
    )
    assert pending_payout.status_code == 400
    assert "not verified" in pending_payout.json()["detail"].lower()

    # 2. Unauthorized callers cannot list/verify/reject payout accounts.
    unauth = await client.get("/api/v1/admin/payout-accounts")
    assert unauth.status_code == 401

    non_admin = await client.post(
        f"/api/v1/admin/payout-accounts/{account_id}/verify",
        json={"verification_reference": "ICIC-NEFT-CHK-20261127"},
        headers={"Authorization": f"Bearer {customer_token}"},
    )
    assert non_admin.status_code == 403

    driver_attempt = await client.post(
        f"/api/v1/admin/payout-accounts/{account_id}/verify",
        json={"verification_reference": "ICIC-NEFT-CHK-20261127"},
        headers={"Authorization": f"Bearer {driver_token}"},
    )
    assert driver_attempt.status_code == 403

    admin = User(
        id=str(uuid.uuid4()),
        phone_number=f"+9197{uuid.uuid4().int % 10**8:08d}",
        email=f"admin_{uuid.uuid4().hex[:8]}@example.test",
        full_name="Finance Admin",
        role="admin",
        is_active=True,
        is_verified=True,
    )
    db_session.add(admin)
    await db_session.commit()

    app.dependency_overrides[get_current_admin] = lambda: admin
    try:
        # 3. Invalid evidence references are rejected.
        for bad_ref in ("abc", "PENNY-FAKE123", "verified", "test"):
            res = await client.post(
                f"/api/v1/admin/payout-accounts/{account_id}/verify",
                json={"verification_reference": bad_ref},
            )
            assert res.status_code in (400, 422), f"Expected rejection for {bad_ref}, got {res.status_code}"

        # 4. Admin lists pending accounts and manually verifies with evidence + notes.
        listed = await client.get("/api/v1/admin/payout-accounts?status_filter=PENDING_VERIFICATION")
        assert listed.status_code == 200
        assert any(row["id"] == account_id for row in listed.json())

        verified_res = await client.post(
            f"/api/v1/admin/payout-accounts/{account_id}/verify",
            json={
                "verification_reference": "ICIC-CHQ-VER-20261127-99",
                "verification_notes": "Cancelled cheque verified against driver KYC",
            },
        )
        assert verified_res.status_code == 200, verified_res.text
        v_body = verified_res.json()
        assert v_body["verification_status"] == "VERIFIED"
        assert v_body["verification_method"] == "MANUAL_ADMIN_REVIEW"
        assert v_body["verified_by"] == admin.id
        assert v_body["verified_at"] is not None
        assert v_body["is_primary"] is True

        # 5. Duplicate verification fails with 409 Conflict.
        dup_res = await client.post(
            f"/api/v1/admin/payout-accounts/{account_id}/verify",
            json={"verification_reference": "ICIC-CHQ-VER-20261127-99"},
        )
        assert dup_res.status_code == 409

        # Audit log recorded admin identity and provenance.
        audit_row = (
            await db_session.execute(
                select(AdministrativeAuditLog).where(
                    AdministrativeAuditLog.target_id == account_id,
                    AdministrativeAuditLog.action == "PAYOUT_ACCOUNT_MANUAL_VERIFIED",
                )
            )
        ).scalar_one()
        audit_data = json.loads(audit_row.changes_json)
        assert audit_row.admin_user_id == admin.id
        assert audit_data["verification_method"] == "MANUAL_ADMIN_REVIEW"
        assert audit_data["verification_reference"] == "ICIC-CHQ-VER-20261127-99"

        # 6. Verified primary account succeeds in payout request and stores immutable snapshot.
        payout_ok = await client.post(
            "/api/v1/drivers/payouts/request",
            json={
                "amount_cents": 25000,
                "payout_account_id": account_id,
                "idempotency_key": f"payout_verified_{uuid.uuid4().hex[:12]}",
            },
            headers={"Authorization": f"Bearer {driver_token}"},
        )
        assert payout_ok.status_code == 200, payout_ok.text
        payout_id = payout_ok.json()["payout_id"]
        payout_row = await db_session.get(DriverPayoutRequest, payout_id)
        assert payout_row.payout_account_id == account_id
        assert payout_row.bank_account_masked == "******1098"
        assert payout_row.ifsc_code_snapshot == "ICIC0004321"
        assert payout_row.beneficiary_name_snapshot == "Verified Pilot"

        # 7. Second account rejected by admin cannot be used for payout or set primary.
        second = await svc.add_payout_account(
            driver_user.id,
            {
                "account_type": "UPI",
                "upi_vpa": "mismatch@okicici",
                "beneficiary_name": "Wrong Holder",
            },
        )
        rej_res = await client.post(
            f"/api/v1/admin/payout-accounts/{second['id']}/reject",
            json={"rejection_reason": "UPI holder name does not match KYC"},
        )
        assert rej_res.status_code == 200
        assert rej_res.json()["verification_status"] == "REJECTED"
        assert rej_res.json()["is_primary"] is False

        rej_payout = await client.post(
            "/api/v1/drivers/payouts/request",
            json={
                "amount_cents": 15000,
                "payout_account_id": second["id"],
                "idempotency_key": f"payout_rejected_{uuid.uuid4().hex[:12]}",
            },
            headers={"Authorization": f"Bearer {driver_token}"},
        )
        assert rej_payout.status_code == 400
    finally:
        app.dependency_overrides.pop(get_current_admin, None)


def test_admin_spa_contains_payout_account_verification_ui():
    admin_html = pathlib.Path("backend/static/admin/index.html").read_text(encoding="utf-8")
    assert "/api/v1/admin/payout-accounts" in admin_html
    assert "openPayoutAccountVerifyModal" in admin_html
    assert "openPayoutAccountRejectModal" in admin_html
    assert "submitPayoutAccountVerify" in admin_html
    assert "submitPayoutAccountReject" in admin_html
