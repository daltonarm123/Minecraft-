# ServerCore Infrastructure

This folder starts the browser-facing platform components. It does **not** start Minecraft or install the ATM11 server pack.

## Start API and website

```bash
cd infrastructure
cp .env.example .env
docker compose up --build
```

- Website: `http://localhost:8080`
- Network API: `http://localhost:8000`
- API documentation: `http://localhost:8000/docs`

## Include the Discord bot

Set `DISCORD_TOKEN` in `.env`, then run:

```bash
docker compose --profile bot up --build
```

## Before public deployment

- Replace the in-memory API store with PostgreSQL.
- Use a real secret manager for `SERVERCORE_API_KEY` and `DISCORD_TOKEN`.
- Put the API and website behind HTTPS.
- Restrict CORS to the production website domain.
- Add monitoring, backups, rate limits, and log retention.
- Deploy the ATM11 Minecraft server separately and install the built ServerCore mod JAR in its `mods` folder.
