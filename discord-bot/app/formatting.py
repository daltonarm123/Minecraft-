from __future__ import annotations

from typing import Any


def format_player_stats(player: dict[str, Any]) -> str:
    username = str(player.get("username", "Unknown"))
    rating = int(player.get("rating", 0))
    wins = int(player.get("wins", 0))
    losses = int(player.get("losses", 0))
    kills = int(player.get("kills", 0))
    deaths = int(player.get("deaths", 0))
    matches = wins + losses
    win_rate = 0.0 if matches == 0 else (wins / matches) * 100
    ratio = float(kills) if deaths == 0 else kills / deaths
    return (
        f"**{username} — Minecraft Stats**\n"
        f"Rating: **{rating:,}**\n"
        f"Wins/Losses: **{wins:,} / {losses:,}** ({win_rate:.1f}% win rate)\n"
        f"Kills/Deaths: **{kills:,} / {deaths:,}** ({ratio:.2f} K/D)"
    )


def format_leaderboard(entries: list[dict[str, Any]]) -> str:
    if not entries:
        return "No ranked players have been recorded yet."
    lines = ["**ServerCore Ranked Leaderboard**"]
    for fallback_rank, entry in enumerate(entries, start=1):
        rank = int(entry.get("rank", fallback_rank))
        username = str(entry.get("username", "Unknown"))
        rating = int(entry.get("rating", 0))
        wins = int(entry.get("wins", 0))
        losses = int(entry.get("losses", 0))
        lines.append(f"`#{rank:>2}` **{username}** — {rating:,} MMR ({wins}-{losses})")
    return "\n".join(lines)


def format_health(health: dict[str, Any]) -> str:
    state = str(health.get("status", "unknown"))
    version = str(health.get("version", "unknown"))
    authentication = "enabled" if health.get("authentication_enabled") else "disabled"
    return (
        f"**ServerCore Status**\n"
        f"API: **{state}**\n"
        f"Version: **{version}**\n"
        f"Write authentication: **{authentication}**"
    )
