# ADR 001: Monorepo Structure & Modular Boundaries

## Status
Accepted

## Context
Phase 1 MVP of the Premium Luxury Ride-Hailing Platform requires three cohesive client interfaces (Customer Android App, Driver Android App, Admin Web Portal) coordinated by an authoritative FastAPI backend. The business logic must guarantee strict zero-trust client boundaries, race-free ride dispatching, deterministic fare calculation, and unforgeable payments.

## Decisions

1. **Monorepo Structure**:
   - `apps/customer-android/`: Independent Android application module for luxury ride consumers.
   - `apps/driver-android/`: Independent Android application module for vetted chauffeur partners.
   - `apps/admin-web/`: React + TypeScript enterprise operations portal.
   - `backend/`: FastAPI service partitioned into `domain`, `application`, `infrastructure`, `api`, `integrations`, and `workers`.
   - `packages/api-contracts/`: Shared schemas, contracts, and type definitions.
   - `database/`: Database migrations and seeds.
   - `deployment/`: Docker, Nginx reverse proxy, and operational scripts.

2. **Security & Boundary Invariants**:
   - Direct frontend access to PostgreSQL is strictly prohibited.
   - All financial and dispatch state transitions are verified and stamped server-side.
   - Database operations use atomic transactions and conditional locking to prevent double-acceptance of rides.
   - Real-time location and tracking use authorized WebSocket channels with verified JWT subscriptions and graceful HTTP polling fallback.
   - Driver documents are isolated with private access controls and administrative review gates.

3. **Database Architecture**:
   - Normalized PostgreSQL schema with UUID primary keys.
   - Financial sums stored in integer minor units (paise/cents) to eliminate IEEE 754 floating-point inaccuracies.
   - Comprehensive audit logging (`ride_status_events`, `payment_events`, `administrative_audit_logs`).

## Consequences
- Clean separation of concerns allows Phase 2 (scheduled rides, multi-city dispatch) and Phase 3 (fleet management, enterprise billing) to expand smoothly without architectural rewrite.
