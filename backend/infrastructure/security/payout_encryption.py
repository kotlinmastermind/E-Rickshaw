"""Authenticated encryption for payout destinations at rest with key-rotation support."""
import base64
import hashlib
import os
from typing import Optional

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

from backend.core.config import settings

_AAD = b"vaisentra:payout-destination:v1"


def _build_cipher(raw_key: Optional[str], *, label: str) -> AESGCM:
    configured = (raw_key or "").strip()
    if len(configured) < 32:
        raise RuntimeError(f"{label} must contain at least 32 characters.")
    return AESGCM(hashlib.sha256(configured.encode("utf-8")).digest())


def _primary_cipher() -> AESGCM:
    return _build_cipher(settings.PAYOUT_DESTINATION_ENCRYPTION_KEY, label="PAYOUT_DESTINATION_ENCRYPTION_KEY")


def _previous_cipher() -> Optional[AESGCM]:
    prev = (getattr(settings, "PAYOUT_DESTINATION_ENCRYPTION_KEY_PREVIOUS", None) or "").strip()
    if not prev:
        return None
    return _build_cipher(prev, label="PAYOUT_DESTINATION_ENCRYPTION_KEY_PREVIOUS")


def encrypt_payout_destination(value: str) -> str:
    if not value:
        raise ValueError("Payout destination value cannot be empty.")
    nonce = os.urandom(12)
    ciphertext = _primary_cipher().encrypt(nonce, value.encode("utf-8"), _AAD)
    return "v1:" + base64.urlsafe_b64encode(nonce + ciphertext).decode("ascii")


def decrypt_payout_destination(value: str) -> str:
    if not value or not value.startswith("v1:"):
        raise RuntimeError("Unsupported or missing payout destination ciphertext.")
    try:
        raw = base64.urlsafe_b64decode(value[3:].encode("ascii"))
    except Exception as exc:
        raise RuntimeError("Payout destination ciphertext is malformed.") from exc
    if len(raw) < 29:
        raise RuntimeError("Payout destination ciphertext is truncated.")

    nonce, payload = raw[:12], raw[12:]
    primary = _primary_cipher()
    try:
        return primary.decrypt(nonce, payload, _AAD).decode("utf-8")
    except Exception as primary_exc:
        previous = _previous_cipher()
        if previous is not None:
            try:
                return previous.decrypt(nonce, payload, _AAD).decode("utf-8")
            except Exception:
                pass
        raise RuntimeError("Payout destination decryption failed; check the configured key.") from primary_exc
