package com.community.servercore.command;

import com.community.servercore.portal.PortalDestination;

import java.util.Objects;

public final class StaffAreaCommandService {
    public static final String DEV_PORTAL_NAME = "dev_area";
    public static final String ADMIN_PORTAL_NAME = "admin_lounge";

    private final PortalCommandService portalCommands;

    public StaffAreaCommandService(PortalCommandService portalCommands) {
        this.portalCommands = Objects.requireNonNull(portalCommands, "portalCommands");
    }

    public CommandResult createDevAreaPortal(CommandActor actor, String displayName, PortalDestination destination) {
        return portalCommands.create(
                actor,
                DEV_PORTAL_NAME,
                displayName == null || displayName.isBlank() ? "Developer Test Area" : displayName,
                destination,
                RoleCommandService.DEV_AREA_PERMISSION);
    }

    public CommandResult createAdminLoungePortal(CommandActor actor, String displayName, PortalDestination destination) {
        return portalCommands.create(
                actor,
                ADMIN_PORTAL_NAME,
                displayName == null || displayName.isBlank() ? "Admin Lounge" : displayName,
                destination,
                RoleCommandService.ADMIN_AREA_PERMISSION);
    }
}
