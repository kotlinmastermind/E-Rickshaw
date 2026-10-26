# Production Deployment & Operations Runbook
## Premium Luxury Ride-Hailing Platform — Phase 1

### 1. Prerequisites
- Docker Engine >= 24.0.0 and Docker Compose >= 2.20.0
- Valid SSL / TLS Certificates (Let's Encrypt or enterprise wildcard)
- PostgreSQL 16
- Redis 7.x
- Configured external provider credentials (SMS: Twilio, Payments: Razorpay, Maps: Google Maps Platform)

---

### 2. Environment Variables Reference
Store production credentials in an encrypted vault (e.g. AWS Secrets Manager, HashiCorp Vault). Never commit `.env` to source control.

```env
# Database
POSTGRES_USER=luxury_admin
POSTGRES_PASSWORD=<strong_generated_password>
POSTGRES_DB=luxury_ride_prod
DATABASE_URL=postgresql+asyncpg://luxury_admin:<password>@postgres:5432/luxury_ride_prod
DATABASE_URL_SYNC=postgresql://luxury_admin:<password>@postgres:5432/luxury_ride_prod

# Core Security
SECRET_KEY=<generate_with_openssl_rand_hex_64>
ENVIRONMENT=production
DEBUG=False

# Integrations
GOOGLE_MAPS_API_KEY=<prod_maps_key>
RAZORPAY_KEY_ID=<prod_rzp_key>
RAZORPAY_KEY_SECRET=<prod_rzp_secret>
RAZORPAY_WEBHOOK_SECRET=<prod_rzp_webhook_secret>
SMS_PROVIDER=twilio
TWILIO_ACCOUNT_SID=<prod_sid>
TWILIO_AUTH_TOKEN=<prod_token>
TWILIO_FROM_NUMBER=<prod_e164_number>
```

---

### 3. Production Deployment Execution

1. **Clone repository**:
   ```bash
   git clone https://github.com/luxury-chauffeur/luxury-ride-platform.git
   cd luxury-ride-platform
   ```

2. **Execute Database Migrations**:
   ```bash
   docker compose -f deployment/docker/docker-compose.prod.yml run --rm backend alembic upgrade head
   ```

3. **Launch Production Stack**:
   ```bash
   docker compose -f deployment/docker/docker-compose.prod.yml up -d
   ```

4. **Verify Container Health**:
   ```bash
   docker compose -f deployment/docker/docker-compose.prod.yml ps
   curl -f https://api.luxurychauffeur.com/health/ready
   ```

---

### 4. Rollback and Incident Recovery Procedure
If a regression is discovered following deployment:
1. Revert container image tag to previous stable release:
   ```bash
   export RELEASE_TAG=v1.0.0-previous
   docker compose -f deployment/docker/docker-compose.prod.yml up -d backend
   ```
2. If database rollback is necessary:
   ```bash
   docker compose -f deployment/docker/docker-compose.prod.yml run --rm backend alembic downgrade -1
   ```
3. To restore full database snapshot:
   ```bash
   ./deployment/scripts/restore_db.sh /opt/luxury_backups/luxury_db_backup_pre_deploy.sql.gz
   ```

---

### 5. Android Application Release Guidance
1. Configure `keystore.properties` (never committed):
   ```properties
   storeFile=/path/to/keystore/luxury_release.jks
   storePassword=ENV_SECRET_KEYSTORE_PASS
   keyAlias=luxury_chauffeur_key
   keyPassword=ENV_SECRET_KEY_PASS
   ```
2. Build signed App Bundles:
   ```bash
   ./gradlew :apps:customer-android:bundleRelease
   ./gradlew :apps:driver-android:bundleRelease
   ```
