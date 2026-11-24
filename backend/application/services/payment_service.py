import json
import hashlib
import inspect
from datetime import datetime, timezone
from typing import Dict, Any, Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, update
from fastapi import HTTPException, status
from backend.core.config import settings
from backend.domain.models import Payment, Ride, CustomerProfile, DriverProfile, PaymentWebhookInbox, DriverWallet, DriverLedgerEntry
from backend.integrations.payments.provider import get_payment_provider, CashPaymentProvider

class PaymentService:
    def __init__(self, db: AsyncSession):
        self.db = db
        self.provider = get_payment_provider()


    # Add to backend/application/services/payment_service.py:

    async def finalize_payment_capture(
        self,
        payment: Payment,
        provider_payment_id: str,
        provider_signature: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Authoritative single-source payment finalization engine.
        Applies idempotency, transitions status to CAPTURED, credits wallet if top-up,
        or marks Ride as COMPLETED if fare payment.
        """
        if payment.status == "CAPTURED":
            return {"status": "ALREADY_PROCESSED", "payment_id": payment.id}

        payment.status = "CAPTURED"
        payment.provider_payment_id = provider_payment_id
        if provider_signature:
            payment.provider_signature = provider_signature

        # Domain Action 1: Wallet Top-Up
        if payment.purpose == "WALLET_TOPUP":
            target_user_id = payment.user_id
            if not target_user_id and payment.customer_id:
                prof = (await self.db.execute(select(CustomerProfile).where(CustomerProfile.id == payment.customer_id))).scalar_one_or_none()
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
                idempotency_key=f"pay_topup_fin_{payment.provider_order_id}"
            )

        # Domain Action 2: Ride Fare Payment
        elif payment.purpose == "RIDE_FARE" and payment.ride_id:
            r_res = await self.db.execute(select(Ride).where(Ride.id == payment.ride_id))
            ride = r_res.scalar_one_or_none()
            if ride and ride.status in ["IN_PROGRESS", "ACCEPTED", "ARRIVED", "STARTED"]:
                ride.status = "COMPLETED"
                ride.completed_at = ride.completed_at or datetime.now(timezone.utc)
                if ride.final_fare_cents is None:
                    ride.final_fare_cents = payment.amount_cents

        await self.db.commit()
        return {
            "status": "CAPTURED",
            "payment_id": payment.id,
            "purpose": payment.purpose,
            "amount_cents": payment.amount_cents
        }
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
                add_res = self.db.add(failed_inbox)
                if inspect.isawaitable(add_res):
                    await add_res
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
            add_res = self.db.add(existing_inbox)
            if inspect.isawaitable(add_res):
                await add_res
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
            if order_id and not payment.provider_order_id:
                payment.provider_order_id = order_id

            fin_result = await self.finalize_payment_capture(
                payment=payment,
                provider_payment_id=payment_id_from_entity,
                provider_signature=signature_header
            )
            if existing_inbox and hasattr(existing_inbox, "event_digest"):
                existing_inbox.status = "PROCESSED"
            await self.db.commit()
            return {
                "status": "SUCCESS",
                "event": webhook_event,
                "payment_id": payment.id,
                "purpose": getattr(payment, "purpose", "RIDE_FARE"),
                "detail": fin_result
            }

            
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

        ride = (await self.db.execute(select(Ride).where(Ride.id == ride_id).with_for_update())).scalar_one_or_none()
        if not ride:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Ride not found.")

        # Verify driver is actually assigned to this ride
        if ride.assigned_driver_id != driver_prof.id:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="You are not the designated chauffeur for this journey.")

        stmt = select(Payment).where(Payment.ride_id == ride_id).with_for_update()
        res = await self.db.execute(stmt)
        payment = res.scalar_one_or_none()
        if not payment:
            # Initialize payment for cash ride if not yet created
            fare = int(ride.final_fare_cents or ride.estimated_fare_cents or 0)
            if fare <= 0:
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail="Cannot collect cash: Ride does not have a valid final fare or fare snapshot."
                )
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

        if payment.amount_cents <= 0:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Cannot collect cash: Payment amount is invalid or zero."
            )

        if payment.cash_collected_confirmed or payment.status in ["CAPTURED", "SETTLED"]:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Cash collection for this ride has already been confirmed and processed."
            )

        if payment.payment_method != "CASH":
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Journey was not designated for physical cash settlement.")

        now = datetime.now(timezone.utc)
        payment.cash_collected_confirmed = True
        payment.cash_confirmed_by_driver_at = now
        payment.status = "CAPTURED"
        payment.provider_name = "CASH_MANUAL"

        if ride.status != "COMPLETED":
            ride.status = "COMPLETED"

        # Deduct platform commission (15%) from Driver Wallet and record in Driver Ledger
        fare_cents = payment.amount_cents
        commission_cents = int(round(fare_cents * 0.15))

        wallet_res = await self.db.execute(select(DriverWallet).where(DriverWallet.driver_id == driver_prof.id).with_for_update())
        wallet = wallet_res.scalar_one_or_none()
        if not wallet:
            wallet = DriverWallet(driver_id=driver_prof.id, balance_cents=0, currency="INR")
            self.db.add(wallet)
            await self.db.flush()

        wallet.balance_cents -= commission_cents

        import uuid
        ledger_entry = DriverLedgerEntry(
            id=str(uuid.uuid4()),
            driver_id=driver_prof.id,
            entry_type="DEBIT",
            category="COMMISSION",
            amount_cents=-commission_cents,
            balance_after_cents=wallet.balance_cents,
            reference_id=ride.id,
            description=f"Platform commission (15%) for cash ride #{ride.id[:8]}",
            created_at=now
        )
        self.db.add(ledger_entry)

        await self.db.commit()

        return {
            "payment_id": payment.id,
            "status": payment.status,
            "cash_collected": True,
            "amount_cents": payment.amount_cents,
            "commission_deducted_cents": commission_cents,
            "driver_wallet_balance_cents": wallet.balance_cents
        }
