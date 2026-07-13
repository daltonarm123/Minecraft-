package com.community.servercore.service;

import com.community.servercore.portal.Portal;
import com.community.servercore.portal.PortalDestination;
import com.community.servercore.portal.PortalRegion;
import com.community.servercore.storage.InMemoryPortalRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PortalServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-13T03:00:00Z");

    @Test
    void savesAndFindsPortalAtPlayerLocation() throws Exception {
        PortalService service = createService((playerId, permission) -> true, successfulTeleport());
        Portal portal = portal("survival", new PortalRegion(0, 0, 0, 10, 10, 10), true, 3);

        PortalMutationResult result = service.save(portal);

        assertThat(result.successful()).isTrue();
        assertThat(service.findAt("overworld", 5, 5, 5)).contains(portal);
        assertThat(service.findAt("overworld", 20, 5, 5)).isEmpty();
    }

    @Test
    void rejectsOverlappingPortalRegions() throws Exception {
        PortalService service = createService((playerId, permission) -> true, successfulTeleport());
        Portal first = portal("survival", new PortalRegion(0, 0, 0, 10, 10, 10), true, 3);
        Portal second = portal("pvp", new PortalRegion(5, 0, 5, 15, 10, 15), true, 3);

        assertThat(service.save(first).successful()).isTrue();
        PortalMutationResult result = service.save(second);

        assertThat(result.successful()).isFalse();
        assertThat(result.errors()).anyMatch(message -> message.contains("overlaps"));
        assertThat(service.list()).containsExactly(first);
    }

    @Test
    void deniesPlayerWithoutRequiredPermission() throws Exception {
        PortalService service = createService((playerId, permission) -> false, successfulTeleport());
        Portal portal = portal("survival", new PortalRegion(0, 0, 0, 10, 10, 10), true, 3);
        service.save(portal);

        PortalUseResult result = service.tryUse(UUID.randomUUID(), "overworld", 5, 5, 5);

        assertThat(result.status()).isEqualTo(PortalUseStatus.ACCESS_DENIED);
        assertThat(result.message()).isEqualTo("You need access to use this portal.");
    }

    @Test
    void teleportsOnceThenEnforcesCooldown() throws Exception {
        AtomicInteger teleportCount = new AtomicInteger();
        PortalTeleportService teleportService = (playerId, destination) -> {
            teleportCount.incrementAndGet();
            return TeleportResult.success("Entering Survival...");
        };
        PortalService service = createService((playerId, permission) -> true, teleportService);
        Portal portal = portal("survival", new PortalRegion(0, 0, 0, 10, 10, 10), true, 5);
        service.save(portal);
        UUID playerId = UUID.randomUUID();

        PortalUseResult first = service.tryUse(playerId, "overworld", 5, 5, 5);
        PortalUseResult second = service.tryUse(playerId, "overworld", 5, 5, 5);

        assertThat(first.status()).isEqualTo(PortalUseStatus.SUCCESS);
        assertThat(first.message()).isEqualTo("Entering Survival...");
        assertThat(second.status()).isEqualTo(PortalUseStatus.COOLDOWN_ACTIVE);
        assertThat(second.remainingCooldown().getSeconds()).isEqualTo(5);
        assertThat(teleportCount).hasValue(1);
    }

    @Test
    void reportsTeleportFailureWithoutStartingCooldown() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        PortalTeleportService teleportService = (playerId, destination) -> {
            attempts.incrementAndGet();
            return TeleportResult.failure("Destination server is unavailable.");
        };
        PortalService service = createService((playerId, permission) -> true, teleportService);
        Portal portal = portal("survival", new PortalRegion(0, 0, 0, 10, 10, 10), true, 5);
        service.save(portal);
        UUID playerId = UUID.randomUUID();

        PortalUseResult first = service.tryUse(playerId, "overworld", 5, 5, 5);
        PortalUseResult second = service.tryUse(playerId, "overworld", 5, 5, 5);

        assertThat(first.status()).isEqualTo(PortalUseStatus.TELEPORT_FAILED);
        assertThat(first.message()).contains("unavailable");
        assertThat(second.status()).isEqualTo(PortalUseStatus.TELEPORT_FAILED);
        assertThat(attempts).hasValue(2);
    }

    private static PortalService createService(
            PortalAccessService accessService,
            PortalTeleportService teleportService) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new PortalService(
                new InMemoryPortalRepository(),
                new PortalValidator(),
                new PortalCooldownService(clock),
                accessService,
                teleportService,
                false);
    }

    private static PortalTeleportService successfulTeleport() {
        return (playerId, destination) -> TeleportResult.success("Teleport complete.");
    }

    private static Portal portal(String name, PortalRegion region, boolean enabled, int cooldownSeconds) {
        return new Portal(
                UUID.randomUUID(),
                name,
                name,
                "overworld",
                region,
                PortalDestination.location("survival", 100, 70, 100, 0, 0),
                enabled,
                "servercore.portal." + name,
                cooldownSeconds,
                "Entering " + name + "...",
                "You need access to use this portal.",
                Map.of());
    }
}
