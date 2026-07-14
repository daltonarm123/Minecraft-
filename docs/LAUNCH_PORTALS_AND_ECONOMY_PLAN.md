# Launch Portals and Economy Plan

## Launch scope

The first public gameplay modes are:

- Survival
- 1v1 Arena

No additional portal destinations should be advertised at launch.

## Spawn portal layout

At spawn, create exactly two player-facing portals:

1. `survival`
2. `duel1v1`

Use clear signage in spawn so players can identify each destination without commands.

## Admin portal setup runbook

The destination is where the admin stands when running `/portal create <name>`.

### A) Create the Survival portal

1. Stand at the first corner of the Survival portal trigger region in spawn.
2. Run `/portal begin survival`.
3. Run `/portal pos1 survival`.
4. Stand at the opposite corner of the trigger region.
5. Run `/portal pos2 survival`.
6. Travel to the Survival arrival point.
7. Run `/portal create survival`.
8. Run `/portal info survival`.

### B) Create the 1v1 portal

1. Stand at the first corner of the 1v1 portal trigger region in spawn.
2. Run `/portal begin duel1v1`.
3. Run `/portal pos1 duel1v1`.
4. Stand at the opposite corner of the trigger region.
5. Run `/portal pos2 duel1v1`.
6. Travel to the 1v1 arrival point.
7. Run `/portal create duel1v1`.
8. Run `/portal info duel1v1`.

### C) Validate and lock

1. Run `/portal list` and confirm only the intended launch portals are enabled.
2. Test both portals with an operator and a normal player.
3. Restart server and re-test both portals.
4. Keep non-launch portals disabled until new modes are announced.

## Launch economy model (server-only)

## Currency

Use one server currency at launch:

- Name: Server Credits
- Symbol: SC

Single-currency launch keeps the system simple for players and staff.

## How players get currency

1. Player market sales (primary source)
2. Server activities and rewards (quests/events/milestones)
3. Optional paid top-up packages

## How currency is spent (sinks)

1. Market purchases
2. Listing fee for posted items
3. Transaction tax on completed sales
4. Optional utility sinks (cosmetic unlocks, convenience services)

Fees and taxes are important to control inflation from both gameplay and paid top-ups.

## Monetization boundaries

Paid top-ups are allowed only if they do not create direct combat power.

Allowed:

- Currency for market trading and non-combat utility
- Cosmetic and identity-related spending

Not allowed:

- Direct PvP stat advantages
- Exclusive power progression locked behind payment
- Real-money cash-out or currency conversion outside the server

## Anti-abuse and fairness controls

1. Daily transfer cap per account age tier
2. New-account trade cooldown before full market access
3. Listing price floors and ceilings by item category
4. Velocity alerts for suspicious wallet spikes
5. Full transaction audit log with actor IDs and timestamps
6. Staff tooling to freeze wallets and reverse fraudulent transfers

## Data and implementation checklist

Implement before public market launch:

1. Wallet ledger (append-only, no silent balance edits)
2. Market listing table (seller, item, quantity, ask price, expiration)
3. Trade execution records (buyer, seller, gross, fee, tax, net)
4. Admin audit views for disputes and rollback operations
5. API endpoints for wallet, market list/create/cancel/buy, and admin review
6. Server commands for basic staff moderation actions

## Suggested launch defaults

- Listing fee: flat fee in SC per listing
- Sales tax: percentage of final sale
- Minimum listing duration: 15 minutes
- Maximum active listings per player: conservative at launch

Tune these values weekly using live economy telemetry.

## Launch communication checklist

Before enabling paid currency top-ups:

1. Publish pricing and package contents
2. Publish refund and support policy
3. Publish economy rules and enforcement actions
4. Train staff on wallet/trade dispute workflow
