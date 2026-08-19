package com.community.servercore.neoforge;

import com.community.servercore.staff.StaffRole;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

final class NeoForgePlayerDisplayEvents {

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        setupTeams(event.getServer().getScoreboard());
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            refreshPlayerTeam(player);
        }
    }

    // Called after /role give or /role revoke so the change takes effect immediately.
    static void refreshPlayerTeam(ServerPlayer player) {
        Scoreboard scoreboard = player.server.getScoreboard();
        // Remove from any existing servercore rank teams first
        for (StaffRole role : StaffRole.values()) {
            PlayerTeam team = scoreboard.getPlayerTeam(teamName(role));
            if (team != null) scoreboard.removePlayerFromTeam(player.getScoreboardName(), team);
        }
        // Assign to the highest role the player holds
        for (StaffRole role : StaffRole.values()) {
            if (NeoForgePermissions.check(player, role.permission())) {
                PlayerTeam team = scoreboard.getPlayerTeam(teamName(role));
                if (team != null) scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
                return;
            }
        }
    }

    private static void setupTeams(Scoreboard scoreboard) {
        for (StaffRole role : StaffRole.values()) {
            String name = teamName(role);
            if (scoreboard.getPlayerTeam(name) == null) {
                PlayerTeam team = scoreboard.addPlayerTeam(name);
                team.setDisplayName(Component.literal(role.displayName()));
                team.setPlayerPrefix(rolePrefix(role));
            }
        }
    }

    private static String teamName(StaffRole role) {
        return "sc_" + role.name().toLowerCase();
    }

    private static Component rolePrefix(StaffRole role) {
        ChatFormatting color = switch (role) {
            case OWNER -> ChatFormatting.GOLD;
            case ADMIN -> ChatFormatting.RED;
            case DEVELOPER -> ChatFormatting.AQUA;
            case SUPPORT -> ChatFormatting.GREEN;
            case MODERATOR -> ChatFormatting.BLUE;
        };
        return Component.literal("[" + role.displayName() + "] ").withStyle(color);
    }

    NeoForgePlayerDisplayEvents() {}
}
