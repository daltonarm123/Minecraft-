package com.community.servercore.neoforge;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** Lightweight recurring event calendar and staff event broadcasts. */
final class GamingCastleEvents {
    private GamingCastleEvents() { }

    static void register(RegisterCommandsEvent event) {
        var root = Commands.literal("event")
                .executes(context -> status(context.getSource()));

        root.then(Commands.literal("status")
                .executes(context -> status(context.getSource())));

        root.then(Commands.literal("announce")
                .requires(GamingCastleEvents::isStaffSource)
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(context -> announce(
                                context.getSource(),
                                StringArgumentType.getString(context, "message")))));

        root.then(Commands.literal("duels")
                .requires(GamingCastleEvents::isStaffSource)
                .executes(context -> preset(
                        context.getSource(),
                        "DUEL NIGHT is starting! Queue with /duel join ranked or /duel join casual.")));

        root.then(Commands.literal("build")
                .requires(GamingCastleEvents::isStaffSource)
                .executes(context -> preset(
                        context.getSource(),
                        "BUILD EVENT is live in Survival! Staff will announce the theme and judging time.")));

        root.then(Commands.literal("market")
                .requires(GamingCastleEvents::isStaffSource)
                .executes(context -> preset(
                        context.getSource(),
                        "MARKET EVENT is live! Visit the Market District and check /market list.")));

        event.getDispatcher().register(root);
    }

    private static boolean weekendArenaRush() {
        DayOfWeek day = LocalDate.now(ZoneOffset.UTC).getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    private static int status(CommandSourceStack source) {
        if (weekendArenaRush()) {
            source.sendSuccess(() -> Component.literal(
                            "Weekend Arena Rush is on the calendar today. Use /duel join casual or /duel join ranked and watch for staff event announcements.")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        } else {
            source.sendSuccess(() -> Component.literal(
                            "Recurring community focus: Weekend Arena Rush every Saturday and Sunday (UTC).")
                    .withStyle(ChatFormatting.AQUA), false);
        }
        source.sendSuccess(() -> Component.literal(
                "Staff can launch Duel Night, Build, Market, or custom announcements with /event."), false);
        return 1;
    }

    private static int announce(CommandSourceStack source, String message) {
        return preset(source, clean(message));
    }

    private static int preset(CommandSourceStack source, String message) {
        Component header = Component.literal("[GAMING CASTLE EVENT]")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
        Component body = Component.literal(message).withStyle(ChatFormatting.GOLD);
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            player.sendSystemMessage(header);
            player.sendSystemMessage(body);
        }
        source.sendSuccess(() -> Component.literal("Event announcement broadcast."), false);
        return 1;
    }

    private static boolean isStaffSource(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return NeoForgePermissions.check(player, NeoForgePermissions.STAFF_PERMISSION);
        }
        return source.hasPermission(Commands.LEVEL_OWNERS);
    }

    private static String clean(String value) {
        String result = value == null ? "" : value.trim().replace('\n', ' ').replace('\r', ' ');
        return result.length() <= 180 ? result : result.substring(0, 180);
    }
}
