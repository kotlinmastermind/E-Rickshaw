import os
import hashlib
from abc import ABC, abstractmethod
from typing import Tuple
from fastapi import UploadFile, HTTPException, status
from backend.core.config import settings

ALLOWED_MIME_TYPES = {
    "application/pdf": [b"%PDF"],
    "image/jpeg": [b"\xff\xd8\xff"],
    "image/png": [b"\x89PNG\r\n\x1a\n"],
    "image/webp": [b"RIFF"]
}

class StorageProvider(ABC):
    @abstractmethod
    async def save_driver_document(self, driver_id: str, document_type: str, file: UploadFile) -> Tuple[str, str, int, str]:
        """Returns (storage_path, mime_type, file_size_bytes, file_hash_sha256)."""
        pass

class LocalPrivateStorageProvider(StorageProvider):
    def __init__(self, base_dir: str):
        self.base_dir = base_dir
        os.makedirs(self.base_dir, exist_ok=True)

    async def save_driver_document(self, driver_id: str, document_type: str, file: UploadFile) -> Tuple[str, str, int, str]:
        # Read initial bytes to verify magic number
        header = await file.read(16)
        if not header:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Uploaded file is empty.")

        # Validate MIME & magic bytes
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

        driver_dir = os.path.join(self.base_dir, driver_id)
        os.makedirs(driver_dir, exist_ok=True)

        ext = "pdf" if detected_mime == "application/pdf" else ("png" if detected_mime == "image/png" else "jpg")
        safe_filename = f"{document_type.lower()}_{sha256_hash[:12]}.{ext}"
        target_path = os.path.join(driver_dir, safe_filename)

        with open(target_path, "wb") as f:
            f.write(full_content)

        return (target_path, detected_mime, file_size, sha256_hash)

class SupabaseCloudStorageProvider(StorageProvider):
    """
    Cloud object storage provider using Supabase Storage buckets.
    Persists driver KYC files in the cloud with fallback to local private storage.
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
                ext = "pdf" if detected_mime == "application/pdf" else ("png" if detected_mime == "image/png" else "jpg")
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
                        cloud_ref = f"{self.supabase_url.rstrip('/')}/storage/v1/object/public/{self.bucket}/{remote_object_name}"
                        return (cloud_ref, detected_mime, file_size, sha256_hash)
            except Exception:
                pass # Gracefully return local path on network fallback
                
        return (local_path, detected_mime, file_size, sha256_hash)

_local_storage = LocalPrivateStorageProvider(settings.STORAGE_LOCAL_DIR)
_storage_instance = SupabaseCloudStorageProvider(_local_storage)

def get_storage_provider() -> StorageProvider:
    return _storage_instance
