package com.community.servercore.selection;

import com.community.servercore.portal.PortalRegion;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record PortalSelection(
        String portalName,
        WorldPosition first,
        WorldPosition second,
        Instant updatedAt) {

    public PortalSelection {
        if (portalName == null || portalName.isBlank()) {
            throw new IllegalArgumentException("portalName must not be blank");
        }
        portalName = portalName.trim().toLowerCase();
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (first != null && second != null && !first.world().equalsIgnoreCase(second.world())) {
            throw new IllegalArgumentException("Both selection points must be in the same world");
        }
    }

    public boolean complete() {
        return first != null && second != null;
    }

    public Optional<PortalRegion> region() {
        if (!complete()) {
            return Optional.empty();
        }
        return Optional.of(new PortalRegion(
                first.x(), first.y(), first.z(),
                second.x(), second.y(), second.z()));
    }

    public Optional<String> world() {
        if (first != null) {
            return Optional.of(first.world());
        }
        if (second != null) {
            return Optional.of(second.world());
        }
        return Optional.empty();
    }

    public PortalSelection withFirst(WorldPosition position, Instant now) {
        return new PortalSelection(portalName, Objects.requireNonNull(position, "position"), second, now);
    }

    public PortalSelection withSecond(WorldPosition position, Instant now) {
        return new PortalSelection(portalName, first, Objects.requireNonNull(position, "position"), now);
    }
}
