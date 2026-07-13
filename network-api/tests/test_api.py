from uuid import uuid4

from fastapi.testclient import TestClient

from app.main import create_app
from app.store import NetworkStore


def client() -> TestClient:
    return TestClient(create_app(NetworkStore()))


def cosmetic(cosmetic_id: str = "founder-crown", *, enabled: bool = True) -> dict[str, object]:
    return {
        "cosmetic_id": cosmetic_id,
        "display_name": "Founder Crown",
        "category": "HEAD",
        "rarity": "LEGENDARY",
        "unlock_source": "FOUNDER",
        "asset_id": f"servercore:cosmetics/{cosmetic_id}",
        "description": "Original founder cosmetic",
        "enabled": enabled,
        "tags": ["launch"],
    }


def test_health() -> None:
    response = client().get("/health")

    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_player_upsert_leaderboard_and_username_lookup() -> None:
    api = client()
    first_id = uuid4()
    second_id = uuid4()

    first = {
        "player_id": str(first_id),
        "username": "First",
        "wins": 5,
        "losses": 1,
        "kills": 10,
        "deaths": 3,
        "rating": 1200,
    }
    second = {
        "player_id": str(second_id),
        "username": "Second",
        "wins": 10,
        "losses": 2,
        "kills": 20,
        "deaths": 4,
        "rating": 1500,
    }

    assert api.put(f"/players/{first_id}", json=first).status_code == 200
    assert api.put(f"/players/{second_id}", json=second).status_code == 200
    leaderboard = api.get("/leaderboard?limit=10")
    lookup = api.get("/players/by-name/second")

    assert leaderboard.status_code == 200
    body = leaderboard.json()
    assert [entry["username"] for entry in body] == ["Second", "First"]
    assert body[0]["rank"] == 1
    assert lookup.status_code == 200
    assert lookup.json()["player_id"] == str(second_id)


def test_rejects_player_path_mismatch() -> None:
    api = client()
    path_id = uuid4()
    payload_id = uuid4()

    response = api.put(
        f"/players/{path_id}",
        json={"player_id": str(payload_id), "username": "Mismatch"},
    )

    assert response.status_code == 400


def test_match_requires_distinct_players() -> None:
    api = client()
    player_id = uuid4()

    response = api.post(
        "/matches",
        json={
            "first_player_id": str(player_id),
            "second_player_id": str(player_id),
            "mode": "RANKED",
            "arena_id": "arena-one",
        },
    )

    assert response.status_code == 422


def test_cosmetic_catalog_grant_equip_and_revoke() -> None:
    api = client()
    player_id = uuid4()

    assert api.put("/cosmetics/founder-crown", json=cosmetic()).status_code == 200
    catalog = api.get("/cosmetics")
    assert catalog.status_code == 200
    assert [entry["cosmetic_id"] for entry in catalog.json()] == ["founder-crown"]

    granted = api.post(f"/players/{player_id}/cosmetics/founder-crown/grant")
    assert granted.status_code == 200
    assert granted.json()["owned_cosmetic_ids"] == ["founder-crown"]

    equipped = api.put(f"/players/{player_id}/cosmetics/founder-crown/equip")
    assert equipped.status_code == 200
    assert equipped.json()["equipped_by_category"] == {"HEAD": "founder-crown"}

    revoked = api.delete(f"/players/{player_id}/cosmetics/founder-crown")
    assert revoked.status_code == 200
    assert revoked.json()["owned_cosmetic_ids"] == []
    assert revoked.json()["equipped_by_category"] == {}


def test_rejects_unowned_and_disabled_cosmetics() -> None:
    api = client()
    player_id = uuid4()

    assert api.put("/cosmetics/founder-crown", json=cosmetic()).status_code == 200
    unowned = api.put(f"/players/{player_id}/cosmetics/founder-crown/equip")
    assert unowned.status_code == 400
    assert "does not own" in unowned.json()["detail"]

    assert api.post(f"/players/{player_id}/cosmetics/founder-crown/grant").status_code == 200
    assert api.put(
        "/cosmetics/founder-crown",
        json=cosmetic(enabled=False),
    ).status_code == 200

    hidden_catalog = api.get("/cosmetics")
    full_catalog = api.get("/cosmetics?include_disabled=true")
    assert hidden_catalog.json() == []
    assert len(full_catalog.json()) == 1

    disabled = api.put(f"/players/{player_id}/cosmetics/founder-crown/equip")
    assert disabled.status_code == 400
    assert "disabled" in disabled.json()["detail"]


def test_unequips_category_and_rejects_bad_cosmetic_path() -> None:
    api = client()
    player_id = uuid4()

    mismatch = api.put("/cosmetics/other-id", json=cosmetic())
    assert mismatch.status_code == 400

    api.put("/cosmetics/founder-crown", json=cosmetic())
    api.post(f"/players/{player_id}/cosmetics/founder-crown/grant")
    api.put(f"/players/{player_id}/cosmetics/founder-crown/equip")

    response = api.delete(f"/players/{player_id}/cosmetics/categories/HEAD")
    assert response.status_code == 200
    assert response.json()["equipped_by_category"] == {}
