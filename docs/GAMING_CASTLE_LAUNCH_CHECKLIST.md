# Gaming Castle Launch Checklist

This checklist is for the ATM10 / Minecraft 1.21.1 Gaming Castle world and the ServerCore launch branch.

## ServerCore launch branch

- Branch: `feature/gaming-castle-launch-ready`
- Draft PR: #5
- Do not merge to `main` until CI is green and the in-world checklist below passes.

## Persistent ServerCore data

The launch build stores persistent community data under `config/servercore/`.

Important files include:

- `portals.json` - managed portal network
- `roles.json` - ServerCore staff roles
- `wallets.json` - balances and transaction ledger
- `players.json` - duel/player profiles and ratings
- `market-listings.json` - legacy core-market listing persistence
- `auction-house.json` - real item-backed public auction listings/mailbox
- `community-player-data.json` - homes, daily rewards, welcomes, warnings, mutes, notes
- `cosmetics.json` - cosmetic ownership/catalog state

Back up the world and `config/servercore/` together.

## Gaming Castle areas

- Hub: approximately X -265..-25, Z -70..185
- Market: approximately X 1440..1560, Z -60..60
- Duels: approximately X -1565..-1435, Z -65..65
- Staff: approximately X -52..52, Z -1552..-1444
- Survival landing only: approximately X -20..20, Z 1468..1540

The Hub, Market, Staff Lounge, Duels infrastructure, and Survival landing are protected from non-staff griefing. Survival outside the landing infrastructure is intentionally buildable.

## Managed portals

Expected portal names:

- `gc_market`
- `gc_staff`
- `gc_survival`
- `gc_duels`
- `gc_market_return`
- `gc_staff_return`
- `gc_survival_return`
- `gc_duels_return`

Use `/portal list` as staff to verify all eight after startup.

## Public commands to test

Travel and information:

- `/spawn`
- `/hub`
- `/sethome`
- `/home`
- `/back`
- `/tpa <player>`
- `/tpaccept`
- `/tpdeny`
- `/rules`
- `/help`
- `/discord`

Economy and progression:

- `/balance`
- `/shop`
- `/pay <player> <amount>`
- `/daily`
- `/stats`
- `/leaderboard`
- `/market list`
- `/market sell <price>` while holding the item to sell
- `/market buy <listing-id>`
- `/market cancel <listing-id>`
- `/market claim`

Duels:

- `/duel join casual`
- `/duel join ranked`
- `/duel leave`
- `/duel status`
- `/duel stats`
- `/duel spectate`
- `/duel lobby`

Community events:

- `/event`
- `/event status`
- Staff: `/event duels`, `/event build`, `/event market`, `/event announce <message>`

Staff tools:

- `/staff warn <player> <reason>`
- `/staff note <player> <note>`
- `/staff notes <player>`
- `/staff mute <player> <minutes> [reason]`
- `/staff unmute <player>`
- `/staff freeze <player>`
- `/staff unfreeze <player>`
- `/staff kick <player> [reason]`
- `/staff ban <player> [reason]`
- `/staff vanish`
- `/staff inventory <player>`
- Public reports: `/report <player> <reason>`

## Required in-world test before opening the server

Test with at least two accounts. One should be a completely normal, non-OP, non-staff player.

1. Restart Minecraft/server completely and make sure ServerCore starts without exceptions.
2. Run `/portal list` and verify all eight Gaming Castle portals exist.
3. Walk through every outbound portal and every return portal.
4. Confirm a normal player cannot enter the Staff portal.
5. Confirm a normal player cannot break/place blocks, use buckets/fire, damage decorations, piston-grief, or explosion-grief protected infrastructure.
6. Confirm staff can still edit protected areas.
7. Confirm PvP/entity attacks are blocked in Hub, Market, Staff, and Survival landing, but combat works in the Duels district.
8. Apply the dense Gaming Castle lighting pack and inspect Hub, Market, Duels, Staff, and Survival landing at night for remaining dark spawn pockets.
9. Restart again and verify `/balance` is unchanged.
10. Claim `/daily`, restart, and verify it cannot be claimed twice the same UTC day.
11. Set `/home`, restart, and verify the saved home still works.
12. Test `/market sell` with an actual ATM10 item containing components/NBT, restart, then buy/claim it with another player. Confirm no duplication and no item loss.
13. Fill the buyer inventory and buy an auction item. Confirm it remains safely claimable with `/market claim`.
14. Run a Casual duel and a Ranked duel. Verify both inventories restore correctly after death, forfeit, and disconnect/reconnect testing.
15. Verify combat-tagged players cannot escape with `/hub`, `/home`, `/back`, TPA, or portal pads.
16. Verify warnings, notes, and mutes survive a restart.
17. Run the backup script and confirm a timestamped backup archive is created before public launch.
18. Run an ATM10 stress test with several players/chunk loading and confirm TPS/memory are acceptable.

## Backup policy

Use `scripts/backup-gaming-castle.ps1` before every mod update, ServerCore JAR replacement, datapack rebuild, or major staff action affecting the world.

Recommended retention:

- keep several recent automatic/daily backups
- keep one known-good pre-update backup
- periodically copy a backup off the server machine

## Launch rule

Do not merge PR #5 or advertise the server publicly until GitHub CI is green and the full in-world checklist has passed on the exact JAR/world/datapacks that will be used for launch.
