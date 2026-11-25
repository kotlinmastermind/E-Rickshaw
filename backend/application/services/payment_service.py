"""
Authoritative Payment & Financial Lifecycle Service (Razorpay & Cash).

Provides:
- Centralized Payment and Refund state machines with explicit transition guards
- Single authoritative capture finalization engine (WALLET_TOPUP & RIDE_FARE)
- Single consolidated Razorpay webhook processor with raw-byte HMAC verification,
  durable inbox lifecycle, concurrency-safe deduplication, strict entity binding,
  excess refund protection, and dedicated reconciliation anomaly recording
- Separated physical cash settlement handler
"""
import asyncio
import hashlib
import json
import logging
import uuid
from datetime import datetime, timedelta, timezone
from typing import Any, Dict, Optional

from fastapi import HTTPException, status
from sqlalchemy import or_, select, update
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from backend.core.config import settings
from backend.domain.models import (
    CustomerProfile,
    DriverLedgerEntry,
    DriverProfile,
    DriverWallet,
    Payment,
    PaymentEvent,
    PaymentOrderAttempt,
    PaymentReconciliationAnomaly,
    PaymentRefund,
    PaymentWebhookInbox,
    Ride,
)
from backend.integrations.payments.provider import get_payment_provider

logger = logging.getLogger("payments.service")

INBOX_TERMINAL_STATES = frozenset({"PROCESSED", "UNMATCHED_PAYMENT", "RECONCILIATION_REQUIRED"})
INBOX_STALE_PROCESSING_SECONDS = 60

SUPPORTED_FINANCIAL_WEBHOOK_EVENTS = frozenset({
    "payment.authorized",
    "payment.captured",
    "payment.failed",
    "order.paid",
    "refund.created",
    "refund.processed",
    "refund.failed",
})

SUPPORTED_DISPUTE_WEBHOOK_EVENTS = frozenset({
    "payment.dispute.created",
    "payment.dispute.won",
    "payment.dispute.lost",
    "payment.dispute.closed",
    "payment.dispute.under_review",
    "payment.dispute.action_required",
})

INFORMATIONAL_WEBHOOK_EVENTS = frozenset({
    "settlement.processed",
    "payment.downtime.started",
    "payment.downtime.resolved",
    "payment.downtime.updated",
    "fund_account.validation.completed",
    "fund_account.validation.failed",
    "refund.speed_changed",
})

NOTIFICATION_WEBHOOK_EVENTS = frozenset({
    "order.notification.delivered",
    "order.notification.failed",
})

SUPPORTED_WEBHOOK_EVENTS = (
    SUPPORTED_FINANCIAL_WEBHOOK_EVENTS
    | SUPPORTED_DISPUTE_WEBHOOK_EVENTS
    | INFORMATIONAL_WEBHOOK_EVENTS
    | NOTIFICATION_WEBHOOK_EVENTS
)

VALID_PAYMENT_TRANSITIONS: Dict[str, set[str]] = {
    "CREATED": {"AUTHORIZED", "CAPTURED", "FAILED", "EXPIRED"},
    "PENDING": {"AUTHORIZED", "CAPTURED", "FAILED", "EXPIRED"},
    "AUTHORIZED": {"CAPTURED", "FAILED", "EXPIRED"},
    "CAPTURED": {"REFUND_PENDING", "PARTIALLY_REFUNDED", "REFUNDED"},
    "REFUND_PENDING": {"PARTIALLY_REFUNDED", "REFUNDED", "CAPTURED"},
    "PARTIALLY_REFUNDED": {"REFUND_PENDING", "PARTIALLY_REFUNDED", "REFUNDED"},
    "PAID": {"REFUND_PENDING", "PARTIALLY_REFUNDED", "REFUNDED"},
    "SETTLED": {"REFUND_PENDING", "PARTIALLY_REFUNDED", "REFUNDED"},
    "SUCCESS": {"REFUND_PENDING", "PARTIALLY_REFUNDED", "REFUNDED"},
    "REFUNDED": set(),
    "FAILED": set(),
    "EXPIRED": set(),
}

VALID_REFUND_TRANSITIONS: Dict[str, set[str]] = {
    "INITIATED": {
        "PENDING",
        "GATEWAY_PROCESSING",
        "CREDITED",
        "PROCESSED",
        "FAILED",
        "ANOMALY_EXCESS",
        "PARTIALLY_APPLIED_EXCESS",
    },
    "PENDING_APPROVAL": {"APPROVED_AWAITING_PROVIDER", "REJECTED", "INITIATED"},
    "APPROVED_AWAITING_PROVIDER": {"INITIATED", "GATEWAY_PROCESSING", "CREDITED", "PROCESSED", "FAILED"},
    "PENDING": {"GATEWAY_PROCESSING", "CREDITED", "PROCESSED", "FAILED", "ANOMALY_EXCESS", "PARTIALLY_APPLIED_EXCESS"},
    "GATEWAY_PROCESSING": {"CREDITED", "PROCESSED", "FAILED", "ANOMALY_EXCESS", "PARTIALLY_APPLIED_EXCESS"},
    # Controlled recovery when gateway subsequently succeeds on the same refund ID
    "FAILED": {"GATEWAY_PROCESSING", "CREDITED", "PROCESSED", "ANOMALY_EXCESS", "PARTIALLY_APPLIED_EXCESS"},
    "CREDITED": set(),
    "PROCESSED": set(),
    "REJECTED": set(),
    "ANOMALY_EXCESS": set(),
    "PARTIALLY_APPLIED_EXCESS": set(),
}


def validate_payment_transition(current_status: str, target_status: str) -> bool:
    """Return True if transitioning Payment from current_status to target_status is allowed (or idempotent)."""
    if current_status == target_status:
        return True
    return target_status in VALID_PAYMENT_TRANSITIONS.get(current_status, set())


def validate_refund_transition(current_status: str, target_status: str) -> bool:
    """Return True if transitioning PaymentRefund from current_status to target_status is allowed (or idempotent)."""
    if current_status == target_status:
        return True
    return target_status in VALID_REFUND_TRANSITIONS.get(current_status, set())


def transition_payment_status(payment: Payment, target_status: str, *, context: str = "") -> str:
    """Validate and apply a central state transition on a Payment instance."""
    current = payment.status
    if current == target_status:
        return target_status
    if not validate_payment_transition(current, target_status):
        ctx = f" [{context}]" if context else ""
        raise ValueError(f"Invalid payment state transition: cannot transition from {current} to {target_status}{ctx}")
    payment.status = target_status
    return target_status


def transition_refund_status(refund: PaymentRefund, target_status: str, *, context: str = "") -> str:
    """Validate and apply a central state transition on a PaymentRefund instance."""
    current = refund.status
    if current == target_status:
        return target_status
    if not validate_refund_transition(current, target_status):
        ctx = f" [{context}]" if context else ""
        raise ValueError(f"Invalid refund state transition: cannot transition from {current} to {target_status}{ctx}")
    refund.status = target_status
    return target_status


def _is_int_paise(val: Any) -> bool:
    return isinstance(val, int) and not isinstance(val, bool)


class PaymentService:
    def __init__(self, db: AsyncSession):
        self.db = db
        self.provider = get_payment_provider()

    async def finalize_payment_capture(
        self,
        payment: Payment,
        provider_payment_id: str,
        provider_signature: Optional[str] = None,
        *,
        auto_commit: bool = True,
    ) -> Dict[str, Any]:
        """
        Authoritative single-source payment capture engine shared across verification & webhook paths.
        Applies strict idempotency, prevents gateway payment_id reuse across unrelated orders,
        transitions state via central guard, credits wallet/driver ledger, and persists atomically.
        """
        if not provider_payment_id or not str(provider_payment_id).strip():
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Authoritative provider_payment_id is required to finalize capture.",
            )
        provider_payment_id = str(provider_payment_id).strip()

        # Idempotency check across settled / partially-refunded states
        if payment.status in {"CAPTURED", "PARTIALLY_REFUNDED", "REFUNDED", "PAID", "SETTLED", "SUCCESS"}:
            if payment.provider_payment_id and payment.provider_payment_id != provider_payment_id:
                raise HTTPException(
                    status_code=status.HTTP_409_CONFLICT,
                    detail="Payment is already finalized with a different gateway payment ID.",
                )
            return {"status": "ALREADY_PROCESSED", "payment_id": payment.id}

        # Guard against gateway payment_id reuse across different internal payments
        reuse_stmt = select(Payment).where(
            Payment.provider_payment_id == provider_payment_id,
            Payment.id != payment.id,
        )
        reused = (await self.db.execute(reuse_stmt)).scalar_one_or_none()
        if reused:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail=f"Gateway payment {provider_payment_id} is already bound to another transaction.",
            )

        transition_payment_status(payment, "CAPTURED", context="finalize_payment_capture")
        payment.provider_payment_id = provider_payment_id
        if provider_signature:
            payment.provider_signature = provider_signature
        if not payment.merchant_account_id and (settings.RAZORPAY_MERCHANT_ACCOUNT_ID or "").strip():
            payment.merchant_account_id = settings.RAZORPAY_MERCHANT_ACCOUNT_ID.strip()

        if payment.provider_order_id:
            await self.db.execute(
                update(PaymentOrderAttempt)
                .where(PaymentOrderAttempt.provider_order_id == payment.provider_order_id)
                .values(status="CAPTURED")
            )

        # Domain Action 1: Wallet Top-Up
        if payment.purpose == "WALLET_TOPUP":
            target_user_id = payment.user_id
            if not target_user_id and payment.customer_id:
                prof = (
                    await self.db.execute(
                        select(CustomerProfile).where(CustomerProfile.id == payment.customer_id)
                    )
                ).scalar_one_or_none()
                if prof:
                    target_user_id = prof.user_id

            if not target_user_id:
                raise ValueError(f"Cannot credit wallet: Payment {payment.id} has no associated user_id.")

            from backend.application.services.wallet_service import WalletService

            await WalletService.credit_wallet(
                session=self.db,
                user_id=target_user_id,
                amount_cents=payment.amount_cents,
                transaction_type="CREDIT",
                source="TOP_UP",
                reference_id=provider_payment_id,
                description=f"Razorpay Wallet Top-up ({provider_payment_id})",
                idempotency_key=f"pay_topup_fin_{payment.provider_order_id or payment.id}",
            )

        # Domain Action 2: Ride Fare Payment
        elif payment.purpose == "RIDE_FARE" and payment.ride_id:
            r_res = await self.db.execute(
                select(Ride).where(Ride.id == payment.ride_id).with_for_update()
            )
            ride = r_res.scalar_one_or_none()
            if ride:
                if ride.status in ["IN_PROGRESS", "ACCEPTED", "ARRIVED", "STARTED"]:
                    ride.status = "COMPLETED"
                    ride.completed_at = ride.completed_at or datetime.now(timezone.utc)
                if ride.final_fare_cents is None:
                    ride.final_fare_cents = payment.amount_cents
                if not ride.assigned_driver_id and payment.driver_id:
                    ride.assigned_driver_id = payment.driver_id

                if ride.assigned_driver_id:
                    from backend.application.services.commission_service import CommissionService
                    fare_cents = payment.amount_cents
                    snap = await CommissionService.calculate_for_ride(
                        self.db,
                        gross_cents=fare_cents,
                        category_id=getattr(ride, "vehicle_category_id", None) or getattr(ride, "category_id", None),
                        effective_at=getattr(ride, "completed_at", None) or getattr(ride, "requested_at", None),
                        calculation_basis="RIDE_COMPLETION",
                    )
                    commission_cents = snap.commission_cents
                    await CommissionService.record_ride_snapshot(
                        self.db,
                        ride_id=ride.id,
                        snapshot=snap,
                        category_id=getattr(ride, "vehicle_category_id", None) or getattr(ride, "category_id", None),
                        commission_classification="PAYABLE",
                        currency=payment.currency or "INR",
                    )

                    d_w_res = await self.db.execute(
                        select(DriverWallet)
                        .where(DriverWallet.driver_id == ride.assigned_driver_id)
                        .with_for_update()
                    )
                    d_wallet = d_w_res.scalar_one_or_none()
                    if not d_wallet:
                        d_wallet = DriverWallet(
                            driver_id=ride.assigned_driver_id,
                            balance_cents=0,
                            currency="INR",
                        )
                        self.db.add(d_wallet)
                        await self.db.flush()

                    existing_earn = (
                        await self.db.execute(
                            select(DriverLedgerEntry).where(
                                DriverLedgerEntry.driver_id == ride.assigned_driver_id,
                                DriverLedgerEntry.reference_id == ride.id,
                                DriverLedgerEntry.category == "TRIP_EARNING",
                            )
                        )
                    ).scalar_one_or_none()

                    if not existing_earn:
                        now_ts = datetime.now(timezone.utc)
                        d_wallet.balance_cents += fare_cents
                        d_wallet.updated_at = now_ts
                        self.db.add(
                            DriverLedgerEntry(
                                id=str(uuid.uuid4()),
                                driver_id=ride.assigned_driver_id,
                                entry_type="CREDIT",
                                category="TRIP_EARNING",
                                amount_cents=fare_cents,
                                balance_after_cents=d_wallet.balance_cents,
                                reference_id=ride.id,
                                description=f"Gross earnings for online ride #{ride.id[:8]}",
                                created_at=now_ts,
                            )
                        )
                        existing_comm = (
                            await self.db.execute(
                                select(DriverLedgerEntry).where(
                                    DriverLedgerEntry.driver_id == ride.assigned_driver_id,
                                    DriverLedgerEntry.reference_id == ride.id,
                                    DriverLedgerEntry.category.in_(["COMMISSION_PAYABLE", "COMMISSION"]),
                                )
                            )
                        ).scalar_one_or_none()
                        if not existing_comm and commission_cents > 0:
                            self.db.add(
                                DriverLedgerEntry(
                                    id=str(uuid.uuid4()),
                                    driver_id=ride.assigned_driver_id,
                                    entry_type="DEBIT",
                                    category="COMMISSION_PAYABLE",
                                    amount_cents=-commission_cents,
                                    balance_after_cents=d_wallet.balance_cents,
                                    reference_id=ride.id,
                                    description=f"Platform commission ({snap.percentage}% v{snap.version}) for online ride #{ride.id[:8]}",
                                    created_at=now_ts,
                                )
                            )

        if auto_commit:
            try:
                await self.db.commit()
            except Exception as e:
                await self.db.rollback()
                logger.error("Database commit failed during finalize_payment_capture: %s", e)
                raise HTTPException(
                    status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                    detail="Database commit failure during payment capture persistence.",
                )
        else:
            await self.db.flush()

        return {
            "status": "CAPTURED",
            "payment_id": payment.id,
            "purpose": payment.purpose,
            "amount_cents": payment.amount_cents,
        }

    async def _reconcile_uncertain_attempts(
        self,
        *,
        payment: Payment,
        amount_cents: int,
        currency: str,
        fallback_receipt: str,
    ) -> Optional[PaymentOrderAttempt]:
        """Reconcile prior CREATING/UNCERTAIN attempts against Razorpay receipt API before creating a new order."""
        pay_id = payment.id
        currency_upper = (currency or "INR").upper()
        stmt = (
            select(PaymentOrderAttempt)
            .where(
                PaymentOrderAttempt.payment_id == pay_id,
                PaymentOrderAttempt.status.in_(("CREATING", "UNCERTAIN")),
            )
            .order_by(PaymentOrderAttempt.created_at.desc())
        )
        prior_attempts = (await self.db.execute(stmt)).scalars().all()
        checked_receipts: set[str] = set()

        for att in prior_attempts:
            rcpt = (att.receipt or fallback_receipt or "").strip()
            if att.amount_cents == amount_cents and rcpt and rcpt not in checked_receipts:
                checked_receipts.add(rcpt)
                try:
                    found = await self.provider.fetch_orders_by_receipt(rcpt)
                    items = found.get("items", []) if isinstance(found, dict) else (found or [])
                    matched = next(
                        (
                            o
                            for o in items
                            if o.get("id")
                            and o.get("amount") == amount_cents
                            and str(o.get("currency") or "INR").upper() == currency_upper
                            and str(o.get("status") or "").lower() != "attempted_failed"
                        ),
                        None,
                    )
                    if matched:
                        rec_order_id = matched["id"]
                        await self.db.execute(
                            update(PaymentOrderAttempt)
                            .where(
                                PaymentOrderAttempt.payment_id == pay_id,
                                PaymentOrderAttempt.status == "CREATED",
                                PaymentOrderAttempt.id != att.id,
                            )
                            .values(status="SUPERSEDED")
                        )
                        att.provider_order_id = rec_order_id
                        att.status = "CREATED"
                        att.lease_owner = None
                        att.failure_reason = None
                        payment.provider_order_id = rec_order_id
                        payment.amount_cents = amount_cents
                        payment.order_creation_lease_owner = None
                        payment.order_creation_lease_expires_at = None
                        self.db.add(
                            PaymentEvent(
                                payment_id=pay_id,
                                event_type="ORDER_RECOVERED_BY_RECEIPT",
                                payload_json=json.dumps({"order_id": rec_order_id, "receipt": rcpt}),
                            )
                        )
                        await self.db.commit()
                        await self.db.refresh(att)
                        return att
                except Exception as rec_err:
                    logger.warning("Uncertain order receipt check failed for %s: %s", rcpt, rec_err)

            if att.status == "CREATING":
                att.status = "FAILED"
                att.lease_owner = None
                att.failure_reason = att.failure_reason or "Stale CREATING attempt retired after lease timeout."

        if prior_attempts:
            await self.db.commit()
        return None

    async def execute_order_creation_with_lease(
        self,
        *,
        payment: Payment,
        amount_cents: int,
        currency: str,
        deterministic_receipt: str,
        idempotency_key: Optional[str],
        notes: Dict[str, Any],
        allow_supersede_on_amount_change: bool = False,
    ) -> Dict[str, Any]:
        """
        Multi-worker safe Razorpay order creation orchestrator using an atomic DB lease
        and PaymentOrderAttempt history (`CREATING -> CREATED | SUPERSEDED | UNCERTAIN | FAILED`).
        """
        clean_currency = (currency or "INR").upper()
        if amount_cents <= 0:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Order amount must be positive.")
        target_payment_id = payment.id

        def _reuse_payload(pay_obj: Payment, reused: bool = True, recovered: bool = False) -> Dict[str, Any]:
            return {
                "payment_id": pay_obj.id,
                "order_id": pay_obj.provider_order_id,
                "amount": pay_obj.amount_cents,
                "amount_cents": pay_obj.amount_cents,
                "currency": pay_obj.currency,
                "client_token": pay_obj.provider_order_id,
                "provider": "RAZORPAY",
                "key_id": settings.RAZORPAY_KEY_ID or "",
                "receipt": pay_obj.order_creation_receipt or deterministic_receipt,
                "status": "created",
                "reused_existing_order": reused,
                "recovered_order": recovered,
            }

        for _cycle in range(2):
            current = (await self.db.execute(select(Payment).where(Payment.id == target_payment_id))).scalar_one()
            if current.status in ("CAPTURED", "AUTHORIZED", "PARTIALLY_REFUNDED", "REFUNDED", "PAID", "SETTLED", "SUCCESS"):
                return {
                    "payment_id": target_payment_id,
                    "order_id": current.provider_order_id,
                    "status": current.status,
                    "amount": current.amount_cents,
                    "amount_cents": current.amount_cents,
                    "currency": current.currency,
                    "already_settled": True,
                }
            if current.status in ("FAILED", "EXPIRED"):
                raise HTTPException(
                    status_code=status.HTTP_409_CONFLICT,
                    detail="Cannot reuse failed or expired payment record; initialize a new payment intent.",
                )
            if current.currency.upper() != clean_currency:
                raise HTTPException(
                    status_code=status.HTTP_409_CONFLICT,
                    detail="Payment currency disagrees with requested currency.",
                )

            if current.provider_order_id and current.status in ("CREATED", "PENDING"):
                if current.amount_cents == amount_cents:
                    return _reuse_payload(current, reused=True)
                if not allow_supersede_on_amount_change:
                    raise HTTPException(
                        status_code=status.HTTP_409_CONFLICT,
                        detail="Idempotency conflict: order already exists for a different amount.",
                    )
                if (settings.RAZORPAY_KEY_ID and settings.RAZORPAY_KEY_SECRET) and not current.provider_order_id.startswith("order_test_"):
                    try:
                        existing_gw = await self.provider.fetch_order_payments(current.provider_order_id)
                        gw_items = existing_gw.get("items", []) if isinstance(existing_gw, dict) else (existing_gw or [])
                        captured = next(
                            (p for p in gw_items if str(p.get("status") or "").lower() == "captured"),
                            None,
                        )
                        if captured:
                            locked_cap = (
                                await self.db.execute(select(Payment).where(Payment.id == target_payment_id).with_for_update())
                            ).scalar_one()
                            captured_order_id = locked_cap.provider_order_id
                            cap_res = await self.finalize_payment_capture(payment=locked_cap, provider_payment_id=captured["id"])
                            return {
                                "payment_id": target_payment_id,
                                "order_id": captured_order_id,
                                "status": cap_res.get("status", "CAPTURED"),
                                "amount": cap_res.get("amount_cents", amount_cents),
                                "amount_cents": cap_res.get("amount_cents", amount_cents),
                                "currency": clean_currency,
                                "already_settled": True,
                            }
                    except HTTPException:
                        pass
                    except Exception as exc:
                        logger.warning("Order pre-supersede check failed for %s: %s", current.provider_order_id, exc)

            # Atomic DB lease acquisition across workers & Render instances
            now = datetime.now(timezone.utc)
            lease_owner = f"lease_{uuid.uuid4().hex[:24]}"
            lease_expires = now + timedelta(seconds=30)

            lease_stmt = (
                update(Payment)
                .where(
                    Payment.id == target_payment_id,
                    Payment.status.in_(("CREATED", "PENDING")),
                    or_(
                        Payment.order_creation_lease_owner.is_(None),
                        Payment.order_creation_lease_expires_at < now,
                    ),
                )
                .values(
                    order_creation_lease_owner=lease_owner,
                    order_creation_lease_expires_at=lease_expires,
                    order_creation_receipt=deterministic_receipt,
                )
            )
            if not allow_supersede_on_amount_change:
                lease_stmt = lease_stmt.where(Payment.provider_order_id.is_(None))

            lease_res = await self.db.execute(lease_stmt)
            if lease_res.rowcount == 1:
                await self.db.commit()
                try:
                    locked_pay = (await self.db.execute(select(Payment).where(Payment.id == target_payment_id))).scalar_one()
                    recovered_att = await self._reconcile_uncertain_attempts(
                        payment=locked_pay,
                        amount_cents=amount_cents,
                        currency=clean_currency,
                        fallback_receipt=deterministic_receipt,
                    )
                    if recovered_att and recovered_att.provider_order_id:
                        refreshed = (await self.db.execute(select(Payment).where(Payment.id == target_payment_id))).scalar_one()
                        return _reuse_payload(refreshed, reused=True, recovered=True)

                    attempt_id = str(uuid.uuid4())
                    attempt = PaymentOrderAttempt(
                        id=attempt_id,
                        payment_id=target_payment_id,
                        provider_order_id=None,
                        receipt=deterministic_receipt,
                        idempotency_key=idempotency_key,
                        amount_cents=amount_cents,
                        currency=clean_currency,
                        merchant_account_id=(settings.RAZORPAY_MERCHANT_ACCOUNT_ID or "").strip() or locked_pay.merchant_account_id,
                        lease_owner=lease_owner,
                        status="CREATING",
                    )
                    self.db.add(attempt)
                    try:
                        await self.db.commit()
                    except IntegrityError:
                        # Defense-in-depth partial unique index uq_payment_order_attempts_one_creating blocked a race
                        await self.db.rollback()
                        await self.db.execute(
                            update(Payment)
                            .where(Payment.id == target_payment_id, Payment.order_creation_lease_owner == lease_owner)
                            .values(order_creation_lease_owner=None, order_creation_lease_expires_at=None)
                        )
                        await self.db.commit()
                        continue

                    try:
                        gw_order = await self.provider.create_razorpay_order(
                            amount_cents=amount_cents,
                            currency=clean_currency,
                            receipt=deterministic_receipt,
                            notes=notes,
                        )
                    except HTTPException as gw_http:
                        is_client_rej = 400 <= gw_http.status_code < 500
                        locked_att = (
                            await self.db.execute(select(PaymentOrderAttempt).where(PaymentOrderAttempt.id == attempt_id).with_for_update())
                        ).scalar_one_or_none()
                        if locked_att:
                            locked_att.status = "FAILED" if is_client_rej else "UNCERTAIN"
                            locked_att.failure_reason = str(gw_http.detail)[:500]
                            locked_att.lease_owner = None
                        await self.db.execute(
                            update(Payment)
                            .where(Payment.id == target_payment_id, Payment.order_creation_lease_owner == lease_owner)
                            .values(order_creation_lease_owner=None, order_creation_lease_expires_at=None)
                        )
                        self.db.add(
                            PaymentEvent(
                                payment_id=target_payment_id,
                                event_type="ORDER_CREATE_FAILED" if is_client_rej else "ORDER_CREATE_UNCERTAIN",
                                payload_json=json.dumps({"receipt": deterministic_receipt, "detail": str(gw_http.detail)[:250]}),
                            )
                        )
                        await self.db.commit()
                        raise
                    except Exception as gw_err:
                        locked_att = (
                            await self.db.execute(select(PaymentOrderAttempt).where(PaymentOrderAttempt.id == attempt_id).with_for_update())
                        ).scalar_one_or_none()
                        if locked_att:
                            locked_att.status = "UNCERTAIN"
                            locked_att.failure_reason = str(gw_err)[:500]
                            locked_att.lease_owner = None
                        await self.db.execute(
                            update(Payment)
                            .where(Payment.id == target_payment_id, Payment.order_creation_lease_owner == lease_owner)
                            .values(order_creation_lease_owner=None, order_creation_lease_expires_at=None)
                        )
                        self.db.add(
                            PaymentEvent(
                                payment_id=target_payment_id,
                                event_type="ORDER_CREATE_UNCERTAIN",
                                payload_json=json.dumps({"receipt": deterministic_receipt, "error": str(gw_err)[:250]}),
                            )
                        )
                        await self.db.commit()
                        raise HTTPException(
                            status_code=status.HTTP_504_GATEWAY_TIMEOUT,
                            detail="Gateway order creation timed out; retry safely to reconcile before recreating.",
                        )

                    new_order_id = gw_order.get("id") if isinstance(gw_order, dict) else None
                    if not new_order_id:
                        locked_att = (
                            await self.db.execute(select(PaymentOrderAttempt).where(PaymentOrderAttempt.id == attempt_id).with_for_update())
                        ).scalar_one_or_none()
                        if locked_att:
                            locked_att.status = "UNCERTAIN"
                            locked_att.failure_reason = "Gateway returned missing order id."
                            locked_att.lease_owner = None
                        await self.db.execute(
                            update(Payment)
                            .where(Payment.id == target_payment_id, Payment.order_creation_lease_owner == lease_owner)
                            .values(order_creation_lease_owner=None, order_creation_lease_expires_at=None)
                        )
                        await self.db.commit()
                        raise HTTPException(status_code=502, detail="Gateway returned invalid Razorpay order identifier.")

                    # Success: finalize Payment + preserve prior attempts as SUPERSEDED
                    locked_pay = (
                        await self.db.execute(select(Payment).where(Payment.id == target_payment_id).with_for_update())
                    ).scalar_one()
                    locked_att = (
                        await self.db.execute(select(PaymentOrderAttempt).where(PaymentOrderAttempt.id == attempt_id).with_for_update())
                    ).scalar_one()
                    previous_order_id = locked_pay.provider_order_id

                    await self.db.execute(
                        update(PaymentOrderAttempt)
                        .where(
                            PaymentOrderAttempt.payment_id == target_payment_id,
                            PaymentOrderAttempt.status == "CREATED",
                            PaymentOrderAttempt.id != attempt_id,
                        )
                        .values(status="SUPERSEDED")
                    )

                    locked_att.provider_order_id = new_order_id
                    locked_att.status = "CREATED"
                    locked_att.lease_owner = None
                    locked_att.failure_reason = None

                    locked_pay.provider_order_id = new_order_id
                    locked_pay.amount_cents = amount_cents
                    locked_pay.merchant_account_id = (
                        (settings.RAZORPAY_MERCHANT_ACCOUNT_ID or "").strip() or locked_pay.merchant_account_id
                    )
                    locked_pay.order_creation_lease_owner = None
                    locked_pay.order_creation_lease_expires_at = None

                    self.db.add(
                        PaymentEvent(
                            payment_id=target_payment_id,
                            event_type="ORDER_CREATED",
                            payload_json=json.dumps(
                                {
                                    "order_id": new_order_id,
                                    "previous_order_id": previous_order_id,
                                    "amount_cents": amount_cents,
                                    "receipt": deterministic_receipt,
                                }
                            ),
                        )
                    )
                    result_payload = _reuse_payload(locked_pay, reused=False)
                    await self.db.commit()
                    return result_payload
                except HTTPException:
                    raise
                except Exception:
                    await self.db.rollback()
                    await self.db.execute(
                        update(Payment)
                        .where(Payment.id == target_payment_id, Payment.order_creation_lease_owner == lease_owner)
                        .values(order_creation_lease_owner=None, order_creation_lease_expires_at=None)
                    )
                    await self.db.commit()
                    raise

            # Lease held by peer worker -> wait in non-locking read snapshots for peer completion
            await self.db.rollback()
            peer_cleared_without_order = False
            for _poll in range(40):
                await asyncio.sleep(0.1)
                self.db.expire_all()
                polled = (await self.db.execute(select(Payment).where(Payment.id == target_payment_id))).scalar_one_or_none()
                if not polled:
                    break
                if polled.status in ("CAPTURED", "AUTHORIZED", "PARTIALLY_REFUNDED", "REFUNDED", "PAID", "SETTLED", "SUCCESS"):
                    return {
                        "payment_id": polled.id,
                        "order_id": polled.provider_order_id,
                        "status": polled.status,
                        "amount": polled.amount_cents,
                        "amount_cents": polled.amount_cents,
                        "currency": polled.currency,
                        "already_settled": True,
                    }
                if polled.provider_order_id and polled.amount_cents == amount_cents and polled.status in ("CREATED", "PENDING"):
                    return _reuse_payload(polled, reused=True)
                now_poll = datetime.now(timezone.utc)
                lease_expires_at = polled.order_creation_lease_expires_at
                if lease_expires_at and lease_expires_at.tzinfo is None:
                    lease_expires_at = lease_expires_at.replace(tzinfo=timezone.utc)
                lease_inactive = (
                    not polled.order_creation_lease_owner
                    or (lease_expires_at and lease_expires_at < now_poll)
                )
                if lease_inactive and polled.provider_order_id is None:
                    peer_cleared_without_order = True
                    break

            if peer_cleared_without_order:
                continue

        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Concurrent Razorpay order creation already in progress for this payment; please retry.",
        )

    async def create_payment_intent(
        self,
        user_id: str,
        ride_id: str,
        idempotency_key: str,
        *,
        force_new_order_if_amount_changed: bool = True,
    ) -> Dict[str, Any]:
        """
        Creates or idempotently reuses an authoritative Razorpay payment intent for a ride
        using DB-backed leases so concurrent calls across workers never create duplicate orders.
        """
        prof_res = await self.db.execute(select(CustomerProfile).where(CustomerProfile.user_id == user_id))
        prof = prof_res.scalar_one_or_none()
        if not prof:
            prof = CustomerProfile(user_id=user_id, preferred_payment_method="RAZORPAY", rating_average=5.0, rating_count=0)
            self.db.add(prof)
            await self.db.flush()
        customer_profile_id = prof.id

        ride_res = await self.db.execute(select(Ride).where(Ride.id == ride_id))
        ride = ride_res.scalar_one_or_none()
        if not ride:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Ride not found.")
        if ride.customer_id != customer_profile_id:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="You do not own this ride.")

        fare = int(ride.final_fare_cents or ride.estimated_fare_cents or 0)
        if fare <= 0:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Ride fare has not been calculated.")

        # Validate explicit idempotency key does not collide with another ride/user/purpose
        idem_pay = (
            await self.db.execute(select(Payment).where(Payment.idempotency_key == idempotency_key))
        ).scalar_one_or_none()
        if idem_pay:
            if idem_pay.user_id and idem_pay.user_id != user_id:
                raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Idempotency key belongs to a different user.")
            if idem_pay.ride_id != ride_id or getattr(idem_pay, "purpose", "RIDE_FARE") != "RIDE_FARE":
                raise HTTPException(
                    status_code=status.HTTP_409_CONFLICT,
                    detail="Idempotency key is already bound to a different ride or payment purpose.",
                )

        stmt = select(Payment).where(Payment.ride_id == ride_id)
        payment = (await self.db.execute(stmt)).scalar_one_or_none()
        if not payment:
            scoped_idem = idempotency_key[:128] if idempotency_key else f"ride_{ride_id}"[:128]
            new_pay = Payment(
                ride_id=ride_id,
                user_id=user_id,
                customer_id=customer_profile_id,
                amount_cents=fare,
                currency="INR",
                purpose="RIDE_FARE",
                status="CREATED",
                payment_method="RAZORPAY",
                provider_name="RAZORPAY",
                merchant_account_id=(settings.RAZORPAY_MERCHANT_ACCOUNT_ID or "").strip() or None,
                idempotency_key=scoped_idem,
            )
            self.db.add(new_pay)
            try:
                await self.db.commit()
                await self.db.refresh(new_pay)
                payment = new_pay
            except IntegrityError:
                await self.db.rollback()
                payment = (await self.db.execute(stmt)).scalar_one()

        deterministic_receipt = f"ride_{payment.id.replace('-', '')[:24]}_{fare}"[:40]
        return await self.execute_order_creation_with_lease(
            payment=payment,
            amount_cents=fare,
            currency="INR",
            deterministic_receipt=deterministic_receipt,
            idempotency_key=idempotency_key,
            notes={
                "internal_payment_id": payment.id,
                "ride_id": ride_id,
                "user_id": user_id,
                "customer_id": customer_profile_id,
                "purpose": "RIDE_FARE",
            },
            allow_supersede_on_amount_change=force_new_order_if_amount_changed,
        )

    async def _resolve_customer_user_id(self, payment: Payment) -> Optional[str]:
        """Resolve the customer's User.id from Payment.user_id or CustomerProfile.user_id."""
        if getattr(payment, "user_id", None):
            return payment.user_id
        if getattr(payment, "customer_id", None):
            cust_prof = (
                await self.db.execute(
                    select(CustomerProfile).where(CustomerProfile.id == payment.customer_id)
                )
            ).scalar_one_or_none()
            if cust_prof:
                return cust_prof.user_id
        return None

    async def apply_confirmed_refund_settlement(
        self,
        *,
        payment: Payment,
        refund_obj: Optional[PaymentRefund],
        refund_id: str,
        raw_refund_amount: int,
        reason: str,
        arn_reference: Optional[str] = None,
        settled_status: str = "CREDITED",
        inbox: Optional[PaymentWebhookInbox] = None,
        parsed: Optional[Dict[str, Any]] = None,
        now: Optional[datetime] = None,
    ) -> Dict[str, Any]:
        """
        Single authoritative settlement handler for confirmed gateway refunds.
        Called by synchronous refund API (when status=='processed'), webhook refund.processed,
        and background reconciliation once Razorpay confirms settlement.
        Enforces strict paise ceiling, prevents duplicate settlement/wallet debit,
        and handles WALLET_TOPUP clawback / RefundLiability creation atomically.
        """
        now = now or datetime.now(timezone.utc)
        terminal_settled_states = {"CREDITED", "PROCESSED", "ANOMALY_EXCESS", "PARTIALLY_APPLIED_EXCESS"}
        if refund_obj and refund_obj.status in terminal_settled_states:
            return {
                "already_processed": True,
                "applied_refund": int(refund_obj.amount_cents or 0),
                "raw_refund_amount": int(refund_obj.raw_gateway_amount_cents or refund_obj.amount_cents or 0),
                "excess_anomaly_cents": int(refund_obj.excess_amount_cents or 0),
                "is_anomaly": refund_obj.status in {"ANOMALY_EXCESS", "PARTIALLY_APPLIED_EXCESS"},
                "anomaly_reason": refund_obj.reason or reason,
                "refund_status": refund_obj.status,
                "refund_obj": refund_obj,
            }

        current_refunded = int(payment.refunded_amount_cents or 0)
        remaining_refundable = max(0, int(payment.amount_cents) - current_refunded)
        if raw_refund_amount > remaining_refundable:
            applied_refund = remaining_refundable
            excess_anomaly_cents = raw_refund_amount - remaining_refundable
            is_anomaly = True
            logger.critical(
                "REFUND CEILING BREACH: Payment %s captured=%d, refunded=%d, remaining=%d, raw_refund=%d, excess=%d",
                payment.id,
                payment.amount_cents,
                current_refunded,
                remaining_refundable,
                raw_refund_amount,
                excess_anomaly_cents,
            )
        else:
            applied_refund = raw_refund_amount
            excess_anomaly_cents = 0
            is_anomaly = False

        if applied_refund > 0:
            payment.refunded_amount_cents = current_refunded + applied_refund
            target_pay_status = (
                "REFUNDED" if payment.refunded_amount_cents >= payment.amount_cents else "PARTIALLY_REFUNDED"
            )
            transition_payment_status(payment, target_pay_status, context=f"refund settlement ({settled_status})")
        elif payment.status == "REFUND_PENDING":
            other_inflight = (
                await self.db.execute(
                    select(PaymentRefund.id).where(
                        PaymentRefund.payment_id == payment.id,
                        PaymentRefund.id != (refund_obj.id if refund_obj else ""),
                        PaymentRefund.status.in_(("INITIATED", "PENDING", "GATEWAY_PROCESSING", "APPROVED_AWAITING_PROVIDER")),
                    )
                )
            ).scalar_one_or_none()
            if not other_inflight:
                fallback_status = "PARTIALLY_REFUNDED" if current_refunded > 0 else "CAPTURED"
                transition_payment_status(payment, fallback_status, context="refund settlement zero applied")

        refund_status = (
            "PARTIALLY_APPLIED_EXCESS"
            if is_anomaly and applied_refund > 0
            else ("ANOMALY_EXCESS" if applied_refund == 0 else settled_status)
        )
        anomaly_reason = (
            f"GATEWAY_OVER_REFUND_ANOMALY: Gateway refund {refund_id} of {raw_refund_amount} paise "
            f"exceeds remaining refundable ceiling of {remaining_refundable} paise "
            f"(applied={applied_refund}, excess={excess_anomaly_cents})."
            if is_anomaly
            else reason
        )

        effective_arn = arn_reference or (refund_obj.arn_reference if refund_obj else None) or refund_id
        is_partial_flag = bool(applied_refund > 0 and payment.status == "PARTIALLY_REFUNDED")
        if refund_obj:
            transition_refund_status(refund_obj, refund_status, context=f"refund settlement ({settled_status})")
            refund_obj.amount_cents = applied_refund
            refund_obj.raw_gateway_amount_cents = raw_refund_amount
            refund_obj.excess_amount_cents = excess_anomaly_cents
            refund_obj.provider_refund_id = refund_id
            refund_obj.arn_reference = effective_arn
            refund_obj.is_partial = is_partial_flag
            refund_obj.settled_at = now
            refund_obj.last_error = anomaly_reason[:500] if is_anomaly else None
            if is_anomaly or not refund_obj.reason:
                refund_obj.reason = anomaly_reason[:255]
        else:
            refund_obj = PaymentRefund(
                payment_id=payment.id,
                ride_id=payment.ride_id,
                customer_id=payment.customer_id,
                amount_cents=applied_refund,
                raw_gateway_amount_cents=raw_refund_amount,
                excess_amount_cents=excess_anomaly_cents,
                is_partial=is_partial_flag,
                reason=anomaly_reason[:255],
                status=refund_status,
                provider_refund_id=refund_id,
                arn_reference=effective_arn,
                settled_at=now,
            )
            self.db.add(refund_obj)

        if is_anomaly and excess_anomaly_cents > 0:
            await self._record_reconciliation_anomaly(
                anomaly_type="EXCESS_REFUND",
                payment=payment,
                inbox=inbox,
                parsed=parsed or {"provider_payment_id": payment.provider_payment_id, "order_id": payment.provider_order_id, "refund_id": refund_id, "currency": payment.currency or "INR"},
                expected_amount_cents=remaining_refundable,
                raw_gateway_amount_cents=raw_refund_amount,
                applied_amount_cents=applied_refund,
                excess_amount_cents=excess_anomaly_cents,
                reason=anomaly_reason,
            )

        # Clawback ONLY on confirmed gateway settlement and ONLY on internally applied refund
        if getattr(payment, "purpose", None) == "WALLET_TOPUP" and applied_refund > 0:
            topup_user_id = await self._resolve_customer_user_id(payment)
            if topup_user_id:
                from backend.application.services.wallet_service import WalletService

                await WalletService.process_refund_clawback(
                    session=self.db,
                    user_id=topup_user_id,
                    refund_amount_cents=applied_refund,
                    payment_id=payment.id,
                    gateway_refund_id=refund_id,
                    idempotency_key=f"refund_debit_{refund_id}",
                )

        self.db.add(
            PaymentEvent(
                payment_id=payment.id,
                event_type="WEBHOOK_REFUND_PROCESSED" if inbox else "REFUND_PROCESSED",
                payload_json=json.dumps(
                    {
                        "refund_id": refund_id,
                        "raw_gateway_amount_cents": raw_refund_amount,
                        "applied_amount_cents": applied_refund,
                        "excess_amount_cents": excess_anomaly_cents,
                        "status": refund_status,
                    }
                ),
            )
        )

        return {
            "already_processed": False,
            "applied_refund": applied_refund,
            "raw_refund_amount": raw_refund_amount,
            "excess_anomaly_cents": excess_anomaly_cents,
            "is_anomaly": is_anomaly,
            "anomaly_reason": anomaly_reason,
            "refund_status": refund_status,
            "refund_obj": refund_obj,
        }

    async def apply_failed_refund_reversal(
        self,
        *,
        payment: Payment,
        refund_obj: Optional[PaymentRefund],
        refund_id: str,
        raw_refund_amount: int,
        failure_reason: str,
        now: Optional[datetime] = None,
    ) -> Dict[str, Any]:
        """
        Handles failed gateway refund transitions cleanly: marks refund FAILED, reverses any
        clawback/liability if one existed, and reverts payment.status out of REFUND_PENDING
        when no other in-flight refunds remain.
        """
        now = now or datetime.now(timezone.utc)
        terminal_settled_states = {"CREDITED", "PROCESSED", "ANOMALY_EXCESS", "PARTIALLY_APPLIED_EXCESS"}
        if refund_obj and refund_obj.status in terminal_settled_states:
            return {
                "rejected_transition": True,
                "reason": f"Cannot transition {refund_obj.status} refund to FAILED.",
            }

        if refund_obj:
            transition_refund_status(refund_obj, "FAILED", context="apply_failed_refund_reversal")
            refund_obj.provider_refund_id = refund_id
            refund_obj.arn_reference = refund_obj.arn_reference or refund_id
            refund_obj.raw_gateway_amount_cents = raw_refund_amount
            refund_obj.last_error = failure_reason[:500]
        else:
            refund_obj = PaymentRefund(
                payment_id=payment.id,
                ride_id=payment.ride_id,
                customer_id=payment.customer_id,
                amount_cents=raw_refund_amount,
                raw_gateway_amount_cents=raw_refund_amount,
                excess_amount_cents=0,
                is_partial=False,
                reason=failure_reason[:255],
                status="FAILED",
                provider_refund_id=refund_id,
                arn_reference=refund_id,
                last_error=failure_reason[:500],
            )
            self.db.add(refund_obj)
            await self.db.flush()

        # Reverse any clawback / liability if a legacy path or race ever created one
        reversal_summary: Dict[str, Any] = {"status": "NO_CLAWBACK_FOUND"}
        if getattr(payment, "purpose", None) == "WALLET_TOPUP":
            topup_user_id = await self._resolve_customer_user_id(payment)
            if topup_user_id:
                from backend.application.services.wallet_service import WalletService

                reversal_summary = await WalletService.reverse_refund_clawback(
                    session=self.db,
                    user_id=topup_user_id,
                    gateway_refund_id=refund_id,
                    payment_id=payment.id,
                    reason=failure_reason,
                )

        # Revert payment.status out of REFUND_PENDING if no other in-flight refunds remain
        if payment.status == "REFUND_PENDING":
            other_inflight = (
                await self.db.execute(
                    select(PaymentRefund.id).where(
                        PaymentRefund.payment_id == payment.id,
                        PaymentRefund.id != refund_obj.id,
                        PaymentRefund.status.in_(("INITIATED", "PENDING", "GATEWAY_PROCESSING", "APPROVED_AWAITING_PROVIDER")),
                    )
                )
            ).scalar_one_or_none()
            if not other_inflight:
                reverted_status = "PARTIALLY_REFUNDED" if int(payment.refunded_amount_cents or 0) > 0 else "CAPTURED"
                transition_payment_status(payment, reverted_status, context="apply_failed_refund_reversal")

        self.db.add(
            PaymentEvent(
                payment_id=payment.id,
                event_type="REFUND_FAILED",
                payload_json=json.dumps(
                    {
                        "refund_id": refund_id,
                        "raw_gateway_amount_cents": raw_refund_amount,
                        "reason": failure_reason[:255],
                        "clawback_reversal": reversal_summary.get("status"),
                    }
                ),
            )
        )
        return {
            "rejected_transition": False,
            "refund_obj": refund_obj,
            "clawback_reversal": reversal_summary,
        }

    async def _handle_dispute_webhook(
        self,
        *,
        webhook_event: str,
        inbox: PaymentWebhookInbox,
        payload_obj: Dict[str, Any],
        now: datetime,
        secret_slot: str,
    ) -> Dict[str, Any]:
        """Handle Razorpay chargeback/dispute webhooks cleanly with audit & reconciliation tracking."""
        dispute_wrap = payload_obj.get("dispute")
        dispute_entity = dispute_wrap.get("entity") if isinstance(dispute_wrap, dict) else {}
        if not isinstance(dispute_entity, dict):
            dispute_entity = {}
        pay_wrap = payload_obj.get("payment")
        payment_entity = pay_wrap.get("entity") if isinstance(pay_wrap, dict) else {}
        if not isinstance(payment_entity, dict):
            payment_entity = {}

        dispute_id = dispute_entity.get("id")
        provider_payment_id = dispute_entity.get("payment_id") or payment_entity.get("id")
        raw_amount = dispute_entity.get("amount") or payment_entity.get("amount")

        payment: Optional[Payment] = None
        if provider_payment_id:
            payment = (
                await self.db.execute(
                    select(Payment).where(Payment.provider_payment_id == str(provider_payment_id).strip()).with_for_update()
                )
            ).scalar_one_or_none()

        is_resolved = webhook_event in {"payment.dispute.won", "payment.dispute.closed"}
        reason = (
            f"Razorpay dispute event {webhook_event}"
            f" (dispute_id={dispute_id or 'unknown'}, phase={dispute_entity.get('phase') or 'n/a'},"
            f" status={dispute_entity.get('status') or 'n/a'})"
        )

        # Update prior open dispute anomalies if won/closed, else record an OPEN dispute anomaly
        if is_resolved and provider_payment_id:
            await self.db.execute(
                update(PaymentReconciliationAnomaly)
                .where(
                    PaymentReconciliationAnomaly.provider_payment_id == str(provider_payment_id).strip(),
                    PaymentReconciliationAnomaly.anomaly_type == "PAYMENT_DISPUTE",
                    PaymentReconciliationAnomaly.status == "OPEN",
                )
                .values(status="RESOLVED", updated_at=now)
            )
        else:
            self.db.add(
                PaymentReconciliationAnomaly(
                    payment_id=payment.id if payment else None,
                    webhook_inbox_id=inbox.id,
                    provider_name="RAZORPAY",
                    provider_payment_id=str(provider_payment_id).strip() if provider_payment_id else None,
                    provider_order_id=payment.provider_order_id if payment else payment_entity.get("order_id"),
                    provider_refund_id=str(dispute_id).strip() if dispute_id else None,
                    anomaly_type="PAYMENT_DISPUTE",
                    expected_amount_cents=payment.amount_cents if payment else None,
                    raw_gateway_amount_cents=raw_amount if _is_int_paise(raw_amount) else None,
                    applied_amount_cents=0,
                    excess_amount_cents=0,
                    currency=str(dispute_entity.get("currency") or "INR").upper(),
                    status="OPEN",
                    reason=reason[:500],
                    details_json=json.dumps(
                        {
                            "digest": inbox.event_digest,
                            "event": webhook_event,
                            "dispute_id": dispute_id,
                            "reason_code": dispute_entity.get("reason_code"),
                        }
                    ),
                )
            )

        if payment:
            self.db.add(
                PaymentEvent(
                    payment_id=payment.id,
                    event_type=webhook_event.upper().replace(".", "_"),
                    payload_json=json.dumps(
                        {
                            "inbox_id": inbox.id,
                            "dispute_id": dispute_id,
                            "amount": raw_amount,
                            "status": dispute_entity.get("status"),
                        }
                    ),
                )
            )

        inbox.status = "PROCESSED" if is_resolved else "RECONCILIATION_REQUIRED"
        inbox.last_error = None if is_resolved else reason[:500]
        inbox.processed_at = now
        await self._commit_or_500(f"dispute webhook {webhook_event}")
        return {
            "status": "DISPUTE_RESOLVED" if is_resolved else "DISPUTE_RECORDED",
            "event": webhook_event,
            "payment_id": payment.id if payment else None,
            "dispute_id": dispute_id,
            "secret_slot": secret_slot,
            "message": reason,
        }

    # ------------------------------------------------------------------
    # Authoritative Razorpay Webhook Processor (Single Consolidated Path)
    # ------------------------------------------------------------------
    async def process_webhook(
        self,
        raw_body: bytes,
        signature_header: str,
        account_header: Optional[str] = None,
    ) -> Dict[str, Any]:
        """
        Cryptographically verifies and processes inbound Razorpay webhooks atomically.
        Steps:
        1. Raw-bytes HMAC-SHA256 verification using RAZORPAY_WEBHOOK_SECRET.
        2. Strict JSON and event envelope validation (no unsafe event defaults).
        3. Concurrency-safe durable inbox initialization (RECEIVED -> PROCESSING).
        4. Event-specific payload validation & locked payment/order resolution.
        5. Central state transition / capture / refund + reconciliation anomaly persistence.
        6. Single atomic DB commit with rollback and HTTP 500 propagation on failure.
        """
        now = datetime.now(timezone.utc)
        digest = hashlib.sha256(raw_body).hexdigest()

        # 1. Verify raw-byte webhook signature before parsing or mutating state
        verification, secret_slot = await self.provider.verify_webhook_payload_detailed(raw_body, signature_header)
        if not verification.is_valid:
            await self._record_invalid_signature_attempt(digest=digest, raw_body=raw_body, now=now)
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Invalid webhook signature.",
            )

        # 2. Strict JSON parsing & event validation (no unsafe default event)
        try:
            data = json.loads(raw_body.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Verified webhook body is not valid JSON.",
            )

        if not isinstance(data, dict):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Malformed Razorpay webhook payload: expected JSON object.",
            )

        raw_event = data.get("event")
        if not isinstance(raw_event, str) or not raw_event.strip():
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Malformed Razorpay webhook payload: missing event type.",
            )
        webhook_event = raw_event.strip()
        provider_event_id = data.get("id") if isinstance(data.get("id"), str) else None

        header_acct = account_header.strip() if isinstance(account_header, str) and account_header.strip() else None
        raw_payload_acct = data.get("account_id")
        if raw_payload_acct is not None and not isinstance(raw_payload_acct, str):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Malformed Razorpay webhook payload: account_id must be a string.",
            )
        payload_acct = raw_payload_acct.strip() if isinstance(raw_payload_acct, str) and raw_payload_acct.strip() else None

        # 3. Acquire / initialize locked inbox row with concurrent race handling
        inbox, early_dup = await self._acquire_or_create_inbox(
            digest=digest,
            raw_body=raw_body,
            webhook_event=webhook_event,
            provider_event_id=provider_event_id,
            now=now,
        )
        if early_dup is not None:
            return early_dup

        effective_account = header_acct or payload_acct
        inbox.merchant_account_id = effective_account

        # Enforce strict single-merchant account isolation
        if header_acct and payload_acct and header_acct != payload_acct:
            reason = f"Merchant account header mismatch: header '{header_acct}' disagrees with payload '{payload_acct}'."
            await self._record_anomaly_and_require_reconciliation(
                inbox=inbox,
                anomaly_type="MERCHANT_ACCOUNT_MISMATCH",
                payment=None,
                parsed={"provider_payment_id": None, "order_id": None, "refund_id": None, "currency": "INR"},
                reason=reason,
                now=now,
            )
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=reason)

        acct_ok, acct_reason = self.provider.validate_merchant_account(effective_account)
        if not acct_ok:
            await self._record_anomaly_and_require_reconciliation(
                inbox=inbox,
                anomaly_type="MERCHANT_ACCOUNT_MISMATCH",
                payment=None,
                parsed={"provider_payment_id": None, "order_id": None, "refund_id": None, "currency": "INR"},
                reason=acct_reason,
                now=now,
            )
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=acct_reason)

        # Unsupported unknown events are persisted as FAILED and rejected with HTTP 400
        if webhook_event not in SUPPORTED_WEBHOOK_EVENTS:
            await self._fail_inbox(inbox, f"Unsupported webhook event type: {webhook_event}", now)
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Unsupported webhook event type: {webhook_event}.",
            )

        payload_obj = data.get("payload")
        if not isinstance(payload_obj, dict):
            await self._fail_inbox(inbox, "Malformed payload: missing payload dictionary.", now)
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Malformed Razorpay webhook payload: missing payload object.",
            )

        # Supported Dispute Events: persist audit anomaly + PaymentEvent without HTTP retry loops
        if webhook_event in SUPPORTED_DISPUTE_WEBHOOK_EVENTS:
            return await self._handle_dispute_webhook(
                webhook_event=webhook_event,
                inbox=inbox,
                payload_obj=payload_obj,
                now=now,
                secret_slot=secret_slot,
            )

        # Supported Informational Events (downtime/settlement/fund_account): acknowledge idempotently
        if webhook_event in INFORMATIONAL_WEBHOOK_EVENTS:
            inbox.status = "PROCESSED"
            inbox.processed_at = now
            inbox.last_error = None
            await self._commit_or_500(f"informational webhook {webhook_event}")
            return {
                "status": "ACKNOWLEDGED_INFORMATIONAL",
                "event": webhook_event,
                "secret_slot": secret_slot,
                "merchant_account_id": effective_account,
                "message": f"Informational Razorpay event {webhook_event} recorded cleanly.",
            }

        # Supported Order Notification Events (order.notification.*): record safely without retries
        if webhook_event in NOTIFICATION_WEBHOOK_EVENTS:
            ord_ent = (payload_obj.get("order") or {}).get("entity") if isinstance(payload_obj.get("order"), dict) else {}
            notif_ent = (payload_obj.get("order_notification") or {}).get("entity") if isinstance(payload_obj.get("order_notification"), dict) else {}
            notif_order_id = (
                (ord_ent.get("id") if isinstance(ord_ent, dict) else None)
                or (notif_ent.get("order_id") if isinstance(notif_ent, dict) else None)
                or data.get("order_id")
            )
            matched_pay: Optional[Payment] = None
            if notif_order_id:
                clean_notif_order = str(notif_order_id).strip()
                matched_pay = (
                    await self.db.execute(select(Payment).where(Payment.provider_order_id == clean_notif_order))
                ).scalar_one_or_none()
                if not matched_pay:
                    att = (
                        await self.db.execute(
                            select(PaymentOrderAttempt).where(PaymentOrderAttempt.provider_order_id == clean_notif_order)
                        )
                    ).scalar_one_or_none()
                    if att:
                        matched_pay = (
                            await self.db.execute(select(Payment).where(Payment.id == att.payment_id))
                        ).scalar_one_or_none()
            if matched_pay:
                self.db.add(
                    PaymentEvent(
                        payment_id=matched_pay.id,
                        event_type=webhook_event.upper().replace(".", "_"),
                        payload_json=json.dumps({"inbox_id": inbox.id, "order_id": notif_order_id}),
                    )
                )
            if webhook_event == "order.notification.failed":
                logger.warning("Razorpay order notification failed for order %s", notif_order_id)
            inbox.status = "PROCESSED"
            inbox.processed_at = now
            inbox.last_error = None
            await self._commit_or_500(f"notification webhook {webhook_event}")
            return {
                "status": "ACKNOWLEDGED_NOTIFICATION",
                "event": webhook_event,
                "order_id": notif_order_id,
                "payment_id": matched_pay.id if matched_pay else None,
                "secret_slot": secret_slot,
                "merchant_account_id": effective_account,
                "message": f"Notification Razorpay event {webhook_event} recorded cleanly.",
            }

        try:
            parsed = self._extract_and_validate_event_entities(
                webhook_event=webhook_event,
                data=data,
                payload_obj=payload_obj,
            )
        except HTTPException as exc:
            await self._fail_inbox(inbox, str(exc.detail), now)
            raise

        # 4. Strict payment resolution & ownership / cross-order conflict validation
        payment, early_unmatched = await self._resolve_and_validate_payment(
            webhook_event=webhook_event,
            digest=digest,
            inbox=inbox,
            parsed=parsed,
            now=now,
        )
        if early_unmatched is not None:
            return early_unmatched

        if payment.merchant_account_id and effective_account and payment.merchant_account_id != effective_account:
            reason = (
                f"Payment {payment.id} merchant account '{payment.merchant_account_id}' "
                f"conflicts with webhook account '{effective_account}'."
            )
            await self._record_anomaly_and_require_reconciliation(
                inbox=inbox,
                anomaly_type="MERCHANT_ACCOUNT_MISMATCH",
                payment=payment,
                parsed=parsed,
                reason=reason,
                now=now,
            )
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=reason)
        if effective_account and not payment.merchant_account_id:
            payment.merchant_account_id = effective_account

        # Reject Razorpay gateway events targeting physical cash settlements
        if payment.provider_name == "CASH" or payment.payment_method == "CASH":
            reason = f"Razorpay webhook {webhook_event} rejected for CASH payment {payment.id}."
            await self._record_anomaly_and_require_reconciliation(
                inbox=inbox,
                anomaly_type="CASH_GATEWAY_CONFLICT",
                payment=payment,
                parsed=parsed,
                reason=reason,
                now=now,
            )
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=reason)

        # 5. Execute authoritative event handlers (no commits inside handlers until final commit)
        if "refund" in webhook_event:
            return await self._handle_refund_webhook(
                webhook_event=webhook_event,
                inbox=inbox,
                payment=payment,
                parsed=parsed,
                now=now,
            )

        return await self._handle_payment_lifecycle_webhook(
            webhook_event=webhook_event,
            signature_header=signature_header,
            inbox=inbox,
            payment=payment,
            parsed=parsed,
            now=now,
        )

    async def _record_invalid_signature_attempt(self, *, digest: str, raw_body: bytes, now: datetime) -> None:
        """Persist failed invalid-signature delivery safely without downgrading an already-verified inbox row."""
        existing = (
            await self.db.execute(
                select(PaymentWebhookInbox).where(PaymentWebhookInbox.event_digest == digest)
            )
        ).scalar_one_or_none()

        if existing:
            if existing.signature_verified:
                logger.critical(
                    "Security alert: Forged signature received for already signature-verified digest %s",
                    digest,
                )
                return
            existing.attempts += 1
            existing.last_attempt_at = now
            existing.last_error = "Invalid webhook signature."
            existing.status = "FAILED"
        else:
            self.db.add(
                PaymentWebhookInbox(
                    provider_name="RAZORPAY",
                    event_digest=digest,
                    payload_json=raw_body.decode("utf-8", errors="replace"),
                    signature_verified=False,
                    status="FAILED",
                    attempts=1,
                    last_error="Invalid webhook signature.",
                    last_attempt_at=now,
                )
            )

        try:
            await self.db.commit()
        except IntegrityError:
            await self.db.rollback()
        except Exception as err:
            await self.db.rollback()
            logger.error("Failed to persist invalid signature webhook inbox entry: %s", err)
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail="Database failure recording invalid webhook attempt.",
            )

    async def _acquire_or_create_inbox(
        self,
        *,
        digest: str,
        raw_body: bytes,
        webhook_event: str,
        provider_event_id: Optional[str],
        now: datetime,
    ) -> tuple[PaymentWebhookInbox, Optional[Dict[str, Any]]]:
        """Initialize durable webhook inbox row cleanly, handling unique constraint collisions under concurrency."""
        inbox_stmt = (
            select(PaymentWebhookInbox)
            .where(PaymentWebhookInbox.event_digest == digest)
            .with_for_update()
        )
        existing_inbox = (await self.db.execute(inbox_stmt)).scalar_one_or_none()

        if not existing_inbox:
            existing_inbox = PaymentWebhookInbox(
                provider_name="RAZORPAY",
                event_digest=digest,
                event_type=webhook_event,
                provider_event_id=provider_event_id,
                payload_json=raw_body.decode("utf-8", errors="replace"),
                signature_verified=True,
                status="RECEIVED",
                attempts=1,
                last_attempt_at=now,
            )
            self.db.add(existing_inbox)
            try:
                await self.db.flush()
            except IntegrityError:
                # Concurrent worker inserted the same digest first; rollback and lock the winner row
                await self.db.rollback()
                existing_inbox = (await self.db.execute(inbox_stmt)).scalar_one_or_none()
                if not existing_inbox:
                    raise HTTPException(
                        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                        detail="Webhook inbox contention could not be resolved.",
                    )
            except Exception as err:
                await self.db.rollback()
                logger.error("Durable inbox initialization failed: %s", err)
                raise HTTPException(
                    status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                    detail="Database error during webhook inbox initialization.",
                )

        # Already finished (or reconciliation flagged) after verified delivery -> strictly idempotent
        if existing_inbox.signature_verified and existing_inbox.status in INBOX_TERMINAL_STATES:
            return existing_inbox, {
                "status": "ALREADY_PROCESSED",
                "digest": digest,
                "message": "Webhook event was previously processed idempotently.",
            }

        # Atomic Compare-And-Swap claim so concurrent workers (even on dialects without row locks) cannot double-claim
        stale_cutoff = now - timedelta(seconds=INBOX_STALE_PROCESSING_SECONDS)
        if existing_inbox.status == "RECEIVED":
            claim_stmt = (
                update(PaymentWebhookInbox)
                .where(
                    PaymentWebhookInbox.id == existing_inbox.id,
                    PaymentWebhookInbox.status == "RECEIVED",
                )
                .values(
                    status="PROCESSING",
                    signature_verified=True,
                    event_type=webhook_event,
                    provider_event_id=provider_event_id or existing_inbox.provider_event_id,
                    last_attempt_at=now,
                    last_error=None,
                )
            )
        else:
            claim_stmt = (
                update(PaymentWebhookInbox)
                .where(
                    PaymentWebhookInbox.id == existing_inbox.id,
                    or_(
                        PaymentWebhookInbox.status.in_(["PENDING", "FAILED"]),
                        (PaymentWebhookInbox.status == "PROCESSING")
                        & (
                            (PaymentWebhookInbox.last_attempt_at.is_(None))
                            | (PaymentWebhookInbox.last_attempt_at <= stale_cutoff)
                        ),
                    ),
                )
                .values(
                    status="PROCESSING",
                    signature_verified=True,
                    event_type=webhook_event,
                    provider_event_id=provider_event_id or existing_inbox.provider_event_id,
                    attempts=PaymentWebhookInbox.attempts + 1,
                    last_attempt_at=now,
                    last_error=None,
                )
            )

        claim_res = await self.db.execute(claim_stmt)
        if claim_res.rowcount == 0:
            await self.db.refresh(existing_inbox)
            if existing_inbox.signature_verified and existing_inbox.status in INBOX_TERMINAL_STATES:
                return existing_inbox, {
                    "status": "ALREADY_PROCESSED",
                    "digest": digest,
                    "message": "Webhook event was previously processed idempotently.",
                }
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Webhook delivery is already actively processing.",
            )

        await self.db.refresh(existing_inbox)
        return existing_inbox, None

    async def _fail_inbox(self, inbox: PaymentWebhookInbox, error_msg: str, now: datetime) -> None:
        inbox.status = "FAILED"
        inbox.last_error = error_msg[:500]
        inbox.last_attempt_at = now
        try:
            await self.db.commit()
        except Exception as err:
            await self.db.rollback()
            logger.error("Database failure persisting FAILED inbox state: %s", err)
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail="Database failure persisting webhook error state.",
            )

    def _extract_and_validate_event_entities(
        self,
        *,
        webhook_event: str,
        data: Dict[str, Any],
        payload_obj: Dict[str, Any],
    ) -> Dict[str, Any]:
        """Validate event-specific Razorpay payload sections and prevent entity confusion."""
        pay_wrap = payload_obj.get("payment")
        payment_entity = pay_wrap.get("entity") if isinstance(pay_wrap, dict) else None
        order_wrap = payload_obj.get("order")
        order_entity = order_wrap.get("entity") if isinstance(order_wrap, dict) else None

        if "refund" in webhook_event:
            ref_wrap = payload_obj.get("refund")
            refund_entity = ref_wrap.get("entity") if isinstance(ref_wrap, dict) else None
            if not isinstance(refund_entity, dict):
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail=f"Malformed {webhook_event} payload: missing refund entity.",
                )
            refund_id = refund_entity.get("id")
            if not isinstance(refund_id, str) or not refund_id.strip():
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail=f"Malformed {webhook_event} payload: missing refund.entity.id.",
                )
            refund_id = refund_id.strip()

            ref_pay_id = refund_entity.get("payment_id")
            ent_pay_id = payment_entity.get("id") if isinstance(payment_entity, dict) else None
            if ref_pay_id and ent_pay_id and ref_pay_id != ent_pay_id:
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail="Refund entity payment_id disagrees with payment entity id.",
                )
            provider_payment_id = ref_pay_id or ent_pay_id
            if not provider_payment_id or not isinstance(provider_payment_id, str):
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail=f"Malformed {webhook_event} payload: missing payment identifier.",
                )

            ref_currency = refund_entity.get("currency")
            if ref_currency is not None and str(ref_currency).upper() != "INR":
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail=f"Invalid refund currency: {ref_currency}. Expected INR.",
                )

            raw_refund_amount = refund_entity.get("amount")
            if not _is_int_paise(raw_refund_amount) or raw_refund_amount <= 0:
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail="Refund amount must be a positive integer in paise.",
                )

            order_id = (
                (payment_entity.get("order_id") if isinstance(payment_entity, dict) else None)
                or (order_entity.get("id") if isinstance(order_entity, dict) else None)
                or data.get("order_id")
            )
            return {
                "kind": "refund",
                "refund_entity": refund_entity,
                "payment_entity": payment_entity if isinstance(payment_entity, dict) else {},
                "order_entity": order_entity if isinstance(order_entity, dict) else {},
                "refund_id": refund_id,
                "gateway_refund_status": str(refund_entity.get("status") or "").lower(),
                "raw_refund_amount": raw_refund_amount,
                "provider_payment_id": provider_payment_id.strip(),
                "order_id": order_id,
                "notes": (
                    payment_entity.get("notes")
                    if isinstance(payment_entity, dict) and isinstance(payment_entity.get("notes"), dict)
                    else {}
                ),
                "currency": ref_currency.upper() if isinstance(ref_currency, str) else "INR",
            }

        # Payment / order events
        if not isinstance(payment_entity, dict):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Malformed {webhook_event} payload: missing payment entity.",
            )

        provider_payment_id = payment_entity.get("id")
        if not isinstance(provider_payment_id, str) or not provider_payment_id.strip():
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Malformed {webhook_event} payload: missing payment.entity.id.",
            )
        provider_payment_id = provider_payment_id.strip()

        order_id = (
            payment_entity.get("order_id")
            or (order_entity.get("id") if isinstance(order_entity, dict) else None)
            or data.get("order_id")
        )
        if order_entity and payment_entity.get("order_id") and order_entity.get("id"):
            if payment_entity["order_id"] != order_entity["id"]:
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail="Conflicting order_id between order and payment entities.",
                )

        pay_currency = payment_entity.get("currency")
        if pay_currency is not None and str(pay_currency).upper() != "INR":
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Invalid transaction currency: {pay_currency}. Expected INR.",
            )
        if isinstance(order_entity, dict) and order_entity.get("currency") is not None:
            if str(order_entity["currency"]).upper() != "INR":
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail=f"Invalid order currency: {order_entity['currency']}. Expected INR.",
                )

        raw_amount = payment_entity.get("amount")
        if webhook_event in ("payment.captured", "order.paid"):
            if not _is_int_paise(raw_amount) or raw_amount <= 0:
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail=f"Malformed {webhook_event} payload: payment amount must be a positive integer in paise.",
                )
            gw_status = str(payment_entity.get("status") or "").lower()
            if gw_status and gw_status != "captured":
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail=f"Capture webhook rejected: payment status is '{gw_status}', expected 'captured'.",
                )
        elif raw_amount is not None and (not _is_int_paise(raw_amount) or raw_amount <= 0):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Payment amount must be a positive integer in paise.",
            )

        notes = payment_entity.get("notes") if isinstance(payment_entity.get("notes"), dict) else {}
        return {
            "kind": "payment",
            "payment_entity": payment_entity,
            "order_entity": order_entity if isinstance(order_entity, dict) else {},
            "provider_payment_id": provider_payment_id,
            "order_id": order_id,
            "amount": raw_amount,
            "currency": pay_currency.upper() if isinstance(pay_currency, str) else "INR",
            "gateway_payment_status": str(payment_entity.get("status") or "").lower(),
            "notes": notes,
        }

    async def _resolve_and_validate_payment(
        self,
        *,
        webhook_event: str,
        digest: str,
        inbox: PaymentWebhookInbox,
        parsed: Dict[str, Any],
        now: datetime,
    ) -> tuple[Optional[Payment], Optional[Dict[str, Any]]]:
        """Resolve and lock internal Payment row while preventing cross-order/cross-ride reuse."""
        order_id = parsed["order_id"]
        provider_payment_id = parsed["provider_payment_id"]
        notes = parsed["notes"]
        ride_id_from_notes = notes.get("ride_id")
        customer_id_from_notes = notes.get("customer_id")
        user_id_from_notes = notes.get("user_id")

        payment: Optional[Payment] = None
        matched_attempt: Optional[PaymentOrderAttempt] = None
        if order_id:
            payment = (
                await self.db.execute(
                    select(Payment).where(Payment.provider_order_id == order_id).with_for_update()
                )
            ).scalar_one_or_none()
            if not payment:
                matched_attempt = (
                    await self.db.execute(
                        select(PaymentOrderAttempt).where(PaymentOrderAttempt.provider_order_id == order_id)
                    )
                ).scalar_one_or_none()
                if matched_attempt:
                    payment = (
                        await self.db.execute(
                            select(Payment).where(Payment.id == matched_attempt.payment_id).with_for_update()
                        )
                    ).scalar_one_or_none()

        if (
            payment
            and matched_attempt is not None
            and "refund" not in webhook_event
            and payment.status in {"CAPTURED", "PARTIALLY_REFUNDED", "REFUNDED", "PAID", "SETTLED", "SUCCESS"}
            and payment.provider_payment_id
            and provider_payment_id
            and payment.provider_payment_id != provider_payment_id
        ):
            matched_attempt.status = "CAPTURED"
            reason = (
                f"Duplicate order capture detected: alternate/superseded order {order_id} "
                f"(payment {provider_payment_id}) was paid while payment {payment.id} was already "
                f"settled with {payment.provider_payment_id}."
            )
            await self._record_anomaly_and_require_reconciliation(
                inbox=inbox,
                anomaly_type="DOUBLE_ORDER_PAYMENT",
                payment=payment,
                parsed=parsed,
                reason=reason,
                now=now,
            )
            return None, {
                "status": "DUPLICATE_ORDER_CAPTURE_ANOMALY",
                "event": webhook_event,
                "payment_id": payment.id,
                "order_id": order_id,
                "provider_payment_id": provider_payment_id,
                "message": reason,
            }

        if not payment and provider_payment_id:
            by_pid = (
                await self.db.execute(
                    select(Payment).where(Payment.provider_payment_id == provider_payment_id).with_for_update()
                )
            ).scalar_one_or_none()
            if by_pid:
                if order_id and by_pid.provider_order_id and by_pid.provider_order_id != order_id:
                    attempt_for_pid = (
                        await self.db.execute(
                            select(PaymentOrderAttempt).where(
                                PaymentOrderAttempt.payment_id == by_pid.id,
                                PaymentOrderAttempt.provider_order_id == order_id,
                            )
                        )
                    ).scalar_one_or_none()
                    if attempt_for_pid is None:
                        reason = (
                            f"Order conflict: payment {provider_payment_id} belongs to order "
                            f"{by_pid.provider_order_id}, not {order_id}."
                        )
                        await self._record_anomaly_and_require_reconciliation(
                            inbox=inbox,
                            anomaly_type="ORDER_OWNERSHIP_CONFLICT",
                            payment=by_pid,
                            parsed=parsed,
                            reason=reason,
                            now=now,
                        )
                        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=reason)
                    matched_attempt = attempt_for_pid
                payment = by_pid

        # Safe notes.ride_id binding only for non-refund payment events and only when unbound or matching
        if not payment and ride_id_from_notes and "refund" not in webhook_event:
            by_ride = (
                await self.db.execute(
                    select(Payment).where(Payment.ride_id == ride_id_from_notes).with_for_update()
                )
            ).scalar_one_or_none()
            if by_ride:
                order_conflict = bool(order_id and by_ride.provider_order_id and by_ride.provider_order_id != order_id)
                pid_conflict = bool(
                    provider_payment_id and by_ride.provider_payment_id and by_ride.provider_payment_id != provider_payment_id
                )
                if order_conflict or pid_conflict:
                    reason = f"Cross-order hijack prevented on ride {ride_id_from_notes}."
                    await self._record_anomaly_and_require_reconciliation(
                        inbox=inbox,
                        anomaly_type="ORDER_OWNERSHIP_CONFLICT",
                        payment=by_ride,
                        parsed=parsed,
                        reason=reason,
                        now=now,
                    )
                    raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=reason)
                payment = by_ride
            elif webhook_event in ("payment.authorized", "payment.captured", "order.paid"):
                ride_obj = (
                    await self.db.execute(select(Ride).where(Ride.id == ride_id_from_notes).with_for_update())
                ).scalar_one_or_none()
                if ride_obj:
                    expected_fare = int(ride_obj.final_fare_cents or ride_obj.estimated_fare_cents or 0)
                    amt = parsed.get("amount")
                    if customer_id_from_notes and ride_obj.customer_id != customer_id_from_notes:
                        reason = f"Notes customer_id mismatch for ride {ride_obj.id}."
                        await self._record_anomaly_and_require_reconciliation(
                            inbox=inbox,
                            anomaly_type="ORDER_OWNERSHIP_CONFLICT",
                            payment=None,
                            parsed=parsed,
                            reason=reason,
                            now=now,
                        )
                        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=reason)
                    if expected_fare > 0 and amt == expected_fare:
                        payment = Payment(
                            ride_id=ride_obj.id,
                            customer_id=ride_obj.customer_id,
                            amount_cents=expected_fare,
                            currency="INR",
                            status="CREATED",
                            payment_method="RAZORPAY",
                            provider_name="RAZORPAY",
                            provider_order_id=order_id,
                            idempotency_key=f"wh_{digest[:32]}",
                        )
                        self.db.add(payment)
                        await self.db.flush()

        if not payment:
            reason = f"No payment record matches order_id={order_id}, payment_id={provider_payment_id}"
            await self._record_reconciliation_anomaly(
                anomaly_type="UNMATCHED_PAYMENT",
                payment=None,
                inbox=inbox,
                parsed=parsed,
                expected_amount_cents=None,
                raw_gateway_amount_cents=parsed.get("amount") or parsed.get("raw_refund_amount"),
                applied_amount_cents=0,
                excess_amount_cents=0,
                reason=reason,
            )
            inbox.status = "UNMATCHED_PAYMENT"
            inbox.last_error = reason[:500]
            inbox.processed_at = now
            try:
                await self.db.commit()
            except Exception as e:
                await self.db.rollback()
                logger.error("Failed to commit UNMATCHED_PAYMENT inbox status: %s", e)
                raise HTTPException(
                    status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                    detail="Database failure on unmatched payment recording.",
                )
            return None, {
                "status": "UNMATCHED_PAYMENT",
                "event": webhook_event,
                "order_id": order_id,
                "message": "Payment event logged in inbox but not linked to any journey.",
            }

        # Reconcile legitimate superseded PaymentOrderAttempt for this exact Payment before strict check
        if matched_attempt is not None and order_id and payment.provider_order_id != order_id and "refund" not in webhook_event:
            if payment.status in {"CREATED", "PENDING", "AUTHORIZED"}:
                if (
                    matched_attempt.amount_cents != payment.amount_cents
                    and parsed.get("amount") == matched_attempt.amount_cents
                ):
                    await self._record_reconciliation_anomaly(
                        anomaly_type="SUPERSEDED_ORDER_AMOUNT_DRIFT",
                        payment=payment,
                        inbox=inbox,
                        parsed=parsed,
                        expected_amount_cents=payment.amount_cents,
                        raw_gateway_amount_cents=parsed.get("amount"),
                        applied_amount_cents=matched_attempt.amount_cents,
                        excess_amount_cents=0,
                        reason=(
                            f"Customer paid superseded order {order_id} ({matched_attempt.amount_cents} paise) "
                            f"instead of current order {payment.provider_order_id} ({payment.amount_cents} paise)."
                        ),
                    )
                    payment.amount_cents = matched_attempt.amount_cents
                payment.provider_order_id = order_id

        # Validate consistent ownership/binding against matched Payment
        if order_id and payment.provider_order_id and payment.provider_order_id != order_id:
            attempt_check = (
                await self.db.execute(
                    select(PaymentOrderAttempt).where(
                        PaymentOrderAttempt.payment_id == payment.id,
                        PaymentOrderAttempt.provider_order_id == order_id,
                    )
                )
            ).scalar_one_or_none()
            if attempt_check is None:
                reason = f"Order mismatch: Payment {payment.id} expects {payment.provider_order_id}, got {order_id}."
                await self._record_anomaly_and_require_reconciliation(
                    inbox=inbox, anomaly_type="ORDER_OWNERSHIP_CONFLICT", payment=payment, parsed=parsed, reason=reason, now=now
                )
                raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=reason)

        if ride_id_from_notes and payment.ride_id and payment.ride_id != ride_id_from_notes:
            reason = f"Ride mismatch: Payment {payment.id} is bound to {payment.ride_id}, not {ride_id_from_notes}."
            await self._record_anomaly_and_require_reconciliation(
                inbox=inbox, anomaly_type="ORDER_OWNERSHIP_CONFLICT", payment=payment, parsed=parsed, reason=reason, now=now
            )
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=reason)

        if user_id_from_notes and payment.user_id and payment.user_id != user_id_from_notes:
            reason = f"User mismatch: Payment {payment.id} is bound to {payment.user_id}, not {user_id_from_notes}."
            await self._record_anomaly_and_require_reconciliation(
                inbox=inbox, anomaly_type="ORDER_OWNERSHIP_CONFLICT", payment=payment, parsed=parsed, reason=reason, now=now
            )
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=reason)

        if payment.currency.upper() != "INR":
            reason = f"Currency mismatch: Payment {payment.id} currency is {payment.currency}, expected INR."
            await self._record_anomaly_and_require_reconciliation(
                inbox=inbox, anomaly_type="CURRENCY_MISMATCH", payment=payment, parsed=parsed, reason=reason, now=now
            )
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=reason)

        return payment, None

    async def _handle_payment_lifecycle_webhook(
        self,
        *,
        webhook_event: str,
        signature_header: str,
        inbox: PaymentWebhookInbox,
        payment: Payment,
        parsed: Dict[str, Any],
        now: datetime,
    ) -> Dict[str, Any]:
        provider_payment_id = parsed["provider_payment_id"]
        order_id = parsed["order_id"]
        gw_amount = parsed.get("amount")

        # Prevent same provider_payment_id from capturing a different Payment
        existing_other = (
            await self.db.execute(
                select(Payment).where(
                    Payment.provider_payment_id == provider_payment_id,
                    Payment.id != payment.id,
                )
            )
        ).scalar_one_or_none()
        if existing_other:
            reason = f"Gateway payment {provider_payment_id} already bound to payment {existing_other.id}."
            await self._record_anomaly_and_require_reconciliation(
                inbox=inbox, anomaly_type="ORDER_OWNERSHIP_CONFLICT", payment=payment, parsed=parsed, reason=reason, now=now
            )
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=reason)

        if webhook_event == "payment.failed":
            if payment.status in {"CAPTURED", "PARTIALLY_REFUNDED", "REFUNDED", "PAID", "SETTLED", "SUCCESS"}:
                inbox.status = "PROCESSED"
                inbox.processed_at = now
                await self._commit_or_500("webhook payment.failed terminal ignore")
                return {
                    "status": "IGNORED_TERMINAL_STATE",
                    "event": webhook_event,
                    "payment_id": payment.id,
                    "message": f"Cannot downgrade {payment.status} payment on payment.failed.",
                }
            transition_payment_status(payment, "FAILED", context="webhook payment.failed")
            if not payment.provider_payment_id:
                payment.provider_payment_id = provider_payment_id
            if order_id:
                await self.db.execute(
                    update(PaymentOrderAttempt)
                    .where(PaymentOrderAttempt.provider_order_id == order_id)
                    .values(status="FAILED")
                )

        elif webhook_event == "payment.authorized":
            if gw_amount is not None and gw_amount != payment.amount_cents:
                reason = (
                    f"Authorization amount mismatch for payment {payment.id}: "
                    f"expected {payment.amount_cents}, got {gw_amount}."
                )
                await self._record_anomaly_and_require_reconciliation(
                    inbox=inbox,
                    anomaly_type="PAYMENT_AMOUNT_MISMATCH",
                    payment=payment,
                    parsed=parsed,
                    reason=reason,
                    now=now,
                )
                raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=reason)

            if payment.status in {"CAPTURED", "PARTIALLY_REFUNDED", "REFUNDED", "PAID", "SETTLED", "SUCCESS"}:
                inbox.status = "PROCESSED"
                inbox.processed_at = now
                await self._commit_or_500("webhook payment.authorized terminal ignore")
                return {
                    "status": "ALREADY_PROCESSED",
                    "event": webhook_event,
                    "payment_id": payment.id,
                    "message": "Payment already captured/settled; ignoring late authorization webhook.",
                }
            if not validate_payment_transition(payment.status, "AUTHORIZED"):
                reason = f"Invalid state transition {payment.status} -> AUTHORIZED for payment {payment.id}."
                await self._record_anomaly_and_require_reconciliation(
                    inbox=inbox, anomaly_type="INVALID_STATE_TRANSITION", payment=payment, parsed=parsed, reason=reason, now=now
                )
                raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=reason)

            transition_payment_status(payment, "AUTHORIZED", context="webhook payment.authorized")
            payment.provider_payment_id = provider_payment_id
            if order_id and not payment.provider_order_id:
                payment.provider_order_id = order_id
            if order_id:
                await self.db.execute(
                    update(PaymentOrderAttempt)
                    .where(PaymentOrderAttempt.provider_order_id == order_id)
                    .values(status="AUTHORIZED")
                )

        elif webhook_event in ("payment.captured", "order.paid"):
            if gw_amount != payment.amount_cents:
                reason = (
                    f"Capture amount mismatch for payment {payment.id}: "
                    f"expected {payment.amount_cents}, got {gw_amount}."
                )
                await self._record_anomaly_and_require_reconciliation(
                    inbox=inbox,
                    anomaly_type="PAYMENT_AMOUNT_MISMATCH",
                    payment=payment,
                    parsed=parsed,
                    reason=reason,
                    now=now,
                )
                raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=reason)

            if payment.status in {"FAILED", "EXPIRED"}:
                reason = f"Cannot capture payment {payment.id} in terminal state {payment.status}."
                await self._record_anomaly_and_require_reconciliation(
                    inbox=inbox, anomaly_type="INVALID_STATE_TRANSITION", payment=payment, parsed=parsed, reason=reason, now=now
                )
                raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=reason)

            if order_id and not payment.provider_order_id:
                payment.provider_order_id = order_id

            fin_result = await self.finalize_payment_capture(
                payment=payment,
                provider_payment_id=provider_payment_id,
                provider_signature=signature_header,
                auto_commit=False,
            )
            self.db.add(
                PaymentEvent(
                    payment_id=payment.id,
                    event_type=webhook_event.upper().replace(".", "_"),
                    payload_json=json.dumps(
                        {"inbox_id": inbox.id, "digest": inbox.event_digest, "provider_payment_id": provider_payment_id}
                    ),
                )
            )
            inbox.status = "PROCESSED"
            inbox.processed_at = now
            await self._commit_or_500("finalize_payment_capture webhook")

            if fin_result.get("status") == "ALREADY_PROCESSED":
                return {
                    "status": "ALREADY_PROCESSED",
                    "event": webhook_event,
                    "payment_id": payment.id,
                    "detail": fin_result,
                }
            return {
                "status": "SUCCESS",
                "event": webhook_event,
                "payment_id": payment.id,
                "purpose": getattr(payment, "purpose", "RIDE_FARE"),
                "detail": fin_result,
            }

        self.db.add(
            PaymentEvent(
                payment_id=payment.id,
                event_type=webhook_event.upper().replace(".", "_"),
                payload_json=json.dumps({"inbox_id": inbox.id, "digest": inbox.event_digest}),
            )
        )
        inbox.status = "PROCESSED"
        inbox.processed_at = now
        await self._commit_or_500("webhook payment lifecycle")

        return {
            "status": "SUCCESS",
            "event": webhook_event,
            "payment_id": payment.id,
            "provider_payment_id": provider_payment_id,
            "order_id": order_id,
            "amount_cents": payment.amount_cents,
        }

    async def _handle_refund_webhook(
        self,
        *,
        webhook_event: str,
        inbox: PaymentWebhookInbox,
        payment: Payment,
        parsed: Dict[str, Any],
        now: datetime,
    ) -> Dict[str, Any]:
        refund_id = parsed["refund_id"]
        gateway_status = parsed["gateway_refund_status"]
        raw_refund_amount = parsed["raw_refund_amount"]
        provider_payment_id = parsed["provider_payment_id"]
        refund_entity = parsed.get("refund_entity") or {}

        if payment.status not in {
            "CAPTURED",
            "REFUND_PENDING",
            "PARTIALLY_REFUNDED",
            "REFUNDED",
            "PAID",
            "SETTLED",
            "SUCCESS",
        }:
            reason = f"Refund {refund_id} rejected for non-captured payment {payment.id} in status {payment.status}."
            await self._record_anomaly_and_require_reconciliation(
                inbox=inbox, anomaly_type="REFUND_ON_UNCAPTURED_PAYMENT", payment=payment, parsed=parsed, reason=reason, now=now
            )
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=reason)

        if payment.provider_payment_id and payment.provider_payment_id != provider_payment_id:
            reason = (
                f"Refund {refund_id} targets payment {provider_payment_id}, "
                f"which does not match internal payment gateway ID {payment.provider_payment_id}."
            )
            await self._record_anomaly_and_require_reconciliation(
                inbox=inbox, anomaly_type="REFUND_PAYMENT_MISMATCH", payment=payment, parsed=parsed, reason=reason, now=now
            )
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=reason)

        # Look up by authoritative provider_refund_id (with arn_reference / notes internal_refund_id fallback)
        internal_refund_id_note = (
            (refund_entity.get("notes") or {}).get("internal_refund_id")
            if isinstance(refund_entity.get("notes"), dict)
            else None
        )
        lookup_clauses = [
            PaymentRefund.provider_refund_id == refund_id,
            PaymentRefund.arn_reference == refund_id,
            PaymentRefund.id == refund_id,
        ]
        if internal_refund_id_note:
            lookup_clauses.append(PaymentRefund.id == str(internal_refund_id_note))

        existing_refund_stmt = select(PaymentRefund).where(or_(*lookup_clauses)).with_for_update()
        existing_refund = (await self.db.execute(existing_refund_stmt)).scalar_one_or_none()
        if existing_refund and existing_refund.payment_id != payment.id:
            reason = f"Gateway refund ID {refund_id} already belongs to payment {existing_refund.payment_id}."
            await self._record_anomaly_and_require_reconciliation(
                inbox=inbox, anomaly_type="REFUND_PAYMENT_MISMATCH", payment=payment, parsed=parsed, reason=reason, now=now
            )
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=reason)

        # 1. Handle refund.failed via central reversal helper
        if webhook_event == "refund.failed" or (gateway_status == "failed" and webhook_event != "refund.processed"):
            rev = await self.apply_failed_refund_reversal(
                payment=payment,
                refund_obj=existing_refund,
                refund_id=refund_id,
                raw_refund_amount=raw_refund_amount,
                failure_reason=f"Gateway refund failure ({webhook_event})",
                now=now,
            )
            if rev.get("rejected_transition"):
                logger.warning(
                    "Rejecting refund.failed transition for refund %s: %s",
                    existing_refund.id if existing_refund else refund_id,
                    rev.get("reason"),
                )
                inbox.status = "PROCESSED"
                inbox.processed_at = now
                await self._commit_or_500("refund.failed regression rejection")
                return {
                    "status": "REJECTED_STATE_TRANSITION",
                    "event": webhook_event,
                    "payment_id": payment.id,
                    "refund_id": refund_id,
                    "message": rev["reason"],
                }

            inbox.status = "PROCESSED"
            inbox.processed_at = now
            await self._commit_or_500("webhook refund.failed")
            return {
                "status": "REFUND_FAILED",
                "event": webhook_event,
                "payment_id": payment.id,
                "refund_id": refund_id,
                "message": "Gateway refund failed; payment state and any provisional clawback cleanly reversed.",
            }

        # 2. Handle refund.created / pending
        if webhook_event == "refund.created" or (gateway_status == "pending" and webhook_event != "refund.processed"):
            if existing_refund:
                if existing_refund.status in {"CREDITED", "PROCESSED", "ANOMALY_EXCESS", "PARTIALLY_APPLIED_EXCESS"}:
                    inbox.status = "PROCESSED"
                    inbox.processed_at = now
                    await self._commit_or_500("refund.created already credited")
                    return {
                        "status": "ALREADY_PROCESSED",
                        "event": webhook_event,
                        "payment_id": payment.id,
                        "refund_id": refund_id,
                        "message": "Refund is already CREDITED.",
                    }
                if existing_refund.status == "FAILED":
                    inbox.status = "PROCESSED"
                    inbox.processed_at = now
                    await self._commit_or_500("refund.created late ignore")
                    return {
                        "status": "REFUND_FAILED",
                        "event": webhook_event,
                        "payment_id": payment.id,
                        "refund_id": refund_id,
                        "message": "Refund already FAILED; ignoring late refund.created.",
                    }
                existing_refund.provider_refund_id = refund_id
                existing_refund.raw_gateway_amount_cents = raw_refund_amount
                if existing_refund.status == "INITIATED":
                    transition_refund_status(existing_refund, "GATEWAY_PROCESSING", context="webhook refund.created")
            else:
                self.db.add(
                    PaymentRefund(
                        payment_id=payment.id,
                        ride_id=payment.ride_id,
                        customer_id=payment.customer_id,
                        amount_cents=raw_refund_amount,
                        raw_gateway_amount_cents=raw_refund_amount,
                        excess_amount_cents=0,
                        is_partial=False,
                        reason=f"Gateway refund created ({webhook_event})",
                        status="PENDING",
                        provider_refund_id=refund_id,
                        arn_reference=refund_id,
                    )
                )

            inbox.status = "PROCESSED"
            inbox.processed_at = now
            await self._commit_or_500("webhook refund.created")
            return {
                "status": "REFUND_PENDING",
                "event": webhook_event,
                "payment_id": payment.id,
                "refund_id": refund_id,
                "message": "Refund created at gateway; awaiting authoritative settlement before clawback.",
            }

        # 3. Authoritative settlement: refund.processed
        if gateway_status and gateway_status != "processed":
            reason = f"refund.processed payload contained non-processed gateway status '{gateway_status}'."
            await self._fail_inbox(inbox, reason, now)
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=reason)

        arn = (refund_entity.get("acquirer_data") or {}).get("arn") if isinstance(refund_entity.get("acquirer_data"), dict) else None
        settled = await self.apply_confirmed_refund_settlement(
            payment=payment,
            refund_obj=existing_refund,
            refund_id=refund_id,
            raw_refund_amount=raw_refund_amount,
            reason=f"Authoritative webhook refund ({webhook_event})",
            arn_reference=arn or refund_id,
            settled_status="CREDITED",
            inbox=inbox,
            parsed=parsed,
            now=now,
        )
        if settled.get("already_processed"):
            inbox.status = "PROCESSED"
            inbox.processed_at = now
            await self._commit_or_500("refund.processed idempotent replay")
            return {
                "status": "ALREADY_PROCESSED",
                "event": webhook_event,
                "payment_id": payment.id,
                "refund_id": refund_id,
                "message": "Refund event was previously processed idempotently.",
            }

        is_anomaly = settled["is_anomaly"]
        applied_refund = settled["applied_refund"]
        excess_anomaly_cents = settled["excess_anomaly_cents"]
        anomaly_reason = settled["anomaly_reason"]

        inbox.status = "RECONCILIATION_REQUIRED" if is_anomaly else "PROCESSED"
        inbox.last_error = anomaly_reason[:500] if is_anomaly else None
        inbox.processed_at = now
        await self._commit_or_500("webhook refund.processed")

        if is_anomaly and applied_refund == 0:
            return {
                "status": "ANOMALY_EXCESS_REFUND",
                "event": webhook_event,
                "payment_id": payment.id,
                "refund_id": refund_id,
                "raw_gateway_amount_cents": raw_refund_amount,
                "applied_amount_cents": applied_refund,
                "excess_amount_cents": excess_anomaly_cents,
                "message": anomaly_reason,
            }

        return {
            "status": "SUCCESS",
            "event": webhook_event,
            "payment_id": payment.id,
            "refund_id": refund_id,
            "applied_amount_cents": applied_refund,
            "raw_gateway_amount_cents": raw_refund_amount,
            "excess_amount_cents": excess_anomaly_cents,
            "amount_cents": payment.amount_cents,
        }

    async def _record_reconciliation_anomaly(
        self,
        *,
        anomaly_type: str,
        payment: Optional[Payment],
        inbox: Optional[PaymentWebhookInbox],
        parsed: Dict[str, Any],
        expected_amount_cents: Optional[int],
        raw_gateway_amount_cents: Optional[int],
        applied_amount_cents: Optional[int],
        excess_amount_cents: int,
        reason: str,
    ) -> None:
        refund_id = parsed.get("refund_id")
        if refund_id:
            dup = (
                await self.db.execute(
                    select(PaymentReconciliationAnomaly).where(
                        PaymentReconciliationAnomaly.anomaly_type == anomaly_type,
                        PaymentReconciliationAnomaly.provider_refund_id == refund_id,
                    )
                )
            ).scalar_one_or_none()
            if dup:
                return

        self.db.add(
            PaymentReconciliationAnomaly(
                payment_id=payment.id if payment else None,
                webhook_inbox_id=inbox.id if inbox else None,
                provider_name="RAZORPAY",
                provider_payment_id=parsed.get("provider_payment_id"),
                provider_order_id=parsed.get("order_id"),
                provider_refund_id=refund_id,
                anomaly_type=anomaly_type,
                expected_amount_cents=expected_amount_cents,
                raw_gateway_amount_cents=raw_gateway_amount_cents,
                applied_amount_cents=applied_amount_cents,
                excess_amount_cents=excess_amount_cents,
                currency=parsed.get("currency", "INR"),
                status="OPEN",
                reason=reason[:500],
                details_json=json.dumps(
                    {
                        "digest": inbox.event_digest if inbox else None,
                        "event": inbox.event_type if inbox else "INTERNAL",
                    }
                ),
            )
        )

    async def _record_anomaly_and_require_reconciliation(
        self,
        *,
        inbox: PaymentWebhookInbox,
        anomaly_type: str,
        payment: Optional[Payment],
        parsed: Dict[str, Any],
        reason: str,
        now: datetime,
    ) -> None:
        await self._record_reconciliation_anomaly(
            anomaly_type=anomaly_type,
            payment=payment,
            inbox=inbox,
            parsed=parsed,
            expected_amount_cents=payment.amount_cents if payment else None,
            raw_gateway_amount_cents=parsed.get("amount") or parsed.get("raw_refund_amount"),
            applied_amount_cents=0,
            excess_amount_cents=0,
            reason=reason,
        )
        inbox.status = "RECONCILIATION_REQUIRED"
        inbox.last_error = reason[:500]
        inbox.processed_at = now
        await self._commit_or_500(f"reconciliation anomaly ({anomaly_type})")

    async def _commit_or_500(self, op_label: str) -> None:
        try:
            await self.db.commit()
        except Exception as e:
            await self.db.rollback()
            logger.error("Database commit failed during %s: %s", op_label, e)
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail=f"Database commit failure during {op_label}.",
            )

    # ------------------------------------------------------------------
    # Physical Cash Settlement Flow (Strict Separation from Razorpay)
    # ------------------------------------------------------------------
    async def confirm_cash_collection(self, driver_user_id: str, ride_id: str) -> Dict[str, Any]:
        """
        Driver confirmation of physical cash collection at journey finish.
        Enforces chauffeur authorization and uses central state transitions cleanly.
        """
        driver_prof = (
            await self.db.execute(select(DriverProfile).where(DriverProfile.user_id == driver_user_id))
        ).scalar_one_or_none()
        if not driver_prof:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="User is not a registered chauffeur.")

        ride = (await self.db.execute(select(Ride).where(Ride.id == ride_id).with_for_update())).scalar_one_or_none()
        if not ride:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Ride not found.")

        if ride.assigned_driver_id != driver_prof.id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="You are not the designated chauffeur for this journey.",
            )

        stmt = select(Payment).where(Payment.ride_id == ride_id).with_for_update()
        payment = (await self.db.execute(stmt)).scalar_one_or_none()
        if not payment:
            fare = int(ride.final_fare_cents or ride.estimated_fare_cents or 0)
            if fare <= 0:
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail="Cannot collect cash: Ride does not have a valid final fare or fare snapshot.",
                )
            payment = Payment(
                ride_id=ride_id,
                customer_id=ride.customer_id,
                amount_cents=fare,
                currency="INR",
                status="CREATED",
                payment_method="CASH",
                provider_name="CASH",
                idempotency_key=f"cash_{ride_id}",
            )
            self.db.add(payment)
            await self.db.flush()

        if payment.amount_cents <= 0:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Cannot collect cash: Payment amount is invalid or zero.",
            )

        if payment.cash_collected_confirmed or payment.status in ["CAPTURED", "SETTLED", "PARTIALLY_REFUNDED", "REFUNDED"]:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Cash collection for this ride has already been confirmed and processed.",
            )

        if payment.payment_method != "CASH":
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Journey was not designated for physical cash settlement.",
            )

        now = datetime.now(timezone.utc)
        payment.cash_collected_confirmed = True
        payment.cash_confirmed_by_driver_at = now
        try:
            transition_payment_status(payment, "CAPTURED", context="cash collection")
        except ValueError as err:
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=str(err))
        payment.provider_name = "CASH"

        if ride.status != "COMPLETED":
            ride.status = "COMPLETED"

        from backend.application.services.commission_service import CommissionService
        fare_cents = payment.amount_cents
        snap = await CommissionService.calculate_for_ride(
            self.db,
            gross_cents=fare_cents,
            category_id=getattr(ride, "vehicle_category_id", None) or getattr(ride, "category_id", None),
            effective_at=getattr(ride, "completed_at", None) or getattr(ride, "requested_at", None) or now,
            calculation_basis="RIDE_COMPLETION",
        )
        commission_cents = snap.commission_cents
        await CommissionService.record_ride_snapshot(
            self.db,
            ride_id=ride.id,
            snapshot=snap,
            category_id=getattr(ride, "vehicle_category_id", None) or getattr(ride, "category_id", None),
            commission_classification="RECEIVABLE",
            currency=payment.currency or "INR",
        )

        existing_comm = (
            await self.db.execute(
                select(DriverLedgerEntry).where(
                    DriverLedgerEntry.driver_id == driver_prof.id,
                    DriverLedgerEntry.reference_id == ride.id,
                    DriverLedgerEntry.category.in_(["COMMISSION_RECEIVABLE", "COMMISSION"]),
                )
            )
        ).scalars().first()

        wallet = (
            await self.db.execute(
                select(DriverWallet).where(DriverWallet.driver_id == driver_prof.id).with_for_update()
            )
        ).scalar_one_or_none()
        if not wallet:
            wallet = DriverWallet(driver_id=driver_prof.id, balance_cents=0, currency="INR")
            self.db.add(wallet)
            await self.db.flush()

        if not existing_comm:
            # Record gross cash earning and commission receivable separately.
            # Do NOT immediately deduct commission from the driver's wallet on cash collection.
            self.db.add(
                DriverLedgerEntry(
                    id=str(uuid.uuid4()),
                    driver_id=driver_prof.id,
                    entry_type="CREDIT",
                    category="CASH_TRIP_EARNING",
                    amount_cents=fare_cents,
                    balance_after_cents=wallet.balance_cents,
                    reference_id=ride.id,
                    description=f"Gross cash earning for ride #{ride.id[:8]}",
                    created_at=now,
                )
            )
            if commission_cents > 0:
                self.db.add(
                    DriverLedgerEntry(
                        id=str(uuid.uuid4()),
                        driver_id=driver_prof.id,
                        entry_type="DEBIT",
                        category="COMMISSION_RECEIVABLE",
                        amount_cents=-commission_cents,
                        balance_after_cents=wallet.balance_cents,
                        reference_id=ride.id,
                        description=f"Platform commission receivable ({snap.percentage}% v{snap.version}) for cash ride #{ride.id[:8]}",
                        created_at=now,
                    )
                )

        await self._commit_or_500("cash collection confirmation")

        driver_net_earnings_cents = fare_cents - commission_cents

        return {
            "payment_id": payment.id,
            "status": payment.status,
            "payment_method": "CASH",
            "provider_name": "CASH",
            "cash_collected_cents": fare_cents,
            "driver_gross_earnings_cents": fare_cents,
            "driver_net_earnings_cents": driver_net_earnings_cents,
            "commission_receivable_cents": commission_cents,
            "commission_deducted_cents": 0,
            "commission_percentage": snap.percentage,
            "commission_version": snap.version,
            "driver_wallet_balance_cents": wallet.balance_cents,
            "cash_collected": True,
            "amount_cents": payment.amount_cents,
        }

    # ------------------------------------------------------------------
    # Authoritative Strict Checkout Payment Verification & Capture
    # ------------------------------------------------------------------
    async def verify_checkout_and_finalize_capture(
        self,
        *,
        actor_user_id: str,
        actor_role: Optional[str],
        razorpay_order_id: str,
        razorpay_payment_id: str,
        razorpay_signature: str,
        expected_purpose: Optional[str] = None,
        ride_id: Optional[str] = None,
    ) -> Dict[str, Any]:
        """
        Single authoritative checkout payment verification and capture engine shared by
        Wallet Top-up verification and Ride Fare / Generic checkout verification.
        Performs fail-closed gateway response validation (no optional/defaulted fields)
        and deterministic row locking: Payment -> Wallet / Ride -> DriverWallet.
        """
        clean_order_id = (razorpay_order_id or "").strip()
        clean_payment_id = (razorpay_payment_id or "").strip()
        clean_signature = (razorpay_signature or "").strip()
        clean_ride_id = (ride_id or "").strip() or None
        is_admin = (actor_role or "").strip().lower() in ("admin", "superadmin", "super administrator")

        if not clean_order_id or not clean_payment_id or not clean_signature:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="razorpay_order_id, razorpay_payment_id, and razorpay_signature are all required.",
            )

        # 1. Cryptographic HMAC-SHA256 signature verification
        if not self.provider.verify_payment_signature(
            razorpay_order_id=clean_order_id,
            razorpay_payment_id=clean_payment_id,
            razorpay_signature=clean_signature,
        ):
            logger.error(
                "Razorpay signature mismatch: order_id=%s, payment_id=%s, user_id=%s",
                clean_order_id,
                clean_payment_id,
                actor_user_id,
            )
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Cryptographic verification failed: Invalid Razorpay signature.",
            )

        async def _load_payment_by_order(*, lock: bool) -> tuple[Optional[Payment], Optional[PaymentOrderAttempt]]:
            q = select(Payment).where(Payment.provider_order_id == clean_order_id)
            if lock:
                q = q.with_for_update()
            found = (await self.db.execute(q)).scalar_one_or_none()
            att_match: Optional[PaymentOrderAttempt] = None
            if not found:
                att_match = (
                    await self.db.execute(
                        select(PaymentOrderAttempt).where(
                            PaymentOrderAttempt.provider_order_id == clean_order_id
                        )
                    )
                ).scalar_one_or_none()
                if att_match:
                    q_att = select(Payment).where(Payment.id == att_match.payment_id)
                    if lock:
                        q_att = q_att.with_for_update()
                    found = (await self.db.execute(q_att)).scalar_one_or_none()
            if not found and clean_ride_id and expected_purpose != "WALLET_TOPUP":
                q_ride = select(Payment).where(Payment.ride_id == clean_ride_id)
                if lock:
                    q_ride = q_ride.with_for_update()
                found = (await self.db.execute(q_ride)).scalar_one_or_none()
            elif found and found.provider_order_id != clean_order_id:
                att_match = (
                    await self.db.execute(
                        select(PaymentOrderAttempt).where(
                            PaymentOrderAttempt.payment_id == found.id,
                            PaymentOrderAttempt.provider_order_id == clean_order_id,
                        )
                    )
                ).scalar_one_or_none()
            return found, att_match

        async def _assert_ownership_and_binding(
            pay_obj: Payment, att_obj: Optional[PaymentOrderAttempt]
        ) -> Optional[PaymentOrderAttempt]:
            if pay_obj.provider_name == "CASH" or pay_obj.payment_method == "CASH":
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail="Physical CASH payments cannot be verified via Razorpay.",
                )
            if clean_ride_id and pay_obj.ride_id and pay_obj.ride_id != clean_ride_id:
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail="Specified ride_id does not match the payment order association.",
                )
            if expected_purpose and pay_obj.purpose != expected_purpose:
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail=f"Payment purpose mismatch: expected {expected_purpose}, found {pay_obj.purpose}.",
                )
            if pay_obj.currency.upper() != "INR":
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail=f"Invalid internal payment currency: {pay_obj.currency}. Expected INR.",
                )

            if pay_obj.purpose == "WALLET_TOPUP":
                if not pay_obj.user_id or (pay_obj.user_id != actor_user_id and not is_admin):
                    raise HTTPException(
                        status_code=status.HTTP_403_FORBIDDEN,
                        detail="Access denied: You do not own this payment order.",
                    )
            else:
                if not is_admin:
                    if pay_obj.user_id and pay_obj.user_id != actor_user_id:
                        raise HTTPException(
                            status_code=status.HTTP_403_FORBIDDEN,
                            detail="You do not have permission to verify this payment.",
                        )
                    if pay_obj.customer_id:
                        prof = (
                            await self.db.execute(
                                select(CustomerProfile).where(CustomerProfile.user_id == actor_user_id)
                            )
                        ).scalar_one_or_none()
                        if not prof or pay_obj.customer_id != prof.id:
                            raise HTTPException(
                                status_code=status.HTTP_403_FORBIDDEN,
                                detail="You do not have permission to verify payment for this journey.",
                            )
                    elif not pay_obj.user_id:
                        raise HTTPException(
                            status_code=status.HTTP_403_FORBIDDEN,
                            detail="Payment ownership cannot be established.",
                        )

            effective_att = att_obj
            if pay_obj.provider_order_id and pay_obj.provider_order_id != clean_order_id:
                if effective_att is None:
                    effective_att = (
                        await self.db.execute(
                            select(PaymentOrderAttempt).where(
                                PaymentOrderAttempt.payment_id == pay_obj.id,
                                PaymentOrderAttempt.provider_order_id == clean_order_id,
                            )
                        )
                    ).scalar_one_or_none()
                if effective_att is None:
                    raise HTTPException(
                        status_code=status.HTTP_409_CONFLICT,
                        detail="Payment is bound to a different Razorpay order ID.",
                    )
            elif not pay_obj.provider_order_id and effective_att is None:
                effective_att = (
                    await self.db.execute(
                        select(PaymentOrderAttempt).where(
                            PaymentOrderAttempt.payment_id == pay_obj.id,
                            PaymentOrderAttempt.provider_order_id == clean_order_id,
                        )
                    )
                ).scalar_one_or_none()
                if effective_att is None:
                    raise HTTPException(
                        status_code=status.HTTP_409_CONFLICT,
                        detail="Order is not associated with the target internal payment record.",
                    )
            return effective_att

        # 2. Pre-read internal Payment without holding row locks across outbound HTTP
        pre_payment, pre_attempt = await _load_payment_by_order(lock=False)
        if not pre_payment:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=f"No payment record matches order {clean_order_id}.",
            )
        pre_attempt = await _assert_ownership_and_binding(pre_payment, pre_attempt)

        settled_states = ("CAPTURED", "PARTIALLY_REFUNDED", "REFUNDED", "PAID", "SETTLED", "SUCCESS")
        if pre_payment.status in settled_states:
            if pre_payment.provider_payment_id and pre_payment.provider_payment_id != clean_payment_id:
                raise HTTPException(
                    status_code=status.HTTP_409_CONFLICT,
                    detail="Payment is already settled with a different gateway payment ID.",
                )
            return {
                "already_processed": True,
                "status": "ALREADY_PROCESSED",
                "payment_status": pre_payment.status,
                "payment_id": pre_payment.provider_payment_id or clean_payment_id,
                "internal_payment_id": pre_payment.id,
                "order_id": pre_payment.provider_order_id or clean_order_id,
                "ride_id": pre_payment.ride_id or clean_ride_id,
                "amount_cents": pre_payment.amount_cents,
                "purpose": pre_payment.purpose,
            }

        expected_verify_amount = int(
            pre_attempt.amount_cents if pre_attempt is not None else pre_payment.amount_cents
        )
        target_payment_id = pre_payment.id

        # 3. Authoritative Server-Side Fetch from Razorpay API (outside DB write lock)
        try:
            gateway_payment = await self.provider.fetch_payment(
                payment_id=clean_payment_id,
                expected_amount_cents=expected_verify_amount,
                expected_order_id=clean_order_id,
            )
        except HTTPException:
            raise
        except Exception as exc:
            logger.error("Failed fetching payment %s from Razorpay gateway: %s", clean_payment_id, exc)
            raise HTTPException(
                status_code=status.HTTP_502_BAD_GATEWAY,
                detail="Failed to verify payment status with gateway provider.",
            )

        # 4. Strict Fail-Closed Gateway Payload Validation (no empty/None/default bypasses)
        if not isinstance(gateway_payment, dict):
            raise HTTPException(
                status_code=status.HTTP_502_BAD_GATEWAY,
                detail="Malformed response received from payment gateway.",
            )

        gw_payment_id = gateway_payment.get("id")
        if not isinstance(gw_payment_id, str) or not gw_payment_id.strip():
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Authoritative gateway response is missing required payment identifier.",
            )
        if gw_payment_id.strip() != clean_payment_id:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Authoritative payment ID mismatch with gateway.",
            )

        gw_order_id = gateway_payment.get("order_id")
        if not isinstance(gw_order_id, str) or not gw_order_id.strip():
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Authoritative gateway response is missing required order identifier.",
            )
        if gw_order_id.strip() != clean_order_id:
            logger.critical(
                "SECURITY ANOMALY: Gateway order mismatch! Expected %s, got %s",
                clean_order_id,
                gw_order_id,
            )
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Authoritative payment does not correspond to expected Razorpay order.",
            )

        gw_account_id = gateway_payment.get("account_id")
        acct_ok, acct_reason = self.provider.validate_merchant_account(gw_account_id)
        if not acct_ok:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=acct_reason)

        raw_gw_currency = gateway_payment.get("currency")
        if not isinstance(raw_gw_currency, str) or not raw_gw_currency.strip():
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Authoritative gateway response is missing currency.",
            )
        gw_currency = raw_gw_currency.strip().upper()
        if gw_currency != "INR":
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Invalid transaction currency: {gw_currency}. Must be INR.",
            )

        gw_status_raw = gateway_payment.get("status")
        if not isinstance(gw_status_raw, str) or not gw_status_raw.strip():
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Authoritative gateway response is missing payment status.",
            )
        gw_status = gw_status_raw.strip().lower()

        gw_amount = gateway_payment.get("amount")
        if not _is_int_paise(gw_amount) or gw_amount <= 0:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Authoritative gateway response contains invalid amount.",
            )
        if gw_amount != expected_verify_amount:
            logger.critical(
                "SECURITY ANOMALY: Payment amount mismatch! Expected %d paise, gateway reports %d paise",
                expected_verify_amount,
                gw_amount,
            )
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Payment amount mismatch between client, order, and gateway.",
            )

        if expected_purpose == "WALLET_TOPUP" and gw_status != "captured":
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Payment status is {gw_status}; expected captured.",
            )

        # 5. Acquire Pessimistic Row Locks in Consistent Order: Payment -> Wallet / Ride -> DriverWallet
        payment = (
            await self.db.execute(select(Payment).where(Payment.id == target_payment_id).with_for_update())
        ).scalar_one_or_none()
        if not payment:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Payment record disappeared.")

        locked_attempt = await _assert_ownership_and_binding(payment, pre_attempt)

        if gw_account_id and not payment.merchant_account_id:
            payment.merchant_account_id = gw_account_id.strip() if isinstance(gw_account_id, str) else None

        expected_locked_amount = int(
            locked_attempt.amount_cents if locked_attempt is not None else payment.amount_cents
        )
        if gw_amount != expected_locked_amount:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Payment amount mismatch between client, order, and locked payment record.",
            )

        if payment.status in settled_states:
            if payment.provider_payment_id and payment.provider_payment_id != clean_payment_id:
                raise HTTPException(
                    status_code=status.HTTP_409_CONFLICT,
                    detail="Payment is already settled with a different gateway payment ID.",
                )
            return {
                "already_processed": True,
                "status": "ALREADY_PROCESSED",
                "payment_status": payment.status,
                "payment_id": payment.provider_payment_id or clean_payment_id,
                "internal_payment_id": target_payment_id,
                "order_id": payment.provider_order_id or clean_order_id,
                "ride_id": payment.ride_id or clean_ride_id,
                "amount_cents": payment.amount_cents,
                "purpose": payment.purpose,
            }

        reused = (
            await self.db.execute(
                select(Payment).where(
                    Payment.provider_payment_id == clean_payment_id,
                    Payment.id != target_payment_id,
                )
            )
        ).scalar_one_or_none()
        if reused:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Gateway payment ID is already bound to another transaction.",
            )

        if gw_status == "authorized":
            try:
                transition_payment_status(payment, "AUTHORIZED", context="verify_checkout_and_finalize_capture")
            except ValueError as err:
                raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=str(err))
            payment.provider_payment_id = clean_payment_id
            await self._commit_or_500("payment authorization persistence")
            return {
                "already_processed": False,
                "status": "AUTHORIZED",
                "payment_status": "AUTHORIZED",
                "payment_id": clean_payment_id,
                "internal_payment_id": target_payment_id,
                "order_id": clean_order_id,
                "ride_id": payment.ride_id or clean_ride_id,
                "amount_cents": payment.amount_cents,
                "purpose": payment.purpose,
            }

        if gw_status != "captured":
            if gw_status == "failed":
                try:
                    transition_payment_status(payment, "FAILED", context="verify_checkout_and_finalize_capture")
                except ValueError as err:
                    raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=str(err))
                payment.provider_payment_id = clean_payment_id
                await self._commit_or_500("payment failed persistence")
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Authoritative gateway payment is not captured. Current status: {gw_status}",
            )

        if locked_attempt is not None and payment.provider_order_id != clean_order_id:
            payment.provider_order_id = clean_order_id
            payment.amount_cents = locked_attempt.amount_cents
        elif not payment.provider_order_id:
            payment.provider_order_id = clean_order_id

        final_amount_cents = payment.amount_cents
        final_purpose = payment.purpose
        final_ride_id = payment.ride_id or clean_ride_id

        await self.finalize_payment_capture(
            payment=payment,
            provider_payment_id=clean_payment_id,
            provider_signature=clean_signature,
            auto_commit=False,
        )

        if final_ride_id and final_purpose == "RIDE_FARE":
            r_res = await self.db.execute(select(Ride).where(Ride.id == final_ride_id).with_for_update())
            ride = r_res.scalar_one_or_none()
            if ride and ride.status in ["IN_PROGRESS", "ACCEPTED", "ARRIVED", "STARTED"]:
                now_comp = datetime.now(timezone.utc)
                ride.status = "COMPLETED"
                ride.completed_at = ride.completed_at or now_comp
                if ride.final_fare_cents is None:
                    ride.final_fare_cents = final_amount_cents or ride.estimated_fare_cents or 0
                if not ride.assigned_driver_id and payment.driver_id:
                    ride.assigned_driver_id = payment.driver_id

        self.db.add(
            PaymentEvent(
                payment_id=target_payment_id,
                event_type="VERIFY_CAPTURED",
                payload_json=json.dumps({"order_id": clean_order_id, "payment_id": clean_payment_id, "purpose": final_purpose}),
            )
        )
        await self._commit_or_500("payment verification and capture")

        return {
            "already_processed": False,
            "status": "CAPTURED",
            "payment_status": "CAPTURED",
            "payment_id": clean_payment_id,
            "internal_payment_id": target_payment_id,
            "order_id": clean_order_id,
            "ride_id": final_ride_id,
            "amount_cents": final_amount_cents,
            "purpose": final_purpose,
        }
