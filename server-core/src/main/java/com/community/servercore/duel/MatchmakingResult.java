package com.community.servercore.duel;

import java.util.Objects;
import java.util.Optional;

public record MatchmakingResult(
        MatchmakingStatus status,
        String message,
        DuelMatch match) {

    public MatchmakingResult {
        Objects.requireNonNull(status, "status");
        message = Objects.requireNonNullElse(message, "");
        if (status == MatchmakingStatus.MATCHED && match == null) {
            throw new IllegalArgumentException("Matched results require a match");
        }
    }

    public Optional<DuelMatch> optionalMatch() {
        return Optional.ofNullable(match);
    }

    public static MatchmakingResult of(MatchmakingStatus status, String message) {
        return new MatchmakingResult(status, message, null);
    }

    public static MatchmakingResult matched(DuelMatch match) {
        return new MatchmakingResult(MatchmakingStatus.MATCHED, "Opponent found.", match);
    }
}
