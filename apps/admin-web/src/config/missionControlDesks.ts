/**
 * Vaisentra Mobility Mission Control - Enterprise Desk Configuration
 * Standardized typed desk and category registry for Operations and Fleet Management.
 */

export interface MissionControlDesk {
  id: string;
  category: string;
  icon: string;
  title: string;
  table: string;
  badge: string;
}

export interface MissionControlCategory {
  id: string;
  label: string;
  icon: string;
}

export interface AppScreenMetadata {
  id: number;
  name: string;
  desc: string;
  table: string;
  tag: string;
}

export const MISSION_CONTROL_CATEGORIES: MissionControlCategory[] = [
  { id: 'overview', label: 'Executive Overview', icon: '📊' },
  { id: 'dispatch', label: 'Fleet & Dispatch', icon: '🗺️' },
  { id: 'customer', label: 'Customer Operations', icon: '👥' },
  { id: 'driver', label: 'Pilot & Fleet', icon: '⚡' },
  { id: 'tariffs', label: 'Tariffs & Finance', icon: '🏷️' },
  { id: 'geofence', label: 'Geofence & Zones', icon: '🌐' },
  { id: 'marketing', label: 'Growth & CRM', icon: '📢' },
  { id: 'safety', label: 'Safety & SOC', icon: '🚨' },
  { id: 'security', label: 'Security & RBAC', icon: '🔐' }
];

export const MISSION_CONTROL_DESKS: MissionControlDesk[] = [
  { id: "dashboard", category: "overview", icon: "📊", title: "1. Executive KPI Dashboard", table: "rides, payments, wallets", badge: "Live KPI" },
  { id: "gps_map", category: "dispatch", icon: "🗺️", title: "2. Live GPS Fleet Telemetry Map", table: "driver_locations, vehicles", badge: "Telemetry" },
  { id: "dispatch", category: "dispatch", icon: "🚖", title: "3. Live Ride Dispatch & Override", table: "rides, ride_assignments", badge: "Dispatch" },
  { id: "pooling", category: "dispatch", icon: "👥", title: "4. Eco-Pooling & Corridor Allocator", table: "pooled_trips, pooled_bookings", badge: "Carpool" },
  { id: "customer_dir", category: "customer", icon: "👤", title: "5. Customer Directory & KYC Control", table: "users, customer_profiles", badge: "Customers" },
  { id: "customer_wallets", category: "customer", icon: "💳", title: "6. Customer Wallets & Ledger Control", table: "wallets, wallet_transactions", badge: "Finances" },
  { id: "rides_ledger", category: "customer", icon: "📑", title: "7. Bookings & Rides Master Ledger", table: "rides, ride_stops, splits", badge: "Operations" },
  { id: "fare_disputes", category: "customer", icon: "⚖️", title: "8. Fare Disputes & Instant Arbitration", table: "fare_disputes, refunds", badge: "Arbitration" },
  { id: "lost_items", category: "customer", icon: "🎒", title: "9. Lost & Found Concierge Desk", table: "lost_item_reports", badge: "Concierge" },
  { id: "support_tickets", category: "customer", icon: "💬", title: "10. Support Tickets & Live SLA Thread", table: "support_tickets, messages", badge: "SLA Desk" },
  { id: "customer_loyalty", category: "customer", icon: "🎁", title: "11. Customer Loyalty & Streaks", table: "customer_profiles", badge: "Loyalty" },
  { id: "driver_kyc", category: "driver", icon: "📋", title: "12. Driver Applications & KYC Approvals", table: "driver_profiles, documents", badge: "Approvals" },
  { id: "document_ocr", category: "driver", icon: "🔍", title: "13. Forensic Document OCR & Sarathi Audit", table: "driver_documents", badge: "Audit" },
  { id: "driver_availability", category: "driver", icon: "⚡", title: "14. Driver Duty & Force Shift Control", table: "driver_availability, shifts", badge: "Duty Ops" },
  { id: "fatigue_monitor", category: "driver", icon: "☕", title: "15. Driver Fatigue & Rest Guard", table: "driver_fatigue_checks", badge: "Safety" },
  { id: "vehicle_fleet", category: "driver", icon: "🛺", title: "16. Vehicle Fleet Allocation & Inventory", table: "vehicles, assignments", badge: "Fleet" },
  { id: "driver_payouts", category: "driver", icon: "💵", title: "17. Driver Payout Approvals (IMPS)", table: "driver_payout_requests", badge: "Payouts" },
  { id: "driver_incentives", category: "driver", icon: "🎯", title: "18. Daily Targets & Incentive Bonuses", table: "driver_daily_targets, goals", badge: "Incentives" },
  { id: "driver_ratings", category: "driver", icon: "⭐", title: "19. Driver Performance, Ratings & Ban", table: "driver_ratings, profiles", badge: "Quality" },
  { id: "driver_tickets", category: "driver", icon: "🎫", title: "20. Driver Helpdesk & Onboarding", table: "driver_onboarding_tickets", badge: "Tickets" },
  { id: "fare_matrix", category: "tariffs", icon: "🏷️", title: "21. Dynamic Fare Matrix & Tariffs", table: "fare_rules, estimates", badge: "Tariffs" },
  { id: "geofence", category: "geofence", icon: "🌐", title: "22. Geofence Zones & Station Corridors", table: "geofence_zones", badge: "Geofences" },
  { id: "surge_rules", category: "tariffs", icon: "📈", title: "23. Surge Multiplier Demand Overrides", table: "surge_rules", badge: "Surge" },
  { id: "road_restrictions", category: "geofence", icon: "🚫", title: "24. Municipal Road Restrictions", table: "road_restrictions", badge: "Hazards" },
  { id: "payment_logs", category: "tariffs", icon: "💳", title: "25. Payment Gateway Logs & UPI Webhooks", table: "payments, saved_methods", badge: "Gateway" },
  { id: "promos", category: "marketing", icon: "🎟️", title: "26. Promo Codes & Campaign Engine", table: "promo_codes, redemptions", badge: "Marketing" },
  { id: "broadcast", category: "marketing", icon: "📢", title: "27. Mass Push Broadcast Center", table: "broadcast_notifications", badge: "Broadcast" },
  { id: "notification_templates", category: "marketing", icon: "📝", title: "28. Notification Template Designer", table: "notification_templates", badge: "Templates" },
  { id: "emergency_sos", category: "safety", icon: "🚨", title: "29. Emergency SOS & Security Desk", table: "emergency_alerts, sos_events", badge: "SOC Desk" },
  { id: "incident_reports", category: "safety", icon: "🛡️", title: "30. Safety Incident Investigations", table: "incident_reports, evidence", badge: "Legal" },
  { id: "rbac_security", category: "security", icon: "🔐", title: "31. Admin RBAC Roles & Permissions", table: "admin_roles, permissions", badge: "Security" },
  { id: "audit_logs", category: "security", icon: "📜", title: "32. Platform Audit Trails & Compliance", table: "audit_logs, route_latency", badge: "Audit" }
];

export const APP_CUSTOMER_SCREENS: AppScreenMetadata[] = [
  { id: 1, name: "1. Brand Splash & Locale", desc: "Select English, Hindi, Bengali, Tamil. Sets app locale.", table: "users, notification_templates", tag: "Auth" },
  { id: 2, name: "2. Phone & Password Sign-In", desc: "Standard phone + password login with JWT token issue.", table: "users, sessions", tag: "Auth" },
  { id: 3, name: "3. SMS OTP Quick Sign-In", desc: "6-digit OTP verification with auto-read and fallback.", table: "users, sessions, device_tokens", tag: "Auth" },
  { id: 4, name: "4. Password Recovery & Pin Reset", desc: "OTP-authenticated password reset flow.", table: "users.password_hash, audit_logs", tag: "Auth" },
  { id: 5, name: "5. Customer Registration & KYC", desc: "Full name, mobile, email, avatar and referral code.", table: "users, customer_profiles", tag: "Onboarding" },
  { id: 6, name: "6. Passkey & Biometric App Lock", desc: "Fingerprint, Face Unlock & 4-digit PIN lock setup.", table: "users.passkey_credential_id, biometric_enabled", tag: "Security" },
  { id: 7, name: "7. Map Home & Live Radar", desc: "Live GPS location with nearby active e-rickshaws radar.", table: "driver_locations, vehicle_categories, geofence_zones", tag: "Home" },
  { id: 8, name: "8. Destination Search & Places", desc: "Search destination with autocomplete, recent places.", table: "favorite_locations, saved_location_pins", tag: "Booking" },
  { id: 9, name: "9. Multi-Stop Route Builder", desc: "Add up to 3 intermediate stops with dwell-time guidance.", table: "ride_stops, rides", tag: "Booking" },
  { id: 10, name: "10. Vehicle Tier & Fare Estimate", desc: "Compare Standard, Share, Auto, Bike, Cargo loaders.", table: "vehicle_categories, fare_estimates", tag: "Booking" },
  { id: 11, name: "11. Ride Comfort & Cabin Settings", desc: "Configure AC temp, quiet ride, music, luggage assist.", table: "customer_profiles", tag: "Preferences" },
  { id: 12, name: "12. Accessibility & Green Filter", desc: "Wheelchair ramp, step-free access, guide dog, EV-only.", table: "customer_profiles", tag: "Preferences" },
  { id: 13, name: "13. Price Lock & Fare Breakdown", desc: "Locked quote with base, distance, time, and tax fees.", table: "fare_estimates", tag: "Payment" },
  { id: 14, name: "14. Promo Codes & Vouchers", desc: "Apply coupon codes for instant percentage/flat discounts.", table: "promo_codes, promo_redemptions", tag: "Discounts" },
  { id: 15, name: "15. Payment Method & UPI Picker", desc: "Select Cash, UPI (GPay/PhonePe), Saved Cards, Wallet.", table: "saved_payment_methods, payments", tag: "Payment" },
  { id: 16, name: "16. Cashless Wallet Hub", desc: "Check balance, add money, UPI instant reload.", table: "wallets, wallet_transactions, payments", tag: "Wallet" },
  { id: 17, name: "17. Wallet Auto-Topup Rules", desc: "Configure threshold trigger and auto-recharge amount.", table: "wallets", tag: "Wallet" },
  { id: 18, name: "18. Schedule Later & Transit Sync", desc: "Book advance ride with flight/train delay tracking.", table: "rides", tag: "Booking" },
  { id: 19, name: "19. Book for Family / Guest", desc: "Enter guest passenger name, phone and relationship.", table: "rides", tag: "Booking" },
  { id: 20, name: "20. Corporate & Business Profile", desc: "Cost center, project code and GST corporate tax invoice.", table: "rides", tag: "Corporate" },
  { id: 21, name: "21. Eco-Pool Shared Seat Booking", desc: "Reserve 1 or 2 seats in pooled e-rickshaw at fixed rate.", table: "pooled_trips, pooled_bookings", tag: "Carpool" },
  { id: 22, name: "22. Shared Pool Co-Passenger Chat", desc: "In-pool group announcements and stop notices.", table: "pooled_trip_messages", tag: "Carpool" },
  { id: 23, name: "23. Dispatch Radar & Pilot Matching", desc: "Finding nearest top-rated verified pilot within 1.5 km.", table: "ride_assignments, driver_availability", tag: "Dispatch" },
  { id: 24, name: "24. Pilot Assigned & ETA Tracker", desc: "Pilot name, rating, vehicle model, plate, phone badge.", table: "rides.assigned_driver_id, driver_profiles", tag: "In-Trip" },
  { id: 25, name: "25. Driver En-Route & Vehicle RC", desc: "Verify vehicle RC plate, PUC, fitness and commercial permit.", table: "vehicles", tag: "In-Trip" },
  { id: 26, name: "26. Pilot Arrived & Meeting Point", desc: "Driver arrival photo, gate landmark and pickup buzzer.", table: "rides", tag: "In-Trip" },
  { id: 27, name: "27. Boarding & 4-Digit Security OTP", desc: "Share start OTP with pilot before ride begins.", table: "rides", tag: "In-Trip" },
  { id: 28, name: "28. Live Telemetry & Speedometer", desc: "Real-time GPS route, speedometer, heading and ETA.", table: "driver_locations", tag: "In-Trip" },
  { id: 29, name: "29. Route Deviations & Unplanned Stops", desc: "Automated security alert if vehicle departs mapped route.", table: "rides", tag: "Safety" },
  { id: 30, name: "30. In-Cabin Audio Safety Recording", desc: "Encrypted emergency cabin audio recording for safety.", table: "rides", tag: "Safety" },
  { id: 31, name: "31. Live Journey Link Sharing", desc: "Share tracking link via WhatsApp/SMS with trusted contacts.", table: "trip_share_links", tag: "Safety" },
  { id: 32, name: "32. Emergency SOS & Police Dispatch", desc: "1-tap 112 emergency trigger, notifies contacts and SOC.", table: "emergency_alerts, rides", tag: "Safety" },
  { id: 33, name: "33. Emergency Contacts Manager", desc: "Add trusted guardians to notify on start and arrival.", table: "emergency_contacts", tag: "Safety" },
  { id: 34, name: "34. In-Trip Pilot Chat & Auto-Translate", desc: "Two-way instant messaging with live Hindi/English translation.", table: "ride_chats, ride_chat_messages", tag: "In-Trip" },
  { id: 35, name: "35. Split Fare with Co-Riders", desc: "Split bill equally or custom by contact number.", table: "ride_fare_splits", tag: "Payment" },
  { id: 36, name: "36. Ride Completed & Digital Receipt", desc: "Final fare itemized with waiting, distance, discount.", table: "rides, payments", tag: "Post-Trip" },
  { id: 37, name: "37. Driver Rating & Multi-Factor CSAT", desc: "Cleanliness, comfort, driving skill, service score.", table: "driver_ratings", tag: "Feedback" },
  { id: 38, name: "38. Driver Tip & Compliments", desc: "Add tip directly to pilot's wallet.", table: "rides, driver_ratings", tag: "Feedback" },
  { id: 39, name: "39. Past Trips & Ride History", desc: "Filterable ledger of past completed and cancelled trips.", table: "rides, payments", tag: "History" },
  { id: 40, name: "40. GST Tax Invoice & Download PDF", desc: "Official tax invoice with corporate billing reference.", table: "rides", tag: "Invoices" },
  { id: 41, name: "41. Fare Dispute & Overcharge Claim", desc: "Dispute route extra charges with instant refund claim.", table: "fare_disputes", tag: "Support" },
  { id: 42, name: "42. Lost Item Report & Inquiry", desc: "Report lost phones, bags or keys left in e-rickshaw.", table: "lost_item_reports", tag: "Support" },
  { id: 43, name: "43. Support Ticket Help Desk", desc: "Create tickets for ride issues, payments, safety.", table: "support_tickets", tag: "Support" },
  { id: 44, name: "44. Live Support Agent Chat Thread", desc: "Chat with operations and support staff.", table: "support_messages", tag: "Support" },
  { id: 45, name: "45. Safety Incident Formal Report", desc: "Log formal incident with photo evidence and severity.", table: "incident_reports", tag: "Safety" },
  { id: 46, name: "46. Refer & Earn Cash Program", desc: "Unique referral code, track rewarded friends & earnings.", table: "referral_records", tag: "Rewards" },
  { id: 47, name: "47. Green Club Loyalty & Streaks", desc: "Tier perks, streak bonus and rewards.", table: "customer_profiles", tag: "Rewards" },
  { id: 48, name: "48. Notification Inbox & Alerts", desc: "System notices, ride reminders, offer announcements.", table: "notifications", tag: "Alerts" },
  { id: 49, name: "49. Saved Places & Favorite Pins", desc: "Manage Home, Office, Metro Station, Gym pin shortcuts.", table: "saved_location_pins", tag: "Settings" },
  { id: 50, name: "50. Account Settings & Privacy Controls", desc: "Manage profile, active sessions, data rights, logout.", table: "users, customer_profiles", tag: "Account" }
];

export const APP_DRIVER_SCREENS: AppScreenMetadata[] = [
  { id: 1, name: "1. Pilot Cockpit Login", desc: "Driver credentials sign-in with phone + secure password.", table: "users, driver_profiles", tag: "Auth" },
  { id: 2, name: "2. Pilot SMS OTP Sign-In", desc: "Quick authentication for pilots on the road.", table: "users, sessions", tag: "Auth" },
  { id: 3, name: "3. Pilot Password Recovery", desc: "SMS-verified pilot credential reset procedure.", table: "users.password_hash", tag: "Auth" },
  { id: 4, name: "4. Pilot Profile & Academy KYC", desc: "License number, badge ID, academy level certifications.", table: "driver_profiles", tag: "Onboarding" },
  { id: 5, name: "5. Document Vault & Verification", desc: "Aadhaar, Driving License, Police Verification clearance.", table: "driver_documents", tag: "Onboarding" },
  { id: 6, name: "6. Document Expiry Watchdog", desc: "Upcoming document expiry warnings and resubmissions.", table: "driver_documents", tag: "Onboarding" },
  { id: 7, name: "7. Assigned Vehicle & Inspection", desc: "E-Rickshaw model, year, registration plate, battery status.", table: "driver_vehicle_assignments", tag: "Vehicle" },
  { id: 8, name: "8. Vehicle RC, Fitness & PUC", desc: "Permit number, PUC emission validity, insurance policy.", table: "vehicles", tag: "Vehicle" },
  { id: 9, name: "9. Shift Start & Odometer Log", desc: "Enter initial km reading and battery percentage to start.", table: "driver_shifts", tag: "Shift" },
  { id: 10, name: "10. Duty Switch & Auto-Accept Radar", desc: "Go Online / Offline toggle with auto-dispatch radius.", table: "driver_availability", tag: "Cockpit" },
  { id: 11, name: "11. High-Frequency GPS Ping", desc: "Real-time location, heading, speed & anti-spoof check.", table: "driver_locations", tag: "Telemetry" },
  { id: 12, name: "12. Driver Fatigue & Rest Check", desc: "Mandatory rest prompts after continuous driving limit.", table: "driver_fatigue_checks", tag: "Safety" },
  { id: 13, name: "13. Heatmap & Surge Zones", desc: "Live city demand map showing surge multipliers.", table: "geofence_zones, surge_rules", tag: "Demand" },
  { id: 14, name: "14. Road Restrictions & Construction", desc: "Live municipal road blocks, waterlogging, detours.", table: "road_restrictions", tag: "Navigation" },
  { id: 15, name: "15. Incoming Ride Offer", desc: "Accept/Decline offer card with pickup dist, fare, time.", table: "ride_assignments", tag: "Dispatch" },
  { id: 16, name: "16. Auto-Dispatch Offer Queue", desc: "Continuous trip matching and queued requests.", table: "rides, ride_assignments", tag: "Dispatch" },
  { id: 17, name: "17. Navigation to Pickup Spot", desc: "Turn-by-turn routing to commuter boarding point.", table: "rides", tag: "En-Route" },
  { id: 18, name: "18. Arrived at Pickup Point", desc: "Notify customer with photo proof and entrance guidance.", table: "rides", tag: "En-Route" },
  { id: 19, name: "19. Waiting Timer & Billable Meter", desc: "Grace waiting period tracker and per-minute wait fee.", table: "rides", tag: "Waiting" },
  { id: 20, name: "20. Boarding & Commuter OTP Verify", desc: "Enter customer's 4-digit code to unlock the trip.", table: "rides", tag: "Trip" },
  { id: 21, name: "21. In-Trip Turn Navigation", desc: "Live route guide with ETA, distance left, speed limit.", table: "rides", tag: "Trip" },
  { id: 22, name: "22. Multi-Stop Waypoints", desc: "Sequential stop navigation with passenger instruction notes.", table: "ride_stops", tag: "Trip" },
  { id: 23, name: "23. Route Deviation & Anomaly Alarm", desc: "Detects abnormal route detours and stationary delays.", table: "rides", tag: "Safety" },
  { id: 24, name: "24. In-Cabin Sound & Safety Status", desc: "Cabin ambient sound level check and safety check-in.", table: "rides", tag: "Safety" },
  { id: 25, name: "25. Driver Emergency SOS Panic", desc: "Instant assistance trigger with SOC agent connect.", table: "emergency_alerts", tag: "Safety" },
  { id: 26, name: "26. Passenger In-Trip Messaging", desc: "Quick-canned phrases and live chat with passenger.", table: "ride_chats", tag: "Trip" },
  { id: 27, name: "27. Shared Pool Trip Manifest", desc: "View co-passengers, seats occupied and total pool payout.", table: "pooled_trips", tag: "Carpool" },
  { id: 28, name: "28. Pooled Stops Queue", desc: "Optimized pickup and drop-off sequence for shared rides.", table: "pooled_trip_stops", tag: "Carpool" },
  { id: 29, name: "29. Trip Finished & Fare Meter", desc: "Actual distance, actual minutes, final earnings breakdown.", table: "rides", tag: "Summary" },
  { id: 30, name: "30. Cash Collection Confirmer", desc: "Collect exact cash, verify bill, mark cash collected.", table: "payments", tag: "Payment" },
  { id: 31, name: "31. Dynamic Driver UPI QR Code", desc: "Display UPI code for instant passenger scan.", table: "payments", tag: "Payment" },
  { id: 32, name: "32. Passenger Rating & Reviews", desc: "Driver rating breakdown and badge accolades.", table: "driver_ratings", tag: "Feedback" },
  { id: 33, name: "33. Tips & Extra Earnings Ledger", desc: "View customer tips credited 100% to pilot balance.", table: "rides, driver_ledger_entries", tag: "Earnings" },
  { id: 34, name: "34. Shift Summary & Metrics", desc: "Gross fares, platform cut, net earnings, trip count.", table: "driver_shifts", tag: "Shift" },
  { id: 35, name: "35. Shift End & Closing Odometer", desc: "Enter ending odometer, vehicle handover check.", table: "driver_shifts", tag: "Shift" },
  { id: 36, name: "36. Daily Targets & Incentive Bonus", desc: "Target ride completions and bonus progress.", table: "driver_daily_targets", tag: "Incentives" },
  { id: 37, name: "37. Weekly Goals & Progress", desc: "Track weekly ride goals and milestone bonuses.", table: "driver_weekly_goals", tag: "Incentives" },
  { id: 38, name: "38. Streak Missions & Badges", desc: "Consecutive peak-hour completions for tiered multipliers.", table: "driver_incentive_progress", tag: "Incentives" },
  { id: 39, name: "39. Pilot Earnings & Balance", desc: "Total wallet balance, today's pay, pending settlements.", table: "driver_profiles", tag: "Wallet" },
  { id: 40, name: "40. Payout Bank & UPI Accounts", desc: "Manage bank account, IFSC code, and primary UPI VPA.", table: "driver_payout_accounts", tag: "Banking" },
  { id: 41, name: "41. Instant IMPS/UPI Payout", desc: "Withdraw earnings to bank account instantly.", table: "driver_payout_requests", tag: "Banking" },
  { id: 42, name: "42. Driver Account Ledger", desc: "Itemized statement of all ride credits and deductions.", table: "driver_ledger_entries", tag: "Finance" },
  { id: 43, name: "43. Performance & Safety Scorecard", desc: "Lifetime rating, cancellation rate, on-time percentage.", table: "driver_profiles", tag: "Profile" },
  { id: 44, name: "44. Electric Academy Training", desc: "E-rickshaw maintenance, road safety & customer etiquette.", table: "driver_profiles", tag: "Training" },
  { id: 45, name: "45. Driver Incident Reporting", desc: "File road crash, passenger dispute or route incident.", table: "incident_reports", tag: "Safety" },
  { id: 46, name: "46. Found Lost Item in Cab", desc: "Report customer items found in the e-rickshaw rear seat.", table: "lost_item_reports", tag: "Support" },
  { id: 47, name: "47. Driver Helpdesk Tickets", desc: "Submit queries regarding vehicle maintenance, pay, KYC.", table: "driver_onboarding_tickets", tag: "Support" },
  { id: 48, name: "48. Driver Emergency Contacts", desc: "Set family contact for safety alerts and SOS escalation.", table: "driver_profiles", tag: "Safety" },
  { id: 49, name: "49. Push Notifications Center", desc: "Platform alerts, weather warnings, surge announcements.", table: "notifications", tag: "Alerts" },
  { id: 50, name: "50. Driver Preferences & Zones", desc: "Operating zone filter, vehicle class preference, language.", table: "driver_profiles", tag: "Settings" }
];

// Backward-compatibility aliases for smooth transition
export const BLUEPRINT_CATEGORIES = MISSION_CONTROL_CATEGORIES;
export const BLUEPRINT_DESKS = MISSION_CONTROL_DESKS;
export const BLUEPRINT_CUSTOMER_SCREENS = APP_CUSTOMER_SCREENS;
export const BLUEPRINT_DRIVER_SCREENS = APP_DRIVER_SCREENS;
export type DeskItem = MissionControlDesk;
export type AppScreenInfo = AppScreenMetadata;

