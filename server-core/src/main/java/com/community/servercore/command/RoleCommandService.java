package com.community.servercore.command;

import com.community.servercore.staff.StaffRole;

import java.util.ArrayList;
import java.util.List;

public final class RoleCommandService {
    public static final String VIEW_PERMISSION = "servercore.roles.view";
    public static final String DEV_AREA_PERMISSION = "servercore.area.dev";
    public static final String ADMIN_AREA_PERMISSION = "servercore.area.admin";

    public CommandResult listRoles(CommandActor actor) {
        if (!authorized(actor)) {
            return CommandResult.failure("You do not have permission to view role definitions.");
        }
        List<String> messages = new ArrayList<>();
        messages.add("ServerCore staff roles:");
        for (StaffRole role : StaffRole.values()) {
            messages.add("- " + role.displayName() + " (" + role.permission() + ")");
        }
        return CommandResult.success(messages);
    }

    public CommandResult myRoles(CommandActor actor) {
        if (actor == null) {
            return CommandResult.failure("Actor is required.");
        }
        List<String> active = new ArrayList<>();
        for (StaffRole role : StaffRole.values()) {
            if (actor.hasPermission(role.permission())) {
                active.add(role.displayName());
            }
        }
        if (active.isEmpty()) {
            return CommandResult.success("No explicit staff roles detected.");
        }
        return CommandResult.success("Active staff roles: " + String.join(", ", active));
    }

    public CommandResult areaAccess(CommandActor actor) {
        if (actor == null) {
            return CommandResult.failure("Actor is required.");
        }
        boolean dev = canAccessDevArea(actor);
        boolean admin = canAccessAdminLounge(actor);
        return CommandResult.success(List.of(
                "Dev area access: " + (dev ? "granted" : "denied"),
                "Admin lounge access: " + (admin ? "granted" : "denied")));
    }

    public static boolean canAccessDevArea(CommandActor actor) {
        return actor != null
                && (actor.hasPermission(DEV_AREA_PERMISSION)
                || actor.hasPermission(StaffRole.DEVELOPER.permission())
                || actor.hasPermission(StaffRole.ADMIN.permission())
                || actor.hasPermission(StaffRole.OWNER.permission()));
    }

    public static boolean canAccessAdminLounge(CommandActor actor) {
        return actor != null
                && (actor.hasPermission(ADMIN_AREA_PERMISSION)
                || actor.hasPermission(StaffRole.SUPPORT.permission())
                || actor.hasPermission(StaffRole.MODERATOR.permission())
                || actor.hasPermission(StaffRole.ADMIN.permission())
                || actor.hasPermission(StaffRole.OWNER.permission()));
    }

    private static boolean authorized(CommandActor actor) {
        if (actor == null) {
            return false;
        }
        if (actor.hasPermission(VIEW_PERMISSION)) {
            return true;
        }
        for (StaffRole role : StaffRole.values()) {
            if (actor.hasPermission(role.permission())) {
                return true;
            }
        }
        return false;
    }
}
