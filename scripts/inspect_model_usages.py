import glob
import re

unmatched = [
    'AccountDeletionRequest', 'AdminActionJustification', 'CustomReportConfig',
    'CustomerMilestoneBadge', 'CustomerSurvey', 'DataExportArchive', 'DataRetentionPolicy',
    'DriverBatteryOptimizationSetting', 'DriverBlock', 'DriverFinancialSupportRequest',
    'DriverOperatingExpense', 'DriverPayoutReconciliation', 'DriverPayoutSchedule',
    'GiftCard', 'HelpArticleRating', 'HelpArticle', 'LocationRetentionSetting',
    'LoyaltyPointsLedger', 'PersonalizedOffer', 'PrivacyDataSubjectRequest',
    'RecurringRideSchedule', 'ScheduledReportDelivery', 'SensitiveRecordAccessLog',
    'ServiceIncidentAdvisory', 'ServiceRecoveryCredit', 'SupportCallbackRequest',
    'TourismPlace', 'TripTemplate', 'TrustedDevice', 'UserDependent',
    'UserPreferences', 'UserSavedPhone'
]

py_files = glob.glob("backend/**/*.py", recursive=True)

for model in unmatched:
    attrs = set()
    kwargs = set()
    for f in py_files:
        if "models.py" in f or "__init__.py" in f or "match_stubs" in f:
            continue
        with open(f, "r", encoding="utf-8") as fp:
            text = fp.read()
        
        # Attribute access: Model.attr
        for m in re.finditer(rf"\b{model}\.([A-Za-z0-9_]+)\b", text):
            attrs.add(m.group(1))
        
        # Constructor kwargs: Model(kw=..., kw2=...)
        for m in re.finditer(rf"\b{model}\((.*?)\)", text, re.DOTALL):
            args_str = m.group(1)
            for kw in re.finditer(r"([A-Za-z0-9_]+)\s*=", args_str):
                kwargs.add(kw.group(1))
    
    print(f"MODEL: {model}")
    print(f"  Accessed Attributes: {sorted(attrs)}")
    print(f"  Constructor Kwargs:   {sorted(kwargs)}")
    print()

