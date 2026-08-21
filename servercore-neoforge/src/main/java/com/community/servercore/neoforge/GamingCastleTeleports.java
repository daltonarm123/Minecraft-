package com.community.servercore.neoforge;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Set;

final class GamingCastleTeleports {
    static final GamingCastleDataStore.SavedLocation HUB =
            new GamingCastleDataStore.SavedLocation("minecraft:overworld", -145.0, 67.0, 70.0, 180.0F, 0.0F);

    private GamingCastleTeleports() { }

    static GamingCastleDataStore.SavedLocation capture(ServerPlayer player) {
        return new GamingCastleDataStore.SavedLocation(
                player.level().dimension().location().toString(),
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYRot(),
                player.getXRot());
    }

    static boolean teleport(MinecraftServer server, ServerPlayer player, GamingCastleDataStore.SavedLocation destination) {
        if (server == null || player == null || destination == null) {
            return false;
        }
        ResourceLocation id = destination.dimension().contains(":")
                ? ResourceLocation.parse(destination.dimension())
                : ResourceLocation.withDefaultNamespace(destination.dimension());
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
        ServerLevel level = server.getLevel(key);
        if (level == null) {
            return false;
        }
        player.teleportTo(
                level,
                destination.x(),
                destination.y(),
                destination.z(),
                Set.of(),
                destination.yaw(),
                destination.pitch());
        return true;
    }
}
