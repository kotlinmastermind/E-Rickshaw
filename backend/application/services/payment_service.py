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
    PaymentReconciliationAnomaly,
    PaymentRefund,
    PaymentWebhookInbox,
    Ride,
)
from backend.integrations.payments.provider import get_payment_provider

logger = logging.getLogger("payments.service")

INBOX_TERMINAL_STATES = frozenset({"PROCESSED", "UNMATCHED_PAYMENT", "RECONCILIATION_REQUIRED"})
INBOX_STALE_PROCESSING_SECONDS = 60

SUPPORTED_WEBHOOK_EVENTS = frozenset({
    "payment.authorized",
    "payment.captured",
    "payment.failed",
    "order.paid",
    "refund.created",
    "refund.processed",
    "refund.failed",
})

VALID_PAYMENT_TRANSITIONS: Dict[str, set[str]] = {
    "CREATED": {"AUTHORIZED", "CAPTURED", "FAILED", "EXPIRED"},
    "PENDING": {"AUTHORIZED", "CAPTURED", "FAILED", "EXPIRED"},
    "AUTHORIZED": {"CAPTURED", "FAILED", "EXPIRED"},
    "CAPTURED": {"REFUND_PENDING", "PARTIALLY_REFUNDED", "REFUNDED"},
    "REFUND_PENDING": {"PARTIALLY_REFUNDED", "REFUNDED", "CAPTURED"},
    "PARTIALLY_REFUNDED": {"PARTIALLY_REFUNDED", "REFUNDED"},
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
    "PENDING_APPROVAL": {"APPROVED_AWAITING_PROVIDER", "REJECTED"},
    "APPROVED_AWAITING_PROVIDER": {"GATEWAY_PROCESSING", "CREDITED", "PROCESSED", "FAILED"},
    "PENDING": {"GATEWAY_PROCESSING", "CREDITED", "PROCESSED", "FAILED", "ANOMALY_EXCESS", "PARTIALLY_APPLIED_EXCESS"},
    "GATEWAY_PROCESSING": {"CREDITED", "PROCESSED", "FAILED", "ANOMALY_EXCESS", "PARTIALLY_APPLIED_EXCESS"},
    # Controlled recovery when gateway subsequently succeeds on the same refund ID
    "FAILED": {"GATEWAY_PROCESSING", "CREDITED", "ANOMALY_EXCESS", "PARTIALLY_APPLIED_EXCESS"},
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
                    fare_cents = payment.amount_cents
                    commission_cents = int(round(fare_cents * 0.15))
                    driver_earnings = fare_cents - commission_cents

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
                        d_wallet.balance_cents += driver_earnings
                        d_wallet.updated_at = datetime.now(timezone.utc)
                        self.db.add(
                            DriverLedgerEntry(
                                id=str(uuid.uuid4()),
                                driver_id=ride.assigned_driver_id,
                                entry_type="CREDIT",
                                category="TRIP_EARNING",
                                amount_cents=driver_earnings,
                                balance_after_cents=d_wallet.balance_cents,
                                reference_id=ride.id,
                                description=f"Net earnings (85%) for online card/UPI ride #{ride.id[:8]}",
                                created_at=datetime.now(timezone.utc),
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

    async def create_payment_intent(self, user_id: str, ride_id: str, idempotency_key: str) -> Dict[str, Any]:
        """
        Creates or retrieves an authentic payment intent for a ride.
        Enforces customer ownership, server-side fare calculation, and idempotency.
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

        stmt = select(Payment).where(Payment.ride_id == ride_id).with_for_update()
        res = await self.db.execute(stmt)
        payment = res.scalar_one_or_none()

        if not payment:
            payment = Payment(
                ride_id=ride_id,
                customer_id=customer_profile_id,
                amount_cents=fare,
                currency="INR",
                status="CREATED",
                payment_method="RAZORPAY",
                provider_name="RAZORPAY",
                idempotency_key=idempotency_key,
            )
            self.db.add(payment)
            await self.db.flush()

        if payment.status in ["CAPTURED", "AUTHORIZED", "PARTIALLY_REFUNDED", "REFUNDED"]:
            return {
                "payment_id": payment.id,
                "status": payment.status,
                "amount_cents": payment.amount_cents,
                "currency": payment.currency,
                "already_settled": True,
            }

        intent = await self.provider.create_payment_intent(
            amount_cents=payment.amount_cents,
            currency=payment.currency,
            idempotency_key=idempotency_key,
        )

        payment.provider_order_id = intent.order_id
        payment.idempotency_key = idempotency_key
        await self.db.commit()

        return {
            "payment_id": payment.id,
            "order_id": intent.order_id,
            "amount_cents": payment.amount_cents,
            "currency": payment.currency,
            "client_token": intent.client_token,
            "provider": intent.provider,
            "key_id": settings.RAZORPAY_KEY_ID,
        }

    # ------------------------------------------------------------------
    # Authoritative Razorpay Webhook Processor (Single Consolidated Path)
    # ------------------------------------------------------------------
    async def process_webhook(self, raw_body: bytes, signature_header: str) -> Dict[str, Any]:
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
        verification = await self.provider.verify_webhook_payload(raw_body, signature_header)
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

        # Reject unsupported event types cleanly without financial mutations
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
        if order_id:
            payment = (
                await self.db.execute(
                    select(Payment).where(Payment.provider_order_id == order_id).with_for_update()
                )
            ).scalar_one_or_none()

        if not payment and provider_payment_id:
            by_pid = (
                await self.db.execute(
                    select(Payment).where(Payment.provider_payment_id == provider_payment_id).with_for_update()
                )
            ).scalar_one_or_none()
            if by_pid:
                if order_id and by_pid.provider_order_id and by_pid.provider_order_id != order_id:
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

        # Validate consistent ownership/binding against matched Payment
        if order_id and payment.provider_order_id and payment.provider_order_id != order_id:
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

        # Look up by authoritative provider_refund_id (with arn_reference fallback for legacy rows)
        existing_refund_stmt = (
            select(PaymentRefund)
            .where(
                or_(
                    PaymentRefund.provider_refund_id == refund_id,
                    PaymentRefund.arn_reference == refund_id,
                    PaymentRefund.id == refund_id,
                )
            )
            .with_for_update()
        )
        existing_refund = (await self.db.execute(existing_refund_stmt)).scalar_one_or_none()
        if existing_refund and existing_refund.payment_id != payment.id:
            reason = f"Gateway refund ID {refund_id} already belongs to payment {existing_refund.payment_id}."
            await self._record_anomaly_and_require_reconciliation(
                inbox=inbox, anomaly_type="REFUND_PAYMENT_MISMATCH", payment=payment, parsed=parsed, reason=reason, now=now
            )
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=reason)

        # 1. Handle refund.failed
        if webhook_event == "refund.failed" or (gateway_status == "failed" and webhook_event != "refund.processed"):
            if existing_refund:
                if existing_refund.status in {"CREDITED", "PROCESSED", "ANOMALY_EXCESS", "PARTIALLY_APPLIED_EXCESS"}:
                    logger.warning(
                        "Rejecting refund.failed transition for refund %s already in %s",
                        existing_refund.id,
                        existing_refund.status,
                    )
                    inbox.status = "PROCESSED"
                    inbox.processed_at = now
                    await self._commit_or_500("refund.failed regression rejection")
                    return {
                        "status": "REJECTED_STATE_TRANSITION",
                        "event": webhook_event,
                        "payment_id": payment.id,
                        "refund_id": refund_id,
                        "message": f"Cannot transition {existing_refund.status} refund to FAILED.",
                    }
                transition_refund_status(existing_refund, "FAILED", context="webhook refund.failed")
                existing_refund.provider_refund_id = refund_id
                existing_refund.raw_gateway_amount_cents = raw_refund_amount
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
                        reason=f"Gateway refund failure ({webhook_event})",
                        status="FAILED",
                        provider_refund_id=refund_id,
                        arn_reference=refund_id,
                    )
                )

            inbox.status = "PROCESSED"
            inbox.processed_at = now
            await self._commit_or_500("webhook refund.failed")
            return {
                "status": "REFUND_FAILED",
                "event": webhook_event,
                "payment_id": payment.id,
                "refund_id": refund_id,
                "message": "Gateway refund failed; no financial clawback applied.",
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

        if existing_refund and existing_refund.status in {
            "CREDITED",
            "PROCESSED",
            "ANOMALY_EXCESS",
            "PARTIALLY_APPLIED_EXCESS",
        }:
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

        # Enforce integer paise refundable ceiling: total credited refunds <= payment.amount_cents
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
            transition_payment_status(payment, target_pay_status, context="webhook refund.processed")

        refund_status = (
            "PARTIALLY_APPLIED_EXCESS"
            if is_anomaly and applied_refund > 0
            else ("ANOMALY_EXCESS" if applied_refund == 0 else "CREDITED")
        )
        anomaly_reason = (
            f"GATEWAY_OVER_REFUND_ANOMALY: Gateway refund {refund_id} of {raw_refund_amount} paise "
            f"exceeds remaining refundable ceiling of {remaining_refundable} paise "
            f"(applied={applied_refund}, excess={excess_anomaly_cents})."
            if is_anomaly
            else f"Authoritative webhook refund ({webhook_event})"
        )

        if existing_refund:
            transition_refund_status(existing_refund, refund_status, context="webhook refund.processed")
            existing_refund.amount_cents = applied_refund
            existing_refund.raw_gateway_amount_cents = raw_refund_amount
            existing_refund.excess_amount_cents = excess_anomaly_cents
            existing_refund.provider_refund_id = refund_id
            existing_refund.arn_reference = existing_refund.arn_reference or refund_id
            existing_refund.is_partial = bool(applied_refund > 0 and payment.status == "PARTIALLY_REFUNDED")
            existing_refund.reason = anomaly_reason[:255]
        else:
            self.db.add(
                PaymentRefund(
                    payment_id=payment.id,
                    ride_id=payment.ride_id,
                    customer_id=payment.customer_id,
                    amount_cents=applied_refund,
                    raw_gateway_amount_cents=raw_refund_amount,
                    excess_amount_cents=excess_anomaly_cents,
                    is_partial=bool(applied_refund > 0 and payment.status == "PARTIALLY_REFUNDED"),
                    reason=anomaly_reason[:255],
                    status=refund_status,
                    provider_refund_id=refund_id,
                    arn_reference=refund_id,
                )
            )

        if is_anomaly and excess_anomaly_cents > 0:
            await self._record_reconciliation_anomaly(
                anomaly_type="EXCESS_REFUND",
                payment=payment,
                inbox=inbox,
                parsed=parsed,
                expected_amount_cents=remaining_refundable,
                raw_gateway_amount_cents=raw_refund_amount,
                applied_amount_cents=applied_refund,
                excess_amount_cents=excess_anomaly_cents,
                reason=anomaly_reason,
            )

        # Clawback ONLY on valid internally applied refund (never on excess_anomaly_cents)
        if getattr(payment, "purpose", None) == "WALLET_TOPUP" and applied_refund > 0:
            topup_user_id = payment.user_id
            if not topup_user_id and payment.customer_id:
                cust_prof = (
                    await self.db.execute(
                        select(CustomerProfile).where(CustomerProfile.id == payment.customer_id)
                    )
                ).scalar_one_or_none()
                if cust_prof:
                    topup_user_id = cust_prof.user_id
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
                event_type="WEBHOOK_REFUND_PROCESSED",
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
        inbox: PaymentWebhookInbox,
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
                webhook_inbox_id=inbox.id,
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
                details_json=json.dumps({"digest": inbox.event_digest, "event": inbox.event_type}),
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

        existing_comm = (
            await self.db.execute(
                select(DriverLedgerEntry).where(
                    DriverLedgerEntry.driver_id == driver_prof.id,
                    DriverLedgerEntry.reference_id == ride.id,
                    DriverLedgerEntry.category == "COMMISSION",
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
            fare_cents = payment.amount_cents
            commission_cents = int(round(fare_cents * 0.15))
            wallet.balance_cents -= commission_cents
            self.db.add(
                DriverLedgerEntry(
                    id=str(uuid.uuid4()),
                    driver_id=driver_prof.id,
                    entry_type="DEBIT",
                    category="COMMISSION",
                    amount_cents=-commission_cents,
                    balance_after_cents=wallet.balance_cents,
                    reference_id=ride.id,
                    description=f"Platform commission (15%) for cash ride #{ride.id[:8]}",
                    created_at=now,
                )
            )

        await self._commit_or_500("cash collection confirmation")

        fare_cents = payment.amount_cents
        commission_cents = int(round(fare_cents * 0.15))
        driver_net_earnings_cents = fare_cents - commission_cents

        return {
            "payment_id": payment.id,
            "status": payment.status,
            "payment_method": "CASH",
            "provider_name": "CASH",
            "cash_collected_cents": fare_cents,
            "driver_net_earnings_cents": driver_net_earnings_cents,
            "commission_receivable_cents": commission_cents,
            "commission_deducted_cents": commission_cents,
            "driver_wallet_balance_cents": wallet.balance_cents,
            "cash_collected": True,
            "amount_cents": payment.amount_cents,
        }
