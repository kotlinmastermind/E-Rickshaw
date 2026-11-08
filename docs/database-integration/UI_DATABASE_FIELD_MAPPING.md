# UI → FastAPI → Supabase PostgreSQL Field Mapping Specification
**Project:** RideGo Enterprise E-Rickshaw Ride-Booking System  
**Document:** UI Database Field Mapping Master Specification  
**Version:** 1.0.0 (Production Verified)  
**Database Source of Truth:** Remote Supabase PostgreSQL (76 Public Tables)

---

## 1. Executive Overview

This document specifies the exact end-to-end data binding between every business-data field displayed, captured, or edited in the **Customer Android App**, **Driver Android App**, and **Admin Web (Mission Control)** and the underlying **Supabase PostgreSQL** database via the **FastAPI** backend service layer.

### Architecture Flow:
```
[ Android / React Client ]
           │
     HTTPS REST / JWT
           ▼
[ FastAPI Route Handlers ]
           │
   Pydantic Schema Validation & RBAC Authorization
           ▼
[ Application Business Services ]
           │
   SQLAlchemy 2.0 Async ORM / Transactions
           ▼
[ Supabase PostgreSQL 15+ (Remote) ]
```

---

## 2. Table-Specific Deep Mapping (Section 9 Target Tables)

### 2.1 Table: `customer_profiles`
* **PostgreSQL Table:** `public.customer_profiles` (40 Columns)
* **SQLAlchemy Model:** `backend.domain.models.CustomerProfile`
* **Primary Key:** `id` (VARCHAR(36) / UUID)
* **Foreign Key:** `user_id -> users.id`
* **Associated Endpoints:**
  - `GET /api/v1/customers/profile`
  - `PUT /api/v1/customers/profile`
  - `GET /api/v1/customers/preferences`
  - `PUT /api/v1/customers/preferences`
  - `GET /api/v1/customers/preferences/summary`

| UI Screen | UI Field Label | UI State Variable | API Endpoint | HTTP | Request / Response Field | FastAPI Schema | PostgreSQL Column | Data Type | Validation / Constraints | Authorization | Status |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Screen 25: Profile Settings / Guest Profile | Full Name | `fullName` / `userName` | `/api/v1/customers/profile` | PUT / GET | `full_name` | `CustomerProfileUpdate` | `users.full_name` | VARCHAR(128) | Non-empty string | Owner (Customer JWT) | CONNECTED |
| Screen 25: Profile Settings / Guest Profile | Phone Number | `phone` / `userPhone` | `/api/v1/customers/profile` | GET | `phone_number` | Response Dict | `users.phone_number` | VARCHAR(32) | Unique, E.164 format | Owner (Customer JWT) | CONNECTED |
| Screen 25: Profile Settings / Guest Profile | Email Address | `email` / `userEmail` | `/api/v1/customers/profile` | PUT / GET | `email` | `CustomerProfileUpdate` | `users.email` | VARCHAR(255) | Valid email format | Owner (Customer JWT) | CONNECTED |
| Screen 25: Profile Settings / Guest Profile | Emergency Contact Phone | `emergencyContactPhone` | `/api/v1/customers/profile` | PUT / GET | `emergency_contact_phone` | `CustomerProfileUpdate` | `customer_profiles.emergency_contact_phone` | VARCHAR(32) | Valid phone format | Owner (Customer JWT) | CONNECTED |
| Screen 25: Profile Settings / Guest Profile | Preferred Payment Method | `preferredPaymentMethod` | `/api/v1/customers/profile` | PUT / GET | `preferred_payment_method` | `CustomerProfileUpdate` | `customer_profiles.preferred_payment_method` | VARCHAR(32) | UPI, WALLET, CARD, CASH | Owner (Customer JWT) | CONNECTED |
| Screen 06 / 14: Commuter Preferences | Silent Discretion (Quiet Ride) | `quietRide` / `prefQuietRide` | `/api/v1/customers/preferences` | PUT / GET | `quiet_ride` | `CustomerPreferencesUpdate` | `customer_profiles.quiet_ride` | BOOLEAN | Default: False | Owner (Customer JWT) | CONNECTED |
| Screen 06 / 14: Commuter Preferences | Cabin Temperature | `temperatureCelsius` | `/api/v1/customers/preferences` | PUT / GET | `temperature_celsius` | `CustomerPreferencesUpdate` | `customer_profiles.temperature_celsius` | FLOAT8 | Range 18.0 - 28.0 | Owner (Customer JWT) | CONNECTED |
| Screen 06 / 14: Commuter Preferences | Audio Atmosphere / Music | `preferredMusic` | `/api/v1/customers/preferences` | PUT / GET | `preferred_music` | `CustomerPreferencesUpdate` | `customer_profiles.preferred_music` | VARCHAR(128) | String | Owner (Customer JWT) | CONNECTED |
| Screen 06 / 14: Commuter Preferences | Complimentary Beverage | `preferredWater` | `/api/v1/customers/preferences` | PUT / GET | `preferred_water` | `CustomerPreferencesUpdate` | `customer_profiles.preferred_water` | VARCHAR(128) | String | Owner (Customer JWT) | CONNECTED |
| Screen 06 / 14: Commuter Preferences | No Phone Calls On Arrival | `noPhoneCallRide` | `/api/v1/customers/preferences` | PUT / GET | `no_phone_call_ride` | `CustomerPreferencesUpdate` | `customer_profiles.no_phone_call_ride` | BOOLEAN | Default: False | Owner (Customer JWT) | CONNECTED |
| Screen 06 / 14: Commuter Preferences | Chauffeur Language Preference | `preferredDriverLanguage` | `/api/v1/customers/preferences` | PUT / GET | `preferred_driver_language` | `CustomerPreferencesUpdate` | `customer_profiles.preferred_driver_language` | VARCHAR(64) | Default: English / Hindi | Owner (Customer JWT) | CONNECTED |
| Screen 06 / 14: Commuter Preferences | Female Chauffeur Preferred | `femaleDriver` / `prefFemaleDriver` | `/api/v1/customers/preferences` | PUT / GET | `female_driver_preferred` | `CustomerPreferencesUpdate` | `customer_profiles.female_driver_preferred` | BOOLEAN | Default: False | Owner (Customer JWT) | CONNECTED |
| Screen 06 / 14: Commuter Preferences | Exterior Color Preference | `vehicleColorPreference` | `/api/v1/customers/preferences` | PUT / GET | `vehicle_color_preference` | `CustomerPreferencesUpdate` | `customer_profiles.vehicle_color_preference` | VARCHAR(64) | String | Owner (Customer JWT) | CONNECTED |
| Screen 06 / 14: Commuter Preferences | Wheelchair Accessibility Required | `wheelchair` / `prefWheelchair` | `/api/v1/customers/preferences` | PUT / GET | `wheelchair_access_required` | `CustomerPreferencesUpdate` | `customer_profiles.wheelchair_access_required` | BOOLEAN | Default: False | Owner (Customer JWT) | CONNECTED |
| Screen 06 / 14: Commuter Preferences | Step-Free Boarding Required | `stepFreePickupRequired` | `/api/v1/customers/preferences` | PUT / GET | `step_free_pickup_required` | `CustomerPreferencesUpdate` | `customer_profiles.step_free_pickup_required` | BOOLEAN | Default: False | Owner (Customer JWT) | CONNECTED |
| Screen 06 / 14: Commuter Preferences | Pet-Friendly Ride | `petFriendly` / `prefPetFriendly` | `/api/v1/customers/preferences` | PUT / GET | `pet_friendly_preferred` | `CustomerPreferencesUpdate` | `customer_profiles.pet_friendly_preferred` | BOOLEAN | Default: False | Owner (Customer JWT) | CONNECTED |
| Screen 06 / 14: Commuter Preferences | Extra Legroom Requested | `extraLegroomRequired` | `/api/v1/customers/preferences` | PUT / GET | `extra_legroom_required` | `CustomerPreferencesUpdate` | `customer_profiles.extra_legroom_required` | BOOLEAN | Default: False | Owner (Customer JWT) | CONNECTED |
| Screen 06 / 14: Commuter Preferences | Special Accessibility Notes | `accessibilityNotes` | `/api/v1/customers/preferences` | PUT / GET | `accessibility_notes` | `CustomerPreferencesUpdate` | `customer_profiles.accessibility_notes` | TEXT | Optional Text | Owner (Customer JWT) | CONNECTED |
| Screen 23: Referral Program | Personal Referral Code | `referralCode` | `/api/v1/customers/profile` | GET | `referral_code` | Response Dict | `customer_profiles.referral_code` | VARCHAR(32) | Unique | Owner (Customer JWT) | CONNECTED |
| Screen 25 / Header | VIP Tier Status | `tier` / `membershipTier` | `/api/v1/customers/profile` | GET | `tier` | Response Dict | `customer_profiles.tier` | VARCHAR(32) | STANDARD, GOLD, ROYAL_SOVEREIGN | Owner (Customer JWT) | CONNECTED |
| Screen 25 / Header | Customer Rating Average | `ratingAverage` | `/api/v1/customers/profile` | GET | `rating_average` | Response Dict | `customer_profiles.rating_average` | FLOAT8 | Range 1.0 - 5.0 | Owner (Customer JWT) | CONNECTED |

---

### 2.2 Table: `driver_shifts`
* **PostgreSQL Table:** `public.driver_shifts` (22 Columns)
* **SQLAlchemy Model:** `backend.domain.models.DriverShift`
* **Primary Key:** `id` (VARCHAR(36) / UUID)
* **Foreign Key:** `driver_id -> driver_profiles.id`, `vehicle_id -> vehicles.id`
* **Associated Endpoints:**
  - `POST /api/v1/drivers/financials/shifts/start`
  - `POST /api/v1/drivers/financials/shifts/end`
  - `GET /api/v1/drivers/financials/shifts/current`
  - `POST /api/v1/drivers/financials/shifts/break`

| UI Screen | UI Field Label | UI State Variable | API Endpoint | HTTP | Request / Response Field | FastAPI Schema | PostgreSQL Column | Data Type | Validation / Constraints | Authorization | Status |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Screen 8: Pilot Cockpit / Duty Toggle | Duty Online / Shift Start | `isOnline` = true | `/api/v1/drivers/financials/shifts/start` | POST | `start_odometer_km`, `vehicle_id` | `StartShiftPayload` | `driver_shifts.start_odometer_km`, `started_at` | FLOAT8, TIMESTAMP WITH TIME ZONE | Positive odometer, start timestamp | Driver JWT | CONNECTED |
| Screen 8: Pilot Cockpit / Duty Toggle | Duty Offline / Shift End | `isOnline` = false | `/api/v1/drivers/financials/shifts/end` | POST | `end_odometer_km` | `EndShiftPayload` | `driver_shifts.end_odometer_km`, `ended_at` | FLOAT8, TIMESTAMP WITH TIME ZONE | End km >= Start km | Driver JWT | CONNECTED |
| Screen 18: Daily Shift Telemetry | Active Shift Status | `shiftStatus` | `/api/v1/drivers/financials/shifts/current` | GET | `status` | Shift Status Dict | `driver_shifts.status` | VARCHAR(20) | ACTIVE, COMPLETED, ON_BREAK | Driver JWT | CONNECTED |
| Screen 18: Daily Shift Telemetry | Shift Driving Duration | `onlineMinutes` | `/api/v1/drivers/financials/shifts/current` | GET | `online_minutes` | Shift Status Dict | `driver_shifts.online_minutes` | INTEGER | Non-negative integer | Driver JWT | CONNECTED |
| Screen 18: Daily Shift Telemetry | Start Shift Odometer | `startOdometer` | `/api/v1/drivers/financials/shifts/current` | GET | `start_odometer_km` | Shift Status Dict | `driver_shifts.start_odometer_km` | FLOAT8 | Positive float | Driver JWT | CONNECTED |
| Screen 18: Daily Shift Telemetry | Current / End Odometer | `endOdometer` | `/api/v1/drivers/financials/shifts/current` | GET | `end_odometer_km` | Shift Status Dict | `driver_shifts.end_odometer_km` | FLOAT8 | Positive float | Driver JWT | CONNECTED |
| Screen 18: Daily Shift Telemetry | Trips Completed in Shift | `tripsCompleted` | `/api/v1/drivers/financials/shifts/current` | GET | `trips_completed` | Shift Status Dict | `driver_shifts.trips_completed` | INTEGER | Non-negative integer | Driver JWT | CONNECTED |
| Screen 18 / 19: Shift Gross Earnings | Gross Shift Revenue | `grossEarnings` | `/api/v1/drivers/financials/shifts/current` | GET | `shift_gross_earnings_cents` | Shift Status Dict | `driver_shifts.shift_gross_earnings_cents` | INTEGER | Derived from completed rides | Driver JWT | CONNECTED |
| Screen 18 / 19: Shift Net Earnings | Net Take-Home Earnings | `netEarnings` | `/api/v1/drivers/financials/shifts/current` | GET | `shift_net_earnings_cents` | Shift Status Dict | `driver_shifts.shift_net_earnings_cents` | INTEGER | Gross minus commission | Driver JWT | CONNECTED |
| Screen 18: Shift Compliance Break | Record Rest Break | `breakMinutes` | `/api/v1/drivers/financials/shifts/break` | POST | `duration_minutes` | `RecordBreakPayload` | `driver_shifts.break_minutes` | INTEGER | 5 <= duration <= 120 | Driver JWT | CONNECTED |

---

### 2.3 Table: `incident_reports`
* **PostgreSQL Table:** `public.incident_reports` (15 Columns)
* **SQLAlchemy Model:** `backend.domain.models.IncidentReport`
* **Primary Key:** `id` (VARCHAR(36) / UUID)
* **Foreign Key:** `ride_id -> rides.id`, `reporter_user_id -> users.id`
* **Associated Endpoints:**
  - `POST /api/v1/safety/incidents` (Customer / Driver)
  - `POST /api/v1/drivers/safety/incidents` (Driver)
  - `GET /api/v1/safety/incidents/{ticket_number}` (Incident Detail & Status)
  - `GET /api/v1/safety/admin/cases` (Admin Case Management)
  - `POST /api/v1/safety/admin/cases/{ticket_number}/resolve` (Admin Case Resolution)

| UI Screen | UI Field Label | UI State Variable | API Endpoint | HTTP | Request / Response Field | FastAPI Schema | PostgreSQL Column | Data Type | Validation / Constraints | Authorization | Status |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Screen 16 (Cust) / Screen 24 (Driver): Incident Report | Incident Category | `category` / `incidentType` | `/api/v1/safety/incidents` | POST | `category` | `IncidentReportPayload` | `incident_reports.category` | VARCHAR(50) | ACCIDENT, HARASSMENT, UNRULY, OTHER | Customer / Driver JWT | CONNECTED |
| Screen 16 / 24: Incident Description | Description & Notes | `description` / `incidentNotes` | `/api/v1/safety/incidents` | POST | `description` | `IncidentReportPayload` | `incident_reports.description` | TEXT | Min 10 characters | Customer / Driver JWT | CONNECTED |
| Screen 16 / 24: Incident Severity | Severity Level | `severity` | `/api/v1/safety/incidents` | POST | `severity` | `IncidentReportPayload` | `incident_reports.severity` | VARCHAR(20) | LOW, MEDIUM, HIGH, CRITICAL | Customer / Driver JWT | CONNECTED |
| Screen 16 / 24: Photo Evidence | Evidence Photo URL | `photoUrl` | `/api/v1/safety/incidents` | POST | `photo_url` | `IncidentReportPayload` | `incident_reports.photo_url` | VARCHAR(512) | Valid URL / storage path | Customer / Driver JWT | CONNECTED |
| Screen 16 / 24: Associated Ride | Ride ID | `rideId` / `activeRideId` | `/api/v1/safety/incidents` | POST | `ride_id` | `IncidentReportPayload` | `incident_reports.ride_id` | VARCHAR(36) | Valid FK to rides.id | Participant Check | CONNECTED |
| Safety Desk / Incident Status | Incident Ticket Number | `ticketNumber` | `/api/v1/safety/incidents/{ticket}` | GET | `ticket_number` | Response Dict | `incident_reports.ticket_number` | VARCHAR(32) | Unique format INC-YYYYMMDD-XXXX | Owner / Admin | CONNECTED |
| Safety Desk / Admin Case Resolution | Resolution Status | `resolutionStatus` | `/api/v1/safety/admin/cases/{t}/resolve` | POST | `status` | Case Resolve Payload | `incident_reports.status` | VARCHAR(30) | RESOLVED, DISMISSED, ACTION_TAKEN | Superadmin Only | CONNECTED |
| Safety Desk / Admin Case Resolution | Resolution Notes & Follow-up | `resolutionNotes` | `/api/v1/safety/admin/cases/{t}/resolve` | POST | `resolution_notes`, `follow_up_action` | Case Resolve Payload | `incident_reports.resolution_notes`, `follow_up_action` | TEXT | Required on resolution | Superadmin Only | CONNECTED |

---

### 2.4 Table: `trip_share_links`
* **PostgreSQL Table:** `public.trip_share_links` (12 Columns)
* **SQLAlchemy Model:** `backend.domain.models.TripShareLink`
* **Primary Key:** `id` (VARCHAR(36) / UUID)
* **Foreign Key:** `ride_id -> rides.id`, `created_by_user_id -> users.id`
* **Associated Endpoints:**
  - `POST /api/v1/rides/{ride_id}/share`
  - `GET /api/v1/rides/share/{token}`

| UI Screen | UI Field Label | UI State Variable | API Endpoint | HTTP | Request / Response Field | FastAPI Schema | PostgreSQL Column | Data Type | Validation / Constraints | Authorization | Status |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Screen 19: Trip Share (Cust) | Recipient Contact Name | `recipientName` | `/api/v1/rides/{ride_id}/share` | POST | `recipient_name` | `TripSharePayload` | `trip_share_links.recipient_name` | VARCHAR(128) | Optional string | Ride Customer JWT | CONNECTED |
| Screen 19: Trip Share (Cust) | Recipient Phone Number | `recipientPhone` | `/api/v1/rides/{ride_id}/share` | POST | `recipient_phone` | `TripSharePayload` | `trip_share_links.recipient_phone` | VARCHAR(32) | Optional phone string | Ride Customer JWT | CONNECTED |
| Screen 19: Trip Share (Cust) | Mask Exact Home Address | `hideExactAddress` | `/api/v1/rides/{ride_id}/share` | POST | `hide_exact_address` | `TripSharePayload` | `trip_share_links.hide_exact_address` | BOOLEAN | Default: False | Ride Customer JWT | CONNECTED |
| Screen 19: Trip Share (Cust) | Generated Secure Share Token | `shareToken` / `shareUrl` | `/api/v1/rides/{ride_id}/share` | POST | `share_token` | Response Dict | `trip_share_links.share_token` | VARCHAR(64) | 64-char crypto hex | Ride Customer JWT | CONNECTED |
| Public Web / Shared View | Shared Live Telemetry View | `sharedRideState` | `/api/v1/rides/share/{token}` | GET | Live Ride DTO | Response Dict | `rides.*`, `trip_share_links.*` | JSON | Valid token & expires_at check | Public (Token-Authorized) | CONNECTED |
| Public Web / Shared View | Access Counter | `accessCount` | `/api/v1/rides/share/{token}` | GET | (Server-side increment) | Internal increment | `trip_share_links.access_count` | INTEGER | Incremented on each view | Public (Token-Authorized) | CONNECTED |

---

### 2.5 Table: `shared_ride_preferences`
* **PostgreSQL Table:** `public.shared_ride_preferences` (4 Columns)
* **SQLAlchemy Model:** `backend.domain.models.SharedRidePreference`
* **Primary Key:** `id` (VARCHAR(36) / UUID)
* **Foreign Key:** `customer_id -> customer_profiles.id`
* **Associated Endpoints:**
  - `GET /api/v1/pools/preferences/detour`
  - `PUT /api/v1/pools/preferences/detour`

| UI Screen | UI Field Label | UI State Variable | API Endpoint | HTTP | Request / Response Field | FastAPI Schema | PostgreSQL Column | Data Type | Validation / Constraints | Authorization | Status |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Screen 11: EcoPool / Shared Preferences | Max Allowable Detour Distance | `maxDetourKm` | `/api/v1/pools/preferences/detour` | GET | `maximum_detour_km` | Response Dict | `shared_ride_preferences.maximum_detour_km` | FLOAT8 | Default: 1.5 km | Customer JWT | CONNECTED |
| Screen 11: EcoPool / Shared Preferences | Update Max Detour | `maxDetourKm` | `/api/v1/pools/preferences/detour` | PUT | `maximum_detour_km` | `SharedRidePreferenceRequest` | `shared_ride_preferences.maximum_detour_km` | FLOAT8 | Range 0.5 - 5.0 km | Customer JWT | CONNECTED |

---

## 3. Core Operational UI Mapping (Customer, Driver & Admin)

### 3.1 Customer Authentication & Registration
| UI Screen | UI Field Label | UI State Variable | API Endpoint | HTTP | Schema / Parameters | PostgreSQL Table & Column | Status |
|---|---|---|---|---|---|---|---|
| Screen 02: Password Sign In | Mobile Number / Email | `userPhone` / `identifier` | `/api/v1/auth/login/unified` | POST | `UnifiedLoginRequestPayload` | `users.phone_number` / `users.email` | CONNECTED |
| Screen 02: Password Sign In | Password | `password` | `/api/v1/auth/login/unified` | POST | `UnifiedLoginRequestPayload` | `users.password_hash` (bcrypt verified) | CONNECTED |
| Screen 03: Phone SMS OTP | 6-Digit SMS OTP Code | `otpCode` | `/api/v1/auth/otp/verify` | POST | `OtpVerifyPayload` | Server OTP Cache / Verification | CONNECTED |
| Screen 04: Forgot Password | SMS Reset Verification | `otpCode` & `newPassword` | `/api/v1/auth/forgot-password/reset` | POST | `ResetPasswordPayload` | `users.password_hash` (updated) | CONNECTED |
| Screen 05: Sign Up Identity | Full Legal Name | `userName` | `/api/v1/auth/signup/customer` | POST | `CustomerSignupRequestPayload` | `users.full_name` | CONNECTED |
| Screen 05: Sign Up Identity | Phone Number | `userPhone` | `/api/v1/auth/signup/customer` | POST | `CustomerSignupRequestPayload` | `users.phone_number` | CONNECTED |
| Screen 05: Sign Up Identity | Email Address | `userEmail` | `/api/v1/auth/signup/customer` | POST | `CustomerSignupRequestPayload` | `users.email` | CONNECTED |

### 3.2 Booking, Tracking & Rating
| UI Screen | UI Field Label | UI State Variable | API Endpoint | HTTP | Schema / Parameters | PostgreSQL Table & Column | Status |
|---|---|---|---|---|---|---|---|
| Screen 10: Vehicle Category Select | Category Cards | `categories` | `/api/v1/vehicles/categories` | GET | `List[VehicleCategory]` | `vehicle_categories.*` | CONNECTED |
| Screen 10: Vehicle Category Select | Fare Estimate | `selectedFareRupees` | `/api/v1/fares/estimate` | POST | `FareEstimateRequestPayload` | `fare_estimates.*`, `fare_rules.*` | CONNECTED |
| Screen 10: Confirm Booking | Request Chauffeur Ride | `activeRideId` | `/api/v1/rides/request` | POST | `RideRequestPayload` | `rides.*` (creates live ride record) | CONNECTED |
| Screen 15: Active Ride Live | Live Ride Telemetry | `rideStatus`, `liveDriverLatLng` | `/api/v1/rides/{ride_id}` | GET | `RideResponse` | `rides.status`, `driver_locations.*` | CONNECTED |
| Screen 21: Rate Chauffeur | 1-5 Star Rating & Feedback | `rating`, `feedback` | `/api/v1/rides/{ride_id}/rate` | POST | `RateDriverRequest` | `driver_ratings.*`, `driver_profiles.rating_average` | CONNECTED |
| Screen 22: In-App Wallet | Wallet Balance & Topup | `walletBalanceRupees` | `/api/v1/wallets` & `/api/v1/wallets/topup` | GET / POST | `TopupWalletRequest` | `wallets.balance_cents`, `wallet_transactions.*` | CONNECTED |

### 3.3 Driver Cockpit & Operational Telemetry
| UI Screen | UI Field Label | UI State Variable | API Endpoint | HTTP | Schema / Parameters | PostgreSQL Table & Column | Status |
|---|---|---|---|---|---|---|---|
| Screen 1: Pilot Login | Mobile / Password | `phone`, `password` | `/api/v1/auth/login/unified` | POST | `UnifiedLoginRequestPayload` | `users.*`, `driver_profiles.*` | CONNECTED |
| Screen 8: Cockpit Duty Switch | Online Availability | `isDutyOnline` | `/api/v1/drivers/availability` | POST | `DriverAvailabilityPayload` | `driver_availability.is_online` | CONNECTED |
| Screen 8: Cockpit GPS Stream | Telemetry Location Ping | `latitude`, `longitude`, `speed` | `/api/v1/drivers/location` | POST | `DriverLocationPayload` | `driver_locations.*` | CONNECTED |
| Screen 10: Dispatch Offer | Accept Ride Booking | `onAcceptRide(rideId)` | `/api/v1/rides/{id}/accept` | POST | `RideActionPayload` | `rides.status = 'ASSIGNED'`, `rides.assigned_driver_id` | CONNECTED |
| Screen 12: Arrived At Station | Mark Chauffeur Arrived | `onArrived(rideId)` | `/api/v1/rides/{id}/arrived` | POST | `RideActionPayload` | `rides.status = 'ARRIVED'` | CONNECTED |
| Screen 13: Commuter OTP Verify | Start Trip OTP Validation | `startOtp` | `/api/v1/rides/{id}/start` | POST | `RideStartPayload` | `rides.status = 'IN_PROGRESS'`, verifies `rides.start_otp` | CONNECTED |
| Screen 17: Trip Complete | Cash / Online Finalization | `onComplete(rideId)` | `/api/v1/rides/{id}/complete` | POST | `RideActionPayload` | `rides.status = 'COMPLETED'`, `rides.completed_at` | CONNECTED |
| Screen 19: Earnings Ledger | Trip Ledger & Net Balance | `ledgerEntries` | `/api/v1/drivers/financials/dashboard` | GET | `EarningsDashboardResponse` | `driver_ledger_entries.*`, `driver_profiles.wallet_balance_cents` | CONNECTED |
| Screen 22: Instant Payout | Bank / UPI Payout Request | `amountRupees` | `/api/v1/drivers/financials/payouts` | POST | `PayoutRequestPayload` | `driver_payout_requests.*` | CONNECTED |
| Screen 23: Pilot Fatigue Check | Compliance Check-In | `fatigueResult`, `notes` | `/api/v1/drivers/safety/fatigue-check` | POST | `FatigueCheckPayload` | `driver_fatigue_checks.*` | CONNECTED |

### 3.4 Admin Web Mission Control Desks
| Admin Desk | Data Rendered / Action | API Endpoint | HTTP Method | Supabase PostgreSQL Table | Status |
|---|---|---|---|---|---|
| Desk 1: Mission Control Dashboard | Total Rides, Gross Booking, Online Drivers | `/api/v1/admin/dashboard/stats` | GET | `rides`, `users`, `driver_availability` | CONNECTED |
| Desk 2: Live GPS Telemetry Map | Active Vehicles on Real Mapbox Canvas | `/api/v1/vehicles/nearby`, `/api/v1/location/active-fleet` | GET | `driver_locations`, `driver_availability`, `vehicles` | CONNECTED |
| Desk 5: Customer Directory & KYC | Real Customer Profiles, Tiers, Balances | `/api/v1/admin/users?role=customer` | GET | `users`, `customer_profiles`, `wallets` | CONNECTED |
| Desk 7: Driver Verification & Fleet | Real Driver Documents, Approvals, Status | `/api/v1/admin/drivers` | GET | `driver_profiles`, `users`, `driver_documents` | CONNECTED |
| Desk 8: Driver Payout Console | Pending Payout Requests, Approval & Rejection | `/api/v1/admin/payouts` | GET / POST | `driver_payout_requests`, `driver_profiles` | CONNECTED |
| Desk 9: Fare Matrix & Tier Config | Live Base Fare, Per Km, Tax Rules | `/api/v1/fares/rules`, `/api/v1/admin/fares` | GET / POST | `fare_rules`, `vehicle_categories` | CONNECTED |
| Desk 10: Surge Pricing Engine | Multiplier Rules, Zone Dynamic Pricing | `/api/v1/admin/surge-rules` | GET / POST | `surge_rules`, `geofence_zones` | CONNECTED |
| Desk 11: Broadcast & Passenger Alerts | Scheduled Push Announcements | `/api/v1/admin/broadcasts` | GET / POST | `broadcast_notifications`, `broadcast_deliveries` | CONNECTED |
| Desk 12: Safety & Emergency SOS | Active Incidents, Panic Alerts, Case Resolution | `/api/v1/safety/admin/cases` | GET / POST | `incident_reports`, `emergency_alerts` | CONNECTED |
| Desk 13: System Audit Logs | Immutable Audit Events & Compliance Logs | `/api/v1/admin/audit-logs` | GET | `audit_logs` | CONNECTED |
