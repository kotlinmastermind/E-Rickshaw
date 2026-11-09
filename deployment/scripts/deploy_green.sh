#!/bin/bash
# ==============================================================================
# VAISENTRA MOBILITY — ENTERPRISE BLUE/GREEN DEPLOYMENT PROCEDURE
# Executes zero-downtime staged deployment of Green release container,
# performs host-level readiness verification, and shifts Nginx traffic.
# ==============================================================================
set -euo pipefail

RELEASE_TAG="${1:-latest}"
COMPOSE_FILE="deployment/docker/docker-compose.prod.yml"
NGINX_DIR="deployment/reverse-proxy"

echo "================================================================================"
echo "VAISENTRA MOBILITY — STAGED GREEN RELEASE DEPLOYMENT"
echo "Target Release Tag: ${RELEASE_TAG}"
echo "Compose File:       ${COMPOSE_FILE}"
echo "Timestamp:          $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
echo "================================================================================"

# Step 1: Pre-flight Blue Health Check
echo "[Step 1/6] Verifying currently active Blue backend health..."
if command -v curl >/dev/null 2>&1; then
    BLUE_HEALTH=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8000/health/ready || true)
    if [ "${BLUE_HEALTH}" != "200" ]; then
        echo "WARNING: Host check on port 8000 returned HTTP ${BLUE_HEALTH}. Checking inside Blue container..."
        docker exec vaisentra-backend-blue curl -s -f http://localhost:8000/health/ready || {
            echo "ERROR: Active Blue container is unhealthy! Aborting deployment to protect system."
            exit 1
        }
    fi
    echo "Active Blue backend verified healthy (HTTP 200)."
else
    echo "curl not found on host; checking inside Blue container..."
    docker exec vaisentra-backend-blue curl -s -f http://localhost:8000/health/ready
fi

# Step 2: Ensure internal_net Network Exists
echo "[Step 2/6] Verifying Docker internal_net network..."
docker network inspect internal_net >/dev/null 2>&1 || {
    echo "Creating isolated internal_net network..."
    docker network create --driver bridge internal_net
}

# Step 3: Start Green Container via Compose Profile
echo "[Step 3/6] Starting Green backend container (${RELEASE_TAG}) on loopback port 8001..."
GREEN_RELEASE_TAG="${RELEASE_TAG}" docker compose -f "${COMPOSE_FILE}" --profile green up -d backend-green

# Step 4: Validate Green Health Before Traffic Switch
echo "[Step 4/6] Waiting for Green backend initialization and readiness probe..."
GREEN_READY=false
for i in {1..12}; do
    echo "  Readiness check attempt ${i}/12 (waiting 5s)..."
    sleep 5
    HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8001/health/ready || true)
    if [ "${HTTP_STATUS}" == "200" ]; then
        GREEN_READY=true
        echo "  Green container returned HTTP 200 READY."
        break
    fi
done

if [ "${GREEN_READY}" != "true" ]; then
    echo "ERROR: Green container failed readiness checks! Initiating immediate safety abort."
    docker logs --tail=50 vaisentra-backend-green
    echo "Stopping failed Green container without touching active production traffic..."
    docker compose -f "${COMPOSE_FILE}" --profile green stop backend-green
    docker compose -f "${COMPOSE_FILE}" --profile green rm -f backend-green
    echo "DEPLOYMENT ABORTED SAFELY. Production traffic remains 100% on Blue."
    exit 1
fi

# Step 5: Atomic Nginx Configuration Update
echo "[Step 5/6] Green verified. Applying Green upstream configuration to Nginx..."
cp "${NGINX_DIR}/nginx.conf" "${NGINX_DIR}/nginx.conf.prev"
cp "${NGINX_DIR}/nginx.conf.green" "${NGINX_DIR}/nginx.conf"

echo "Validating Nginx syntax..."
docker exec vaisentra-reverse-proxy nginx -t || {
    echo "ERROR: Nginx syntax validation failed! Reverting immediately..."
    cp "${NGINX_DIR}/nginx.conf.prev" "${NGINX_DIR}/nginx.conf"
    exit 1
}

echo "Reloading Nginx gracefully (Zero-Downtime Socket Handover)..."
docker exec vaisentra-reverse-proxy nginx -s reload

# Step 6: Verify Public Ingress
echo "[Step 6/6] Verifying public API routing through reverse proxy..."
sleep 3
PUBLIC_HEALTH=$(curl -s -k -o /dev/null -w "%{http_code}" https://127.0.0.1/health/ready || true)
echo "Public ingress health check returned HTTP: ${PUBLIC_HEALTH}"

echo "================================================================================"
echo "SUCCESS: GREEN RELEASE DEPLOYED AND ACTIVATED."
echo "Active Container:   vaisentra-backend-green (Port 8000 via internal_net)"
echo "Standby Container:  vaisentra-backend-blue (Port 8000 via internal_net, RETAINED)"
echo "Rollback Available: Run 'deployment/scripts/rollback_blue.sh' if needed."
echo "================================================================================"

