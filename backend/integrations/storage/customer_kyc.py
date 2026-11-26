"""Fail-closed private storage for customer identity documents.

Customer KYC files must never use the local filesystem fallback used by legacy
driver-document flows. Only a verified private Supabase Storage bucket is accepted.
"""
from __future__ import annotations

from urllib.parse import quote

import httpx
from fastapi import HTTPException, status

from backend.core.config import settings


class CustomerKycStorage:
    def __init__(self) -> None:
        self.base_url = (settings.SUPABASE_URL or "").rstrip("/")
        self.service_role_key = getattr(settings, "SUPABASE_SERVICE_ROLE_KEY", "") or ""
        self.bucket = getattr(settings, "CUSTOMER_KYC_STORAGE_BUCKET", "customer-kyc-documents")

    def _headers(self, content_type: str = "application/json") -> dict[str, str]:
        if not self.base_url or not self.service_role_key:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Customer KYC private storage is not configured.",
            )
        return {
            "apikey": self.service_role_key,
            "Authorization": f"Bearer {self.service_role_key}",
            "Content-Type": content_type,
        }

    async def _assert_private_bucket(self, client: httpx.AsyncClient) -> None:
        headers = self._headers()
        url = f"{self.base_url}/storage/v1/bucket/{quote(self.bucket, safe='')}"
        try:
            response = await client.get(url, headers=headers)
        except httpx.RequestError as exc:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Customer KYC private storage is temporarily unavailable.",
            ) from exc
        if response.status_code != 200:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Customer KYC private storage bucket is missing or inaccessible.",
            )
        try:
            bucket_config = response.json()
        except ValueError as exc:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Customer KYC storage configuration could not be verified.",
            ) from exc
        if not isinstance(bucket_config, dict) or bucket_config.get("public") is not False:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Customer KYC storage bucket must be private.",
            )

    async def upload(
        self,
        *,
        user_id: str,
        document_id: str,
        extension: str,
        mime_type: str,
        content: bytes,
    ) -> str:
        object_path = f"{user_id}/{document_id}.{extension}"
        encoded_path = quote(object_path, safe="/")
        url = f"{self.base_url}/storage/v1/object/{quote(self.bucket, safe='')}/{encoded_path}"
        headers = self._headers(mime_type)
        headers["x-upsert"] = "false"
        try:
            async with httpx.AsyncClient(timeout=20.0) as client:
                await self._assert_private_bucket(client)
                response = await client.post(url, content=content, headers=headers)
        except HTTPException:
            raise
        except httpx.RequestError as exc:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Customer KYC document could not be uploaded to private storage.",
            ) from exc
        if response.status_code not in (200, 201):
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Customer KYC document could not be saved to private storage.",
            )
        return f"private://{self.bucket}/{object_path}"

    async def create_signed_url(self, storage_path: str, expires_in_seconds: int = 60) -> str:
        prefix = f"private://{self.bucket}/"
        if not storage_path.startswith(prefix):
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid customer KYC storage reference.")
        object_path = storage_path[len(prefix):]
        encoded_path = quote(object_path, safe="/")
        url = f"{self.base_url}/storage/v1/object/sign/{quote(self.bucket, safe='')}/{encoded_path}"
        headers = self._headers()
        try:
            async with httpx.AsyncClient(timeout=12.0) as client:
                await self._assert_private_bucket(client)
                response = await client.post(url, json={"expiresIn": expires_in_seconds}, headers=headers)
        except HTTPException:
            raise
        except httpx.RequestError as exc:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Customer KYC document access is temporarily unavailable.",
            ) from exc
        if response.status_code != 200:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Customer KYC document access could not be authorized.",
            )
        try:
            signed_url = response.json().get("signedURL")
        except (ValueError, AttributeError) as exc:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Customer KYC signed URL response was invalid.",
            ) from exc
        if not isinstance(signed_url, str) or not signed_url:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Customer KYC signed URL was not returned by storage.",
            )
        if signed_url.startswith("http://") or signed_url.startswith("https://"):
            return signed_url
        if signed_url.startswith("/storage/v1/"):
            return f"{self.base_url}{signed_url}"
        return f"{self.base_url}/storage/v1/{signed_url.lstrip("/")}"

    async def delete(self, storage_path: str) -> None:
        prefix = f"private://{self.bucket}/"
        if not storage_path.startswith(prefix):
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid customer KYC storage reference.")
        object_path = storage_path[len(prefix):]
        encoded_path = quote(object_path, safe="/")
        url = f"{self.base_url}/storage/v1/object/{quote(self.bucket, safe='')}/{encoded_path}"
        headers = self._headers()
        try:
            async with httpx.AsyncClient(timeout=12.0) as client:
                await self._assert_private_bucket(client)
                response = await client.delete(url, headers=headers)
        except HTTPException:
            raise
        except httpx.RequestError as exc:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Customer KYC document could not be removed from private storage.",
            ) from exc
        if response.status_code not in (200, 204):
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Customer KYC document could not be removed from private storage.",
            )


customer_kyc_storage = CustomerKycStorage()
