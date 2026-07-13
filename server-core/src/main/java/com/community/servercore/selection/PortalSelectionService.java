package com.community.servercore.selection;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PortalSelectionService {
    private final Map<UUID, PortalSelection> selections = new ConcurrentHashMap<>();
    private final Clock clock;

    public PortalSelectionService() {
        this(Clock.systemUTC());
    }

    public PortalSelectionService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PortalSelection begin(UUID playerId, String portalName) {
        Objects.requireNonNull(playerId, "playerId");
        PortalSelection selection = new PortalSelection(portalName, null, null, clock.instant());
        selections.put(playerId, selection);
        return selection;
    }

    public PortalSelection setFirst(UUID playerId, String portalName, WorldPosition position) {
        return update(playerId, portalName, position, true);
    }

    public PortalSelection setSecond(UUID playerId, String portalName, WorldPosition position) {
        return update(playerId, portalName, position, false);
    }

    public Optional<PortalSelection> get(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return Optional.ofNullable(selections.get(playerId));
    }

    public void clear(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        selections.remove(playerId);
    }

    public int purgeOlderThan(Duration maximumAge) {
        Objects.requireNonNull(maximumAge, "maximumAge");
        if (maximumAge.isNegative() || maximumAge.isZero()) {
            throw new IllegalArgumentException("maximumAge must be positive");
        }
        Instant cutoff = clock.instant().minus(maximumAge);
        int before = selections.size();
        selections.entrySet().removeIf(entry -> entry.getValue().updatedAt().isBefore(cutoff));
        return before - selections.size();
    }

    private PortalSelection update(
            UUID playerId,
            String portalName,
            WorldPosition position,
            boolean firstPoint) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(position, "position");
        String normalizedName = normalizeName(portalName);
        Instant now = clock.instant();

        return selections.compute(playerId, (ignored, existing) -> {
            PortalSelection base = existing == null || !existing.portalName().equals(normalizedName)
                    ? new PortalSelection(normalizedName, null, null, now)
                    : existing;
            return firstPoint ? base.withFirst(position, now) : base.withSecond(position, now);
        });
    }

    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("portalName must not be blank");
        }
        return name.trim().toLowerCase();
    }
}
