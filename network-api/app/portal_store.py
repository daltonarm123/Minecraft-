from __future__ import annotations

import secrets
from datetime import datetime, timedelta, timezone
from threading import RLock
from uuid import UUID

from .portal_models import (
    DiscordIdentity,
    FeatureProposal,
    FeatureProposalCreate,
    LinkChallenge,
    MembershipRecord,
    MembershipUpdate,
    MinecraftLink,
    SupportTicket,
    SupportTicketCreate,
    TicketStatus,
)


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


class PortalStore:
    """Thread-safe MVP store for portal sessions and community workflows.

    This intentionally mirrors NetworkStore's prototype behavior. Replace it with
    PostgreSQL before accepting public subscriptions or support requests.
    """

    def __init__(self) -> None:
        self._lock = RLock()
        self._oauth_states: dict[str, datetime] = {}
        self._sessions: dict[str, tuple[DiscordIdentity, datetime]] = {}
        self._link_challenges: dict[str, LinkChallenge] = {}
        self._links_by_discord: dict[str, MinecraftLink] = {}
        self._discord_by_player: dict[UUID, str] = {}
        self._memberships: dict[str, MembershipRecord] = {}
        self._tickets: dict[UUID, SupportTicket] = {}
        self._features: dict[UUID, FeatureProposal] = {}
        self._feature_votes: dict[UUID, set[str]] = {}
        self._seed_features()

    def create_oauth_state(self, *, ttl: timedelta = timedelta(minutes=10)) -> str:
        state = secrets.token_urlsafe(24)
        with self._lock:
            self._oauth_states[state] = utc_now() + ttl
        return state

    def consume_oauth_state(self, state: str) -> bool:
        with self._lock:
            expires_at = self._oauth_states.pop(state, None)
            return expires_at is not None and expires_at > utc_now()

    def create_session(
        self,
        identity: DiscordIdentity,
        *,
        ttl: timedelta = timedelta(days=7),
    ) -> str:
        token = secrets.token_urlsafe(32)
        with self._lock:
            self._sessions[token] = (identity.model_copy(deep=True), utc_now() + ttl)
        return token

    def get_session(self, token: str | None) -> DiscordIdentity | None:
        if not token:
            return None
        with self._lock:
            session = self._sessions.get(token)
            if session is None:
                return None
            identity, expires_at = session
            if expires_at <= utc_now():
                self._sessions.pop(token, None)
                return None
            return identity.model_copy(deep=True)

    def delete_session(self, token: str | None) -> None:
        if not token:
            return
        with self._lock:
            self._sessions.pop(token, None)

    def create_link_challenge(
        self,
        discord_user_id: str,
        *,
        ttl: timedelta = timedelta(minutes=10),
    ) -> LinkChallenge:
        alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        with self._lock:
            for code, challenge in list(self._link_challenges.items()):
                if challenge.expires_at <= utc_now() or challenge.discord_user_id == discord_user_id:
                    self._link_challenges.pop(code, None)
            while True:
                code = "".join(secrets.choice(alphabet) for _ in range(6))
                if code not in self._link_challenges:
                    break
            challenge = LinkChallenge(
                code=code,
                discord_user_id=discord_user_id,
                expires_at=utc_now() + ttl,
            )
            self._link_challenges[code] = challenge
            return challenge.model_copy(deep=True)

    def confirm_link(self, code: str, player_id: UUID, username: str) -> MinecraftLink:
        normalized = code.strip().upper()
        with self._lock:
            challenge = self._link_challenges.pop(normalized, None)
            if challenge is None or challenge.expires_at <= utc_now():
                raise ValueError("Link code is invalid or expired")

            existing_discord = self._discord_by_player.get(player_id)
            if existing_discord and existing_discord != challenge.discord_user_id:
                raise ValueError("Minecraft account is already linked to another Discord account")

            old_link = self._links_by_discord.get(challenge.discord_user_id)
            if old_link is not None:
                self._discord_by_player.pop(old_link.player_id, None)

            link = MinecraftLink(
                discord_user_id=challenge.discord_user_id,
                player_id=player_id,
                username=username.strip(),
            )
            self._links_by_discord[challenge.discord_user_id] = link
            self._discord_by_player[player_id] = challenge.discord_user_id
            return link.model_copy(deep=True)

    def get_link(self, discord_user_id: str) -> MinecraftLink | None:
        with self._lock:
            link = self._links_by_discord.get(discord_user_id)
            return None if link is None else link.model_copy(deep=True)

    def get_membership(self, discord_user_id: str) -> MembershipRecord:
        with self._lock:
            membership = self._memberships.get(discord_user_id)
            if membership is None:
                membership = MembershipRecord(discord_user_id=discord_user_id)
            return membership.model_copy(deep=True)

    def update_membership(
        self,
        discord_user_id: str,
        update: MembershipUpdate,
    ) -> MembershipRecord:
        membership = MembershipRecord(
            discord_user_id=discord_user_id,
            plan=update.plan,
            status=update.status,
            current_period_end=update.current_period_end,
            updated_at=utc_now(),
        )
        with self._lock:
            self._memberships[discord_user_id] = membership
            return membership.model_copy(deep=True)

    def create_ticket(
        self,
        identity: DiscordIdentity,
        payload: SupportTicketCreate,
    ) -> SupportTicket:
        ticket = SupportTicket(
            discord_user_id=identity.discord_user_id,
            discord_username=identity.global_name or identity.username,
            subject=payload.subject.strip(),
            message=payload.message.strip(),
        )
        with self._lock:
            self._tickets[ticket.ticket_id] = ticket
            return ticket.model_copy(deep=True)

    def list_tickets(self, *, discord_user_id: str | None = None) -> list[SupportTicket]:
        with self._lock:
            tickets = list(self._tickets.values())
            if discord_user_id is not None:
                tickets = [ticket for ticket in tickets if ticket.discord_user_id == discord_user_id]
            tickets.sort(key=lambda ticket: ticket.created_at, reverse=True)
            return [ticket.model_copy(deep=True) for ticket in tickets]

    def update_ticket_status(self, ticket_id: UUID, status: TicketStatus) -> SupportTicket:
        with self._lock:
            ticket = self._tickets.get(ticket_id)
            if ticket is None:
                raise KeyError("Support ticket not found")
            updated = ticket.model_copy(update={"status": status, "updated_at": utc_now()})
            self._tickets[ticket_id] = updated
            return updated.model_copy(deep=True)

    def create_feature(self, payload: FeatureProposalCreate) -> FeatureProposal:
        proposal = FeatureProposal(
            title=payload.title.strip(),
            description=payload.description.strip(),
        )
        with self._lock:
            self._features[proposal.proposal_id] = proposal
            self._feature_votes[proposal.proposal_id] = set()
            return proposal.model_copy(deep=True)

    def list_features(self) -> list[FeatureProposal]:
        with self._lock:
            features = sorted(
                self._features.values(),
                key=lambda proposal: (-proposal.vote_count, proposal.created_at),
            )
            return [proposal.model_copy(deep=True) for proposal in features]

    def vote_feature(self, proposal_id: UUID, discord_user_id: str) -> FeatureProposal:
        with self._lock:
            proposal = self._features.get(proposal_id)
            if proposal is None:
                raise KeyError("Feature proposal not found")
            voters = self._feature_votes.setdefault(proposal_id, set())
            voters.add(discord_user_id)
            updated = proposal.model_copy(update={"vote_count": len(voters)})
            self._features[proposal_id] = updated
            return updated.model_copy(deep=True)

    def _seed_features(self) -> None:
        defaults = (
            FeatureProposalCreate(
                title="Cross-play launch",
                description="Complete and test the Bedrock bridge for console and mobile players.",
            ),
            FeatureProposalCreate(
                title="Seasonal quests",
                description="Add rotating quests with cosmetic rewards and leaderboard points.",
            ),
            FeatureProposalCreate(
                title="Player marketplace dashboard",
                description="Let members browse server market listings from the web portal.",
            ),
        )
        for payload in defaults:
            self.create_feature(payload)


store = PortalStore()
