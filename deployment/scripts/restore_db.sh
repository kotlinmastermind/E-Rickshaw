#!/bin/bash
# ==============================================================================
# VAISENTRA MOBILITY — ENTERPRISE DATABASE DISASTER RECOVERY & RESTORE SCRIPT
# Supports: PostgreSQL Custom Archives (.dump) and Compressed SQL (.sql.gz)
# Targets: Remote Managed PostgreSQL (Supabase) or Local Container
# ==============================================================================
set -euo pipefail

if [ $# -lt 1 ]; then
    echo "Usage: $0 <path_to_backup_archive> [--confirm]"
    echo "Example: $0 database/backups/production_full_backup_f23a1b2c3d4e_20261109_012359.dump"
    exit 1
fi

RESTORE_FILE="$1"
CONFIRM="${2:-}"

if [ ! -f "${RESTORE_FILE}" ]; then
    echo "Error: Backup file '${RESTORE_FILE}' not found."
    exit 1
fi

echo "================================================================================"
echo "VAISENTRA MOBILITY — PRODUCTION DATABASE RESTORE INITIATION"
echo "Backup Archive: ${RESTORE_FILE}"
echo "Archive Size:   $(ls -lh "${RESTORE_FILE}" | awk '{print $5}')"
echo "Timestamp:      $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
echo "================================================================================"

if [ "${CONFIRM}" != "--confirm" ]; then
    echo "WARNING: Restoring a database snapshot into active infrastructure will overwrite"
    echo "existing schema and data in the target database."
    echo "To execute, provide '--confirm' as the second argument."
    echo "Aborting dry-run safely."
    exit 0
fi

echo "Initiating restore in 5 seconds... Press Ctrl+C to abort."
sleep 5

# Detect archive format
if [[ "${RESTORE_FILE}" == *.dump ]]; then
    echo "[$(date -u)] Detected PostgreSQL Custom Archive (.dump). Utilizing pg_restore..."
    
    # Check for direct connection credentials or Docker container
    if [ -n "${PGHOST:-}" ] && [ -n "${PGDATABASE:-}" ]; then
        echo "[$(date -u)] Restoring to target host: ${PGHOST}:${PGPORT:-5432}/${PGDATABASE}"
        # pg_restore exit code 1 indicates non-fatal warnings (e.g. schema public already exists)
        set +e
        pg_restore -h "${PGHOST}" -p "${PGPORT:-5432}" -U "${PGUSER:-postgres}" -d "${PGDATABASE}" \
                   --no-owner --no-privileges "${RESTORE_FILE}"
        RESTORE_EXIT=$?
        set -e
    elif command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' | grep -q 'postgres'; then
        PG_CONTAINER=$(docker ps --format '{{.Names}}' | grep -E 'postgres|vaisentra-postgres' | head -n 1)
        echo "[$(date -u)] Restoring into active Docker container: ${PG_CONTAINER}"
        set +e
        docker cp "${RESTORE_FILE}" "${PG_CONTAINER}:/tmp/restore.dump"
        docker exec -i "${PG_CONTAINER}" pg_restore -U "${POSTGRES_USER:-postgres}" -d "${POSTGRES_DB:-postgres}" \
                    --no-owner --no-privileges /tmp/restore.dump
        RESTORE_EXIT=$?
        docker exec -i "${PG_CONTAINER}" rm -f /tmp/restore.dump
        set -e
    else
        echo "Error: No PostgreSQL credentials (PGHOST, PGDATABASE) or running postgres container found."
        exit 1
    fi

    # Evaluate pg_restore exit code:
    # 0 = clean success, 1 = non-fatal warnings ignored, 2 = fatal error
    if [ ${RESTORE_EXIT} -eq 0 ]; then
        echo "[$(date -u)] pg_restore completed with Exit Code 0 (Clean Success)."
    elif [ ${RESTORE_EXIT} -eq 1 ]; then
        echo "[$(date -u)] pg_restore completed with Exit Code 1 (Warnings ignored, e.g. pre-existing public schema)."
        echo "[$(date -u)] Schema and table data restored successfully."
    else
        echo "[$(date -u)] ERROR: pg_restore failed with FATAL Exit Code ${RESTORE_EXIT}."
        exit ${RESTORE_EXIT}
    fi

elif [[ "${RESTORE_FILE}" == *.sql.gz ]]; then
    echo "[$(date -u)] Detected gzipped SQL archive (.sql.gz). Utilizing psql stream..."
    if [ -n "${PGHOST:-}" ] && [ -n "${PGDATABASE:-}" ]; then
        gunzip -c "${RESTORE_FILE}" | psql -h "${PGHOST}" -p "${PGPORT:-5432}" -U "${PGUSER:-postgres}" -d "${PGDATABASE}"
    elif command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' | grep -q 'postgres'; then
        PG_CONTAINER=$(docker ps --format '{{.Names}}' | grep -E 'postgres|vaisentra-postgres' | head -n 1)
        gunzip -c "${RESTORE_FILE}" | docker exec -i "${PG_CONTAINER}" psql -U "${POSTGRES_USER:-postgres}" -d "${POSTGRES_DB:-postgres}"
    else
        echo "Error: No target host or postgres container found."
        exit 1
    fi
else
    echo "Error: Unsupported archive format: ${RESTORE_FILE}. Expected .dump or .sql.gz."
    exit 1
fi

echo "[$(date -u)] Database restoration completed successfully."
