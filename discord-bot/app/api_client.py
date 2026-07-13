from __future__ import annotations

from dataclasses import dataclass
from typing import Any
from urllib.parse import quote

import httpx


class ServerCoreApiError(RuntimeError):
    """Raised when the network API cannot satisfy a Discord command."""


@dataclass(frozen=True, slots=True)
class ServerCoreApiClient:
    base_url: str
    api_key: str = ""
    timeout_seconds: float = 10.0

    def __post_init__(self) -> None:
        normalized = self.base_url.strip().rstrip("/")
        if not normalized.startswith(("http://", "https://")):
            raise ValueError("base_url must use http or https")
        if self.timeout_seconds <= 0:
            raise ValueError("timeout_seconds must be positive")
        object.__setattr__(self, "base_url", normalized)
        object.__setattr__(self, "api_key", self.api_key.strip())

    async def health(self) -> dict[str, Any]:
        return await self._get("/health")

    async def player_by_username(self, username: str) -> dict[str, Any]:
        normalized = username.strip()
        if not normalized:
            raise ValueError("username must not be blank")
        return await self._get(f"/players/by-name/{quote(normalized, safe='')}")

    async def leaderboard(self, limit: int = 10) -> list[dict[str, Any]]:
        if limit < 1 or limit > 25:
            raise ValueError("limit must be between 1 and 25")
        result = await self._get("/leaderboard", params={"limit": limit})
        if not isinstance(result, list):
            raise ServerCoreApiError("Leaderboard response was not a list")
        return result

    async def _get(
        self,
        path: str,
        *,
        params: dict[str, Any] | None = None,
    ) -> dict[str, Any] | list[dict[str, Any]]:
        headers = {"Accept": "application/json"}
        if self.api_key:
            headers["X-ServerCore-Key"] = self.api_key
        try:
            async with httpx.AsyncClient(
                base_url=self.base_url,
                timeout=self.timeout_seconds,
                headers=headers,
            ) as client:
                response = await client.get(path, params=params)
        except httpx.HTTPError as exception:
            raise ServerCoreApiError(f"Network API request failed: {exception}") from exception

        if response.status_code == 404:
            raise ServerCoreApiError("The requested Minecraft record was not found")
        if response.is_error:
            detail = response.text[:500]
            raise ServerCoreApiError(
                f"Network API returned HTTP {response.status_code}: {detail}"
            )
        try:
            return response.json()
        except ValueError as exception:
            raise ServerCoreApiError("Network API returned invalid JSON") from exception
