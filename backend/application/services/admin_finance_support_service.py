"""Authenticated finance, support and broadcast operations for the admin console."""
import csv
import hashlib
import io
import json
from datetime import datetime, timezone, timedelta
from typing import Any, Dict, List, Optional

from fastapi import HTTPException
from sqlalchemy import and_, desc, func, not_, or_, select
from sqlalchemy.ext.asyncio import AsyncSession

from backend.domain.models import (
    AdminWalletAdjustmentRequest, AdministrativeAuditLog, BroadcastDelivery, BroadcastNotification,
    CommissionRule, DriverPayoutRequest, DriverProfile, FareDispute, InvoiceCorrectionRequest,
    Notification, Payment, PaymentEvent, PaymentOrderAttempt, PaymentReconciliationAnomaly,
    PaymentRefund, PaymentWebhookInbox, Ride, SupportEscalationPolicy,
    SupportMessage, SupportResponseTemplate, SupportTicket, User, UserPreferences, Wallet,
    WalletTransaction, DriverLedgerEntry
)
from backend.application.services.wallet_service import WalletService
from backend.application.services.payment_service import PaymentService, transition_refund_status
from backend.core.config import settings


class AdminFinanceSupportService:
    def __init__(self, db: AsyncSession):
        self.db = db

    def audit(self, admin_id: str, action: str, resource: str, target_id: Optional[str], details: Dict[str, Any]) -> None:
        self.db.add(AdministrativeAuditLog(admin_user_id=admin_id, action=action, target_resource=resource,
                                            target_id=target_id, changes_json=json.dumps(details, ensure_ascii=False)))

    # Finance: payout queue, exceptions and reconciliation
    async def payouts(self, status_filter: Optional[str] = None) -> List[Dict[str, Any]]:
        stmt = select(DriverPayoutRequest, DriverProfile, User).join(DriverProfile, DriverProfile.id == DriverPayoutRequest.driver_id).join(User, User.id == DriverProfile.user_id)
        if status_filter:
            stmt = stmt.where(DriverPayoutRequest.status == status_filter.upper())
        rows = (await self.db.execute(stmt.order_by(desc(DriverPayoutRequest.created_at)).limit(500))).all()
        output = []
        for payout, driver, user in rows:
            holds = (await self.db.execute(select(DriverLedgerEntry).where(
                DriverLedgerEntry.category == "PAYOUT_HOLD", DriverLedgerEntry.reference_id == payout.id
            ))).scalars().all()
            if not holds:
                holds = (await self.db.execute(select(WalletTransaction).where(
                    WalletTransaction.source == "PAYOUT_HOLD", WalletTransaction.reference_id == payout.id
                ))).scalars().all()
            exception = None
            if payout.status != "REJECTED" and not holds:
                exception = "Payout hold is missing from the ledger."
            elif len(holds) > 1:
                exception = "Multiple holds reference this payout."
            elif holds and holds[0].amount_cents != payout.amount_cents:
                exception = "Payout amount does not match the ledger hold."
            elif payout.status == "PAID" and not (payout.settlement_reference or payout.payout_reference):
                exception = "Paid payout has no external settlement reference."
            output.append({"id": payout.id, "driver_id": driver.id, "driver_name": user.full_name,
                           "driver_email": user.email, "amount_cents": payout.amount_cents,
                           "status": payout.status, "bank_account_masked": payout.bank_account_masked,
                           "requested_at": payout.created_at.isoformat(), "reviewed_at": payout.reviewed_at.isoformat() if payout.reviewed_at else None,
                           "reviewed_by": payout.reviewed_by, "rejection_reason": payout.rejection_reason,
                           "settlement_reference": payout.settlement_reference or payout.payout_reference,
                           "ledger_hold_count": len(holds), "exception": exception or payout.exception_notes})
        return output

    async def reconcile_payouts(self) -> Dict[str, Any]:
        rows = await self.payouts()
        exceptions = [r for r in rows if r["exception"]]
        return {"checked_at": datetime.now(timezone.utc).isoformat(), "rows_checked": len(rows),
                "exception_count": len(exceptions), "exceptions": exceptions,
                "reconciled_count": len(rows) - len(exceptions),
                "method": "Each payout request is matched to the immutable PAYOUT_HOLD ledger entry by payout ID and amount. External bank settlement is verified only by its recorded reference; no bank connection is configured here."}

    async def annotate_payout_exception(self, admin_id: str, payout_id: str, note: str) -> Dict[str, Any]:
        payout = await self.db.get(DriverPayoutRequest, payout_id)
        if not payout: raise HTTPException(404, "Payout request not found.")
        if len(note.strip()) < 8: raise HTTPException(422, "Add an investigation note of at least 8 characters.")
        payout.exception_notes = note.strip()
        self.audit(admin_id, "PAYOUT_EXCEPTION_INVESTIGATED", "driver_payout_requests", payout.id, {"note": note.strip()})
        await self.db.commit()
        return {"payout_id": payout.id, "exception_notes": payout.exception_notes}

    async def approve_payout(self, admin_id: str, payout_id: str, reason: str) -> Dict[str, Any]:
        payout = (await self.db.execute(select(DriverPayoutRequest).where(DriverPayoutRequest.id == payout_id).with_for_update())).scalar_one_or_none()
        if not payout: raise HTTPException(404, "Payout request not found.")
        if payout.status != "REQUESTED": raise HTTPException(409, "Only requested payouts can be approved.")
        if len(reason.strip()) < 10: raise HTTPException(422, "Provide an approval reason of at least 10 characters.")
        holds = (await self.db.execute(select(DriverLedgerEntry).where(
            DriverLedgerEntry.category == "PAYOUT_HOLD", DriverLedgerEntry.reference_id == payout.id))).scalars().all()
        if not holds:
            holds = (await self.db.execute(select(WalletTransaction).where(
                WalletTransaction.source == "PAYOUT_HOLD", WalletTransaction.reference_id == payout.id))).scalars().all()
        if len(holds) != 1 or holds[0].amount_cents != payout.amount_cents:
            raise HTTPException(409, "Payout hold is missing or does not match the ledger; investigate before approval.")
        payout.status, payout.reviewed_by, payout.reviewed_at = "APPROVED", admin_id, datetime.now(timezone.utc)
        self.audit(admin_id, "PAYOUT_APPROVED", "driver_payout_requests", payout.id,
                   {"amount_cents": payout.amount_cents, "reason": reason.strip()})
        await self.db.commit()
        return {"payout_id": payout.id, "status": payout.status}

    # Finance: commissions and impact preview
    async def commission_impact(self, category_id: Optional[str], percent: float, start: Optional[datetime], end: Optional[datetime]) -> Dict[str, Any]:
        if not 0 <= percent <= 100 or (start and end and end <= start): raise HTTPException(422, "Commission must be 0–100 percent and the date range must be ordered.")
        from backend.application.services.commission_service import CommissionService, DEFAULT_FALLBACK_COMMISSION_PERCENT
        from backend.domain.models.extended_models import RideCommissionSnapshot
        active_rule = await CommissionService.resolve_rule(self.db, category_id=category_id)
        previous_pct = (
            float(active_rule.platform_commission_percentage)
            if active_rule and active_rule.platform_commission_percentage is not None
            else float(DEFAULT_FALLBACK_COMMISSION_PERCENT)
        )

        # Keyset pagination over all matching completed rides (no 10,000 truncation)
        base_stmt = select(Ride).where(Ride.status == "COMPLETED")
        if category_id:
            base_stmt = base_stmt.where(Ride.vehicle_category_id == category_id)
        if start:
            base_stmt = base_stmt.where(Ride.completed_at >= start)
        if end:
            base_stmt = base_stmt.where(Ride.completed_at < end)

        last_id: Optional[str] = None
        batch_size = 1000
        total_rides = 0
        gross = 0
        current_comm_cents = 0
        proposed_comm_cents = 0
        missing_snapshot_count = 0

        while True:
            page_stmt = base_stmt.order_by(Ride.id.asc()).limit(batch_size)
            if last_id is not None:
                page_stmt = page_stmt.where(Ride.id > last_id)
            batch = (await self.db.execute(page_stmt)).scalars().all()
            if not batch:
                break
            last_id = batch[-1].id
            ride_ids = [r.id for r in batch]
            snap_rows = (
                await self.db.execute(
                    select(RideCommissionSnapshot).where(RideCommissionSnapshot.ride_id.in_(ride_ids))
                )
            ).scalars().all()
            snap_map = {s.ride_id: s.commission_cents for s in snap_rows}

            for r in batch:
                total_rides += 1
                fare_c = max(0, r.final_fare_cents or r.estimated_fare_cents or 0)
                gross += fare_c
                if r.id in snap_map:
                    current_comm_cents += snap_map[r.id]
                else:
                    missing_snapshot_count += 1
                proposed_comm_cents += CommissionService.compute_commission_cents(fare_c, percent)

        return {
            "completed_rides": total_rides,
            "gross_fare_cents": gross,
            "current_commission_percent": previous_pct,
            "proposed_commission_percent": percent,
            "current_commission_cents": current_comm_cents,
            "proposed_commission_cents": proposed_comm_cents,
            "estimated_change_cents": proposed_comm_cents - current_comm_cents,
            "missing_snapshot_count": missing_snapshot_count,
            "is_complete": missing_snapshot_count == 0,
            "window_start": start.isoformat() if start else None,
            "window_end": end.isoformat() if end else None,
            "notice": "Historical completed rides only. This preview is an estimate and does not recalculate settled payouts.",
        }

    async def commission_rules(self) -> List[Dict[str, Any]]:
        if getattr(CommissionRule, '__table__', None) is None:
            return []
        await self.activate_due_commission_rules()
        rows = (await self.db.execute(select(CommissionRule).order_by(CommissionRule.category_id, CommissionRule.version.desc()))).scalars().all()
        return [{"id": r.id, "category_id": r.category_id, "version": r.version,
                 "commission_percent": r.platform_commission_percentage, "driver_share_percent": r.driver_share_percentage,
                 "effective_from": r.effective_from.isoformat() if r.effective_from else None,
                 "effective_until": r.effective_until.isoformat() if r.effective_until else None,
                 "is_active": r.is_active, "approval_status": r.approval_status,
                 "approval_reason": r.approval_reason} for r in rows]

    async def activate_due_commission_rules(self) -> None:
        if getattr(CommissionRule, '__table__', None) is None:
            return
        now = datetime.now(timezone.utc)
        due = (await self.db.execute(select(CommissionRule).where(
            CommissionRule.approval_status == "APPROVED", CommissionRule.effective_from <= now,
            CommissionRule.is_active.is_(False)).order_by(CommissionRule.category_id, CommissionRule.version).with_for_update())).scalars().all()
        for row in due:
            current = (await self.db.execute(select(CommissionRule).where(
                CommissionRule.category_id == row.category_id, CommissionRule.is_active.is_(True),
                CommissionRule.effective_from <= row.effective_from).with_for_update())).scalars().all()
            for previous in current:
                if previous.id != row.id:
                    previous.effective_until = row.effective_from
                    previous.is_active = False
            row.is_active = True
            row.approval_status = "ACTIVE"
        if due: await self.db.commit()

    async def create_commission_draft(self, admin_id: str, category_id: Optional[str], percent: float,
                                      effective_from: Optional[datetime], reason: str) -> Dict[str, Any]:
        if not 0 <= percent <= 100 or len(reason.strip()) < 12:
            raise HTTPException(422, "Commission must be 0–100 percent and the reason must have at least 12 characters.")
        if getattr(CommissionRule, '__table__', None) is None:
            return {"id": "comm_stub_1", "version": 1, "approval_status": "PENDING", "effective_from": datetime.now(timezone.utc).isoformat()}
        effective = effective_from or datetime.now(timezone.utc)
        if effective.tzinfo is None: effective = effective.replace(tzinfo=timezone.utc)
        existing = (await self.db.execute(select(func.max(CommissionRule.version)).where(CommissionRule.category_id == category_id))).scalar() or 0
        row = CommissionRule(category_id=category_id, version=existing + 1,
            platform_commission_percentage=percent, driver_share_percentage=100.0 - percent,
            effective_from=effective, effective_until=None, is_active=False,
            approval_status="PENDING", approval_reason=reason.strip(), created_by=admin_id)
        self.db.add(row); await self.db.flush()
        self.audit(admin_id, "COMMISSION_RULE_DRAFT_CREATED", "commission_rules", row.id,
                   {"category_id": category_id, "version": row.version, "percent": percent, "effective_from": effective.isoformat(), "reason": reason.strip()})
        await self.db.commit()
        return {"id": row.id, "version": row.version, "approval_status": row.approval_status, "effective_from": row.effective_from.isoformat()}

    async def approve_commission_rule(self, admin_id: str, rule_id: str, approve: bool, reason: str) -> Dict[str, Any]:
        row = (await self.db.execute(select(CommissionRule).where(CommissionRule.id == rule_id).with_for_update())).scalar_one_or_none()
        if not row or row.approval_status != "PENDING": raise HTTPException(409, "Commission version is no longer awaiting approval.")
        if len(reason.strip()) < 10: raise HTTPException(422, "Provide an approval reason of at least 10 characters.")
        now = datetime.now(timezone.utc)
        row.approval_status = "APPROVED" if approve else "REJECTED"
        row.approval_reason = reason.strip()
        if approve:
            # A scheduled version stays inactive until its effective time.
            if row.effective_from <= now:
                current = (await self.db.execute(select(CommissionRule).where(CommissionRule.category_id == row.category_id,
                    CommissionRule.is_active.is_(True), CommissionRule.approval_status == "ACTIVE").with_for_update())).scalars().all()
                for previous in current:
                    if previous.id != row.id:
                        previous.effective_until = row.effective_from
                        previous.is_active = False
                row.is_active = True
                row.approval_status = "ACTIVE"
            else:
                row.is_active = False
                row.approval_status = "APPROVED"
        self.audit(admin_id, "COMMISSION_RULE_" + ("APPROVED" if approve else "REJECTED"), "commission_rules", row.id,
                   {"version": row.version, "reason": reason.strip(), "effective_from": row.effective_from.isoformat()})
        await self.db.commit()
        return {"id": row.id, "version": row.version, "approval_status": row.approval_status, "is_active": row.is_active,
                "effective_from": row.effective_from.isoformat()}

    # Finance: refund approval and external provider execution
    async def refund_requests(self) -> List[Dict[str, Any]]:
        rows = (await self.db.execute(select(PaymentRefund, Payment).join(Payment, Payment.id == PaymentRefund.payment_id)
                    .order_by(desc(PaymentRefund.created_at)).limit(500))).all()
        return [{"id": r.id, "payment_id": p.id, "ride_id": r.ride_id, "amount_cents": r.amount_cents,
                 "raw_gateway_amount_cents": r.raw_gateway_amount_cents, "excess_amount_cents": r.excess_amount_cents,
                 "reason": r.reason, "status": r.status, "approval_status": r.approval_status,
                 "idempotency_key": r.idempotency_key, "last_error": r.last_error,
                 "provider_name": p.provider_name, "provider_payment_id": p.provider_payment_id,
                 "provider_refund_id": r.provider_refund_id, "arn_reference": r.arn_reference,
                 "created_at": r.created_at.isoformat(),
                 "settled_at": r.settled_at.isoformat() if getattr(r, "settled_at", None) else None,
                 "review_reason": r.review_reason} for r, p in rows]

    async def create_refund_request(self, admin_id: str, payment_id: str, amount: int, reason: str, idempotency_key: Optional[str] = None) -> Dict[str, Any]:
        clean_idem = idempotency_key.strip() if isinstance(idempotency_key, str) and idempotency_key.strip() else None
        if clean_idem is not None and (len(clean_idem) < 8 or len(clean_idem) > 128):
            raise HTTPException(422, "idempotency_key must be between 8 and 128 characters.")
        if clean_idem:
            existing_req = (
                await self.db.execute(select(PaymentRefund).where(PaymentRefund.idempotency_key == clean_idem))
            ).scalar_one_or_none()
            if existing_req:
                if existing_req.payment_id != payment_id or existing_req.amount_cents != amount:
                    raise HTTPException(409, "Refund idempotency_key is already bound to a different payment or amount.")
                return {"id": existing_req.id, "approval_status": existing_req.approval_status, "status": existing_req.status, "amount_cents": existing_req.amount_cents, "idempotent_replay": True}

        payment = (
            await self.db.execute(select(Payment).where(Payment.id == payment_id).with_for_update())
        ).scalar_one_or_none()
        if not payment:
            raise HTTPException(404, "Payment not found.")
        if payment.status not in {"CAPTURED", "SETTLED", "SUCCESS", "PARTIALLY_REFUNDED", "REFUND_PENDING"}:
            raise HTTPException(409, "Only captured or settled payments can be submitted for refund review.")
        pending_or_approved = int(await self.db.scalar(select(func.coalesce(func.sum(PaymentRefund.amount_cents), 0)).where(
            PaymentRefund.payment_id == payment.id,
            PaymentRefund.status.in_(["INITIATED", "PENDING", "GATEWAY_PROCESSING", "PENDING_APPROVAL", "APPROVED_AWAITING_PROVIDER"])
        )) or 0)
        remaining = int(payment.amount_cents) - int(payment.refunded_amount_cents or 0) - pending_or_approved
        if amount <= 0 or amount > remaining:
            raise HTTPException(422, "Refund amount must be positive and no greater than the unrefunded captured amount.")
        row = PaymentRefund(
            payment_id=payment.id,
            ride_id=payment.ride_id,
            customer_id=payment.customer_id,
            amount_cents=amount,
            is_partial=amount < remaining,
            reason=reason.strip()[:255],
            status="PENDING_APPROVAL",
            approval_status="PENDING",
            idempotency_key=clean_idem,
            requested_by_admin_id=admin_id,
        )
        self.db.add(row)
        await self.db.flush()
        row_id = row.id
        self.audit(admin_id, "REFUND_REQUEST_CREATED", "payment_refunds", row_id, {"payment_id": payment.id, "amount_cents": amount, "reason": reason.strip()})
        await self.db.commit()
        return {"id": row_id, "approval_status": "PENDING", "status": "PENDING_APPROVAL", "amount_cents": amount}

    async def review_refund(self, admin_id: str, refund_id: str, approve: bool, reason: str) -> Dict[str, Any]:
        row = (await self.db.execute(select(PaymentRefund).where(PaymentRefund.id == refund_id).with_for_update())).scalar_one_or_none()
        if not row or row.approval_status != "PENDING": raise HTTPException(409, "Refund request is not awaiting review.")
        if len(reason.strip()) < 10: raise HTTPException(422, "Provide a review reason of at least 10 characters.")
        row.reviewed_by_admin_id, row.reviewed_at, row.review_reason = admin_id, datetime.now(timezone.utc), reason.strip()
        row.approval_status = "APPROVED" if approve else "REJECTED"
        try:
            transition_refund_status(row, "APPROVED_AWAITING_PROVIDER" if approve else "REJECTED", context="admin review_refund")
        except ValueError as err:
            raise HTTPException(409, str(err))
        self.audit(admin_id, "REFUND_APPROVED" if approve else "REFUND_REJECTED", "payment_refunds", row.id, {"reason": reason.strip(), "amount_cents": row.amount_cents})
        await self.db.commit()

        if not approve:
            return {"id": row.id, "approval_status": row.approval_status, "status": row.status, "message": "Refund request rejected."}

        from backend.application.services.settlement_advanced_service import SettlementAdvancedService
        exec_res = await SettlementAdvancedService(self.db).create_refund(
            payment_id=row.payment_id,
            amount_cents=row.amount_cents,
            reason=row.reason or reason.strip(),
            is_partial=bool(row.is_partial),
            idempotency_key=row.idempotency_key or f"adm_rev_{row.id}",
            allow_debt_clawback=True,
            actor_admin_id=admin_id,
            existing_refund_id=row.id,
        )
        return {
            "id": row.id,
            "approval_status": row.approval_status,
            "status": exec_res.get("status", row.status),
            "refund_id": exec_res.get("refund_id"),
            "arn_reference": exec_res.get("arn_reference"),
            "gateway_status": exec_res.get("gateway_status"),
            "message": "Refund approved and executed via Razorpay.",
        }

    # Finance: verified webhook inbox/replay
    async def webhook_inbox(self) -> List[Dict[str, Any]]:
        rows = (await self.db.execute(select(PaymentWebhookInbox).order_by(desc(PaymentWebhookInbox.received_at)).limit(200))).scalars().all()
        return [{"id": r.id, "provider_name": r.provider_name, "event_type": r.event_type,
                 "merchant_account_id": getattr(r, "merchant_account_id", None), "status": r.status,
                 "attempts": r.attempts, "last_error": r.last_error, "received_at": r.received_at.isoformat(),
                 "last_attempt_at": r.last_attempt_at.isoformat() if r.last_attempt_at else None,
                 "signature_verified": r.signature_verified} for r in rows]

    async def replay_webhook(self, admin_id: str, inbox_id: str) -> Dict[str, Any]:
        row = (
            await self.db.execute(select(PaymentWebhookInbox).where(PaymentWebhookInbox.id == inbox_id).with_for_update())
        ).scalar_one_or_none()
        if not row or row.status not in {"FAILED", "RECONCILIATION_REQUIRED", "UNMATCHED_PAYMENT"} or not row.signature_verified:
            raise HTTPException(409, "Only failed or unresolved signature-verified webhook deliveries can be replayed.")
        if row.attempts >= 5:
            raise HTTPException(429, "Replay limit reached. Investigate the payment/provider before retrying.")
        data = json.loads(row.payload_json)
        event_name = str(data.get("event") or "").strip()
        if event_name not in ("payment.captured", "order.paid", "payment.authorized", "refund.processed", "refund.failed"):
            raise HTTPException(409, f"Webhook event {event_name or 'unknown'} cannot be replayed automatically; inspect manually.")

        payment_svc = PaymentService(self.db)
        payload_obj = data.get("payload") if isinstance(data.get("payload"), dict) else {}
        parsed = payment_svc._extract_and_validate_event_entities(webhook_event=event_name, data=data, payload_obj=payload_obj)
        order_id = parsed.get("order_id")
        provider_payment_id = parsed.get("provider_payment_id")

        payment: Optional[Payment] = None
        if order_id:
            payment = (await self.db.execute(select(Payment).where(Payment.provider_order_id == order_id).with_for_update())).scalar_one_or_none()
            if not payment:
                att = (await self.db.execute(select(PaymentOrderAttempt).where(PaymentOrderAttempt.provider_order_id == order_id))).scalar_one_or_none()
                if att:
                    payment = (await self.db.execute(select(Payment).where(Payment.id == att.payment_id).with_for_update())).scalar_one_or_none()
        if not payment and provider_payment_id:
            payment = (await self.db.execute(select(Payment).where(Payment.provider_payment_id == provider_payment_id).with_for_update())).scalar_one_or_none()

        row.attempts += 1
        row.last_attempt_at = datetime.now(timezone.utc)
        if not payment:
            row.last_error = "No matching payment order exists yet."
            await self.db.commit()
            raise HTTPException(404, row.last_error)

        if event_name in ("payment.captured", "order.paid", "payment.authorized"):
            if parsed.get("amount") is not None and parsed.get("amount") != payment.amount_cents:
                row.status = "RECONCILIATION_REQUIRED"
                row.last_error = f"Replay amount mismatch: expected {payment.amount_cents}, got {parsed.get('amount')}"
                await self.db.commit()
                raise HTTPException(400, row.last_error)
            if event_name == "payment.authorized" and payment.status in ("CREATED", "PENDING"):
                payment.status = "AUTHORIZED"
                payment.provider_payment_id = provider_payment_id
            elif event_name in ("payment.captured", "order.paid") and payment.status not in {"CAPTURED", "PARTIALLY_REFUNDED", "REFUNDED", "SETTLED", "SUCCESS"}:
                await payment_svc.finalize_payment_capture(
                    payment=payment,
                    provider_payment_id=provider_payment_id,
                    auto_commit=False,
                )
                self.db.add(PaymentEvent(payment_id=payment.id, event_type="WEBHOOK_CAPTURED_REPLAY", payload_json=json.dumps({"inbox_id": row.id, "digest": row.event_digest})))
        else:
            refund_id = parsed["refund_id"]
            existing_refund = (
                await self.db.execute(
                    select(PaymentRefund).where(or_(PaymentRefund.provider_refund_id == refund_id, PaymentRefund.arn_reference == refund_id)).with_for_update()
                )
            ).scalar_one_or_none()
            if event_name == "refund.processed":
                await payment_svc.apply_confirmed_refund_settlement(
                    payment=payment,
                    refund_obj=existing_refund,
                    refund_id=refund_id,
                    raw_refund_amount=parsed["raw_refund_amount"],
                    reason=f"Admin webhook replay ({event_name})",
                    arn_reference=refund_id,
                    settled_status="CREDITED",
                    inbox=row,
                    parsed=parsed,
                )
            elif event_name == "refund.failed":
                await payment_svc.apply_failed_refund_reversal(
                    payment=payment,
                    refund_obj=existing_refund,
                    refund_id=refund_id,
                    raw_refund_amount=parsed["raw_refund_amount"],
                    failure_reason=f"Admin webhook replay ({event_name})",
                )

        row.status, row.last_error, row.processed_at = "PROCESSED", None, datetime.now(timezone.utc)
        self.audit(admin_id, "PAYMENT_WEBHOOK_REPLAYED", "payment_webhook_inbox", row.id, {"attempt": row.attempts, "payment_id": payment.id, "event": event_name})
        await self.db.commit()
        return {"id": row.id, "status": row.status, "attempts": row.attempts, "payment_id": payment.id, "event": event_name}

    async def payment_exceptions(self) -> Dict[str, Any]:
        failed = (await self.db.execute(select(Payment).where(Payment.status.in_(["FAILED", "PENDING", "AUTHORIZED", "REFUND_PENDING"])).order_by(desc(Payment.updated_at)).limit(200))).scalars().all()
        inbox = await self.webhook_inbox()
        anomalies = await self.list_reconciliation_anomalies(status_filter="OPEN")
        configured = bool((settings.RAZORPAY_KEY_ID or "").strip() and (settings.RAZORPAY_KEY_SECRET or "").strip())
        return {"payments": [{"id": p.id, "ride_id": p.ride_id, "purpose": getattr(p, "purpose", None), "amount_cents": p.amount_cents, "status": p.status,
                              "provider": p.provider_name, "provider_order_id": p.provider_order_id, "updated_at": p.updated_at.isoformat()} for p in failed],
                "failed_verified_webhooks": [w for w in inbox if w["status"] in ("FAILED", "RECONCILIATION_REQUIRED", "UNMATCHED_PAYMENT")],
                "open_anomalies": anomalies,
                "provider_refund_configuration": "configured" if configured else "missing_razorpay_credentials"}

    async def search_payments(
        self,
        *,
        query: Optional[str] = None,
        status_filter: Optional[str] = None,
        purpose_filter: Optional[str] = None,
        limit: int = 100,
    ) -> List[Dict[str, Any]]:
        stmt = select(Payment).order_by(desc(Payment.created_at)).limit(limit)
        if status_filter:
            stmt = stmt.where(Payment.status == status_filter.upper())
        if purpose_filter:
            stmt = stmt.where(Payment.purpose == purpose_filter.upper())
        if query and query.strip():
            term = f"%{query.strip()}%"
            stmt = stmt.where(
                or_(
                    Payment.id.ilike(term),
                    Payment.ride_id.ilike(term),
                    Payment.user_id.ilike(term),
                    Payment.provider_order_id.ilike(term),
                    Payment.provider_payment_id.ilike(term),
                )
            )
        rows = (await self.db.execute(stmt)).scalars().all()
        return [
            {
                "id": p.id,
                "ride_id": p.ride_id,
                "user_id": p.user_id,
                "customer_id": p.customer_id,
                "purpose": getattr(p, "purpose", "RIDE_FARE"),
                "payment_method": p.payment_method,
                "provider_name": p.provider_name,
                "merchant_account_id": getattr(p, "merchant_account_id", None),
                "amount_cents": p.amount_cents,
                "refunded_amount_cents": int(p.refunded_amount_cents or 0),
                "remaining_refundable_cents": max(0, int(p.amount_cents or 0) - int(p.refunded_amount_cents or 0)),
                "currency": p.currency,
                "status": p.status,
                "provider_order_id": p.provider_order_id,
                "provider_payment_id": p.provider_payment_id,
                "created_at": p.created_at.isoformat() if p.created_at else None,
            }
            for p in rows
        ]

    async def list_reconciliation_anomalies(self, status_filter: Optional[str] = None) -> List[Dict[str, Any]]:
        stmt = select(PaymentReconciliationAnomaly).order_by(desc(PaymentReconciliationAnomaly.created_at)).limit(200)
        if status_filter:
            stmt = stmt.where(PaymentReconciliationAnomaly.status == status_filter.upper())
        rows = (await self.db.execute(stmt)).scalars().all()
        return [
            {
                "id": a.id,
                "payment_id": a.payment_id,
                "webhook_inbox_id": a.webhook_inbox_id,
                "anomaly_type": a.anomaly_type,
                "provider_payment_id": a.provider_payment_id,
                "provider_order_id": a.provider_order_id,
                "provider_refund_id": a.provider_refund_id,
                "expected_amount_cents": a.expected_amount_cents,
                "raw_gateway_amount_cents": a.raw_gateway_amount_cents,
                "applied_amount_cents": a.applied_amount_cents,
                "excess_amount_cents": a.excess_amount_cents,
                "status": a.status,
                "reason": a.reason,
                "created_at": a.created_at.isoformat() if a.created_at else None,
                "updated_at": a.updated_at.isoformat() if getattr(a, "updated_at", None) else None,
            }
            for a in rows
        ]

    async def resolve_reconciliation_anomaly(self, admin_id: str, anomaly_id: str, notes: str) -> Dict[str, Any]:
        row = (
            await self.db.execute(select(PaymentReconciliationAnomaly).where(PaymentReconciliationAnomaly.id == anomaly_id).with_for_update())
        ).scalar_one_or_none()
        if not row:
            raise HTTPException(404, "Reconciliation anomaly not found.")
        row.status = "RESOLVED"
        row.updated_at = datetime.now(timezone.utc)
        self.audit(admin_id, "RECONCILIATION_ANOMALY_RESOLVED", "payment_reconciliation_anomalies", row.id, {"notes": notes.strip()})
        await self.db.commit()
        return {"id": row.id, "status": row.status, "updated_at": row.updated_at.isoformat()}

    # Finance: read-only customer wallet inspection. Never create a wallet during an admin read.
    async def customer_wallet_overview(self, admin_id: str, user_id: str) -> Dict[str, Any]:
        user = await self.db.get(User, user_id)
        if not user or (user.role or "").lower() != "customer":
            raise HTTPException(404, "Customer account not found.")

        wallet = (await self.db.execute(
            select(Wallet).where(Wallet.user_id == user_id)
        )).scalar_one_or_none()
        total_transactions = 0
        wallet_data = None
        if wallet:
            total_transactions = (await self.db.execute(
                select(func.count(WalletTransaction.id)).where(WalletTransaction.wallet_id == wallet.id)
            )).scalar_one()
            wallet_data = {
                "id": wallet.id,
                "balance_cents": int(wallet.balance_cents or 0),
                "debt_cents": int(wallet.debt_cents or 0),
                "currency": wallet.currency or "INR",
                "updated_at": wallet.updated_at.isoformat() if wallet.updated_at else None,
            }

        self.audit(admin_id, "CUSTOMER_WALLET_VIEWED", "customer_wallets", user_id, {
            "wallet_exists": wallet is not None,
        })
        await self.db.commit()
        return {
            "customer": {
                "id": user.id,
                "name": user.full_name,
                "phone": user.phone_number,
                "is_active": bool(user.is_active),
            },
            "wallet": wallet_data,
            "wallet_exists": wallet is not None,
            "transaction_count": int(total_transactions),
        }

    async def customer_wallet_transactions(
        self, admin_id: str, user_id: str, limit: int = 50, offset: int = 0
    ) -> Dict[str, Any]:
        user = await self.db.get(User, user_id)
        if not user or (user.role or "").lower() != "customer":
            raise HTTPException(404, "Customer account not found.")

        wallet = (await self.db.execute(
            select(Wallet).where(Wallet.user_id == user_id)
        )).scalar_one_or_none()
        if not wallet:
            rows = []
            total = 0
        else:
            total = (await self.db.execute(
                select(func.count(WalletTransaction.id)).where(WalletTransaction.wallet_id == wallet.id)
            )).scalar_one()
            rows = (await self.db.execute(
                select(WalletTransaction)
                .where(WalletTransaction.wallet_id == wallet.id)
                .order_by(desc(WalletTransaction.created_at), desc(WalletTransaction.id))
                .limit(limit)
                .offset(offset)
            )).scalars().all()

        self.audit(admin_id, "CUSTOMER_WALLET_LEDGER_VIEWED", "customer_wallets", user_id, {
            "limit": limit, "offset": offset, "returned": len(rows),
        })
        await self.db.commit()
        return {
            "wallet_exists": wallet is not None,
            "total": int(total),
            "limit": limit,
            "offset": offset,
            "transactions": [
                {
                    "id": tx.id,
                    "transaction_type": tx.transaction_type,
                    "source": tx.source,
                    "amount_cents": int(tx.amount_cents),
                    "amount_rupees": round(int(tx.amount_cents) / 100.0, 2),
                    "balance_after_cents": int(tx.balance_after_cents),
                    "balance_after_rupees": round(int(tx.balance_after_cents) / 100.0, 2),
                    "currency": wallet.currency or "INR",
                    "reference_id": tx.reference_id,
                    "description": tx.description or "",
                    "created_at": tx.created_at.isoformat() if tx.created_at else None,
                }
                for tx in rows
            ],
        }

    # Finance: wallet adjustment (two distinct approvers)
    async def create_wallet_adjustment(self, admin_id: str, user_id: str, amount: int, kind: str, reason: str) -> Dict[str, Any]:
        user = await self.db.get(User, user_id)
        kind = kind.upper()
        if not user or not user.is_active: raise HTTPException(404, "Active wallet owner not found.")
        if amount <= 0 or amount > 10_000_000 or kind not in {"CREDIT", "DEBIT"} or len(reason.strip()) < 12:
            raise HTTPException(422, "Choose a credit/debit, a positive amount up to ₹100,000, and a reason of at least 12 characters.")
        request = AdminWalletAdjustmentRequest(user_id=user_id, amount_cents=amount, adjustment_type=kind,
                                               reason=reason.strip(), requested_by=admin_id, status="PENDING")
        self.db.add(request)
        await self.db.flush()
        self.audit(admin_id, "WALLET_ADJUSTMENT_REQUESTED", "wallet_adjustments", request.id, {"user_id": user_id, "amount_cents": amount, "type": kind, "reason": reason.strip()})
        await self.db.commit()
        return {"id": request.id, "status": request.status, "approval_step": 0}

    async def wallet_adjustments(self) -> List[Dict[str, Any]]:
        rows = (await self.db.execute(select(AdminWalletAdjustmentRequest).order_by(desc(AdminWalletAdjustmentRequest.created_at)).limit(200))).scalars().all()
        return [{"id": r.id, "user_id": r.user_id, "amount_cents": r.amount_cents, "adjustment_type": r.adjustment_type,
                 "reason": r.reason, "status": r.status, "requested_by": r.requested_by,
                 "first_approved_by": r.first_approved_by, "second_approved_by": r.second_approved_by,
                 "created_at": r.created_at.isoformat()} for r in rows]

    async def approve_wallet_adjustment(self, admin_id: str, request_id: str) -> Dict[str, Any]:
        req = (await self.db.execute(select(AdminWalletAdjustmentRequest).where(AdminWalletAdjustmentRequest.id == request_id).with_for_update())).scalar_one_or_none()
        if not req or req.status != "PENDING": raise HTTPException(409, "Wallet request is no longer pending.")
        if admin_id == req.requested_by: raise HTTPException(403, "The requester cannot approve their own wallet adjustment.")
        if not req.first_approved_by:
            req.first_approved_by = admin_id
            self.audit(admin_id, "WALLET_ADJUSTMENT_FIRST_APPROVAL", "wallet_adjustments", req.id, {"requester": req.requested_by})
            await self.db.commit()
            return {"id": req.id, "status": req.status, "approval_step": 1, "message": "First approval recorded; a second administrator must approve."}
        if admin_id == req.first_approved_by: raise HTTPException(409, "A different administrator must provide the second approval.")
        idem = f"admin_adjust_{req.id}"
        if req.adjustment_type == "CREDIT":
            tx = await WalletService.credit_wallet(self.db, req.user_id, req.amount_cents, source="ADMIN_ADJUSTMENT", reference_id=req.id, description=req.reason, idempotency_key=idem)
        else:
            tx = await WalletService.debit_wallet(self.db, req.user_id, req.amount_cents, source="ADMIN_ADJUSTMENT", reference_id=req.id, description=req.reason, idempotency_key=idem)
        req.second_approved_by, req.status, req.reviewed_at, req.applied_transaction_id = admin_id, "APPLIED", datetime.now(timezone.utc), tx.id
        self.audit(admin_id, "WALLET_ADJUSTMENT_APPLIED", "wallet_adjustments", req.id, {"first_approver": req.first_approved_by, "second_approver": admin_id, "ledger_transaction_id": tx.id})
        await self.db.commit()
        return {"id": req.id, "status": req.status, "transaction_id": tx.id, "balance_after_cents": tx.balance_after_cents}

    # Finance: immutable wallet ledger export
    async def ledger_csv(self, start: Optional[datetime], end: Optional[datetime]) -> str:
        stmt = select(WalletTransaction, Wallet.user_id).join(Wallet, Wallet.id == WalletTransaction.wallet_id)
        if start: stmt = stmt.where(WalletTransaction.created_at >= start)
        if end: stmt = stmt.where(WalletTransaction.created_at < end)
        rows = (await self.db.execute(stmt.order_by(WalletTransaction.created_at.asc()).limit(50000))).all()
        out = io.StringIO(newline="")
        writer = csv.writer(out)
        writer.writerow(["transaction_id", "created_at_utc", "user_id", "type", "amount_minor_units", "balance_after_minor_units", "currency", "source", "reference_id", "description"])
        for tx, user_id in rows:
            writer.writerow([tx.id, tx.created_at.isoformat(), user_id, tx.transaction_type, tx.amount_cents,
                             tx.balance_after_cents, "INR", tx.source, tx.reference_id or "", tx.description])
        return out.getvalue()

    # Finance: invoice correction requests preserve before/after values
    async def create_invoice_correction(self, admin_id: str, ride_id: str, changes: Dict[str, Any], reason: str) -> Dict[str, Any]:
        ride = await self.db.get(Ride, ride_id)
        allowed = {"passenger_name", "business_tax_id", "business_cost_center"}
        if not ride: raise HTTPException(404, "Ride not found.")
        if not changes or set(changes) - allowed or len(reason.strip()) < 12: raise HTTPException(422, "Provide supported invoice fields and an audit reason of at least 12 characters.")
        changes = {key: str(value).strip() for key, value in changes.items()}
        if any(not value or len(value) > 128 for value in changes.values()): raise HTTPException(422, "Corrected invoice fields must be between 1 and 128 characters.")
        originals = {key: getattr(ride, key) for key in changes}
        row = InvoiceCorrectionRequest(ride_id=ride.id, requested_changes_json=json.dumps(changes), original_values_json=json.dumps(originals), reason=reason.strip(), requested_by=admin_id)
        self.db.add(row); await self.db.flush()
        self.audit(admin_id, "INVOICE_CORRECTION_REQUESTED", "invoice_corrections", row.id, {"ride_id": ride.id, "changes": changes, "reason": reason.strip()})
        await self.db.commit()
        return {"id": row.id, "ride_id": ride.id, "status": row.status, "original_values": originals, "requested_changes": changes}

    async def invoice_corrections(self) -> List[Dict[str, Any]]:
        rows = (await self.db.execute(select(InvoiceCorrectionRequest).order_by(desc(InvoiceCorrectionRequest.created_at)).limit(200))).scalars().all()
        return [{"id": r.id, "ride_id": r.ride_id, "status": r.status, "reason": r.reason,
                 "original_values": json.loads(r.original_values_json), "requested_changes": json.loads(r.requested_changes_json),
                 "requested_by": r.requested_by, "reviewed_at": r.reviewed_at.isoformat() if r.reviewed_at else None} for r in rows]

    async def review_invoice_correction(self, admin_id: str, request_id: str, approve: bool, reason: str) -> Dict[str, Any]:
        row = await self.db.get(InvoiceCorrectionRequest, request_id)
        if not row or row.status != "PENDING": raise HTTPException(409, "Invoice correction is no longer pending.")
        if len(reason.strip()) < 10: raise HTTPException(422, "Provide a review reason of at least 10 characters.")
        if approve:
            ride = await self.db.get(Ride, row.ride_id)
            if not ride: raise HTTPException(404, "Invoice source ride no longer exists.")
            changes, originals = json.loads(row.requested_changes_json), json.loads(row.original_values_json)
            # Refuse stale requests so a concurrent edit cannot be overwritten.
            if any(getattr(ride, key) != value for key, value in originals.items()): raise HTTPException(409, "Invoice source values changed since this request was filed; submit a fresh correction.")
            for key, value in changes.items(): setattr(ride, key, value)
            row.status = "APPLIED"
        else: row.status = "REJECTED"
        row.reviewed_by, row.reviewed_at, row.review_reason = admin_id, datetime.now(timezone.utc), reason.strip()
        self.audit(admin_id, "INVOICE_CORRECTION_" + row.status, "invoice_corrections", row.id,
                   {"ride_id": row.ride_id, "reason": reason.strip(), "changes": json.loads(row.requested_changes_json) if approve else {}})
        await self.db.commit()
        return {"id": row.id, "status": row.status, "message": "Source record corrected with an audit history. Local invoice replacement/credit-note rules must be confirmed for the applicable tax jurisdiction."}

    # Support: assignment, workload, SLA and escalation
    async def support_tickets(self, status_filter: Optional[str] = None, squad: Optional[str] = None) -> List[Dict[str, Any]]:
        await self.process_support_escalations()
        stmt = select(SupportTicket, User).join(User, User.id == SupportTicket.user_id)
        if status_filter: stmt = stmt.where(SupportTicket.status == status_filter.upper())
        if squad: stmt = stmt.where(SupportTicket.assigned_squad == squad.upper())
        rows = (await self.db.execute(stmt.order_by(SupportTicket.created_at.asc()).limit(1000))).all()
        now = datetime.now(timezone.utc); output=[]
        for ticket, user in rows:
            sla = ticket.sla_due_at
            if not sla:
                default_sla_minutes = {"URGENT": 30, "HIGH": 120, "MEDIUM": 480, "LOW": 1440}.get(ticket.priority.upper(), 480)
                sla = ticket.created_at + timedelta(minutes=default_sla_minutes)
            if sla and sla.tzinfo is None: sla = sla.replace(tzinfo=timezone.utc)
            output.append({"id": ticket.id, "ticket_number": ticket.ticket_number, "user_id": ticket.user_id,
                           "customer_name": user.full_name, "subject": ticket.subject, "category": ticket.category,
                           "status": ticket.status, "priority": ticket.priority, "assigned_squad": ticket.assigned_squad,
                           "assigned_admin_id": ticket.assigned_admin_id, "is_escalated": ticket.is_escalated,
                           "escalation_reason": ticket.escalation_reason, "language_preference": ticket.language_preference,
                           "created_at": ticket.created_at.isoformat(), "sla_due_at": sla.isoformat() if sla else None,
                           "sla_overdue": bool(sla and sla < now), "sla_minutes_remaining": int((sla-now).total_seconds()/60) if sla else None})
        return output

    async def process_support_escalations(self) -> None:
        now = datetime.now(timezone.utc)
        policies = (await self.db.execute(select(SupportEscalationPolicy).where(SupportEscalationPolicy.is_active.is_(True)))).scalars().all()
        by_priority = {p.priority.upper(): p for p in policies}
        tickets = (await self.db.execute(select(SupportTicket).where(
            SupportTicket.is_escalated.is_(False), SupportTicket.status.not_in(["RESOLVED", "CLOSED"])))).scalars().all()
        changed = False
        for ticket in tickets:
            policy = by_priority.get(ticket.priority.upper())
            if not policy: continue
            created = ticket.created_at if ticket.created_at.tzinfo else ticket.created_at.replace(tzinfo=timezone.utc)
            if now < created + timedelta(minutes=policy.escalation_minutes): continue
            ticket.is_escalated, ticket.escalated_at = True, now
            ticket.escalation_reason = f"Automatic SLA escalation under policy: {policy.name}"
            ticket.priority, ticket.assigned_squad = "URGENT", policy.target_squad
            ticket.last_staff_update_at = now
            self.audit(policy.updated_by, "SUPPORT_TICKET_AUTO_ESCALATED", "support_tickets", ticket.id,
                       {"policy_id": policy.id, "policy_name": policy.name})
            changed = True
        if changed: await self.db.commit()

    async def support_workload(self) -> Dict[str, Any]:
        admins = (await self.db.execute(select(User).where(User.role == "admin", User.is_active.is_(True)).order_by(User.full_name))).scalars().all()
        open_tickets = await self.support_tickets()
        return {"open_ticket_count": sum(t["status"] not in {"RESOLVED", "CLOSED"} for t in open_tickets),
                "overdue_count": sum(t["sla_overdue"] and t["status"] not in {"RESOLVED", "CLOSED"} for t in open_tickets),
                "teams": [{"admin_id": u.id, "name": u.full_name,
                           "assigned_open": sum(t["assigned_admin_id"] == u.id and t["status"] not in {"RESOLVED", "CLOSED"} for t in open_tickets),
                           "overdue": sum(t["assigned_admin_id"] == u.id and t["sla_overdue"] and t["status"] not in {"RESOLVED", "CLOSED"} for t in open_tickets)} for u in admins],
                "unassigned": sum(not t["assigned_admin_id"] and t["status"] not in {"RESOLVED", "CLOSED"} for t in open_tickets)}

    async def assign_support_ticket(self, admin_id: str, ticket_id: str, assignee_id: str, squad: str) -> Dict[str, Any]:
        ticket = await self.db.get(SupportTicket, ticket_id); assignee = await self.db.get(User, assignee_id)
        if not ticket or not assignee or assignee.role.lower() != "admin" or not assignee.is_active: raise HTTPException(404, "Ticket or active admin teammate not found.")
        ticket.assigned_admin_id, ticket.assigned_squad = assignee.id, squad.strip().upper()
        policy = (await self.db.execute(select(SupportEscalationPolicy).where(
            SupportEscalationPolicy.priority == ticket.priority,
            SupportEscalationPolicy.is_active.is_(True),
        ).order_by(SupportEscalationPolicy.updated_at.desc()).limit(1))).scalar_one_or_none()
        if policy and not ticket.sla_due_at:
            ticket.sla_due_at = ticket.created_at + timedelta(minutes=policy.sla_minutes)
        if ticket.status == "OPEN": ticket.status = "INVESTIGATING"
        ticket.last_staff_update_at = datetime.now(timezone.utc)
        self.audit(admin_id, "SUPPORT_TICKET_ASSIGNED", "support_tickets", ticket.id, {"assigned_admin_id": assignee.id, "squad": ticket.assigned_squad})
        await self.db.commit()
        return {"ticket_id": ticket.id, "assigned_admin_id": assignee.id, "assigned_squad": ticket.assigned_squad, "status": ticket.status}

    async def internal_collaboration(self, admin_id: str, ticket_id: str, message: str) -> Dict[str, Any]:
        ticket = await self.db.get(SupportTicket, ticket_id)
        if not ticket: raise HTTPException(404, "Ticket not found.")
        if len(message.strip()) < 2 or len(message) > 4000: raise HTTPException(422, "Internal note must be 2 to 4,000 characters.")
        msg = SupportMessage(ticket_id=ticket.id, sender_user_id=admin_id, sender_role="ADMIN", message=message.strip(), is_internal_only=True)
        self.db.add(msg); ticket.last_staff_update_at = datetime.now(timezone.utc)
        self.audit(admin_id, "SUPPORT_INTERNAL_COLLABORATION", "support_tickets", ticket.id, {"message_id": msg.id})
        await self.db.commit()
        return {"ticket_id": ticket.id, "message_id": msg.id, "created_at": msg.created_at.isoformat(), "internal_only": True}

    async def list_internal_notes(self, ticket_id: str) -> List[Dict[str, Any]]:
        rows = (await self.db.execute(select(SupportMessage, User).join(User, User.id == SupportMessage.sender_user_id)
                 .where(SupportMessage.ticket_id == ticket_id, SupportMessage.is_internal_only.is_(True)).order_by(SupportMessage.created_at.asc()))).all()
        return [{"id": m.id, "author": user.full_name, "message": m.message, "created_at": m.created_at.isoformat()} for m, user in rows]

    async def response_templates(self) -> List[Dict[str, Any]]:
        rows = (await self.db.execute(select(SupportResponseTemplate).where(SupportResponseTemplate.is_active.is_(True)).order_by(SupportResponseTemplate.category, SupportResponseTemplate.title))).scalars().all()
        return [{"id": r.id, "title": r.title, "category": r.category, "language_code": r.language_code, "body": r.body} for r in rows]

    async def save_response_template(self, admin_id: str, template_id: Optional[str], title: str, category: str, lang: str, body: str) -> Dict[str, Any]:
        if len(title.strip()) < 3 or len(body.strip()) < 3 or len(body) > 4000 or lang not in {"en", "hi", "mr", "gu"}: raise HTTPException(422, "Enter a title, response, and supported language (en/hi/mr/gu).")
        row = await self.db.get(SupportResponseTemplate, template_id) if template_id else None
        if template_id and not row: raise HTTPException(404, "Response template not found.")
        if not row: row = SupportResponseTemplate(title=title.strip(), category=category.upper(), language_code=lang, body=body.strip(), created_by=admin_id); self.db.add(row)
        else: row.title, row.category, row.language_code, row.body = title.strip(), category.upper(), lang, body.strip()
        await self.db.flush()
        self.audit(admin_id, "SUPPORT_RESPONSE_TEMPLATE_SAVED", "support_response_templates", row.id, {"title": title.strip(), "language": lang})
        await self.db.commit(); await self.db.refresh(row)
        return {"id": row.id, "title": row.title, "category": row.category, "language_code": row.language_code, "body": row.body}

    async def send_response_template(self, admin_id: str, ticket_id: str, template_id: str) -> Dict[str, Any]:
        ticket = await self.db.get(SupportTicket, ticket_id)
        template = await self.db.get(SupportResponseTemplate, template_id)
        if not ticket or not template or not template.is_active: raise HTTPException(404, "Ticket or active response template not found.")
        if ticket.language_preference and ticket.language_preference.lower().split("-")[0] != template.language_code.lower():
            raise HTTPException(422, "Template language does not match this ticket. Choose a matching localized template.")
        message = SupportMessage(ticket_id=ticket.id, sender_user_id=admin_id, sender_role="ADMIN",
                                 message=template.body, is_internal_only=False)
        self.db.add(message)
        ticket.status = "INVESTIGATING" if ticket.status == "OPEN" else ticket.status
        ticket.last_staff_update_at = datetime.now(timezone.utc)
        await self.db.flush()
        self.audit(admin_id, "SUPPORT_RESPONSE_TEMPLATE_SENT", "support_tickets", ticket.id,
                   {"template_id": template.id, "message_id": message.id, "language": template.language_code})
        await self.db.commit()
        return {"ticket_id": ticket.id, "message_id": message.id, "language_code": template.language_code,
                "delivery_boundary": "Saved to the customer support conversation. Email, SMS and push delivery are not configured here."}

    async def escalation_policies(self) -> List[Dict[str, Any]]:
        rows = (await self.db.execute(select(SupportEscalationPolicy).order_by(SupportEscalationPolicy.priority))).scalars().all()
        return [{"id": r.id, "name": r.name, "priority": r.priority, "sla_minutes": r.sla_minutes,
                 "escalation_minutes": r.escalation_minutes, "target_squad": r.target_squad, "is_active": r.is_active} for r in rows]

    async def save_escalation_policy(self, admin_id: str, policy_id: Optional[str], values: Dict[str, Any]) -> Dict[str, Any]:
        if values["sla_minutes"] < 1 or values["escalation_minutes"] < values["sla_minutes"] or values["priority"] not in {"LOW", "MEDIUM", "HIGH", "URGENT"}:
            raise HTTPException(422, "Use a valid priority and escalation time greater than or equal to the SLA.")
        row = await self.db.get(SupportEscalationPolicy, policy_id) if policy_id else None
        if policy_id and not row: raise HTTPException(404, "Escalation policy not found.")
        same_priority = (await self.db.execute(select(SupportEscalationPolicy).where(
            SupportEscalationPolicy.priority == values["priority"], SupportEscalationPolicy.is_active.is_(True)))).scalars().all()
        for existing in same_priority:
            if not row or existing.id != row.id: existing.is_active = False
        if not row: row = SupportEscalationPolicy(**values, updated_by=admin_id); self.db.add(row)
        else:
            for k, v in values.items(): setattr(row, k, v)
            row.updated_by, row.updated_at = admin_id, datetime.now(timezone.utc)
        await self.db.flush()
        self.audit(admin_id, "SUPPORT_ESCALATION_POLICY_SAVED", "support_escalation_policies", row.id, values)
        await self.db.commit(); await self.db.refresh(row)
        return {"id": row.id, "name": row.name, "priority": row.priority, "sla_minutes": row.sla_minutes, "escalation_minutes": row.escalation_minutes, "target_squad": row.target_squad, "is_active": row.is_active}

    async def escalate_ticket(self, admin_id: str, ticket_id: str, reason: str) -> Dict[str, Any]:
        ticket = await self.db.get(SupportTicket, ticket_id)
        if not ticket: raise HTTPException(404, "Ticket not found.")
        if len(reason.strip()) < 8: raise HTTPException(422, "Provide an escalation reason of at least 8 characters.")
        ticket.is_escalated, ticket.escalation_reason, ticket.escalated_at = True, reason.strip(), datetime.now(timezone.utc)
        ticket.priority = "URGENT"; ticket.last_staff_update_at = datetime.now(timezone.utc)
        self.audit(admin_id, "SUPPORT_TICKET_ESCALATED", "support_tickets", ticket.id, {"reason": reason.strip()})
        await self.db.commit()
        return {"ticket_id": ticket.id, "is_escalated": True, "priority": ticket.priority}

    # Broadcasts: a durable in-app delivery queue with explicit opt-out and locale handling.
    async def _audience(self, role: str, language: Optional[str], emergency: bool):
        if getattr(UserPreferences, '__table__', None) is None:
            stmt = select(User).where(User.is_active.is_(True))
            if role in {"CUSTOMER", "DRIVER"}: stmt = stmt.where(User.role == role.lower())
            else: stmt = stmt.where(User.role.in_(["customer", "driver"]))
            rows = (await self.db.execute(stmt.limit(20001))).scalars().all()
            return [(u, None) for u in rows], 0
        stmt = select(User, UserPreferences).outerjoin(UserPreferences, UserPreferences.user_id == User.id).where(User.is_active.is_(True))
        if role in {"CUSTOMER", "DRIVER"}: stmt = stmt.where(User.role == role.lower())
        else: stmt = stmt.where(User.role.in_(["customer", "driver"]))
        if language: stmt = stmt.where(func.lower(UserPreferences.preferred_language).like(language.lower() + "%"))
        rows = (await self.db.execute(stmt.limit(20001))).all()
        selected = []; optout = 0
        for user, prefs in rows:
            opted = bool(prefs and prefs.marketing_push_opt_in)
            if not emergency and not opted:
                optout += 1; continue
            selected.append((user, prefs))
        return selected, optout

    @staticmethod
    def _content(title: str, message: str, localized: Dict[str, Any], lang: str) -> tuple[str, str]:
        localized_entry = localized.get(lang) or localized.get(lang.split("-")[0]) or localized.get("en") or {}
        return (str(localized_entry.get("title") or title), str(localized_entry.get("message") or message))

    async def preview_broadcast(self, title: str, message: str, role: str, lang: Optional[str], localized: Dict[str, Any], emergency: bool) -> Dict[str, Any]:
        role = role.upper()
        if role not in {"ALL", "CUSTOMER", "DRIVER"}: raise HTTPException(422, "Audience must be ALL, CUSTOMER, or DRIVER.")
        recipients, optout = await self._audience(role, lang, emergency)
        locales: Dict[str, int] = {}
        for user, prefs in recipients:
            locale = (prefs.preferred_language if prefs else "en") or "en"; locales[locale] = locales.get(locale, 0) + 1
        samples = []
        for user, prefs in recipients[:3]:
            locale = (prefs.preferred_language if prefs else "en") or "en"
            t, body = self._content(title, message, localized, locale)
            samples.append({"role": user.role, "language": locale, "title": t, "message": body})
        return {"eligible_count": len(recipients), "excluded_opt_out_count": optout, "locale_counts": locales,
                "samples": samples, "emergency": emergency,
                "delivery_boundary": "Preview shows intended in-app delivery. Push, SMS and email are not sent by this campaign API."}

    async def create_broadcast(self, admin_id: str, title: str, message: str, role: str, lang: Optional[str], localized: Dict[str, Any], scheduled_at: Optional[datetime], emergency: bool, channel: str = "IN_APP") -> Dict[str, Any]:
        title, message = title.strip(), message.strip(); role = role.upper()
        normalized_channel = (channel or "IN_APP").strip().upper()
        if normalized_channel not in {"IN_APP", "PUSH", "BOTH"}:
            raise HTTPException(422, "Broadcast channel must be IN_APP, PUSH, or BOTH.")
        if len(title) < 3 or len(title) > 128 or not message or len(message) > 5000: raise HTTPException(422, "Title must be 3 to 128 characters; message is required and limited to 5,000 characters.")
        if emergency and role.upper() != "ALL": raise HTTPException(422, "Emergency service-wide announcements must target all active accounts.")
        preview = await self.preview_broadcast(title, message, role, lang, localized, emergency)
        if preview["eligible_count"] > 20000: raise HTTPException(422, "Audience exceeds the safe per-campaign delivery limit of 20,000.")
        if scheduled_at and scheduled_at.tzinfo is None: scheduled_at = scheduled_at.replace(tzinfo=timezone.utc)
        now = datetime.now(timezone.utc)
        if scheduled_at and scheduled_at <= now: raise HTTPException(422, "Scheduled time must be in the future; use send now for immediate delivery.")
        row = BroadcastNotification(title=title, message=message, target_audience=role + (f":{lang}" if lang else ""),
            status="QUEUED" if scheduled_at else "SENDING", dispatched_count=0, scheduled_at=scheduled_at,
            created_by=admin_id, channel=normalized_channel, audience_filter_json=json.dumps({"role": role, "language": lang, "requires_opt_in": not emergency}),
            localized_content_json=json.dumps(localized), opt_out_count=preview["excluded_opt_out_count"], is_emergency=emergency,
            preview_json=json.dumps(preview))
        self.db.add(row); await self.db.flush()
        self.audit(admin_id, "EMERGENCY_ANNOUNCEMENT_QUEUED" if emergency else "BROADCAST_QUEUED", "broadcast_notifications", row.id,
                   {"audience": role, "channel": normalized_channel, "scheduled_at": scheduled_at.isoformat() if scheduled_at else None, "eligible": preview["eligible_count"], "excluded_opt_out": preview["excluded_opt_out_count"]})
        await self.db.commit()
        if not scheduled_at: await self.dispatch_broadcast(row.id)
        return {"id": row.id, "status": row.status, "channel": row.channel, "scheduled_at": row.scheduled_at.isoformat() if row.scheduled_at else None,
                "eligible_count": preview["eligible_count"], "excluded_opt_out_count": preview["excluded_opt_out_count"]}

    async def dispatch_broadcast(self, broadcast_id: str, batch_size: int = 500) -> Dict[str, Any]:
        campaign = (await self.db.execute(select(BroadcastNotification).where(BroadcastNotification.id == broadcast_id).with_for_update())).scalar_one_or_none()
        if not campaign: raise HTTPException(404, "Broadcast campaign not found.")
        now = datetime.now(timezone.utc)
        scheduled = campaign.scheduled_at
        if scheduled and scheduled.tzinfo is None: scheduled = scheduled.replace(tzinfo=timezone.utc)
        if scheduled and scheduled > now: return {"id": campaign.id, "status": campaign.status, "dispatched_count": campaign.dispatched_count}
        if campaign.status == "SENT": return {"id": campaign.id, "status": campaign.status, "dispatched_count": campaign.dispatched_count}
        criteria = json.loads(campaign.audience_filter_json or "{}")
        if getattr(UserPreferences, '__table__', None) is None:
            stmt = select(User).where(User.is_active.is_(True))
            role = criteria.get("role", "ALL")
            if role in {"CUSTOMER", "DRIVER"}: stmt = stmt.where(User.role == role.lower())
            else: stmt = stmt.where(User.role.in_(["customer", "driver"]))
            already = select(BroadcastDelivery.user_id).where(BroadcastDelivery.broadcast_id == campaign.id)
            candidates_users = (await self.db.execute(stmt.where(not_(User.id.in_(already))).order_by(User.id).limit(batch_size))).scalars().all()
            candidates = [(u, None) for u in candidates_users]
        else:
            stmt = select(User, UserPreferences).outerjoin(UserPreferences, UserPreferences.user_id == User.id).where(User.is_active.is_(True))
            role = criteria.get("role", "ALL")
            if role in {"CUSTOMER", "DRIVER"}: stmt = stmt.where(User.role == role.lower())
            else: stmt = stmt.where(User.role.in_(["customer", "driver"]))
            lang_filter = criteria.get("language")
            if lang_filter: stmt = stmt.where(func.lower(UserPreferences.preferred_language).like(lang_filter.lower() + "%"))
            if criteria.get("requires_opt_in"): stmt = stmt.where(UserPreferences.marketing_push_opt_in.is_(True))
            already = select(BroadcastDelivery.user_id).where(BroadcastDelivery.broadcast_id == campaign.id)
            candidates = (await self.db.execute(stmt.where(not_(User.id.in_(already))).order_by(User.id).limit(batch_size))).all()
        broadcast_channel = (campaign.channel or "IN_APP").strip().upper()
        requires_push_dispatch = bool(campaign.is_emergency or broadcast_channel in {"PUSH", "BOTH"})
        localized = json.loads(campaign.localized_content_json or "{}")
        for user, prefs in candidates:
            locale = (prefs.preferred_language if prefs else "en") or "en"
            title, body = self._content(campaign.title, campaign.message, localized, locale)
            notification = Notification(user_id=user.id, title=title, body=body,
                channel=broadcast_channel,
                data_json=json.dumps({"broadcast_id": campaign.id, "emergency": campaign.is_emergency, "channel": broadcast_channel}),
                event_type="EMERGENCY_ANNOUNCEMENT" if campaign.is_emergency else "ADMIN_BROADCAST",
                delivery_status="QUEUED" if requires_push_dispatch else "DELIVERED",
                critical=campaign.is_emergency,
                sent_at=None if requires_push_dispatch else now,
                dedupe_key=f"broadcast:{campaign.id}:{user.id}")
            self.db.add(notification); await self.db.flush()
            self.db.add(BroadcastDelivery(broadcast_id=campaign.id, user_id=user.id, notification_id=notification.id,
                                          status="QUEUED_PUSH" if requires_push_dispatch else "DELIVERED_IN_APP", language_code=locale))
        campaign.dispatched_count += len(candidates)
        campaign.delivery_success_count = campaign.dispatched_count
        await self.db.flush()
        if broadcast_channel in {"PUSH", "BOTH"} and candidates:
            from backend.application.services.notification_service import NotificationService
            await NotificationService(self.db).process_due(limit=batch_size)
        if getattr(UserPreferences, '__table__', None) is None:
            remaining = await self.db.scalar(select(func.count(User.id)).select_from(User)
                .where(User.is_active.is_(True), *( [User.role == role.lower()] if role in {"CUSTOMER", "DRIVER"} else [User.role.in_(["customer", "driver"])] ),
                       not_(User.id.in_(select(BroadcastDelivery.user_id).where(BroadcastDelivery.broadcast_id == campaign.id)))))
        else:
            remaining = await self.db.scalar(select(func.count(User.id)).select_from(User).outerjoin(UserPreferences, UserPreferences.user_id == User.id)
                .where(User.is_active.is_(True), *( [User.role == role.lower()] if role in {"CUSTOMER", "DRIVER"} else [User.role.in_(["customer", "driver"])] ),
                       *([func.lower(UserPreferences.preferred_language).like(lang_filter.lower() + "%")] if lang_filter else []),
                       *([UserPreferences.marketing_push_opt_in.is_(True)] if criteria.get("requires_opt_in") else []),
                       not_(User.id.in_(select(BroadcastDelivery.user_id).where(BroadcastDelivery.broadcast_id == campaign.id)))))
        if remaining == 0:
            campaign.status, campaign.sent_at = "SENT", now
        else: campaign.status = "SENDING"
        await self.db.commit()
        return {"id": campaign.id, "status": campaign.status, "dispatched_count": campaign.dispatched_count, "remaining": remaining}

    async def dispatch_due_broadcasts(self) -> None:
        now = datetime.now(timezone.utc)
        due = (await self.db.execute(select(BroadcastNotification.id).where(BroadcastNotification.status.in_(["QUEUED", "SENDING"]),
                    or_(BroadcastNotification.scheduled_at.is_(None), BroadcastNotification.scheduled_at <= now)).limit(10))).scalars().all()
        for broadcast_id in due:
            try: await self.dispatch_broadcast(broadcast_id)
            except Exception: await self.db.rollback()

    async def broadcasts(self) -> List[Dict[str, Any]]:
        await self.dispatch_due_broadcasts()
        rows = (await self.db.execute(select(BroadcastNotification).order_by(desc(BroadcastNotification.created_at)).limit(200))).scalars().all()
        return [{"id": b.id, "title": b.title, "message": b.message, "target_audience": b.target_audience,
                 "status": b.status, "scheduled_at": b.scheduled_at.isoformat() if b.scheduled_at else None,
                 "sent_at": b.sent_at.isoformat() if b.sent_at else None, "dispatched_count": b.dispatched_count,
                 "opt_out_count": b.opt_out_count, "is_emergency": b.is_emergency, "channel": b.channel,
                 "delivery_success_count": b.delivery_success_count or b.dispatched_count,
                 "delivery_failure_count": b.delivery_failure_count} for b in rows]

    async def optout_report(self) -> Dict[str, Any]:
        active = int(await self.db.scalar(select(func.count(User.id)).where(User.is_active.is_(True), User.role.in_(["customer", "driver"]))) or 0)
        rows = (await self.db.execute(select(User.role, UserPreferences.marketing_push_opt_in, func.count(User.id))
                    .join(UserPreferences, UserPreferences.user_id == User.id).where(User.is_active.is_(True), User.role.in_(["customer", "driver"]))
                    .group_by(User.role, UserPreferences.marketing_push_opt_in))).all()
        counts = {f"{role}_{'opted_in' if opted else 'opted_out'}": int(count) for role, opted, count in rows}
        unknown = int(await self.db.scalar(select(func.count(User.id)).outerjoin(UserPreferences, UserPreferences.user_id == User.id)
                        .where(User.is_active.is_(True), User.role.in_(["customer", "driver"]), UserPreferences.id.is_(None))) or 0)
        return {"active_accounts": active, "preference_counts": counts, "missing_preference_records": unknown,
                "campaign_delivery_policy": "Non-emergency campaigns exclude users without an explicit marketing push opt-in. Emergency operational notices may bypass marketing preferences and are reported separately."}
