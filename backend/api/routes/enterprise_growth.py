import json
from fastapi import APIRouter, Depends, HTTPException, Header, status
from sqlalchemy.ext.asyncio import AsyncSession
from typing import Dict, Any, List, Optional
from backend.api.deps import get_db, get_current_user
from backend.domain.models.models import User
from backend.application.services.enterprise_growth_service import EnterpriseGrowthService

router = APIRouter(prefix="/enterprise", tags=["Enterprise, Corporate, Partnerships & Growth"])

# =============================================================================
# 476, 477, 478, 479, 480 & 489. Corporate Accounts, Employees, Policies & Invoices
# =============================================================================

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

@router.get("/corporate/accounts/{id}")
async def get_corporate_account(id: str, db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    acc = await service.get_corporate_account(id)
    if not acc:
        raise HTTPException(status_code=404, detail="Corporate account not found")
    return {
        "id": acc.id,
        "company_name": acc.company_name,
        "tax_id": acc.tax_id,
        "billing_email": acc.billing_email,
        "credit_limit_rupees": acc.credit_limit_cents / 100.0,
        "balance_due_rupees": acc.balance_due_cents / 100.0,
        "status": acc.status
    }

@router.post("/corporate/accounts")
async def create_corporate_account(payload: Dict[str, Any], db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    acc = await service.create_corporate_account(payload)
    return {"message": "Corporate account onboarded", "id": acc.id, "company_name": acc.company_name}

@router.patch("/corporate/accounts/{id}")
async def update_corporate_account(id: str, payload: Dict[str, Any], db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    acc = await service.update_corporate_account(id, payload)
    if not acc:
        raise HTTPException(status_code=404, detail="Corporate account not found")
    return {"message": "Corporate account updated", "id": acc.id, "status": acc.status}

@router.get("/corporate/employees/{corporate_id}")
async def get_corporate_employees(corporate_id: str, db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    employees = await service.get_corporate_employees(corporate_id)
    return [
        {
            "id": e.id,
            "corporate_id": e.corporate_id,
            "user_id": e.user_id,
            "cost_center": e.cost_center,
            "max_ride_fare_rupees": e.max_ride_fare_cents / 100.0,
            "requires_approval": e.requires_approval,
            "status": e.status
        }
        for e in employees
    ]

@router.post("/corporate/employees")
async def enroll_employee(payload: Dict[str, Any], db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    emp = await service.enroll_employee(payload)
    return {"message": "Employee enrolled in corporate portal", "id": emp.id, "cost_center": emp.cost_center}

@router.get("/corporate/my-profile")
async def get_my_corporate_profile(
    db: AsyncSession = Depends(get_db),
    user_id: Optional[str] = None
):
    service = EnterpriseGrowthService(db)
    uid = user_id or "default_user_1"
    return await service.get_employee_profile_by_user(uid)

@router.get("/corporate/cost-centers/{corporate_id}")
async def get_cost_centers_summary(corporate_id: str, db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    return await service.get_cost_centers_summary(corporate_id)

@router.get("/corporate/policies/{corporate_id}")
async def get_corporate_policies(corporate_id: str, db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    policies = await service.get_corporate_policies(corporate_id)
    return [
        {
            "id": p.id,
            "corporate_id": p.corporate_id,
            "policy_name": p.policy_name,
            "allowed_categories": json.loads(p.allowed_categories_json) if p.allowed_categories_json else [],
            "max_fare_per_ride_rupees": p.max_fare_per_ride_cents / 100.0,
            "curfew_start_time": p.curfew_start_time,
            "curfew_end_time": p.curfew_end_time,
            "is_active": p.is_active
        }
        for p in policies
    ]

@router.post("/corporate/policies")
async def create_corporate_policy(payload: Dict[str, Any], db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    corp_id = payload.get("corporate_id", "corp_default")
    pol = await service.create_corporate_policy(corp_id, payload)
    return {"message": "Travel policy saved", "id": pol.id, "policy_name": pol.policy_name}

@router.post("/corporate/policies/validate")
async def validate_policy_compliance(payload: Dict[str, Any], db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    corp_id = payload.get("corporate_id", "corp_default")
    cat = payload.get("category", "SEDAN")
    fare = int(payload.get("fare_cents", 250000))
    return await service.validate_travel_policy(corp_id, cat, fare)

@router.get("/corporate/invoices/{corporate_id}")
async def get_corporate_invoices(corporate_id: str, db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    invoices = await service.get_corporate_invoices(corporate_id)
    return [
        {
            "id": i.id,
            "invoice_number": i.invoice_number,
            "corporate_id": i.corporate_id,
            "billing_period": i.billing_period,
            "total_amount_rupees": i.total_amount_cents / 100.0,
            "status": i.status,
            "pdf_url": i.pdf_url,
            "due_date": i.due_date.isoformat() if i.due_date else None
        }
        for i in invoices
    ]

@router.post("/corporate/invoices/generate")
async def generate_corporate_invoice(payload: Dict[str, Any], db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    corp_id = payload.get("corporate_id", "corp_default")
    period = payload.get("billing_period", "2026-M10")
    inv = await service.generate_corporate_invoice(corp_id, period)
    return {"message": "Invoice generated", "invoice_number": inv.invoice_number, "total_rupees": inv.total_amount_cents / 100.0}

@router.patch("/corporate/invoices/{invoice_id}/pay")
async def mark_corporate_invoice_paid(invoice_id: str, db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    inv = await service.mark_corporate_invoice_paid(invoice_id)
    if not inv:
        raise HTTPException(status_code=404, detail="Invoice not found")
    return {"message": "Invoice paid", "invoice_number": inv.invoice_number, "status": inv.status}

@router.get("/corporate/approvals")
async def get_approval_requests(db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    requests = await service.get_approval_requests()
    return [
        {
            "id": req.id,
            "ride_id": req.ride_id,
            "employee_id": req.employee_id,
            "cost_center": req.cost_center,
            "project_code": req.project_code,
            "status": req.status,
            "justification": req.justification,
            "created_at": req.created_at.isoformat() if req.created_at else None
        }
        for req in requests
    ]

@router.post("/corporate/approvals")
async def submit_approval_request(payload: Dict[str, Any], db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    req = await service.submit_approval_request(payload)
    return {"message": "Approval request submitted", "id": req.id, "status": req.status}

@router.post("/corporate/approvals/{id}/action")
async def action_approval_request(id: str, payload: Dict[str, Any], db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    action = payload.get("action", "APPROVE")
    approver = payload.get("approver_id", "admin_approver_1")
    req = await service.action_approval_request(id, approver, action)
    if not req:
        raise HTTPException(status_code=404, detail="Approval request not found")
    return {"message": f"Approval request {action.lower()}d", "id": req.id, "status": req.status}

# =============================================================================
# 481, 482, 483 & 484. Concierge Dashboard, Partner Accounts, API & Referral Attribution
# =============================================================================

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

@router.post("/partners/accounts")
async def create_partner_account(payload: Dict[str, Any], db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    p = await service.create_partner_account(payload)
    return {"message": "Partner onboarded", "id": p.id, "partner_name": p.partner_name, "referral_code": p.referral_code}

@router.get("/partners/concierge/dashboard")
async def get_concierge_dashboard(db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    return await service.get_concierge_dashboard()

@router.post("/partner-api/bookings")
async def partner_api_booking(
    payload: Dict[str, Any],
    db: AsyncSession = Depends(get_db),
    x_partner_api_key: Optional[str] = Header(None)
):
    service = EnterpriseGrowthService(db)
    api_key = x_partner_api_key or payload.get("api_key") or payload.get("referral_code") or "partner_taj_2026"
    return await service.partner_api_booking(api_key, payload)

@router.get("/partners/attributions")
async def get_partner_attributions(partner_id: Optional[str] = None, db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    return await service.get_partner_attributions(partner_id)

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

@router.post("/partners/events")
async def create_event_workspace(payload: Dict[str, Any], db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    evt = await service.create_event_workspace(payload)
    return {"message": "Event transport workspace created", "id": evt.id, "event_name": evt.event_name}

# =============================================================================
# 486, 487, 488 & 490. Subscriptions, Prepaid Bundles & Loyalty Partner Catalog
# =============================================================================

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

@router.post("/subscriptions/plans")
async def create_subscription_plan(payload: Dict[str, Any], db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    p = await service.create_subscription_plan(payload)
    return {"message": "Subscription plan registered", "id": p.id, "plan_code": p.plan_code}

@router.post("/subscriptions/subscribe")
async def subscribe_to_plan(payload: Dict[str, Any], db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    cust_id = payload.get("customer_id", "cust_active_01")
    plan_id = payload["plan_id"]
    sub = await service.subscribe_customer(cust_id, plan_id)
    return {"message": "Subscription active", "id": sub.id, "rides_remaining": sub.rides_remaining}

@router.get("/subscriptions/my-subscription")
async def get_my_subscription(customer_id: Optional[str] = None, db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    cust_id = customer_id or "cust_active_01"
    sub = await service.get_customer_subscription(cust_id)
    if not sub:
        return {"active": False, "rides_remaining": 0}
    return {
        "active": True,
        "id": sub.id,
        "rides_remaining": sub.rides_remaining,
        "expires_at": sub.expires_at.isoformat()
    }

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

@router.post("/subscriptions/bundles")
async def create_prepaid_bundle(payload: Dict[str, Any], db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    b = await service.create_prepaid_bundle(payload)
    return {"message": "Prepaid ride bundle created", "id": b.id, "bundle_code": b.bundle_code}

@router.post("/subscriptions/bundles/purchase")
async def purchase_prepaid_bundle(payload: Dict[str, Any], db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    cust_id = payload.get("customer_id", "cust_active_01")
    bundle_id = payload["bundle_id"]
    return await service.purchase_prepaid_bundle(cust_id, bundle_id)

@router.get("/loyalty-catalog")
async def get_loyalty_catalog(db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    items = await service.get_loyalty_catalog()
    return [
        {
            "id": it.id,
            "partner_name": it.partner_name,
            "offer_title": it.offer_title,
            "required_points": it.required_points,
            "promo_code": it.promo_code,
            "logo_url": it.logo_url,
            "is_active": it.is_active
        }
        for it in items
    ]

@router.post("/loyalty-catalog/{id}/redeem")
async def redeem_loyalty_offer(id: str, payload: Dict[str, Any], db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    cust_id = payload.get("customer_id", "cust_active_01")
    return await service.redeem_loyalty_offer(cust_id, id)

# =============================================================================
# 491, 492, 493 & 494. Airport Transfers, Intercity, Hourly & Service Add-ons
# =============================================================================

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

@router.post("/products/airport-transfers")
async def create_airport_package(payload: Dict[str, Any], db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    pkg = await service.create_airport_package(payload)
    return {"message": "Airport transfer package created", "id": pkg.id, "airport_code": pkg.airport_code}

@router.post("/products/airport-transfers/book")
async def book_airport_package(payload: Dict[str, Any], db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    cust_id = payload.get("customer_id", "cust_active_01")
    return await service.book_airport_package(cust_id, payload)

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

@router.post("/products/intercity")
async def create_intercity_product(payload: Dict[str, Any], db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    prod = await service.create_intercity_product(payload)
    return {"message": "Intercity route product created", "id": prod.id, "route_name": prod.route_name}

@router.get("/products/hourly-bookings")
async def get_hourly_bookings(db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    bookings = await service.get_hourly_bookings()
    return [
        {
            "id": b.id,
            "customer_id": b.customer_id,
            "requested_hours": b.requested_hours,
            "hourly_rate_rupees": b.hourly_rate_cents / 100.0,
            "kilometers_included": b.kilometers_included,
            "created_at": b.created_at.isoformat() if b.created_at else None
        }
        for b in bookings
    ]

@router.post("/products/hourly-bookings")
async def create_hourly_booking(payload: Dict[str, Any], db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    cust_id = payload.get("customer_id", "cust_active_01")
    b = await service.create_hourly_booking(cust_id, payload)
    return {"message": "Hourly chauffeur reserved", "id": b.id, "requested_hours": b.requested_hours}

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

@router.post("/products/add-ons")
async def create_chauffeur_addon(payload: Dict[str, Any], db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    add = await service.create_chauffeur_addon(payload)
    return {"message": "Chauffeur add-on registered", "id": add.id, "addon_code": add.addon_code}

# =============================================================================
# 495, 496 & 497. White-Label Tenant Config, Multi-City & City Tax & Fares
# =============================================================================

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
            "custom_domain": cfg.custom_domain,
            "logo_url": cfg.logo_url
        }
        for cfg in configs
    ]

@router.post("/tenant/configs")
async def create_white_label_config(payload: Dict[str, Any], db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    cfg = await service.create_or_update_white_label_config(payload)
    return {"message": "White-label tenant configured", "id": cfg.id, "tenant_code": cfg.tenant_code}

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

@router.patch("/cities/{city_code}/tax-fare")
async def update_city_tax_fare(city_code: str, payload: Dict[str, Any], db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    city = await service.update_city_tax_fare(city_code, payload)
    if not city:
        raise HTTPException(status_code=404, detail="City configuration not found")
    return {"message": f"Updated settings for {city.city_name}", "city_code": city.city_code, "tax_rate": city.tax_rate_percentage}

# =============================================================================
# 498, 499 & 500. A/B Testing Experiments, Feature Requests & Feedback Analytics
# =============================================================================

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

@router.get("/growth/ab-experiments/{key}/evaluate")
async def evaluate_experiment(key: str, user_id: Optional[str] = None, db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    uid = user_id or "user_guest_1"
    return await service.evaluate_ab_experiment(key, uid)

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

@router.post("/growth/feature-requests")
async def create_feature_request(payload: Dict[str, Any], db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    r = await service.create_feature_request(payload)
    return {"message": "Feature request posted", "id": r.id, "feature_key": r.feature_key}

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

@router.post("/growth/feedback-analytics")
async def submit_feedback(payload: Dict[str, Any], db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    fb = await service.submit_feedback(payload)
    return {"message": "Feedback recorded", "id": fb.id, "sentiment": fb.sentiment}

@router.get("/growth/feedback-analytics/summary")
async def get_feedback_summary(db: AsyncSession = Depends(get_db)):
    service = EnterpriseGrowthService(db)
    return await service.get_feedback_summary()
