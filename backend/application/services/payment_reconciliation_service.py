"""
Automated Payment Reconciliation Background Worker.
Periodically queries Razorpay for orders stuck in CREATED or PENDING states,
reconciles captured payments into the wallet ledger, and recovers lost callbacks.
"""
import asyncio
import logging
from datetime import datetime, timezone, timedelta
from sqlalchemy import select
from backend.infrastructure.database.session import async_session_maker
from backend.domain.models import Payment, RefundLiability
from backend.application.services.payment_service import (
    PaymentService,
    transition_payment_status,
    validate_payment_transition,
)
from backend.integrations.payments.provider import get_payment_provider

logger = logging.getLogger("reconciliation.worker")

class PaymentReconciliationService:
    @staticmethod
    async def reconcile_pending_payments():
        """Scans pending orders older than 5 minutes and audits them against Razorpay API."""
        provider = get_payment_provider()
        cutoff = datetime.now(timezone.utc) - timedelta(minutes=5)

        async with async_session_maker() as session:
            stmt = select(Payment).where(
                Payment.status.in_(["CREATED", "PENDING", "AUTHORIZED"]),
                Payment.created_at <= cutoff
            ).limit(20)
            res = await session.execute(stmt)
            pending_payments = res.scalars().all()

            for pay in pending_payments:
                if not pay.provider_order_id:
                    continue
                try:
                    # Query Razorpay for payments attached to this order
                    import httpx
                    url = f"https://api.razorpay.com/v1/orders/{pay.provider_order_id}/payments"
                    async with httpx.AsyncClient() as client:
                        resp = await client.get(url, auth=(provider.key_id, provider.key_secret), timeout=10.0)
                        if resp.status_code != 200:
                            continue
                        data = resp.json()
                        items = data.get("items", [])
                        
                        captured_payment = next(
                            (
                                p for p in items
                                if str(p.get("status") or "").lower() == "captured"
                                and p.get("amount") == pay.amount_cents
                                and str(p.get("currency") or "").upper() == "INR"
                            ),
                            None,
                        )
                        if captured_payment:
                            logger.info(f"Reconciling captured payment {captured_payment['id']} for order {pay.provider_order_id}")
                            service = PaymentService(session)
                            await service.finalize_payment_capture(
                                payment=pay,
                                provider_payment_id=captured_payment["id"]
                            )
                        elif all(str(p.get("status") or "").lower() == "failed" for p in items) and len(items) > 0:
                            if validate_payment_transition(pay.status, "FAILED"):
                                transition_payment_status(pay, "FAILED", context="reconciliation worker")
                                await session.commit()
                except Exception as e:
                    logger.error(f"Reconciliation error on order {pay.provider_order_id}: {e}")

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
                    f"Reconciliation: Found {len(liabs)} active refund liabilities totaling ₹{total_unrecovered / 100:.2f}."
                )
            return {
                "active_count": len(liabs),
                "total_unrecovered_cents": total_unrecovered,
                "total_unrecovered_rupees": round(total_unrecovered / 100.0, 2)
            }

async def run_payment_reconciliation_loop():
    """Background loop executing reconciliation every 300 seconds."""
    while True:
        try:
            await PaymentReconciliationService.reconcile_pending_payments()
        except asyncio.CancelledError:
            break
        except Exception as e:
            logger.exception(f"Payment reconciliation cycle failure: {e}")
        await asyncio.sleep(300)