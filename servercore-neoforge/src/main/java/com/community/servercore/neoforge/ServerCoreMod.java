package com.community.servercore.neoforge;

import com.community.servercore.ServerCoreRuntime;
import com.community.servercore.service.PortalUseResult;
import com.community.servercore.service.PortalUseStatus;
import com.community.servercore.staff.StaffRole;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

@Mod(ServerCoreMod.MOD_ID)
public final class ServerCoreMod {
    public static final String MOD_ID = "servercore";
    private static final Logger LOGGER = LogUtils.getLogger();

    // Development bootstrap account used by the private ATM10 test instance.
    private static final UUID DEVELOPMENT_PLAYER_UUID =
            UUID.fromString("ddd126d4-deb9-4e42-9a63-355f0571a966");

    private static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, MOD_ID);
    public static final DeferredHolder<MenuType<?>, MenuType<NeoForgeShopMenu>> SHOP_MENU =
            MENU_TYPES.register("shop", () -> IMenuTypeExtension.create(NeoForgeShopMenu::new));

    private volatile MinecraftServer server;
    private volatile ServerCoreRuntime runtime;

    public ServerCoreMod(IEventBus modEventBus) {
        MENU_TYPES.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(NeoForgePermissions::onGatherNodes);
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new NeoForgeCombatEvents(() -> runtime));
        NeoForge.EVENT_BUS.register(new NeoForgePlayerDisplayEvents());
        NeoForge.EVENT_BUS.register(new NeoForgeCityProtectionEvents(() -> runtime));
        LOGGER.info("ServerCore NeoForge adapter loaded");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        server = event.getServer();
        try {
            runtime = ServerCoreRuntime.bootstrap(
                    Path.of("config", "servercore"),
                    new NeoForgePortalAccessService(() -> server),
                    new NeoForgePortalTeleportService(() -> server));
            NeoForgePermissions.setRoleStore(runtime.roleStore());
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
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !DEVELOPMENT_PLAYER_UUID.equals(player.getUUID())) {
            return;
        }

        ServerCoreRuntime current = runtime;
        MinecraftServer currentServer = server;
        if (current == null || currentServer == null) {
            return;
        }

        try {
            current.roleStore().grant(player.getUUID(), StaffRole.DEVELOPER);
        } catch (IOException exception) {
            LOGGER.error("Failed to grant Developer role to {}", player.getName().getString(), exception);
        }

        // The current command tree still uses vanilla GameMaster gates at the top level.
        // OP the designated development account so every ServerCore command is available
        // while we finish migrating those gates to ServerCore permission nodes.
        if (!currentServer.getPlayerList().isOp(player.getGameProfile())) {
            currentServer.getPlayerList().op(player.getGameProfile());
            currentServer.getPlayerList().sendPlayerPermissionLevel(player);
        }

        NeoForgePlayerDisplayEvents.refreshPlayerTeam(player);
        LOGGER.info("Development permissions enabled for {}", player.getName().getString());
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
