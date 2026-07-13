#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "ERROR: required command '$1' is not installed." >&2
    exit 2
  fi
}

require_command python3
require_command node
require_command java
require_command gradle

JAVA_VERSION="$(java -version 2>&1 | head -n 1)"
echo "Using $JAVA_VERSION"
echo "Using $(python3 --version)"
echo "Using Node $(node --version)"
echo "Using $(gradle --version | awk '/^Gradle / {print; exit}')"

VERIFY_DIR="${SERVERCORE_VERIFY_DIR:-$ROOT/.verify}"
VENV="$VERIFY_DIR/venv"
mkdir -p "$VERIFY_DIR"

if [[ ! -x "$VENV/bin/python" ]]; then
  python3 -m venv "$VENV"
fi

"$VENV/bin/python" -m pip install --upgrade pip
"$VENV/bin/python" -m pip install -e "$ROOT/network-api[test]"
"$VENV/bin/python" -m pip install -e "$ROOT/discord-bot[test]"

(
  cd network-api
  "$VENV/bin/python" -m compileall -q app tests
  "$VENV/bin/python" -m pytest -q
)

(
  cd discord-bot
  "$VENV/bin/python" -m compileall -q app tests
  "$VENV/bin/python" -m pytest -q
)

(
  cd website
  node --check config.js
  node --check app.js
  test -s index.html
  test -s styles.css
  test -s nginx.conf
)

(
  cd server-core
  gradle --no-daemon clean test build
)

if [[ "${VERIFY_NEOFORGE:-1}" == "1" ]]; then
  (
    cd servercore-neoforge
    CI=true gradle --no-daemon clean build --stacktrace
  )
else
  echo "Skipping NeoForge build because VERIFY_NEOFORGE=$VERIFY_NEOFORGE"
fi

echo "All requested local verification steps passed."
