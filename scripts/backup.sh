#!/bin/bash
set -e

BACKUP_BASE="/backup/govcms"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="$BACKUP_BASE/$TIMESTAMP"
DB_USER="${PROD_DB_USER:-GOVCMS1}"
DB_PASSWORD="${PROD_DB_PASSWORD:-}"
DB_HOST="${PROD_DB_HOST:-localhost}"
DB_PORT="${PROD_DB_PORT:-5236}"
DB_NAME="${PROD_DB_NAME:-GOVCMS1}"
STORAGE_DIR="/var/govcms/storage"
RETENTION_DAYS=30

mkdir -p "$BACKUP_DIR"

echo "[$(date)] Starting backup to $BACKUP_DIR"

echo "Backing up database..."
disql "$DB_USER/$DB_PASSWORD@$DB_HOST:$DB_PORT" -e "BACKUP DATABASE FULL TO '$BACKUP_DIR/db.bak' COMPRESSED;"

echo "Backing up storage..."
tar czf "$BACKUP_DIR/storage.tar.gz" -C "$STORAGE_DIR" .

echo "Backing up application jar..."
cp /opt/govcms/govcms-admin-0.0.1-SNAPSHOT.jar "$BACKUP_DIR/"

echo "[$(date)] Backup completed: $BACKUP_DIR"

find "$BACKUP_BASE" -maxdepth 1 -type d -mtime +$RETENTION_DAYS -exec rm -rf {} \; 2>/dev/null || true
echo "Cleaned up backups older than $RETENTION_DAYS days"
