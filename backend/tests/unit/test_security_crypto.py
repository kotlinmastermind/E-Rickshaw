import pytest
from backend.infrastructure.security.crypto import (
    generate_secure_otp, hash_secret, create_jwt_token,
    decode_jwt_token, verify_hmac_signature, verify_otp_constant_time
)

def test_otp_generation():
    otp = generate_secure_otp(6)
    assert len(otp) == 6
    assert otp.isdigit()

def test_otp_constant_time_verification():
    assert verify_otp_constant_time("123456", "123456") is True
    assert verify_otp_constant_time("123456", "654321") is False
    assert verify_otp_constant_time("123456", "12345") is False

def test_jwt_lifecycle():
    token = create_jwt_token(user_id="user_test_uuid_123", role="customer", session_id="session_456")
    payload = decode_jwt_token(token)
    assert payload["sub"] == "user_test_uuid_123"
    assert payload["role"] == "customer"
    assert payload["session_id"] == "session_456"
    assert payload["iss"] == "vaisentra-mobility"
    assert payload["aud"] == "vaisentra-mobility-clients"

def test_jwt_backward_compatibility():
    import jwt
    from backend.core.config import settings
    legacy_payload = {
        "sub": "user_legacy_123",
        "role": "driver",
        "session_id": "session_legacy",
        "token_type": "access",
        "iss": "luxury-ride-platform",
        "aud": "luxury-ride-clients"
    }
    legacy_token = jwt.encode(legacy_payload, settings.SECRET_KEY, algorithm=settings.ALGORITHM)
    decoded = decode_jwt_token(legacy_token)
    assert decoded["sub"] == "user_legacy_123"
    assert decoded["iss"] == "luxury-ride-platform"

def test_hmac_signature_verification():
    secret = "whsec_test_secret_key"
    payload = b'{"order_id": "order_123", "status": "captured"}'
    import hmac, hashlib
    valid_sig = hmac.new(secret.encode("utf-8"), payload, hashlib.sha256).hexdigest()

    assert verify_hmac_signature(payload, valid_sig, secret) is True
    assert verify_hmac_signature(payload, "invalid_forged_signature", secret) is False
