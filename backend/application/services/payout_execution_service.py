"""Authoritative driver payout execution, state-machine, destination decryption, and webhook reconciliation service."""
import hashlib
import json
import logging
import os
import uuid
from datetime import datetime, timezone
from typing import Any, Dict, Optional

import httpx
from fastapi import HTTPException, status
from sqlalchemy import select, update
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from backend.core.config import settings
from backend.domain.models import (
    AdministrativeAuditLog,
    DriverCommissionSettlement,
    DriverLedgerEntry,
    DriverPayoutAccount,
    DriverPayoutRequest,
    DriverWallet,
    PaymentWebhookInbox,
    User,
)
from backend.infrastructure.security.payout_encryption import decrypt_payout_destination
from backend.integrations.payments.provider import get_payment_provider

logger = logging.getLogger("payout.execution")

ALLOWED_PAYOUT_TRANSITIONS = {
    "REQUESTED": {"APPROVED", "REJECTED"},
    "APPROVED": {"AWAITING_MANUAL_TRANSFER", "PROCESSING", "FAILED", "REJECTED"},
    "AWAITING_MANUAL_TRANSFER": {"PAID", "FAILED"},
    "PROCESSING": {"PAID", "FAILED"},
    "FAILED": {"APPROVED"},
    "PAID": set(),
    "REJECTED": set(),
}

TERMINAL_PAYOUT_STATUSES = {"PAID", "REJECTED"}
ACTIVE_TRANSFER_ELIGIBLE_STATUSES = {"APPROVED", "AWAITING_MANUAL_TRANSFER", "PROCESSING"}


def _sanitize_provider_payload(payload: Optional[Dict[str, Any]]) -> str:
    """Persist only safe status/reference metadata; strip any destination fields."""
    if not isinstance(payload, dict):
        return "{}"
    allowed_keys = ("id", "entity", "status", "mode", "purpose", "utr", "reference_id", "failure_reason", "created_at")
    sanitized = {k: payload[k] for k in allowed_keys if k in payload and payload[k] is not None}
    return json.dumps(sanitized, ensure_ascii=False)


class PayoutExecutionService:
    """Narrowly scoped service owning payout destination decryption and settlement transitions."""

    @staticmethod
    def get_execution_mode() -> str:
        configured = (getattr(settings, "PAYOUT_EXECUTION_MODE", "MANUAL_TRANSFER") or "MANUAL_TRANSFER").strip().upper()
        x_acct = (getattr(settings, "RAZORPAYX_ACCOUNT_NUMBER", None) or "").strip()
        if configured == "RAZORPAYX" and x_acct and (settings.RAZORPAY_KEY_ID or "").strip() and (settings.RAZORPAY_KEY_SECRET or "").strip():
            return "RAZORPAYX"
        return "MANUAL_TRANSFER"

    @staticmethod
    async def _require_authorized_admin(session: AsyncSession, admin_user_id: str) -> User:
        admin = (
            await session.execute(select(User).where(User.id == admin_user_id))
        ).scalar_one_or_none()
        if not admin or not admin.is_active or (admin.role or "").lower() != "admin":
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Admin authorization required for payout execution.")
        return admin

    @staticmethod
    async def retrieve_destination_for_authorized_transfer(
        session: AsyncSession,
        *,
        payout_request_id: str,
        admin_user_id: str,
    ) -> Dict[str, Any]:
        """
        Narrowly scoped retrieval that decrypts a payout destination ONLY immediately before
        an authorized transfer execution, after verifying admin authorization, payout state,
        driver ownership, verified status, and primary status.
        """
        await PayoutExecutionService._require_authorized_admin(session, admin_user_id)

        payout = (
            await session.execute(
                select(DriverPayoutRequest)
                .where(DriverPayoutRequest.id == payout_request_id)
                .with_for_update()
                .execution_options(populate_existing=True)
            )
        ).scalar_one_or_none()
        if not payout:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Payout request not found.")
        if payout.status not in ACTIVE_TRANSFER_ELIGIBLE_STATUSES:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail=f"Payout request in status {payout.status} is not eligible for destination retrieval.",
            )
        if not payout.payout_account_id:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Legacy payout request has no linked verified payout account.",
            )

        account = (
            await session.execute(
                select(DriverPayoutAccount)
                .where(DriverPayoutAccount.id == payout.payout_account_id)
                .with_for_update()
            )
        ).scalar_one_or_none()
        if not account:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Linked payout account not found.")
        if account.driver_id != payout.driver_id:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Cross-driver payout destination access denied.")
        if account.verification_status != "VERIFIED":
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail=f"Payout destination is not verified (status: {account.verification_status}).",
            )
        if not account.is_primary:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Payout destination is no longer the driver's active primary account.",
            )

        encrypted_val = account.account_number_encrypted if account.account_type == "BANK_ACCOUNT" else account.upi_vpa_encrypted
        if not encrypted_val:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Encrypted payout destination missing; ask driver to re-register.",
            )
        try:
            decrypted_destination = decrypt_payout_destination(encrypted_val)
        except RuntimeError as exc:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Payout destination decryption unavailable; verify encryption key configuration.",
            ) from exc

        session.add(AdministrativeAuditLog(
            admin_user_id=admin_user_id,
            action="PAYOUT_DESTINATION_ACCESSED_FOR_TRANSFER",
            target_resource="driver_payout_requests",
            target_id=payout.id,
            changes_json=json.dumps({
                "payout_account_id": account.id,
                "account_type": account.account_type,
                "account_number_mask": account.account_number_mask,
                "execution_mode": PayoutExecutionService.get_execution_mode(),
            }),
        ))
        await session.flush()

        return {
            "payout_request_id": payout.id,
            "payout_account_id": account.id,
            "driver_id": payout.driver_id,
            "account_type": account.account_type,
            "bank_name": account.bank_name,
            "ifsc_code": account.ifsc_code,
            "beneficiary_name": account.beneficiary_name,
            "account_number": decrypted_destination if account.account_type == "BANK_ACCOUNT" else None,
            "upi_vpa": decrypted_destination if account.account_type == "UPI" else None,
            "masked_destination": account.account_number_mask,
        }

    @staticmethod
    async def _transition_status_atomic(
        session: AsyncSession,
        *,
        payout: DriverPayoutRequest,
        expected_status: str,
        target_status: str,
        updates: Dict[str, Any],
    ) -> DriverPayoutRequest:
        allowed = ALLOWED_PAYOUT_TRANSITIONS.get(expected_status, set())
        if target_status not in allowed:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Illegal payout status transition: {expected_status} -> {target_status}",
            )
        values = {"status": target_status, **updates}
        res = await session.execute(
            update(DriverPayoutRequest)
            .where(
                DriverPayoutRequest.id == payout.id,
                DriverPayoutRequest.status == expected_status,
            )
            .values(**values)
        )
        if res.rowcount != 1:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail=f"Concurrent payout status modification detected (expected {expected_status}).",
            )
        await session.refresh(payout)
        return payout

    @staticmethod
    async def _reverse_payout_hold_once(
        session: AsyncSession,
        *,
        payout: DriverPayoutRequest,
        reason: str,
        now: datetime,
    ) -> bool:
        """Reverse the payout hold to DriverWallet exactly once for the current hold cycle. Preserves commission settlement."""
        rev_ref = payout.id if (payout.retry_count or 0) == 0 else f"{payout.id}:retry:{payout.retry_count}"
        existing_rev = (
            await session.execute(
                select(DriverLedgerEntry).where(
                    DriverLedgerEntry.driver_id == payout.driver_id,
                    DriverLedgerEntry.category == "PAYOUT_REVERSAL",
                    DriverLedgerEntry.reference_id == rev_ref,
                )
            )
        ).scalar_one_or_none()
        if existing_rev:
            return False

        d_wallet = (
            await session.execute(
                select(DriverWallet)
                .where(DriverWallet.driver_id == payout.driver_id)
                .with_for_update()
                .execution_options(populate_existing=True)
            )
        ).scalar_one_or_none()
        if not d_wallet:
            raise HTTPException(status_code=500, detail="Driver wallet missing during payout hold reversal.")

        upd = await session.execute(
            update(DriverWallet)
            .where(DriverWallet.driver_id == payout.driver_id)
            .values(balance_cents=DriverWallet.balance_cents + payout.amount_cents, updated_at=now)
            .returning(DriverWallet.balance_cents)
        )
        new_bal = upd.scalar_one()
        await session.refresh(d_wallet)

        session.add(DriverLedgerEntry(
            id=str(uuid.uuid4()),
            driver_id=payout.driver_id,
            entry_type="CREDIT",
            category="PAYOUT_REVERSAL",
            amount_cents=payout.amount_cents,
            balance_after_cents=new_bal,
            reference_id=rev_ref,
            description=f"Refund for failed/rejected payout: {reason[:180]}",
            created_at=now,
        ))
        await session.flush()
        return True

    @staticmethod
    async def initiate_manual_transfer(
        session: AsyncSession,
        *,
        payout_id: str,
        admin_user_id: str,
        settlement_reference: str,
        evidence_notes: Optional[str] = None,
    ) -> DriverPayoutRequest:
        """
        Record manual bank transfer UTR/reference & evidence, transitioning APPROVED -> AWAITING_MANUAL_TRANSFER.
        A separate authorized confirmation step is required before marking PAID.
        """
        clean_ref = (settlement_reference or "").strip()
        if len(clean_ref) < 8 or clean_ref.lower() in {"placeholder", "test_utr", "utr12345"}:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Valid external banking transfer reference (UTR/RRN, min 8 chars) is required.",
            )

        await PayoutExecutionService._require_authorized_admin(session, admin_user_id)

        payout = (
            await session.execute(
                select(DriverPayoutRequest)
                .where(DriverPayoutRequest.id == payout_id)
                .with_for_update()
                .execution_options(populate_existing=True)
            )
        ).scalar_one_or_none()
        if not payout:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Payout request not found.")
        if payout.status != "APPROVED":
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Manual transfer can only be recorded for APPROVED payouts (current: {payout.status}).",
            )

        dup = (
            await session.execute(
                select(DriverPayoutRequest).where(
                    DriverPayoutRequest.settlement_reference == clean_ref,
                    DriverPayoutRequest.id != payout.id,
                )
            )
        ).scalar_one_or_none()
        if dup:
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=f"Transfer reference {clean_ref} is already used.")

        # Validate & decrypt destination strictly inside execution service (verifies ownership/verified/primary/key)
        dest = await PayoutExecutionService.retrieve_destination_for_authorized_transfer(
            session, payout_request_id=payout.id, admin_user_id=admin_user_id
        )

        now = datetime.now(timezone.utc)
        metadata = {
            "mode": "MANUAL_TRANSFER",
            "masked_destination": dest["masked_destination"],
            "evidence_notes": (evidence_notes or "").strip()[:240] or None,
        }
        try:
            async with session.begin_nested():
                payout = await PayoutExecutionService._transition_status_atomic(
                    session,
                    payout=payout,
                    expected_status="APPROVED",
                    target_status="AWAITING_MANUAL_TRANSFER",
                    updates={
                        "execution_mode": "MANUAL_TRANSFER",
                        "settlement_reference": clean_ref,
                        "payout_reference": clean_ref,
                        "manual_transfer_initiated_by": admin_user_id,
                        "manual_transfer_initiated_at": now,
                        "provider_metadata_json": json.dumps(metadata, ensure_ascii=False),
                        "updated_at": now,
                    },
                )
        except IntegrityError as exc:
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Transfer reference already assigned.") from exc

        session.add(AdministrativeAuditLog(
            admin_user_id=admin_user_id,
            action="PAYOUT_MANUAL_TRANSFER_RECORDED",
            target_resource="driver_payout_requests",
            target_id=payout.id,
            changes_json=json.dumps({
                "settlement_reference": clean_ref,
                "execution_mode": "MANUAL_TRANSFER",
                "amount_cents": payout.amount_cents,
                "masked_destination": dest["masked_destination"],
                "initiated_at": now.isoformat(),
            }),
        ))
        await session.flush()
        return payout

    @staticmethod
    async def confirm_manual_transfer(
        session: AsyncSession,
        *,
        payout_id: str,
        admin_user_id: str,
        confirmation_notes: Optional[str] = None,
    ) -> DriverPayoutRequest:
        """Separate authorized admin confirmation transitioning AWAITING_MANUAL_TRANSFER -> PAID."""
        await PayoutExecutionService._require_authorized_admin(session, admin_user_id)

        payout = (
            await session.execute(
                select(DriverPayoutRequest)
                .where(DriverPayoutRequest.id == payout_id)
                .with_for_update()
                .execution_options(populate_existing=True)
            )
        ).scalar_one_or_none()
        if not payout:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Payout request not found.")
        if payout.status != "AWAITING_MANUAL_TRANSFER":
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Manual transfer confirmation requires AWAITING_MANUAL_TRANSFER status (current: {payout.status}).",
            )
        if not (payout.settlement_reference or "").strip():
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Cannot confirm transfer without recorded UTR/reference.")

        now = datetime.now(timezone.utc)
        payout = await PayoutExecutionService._transition_status_atomic(
            session,
            payout=payout,
            expected_status="AWAITING_MANUAL_TRANSFER",
            target_status="PAID",
            updates={
                "confirmed_by": admin_user_id,
                "confirmed_at": now,
                "paid_at": now,
                "reviewed_by": admin_user_id,
                "reviewed_at": now,
                "updated_at": now,
            },
        )
        session.add(AdministrativeAuditLog(
            admin_user_id=admin_user_id,
            action="PAYOUT_MANUAL_TRANSFER_CONFIRMED",
            target_resource="driver_payout_requests",
            target_id=payout.id,
            changes_json=json.dumps({
                "settlement_reference": payout.settlement_reference,
                "confirmed_by": admin_user_id,
                "confirmed_at": now.isoformat(),
                "confirmation_notes": (confirmation_notes or "").strip()[:240] or None,
                "amount_cents": payout.amount_cents,
            }),
        ))
        await session.flush()
        return payout

    @staticmethod
    async def execute_provider_payout(
        session: AsyncSession,
        *,
        payout_id: str,
        admin_user_id: str,
    ) -> DriverPayoutRequest:
        """
        Execute payout via configured RazorpayX payout provider (APPROVED -> PROCESSING).
        Never marks PAID on acceptance; waits for webhook or explicit reconciliation.
        """
        await PayoutExecutionService._require_authorized_admin(session, admin_user_id)
        if PayoutExecutionService.get_execution_mode() != "RAZORPAYX":
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="No automated payout provider is configured; use the MANUAL_TRANSFER workflow.",
            )

        payout = (
            await session.execute(
                select(DriverPayoutRequest)
                .where(DriverPayoutRequest.id == payout_id)
                .with_for_update()
                .execution_options(populate_existing=True)
            )
        ).scalar_one_or_none()
        if not payout:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Payout request not found.")
        if payout.status != "APPROVED":
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Provider execution requires APPROVED status (current: {payout.status}).",
            )

        # Reconcile uncertain prior attempt before issuing a duplicate provider transfer
        prior_meta = json.loads(payout.provider_metadata_json or "{}") if payout.provider_metadata_json else {}
        if prior_meta.get("uncertain_outcome") and payout.provider_idempotency_key:
            reconciled = await PayoutExecutionService.reconcile_provider_payout(
                session, payout_id=payout.id, admin_user_id=admin_user_id
            )
            if reconciled.status in {"PROCESSING", "PAID", "FAILED"}:
                return reconciled

        dest = await PayoutExecutionService.retrieve_destination_for_authorized_transfer(
            session, payout_request_id=payout.id, admin_user_id=admin_user_id
        )

        idem_key = f"payout_{payout.id}_attempt_{payout.retry_count or 0}"
        now = datetime.now(timezone.utc)
        x_acct = (getattr(settings, "RAZORPAYX_ACCOUNT_NUMBER", None) or "").strip()

        fund_account: Dict[str, Any] = {
            "account_type": "vpa" if dest["account_type"] == "UPI" else "bank_account",
            "contact": {"name": dest["beneficiary_name"], "type": "vendor", "reference_id": dest["driver_id"]},
        }
        if dest["account_type"] == "UPI":
            fund_account["vpa"] = {"address": dest["upi_vpa"]}
        else:
            fund_account["bank_account"] = {
                "name": dest["beneficiary_name"],
                "ifsc": dest["ifsc_code"],
                "account_number": dest["account_number"],
            }

        provider_payload = {
            "account_number": x_acct,
            "amount": int(payout.amount_cents),
            "currency": "INR",
            "mode": "UPI" if dest["account_type"] == "UPI" else "IMPS",
            "purpose": "payout",
            "fund_account": fund_account,
            "queue_if_low_balance": False,
            "reference_id": payout.id,
        }

        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                resp = await client.post(
                    "https://api.razorpay.com/v1/payouts",
                    json=provider_payload,
                    auth=(settings.RAZORPAY_KEY_ID or "", settings.RAZORPAY_KEY_SECRET or ""),
                    headers={"X-Payout-Idempotency": idem_key},
                )
        except httpx.TimeoutException as exc:
            payout.execution_mode = "RAZORPAYX"
            payout.provider_name = "RAZORPAYX"
            payout.provider_idempotency_key = idem_key
            payout.provider_metadata_json = json.dumps({"uncertain_outcome": True, "error": "provider_timeout"})
            payout.updated_at = now
            session.add(AdministrativeAuditLog(
                admin_user_id=admin_user_id,
                action="PAYOUT_PROVIDER_TIMEOUT_UNCERTAIN",
                target_resource="driver_payout_requests",
                target_id=payout.id,
                changes_json=json.dumps({"provider_idempotency_key": idem_key}),
            ))
            await session.flush()
            raise HTTPException(
                status_code=status.HTTP_504_GATEWAY_TIMEOUT,
                detail="Payout provider timed out; reconcile outcome before retrying.",
            ) from exc
        except Exception as exc:
            payout.execution_mode = "RAZORPAYX"
            payout.provider_name = "RAZORPAYX"
            payout.provider_idempotency_key = idem_key
            payout.provider_metadata_json = json.dumps({"uncertain_outcome": True, "error": "provider_transport_error"})
            payout.updated_at = now
            await session.flush()
            raise HTTPException(
                status_code=status.HTTP_502_BAD_GATEWAY,
                detail="Payout provider transport error; reconcile before retrying.",
            ) from exc

        if resp.status_code in (200, 201):
            body = resp.json()
            provider_payout_id = body.get("id")
            if not provider_payout_id:
                raise HTTPException(status_code=502, detail="Provider response missing payout id.")
            payout = await PayoutExecutionService._transition_status_atomic(
                session,
                payout=payout,
                expected_status="APPROVED",
                target_status="PROCESSING",
                updates={
                    "execution_mode": "RAZORPAYX",
                    "provider_name": "RAZORPAYX",
                    "provider_payout_id": provider_payout_id,
                    "provider_idempotency_key": idem_key,
                    "provider_metadata_json": _sanitize_provider_payload(body),
                    "updated_at": now,
                },
            )
            session.add(AdministrativeAuditLog(
                admin_user_id=admin_user_id,
                action="PAYOUT_PROVIDER_INITIATED",
                target_resource="driver_payout_requests",
                target_id=payout.id,
                changes_json=json.dumps({
                    "provider_name": "RAZORPAYX",
                    "provider_payout_id": provider_payout_id,
                    "provider_idempotency_key": idem_key,
                    "amount_cents": payout.amount_cents,
                }),
            ))
            await session.flush()
            return payout

        if 400 <= resp.status_code < 500:
            reason = f"Provider rejected payout ({resp.status_code})"
            payout = await PayoutExecutionService.fail_payout(
                session, payout_id=payout.id, actor_user_id=admin_user_id, failure_reason=reason
            )
            return payout

        payout.execution_mode = "RAZORPAYX"
        payout.provider_name = "RAZORPAYX"
        payout.provider_idempotency_key = idem_key
        payout.provider_metadata_json = json.dumps({"uncertain_outcome": True, "http_status": resp.status_code})
        payout.updated_at = now
        await session.flush()
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=f"Provider returned upstream error {resp.status_code}; reconcile before retrying.",
        )

    @staticmethod
    async def reconcile_provider_payout(
        session: AsyncSession,
        *,
        payout_id: str,
        admin_user_id: str,
    ) -> DriverPayoutRequest:
        """Query provider for an uncertain or processing payout and apply authoritative state transition."""
        await PayoutExecutionService._require_authorized_admin(session, admin_user_id)
        payout = (
            await session.execute(
                select(DriverPayoutRequest)
                .where(DriverPayoutRequest.id == payout_id)
                .with_for_update()
                .execution_options(populate_existing=True)
            )
        ).scalar_one_or_none()
        if not payout:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Payout request not found.")
        if payout.status in TERMINAL_PAYOUT_STATUSES:
            return payout

        async with httpx.AsyncClient(timeout=10.0) as client:
            if payout.provider_payout_id:
                resp = await client.get(
                    f"https://api.razorpay.com/v1/payouts/{payout.provider_payout_id}",
                    auth=(settings.RAZORPAY_KEY_ID or "", settings.RAZORPAY_KEY_SECRET or ""),
                )
                item = resp.json() if resp.status_code == 200 else None
            else:
                x_acct = (getattr(settings, "RAZORPAYX_ACCOUNT_NUMBER", None) or "").strip()
                resp = await client.get(
                    "https://api.razorpay.com/v1/payouts",
                    params={"account_number": x_acct, "reference_id": payout.id},
                    auth=(settings.RAZORPAY_KEY_ID or "", settings.RAZORPAY_KEY_SECRET or ""),
                )
                items = (resp.json() or {}).get("items", []) if resp.status_code == 200 else []
                item = items[0] if items else None

        if not item:
            return payout

        prov_status = (item.get("status") or "").lower()
        now = datetime.now(timezone.utc)
        if prov_status == "processed":
            utr = (item.get("utr") or item.get("id") or "").strip()
            return await PayoutExecutionService._transition_status_atomic(
                session,
                payout=payout,
                expected_status=payout.status,
                target_status="PAID",
                updates={
                    "provider_payout_id": item.get("id") or payout.provider_payout_id,
                    "settlement_reference": utr or payout.settlement_reference,
                    "payout_reference": utr or payout.payout_reference,
                    "provider_metadata_json": _sanitize_provider_payload(item),
                    "confirmed_by": admin_user_id,
                    "confirmed_at": now,
                    "paid_at": now,
                    "updated_at": now,
                },
            )
        if prov_status in {"rejected", "reversed", "cancelled", "failed"}:
            return await PayoutExecutionService.fail_payout(
                session,
                payout_id=payout.id,
                actor_user_id=admin_user_id,
                failure_reason=item.get("failure_reason") or f"Provider status: {prov_status}",
            )
        if prov_status in {"queued", "pending", "processing"} and payout.status == "APPROVED":
            return await PayoutExecutionService._transition_status_atomic(
                session,
                payout=payout,
                expected_status="APPROVED",
                target_status="PROCESSING",
                updates={
                    "provider_payout_id": item.get("id") or payout.provider_payout_id,
                    "provider_metadata_json": _sanitize_provider_payload(item),
                    "updated_at": now,
                },
            )
        return payout

    @staticmethod
    async def fail_payout(
        session: AsyncSession,
        *,
        payout_id: str,
        actor_user_id: Optional[str],
        failure_reason: str,
    ) -> DriverPayoutRequest:
        """Transition APPROVED/AWAITING_MANUAL_TRANSFER/PROCESSING -> FAILED and reverse hold once."""
        clean_reason = (failure_reason or "").strip() or "Payout execution failed"
        payout = (
            await session.execute(
                select(DriverPayoutRequest)
                .where(DriverPayoutRequest.id == payout_id)
                .with_for_update()
                .execution_options(populate_existing=True)
            )
        ).scalar_one_or_none()
        if not payout:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Payout request not found.")
        if payout.status == "FAILED":
            return payout
        if payout.status not in {"APPROVED", "AWAITING_MANUAL_TRANSFER", "PROCESSING"}:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Cannot mark payout FAILED from status {payout.status}.",
            )

        now = datetime.now(timezone.utc)
        payout = await PayoutExecutionService._transition_status_atomic(
            session,
            payout=payout,
            expected_status=payout.status,
            target_status="FAILED",
            updates={
                "failure_reason": clean_reason[:500],
                "failed_at": now,
                "updated_at": now,
            },
        )
        await PayoutExecutionService._reverse_payout_hold_once(
            session, payout=payout, reason=clean_reason, now=now
        )
        if actor_user_id:
            session.add(AdministrativeAuditLog(
                admin_user_id=actor_user_id,
                action="PAYOUT_FAILED",
                target_resource="driver_payout_requests",
                target_id=payout.id,
                changes_json=json.dumps({"failure_reason": clean_reason, "amount_cents": payout.amount_cents}),
            ))
        await session.flush()
        return payout

    @staticmethod
    async def retry_failed_payout(
        session: AsyncSession,
        *,
        payout_id: str,
        admin_user_id: str,
    ) -> DriverPayoutRequest:
        """
        Safely retry a FAILED payout (FAILED -> APPROVED), re-reserving the payout hold atomically
        from DriverWallet without creating a duplicate DriverCommissionSettlement.
        """
        await PayoutExecutionService._require_authorized_admin(session, admin_user_id)
        payout = (
            await session.execute(
                select(DriverPayoutRequest)
                .where(DriverPayoutRequest.id == payout_id)
                .with_for_update()
                .execution_options(populate_existing=True)
            )
        ).scalar_one_or_none()
        if not payout:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Payout request not found.")
        if payout.status != "FAILED":
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Only FAILED payouts can be retried (current: {payout.status}).",
            )

        # Verify linked destination is still verified & primary before re-reserving funds
        if payout.payout_account_id:
            acct = await session.get(DriverPayoutAccount, payout.payout_account_id)
            if not acct or acct.driver_id != payout.driver_id or acct.verification_status != "VERIFIED" or not acct.is_primary:
                raise HTTPException(
                    status_code=status.HTTP_409_CONFLICT,
                    detail="Payout account is no longer verified and primary; cannot retry.",
                )

        # Lock DriverWallet and re-reserve payout amount atomically
        d_wallet = (
            await session.execute(
                select(DriverWallet)
                .where(DriverWallet.driver_id == payout.driver_id)
                .with_for_update()
                .execution_options(populate_existing=True)
            )
        ).scalar_one_or_none()
        if not d_wallet:
            raise HTTPException(status_code=400, detail="Driver wallet not found.")

        now = datetime.now(timezone.utc)
        upd = await session.execute(
            update(DriverWallet)
            .where(
                DriverWallet.driver_id == payout.driver_id,
                DriverWallet.balance_cents >= payout.amount_cents,
            )
            .values(balance_cents=DriverWallet.balance_cents - payout.amount_cents, updated_at=now)
            .returning(DriverWallet.balance_cents)
        )
        new_bal = upd.scalar_one_or_none()
        if new_bal is None:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Insufficient wallet balance to retry payout.",
            )
        await session.refresh(d_wallet)

        next_retry = (payout.retry_count or 0) + 1
        hold_ref = f"{payout.id}:retry:{next_retry}"
        session.add(DriverLedgerEntry(
            id=str(uuid.uuid4()),
            driver_id=payout.driver_id,
            entry_type="DEBIT",
            category="PAYOUT_RETRY_HOLD",
            amount_cents=payout.amount_cents,
            balance_after_cents=new_bal,
            reference_id=hold_ref,
            description=f"Retry #{next_retry} payout hold ({payout.id[:8]})",
            created_at=now,
        ))

        payout = await PayoutExecutionService._transition_status_atomic(
            session,
            payout=payout,
            expected_status="FAILED",
            target_status="APPROVED",
            updates={
                "retry_count": next_retry,
                "failure_reason": None,
                "failed_at": None,
                "settlement_reference": None,
                "payout_reference": None,
                "reviewed_by": admin_user_id,
                "reviewed_at": now,
                "updated_at": now,
            },
        )
        session.add(AdministrativeAuditLog(
            admin_user_id=admin_user_id,
            action="PAYOUT_RETRIED",
            target_resource="driver_payout_requests",
            target_id=payout.id,
            changes_json=json.dumps({"retry_count": next_retry, "amount_cents": payout.amount_cents}),
        ))
        await session.flush()
        return payout

    @staticmethod
    async def process_payout_webhook(
        session: AsyncSession,
        *,
        raw_body: bytes,
        signature_header: str,
    ) -> Dict[str, Any]:
        """
        Verify provider webhook signature, persist idempotently in PaymentWebhookInbox,
        and apply out-of-order-safe state transitions for payout.* events.
        """
        provider = get_payment_provider()
        verification, _slot = await provider.verify_webhook_payload_detailed(raw_body, signature_header or "")
        digest = hashlib.sha256(raw_body).hexdigest()
        now = datetime.now(timezone.utc)

        if not verification.is_valid:
            existing_bad = (
                await session.execute(select(PaymentWebhookInbox).where(PaymentWebhookInbox.event_digest == digest))
            ).scalar_one_or_none()
            if not existing_bad:
                session.add(PaymentWebhookInbox(
                    provider_name="RAZORPAYX",
                    event_digest=digest,
                    payload_json=raw_body.decode("utf-8", errors="replace"),
                    signature_verified=False,
                    status="FAILED",
                    attempts=1,
                    last_error=verification.message or "Invalid webhook signature.",
                    last_attempt_at=now,
                ))
                await session.commit()
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid payout webhook signature.")

        try:
            payload = json.loads(raw_body.decode("utf-8"))
        except Exception as exc:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Malformed webhook JSON.") from exc

        event_name = (payload.get("event") or "").strip().lower()
        entity = payload.get("payload", {}).get("payout", {}).get("entity", {}) or {}
        provider_payout_id = (entity.get("id") or "").strip() or None
        reference_id = (entity.get("reference_id") or "").strip() or None

        inbox = (
            await session.execute(
                select(PaymentWebhookInbox).where(PaymentWebhookInbox.event_digest == digest).with_for_update()
            )
        ).scalar_one_or_none()
        if inbox and inbox.signature_verified and inbox.status == "PROCESSED":
            return {"status": "ALREADY_PROCESSED", "digest": digest}

        if not inbox:
            inbox = PaymentWebhookInbox(
                provider_name="RAZORPAYX",
                event_digest=digest,
                event_type=event_name,
                provider_event_id=provider_payout_id,
                payload_json=raw_body.decode("utf-8", errors="replace"),
                signature_verified=True,
                status="PROCESSING",
                attempts=1,
                last_attempt_at=now,
            )
            session.add(inbox)
            try:
                await session.flush()
            except IntegrityError:
                await session.rollback()
                return {"status": "ALREADY_PROCESSED", "digest": digest}

        payout = None
        if provider_payout_id:
            payout = (
                await session.execute(
                    select(DriverPayoutRequest)
                    .where(DriverPayoutRequest.provider_payout_id == provider_payout_id)
                    .with_for_update()
                )
            ).scalar_one_or_none()
        if not payout and reference_id:
            payout = (
                await session.execute(
                    select(DriverPayoutRequest).where(DriverPayoutRequest.id == reference_id).with_for_update()
                )
            ).scalar_one_or_none()

        if not payout:
            inbox.status = "PROCESSED"
            inbox.processed_at = now
            await session.commit()
            return {"status": "IGNORED_UNKNOWN_PAYOUT", "digest": digest}

        # Out-of-order / terminal protection
        if payout.status in TERMINAL_PAYOUT_STATUSES:
            inbox.status = "PROCESSED"
            inbox.processed_at = now
            await session.commit()
            return {"status": "IGNORED_OUT_OF_ORDER", "payout_id": payout.id, "payout_status": payout.status}

        if event_name == "payout.processed":
            utr = (entity.get("utr") or provider_payout_id or "").strip()
            if payout.status in {"APPROVED", "PROCESSING"}:
                await PayoutExecutionService._transition_status_atomic(
                    session,
                    payout=payout,
                    expected_status=payout.status,
                    target_status="PAID",
                    updates={
                        "provider_payout_id": provider_payout_id or payout.provider_payout_id,
                        "settlement_reference": utr or payout.settlement_reference,
                        "payout_reference": utr or payout.payout_reference,
                        "provider_metadata_json": _sanitize_provider_payload(entity),
                        "paid_at": now,
                        "confirmed_at": now,
                        "updated_at": now,
                    },
                )
        elif event_name in {"payout.failed", "payout.reversed", "payout.rejected"}:
            if payout.status in {"APPROVED", "PROCESSING"}:
                await PayoutExecutionService.fail_payout(
                    session,
                    payout_id=payout.id,
                    actor_user_id=None,
                    failure_reason=entity.get("failure_reason") or f"Webhook event {event_name}",
                )
        elif event_name in {"payout.queued", "payout.pending", "payout.initiated"}:
            if payout.status == "APPROVED":
                await PayoutExecutionService._transition_status_atomic(
                    session,
                    payout=payout,
                    expected_status="APPROVED",
                    target_status="PROCESSING",
                    updates={
                        "provider_payout_id": provider_payout_id or payout.provider_payout_id,
                        "provider_metadata_json": _sanitize_provider_payload(entity),
                        "updated_at": now,
                    },
                )

        inbox.status = "PROCESSED"
        inbox.processed_at = now
        await session.commit()
        return {"status": "PROCESSED", "payout_id": payout.id, "payout_status": payout.status}

    @staticmethod
    async def reconcile_payout_invariants(session: AsyncSession, payout_id: str) -> Dict[str, Any]:
        """Verify wallet, ledger holds/reversals, commission settlements, and payout state consistency."""
        payout = (
            await session.execute(
                select(DriverPayoutRequest)
                .where(DriverPayoutRequest.id == payout_id)
                .execution_options(populate_existing=True)
            )
        ).scalar_one_or_none()
        if not payout:
            raise HTTPException(status_code=404, detail="Payout request not found.")
        wallet = (
            await session.execute(
                select(DriverWallet)
                .where(DriverWallet.driver_id == payout.driver_id)
                .execution_options(populate_existing=True)
            )
        ).scalar_one_or_none()
        ledger_rows = (
            await session.execute(
                select(DriverLedgerEntry).where(
                    DriverLedgerEntry.driver_id == payout.driver_id,
                    DriverLedgerEntry.reference_id.like(f"{payout.id}%"),
                )
            )
        ).scalars().all()
        settlements = (
            await session.execute(
                select(DriverCommissionSettlement).where(DriverCommissionSettlement.payout_request_id == payout.id)
            )
        ).scalars().all()

        holds = sum(e.amount_cents for e in ledger_rows if e.category in {"PAYOUT_HOLD", "PAYOUT_RETRY_HOLD"})
        reversals = sum(e.amount_cents for e in ledger_rows if e.category == "PAYOUT_REVERSAL")
        expected_net_hold = 0 if payout.status in {"FAILED", "REJECTED"} else payout.amount_cents

        return {
            "payout_id": payout.id,
            "status": payout.status,
            "wallet_balance_cents": wallet.balance_cents if wallet else 0,
            "net_hold_cents": holds - reversals,
            "expected_net_hold_cents": expected_net_hold,
            "commission_settlement_count": len(settlements),
            "is_balanced": (holds - reversals) == expected_net_hold and len(settlements) <= 1,
        }
