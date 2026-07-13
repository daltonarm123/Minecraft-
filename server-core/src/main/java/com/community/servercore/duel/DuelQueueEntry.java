package com.community.servercore.duel;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DuelQueueEntry(
        UUID playerId,
        String username,
        DuelMode mode,
        int rating,
        Instant joinedAt) {

    public DuelQueueEntry {
        Objects.requireNonNull(playerId, "playerId");
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        username = username.trim();
        Objects.requireNonNull(mode, "mode");
        if (rating < 0) {
            throw new IllegalArgumentException("rating must not be negative");
        }
        Objects.requireNonNull(joinedAt, "joinedAt");
    }
}
