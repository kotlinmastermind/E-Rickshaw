#!/bin/bash
set -euo pipefail

if [ $# -lt 1 ]; then
    echo "Usage: $0 <path_to_backup.sql.gz>"
    exit 1
fi

RESTORE_FILE="$1"

if [ ! -f "${RESTORE_FILE}" ]; then
    echo "Error: Backup file '${RESTORE_FILE}' not found."
    exit 1
fi

echo "[$(date)] WARNING: Initiating authoritative database restore from ${RESTORE_FILE}"
echo "Press Ctrl+C within 5 seconds to abort."
sleep 5

gunzip -c "${RESTORE_FILE}" | docker exec -i luxury_postgres psql -U "${POSTGRES_USER:-luxury_user}" -d "${POSTGRES_DB:-luxury_ride_db}"

echo "[$(date)] Database restoration completed successfully."
