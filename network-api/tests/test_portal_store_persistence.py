from __future__ import annotations

from datetime import timedelta

from app.portal_models import DiscordIdentity, MembershipUpdate
from app.portal_store import PortalStore


def test_portal_store_persists_membership_and_sessions(tmp_path) -> None:
    store_path = tmp_path / "portal-store.json"
    store = PortalStore(store_path)
    identity = DiscordIdentity(discord_user_id="111", username="test-user")
    session_token = store.create_session(identity)

    store.update_membership(
        "111",
        MembershipUpdate(discord_user_id="111", plan="founder", status="ACTIVE"),
    )

    reloaded = PortalStore(store_path)

    restored_identity = reloaded.get_session(session_token)
    assert restored_identity is not None
    assert restored_identity.discord_user_id == "111"
    assert restored_identity.username == "test-user"

    membership = reloaded.get_membership("111")
    assert membership.status == "ACTIVE"
    assert membership.plan == "founder"


def test_portal_store_prunes_expired_state_on_load(tmp_path) -> None:
    store_path = tmp_path / "portal-store.json"
    store = PortalStore(store_path)
    identity = DiscordIdentity(discord_user_id="222", username="expired-user")
    session_token = store.create_session(identity, ttl=timedelta(seconds=-1))

    reloaded = PortalStore(store_path)

    assert reloaded.get_session(session_token) is None
    assert reloaded.get_membership("222").discord_user_id == "222"
