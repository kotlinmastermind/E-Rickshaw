import os
import base64
import json
from typing import Dict, Any, Optional
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from backend.core.config import settings

# Master data encryption key derived from environment or secret configuration (32 bytes for AES-256-GCM)
_RAW_KEY = getattr(settings, "DATA_ENCRYPTION_KEY", None)
if _RAW_KEY and len(_RAW_KEY.encode()) >= 32:
    _AES_KEY = _RAW_KEY.encode()[:32]
else:
    # 32-byte deterministic fallback derived from settings.SECRET_KEY for testing/dev environments
    import hashlib
    _AES_KEY = hashlib.sha256(settings.SECRET_KEY.encode("utf-8")).digest()

def encrypt_sensitive_field(plaintext: str) -> str:
    """
    Encrypts sensitive customer/driver PII at rest using AES-256-GCM with a unique 96-bit nonce.
    Returns base64 encoded ciphertext string.
    """
    if not plaintext:
        return ""
    aesgcm = AESGCM(_AES_KEY)
    nonce = os.urandom(12)  # 96-bit nonce
    ciphertext = aesgcm.encrypt(nonce, plaintext.encode("utf-8"), None)
    payload = nonce + ciphertext
    return base64.b64encode(payload).decode("utf-8")

def decrypt_sensitive_field(ciphertext_b64: str) -> str:
    """
    Decrypts AES-256-GCM ciphertext using the configured key.
    """
    if not ciphertext_b64:
        return ""
    try:
        raw = base64.b64decode(ciphertext_b64.encode("utf-8"))
        nonce = raw[:12]
        ciphertext = raw[12:]
        aesgcm = AESGCM(_AES_KEY)
        decrypted = aesgcm.decrypt(nonce, ciphertext, None)
        return decrypted.decode("utf-8")
    except Exception:
        return "[ENCRYPTION_DECRYPTION_ERROR]"

def mask_phone_number(phone: str) -> str:
    """Masks phone number preserving only country code and last 4 digits (+91******1210)."""
    if not phone or len(phone) < 6:
        return "******"
    return f"{phone[:3]}{'*' * (len(phone) - 7)}{phone[-4:]}"

def mask_email(email: Optional[str]) -> str:
    """Masks email address (e.g. j***n@domain.com)."""
    if not email or "@" not in email:
        return "***@domain.com"
    parts = email.split("@")
    user = parts[0]
    domain = parts[1]
    if len(user) <= 2:
        masked_user = user[0] + "*"
    else:
        masked_user = user[0] + ("*" * (len(user) - 2)) + user[-1]
    return f"{masked_user}@{domain}"

def mask_financial_identifier(identifier: Optional[str]) -> str:
    """Masks bank account or UPI ID (e.g. VIP-BANK-****-4921)."""
    if not identifier or len(identifier) < 4:
        return "****"
    return f"{'*' * (len(identifier) - 4)}{identifier[-4:]}"

def redact_sensitive_dict(data: Dict[str, Any]) -> Dict[str, Any]:
    """Recursively redacts sensitive credentials, passwords, tokens, and PII from log payloads."""
    sensitive_keys = {
        "password", "secret", "token", "access_token", "refresh_token",
        "otp", "otp_code", "authorization", "start_otp", "signature",
        "private_key", "card_number", "cvv"
    }
    redacted = {}
    for k, v in data.items():
        if k.lower() in sensitive_keys:
            redacted[k] = "[REDACTED]"
        elif isinstance(v, dict):
            redacted[k] = redact_sensitive_dict(v)
        elif isinstance(v, list) and v and isinstance(v[0], dict):
            redacted[k] = [redact_sensitive_dict(item) for item in v]
        elif k.lower() in ["phone", "phone_number"]:
            redacted[k] = mask_phone_number(str(v))
        elif k.lower() == "email":
            redacted[k] = mask_email(str(v))
        else:
            redacted[k] = v
    return redacted
