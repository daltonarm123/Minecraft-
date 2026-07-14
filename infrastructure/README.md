# ServerCore Infrastructure

This folder starts the server-side API component. It does **not** start Minecraft or install the ATM11 server pack.

## Start API

```bash
cd infrastructure
cp .env.example .env
docker compose up --build
```

- Network API: `http://localhost:8000`
- API documentation: `http://localhost:8000/docs`

## Before public deployment

- Replace the in-memory API store with PostgreSQL.
- Use a real secret manager for `SERVERCORE_API_KEY`.
- Put the API behind HTTPS.
- Restrict CORS to approved production origins.
- Add monitoring, backups, rate limits, and log retention.
- Deploy the ATM11 Minecraft server separately and install the built ServerCore mod JAR in its `mods` folder.
