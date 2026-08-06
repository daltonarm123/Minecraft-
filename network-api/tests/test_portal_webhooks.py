from __future__ import annotations

import hashlib
import hmac
import json

import pytest
from fastapi.testclient import TestClient

from app.main import create_app
from app.portal_store import PortalStore
from app.store import NetworkStore


def _sign_payload(payload: dict[str, object], secret: str) -> str:
    body = json.dumps(payload, separators=(",", ":")).encode("utf-8")
    digest = hmac.new(secret.encode("utf-8"), body, hashlib.sha256).hexdigest()
    return f"sha256={digest}"


def test_membership_webhook_updates_membership_status(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("SERVERCORE_WEBHOOK_SECRET", "test-secret")
    app = create_app(network_store=NetworkStore(), portal_store=PortalStore())
    client = TestClient(app)
    payload = {
        "discord_user_id": "123456",
        "status": "ACTIVE",
        "plan": "founder",
        "current_period_end": "2030-01-01T00:00:00Z",
    }

    response = client.post(
        "/api/portal/membership/webhook",
        json=payload,
        headers={"X-ServerCore-Signature": _sign_payload(payload, "test-secret")},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["discord_user_id"] == "123456"
    assert body["status"] == "ACTIVE"


def test_membership_webhook_requires_valid_signature(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("SERVERCORE_WEBHOOK_SECRET", "test-secret")
    app = create_app(network_store=NetworkStore(), portal_store=PortalStore())
    client = TestClient(app)

    response = client.post(
        "/api/portal/membership/webhook",
        json={
            "discord_user_id": "123456",
            "status": "ACTIVE",
            "plan": "founder",
        },
    )

    assert response.status_code == 401
    assert "signature" in response.json()["detail"].lower()


def test_membership_webhook_replays_are_idempotent(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("SERVERCORE_WEBHOOK_SECRET", "test-secret")
    app = create_app(network_store=NetworkStore(), portal_store=PortalStore())
    client = TestClient(app)
    payload = {
        "discord_user_id": "789012",
        "status": "ACTIVE",
        "plan": "founder",
    }
    headers = {"X-ServerCore-Signature": _sign_payload(payload, "test-secret")}

    first = client.post("/api/portal/membership/webhook", json=payload, headers=headers)
    second = client.post("/api/portal/membership/webhook", json=payload, headers=headers)

    assert first.status_code == 200
    assert second.status_code == 200
    assert second.json() == first.json()
