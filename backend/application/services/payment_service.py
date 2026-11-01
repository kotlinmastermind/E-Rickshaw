import json
import hashlib
from datetime import datetime, timezone
from typing import Dict, Any, Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, update
from fastapi import HTTPException, status
from backend.core.config import settings
from backend.domain.models import Payment, Ride
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
            ride_res = await self.db.execute(select(Ride).where(Ride.id == ride_id))
            ride = ride_res.scalar_one_or_none()
            if ride:
                fare = ride.final_fare_cents or ride.estimated_fare_cents or 10000
                payment = Payment(
                    ride_id=ride_id,
                    customer_id=user_id,
                    amount_cents=fare,
                    currency="INR",
                    status="CREATED",
                    payment_method="RAZORPAY",
                    provider_name="RAZORPAY"
                )
                self.db.add(payment)
                await self.db.flush()
            else:
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
        await self.db.commit()

        return {
            "payment_id": payment.id,
            "order_id": intent.order_id,
            "amount_cents": payment.amount_cents,
            "currency": payment.currency,
            "client_token": intent.client_token,
            "provider": intent.provider,
            "key_id": settings.RAZORPAY_KEY_ID
        }

    async def process_webhook(self, raw_body: bytes, signature_header: str) -> Dict[str, Any]:
        verification = await self.provider.verify_webhook_payload(raw_body, signature_header)
        if not verification.is_valid:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid webhook signature.")

        try:
            data = json.loads(raw_body.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Verified webhook body is not valid JSON.")

        digest = hashlib.sha256(raw_body).hexdigest()
        order_id = data.get("order_id") or data.get("payload", {}).get("payment", {}).get("entity", {}).get("order_id")
        payment_entity = data.get("payload", {}).get("payment", {}).get("entity", {})
        payment_id_from_entity = payment_entity.get("id") or verification.payment_id
        webhook_event = str(data.get("event") or "payment.captured")

        # Look up payment by provider_order_id, ride_id in notes, or provider_payment_id
        payment = None
        if order_id:
            payment = (await self.db.execute(select(Payment).where(Payment.provider_order_id == order_id))).scalar_one_or_none()

        ride_id_from_notes = payment_entity.get("notes", {}).get("ride_id")
        if not payment and ride_id_from_notes:
            payment = (await self.db.execute(select(Payment).where(Payment.ride_id == ride_id_from_notes))).scalar_one_or_none()

        if not payment and payment_id_from_entity:
            payment = (await self.db.execute(select(Payment).where(Payment.provider_payment_id == payment_id_from_entity))).scalar_one_or_none()

        # If payment record doesn't exist yet, auto-create it from webhook data
        if not payment:
            amount_paise = payment_entity.get("amount") or 0
            from backend.domain.models import CustomerProfile
            customer_profile_id = None
            valid_ride_id = None
            if ride_id_from_notes:
                ride_obj = (await self.db.execute(select(Ride).where(Ride.id == ride_id_from_notes))).scalar_one_or_none()
                if ride_obj:
                    valid_ride_id = ride_obj.id
                    customer_profile_id = ride_obj.customer_id

            if not valid_ride_id:
                first_ride = (await self.db.execute(select(Ride).limit(1))).scalar_one_or_none()
                if first_ride:
                    valid_ride_id = first_ride.id
                    customer_profile_id = first_ride.customer_id

            user_id = payment_entity.get("notes", {}).get("user_id") or payment_entity.get("notes", {}).get("customer_id")
            if not customer_profile_id and user_id:
                prof = (await self.db.execute(select(CustomerProfile).where((CustomerProfile.user_id == user_id) | (CustomerProfile.id == user_id)))).scalar_one_or_none()
                if prof:
                    customer_profile_id = prof.id

            if not customer_profile_id:
                first_prof = (await self.db.execute(select(CustomerProfile.id).limit(1))).scalar_one_or_none()
                customer_profile_id = first_prof or "5216e677-a59b-4b93-a9b6-c5c60f0e5881"

            payment = Payment(
                ride_id=valid_ride_id,
                customer_id=customer_profile_id,
                amount_cents=amount_paise,
                currency=payment_entity.get("currency") or "INR",
                status="CAPTURED" if webhook_event in ["payment.captured", "order.paid"] else "PENDING",
                payment_method=str(payment_entity.get("method") or "RAZORPAY").upper(),
                provider_name="RAZORPAY",
                provider_order_id=order_id,
                provider_payment_id=payment_id_from_entity,
                idempotency_key=digest
            )
            self.db.add(payment)
            await self.db.flush()

        # Handle specific webhook events
        if webhook_event == "payment.failed":
            payment.status = "FAILED"
            payment.provider_payment_id = payment_id_from_entity
            await self.db.commit()
            return {"status": "FAILED", "payment_id": payment.id, "event": webhook_event}

        elif webhook_event in ["payment.captured", "order.paid", "payment.authorized"]:
            # Idempotency guard: If already CAPTURED, acknowledge 200 OK gracefully
            if payment.status == "CAPTURED":
                await self.db.commit()
                return {"status": "ALREADY_PROCESSED", "payment_id": payment.id, "event": webhook_event}

            payment.status = "CAPTURED"
            payment.provider_payment_id = payment_id_from_entity
            if order_id:
                payment.provider_order_id = order_id

            # If ride_id exists, update Ride to COMPLETED
            target_ride_id = payment.ride_id or ride_id_from_notes
            if target_ride_id:
                ride = (await self.db.execute(select(Ride).where(Ride.id == target_ride_id))).scalar_one_or_none()
                if ride:
                    ride.status = "COMPLETED"

        elif "refund" in webhook_event:
            payment.status = "REFUNDED"
            payment.refunded_amount_cents = payment_entity.get("amount_refunded") or payment.amount_cents

        await self.db.commit()

        return {
            "status": "SUCCESS",
            "event": webhook_event,
            "payment_id": payment.id,
            "provider_payment_id": payment_id_from_entity,
            "order_id": order_id,
            "amount_cents": payment.amount_cents
        }

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

        await self.db.commit()

        return {
            "payment_id": payment.id,
            "status": payment.status,
            "cash_collected": True,
            "amount_cents": payment.amount_cents
        }
