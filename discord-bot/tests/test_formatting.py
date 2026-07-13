from app.formatting import format_health, format_leaderboard, format_player_stats


def test_formats_player_stats() -> None:
    message = format_player_stats(
        {
            "username": "Dalton",
            "rating": 1842,
            "wins": 127,
            "losses": 48,
            "kills": 300,
            "deaths": 100,
        }
    )

    assert "Dalton" in message
    assert "1,842" in message
    assert "72.6%" in message
    assert "3.00 K/D" in message


def test_formats_leaderboard() -> None:
    message = format_leaderboard(
        [
            {"rank": 1, "username": "First", "rating": 1500, "wins": 10, "losses": 2},
            {"rank": 2, "username": "Second", "rating": 1400, "wins": 8, "losses": 4},
        ]
    )

    assert "# 1" in message
    assert "First" in message
    assert "Second" in message


def test_formats_health() -> None:
    message = format_health(
        {"status": "ok", "version": "0.1.0", "authentication_enabled": True}
    )

    assert "ok" in message
    assert "0.1.0" in message
    assert "enabled" in message
