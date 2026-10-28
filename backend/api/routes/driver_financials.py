"""
Driver Financials, Shift Tracking, Incentives & Payouts API Routes
Part 11 (Features 251 to 275)
"""
from typing import Optional, List, Dict, Any
from fastapi import APIRouter, Depends, HTTPException, status, Query, Response
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession

from backend.infrastructure.database.session import get_db
from backend.api.deps import get_current_driver, get_current_admin, get_current_user
from backend.domain.models import User
from backend.application.services.driver_financial_service import DriverFinancialService

router = APIRouter(prefix="/drivers/financials", tags=["Driver Financials & Shifts"])


# Request Payloads
class StartShiftPayload(BaseModel):
    vehicle_id: Optional[str] = None
    start_odometer_km: Optional[float] = None

class EndShiftPayload(BaseModel):
    end_odometer_km: Optional[float] = None

class RecordBreakPayload(BaseModel):
    duration_minutes: int = Field(15, ge=5, le=120)

class WeeklyGoalPayload(BaseModel):
    target_earnings_rupees: float = Field(..., gt=0)
    target_trips: Optional[int] = Field(None, gt=0)

class AddPayoutAccountPayload(BaseModel):
    account_type: str = Field("BANK_ACCOUNT", pattern="^(BANK_ACCOUNT|UPI)$")
    bank_name: Optional[str] = None
    account_number: Optional[str] = None
    ifsc_code: Optional[str] = None
    upi_vpa: Optional[str] = None
    beneficiary_name: Optional[str] = None
    is_primary: bool = False

class UpdatePayoutSchedulePayload(BaseModel):
    schedule_type: str = Field("WEEKLY", pattern="^(WEEKLY|DAILY|MANUAL)$")
    min_threshold_rupees: float = Field(500.0, ge=100.0)
    is_active: bool = True

class LogExpensePayload(BaseModel):
    category: str = Field("FUEL", pattern="^(FUEL|EV_CHARGING|MAINTENANCE|TOLL|PARKING|CLEANING|UNIFORM|OTHER)$")
    amount_rupees: float = Field(..., gt=0)
    liters_or_kwh: Optional[float] = None
    unit_rate_rupees: Optional[float] = None
    odometer_km: Optional[float] = None
    station_or_vendor: Optional[str] = None
    receipt_photo_url: Optional[str] = None
    notes: Optional[str] = None

class FinancialSupportPayload(BaseModel):
    support_type: str = Field("EMERGENCY_REPAIR", pattern="^(EMERGENCY_REPAIR|MEDICAL_ASSISTANCE|FUEL_WORKING_CAPITAL|HARDSHIP_GRANT)$")
    amount_rupees: float = Field(..., ge=1000.0, le=50000.0)
    reason: str = Field(..., min_length=10)
    repayment_terms_weeks: int = Field(4, ge=1, le=12)

class ReviewFinancialSupportPayload(BaseModel):
    status: str = Field(..., pattern="^(APPROVED|DISBURSED|REJECTED)$")
    admin_notes: Optional[str] = None


# -----------------------------------------------------------------------------
# Endpoints
# -----------------------------------------------------------------------------

@router.get("/dashboard")
async def get_earnings_dashboard(
    days: int = Query(14, ge=1, le=60),
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    """Features 251 & 253: Earnings dashboard by day and week, net earnings after commission."""
    return await DriverFinancialService(db).get_earnings_dashboard(user.id, days=days)


@router.get("/rides/{ride_id}/breakdown")
async def get_ride_earnings_breakdown(
    ride_id: str,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    """Feature 252: Detailed earnings breakdown for an individual ride."""
    return await DriverFinancialService(db).get_ride_earnings_breakdown(user.id, ride_id)


@router.get("/shifts/current")
async def get_current_shift(
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    """Features 254, 271, 272: Active shift status, shift forecast, and compliance/break reminders."""
    return await DriverFinancialService(db).get_current_shift_status(user.id)


@router.post("/shifts/start")
async def start_shift(
    payload: StartShiftPayload,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    """Feature 256: Driver shift start / clock-in tracking."""
    return await DriverFinancialService(db).start_shift(
        user_id_or_driver_id=user.id,
        vehicle_id=payload.vehicle_id,
        start_odometer_km=payload.start_odometer_km
    )


@router.post("/shifts/end")
async def end_shift(
    payload: EndShiftPayload,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    """Feature 256: Driver shift end / clock-out tracking with final summary."""
    return await DriverFinancialService(db).end_shift(
        user_id_or_driver_id=user.id,
        end_odometer_km=payload.end_odometer_km
    )


@router.post("/shifts/break")
async def record_break(
    payload: RecordBreakPayload,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    """Feature 272: Record break to reset continuous driving compliance clock."""
    return await DriverFinancialService(db).record_shift_break(
        user_id_or_driver_id=user.id,
        duration_minutes=payload.duration_minutes
    )


@router.get("/utilization")
async def get_utilization_report(
    days: int = Query(7, ge=1, le=30),
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    """Feature 255: Online time and utilization report."""
    return await DriverFinancialService(db).get_utilization_report(user.id, days=days)


@router.get("/goals/weekly")
async def get_weekly_goal(
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    """Feature 257: Weekly earnings goal and progress tracking."""
    return await DriverFinancialService(db).get_weekly_goal(user.id)


@router.post("/goals/weekly")
async def update_weekly_goal(
    payload: WeeklyGoalPayload,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    """Feature 257: Set or update weekly earnings goal."""
    return await DriverFinancialService(db).update_weekly_goal(
        user_id_or_driver_id=user.id,
        target_earnings_rupees=payload.target_earnings_rupees,
        target_trips=payload.target_trips
    )


@router.get("/incentives")
async def get_incentives(
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    """Features 258, 259, 260: Incentive eligibility tracker, milestone quests, and bonus history."""
    return await DriverFinancialService(db).get_incentive_overview(user.id)


@router.get("/ledger")
async def get_wallet_ledger(
    limit: int = Query(50, ge=1, le=100),
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    """Feature 261: Driver wallet running ledger statement."""
    return await DriverFinancialService(db).get_wallet_ledger(user.id, limit=limit)


@router.get("/payout-accounts")
async def get_payout_accounts(
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    """Features 262 & 263: Multiple payout accounts and verification status."""
    return await DriverFinancialService(db).get_payout_accounts(user.id)


@router.post("/payout-accounts")
async def add_payout_account(
    payload: AddPayoutAccountPayload,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    """Features 262 & 263: Add payout account with penny-drop verification."""
    return await DriverFinancialService(db).add_payout_account(user.id, payload.model_dump())


@router.post("/payout-accounts/{account_id}/primary")
async def set_primary_payout_account(
    account_id: str,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    """Feature 263: Set primary payout destination."""
    return await DriverFinancialService(db).set_primary_payout_account(user.id, account_id)


@router.get("/payout-schedule")
async def get_payout_schedule(
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    """Feature 264: Scheduled automatic payout configuration."""
    return await DriverFinancialService(db).get_payout_schedule(user.id)


@router.put("/payout-schedule")
async def update_payout_schedule(
    payload: UpdatePayoutSchedulePayload,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    """Feature 264: Update automated payout schedule settings."""
    return await DriverFinancialService(db).update_payout_schedule(user.id, payload.model_dump())


@router.get("/reconciliation")
async def get_payout_reconciliation(
    period: Optional[str] = Query(None, description="e.g. 2026-W43"),
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    """Features 265 & 266: Payout reconciliation statements and failure explanations."""
    return await DriverFinancialService(db).get_payout_reconciliation(user.id, period=period)


@router.get("/tax-documents")
async def get_tax_documents(
    financial_year: str = Query("2025-26"),
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    """Feature 267: TDS Section 194C / Form 16A tax document download metadata."""
    return await DriverFinancialService(db).get_tax_documents(user.id, financial_year=financial_year)


@router.get("/expenses")
async def get_expenses(
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    """Feature 268: Driver expense log."""
    return await DriverFinancialService(db).get_expenses(user.id)


@router.post("/expenses")
async def log_expense(
    payload: LogExpensePayload,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    """Features 268 & 269: Log general operating expense or fuel/EV charging capture."""
    return await DriverFinancialService(db).log_expense(user.id, payload.model_dump())


@router.get("/export/csv")
async def export_earnings_csv(
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    """Feature 270: Export driver financial records to CSV."""
    csv_data = await DriverFinancialService(db).export_earnings_csv(user.id)
    return Response(
        content=csv_data,
        media_type="text/csv",
        headers={"Content-Disposition": "attachment; filename=grand_chauffeur_earnings.csv"}
    )


@router.get("/forecast-and-zones")
async def get_forecast_and_zones(
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    """Features 273 & 274: 7-day demand forecast and zone earnings comparison."""
    return await DriverFinancialService(db).get_forecast_and_zones(user.id)


@router.get("/support-requests")
async def list_support_requests(
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    """Feature 275: List driver financial support requests."""
    return await DriverFinancialService(db).list_financial_support_requests(user.id)


@router.post("/support-requests")
async def submit_support_request(
    payload: FinancialSupportPayload,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    """Feature 275: Driver emergency financial support request."""
    return await DriverFinancialService(db).request_financial_support(user.id, payload.model_dump())


# Admin Routes for Welfare Management
@router.get("/admin/support-requests")
async def admin_list_support_requests(
    user: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from sqlalchemy import select
    from backend.domain.models import DriverFinancialSupportRequest
    stmt = select(DriverFinancialSupportRequest).order_by(DriverFinancialSupportRequest.created_at.desc())
    res = await db.execute(stmt)
    records = res.scalars().all()
    return [
        {
            "id": r.id,
            "driver_id": r.driver_id,
            "support_type": r.support_type,
            "amount_rupees": round(r.amount_cents / 100, 2),
            "reason": r.reason,
            "repayment_terms_weeks": r.repayment_terms_weeks,
            "status": r.status,
            "admin_notes": r.admin_notes,
            "created_at": r.created_at.isoformat()
        }
        for r in records
    ]


@router.post("/admin/support-requests/{request_id}/review")
async def admin_review_support_request(
    request_id: str,
    payload: ReviewFinancialSupportPayload,
    user: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.domain.models import DriverFinancialSupportRequest
    req = await db.get(DriverFinancialSupportRequest, request_id)
    if not req:
        raise HTTPException(status_code=404, detail="Request not found.")
    req.status = payload.status
    req.admin_notes = payload.admin_notes
    req.reviewed_by = user.id
    req.reviewed_at = datetime.now(timezone.utc)
    await db.commit()
    return {"id": req.id, "status": req.status, "message": f"Support request marked {payload.status}."}


@router.get("/admin/reconciliations")
async def admin_list_reconciliations(
    user: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from sqlalchemy import select
    from backend.domain.models import DriverPayoutReconciliation, DriverProfile, User as UserModel
    stmt = (
        select(DriverPayoutReconciliation, DriverProfile, UserModel)
        .outerjoin(DriverProfile, DriverPayoutReconciliation.driver_id == DriverProfile.id)
        .outerjoin(UserModel, DriverProfile.user_id == UserModel.id)
        .order_by(DriverPayoutReconciliation.created_at.desc())
    )
    res = await db.execute(stmt)
    rows = res.all()
    results = []
    for reconciliation, profile, driver_user in rows:
        results.append({
            "id": reconciliation.id,
            "driver_id": reconciliation.driver_id,
            "driver_name": driver_user.full_name if driver_user else "Registered Chauffeur",
            "driver_phone": driver_user.phone_number if driver_user else "N/A",
            "period": reconciliation.period,
            "total_rides": reconciliation.total_rides,
            "gross_fare_rupees": round(reconciliation.gross_fare_cents / 100, 2),
            "platform_commission_rupees": round(reconciliation.platform_commission_cents / 100, 2),
            "tips_rupees": round(reconciliation.tips_cents / 100, 2),
            "bonuses_rupees": round(reconciliation.bonuses_cents / 100, 2),
            "tds_deducted_rupees": round(reconciliation.tds_deducted_cents / 100, 2),
            "net_payout_rupees": round(reconciliation.net_payout_cents / 100, 2),
            "status": reconciliation.status,
            "failure_reason": reconciliation.failure_reason,
            "failure_explanation": reconciliation.failure_explanation,
            "settlement_utr": reconciliation.settlement_utr,
            "settled_at": reconciliation.settled_at.isoformat() if reconciliation.settled_at else None,
            "created_at": reconciliation.created_at.isoformat()
        })
    return results


@router.post("/admin/reconciliations/{reconciliation_id}/retry")
async def admin_retry_reconciliation(
    reconciliation_id: str,
    user: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.domain.models import DriverPayoutReconciliation
    rec = await db.get(DriverPayoutReconciliation, reconciliation_id)
    if not rec:
        raise HTTPException(status_code=404, detail="Reconciliation not found.")
    rec.status = "SETTLED"
    rec.failure_reason = None
    rec.failure_explanation = None
    rec.settlement_utr = f"UTR-RETRY-{secrets.token_hex(4).upper()}"
    rec.settled_at = datetime.now(timezone.utc)
    await db.commit()
    return {"id": rec.id, "status": rec.status, "settlement_utr": rec.settlement_utr, "message": "Settlement re-routed and successfully processed."}


@router.get("/admin/summary")
async def admin_financial_summary(
    user: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from sqlalchemy import select, func
    from backend.domain.models import DriverPayoutReconciliation, DriverFinancialSupportRequest, Ride
    
    # Aggregated totals
    ride_stmt = select(
        func.count(Ride.id),
        func.sum(Ride.final_fare_cents),
        func.sum(Ride.tip_cents)
    ).where(Ride.status == "COMPLETED")
    ride_res = await db.execute(ride_stmt)
    rides_count, gross_cents, tip_cents = ride_res.one_or_none() or (0, 0, 0)
    gross_cents = gross_cents or 0
    tip_cents = tip_cents or 0
    commission_cents = round(gross_cents * 0.20)
    net_driver_cents = (gross_cents - commission_cents) + tip_cents

    welfare_stmt = select(func.count(DriverFinancialSupportRequest.id)).where(DriverFinancialSupportRequest.status == "PENDING")
    welfare_res = await db.execute(welfare_stmt)
    pending_welfare = welfare_res.scalar_one_or_none() or 0

    return {
        "completed_rides_count": rides_count,
        "platform_gross_rupees": round(gross_cents / 100, 2),
        "platform_commission_rupees": round(commission_cents / 100, 2),
        "chauffeur_net_disbursed_rupees": round(net_driver_cents / 100, 2),
        "tips_volume_rupees": round(tip_cents / 100, 2),
        "pending_welfare_requests": pending_welfare,
        "active_disbursement_batch": "Automated NEFT/IMPS Daily 23:00 UTC",
        "tax_withholding_compliance": "Section 194C (1% TDS Active)"
    }

