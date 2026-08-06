package com.community.servercore.service;

import com.community.servercore.portal.DestinationType;
import com.community.servercore.portal.PortalDestination;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ConfigurablePortalResolver implements PortalResolver {
    private final Map<String, PortalDestination> destinations;

    public ConfigurablePortalResolver() {
        this(Collections.emptyMap());
    }

    public ConfigurablePortalResolver(Map<String, PortalDestination> destinations) {
        this.destinations = new ConcurrentHashMap<>(
                Objects.requireNonNull(destinations, "destinations"));
    }

    public void register(String key, PortalDestination destination) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(destination, "destination");
        destinations.put(key.trim().toLowerCase(), destination);
    }

    public void registerAll(Map<String, String> routingEntries) {
        Objects.requireNonNull(routingEntries, "routingEntries");
        routingEntries.forEach((key, value) -> register(key, parseDestination(value)));
    }

    @Override
    public Optional<PortalDestination> resolve(PortalDestination destination) {
        Objects.requireNonNull(destination, "destination");
        if (destination.type() == DestinationType.LOCATION) {
            return Optional.of(destination);
        }
        String key = destination.type().name().toLowerCase() + ":" + destination.target().trim().toLowerCase();
        return Optional.ofNullable(destinations.get(key));
    }

    private static PortalDestination parseDestination(String value) {
        Objects.requireNonNull(value, "value");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Portal routing entries must not be blank");
        }
        String[] parts = trimmed.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Portal routing entries must use type:target format");
        }
        String type = parts[0].trim().toUpperCase();
        String target = parts[1].trim();
        return switch (type) {
            case "LOCATION" -> PortalDestination.location(target, 0, 64, 0, 0, 0);
            case "WORLD" -> PortalDestination.world(target);
            case "SERVER", "ARENA", "EVENT" -> new PortalDestination(
                    DestinationType.valueOf(type),
                    target,
                    null,
                    null,
                    null,
                    null,
                    null);
            default -> throw new IllegalArgumentException("Unsupported portal destination type: " + type);
        };
    }
}
