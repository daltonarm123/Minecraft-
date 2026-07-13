# ServerCore Cosmetics and Wardrobe System

## Current implementation

The platform-independent ServerCore module now contains a server-authoritative cosmetics foundation.

Implemented concepts:

- Stable cosmetic IDs
- Cosmetic categories and rarities
- Unlock sources
- Namespaced asset references
- Enabled and disabled definitions
- Player ownership
- One equipped cosmetic per category
- Grant, revoke, equip, and unequip operations
- Automatic unequip when ownership is revoked
- Atomic JSON persistence with backup files
- Unit tests for ownership, wardrobe rules, and restart persistence

The server remains authoritative. A client must never be allowed to claim ownership or equip an item that the server has not granted.

## Current limitation

Cosmetic definitions and player wardrobes now persist to `config/servercore/cosmetics.json`, with the previous valid file retained as `cosmetics.json.bak` when data changes.

JSON is suitable for the private prototype and early testing. PostgreSQL should replace it before a large public launch so grants, purchases, audit history, and concurrent services can use transactional storage.

The current NeoForge adapter does not yet render custom models, textures, outfits, pets, particles, emotes, or animations. Rendering requires a client-side ServerCore module distributed as part of the final custom ATM11 pack.

## Planned data flow

```text
Original licensed cosmetic assets
            |
            v
Cosmetic definition catalog
            |
            v
Hosted Minecraft server / ServerCore
  validates ownership and equipment
            |
            +---- persistent database
            |
            +---- Network API
            |
            v
Client-side ServerCore renderer
  loads approved model and texture IDs
```

## Required next stages

1. Add staff commands for grant, revoke, enable, disable, inspect, and audit.
2. Add player commands and a wardrobe protocol.
3. Add API endpoints for public catalog and owned cosmetics.
4. Add network packets that synchronize equipped cosmetic IDs to clients.
5. Build the client-side renderer for titles and nameplates first.
6. Add original 3D outfit models only after the client module loads reliably.
7. Add wardrobe preview UI.
8. Connect achievement, quest, event, founder, and supporter unlock sources.
9. Move public-server storage from JSON to PostgreSQL.
10. Test reconnects, restarts, revoked assets, disabled assets, and multiplayer visibility.

## Security rules

- Never accept ownership claims from the client.
- Validate every equip request on the server.
- Reject disabled, missing, or unowned cosmetics.
- Do not attach armor, damage, speed, loot, or progression bonuses to cosmetic definitions.
- Record all staff grants and revocations before public launch.
- Only load original or properly licensed models, textures, sounds, and animations.
- Do not create or sell official-style Minecraft capes.
