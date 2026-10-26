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

_storage_instance = LocalPrivateStorageProvider(settings.STORAGE_LOCAL_DIR)

def get_storage_provider() -> StorageProvider:
    return _storage_instance
