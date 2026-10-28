"""
Unit Tests for Part 20: Enterprise, Corporate Accounts, Concierge Portals, Subscriptions, Multi-City & Growth (Features 476 - 500).
"""
import pytest
import uuid
from datetime import datetime, timezone, timedelta

from backend.domain.models.models import (
    CorporateAccount, EmployeeProfile, CorporateTravelPolicy,
    CorporateInvoice, BusinessApprovalRequest, PartnerAccount,
    PartnerBooking, EventTransportWorkspace, FleetSubscriptionPlan,
    CustomerSubscription, PrepaidRideBundle, LoyaltyPartnerCatalog,
    AirportTransferPackage, IntercityProduct, HourlyChauffeurBooking,
    ChauffeurServiceAddon, WhiteLabelTenantConfig, MultiCityOperatingConfig,
    ABTestExperiment, FeatureRequestVote, ProductFeedbackAnalytics
)
from backend.application.services.enterprise_growth_service import EnterpriseGrowthService

@pytest.mark.asyncio
async def test_part20_corporate_accounts_and_policies(db_session):
    """Features 476, 477, 478 & 480: Corporate accounts, employees, policies, and cost centers."""
    service = EnterpriseGrowthService(db_session)

    # 476. Corporate customer accounts
    company_name = f"Goldman Sachs India - {uuid.uuid4().hex[:6]}"
    acc = await service.create_corporate_account({
        "company_name": company_name,
        "tax_id": "27AAACG9988H1Z4",
        "billing_email": "invoicing@gs.com",
        "credit_limit_cents": 100000000
    })
    assert acc.id is not None
    assert acc.company_name == company_name
    assert acc.credit_limit_cents == 100000000

    # 477 & 480. Employee profile & cost center tagging
    emp = await service.enroll_employee({
        "corporate_id": acc.id,
        "user_id": f"user_corp_{uuid.uuid4().hex[:6]}",
        "cost_center": "INVESTMENT_BANKING_MUM",
        "max_ride_fare_cents": 350000,
        "requires_approval": False
    })
    assert emp.id is not None
    assert emp.cost_center == "INVESTMENT_BANKING_MUM"

    # Cost centers summary
    cc_summary = await service.get_cost_centers_summary(acc.id)
    assert len(cc_summary) >= 1
    target_cc = next((c for c in cc_summary if c["cost_center"] == "INVESTMENT_BANKING_MUM"), None)
    assert target_cc is not None
    assert target_cc["employee_count"] == 1

    # 478. Corporate travel policy rules
    pol = await service.create_corporate_policy(acc.id, {
        "policy_name": "Executive Partner Travel Tier",
        "allowed_categories": ["SEDAN", "EXECUTIVE_LIMO"],
        "max_fare_per_ride_cents": 500000,
        "curfew_start_time": "23:00",
        "curfew_end_time": "05:00"
    })
    assert pol.id is not None

    # Test policy compliance check
    valid_check = await service.validate_travel_policy(acc.id, "SEDAN", 250000)
    assert valid_check["compliant"] is True

    invalid_cat_check = await service.validate_travel_policy(acc.id, "HELICOPTER", 250000)
    assert invalid_cat_check["compliant"] is False

    invalid_fare_check = await service.validate_travel_policy(acc.id, "SEDAN", 800000)
    assert invalid_fare_check["compliant"] is False

@pytest.mark.asyncio
async def test_part20_invoicing_and_approvals(db_session):
    """Features 479 & 489: Centralized corporate invoicing & travel approval workflow."""
    service = EnterpriseGrowthService(db_session)

    accounts = await service.get_corporate_accounts()
    corp_id = accounts[0].id

    # 479. Centralized corporate invoicing
    inv = await service.generate_corporate_invoice(corp_id, "2026-M11")
    assert inv.id is not None
    assert "INV-CORP" in inv.invoice_number
    assert inv.status == "ISSUED"

    # Mark paid
    paid_inv = await service.mark_corporate_invoice_paid(inv.id)
    assert paid_inv.status == "PAID"

    # 489. Business travel approval workflow
    req = await service.submit_approval_request({
        "ride_id": f"ride_test_{uuid.uuid4().hex[:6]}",
        "employee_id": f"emp_test_{uuid.uuid4().hex[:6]}",
        "cost_center": "CLIENT_ADVISORY",
        "project_code": "PROJECT_AURORA",
        "justification": "Emergency executive transport for transatlantic merger meeting."
    })
    assert req.id is not None
    assert req.status == "PENDING"

    # Approve request
    approved_req = await service.action_approval_request(req.id, "approver_exec_1", "APPROVE")
    assert approved_req.status == "APPROVED"
    assert approved_req.approver_id == "approver_exec_1"

@pytest.mark.asyncio
async def test_part20_partners_concierge_and_events(db_session):
    """Features 481, 482, 483, 484 & 485: Hotel partners, concierge desk, partner API and events."""
    service = EnterpriseGrowthService(db_session)

    # 482. Hotel and venue partner accounts
    partner_name = f"The Oberoi Mumbai - {uuid.uuid4().hex[:6]}"
    p = await service.create_partner_account({
        "partner_name": partner_name,
        "partner_type": "HOTEL",
        "commission_rate_pct": 15.0,
        "referral_code": f"OBEROI_{uuid.uuid4().hex[:4].upper()}"
    })
    assert p.id is not None
    assert p.partner_name == partner_name

    # 481. Concierge booking dashboard
    dash = await service.get_concierge_dashboard()
    assert dash["active_partners_count"] >= 1
    assert "guest_satisfaction_score" in dash

    # 483 & 484. Partner booking API & attribution
    booking_res = await service.partner_api_booking(p.referral_code, {
        "guest_name": "His Excellency Lord Sterling",
        "guest_phone": "+91 98200 77889",
        "category": "EXECUTIVE_LIMO",
        "fare_cents": 2000000
    })
    assert booking_res["status"] == "DISPATCH_CONFIRMED"
    assert booking_res["partner_id"] == p.id
    assert booking_res["commission_earned_rupees"] == 3000.0  # 15% of ₹20,000

    # Attribution ledger
    attrs = await service.get_partner_attributions(p.id)
    assert len(attrs) >= 1
    assert attrs[0]["guest_name"] == "His Excellency Lord Sterling"

    # 485. Event transport planning workspace
    evt = await service.create_event_workspace({
        "event_name": "Global Sovereign Wealth Summit 2026",
        "venue_address": "Jio World Centre, BKC, Mumbai",
        "allocated_vehicles_count": 35,
        "total_guests": 300,
        "status": "PLANNING"
    })
    assert evt.id is not None
    assert evt.allocated_vehicles_count == 35

@pytest.mark.asyncio
async def test_part20_subscriptions_bundles_and_specialty_products(db_session):
    """Features 486, 487, 488, 490, 491, 492, 493 & 494."""
    service = EnterpriseGrowthService(db_session)

    # 486 & 487. Fleet subscription packages & plan management
    plan_code = f"PLAN_VIP_{uuid.uuid4().hex[:6].upper()}"
    plan = await service.create_subscription_plan({
        "plan_code": plan_code,
        "name": "Sovereign Executive 25 Pass",
        "monthly_price_cents": 9500000,
        "included_rides_count": 25,
        "category_code": "LUXURY_SUV"
    })
    assert plan.id is not None
    assert plan.included_rides_count == 25

    # Subscribe customer
    sub = await service.subscribe_customer("cust_test_vip", plan.id)
    assert sub.id is not None
    assert sub.rides_remaining == 25
    assert sub.status == "ACTIVE"

    # 488. Prepaid ride bundles
    bundle_code = f"BUNDLE_{uuid.uuid4().hex[:6].upper()}"
    bundle = await service.create_prepaid_bundle({
        "bundle_code": bundle_code,
        "title": "15-Ride Royal Pass Pack",
        "ride_count": 15,
        "price_cents": 5500000,
        "discount_percentage": 18.0
    })
    assert bundle.id is not None

    purchase_res = await service.purchase_prepaid_bundle("cust_test_vip", bundle.id)
    assert purchase_res["status"] == "PURCHASED"
    assert purchase_res["rides_credited"] == 15

    # 490. Customer loyalty partner catalog
    loyalty_items = await service.get_loyalty_catalog()
    assert len(loyalty_items) >= 1
    redeem_res = await service.redeem_loyalty_offer("cust_test_vip", loyalty_items[0].id)
    assert redeem_res["status"] == "REDEEMED"
    assert "voucher_code" in redeem_res

    # 491. Airport transfer package catalog
    airport_pkgs = await service.get_airport_packages()
    assert len(airport_pkgs) >= 2
    book_air = await service.book_airport_package("cust_test_vip", {
        "package_id": airport_pkgs[0].id,
        "flight_number": "BA-199",
        "terminal": "T2"
    })
    assert book_air["status"] == "CONFIRMED"
    assert book_air["meets_and_greets"] is True

    # 492. Intercity ride product
    intercity_routes = await service.get_intercity_products()
    assert len(intercity_routes) >= 2
    mumbai_pune = next((r for r in intercity_routes if "Mumbai" in r.origin_city), intercity_routes[0])
    assert mumbai_pune.toll_included is True
    assert mumbai_pune.distance_km > 50

    # 493. Hourly chauffeur booking
    hourly = await service.create_hourly_booking("cust_test_vip", {"requested_hours": 6})
    assert hourly.id is not None
    assert hourly.requested_hours == 6
    assert hourly.kilometers_included == 60

    # 494. Chauffeur service add-ons
    addons = await service.get_chauffeur_addons()
    assert len(addons) >= 2
    armored = next((a for a in addons if "ARMORED" in a.addon_code), addons[0])
    assert armored.price_cents > 0

@pytest.mark.asyncio
async def test_part20_multicity_experiments_and_feedback(db_session):
    """Features 495, 496, 497, 498, 499 & 500."""
    service = EnterpriseGrowthService(db_session)

    # 495. White-label tenant configuration
    t_code = f"TENANT_{uuid.uuid4().hex[:6].upper()}"
    tenant = await service.create_or_update_white_label_config({
        "tenant_code": t_code,
        "brand_name": "Emirates Sovereign Chauffeur",
        "primary_color": "#D4AF37",
        "secondary_color": "#1A1A24",
        "custom_domain": "chauffeur.emirates.com"
    })
    assert tenant.id is not None
    assert tenant.tenant_code == t_code

    # 496 & 497. Multi-city operating configuration & tax/fares
    cities = await service.get_city_configs()
    assert len(cities) >= 3

    updated_city = await service.update_city_tax_fare("MUMBAI", {
        "tax_rate_percentage": 5.5,
        "min_fare_cents": 120000
    })
    assert updated_city.tax_rate_percentage == 5.5
    assert updated_city.min_fare_cents == 120000

    # 498. Experiment & A/B test framework
    exps = await service.get_ab_experiments()
    assert len(exps) >= 1
    eval_res = await service.evaluate_ab_experiment(exps[0].experiment_key, "user_executive_99")
    assert "assigned_variant" in eval_res
    assert eval_res["experiment_key"] == exps[0].experiment_key

    # 499. Customer feature request voting
    reqs = await service.get_feature_requests()
    assert len(reqs) >= 1
    first_key = reqs[0].feature_key
    initial_votes = reqs[0].upvote_count
    voted = await service.vote_feature_request(first_key)
    assert voted.upvote_count == initial_votes + 1

    # 500. Product feedback analytics dashboard
    fb = await service.submit_feedback({
        "user_id": "user_vip_007",
        "rating_score": 5,
        "feedback_category": "CHAUFFEUR_SERVICE",
        "comments": "The chauffeur maintained absolute discretion and the Rolls Royce Phantom was flawless."
    })
    assert fb.sentiment == "POSITIVE"

    summary = await service.get_feedback_summary()
    assert summary["total_feedbacks"] >= 1
    assert summary["average_rating"] >= 4.0
    assert "sentiment_breakdown" in summary
