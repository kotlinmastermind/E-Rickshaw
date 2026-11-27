"""Regression tests proving Admin analytics never substitute demo metrics for empty tables."""

import pytest

from backend.application.services.analytics_bi_service import AnalyticsBiService


@pytest.mark.asyncio
async def test_admin_analytics_reports_empty_database_as_empty_not_demo_data(db_session):
    service = AnalyticsBiService(db_session)

    funnel = await service.get_ride_funnel_conversion(days=30)
    assert funnel["total_quotes"] == 0
    assert funnel["total_completed"] == 0
    assert funnel["overall_conversion_pct"] == 0.0
    assert [stage["volume"] for stage in funnel["stages"]] == [0, 0, 0, 0, 0]
    assert funnel["channel_conversions"] == []

    cancellations = await service.get_cancellation_analytics(days=30)
    assert cancellations["total_cancellations"] == 0
    assert cancellations["cancellation_rate_pct"] == 0.0
    assert cancellations["by_actor"] == {}
    assert cancellations["reasons_breakdown"] == []
    assert cancellations["financial_impact"]["cancellation_fee_billed_cents"] is None

    acceptance = await service.get_driver_acceptance_analytics(days=30)
    assert acceptance["total_offers_dispatched"] == 0
    assert acceptance["offers_accepted"] == 0
    assert acceptance["average_response_latency_seconds"] is None
    assert acceptance["acceptance_by_tier"] == []

    utilization = await service.get_driver_utilization_analytics(days=30)
    assert utilization["total_online_hours"] == 0.0
    assert utilization["on_trip_hours"] == 0.0
    assert utilization["fleet_utilization_pct"] == 0.0
    assert utilization["avg_revenue_per_online_hour_inr"] is None
    assert utilization["hourly_utilization_curve"] == []

    eta = await service.get_pickup_eta_accuracy_report(days=30)
    assert eta["total_trips_analyzed"] == 0
    assert eta["mean_absolute_error_minutes"] is None
    assert eta["accuracy_data_available"] is False

    fare = await service.get_fare_estimate_accuracy_report(days=30)
    assert fare["total_completed_trips"] == 0
    assert fare["compared_trips"] == 0
    assert fare["exact_match_rate_pct"] is None
    assert fare["variance_categories"] == [
        {"category": "Exact Match (0% difference)", "count": 0, "pct": 0.0},
        {"category": "Minor Addition (0-10% higher)", "count": 0, "pct": 0.0},
        {"category": "Significant Addition (>10% higher)", "count": 0, "pct": 0.0},
        {"category": "Fare Reduction", "count": 0, "pct": 0.0},
    ]

    supply = await service.get_supply_forecast_by_zone()
    assert supply["fleet_status"]["total_registered_chauffeurs"] == 0
    assert supply["fleet_status"]["approved_drivers"] == 0
    assert supply["zone_balances"] == []

    shared = await service.get_shared_ride_matching_performance(days=30)
    assert shared["total_shared_ride_requests"] == 0
    assert shared["successfully_paired_requests"] == 0
    assert shared["financial_savings"]["total_passenger_savings_cents"] == 0

    customers = await service.get_customer_retention_cohorts()
    assert customers["cohorts"] == []
    assert customers["total_customers"] == 0
    assert customers["data_available"] is False

    drivers = await service.get_driver_retention_cohorts()
    assert drivers["cohorts"] == []
    assert drivers["total_registered_drivers"] == 0
    assert drivers["annual_chauffeur_retention_rate_pct"] is None

    promos = await service.get_promo_campaign_effectiveness()
    assert promos["active_campaigns_count"] == 0
    assert promos["campaigns"] == []
    assert promos["total_discount_spend_inr"] == 0.0

    referrals = await service.get_referral_fraud_analytics()
    assert referrals["total_referrals_screened"] == 0
    assert referrals["flagged_suspicious_referrals"] == 0
    assert referrals["suspicious_clusters"] == []

    payments = await service.get_payment_success_rate_dashboard(days=30)
    assert payments["total_transactions_attempted"] == 0
    assert payments["total_successful"] == 0
    assert payments["overall_payment_success_rate_pct"] == 0.0
    assert payments["channel_performance"] == []

    refunds = await service.get_refund_rate_dashboard(days=30)
    assert refunds["total_refund_count"] == 0
    assert refunds["total_refund_volume_cents"] == 0
    assert refunds["refund_pct_of_gross_revenue"] is None
    assert refunds["refund_categories"] == []

    support = await service.get_support_resolution_time_report(days=30)
    assert support["total_support_tickets"] == 0
    assert support["mean_time_to_resolution_hours"] is None
    assert support["by_severity"] == []

    safety = await service.get_safety_incident_trends(days=30)
    assert safety["total_trips_conducted"] == 0
    assert safety["total_safety_incidents"] == 0
    assert safety["incident_rate_per_10k_trips"] is None
    assert safety["severity_breakdown"] == []


@pytest.mark.asyncio
async def test_admin_export_does_not_invent_people_or_financial_accounts(db_session):
    service = AnalyticsBiService(db_session)
    for dataset in ("RIDES", "CHAUFFEURS", "GUESTS"):
        result = await service.export_dataset_with_permissions(
            dataset=dataset,
            user_permissions=["analytics:export"],
            admin_user_id="test-admin",
            export_format="JSON",
        )
        assert result["row_count"] == 0
        assert result["records"] == []
