import uuid

import pytest
from fastapi import HTTPException
from sqlalchemy import select

from backend.application.services.commission_service import CommissionService
from backend.application.services.dispatch_advanced_service import DispatchAdvancedService
from backend.domain.models import (
    DriverCommissionSettlement, DriverLedgerEntry, DriverPayoutRequest,
    DriverProfile, DriverWallet, User,
)


async def _create_driver_with_wallet(db_session, balance_cents: int):
    suffix = uuid.uuid4().hex[:10]
    user = User(
        phone_number=f"+9199{uuid.uuid4().int % 10**10:010d}",
        full_name="Commission Settlement Driver",
        role="driver",
        is_active=True,
    )
    db_session.add(user)
    await db_session.flush()
    driver = DriverProfile(
        user_id=user.id,
        license_number=f"DL-COMM-{suffix}",
        approval_status="APPROVED",
    )
    db_session.add(driver)
    await db_session.flush()
    wallet = DriverWallet(driver_id=driver.id, balance_cents=balance_cents, currency="INR")
    db_session.add(wallet)
    await db_session.flush()
    return driver, wallet


@pytest.mark.asyncio
async def test_payout_settles_deferred_commission_before_holding_payout(db_session):
    driver, wallet = await _create_driver_with_wallet(db_session, 100000)
    db_session.add_all([
        DriverLedgerEntry(driver_id=driver.id, entry_type="DEBIT", category="COMMISSION_PAYABLE", amount_cents=-10000, balance_after_cents=100000, reference_id="ride-payable-1", description="Digital ride commission"),
        DriverLedgerEntry(driver_id=driver.id, entry_type="DEBIT", category="COMMISSION_RECEIVABLE", amount_cents=-5000, balance_after_cents=100000, reference_id="ride-receivable-1", description="Cash ride commission"),
    ])
    await db_session.commit()

    due_before = await CommissionService.get_driver_commission_outstanding_cents(db_session, driver.id)
    assert due_before == 15000

    payout = await DispatchAdvancedService.request_payout(
        session=db_session,
        driver_id_or_user_id=driver.id,
        amount_cents=80000,
        bank_details={"account_number_mask": "BANK *******1234"},
        idempotency_key=f"commission-payout-{uuid.uuid4().hex[:16]}",
    )
    await db_session.refresh(wallet)
    assert payout.status == "REQUESTED"
    assert wallet.balance_cents == 5000

    settlement = (await db_session.execute(
        select(DriverCommissionSettlement).where(DriverCommissionSettlement.payout_request_id == payout.id)
    )).scalar_one()
    assert settlement.amount_cents == 15000
    assert settlement.currency == "INR"
    assert await CommissionService.get_driver_commission_outstanding_cents(db_session, driver.id) == 0

    settlement_ledger = (await db_session.execute(
        select(DriverLedgerEntry).where(
            DriverLedgerEntry.driver_id == driver.id,
            DriverLedgerEntry.category == "COMMISSION_SETTLEMENT",
            DriverLedgerEntry.reference_id == payout.id,
        )
    )).scalar_one()
    assert settlement_ledger.amount_cents == -15000
    assert settlement_ledger.balance_after_cents == 85000

    # Idempotent retry must not settle commission twice or create another payout hold.
    retry = await DispatchAdvancedService.request_payout(
        session=db_session, driver_id_or_user_id=driver.id, amount_cents=80000,
        bank_details={"account_number_mask": "BANK *******1234"},
        idempotency_key=payout.idempotency_key,
    )
    await db_session.refresh(wallet)
    assert retry.id == payout.id
    assert wallet.balance_cents == 5000
    settlement_count = (await db_session.execute(
        select(DriverCommissionSettlement).where(DriverCommissionSettlement.driver_id == driver.id)
    )).scalars().all()
    assert len(settlement_count) == 1


@pytest.mark.asyncio
async def test_payout_rejects_amount_above_balance_after_commission_reserve(db_session):
    driver, wallet = await _create_driver_with_wallet(db_session, 100000)
    db_session.add(DriverLedgerEntry(
        driver_id=driver.id, entry_type="DEBIT", category="COMMISSION_PAYABLE",
        amount_cents=-15000, balance_after_cents=100000, reference_id="ride-commission-reserve",
        description="Deferred commission",
    ))
    await db_session.commit()

    with pytest.raises(HTTPException) as error:
        await DispatchAdvancedService.request_payout(
            session=db_session, driver_id_or_user_id=driver.id, amount_cents=86000,
            bank_details={"account_number_mask": "BANK *******1234"},
            idempotency_key=f"commission-payout-reject-{uuid.uuid4().hex[:12]}",
        )
    assert error.value.status_code == 400
    assert "withdrawable" in str(error.value.detail).lower()
    await db_session.refresh(wallet)
    assert wallet.balance_cents == 100000
    settlements = (await db_session.execute(
        select(DriverCommissionSettlement).where(DriverCommissionSettlement.driver_id == driver.id)
    )).scalars().all()
    assert settlements == []
    assert await CommissionService.get_driver_commission_outstanding_cents(db_session, driver.id) == 15000