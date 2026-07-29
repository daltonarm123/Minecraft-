#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if ! command -v curl >/dev/null 2>&1; then
  echo "ERROR: curl is required for the smoke test." >&2
  exit 2
fi

if ! docker compose -f "$ROOT/infrastructure/docker-compose.yml" ps --services --filter status=running | grep -q '^network-api$'; then
  echo "API container is not running. Start it with ./scripts/start-dev.sh" >&2
  exit 1
fi

HEALTH_RESPONSE="$(curl -fsS http://localhost:8000/health)"
echo "Health endpoint responded:"
echo "$HEALTH_RESPONSE"
