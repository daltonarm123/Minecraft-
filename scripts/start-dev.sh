#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  echo "Starting local services with Docker Compose..."
  (cd infrastructure && docker compose up --build -d)
  echo "API is available at http://localhost:8000"
  echo "API docs: http://localhost:8000/docs"
  exit 0
fi

echo "Docker is not available; starting the API directly with uvicorn..."
if [ ! -d "$ROOT/.venv" ]; then
  python3 -m venv "$ROOT/.venv"
fi
. "$ROOT/.venv/bin/activate"
pip install -e "$ROOT/network-api[test]" >/dev/null
pkill -f "uvicorn app.main:app --app-dir $ROOT/network-api" 2>/dev/null || true
nohup uvicorn app.main:app --app-dir "$ROOT/network-api" --host 0.0.0.0 --port 8000 >"$ROOT/.local-api.log" 2>&1 &

echo "API is available at http://localhost:8000"
echo "API docs: http://localhost:8000/docs"
echo "Logs: $ROOT/.local-api.log"
