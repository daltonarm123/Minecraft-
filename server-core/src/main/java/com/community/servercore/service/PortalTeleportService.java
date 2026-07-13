package com.community.servercore.service;

import com.community.servercore.portal.PortalDestination;

import java.util.UUID;

@FunctionalInterface
public interface PortalTeleportService {
    TeleportResult teleport(UUID playerId, PortalDestination destination);
}
