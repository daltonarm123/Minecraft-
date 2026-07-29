#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT/infrastructure"

echo "Stopping services and removing local state..."
docker compose down --volumes --remove-orphans || true
rm -rf "$ROOT/.verify"

echo "Local dev environment reset."
