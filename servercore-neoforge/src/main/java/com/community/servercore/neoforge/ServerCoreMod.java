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
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
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
    private static final Path DATA_DIRECTORY = Path.of("config", "servercore");

    private static final UUID DEVELOPMENT_PLAYER_UUID =
            UUID.fromString("ddd126d4-deb9-4e42-9a63-355f0571a966");

    private static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, MOD_ID);
    public static final DeferredHolder<MenuType<?>, MenuType<NeoForgeShopMenu>> SHOP_MENU =
            MENU_TYPES.register("shop", () -> IMenuTypeExtension.create(NeoForgeShopMenu::new));

    private volatile MinecraftServer server;
    private volatile ServerCoreRuntime runtime;
    private volatile GamingCastleDataStore communityData;
    private final GamingCastleStaffTools staffTools;
    private final GamingCastleDuels duels;

    public ServerCoreMod(IEventBus modEventBus) {
        this.staffTools = new GamingCastleStaffTools(() -> communityData);
        this.duels = new GamingCastleDuels(() -> runtime);

        MENU_TYPES.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(NeoForgePermissions::onGatherNodes);
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new NeoForgeCombatEvents(() -> runtime));
        NeoForge.EVENT_BUS.register(new NeoForgePlayerDisplayEvents());
        NeoForge.EVENT_BUS.register(new NeoForgeCityProtectionEvents(() -> runtime));
        NeoForge.EVENT_BUS.register(new GamingCastlePlayerEvents(() -> runtime, () -> communityData));
        NeoForge.EVENT_BUS.register(staffTools);
        NeoForge.EVENT_BUS.register(duels);
        LOGGER.info("ServerCore NeoForge adapter loaded");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        server = event.getServer();
        try {
            runtime = ServerCoreRuntime.bootstrap(
                    DATA_DIRECTORY,
                    new NeoForgePortalAccessService(() -> server),
                    new NeoForgePortalTeleportService(() -> server));
            NeoForgePermissions.setRoleStore(runtime.roleStore());

            try {
                communityData = new GamingCastleDataStore(DATA_DIRECTORY.resolve("community-player-data.json"));
            } catch (IOException exception) {
                communityData = null;
                LOGGER.error("Unable to load Gaming Castle community player data", exception);
            }

            try {
                GamingCastlePortalBootstrap.ensure(runtime);
            } catch (IOException exception) {
                LOGGER.error("Unable to configure the managed Gaming Castle portal network", exception);
            }

            GamingCastleDuelBootstrap.ensure(runtime);

            LOGGER.info(
                    "ServerCore started with {} configured portals and {} duel arena(s)",
                    runtime.portals().list().size(),
                    runtime.arenas().list().size());
        } catch (IOException exception) {
            LOGGER.error("ServerCore failed to start", exception);
            runtime = null;
            communityData = null;
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ServerCoreCommands.register(event, () -> runtime);
        GamingCastleEssentialsCommands.register(event, () -> runtime, () -> communityData);
        staffTools.registerCommands(event);
        duels.registerCommands(event);
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
        communityData = null;
        runtime = null;
        server = null;
    }
}
