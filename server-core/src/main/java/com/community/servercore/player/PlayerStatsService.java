package com.community.servercore.player;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PlayerStatsService {
    private final PlayerProfileRepository repository;
    private final Clock clock;

    public PlayerStatsService(PlayerProfileRepository repository) {
        this(repository, Clock.systemUTC());
    }

    public PlayerStatsService(PlayerProfileRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PlayerProfile registerOrUpdate(UUID playerId, String username) {
        Objects.requireNonNull(playerId, "playerId");
        PlayerProfile profile = repository.findById(playerId)
                .map(existing -> existing.seenAs(username, clock.instant()))
                .orElseGet(() -> PlayerProfile.newPlayer(playerId, username, clock.instant()));
        repository.save(profile);
        return profile;
    }

    public MatchProfileUpdate recordMatch(
            UUID winnerId,
            UUID loserId,
            long winnerKills,
            long loserKills,
            int winnerRating,
            int loserRating) {
        PlayerProfile winner = requireProfile(winnerId);
        PlayerProfile loser = requireProfile(loserId);
        PlayerProfile updatedWinner = winner.withMatchResult(
                true, winnerKills, loserKills, winnerRating, clock.instant());
        PlayerProfile updatedLoser = loser.withMatchResult(
                false, loserKills, winnerKills, loserRating, clock.instant());
        repository.save(updatedWinner);
        repository.save(updatedLoser);
        return new MatchProfileUpdate(updatedWinner, updatedLoser);
    }

    public Optional<PlayerProfile> find(UUID playerId) {
        return repository.findById(playerId);
    }

    public Optional<PlayerProfile> find(String username) {
        return repository.findByUsername(username);
    }

    public List<PlayerProfile> leaderboard(int limit) {
        if (limit < 1 || limit > 1_000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
        return repository.findAll().stream().limit(limit).toList();
    }

    private PlayerProfile requireProfile(UUID playerId) {
        return repository.findById(Objects.requireNonNull(playerId, "playerId"))
                .orElseThrow(() -> new IllegalStateException("Player profile not found: " + playerId));
    }

    public record MatchProfileUpdate(PlayerProfile winner, PlayerProfile loser) {
        public MatchProfileUpdate {
            Objects.requireNonNull(winner, "winner");
            Objects.requireNonNull(loser, "loser");
        }
    }
}
