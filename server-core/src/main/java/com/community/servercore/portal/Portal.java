package com.community.servercore.portal;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record Portal(
        UUID id,
        String name,
        String displayName,
        String sourceWorld,
        PortalRegion region,
        PortalDestination destination,
        boolean enabled,
        String permission,
        int cooldownSeconds,
        String entryMessage,
        String deniedMessage,
        Map<String, String> metadata) {

    public Portal {
        Objects.requireNonNull(id, "id");
        name = requireText(name, "name").toLowerCase();
        displayName = requireText(displayName, "displayName");
        sourceWorld = requireText(sourceWorld, "sourceWorld");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(destination, "destination");
        permission = permission == null ? "" : permission.trim();
        if (cooldownSeconds < 0) {
            throw new IllegalArgumentException("cooldownSeconds must be zero or greater");
        }
        entryMessage = entryMessage == null ? "" : entryMessage;
        deniedMessage = deniedMessage == null ? "You cannot use this portal." : deniedMessage;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
