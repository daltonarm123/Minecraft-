#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT/infrastructure"

echo "Starting local services with Docker Compose..."
docker compose up --build -d

echo "API is available at http://localhost:8000"
echo "API docs: http://localhost:8000/docs"
