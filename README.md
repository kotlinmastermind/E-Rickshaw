# E-Rickshaw Ride-Hailing Platform 🛺

A complete, production-grade ride-hailing and dispatch system tailored for modern electric rickshaws (E-Rickshaw), featuring native Android apps for riders and drivers, a high-performance FastAPI asynchronous backend, and an operations admin console.

---

## 🏗️ Architecture Overview

The repository is organized as a multi-module workspace:

- **`backend/`**: FastAPI (Python 3.12) with asynchronous SQLAlchemy (`asyncpg`), Alembic database migrations, Redis-backed WebSocket/pub-sub for real-time ride tracking, and JWT + OTP authentication.
- **`apps/customer-android/`**: Native Android app for riders (Kotlin + Jetpack Compose, Retrofit2, Coroutines, Google Maps integration).
- **`apps/driver-android/`**: Native Android app for drivers (Kotlin + Jetpack Compose, foreground location service for real-time GPS streaming).
- **`apps/admin-web/`**: Live operations and dispatch console built with React + Vite.
- **`render.yaml`**: Infrastructure-as-Code Blueprint for one-click deployment on [Render](https://render.com).

---

## 🚀 Cloud Deployment (Render)

This platform includes a pre-configured `render.yaml` Blueprint designed for Render.

### Steps to Deploy:
1. Log into your [Render Dashboard](https://dashboard.render.com/).
2. Click **New +** > **Blueprint**.
3. Connect your GitHub repository: `https://github.com/kotlinmastermind/E-Rickshaw`.
4. Render will automatically read `render.yaml` and configure the **`e-rickshaw-api`** service.
5. In the Render Dashboard environment settings for the service, configure:
   - `DATABASE_URL`: Your PostgreSQL connection string with `postgresql+asyncpg://`
   - `DATABASE_URL_SYNC`: PostgreSQL sync string with `postgresql+psycopg2://`
   - `REDIS_URL`: Your Redis connection string (`rediss://...`)
   - `SECRET_KEY`: Auto-generated or custom strong secret key
6. Once deployed, your API will be live at:
   ```
   https://e-rickshaw-api.onrender.com
   ```

---

## 📱 Android Apps Network Configuration

Both Android apps come pre-configured to connect to the Render cloud API by default:

- **Customer App**: `apps/customer-android/src/main/java/com/luxury/chauffeur/customer/core/network/ApiClient.kt`
- **Driver App**: `apps/driver-android/src/main/java/com/luxury/chauffeur/driver/core/network/DriverApiClient.kt`

```kotlin
// Production Cloud URL (default):
const val RENDER_BASE_URL: String = "https://e-rickshaw-api.onrender.com/"

// Local testing via Android Emulator:
const val LOCAL_EMULATOR_BASE_URL: String = "http://10.0.2.2:8000/"
```

To switch between Cloud and Local testing, simply toggle `baseUrl = RENDER_BASE_URL` or `baseUrl = LOCAL_EMULATOR_BASE_URL`.

---

## 🛠️ Local Development

### Backend
```bash
# 1. Navigate to backend
cd backend

# 2. Setup virtual environment & dependencies
python -m venv .venv
source .venv/bin/activate  # On Windows: .venv\Scripts\activate
pip install -r requirements.txt

# 3. Setup environment variables
cp ../.env.example ../.env

# 4. Start local development server
uvicorn backend.main:app --reload --host 0.0.0.0 --port 8000
```

### Health Check
Verify backend health:
- `GET /health/live` -> `{"status": "alive"}`
- `GET /docs` -> Interactive Swagger UI
