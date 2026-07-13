# ATM11 Private Server Test Plan

Use this plan only after the ServerCore NeoForge JAR builds successfully. Do not install an unverified build on a public or valuable world.

## Test environment

- A fresh private All The Mods 11 server matching `platform/atm11/compatibility.json`
- Java 25
- The same ATM11 server-pack version on the server and every tester's client
- A copied ServerCore JAR in the server `mods/` directory
- At least two operator testers
- A disposable world or a full world backup
- Access to `logs/latest.log` and crash reports

## Installation smoke test

1. Back up the test server.
2. Stop the server completely.
3. Copy `servercore-0.1.0.jar` into `mods/`.
4. Start the server.
5. Confirm the server reaches the ready state without a mod-loading error.
6. Run `/servercore status` as an operator.
7. Confirm `config/servercore/servercore.json` is created.
8. Confirm no credentials or personal information appear in the logs.

A failure in this section blocks all other testing.

## Create the first portal

The portal region is selected where players will enter. The destination is the location where the administrator stands when the portal is created.

1. Stand at the first corner of the desired portal region.
2. Run `/portal begin survival`.
3. Run `/portal pos1 survival`.
4. Stand at the opposite corner of the portal region.
5. Run `/portal pos2 survival`.
6. Travel to the desired arrival point.
7. Run `/portal create survival`.
8. Run `/portal info survival`.
9. Walk into the selected portal region.

Expected result: the player is moved to the saved destination and receives a success message.

## Required portal tests

Record pass/fail for every item.

- Enter through every side and corner of the selected region.
- Stand exactly on the minimum and maximum boundaries.
- Enter repeatedly during the configured cooldown.
- Test two players entering in the same tick window.
- Test an operator and a normal player.
- Disable the portal and confirm it no longer teleports.
- Enable it and confirm it works again.
- Restart the server and confirm the portal remains saved.
- Rename neither the dimension nor modpack between restart tests.
- Test a destination in the overworld.
- Test a destination in another loaded dimension.
- Test an invalid or unavailable dimension and confirm the server does not crash.
- Test after player death and respawn.
- Test while mounted, crouching, sprinting, and flying where the modpack permits it.
- Confirm a failed teleport does not begin the cooldown.
- Confirm a player cannot become trapped in an instant portal loop.

## Performance test

1. Create 10 portals in different locations.
2. Have at least five players move around portal regions for 15 minutes.
3. Watch server tick time, memory use, and log volume.
4. Confirm portal checks do not flood `latest.log`.
5. Repeat with the configured check interval changed from 5 ticks to 10 ticks.

## Persistence and recovery test

- Stop the server normally and inspect `portals.json`.
- Confirm a backup file is created after a later change.
- Deliberately place invalid JSON only on a disposable copy and confirm startup fails safely with a useful error.
- Restore the backup and confirm portals load.
- Confirm a partial write or forced stop does not destroy the last valid portal file.

## Duel foundation tests

The Java duel and rating logic exists, but actual Minecraft combat wiring is not complete. Do not describe ranked PvP as playable until these later tests pass:

- Arena registration and spawn points
- Queue joining and leaving
- Two-player matching
- Arena reservation
- Inventory snapshot and restoration
- Kit application
- Countdown and movement lock
- Death, disconnect, surrender, and timeout handling
- Spectator behavior
- Winner recording and Elo changes
- Arena cleanup and reuse
- Restart recovery during an active match

## Tester bug report template

```text
SERVERCORE BUG

Build/JAR name:
ATM11 server-pack version:
NeoForge version:
Tester Minecraft name:
Date and local time:
Feature tested:
Exact commands used:
What happened:
What should have happened:
Can it be reproduced every time:
Coordinates and dimension:
Other players present:
Video or screenshots:
Relevant latest.log lines:
Crash-report filename, if any:
```

## Files the developer needs after a failure

- The exact ServerCore JAR filename
- `logs/latest.log`
- The matching crash report, when one exists
- `config/servercore/servercore.json`
- `config/servercore/portals.json`
- A short screen recording beginning before reproduction
- The exact ATM11 server-pack and NeoForge versions

Remove tokens, passwords, IP allowlists, and unrelated personal information before sharing logs.
