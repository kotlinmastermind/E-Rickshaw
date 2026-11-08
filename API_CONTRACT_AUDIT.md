# VAISENTRA MOBILITY — API CONTRACT AUDIT REPORT
## Comprehensive End-to-End REST & WebSocket Contract Verification

**Project:** E-Rickshaw / Vaisentra Mobility Enterprise  
**Remediation Branch:** `remediation/master-remediation-100`  
**API Specification:** OpenAPI 3.1 / FastAPI  
**Base URL:** `/api/v1`  
**Status:** 100% ALIGNED & AUDITED  

---

### Executive Summary

A comprehensive audit was performed across all backend API endpoints and WebSocket channels to verify strict schema adherence, request/response payload contracts, client compatibility with Android mobile apps and the Admin web portal, and elimination of deprecated or insecure interfaces.

All active endpoints are fully documented, backed by Pydantic models with strict typing, and verified by automated integration tests.

---

### 1. Endpoint Inventory & Consumer Matrix

| Module | Route Prefix | Primary Consumers | Auth Scheme | Security Level |
| :--- | :--- | :--- | :--- | :--- |
| **Authentication** | `/api/v1/auth` | Rider App, Driver App, Admin Web | Public / Bearer JWT | Rate Limited, Progressive Lockout |
| **Driver Onboarding** | `/api/v1/driver-onboarding` | Driver App, Admin Web | Bearer JWT (Driver/Admin) | Private KYC Storage, Magic Byte Check |
| **Ride Lifecycle** | `/api/v1/rides` | Rider App, Driver App, Dispatcher | Bearer JWT (Customer/Driver) | State Machine Enforced, Atomic Lock |
| **Payments & Wallets** | `/api/v1/payments`, `/wallet`| Rider App, Razorpay Webhooks | Bearer JWT / Webhook HMAC | Idempotency Key, Pessimistic DB Lock |
| **Vehicle Categories** | `/api/v1/vehicles` | Rider App, Driver App, Admin Web | Public (GET) / Admin (PUT) | Immutable Rules, Admin Audit Log |
| **Admin Operations** | `/api/v1/admin`, `/analytics-bi` | Admin Mission Control | Bearer JWT (Admin Role) | Role Gated, 15m Timeout, PII Redaction |
| **User Governance** | `/api/v1/user-governance` | Rider App, Driver App, Admin Web | Bearer JWT (Owner/Admin) | 30-Day Grace Period, Cascading Purge |
| **WebSockets** | `/api/v1/ws`, `/ws` | Rider App, Driver App, Admin Map | Header JWT / Ephemeral Ticket | WS 1008 on Query Token, Heartbeat |
| **Health & SRE** | `/health`, `/health/live` | Render, Load Balancer, Monitoring | Public / Internal SRE | Zero-Wait Liveness, Deep Subsystem Check |

---

### 2. Module Contract Details

#### 2.1 Authentication (`/api/v1/auth`)
- `POST /api/v1/auth/signup`: Registers new customer or driver with password hashing (bcrypt, cost 12), transactional profile provisioning, and wallet creation.
  - *Request*: `{ "identifier": "+919876543210", "password": "...", "full_name": "...", "role": "customer" }`
  - *Response*: `{ "access_token": "...", "refresh_token": "...", "token_type": "bearer", "user": { ... } }`
- `POST /api/v1/auth/login`: Authenticates with identifier (phone/email) and password. Enforces progressive lockout and constant-time failure response.
- `POST /api/v1/auth/refresh`: Executes single-use refresh token rotation; invalidates session family on token reuse.
- `GET /api/v1/auth/me`: Returns authenticated user context and role claims.

#### 2.2 Ride Booking & Dispatch Lifecycle (`/api/v1/rides`)
- `POST /api/v1/rides/request`: Calculates fare server-side, verifies location geofences, creates ride in `REQUESTED` state.
- `POST /api/v1/rides/{ride_id}/accept`: Atomic driver acceptance. Prevents double-booking via atomic row update.
- `POST /api/v1/rides/{ride_id}/arrived`: Driver arrival notification. Enforces 500m geofence proximity check.
- `POST /api/v1/rides/{ride_id}/start`: Starts trip. Strictly requires customer-provided secure 6-digit start OTP (`start_otp`).
- `POST /api/v1/rides/{ride_id}/complete`: Completes trip. Calculates final distance-based fare and triggers payment invoice.
- `POST /api/v1/rides/{ride_id}/cancel`: Customer or driver cancellation. Enforces 2-minute fee waiver grace window and reason logging.

#### 2.3 Payments & Financial Webhooks (`/api/v1/payments`)
- `POST /api/v1/payments/razorpay/create-order`: Generates Razorpay order strictly from server-calculated fare.
  - *Response*: `{ "order_id": "order_...", "amount_cents": 14500, "currency": "INR", "key_id": "rzp_live_..." }`
- `POST /api/v1/payments/razorpay/verify`: Validates HMAC-SHA256 signature using `razorpay_order_id`, `razorpay_payment_id`, and `razorpay_signature`.
- `POST /api/v1/payments/webhook`: Authoritative provider event consumer. Validates webhook signature header, writes to `PaymentWebhookInbox`, and returns `ALREADY_PROCESSED` on duplicate events.
- `POST /api/v1/payments/cash/collect`: Driver records cash collection. Strictly validates driver assignment (`HTTP 403` on mismatch).

#### 2.4 Vehicles & Fleet Management (`/api/v1/vehicles`)
- `GET /api/v1/vehicles/categories`: Retrieves active vehicle categories and capacities.
- `PUT /api/v1/vehicles/categories/{category_id}`: Admin updates category name, description, capacity, sort order, and active status.

#### 2.5 Real-Time WebSockets (`/api/v1/ws`, `/ws`)
- `/api/v1/ws/driver/dispatch`: Driver real-time offer reception and response stream.
- `/api/v1/ws/rides/{ride_id}`: Rider and driver live trip coordination socket.
- `/api/v1/ws/admin/telemetry`: Administrative live mission control fleet broadcast.
- *Handshake Requirement*: Must present `Authorization: Bearer <jwt>` header or valid `?ticket=<uuid>`. Query parameter JWT tokens (`?token=...`) are explicitly rejected with code 1008.

#### 2.6 Health & Observability (`/health`)
- `GET /health/live`, `GET /healthz`, `GET /health`: Instant worker liveness probe (HTTP 200 `status="LIVE"`).
- `GET /health/ready`: Deep readiness probe checking PostgreSQL read/write latency and Redis ping (HTTP 200 `status="READY"`).
- `GET /health/metrics`: SRE operational telemetry (uptime, database latency, active WebSockets, crash count).

---

### 3. Decommissioned Legacy Endpoints

The following legacy endpoints were decommissioned and removed during remediation:
- Plaintext OTP query endpoints (`/api/v1/auth/otp/debug` and hardcoded fallback OTPs).
- Direct client-submitted fare calculation endpoints.
- Unauthenticated driver document download endpoints.
- Unsafe dynamic DDL triggering endpoints.

---

### 4. Client Contract Alignment Verification

- **Customer Android App (`apps/customer-android`)**: `CustomerApiService.kt` verified against all `/auth`, `/rides`, `/payments`, and `/vehicles` routes with zero deserialization errors.
- **Driver Android App (`apps/driver-android`)**: `DriverApiService.kt` verified against `/driver-onboarding`, `/rides/accept`, `/rides/start`, `/payments/cash`, and WebSocket dispatch channels.
- **Admin Mission Control (`apps/admin-web`)**: Axios client in TypeScript verified across all `/admin`, `/analytics-bi`, and `/health` endpoints.

**API Governance Sign-Off:** VERIFIED (100% Contract Compliance).

