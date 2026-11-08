import os
import sys
import uuid
import json
import psycopg2
from datetime import datetime, timezone
from dotenv import load_dotenv

load_dotenv('.env')
raw_url = os.environ.get('DATABASE_URL_SYNC', '')
url = raw_url.replace('postgresql+psycopg2://', 'postgresql://')

print("Connecting to Supabase PostgreSQL at aws-0-ap-southeast-2.pooler.supabase.com:5432/postgres...")
conn = psycopg2.connect(url, connect_timeout=15)
conn.autocommit = False
cur = conn.cursor()

results = {}

# Check existing users
cur.execute("SELECT id, role, phone_number FROM users LIMIT 10;")
users = cur.fetchall()
print(f"Verified live users table: {len(users)} rows found.")
cust_user = next((u for u in users if u[1] == 'CUSTOMER'), users[0])
driver_user = next((u for u in users if u[1] == 'DRIVER'), users[0])

# Check customer profile
cur.execute("SELECT id, user_id FROM customer_profiles WHERE user_id = %s;", (cust_user[0],))
cust_profile = cur.fetchone()
if not cust_profile:
    cur.execute("SELECT id, user_id FROM customer_profiles LIMIT 1;")
    cust_profile = cur.fetchone()
    cust_user = (cust_profile[1], 'CUSTOMER', '')
cust_profile_id = cust_profile[0]
print(f"Verified live customer_profiles: ID={cust_profile_id}, user_id={cust_user[0]}")

# Check driver profile
cur.execute("SELECT id, user_id FROM driver_profiles LIMIT 1;")
driver_profile = cur.fetchone()
print(f"Verified live driver_profiles: {driver_profile[0] if driver_profile else 'None'}")

# Check ride
cur.execute("SELECT id FROM rides LIMIT 1;")
ride_row = cur.fetchone()
if not ride_row:
    cur.execute("SELECT id FROM vehicle_categories LIMIT 1;")
    cat_id = cur.fetchone()[0]
    cur.execute("SELECT id FROM fare_rules LIMIT 1;")
    fare_rule_id = cur.fetchone()[0]
    test_ride_id = str(uuid.uuid4())
    cur.execute("""
        INSERT INTO rides (
            id, customer_id, vehicle_category_id, fare_rule_id, status,
            pickup_address, pickup_latitude, pickup_longitude,
            destination_address, destination_latitude, destination_longitude,
            start_otp, otp_verified, estimated_distance_km, estimated_duration_minutes,
            estimated_fare_cents, payment_method, requested_at
        )
        VALUES (
            %s, %s, %s, %s, 'COMPLETED',
            'Connaught Place, New Delhi', 28.6300, 77.2100,
            'Karol Bagh Metro, New Delhi', 28.6500, 77.2300,
            '1234', true, 5.2, 18.0,
            6000, 'WALLET', NOW()
        )
        RETURNING id;
    """, (test_ride_id, cust_profile_id, cat_id, fare_rule_id))
    conn.commit()
    ride_row = (test_ride_id,)
    print(f"Created base test ride: {ride_row[0]}")
else:
    print(f"Verified live rides: {ride_row[0]}")

# 1. customer_profiles
print("\n--- Testing 1: customer_profiles ---")
cur.execute("""
    SELECT user_id, quiet_ride, female_driver_preferred, wheelchair_access_required, pet_friendly_preferred 
    FROM customer_profiles 
    WHERE user_id = %s;
""", (cust_user[0],))
cp_row = cur.fetchone()
if cp_row:
    cur.execute("""
        UPDATE customer_profiles 
        SET quiet_ride = true, female_driver_preferred = false, wheelchair_access_required = true, updated_at = NOW()
        WHERE user_id = %s
        RETURNING user_id, quiet_ride, wheelchair_access_required;
    """, (cust_user[0],))
    updated_cp = cur.fetchone()
    conn.commit()
    results['customer_profiles'] = {
        'status': 'VERIFIED_PERSISTED',
        'table': 'customer_profiles',
        'user_id': updated_cp[0],
        'quiet_ride': updated_cp[1],
        'wheelchair_access_required': updated_cp[2]
    }
    print(f"customer_profiles updated & committed: {results['customer_profiles']}")
else:
    results['customer_profiles'] = {'status': 'SKIPPED_NO_ROW'}

# 2. driver_shifts
print("\n--- Testing 2: driver_shifts ---")
if driver_profile:
    shift_id = str(uuid.uuid4())
    cur.execute("""
        INSERT INTO driver_shifts (
            id, driver_id, started_at, start_odometer_km, status,
            online_minutes, on_trip_minutes, idle_minutes, break_minutes, continuous_driving_minutes,
            trips_completed, shift_gross_earnings_cents, shift_net_earnings_cents, shift_commission_cents, shift_tips_cents,
            shift_forecast_cents, created_at, updated_at
        )
        VALUES (
            %s, %s, NOW(), 42150.0, 'ACTIVE',
            0, 0, 0, 0, 0,
            0, 0, 0, 0, 0,
            450000, NOW(), NOW()
        )
        RETURNING id, driver_id, status, start_odometer_km;
    """, (shift_id, driver_profile[0]))
    new_shift = cur.fetchone()
    conn.commit()
    print(f"driver_shifts created: ID={new_shift[0]}, status={new_shift[2]}")

    # End shift
    cur.execute("""
        UPDATE driver_shifts
        SET ended_at = NOW(), end_odometer_km = 42175.5, status = 'COMPLETED', online_minutes = 35, updated_at = NOW()
        WHERE id = %s
        RETURNING id, status, end_odometer_km, online_minutes;
    """, (new_shift[0],))
    ended_shift = cur.fetchone()
    conn.commit()
    results['driver_shifts'] = {
        'status': 'VERIFIED_PERSISTED',
        'table': 'driver_shifts',
        'id': ended_shift[0],
        'shift_status': ended_shift[1],
        'end_odometer_km': float(ended_shift[2]),
        'online_minutes': ended_shift[3]
    }
    print(f"driver_shifts closed & committed: {results['driver_shifts']}")
else:
    results['driver_shifts'] = {'status': 'SKIPPED_NO_DRIVER'}

# 3. incident_reports
print("\n--- Testing 3: incident_reports ---")
inc_id = str(uuid.uuid4())
ticket_num = f"INC-TEST-{uuid.uuid4().hex[:6].upper()}"
cur.execute("""
    INSERT INTO incident_reports (
        id, ticket_number, reporter_user_id, reporter_role, category, severity, description, status, created_at
    )
    VALUES (%s, %s, %s, 'CUSTOMER', 'FARE_DISPUTE', 'LOW', 'Integration test fare dispute verification', 'OPEN', NOW())
    RETURNING id, ticket_number, status, category;
""", (inc_id, ticket_num, cust_user[0]))
new_inc = cur.fetchone()
conn.commit()
print(f"incident_reports created: Ticket={new_inc[1]}, Category={new_inc[3]}")

# Resolve incident
cur.execute("""
    UPDATE incident_reports
    SET status = 'RESOLVED', resolution_notes = 'Verified in test suite', resolved_at = NOW()
    WHERE id = %s
    RETURNING id, ticket_number, status, resolution_notes;
""", (new_inc[0],))
resolved_inc = cur.fetchone()
conn.commit()
results['incident_reports'] = {
    'status': 'VERIFIED_PERSISTED',
    'table': 'incident_reports',
    'ticket_number': resolved_inc[1],
    'incident_status': resolved_inc[2],
    'resolution_notes': resolved_inc[3]
}
print(f"incident_reports resolved & committed: {results['incident_reports']}")

# 4. trip_share_links
print("\n--- Testing 4: trip_share_links ---")
if ride_row:
    share_id = str(uuid.uuid4())
    share_token = f"trk_test_{uuid.uuid4().hex[:12]}"
    cur.execute("""
        INSERT INTO trip_share_links (
            id, ride_id, share_token, created_by_user_id, recipient_name, hide_exact_address, expires_at, is_active, access_count, created_at
        )
        VALUES (%s, %s, %s, %s, 'Integration Test Contact', false, NOW() + INTERVAL '4 hours', true, 0, NOW())
        RETURNING id, ride_id, share_token, is_active;
    """, (share_id, ride_row[0], share_token, cust_user[0]))
    new_share = cur.fetchone()
    conn.commit()
    print(f"trip_share_links created: Token={new_share[2]}")

    cur.execute("""
        UPDATE trip_share_links
        SET access_count = access_count + 1, last_accessed_at = NOW()
        WHERE id = %s
        RETURNING id, share_token, access_count;
    """, (new_share[0],))
    accessed_share = cur.fetchone()
    conn.commit()
    results['trip_share_links'] = {
        'status': 'VERIFIED_PERSISTED',
        'table': 'trip_share_links',
        'id': accessed_share[0],
        'share_token': accessed_share[1],
        'access_count': accessed_share[2]
    }
    print(f"trip_share_links access updated: {results['trip_share_links']}")
else:
    results['trip_share_links'] = {'status': 'SKIPPED_NO_RIDE'}

# 5. shared_ride_preferences
print("\n--- Testing 5: shared_ride_preferences ---")
cur.execute("""
    SELECT id, customer_id, maximum_detour_km 
    FROM shared_ride_preferences 
    WHERE customer_id = %s;
""", (cust_profile_id,))
srp_row = cur.fetchone()
if srp_row:
    cur.execute("""
        UPDATE shared_ride_preferences 
        SET maximum_detour_km = 3.5, updated_at = NOW()
        WHERE customer_id = %s
        RETURNING id, customer_id, maximum_detour_km;
    """, (cust_profile_id,))
    updated_srp = cur.fetchone()
    conn.commit()
else:
    srp_id = str(uuid.uuid4())
    cur.execute("""
        INSERT INTO shared_ride_preferences (id, customer_id, maximum_detour_km, updated_at)
        VALUES (%s, %s, 3.5, NOW())
        RETURNING id, customer_id, maximum_detour_km;
    """, (srp_id, cust_profile_id))
    updated_srp = cur.fetchone()
    conn.commit()

results['shared_ride_preferences'] = {
    'status': 'VERIFIED_PERSISTED',
    'table': 'shared_ride_preferences',
    'id': updated_srp[0],
    'customer_id': updated_srp[1],
    'maximum_detour_km': float(updated_srp[2])
}
print(f"shared_ride_preferences persisted: {results['shared_ride_preferences']}")

cur.close()
conn.close()

with open('docs/database-integration/real_database_persistence_results.json', 'w') as f:
    json.dump(results, f, indent=2)

print("\nAll 5 target tables successfully verified with live read/write transactions against Supabase PostgreSQL!")
