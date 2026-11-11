# VaisentraMobility Admin Portal: build prompt for a NEW Emergent web project

Paste the **PROMPT** section below into a new Emergent **web** project.
Before that: Publish the RiderGo project (it hosts the shared Mobility API) and replace `<MOBILITY_API_URL>` with its deployed URL.

---
## PROMPT

Build the **VaisentraMobility Admin Portal** website (VaisentraMobility by VAISENTRA TECHNOLOGIES (OPC) PRIVATE LIMITED).
IMPORTANT: Do NOT create a backend, database or new auth system. The website is only a frontend for the existing deployed **Mobility API** at `<MOBILITY_API_URL>` (all routes start with `/api`). The RiderGo and DriverGo apps use the same API, so the data is shared. Put the URL in an env var (`REACT_APP_MOBILITY_API_URL`), never hardcode it. Do not modify the backend.

Stack: React web (responsive: desktop sidebar ≥900px, top tab row on mobile). Leaflet map with OpenStreetMap tiles + dark CSS filter for the fleet map (no key needed), or Mapbox GL JS if I provide a public token.
Store the JWT in sessionStorage. Sign out on 401, or on 403 whose detail contains "blocked".

Design: dark command center. Colors: background #121316, panels #1A1C23, raised #232630, borders #2D313D, text #F4F5F7, muted #8A91A0, brand orange #F58220, brand tint #2E2117, success #22C55E, warning #FBBF24, error #EF4444, info #3B82F6. Headings Barlow Condensed, body DM Sans. Left sidebar shows the logo text "VaisentraMobility · Admin Portal" and the company credit at the bottom.

### Pages (all must exist)
1. **Login**: email + password. `POST /api/auth/admin/login {email, password}` → `{token, user:{id, role:"admin", name, email}}`. 401 shows "Invalid email or password".
2. **Dashboard** (auto-refresh every 10 s):
   - `GET /api/admin/stats` → `{rides:{total, completed, cancelled, active, gmv_paise, today, avg_match_sec}, fleet:{total, online, on_ride, pending_approval}, riders, surge, cancellation_rate}`. Show stat cards: total rides, GMV paid (₹, from paise), active rides, drivers online (online+on_ride / total), surge x, cancellation %, riders, avg match time.
   - **Smart dispatch** card from `GET /api/admin/dispatch` → `{offers:{total_offers, accepted, declined, missed, avg_accept_ring, avg_respond_sec}, acceptance_rate, phases}`.
3. **Live Fleet**: map of `GET /api/admin/drivers` (current_lat/lng). Colour by status: online green, on_ride orange, offline grey. Show counts.
4. **Rides**: one-line horizontal filter chips (all, requested, matched, in_progress, completed, cancelled, expired) → `GET /api/admin/rides?status=&limit=50`. Table: pickup → drop, rider, driver, plate, time, status badge, fare, payment method/status.
5. **Drivers**: `GET /api/admin/drivers` → `[{user_id, name, phone, is_blocked, vehicle_type, vehicle_model, vehicle_number, status, is_approved, rating, total_rides, rides_today}]`.
   - Block/Unblock: `POST /api/admin/drivers/{id}/block {blocked}`.
   - Approve/Unapprove: `POST /api/admin/drivers/{id}/approve {blocked: <true = approved>}`.
6. **Riders**: search `GET /api/admin/users?q=`.
   - Block/Unblock: `POST /api/admin/users/{id}/block {blocked}`.
   - Wallet adjust (positive or negative paise, with a note): `POST /api/admin/wallet/adjust {user_id, amount_paise, note}`.
7. **Promos**: `GET /api/admin/promos` → `[{code, description, discount_type: percent|flat, value (percent or paise), max_discount_paise, min_fare_paise, first_ride_only, per_user_limit, max_uses_total, uses, active}]`.
   - Create: `POST /api/admin/promos {code (A-Z0-9, 3-20), description, discount_type, value, max_discount_paise?, min_fare_paise, first_ride_only, per_user_limit, max_uses_total?}`. A duplicate code returns 409.
   - Pause/Activate: `POST /api/admin/promos/{code}/active {active}`.
8. **Admins**: `GET /api/admin/admins` → `[{id, name, email, is_blocked, is_me}]`.
   - Create: `POST /api/admin/admins {name, email, password (min 10)}`. A duplicate email returns 409.
   - Enable/Disable: `POST /api/admin/admins/{id}/block {blocked}`. Disabling yourself or the last active admin returns 400.
   - Show a loading state on every action button.

General: errors are `{detail}` (string or list of `{loc, msg}`); show them in a toast. Money is always paise → format as ₹ with en-IN grouping. Every interactive element needs a `data-testid`.

### Test login
admin@vaisentramobility.in / Vaisentra@2026 (change before production).

---
## Backend note (RiderGo project)
CORS is controlled by `CORS_ORIGINS` in the RiderGo backend env (default `*`). After the Admin site is live, set it to the admin site's URL in RiderGo's Deployment → Secrets and redeploy.
