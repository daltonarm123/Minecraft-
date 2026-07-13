package com.community.servercore.neoforge;

import com.community.servercore.portal.Portal;
import com.community.servercore.service.PortalAccessService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

final class NeoForgePortalAccessService implements PortalAccessService {
    private final Supplier<MinecraftServer> serverSupplier;

    NeoForgePortalAccessService(Supplier<MinecraftServer> serverSupplier) {
        this.serverSupplier = Objects.requireNonNull(serverSupplier, "serverSupplier");
    }

    @Override
    public boolean canUse(UUID playerId, Portal portal) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(portal, "portal");
        if (portal.permission().isBlank()) {
            return true;
        }
        MinecraftServer server = serverSupplier.get();
        if (server == null) {
            return false;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        return player != null && player.createCommandSourceStack().hasPermission(2);
    }
}
