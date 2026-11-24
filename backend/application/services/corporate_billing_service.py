import re
import uuid
from datetime import datetime, timezone, timedelta
from typing import Dict, Any, List, Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, update, desc, func, and_, or_
from fastapi import HTTPException, status

from backend.domain.models import (
    User, CustomerProfile, Ride, VehicleCategory,
    CorporateAccount, CorporateEmployee, CorporateTravelPolicy,
    CorporateInvoice, CorporateInvoiceItem
)

# Indian GSTIN validation regex (15 alphanumeric characters)
# 2 state digits + 5 PAN letters + 4 PAN digits + 1 PAN letter + 1 entity + 'Z' + 1 checksum
GSTIN_REGEX = re.compile(r"^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$")


class CorporateBillingService:
    def __init__(self, db: AsyncSession):
        self.db = db

    # -------------------------------------------------------------------------
    # 1. Corporate Profile Registration & Onboarding
    # -------------------------------------------------------------------------
    async def register_account(
        self,
        user_id: str,
        company_name: str,
        tax_id: str,
        billing_email: str,
        billing_contact_name: Optional[str] = None,
        billing_contact_phone: Optional[str] = None,
        trade_name: Optional[str] = None,
        registered_address: Optional[str] = None,
        billing_address: Optional[str] = None,
        default_cost_center: Optional[str] = "HEADQUARTERS",
        employee_id: Optional[str] = "EMP-001",
        department: Optional[str] = "Executive"
    ) -> Dict[str, Any]:
        clean_gstin = tax_id.strip().upper()
        if not GSTIN_REGEX.match(clean_gstin):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Invalid Indian GSTIN '{tax_id}'. Must be a 15-character statutory GST identification number."
            )

        # Check if GSTIN already registered
        stmt = select(CorporateAccount).where(CorporateAccount.tax_id == clean_gstin)
        res = await self.db.execute(stmt)
        existing = res.scalar_one_or_none()
        if existing:
            # Check if this user is already admin or employee
            if existing.admin_user_id == user_id:
                return await self.get_account_details(existing.id, user_id)
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail=f"Corporate account with GSTIN '{clean_gstin}' is already registered."
            )

        now = datetime.now(timezone.utc)
        corp = CorporateAccount(
            company_name=company_name.strip(),
            trade_name=trade_name.strip() if trade_name else company_name.strip(),
            tax_id=clean_gstin,
            pan=clean_gstin[2:12],
            billing_email=billing_email.strip().lower(),
            billing_contact_name=billing_contact_name or "Corporate Administrator",
            billing_contact_phone=billing_contact_phone,
            registered_address=registered_address or "Corporate Headquarters, India",
            billing_address=billing_address or registered_address or "Corporate Headquarters, India",
            admin_user_id=user_id,
            credit_limit_cents=50000000,  # 5 Lakh INR default credit line
            balance_due_cents=0,
            monthly_spending_limit_cents=10000000,  # 1 Lakh INR monthly limit
            currency="INR",
            payment_terms="NET_30",
            default_cost_center=default_cost_center or "OPS-CORP",
            status="PENDING_APPROVAL",
            created_at=now,
            updated_at=now
        )
        self.db.add(corp)
        await self.db.flush()

        # Create Primary Admin Employee Record
        emp = CorporateEmployee(
            corporate_id=corp.id,
            user_id=user_id,
            employee_id=employee_id or f"EMP-{uuid.uuid4().hex[:6].upper()}",
            employee_name=billing_contact_name or "Corporate Account Owner",
            employee_email=billing_email.strip().lower(),
            employee_phone=billing_contact_phone,
            department=department or "Management",
            cost_center=default_cost_center or "OPS-CORP",
            designation="Account Administrator",
            status="ACTIVE",
            monthly_ride_limit=100,
            monthly_spending_limit_cents=10000000,
            per_ride_limit_cents=500000,
            approval_required=False,
            created_at=now,
            updated_at=now
        )
        self.db.add(emp)

        # Create Default Travel Policy
        policy = CorporateTravelPolicy(
            corporate_id=corp.id,
            policy_name="Standard Enterprise Mobility Policy",
            allowed_categories_json='["SEDAN", "LUXURY_SUV", "EXECUTIVE_LIMO", "AUTO_RICKSHAW", "E_RICKSHAW"]',
            max_fare_per_ride_cents=500000,  # ₹5,000 max per ride
            monthly_ride_limit=60,
            monthly_spending_limit_cents=10000000,
            daily_ride_limit=4,
            approval_required=False,
            is_active=True,
            created_at=now,
            updated_at=now
        )
        self.db.add(policy)

        # Clean legacy accessibility notes if present for this customer
        stmt_prof = select(CustomerProfile).where(CustomerProfile.user_id == user_id)
        prof_res = await self.db.execute(stmt_prof)
        prof = prof_res.scalar_one_or_none()
        if prof and prof.accessibility_notes and "GSTIN:" in prof.accessibility_notes:
            prof.accessibility_notes = None

        await self.db.commit()
        await self.db.refresh(corp)
        return await self.get_account_details(corp.id, user_id)

    # -------------------------------------------------------------------------
    # 2. Get Corporate Account for Authenticated User
    # -------------------------------------------------------------------------
    async def get_user_corporate_account(self, user_id: str) -> Optional[Dict[str, Any]]:
        # Check if user is admin
        stmt = select(CorporateAccount).where(CorporateAccount.admin_user_id == user_id)
        res = await self.db.execute(stmt)
        corp = res.scalar_one_or_none()

        if not corp:
            # Check if user is an employee in any corporate account
            stmt_emp = select(CorporateEmployee).where(
                and_(CorporateEmployee.user_id == user_id, CorporateEmployee.status == "ACTIVE")
            )
            res_emp = await self.db.execute(stmt_emp)
            emp = res_emp.scalar_one_or_none()
            if emp:
                stmt_corp = select(CorporateAccount).where(CorporateAccount.id == emp.corporate_id)
                res_corp = await self.db.execute(stmt_corp)
                corp = res_corp.scalar_one_or_none()

        if not corp:
            return None

        return await self.get_account_details(corp.id, user_id)

    # -------------------------------------------------------------------------
    # 3. Account Details Serialization
    # -------------------------------------------------------------------------
    async def get_account_details(self, corporate_id: str, requesting_user_id: str) -> Dict[str, Any]:
        stmt = select(CorporateAccount).where(CorporateAccount.id == corporate_id)
        corp = (await self.db.execute(stmt)).scalar_one_or_none()
        if not corp:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Corporate account not found.")

        # Find employee profile for requesting user
        stmt_emp = select(CorporateEmployee).where(
            and_(CorporateEmployee.corporate_id == corporate_id, CorporateEmployee.user_id == requesting_user_id)
        )
        emp = (await self.db.execute(stmt_emp)).scalar_one_or_none()

        # Find policies
        stmt_pol = select(CorporateTravelPolicy).where(
            and_(CorporateTravelPolicy.corporate_id == corporate_id, CorporateTravelPolicy.is_active == True)
        )
        policies = (await self.db.execute(stmt_pol)).scalars().all()

        return {
            "id": corp.id,
            "company_name": corp.company_name,
            "trade_name": corp.trade_name,
            "tax_id": corp.tax_id,
            "pan": corp.pan,
            "billing_email": corp.billing_email,
            "billing_contact_name": corp.billing_contact_name,
            "billing_contact_phone": corp.billing_contact_phone,
            "registered_address": corp.registered_address,
            "billing_address": corp.billing_address,
            "status": corp.status,
            "rejection_reason": corp.rejection_reason,
            "credit_limit_cents": corp.credit_limit_cents,
            "credit_limit_rupees": round((corp.credit_limit_cents or 0) / 100, 2),
            "balance_due_cents": corp.balance_due_cents,
            "balance_due_rupees": round((corp.balance_due_cents or 0) / 100, 2),
            "monthly_spending_limit_cents": corp.monthly_spending_limit_cents,
            "monthly_spending_limit_rupees": round((corp.monthly_spending_limit_cents or 0) / 100, 2),
            "currency": corp.currency or "INR",
            "payment_terms": corp.payment_terms or "NET_30",
            "default_cost_center": corp.default_cost_center,
            "is_admin": corp.admin_user_id == requesting_user_id,
            "created_at": corp.created_at.isoformat() if corp.created_at else None,
            "membership": {
                "employee_id": emp.employee_id if emp else None,
                "department": emp.department if emp else None,
                "cost_center": emp.cost_center if emp else corp.default_cost_center,
                "designation": emp.designation if emp else None,
                "status": emp.status if emp else "INACTIVE",
                "monthly_ride_limit": emp.monthly_ride_limit if emp else 50,
                "per_ride_limit_rupees": round((emp.per_ride_limit_cents or 200000) / 100, 2) if emp else 2000.0,
                "approval_required": emp.approval_required if emp else False
            } if emp else None,
            "policies": [
                {
                    "id": p.id,
                    "policy_name": p.policy_name,
                    "allowed_categories": p.allowed_categories_json,
                    "max_fare_rupees": round((p.max_fare_per_ride_cents or 500000) / 100, 2),
                    "curfew_start": p.curfew_start_time,
                    "curfew_end": p.curfew_end_time,
                    "approval_required": p.approval_required
                }
                for p in policies
            ]
        }

    # -------------------------------------------------------------------------
    # 4. Profile Update & Resubmission
    # -------------------------------------------------------------------------
    async def update_account_profile(
        self,
        corporate_id: str,
        user_id: str,
        company_name: Optional[str] = None,
        billing_address: Optional[str] = None,
        billing_contact_name: Optional[str] = None,
        billing_contact_phone: Optional[str] = None,
        default_cost_center: Optional[str] = None
    ) -> Dict[str, Any]:
        stmt = select(CorporateAccount).where(CorporateAccount.id == corporate_id)
        corp = (await self.db.execute(stmt)).scalar_one_or_none()
        if not corp:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Corporate account not found.")

        if corp.admin_user_id != user_id:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Only corporate administrator can modify account details.")

        if company_name:
            corp.company_name = company_name.strip()
        if billing_address:
            corp.billing_address = billing_address.strip()
        if billing_contact_name:
            corp.billing_contact_name = billing_contact_name.strip()
        if billing_contact_phone:
            corp.billing_contact_phone = billing_contact_phone.strip()
        if default_cost_center:
            corp.default_cost_center = default_cost_center.strip()

        corp.updated_at = datetime.now(timezone.utc)
        await self.db.commit()
        return await self.get_account_details(corp.id, user_id)

    async def submit_for_approval(self, corporate_id: str, user_id: str) -> Dict[str, Any]:
        stmt = select(CorporateAccount).where(CorporateAccount.id == corporate_id)
        corp = (await self.db.execute(stmt)).scalar_one_or_none()
        if not corp:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Corporate account not found.")

        if corp.admin_user_id != user_id:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Only corporate administrator can submit profile.")

        corp.status = "PENDING_APPROVAL"
        corp.rejection_reason = None
        corp.updated_at = datetime.now(timezone.utc)
        await self.db.commit()
        return {"message": "Corporate account submitted for administrator verification and approval.", "status": corp.status}

    # -------------------------------------------------------------------------
    # 5. Employee Membership Management
    # -------------------------------------------------------------------------
    async def list_employees(self, corporate_id: str, user_id: str) -> List[Dict[str, Any]]:
        # Verify access
        await self._verify_admin_access(corporate_id, user_id)
        stmt = select(CorporateEmployee).where(CorporateEmployee.corporate_id == corporate_id).order_by(CorporateEmployee.created_at)
        res = await self.db.execute(stmt)
        employees = res.scalars().all()
        return [
            {
                "id": e.id,
                "employee_id": e.employee_id,
                "employee_name": e.employee_name,
                "employee_email": e.employee_email,
                "employee_phone": e.employee_phone,
                "department": e.department,
                "cost_center": e.cost_center,
                "designation": e.designation,
                "status": e.status,
                "monthly_ride_limit": e.monthly_ride_limit,
                "per_ride_limit_rupees": round((e.per_ride_limit_cents or 0) / 100, 2),
                "approval_required": e.approval_required,
                "created_at": e.created_at.isoformat() if e.created_at else None
            }
            for e in employees
        ]

    async def add_employee(
        self,
        corporate_id: str,
        user_id: str,
        employee_id: str,
        employee_name: str,
        employee_email: str,
        employee_phone: Optional[str] = None,
        department: Optional[str] = "General",
        cost_center: Optional[str] = None,
        designation: Optional[str] = "Staff",
        per_ride_limit_cents: int = 200000,
        monthly_ride_limit: int = 50,
        approval_required: bool = False
    ) -> Dict[str, Any]:
        await self._verify_admin_access(corporate_id, user_id)

        # Check for duplicate employee_id
        stmt_dup = select(CorporateEmployee).where(
            and_(CorporateEmployee.corporate_id == corporate_id, CorporateEmployee.employee_id == employee_id.strip())
        )
        dup = (await self.db.execute(stmt_dup)).scalar_one_or_none()
        if dup:
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=f"Employee ID '{employee_id}' already exists.")

        # Check if email is associated with a platform user
        stmt_u = select(User).where(User.email == employee_email.strip().lower())
        matched_user = (await self.db.execute(stmt_u)).scalar_one_or_none()

        now = datetime.now(timezone.utc)
        emp = CorporateEmployee(
            corporate_id=corporate_id,
            user_id=matched_user.id if matched_user else None,
            employee_id=employee_id.strip(),
            employee_name=employee_name.strip(),
            employee_email=employee_email.strip().lower(),
            employee_phone=employee_phone.strip() if employee_phone else None,
            department=department or "General",
            cost_center=cost_center or "OPS-CORP",
            designation=designation or "Staff",
            status="ACTIVE",
            monthly_ride_limit=monthly_ride_limit,
            per_ride_limit_cents=per_ride_limit_cents,
            approval_required=approval_required,
            created_at=now,
            updated_at=now
        )
        self.db.add(emp)
        await self.db.commit()
        await self.db.refresh(emp)
        return {
            "id": emp.id,
            "employee_id": emp.employee_id,
            "employee_name": emp.employee_name,
            "status": emp.status,
            "message": "Employee successfully added to corporate account."
        }

    async def remove_employee(self, corporate_id: str, employee_record_id: str, user_id: str) -> Dict[str, Any]:
        await self._verify_admin_access(corporate_id, user_id)
        stmt = select(CorporateEmployee).where(
            and_(CorporateEmployee.corporate_id == corporate_id, CorporateEmployee.id == employee_record_id)
        )
        emp = (await self.db.execute(stmt)).scalar_one_or_none()
        if not emp:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Employee record not found.")

        emp.status = "INACTIVE"
        emp.updated_at = datetime.now(timezone.utc)
        await self.db.commit()
        return {"message": f"Employee '{emp.employee_name}' set to INACTIVE."}

    # -------------------------------------------------------------------------
    # 6. Corporate Ride Booking Policy Enforcement Engine
    # -------------------------------------------------------------------------
    async def validate_and_apply_corporate_ride(
        self,
        customer_user_id: str,
        category_id: str,
        estimated_fare_cents: int,
        business_cost_center: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Authoritative server-side validation for corporate ride bookings.
        Enforces membership, corporate account status, travel policy limits,
        curfew restrictions, credit line, and approval workflows.
        """
        # Find active corporate employee membership for this customer
        stmt_emp = select(CorporateEmployee).where(
            and_(CorporateEmployee.user_id == customer_user_id, CorporateEmployee.status == "ACTIVE")
        )
        emp = (await self.db.execute(stmt_emp)).scalar_one_or_none()
        if not emp:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="You do not have an active corporate employee membership. Please link or activate your corporate profile."
            )

        # Fetch corporate account
        stmt_corp = select(CorporateAccount).where(CorporateAccount.id == emp.corporate_id)
        corp = (await self.db.execute(stmt_corp)).scalar_one_or_none()
        if not corp:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Corporate account not found.")

        # Check account status
        if corp.status not in ("APPROVED", "ACTIVE"):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail=f"Corporate account '{corp.company_name}' is currently in '{corp.status}' status and cannot book corporate rides."
            )

        # Check credit limit and balance due
        projected_balance = (corp.balance_due_cents or 0) + estimated_fare_cents
        if corp.credit_limit_cents and projected_balance > corp.credit_limit_cents:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Corporate credit limit exceeded. Please contact your company administrator to clear outstanding invoices."
            )

        # Check per-ride limit for employee
        if emp.per_ride_limit_cents and estimated_fare_cents > emp.per_ride_limit_cents:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Ride fare ₹{estimated_fare_cents / 100:.2f} exceeds your individual corporate per-ride limit of ₹{emp.per_ride_limit_cents / 100:.2f}."
            )

        # Check corporate travel policy
        stmt_pol = select(CorporateTravelPolicy).where(
            and_(CorporateTravelPolicy.corporate_id == corp.id, CorporateTravelPolicy.is_active == True)
        )
        policy = (await self.db.execute(stmt_pol)).scalars().first()

        approval_status = "NOT_REQUIRED"
        if policy:
            # Policy fare ceiling
            if policy.max_fare_per_ride_cents and estimated_fare_cents > policy.max_fare_per_ride_cents:
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail=f"Estimated fare exceeds maximum corporate travel policy cap of ₹{policy.max_fare_per_ride_cents / 100:.2f}."
                )

            # Curfew hours check (HH:MM format)
            if policy.curfew_start_time and policy.curfew_end_time:
                now_local = datetime.now(timezone.utc) + timedelta(hours=5, minutes=30)  # IST
                now_str = now_local.strftime("%H:%M")
                if policy.curfew_start_time <= now_str <= policy.curfew_end_time:
                    raise HTTPException(
                        status_code=status.HTTP_403_FORBIDDEN,
                        detail=f"Corporate booking restricted during policy curfew hours ({policy.curfew_start_time} - {policy.curfew_end_time})."
                    )

            if policy.approval_required or emp.approval_required:
                approval_status = "PENDING_APPROVAL"
            else:
                approval_status = "APPROVED"

        cost_center = business_cost_center or emp.cost_center or corp.default_cost_center or "OPS-CORP"
        corp_ref = f"CORP-{corp.id[:8].upper()}-{emp.employee_id}"

        return {
            "corporate_id": corp.id,
            "corporate_employee_id": emp.id,
            "corporate_approval_status": approval_status,
            "business_cost_center": cost_center,
            "business_tax_id": corp.tax_id,
            "corporate_billing_ref": corp_ref,
            "company_name": corp.company_name
        }

    # -------------------------------------------------------------------------
    # 7. Monthly Consolidated Corporate Invoicing
    # -------------------------------------------------------------------------
    async def generate_consolidated_invoice(
        self,
        corporate_id: str,
        billing_period: str  # e.g. "2026-11"
    ) -> Dict[str, Any]:
        stmt_corp = select(CorporateAccount).where(CorporateAccount.id == corporate_id)
        corp = (await self.db.execute(stmt_corp)).scalar_one_or_none()
        if not corp:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Corporate account not found.")

        # Check for existing invoice for this period
        stmt_inv = select(CorporateInvoice).where(
            and_(CorporateInvoice.corporate_id == corporate_id, CorporateInvoice.billing_period == billing_period)
        )
        existing_inv = (await self.db.execute(stmt_inv)).scalar_one_or_none()
        if existing_inv:
            return await self.get_invoice_detail(existing_inv.id)

        # Retrieve completed corporate rides not yet invoiced
        stmt_rides = select(Ride).where(
            and_(
                Ride.corporate_id == corporate_id,
                Ride.status == "COMPLETED",
                Ride.invoice_number.is_(None)
            )
        ).order_by(Ride.completed_at)
        rides = (await self.db.execute(stmt_rides)).scalars().all()

        if not rides:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=f"No completed uninvoiced rides found for corporate account '{corp.company_name}' in period '{billing_period}'."
            )

        subtotal_cents = sum(r.final_fare_cents or r.estimated_fare_cents or 0 for r in rides)
        # 5% GST for passenger transport (CGST 2.5% + SGST 2.5% or IGST 5%)
        tax_cents = int(round(subtotal_cents * 0.05))
        platform_fee_cents = int(round(subtotal_cents * 0.02))  # 2% platform facilitation
        total_amount_cents = subtotal_cents + tax_cents + platform_fee_cents

        now = datetime.now(timezone.utc)
        inv_number = f"INV-CORP-{billing_period.replace('-', '')}-{uuid.uuid4().hex[:6].upper()}"

        invoice = CorporateInvoice(
            invoice_number=inv_number,
            corporate_id=corporate_id,
            billing_period=billing_period,
            subtotal_cents=subtotal_cents,
            tax_amount_cents=tax_cents,
            discount_cents=0,
            platform_fee_cents=platform_fee_cents,
            total_amount_cents=total_amount_cents,
            amount_paid_cents=0,
            status="ISSUED",
            pdf_url=f"/invoices/corporate/{inv_number}.pdf",
            due_date=now + timedelta(days=30),
            created_at=now,
            updated_at=now
        )
        self.db.add(invoice)
        await self.db.flush()

        # Add Line Items and stamp invoice number on rides
        for r in rides:
            item = CorporateInvoiceItem(
                invoice_id=invoice.id,
                ride_id=r.id,
                employee_id=r.corporate_employee_id,
                passenger_name=r.passenger_name or "Corporate Passenger",
                cost_center=r.business_cost_center or corp.default_cost_center,
                fare_cents=r.final_fare_cents or r.estimated_fare_cents or 0,
                tax_cents=int(round((r.final_fare_cents or r.estimated_fare_cents or 0) * 0.05)),
                created_at=now
            )
            self.db.add(item)
            r.invoice_number = inv_number

        # Update corporate balance due
        corp.balance_due_cents = (corp.balance_due_cents or 0) + total_amount_cents
        corp.updated_at = now

        await self.db.commit()
        await self.db.refresh(invoice)
        return await self.get_invoice_detail(invoice.id)

    # -------------------------------------------------------------------------
    # 8. List & Detail Invoices
    # -------------------------------------------------------------------------
    async def list_corporate_invoices(self, corporate_id: str, user_id: str) -> List[Dict[str, Any]]:
        stmt = select(CorporateInvoice).where(CorporateInvoice.corporate_id == corporate_id).order_by(desc(CorporateInvoice.created_at))
        res = await self.db.execute(stmt)
        invoices = res.scalars().all()
        return [
            {
                "id": inv.id,
                "invoice_number": inv.invoice_number,
                "billing_period": inv.billing_period,
                "subtotal_rupees": round((inv.subtotal_cents or 0) / 100, 2),
                "tax_rupees": round((inv.tax_amount_cents or 0) / 100, 2),
                "total_rupees": round((inv.total_amount_cents or 0) / 100, 2),
                "amount_paid_rupees": round((inv.amount_paid_cents or 0) / 100, 2),
                "status": inv.status,
                "pdf_url": inv.pdf_url,
                "due_date": inv.due_date.isoformat() if inv.due_date else None,
                "created_at": inv.created_at.isoformat() if inv.created_at else None
            }
            for inv in invoices
        ]

    async def get_invoice_detail(self, invoice_id: str) -> Dict[str, Any]:
        stmt = select(CorporateInvoice).where(CorporateInvoice.id == invoice_id)
        inv = (await self.db.execute(stmt)).scalar_one_or_none()
        if not inv:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Corporate invoice not found.")

        stmt_corp = select(CorporateAccount).where(CorporateAccount.id == inv.corporate_id)
        corp = (await self.db.execute(stmt_corp)).scalar_one_or_none()

        stmt_items = select(CorporateInvoiceItem).where(CorporateInvoiceItem.invoice_id == invoice_id)
        items = (await self.db.execute(stmt_items)).scalars().all()

        return {
            "id": inv.id,
            "invoice_number": inv.invoice_number,
            "corporate_id": inv.corporate_id,
            "company_name": corp.company_name if corp else "Corporate Client",
            "tax_id": corp.tax_id if corp else "N/A",
            "billing_address": corp.billing_address if corp else "N/A",
            "billing_period": inv.billing_period,
            "subtotal_rupees": round((inv.subtotal_cents or 0) / 100, 2),
            "tax_rupees": round((inv.tax_amount_cents or 0) / 100, 2),
            "platform_fee_rupees": round((inv.platform_fee_cents or 0) / 100, 2),
            "total_rupees": round((inv.total_amount_cents or 0) / 100, 2),
            "amount_paid_rupees": round((inv.amount_paid_cents or 0) / 100, 2),
            "outstanding_rupees": round(((inv.total_amount_cents or 0) - (inv.amount_paid_cents or 0)) / 100, 2),
            "status": inv.status,
            "pdf_url": inv.pdf_url,
            "due_date": inv.due_date.isoformat() if inv.due_date else None,
            "created_at": inv.created_at.isoformat() if inv.created_at else None,
            "line_items": [
                {
                    "id": item.id,
                    "ride_id": item.ride_id,
                    "employee_id": item.employee_id,
                    "passenger_name": item.passenger_name,
                    "cost_center": item.cost_center,
                    "fare_rupees": round(item.fare_cents / 100, 2),
                    "tax_rupees": round(item.tax_cents / 100, 2),
                    "total_rupees": round((item.fare_cents + item.tax_cents) / 100, 2)
                }
                for item in items
            ]
        }

    # -------------------------------------------------------------------------
    # Helper: Verify Admin Access
    # -------------------------------------------------------------------------
    async def _verify_admin_access(self, corporate_id: str, user_id: str):
        stmt = select(CorporateAccount).where(CorporateAccount.id == corporate_id)
        corp = (await self.db.execute(stmt)).scalar_one_or_none()
        if not corp:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Corporate account not found.")
        if corp.admin_user_id != user_id:
            # Check if user is an admin-designated employee
            stmt_emp = select(CorporateEmployee).where(
                and_(
                    CorporateEmployee.corporate_id == corporate_id,
                    CorporateEmployee.user_id == user_id,
                    CorporateEmployee.status == "ACTIVE"
                )
            )
            emp = (await self.db.execute(stmt_emp)).scalar_one_or_none()
            if not emp or "admin" not in (emp.designation or "").lower():
                raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Corporate administrator privileges required.")
