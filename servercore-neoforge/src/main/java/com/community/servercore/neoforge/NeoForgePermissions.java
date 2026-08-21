package com.community.servercore.neoforge;

import com.community.servercore.command.EconomyCommandService;
import com.community.servercore.command.PortalCommandService;
import com.community.servercore.command.RoleCommandService;
import com.community.servercore.staff.LocalRoleStore;
import com.community.servercore.staff.StaffRole;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class NeoForgePermissions {
    static final String STAFF_PERMISSION = "servercore.staff";

    private static final UUID DEVELOPMENT_PLAYER_UUID =
            UUID.fromString("ddd126d4-deb9-4e42-9a63-355f0571a966");

    private static final Map<String, PermissionNode<Boolean>> NODES = new LinkedHashMap<>();
    private static volatile LocalRoleStore roleStore;

    static {
        for (StaffRole role : StaffRole.values()) node(role.permission(), false);
        node(STAFF_PERMISSION, false);
        node(PortalCommandService.ADMIN_PERMISSION, false);
        // Player-facing permissions are available by default; FTB Ranks can still override them.
        node(EconomyCommandService.USE_PERMISSION, true);
        node(EconomyCommandService.ADMIN_PERMISSION, false);
        node(EconomyCommandService.MODERATION_PERMISSION, false);
        node(RoleCommandService.VIEW_PERMISSION, true);
        node(RoleCommandService.MANAGE_PERMISSION, false);
        node(RoleCommandService.DEV_AREA_PERMISSION, false);
        node(RoleCommandService.ADMIN_AREA_PERMISSION, false);
    }

    static void setRoleStore(LocalRoleStore store) {
        roleStore = store;
    }

    static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(NODES.values().toArray(new PermissionNode[0]));
    }

    static boolean check(ServerPlayer player, String permission) {
        if (DEVELOPMENT_PLAYER_UUID.equals(player.getUUID()) && permission.startsWith("servercore.")) {
            if (permission.startsWith("servercore.role.")) {
                return StaffRole.DEVELOPER.permission().equals(permission);
            }
            return true;
        }

        LocalRoleStore store = roleStore;
        if (store != null) {
            if (STAFF_PERMISSION.equals(permission) && !store.rolesFor(player.getUUID()).isEmpty()) {
                return true;
            }
            if (store.has(player.getUUID(), permission)) {
                return true;
            }
        }

        PermissionNode<Boolean> node = NODES.get(permission);
        if (node != null) return Boolean.TRUE.equals(PermissionAPI.getPermission(player, node));
        return player.hasPermissions(2);
    }

    private static void node(String dotted, boolean defaultForPlayers) {
        String[] parts = dotted.split("\\.", 2);
        String namespace = parts.length == 2 ? parts[0] : "servercore";
        String path = parts.length == 2 ? parts[1] : dotted;
        NODES.put(dotted, new PermissionNode<>(namespace, path, PermissionTypes.BOOLEAN,
                (player, uuid, ctx) -> player != null
                        && (defaultForPlayers || player.hasPermissions(2))));
    }

    private NeoForgePermissions() {}
}
