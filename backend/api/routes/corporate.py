from typing import Dict, Any, List, Optional
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession

from backend.infrastructure.database.session import get_db
from backend.domain.models import User
from backend.api.deps import get_current_customer
from backend.application.services.corporate_billing_service import CorporateBillingService

router = APIRouter(prefix="/corporate", tags=["Corporate Billing & B2B"])


class CorporateRegistrationPayload(BaseModel):
    company_name: str = Field(..., min_length=2, max_length=128)
    tax_id: str = Field(..., min_length=15, max_length=15, description="15-character statutory Indian GSTIN")
    billing_email: str = Field(..., min_length=5, max_length=128)
    billing_contact_name: Optional[str] = None
    billing_contact_phone: Optional[str] = None
    trade_name: Optional[str] = None
    registered_address: Optional[str] = None
    billing_address: Optional[str] = None
    default_cost_center: Optional[str] = "OPS-CORP"
    employee_id: Optional[str] = "EMP-001"
    department: Optional[str] = "Executive"


class CorporateUpdatePayload(BaseModel):
    company_name: Optional[str] = None
    billing_address: Optional[str] = None
    billing_contact_name: Optional[str] = None
    billing_contact_phone: Optional[str] = None
    default_cost_center: Optional[str] = None


class AddEmployeePayload(BaseModel):
    employee_id: str = Field(..., min_length=2, max_length=64)
    employee_name: str = Field(..., min_length=2, max_length=128)
    employee_email: str = Field(..., min_length=5, max_length=128)
    employee_phone: Optional[str] = None
    department: Optional[str] = "General"
    cost_center: Optional[str] = "OPS-CORP"
    designation: Optional[str] = "Staff"
    per_ride_limit_cents: int = 200000
    monthly_ride_limit: int = 50
    approval_required: bool = False


class GenerateInvoicePayload(BaseModel):
    billing_period: str = Field(..., pattern=r"^\d{4}-\d{2}$", description="Format: YYYY-MM e.g. 2026-11")


@router.post("/accounts", response_model=Dict[str, Any], status_code=status.HTTP_201_CREATED)
async def register_corporate_account(
    payload: CorporateRegistrationPayload,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = CorporateBillingService(db)
    return await service.register_account(
        user_id=user.id,
        company_name=payload.company_name,
        tax_id=payload.tax_id,
        billing_email=payload.billing_email,
        billing_contact_name=payload.billing_contact_name or user.full_name,
        billing_contact_phone=payload.billing_contact_phone or user.phone_number,
        trade_name=payload.trade_name,
        registered_address=payload.registered_address,
        billing_address=payload.billing_address,
        default_cost_center=payload.default_cost_center,
        employee_id=payload.employee_id,
        department=payload.department
    )


@router.get("/accounts/me", response_model=Dict[str, Any])
async def get_my_corporate_account(
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = CorporateBillingService(db)
    account = await service.get_user_corporate_account(user.id)
    if not account:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="No corporate account linked to this customer account. Please register a corporate profile."
        )
    return account


@router.put("/accounts/me", response_model=Dict[str, Any])
async def update_my_corporate_account(
    payload: CorporateUpdatePayload,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = CorporateBillingService(db)
    account = await service.get_user_corporate_account(user.id)
    if not account:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Corporate account not found.")

    return await service.update_account_profile(
        corporate_id=account["id"],
        user_id=user.id,
        company_name=payload.company_name,
        billing_address=payload.billing_address,
        billing_contact_name=payload.billing_contact_name,
        billing_contact_phone=payload.billing_contact_phone,
        default_cost_center=payload.default_cost_center
    )


@router.post("/accounts/me/submit", response_model=Dict[str, Any])
async def submit_corporate_account_for_approval(
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = CorporateBillingService(db)
    account = await service.get_user_corporate_account(user.id)
    if not account:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Corporate account not found.")

    return await service.submit_for_approval(corporate_id=account["id"], user_id=user.id)


@router.get("/accounts/me/employees", response_model=List[Dict[str, Any]])
async def list_corporate_employees(
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = CorporateBillingService(db)
    account = await service.get_user_corporate_account(user.id)
    if not account:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Corporate account not found.")

    return await service.list_employees(corporate_id=account["id"], user_id=user.id)


@router.post("/accounts/me/employees", response_model=Dict[str, Any], status_code=status.HTTP_201_CREATED)
async def add_corporate_employee(
    payload: AddEmployeePayload,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = CorporateBillingService(db)
    account = await service.get_user_corporate_account(user.id)
    if not account:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Corporate account not found.")

    return await service.add_employee(
        corporate_id=account["id"],
        user_id=user.id,
        employee_id=payload.employee_id,
        employee_name=payload.employee_name,
        employee_email=payload.employee_email,
        employee_phone=payload.employee_phone,
        department=payload.department,
        cost_center=payload.cost_center,
        designation=payload.designation,
        per_ride_limit_cents=payload.per_ride_limit_cents,
        monthly_ride_limit=payload.monthly_ride_limit,
        approval_required=payload.approval_required
    )


@router.delete("/accounts/me/employees/{employee_record_id}", response_model=Dict[str, Any])
async def remove_corporate_employee(
    employee_record_id: str,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = CorporateBillingService(db)
    account = await service.get_user_corporate_account(user.id)
    if not account:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Corporate account not found.")

    return await service.remove_employee(corporate_id=account["id"], employee_record_id=employee_record_id, user_id=user.id)


@router.get("/accounts/me/invoices", response_model=List[Dict[str, Any]])
async def list_corporate_invoices(
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = CorporateBillingService(db)
    account = await service.get_user_corporate_account(user.id)
    if not account:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Corporate account not found.")

    return await service.list_corporate_invoices(corporate_id=account["id"], user_id=user.id)


@router.get("/accounts/me/invoices/{invoice_id}", response_model=Dict[str, Any])
async def get_corporate_invoice_detail(
    invoice_id: str,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = CorporateBillingService(db)
    account = await service.get_user_corporate_account(user.id)
    if not account:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Corporate account not found.")

    invoice = await service.get_invoice_detail(invoice_id)
    if invoice["corporate_id"] != account["id"]:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Access denied to this corporate invoice.")
    return invoice


@router.post("/accounts/me/invoices/generate", response_model=Dict[str, Any])
async def generate_corporate_invoice(
    payload: GenerateInvoicePayload,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = CorporateBillingService(db)
    account = await service.get_user_corporate_account(user.id)
    if not account:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Corporate account not found.")

    return await service.generate_consolidated_invoice(
        corporate_id=account["id"],
        billing_period=payload.billing_period
    )

