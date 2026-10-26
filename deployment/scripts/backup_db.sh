#!/bin/bash
set -euo pipefail

BACKUP_DIR="/opt/luxury_backups"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILE="${BACKUP_DIR}/luxury_db_backup_${TIMESTAMP}.sql.gz"

mkdir -p "${BACKUP_DIR}"

echo "[$(date)] Initiating authoritative PostgreSQL backup..."
docker exec luxury_postgres pg_dump -U "${POSTGRES_USER:-luxury_user}" "${POSTGRES_DB:-luxury_ride_db}" | gzip > "${BACKUP_FILE}"

echo "[$(date)] Backup successfully generated at: ${BACKUP_FILE}"

# Retention policy: Remove backups older than 30 days
find "${BACKUP_DIR}" -type f -name "luxury_db_backup_*.sql.gz" -mtime +30 -delete
echo "[$(date)] Backup retention policy applied."
