# DriverGo — build prompt for a NEW Emergent mobile project

Paste the section **"PROMPT"** below into a new Emergent **mobile (Expo)** project.
Before that: Publish the RiderGo project (it hosts the shared Mobility API) and replace `<MOBILITY_API_URL>` with its deployed URL.
Optional but recommended: "Save to GitHub" the RiderGo project, so the new agent can copy the driver code directly (file list at the end).

---
## PROMPT

Build **DriverGo**, the driver Android app of **VaisentraMobility** (by VAISENTRA TECHNOLOGIES (OPC) PRIVATE LIMITED).
IMPORTANT: Do NOT create a new backend or database. This app is a client of an existing, deployed backend called **Mobility API** at `<MOBILITY_API_URL>` (all routes start with `/api`). Put the URL in `frontend/.env` as `EXPO_PUBLIC_MOBILITY_API_URL`. Rider app and Admin website use the same backend, so all data is shared. Do not modify the backend.

Stack: Expo Router, TypeScript, react-native-webview (Mapbox GL JS map inside a WebView so it works in Expo Go), expo-location, expo-haptics, react-native-reanimated, secure token storage. English UI. Package name must be different from RiderGo (e.g. `com.vaisentra.drivergo`). App name "DriverGo".
Ask me for `EXPO_PUBLIC_MAPBOX_PUBLIC_TOKEN` (Mapbox public pk. token) via the secrets card.

Design: dark command-center style. Colors: surface #121316, card #1A1C23, raised #232630, border #2D313D, text #F4F5F7, muted #8A91A0, brand orange #F58220 (text on brand #121316), brand tint #2E2117, success #22C55E, warning #FBBF24, error #EF4444, info #3B82F6. Headings Barlow Condensed Bold, body DM Sans. Big thumb-friendly buttons (min 54px), 8pt spacing, radius 12/20.

### Screens (all must exist)
1. **Login**: +91 phone (regex ^[6-9]\d{9}$) → Send OTP → 6-digit OTP + optional name → Verify. If the response has `dev_otp`, show it in a yellow "Test mode — SMS gateway not connected" card. Resend timer 30 s.
2. **Driver setup** (when `needs_driver_profile` true, or `/auth/me`.driver.vehicle_number empty): vehicle type chips bike/auto/cab, model, registration (regex ^[A-Z]{2}[ -]?\d{1,2}[ -]?[A-Z]{0,3}[ -]?\d{4}$), licence (^[A-Z0-9-]{5,20}$).
3. **Tabs: Drive / Earnings / Profile.**
   - **Drive**: full-screen map with my marker, pickup/drop markers and route line. A big online/offline toggle card on top (green when online), Today ₹ and Trips stats. Going online first shows a location pre-permission sheet (Allow → request permission; if denied and can't ask again, show "Open Settings"; "Not now" goes online with last known/simulated location). While online with GPS, stream location every 5 s / 15 m over WebSocket.
     - **Ride offer modal**: arrives over WebSocket. Shows a countdown bar (ttl seconds), fare, pickup ETA, trip km, payment method, H3 ring number, pickup and drop. Accept / Decline; auto-decline at 0.
     - **Active ride panel** by status:
       - matched: "Start trip to pickup" → POST arrived
       - arriving: "I've arrived" → POST at-pickup
       - arrived: 4-digit rider PIN input + "Start ride" → POST start {otp}. A wrong PIN returns 400: show a toast, stay logged in.
       - in_progress: "Complete ride"
       - completed: if cash, "Collected ₹X cash" → collect-cash, then Done
     - Also on the panel: call rider (tel:), navigate (Google Maps dir URL), "Cancel & reassign" before the trip starts, and a "Simulate driving" checkbox when there is no GPS (moves the marker toward the target every 2 s and sends location).
   - **Earnings**: today / last 7 days / lifetime cards and a recent trips list, with pull to refresh.
   - **Profile**: name, phone, rating, vehicle card, security note, Log out, company credit footer.

### API contract (JSON; money is always in **paise**; send `Authorization: Bearer <token>`)
- Errors look like `{detail: string}` or `{detail: [{loc, msg}]}`. On **401**, or on **403 whose detail contains "blocked"**, sign out.
- `POST /api/auth/otp/send {phone}` → `{ok, expires_in, dev_otp?}`. 429 if resent within 30 s.
- `POST /api/auth/otp/verify {phone, code, role:"driver", name}` → `{token, user:{id,role,name,phone}, needs_driver_profile}`
- `GET /api/auth/me` → `{id, name, phone, rating, driver:{vehicle_type, vehicle_model, vehicle_number, status, total_rides, rating, current_lat, current_lng}}`
- `POST /api/auth/driver/profile {vehicle_type, vehicle_model, vehicle_number, license_number}`
- `POST /api/driver/status {online, lat, lng}` → `{ok, status}`
- `POST /api/driver/location {lat, lng, heading?}` (REST fallback when the socket is closed)
- `POST /api/driver/offers/respond {ride_id, accept}` → `{ok, reason?}`. Reason values: `unavailable` (another driver got it), `expired`, `offline`.
- `GET /api/driver/active-ride` → `{ride:{id,status,pickup:{lat,lng,address},dropoff,fare_paise,payment_method,payment_status,distance_km}|null, rider:{name,phone,rating}}`
- `POST /api/driver/rides/{id}/arrived` | `/at-pickup` | `/start {otp}` | `/complete` → `{ok, fare_paise, wallet_failed}` | `/collect-cash` | `/cancel {reason}`
- `GET /api/driver/earnings` → `{today:{paise,rides}, week, total, rides:[{id,fare_paise,payment_method,completed_at,dropoff}]}`
- `GET /api/driver/rides/history`
- `GET /api/geo/route?flat=&flng=&tlat=&tlng=` → `{geometry:[[lng,lat],...], eta_min, distance_km}`

### WebSocket `wss://<host>/api/ws?token=<JWT>`
- Client → server:
  - `{type:"location", lat, lng, heading}`
  - `{type:"offer_response", ride_id, accept}`
  - `{type:"ping"}` every 25 s
- Server → client:
  - `ride_offer {ride_id, rider_name, pickup, dropoff, distance_km, fare_paise, payment_method, eta_to_pickup_min, ring, ttl}`
  - `offer_result {ok, ride_id?, reason?}`
  - `ride_cancelled {ride_id}`
  - `pong`
- Reconnect with exponential backoff. Close codes **4401/4403**: stop reconnecting and sign out.

### Test accounts (shared backend)
Driver 9000000014 (auto, Indiranagar Bengaluru), 9000000001 (bike), 9000000009 (cab). To see offers, book a ride near 12.9719, 77.6229 from the RiderGo app with rider 9876543210.

---
## Files to copy from the RiderGo repo (if saved to GitHub)
`frontend/app/driver/` (all), `frontend/app/driver-setup.tsx`, `frontend/app/login.tsx`, `frontend/src/{api.ts, auth.tsx, socket.ts, theme.ts, navigation.ts, booking-store.ts}`, `frontend/src/components/{ui.tsx, toast.tsx, location.tsx, tabs.tsx, profile-view.tsx, MapboxMap.tsx, MapboxMap.web.tsx, map-html.ts}`, `frontend/assets/fonts/`. In api.ts, change the base URL to `EXPO_PUBLIC_MOBILITY_API_URL`.

## Background location (add to DriverGo)
Copy `src/background-location.ts` from the RiderGo/backend repo. Import it once in `app/_layout.tsx`, start updates when the driver goes online with live GPS (after asking "Always" location permission with an explanation sheet) and stop on offline/sign-out. Add to app.json: iOS `UIBackgroundModes: ["location"]` + `NSLocationAlwaysAndWhenInUseUsageDescription`; Android `ACCESS_BACKGROUND_LOCATION`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`; expo-location plugin `isIosBackgroundLocationEnabled`, `isAndroidBackgroundLocationEnabled`, `isAndroidForegroundServiceEnabled` = true. Works only in a native build, not Expo Go.
