# ServerCore Discord Bot

Community commands that read player and duel data from the ServerCore Network API.

## Commands

- `!mchelp`
- `!mcstats <MinecraftName>`
- `!mctop [1-25]`
- `!mcstatus`

## Required environment variables

- `DISCORD_TOKEN` — Discord bot token.
- `SERVERCORE_API_URL` — API base URL, such as `http://network-api:8000`.
- `SERVERCORE_API_KEY` — optional until write commands are added.
- `LOG_LEVEL` — optional; defaults to `INFO`.

The bot requires Discord's **Message Content Intent** because this prototype uses prefix commands. Slash commands can replace prefix commands later.

## Run

```bash
pip install -e '.[test]'
python -m app.bot
```

## Test

```bash
pytest -q
```
