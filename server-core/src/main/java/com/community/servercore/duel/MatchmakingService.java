package com.community.servercore.duel;

import com.community.servercore.player.PlayerProfile;
import com.community.servercore.player.PlayerStatsService;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MatchmakingService {
    private final Map<DuelMode, Deque<DuelQueueEntry>> queues = new EnumMap<>(DuelMode.class);
    private final Map<UUID, DuelMatch> matchesById = new HashMap<>();
    private final Map<UUID, UUID> activeMatchByPlayer = new HashMap<>();
    private final ArenaRegistry arenaRegistry;
    private final PlayerStatsService playerStatsService;
    private final RatingService ratingService;
    private final Clock clock;
    private final int maximumRankedRatingDifference;

    public MatchmakingService(
            ArenaRegistry arenaRegistry,
            PlayerStatsService playerStatsService,
            RatingService ratingService) {
        this(arenaRegistry, playerStatsService, ratingService, Clock.systemUTC(), 300);
    }

    public MatchmakingService(
            ArenaRegistry arenaRegistry,
            PlayerStatsService playerStatsService,
            RatingService ratingService,
            Clock clock,
            int maximumRankedRatingDifference) {
        this.arenaRegistry = Objects.requireNonNull(arenaRegistry, "arenaRegistry");
        this.playerStatsService = Objects.requireNonNull(playerStatsService, "playerStatsService");
        this.ratingService = Objects.requireNonNull(ratingService, "ratingService");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (maximumRankedRatingDifference < 0 || maximumRankedRatingDifference > 5_000) {
            throw new IllegalArgumentException("maximumRankedRatingDifference must be between 0 and 5000");
        }
        this.maximumRankedRatingDifference = maximumRankedRatingDifference;
        for (DuelMode mode : DuelMode.values()) {
            queues.put(mode, new ArrayDeque<>());
        }
    }

    public synchronized MatchmakingResult join(UUID playerId, String username, DuelMode mode) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(mode, "mode");
        if (activeMatchByPlayer.containsKey(playerId)) {
            return MatchmakingResult.of(
                    MatchmakingStatus.ALREADY_IN_MATCH,
                    "You already have an active duel.");
        }
        if (isQueued(playerId)) {
            return MatchmakingResult.of(
                    MatchmakingStatus.ALREADY_QUEUED,
                    "You are already in a matchmaking queue.");
        }

        PlayerProfile profile = playerStatsService.registerOrUpdate(playerId, username);
        DuelQueueEntry entrant = new DuelQueueEntry(
                playerId,
                profile.username(),
                mode,
                profile.rating(),
                clock.instant());
        Deque<DuelQueueEntry> queue = queues.get(mode);
        Iterator<DuelQueueEntry> iterator = queue.iterator();
        DuelQueueEntry opponent = null;
        while (iterator.hasNext()) {
            DuelQueueEntry candidate = iterator.next();
            if (compatible(entrant, candidate)) {
                opponent = candidate;
                break;
            }
        }

        if (opponent == null) {
            queue.addLast(entrant);
            return MatchmakingResult.of(MatchmakingStatus.QUEUED, "Added to the " + mode + " queue.");
        }

        UUID matchId = UUID.randomUUID();
        Optional<ArenaDefinition> arena = arenaRegistry.reserveAny(matchId);
        if (arena.isEmpty()) {
            queue.addLast(entrant);
            return MatchmakingResult.of(
                    MatchmakingStatus.NO_AVAILABLE_ARENA,
                    "No arena is available yet; you remain queued.");
        }

        queue.remove(opponent);
        DuelMatch match = DuelMatch.created(
                matchId,
                opponent.playerId(),
                entrant.playerId(),
                mode,
                arena.orElseThrow().arenaId(),
                clock.instant());
        matchesById.put(match.matchId(), match);
        activeMatchByPlayer.put(match.firstPlayerId(), match.matchId());
        activeMatchByPlayer.put(match.secondPlayerId(), match.matchId());
        return MatchmakingResult.matched(match);
    }

    public synchronized MatchmakingResult leave(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        for (Deque<DuelQueueEntry> queue : queues.values()) {
            boolean removed = queue.removeIf(entry -> entry.playerId().equals(playerId));
            if (removed) {
                return MatchmakingResult.of(MatchmakingStatus.NOT_QUEUED, "Removed from matchmaking.");
            }
        }
        return MatchmakingResult.of(MatchmakingStatus.NOT_QUEUED, "You are not queued.");
    }

    public synchronized DuelMatch start(UUID matchId) {
        DuelMatch match = requireMatch(matchId);
        DuelMatch started = match.start(clock.instant());
        matchesById.put(matchId, started);
        return started;
    }

    public synchronized MatchCompletion complete(
            UUID matchId,
            UUID winnerId,
            long winnerKills,
            long loserKills) {
        DuelMatch match = requireMatch(matchId);
        if (match.status() != DuelStatus.STARTED) {
            throw new IllegalStateException("Only started matches can be completed");
        }
        if (!match.contains(winnerId)) {
            throw new IllegalArgumentException("Winner must belong to the match");
        }
        UUID loserId = match.opponentOf(winnerId).orElseThrow();
        PlayerProfile winner = playerStatsService.find(winnerId)
                .orElseThrow(() -> new IllegalStateException("Winner profile is missing"));
        PlayerProfile loser = playerStatsService.find(loserId)
                .orElseThrow(() -> new IllegalStateException("Loser profile is missing"));

        int winnerRating = winner.rating();
        int loserRating = loser.rating();
        if (match.mode() == DuelMode.RANKED) {
            RatingService.RatingChange change = ratingService.calculate(winner.rating(), loser.rating());
            winnerRating = change.winnerRating();
            loserRating = change.loserRating();
        }

        PlayerStatsService.MatchProfileUpdate profiles = playerStatsService.recordMatch(
                winnerId,
                loserId,
                winnerKills,
                loserKills,
                winnerRating,
                loserRating);
        DuelMatch completed = match.complete(winnerId, clock.instant());
        matchesById.put(matchId, completed);
        finishMatch(completed);
        return new MatchCompletion(completed, profiles);
    }

    public synchronized DuelMatch cancel(UUID matchId) {
        DuelMatch match = requireMatch(matchId);
        DuelMatch cancelled = match.cancel(clock.instant());
        matchesById.put(matchId, cancelled);
        finishMatch(cancelled);
        return cancelled;
    }

    public synchronized Optional<DuelMatch> findMatch(UUID matchId) {
        return Optional.ofNullable(matchesById.get(Objects.requireNonNull(matchId, "matchId")));
    }

    public synchronized Optional<DuelMatch> activeMatchFor(UUID playerId) {
        UUID matchId = activeMatchByPlayer.get(Objects.requireNonNull(playerId, "playerId"));
        return matchId == null ? Optional.empty() : Optional.ofNullable(matchesById.get(matchId));
    }

    public synchronized List<DuelQueueEntry> queued(DuelMode mode) {
        return List.copyOf(queues.get(Objects.requireNonNull(mode, "mode")));
    }

    public synchronized List<DuelMatch> matches() {
        return List.copyOf(new ArrayList<>(matchesById.values()));
    }

    private boolean compatible(DuelQueueEntry entrant, DuelQueueEntry candidate) {
        if (entrant.mode() == DuelMode.CASUAL) {
            return true;
        }
        return Math.abs(entrant.rating() - candidate.rating()) <= maximumRankedRatingDifference;
    }

    private boolean isQueued(UUID playerId) {
        return queues.values().stream()
                .flatMap(Deque::stream)
                .anyMatch(entry -> entry.playerId().equals(playerId));
    }

    private DuelMatch requireMatch(UUID matchId) {
        return Optional.ofNullable(matchesById.get(Objects.requireNonNull(matchId, "matchId")))
                .orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchId));
    }

    private void finishMatch(DuelMatch match) {
        activeMatchByPlayer.remove(match.firstPlayerId());
        activeMatchByPlayer.remove(match.secondPlayerId());
        arenaRegistry.release(match.matchId());
    }

    public record MatchCompletion(
            DuelMatch match,
            PlayerStatsService.MatchProfileUpdate profiles) {
        public MatchCompletion {
            Objects.requireNonNull(match, "match");
            Objects.requireNonNull(profiles, "profiles");
        }
    }
}
