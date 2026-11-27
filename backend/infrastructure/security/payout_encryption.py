"""Authenticated encryption for payout destinations at rest."""
import base64
import hashlib
import os

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

from backend.core.config import settings

_AAD = b"vaisentra:payout-destination:v1"


def _cipher() -> AESGCM:
    configured = (settings.PAYOUT_DESTINATION_ENCRYPTION_KEY or "").strip()
    if len(configured) < 32:
        raise RuntimeError("PAYOUT_DESTINATION_ENCRYPTION_KEY must contain at least 32 characters.")
    return AESGCM(hashlib.sha256(configured.encode("utf-8")).digest())


def encrypt_payout_destination(value: str) -> str:
    if not value:
        raise ValueError("Payout destination value cannot be empty.")
    nonce = os.urandom(12)
    ciphertext = _cipher().encrypt(nonce, value.encode("utf-8"), _AAD)
    return "v1:" + base64.urlsafe_b64encode(nonce + ciphertext).decode("ascii")


def decrypt_payout_destination(value: str) -> str:
    if not value or not value.startswith("v1:"):
        raise RuntimeError("Unsupported or missing payout destination ciphertext.")
    try:
        raw = base64.urlsafe_b64decode(value[3:].encode("ascii"))
        if len(raw) < 29:
            raise ValueError("Ciphertext is truncated.")
        return _cipher().decrypt(raw[:12], raw[12:], _AAD).decode("utf-8")
    except Exception as exc:
        raise RuntimeError("Payout destination decryption failed; check the configured key.") from exc
