from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from typing import Dict, Any, List, Optional
from backend.api.deps import get_db, get_current_user
from backend.domain.models.models import User
from backend.application.services.enterprise_growth_service import EnterpriseGrowthService

router = APIRouter(prefix="/enterprise", tags=["Enterprise, Corporate, Partnerships & Growth"])

# Features 476, 477, 478, 479, 480 & 489
@router.get("/corporate/accounts")
async def get_corporate_accounts(db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    accounts = await service.get_corporate_accounts()
    return [
        {
            "id": a.id,
            "company_name": a.company_name,
            "tax_id": a.tax_id,
            "billing_email": a.billing_email,
            "credit_limit_cents": a.credit_limit_cents,
            "credit_limit_rupees": a.credit_limit_cents / 100.0,
            "balance_due_cents": a.balance_due_cents,
            "balance_due_rupees": a.balance_due_cents / 100.0,
            "status": a.status
        }
        for a in accounts
    ]

@router.post("/corporate/accounts")
async def create_corporate_account(payload: Dict[str, Any], db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)):
    service = EnterpriseGrowthService(db)
    acc = await service.create_corporate_account(payload)
    return {"message": "Corporate account onboarded", "id": acc.id, "company_name": acc.company_name}

@router.get("/corporate/policies/{corporate_id}")
async def get_corporate_policies(corporate_id: str, db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    policies = await service.get_corporate_policies(corporate_id)
    return [
        {
            "id": p.id,
            "policy_name": p.policy_name,
            "max_fare_per_ride_cents": p.max_fare_per_ride_cents,
            "max_fare_per_ride_rupees": p.max_fare_per_ride_cents / 100.0,
            "curfew_start_time": p.curfew_start_time,
            "curfew_end_time": p.curfew_end_time,
            "is_active": p.is_active
        }
        for p in policies
    ]

@router.get("/corporate/invoices/{corporate_id}")
async def get_corporate_invoices(corporate_id: str, db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    invoices = await service.get_corporate_invoices(corporate_id)
    return [
        {
            "id": i.id,
            "invoice_number": i.invoice_number,
            "billing_period": i.billing_period,
            "total_amount_cents": i.total_amount_cents,
            "total_amount_rupees": i.total_amount_cents / 100.0,
            "status": i.status,
            "pdf_url": i.pdf_url,
            "due_date": i.due_date.isoformat() if i.due_date else None
        }
        for i in invoices
    ]

@router.get("/corporate/approvals")
async def get_approval_requests(db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    requests = await service.get_approval_requests()
    return [
        {
            "id": req.id,
            "ride_id": req.ride_id,
            "cost_center": req.cost_center,
            "project_code": req.project_code,
            "status": req.status,
            "justification": req.justification
        }
        for req in requests
    ]

# Features 481, 482, 483, 484 & 485
@router.get("/partners/accounts")
async def get_partner_accounts(db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    partners = await service.get_partner_accounts()
    return [
        {
            "id": p.id,
            "partner_type": p.partner_type,
            "partner_name": p.partner_name,
            "commission_rate_pct": p.commission_rate_pct,
            "referral_code": p.referral_code
        }
        for p in partners
    ]

@router.get("/partners/events")
async def get_event_workspaces(db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    events = await service.get_event_workspaces()
    return [
        {
            "id": e.id,
            "event_name": e.event_name,
            "venue_address": e.venue_address,
            "event_date": e.event_date.isoformat() if e.event_date else None,
            "allocated_vehicles_count": e.allocated_vehicles_count,
            "total_guests": e.total_guests,
            "status": e.status
        }
        for e in events
    ]

# Features 486, 487, 488, 490, 491, 492, 493 & 494
@router.get("/subscriptions/plans")
async def get_subscription_plans(db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    plans = await service.get_subscription_plans()
    return [
        {
            "id": p.id,
            "plan_code": p.plan_code,
            "name": p.name,
            "monthly_price_cents": p.monthly_price_cents,
            "monthly_price_rupees": p.monthly_price_cents / 100.0,
            "included_rides_count": p.included_rides_count,
            "category_code": p.category_code,
            "is_active": p.is_active
        }
        for p in plans
    ]

@router.get("/subscriptions/bundles")
async def get_prepaid_bundles(db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    bundles = await service.get_prepaid_bundles()
    return [
        {
            "id": b.id,
            "bundle_code": b.bundle_code,
            "title": b.title,
            "ride_count": b.ride_count,
            "price_cents": b.price_cents,
            "price_rupees": b.price_cents / 100.0,
            "discount_percentage": b.discount_percentage,
            "is_active": b.is_active
        }
        for b in bundles
    ]

@router.get("/products/airport-transfers")
async def get_airport_packages(db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    packages = await service.get_airport_packages()
    return [
        {
            "id": pkg.id,
            "airport_code": pkg.airport_code,
            "package_title": pkg.package_title,
            "category_code": pkg.category_code,
            "flat_fare_cents": pkg.flat_fare_cents,
            "flat_fare_rupees": pkg.flat_fare_cents / 100.0,
            "meets_and_greets_included": pkg.meets_and_greets_included
        }
        for pkg in packages
    ]

@router.get("/products/intercity")
async def get_intercity_products(db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    products = await service.get_intercity_products()
    return [
        {
            "id": prod.id,
            "route_name": prod.route_name,
            "origin_city": prod.origin_city,
            "destination_city": prod.destination_city,
            "distance_km": prod.distance_km,
            "fixed_fare_cents": prod.fixed_fare_cents,
            "fixed_fare_rupees": prod.fixed_fare_cents / 100.0,
            "toll_included": prod.toll_included
        }
        for prod in products
    ]

@router.get("/products/add-ons")
async def get_chauffeur_addons(db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    addons = await service.get_chauffeur_addons()
    return [
        {
            "id": add.id,
            "addon_code": add.addon_code,
            "title": add.title,
            "price_cents": add.price_cents,
            "price_rupees": add.price_cents / 100.0,
            "description": add.description,
            "category_compatibility": add.category_compatibility
        }
        for add in addons
    ]

# Features 495, 496 & 497
@router.get("/tenant/configs")
async def get_white_label_configs(db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    configs = await service.get_white_label_configs()
    return [
        {
            "id": cfg.id,
            "tenant_code": cfg.tenant_code,
            "brand_name": cfg.brand_name,
            "primary_color": cfg.primary_color,
            "secondary_color": cfg.secondary_color,
            "custom_domain": cfg.custom_domain
        }
        for cfg in configs
    ]

@router.get("/cities/configs")
async def get_city_configs(db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    cities = await service.get_city_configs()
    return [
        {
            "id": c.id,
            "city_code": c.city_code,
            "city_name": c.city_name,
            "country": c.country,
            "timezone": c.timezone,
            "tax_rate_percentage": c.tax_rate_percentage,
            "min_fare_cents": c.min_fare_cents,
            "min_fare_rupees": c.min_fare_cents / 100.0,
            "is_active": c.is_active
        }
        for c in cities
    ]

# Features 498, 499 & 500
@router.get("/growth/ab-experiments")
async def get_ab_experiments(db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    exps = await service.get_ab_experiments()
    return [
        {
            "id": e.id,
            "experiment_key": e.experiment_key,
            "name": e.name,
            "description": e.description,
            "variants": json.loads(e.variants_json) if e.variants_json else [],
            "rollout_status": e.rollout_status
        }
        for e in exps
    ]

@router.get("/growth/feature-requests")
async def get_feature_requests(db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    reqs = await service.get_feature_requests()
    return [
        {
            "id": r.id,
            "feature_key": r.feature_key,
            "title": r.title,
            "description": r.description,
            "category": r.category,
            "upvote_count": r.upvote_count,
            "status": r.status
        }
        for r in reqs
    ]

@router.post("/growth/feature-requests/{key}/vote")
async def vote_feature_request(key: str, db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    feat = await service.vote_feature_request(key)
    if not feat:
        raise HTTPException(status_code=404, detail="Feature request not found")
    return {"message": "Upvoted feature request", "feature_key": feat.feature_key, "upvote_count": feat.upvote_count}

@router.get("/growth/feedback-analytics")
async def get_feedback_analytics(db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    feedbacks = await service.get_feedback_analytics()
    return [
        {
            "id": f.id,
            "user_id": f.user_id,
            "rating_score": f.rating_score,
            "feedback_category": f.feedback_category,
            "comments": f.comments,
            "sentiment": f.sentiment,
            "created_at": f.created_at.isoformat() if f.created_at else None
        }
        for f in feedbacks
    ]
