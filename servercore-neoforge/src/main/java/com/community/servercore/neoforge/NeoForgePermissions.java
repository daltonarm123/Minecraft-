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
    private static final UUID DEVELOPMENT_PLAYER_UUID =
            UUID.fromString("ddd126d4-deb9-4e42-9a63-355f0571a966");

    private static final Map<String, PermissionNode<Boolean>> NODES = new LinkedHashMap<>();
    // Set once on server start; allows /role give to work without a separate permissions mod
    private static volatile LocalRoleStore roleStore;

    static {
        for (StaffRole role : StaffRole.values()) node(role.permission());
        node(PortalCommandService.ADMIN_PERMISSION); // servercore.admin — portal management
        node(EconomyCommandService.USE_PERMISSION);
        node(EconomyCommandService.ADMIN_PERMISSION);
        node(EconomyCommandService.MODERATION_PERMISSION);
        node(RoleCommandService.VIEW_PERMISSION);
        node(RoleCommandService.MANAGE_PERMISSION);
        node(RoleCommandService.DEV_AREA_PERMISSION);
        node(RoleCommandService.ADMIN_AREA_PERMISSION);
    }

    static void setRoleStore(LocalRoleStore store) {
        roleStore = store;
    }

    static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(NODES.values().toArray(new PermissionNode[0]));
    }

    static boolean check(ServerPlayer player, String permission) {
        // Private ATM10 development bootstrap: SoaREnvy should display only as Developer,
        // but can exercise all non-role ServerCore permissions while features are being tested.
        // This must run before PermissionAPI because the dev account is temporarily OP'd and
        // the default resolver would otherwise make every staff role (including Owner) true.
        if (DEVELOPMENT_PLAYER_UUID.equals(player.getUUID()) && permission.startsWith("servercore.")) {
            if (permission.startsWith("servercore.role.")) {
                return StaffRole.DEVELOPER.permission().equals(permission);
            }
            return true;
        }

        // Local store grants (from /role give) take priority
        LocalRoleStore store = roleStore;
        if (store != null && store.has(player.getUUID(), permission)) return true;

        // Fall through to FTB Ranks / other permission mods via NeoForge PermissionAPI
        PermissionNode<Boolean> node = NODES.get(permission);
        if (node != null) return Boolean.TRUE.equals(PermissionAPI.getPermission(player, node));
        return player.hasPermissions(2);
    }

    private static void node(String dotted) {
        String[] parts = dotted.split("\\.", 2);
        String namespace = parts.length == 2 ? parts[0] : "servercore";
        String path = parts.length == 2 ? parts[1] : dotted;
        // Default resolver: grant to op level 2 when no permission mod is present
        NODES.put(dotted, new PermissionNode<>(namespace, path, PermissionTypes.BOOLEAN,
                (player, uuid, ctx) -> player != null && player.hasPermissions(2)));
    }

    private NeoForgePermissions() {}
}
