from __future__ import annotations

from datetime import datetime, timezone
from enum import StrEnum
from uuid import UUID, uuid4

from pydantic import BaseModel, Field, field_validator, model_validator


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


class DuelMode(StrEnum):
    CASUAL = "CASUAL"
    RANKED = "RANKED"


class CosmeticCategory(StrEnum):
    OUTFIT = "OUTFIT"
    HEAD = "HEAD"
    BACK = "BACK"
    SHOULDER = "SHOULDER"
    WEAPON = "WEAPON"
    TOOL = "TOOL"
    TRAIL = "TRAIL"
    FOOTSTEP = "FOOTSTEP"
    SPAWN_EFFECT = "SPAWN_EFFECT"
    TELEPORT_EFFECT = "TELEPORT_EFFECT"
    VICTORY_POSE = "VICTORY_POSE"
    EMOTE = "EMOTE"
    NAME_COLOR = "NAME_COLOR"
    TITLE = "TITLE"
    NAMEPLATE = "NAMEPLATE"
    PET = "PET"


class CosmeticRarity(StrEnum):
    COMMON = "COMMON"
    UNCOMMON = "UNCOMMON"
    RARE = "RARE"
    EPIC = "EPIC"
    LEGENDARY = "LEGENDARY"
    MYTHIC = "MYTHIC"
    EVENT = "EVENT"


class CosmeticUnlockSource(StrEnum):
    DEFAULT = "DEFAULT"
    ACHIEVEMENT = "ACHIEVEMENT"
    QUEST = "QUEST"
    SEASON = "SEASON"
    EVENT = "EVENT"
    BOSS_DROP = "BOSS_DROP"
    BLUEPRINT = "BLUEPRINT"
    FACTION = "FACTION"
    FOUNDER = "FOUNDER"
    SUPPORTER = "SUPPORTER"
    STAFF = "STAFF"


class PlayerUpsert(BaseModel):
    player_id: UUID
    username: str = Field(min_length=1, max_length=32)
    last_seen: datetime = Field(default_factory=utc_now)
    wins: int = Field(default=0, ge=0)
    losses: int = Field(default=0, ge=0)
    kills: int = Field(default=0, ge=0)
    deaths: int = Field(default=0, ge=0)
    rating: int = Field(default=1000, ge=0, le=100_000)


class PlayerRecord(PlayerUpsert):
    first_seen: datetime = Field(default_factory=utc_now)

    @property
    def matches_played(self) -> int:
        return self.wins + self.losses

    @property
    def win_rate(self) -> float:
        return 0.0 if self.matches_played == 0 else self.wins / self.matches_played


class LeaderboardEntry(BaseModel):
    rank: int = Field(ge=1)
    player_id: UUID
    username: str
    rating: int
    wins: int
    losses: int
    win_rate: float


class MatchCreate(BaseModel):
    match_id: UUID = Field(default_factory=uuid4)
    first_player_id: UUID
    second_player_id: UUID
    mode: DuelMode
    arena_id: str = Field(min_length=1, max_length=64)
    started_at: datetime = Field(default_factory=utc_now)
    completed_at: datetime | None = None
    winner_id: UUID | None = None
    loser_id: UUID | None = None

    @model_validator(mode="after")
    def validate_players_and_result(self) -> MatchCreate:
        if self.first_player_id == self.second_player_id:
            raise ValueError("players must be different")
        participants = {self.first_player_id, self.second_player_id}
        if self.winner_id is not None and self.winner_id not in participants:
            raise ValueError("winner must be a participant")
        if self.loser_id is not None and self.loser_id not in participants:
            raise ValueError("loser must be a participant")
        if (self.winner_id is None) != (self.loser_id is None):
            raise ValueError("winner and loser must be supplied together")
        if self.winner_id is not None and self.winner_id == self.loser_id:
            raise ValueError("winner and loser must be different")
        if self.completed_at is not None and self.completed_at < self.started_at:
            raise ValueError("completed_at must not be before started_at")
        return self


class AuditEventCreate(BaseModel):
    event_id: UUID = Field(default_factory=uuid4)
    event_type: str = Field(min_length=1, max_length=64)
    occurred_at: datetime = Field(default_factory=utc_now)
    actor_id: UUID | None = None
    actor_name: str = Field(default="system", max_length=64)
    message: str = Field(default="", max_length=2000)
    attributes: dict[str, str] = Field(default_factory=dict)


class CosmeticDefinition(BaseModel):
    cosmetic_id: str = Field(min_length=1, max_length=64)
    display_name: str = Field(min_length=1, max_length=80)
    category: CosmeticCategory
    rarity: CosmeticRarity
    unlock_source: CosmeticUnlockSource
    asset_id: str = Field(min_length=3, max_length=160)
    description: str = Field(default="", max_length=500)
    enabled: bool = True
    tags: set[str] = Field(default_factory=set)

    @field_validator("cosmetic_id")
    @classmethod
    def normalize_cosmetic_id(cls, value: str) -> str:
        normalized = value.strip().lower()
        if not normalized or any(
            character not in "abcdefghijklmnopqrstuvwxyz0123456789_.-"
            for character in normalized
        ):
            raise ValueError("cosmetic_id contains invalid characters")
        return normalized

    @field_validator("asset_id")
    @classmethod
    def validate_asset_id(cls, value: str) -> str:
        normalized = value.strip().lower()
        if normalized.count(":") != 1:
            raise ValueError("asset_id must use namespace:path")
        namespace, path = normalized.split(":", 1)
        allowed_namespace = "abcdefghijklmnopqrstuvwxyz0123456789_.-"
        allowed_path = allowed_namespace + "/"
        if not namespace or not path:
            raise ValueError("asset_id must use namespace:path")
        if any(character not in allowed_namespace for character in namespace):
            raise ValueError("asset_id namespace contains invalid characters")
        if any(character not in allowed_path for character in path):
            raise ValueError("asset_id path contains invalid characters")
        return normalized

    @field_validator("tags")
    @classmethod
    def normalize_tags(cls, values: set[str]) -> set[str]:
        normalized: set[str] = set()
        for value in values:
            tag = value.strip().lower()
            if not tag or len(tag) > 64:
                raise ValueError("tags must contain 1 to 64 characters")
            normalized.add(tag)
        return normalized


class CosmeticPlayerState(BaseModel):
    player_id: UUID
    owned_cosmetic_ids: set[str] = Field(default_factory=set)
    equipped_by_category: dict[CosmeticCategory, str] = Field(default_factory=dict)


class HealthResponse(BaseModel):
    status: str
    service: str
    version: str
    authentication_enabled: bool
