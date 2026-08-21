package com.community.servercore.neoforge;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Prevents player teleport commands/portals from being used as PvP escape buttons. */
final class GamingCastleCombatTracker {
    private static final long COMBAT_TAG_MILLIS = 10_000L;
    private static final Map<UUID, Long> TAGGED_UNTIL = new ConcurrentHashMap<>();

    @SubscribeEvent
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)
                || !(event.getSource().getEntity() instanceof ServerPlayer attacker)
                || attacker.getUUID().equals(victim.getUUID())) {
            return;
        }
        long until = System.currentTimeMillis() + COMBAT_TAG_MILLIS;
        boolean attackerWasTagged = tagged(attacker);
        boolean victimWasTagged = tagged(victim);
        TAGGED_UNTIL.put(attacker.getUUID(), until);
        TAGGED_UNTIL.put(victim.getUUID(), until);
        if (!attackerWasTagged) {
            attacker.displayClientMessage(Component.literal("Combat tagged for 10s - teleporting is disabled.")
                    .withStyle(ChatFormatting.RED), true);
        }
        if (!victimWasTagged) {
            victim.displayClientMessage(Component.literal("Combat tagged for 10s - teleporting is disabled.")
                    .withStyle(ChatFormatting.RED), true);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        TAGGED_UNTIL.remove(event.getEntity().getUUID());
    }

    static boolean tagged(ServerPlayer player) {
        Long until = TAGGED_UNTIL.get(player.getUUID());
        if (until == null) return false;
        if (until <= System.currentTimeMillis()) {
            TAGGED_UNTIL.remove(player.getUUID(), until);
            return false;
        }
        return true;
    }

    static boolean teleportBlocked(ServerPlayer player) {
        return tagged(player) || GamingCastleDuels.isDueling(player.getUUID());
    }

    static String blockReason(ServerPlayer player) {
        return GamingCastleDuels.isDueling(player.getUUID())
                ? "You cannot teleport out of an active duel. Use /duel leave to forfeit."
                : "You cannot teleport while combat tagged. Wait a few seconds without PvP damage.";
    }
}
