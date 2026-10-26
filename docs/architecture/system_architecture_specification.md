# System Architecture Specification
## Premium Luxury Ride-Hailing Platform — Phase 1 MVP

### 1. Monorepo Architecture Overview
The platform employs a strict modular monorepo structure separating client interfaces, business domains, persistence layers, and external adapters:

```
luxury-ride-platform/
    apps/
        customer-android/       # Kotlin & Jetpack Compose guest app
        driver-android/         # Kotlin & Jetpack Compose chauffeur app
        admin-web/              # React, TypeScript & Vite executive portal
    backend/
        api/routes/             # HTTP REST and WebSocket route definitions
        application/services/   # Authoritative business logic & use-cases
        domain/models/          # Normalized SQLAlchemy 2.0 entities
        domain/state_machine/   # Deterministic ride state machine guards
        infrastructure/         # Database sessions, repositories, crypto
        integrations/           # SMS, Maps, Razorpay/UPI, Notifications, Storage
        tests/                  # Automated unit and integration test suites
    packages/
        api-contracts/          # Shared OpenAPI 3.1.0 specifications
        shared-documentation/   # Cross-system guides and design tokens
    database/
        migrations/             # Alembic database versioning
        seeds/                  # Idempotent baseline seed data
    deployment/
        docker/                 # Multi-stage Dockerfiles & Compose manifests
        reverse-proxy/          # Hardened Nginx edge gateway
        scripts/                # Automated backup and restoration scripts
    docs/                       # Architecture, security, operations, testing
    .github/workflows/          # Continuous Integration automated test pipeline
```

### 2. Zero-Trust Architectural Invariants
1. **Frontend Isolation**: No client application possesses direct database credentials or queries PostgreSQL. All actions flow through authenticated backend endpoints.
2. **Authoritative Server Calculation**: Neither trip fares nor driver acceptance states can be submitted arbitrarily by client apps. The server calculates and stamps all financial values in integer minor units (paise/cents).
3. **Atomic Concurrency Control**:
   Driver assignment is executed via atomic conditional updates:
   ```sql
   UPDATE rides
   SET status = 'ASSIGNED', assigned_driver_id = :driver_id, assigned_at = :now
   WHERE id = :ride_id AND status = 'REQUESTED' AND assigned_driver_id IS NULL;
   ```
   If affected rows equal 0, the operation aborts with `409 Conflict`, mathematically guaranteeing that two chauffeurs can never accept the same trip.
4. **Trip State Machine Enforcements**:
   - `REQUESTED` -> `ASSIGNED` (Actor: DRIVER)
   - `ASSIGNED` -> `ARRIVED` (Actor: DRIVER)
   - `ARRIVED` -> `IN_PROGRESS` (Actor: DRIVER, verified against 6-digit constant-time OTP)
   - `IN_PROGRESS` -> `COMPLETED` (Actor: DRIVER, generates payment record)
   - Any actor attempting unauthorized transitions is rejected with `RideTransitionError`.

### 3. Real-Time Telemetry & Tracking
Real-time tracking utilizes WebSocket channels (`/api/v1/ws/rides/{id}/tracking`) authenticated with Bearer tokens and channel-level subscription authorization, backed by an HTTP polling fallback (`GET /api/v1/rides/{id}/track`).

### 4. Expansion Preparedness (Phase 2 & Phase 3)
The clean separation between application services and external adapters guarantees that Phase 2 (scheduled reservations, multi-city geo-fencing) and Phase 3 (enterprise fleet management, corporate invoicing) can be implemented by adding domain services without refactoring core tables or client contracts.
