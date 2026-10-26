import json
from datetime import datetime, timezone
from typing import Dict, Any, Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, update
from fastapi import HTTPException, status
from backend.domain.models import Payment, PaymentEvent, Ride
from backend.integrations.payments.provider import get_payment_provider, CashPaymentProvider

class PaymentService:
    def __init__(self, db: AsyncSession):
        self.db = db
        self.provider = get_payment_provider()

    async def create_payment_intent(self, user_id: str, ride_id: str, idempotency_key: str) -> Dict[str, Any]:
        stmt = select(Payment).where(Payment.ride_id == ride_id)
        res = await self.db.execute(stmt)
        payment = res.scalar_one_or_none()
        if not payment:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Payment record for ride not found.")

        if payment.status in ["CAPTURED", "AUTHORIZED"]:
            return {
                "payment_id": payment.id,
                "status": payment.status,
                "amount_cents": payment.amount_cents,
                "currency": payment.currency,
                "already_settled": True
            }

        # Create external intent
        intent = await self.provider.create_payment_intent(
            amount_cents=payment.amount_cents,
            currency=payment.currency,
            idempotency_key=idempotency_key
        )

        payment.provider_order_id = intent.order_id
        payment.idempotency_key = idempotency_key
        
        event = PaymentEvent(
            payment_id=payment.id,
            event_type="INTENT_CREATED",
            payload_json=json.dumps(intent.to_dict())
        )
        self.db.add(event)
        await self.db.commit()

        return {
            "payment_id": payment.id,
            "order_id": intent.order_id,
            "amount_cents": payment.amount_cents,
            "currency": payment.currency,
            "client_token": intent.client_token,
            "provider": intent.provider
        }

    async def process_webhook(self, raw_body: bytes, signature_header: str) -> Dict[str, Any]:
        verification = await self.provider.verify_webhook_payload(raw_body, signature_header)
        if not verification.is_valid:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid webhook signature.")

        data = json.loads(raw_body.decode("utf-8"))
        order_id = data.get("order_id")
        if not order_id:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Missing order reference in webhook payload.")

        stmt = select(Payment).where(Payment.provider_order_id == order_id)
        res = await self.db.execute(stmt)
        payment = res.scalar_one_or_none()
        if not payment:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Associated payment record not found.")

        # Idempotency guard: If already CAPTURED, return gracefully
        if payment.status == "CAPTURED":
            return {"status": "ALREADY_PROCESSED", "payment_id": payment.id}

        payment.status = "CAPTURED"
        payment.provider_payment_id = verification.payment_id
        payment.provider_signature = signature_header

        event = PaymentEvent(
            payment_id=payment.id,
            event_type="WEBHOOK_CAPTURED",
            payload_json=raw_body.decode("utf-8")
        )
        self.db.add(event)
        await self.db.commit()

        return {"status": "SUCCESS", "payment_id": payment.id, "amount_cents": payment.amount_cents}

    async def confirm_cash_collection(self, driver_user_id: str, ride_id: str) -> Dict[str, Any]:
        """Explicit chauffeur confirmation of cash collection at trip finish."""
        stmt = select(Payment).where(Payment.ride_id == ride_id)
        res = await self.db.execute(stmt)
        payment = res.scalar_one_or_none()
        if not payment:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Payment record not found.")

        if payment.payment_method != "CASH":
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Journey was not designated for physical cash settlement.")

        now = datetime.now(timezone.utc)
        payment.cash_collected_confirmed = True
        payment.cash_confirmed_by_driver_at = now
        payment.status = "CAPTURED"
        payment.provider_name = "CASH_MANUAL"

        event = PaymentEvent(
            payment_id=payment.id,
            event_type="CASH_COLLECTED_CONFIRMED",
            payload_json=json.dumps({"driver_user_id": driver_user_id, "confirmed_at": now.isoformat()})
        )
        self.db.add(event)
        await self.db.commit()

        return {
            "payment_id": payment.id,
            "status": payment.status,
            "cash_collected": True,
            "amount_cents": payment.amount_cents
        }
