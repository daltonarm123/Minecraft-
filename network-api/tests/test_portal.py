from uuid import uuid4

from fastapi.testclient import TestClient

from app.main import create_app
from app.models import PlayerUpsert
from app.portal_models import DiscordIdentity
from app.portal_store import PortalStore
from app.store import NetworkStore


def authenticated_client() -> tuple[TestClient, NetworkStore, PortalStore, DiscordIdentity]:
    network_store = NetworkStore()
    portal_store = PortalStore()
    api = TestClient(create_app(network_store, portal_store))
    identity = DiscordIdentity(
        discord_user_id="123456789",
        username="builder",
        global_name="Builder",
    )
    token = portal_store.create_session(identity)
    api.cookies.set("servercore_session", token)
    return api, network_store, portal_store, identity


def test_portal_page_and_authentication_requirement() -> None:
    api = TestClient(create_app(NetworkStore(), PortalStore()))

    page = api.get("/portal")
    me = api.get("/api/portal/me")

    assert page.status_code == 200
    assert "ServerCore Portal" in page.text
    assert me.status_code == 401


def test_linked_player_stats_and_achievements() -> None:
    api, network_store, _, _ = authenticated_client()
    player_id = uuid4()
    network_store.upsert_player(
        PlayerUpsert(
            player_id=player_id,
            username="MinerOne",
            wins=12,
            losses=3,
            kills=125,
            deaths=20,
            rating=1300,
        )
    )

    challenge = api.post("/api/portal/link-challenges")
    assert challenge.status_code == 200
    confirmation = api.post(
        "/api/portal/link-confirmations",
        json={
            "code": challenge.json()["code"],
            "player_id": str(player_id),
            "username": "MinerOne",
        },
    )
    me = api.get("/api/portal/me")

    assert confirmation.status_code == 200
    assert me.status_code == 200
    assert me.json()["minecraft_link"]["username"] == "MinerOne"
    assert me.json()["player"]["rating"] == 1300
    assert all(item["state"] == "UNLOCKED" for item in me.json()["achievements"])


def test_support_ticket_workflow() -> None:
    api, _, _, _ = authenticated_client()

    created = api.post(
        "/api/portal/tickets",
        json={"subject": "Cannot connect", "message": "The server says my account is not linked."},
    )
    listed = api.get("/api/portal/tickets")
    updated = api.patch(
        f"/api/portal/admin/tickets/{created.json()['ticket_id']}",
        json={"status": "RESOLVED"},
    )

    assert created.status_code == 201
    assert len(listed.json()) == 1
    assert updated.status_code == 200
    assert updated.json()["status"] == "RESOLVED"


def test_feature_votes_are_one_per_discord_account() -> None:
    api, _, _, _ = authenticated_client()
    features = api.get("/api/portal/features").json()
    proposal_id = features[0]["proposal_id"]

    first_vote = api.post(f"/api/portal/features/{proposal_id}/vote")
    second_vote = api.post(f"/api/portal/features/{proposal_id}/vote")

    assert first_vote.status_code == 200
    assert first_vote.json()["vote_count"] == 1
    assert second_vote.json()["vote_count"] == 1


def test_membership_admin_update_and_unconfigured_checkout() -> None:
    api, _, _, identity = authenticated_client()

    checkout = api.post("/api/portal/membership/checkout")
    updated = api.put(
        f"/api/portal/admin/memberships/{identity.discord_user_id}",
        json={"plan": "founder", "status": "ACTIVE"},
    )
    membership = api.get("/api/portal/membership")

    assert checkout.status_code == 503
    assert updated.status_code == 200
    assert membership.json()["status"] == "ACTIVE"
