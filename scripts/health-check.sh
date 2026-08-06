#!/usr/bin/env bash
set -euo pipefail

if curl -fsS http://localhost:8000/health >/dev/null 2>&1; then
  echo "healthy"
  exit 0
fi

echo "unhealthy"
exit 1
