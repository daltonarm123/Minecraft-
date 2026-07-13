# Minecraft Community Server

Browser-first development foundation for a custom modded Minecraft community server.

## Current status

This repository currently contains the platform-independent `ServerCore` prototype. It focuses on portal domain logic, JSON persistence, validation, cooldowns, permissions, and testable command abstractions without requiring Minecraft to be installed.

The actual NeoForge integration will be added after the team selects the Minecraft version, NeoForge version, and base modpack.

## Planned system

```text
Minecraft ServerCore mod
        |
        | Future HTTP/WebSocket communication
        v
Network API
        |
        v
PostgreSQL
        |
        +---- Website
        |
        +---- Discord Bot
```

## Open in Codespaces

1. Open this repository on GitHub.
2. Select **Code** → **Codespaces** → **Create codespace on main**.
3. Wait for the Java development container to finish building.

## Build and test

```bash
cd server-core
./gradlew test
./gradlew build
```

The built JAR will appear under `server-core/build/libs/`.

## Known limitations

- No Minecraft or NeoForge event wiring yet.
- No in-game command registration yet.
- No live teleport implementation yet.
- No production deployment or payment system.
- Cross-server transfers require a later proxy/network decision.

## Repository areas

- `server-core/` — Java portal-system MVP.
- `docs/` — architecture, development, testing, and roadmap documents.
- `network-api/` — future API placeholder.
- `discord-bot/` — future Discord integration placeholder.
- `website/` — future leaderboard/site placeholder.
- `infrastructure/` — deployment examples and notes.
