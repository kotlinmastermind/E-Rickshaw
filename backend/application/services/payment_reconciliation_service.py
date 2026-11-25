"""
Automated Payment & Refund Reconciliation Background Worker.
Periodically queries Razorpay for:
1. Orders created without a saved gateway order_id (crash/timeout recovery via receipt)
2. Pending/authorized payments stuck in CREATED, PENDING, or AUTHORIZED
3. Refunds stuck in INITIATED, PENDING, or GATEWAY_PROCESSING
4. Active wallet RefundLiability platform exposure
"""
import asyncio
import json
import logging
from datetime import datetime, timezone, timedelta
from sqlalchemy import select
from backend.infrastructure.database.session import async_session_maker
from backend.domain.models import (
    Payment,
    PaymentOrderAttempt,
    PaymentReconciliationAnomaly,
    PaymentRefund,
    RefundLiability,
)
from backend.application.services.payment_service import (
    PaymentService,
    transition_payment_status,
    validate_payment_transition,
)
from backend.integrations.payments.provider import get_payment_provider

logger = logging.getLogger("reconciliation.worker")


class PaymentReconciliationService:
    @staticmethod
    async def reconcile_lost_orders():
        """Recovers Payment intents that persisted locally without receiving/saving provider_order_id."""
        provider = get_payment_provider()
        cutoff = datetime.now(timezone.utc) - timedelta(minutes=2)

        async with async_session_maker() as session:
            stmt = select(Payment).where(
                Payment.provider_name == "RAZORPAY",
                Payment.provider_order_id.is_(None),
                Payment.status.in_(["CREATED", "PENDING"]),
                Payment.created_at <= cutoff,
            ).limit(20)
            pending_intents = (await session.execute(stmt)).scalars().all()
            recovered_count = 0

            for pay in pending_intents:
                candidate_receipts = []
                if pay.ride_id:
                    candidate_receipts.append(f"ride_{pay.id.replace('-', '')[:24]}_{pay.amount_cents}"[:40])
                    candidate_receipts.append(f"ride_{pay.ride_id.replace('-', '')[:24]}")
                if pay.idempotency_key and pay.idempotency_key.startswith("wtop_pending_"):
                    candidate_receipts.append(pay.idempotency_key.replace("wtop_pending_", "", 1))
                for rcpt in candidate_receipts:
                    try:
                        orders_resp = await provider.fetch_orders_by_receipt(rcpt)
                        orders = (
                            orders_resp.get("items", [])
                            if isinstance(orders_resp, dict)
                            else (orders_resp or [])
                        )
                        match = next(
                            (
                                o
                                for o in orders
                                if o.get("id")
                                and o.get("amount") == pay.amount_cents
                                and str(o.get("currency") or "INR").upper() == "INR"
                            ),
                            None,
                        )
                        if match:
                            pay.provider_order_id = match["id"]
                            session.add(
                                PaymentOrderAttempt(
                                    payment_id=pay.id,
                                    provider_order_id=match["id"],
                                    receipt=rcpt,
                                    amount_cents=pay.amount_cents,
                                    currency="INR",
                                    merchant_account_id=pay.merchant_account_id,
                                    status="RECOVERED",
                                )
                            )
                            await session.commit()
                            recovered_count += 1
                            break
                    except Exception as exc:
                        logger.warning("Lost-order receipt lookup failed for payment %s: %s", pay.id, exc)
            return {"recovered_orders": recovered_count}

    @staticmethod
    async def reconcile_pending_payments():
        """Scans pending orders older than 5 minutes and audits them against Razorpay API."""
        provider = get_payment_provider()
        cutoff = datetime.now(timezone.utc) - timedelta(minutes=5)

        async with async_session_maker() as session:
            stmt = select(Payment).where(
                Payment.provider_name == "RAZORPAY",
                Payment.status.in_(["CREATED", "PENDING", "AUTHORIZED"]),
                Payment.created_at <= cutoff,
            ).limit(20)
            pending_payments = (await session.execute(stmt)).scalars().all()

            for pay in pending_payments:
                if not pay.provider_order_id:
                    continue
                try:
                    items_resp = await provider.fetch_order_payments(pay.provider_order_id)
                    items = (
                        items_resp.get("items", [])
                        if isinstance(items_resp, dict)
                        else (items_resp or [])
                    )
                    if not items:
                        continue

                    captured_payment = next(
                        (
                            p
                            for p in items
                            if str(p.get("status") or "").lower() == "captured"
                            and p.get("amount") == pay.amount_cents
                            and str(p.get("currency") or "").upper() == "INR"
                        ),
                        None,
                    )
                    if captured_payment:
                        acct_ok, acct_reason = provider.validate_merchant_account(captured_payment.get("account_id"))
                        if not acct_ok:
                            session.add(
                                PaymentReconciliationAnomaly(
                                    payment_id=pay.id,
                                    provider_name="RAZORPAY",
                                    provider_payment_id=captured_payment.get("id"),
                                    provider_order_id=pay.provider_order_id,
                                    anomaly_type="MERCHANT_ACCOUNT_MISMATCH",
                                    expected_amount_cents=pay.amount_cents,
                                    raw_gateway_amount_cents=captured_payment.get("amount"),
                                    applied_amount_cents=0,
                                    excess_amount_cents=0,
                                    currency="INR",
                                    status="OPEN",
                                    reason=acct_reason[:500],
                                    details_json=json.dumps({"source": "reconcile_pending_payments"}),
                                )
                            )
                            await session.commit()
                            continue

                        logger.info(
                            "Reconciling captured payment %s for order %s",
                            captured_payment["id"],
                            pay.provider_order_id,
                        )
                        service = PaymentService(session)
                        await service.finalize_payment_capture(
                            payment=pay,
                            provider_payment_id=captured_payment["id"],
                        )
                    elif all(str(p.get("status") or "").lower() == "failed" for p in items):
                        if validate_payment_transition(pay.status, "FAILED"):
                            transition_payment_status(pay, "FAILED", context="reconciliation worker")
                            await session.commit()
                except Exception as e:
                    logger.error("Reconciliation error on order %s: %s", pay.provider_order_id, e)

    @staticmethod
    async def reconcile_pending_refunds():
        """Polls Razorpay for refunds stuck in INITIATED/PENDING/GATEWAY_PROCESSING and settles or reverses."""
        provider = get_payment_provider()
        cutoff = datetime.now(timezone.utc) - timedelta(minutes=2)

        async with async_session_maker() as session:
            stmt = select(PaymentRefund).where(
                PaymentRefund.status.in_(["INITIATED", "PENDING", "GATEWAY_PROCESSING"]),
                PaymentRefund.created_at <= cutoff,
            ).limit(20)
            stuck_refunds = (await session.execute(stmt)).scalars().all()
            settled_count = 0
            failed_count = 0

            for refund in stuck_refunds:
                if not refund.provider_refund_id:
                    continue
                try:
                    gw_refund = await provider.fetch_refund(refund.provider_refund_id)
                    if not gw_refund:
                        continue
                    gw_status = str(gw_refund.get("status") or "").lower()
                    gw_amount = int(gw_refund.get("amount") or refund.amount_cents or 0)

                    locked_pay = (
                        await session.execute(select(Payment).where(Payment.id == refund.payment_id).with_for_update())
                    ).scalar_one_or_none()
                    locked_ref = (
                        await session.execute(select(PaymentRefund).where(PaymentRefund.id == refund.id).with_for_update())
                    ).scalar_one_or_none()
                    if not locked_pay or not locked_ref:
                        continue

                    svc = PaymentService(session)
                    if gw_status == "processed":
                        arn = (gw_refund.get("acquirer_data") or {}).get("arn") or refund.provider_refund_id
                        await svc.apply_confirmed_refund_settlement(
                            payment=locked_pay,
                            refund_obj=locked_ref,
                            refund_id=refund.provider_refund_id,
                            raw_refund_amount=gw_amount,
                            reason=locked_ref.reason or "Reconciled settled refund",
                            arn_reference=arn,
                            settled_status="PROCESSED",
                        )
                        await session.commit()
                        settled_count += 1
                    elif gw_status == "failed":
                        await svc.apply_failed_refund_reversal(
                            payment=locked_pay,
                            refund_obj=locked_ref,
                            refund_id=refund.provider_refund_id,
                            raw_refund_amount=gw_amount,
                            failure_reason="Gateway refund failed during reconciliation.",
                        )
                        await session.commit()
                        failed_count += 1
                except Exception as exc:
                    logger.error("Refund reconciliation failed for %s: %s", refund.id, exc)
            return {"settled_refunds": settled_count, "failed_refunds": failed_count}

    @staticmethod
    async def reconcile_refund_liabilities():
        """Scans active refund liabilities and audits total unrecovered platform debt."""
        async with async_session_maker() as session:
            stmt = select(RefundLiability).where(RefundLiability.status == "ACTIVE")
            res = await session.execute(stmt)
            liabs = res.scalars().all()
            total_unrecovered = sum(l.outstanding_recovery_cents for l in liabs)
            if total_unrecovered > 0:
                logger.warning(
                    "Reconciliation: Found %d active refund liabilities totaling INR %.2f.",
                    len(liabs),
                    total_unrecovered / 100.0,
                )
            return {
                "active_count": len(liabs),
                "total_unrecovered_cents": total_unrecovered,
                "total_unrecovered_rupees": round(total_unrecovered / 100.0, 2),
            }


async def run_payment_reconciliation_loop():
    """Background loop executing payment, order, and refund reconciliation every 300 seconds."""
    while True:
        try:
            await PaymentReconciliationService.reconcile_lost_orders()
            await PaymentReconciliationService.reconcile_pending_payments()
            await PaymentReconciliationService.reconcile_pending_refunds()
            await PaymentReconciliationService.reconcile_refund_liabilities()
        except asyncio.CancelledError:
            break
        except Exception as e:
            logger.exception("Payment reconciliation cycle failure: %s", e)
        await asyncio.sleep(300)