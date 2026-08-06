package com.community.servercore.neoforge;

import com.community.servercore.ServerCoreRuntime;
import com.community.servercore.audit.AuditEvent;
import com.community.servercore.audit.AuditEventType;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.function.Supplier;

final class NeoForgeCombatEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Supplier<ServerCoreRuntime> runtimeSupplier;

    NeoForgeCombatEvents(Supplier<ServerCoreRuntime> runtimeSupplier) {
        this.runtimeSupplier = Objects.requireNonNull(runtimeSupplier, "runtimeSupplier");
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerCoreRuntime runtime = runtimeSupplier.get();
        if (runtime == null) {
            return;
        }
        runtime.audit().publish(AuditEvent.system(
                AuditEventType.PLAYER_KILLED,
                java.time.Clock.systemUTC().instant(),
                "Combat event captured for " + player.getGameProfile().getName()));
        LOGGER.debug("Captured combat event for {}", player.getGameProfile().getName());
    }
}
