#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

VERIFY_API="${VERIFY_API:-1}"
VERIFY_CORE="${VERIFY_CORE:-1}"
VERIFY_NEOFORGE="${VERIFY_NEOFORGE:-1}"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "ERROR: required command '$1' is not installed." >&2
    exit 2
  fi
}

require_command python3

if [[ "$VERIFY_API" == "1" ]]; then
  if ! python3 -c 'import pip' >/dev/null 2>&1; then
    echo "ERROR: Python module 'pip' is required for API verification. Install python3-pip." >&2
    exit 2
  fi
  if ! python3 -c 'import venv' >/dev/null 2>&1; then
    echo "ERROR: Python module 'venv' is required for API verification. Install python3-venv." >&2
    exit 2
  fi
fi

if [[ "$VERIFY_CORE" == "1" || "$VERIFY_NEOFORGE" == "1" ]]; then
  require_command java
  require_command gradle
fi

if [[ "$VERIFY_CORE" == "1" || "$VERIFY_NEOFORGE" == "1" ]]; then
  JAVA_VERSION="$(java -version 2>&1 | head -n 1)"
  echo "Using $JAVA_VERSION"
fi
echo "Using $(python3 --version)"
if [[ "$VERIFY_CORE" == "1" || "$VERIFY_NEOFORGE" == "1" ]]; then
  echo "Using $(gradle --version | awk '/^Gradle / {print; exit}')"
fi

if [[ "$VERIFY_API" == "1" ]]; then
  VERIFY_DIR="${SERVERCORE_VERIFY_DIR:-$ROOT/.verify}"
  VENV="$VERIFY_DIR/venv"
  mkdir -p "$VERIFY_DIR"

  if [[ ! -x "$VENV/bin/python" ]]; then
    python3 -m venv "$VENV"
  fi

  "$VENV/bin/python" -m pip install --upgrade pip
  "$VENV/bin/python" -m pip install -e "$ROOT/network-api[test]"
fi

if [[ "$VERIFY_API" == "1" ]]; then
  (
    cd network-api
    "$VENV/bin/python" -m compileall -q app tests
    "$VENV/bin/python" -m pytest -q
  )
else
  echo "Skipping Network API checks because VERIFY_API=$VERIFY_API"
fi

if [[ "$VERIFY_CORE" == "1" ]]; then
  (
    cd server-core
    gradle --no-daemon clean test build
  )
else
  echo "Skipping server-core build because VERIFY_CORE=$VERIFY_CORE"
fi

if [[ "$VERIFY_NEOFORGE" == "1" ]]; then
  (
    cd servercore-neoforge
    CI=true gradle --no-daemon clean build --stacktrace
  )
else
  echo "Skipping NeoForge build because VERIFY_NEOFORGE=$VERIFY_NEOFORGE"
fi

echo "All requested local verification steps passed."
