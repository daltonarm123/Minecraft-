package com.community.servercore.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PortalCooldownService {
    private final Map<UUID, Instant> lastUseByPlayer = new ConcurrentHashMap<>();
    private final Clock clock;

    public PortalCooldownService() {
        this(Clock.systemUTC());
    }

    public PortalCooldownService(Clock clock) {
        this.clock = clock;
    }

    public void recordUse(UUID playerId) {
        lastUseByPlayer.put(playerId, clock.instant());
    }

    public Duration remaining(UUID playerId, int cooldownSeconds) {
        Instant lastUse = lastUseByPlayer.get(playerId);
        if (lastUse == null || cooldownSeconds <= 0) {
            return Duration.ZERO;
        }
        Instant readyAt = lastUse.plusSeconds(cooldownSeconds);
        Duration remaining = Duration.between(clock.instant(), readyAt);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    public boolean isReady(UUID playerId, int cooldownSeconds) {
        return remaining(playerId, cooldownSeconds).isZero();
    }

    public void clear(UUID playerId) {
        lastUseByPlayer.remove(playerId);
    }

    public void clearAll() {
        lastUseByPlayer.clear();
    }
}
