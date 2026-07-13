package com.community.servercore.selection;

public record WorldPosition(String world, double x, double y, double z) {
    public WorldPosition {
        if (world == null || world.isBlank()) {
            throw new IllegalArgumentException("world must not be blank");
        }
        world = world.trim();
        validateFinite(x, "x");
        validateFinite(y, "y");
        validateFinite(z, "z");
    }

    private static void validateFinite(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
    }
}
