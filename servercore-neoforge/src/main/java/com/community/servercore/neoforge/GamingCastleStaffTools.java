package com.community.servercore.neoforge;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Staff moderation tools backed by ServerCore roles and persistent community data. */
final class GamingCastleStaffTools {
    private static final Map<UUID, GamingCastleDataStore.SavedLocation> FROZEN = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> VANISHED = new ConcurrentHashMap<>();

    private final Supplier<GamingCastleDataStore> dataSupplier;

    GamingCastleStaffTools(Supplier<GamingCastleDataStore> dataSupplier) {
        this.dataSupplier = Objects.requireNonNull(dataSupplier, "dataSupplier");
    }

    void registerCommands(RegisterCommandsEvent event) {
        var dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("report")
                .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(context -> report(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "player"),
                                        StringArgumentType.getString(context, "reason"))))));

        var staff = Commands.literal("staff")
                .requires(GamingCastleStaffTools::isStaffSource);

        staff.then(Commands.literal("warn")
                .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(context -> warn(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "player"),
                                        StringArgumentType.getString(context, "reason"))))));

        staff.then(Commands.literal("note")
                .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("note", StringArgumentType.greedyString())
                                .executes(context -> note(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "player"),
                                        StringArgumentType.getString(context, "note"))))));

        staff.then(Commands.literal("notes")
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(context -> notes(
                                context.getSource(),
                                StringArgumentType.getString(context, "player")))));

        staff.then(Commands.literal("mute")
                .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 10080))
                                .executes(context -> mute(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "player"),
                                        IntegerArgumentType.getInteger(context, "minutes"),
                                        "Muted by staff"))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(context -> mute(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "player"),
                                                IntegerArgumentType.getInteger(context, "minutes"),
                                                StringArgumentType.getString(context, "reason")))))));

        staff.then(Commands.literal("unmute")
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(context -> unmute(
                                context.getSource(),
                                StringArgumentType.getString(context, "player")))));

        staff.then(Commands.literal("freeze")
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(context -> freeze(
                                context.getSource(),
                                StringArgumentType.getString(context, "player"),
                                true))));

        staff.then(Commands.literal("unfreeze")
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(context -> freeze(
                                context.getSource(),
                                StringArgumentType.getString(context, "player"),
                                false))));

        staff.then(Commands.literal("kick")
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(context -> kick(
                                context.getSource(),
                                StringArgumentType.getString(context, "player"),
                                "Removed by Gaming Castle staff"))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(context -> kick(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "player"),
                                        StringArgumentType.getString(context, "reason"))))));

        staff.then(Commands.literal("ban")
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(context -> ban(
                                context.getSource(),
                                StringArgumentType.getString(context, "player"),
                                "Banned by Gaming Castle staff"))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(context -> ban(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "player"),
                                        StringArgumentType.getString(context, "reason"))))));

        staff.then(Commands.literal("vanish")
                .executes(context -> vanish(context.getSource())));

        staff.then(Commands.literal("inventory")
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(context -> inventory(
                                context.getSource(),
                                StringArgumentType.getString(context, "player")))));

        dispatcher.register(staff);
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        GamingCastleDataStore.SavedLocation frozenAt = FROZEN.get(player.getUUID());
        if (frozenAt == null) {
            return;
        }
        player.setDeltaMovement(0.0, 0.0, 0.0);
        if (player.tickCount % 5 == 0) {
            GamingCastleTeleports.teleport(player.getServer(), player, frozenAt);
        }
    }

    private int report(CommandSourceStack source, String targetName, String reason)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer reporter = source.getPlayerOrException();
        ServerPlayer target = findOnline(source, targetName);
        if (target == null) {
            source.sendFailure(Component.literal("That player must be online to report them."));
            return 0;
        }
        if (target.getUUID().equals(reporter.getUUID())) {
            source.sendFailure(Component.literal("You cannot report yourself."));
            return 0;
        }
        GamingCastleDataStore data = store(source);
        if (data == null) return 0;
        String note = "REPORT " + Instant.now() + " by " + reporter.getName().getString() + ": " + clean(reason);
        data.addNote(target.getUUID(), note);
        notifyStaff(source, Component.literal(
                        "[REPORT] " + reporter.getName().getString() + " reported " + target.getName().getString()
                                + ": " + clean(reason))
                .withStyle(ChatFormatting.RED));
        source.sendSuccess(() -> Component.literal("Report submitted to Gaming Castle staff.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private int warn(CommandSourceStack source, String targetName, String reason) {
        ServerPlayer target = findOnline(source, targetName);
        if (target == null) {
            source.sendFailure(Component.literal("Player not found or not online."));
            return 0;
        }
        GamingCastleDataStore data = store(source);
        if (data == null) return 0;
        int count = data.addWarning(target.getUUID(), clean(reason));
        target.sendSystemMessage(Component.literal(
                        "STAFF WARNING #" + count + ": " + clean(reason))
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        source.sendSuccess(() -> Component.literal(
                "Warned " + target.getName().getString() + ". Total warnings: " + count), false);
        return 1;
    }

    private int note(CommandSourceStack source, String targetName, String note) {
        ServerPlayer target = findOnline(source, targetName);
        if (target == null) {
            source.sendFailure(Component.literal("Player not found or not online."));
            return 0;
        }
        GamingCastleDataStore data = store(source);
        if (data == null) return 0;
        String staffName = source.getEntity() instanceof ServerPlayer player
                ? player.getName().getString()
                : "CONSOLE";
        data.addNote(target.getUUID(), "NOTE " + Instant.now() + " by " + staffName + ": " + clean(note));
        source.sendSuccess(() -> Component.literal("Staff note saved for " + target.getName().getString() + "."), false);
        return 1;
    }

    private int notes(CommandSourceStack source, String targetName) {
        ServerPlayer target = findOnline(source, targetName);
        if (target == null) {
            source.sendFailure(Component.literal("Player not found or not online."));
            return 0;
        }
        GamingCastleDataStore data = store(source);
        if (data == null) return 0;
        List<String> notes = data.notes(target.getUUID());
        source.sendSuccess(() -> Component.literal(
                target.getName().getString() + " | warnings=" + data.warnings(target.getUUID())
                        + " | notes=" + notes.size()).withStyle(ChatFormatting.GOLD), false);
        if (notes.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No staff notes."), false);
        } else {
            for (String value : notes) {
                source.sendSuccess(() -> Component.literal("- " + value), false);
            }
        }
        return 1;
    }

    private int mute(CommandSourceStack source, String targetName, int minutes, String reason) {
        ServerPlayer target = findOnline(source, targetName);
        if (target == null) {
            source.sendFailure(Component.literal("Player not found or not online."));
            return 0;
        }
        GamingCastleDataStore data = store(source);
        if (data == null) return 0;
        long until = System.currentTimeMillis() + (minutes * 60_000L);
        data.muteUntil(target.getUUID(), until);
        data.addNote(target.getUUID(), "MUTE " + Instant.now() + " for " + minutes + "m: " + clean(reason));
        target.sendSystemMessage(Component.literal(
                "You have been muted for " + minutes + " minute(s): " + clean(reason))
                .withStyle(ChatFormatting.RED));
        source.sendSuccess(() -> Component.literal("Muted " + target.getName().getString() + " for " + minutes + " minute(s)."), false);
        return 1;
    }

    private int unmute(CommandSourceStack source, String targetName) {
        ServerPlayer target = findOnline(source, targetName);
        if (target == null) {
            source.sendFailure(Component.literal("Player not found or not online."));
            return 0;
        }
        GamingCastleDataStore data = store(source);
        if (data == null) return 0;
        data.unmute(target.getUUID());
        data.addNote(target.getUUID(), "UNMUTE " + Instant.now());
        target.sendSystemMessage(Component.literal("Your chat mute has been removed.").withStyle(ChatFormatting.GREEN));
        source.sendSuccess(() -> Component.literal("Unmuted " + target.getName().getString() + "."), false);
        return 1;
    }

    private int freeze(CommandSourceStack source, String targetName, boolean freeze) {
        ServerPlayer target = findOnline(source, targetName);
        if (target == null) {
            source.sendFailure(Component.literal("Player not found or not online."));
            return 0;
        }
        if (freeze) {
            FROZEN.put(target.getUUID(), GamingCastleTeleports.capture(target));
            target.sendSystemMessage(Component.literal("You have been frozen by staff. Do not disconnect.")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        } else {
            FROZEN.remove(target.getUUID());
            target.sendSystemMessage(Component.literal("You are no longer frozen.").withStyle(ChatFormatting.GREEN));
        }
        source.sendSuccess(() -> Component.literal((freeze ? "Froze " : "Unfroze ") + target.getName().getString() + "."), false);
        return 1;
    }

    private int kick(CommandSourceStack source, String targetName, String reason) {
        ServerPlayer target = findOnline(source, targetName);
        if (target == null) {
            source.sendFailure(Component.literal("Player not found or not online."));
            return 0;
        }
        GamingCastleDataStore data = store(source);
        if (data != null) {
            data.addNote(target.getUUID(), "KICK " + Instant.now() + ": " + clean(reason));
        }
        target.connection.disconnect(Component.literal("Gaming Castle: " + clean(reason)));
        source.sendSuccess(() -> Component.literal("Kicked " + targetName + "."), false);
        return 1;
    }

    private int ban(CommandSourceStack source, String targetName, String reason) {
        ServerPlayer target = findOnline(source, targetName);
        if (target != null) {
            GamingCastleDataStore data = store(source);
            if (data != null) {
                data.addNote(target.getUUID(), "BAN " + Instant.now() + ": " + clean(reason));
            }
        }
        int result = source.getServer().getCommands().performPrefixedCommand(
                source.withPermission(Commands.LEVEL_OWNERS),
                "ban " + targetName + " " + clean(reason));
        if (result > 0) {
            source.sendSuccess(() -> Component.literal("Banned " + targetName + "."), false);
        } else {
            source.sendFailure(Component.literal("Ban command failed. Check the player name or server ban configuration."));
        }
        return result;
    }

    private int vanish(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean next = !VANISHED.getOrDefault(player.getUUID(), false);
        VANISHED.put(player.getUUID(), next);
        player.setInvisible(next);
        source.sendSuccess(() -> Component.literal(next ? "Basic vanish enabled." : "Vanish disabled.")
                .withStyle(next ? ChatFormatting.AQUA : ChatFormatting.GREEN), false);
        if (next) {
            source.sendSuccess(() -> Component.literal(
                    "Note: this launch vanish hides your model; full tab-list/network vanish can be added later."), false);
        }
        return 1;
    }

    private int inventory(CommandSourceStack source, String targetName) {
        ServerPlayer target = findOnline(source, targetName);
        if (target == null) {
            source.sendFailure(Component.literal("Player not found or not online."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Inventory of " + target.getName().getString() + ":")
                .withStyle(ChatFormatting.GOLD), false);
        int shown = 0;
        int occupied = 0;
        for (int slot = 0; slot < target.getInventory().getContainerSize(); slot++) {
            ItemStack stack = target.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            occupied++;
            if (shown < 20) {
                int displaySlot = slot;
                source.sendSuccess(() -> Component.literal(
                        "slot " + displaySlot + ": " + stack.getCount() + "x " + stack.getHoverName().getString()), false);
                shown++;
            }
        }
        int total = occupied;
        source.sendSuccess(() -> Component.literal("Occupied slots: " + total
                + (total > 20 ? " (first 20 shown)" : "")), false);
        return 1;
    }

    private GamingCastleDataStore store(CommandSourceStack source) {
        GamingCastleDataStore data = dataSupplier.get();
        if (data == null) {
            source.sendFailure(Component.literal("Community moderation data is not available."));
        }
        return data;
    }

    private static ServerPlayer findOnline(CommandSourceStack source, String name) {
        return source.getServer().getPlayerList().getPlayerByName(name);
    }

    private static boolean isStaffSource(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return NeoForgePermissions.check(player, NeoForgePermissions.STAFF_PERMISSION);
        }
        return source.hasPermission(Commands.LEVEL_OWNERS);
    }

    private static void notifyStaff(CommandSourceStack source, Component message) {
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            if (NeoForgePermissions.check(player, NeoForgePermissions.STAFF_PERMISSION)) {
                player.sendSystemMessage(message);
            }
        }
    }

    private static String clean(String value) {
        String result = value == null ? "" : value.trim().replace('\n', ' ').replace('\r', ' ');
        return result.length() <= 160 ? result : result.substring(0, 160);
    }
}
