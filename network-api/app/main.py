from __future__ import annotations

import os
from typing import Annotated
from uuid import UUID

from fastapi import Depends, FastAPI, Header, HTTPException, Query, status
from fastapi.middleware.cors import CORSMiddleware

from .models import (
    AuditEventCreate,
    HealthResponse,
    LeaderboardEntry,
    MatchCreate,
    PlayerRecord,
    PlayerUpsert,
)
from .store import NetworkStore, store as default_store

SERVICE_VERSION = "0.1.0"
API_KEY = os.getenv("SERVERCORE_API_KEY", "").strip()
ALLOWED_ORIGINS = [
    origin.strip()
    for origin in os.getenv(
        "SERVERCORE_ALLOWED_ORIGINS",
        "http://localhost:8080,http://127.0.0.1:8080",
    ).split(",")
    if origin.strip()
]


def require_write_key(
    supplied_key: Annotated[str | None, Header(alias="X-ServerCore-Key")] = None,
) -> None:
    if API_KEY and supplied_key != API_KEY:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing or invalid ServerCore API key",
        )


def create_app(network_store: NetworkStore | None = None) -> FastAPI:
    active_store = network_store or default_store
    application = FastAPI(
        title="ServerCore Network API",
        version=SERVICE_VERSION,
        description="Internal service for player profiles, duels, leaderboards, and audit events.",
    )
    application.add_middleware(
        CORSMiddleware,
        allow_origins=ALLOWED_ORIGINS,
        allow_credentials=False,
        allow_methods=["GET", "POST", "PUT", "OPTIONS"],
        allow_headers=["Accept", "Content-Type", "X-ServerCore-Key"],
    )

    @application.get("/health", response_model=HealthResponse)
    def health() -> HealthResponse:
        return HealthResponse(
            status="ok",
            service="servercore-network-api",
            version=SERVICE_VERSION,
            authentication_enabled=bool(API_KEY),
        )

    @application.put(
        "/players/{player_id}",
        response_model=PlayerRecord,
        dependencies=[Depends(require_write_key)],
    )
    def upsert_player(player_id: UUID, payload: PlayerUpsert) -> PlayerRecord:
        if player_id != payload.player_id:
            raise HTTPException(status_code=400, detail="Path and payload player IDs must match")
        return active_store.upsert_player(payload)

    @application.get("/players/by-name/{username}", response_model=PlayerRecord)
    def get_player_by_username(username: str) -> PlayerRecord:
        player = active_store.get_player_by_username(username)
        if player is None:
            raise HTTPException(status_code=404, detail="Player not found")
        return player

    @application.get("/players/{player_id}", response_model=PlayerRecord)
    def get_player(player_id: UUID) -> PlayerRecord:
        player = active_store.get_player(player_id)
        if player is None:
            raise HTTPException(status_code=404, detail="Player not found")
        return player

    @application.get("/leaderboard", response_model=list[LeaderboardEntry])
    def leaderboard(limit: Annotated[int, Query(ge=1, le=1000)] = 100) -> list[LeaderboardEntry]:
        return active_store.leaderboard(limit)

    @application.post(
        "/matches",
        response_model=MatchCreate,
        status_code=status.HTTP_201_CREATED,
        dependencies=[Depends(require_write_key)],
    )
    def create_match(payload: MatchCreate) -> MatchCreate:
        return active_store.save_match(payload)

    @application.get("/matches/{match_id}", response_model=MatchCreate)
    def get_match(match_id: UUID) -> MatchCreate:
        match = active_store.get_match(match_id)
        if match is None:
            raise HTTPException(status_code=404, detail="Match not found")
        return match

    @application.post(
        "/events",
        response_model=AuditEventCreate,
        status_code=status.HTTP_201_CREATED,
        dependencies=[Depends(require_write_key)],
    )
    def create_event(payload: AuditEventCreate) -> AuditEventCreate:
        return active_store.add_event(payload)

    @application.get("/events", response_model=list[AuditEventCreate])
    def recent_events(limit: Annotated[int, Query(ge=1, le=1000)] = 100) -> list[AuditEventCreate]:
        return active_store.recent_events(limit)

    return application


app = create_app()
