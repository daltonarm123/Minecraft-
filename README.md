# ServerCore Minecraft Server Platform

Custom development foundation for an All The Mods 11 server with modded survival, configurable portals, ranked duels, a player economy, and server-side APIs.

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
- Server-authoritative cosmetics, wallets, shop catalog, and player market services
- Automated unit tests that do not require Minecraft

### ATM11 NeoForge adapter

- Actual NeoForge mod project for Minecraft 26.1.2
- Server lifecycle bootstrap
- Player portal-region detection
- Local world/dimension location teleports
- Operator-backed administration permissions
- Discord-to-Minecraft account linking with `/link <code>`
- In-game portal, role, economy, shop, and market commands

### Network services

- FastAPI player, leaderboard, match, event, cosmetic, and health endpoints
- Discord OAuth player login
- Web player portal at `/portal`
- Linked Minecraft profile, stats, achievements, and leaderboard views
- Hosted membership checkout handoff and server-authoritative membership status
- Support tickets and community feature voting
- Optional API-key protection for write operations
- CORS configuration for approved origins
- Dockerfiles and a local Docker Compose stack
- GitHub Actions workflows for core, NeoForge, and API validation

## Player portal MVP

The portal gives members one place to:

- Log in with Discord
- Link their Minecraft account
- View synchronized stats and achievements
- Check leaderboards
- Open the configured $5 membership checkout
- Submit support tickets
- Vote on proposed features

Deployment and security notes: `docs/PLAYER_PORTAL.md`

## Console readiness

The platform is preparing for mixed Java PC and console players through a staged Bedrock bridge approach.

- Planning doc: `docs/CONSOLE_CONNECTIVITY_PLAN.md`
- Current state: not yet enabled for public players
- Release rule: ship only after mixed-protocol private tests pass

## Launch portals and economy

Initial launch gameplay is focused on Survival and 1v1 portal routing from spawn, plus a controlled in-game currency and player market model.

- Execution plan: `docs/LAUNCH_PORTALS_AND_ECONOMY_PLAN.md`
- Shop pricing catalog: `docs/SHOP_CATALOG.md`
- Staff roles and command map: `docs/STAFF_ROLES_AND_COMMANDS.md`

## Repository structure

```text
server-core/          Platform-independent Java domain and services
servercore-neoforge/  ATM11/NeoForge Minecraft adapter and mod build
network-api/          FastAPI service and player portal
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

## Run API locally

```bash
cd infrastructure
cp .env.example .env
docker compose up --build
```

API: `http://localhost:8000`  
API docs: `http://localhost:8000/docs`  
Player portal: `http://localhost:8000/portal`

## Current limitations

- The API and player portal currently use in-memory storage; PostgreSQL is required before public launch.
- Payment checkout can be configured, but verified payment-provider webhooks and automatic join enforcement are not implemented yet.
- Only local `LOCATION` portal destinations are connected to Minecraft. Cross-server `SERVER`, `ARENA`, and `EVENT` routing still needs a proxy or dedicated resolver.
- Ranked duel domain logic exists, but Minecraft combat lifecycle, inventory kits, arena boundaries, death handling, and spectator behavior still need NeoForge event wiring.
- The ATM11 mod JAR must pass CI and then be tested inside a real private ATM11 server.
- Console access support is planned but not yet operational; Bedrock bridge setup, identity-link policy, and mixed-protocol validation are still required.
- Hosting, backups, monitoring, moderation policy, and production secrets are not configured.

## Development rule

Do not deploy changes directly to a public Minecraft server. Build through CI, install the artifact on a private ATM11 test server, test with designated players, and only then promote a known build to production.
