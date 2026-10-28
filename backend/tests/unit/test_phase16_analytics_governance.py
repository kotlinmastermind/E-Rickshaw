"""
Unit Tests for Part 16: Analytics, BI Reporting, Governance Auditing and DPDP Privacy (Features 376 - 400).
"""
import pytest
import uuid
from datetime import datetime, timezone, timedelta
from fastapi import HTTPException

from backend.domain.models import (
    User, CustomerProfile, DriverProfile, Ride,
    FareEstimate, ScheduledReportDelivery, CustomReportConfig,
    SensitiveRecordAccessLog, DataRetentionPolicy, PrivacyDataSubjectRequest,
    AdminActionJustification, AdministrativeAuditLog
)
from backend.application.services.analytics_bi_service import AnalyticsBiService


@pytest.mark.asyncio
async def test_part16_operational_analytics_suite(db_session):
    """Features 376-384: Operational Analytics, Accuracy Reports & Zone Forecasts."""
    service = AnalyticsBiService(db_session)

    # 376. Ride Funnel
    funnel = await service.get_ride_funnel_conversion(days=30)
    assert "overall_conversion_pct" in funnel
    assert len(funnel["stages"]) == 5
    assert funnel["stages"][0]["name"] == "Quote Requested"
    assert funnel["stages"][4]["name"] == "Trip Completed"
    assert funnel["overall_conversion_pct"] > 0

    # 377. Cancellation Analytics
    cancels = await service.get_cancellation_analytics(days=30)
    assert "by_actor" in cancels
    assert "CUSTOMER" in cancels["by_actor"]
    assert "DRIVER" in cancels["by_actor"]
    assert "SYSTEM_TIMEOUT" in cancels["by_actor"]
    assert len(cancels["reasons_breakdown"]) > 0
    assert cancels["financial_impact"]["cancellation_fee_billed_inr"] > 0

    # 378. Driver Acceptance Analytics
    acceptance = await service.get_driver_acceptance_analytics(days=30)
    assert acceptance["overall_acceptance_rate_pct"] >= 80.0
    assert acceptance["average_response_latency_seconds"] < 10.0
    assert len(acceptance["acceptance_by_tier"]) == 3

    # 379. Driver Utilization Analytics
    utilization = await service.get_driver_utilization_analytics(days=30)
    assert utilization["fleet_utilization_pct"] > 50.0
    assert utilization["avg_revenue_per_online_hour_inr"] > 0
    assert len(utilization["hourly_utilization_curve"]) == 6

    # 380. Pickup ETA Accuracy Report
    eta_acc = await service.get_pickup_eta_accuracy_report(days=30)
    assert eta_acc["mean_absolute_error_minutes"] < 3.0
    assert eta_acc["accuracy_within_5_mins_pct"] > 90.0
    assert len(eta_acc["distribution"]) == 4

    # 381. Fare Estimate Accuracy Report
    fare_acc = await service.get_fare_estimate_accuracy_report(days=30)
    assert fare_acc["exact_match_rate_pct"] > 80.0
    assert fare_acc["mean_fare_discrepancy_pct"] < 5.0

    # 382. Zone Demand Forecast
    demand = await service.get_demand_forecast_by_zone()
    assert len(demand["zones"]) >= 3
    assert len(demand["zones"][0]["expected_hourly_demand"]) == 24
    assert demand["zones"][0]["confidence_score_pct"] > 90.0

    # 383. Zone Supply Forecast
    supply = await service.get_supply_forecast_by_zone()
    assert "zone_balances" in supply
    assert any(b["balance_status"] == "DEFICIT" for b in supply["zone_balances"])

    # 384. Shared Ride Matching Performance
    shared = await service.get_shared_ride_matching_performance(days=30)
    assert shared["shared_match_rate_pct"] > 75.0
    assert shared["average_detour_delay_minutes"] <= shared["max_allowed_detour_minutes"]
    assert shared["environmental_impact"]["co2_saved_kg"] > 0


@pytest.mark.asyncio
async def test_part16_business_finance_bi(db_session):
    """Features 385-392: Retention Cohorts, Marketing ROI, Payment Gateways & SLAs."""
    service = AnalyticsBiService(db_session)

    # 385. Customer Retention Cohorts
    cust_cohorts = await service.get_customer_retention_cohorts()
    assert len(cust_cohorts["cohorts"]) >= 5
    assert cust_cohorts["cohorts"][0]["retention_curve"][0] == 100.0

    # 386. Driver Retention Cohorts
    driver_cohorts = await service.get_driver_retention_cohorts()
    assert driver_cohorts["annual_chauffeur_retention_rate_pct"] > 80.0

    # 387. Promo Campaign Effectiveness
    promos = await service.get_promo_campaign_effectiveness()
    assert promos["aggregate_roi_multiple"] > 5.0
    assert len(promos["campaigns"]) >= 3

    # 388. Referral Fraud Analytics
    fraud = await service.get_referral_fraud_analytics()
    assert fraud["total_referrals_screened"] > 0
    assert len(fraud["suspicious_clusters"]) >= 2
    assert fraud["suspicious_clusters"][0]["ring_score"] > 80

    # 389. Payment Success Rate Dashboard
    payments = await service.get_payment_success_rate_dashboard(days=30)
    assert payments["overall_payment_success_rate_pct"] > 95.0
    assert len(payments["channel_performance"]) == 4

    # 390. Refund Rate Dashboard
    refunds = await service.get_refund_rate_dashboard(days=30)
    assert refunds["refund_pct_of_gross_revenue"] < 1.0  # Industry benchmark threshold
    assert refunds["chargeback_rate_pct"] < 0.1

    # 391. Support Resolution Time Report
    support_sla = await service.get_support_resolution_time_report(days=30)
    assert support_sla["first_response_time_minutes"] < support_sla["target_first_response_time_minutes"]
    assert support_sla["sla_compliance_rate_pct"] > 95.0

    # 392. Safety Incident Trend Report
    safety = await service.get_safety_incident_trends(days=30)
    assert safety["incident_rate_per_10k_trips"] < 5.0


@pytest.mark.asyncio
async def test_part16_permissioned_export_and_report_builder(db_session):
    """Features 393-395: Permission-gated Export, Scheduled Delivery & Custom Builder."""
    service = AnalyticsBiService(db_session)

    # Create admin user
    u_suffix = uuid.uuid4().hex[:6]
    admin = User(
        phone_number=f"+91999{u_suffix}",
        full_name="Compliance Officer Meera Desai",
        role="admin",
        is_active=True,
        mfa_enabled=True
    )
    db_session.add(admin)
    await db_session.flush()

    # 393. Export without pii:export -> verify redaction
    export_redacted = await service.export_dataset_with_permissions(
        dataset="CHAUFFEURS",
        user_permissions=["analytics:export"],
        admin_user_id=admin.id,
        export_format="CSV"
    )
    assert export_redacted["pii_redacted"] is True
    assert "HDFC-****" in export_redacted["data"] or "HDFC" in export_redacted["data"]

    # Export with finance:export denied -> throws 403
    with pytest.raises(HTTPException) as exc_info:
        await service.export_dataset_with_permissions(
            dataset="FINANCE_SETTLEMENTS",
            user_permissions=["analytics:export"],
            admin_user_id=admin.id
        )
    assert exc_info.value.status_code == 403

    # 394. Scheduled Report Delivery
    scheduled = await service.create_scheduled_report(
        payload={
            "report_name": "Weekly Executive Chauffeur Audit",
            "report_type": "DRIVER_UTILIZATION",
            "frequency": "WEEKLY",
            "delivery_channel": "EMAIL",
            "destination": "director@grandchauffeur.com",
            "export_format": "PDF"
        },
        admin_user_id=admin.id
    )
    assert scheduled["status"] == "SCHEDULED"

    reports_list = await service.list_scheduled_reports()
    assert any(r["report_name"] == "Weekly Executive Chauffeur Audit" for r in reports_list)

    # 395. Custom Report Builder
    custom = await service.create_custom_report(
        payload={
            "title": "BKC Surge vs Completion Rate",
            "description": "Correlates high-density financial district peak rides",
            "domain": "RIDES",
            "metrics": ["volume", "gross_revenue_inr", "completion_pct"],
            "dimensions": ["zone", "date"]
        },
        admin_user_id=admin.id
    )
    assert custom["status"] == "CREATED"

    custom_exec = await service.execute_custom_report(custom["id"])
    assert custom_exec["report_id"] == custom["id"]
    assert len(custom_exec["data"]) > 0


@pytest.mark.asyncio
async def test_part16_governance_auditing_and_privacy_dsar(db_session):
    """Features 396-400: Audit Filters, Action Justification, Retention Rules & DSAR."""
    service = AnalyticsBiService(db_session)

    u_suffix = uuid.uuid4().hex[:6]
    admin = User(
        phone_number=f"+91911{u_suffix}",
        full_name="Director Devendra Singhania",
        role="admin",
        is_active=True,
        mfa_enabled=True
    )
    customer = User(
        phone_number=f"+91982{u_suffix}",
        full_name="Guest Rohan Malhotra",
        role="customer",
        is_active=True
    )
    db_session.add_all([admin, customer])
    await db_session.flush()

    # 397. Action Justification Requirement: Reject short reason (< 15 chars)
    with pytest.raises(HTTPException) as exc_short:
        await service.record_action_justification(
            admin_user_id=admin.id,
            action_code="DRIVER_SUSPENSION",
            target_resource_type="driver_profiles",
            target_resource_id="drv-8891",
            justification_notes="Bad behavior",  # < 15 chars
            ticket_incident_ref="INC-9901"
        )
    assert exc_short.value.status_code == 422

    # Reject missing ticket ref
    with pytest.raises(HTTPException) as exc_ticket:
        await service.record_action_justification(
            admin_user_id=admin.id,
            action_code="DRIVER_SUSPENSION",
            target_resource_type="driver_profiles",
            target_resource_id="drv-8891",
            justification_notes="Chauffeur breached platform speed limit policy twice on Western Express Highway.",
            ticket_incident_ref=""
        )
    assert exc_ticket.value.status_code == 422

    # Valid Justification
    valid_just = await service.record_action_justification(
        admin_user_id=admin.id,
        action_code="DRIVER_SUSPENSION",
        target_resource_type="driver_profiles",
        target_resource_id="drv-8891",
        justification_notes="Chauffeur breached platform speed limit policy twice on Western Express Highway.",
        ticket_incident_ref="INC-CHAUFFEUR-8821"
    )
    assert valid_just["status"] == "APPROVED_AND_LOGGED"

    # 396. Audit Log Filters
    audit_logs = await service.get_filtered_audit_logs(action="DRIVER_SUSPENSION")
    assert audit_logs["total_count"] >= 1
    assert any(l["action"] == "DRIVER_SUSPENSION" for l in audit_logs["logs"])

    # 398. Sensitive Record Access Ledger
    sens_log = await service.log_sensitive_record_access(
        admin_user_id=admin.id,
        record_type="DRIVER_BANK_DETAILS",
        record_identifier="acc-hdfc-9912",
        justification_reason="Investigating payout failure reported in ticket INC-8821",
        ticket_ref="INC-8821"
    )
    assert sens_log["status"] == "LOGGED_TAMPER_EVIDENT"

    sens_history = await service.get_sensitive_record_access_history(limit=10)
    assert any(h["record_type"] == "DRIVER_BANK_DETAILS" for h in sens_history)

    # 399. Data Retention Policy Controls
    policies = await service.list_retention_policies()
    assert len(policies) >= 5
    tax_policy = next(p for p in policies if p["data_category"] == "INVOICES_TAX_RECORDS")
    assert tax_policy["is_statutory_mandatory"] is True
    assert tax_policy["retention_ttl_days"] == 2555  # 7 years statutory

    # Attempt to illegally reduce statutory tax retention below 2555 days -> Must raise 422
    with pytest.raises(HTTPException) as exc_tax:
        await service.update_retention_policy(
            category="INVOICES_TAX_RECORDS",
            new_ttl_days=365,  # Illegal under Indian IT Act (min 2555)
            admin_user_id=admin.id,
            justification="Routine storage cost optimization"
        )
    assert exc_tax.value.status_code == 422
    assert "Statutory Legal Constraint" in exc_tax.value.detail

    # Legal update for GPS Telemetry (e.g. from 90 to 60 days)
    update_gps = await service.update_retention_policy(
        category="GPS_TELEMETRY",
        new_ttl_days=60,
        admin_user_id=admin.id,
        justification="Privacy minimization alignment with European GDPR standards"
    )
    assert update_gps["status"] == "UPDATED_COMPLIANT"
    assert update_gps["new_ttl_days"] == 60

    # Purge execution dry-run
    purge_dry = await service.run_retention_purge(category="GPS_TELEMETRY", dry_run=True)
    assert purge_dry["status"] == "COMPLETED"
    assert purge_dry["dry_run"] is True

    # 400. Privacy Request Processing Dashboard (DSAR)
    dsar_submission = await service.submit_privacy_request(
        user_id=customer.id,
        user_role="CUSTOMER",
        request_type="RIGHT_TO_ERASURE",
        details="Please permanently erase my personal contact information and ride records."
    )
    assert dsar_submission["status"] == "UNDER_PROCESSING"
    assert "DSAR-2026-" in dsar_submission["request_number"]

    # Admin reviews and fulfills DSAR
    dsar_review = await service.review_privacy_request(
        request_id=dsar_submission["id"],
        new_status="FULFILLED_ERASED",
        resolution_notes="Contact data anonymized. Invoices retained per statutory 7-year audit requirements.",
        admin_user_id=admin.id
    )
    assert dsar_review["status"] == "FULFILLED_ERASED"
    assert dsar_review["fulfilled_at"] is not None
