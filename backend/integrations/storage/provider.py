import os
import hashlib
import hmac
import time
import re
import urllib.parse
from abc import ABC, abstractmethod
from typing import Tuple, Optional
from fastapi import UploadFile, HTTPException, status
from backend.core.config import settings

ALLOWED_MIME_TYPES = {
    "application/pdf": [b"%PDF"],
    "image/jpeg": [b"\xff\xd8\xff"],
    "image/png": [b"\x89PNG\r\n\x1a\n"],
    "image/webp": [b"RIFF"]
}

PERMITTED_EXTENSIONS = {"pdf", "jpg", "jpeg", "png", "webp"}

MIME_EXTENSION_MAP = {
    "application/pdf": {"pdf"},
    "image/jpeg": {"jpg", "jpeg"},
    "image/png": {"png"},
    "image/webp": {"webp"}
}

class StorageProvider(ABC):
    @abstractmethod
    async def save_driver_document(self, driver_id: str, document_type: str, file: UploadFile) -> Tuple[str, str, int, str]:
        """Returns (storage_path, mime_type, file_size_bytes, file_hash_sha256)."""
        pass

    @abstractmethod
    async def generate_signed_url(self, storage_path: str, expires_in_seconds: int = 300) -> str:
        """Returns a short-lived authorized download URL for private KYC documents."""
        pass


def verify_signed_url_token(rel_path: str, expires: int, signature: str) -> bool:
    """Verifies that the HMAC signature and timestamp for a local private storage file are valid."""
    if expires < int(time.time()):
        return False
    sig_payload = f"{rel_path}:{expires}".encode("utf-8")
    expected = hmac.new(settings.SECRET_KEY.encode("utf-8"), sig_payload, hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected.lower(), signature.lower())


class LocalPrivateStorageProvider(StorageProvider):
    def __init__(self, base_dir: str):
        self.base_dir = os.path.abspath(base_dir)
        os.makedirs(self.base_dir, exist_ok=True)

    async def save_driver_document(self, driver_id: str, document_type: str, file: UploadFile) -> Tuple[str, str, int, str]:
        # Task 045: Prevent arbitrary file uploads & directory traversal
        if not re.match(r"^[a-zA-Z0-9_-]+$", driver_id):
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid driver identifier format.")
        if not re.match(r"^[a-zA-Z0-9_-]+$", document_type):
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid document type format.")

        filename = file.filename or ""
        ext = os.path.splitext(filename)[1].lower().lstrip(".")
        if ext and ext not in PERMITTED_EXTENSIONS:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Security validation failed: File extension .{ext} is not permitted. Permitted: {', '.join(sorted(PERMITTED_EXTENSIONS))}."
            )

        # Read initial bytes to verify magic number
        header = await file.read(16)
        if not header:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Uploaded file is empty.")

        # Validate MIME & magic bytes (Task 044)
        detected_mime = None
        for mime, magic_list in ALLOWED_MIME_TYPES.items():
            for magic in magic_list:
                if header.startswith(magic):
                    detected_mime = mime
                    break
            if detected_mime:
                break

        if not detected_mime:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Security validation failed: File signature does not match allowed types (PDF, JPEG, PNG, WEBP)."
            )

        # Check extension against detected MIME content
        if ext and ext not in MIME_EXTENSION_MAP.get(detected_mime, set()):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Security validation failed: File extension .{ext} does not match detected MIME content ({detected_mime})."
            )

        # Read remainder of file to enforce size and compute SHA-256
        rest = await file.read()
        full_content = header + rest
        file_size = len(full_content)

        if file_size > settings.STORAGE_MAX_FILE_SIZE_BYTES:
            raise HTTPException(
                status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
                detail=f"File exceeds maximum allowed size of {settings.STORAGE_MAX_FILE_SIZE_BYTES // (1024*1024)}MB."
            )

        sha256_hash = hashlib.sha256(full_content).hexdigest()

        driver_dir = os.path.abspath(os.path.join(self.base_dir, driver_id))
        if not driver_dir.startswith(self.base_dir):
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Access denied: Directory traversal detected.")
        os.makedirs(driver_dir, exist_ok=True)

        target_ext = "pdf" if detected_mime == "application/pdf" else ("png" if detected_mime == "image/png" else ("webp" if detected_mime == "image/webp" else "jpg"))
        safe_filename = f"{document_type.lower()}_{sha256_hash[:12]}.{target_ext}"
        target_path = os.path.abspath(os.path.join(driver_dir, safe_filename))
        if not target_path.startswith(driver_dir):
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Access denied: Unsafe destination path.")

        with open(target_path, "wb") as f:
            f.write(full_content)

        return (target_path, detected_mime, file_size, sha256_hash)

    async def generate_signed_url(self, storage_path: str, expires_in_seconds: int = 300) -> str:
        # Task 043: Implement signed document URLs for local private storage
        now = int(time.time())
        exp = now + expires_in_seconds
        clean_path = os.path.abspath(storage_path)
        if not clean_path.startswith(self.base_dir):
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Access denied: Invalid storage reference.")

        rel_path = os.path.relpath(clean_path, self.base_dir).replace("\\", "/")
        sig_payload = f"{rel_path}:{exp}".encode("utf-8")
        sig = hmac.new(settings.SECRET_KEY.encode("utf-8"), sig_payload, hashlib.sha256).hexdigest()
        encoded_rel = urllib.parse.quote(rel_path)
        return f"/api/v1/driver-onboarding/documents/download?file={encoded_rel}&expires={exp}&sig={sig}"


class SupabaseCloudStorageProvider(StorageProvider):
    """
    Cloud object storage provider using private Supabase Storage buckets.
    Persists driver KYC files in a private bucket with fallback to local private storage.
    Never exposes public bucket URLs (Task 041).
    """
    def __init__(self, fallback: LocalPrivateStorageProvider):
        self.fallback = fallback
        self.supabase_url = os.getenv("SUPABASE_URL") or getattr(settings, "SUPABASE_URL", None)
        self.supabase_key = os.getenv("SUPABASE_KEY") or getattr(settings, "SUPABASE_KEY", None)
        self.bucket = "driver-documents"

    async def save_driver_document(self, driver_id: str, document_type: str, file: UploadFile) -> Tuple[str, str, int, str]:
        # Perform local validation & save first
        local_path, detected_mime, file_size, sha256_hash = await self.fallback.save_driver_document(driver_id, document_type, file)
        
        # If Supabase credentials are configured, persist to remote cloud bucket
        if self.supabase_url and self.supabase_key:
            try:
                import httpx
                ext = "pdf" if detected_mime == "application/pdf" else ("png" if detected_mime == "image/png" else ("webp" if detected_mime == "image/webp" else "jpg"))
                remote_object_name = f"{driver_id}/{document_type.lower()}_{sha256_hash[:12]}.{ext}"
                upload_url = f"{self.supabase_url.rstrip('/')}/storage/v1/object/{self.bucket}/{remote_object_name}"
                
                with open(local_path, "rb") as f:
                    file_bytes = f.read()
                    
                headers = {
                    "apikey": self.supabase_key,
                    "Authorization": f"Bearer {self.supabase_key}",
                    "Content-Type": detected_mime,
                    "x-upsert": "true"
                }
                async with httpx.AsyncClient() as client:
                    resp = await client.post(upload_url, content=file_bytes, headers=headers, timeout=12.0)
                    if resp.status_code in [200, 201]:
                        # Task 041: Private storage reference - NO public URL
                        cloud_ref = f"private://{self.bucket}/{remote_object_name}"
                        return (cloud_ref, detected_mime, file_size, sha256_hash)
            except Exception:
                pass # Gracefully return local path on network fallback
                
        return (local_path, detected_mime, file_size, sha256_hash)

    async def generate_signed_url(self, storage_path: str, expires_in_seconds: int = 300) -> str:
        # Task 043: Implement signed download URL using Supabase Storage API
        if storage_path.startswith("private://") or f"{self.bucket}/" in storage_path:
            clean_path = storage_path.removeprefix("private://").removeprefix(f"{self.bucket}/")
            if self.supabase_url and self.supabase_key:
                try:
                    import httpx
                    sign_url = f"{self.supabase_url.rstrip('/')}/storage/v1/object/sign/{self.bucket}/{clean_path}"
                    headers = {
                        "apikey": self.supabase_key,
                        "Authorization": f"Bearer {self.supabase_key}",
                        "Content-Type": "application/json"
                    }
                    async with httpx.AsyncClient() as client:
                        resp = await client.post(sign_url, json={"expiresIn": expires_in_seconds}, headers=headers, timeout=10.0)
                        if resp.status_code == 200:
                            data = resp.json()
                            signed_suffix = data.get("signedURL")
                            if signed_suffix:
                                return f"{self.supabase_url.rstrip('/')}/storage/v1{signed_suffix}"
                except Exception:
                    pass
        return await self.fallback.generate_signed_url(storage_path, expires_in_seconds)


_local_storage = LocalPrivateStorageProvider(settings.STORAGE_LOCAL_DIR)
_storage_instance = SupabaseCloudStorageProvider(_local_storage)

def get_storage_provider() -> StorageProvider:
    return _storage_instance
