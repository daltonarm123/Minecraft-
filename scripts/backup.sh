#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKUP_DIR="${BACKUP_DIR:-$ROOT/.backups}"
STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP_PATH="$BACKUP_DIR/servercore-backup-$STAMP"

mkdir -p "$BACKUP_DIR"
mkdir -p "$BACKUP_PATH"

echo "Creating backup at $BACKUP_PATH"
cp -R "$ROOT/network-api" "$BACKUP_PATH/network-api" 2>/dev/null || true
cp -R "$ROOT/server-core" "$BACKUP_PATH/server-core" 2>/dev/null || true
cp -R "$ROOT/servercore-neoforge" "$BACKUP_PATH/servercore-neoforge" 2>/dev/null || true
cp -R "$ROOT/infrastructure" "$BACKUP_PATH/infrastructure" 2>/dev/null || true
cp -R "$ROOT/docs" "$BACKUP_PATH/docs" 2>/dev/null || true

echo "Backup complete: $BACKUP_PATH"
