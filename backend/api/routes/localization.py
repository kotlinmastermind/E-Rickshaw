from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.ext.asyncio import AsyncSession
from typing import Dict, Any, List, Optional

from backend.api.deps import get_db, get_current_user
from backend.domain.models.models import User
from backend.application.services.localization_service import LocalizationService

router = APIRouter(prefix="/localization", tags=["Dynamic Localization & Strings Catalog"])

@router.get("/translations/{locale}")
async def get_locale_translations(
    locale: str,
    db: AsyncSession = Depends(get_db)
):
    """Returns dynamic key-value dictionary for requested language (en, hi, mr, gu)."""
    service = LocalizationService(db)
    translations = await service.get_translations_for_locale(locale)
    return {
        "locale": locale,
        "count": len(translations),
        "translations": translations
    }

@router.get("/catalog")
async def get_localization_catalog(
    category: Optional[str] = None,
    db: AsyncSession = Depends(get_db)
):
    """Returns complete list of dynamic translation catalog entries."""
    service = LocalizationService(db)
    entries = await service.get_all_catalog_entries(category)
    return [
        {
            "id": e.id,
            "string_key": e.string_key,
            "locale": e.locale,
            "category": e.category,
            "translated_text": e.translated_text,
            "updated_at": e.updated_at.isoformat() if e.updated_at else None
        }
        for e in entries
    ]

@router.post("/catalog")
async def upsert_localization_entry(
    payload: Dict[str, Any],
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    """Allows admin users to dynamically update or create translation string keys."""
    service = LocalizationService(db)
    string_key = payload.get("string_key")
    locale = payload.get("locale", "en")
    category = payload.get("category", "COMMON")
    translated_text = payload.get("translated_text")

    if not string_key or not translated_text:
        raise HTTPException(status_code=400, detail="string_key and translated_text are required")

    entry = await service.upsert_translation(string_key, locale, category, translated_text)
    return {
        "message": "Translation catalog entry saved successfully",
        "id": entry.id,
        "string_key": entry.string_key,
        "locale": entry.locale,
        "translated_text": entry.translated_text
    }
