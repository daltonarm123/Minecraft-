package com.community.servercore.neoforge;

import com.community.servercore.ServerCoreRuntime;
import com.community.servercore.service.PortalUseResult;
import com.community.servercore.service.PortalUseStatus;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;

@Mod(ServerCoreMod.MOD_ID)
public final class ServerCoreMod {
    public static final String MOD_ID = "servercore";
    private static final Logger LOGGER = LogUtils.getLogger();

    private volatile MinecraftServer server;
    private volatile ServerCoreRuntime runtime;

    public ServerCoreMod(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.register(this);
        LOGGER.info("ServerCore NeoForge 1.21.1 test adapter loaded");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        server = event.getServer();
        try {
            runtime = ServerCoreRuntime.bootstrap(
                    Path.of("config", "servercore"),
                    new NeoForgePortalAccessService(() -> server),
                    new NeoForgePortalTeleportService(() -> server));
            LOGGER.info(
                    "ServerCore started with {} configured portals",
                    runtime.portals().list().size());
        } catch (IOException exception) {
            LOGGER.error("ServerCore failed to start", exception);
            runtime = null;
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ServerCoreCommands.register(event, () -> runtime);
        AccountLinkCommands.register(event);
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        ServerCoreRuntime current = runtime;
        if (current == null || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % current.config().portalCheckIntervalTicks() != 0) {
            return;
        }

        PortalUseResult result = current.portals().tryUse(
                player.getUUID(),
                player.level().dimension().location().toString(),
                player.getX(),
                player.getY(),
                player.getZ());
        if (result.status() == PortalUseStatus.SUCCESS && !result.message().isBlank()) {
            player.sendSystemMessage(Component.literal(result.message()));
        } else if (result.status() == PortalUseStatus.TELEPORT_FAILED) {
            player.sendSystemMessage(Component.literal(result.message()));
            LOGGER.warn("Portal teleport failed for {}: {}", player.getName().getString(), result.message());
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("ServerCore stopping");
        runtime = null;
        server = null;
    }
}
