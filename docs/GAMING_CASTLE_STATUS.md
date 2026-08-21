# Gaming Castle — Current Server State

Last updated: 2026-08-20

## Main hub

- Community name: **Gaming Castle**
- Overworld world spawn / Realm City center: approximately `-145 64 50`
- Main protected city footprint: X `-265..-25`, Z `-70..185`
- Normal players cannot break ServerCore-protected infrastructure; ServerCore staff roles can edit it.

## Hub portal network

ServerCore automatically manages these portals on startup:

| Portal | Hub source | Destination |
| --- | --- | --- |
| `gc_market` | Market pad in Gaming Castle | Market District near `1500 72 30` |
| `gc_staff` | Staff / Dev pad in Gaming Castle | Staff Lounge near `0 72 -1480` |
| `gc_survival` | Survival pad in Gaming Castle | Survival landing near `0 72 1500` |
| `gc_duels` | Duels pad in Gaming Castle | Duels Arena near `-1500 72 30` |
| `gc_market_return` | Market return pad | Gaming Castle hub |
| `gc_staff_return` | Staff return pad | Gaming Castle hub |
| `gc_survival_return` | Survival return pad | Gaming Castle hub |
| `gc_duels_return` | Duels return pad | Gaming Castle hub |

The Staff portal requires `servercore.staff`. Any player holding a ServerCore staff role satisfies this permission.

## Protected destination infrastructure

- Market District: X `1440..1560`, Z `-60..60`
- Duels Arena: X `-1565..-1435`, Z `-65..65`
- Staff Lounge: X `-52..52`, Z `-1552..-1444`
- Survival landing infrastructure only: X `-20..20`, Z `1468..1540`
- The rest of the Survival area is intentionally buildable by normal players.

## Datapacks used to build the current world

These are world/datapack assets rather than ServerCore source code. Keep the installed ZIPs in the active world's `datapacks` folder and retain backups when rebuilding areas.

Current/final pack chain from the development session:

1. `realm_city_v3_cinematic.zip` — large Realm City base build.
2. `realm_city_water_fix.zip` — contained the central water basins.
3. `realm_city_v4_art_upgrade.zip` — palace, portal districts, walls, entrance, and visual upgrade.
4. `gaming_castle_signs_v4_fixed.zip` — fixed wall-mounted portal labels and Gaming Castle branding.
5. `gaming_castle_destinations_v1.zip` — Market, Duels, Staff, Survival destination areas and hub pads.
6. `gaming_castle_destination_patch_v2.zip` — lighting improvements and finished Staff return corridor.

Important namespaces / useful tests:

- Realm City: `realmcity:*`
- Water fix: `realmcityfix:*`
- V4 city upgrade: `realmcityv4:*`
- Final signs: `gamingcastlev4:*`
- Destinations: `gcdest:*`
- Destination lighting/Staff fix: `gcdestfix:*`

## ServerCore development branch

The tested Gaming Castle work currently lives on:

`fix/shop-gui-dev-perms`

This branch contains:

- typed NeoForge shop screen fix
- development permission/rank fixes for SoaREnvy
- Gaming Castle block protection
- `servercore.staff` permission support
- managed Gaming Castle portal bootstrap
- hub/destination return portal regions
- safer startup behavior if managed portal bootstrap fails

The branch should be built with Java 21 from `servercore-neoforge` and the resulting `build/libs/servercore-0.1.0.jar` copied into the ATM10 mods folder for testing/deployment.

## Notes for next session

- The city and destination builds are considered visually usable as of this checkpoint.
- Market, Duels, and Staff received additional lighting; Staff now has a deliberate return corridor.
- Before major new world edits, back up the save.
- Do not rebuild older city/sign datapacks over the finished world unless intentionally replacing those sections.
