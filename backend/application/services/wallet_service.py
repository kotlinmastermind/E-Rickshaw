"""
Wallet Service - Production Ledger & Financial Invariants
Handles immutable double-entry credit and debit transactions, balance queries,
compensating transactions, row locking, and idempotency protection.
"""
import uuid
from typing import Optional, List, Dict, Any
from datetime import datetime, timezone
from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession
from fastapi import HTTPException, status
from backend.domain.models import Wallet, WalletTransaction

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
            session.add(wallet)
            await session.flush()
        return wallet

    @staticmethod
    async def get_balance(session: AsyncSession, user_id: str) -> dict:
        wallet = await WalletService.get_or_create_wallet(session, user_id)
        return {
            "wallet_id": wallet.id,
            "user_id": wallet.user_id,
            "balance_cents": wallet.balance_cents,
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

        new_balance = wallet.balance_cents + amount_cents
        wallet.balance_cents = new_balance

        tx = WalletTransaction(
            wallet_id=wallet.id,
            transaction_type=transaction_type if transaction_type in ["CREDIT", "COMPENSATION_CREDIT"] else "CREDIT",
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

        if not allow_negative and wallet.balance_cents < amount_cents:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Insufficient wallet balance. Available: {wallet.balance_cents} cents, Required: {amount_cents} cents"
            )

        key = idempotency_key or f"tx_deb_{uuid.uuid4()}"
        if idempotency_key:
            idem_stmt = select(WalletTransaction).where(WalletTransaction.idempotency_key == idempotency_key)
            idem_res = await session.execute(idem_stmt)
            existing = idem_res.scalars().first()
            if existing:
                return existing

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
    async def reconcile_wallet_balance(
        session: AsyncSession,
        user_id: str,
        auto_correct: bool = True
    ) -> Dict[str, Any]:
        """
        Task 039: Reconciles the cached wallet balance against the authoritative ledger.
        Computes SUM(CREDIT) - SUM(DEBIT) from immutable WalletTransaction records.
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

        if discrepancy != 0 and auto_correct:
            wallet.balance_cents = ledger_balance

        return {
            "wallet_id": wallet.id,
            "user_id": user_id,
            "cached_balance_cents": cached_balance,
            "ledger_balance_cents": ledger_balance,
            "discrepancy_cents": discrepancy,
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
