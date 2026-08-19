#!/usr/bin/env bash
# Deploy the ServerCore mod to a local ATM10 NeoForge server.
# Usage: ./scripts/deploy-local.sh [/path/to/atm10-server]
#
# The server path defaults to $ATM10_SERVER_DIR or ~/atm10-server if not passed.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
NEOFORGE_DIR="$PROJECT_ROOT/servercore-neoforge"

SERVER_DIR="${1:-${ATM10_SERVER_DIR:-$HOME/atm10-server}}"

if [[ ! -d "$SERVER_DIR" ]]; then
  echo "ERROR: ATM10 server directory not found: $SERVER_DIR"
  echo "  Pass the server path as the first argument or set ATM10_SERVER_DIR."
  exit 1
fi

MODS_DIR="$SERVER_DIR/mods"
if [[ ! -d "$MODS_DIR" ]]; then
  echo "ERROR: No mods/ folder found inside $SERVER_DIR"
  exit 1
fi

echo "Building mod..."
cd "$NEOFORGE_DIR"
./gradlew build --no-daemon --console=plain -q

JAR=$(find "$NEOFORGE_DIR/build/libs" -name "servercore-*.jar" ! -name "*-sources*" | head -1)
if [[ -z "$JAR" ]]; then
  echo "ERROR: No servercore jar found in build/libs"
  exit 1
fi

# Remove any previously deployed version before copying the new one
find "$MODS_DIR" -maxdepth 1 -name "servercore-*.jar" -delete

cp "$JAR" "$MODS_DIR/"
echo "Deployed $(basename "$JAR") → $MODS_DIR"
echo ""
echo "Start your ATM10 server with:"
echo "  cd \"$SERVER_DIR\" && java -Xmx6G -Xms4G @libraries/net/neoforged/neoforge/21.1.247/win_args.txt nogui"
echo "or use the start.sh script that came with the CurseForge pack."
