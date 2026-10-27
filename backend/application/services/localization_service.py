import logging
from typing import Dict, List, Any, Optional
from sqlalchemy import select, update, delete
from sqlalchemy.ext.asyncio import AsyncSession
from backend.domain.models.models import LocalizationCatalog

logger = logging.getLogger("backend.localization")

DEFAULT_TRANSLATIONS = {
    "en": {
        # Customer UI
        "guest_profile": "GUEST PROFILE",
        "submit_review": "SUBMIT REVIEW",
        "bespoke_sanctuary": "BESPOKE SANCTUARY",
        "system_resilience": "SYSTEM RESILIENCE & FEATURE FLAGS",
        "enterprise_products": "ENTERPRISE & B2B PRODUCTS",
        "royal_loyalty": "ROYAL LOYALTY SANCTUARY",
        "referral_sanctuary": "REFERRAL SANCTUARY",
        "exclusive_offers": "EXCLUSIVE OFFERS & PERKS",
        "journey_journals": "JOURNEY JOURNALS & STATEMENTS",
        "executive_review": "CHAUFFEUR SERVICE REVIEW",
        "overall_rating": "OVERALL CHAUFFEUR RATING",
        "detailed_scores": "DETAILED SERVICE SCORES",
        "select_compliments": "SELECT COMPLIMENTS & TAGS",
        "public_comment": "Public Review Note",
        "private_feedback": "Private Feedback (Concierge Fleet Ops Only)",
        "chauffeur_gratuity": "CHAUFFEUR GRATUITY (TIP)",
        "satisfaction_survey": "30-SECOND SATISFACTION SURVEY",
        "tier_privileges": "YOUR TIER PRIVILEGES",
        "available_points": "AVAILABLE POINTS",

        # Driver UI
        "chauffeur_credentials": "CHAUFFEUR CREDENTIALS",
        "back_to_cockpit": "BACK TO COCKPIT",
        "resilience_feature_flags": "RESILIENCE & FEATURE FLAGS",
        "intercity_specialty_addons": "INTERCITY & SPECIALTY ADD-ONS",
        "specialty_readiness": "Confirm Specialty Readiness",
        "online_duty_toggle": "Duty Cockpit Online / Offline",
        "inspection_checklist": "20-Point Daily Inspection Checklist",

        # Admin UI
        "admin_enterprise_suite": "Enterprise, Partnerships & Growth Suite",
        "admin_corporate_accounts": "Corporate Accounts",
        "admin_hotel_partners": "Hotel & Venue Partners",
        "admin_subscriptions": "Subscriptions & Prepaid Bundles",
        "admin_multicity_settings": "Multi-City Operating Settings & Tax",
        "admin_ab_experiments": "A/B Testing Experiments",
        "admin_feature_voting": "Customer Feature Request Voting",
        "admin_feedback_analytics": "Product Feedback Analytics"
    },
    "hi": {
        # Customer UI
        "guest_profile": "अतिथि प्रोफ़ाइल",
        "submit_review": "समीक्षा जमा करें",
        "bespoke_sanctuary": "राजसी सेवा कस्टमाइज़ेशन",
        "system_resilience": "सिस्टम लचीलापन और फ़ीचर फ़्लैग",
        "enterprise_products": "कॉरपोरेट और बी2बी उत्पाद",
        "royal_loyalty": "रॉयल लॉयल्टी अभयारण्य",
        "referral_sanctuary": "रेफ़रल अभयारण्य",
        "exclusive_offers": "विशेष ऑफ़र और लाभ",
        "journey_journals": "यात्रा विवरण और विवरण",
        "executive_review": "चालक सेवा समीक्षा",
        "overall_rating": "समग्र चालक रेटिंग",
        "detailed_scores": "विस्तृत सेवा अंक",
        "select_compliments": "प्रशंसा और टैग चुनें",
        "public_comment": "सार्वजनिक समीक्षा टिप्पणी",
        "private_feedback": "निजी फ़ीडबैक (प्रबंधन केवल)",
        "chauffeur_gratuity": "चालक टिप (ग्रेच्युटी)",
        "satisfaction_survey": "30-सेकंड संतुष्टि सर्वेक्षण",
        "tier_privileges": "आपके स्तर के विशेषाधिकार",
        "available_points": "उपलब्ध अंक",

        # Driver UI
        "chauffeur_credentials": "चालक क्रेडेंशियल",
        "back_to_cockpit": "कॉकपिट पर वापस जाएं",
        "resilience_feature_flags": "लचीलापन और फ़ीचर फ़्लैग",
        "intercity_specialty_addons": "इंटरसिटी और विशेष ऐड-ऑन",
        "specialty_readiness": "विशेष तत्परता की पुष्टि करें",
        "online_duty_toggle": "ड्यूटी ऑनलाइन / ऑफ़लाइन",
        "inspection_checklist": "20-बिंदु दैनिक निरीक्षण सूची",

        # Admin UI
        "admin_enterprise_suite": "एंटरप्राइज़, साझेदारी और विकास सुइट",
        "admin_corporate_accounts": "कॉरपोरेट खाते",
        "admin_hotel_partners": "होटल और स्थल साझेदार",
        "admin_subscriptions": "सदस्यता और प्रीपेड बंडल",
        "admin_multicity_settings": "बहु-शहर संचालन सेटिंग्स",
        "admin_ab_experiments": "ए/बी परीक्षण प्रयोग",
        "admin_feature_voting": "ग्राहक सुविधा अनुरोध वोटिंग",
        "admin_feedback_analytics": "उत्पाद फ़ीडबैक विश्लेषण"
    },
    "mr": {
        # Customer UI
        "guest_profile": "पाहुणे प्रोफाइल",
        "submit_review": "पुनरावलोकन सादर करा",
        "bespoke_sanctuary": "रॉयल सेवा कस्टमायझेशन",
        "system_resilience": "सिस्टम लवचिकता आणि वैशिष्ट्य ध्वज",
        "enterprise_products": "कॉर्पोरेट आणि बी2बी उत्पादने",
        "royal_loyalty": "रॉयल रॉयल्टी अभयारण्य",
        "referral_sanctuary": "संदर्भ अभयारण्य",
        "exclusive_offers": "विशेष ऑफर आणि फायदे",
        "journey_journals": "प्रवास नोंदी आणि विवरणे",
        "executive_review": "चालक सेवा पुनरावलोकन",
        "overall_rating": "एकूण चालक रेटिंग",
        "detailed_scores": "तपशीलवार सेवा गुण",
        "select_compliments": "कौतुक आणि टॅग निवडा",

        # Driver UI
        "chauffeur_credentials": "चालक प्रमाणपत्रे",
        "back_to_cockpit": "कॉकपिटवर परत जा",
        "resilience_feature_flags": "लवचिकता आणि वैशिष्ट्य ध्वज",
        "intercity_specialty_addons": "इंटरसिटी आणि विशेष ॲड-ऑन्स",
        "specialty_readiness": "विशेष तयारीची पुष्टी करा",

        # Admin UI
        "admin_enterprise_suite": "एंटरप्राइझ आणि ग्रोथ सुट",
        "admin_corporate_accounts": "कॉर्पोरेट खाती"
    },
    "gu": {
        # Customer UI
        "guest_profile": "મહેમાન પ્રોફાઇલ",
        "submit_review": "સમીક્ષા સબમિટ કરો",
        "bespoke_sanctuary": "રોયલ સેવા કસ્ટમાઇઝેશન",
        "system_resilience": "સિસ્ટમ સ્થિતિસ્થાપકતા અને સુવિધા ફ્લેગ્સ",
        "enterprise_products": "કોર્પોરેટ અને B2B ઉત્પાદનો",

        # Driver UI
        "chauffeur_credentials": "ડ્રાઇવર ઓળખપત્રો",
        "back_to_cockpit": "કોકપિટ પર પાછા જાઓ",

        # Admin UI
        "admin_enterprise_suite": "એન્ટરપ્રાઇઝ અને ગ્રોથ સ્યુટ"
    }
}

class LocalizationService:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def get_translations_for_locale(self, locale: str = "en") -> Dict[str, str]:
        stmt = select(LocalizationCatalog).where(LocalizationCatalog.locale == locale)
        res = await self.db.execute(stmt)
        entries = list(res.scalars().all())

        if not entries:
            # Seed default translations if catalog is empty
            await self._seed_defaults()
            stmt = select(LocalizationCatalog).where(LocalizationCatalog.locale == locale)
            res = await self.db.execute(stmt)
            entries = list(res.scalars().all())

        result_map = {}
        # Fill defaults first for fallback
        if locale in DEFAULT_TRANSLATIONS:
            result_map.update(DEFAULT_TRANSLATIONS["en"])
            result_map.update(DEFAULT_TRANSLATIONS[locale])

        for entry in entries:
            result_map[entry.string_key] = entry.translated_text

        return result_map

    async def get_all_catalog_entries(self, category: Optional[str] = None) -> List[LocalizationCatalog]:
        stmt = select(LocalizationCatalog)
        if category:
            stmt = stmt.where(LocalizationCatalog.category == category)
        res = await self.db.execute(stmt)
        entries = list(res.scalars().all())
        if not entries:
            await self._seed_defaults()
            return await self.get_all_catalog_entries(category)
        return entries

    async def upsert_translation(self, string_key: str, locale: str, category: str, translated_text: str) -> LocalizationCatalog:
        stmt = select(LocalizationCatalog).where(
            LocalizationCatalog.string_key == string_key,
            LocalizationCatalog.locale == locale
        )
        res = await self.db.execute(stmt)
        entry = res.scalars().first()

        if entry:
            entry.translated_text = translated_text
            entry.category = category
        else:
            entry = LocalizationCatalog(
                string_key=string_key,
                locale=locale,
                category=category,
                translated_text=translated_text
            )
            self.db.add(entry)

        await self.db.commit()
        return entry

    async def _seed_defaults(self):
        for loc, keymap in DEFAULT_TRANSLATIONS.items():
            for key, val in keymap.items():
                category = "ADMIN_UI" if key.startswith("admin_") else ("DRIVER_UI" if key in ["chauffeur_credentials", "back_to_cockpit", "resilience_feature_flags", "intercity_specialty_addons", "specialty_readiness", "online_duty_toggle", "inspection_checklist"] else "CUSTOMER_UI")
                stmt = select(LocalizationCatalog).where(
                    LocalizationCatalog.string_key == key,
                    LocalizationCatalog.locale == loc
                )
                res = await self.db.execute(stmt)
                if not res.scalars().first():
                    item = LocalizationCatalog(
                        string_key=key,
                        locale=loc,
                        category=category,
                        translated_text=val
                    )
                    self.db.add(item)
        await self.db.commit()
