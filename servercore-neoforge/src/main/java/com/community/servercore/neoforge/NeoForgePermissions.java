package com.community.servercore.neoforge;

import com.community.servercore.command.EconomyCommandService;
import com.community.servercore.command.RoleCommandService;
import com.community.servercore.staff.StaffRole;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

import java.util.LinkedHashMap;
import java.util.Map;

final class NeoForgePermissions {
    // All permission nodes keyed by their dotted string so hasPermission() can look them up.
    private static final Map<String, PermissionNode<Boolean>> NODES = new LinkedHashMap<>();

    static {
        // StaffRole nodes — these are the ones FTB Ranks assigns per-player
        for (StaffRole role : StaffRole.values()) {
            node(role.permission());
        }
        // Economy nodes
        node(EconomyCommandService.USE_PERMISSION);
        node(EconomyCommandService.ADMIN_PERMISSION);
        node(EconomyCommandService.MODERATION_PERMISSION);
        // Role view nodes
        node(RoleCommandService.VIEW_PERMISSION);
        node(RoleCommandService.DEV_AREA_PERMISSION);
        node(RoleCommandService.ADMIN_AREA_PERMISSION);
    }

    static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(NODES.values().toArray(new PermissionNode[0]));
    }

    static boolean check(ServerPlayer player, String permission) {
        PermissionNode<Boolean> node = NODES.get(permission);
        if (node != null) {
            return Boolean.TRUE.equals(PermissionAPI.getPermission(player, node));
        }
        // Unknown node — fall back to op level 2 so the server isn't locked open
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
