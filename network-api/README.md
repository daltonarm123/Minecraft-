# ServerCore Network API

Internal FastAPI service for player profiles, rankings, duel records, and audit events.

## Run locally

```bash
python -m venv .venv
source .venv/bin/activate
pip install -e '.[test]'
uvicorn app.main:app --reload
```

## Test

```bash
pytest
```

## Authentication

Set `SERVERCORE_API_KEY` in production. Write endpoints then require the same value in the `X-ServerCore-Key` header. Read endpoints are currently public so a future website and Discord bot can display rankings.

## Current storage limitation

The current implementation uses a bounded in-memory store to establish and test the API contract. Data is lost on restart. Replace `NetworkStore` with PostgreSQL before a public launch.
