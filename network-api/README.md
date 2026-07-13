# ServerCore Network API

Internal FastAPI service for player profiles, rankings, duel records, cosmetics, and audit events.

## Run locally

```bash
python -m venv .venv
source .venv/bin/activate
pip install -e '.[test]'
uvicorn app.main:app --reload
```

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

Cosmetic write operations are server-authoritative. Clients may read the public catalog and wardrobe data, but grants, revocations, definition changes, and equipment changes require the write API key when one is configured.

## Authentication

Set `SERVERCORE_API_KEY` in production. Write endpoints then require the same value in the `X-ServerCore-Key` header. Read endpoints remain public for the website and Discord bot.

## Current storage limitation

The API still uses a bounded in-memory store to establish and test the contract. API data is lost on restart. Replace `NetworkStore` with PostgreSQL before a public launch. The Minecraft core currently has separate JSON prototype persistence; these stores must be unified before production.
