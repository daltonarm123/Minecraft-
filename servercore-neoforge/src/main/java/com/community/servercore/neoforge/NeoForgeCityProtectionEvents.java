package com.community.servercore.neoforge;

import com.community.servercore.ServerCoreRuntime;
import com.community.servercore.staff.StaffRole;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.function.Supplier;

/** Protects the Gaming Castle spawn city from non-staff block breaking. */
final class NeoForgeCityProtectionEvents {
    // Realm City footprint centered around the Gaming Castle spawn hub.
    // Intentionally protects the entire vertical column so players cannot tunnel
    // underneath the city and damage it from below.
    private static final int MIN_X = -265;
    private static final int MAX_X = -25;
    private static final int MIN_Z = -70;
    private static final int MAX_Z = 185;

    private final Supplier<ServerCoreRuntime> runtimeSupplier;

    NeoForgeCityProtectionEvents(Supplier<ServerCoreRuntime> runtimeSupplier) {
        this.runtimeSupplier = runtimeSupplier;
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (!Level.OVERWORLD.equals(player.level().dimension())) {
            return;
        }

        BlockPos pos = event.getPos();
        if (!isInsideGamingCastle(pos)) {
            return;
        }

        ServerCoreRuntime current = runtimeSupplier.get();
        if (current != null && isStaff(current, player)) {
            return;
        }

        event.setCanceled(true);
        player.displayClientMessage(
                Component.literal("Gaming Castle is protected. Only staff can break blocks here."),
                true);
    }

    private static boolean isInsideGamingCastle(BlockPos pos) {
        return pos.getX() >= MIN_X
                && pos.getX() <= MAX_X
                && pos.getZ() >= MIN_Z
                && pos.getZ() <= MAX_Z;
    }

    private static boolean isStaff(ServerCoreRuntime runtime, ServerPlayer player) {
        for (StaffRole role : StaffRole.values()) {
            if (runtime.roleStore().has(player.getUUID(), role.permission())) {
                return true;
            }
        }
        return false;
    }
}
