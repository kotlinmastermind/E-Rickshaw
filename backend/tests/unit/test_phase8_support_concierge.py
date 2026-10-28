import pytest
import uuid
import io
from datetime import datetime, timezone, timedelta
from fastapi import UploadFile

from backend.application.services.support_advanced_service import SupportAdvancedService
from backend.domain.models import (
    User, CustomerProfile, Ride, Payment, Wallet, VehicleCategory, FareRule,
    SupportTicket, SupportMessage, HelpArticle, HelpArticleRating
)

@pytest.mark.asyncio
async def test_part8_support_help_center_and_incidents(db_session):
    service = SupportAdvancedService(db_session)
    await service.seed_articles_and_advisories_if_empty()

    # 176. Searchable help center
    all_articles = await service.search_help_articles()
    assert len(all_articles) >= 5

    cancellation_articles = await service.search_help_articles(category="CANCELLATION")
    assert len(cancellation_articles) >= 1
    assert "cancellation-policy" in cancellation_articles[0]["slug"]

    # 177. Contextual help from booking screens
    booking_articles = await service.search_help_articles(context_tag="booking_screen")
    assert len(booking_articles) >= 2

    # 192. Help article rating
    first_art = all_articles[0]
    rating_res = await service.rate_help_article(
        user_id="usr-test-rater",
        article_id=first_art["id"],
        was_helpful=True,
        feedback_text="Very clear and reassuring policy."
    )
    assert rating_res["status"] == "RATING_SAVED"
    assert rating_res["helpful_count"] > first_art["helpful_count"]

    # 193. Known incident banner
    incidents = await service.get_active_incidents()
    assert len(incidents) >= 1
    assert any("Traffic" in inc["title"] or "UPI" in inc["title"] for inc in incidents)

    # 194. Service status page
    status_page = await service.get_service_status()
    assert status_page["overall_status"] in {"OPERATIONAL", "DEGRADED"}
    assert len(status_page["services"]) == 4
    service_names = {s["service"] for s in status_page["services"]}
    assert "FLEET_DISPATCH" in service_names
    assert "PAYMENT_GATEWAYS" in service_names

@pytest.mark.asyncio
async def test_part8_guided_cancellation_and_fee_explanation(db_session):
    u_suffix = uuid.uuid4().hex[:6]
    user = User(phone_number=f"+9198{uuid.uuid4().int % 100000000:08d}", role="customer", full_name="VIP Lord Hastings")
    db_session.add(user)
    await db_session.flush()

    category = VehicleCategory(code=f"ROYAL_SEDAN_{u_suffix}", name="Executive Sedan", capacity=3)
    db_session.add(category)
    await db_session.flush()

    rule = FareRule(
        category_id=category.id,
        version=1,
        base_fare_cents=50000,
        per_km_cents=3500,
        per_minute_cents=500,
        minimum_fare_cents=100000,
        cancellation_fee_cents=30000,
        surge_multiplier=1.0,
        is_active=True
    )
    db_session.add(rule)
    await db_session.flush()

    customer = CustomerProfile(user_id=user.id)
    db_session.add(customer)
    await db_session.flush()

    ride = Ride(
        customer_id=customer.id,
        vehicle_category_id=category.id,
        fare_rule_id=rule.id,
        status="ALLOCATED",
        pickup_address="Taj Mahal Palace, Colaba",
        pickup_latitude=18.9217,
        pickup_longitude=72.8332,
        destination_address="Bandra Kurla Complex",
        destination_latitude=19.0657,
        destination_longitude=72.8687,
        estimated_distance_km=12.5,
        estimated_duration_minutes=25.0,
        estimated_fare_cents=160000,
        start_otp="5521",
        requested_at=datetime.now(timezone.utc) - timedelta(minutes=2)  # Within 5-min grace
    )
    db_session.add(ride)
    await db_session.flush()

    service = SupportAdvancedService(db_session)

    # 178 & 179: Cancellation guidance within grace period
    guidance_grace = await service.get_cancellation_guidance(user.id, ride.id)
    assert guidance_grace["is_within_grace_period"] is True
    assert guidance_grace["applicable_cancellation_fee_cents"] == 0
    assert len(guidance_grace["reasons"]) >= 5

    # Execute cancellation
    cancel_res = await service.execute_guided_cancellation(
        user_id=user.id,
        ride_id=ride.id,
        reason_code="PLANS_CHANGED",
        notes="Dinner engagement moved to tomorrow."
    )
    assert cancel_res["status"] == "CUSTOMER_CANCELLED"
    assert cancel_res["cancellation_fee_cents"] == 0
    assert cancel_res["waiver_granted"] is True

@pytest.mark.asyncio
async def test_part8_tickets_routing_timeline_attachments_and_csat(db_session):
    u_suffix = uuid.uuid4().hex[:6]
    user = User(phone_number=f"+9198{uuid.uuid4().int % 100000000:08d}", role="customer", full_name="Lady Catherine")
    db_session.add(user)
    await db_session.flush()

    service = SupportAdvancedService(db_session)

    # 180, 184, 191, 196, 200: In-app ticket creation, routing, language, complaint ref, data retention
    ticket_res = await service.create_support_ticket(
        user_id=user.id,
        subject="Lost silk scarf in Maybach rear seat",
        category="LOST_PROPERTY",
        description="Left a Hermès cashmere scarf in the rear left door pocket.",
        priority="HIGH",
        language_preference="en",
        data_retention_days=365
    )
    assert ticket_res["status"] == "CREATED"
    assert ticket_res["assigned_squad"] == "FLEET_OPERATIONS"  # 184 routing verified
    assert ticket_res["complaint_reference_number"].startswith("CMP-")  # 196 reference verified
    assert ticket_res["data_retention_days"] == 365  # 200 retention verified
    t_id = ticket_res["ticket_id"]

    # 198: Duplicate ticket detection
    dup_res = await service.create_support_ticket(
        user_id=user.id,
        subject="Second inquiry for silk scarf",
        category="LOST_PROPERTY",
        description="Any update on the scarf?",
        allow_duplicate=False
    )
    assert dup_res["status"] == "DUPLICATE_WARNING"
    assert dup_res["existing_ticket_id"] == t_id

    # 181: Ticket attachment upload
    file_bytes = b"%PDF-1.5 SAMPLE-INVOICE-AND-CLAIM-PROOF-2026"
    upload_file = UploadFile(
        file=io.BytesIO(file_bytes),
        filename="scarf_receipt.pdf",
        headers={"content-type": "application/pdf"}
    )
    attach_res = await service.upload_ticket_attachment(
        user_id=user.id,
        ticket_id=t_id,
        file=upload_file,
        note="Scarf purchase receipt from boutique."
    )
    assert attach_res["status"] == "ATTACHMENT_UPLOADED"
    assert attach_res["attachment_path"] is not None

    # 182: Ticket status & response timeline
    timeline = await service.get_ticket_timeline(t_id, user.id)
    assert timeline["ticket_id"] == t_id
    assert len(timeline["events"]) >= 2
    assert timeline["assigned_squad"] == "FLEET_OPERATIONS"

    # 195: Support escalation request
    esc_res = await service.escalate_ticket(
        user_id=user.id,
        ticket_id=t_id,
        reason="Flight departs in 3 hours; urgently need chauffeur contact."
    )
    assert esc_res["status"] == "ESCALATED"
    assert esc_res["priority"] == "URGENT"

    # 199: Support message read receipts
    read_res = await service.mark_messages_read(user.id, t_id)
    assert read_res["status"] == "MESSAGES_READ"

    # 197: Support satisfaction survey
    survey_res = await service.submit_csat_survey(
        user_id=user.id,
        ticket_id=t_id,
        score=5,
        comment="Concierge delivered item to airport VIP terminal in 45 minutes. Exceptional!"
    )
    assert survey_res["status"] == "CSAT_RECORDED"
    assert survey_res["csat_score"] == 5

    # 200: Support data retention preference display
    policy = await service.get_data_retention_policy(user.id)
    assert policy["default_retention_days"] == 365
    assert len(policy["applicable_regulations"]) >= 2

@pytest.mark.asyncio
async def test_part8_self_service_workflows_and_callbacks(db_session):
    u_suffix = uuid.uuid4().hex[:6]
    user = User(phone_number=f"+9198{uuid.uuid4().int % 100000000:08d}", role="customer", full_name="Sir Arthur")
    db_session.add(user)
    await db_session.flush()

    category = VehicleCategory(code=f"VIP_EV_{u_suffix}", name="Luxury EV", capacity=4)
    db_session.add(category)
    await db_session.flush()

    customer = CustomerProfile(user_id=user.id)
    db_session.add(customer)
    await db_session.flush()

    rule = FareRule(
        category_id=category.id,
        version=1,
        base_fare_cents=50000,
        per_km_cents=3500,
        per_minute_cents=500,
        minimum_fare_cents=100000,
        cancellation_fee_cents=30000,
        surge_multiplier=1.0,
        is_active=True
    )
    db_session.add(rule)
    await db_session.flush()

    ride = Ride(
        customer_id=customer.id,
        vehicle_category_id=category.id,
        fare_rule_id=rule.id,
        status="COMPLETED",
        pickup_address="St. Regis Penthouse Suites",
        pickup_latitude=18.9950,
        pickup_longitude=72.8250,
        destination_address="CSM T2 Airport",
        destination_latitude=19.0880,
        destination_longitude=72.8680,
        estimated_distance_km=18.0,
        estimated_duration_minutes=35.0,
        estimated_fare_cents=180000,
        final_fare_cents=180000,
        start_otp="7712",
        requested_at=datetime.now(timezone.utc) - timedelta(hours=3)
    )
    db_session.add(ride)
    await db_session.flush()

    service = SupportAdvancedService(db_session)

    # 185: Self-service payment troubleshooting
    diag = await service.troubleshoot_payment(
        user_id=user.id,
        error_code="UPI_BANK_TIMEOUT",
        payment_method="UPI",
        ride_id=ride.id
    )
    assert diag["status"] == "DIAGNOSTIC_COMPLETED"
    assert "NPCI" in diag["title"] or "Timeout" in diag["title"]
    assert diag["can_auto_retry"] is True

    # 186: Self-service account recovery
    rec = await service.initiate_account_recovery(
        user_id=user.id,
        recovery_type="BIOMETRIC_RESYNC"
    )
    assert rec["status"] == "RECOVERY_INITIATED"
    assert rec["challenge_token"].startswith("REC-")

    # 187: Self-service refund request with instant goodwill approval (threshold <= ₹500)
    refund = await service.submit_refund_request(
        user_id=user.id,
        ride_id=ride.id,
        reason="Late arrival delay 8 minutes",
        requested_cents=40000,  # ₹400 <= ₹500 threshold
        explanation="Driver held in security lane at entrance."
    )
    assert refund["status"] == "APPROVED_INSTANT"
    assert refund["auto_approved"] is True
    assert refund["refund_amount_cents"] == 40000

    # 188: Fare review evidence submission
    fare_rev = await service.submit_fare_review(
        user_id=user.id,
        ride_id=ride.id,
        disputed_amount_cents=35000,
        evidence_text="Billed for Bandra-Worli Sea Link toll twice on invoice.",
        evidence_url="https://cdn.luxurychauffeur.com/toll_slip_902.jpg"
    )
    assert fare_rev["status"] == "FARE_REVIEW_LOGGED"
    assert fare_rev["evidence_attached"] is True

    # 189: Lost-item claim workflow
    lost = await service.report_lost_item(
        user_id=user.id,
        ride_id=ride.id,
        item_category="PHONE",
        item_description="iPhone 16 Pro Max in Hermès Leather Case",
        seat_location="Between rear executive seats"
    )
    assert lost["status"] == "LOST_ITEM_REGISTERED"
    assert lost["lost_item_status"] == "REPORTED"

    # 190: Support callback request
    callback = await service.request_callback(
        user_id=user.id,
        phone_number="+919876543210",
        preferred_time_window="IMMEDIATE",
        subject="Airport Transfer Coordination"
    )
    assert callback["status"] == "CALLBACK_SCHEDULED"
    assert callback["time_window"] == "IMMEDIATE"

    # 183: Support conversation notifications
    notifs = await service.get_support_notifications(user.id)
    assert isinstance(notifs, list)
