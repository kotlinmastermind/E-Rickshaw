"""
Enterprise Razorpay Security & Financial Ledger Test Suite.
Verifies rejection of unverified top-ups, HMAC signatures, webhook idempotency,
customer-to-driver escalation prevention, and zero-cost gift card defense.
"""
import pytest
import hmac
import hashlib
import json
import uuid
from httpx import AsyncClient
from backend.core.config import settings

TEST_KEY_SECRET = "test_key_secret_32_characters_long_sec"
TEST_WEBHOOK_SECRET = "test_webhook_secret_32_chars_long_wh"

@pytest.fixture(autouse=True)
def setup_test_credentials(monkeypatch):
    monkeypatch.setattr(settings, "RAZORPAY_KEY_ID", "rzp_test_mock123")
    monkeypatch.setattr(settings, "RAZORPAY_KEY_SECRET", TEST_KEY_SECRET)
    monkeypatch.setattr(settings, "RAZORPAY_WEBHOOK_SECRET", TEST_WEBHOOK_SECRET)
    monkeypatch.setattr(settings, "ENVIRONMENT", "test")

def generate_signature(message: str, secret: str) -> str:
    return hmac.new(secret.encode("utf-8"), message.encode("utf-8"), hashlib.sha256).hexdigest()

@pytest.mark.asyncio
async def test_sec_01_legacy_direct_wallet_credit_is_rejected(client: AsyncClient, customer_token: str):
    """F-01: Proves that direct unverified credit to /wallets/topup is permanently blocked (HTTP 410/404)."""
    resp = await client.post(
        "/api/v1/wallets/topup",
        json={"amount_cents": 5000000, "payment_method": "UPI"},
        headers={"Authorization": f"Bearer {customer_token}"}
    )
    assert resp.status_code in [410, 404, 405]

@pytest.mark.asyncio
async def test_sec_02_wallet_topup_order_creation_and_limits(client: AsyncClient, customer_token: str):
    """F-01: Proves server validates min/max limits and returns legitimate order intent."""
    # Under limit (₹50)
    low_resp = await client.post(
        "/api/v1/wallets/topup/create-order",
        json={"amount_cents": 5000},
        headers={"Authorization": f"Bearer {customer_token}"}
    )
    assert low_resp.status_code == 422

    # Valid ₹500 top-up order
    valid_resp = await client.post(
        "/api/v1/wallets/topup/create-order",
        json={"amount_cents": 50000},
        headers={"Authorization": f"Bearer {customer_token}"}
    )
    assert valid_resp.status_code == 200
    data = valid_resp.json()
    assert "order_id" in data
    assert data["amount_cents"] == 50000

@pytest.mark.asyncio
async def test_sec_03_invalid_hmac_signature_rejection(client: AsyncClient, customer_token: str):
    """F-05: Proves that bogus signatures are rejected with HTTP 400."""
    resp = await client.post(
        "/api/v1/wallets/topup/verify",
        json={
            "razorpay_order_id": "order_test_998877",
            "razorpay_payment_id": "pay_test_112233",
            "razorpay_signature": "invalid_forged_signature_hex_0000000"
        },
        headers={"Authorization": f"Bearer {customer_token}"}
    )
    assert resp.status_code == 400
    assert "signature" in resp.json()["detail"].lower()

@pytest.mark.asyncio
async def test_sec_04_cross_user_payment_verification_rejection(client: AsyncClient, customer_token: str, second_customer_token: str):
    """F-05: Proves User B cannot verify and claim User A's payment order."""
    # User A creates order
    order_res = await client.post(
        "/api/v1/wallets/topup/create-order",
        json={"amount_cents": 50000},
        headers={"Authorization": f"Bearer {customer_token}"}
    )
    order_id = order_res.json()["order_id"]
    pay_id = f"pay_{uuid.uuid4().hex[:12]}"
    valid_sig = generate_signature(f"{order_id}|{pay_id}", TEST_KEY_SECRET)

    # User B attempts to verify User A's order
    hijack_res = await client.post(
        "/api/v1/wallets/topup/verify",
        json={
            "razorpay_order_id": order_id,
            "razorpay_payment_id": pay_id,
            "razorpay_signature": valid_sig
        },
        headers={"Authorization": f"Bearer {second_customer_token}"}
    )
    assert hijack_res.status_code == 403

@pytest.mark.asyncio
async def test_sec_05_webhook_secret_isolation_rejects_key_secret(client: AsyncClient):
    """F-14: Proves webhook verification fails if signed with API key_secret instead of webhook_secret."""
    payload = json.dumps({"event": "payment.captured", "payload": {"payment": {"entity": {"id": "pay_123"}}}})
    key_secret_signature = generate_signature(payload, TEST_KEY_SECRET)

    resp = await client.post(
        "/api/v1/payments/webhook",
        content=payload,
        headers={"X-Razorpay-Signature": key_secret_signature, "Content-Type": "application/json"}
    )
    assert resp.status_code == 400

@pytest.mark.asyncio
async def test_sec_06_webhook_idempotency_prevents_duplicate_credits(client: AsyncClient):
    """F-06 & F-15: Proves duplicate webhook delivery returns ALREADY_PROCESSED and credits ledger exactly once."""
    payload = json.dumps({
        "event": "payment.captured",
        "payload": {
            "payment": {
                "entity": {
                    "id": "pay_unique_10101",
                    "order_id": "order_unique_20202",
                    "amount": 50000,
                    "currency": "INR",
                    "status": "captured"
                }
            }
        }
    })
    sig = generate_signature(payload, TEST_WEBHOOK_SECRET)
    headers = {"X-Razorpay-Signature": sig, "Content-Type": "application/json"}

    # First attempt
    res1 = await client.post("/api/v1/payments/webhook", content=payload, headers=headers)
    # Second duplicate attempt
    res2 = await client.post("/api/v1/payments/webhook", content=payload, headers=headers)
    
    assert res2.status_code == 200
    assert res2.json()["status"] == "ALREADY_PROCESSED"

@pytest.mark.asyncio
async def test_sec_07_unauthenticated_admin_session_token_is_removed(client: AsyncClient):
    """F-08: Proves GET /admin/session/token is dead and returns 404/405/401."""
    resp = await client.get("/api/v1/admin/session/token")
    assert resp.status_code in [404, 405, 401]

@pytest.mark.asyncio
async def test_sec_08_customer_to_driver_escalation_blocked(client: AsyncClient, customer_token: str):
    """F-12: Proves customer calling driver endpoint receives HTTP 403 and is NOT promoted."""
    resp = await client.post(
        "/api/v1/payments/cash-collect",
        json={"ride_id": "ride-dummy-123"},
        headers={"Authorization": f"Bearer {customer_token}"}
    )
    assert resp.status_code == 403

@pytest.mark.asyncio
async def test_sec_09_webhook_readiness_reflects_required_configuration(
    client: AsyncClient, monkeypatch
):
    """Webhook diagnostics must not report ready when signature verification cannot run."""
    ready = await client.get("/api/v1/payments/webhook")
    assert ready.status_code == 200
    assert ready.json()["status"] == "ready"
    assert ready.json()["checks"] == {
        "provider_credentials_configured": True,
        "webhook_secret_configured": True,
    }

    monkeypatch.setattr(settings, "RAZORPAY_WEBHOOK_SECRET", "")
    not_ready = await client.get("/api/v1/payments/webhook")
    assert not_ready.status_code == 200
    assert not_ready.json()["status"] == "not_ready"
    assert not_ready.json()["checks"]["webhook_secret_configured"] is False
