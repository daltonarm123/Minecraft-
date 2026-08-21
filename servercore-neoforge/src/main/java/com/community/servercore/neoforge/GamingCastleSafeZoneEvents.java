package com.community.servercore.neoforge;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.List;

/** Keeps non-duel Gaming Castle infrastructure safe from PvP and entity attacks. */
final class GamingCastleSafeZoneEvents {
    private static final List<SafeRegion> SAFE_REGIONS = List.of(
            new SafeRegion("Gaming Castle", -265, -25, -70, 185),
            new SafeRegion("Market District", 1440, 1560, -60, 60),
            new SafeRegion("Staff Lounge", -52, 52, -1552, -1444),
            new SafeRegion("Survival Landing", -20, 20, 1468, 1540));

    @SubscribeEvent
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)
                || !Level.OVERWORLD.equals(victim.level().dimension())) {
            return;
        }

        SafeRegion region = regionAt(victim);
        if (region == null) {
            return;
        }

        Entity attacker = event.getSource().getEntity();
        if (attacker == null || attacker.getUUID().equals(victim.getUUID())) {
            return;
        }

        event.setCanceled(true);
        if (attacker instanceof ServerPlayer player) {
            player.displayClientMessage(
                    Component.literal("PvP is disabled in " + region.name() + ". Use the Duels District for PvP."),
                    true);
        }
    }

    private static SafeRegion regionAt(ServerPlayer player) {
        int x = player.blockPosition().getX();
        int z = player.blockPosition().getZ();
        for (SafeRegion region : SAFE_REGIONS) {
            if (region.contains(x, z)) {
                return region;
            }
        }
        return null;
    }

    private record SafeRegion(String name, int minX, int maxX, int minZ, int maxZ) {
        private boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
    }
}
