from uuid import uuid4

from fastapi.testclient import TestClient

from app.main import create_app
from app.store import NetworkStore


def client() -> TestClient:
    return TestClient(create_app(NetworkStore()))


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
