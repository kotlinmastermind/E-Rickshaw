# API Specification Reference
## Premium Luxury Ride-Hailing Platform — REST & WebSocket API v1

All API requests accept and return `application/json` with UTC timestamps.

### Authentication Headers
```http
Authorization: Bearer <jwt_access_token>
```

---

### 1. Authentication (`/api/v1/auth`)

#### `POST /api/v1/auth/otp/request`
Request a 6-digit cryptographic authentication passcode.
- **Request Body**: `{"phone_number": "+919876543210"}`
- **Response `200 OK`**:
  ```json
  {
    "message": "Passcode dispatched successfully.",
    "phone_masked": "******3210",
    "expires_in_seconds": 300
  }
  ```
- **Error `429 Too Many Requests`**: Triggered when requested before 60s cooldown expires.

#### `POST /api/v1/auth/otp/verify`
Verify passcode and establish an active revocable session.
- **Request Body**: `{"phone_number": "+919876543210", "otp_code": "849201", "role": "customer"}`
- **Response `200 OK`**:
  ```json
  {
    "access_token": "eyJhbGciOi...",
    "token_type": "bearer",
    "user": {
      "id": "u-uuid",
      "phone_number": "+919876543210",
      "full_name": "Lord Sterling",
      "role": "customer",
      "is_verified": true
    }
  }
  ```

---

### 2. Vehicles & Fares

#### `GET /api/v1/vehicles/categories`
List available luxury fleet categories (Executive Sedan, Prestige SUV, Royal Limousine).

#### `POST /api/v1/fares/estimate`
Server-authoritative fare estimation with auditable quote token.
- **Request Body**:
  ```json
  {
    "category_id": "cat-uuid",
    "pickup_latitude": 18.9217,
    "pickup_longitude": 72.8332,
    "destination_latitude": 19.0974,
    "destination_longitude": 72.8744
  }
  ```
- **Response `200 OK`**:
  ```json
  {
    "estimate_id": "est-uuid",
    "distance_km": 24.8,
    "estimated_duration_minutes": 42.0,
    "base_fare_cents": 50000,
    "estimated_fare_cents": 160000,
    "currency": "INR",
    "expires_at": "2026-10-24T00:30:00Z"
  }
  ```

---

### 3. Ride Lifecycle (`/api/v1/rides`)

- `POST /api/v1/rides/request`: Submit ride booking with validated estimate token.
- `POST /api/v1/rides/{id}/accept`: Chauffeur accepts dispatch (Atomic concurrency protection).
- `POST /api/v1/rides/{id}/arrive`: Chauffeur marks arrival at pickup.
- `POST /api/v1/rides/{id}/start`: Chauffeur verifies guest 6-digit OTP and begins trip.
- `POST /api/v1/rides/{id}/finish`: Trip completed; triggers final fare settlement.
- `POST /api/v1/rides/{id}/cancel`: Cancellation with cancellation policy fee rules.
- `POST /api/v1/rides/{id}/rate`: Submit one-time 5-star review for chauffeur.

---

### 4. Payments (`/api/v1/payments`)

- `POST /api/v1/payments/intent`: Create UPI / Gateway payment order.
- `POST /api/v1/payments/cash-collect`: Chauffeur confirms direct cash collection.
- `POST /api/v1/payments/webhook`: Webhook listener with HMAC SHA-256 signature verification.

---

### 5. WebSockets (`/api/v1/ws`)

- `WS /api/v1/ws/rides/{id}/tracking?token=<jwt>`: Authenticated live tracking telemetry.
- `WS /api/v1/ws/driver/dispatch?token=<jwt>`: Chauffeur instant dispatch offers.
