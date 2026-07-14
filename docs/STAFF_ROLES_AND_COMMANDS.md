# Staff Roles and Commands

## Staff role nodes

Assign these permission nodes through your server permission manager.

- `servercore.role.owner`
- `servercore.role.admin`
- `servercore.role.dev`
- `servercore.role.support`
- `servercore.role.mod`

Area-specific portal gates:

- `servercore.area.dev`
- `servercore.area.admin`

Economy command nodes:

- `servercore.economy.use`
- `servercore.economy.admin`
- `servercore.economy.moderate`

## Recommended role access

- Owner: all nodes
- Admin: admin + moderate + area access
- Developer: economy use + dev area
- Support: economy use + admin area
- Moderator: economy use + moderate + admin area

## Portal setup for staff-only areas

Use permission-locked portal creation from spawn or hidden staff hubs.

### Developer test area portal

1. Select region:
   - `/portal begin dev_area`
   - `/portal pos1 dev_area`
   - `/portal pos2 dev_area`
2. Stand at the dev test destination.
3. Create with gate:
   - `/portal create dev_area servercore.area.dev`

### Admin lounge portal

1. Select region:
   - `/portal begin admin_lounge`
   - `/portal pos1 admin_lounge`
   - `/portal pos2 admin_lounge`
2. Stand at the admin lounge destination.
3. Create with gate:
   - `/portal create admin_lounge servercore.area.admin`

## New role and economy commands

Role visibility:

- `/role list`
- `/role my`
- `/role access`

Economy:

- `/economy balance`
- `/economy pay <target> <amount>`
- `/economy credit <target> <amount>`
- `/economy shop`
- `/economy shopbuy <itemId>`
- `/economy market list [limit]`
- `/economy market sell <itemKey> <itemName> <kind> <quantity> <unitPrice>`
- `/economy market buy <listingId> <quantity>`
- `/economy market cancel <listingId>`
