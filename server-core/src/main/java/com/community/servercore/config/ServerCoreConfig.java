package com.community.servercore.config;

import java.util.Objects;

public record ServerCoreConfig(
        boolean debugMode,
        int portalCheckIntervalTicks,
        String portalFile,
        boolean createBackups,
        int maximumPortals,
        boolean allowOverlappingPortals,
        int defaultCooldownSeconds,
        boolean logPortalUsage,
        String apiBaseUrl,
        int apiTimeoutSeconds) {

    public ServerCoreConfig {
        if (portalCheckIntervalTicks < 1 || portalCheckIntervalTicks > 200) {
            throw new IllegalArgumentException("portalCheckIntervalTicks must be between 1 and 200");
        }
        portalFile = requireText(portalFile, "portalFile");
        if (maximumPortals < 1 || maximumPortals > 10_000) {
            throw new IllegalArgumentException("maximumPortals must be between 1 and 10000");
        }
        if (defaultCooldownSeconds < 0 || defaultCooldownSeconds > 3_600) {
            throw new IllegalArgumentException("defaultCooldownSeconds must be between 0 and 3600");
        }
        apiBaseUrl = Objects.requireNonNullElse(apiBaseUrl, "").trim();
        if (!apiBaseUrl.isEmpty()
                && !(apiBaseUrl.startsWith("http://") || apiBaseUrl.startsWith("https://"))) {
            throw new IllegalArgumentException("apiBaseUrl must be blank or use http/https");
        }
        if (apiTimeoutSeconds < 1 || apiTimeoutSeconds > 120) {
            throw new IllegalArgumentException("apiTimeoutSeconds must be between 1 and 120");
        }
    }

    public static ServerCoreConfig defaults() {
        return new ServerCoreConfig(
                false,
                5,
                "portals.json",
                true,
                100,
                false,
                3,
                true,
                "",
                10);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
