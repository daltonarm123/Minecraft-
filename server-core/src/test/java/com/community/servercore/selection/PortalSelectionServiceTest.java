package com.community.servercore.selection;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortalSelectionServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-13T04:00:00Z");

    @Test
    void buildsNormalizedRegionFromTwoPoints() {
        PortalSelectionService service = new PortalSelectionService(Clock.fixed(NOW, ZoneOffset.UTC));
        UUID playerId = UUID.randomUUID();

        service.setFirst(playerId, "survival", new WorldPosition("minecraft:overworld", 10, 20, 30));
        PortalSelection selection = service.setSecond(
                playerId,
                "survival",
                new WorldPosition("minecraft:overworld", 1, 2, 3));

        assertThat(selection.complete()).isTrue();
        assertThat(selection.region()).hasValueSatisfying(region -> {
            assertThat(region.minX()).isEqualTo(1);
            assertThat(region.maxX()).isEqualTo(10);
            assertThat(region.minY()).isEqualTo(2);
            assertThat(region.maxY()).isEqualTo(20);
        });
    }

    @Test
    void rejectsPointsFromDifferentWorlds() {
        PortalSelectionService service = new PortalSelectionService(Clock.fixed(NOW, ZoneOffset.UTC));
        UUID playerId = UUID.randomUUID();
        service.setFirst(playerId, "survival", new WorldPosition("minecraft:overworld", 0, 0, 0));

        assertThatThrownBy(() -> service.setSecond(
                playerId,
                "survival",
                new WorldPosition("minecraft:the_nether", 1, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same world");
    }
}
