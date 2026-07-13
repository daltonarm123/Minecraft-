from __future__ import annotations

import logging
import os

import discord
from discord.ext import commands

from .api_client import ServerCoreApiClient, ServerCoreApiError
from .formatting import format_health, format_leaderboard, format_player_stats

LOGGER = logging.getLogger("servercore.discord")


def create_bot(api_client: ServerCoreApiClient) -> commands.Bot:
    intents = discord.Intents.default()
    intents.message_content = True
    bot = commands.Bot(command_prefix="!", intents=intents, help_command=None)

    @bot.event
    async def on_ready() -> None:
        LOGGER.info("Logged in as %s", bot.user)
        await bot.change_presence(
            activity=discord.Game(name="All The Mods 11 | !mchelp")
        )

    @bot.command(name="mchelp")
    async def minecraft_help(context: commands.Context[commands.Bot]) -> None:
        await context.send(
            "**ServerCore Commands**\n"
            "`!mcstats <MinecraftName>` — show a player's stats\n"
            "`!mctop [1-25]` — show the ranked leaderboard\n"
            "`!mcstatus` — check the ServerCore API"
        )

    @bot.command(name="mcstats")
    async def minecraft_stats(
        context: commands.Context[commands.Bot],
        *,
        username: str = "",
    ) -> None:
        if not username.strip():
            await context.send("Usage: `!mcstats <MinecraftName>`")
            return
        async with context.typing():
            try:
                player = await api_client.player_by_username(username)
            except (ServerCoreApiError, ValueError) as exception:
                await context.send(f"Unable to load stats: {exception}")
                return
        await context.send(format_player_stats(player))

    @bot.command(name="mctop")
    async def minecraft_top(
        context: commands.Context[commands.Bot],
        limit: int = 10,
    ) -> None:
        if limit < 1 or limit > 25:
            await context.send("Leaderboard size must be between 1 and 25.")
            return
        async with context.typing():
            try:
                entries = await api_client.leaderboard(limit)
            except (ServerCoreApiError, ValueError) as exception:
                await context.send(f"Unable to load the leaderboard: {exception}")
                return
        await context.send(format_leaderboard(entries))

    @bot.command(name="mcstatus")
    async def minecraft_status(context: commands.Context[commands.Bot]) -> None:
        async with context.typing():
            try:
                health = await api_client.health()
            except ServerCoreApiError as exception:
                await context.send(f"ServerCore is unavailable: {exception}")
                return
        await context.send(format_health(health))

    @bot.event
    async def on_command_error(
        context: commands.Context[commands.Bot],
        error: commands.CommandError,
    ) -> None:
        if isinstance(error, commands.CommandNotFound):
            return
        if isinstance(error, commands.BadArgument):
            await context.send("That command contains an invalid value. Try `!mchelp`.")
            return
        LOGGER.exception("Discord command failed", exc_info=error)
        await context.send("That command failed unexpectedly. The error has been logged.")

    return bot


def main() -> None:
    logging.basicConfig(
        level=os.getenv("LOG_LEVEL", "INFO").upper(),
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )
    token = os.getenv("DISCORD_TOKEN", "").strip()
    api_url = os.getenv("SERVERCORE_API_URL", "http://localhost:8000").strip()
    api_key = os.getenv("SERVERCORE_API_KEY", "").strip()
    if not token:
        raise RuntimeError("DISCORD_TOKEN is required")
    bot = create_bot(ServerCoreApiClient(api_url, api_key))
    bot.run(token, log_handler=None)


if __name__ == "__main__":
    main()
