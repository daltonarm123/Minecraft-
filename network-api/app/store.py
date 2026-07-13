from __future__ import annotations

from threading import RLock
from uuid import UUID

from .models import (
    AuditEventCreate,
    CosmeticCategory,
    CosmeticDefinition,
    CosmeticPlayerState,
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
        self._cosmetics: dict[str, CosmeticDefinition] = {}
        self._wardrobes: dict[UUID, CosmeticPlayerState] = {}
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

    def get_player_by_username(self, username: str) -> PlayerRecord | None:
        normalized = username.strip().casefold()
        if not normalized:
            return None
        with self._lock:
            player = next(
                (
                    candidate
                    for candidate in self._players.values()
                    if candidate.username.casefold() == normalized
                ),
                None,
            )
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

    def upsert_cosmetic(self, definition: CosmeticDefinition) -> CosmeticDefinition:
        with self._lock:
            self._cosmetics[definition.cosmetic_id] = definition.model_copy(deep=True)
            if not definition.enabled:
                self._unequip_cosmetic_from_all_players(definition.cosmetic_id)
            return definition.model_copy(deep=True)

    def get_cosmetic(self, cosmetic_id: str) -> CosmeticDefinition | None:
        with self._lock:
            definition = self._cosmetics.get(self._normalize_cosmetic_id(cosmetic_id))
            return None if definition is None else definition.model_copy(deep=True)

    def list_cosmetics(self, *, include_disabled: bool = False) -> list[CosmeticDefinition]:
        with self._lock:
            definitions = sorted(
                self._cosmetics.values(),
                key=lambda definition: (definition.category.value, definition.cosmetic_id),
            )
            return [
                definition.model_copy(deep=True)
                for definition in definitions
                if include_disabled or definition.enabled
            ]

    def get_wardrobe(self, player_id: UUID) -> CosmeticPlayerState:
        with self._lock:
            state = self._wardrobes.get(player_id)
            if state is None:
                state = CosmeticPlayerState(player_id=player_id)
            return state.model_copy(deep=True)

    def grant_cosmetic(self, player_id: UUID, cosmetic_id: str) -> CosmeticPlayerState:
        normalized = self._normalize_cosmetic_id(cosmetic_id)
        with self._lock:
            if normalized not in self._cosmetics:
                raise KeyError(f"Cosmetic not found: {normalized}")
            current = self._wardrobes.get(player_id, CosmeticPlayerState(player_id=player_id))
            owned = set(current.owned_cosmetic_ids)
            owned.add(normalized)
            updated = CosmeticPlayerState(
                player_id=player_id,
                owned_cosmetic_ids=owned,
                equipped_by_category=dict(current.equipped_by_category),
            )
            self._wardrobes[player_id] = updated
            return updated.model_copy(deep=True)

    def revoke_cosmetic(self, player_id: UUID, cosmetic_id: str) -> CosmeticPlayerState:
        normalized = self._normalize_cosmetic_id(cosmetic_id)
        with self._lock:
            current = self._wardrobes.get(player_id, CosmeticPlayerState(player_id=player_id))
            owned = set(current.owned_cosmetic_ids)
            owned.discard(normalized)
            equipped = {
                category: equipped_id
                for category, equipped_id in current.equipped_by_category.items()
                if equipped_id != normalized
            }
            updated = CosmeticPlayerState(
                player_id=player_id,
                owned_cosmetic_ids=owned,
                equipped_by_category=equipped,
            )
            self._wardrobes[player_id] = updated
            return updated.model_copy(deep=True)

    def equip_cosmetic(self, player_id: UUID, cosmetic_id: str) -> CosmeticPlayerState:
        normalized = self._normalize_cosmetic_id(cosmetic_id)
        with self._lock:
            definition = self._cosmetics.get(normalized)
            if definition is None:
                raise KeyError(f"Cosmetic not found: {normalized}")
            if not definition.enabled:
                raise ValueError(f"Cosmetic is disabled: {normalized}")
            current = self._wardrobes.get(player_id, CosmeticPlayerState(player_id=player_id))
            if normalized not in current.owned_cosmetic_ids:
                raise ValueError(f"Player does not own cosmetic: {normalized}")
            equipped = dict(current.equipped_by_category)
            equipped[definition.category] = normalized
            updated = CosmeticPlayerState(
                player_id=player_id,
                owned_cosmetic_ids=set(current.owned_cosmetic_ids),
                equipped_by_category=equipped,
            )
            self._wardrobes[player_id] = updated
            return updated.model_copy(deep=True)

    def unequip_category(
        self,
        player_id: UUID,
        category: CosmeticCategory,
    ) -> CosmeticPlayerState:
        with self._lock:
            current = self._wardrobes.get(player_id, CosmeticPlayerState(player_id=player_id))
            equipped = dict(current.equipped_by_category)
            equipped.pop(category, None)
            updated = CosmeticPlayerState(
                player_id=player_id,
                owned_cosmetic_ids=set(current.owned_cosmetic_ids),
                equipped_by_category=equipped,
            )
            self._wardrobes[player_id] = updated
            return updated.model_copy(deep=True)

    def _unequip_cosmetic_from_all_players(self, cosmetic_id: str) -> None:
        for player_id, current in list(self._wardrobes.items()):
            equipped = {
                category: equipped_id
                for category, equipped_id in current.equipped_by_category.items()
                if equipped_id != cosmetic_id
            }
            if equipped != current.equipped_by_category:
                self._wardrobes[player_id] = CosmeticPlayerState(
                    player_id=player_id,
                    owned_cosmetic_ids=set(current.owned_cosmetic_ids),
                    equipped_by_category=equipped,
                )

    @staticmethod
    def _normalize_cosmetic_id(cosmetic_id: str) -> str:
        normalized = cosmetic_id.strip().lower()
        if not normalized:
            raise ValueError("cosmetic_id must not be blank")
        return normalized


store = NetworkStore()
