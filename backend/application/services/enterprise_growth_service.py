import uuid
import json
import logging
from datetime import datetime, timedelta, timezone
from typing import List, Dict, Any, Optional
from sqlalchemy import select, update, delete, func
from sqlalchemy.ext.asyncio import AsyncSession

from backend.domain.models.models import (
    CorporateAccount, EmployeeProfile, CorporateTravelPolicy,
    CorporateInvoice, BusinessApprovalRequest, PartnerAccount,
    PartnerBooking, EventTransportWorkspace, FleetSubscriptionPlan,
    CustomerSubscription, PrepaidRideBundle, LoyaltyPartnerCatalog,
    AirportTransferPackage, IntercityProduct, HourlyChauffeurBooking,
    ChauffeurServiceAddon, WhiteLabelTenantConfig, MultiCityOperatingConfig,
    ABTestExperiment, FeatureRequestVote, ProductFeedbackAnalytics
)

logger = logging.getLogger("backend.enterprise_growth")

def utc_now():
    return datetime.now(timezone.utc)

class EnterpriseGrowthService:
    def __init__(self, db: AsyncSession):
        self.db = db

    # -------------------------------------------------------------------------
    # 476, 477, 478, 479, 480 & 489. Corporate, Travel Policies, Invoicing & Approvals
    # -------------------------------------------------------------------------
    async def get_corporate_accounts(self) -> List[CorporateAccount]:
        stmt = select(CorporateAccount).order_by(CorporateAccount.company_name)
        res = await self.db.execute(stmt)
        accounts = list(res.scalars().all())
        if not accounts:
            default_acc = CorporateAccount(
                company_name="McKinsey & Company India",
                tax_id="27AAACM1234F1Z9",
                billing_email="corporate-finance@mckinsey.com",
                credit_limit_cents=50000000,
                balance_due_cents=1250000,
                status="ACTIVE"
            )
            self.db.add(default_acc)
            await self.db.commit()
            return [default_acc]
        return accounts

    async def create_corporate_account(self, payload: Dict[str, Any]) -> CorporateAccount:
        acc = CorporateAccount(
            company_name=payload["company_name"],
            tax_id=payload["tax_id"],
            billing_email=payload["billing_email"],
            credit_limit_cents=payload.get("credit_limit_cents", 50000000),
            status="ACTIVE"
        )
        self.db.add(acc)
        await self.db.commit()
        return acc

    async def get_corporate_policies(self, corporate_id: str) -> List[CorporateTravelPolicy]:
        stmt = select(CorporateTravelPolicy).where(CorporateTravelPolicy.corporate_id == corporate_id)
        res = await self.db.execute(stmt)
        policies = list(res.scalars().all())
        if not policies:
            pol = CorporateTravelPolicy(
                corporate_id=corporate_id,
                policy_name="Partner & Managing Director Executive Policy",
                allowed_categories_json='["SEDAN", "LUXURY_SUV", "EXECUTIVE_LIMO", "ULTRA_LUXURY_EV"]',
                max_fare_per_ride_cents=1000000,
                curfew_start_time="23:30",
                curfew_end_time="05:30",
                is_active=True
            )
            self.db.add(pol)
            await self.db.commit()
            return [pol]
        return policies

    async def get_corporate_invoices(self, corporate_id: str) -> List[CorporateInvoice]:
        stmt = select(CorporateInvoice).where(CorporateInvoice.corporate_id == corporate_id)
        res = await self.db.execute(stmt)
        invoices = list(res.scalars().all())
        if not invoices:
            inv = CorporateInvoice(
                invoice_number="INV-CORP-2026-10",
                corporate_id=corporate_id,
                billing_period="2026-M10",
                total_amount_cents=1250000,
                status="ISSUED",
                pdf_url="/invoices/INV-CORP-2026-10.pdf",
                due_date=utc_now() + timedelta(days=15)
            )
            self.db.add(inv)
            await self.db.commit()
            return [inv]
        return invoices

    async def get_approval_requests(self) -> List[BusinessApprovalRequest]:
        stmt = select(BusinessApprovalRequest).order_by(BusinessApprovalRequest.created_at.desc())
        res = await self.db.execute(stmt)
        return list(res.scalars().all())

    # -------------------------------------------------------------------------
    # 481, 482, 483, 484 & 485. Partners, Hotel/Venue Portals & Event Transport
    # -------------------------------------------------------------------------
    async def get_partner_accounts(self) -> List[PartnerAccount]:
        stmt = select(PartnerAccount).order_by(PartnerAccount.partner_name)
        res = await self.db.execute(stmt)
        partners = list(res.scalars().all())
        if not partners:
            defaults = [
                PartnerAccount(
                    partner_type="HOTEL",
                    partner_name="The Taj Mahal Palace Mumbai",
                    api_key_hash="hash_taj_palace_2026",
                    commission_rate_pct=12.5,
                    referral_code="TAJ_MUMBAI_VIP"
                ),
                PartnerAccount(
                    partner_type="VENUE",
                    partner_name="Jio World Convention Centre",
                    api_key_hash="hash_jio_convention_2026",
                    commission_rate_pct=10.0,
                    referral_code="JIO_CONVENTION_EXEC"
                )
            ]
            self.db.add_all(defaults)
            await self.db.commit()
            return defaults
        return partners

    async def get_event_workspaces(self) -> List[EventTransportWorkspace]:
        stmt = select(EventTransportWorkspace).order_by(EventTransportWorkspace.event_date.desc())
        res = await self.db.execute(stmt)
        events = list(res.scalars().all())
        if not events:
            evt = EventTransportWorkspace(
                event_name="Sovereign Luxury Fashion Gala 2026",
                venue_address="Jio World Garden, BKC, Mumbai",
                event_date=utc_now() + timedelta(days=5),
                allocated_vehicles_count=25,
                total_guests=250,
                status="ACTIVE"
            )
            self.db.add(evt)
            await self.db.commit()
            return [evt]
        return events

    # -------------------------------------------------------------------------
    # 486, 487, 488, 490, 491, 492, 493 & 494. Subscriptions, Packages & Add-ons
    # -------------------------------------------------------------------------
    async def get_subscription_plans(self) -> List[FleetSubscriptionPlan]:
        stmt = select(FleetSubscriptionPlan).order_by(FleetSubscriptionPlan.monthly_price_cents)
        res = await self.db.execute(stmt)
        plans = list(res.scalars().all())
        if not plans:
            defaults = [
                FleetSubscriptionPlan(
                    plan_code="PLAN_EXECUTIVE_10",
                    name="Royal Executive 10 Pass",
                    monthly_price_cents=4500000,
                    included_rides_count=10,
                    category_code="SEDAN"
                ),
                FleetSubscriptionPlan(
                    plan_code="PLAN_SOVEREIGN_UNLIMITED",
                    name="Maybach Sovereign Unlimited",
                    monthly_price_cents=18000000,
                    included_rides_count=40,
                    category_code="EXECUTIVE_LIMO"
                )
            ]
            self.db.add_all(defaults)
            await self.db.commit()
            return defaults
        return plans

    async def get_prepaid_bundles(self) -> List[PrepaidRideBundle]:
        stmt = select(PrepaidRideBundle).order_by(PrepaidRideBundle.ride_count)
        res = await self.db.execute(stmt)
        bundles = list(res.scalars().all())
        if not bundles:
            defaults = [
                PrepaidRideBundle(bundle_code="BUNDLE_5_RIDES", title="5-Ride Executive Commute Pack", ride_count=5, price_cents=2000000, discount_percentage=10.0),
                PrepaidRideBundle(bundle_code="BUNDLE_20_RIDES", title="20-Ride Sovereign Corporate Pack", ride_count=20, price_cents=7200000, discount_percentage=20.0)
            ]
            self.db.add_all(defaults)
            await self.db.commit()
            return defaults
        return bundles

    async def get_airport_packages(self) -> List[AirportTransferPackage]:
        stmt = select(AirportTransferPackage).order_by(AirportTransferPackage.airport_code)
        res = await self.db.execute(stmt)
        pkgs = list(res.scalars().all())
        if not pkgs:
            defaults = [
                AirportTransferPackage(airport_code="BOM", package_title="Chhatrapati Shivaji Maharaj Airport VIP Fast-Track", category_code="EXECUTIVE_LIMO", flat_fare_cents=850000, meets_and_greets_included=True),
                AirportTransferPackage(airport_code="DEL", package_title="Indira Gandhi Intl T3 Escort Package", category_code="LUXURY_SUV", flat_fare_cents=750000, meets_and_greets_included=True)
            ]
            self.db.add_all(defaults)
            await self.db.commit()
            return defaults
        return pkgs

    async def get_intercity_products(self) -> List[IntercityProduct]:
        stmt = select(IntercityProduct).order_by(IntercityProduct.route_name)
        res = await self.db.execute(stmt)
        products = list(res.scalars().all())
        if not products:
            defaults = [
                IntercityProduct(route_name="Mumbai - Pune Expressway Corridor", origin_city="Mumbai", destination_city="Pune", distance_km=148.0, fixed_fare_cents=1250000, toll_included=True),
                IntercityProduct(route_name="Delhi - Agra Yamuna Expressway Escort", origin_city="Delhi", destination_city="Agra", distance_km=210.0, fixed_fare_cents=1650000, toll_included=True)
            ]
            self.db.add_all(defaults)
            await self.db.commit()
            return defaults
        return products

    async def get_chauffeur_addons(self) -> List[ChauffeurServiceAddon]:
        stmt = select(ChauffeurServiceAddon).order_by(ChauffeurServiceAddon.addon_code)
        res = await self.db.execute(stmt)
        addons = list(res.scalars().all())
        if not addons:
            defaults = [
                ChauffeurServiceAddon(addon_code="ADDON_ARMORED_ESCORT", title="Armed Security Detail Escort", price_cents=500000, description="Dedicated armed security detail accompanying Maybach vehicle."),
                ChauffeurServiceAddon(addon_code="ADDON_CHAMPAGNE_CRU", title="Chilled Dom Pérignon Champagne Bottle", price_cents=350000, description="Pre-chilled luxury champagne bottle in rear cooler bar.")
            ]
            self.db.add_all(defaults)
            await self.db.commit()
            return defaults
        return addons

    # -------------------------------------------------------------------------
    # 495, 496 & 497. White-Label, Multi-City & City-Specific Settings
    # -------------------------------------------------------------------------
    async def get_white_label_configs(self) -> List[WhiteLabelTenantConfig]:
        stmt = select(WhiteLabelTenantConfig).order_by(WhiteLabelTenantConfig.tenant_code)
        res = await self.db.execute(stmt)
        tenants = list(res.scalars().all())
        if not tenants:
            t = WhiteLabelTenantConfig(
                tenant_code="TENANT_GRAND_CHAUFFEUR",
                brand_name="Grand Chauffeur Global",
                primary_color="#D4AF37",
                secondary_color="#0B0F17",
                custom_domain="grandchauffeur.com"
            )
            self.db.add(t)
            await self.db.commit()
            return [t]
        return tenants

    async def get_city_configs(self) -> List[MultiCityOperatingConfig]:
        stmt = select(MultiCityOperatingConfig).order_by(MultiCityOperatingConfig.city_name)
        res = await self.db.execute(stmt)
        cities = list(res.scalars().all())
        if not cities:
            defaults = [
                MultiCityOperatingConfig(city_code="MUMBAI", city_name="Mumbai Financial Capital", country="India", timezone="Asia/Kolkata", tax_rate_percentage=5.0, min_fare_cents=100000),
                MultiCityOperatingConfig(city_code="DELHI_NCR", city_name="Delhi NCR Capital Region", country="India", timezone="Asia/Kolkata", tax_rate_percentage=5.0, min_fare_cents=100000),
                MultiCityOperatingConfig(city_code="DUBAI", city_name="Dubai International Financial Centre", country="UAE", timezone="Asia/Dubai", tax_rate_percentage=5.0, min_fare_cents=300000)
            ]
            self.db.add_all(defaults)
            await self.db.commit()
            return defaults
        return cities

    # -------------------------------------------------------------------------
    # 498, 499 & 500. A/B Experiments, Feature Requests & Feedback Analytics
    # -------------------------------------------------------------------------
    async def get_ab_experiments(self) -> List[ABTestExperiment]:
        stmt = select(ABTestExperiment).order_by(ABTestExperiment.experiment_key)
        res = await self.db.execute(stmt)
        exps = list(res.scalars().all())
        if not exps:
            e = ABTestExperiment(
                experiment_key="EXP_DYNAMIC_SURGE_TRANSPARENCY",
                name="Dynamic Surge Transparency & Cap Guarantee",
                description="Tests conversion impact of showing max capped fare guarantee badge.",
                variants_json='["control_hidden_cap", "variant_badge_guarantee"]',
                rollout_status="RUNNING"
            )
            self.db.add(e)
            await self.db.commit()
            return [e]
        return exps

    async def get_feature_requests(self) -> List[FeatureRequestVote]:
        stmt = select(FeatureRequestVote).order_by(FeatureRequestVote.upvote_count.desc())
        res = await self.db.execute(stmt)
        reqs = list(res.scalars().all())
        if not reqs:
            defaults = [
                FeatureRequestVote(feature_key="REQ_INFLIGHT_WIFI_CUSTOM", title="Custom In-Flight Wi-Fi SSID & Password", description="Allow guest to specify custom Wi-Fi SSID for executive laptops.", upvote_count=142),
                FeatureRequestVote(feature_key="REQ_STARLINK_SATELLITE", title="Starlink Satellite Internet on Highway Routes", description="Equip Intercity vehicles with Starlink satellite dish.", upvote_count=98)
            ]
            self.db.add_all(defaults)
            await self.db.commit()
            return defaults
        return reqs

    async def vote_feature_request(self, feature_key: str) -> FeatureRequestVote:
        stmt = select(FeatureRequestVote).where(FeatureRequestVote.feature_key == feature_key)
        res = await self.db.execute(stmt)
        feat = res.scalars().first()
        if feat:
            feat.upvote_count += 1
            await self.db.commit()
        return feat

    async def get_feedback_analytics(self) -> List[ProductFeedbackAnalytics]:
        stmt = select(ProductFeedbackAnalytics).order_by(ProductFeedbackAnalytics.created_at.desc()).limit(50)
        res = await self.db.execute(stmt)
        return list(res.scalars().all())
