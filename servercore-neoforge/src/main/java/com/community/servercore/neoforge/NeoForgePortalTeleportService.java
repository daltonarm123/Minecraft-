package com.community.servercore.neoforge;

import com.community.servercore.portal.DestinationType;
import com.community.servercore.portal.PortalDestination;
import com.community.servercore.service.PortalTeleportService;
import com.community.servercore.service.TeleportResult;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

final class NeoForgePortalTeleportService implements PortalTeleportService {
    private final Supplier<MinecraftServer> serverSupplier;

    NeoForgePortalTeleportService(Supplier<MinecraftServer> serverSupplier) {
        this.serverSupplier = Objects.requireNonNull(serverSupplier, "serverSupplier");
    }

    @Override
    public TeleportResult teleport(UUID playerId, PortalDestination destination) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(destination, "destination");
        MinecraftServer server = serverSupplier.get();
        if (server == null) {
            return TeleportResult.failure("The Minecraft server is not available.");
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return TeleportResult.failure("The player is no longer online.");
        }
        if (destination.type() != DestinationType.LOCATION) {
            return TeleportResult.failure(
                    destination.type() + " destinations require a later resolver or proxy integration.");
        }

        try {
            Identifier dimensionId = parseDimension(destination.target());
            ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, dimensionId);
            ServerLevel destinationLevel = server.getLevel(dimensionKey);
            if (destinationLevel == null) {
                return TeleportResult.failure("Unknown destination world: " + destination.target());
            }

            double x = destination.x();
            double y = destination.y();
            double z = destination.z();
            float yaw = destination.yaw() == null ? player.getYRot() : destination.yaw();
            float pitch = destination.pitch() == null ? player.getXRot() : destination.pitch();
            player.teleportTo(destinationLevel, x, y, z, Set.of(), yaw, pitch, false);
            return TeleportResult.success("Teleported to " + dimensionId + ".");
        } catch (RuntimeException exception) {
            return TeleportResult.failure("Teleport failed: " + exception.getMessage());
        }
    }

    private static Identifier parseDimension(String target) {
        return target.contains(":")
                ? Identifier.parse(target)
                : Identifier.withDefaultNamespace(target);
    }
}
