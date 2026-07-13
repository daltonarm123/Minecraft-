package com.community.servercore.duel;

import com.community.servercore.player.InMemoryPlayerProfileRepository;
import com.community.servercore.player.PlayerProfile;
import com.community.servercore.player.PlayerStatsService;
import com.community.servercore.selection.WorldPosition;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MatchmakingServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-13T04:00:00Z");

    @Test
    void matchesPlayersCompletesRankedDuelAndReleasesArena() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        InMemoryPlayerProfileRepository profiles = new InMemoryPlayerProfileRepository();
        PlayerStatsService stats = new PlayerStatsService(profiles, clock);
        ArenaRegistry arenas = new ArenaRegistry();
        arenas.register(new ArenaDefinition(
                "arena-one",
                new WorldPosition("minecraft:overworld", 0, 64, 0),
                new WorldPosition("minecraft:overworld", 10, 64, 0),
                null,
                true));
        MatchmakingService service = new MatchmakingService(
                arenas,
                stats,
                new RatingService(32),
                clock,
                300);
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();

        MatchmakingResult first = service.join(firstPlayer, "First", DuelMode.RANKED);
        MatchmakingResult second = service.join(secondPlayer, "Second", DuelMode.RANKED);

        assertThat(first.status()).isEqualTo(MatchmakingStatus.QUEUED);
        assertThat(second.status()).isEqualTo(MatchmakingStatus.MATCHED);
        DuelMatch created = second.optionalMatch().orElseThrow();
        assertThat(arenas.isReserved("arena-one")).isTrue();

        DuelMatch started = service.start(created.matchId());
        MatchmakingService.MatchCompletion completion = service.complete(
                started.matchId(),
                secondPlayer,
                3,
                1);

        assertThat(completion.match().status()).isEqualTo(DuelStatus.COMPLETED);
        assertThat(arenas.isReserved("arena-one")).isFalse();
        PlayerProfile winner = stats.find(secondPlayer).orElseThrow();
        PlayerProfile loser = stats.find(firstPlayer).orElseThrow();
        assertThat(winner.wins()).isEqualTo(1);
        assertThat(loser.losses()).isEqualTo(1);
        assertThat(winner.rating()).isGreaterThan(1_000);
        assertThat(loser.rating()).isLessThan(1_000);
    }

    @Test
    void preventsDuplicateQueueEntries() {
        MatchmakingService service = serviceWithoutArena();
        UUID playerId = UUID.randomUUID();

        assertThat(service.join(playerId, "Player", DuelMode.CASUAL).status())
                .isEqualTo(MatchmakingStatus.QUEUED);
        assertThat(service.join(playerId, "Player", DuelMode.CASUAL).status())
                .isEqualTo(MatchmakingStatus.ALREADY_QUEUED);
    }

    private static MatchmakingService serviceWithoutArena() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new MatchmakingService(
                new ArenaRegistry(),
                new PlayerStatsService(new InMemoryPlayerProfileRepository(), clock),
                new RatingService(),
                clock,
                300);
    }
}
