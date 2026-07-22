from __future__ import annotations

import os
from collections.abc import Callable
from pathlib import Path
from urllib.parse import parse_qsl, urlencode, urlparse, urlunparse
from uuid import UUID

import httpx
from fastapi import APIRouter, Cookie, Depends, HTTPException, Query, Response, status
from fastapi.responses import HTMLResponse, RedirectResponse

from .models import LeaderboardEntry, PlayerRecord
from .portal_models import (
    AchievementState,
    CheckoutResponse,
    DiscordIdentity,
    FeatureProposal,
    FeatureProposalCreate,
    LinkChallenge,
    LinkConfirmation,
    MembershipRecord,
    MembershipUpdate,
    MinecraftLink,
    PlayerAchievement,
    PortalMe,
    SupportTicket,
    SupportTicketCreate,
    TicketStatusUpdate,
)
from .portal_store import PortalStore
from .store import NetworkStore

SESSION_COOKIE = "servercore_session"
DISCORD_AUTHORIZE_URL = "https://discord.com/oauth2/authorize"
DISCORD_TOKEN_URL = "https://discord.com/api/oauth2/token"
DISCORD_USER_URL = "https://discord.com/api/users/@me"
PORTAL_PAGE = Path(__file__).with_name("portal.html")


def _setting(name: str) -> str:
    return os.getenv(name, "").strip()


def _cookie_secure() -> bool:
    return _setting("SERVERCORE_COOKIE_SECURE").lower() in {"1", "true", "yes", "on"}


def _checkout_url(base_url: str, identity: DiscordIdentity) -> str:
    parsed = urlparse(base_url)
    query = dict(parse_qsl(parsed.query, keep_blank_values=True))
    query.setdefault("client_reference_id", identity.discord_user_id)
    query.setdefault("discord_username", identity.global_name or identity.username)
    return urlunparse(parsed._replace(query=urlencode(query)))


def achievements_for(player: PlayerRecord | None) -> list[PlayerAchievement]:
    if player is None:
        return []

    definitions = (
        ("first-win", "First Victory", "Win your first duel.", player.wins, 1),
        ("duel-veteran", "Duel Veteran", "Win 10 duels.", player.wins, 10),
        ("monster-hunter", "Monster Hunter", "Record 100 kills.", player.kills, 100),
        ("ranked-climber", "Ranked Climber", "Reach a 1,200 duel rating.", player.rating, 1200),
    )
    return [
        PlayerAchievement(
            achievement_id=achievement_id,
            title=title,
            description=description,
            state=AchievementState.UNLOCKED if progress >= target else AchievementState.LOCKED,
            progress=min(progress, target),
            target=target,
        )
        for achievement_id, title, description, progress, target in definitions
    ]


def create_portal_router(
    network_store: NetworkStore,
    portal_store: PortalStore,
    write_key_dependency: Callable[..., None],
) -> APIRouter:
    router = APIRouter()

    def require_user(
        session_token: str | None = Cookie(default=None, alias=SESSION_COOKIE),
    ) -> DiscordIdentity:
        identity = portal_store.get_session(session_token)
        if identity is None:
            raise HTTPException(status_code=401, detail="Discord login required")
        return identity

    @router.get("/portal", response_class=HTMLResponse, include_in_schema=False)
    def portal_page() -> HTMLResponse:
        return HTMLResponse(PORTAL_PAGE.read_text(encoding="utf-8"))

    @router.get("/auth/discord/login", include_in_schema=False)
    def discord_login() -> RedirectResponse:
        client_id = _setting("DISCORD_CLIENT_ID")
        redirect_uri = _setting("DISCORD_REDIRECT_URI")
        if not client_id or not redirect_uri:
            raise HTTPException(status_code=503, detail="Discord OAuth is not configured")
        state_value = portal_store.create_oauth_state()
        query = urlencode(
            {
                "client_id": client_id,
                "response_type": "code",
                "redirect_uri": redirect_uri,
                "scope": "identify",
                "state": state_value,
                "prompt": "consent",
            }
        )
        return RedirectResponse(f"{DISCORD_AUTHORIZE_URL}?{query}")

    @router.get("/auth/discord/callback", include_in_schema=False)
    def discord_callback(
        code: str,
        state_value: str = Query(alias="state"),
    ) -> RedirectResponse:
        if not portal_store.consume_oauth_state(state_value):
            raise HTTPException(status_code=400, detail="Discord OAuth state is invalid or expired")

        client_id = _setting("DISCORD_CLIENT_ID")
        client_secret = _setting("DISCORD_CLIENT_SECRET")
        redirect_uri = _setting("DISCORD_REDIRECT_URI")
        if not client_id or not client_secret or not redirect_uri:
            raise HTTPException(status_code=503, detail="Discord OAuth is not configured")

        try:
            with httpx.Client(timeout=10.0) as client:
                token_response = client.post(
                    DISCORD_TOKEN_URL,
                    data={
                        "client_id": client_id,
                        "client_secret": client_secret,
                        "grant_type": "authorization_code",
                        "code": code,
                        "redirect_uri": redirect_uri,
                    },
                    headers={"Content-Type": "application/x-www-form-urlencoded"},
                )
                token_response.raise_for_status()
                access_token = token_response.json()["access_token"]
                user_response = client.get(
                    DISCORD_USER_URL,
                    headers={"Authorization": f"Bearer {access_token}"},
                )
                user_response.raise_for_status()
                user_data = user_response.json()
        except (httpx.HTTPError, KeyError, ValueError) as exception:
            raise HTTPException(status_code=502, detail="Discord login could not be completed") from exception

        discord_id = str(user_data["id"])
        avatar_hash = user_data.get("avatar")
        identity = DiscordIdentity(
            discord_user_id=discord_id,
            username=str(user_data["username"]),
            global_name=user_data.get("global_name"),
            avatar_url=(
                f"https://cdn.discordapp.com/avatars/{discord_id}/{avatar_hash}.png"
                if avatar_hash
                else None
            ),
        )
        session_token = portal_store.create_session(identity)
        response = RedirectResponse("/portal", status_code=status.HTTP_303_SEE_OTHER)
        response.set_cookie(
            SESSION_COOKIE,
            session_token,
            max_age=7 * 24 * 60 * 60,
            httponly=True,
            secure=_cookie_secure(),
            samesite="lax",
            path="/",
        )
        return response

    @router.post("/auth/logout", status_code=status.HTTP_204_NO_CONTENT)
    def logout(
        session_token: str | None = Cookie(default=None, alias=SESSION_COOKIE),
    ) -> Response:
        portal_store.delete_session(session_token)
        response = Response(status_code=status.HTTP_204_NO_CONTENT)
        response.delete_cookie(SESSION_COOKIE, path="/")
        return response

    @router.get("/api/portal/me", response_model=PortalMe)
    def portal_me(identity: DiscordIdentity = Depends(require_user)) -> PortalMe:
        link = portal_store.get_link(identity.discord_user_id)
        player = network_store.get_player(link.player_id) if link else None
        return PortalMe(
            discord=identity,
            minecraft_link=link,
            player=None if player is None else player.model_dump(mode="json"),
            achievements=achievements_for(player),
            membership=portal_store.get_membership(identity.discord_user_id),
        )

    @router.get("/api/portal/leaderboard", response_model=list[LeaderboardEntry])
    def portal_leaderboard(limit: int = Query(default=25, ge=1, le=100)) -> list[LeaderboardEntry]:
        return network_store.leaderboard(limit)

    @router.get(
        "/api/portal/players/{player_id}/achievements",
        response_model=list[PlayerAchievement],
    )
    def player_achievements(player_id: UUID) -> list[PlayerAchievement]:
        player = network_store.get_player(player_id)
        if player is None:
            raise HTTPException(status_code=404, detail="Player not found")
        return achievements_for(player)

    @router.post("/api/portal/link-challenges", response_model=LinkChallenge)
    def create_link_challenge(
        identity: DiscordIdentity = Depends(require_user),
    ) -> LinkChallenge:
        return portal_store.create_link_challenge(identity.discord_user_id)

    @router.post(
        "/api/portal/link-confirmations",
        response_model=MinecraftLink,
        dependencies=[Depends(write_key_dependency)],
    )
    def confirm_link(payload: LinkConfirmation) -> MinecraftLink:
        try:
            return portal_store.confirm_link(payload.code, payload.player_id, payload.username)
        except ValueError as exception:
            raise HTTPException(status_code=400, detail=str(exception)) from exception

    @router.get("/api/portal/membership", response_model=MembershipRecord)
    def membership(
        identity: DiscordIdentity = Depends(require_user),
    ) -> MembershipRecord:
        return portal_store.get_membership(identity.discord_user_id)

    @router.post("/api/portal/membership/checkout", response_model=CheckoutResponse)
    def membership_checkout(
        identity: DiscordIdentity = Depends(require_user),
    ) -> CheckoutResponse:
        checkout_base = _setting("SERVERCORE_MEMBERSHIP_CHECKOUT_URL")
        if not checkout_base:
            raise HTTPException(status_code=503, detail="Membership checkout is not configured")
        return CheckoutResponse(checkout_url=_checkout_url(checkout_base, identity))

    @router.put(
        "/api/portal/admin/memberships/{discord_user_id}",
        response_model=MembershipRecord,
        dependencies=[Depends(write_key_dependency)],
    )
    def update_membership(
        discord_user_id: str,
        payload: MembershipUpdate,
    ) -> MembershipRecord:
        return portal_store.update_membership(discord_user_id, payload)

    @router.get("/api/portal/tickets", response_model=list[SupportTicket])
    def my_tickets(
        identity: DiscordIdentity = Depends(require_user),
    ) -> list[SupportTicket]:
        return portal_store.list_tickets(discord_user_id=identity.discord_user_id)

    @router.post(
        "/api/portal/tickets",
        response_model=SupportTicket,
        status_code=status.HTTP_201_CREATED,
    )
    def create_ticket(
        payload: SupportTicketCreate,
        identity: DiscordIdentity = Depends(require_user),
    ) -> SupportTicket:
        return portal_store.create_ticket(identity, payload)

    @router.get(
        "/api/portal/admin/tickets",
        response_model=list[SupportTicket],
        dependencies=[Depends(write_key_dependency)],
    )
    def all_tickets() -> list[SupportTicket]:
        return portal_store.list_tickets()

    @router.patch(
        "/api/portal/admin/tickets/{ticket_id}",
        response_model=SupportTicket,
        dependencies=[Depends(write_key_dependency)],
    )
    def update_ticket(ticket_id: UUID, payload: TicketStatusUpdate) -> SupportTicket:
        try:
            return portal_store.update_ticket_status(ticket_id, payload.status)
        except KeyError as exception:
            raise HTTPException(status_code=404, detail=str(exception)) from exception

    @router.get("/api/portal/features", response_model=list[FeatureProposal])
    def features() -> list[FeatureProposal]:
        return portal_store.list_features()

    @router.post(
        "/api/portal/admin/features",
        response_model=FeatureProposal,
        status_code=status.HTTP_201_CREATED,
        dependencies=[Depends(write_key_dependency)],
    )
    def create_feature(payload: FeatureProposalCreate) -> FeatureProposal:
        return portal_store.create_feature(payload)

    @router.post("/api/portal/features/{proposal_id}/vote", response_model=FeatureProposal)
    def vote_feature(
        proposal_id: UUID,
        identity: DiscordIdentity = Depends(require_user),
    ) -> FeatureProposal:
        try:
            return portal_store.vote_feature(proposal_id, identity.discord_user_id)
        except KeyError as exception:
            raise HTTPException(status_code=404, detail=str(exception)) from exception

    return router
