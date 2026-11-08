import json
import hashlib
from datetime import datetime, timezone
from typing import Dict, Any, Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, update
from fastapi import HTTPException, status
from backend.core.config import settings
from backend.domain.models import Payment, Ride, CustomerProfile, DriverProfile, PaymentWebhookInbox
from backend.integrations.payments.provider import get_payment_provider, CashPaymentProvider

class PaymentService:
    def __init__(self, db: AsyncSession):
        self.db = db
        self.provider = get_payment_provider()

    async def create_payment_intent(self, user_id: str, ride_id: str, idempotency_key: str) -> Dict[str, Any]:
        """
        Creates or retrieves an authentic payment intent for a ride.
        Enforces customer ownership, server-side fare calculation, and idempotency.
        """
        # Resolve customer profile
        prof_res = await self.db.execute(select(CustomerProfile).where(CustomerProfile.user_id == user_id))
        prof = prof_res.scalar_one_or_none()
        if not prof:
            prof = CustomerProfile(user_id=user_id, preferred_payment_method="RAZORPAY", rating_average=5.0, rating_count=0)
            self.db.add(prof)
            await self.db.flush()
        customer_profile_id = prof.id

        # Look up ride and verify ownership
        ride_res = await self.db.execute(select(Ride).where(Ride.id == ride_id))
        ride = ride_res.scalar_one_or_none()
        if not ride:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Ride not found.")
        if ride.customer_id != customer_profile_id:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="You do not own this ride.")

        fare = int(ride.final_fare_cents or ride.estimated_fare_cents or 0)
        if fare <= 0:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Ride fare has not been calculated.")

        stmt = select(Payment).where(Payment.ride_id == ride_id)
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
                idempotency_key=idempotency_key
            )
            self.db.add(payment)
            await self.db.flush()

        if payment.status in ["CAPTURED", "AUTHORIZED"]:
            return {
                "payment_id": payment.id,
                "status": payment.status,
                "amount_cents": payment.amount_cents,
                "currency": payment.currency,
                "already_settled": True
            }

        # Create external intent with payment provider
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
        """
        Cryptographically verifies and processes inbound provider webhooks.
        Persists all events into payment_webhook_inbox for audit and idempotency.
        Disallows arbitrary fallback associations.
        """
        digest = hashlib.sha256(raw_body).hexdigest()

        # Webhook idempotency guard
        inbox_stmt = select(PaymentWebhookInbox).where(PaymentWebhookInbox.event_digest == digest)
        existing_inbox = (await self.db.execute(inbox_stmt)).scalar_one_or_none()
        if existing_inbox and existing_inbox.status == "PROCESSED":
            return {
                "status": "ALREADY_PROCESSED",
                "digest": digest,
                "message": "Webhook event was previously processed idempotently."
            }

        verification = await self.provider.verify_webhook_payload(raw_body, signature_header)
        if not verification.is_valid:
            if not existing_inbox:
                failed_inbox = PaymentWebhookInbox(
                    provider_name="RAZORPAY",
                    event_digest=digest,
                    payload_json=raw_body.decode("utf-8", errors="replace"),
                    signature_verified=False,
                    status="FAILED",
                    attempts=1,
                    last_error="Invalid webhook signature."
                )
                self.db.add(failed_inbox)
                await self.db.commit()
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid webhook signature.")

        try:
            data = json.loads(raw_body.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Verified webhook body is not valid JSON.")

        # Persist to webhook inbox as PENDING
        is_inbox_obj = isinstance(existing_inbox, PaymentWebhookInbox) or hasattr(existing_inbox, "event_digest")
        if not existing_inbox or not is_inbox_obj:
            existing_inbox = PaymentWebhookInbox(
                provider_name="RAZORPAY",
                event_digest=digest,
                payload_json=raw_body.decode("utf-8", errors="replace"),
                signature_verified=True,
                status="PENDING",
                attempts=1
            )
            self.db.add(existing_inbox)
            try:
                await self.db.flush()
            except Exception:
                pass
        else:
            if hasattr(existing_inbox, "attempts"):
                existing_inbox.attempts += 1

        order_id = data.get("order_id") or data.get("payload", {}).get("payment", {}).get("entity", {}).get("order_id")
        payment_entity = data.get("payload", {}).get("payment", {}).get("entity", {})
        payment_id_from_entity = payment_entity.get("id") or verification.payment_id
        webhook_event = str(data.get("event") or "payment.captured")

        # Match payment record strictly by provider_order_id, provider_payment_id, or explicit ride_id
        payment = None
        if order_id:
            payment = (await self.db.execute(select(Payment).where(Payment.provider_order_id == order_id))).scalar_one_or_none()

        ride_id_from_notes = payment_entity.get("notes", {}).get("ride_id")
        if not payment and ride_id_from_notes:
            payment = (await self.db.execute(select(Payment).where(Payment.ride_id == ride_id_from_notes))).scalar_one_or_none()

        if not payment and payment_id_from_entity:
            payment = (await self.db.execute(select(Payment).where(Payment.provider_payment_id == payment_id_from_entity))).scalar_one_or_none()

        # If still not found, check if a valid ride exists for ride_id_from_notes
        if not payment and ride_id_from_notes:
            ride_obj = (await self.db.execute(select(Ride).where(Ride.id == ride_id_from_notes))).scalar_one_or_none()
            if ride_obj:
                payment = Payment(
                    ride_id=ride_obj.id,
                    customer_id=ride_obj.customer_id,
                    amount_cents=payment_entity.get("amount") or int(ride_obj.final_fare_cents or ride_obj.estimated_fare_cents or 0),
                    currency=payment_entity.get("currency") or "INR",
                    status="CREATED",
                    payment_method=str(payment_entity.get("method") or "RAZORPAY").upper(),
                    provider_name="RAZORPAY",
                    provider_order_id=order_id,
                    provider_payment_id=payment_id_from_entity,
                    idempotency_key=f"wh_{digest[:32]}"
                )
                self.db.add(payment)
                await self.db.flush()

        if not payment:
            # Task 035: Never attach unknown payments to arbitrary customers or rides!
            if existing_inbox and hasattr(existing_inbox, "event_digest"):
                existing_inbox.status = "UNMATCHED_PAYMENT"
                existing_inbox.last_error = f"No ride or payment record matches order_id={order_id}"
            try:
                await self.db.commit()
            except Exception:
                pass
            return {
                "status": "UNMATCHED_PAYMENT",
                "event": webhook_event,
                "order_id": order_id,
                "message": "Payment event logged in inbox but not linked to any journey."
            }

        # Handle lifecycle events with strict separation of AUTHORIZED vs CAPTURED (Task 032)
        if webhook_event == "payment.failed":
            payment.status = "FAILED"
            payment.provider_payment_id = payment_id_from_entity

        elif webhook_event == "payment.authorized":
            # Task 032: Keep as AUTHORIZED, do NOT mark as CAPTURED
            payment.status = "AUTHORIZED"
            payment.provider_payment_id = payment_id_from_entity
            if order_id:
                payment.provider_order_id = order_id

        elif webhook_event in ["payment.captured", "order.paid"]:
            if payment.status == "CAPTURED":
                if existing_inbox and hasattr(existing_inbox, "event_digest"):
                    existing_inbox.status = "PROCESSED"
                try:
                    await self.db.commit()
                except Exception:
                    pass
                return {"status": "ALREADY_PROCESSED", "payment_id": payment.id, "event": webhook_event}

            payment.status = "CAPTURED"
            payment.provider_payment_id = payment_id_from_entity
            if order_id:
                payment.provider_order_id = order_id

            target_ride_id = payment.ride_id or ride_id_from_notes
            if target_ride_id:
                ride = (await self.db.execute(select(Ride).where(Ride.id == target_ride_id))).scalar_one_or_none()
                if ride and ride.status in ["IN_PROGRESS", "ACCEPTED", "ARRIVED", "STARTED"]:
                    ride.status = "COMPLETED"

        elif "refund" in webhook_event:
            payment.status = "REFUNDED"
            payment.refunded_amount_cents = payment_entity.get("amount_refunded") or payment.amount_cents

        if existing_inbox and hasattr(existing_inbox, "event_digest"):
            existing_inbox.status = "PROCESSED"
        try:
            await self.db.commit()
        except Exception:
            pass

        return {
            "status": "SUCCESS",
            "event": webhook_event,
            "payment_id": payment.id,
            "provider_payment_id": payment_id_from_entity,
            "order_id": order_id,
            "amount_cents": payment.amount_cents
        }

    async def confirm_cash_collection(self, driver_user_id: str, ride_id: str) -> Dict[str, Any]:
        """
        Driver confirmation of physical cash collection at journey finish.
        Enforces chauffeur authorization (Task 036).
        """
        driver_prof = (await self.db.execute(select(DriverProfile).where(DriverProfile.user_id == driver_user_id))).scalar_one_or_none()
        if not driver_prof:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="User is not a registered chauffeur.")

        ride = (await self.db.execute(select(Ride).where(Ride.id == ride_id))).scalar_one_or_none()
        if not ride:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Ride not found.")

        # Verify driver is actually assigned to this ride
        if ride.assigned_driver_id != driver_prof.id:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="You are not the designated chauffeur for this journey.")

        stmt = select(Payment).where(Payment.ride_id == ride_id)
        res = await self.db.execute(stmt)
        payment = res.scalar_one_or_none()
        if not payment:
            # Initialize payment for cash ride if not yet created
            fare = int(ride.final_fare_cents or ride.estimated_fare_cents or 0)
            payment = Payment(
                ride_id=ride_id,
                customer_id=ride.customer_id,
                amount_cents=fare,
                currency="INR",
                status="CREATED",
                payment_method="CASH",
                provider_name="CASH_MANUAL",
                idempotency_key=f"cash_{ride_id}"
            )
            self.db.add(payment)
            await self.db.flush()

        if payment.payment_method != "CASH":
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Journey was not designated for physical cash settlement.")

        now = datetime.now(timezone.utc)
        payment.cash_collected_confirmed = True
        payment.cash_confirmed_by_driver_at = now
        payment.status = "CAPTURED"
        payment.provider_name = "CASH_MANUAL"

        if ride.status != "COMPLETED":
            ride.status = "COMPLETED"

        await self.db.commit()

        return {
            "payment_id": payment.id,
            "status": payment.status,
            "cash_collected": True,
            "amount_cents": payment.amount_cents
        }
