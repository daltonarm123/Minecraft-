package com.community.servercore.service;

import java.util.UUID;

public final class AllowAllPortalAccessService implements PortalAccessService {
    @Override
    public boolean hasPermission(UUID playerId, String permission) {
        return true;
    }
}
