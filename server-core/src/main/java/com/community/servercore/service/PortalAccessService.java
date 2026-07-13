package com.community.servercore.service;

import com.community.servercore.portal.Portal;

import java.util.Objects;
import java.util.UUID;

@FunctionalInterface
public interface PortalAccessService {
    boolean hasPermission(UUID playerId, String permission);

    default boolean canUse(UUID playerId, Portal portal) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(portal, "portal");
        return portal.permission().isBlank() || hasPermission(playerId, portal.permission());
    }
}
