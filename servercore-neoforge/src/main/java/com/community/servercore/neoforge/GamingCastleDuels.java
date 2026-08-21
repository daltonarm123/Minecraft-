package com.community.servercore.neoforge;

import com.community.servercore.ServerCoreRuntime;
import com.community.servercore.duel.ArenaDefinition;
import com.community.servercore.duel.DuelMatch;
import com.community.servercore.duel.DuelMode;
import com.community.servercore.duel.DuelStatus;
import com.community.servercore.duel.MatchmakingResult;
import com.community.servercore.duel.MatchmakingStatus;
import com.community.servercore.economy.WalletTransactionType;
import com.community.servercore.player.PlayerProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Connects the core matchmaking engine to the physical Gaming Castle duel arena. */
final class GamingCastleDuels {
    private static final long CASUAL_WIN_REWARD = 100L;
    private static final long RANKED_WIN_REWARD = 250L;

    private static final Map<UUID, InventorySnapshot> SNAPSHOTS = new ConcurrentHashMap<>();
    private static final Map<UUID, GamingCastleDataStore.SavedLocation> RETURN_LOCATIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> PENDING_RESPAWN_RESTORE = new ConcurrentHashMap<>();

    private final Supplier<ServerCoreRuntime> runtimeSupplier;

    GamingCastleDuels(Supplier<ServerCoreRuntime> runtimeSupplier) {
        this.runtimeSupplier = Objects.requireNonNull(runtimeSupplier, "runtimeSupplier");
    }

    void registerCommands(RegisterCommandsEvent event) {
        var duel = Commands.literal("duel");

        duel.then(Commands.literal("join")
                .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            builder.suggest("casual");
                            builder.suggest("ranked");
                            return builder.buildFuture();
                        })
                        .executes(context -> join(
                                context.getSource(),
                                StringArgumentType.getString(context, "mode")))));

        duel.then(Commands.literal("leave")
                .executes(context -> leave(context.getSource())));
        duel.then(Commands.literal("stats")
                .executes(context -> stats(context.getSource())));
        duel.then(Commands.literal("status")
                .executes(context -> status(context.getSource())));
        duel.then(Commands.literal("spectate")
                .executes(context -> spectate(context.getSource())));
        duel.then(Commands.literal("lobby")
                .executes(context -> duelLobby(context.getSource())));

        event.getDispatcher().register(duel);
    }

    static boolean isDueling(UUID playerId) {
        return SNAPSHOTS.containsKey(playerId);
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer loser)) {
            return;
        }
        ServerCoreRuntime runtime = runtimeSupplier.get();
        if (runtime == null) return;

        Optional<DuelMatch> active = runtime.matchmaking().activeMatchFor(loser.getUUID());
        if (active.isEmpty() || active.orElseThrow().status() != DuelStatus.STARTED) {
            return;
        }

        DuelMatch match = active.orElseThrow();
        UUID winnerId = match.opponentOf(loser.getUUID()).orElse(null);
        if (winnerId == null) return;

        ServerPlayer winner = loser.getServer().getPlayerList().getPlayer(winnerId);
        try {
            runtime.matchmaking().complete(match.matchId(), winnerId, 1, 0);
            long reward = match.mode() == DuelMode.RANKED ? RANKED_WIN_REWARD : CASUAL_WIN_REWARD;
            runtime.wallets().credit(
                    winnerId,
                    reward,
                    WalletTransactionType.CREDIT,
                    "duel-win",
                    loser.getUUID(),
                    Map.of("mode", match.mode().name(), "arena", match.arenaId()));

            PENDING_RESPAWN_RESTORE.put(loser.getUUID(), Boolean.TRUE);
            if (winner != null) {
                restoreAndReturn(winner);
                winner.sendSystemMessage(Component.literal(
                                "DUEL VICTORY! +" + reward + " SC")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            }
            loser.sendSystemMessage(Component.literal("Duel lost. Your inventory will be restored on respawn.")
                    .withStyle(ChatFormatting.RED));
        } catch (RuntimeException exception) {
            if (winner != null) {
                winner.sendSystemMessage(Component.literal("Unable to finalize duel: " + exception.getMessage())
                        .withStyle(ChatFormatting.RED));
            }
        }
    }

    @SubscribeEvent
    public void onDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && SNAPSHOTS.containsKey(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onExperienceDrop(LivingExperienceDropEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && SNAPSHOTS.containsKey(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || PENDING_RESPAWN_RESTORE.remove(player.getUUID()) == null) {
            return;
        }
        restoreAndReturn(player);
        player.sendSystemMessage(Component.literal("Your pre-duel inventory has been restored.")
                .withStyle(ChatFormatting.GREEN));
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerCoreRuntime runtime = runtimeSupplier.get();
        if (runtime != null) {
            runtime.matchmaking().activeMatchFor(player.getUUID()).ifPresent(match -> {
                try {
                    runtime.matchmaking().cancel(match.matchId());
                    ServerPlayer opponent = player.getServer().getPlayerList()
                            .getPlayer(match.opponentOf(player.getUUID()).orElse(UUID.randomUUID()));
                    if (opponent != null) {
                        restoreAndReturn(opponent);
                        opponent.sendSystemMessage(Component.literal("Duel cancelled because the opponent disconnected.")
                                .withStyle(ChatFormatting.YELLOW));
                    }
                } catch (RuntimeException ignored) { }
            });
        }
        restoreInventory(player);
        RETURN_LOCATIONS.remove(player.getUUID());
        PENDING_RESPAWN_RESTORE.remove(player.getUUID());
    }

    private int join(CommandSourceStack source, String modeName)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerCoreRuntime runtime = runtimeSupplier.get();
        if (runtime == null) {
            source.sendFailure(Component.literal("ServerCore has not finished starting."));
            return 0;
        }
        DuelMode mode;
        try {
            mode = DuelMode.valueOf(modeName.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal("Mode must be casual or ranked."));
            return 0;
        }

        MatchmakingResult result = runtime.matchmaking().join(
                player.getUUID(),
                player.getName().getString(),
                mode);
        player.sendSystemMessage(Component.literal(result.message())
                .withStyle(result.status() == MatchmakingStatus.MATCHED
                        ? ChatFormatting.GREEN
                        : ChatFormatting.YELLOW));
        if (result.status() == MatchmakingStatus.MATCHED) {
            return startMatch(source.getServer(), runtime, result.match()) ? 1 : 0;
        }
        return result.status() == MatchmakingStatus.QUEUED ? 1 : 0;
    }

    private int leave(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerCoreRuntime runtime = runtimeSupplier.get();
        if (runtime == null) return 0;

        Optional<DuelMatch> active = runtime.matchmaking().activeMatchFor(player.getUUID());
        if (active.isPresent()) {
            DuelMatch match = active.orElseThrow();
            UUID winnerId = match.opponentOf(player.getUUID()).orElseThrow();
            if (match.status() == DuelStatus.STARTED) {
                runtime.matchmaking().complete(match.matchId(), winnerId, 0, 0);
                ServerPlayer winner = source.getServer().getPlayerList().getPlayer(winnerId);
                if (winner != null) {
                    restoreAndReturn(winner);
                    winner.sendSystemMessage(Component.literal("Opponent forfeited. Duel victory recorded.")
                            .withStyle(ChatFormatting.GOLD));
                }
                restoreAndReturn(player);
                source.sendSuccess(() -> Component.literal("You forfeited the duel."), false);
                return 1;
            }
        }

        MatchmakingResult result = runtime.matchmaking().leave(player.getUUID());
        source.sendSuccess(() -> Component.literal(result.message()), false);
        return 1;
    }

    private int stats(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerCoreRuntime runtime = runtimeSupplier.get();
        if (runtime == null) return 0;
        PlayerProfile profile = runtime.playerStats().registerOrUpdate(player.getUUID(), player.getName().getString());
        source.sendSuccess(() -> Component.literal(
                        "Duels: " + profile.rating() + " rating | " + profile.wins() + "W/" + profile.losses()
                                + "L | " + profile.kills() + "K/" + profile.deaths() + "D")
                .withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private int status(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerCoreRuntime runtime = runtimeSupplier.get();
        if (runtime == null) return 0;
        Optional<DuelMatch> match = runtime.matchmaking().activeMatchFor(player.getUUID());
        if (match.isPresent()) {
            DuelMatch value = match.orElseThrow();
            source.sendSuccess(() -> Component.literal(
                    "Active duel: " + value.mode() + " | status=" + value.status() + " | arena=" + value.arenaId()), false);
            return 1;
        }
        boolean queued = runtime.matchmaking().queued(DuelMode.CASUAL).stream()
                        .anyMatch(entry -> entry.playerId().equals(player.getUUID()))
                || runtime.matchmaking().queued(DuelMode.RANKED).stream()
                        .anyMatch(entry -> entry.playerId().equals(player.getUUID()));
        source.sendSuccess(() -> Component.literal(queued ? "You are queued for a duel." : "You are not queued or in a duel."), false);
        return 1;
    }

    private int spectate(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerCoreRuntime runtime = runtimeSupplier.get();
        if (runtime == null) return 0;
        Optional<DuelMatch> active = runtime.matchmaking().matches().stream()
                .filter(match -> match.status() == DuelStatus.STARTED)
                .findFirst();
        if (active.isEmpty()) {
            source.sendFailure(Component.literal("There is no active duel to spectate."));
            return 0;
        }
        ArenaDefinition arena = runtime.arenas().find(active.orElseThrow().arenaId()).orElse(null);
        if (arena == null || arena.spectatorSpawn() == null) return 0;
        RETURN_LOCATIONS.putIfAbsent(player.getUUID(), GamingCastleTeleports.capture(player));
        var pos = arena.spectatorSpawn();
        GamingCastleDataStore.SavedLocation destination = new GamingCastleDataStore.SavedLocation(
                pos.world(), pos.x(), pos.y(), pos.z(), 0.0F, 20.0F);
        if (!GamingCastleTeleports.teleport(source.getServer(), player, destination)) return 0;
        source.sendSuccess(() -> Component.literal("Spectating the active Gaming Castle duel."), false);
        return 1;
    }

    private int duelLobby(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (isDueling(player.getUUID())) {
            source.sendFailure(Component.literal("Use /duel leave to forfeit before leaving an active match."));
            return 0;
        }
        GamingCastleTeleports.teleport(source.getServer(), player, GamingCastleDuelBootstrap.LOBBY);
        return 1;
    }

    private boolean startMatch(MinecraftServer server, ServerCoreRuntime runtime, DuelMatch created) {
        if (created == null) return false;
        ServerPlayer first = server.getPlayerList().getPlayer(created.firstPlayerId());
        ServerPlayer second = server.getPlayerList().getPlayer(created.secondPlayerId());
        ArenaDefinition arena = runtime.arenas().find(created.arenaId()).orElse(null);
        if (first == null || second == null || arena == null) {
            runtime.matchmaking().cancel(created.matchId());
            return false;
        }

        snapshot(first);
        snapshot(second);
        if (created.mode() == DuelMode.RANKED) {
            equipRankedKit(first);
            equipRankedKit(second);
        }
        healForDuel(first);
        healForDuel(second);

        runtime.matchmaking().start(created.matchId());
        var a = arena.firstSpawn();
        var b = arena.secondSpawn();
        GamingCastleTeleports.teleport(server, first,
                new GamingCastleDataStore.SavedLocation(a.world(), a.x(), a.y(), a.z(), -90.0F, 0.0F));
        GamingCastleTeleports.teleport(server, second,
                new GamingCastleDataStore.SavedLocation(b.world(), b.x(), b.y(), b.z(), 90.0F, 0.0F));

        Component fight = Component.literal("FIGHT! " + created.mode() + " duel started.")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        first.sendSystemMessage(fight);
        second.sendSystemMessage(fight);
        return true;
    }

    private static void snapshot(ServerPlayer player) {
        SNAPSHOTS.put(player.getUUID(), InventorySnapshot.capture(player));
        RETURN_LOCATIONS.put(player.getUUID(), GamingCastleDuelBootstrap.LOBBY);
    }

    private static void equipRankedKit(ServerPlayer player) {
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
        player.getInventory().setItem(1, new ItemStack(Items.BOW));
        player.getInventory().setItem(2, new ItemStack(Items.GOLDEN_APPLE, 3));
        player.getInventory().setItem(8, new ItemStack(Items.ARROW, 64));
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        player.getInventory().setChanged();
    }

    private static void healForDuel(ServerPlayer player) {
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.clearFire();
    }

    private static void restoreAndReturn(ServerPlayer player) {
        restoreInventory(player);
        GamingCastleDataStore.SavedLocation destination = RETURN_LOCATIONS.remove(player.getUUID());
        if (destination == null) destination = GamingCastleDuelBootstrap.LOBBY;
        GamingCastleTeleports.teleport(player.getServer(), player, destination);
        healForDuel(player);
    }

    private static void restoreInventory(ServerPlayer player) {
        InventorySnapshot snapshot = SNAPSHOTS.remove(player.getUUID());
        if (snapshot == null) return;
        int size = Math.min(player.getInventory().getContainerSize(), snapshot.items().size());
        for (int slot = 0; slot < size; slot++) {
            player.getInventory().setItem(slot, snapshot.items().get(slot).copy());
        }
        player.getInventory().setChanged();
    }

    private record InventorySnapshot(List<ItemStack> items) {
        static InventorySnapshot capture(ServerPlayer player) {
            List<ItemStack> items = new ArrayList<>();
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                items.add(player.getInventory().getItem(slot).copy());
            }
            return new InventorySnapshot(List.copyOf(items));
        }
    }
}
