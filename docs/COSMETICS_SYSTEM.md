# ServerCore Cosmetics and Wardrobe System

## Implemented foundation

The platform-independent ServerCore module contains a server-authoritative cosmetics system with:

- stable cosmetic IDs
- categories, rarity levels, and unlock sources
- validated namespaced asset references
- enabled and disabled definitions
- player ownership
- one equipped cosmetic per category
- grant, revoke, equip, and unequip operations
- automatic unequip after revocation or global disabling
- atomic JSON persistence with backup files
- an idempotent original launch catalog
- player and staff command-service logic
- audit events for grants, revocations, equipment changes, and definition status
- API catalog, wardrobe, grant, revoke, equip, and unequip endpoints
- automated ownership, permissions, persistence, and API tests

The server remains authoritative. A client must never be allowed to claim ownership or equip an item that the server has not granted.

## Original launch metadata

The default catalog currently seeds metadata for four original items:

- `pioneer-title`
- `founder-crown`
- `ember-trail`
- `void-nameplate`

These are data definitions, not completed art. Models, textures, animations, sounds, and effects must be created as original or properly licensed assets before they can be displayed in Minecraft.

## Persistence

Cosmetic definitions and player wardrobes persist to `config/servercore/cosmetics.json`. The previous valid file is retained as `cosmetics.json.bak` when data changes.

JSON is suitable for a private prototype. PostgreSQL should replace it before a large public launch so grants, purchases, audit history, and concurrent services use transactional storage.

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

1. Register the cosmetic command service with NeoForge after the adapter build is verified.
2. Add server-to-client packets that synchronize equipped IDs.
3. Build the client renderer for titles and nameplates first.
4. Create the wardrobe preview screen.
5. Add original 3D outfit, hat, pet, trail, emote, and effect assets.
6. Connect achievements, quests, events, founder status, and supporter unlocks.
7. Move public-server storage from JSON to PostgreSQL.
8. Test reconnects, restarts, disabled assets, revoked assets, and multiplayer visibility.

## Security rules

- Never accept ownership claims from the client.
- Validate every equip request on the server.
- Reject disabled, missing, or unowned cosmetics.
- Unequip a cosmetic immediately when it is revoked or disabled.
- Do not attach armor, damage, speed, loot, or progression bonuses to cosmetic definitions.
- Record all staff grants and revocations.
- Load only original or properly licensed models, textures, sounds, and animations.
- Do not create or sell official-style Minecraft capes.
