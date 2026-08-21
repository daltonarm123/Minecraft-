package com.community.servercore.neoforge;

import com.community.servercore.ServerCoreRuntime;
import com.community.servercore.economy.WalletTransactionType;
import com.community.servercore.player.PlayerProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Player-facing quality-of-life commands for the Gaming Castle community. */
final class GamingCastleEssentialsCommands {
    private static final long TPA_TTL_MILLIS = 60_000L;
    private static final long DAILY_BASE_REWARD = 250L;
    private static final long DAILY_STREAK_STEP = 25L;
    private static final long DAILY_STREAK_CAP_BONUS = 250L;

    private static final Map<UUID, GamingCastleDataStore.SavedLocation> BACKS = new ConcurrentHashMap<>();
    private static final Map<UUID, TpaRequest> TPA_REQUESTS = new ConcurrentHashMap<>();

    private GamingCastleEssentialsCommands() { }

    static void register(
            RegisterCommandsEvent event,
            Supplier<ServerCoreRuntime> runtimeSupplier,
            Supplier<GamingCastleDataStore> dataSupplier) {
        var dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("spawn")
                .executes(context -> teleportHub(context.getSource())));
        dispatcher.register(Commands.literal("hub")
                .executes(context -> teleportHub(context.getSource())));

        dispatcher.register(Commands.literal("sethome")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    GamingCastleDataStore store = requireStore(context.getSource(), dataSupplier);
                    if (store == null) return 0;
                    store.setHome(player.getUUID(), GamingCastleTeleports.capture(player));
                    context.getSource().sendSuccess(() -> Component.literal("Home set.").withStyle(ChatFormatting.GREEN), false);
                    return 1;
                }));

        dispatcher.register(Commands.literal("home")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    if (!allowTeleport(context.getSource(), player)) return 0;
                    GamingCastleDataStore store = requireStore(context.getSource(), dataSupplier);
                    if (store == null) return 0;
                    Optional<GamingCastleDataStore.SavedLocation> home = store.home(player.getUUID());
                    if (home.isEmpty()) {
                        context.getSource().sendFailure(Component.literal("You have not set a home yet. Use /sethome."));
                        return 0;
                    }
                    BACKS.put(player.getUUID(), GamingCastleTeleports.capture(player));
                    if (!GamingCastleTeleports.teleport(context.getSource().getServer(), player, home.orElseThrow())) {
                        context.getSource().sendFailure(Component.literal("Your saved home dimension is not available."));
                        return 0;
                    }
                    context.getSource().sendSuccess(() -> Component.literal("Teleported home.").withStyle(ChatFormatting.GREEN), false);
                    return 1;
                }));

        dispatcher.register(Commands.literal("back")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    if (!allowTeleport(context.getSource(), player)) return 0;
                    GamingCastleDataStore.SavedLocation destination = BACKS.get(player.getUUID());
                    if (destination == null) {
                        context.getSource().sendFailure(Component.literal("There is no previous location to return to."));
                        return 0;
                    }
                    GamingCastleDataStore.SavedLocation current = GamingCastleTeleports.capture(player);
                    if (!GamingCastleTeleports.teleport(context.getSource().getServer(), player, destination)) {
                        context.getSource().sendFailure(Component.literal("The previous location is not available."));
                        return 0;
                    }
                    BACKS.put(player.getUUID(), current);
                    return 1;
                }));

        dispatcher.register(Commands.literal("tpa")
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(context -> requestTeleport(
                                context.getSource(),
                                StringArgumentType.getString(context, "player")))));

        dispatcher.register(Commands.literal("tpaccept")
                .executes(context -> acceptTeleport(context.getSource())));
        dispatcher.register(Commands.literal("tpdeny")
                .executes(context -> denyTeleport(context.getSource())));

        dispatcher.register(Commands.literal("rules")
                .executes(context -> showRules(context.getSource())));
        dispatcher.register(Commands.literal("help")
                .executes(context -> showHelp(context.getSource())));
        dispatcher.register(Commands.literal("discord")
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal(
                            "Gaming Castle Discord is not configured yet. Ask staff for the current invite.")
                            .withStyle(ChatFormatting.AQUA), false);
                    return 1;
                }));

        dispatcher.register(Commands.literal("daily")
                .executes(context -> claimDaily(context.getSource(), runtimeSupplier, dataSupplier)));
        dispatcher.register(Commands.literal("stats")
                .executes(context -> showStats(context.getSource(), runtimeSupplier)));
        dispatcher.register(Commands.literal("leaderboard")
                .executes(context -> showLeaderboard(context.getSource(), runtimeSupplier)));

        // Friendly public aliases for safe player-facing economy actions.
        // /market is registered separately by the item-backed GamingCastleAuctionHouse.
        registerRedirect(dispatcher.getRoot(), dispatcher, "shop", "economy", "shop");
        registerRedirect(dispatcher.getRoot(), dispatcher, "balance", "economy", "balance");
        registerRedirect(dispatcher.getRoot(), dispatcher, "pay", "economy", "pay");
    }

    private static int teleportHub(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!allowTeleport(source, player)) return 0;
        BACKS.put(player.getUUID(), GamingCastleTeleports.capture(player));
        if (!GamingCastleTeleports.teleport(source.getServer(), player, GamingCastleTeleports.HUB)) {
            source.sendFailure(Component.literal("Gaming Castle hub is not available."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Welcome back to Gaming Castle.").withStyle(ChatFormatting.LIGHT_PURPLE), false);
        return 1;
    }

    private static int requestTeleport(CommandSourceStack source, String targetName)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer requester = source.getPlayerOrException();
        if (!allowTeleport(source, requester)) return 0;
        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            source.sendFailure(Component.literal("Player not found or not online."));
            return 0;
        }
        if (GamingCastleCombatTracker.teleportBlocked(target)) {
            source.sendFailure(Component.literal("That player cannot receive teleport requests while in combat or a duel."));
            return 0;
        }
        if (target.getUUID().equals(requester.getUUID())) {
            source.sendFailure(Component.literal("You cannot send a teleport request to yourself."));
            return 0;
        }
        TPA_REQUESTS.put(target.getUUID(),
                new TpaRequest(requester.getUUID(), System.currentTimeMillis() + TPA_TTL_MILLIS));
        requester.sendSystemMessage(Component.literal("Teleport request sent to " + target.getName().getString() + ".")
                .withStyle(ChatFormatting.GREEN));
        target.sendSystemMessage(Component.literal(requester.getName().getString()
                        + " wants to teleport to you. Use /tpaccept or /tpdeny within 60 seconds.")
                .withStyle(ChatFormatting.YELLOW));
        return 1;
    }

    private static int acceptTeleport(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = source.getPlayerOrException();
        if (!allowTeleport(source, target)) return 0;
        TpaRequest request = TPA_REQUESTS.remove(target.getUUID());
        if (request == null || request.expiresAtMillis() < System.currentTimeMillis()) {
            source.sendFailure(Component.literal("You do not have an active teleport request."));
            return 0;
        }
        ServerPlayer requester = source.getServer().getPlayerList().getPlayer(request.requesterId());
        if (requester == null) {
            source.sendFailure(Component.literal("The requesting player is no longer online."));
            return 0;
        }
        if (!allowTeleport(source, requester)) {
            requester.sendSystemMessage(Component.literal(GamingCastleCombatTracker.blockReason(requester))
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        BACKS.put(requester.getUUID(), GamingCastleTeleports.capture(requester));
        GamingCastleDataStore.SavedLocation destination = GamingCastleTeleports.capture(target);
        if (!GamingCastleTeleports.teleport(source.getServer(), requester, destination)) {
            source.sendFailure(Component.literal("Teleport failed."));
            return 0;
        }
        requester.sendSystemMessage(Component.literal("Teleport request accepted.").withStyle(ChatFormatting.GREEN));
        target.sendSystemMessage(Component.literal("Accepted " + requester.getName().getString() + "'s teleport request.")
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int denyTeleport(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = source.getPlayerOrException();
        TpaRequest request = TPA_REQUESTS.remove(target.getUUID());
        if (request == null || request.expiresAtMillis() < System.currentTimeMillis()) {
            source.sendFailure(Component.literal("You do not have an active teleport request."));
            return 0;
        }
        ServerPlayer requester = source.getServer().getPlayerList().getPlayer(request.requesterId());
        if (requester != null) {
            requester.sendSystemMessage(Component.literal(target.getName().getString() + " denied your teleport request.")
                    .withStyle(ChatFormatting.RED));
        }
        source.sendSuccess(() -> Component.literal("Teleport request denied."), false);
        return 1;
    }

    private static int claimDaily(
            CommandSourceStack source,
            Supplier<ServerCoreRuntime> runtimeSupplier,
            Supplier<GamingCastleDataStore> dataSupplier)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerCoreRuntime runtime = runtimeSupplier.get();
        GamingCastleDataStore store = requireStore(source, dataSupplier);
        if (runtime == null || store == null) {
            source.sendFailure(Component.literal("ServerCore has not finished starting."));
            return 0;
        }
        GamingCastleDataStore.DailyClaim claim = store.claimDaily(player.getUUID(), LocalDate.now(ZoneOffset.UTC));
        if (!claim.claimed()) {
            source.sendFailure(Component.literal("You already claimed today's reward. Current streak: " + claim.streak() + "."));
            return 0;
        }
        long bonus = Math.min(DAILY_STREAK_CAP_BONUS, Math.max(0, claim.streak() - 1) * DAILY_STREAK_STEP);
        long reward = DAILY_BASE_REWARD + bonus;
        runtime.wallets().credit(
                player.getUUID(),
                reward,
                WalletTransactionType.CREDIT,
                "daily-reward",
                null,
                Map.of("streak", Integer.toString(claim.streak())));
        source.sendSuccess(() -> Component.literal(
                        "Daily reward claimed: " + reward + " SC. Streak: " + claim.streak() + " day(s).")
                .withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int showStats(CommandSourceStack source, Supplier<ServerCoreRuntime> runtimeSupplier)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerCoreRuntime runtime = runtimeSupplier.get();
        if (runtime == null) {
            source.sendFailure(Component.literal("ServerCore has not finished starting."));
            return 0;
        }
        PlayerProfile profile = runtime.playerStats().registerOrUpdate(player.getUUID(), player.getName().getString());
        player.sendSystemMessage(Component.literal("--- Gaming Castle Stats ---").withStyle(ChatFormatting.LIGHT_PURPLE));
        player.sendSystemMessage(Component.literal("Rating: " + profile.rating()
                + " | Wins: " + profile.wins()
                + " | Losses: " + profile.losses()));
        player.sendSystemMessage(Component.literal("Kills: " + profile.kills()
                + " | Deaths: " + profile.deaths()
                + " | Balance: " + runtime.wallets().balance(player.getUUID()) + " SC"));
        return 1;
    }

    private static int showLeaderboard(CommandSourceStack source, Supplier<ServerCoreRuntime> runtimeSupplier) {
        ServerCoreRuntime runtime = runtimeSupplier.get();
        if (runtime == null) {
            source.sendFailure(Component.literal("ServerCore has not finished starting."));
            return 0;
        }
        List<PlayerProfile> leaders = runtime.playerStats().leaderboard(10);
        source.sendSuccess(() -> Component.literal("--- Gaming Castle Duel Leaderboard ---")
                .withStyle(ChatFormatting.GOLD), false);
        if (leaders.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No ranked players yet."), false);
            return 1;
        }
        for (int index = 0; index < leaders.size(); index++) {
            int rank = index + 1;
            PlayerProfile profile = leaders.get(index);
            source.sendSuccess(() -> Component.literal(
                    "#" + rank + " " + profile.username() + " - " + profile.rating()
                            + " rating (" + profile.wins() + "W/" + profile.losses() + "L)"), false);
        }
        return 1;
    }

    private static int showRules(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("--- Gaming Castle Rules ---").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("1. No griefing, stealing, duping, exploits, or intentional lag machines."), false);
        source.sendSuccess(() -> Component.literal("2. Respect other players and staff; harassment and hate speech are not allowed."), false);
        source.sendSuccess(() -> Component.literal("3. PvP belongs in agreed fights or the Duels area unless server rules say otherwise."), false);
        source.sendSuccess(() -> Component.literal("4. Do not bypass claims, protected areas, punishments, or economy systems."), false);
        source.sendSuccess(() -> Component.literal("5. Staff may act on behavior that harms the community even if it is not listed word-for-word."), false);
        return 1;
    }

    private static int showHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("--- Gaming Castle Commands ---").withStyle(ChatFormatting.LIGHT_PURPLE), false);
        source.sendSuccess(() -> Component.literal("Travel: /spawn /hub /sethome /home /back /tpa <player> /tpaccept /tpdeny"), false);
        source.sendSuccess(() -> Component.literal("Economy: /balance /shop /market /pay <player> <amount> /daily"), false);
        source.sendSuccess(() -> Component.literal("Community: /rules /discord /stats /leaderboard /report <player> <reason>"), false);
        source.sendSuccess(() -> Component.literal("Duels: /duel join casual|ranked /duel leave /duel stats /duel spectate"), false);
        return 1;
    }

    private static boolean allowTeleport(CommandSourceStack source, ServerPlayer player) {
        if (!GamingCastleCombatTracker.teleportBlocked(player)) return true;
        source.sendFailure(Component.literal(GamingCastleCombatTracker.blockReason(player)));
        return false;
    }

    private static GamingCastleDataStore requireStore(
            CommandSourceStack source,
            Supplier<GamingCastleDataStore> supplier) {
        GamingCastleDataStore store = supplier.get();
        if (store == null) {
            source.sendFailure(Component.literal("Community data is not available yet."));
        }
        return store;
    }

    private static void registerRedirect(
            CommandNode<CommandSourceStack> root,
            com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher,
            String alias,
            String parent,
            String child) {
        CommandNode<CommandSourceStack> parentNode = root.getChild(parent);
        if (parentNode == null) return;
        CommandNode<CommandSourceStack> target = parentNode.getChild(child);
        if (target != null && root.getChild(alias) == null) {
            dispatcher.register(Commands.literal(alias).redirect(target));
        }
    }

    private record TpaRequest(UUID requesterId, long expiresAtMillis) { }
}
