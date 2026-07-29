#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_FILE="${LOG_FILE:-$ROOT/.watchdog.log}"

mkdir -p "$ROOT"

echo "[$(date -Iseconds)] watchdog starting" >> "$LOG_FILE"

while true; do
  if curl -fsS http://localhost:8000/health >/dev/null 2>&1; then
    :
  else
    echo "[$(date -Iseconds)] health check failed; restarting API container" >> "$LOG_FILE"
    (cd "$ROOT/infrastructure" && docker compose restart network-api) >> "$LOG_FILE" 2>&1 || true
  fi
  sleep 30
 done
