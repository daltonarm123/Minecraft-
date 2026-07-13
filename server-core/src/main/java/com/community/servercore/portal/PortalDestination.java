package com.community.servercore.portal;

import java.util.Objects;
import java.util.Optional;

public record PortalDestination(
        DestinationType type,
        String target,
        Double x,
        Double y,
        Double z,
        Float yaw,
        Float pitch) {

    public PortalDestination {
        Objects.requireNonNull(type, "type");
        target = requireText(target, "target");
        validateCoordinate(x, "x");
        validateCoordinate(y, "y");
        validateCoordinate(z, "z");
        validateCoordinate(yaw, "yaw");
        validateCoordinate(pitch, "pitch");

        if (type == DestinationType.LOCATION && (x == null || y == null || z == null)) {
            throw new IllegalArgumentException("LOCATION destinations require x, y, and z");
        }
    }

    public static PortalDestination world(String worldName) {
        return new PortalDestination(DestinationType.WORLD, worldName, null, null, null, null, null);
    }

    public static PortalDestination location(String worldName, double x, double y, double z, float yaw, float pitch) {
        return new PortalDestination(DestinationType.LOCATION, worldName, x, y, z, yaw, pitch);
    }

    public Optional<Double> optionalX() {
        return Optional.ofNullable(x);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static void validateCoordinate(Number value, String field) {
        if (value != null && !Double.isFinite(value.doubleValue())) {
            throw new IllegalArgumentException(field + " must be finite");
        }
    }
}
