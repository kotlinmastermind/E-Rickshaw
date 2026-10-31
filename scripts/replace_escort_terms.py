import os

replacements = {
    'apps/customer-android/src/main/java/com/luxury/chauffeur/customer/feature/enterprise/EnterpriseProductsDialog.kt': [
        ('Meet & Greet Escort', 'Meet & Greet Assist'),
        ('Armed Security Detail Escort', 'Armed Security Detail Ride')
    ],
    'apps/customer-android/src/main/java/com/luxury/chauffeur/customer/feature/location/LocationScreen.kt': [
        ('Direct non-stop escort. Add waypoints with designated dwell times.', 'Direct non-stop ride. Add stops with wait time.'),
        ('Automatically reserve return chauffeur escort', 'Automatically book return chauffeur ride'),
        ('e.g. Escort to 4th floor penthouse lobby', 'e.g. Drop at 4th floor penthouse lobby'),
        ('SELECT LUXURY ESCORT', 'SELECT LUXURY RIDE')
    ],
    'apps/customer-android/src/main/java/com/luxury/chauffeur/customer/feature/tracking/TrackingAdvancedComponents.kt': [
        ('"SHARE TRIP ESCORT"', '"SHARE LIVE TRIP"'),
        ('"Share Trip Escort"', '"Share Live Trip"')
    ],
    'apps/driver-android/src/main/java/com/luxury/chauffeur/driver/feature/dashboard/DriverDashboardScreen.kt': [
        ('"Direct Escort"', '"Direct Ride"')
    ],
    'apps/admin-web/src/components/FleetAccessibilityConsole.tsx': [
        ('luggage/chair transfer escort', 'luggage/chair transfer assistance')
    ],
    'backend/api/routes/vehicles.py': [
        ('Eco-conscious high-tech private escort', 'Eco-conscious high-tech private ride')
    ],
    'backend/application/services/analytics_bi_service.py': [
        ('"New Member Sanctuary Escort"', '"New Member Sanctuary Ride"')
    ],
    'backend/application/services/booking_advanced_service.py': [
        ('SUMMARY:Grand Chauffeur Escort:', 'SUMMARY:Grand Chauffeur Ride:')
    ],
    'backend/application/services/driver_financial_service.py': [
        ('"Airport Private Aviation Escort Streak"', '"Airport Private Aviation Ride Streak"')
    ],
    'backend/application/services/enterprise_growth_service.py': [
        ('"Indira Gandhi Intl T3 Escort Package"', '"Indira Gandhi Intl T3 Airport Package"'),
        ('"Armed Security Detail Escort"', '"Armed Security Detail Ride"')
    ],
    'backend/application/services/loyalty_feedback_service.py': [
        ('"FLIGHT ESCORT"', '"AIRPORT RIDE"')
    ]
}

for path, rep_list in replacements.items():
    if not os.path.exists(path):
        print(f'File not found: {path}')
        continue
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()
    changed = False
    for old_str, new_str in rep_list:
        if old_str in content:
            content = content.replace(old_str, new_str)
            changed = True
            print(f'Replaced in {path}: {old_str} -> {new_str}')
        else:
            print(f'NOT FOUND in {path}: {old_str}')
    if changed:
        with open(path, 'w', encoding='utf-8') as f:
            f.write(content)
print("Replacement script finished.")

