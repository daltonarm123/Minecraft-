from __future__ import annotations

from threading import RLock
from uuid import UUID

from .models import (
    AuditEventCreate,
    LeaderboardEntry,
    MatchCreate,
    PlayerRecord,
    PlayerUpsert,
)


class NetworkStore:
    """Thread-safe prototype store; replace with PostgreSQL before public launch."""

    def __init__(self, *, maximum_events: int = 10_000) -> None:
        if maximum_events < 1:
            raise ValueError("maximum_events must be positive")
        self._lock = RLock()
        self._players: dict[UUID, PlayerRecord] = {}
        self._matches: dict[UUID, MatchCreate] = {}
        self._events: list[AuditEventCreate] = []
        self._maximum_events = maximum_events

    def upsert_player(self, player: PlayerUpsert) -> PlayerRecord:
        with self._lock:
            existing = self._players.get(player.player_id)
            record = PlayerRecord(
                **player.model_dump(),
                first_seen=existing.first_seen if existing else player.last_seen,
            )
            self._players[player.player_id] = record
            return record.model_copy(deep=True)

    def get_player(self, player_id: UUID) -> PlayerRecord | None:
        with self._lock:
            player = self._players.get(player_id)
            return None if player is None else player.model_copy(deep=True)

    def leaderboard(self, limit: int) -> list[LeaderboardEntry]:
        if limit < 1 or limit > 1000:
            raise ValueError("limit must be between 1 and 1000")
        with self._lock:
            ordered = sorted(
                self._players.values(),
                key=lambda player: (-player.rating, -player.wins, player.username.casefold()),
            )[:limit]
            return [
                LeaderboardEntry(
                    rank=index,
                    player_id=player.player_id,
                    username=player.username,
                    rating=player.rating,
                    wins=player.wins,
                    losses=player.losses,
                    win_rate=player.win_rate,
                )
                for index, player in enumerate(ordered, start=1)
            ]

    def save_match(self, match: MatchCreate) -> MatchCreate:
        with self._lock:
            self._matches[match.match_id] = match.model_copy(deep=True)
            return match.model_copy(deep=True)

    def get_match(self, match_id: UUID) -> MatchCreate | None:
        with self._lock:
            match = self._matches.get(match_id)
            return None if match is None else match.model_copy(deep=True)

    def add_event(self, event: AuditEventCreate) -> AuditEventCreate:
        with self._lock:
            self._events.insert(0, event.model_copy(deep=True))
            del self._events[self._maximum_events :]
            return event.model_copy(deep=True)

    def recent_events(self, limit: int) -> list[AuditEventCreate]:
        if limit < 1 or limit > 1000:
            raise ValueError("limit must be between 1 and 1000")
        with self._lock:
            return [event.model_copy(deep=True) for event in self._events[:limit]]


store = NetworkStore()
