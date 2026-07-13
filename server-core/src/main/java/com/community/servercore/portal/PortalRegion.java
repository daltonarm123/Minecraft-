package com.community.servercore.portal;

public record PortalRegion(
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ) {

    public PortalRegion {
        validateFinite(minX, "minX");
        validateFinite(minY, "minY");
        validateFinite(minZ, "minZ");
        validateFinite(maxX, "maxX");
        validateFinite(maxY, "maxY");
        validateFinite(maxZ, "maxZ");

        double normalizedMinX = Math.min(minX, maxX);
        double normalizedMaxX = Math.max(minX, maxX);
        double normalizedMinY = Math.min(minY, maxY);
        double normalizedMaxY = Math.max(minY, maxY);
        double normalizedMinZ = Math.min(minZ, maxZ);
        double normalizedMaxZ = Math.max(minZ, maxZ);

        minX = normalizedMinX;
        maxX = normalizedMaxX;
        minY = normalizedMinY;
        maxY = normalizedMaxY;
        minZ = normalizedMinZ;
        maxZ = normalizedMaxZ;
    }

    public boolean contains(double x, double y, double z) {
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public boolean overlaps(PortalRegion other) {
        return minX <= other.maxX && maxX >= other.minX
                && minY <= other.maxY && maxY >= other.minY
                && minZ <= other.maxZ && maxZ >= other.minZ;
    }

    private static void validateFinite(double value, String fieldName) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite");
        }
    }
}
