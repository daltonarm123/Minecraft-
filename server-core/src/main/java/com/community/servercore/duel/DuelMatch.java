package com.community.servercore.duel;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record DuelMatch(
        UUID matchId,
        UUID firstPlayerId,
        UUID secondPlayerId,
        DuelMode mode,
        String arenaId,
        DuelStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        UUID winnerId,
        UUID loserId) {

    public DuelMatch {
        Objects.requireNonNull(matchId, "matchId");
        Objects.requireNonNull(firstPlayerId, "firstPlayerId");
        Objects.requireNonNull(secondPlayerId, "secondPlayerId");
        if (firstPlayerId.equals(secondPlayerId)) {
            throw new IllegalArgumentException("A player cannot duel themselves");
        }
        Objects.requireNonNull(mode, "mode");
        arenaId = requireText(arenaId, "arenaId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");

        if (startedAt != null && startedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("startedAt must not be before createdAt");
        }
        if (completedAt != null && startedAt != null && completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("completedAt must not be before startedAt");
        }
        if (status == DuelStatus.COMPLETED) {
            Objects.requireNonNull(winnerId, "winnerId");
            Objects.requireNonNull(loserId, "loserId");
            if (winnerId.equals(loserId)) {
                throw new IllegalArgumentException("winnerId and loserId must differ");
            }
            if (!containsPlayer(winnerId, firstPlayerId, secondPlayerId)
                    || !containsPlayer(loserId, firstPlayerId, secondPlayerId)) {
                throw new IllegalArgumentException("Winner and loser must belong to the match");
            }
            Objects.requireNonNull(completedAt, "completedAt");
        } else if (winnerId != null || loserId != null) {
            throw new IllegalArgumentException("Only completed matches may have a winner and loser");
        }
    }

    public static DuelMatch created(
            UUID firstPlayerId,
            UUID secondPlayerId,
            DuelMode mode,
            String arenaId,
            Instant now) {
        return new DuelMatch(
                UUID.randomUUID(),
                firstPlayerId,
                secondPlayerId,
                mode,
                arenaId,
                DuelStatus.CREATED,
                now,
                null,
                null,
                null,
                null);
    }

    public DuelMatch start(Instant now) {
        if (status != DuelStatus.CREATED) {
            throw new IllegalStateException("Only created matches can be started");
        }
        return new DuelMatch(
                matchId,
                firstPlayerId,
                secondPlayerId,
                mode,
                arenaId,
                DuelStatus.STARTED,
                createdAt,
                Objects.requireNonNull(now, "now"),
                null,
                null,
                null);
    }

    public DuelMatch complete(UUID winner, Instant now) {
        if (status != DuelStatus.STARTED) {
            throw new IllegalStateException("Only started matches can be completed");
        }
        if (!contains(winner)) {
            throw new IllegalArgumentException("Winner must belong to the match");
        }
        UUID loser = firstPlayerId.equals(winner) ? secondPlayerId : firstPlayerId;
        return new DuelMatch(
                matchId,
                firstPlayerId,
                secondPlayerId,
                mode,
                arenaId,
                DuelStatus.COMPLETED,
                createdAt,
                startedAt,
                Objects.requireNonNull(now, "now"),
                winner,
                loser);
    }

    public DuelMatch cancel(Instant now) {
        if (status == DuelStatus.COMPLETED || status == DuelStatus.CANCELLED) {
            throw new IllegalStateException("Match is already finished");
        }
        return new DuelMatch(
                matchId,
                firstPlayerId,
                secondPlayerId,
                mode,
                arenaId,
                DuelStatus.CANCELLED,
                createdAt,
                startedAt,
                Objects.requireNonNull(now, "now"),
                null,
                null);
    }

    public boolean contains(UUID playerId) {
        return containsPlayer(playerId, firstPlayerId, secondPlayerId);
    }

    public Optional<UUID> opponentOf(UUID playerId) {
        if (firstPlayerId.equals(playerId)) {
            return Optional.of(secondPlayerId);
        }
        if (secondPlayerId.equals(playerId)) {
            return Optional.of(firstPlayerId);
        }
        return Optional.empty();
    }

    private static boolean containsPlayer(UUID playerId, UUID first, UUID second) {
        return first.equals(playerId) || second.equals(playerId);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim().toLowerCase();
    }
}
