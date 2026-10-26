from typing import Optional, List
from datetime import datetime, timezone, timedelta
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func

from backend.infrastructure.database.session import get_db
from backend.api.deps import get_current_user, get_current_customer, get_current_admin
from backend.domain.models import User, PromoCode, RidePass, CustomerProfile, ReferralRecord
from backend.application.services.promo_pass_service import PromoPassService

router = APIRouter(tags=["Promos, Passes & Referrals"])

class PromoValidateRequest(BaseModel):
    code: str = Field(..., json_schema_extra={"example": "ROYAL100"})
    category_id: Optional[str] = None
    fare_cents: int = Field(..., gt=0, json_schema_extra={"example": 100000})

class PassPurchaseRequest(BaseModel):
    pass_id: str
    payment_method: str = "WALLET"

class ReferralClaimRequest(BaseModel):
    referral_code: str = Field(..., json_schema_extra={"example": "REF-LUXURY77"})

class PromoCreateRequest(BaseModel):
    code: str
    discount_type: str = "PERCENTAGE"  # PERCENTAGE or FIXED_AMOUNT
    discount_value: float = 20.0
    max_discount_cents: int = 50000
    minimum_fare_cents: int = 100000
    usage_limit_total: Optional[int] = 1000
    usage_limit_per_customer: int = 2
    applicable_category_id: Optional[str] = None
    valid_days: int = 30

class PassCreateRequest(BaseModel):
    title: str
    description: str
    price_cents: int
    validity_days: int
    max_rides: int
    discount_percentage: float
    max_discount_per_ride_cents: int

# ---------------- PROMOS ----------------
@router.post("/promos/validate")
async def validate_promo(
    payload: PromoValidateRequest,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    return await PromoPassService.validate_promo_code(
        session=db,
        code_str=payload.code,
        customer_user_id=user.id,
        category_id=payload.category_id,
        fare_cents=payload.fare_cents
    )

@router.post("/admin/promos")
async def create_promo_code(
    payload: PromoCreateRequest,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    now = datetime.now(timezone.utc)
    promo = PromoCode(
        code=payload.code.strip().upper(),
        discount_type=payload.discount_type,
        discount_value=payload.discount_value,
        max_discount_cents=payload.max_discount_cents,
        min_fare_cents=payload.minimum_fare_cents,
        total_usage_limit=payload.usage_limit_total or 1000,
        per_customer_limit=payload.usage_limit_per_customer,
        vehicle_category_id=payload.applicable_category_id,
        starts_at=now,
        expires_at=now + timedelta(days=payload.valid_days),
        is_active=True
    )
    db.add(promo)
    await db.commit()
    await db.refresh(promo)
    return {"status": "CREATED", "promo_id": promo.id, "code": promo.code}

# ---------------- PASSES ----------------
@router.get("/passes/catalog")
async def get_pass_catalog(db: AsyncSession = Depends(get_db)):
    passes = await PromoPassService.list_pass_catalog(db)
    return [
        {
            "id": p.id,
            "code": p.code,
            "name": p.name,
            "title": p.name,
            "description": p.description,
            "price_cents": p.price_cents,
            "validity_days": p.validity_days,
            "total_rides": p.total_rides,
            "max_rides": p.total_rides,
            "discount_percentage": p.discount_percentage,
            "max_discount_per_ride_cents": p.max_discount_per_ride_cents
        }
        for p in passes
    ]

@router.post("/passes/purchase")
async def purchase_pass(
    payload: PassPurchaseRequest,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    user_pass = await PromoPassService.purchase_ride_pass(
        session=db,
        customer_user_id=user.id,
        pass_id=payload.pass_id,
        payment_method=payload.payment_method
    )
    await db.commit()
    return {
        "status": "ACTIVE",
        "customer_pass_id": user_pass.id,
        "rides_remaining": user_pass.rides_remaining,
        "valid_until": user_pass.valid_until.isoformat(),
        "expires_at": user_pass.valid_until.isoformat()
    }

@router.get("/passes/my-passes")
async def get_my_passes(
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    passes = await PromoPassService.get_customer_passes(db, user.id)
    return [
        {
            "id": p.id,
            "pass_id": p.pass_id,
            "status": p.status,
            "rides_remaining": p.rides_remaining,
            "valid_from": p.valid_from.isoformat() if p.valid_from else None,
            "valid_until": p.valid_until.isoformat() if p.valid_until else None,
            "expires_at": p.valid_until.isoformat() if p.valid_until else None
        }
        for p in passes
    ]

@router.post("/admin/passes")
async def admin_create_pass(
    payload: PassCreateRequest,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    import uuid
    ride_pass = RidePass(
        code=f"PASS-{uuid.uuid4().hex[:6].upper()}",
        name=payload.title,
        description=payload.description,
        price_cents=payload.price_cents,
        validity_days=payload.validity_days,
        total_rides=payload.max_rides,
        discount_percentage=payload.discount_percentage,
        max_discount_per_ride_cents=payload.max_discount_per_ride_cents,
        is_active=True
    )
    db.add(ride_pass)
    await db.commit()
    await db.refresh(ride_pass)
    return {"status": "CREATED", "pass_id": ride_pass.id, "name": ride_pass.name}

# ---------------- REFERRALS ----------------
@router.get("/referrals/my-code")
async def get_my_referral_info(
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    stmt = select(CustomerProfile).where(CustomerProfile.user_id == user.id)
    res = await db.execute(stmt)
    prof = res.scalars().first()
    code = prof.referral_code if prof and prof.referral_code else f"LUX-{user.id[:6].upper()}"

    records = []
    if prof:
        refs = await db.execute(
            select(ReferralRecord).where(ReferralRecord.referrer_id == prof.id)
        )
        records = refs.scalars().all()

    rewarded_records = [r for r in records if r.reward_status == "CREDITED"]
    total_earned = sum(r.referrer_reward_cents for r in rewarded_records)

    return {
        "referral_code": code,
        "total_referrals": len(records),
        "successful_referrals": len(rewarded_records),
        "total_earned_cents": total_earned,
        "reward_per_referral_cents": 50000,
        "share_message": f"Join me on Luxury Chauffeur using my code {code} to get ₹500 ride credits!"
    }

@router.post("/referrals/claim")
async def claim_referral(
    payload: ReferralClaimRequest,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    rec = await PromoPassService.claim_referral(db, user.id, payload.referral_code)
    await db.commit()
    return {
        "status": "CLAIMED",
        "referral_id": rec.id,
        "reward_cents": rec.referred_reward_cents,
        "reward_trigger": "FIRST_COMPLETED_TRIP"
    }
