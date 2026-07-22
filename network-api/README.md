# ServerCore Network API

FastAPI service for player profiles, rankings, duel records, cosmetics, audit events, and the Discord-authenticated player portal.

## Run locally

```bash
python -m venv .venv
source .venv/bin/activate
pip install -e '.[test]'
uvicorn app.main:app --reload
```

Open:

- API docs: `http://localhost:8000/docs`
- Player portal: `http://localhost:8000/portal`

## Test

```bash
pytest -q
```

## Main API areas

- `/health`
- `/players`
- `/leaderboard`
- `/matches`
- `/events`
- `/cosmetics`
- `/players/{player_id}/cosmetics`
- `/api/portal/me`
- `/api/portal/leaderboard`
- `/api/portal/link-challenges`
- `/api/portal/tickets`
- `/api/portal/features`
- `/api/portal/membership`

## Discord login

Create a Discord application and configure an OAuth redirect ending in `/auth/discord/callback`.

```env
DISCORD_CLIENT_ID=
DISCORD_CLIENT_SECRET=
DISCORD_REDIRECT_URI=http://localhost:8000/auth/discord/callback
SERVERCORE_COOKIE_SECURE=false
```

Set `SERVERCORE_COOKIE_SECURE=true` behind production HTTPS.

## Membership checkout

The portal can send members to any hosted checkout page. Configure:

```env
SERVERCORE_MEMBERSHIP_CHECKOUT_URL=https://your-payment-provider.example/checkout
```

The portal appends the Discord user ID as `client_reference_id`. Your payment webhook or an admin service must call `PUT /api/portal/admin/memberships/{discord_user_id}` to activate, cancel, or mark a membership past due.

## Minecraft account linking

The web portal creates a short-lived code. The player runs:

```text
/link ABC123
```

The NeoForge adapter confirms that code with the API. Configure the Minecraft server process with:

```env
SERVERCORE_API_URL=http://network-api:8000
SERVERCORE_API_KEY=replace-with-a-long-random-secret
```

Use the same `SERVERCORE_API_KEY` on the API service.

## Server authentication

Set `SERVERCORE_API_KEY` in production. Server-authoritative and admin endpoints then require the same value in the `X-ServerCore-Key` header.

## Current storage limitation

`NetworkStore` and `PortalStore` are bounded in-memory MVP stores. Player portal sessions, account links, memberships, tickets, votes, profiles, and matches are lost on restart. Replace both stores with PostgreSQL before accepting public subscriptions or relying on this data in production.
