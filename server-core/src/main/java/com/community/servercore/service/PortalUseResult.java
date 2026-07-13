package com.community.servercore.service;

import com.community.servercore.portal.Portal;

import java.time.Duration;
import java.util.Objects;

public record PortalUseResult(
        PortalUseStatus status,
        Portal portal,
        String message,
        Duration remainingCooldown) {

    public PortalUseResult {
        Objects.requireNonNull(status, "status");
        message = Objects.requireNonNullElse(message, "");
        remainingCooldown = Objects.requireNonNullElse(remainingCooldown, Duration.ZERO);
        if (remainingCooldown.isNegative()) {
            throw new IllegalArgumentException("remainingCooldown must not be negative");
        }
    }

    public boolean successful() {
        return status == PortalUseStatus.SUCCESS;
    }

    public static PortalUseResult noPortal() {
        return new PortalUseResult(PortalUseStatus.NO_PORTAL, null, "No portal found at this location.", Duration.ZERO);
    }

    public static PortalUseResult of(PortalUseStatus status, Portal portal, String message) {
        return new PortalUseResult(status, portal, message, Duration.ZERO);
    }

    public static PortalUseResult cooldown(Portal portal, Duration remaining) {
        return new PortalUseResult(
                PortalUseStatus.COOLDOWN_ACTIVE,
                portal,
                "Portal cooldown is still active.",
                remaining);
    }
}
