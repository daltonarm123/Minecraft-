package com.community.servercore.player;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PlayerProfile(
        UUID playerId,
        String username,
        Instant firstSeen,
        Instant lastSeen,
        long wins,
        long losses,
        long kills,
        long deaths,
        int rating) {

    public PlayerProfile {
        Objects.requireNonNull(playerId, "playerId");
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        username = username.trim();
        Objects.requireNonNull(firstSeen, "firstSeen");
        Objects.requireNonNull(lastSeen, "lastSeen");
        if (lastSeen.isBefore(firstSeen)) {
            throw new IllegalArgumentException("lastSeen must not be before firstSeen");
        }
        if (wins < 0 || losses < 0 || kills < 0 || deaths < 0) {
            throw new IllegalArgumentException("player statistics must not be negative");
        }
        if (rating < 0) {
            throw new IllegalArgumentException("rating must not be negative");
        }
    }

    public static PlayerProfile newPlayer(UUID playerId, String username, Instant now) {
        return new PlayerProfile(playerId, username, now, now, 0, 0, 0, 0, 1_000);
    }

    public long matchesPlayed() {
        return wins + losses;
    }

    public double winRate() {
        long matches = matchesPlayed();
        return matches == 0 ? 0.0 : (double) wins / matches;
    }

    public PlayerProfile seenAs(String newUsername, Instant now) {
        return new PlayerProfile(playerId, newUsername, firstSeen, now, wins, losses, kills, deaths, rating);
    }

    public PlayerProfile withMatchResult(boolean won, long matchKills, long matchDeaths, int newRating, Instant now) {
        if (matchKills < 0 || matchDeaths < 0) {
            throw new IllegalArgumentException("match statistics must not be negative");
        }
        return new PlayerProfile(
                playerId,
                username,
                firstSeen,
                now,
                wins + (won ? 1 : 0),
                losses + (won ? 0 : 1),
                kills + matchKills,
                deaths + matchDeaths,
                newRating);
    }
}
