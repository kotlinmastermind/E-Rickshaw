"""
Wallet Service - Production Ledger & Financial Invariants
Handles immutable double-entry credit and debit transactions, balance queries,
compensating transactions, row locking, and idempotency protection.
"""
import uuid
from typing import Optional, List, Dict, Any
from datetime import datetime, timezone
from sqlalchemy import select, update
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession
from fastapi import HTTPException, status
from backend.domain.models import Wallet, WalletTransaction, RefundLiability, PaymentReconciliationAnomaly

class WalletService:
    @staticmethod
    async def get_or_create_wallet(
        session: AsyncSession,
        user_id: str,
        currency: str = "INR",
        for_update: bool = False
    ) -> Wallet:
        """
        Retrieves or creates a wallet for the given user.
        Supports pessimistic row locking (Task 037) to prevent concurrent race conditions.
        """
        stmt = select(Wallet).where(Wallet.user_id == user_id)
        if for_update:
            bind = session.bind
            if bind and getattr(bind.dialect, "name", "") != "sqlite":
                stmt = stmt.with_for_update()

        res = await session.execute(stmt)
        wallet = res.scalars().first()
        if not wallet:
            wallet = Wallet(
                user_id=user_id,
                currency=currency,
                balance_cents=0
            )
            try:
                async with session.begin_nested():
                    session.add(wallet)
                    await session.flush()
            except IntegrityError:
                wallet = (await session.execute(stmt)).scalars().first()
                if not wallet:
                    raise
        return wallet

    @staticmethod
    async def get_balance(session: AsyncSession, user_id: str) -> dict:
        wallet = await WalletService.get_or_create_wallet(session, user_id)
        return {
            "wallet_id": wallet.id,
            "user_id": wallet.user_id,
            "balance_cents": wallet.balance_cents,
            "debt_cents": getattr(wallet, "debt_cents", 0),
            "currency": wallet.currency,
            "updated_at": wallet.updated_at.isoformat() if wallet.updated_at else datetime.now(timezone.utc).isoformat()
        }

    @staticmethod
    async def credit_wallet(
        session: AsyncSession,
        user_id: str,
        amount_cents: int,
        transaction_type: str = "CREDIT",
        source: str = "TOP_UP",
        reference_id: Optional[str] = None,
        description: str = "",
        idempotency_key: Optional[str] = None
    ) -> WalletTransaction:
        if amount_cents <= 0:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Credit amount must be positive integer cents")

        wallet = await WalletService.get_or_create_wallet(session, user_id, for_update=True)

        # Idempotency check (Task 034, 038)
        key = idempotency_key or f"tx_cred_{uuid.uuid4()}"
        if idempotency_key:
            idem_stmt = select(WalletTransaction).where(WalletTransaction.idempotency_key == idempotency_key)
            idem_res = await session.execute(idem_stmt)
            existing = idem_res.scalars().first()
            if existing:
                return existing

        # Automatic debt recovery if user has outstanding refund liability
        remaining_credit = amount_cents
        if hasattr(wallet, "debt_cents") and wallet.debt_cents > 0:
            debt_recovery = min(remaining_credit, wallet.debt_cents)
            wallet.debt_cents -= debt_recovery
            remaining_credit -= debt_recovery

            # Settle active RefundLiability records
            liab_stmt = select(RefundLiability).where(
                RefundLiability.user_id == user_id,
                RefundLiability.status == "ACTIVE"
            ).order_by(RefundLiability.created_at.asc()).with_for_update()
            liab_res = await session.execute(liab_stmt)
            liabilities = liab_res.scalars().all()
            rec_to_apply = debt_recovery
            for liab in liabilities:
                if rec_to_apply <= 0:
                    break
                applicable = min(rec_to_apply, liab.outstanding_recovery_cents)
                liab.outstanding_recovery_cents -= applicable
                if liab.outstanding_recovery_cents == 0:
                    liab.status = "RECOVERED"
                rec_to_apply -= applicable

            debt_tx = WalletTransaction(
                wallet_id=wallet.id,
                transaction_type="DEBT_SETTLEMENT",
                amount_cents=debt_recovery,
                balance_after_cents=wallet.balance_cents,
                source="REFUND_DEBT_RECOVERY",
                reference_id=reference_id,
                idempotency_key=f"debt_rec_{key}",
                description=f"Auto-recovery of outstanding refund debt ({debt_recovery} cents)"
            )
            session.add(debt_tx)

        new_balance = wallet.balance_cents + remaining_credit
        wallet.balance_cents = new_balance

        tx = WalletTransaction(
            wallet_id=wallet.id,
            transaction_type=transaction_type if transaction_type in ["CREDIT", "COMPENSATION_CREDIT"] else "CREDIT",
            amount_cents=remaining_credit,
            balance_after_cents=new_balance,
            source=source,
            reference_id=reference_id,
            idempotency_key=key,
            description=description
        )
        session.add(tx)
        await session.flush()
        return tx

    @staticmethod
    async def debit_wallet(
        session: AsyncSession,
        user_id: str,
        amount_cents: int,
        transaction_type: str = "DEBIT",
        source: str = "RIDE_PAYMENT",
        reference_id: Optional[str] = None,
        description: str = "",
        allow_negative: bool = False,
        idempotency_key: Optional[str] = None
    ) -> WalletTransaction:
        if amount_cents <= 0:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Debit amount must be positive integer cents")

        wallet = await WalletService.get_or_create_wallet(session, user_id, for_update=True)

        key = idempotency_key or f"tx_deb_{uuid.uuid4()}"
        if idempotency_key:
            idem_stmt = select(WalletTransaction).where(WalletTransaction.idempotency_key == idempotency_key)
            idem_res = await session.execute(idem_stmt)
            existing = idem_res.scalars().first()
            if existing:
                expected_type = transaction_type if transaction_type in ["DEBIT", "COMPENSATION_DEBIT"] else "DEBIT"
                if (
                    existing.wallet_id != wallet.id
                    or existing.amount_cents != amount_cents
                    or existing.transaction_type != expected_type
                    or existing.source != source
                ):
                    raise HTTPException(
                        status_code=status.HTTP_409_CONFLICT,
                        detail="Wallet debit idempotency key was already used for a different transaction.",
                    )
                return existing

        if not allow_negative and wallet.balance_cents < amount_cents:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Insufficient wallet balance. Available: {wallet.balance_cents} cents, Required: {amount_cents} cents"
            )

        new_balance = wallet.balance_cents - amount_cents
        wallet.balance_cents = new_balance

        tx = WalletTransaction(
            wallet_id=wallet.id,
            transaction_type=transaction_type if transaction_type in ["DEBIT", "COMPENSATION_DEBIT"] else "DEBIT",
            amount_cents=amount_cents,
            balance_after_cents=new_balance,
            source=source,
            reference_id=reference_id,
            idempotency_key=key,
            description=description
        )
        session.add(tx)
        await session.flush()
        return tx

    @staticmethod
    async def process_refund_clawback(
        session: AsyncSession,
        user_id: str,
        refund_amount_cents: int,
        payment_id: str,
        gateway_refund_id: str,
        idempotency_key: str
    ) -> Dict[str, Any]:
        """
        Durable financial refund recovery mechanism.
        Never drops an authoritative refund event due to insufficient wallet balance.
        Maintains non-negative spendable balance while tracking explicit platform receivable / debt.
        """
        if refund_amount_cents <= 0:
            return {"status": "SKIPPED", "detail": "Refund amount zero"}

        wallet = await WalletService.get_or_create_wallet(session, user_id, for_update=True)

        # Idempotency guard at liability level
        existing_liab = (await session.execute(
            select(RefundLiability).where(RefundLiability.gateway_refund_id == gateway_refund_id)
        )).scalar_one_or_none()
        if existing_liab:
            return {
                "status": "ALREADY_PROCESSED",
                "liability_id": existing_liab.id,
                "debited_from_wallet_cents": existing_liab.debited_from_wallet_cents,
                "outstanding_recovery_cents": existing_liab.outstanding_recovery_cents
            }

        available = max(0, wallet.balance_cents)

        debited_cents = min(available, refund_amount_cents)
        outstanding_debt_cents = refund_amount_cents - debited_cents

        if debited_cents > 0:
            wallet.balance_cents -= debited_cents
            tx_debit = WalletTransaction(
                wallet_id=wallet.id,
                transaction_type="DEBIT",
                amount_cents=debited_cents,
                balance_after_cents=wallet.balance_cents,
                source="TOPUP_REFUND",
                reference_id=payment_id,
                idempotency_key=f"ref_deb_{gateway_refund_id}",
                description=f"Refund clawback for payment {payment_id} (Refund: {gateway_refund_id})"
            )
            session.add(tx_debit)

        if outstanding_debt_cents > 0:
            if hasattr(wallet, "debt_cents"):
                wallet.debt_cents += outstanding_debt_cents
            tx_debt = WalletTransaction(
                wallet_id=wallet.id,
                transaction_type="REFUND_DEBT_ACCRUAL",
                amount_cents=outstanding_debt_cents,
                balance_after_cents=wallet.balance_cents,
                source="TOPUP_REFUND_DEBT",
                reference_id=payment_id,
                idempotency_key=f"ref_debt_{gateway_refund_id}",
                description=f"Unrecovered refund liability: user spent funds prior to gateway refund (Refund: {gateway_refund_id})"
            )
            session.add(tx_debt)

        liability = RefundLiability(
            payment_id=payment_id,
            user_id=user_id,
            gateway_refund_id=gateway_refund_id,
            refund_amount_cents=refund_amount_cents,
            debited_from_wallet_cents=debited_cents,
            outstanding_recovery_cents=outstanding_debt_cents,
            status="ACTIVE" if outstanding_debt_cents > 0 else "RECOVERED"
        )
        session.add(liability)
        await session.flush()

        return {
            "status": "SUCCESS",
            "liability_id": liability.id,
            "debited_from_wallet_cents": debited_cents,
            "outstanding_recovery_cents": outstanding_debt_cents,
            "wallet_balance_cents": wallet.balance_cents,
            "wallet_debt_cents": getattr(wallet, "debt_cents", 0)
        }

    @staticmethod
    async def reverse_refund_clawback(
        session: AsyncSession,
        user_id: str,
        gateway_refund_id: str,
        payment_id: str,
        reason: str = "Gateway refund failed",
    ) -> Dict[str, Any]:
        """
        Safely reverses an earlier refund clawback and/or accrued refund debt when a
        refund fails at the payment gateway, using immutable compensating ledger entries.
        """
        wallet = await WalletService.get_or_create_wallet(session, user_id, for_update=True)
        liab_stmt = select(RefundLiability).where(RefundLiability.gateway_refund_id == gateway_refund_id)
        if getattr(session.bind, "dialect", None) and session.bind.dialect.name != "sqlite":
            liab_stmt = liab_stmt.with_for_update()
        liab = (await session.execute(liab_stmt)).scalar_one_or_none()
        if not liab:
            return {"status": "NO_CLAWBACK_FOUND"}
        if liab.status == "REVERSED_ON_FAILURE":
            return {"status": "ALREADY_REVERSED", "liability_id": liab.id}

        debt_to_reverse = min(int(liab.outstanding_recovery_cents or 0), int(getattr(wallet, "debt_cents", 0) or 0))
        if debt_to_reverse > 0:
            wallet.debt_cents -= debt_to_reverse
            liab.outstanding_recovery_cents = max(0, int(liab.outstanding_recovery_cents or 0) - debt_to_reverse)
            debt_rev_key = f"ref_debt_rev_{gateway_refund_id}"
            existing_debt_rev = (
                await session.execute(select(WalletTransaction).where(WalletTransaction.idempotency_key == debt_rev_key))
            ).scalar_one_or_none()
            if not existing_debt_rev:
                session.add(
                    WalletTransaction(
                        wallet_id=wallet.id,
                        transaction_type="REFUND_DEBT_REVERSAL",
                        amount_cents=debt_to_reverse,
                        balance_after_cents=wallet.balance_cents,
                        source="TOPUP_REFUND_FAILURE_REVERSAL",
                        reference_id=payment_id,
                        idempotency_key=debt_rev_key,
                        description=f"Reversed refund debt accrual on failed refund {gateway_refund_id}: {reason}",
                    )
                )

        liab.status = "REVERSED_ON_FAILURE"
        wallet_restore_cents = int(liab.debited_from_wallet_cents or 0)
        if wallet_restore_cents > 0:
            await WalletService.credit_wallet(
                session=session,
                user_id=user_id,
                amount_cents=wallet_restore_cents,
                transaction_type="COMPENSATION_CREDIT",
                source="TOPUP_REFUND_FAILURE_REVERSAL",
                reference_id=payment_id,
                description=f"Restored wallet balance after failed refund {gateway_refund_id}: {reason}",
                idempotency_key=f"ref_rev_{gateway_refund_id}",
            )
            liab.debited_from_wallet_cents = 0

        await session.flush()
        return {
            "status": "REVERSED",
            "liability_id": liab.id,
            "restored_wallet_cents": wallet_restore_cents,
            "reversed_debt_cents": debt_to_reverse,
        }

    @staticmethod
    async def reconcile_wallet_balance(
        session: AsyncSession,
        user_id: str,
        auto_correct: bool = True
    ) -> Dict[str, Any]:
        """
        Task 039: Reconciles the cached wallet balance against the authoritative ledger.
        Computes SUM(CREDIT) - SUM(DEBIT) from immutable WalletTransaction records
        and logs an auditable anomaly before applying any correction.
        """
        wallet = await WalletService.get_or_create_wallet(session, user_id, for_update=True)

        stmt = select(WalletTransaction).where(WalletTransaction.wallet_id == wallet.id)
        res = await session.execute(stmt)
        txs = res.scalars().all()

        ledger_balance = 0
        for tx in txs:
            if "CREDIT" in tx.transaction_type:
                ledger_balance += tx.amount_cents
            elif "DEBIT" in tx.transaction_type:
                ledger_balance -= tx.amount_cents

        cached_balance = wallet.balance_cents
        discrepancy = cached_balance - ledger_balance

        if discrepancy != 0:
            session.add(
                PaymentReconciliationAnomaly(
                    provider_name="INTERNAL_WALLET",
                    anomaly_type="WALLET_BALANCE_DISCREPANCY",
                    expected_amount_cents=ledger_balance,
                    raw_gateway_amount_cents=cached_balance,
                    applied_amount_cents=ledger_balance if auto_correct else cached_balance,
                    excess_amount_cents=abs(discrepancy),
                    currency=wallet.currency or "INR",
                    status="AUTO_CORRECTED" if auto_correct else "OPEN",
                    reason=(
                        f"Cached balance ({cached_balance}) differed from ledger balance ({ledger_balance}) "
                        f"for user {user_id} (auto_correct={auto_correct})."
                    ),
                )
            )
            if auto_correct:
                wallet.balance_cents = ledger_balance

        # Reconcile outstanding refund liabilities
        liab_stmt = select(RefundLiability).where(
            RefundLiability.user_id == user_id,
            RefundLiability.status == "ACTIVE"
        )
        liabs = (await session.execute(liab_stmt)).scalars().all()
        ledger_debt_cents = sum(l.outstanding_recovery_cents for l in liabs)
        if hasattr(wallet, "debt_cents") and wallet.debt_cents != ledger_debt_cents:
            session.add(
                PaymentReconciliationAnomaly(
                    provider_name="INTERNAL_WALLET",
                    anomaly_type="WALLET_DEBT_DISCREPANCY",
                    expected_amount_cents=ledger_debt_cents,
                    raw_gateway_amount_cents=wallet.debt_cents,
                    applied_amount_cents=ledger_debt_cents if auto_correct else wallet.debt_cents,
                    excess_amount_cents=abs(wallet.debt_cents - ledger_debt_cents),
                    currency=wallet.currency or "INR",
                    status="AUTO_CORRECTED" if auto_correct else "OPEN",
                    reason=(
                        f"Cached debt ({wallet.debt_cents}) differed from active liability sum ({ledger_debt_cents}) "
                        f"for user {user_id} (auto_correct={auto_correct})."
                    ),
                )
            )
            if auto_correct:
                wallet.debt_cents = ledger_debt_cents

        return {
            "wallet_id": wallet.id,
            "user_id": user_id,
            "cached_balance_cents": cached_balance,
            "ledger_balance_cents": ledger_balance,
            "discrepancy_cents": discrepancy,
            "outstanding_debt_cents": getattr(wallet, "debt_cents", 0),
            "reconciled": True if (discrepancy == 0 or auto_correct) else False,
            "transaction_count": len(txs)
        }

    @staticmethod
    async def process_wallet_refund(
        session: AsyncSession,
        user_id: str,
        amount_cents: int,
        payment_id: str,
        reason: str
    ) -> WalletTransaction:
        """
        Task 040: Refunds a payment amount back to the user's wallet with immutable ledger entry.
        """
        return await WalletService.credit_wallet(
            session=session,
            user_id=user_id,
            amount_cents=amount_cents,
            transaction_type="CREDIT",
            source="REFUND",
            reference_id=payment_id,
            description=f"Refund for payment {payment_id}: {reason}",
            idempotency_key=f"refund_{payment_id}_{amount_cents}"
        )

    @staticmethod
    async def create_compensating_entry(
        session: AsyncSession,
        original_tx_id: str,
        reason: str,
        admin_user_id: str
    ) -> WalletTransaction:
        orig_stmt = select(WalletTransaction).where(WalletTransaction.id == original_tx_id)
        orig_res = await session.execute(orig_stmt)
        orig_tx = orig_res.scalars().first()
        if not orig_tx:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Original transaction not found")

        wallet_stmt = select(Wallet).where(Wallet.id == orig_tx.wallet_id)
        w_res = await session.execute(wallet_stmt)
        wallet = w_res.scalars().first()

        if orig_tx.transaction_type in ["CREDIT", "COMPENSATION_CREDIT"]:
            comp_type = "COMPENSATION_DEBIT"
            new_balance = wallet.balance_cents - orig_tx.amount_cents
        else:
            comp_type = "COMPENSATION_CREDIT"
            new_balance = wallet.balance_cents + orig_tx.amount_cents

        wallet.balance_cents = new_balance

        comp_tx = WalletTransaction(
            wallet_id=wallet.id,
            transaction_type=comp_type,
            amount_cents=orig_tx.amount_cents,
            balance_after_cents=new_balance,
            source="ADMIN_ADJUSTMENT",
            reference_id=orig_tx.id,
            idempotency_key=f"comp_{orig_tx.id}_{uuid.uuid4().hex[:8]}",
            description=f"Compensating entry for {orig_tx.id} by admin {admin_user_id}: {reason}"
        )
        session.add(comp_tx)
        await session.flush()
        return comp_tx

    @staticmethod
    async def get_history(
        session: AsyncSession,
        user_id: str,
        limit: int = 50,
        offset: int = 0
    ) -> List[WalletTransaction]:
        wallet = await WalletService.get_or_create_wallet(session, user_id)
        stmt = (
            select(WalletTransaction)
            .where(WalletTransaction.wallet_id == wallet.id)
            .order_by(WalletTransaction.created_at.desc())
            .limit(limit)
            .offset(offset)
        )
        res = await session.execute(stmt)
        return list(res.scalars().all())
