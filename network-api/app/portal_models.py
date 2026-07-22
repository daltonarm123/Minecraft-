from __future__ import annotations

from datetime import datetime, timezone
from enum import StrEnum
from uuid import UUID, uuid4

from pydantic import BaseModel, Field


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


class DiscordIdentity(BaseModel):
    discord_user_id: str = Field(min_length=1, max_length=32)
    username: str = Field(min_length=1, max_length=80)
    global_name: str | None = Field(default=None, max_length=80)
    avatar_url: str | None = None


class MinecraftLink(BaseModel):
    discord_user_id: str
    player_id: UUID
    username: str = Field(min_length=1, max_length=32)
    linked_at: datetime = Field(default_factory=utc_now)


class LinkChallenge(BaseModel):
    code: str = Field(min_length=6, max_length=12)
    discord_user_id: str
    expires_at: datetime


class AchievementState(StrEnum):
    LOCKED = "LOCKED"
    UNLOCKED = "UNLOCKED"


class PlayerAchievement(BaseModel):
    achievement_id: str
    title: str
    description: str
    state: AchievementState
    progress: int = Field(ge=0)
    target: int = Field(ge=1)


class MembershipStatus(StrEnum):
    NONE = "NONE"
    PENDING = "PENDING"
    ACTIVE = "ACTIVE"
    PAST_DUE = "PAST_DUE"
    CANCELED = "CANCELED"


class MembershipRecord(BaseModel):
    discord_user_id: str
    plan: str = Field(default="founder", min_length=1, max_length=64)
    status: MembershipStatus = MembershipStatus.NONE
    current_period_end: datetime | None = None
    updated_at: datetime = Field(default_factory=utc_now)


class MembershipUpdate(BaseModel):
    plan: str = Field(default="founder", min_length=1, max_length=64)
    status: MembershipStatus
    current_period_end: datetime | None = None


class CheckoutResponse(BaseModel):
    checkout_url: str


class TicketStatus(StrEnum):
    OPEN = "OPEN"
    IN_PROGRESS = "IN_PROGRESS"
    RESOLVED = "RESOLVED"
    CLOSED = "CLOSED"


class SupportTicketCreate(BaseModel):
    subject: str = Field(min_length=3, max_length=120)
    message: str = Field(min_length=10, max_length=4000)


class SupportTicket(BaseModel):
    ticket_id: UUID = Field(default_factory=uuid4)
    discord_user_id: str
    discord_username: str
    subject: str
    message: str
    status: TicketStatus = TicketStatus.OPEN
    created_at: datetime = Field(default_factory=utc_now)
    updated_at: datetime = Field(default_factory=utc_now)


class TicketStatusUpdate(BaseModel):
    status: TicketStatus


class FeatureStatus(StrEnum):
    OPEN = "OPEN"
    PLANNED = "PLANNED"
    IN_PROGRESS = "IN_PROGRESS"
    RELEASED = "RELEASED"
    DECLINED = "DECLINED"


class FeatureProposalCreate(BaseModel):
    title: str = Field(min_length=3, max_length=120)
    description: str = Field(min_length=10, max_length=1000)


class FeatureProposal(BaseModel):
    proposal_id: UUID = Field(default_factory=uuid4)
    title: str
    description: str
    status: FeatureStatus = FeatureStatus.OPEN
    vote_count: int = Field(default=0, ge=0)
    created_at: datetime = Field(default_factory=utc_now)


class LinkConfirmation(BaseModel):
    code: str = Field(min_length=6, max_length=12)
    player_id: UUID
    username: str = Field(min_length=1, max_length=32)


class PortalMe(BaseModel):
    discord: DiscordIdentity
    minecraft_link: MinecraftLink | None = None
    player: dict[str, object] | None = None
    achievements: list[PlayerAchievement] = Field(default_factory=list)
    membership: MembershipRecord
