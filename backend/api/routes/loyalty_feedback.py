from datetime import datetime
from typing import Optional, List, Dict, Any
from fastapi import APIRouter, Depends, HTTPException, Query, status
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession

from backend.infrastructure.database.session import get_db
from backend.api.deps import get_current_customer, get_current_admin, get_current_user
from backend.domain.models import User
from backend.application.services.loyalty_feedback_service import LoyaltyFeedbackService

router = APIRouter(prefix="/loyalty-feedback", tags=["Part 7: Loyalty, Feedback & Recovery"])

# ----------------- Request / Response Models -----------------

class DriverRatingRequest(BaseModel):
    overall_rating: int = Field(..., ge=1, le=5)
    cleanliness_score: int = Field(5, ge=1, le=5)
    comfort_score: int = Field(5, ge=1, le=5)
    service_quality_score: int = Field(5, ge=1, le=5)
    navigation_score: int = Field(5, ge=1, le=5)
    feedback: Optional[str] = None
    private_feedback: Optional[str] = None
    feedback_tags: Optional[List[str]] = Field(default_factory=list)
    tip_cents: int = Field(0, ge=0)

class TipDriverRequest(BaseModel):
    tip_cents: int = Field(..., gt=0)

class PointsRedeemRequest(BaseModel):
    points_to_redeem: int = Field(..., ge=100)

class CelebrationDatesRequest(BaseModel):
    birthday_date: Optional[str] = None  # YYYY-MM-DD
    anniversary_date: Optional[str] = None  # YYYY-MM-DD

class OfferFrequencyRequest(BaseModel):
    frequency: str = Field(..., json_schema_extra={"example": "WEEKLY"})  # DAILY, WEEKLY, EXCLUSIVE_ONLY, MUTED

class SurveySubmitRequest(BaseModel):
    ride_id: Optional[str] = None
    survey_type: str = "POST_TRIP_CSAT"
    score: int = Field(..., ge=1, le=10)
    dimensions: Optional[Dict[str, int]] = None
    comments: Optional[str] = None

class ReferralReviewRequest(BaseModel):
    decision: str = Field(..., json_schema_extra={"example": "APPROVE"})  # APPROVE, REJECT
    notes: str = Field(..., min_length=3)

class ServiceRecoveryRequest(BaseModel):
    customer_id: str
    amount_cents: int = Field(..., gt=0)
    reason_category: str = Field(..., json_schema_extra={"example": "LATE_ARRIVAL"})
    notes: str = Field(..., min_length=3)
    ride_id: Optional[str] = None


# ----------------- Endpoints -----------------

# Features 151 - 155: Driver Rating & Categorical Feedback
@router.post("/rides/{ride_id}/rate")
async def submit_driver_rating(
    ride_id: str,
    payload: DriverRatingRequest,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = LoyaltyFeedbackService(db)
    return await service.submit_driver_rating(
        customer_user_id=user.id,
        ride_id=ride_id,
        overall_rating=payload.overall_rating,
        cleanliness_score=payload.cleanliness_score,
        comfort_score=payload.comfort_score,
        service_quality_score=payload.service_quality_score,
        navigation_score=payload.navigation_score,
        feedback=payload.feedback,
        private_feedback=payload.private_feedback,
        feedback_tags=payload.feedback_tags,
        tip_cents=payload.tip_cents
    )

# Features 156, 157: Tip Driver After Ride & Adjustment Window
@router.post("/rides/{ride_id}/tip")
async def tip_driver(
    ride_id: str,
    payload: TipDriverRequest,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = LoyaltyFeedbackService(db)
    return await service.tip_driver(
        customer_user_id=user.id,
        ride_id=ride_id,
        tip_cents=payload.tip_cents
    )

# Features 158, 160, 161: Loyalty Summary & Tier Benefits
@router.get("/loyalty/summary")
async def get_loyalty_summary(
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = LoyaltyFeedbackService(db)
    return await service.get_loyalty_summary(user.id)

# Feature 158: Loyalty Points Ledger
@router.get("/loyalty/ledger")
async def get_loyalty_ledger(
    limit: int = Query(50, ge=1, le=100),
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = LoyaltyFeedbackService(db)
    return await service.get_loyalty_ledger(user.id, limit=limit)

# Feature 162: Points Redemption for Ride Credit
@router.post("/loyalty/redeem")
async def redeem_points_for_credit(
    payload: PointsRedeemRequest,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = LoyaltyFeedbackService(db)
    return await service.redeem_points_for_credit(user.id, payload.points_to_redeem)

# Features 166, 167: Customer Milestones, Badges & Streaks
@router.get("/milestones")
async def get_customer_milestones(
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = LoyaltyFeedbackService(db)
    return await service.get_customer_milestones(user.id)

# Features 163, 164: Referral Dashboard & Eligibility
@router.get("/referrals/dashboard")
async def get_referral_dashboard(
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = LoyaltyFeedbackService(db)
    return await service.get_referral_dashboard(user.id)

# Feature 165: Admin Referral Fraud Review Queue
@router.get("/admin/referrals/fraud-queue")
async def get_admin_referral_fraud_queue(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    service = LoyaltyFeedbackService(db)
    return await service.get_admin_referral_fraud_queue()

@router.post("/admin/referrals/{referral_id}/review")
async def review_referral_fraud(
    referral_id: str,
    payload: ReferralReviewRequest,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    service = LoyaltyFeedbackService(db)
    return await service.review_referral_fraud(
        admin_user_id=admin.id,
        referral_id=referral_id,
        decision=payload.decision,
        notes=payload.notes
    )

# Features 168, 169: Personalized Offers Feed
@router.get("/offers")
async def get_personalized_offers(
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = LoyaltyFeedbackService(db)
    return await service.get_personalized_offers(user.id)

# Feature 168: Birthday or Anniversary Offer Setup
@router.post("/profile/celebrations")
async def update_celebration_dates(
    payload: CelebrationDatesRequest,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = LoyaltyFeedbackService(db)
    return await service.update_celebration_dates(
        customer_user_id=user.id,
        birthday_date=payload.birthday_date,
        anniversary_date=payload.anniversary_date
    )

# Feature 170: Offer Notification Frequency Control
@router.post("/profile/offer-preferences")
async def update_offer_notification_frequency(
    payload: OfferFrequencyRequest,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = LoyaltyFeedbackService(db)
    return await service.update_offer_notification_frequency(
        customer_user_id=user.id,
        frequency=payload.frequency
    )

# Feature 171: Ride History Filters
@router.get("/rides/history")
async def get_filtered_ride_history(
    status: Optional[str] = None,
    date_from: Optional[datetime] = None,
    date_to: Optional[datetime] = None,
    limit: int = Query(50, ge=1, le=100),
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = LoyaltyFeedbackService(db)
    return await service.get_filtered_ride_history(
        customer_user_id=user.id,
        status_filter=status,
        date_from=date_from,
        date_to=date_to,
        limit=limit
    )

# Feature 172: Repeat a Past Trip
@router.post("/rides/{ride_id}/repeat")
async def repeat_past_trip(
    ride_id: str,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = LoyaltyFeedbackService(db)
    return await service.repeat_past_trip(user.id, ride_id)

# Feature 173: Download Monthly Ride Statement
@router.get("/statements/monthly")
async def get_monthly_statement(
    year: int = Query(..., ge=2024, le=2030),
    month: int = Query(..., ge=1, le=12),
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = LoyaltyFeedbackService(db)
    return await service.generate_monthly_statement(user.id, year=year, month=month)

# Feature 174: Customer Satisfaction Survey
@router.post("/surveys")
async def submit_survey(
    payload: SurveySubmitRequest,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = LoyaltyFeedbackService(db)
    return await service.submit_survey(
        customer_user_id=user.id,
        ride_id=payload.ride_id,
        survey_type=payload.survey_type,
        score=payload.score,
        dimensions=payload.dimensions,
        comments=payload.comments
    )

# Feature 175: Service Recovery Credit Issuance Flow (Admin)
@router.post("/admin/service-recovery")
async def issue_service_recovery_credit(
    payload: ServiceRecoveryRequest,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    service = LoyaltyFeedbackService(db)
    return await service.issue_service_recovery_credit(
        admin_user_id=admin.id,
        customer_id=payload.customer_id,
        amount_cents=payload.amount_cents,
        reason_category=payload.reason_category,
        notes=payload.notes,
        ride_id=payload.ride_id
    )

@router.get("/admin/service-recovery/history")
async def get_service_recovery_history(
    limit: int = Query(50, ge=1, le=100),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    service = LoyaltyFeedbackService(db)
    return await service.get_service_recovery_history(limit=limit)
