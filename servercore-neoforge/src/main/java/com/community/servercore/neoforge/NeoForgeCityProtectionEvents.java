package com.community.servercore.neoforge;

import com.community.servercore.ServerCoreRuntime;
import com.community.servercore.staff.StaffRole;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.List;
import java.util.function.Supplier;

/** Protects Gaming Castle infrastructure from non-staff block breaking. */
final class NeoForgeCityProtectionEvents {
    private static final List<ProtectedRegion> PROTECTED_REGIONS = List.of(
            // Main Gaming Castle realm city.
            new ProtectedRegion(-265, -25, -70, 185),
            // Market District.
            new ProtectedRegion(1440, 1560, -60, 60),
            // Duels Arena.
            new ProtectedRegion(-1565, -1435, -65, 65),
            // Staff Lounge.
            new ProtectedRegion(-52, 52, -1552, -1444),
            // Survival landing infrastructure only. The rest of Survival stays buildable.
            new ProtectedRegion(-20, 20, 1468, 1540));

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

        if (!isProtected(event.getPos())) {
            return;
        }

        ServerCoreRuntime current = runtimeSupplier.get();
        if (current != null && isStaff(current, player)) {
            return;
        }

        event.setCanceled(true);
        player.displayClientMessage(
                Component.literal("Gaming Castle infrastructure is protected. Only staff can break blocks here."),
                true);
    }

    private static boolean isProtected(BlockPos pos) {
        for (ProtectedRegion region : PROTECTED_REGIONS) {
            if (region.contains(pos)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isStaff(ServerCoreRuntime runtime, ServerPlayer player) {
        for (StaffRole role : StaffRole.values()) {
            if (runtime.roleStore().has(player.getUUID(), role.permission())) {
                return true;
            }
        }
        return false;
    }

    private record ProtectedRegion(int minX, int maxX, int minZ, int maxZ) {
        private boolean contains(BlockPos pos) {
            return pos.getX() >= minX
                    && pos.getX() <= maxX
                    && pos.getZ() >= minZ
                    && pos.getZ() <= maxZ;
        }
    }
}
