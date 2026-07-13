# ServerCore Community Minecraft Platform

Custom development foundation for a community All The Mods 11 server with modded survival, configurable portals, ranked duels, a public leaderboard, and Discord integration.

## Compatibility target

The current adapter is pinned provisionally to:

- All The Mods 11
- Minecraft `26.1.2`
- NeoForge `26.1.2.76`
- Java `25` for the Minecraft adapter
- Java `21` for platform-independent core tests

Revalidate these values before updating the ATM11 server pack. See `platform/atm11/compatibility.json`.

## What is implemented

### ServerCore Java core

- Portal regions, destinations, validation, cooldowns, permissions, and JSON persistence
- Atomic saves and backups
- Portal selection and administration command logic
- Player profiles, wins/losses, kills/deaths, rating, and leaderboard logic
- Casual and ranked duel matchmaking
- Arena reservation and release
- Elo rating updates
- Structured audit events
- Automated unit tests that do not require Minecraft

### ATM11 NeoForge adapter

- Actual NeoForge mod project for Minecraft 26.1.2
- Server lifecycle bootstrap
- Player portal-region detection
- Local world/dimension location teleports
- Operator-backed administration permissions
- In-game portal commands:
  - `/servercore status`
  - `/portal begin <name>`
  - `/portal pos1 <name>`
  - `/portal pos2 <name>`
  - `/portal create <name>` — run while standing at the destination where players should arrive
  - `/portal list`
  - `/portal info <name>`
  - `/portal delete <name>`
  - `/portal enable <name>`
  - `/portal disable <name>`

### Network and community services

- FastAPI player, leaderboard, match, event, and health endpoints
- Optional API-key protection for write operations
- CORS configuration for the community website
- Discord bot commands for stats, leaderboard, and API health
- Static responsive community website
- Dockerfiles and a local Docker Compose stack
- GitHub Actions workflows for core, NeoForge, API, bot, and website validation

## Repository structure

```text
server-core/          Platform-independent Java domain and services
servercore-neoforge/  ATM11/NeoForge Minecraft adapter and mod build
network-api/          FastAPI service
discord-bot/          Discord community bot
website/              Public status and leaderboard site
infrastructure/       Docker Compose and deployment notes
platform/atm11/       Version compatibility target
```

## Build the platform-independent core

```bash
cd server-core
gradle clean test build
```

Output: `server-core/build/libs/`

## Build the ATM11 mod

Java 25 is required.

```bash
cd servercore-neoforge
gradle clean build
```

Output: `servercore-neoforge/build/libs/servercore-0.1.0.jar`

## Run API and website locally

```bash
cd infrastructure
cp .env.example .env
docker compose up --build
```

Website: `http://localhost:8080`  
API: `http://localhost:8000`  
API docs: `http://localhost:8000/docs`

## Current limitations

- The API currently uses in-memory storage; PostgreSQL is required before public launch.
- Only local `LOCATION` portal destinations are connected to Minecraft. Cross-server `SERVER`, `ARENA`, and `EVENT` routing still needs a proxy or dedicated resolver.
- Ranked duel domain logic exists, but Minecraft combat lifecycle, inventory kits, arena boundaries, death handling, and spectator behavior still need NeoForge event wiring.
- The ATM11 mod JAR must pass CI and then be tested inside a real private ATM11 server.
- No payments, paid entry, shop, or supporter entitlements are implemented.
- Hosting, backups, monitoring, moderation policy, and production secrets are not configured.

## Development rule

Do not deploy changes directly to a public Minecraft server. Build through CI, install the artifact on a private ATM11 test server, test with designated players, and only then promote a known build to production.
