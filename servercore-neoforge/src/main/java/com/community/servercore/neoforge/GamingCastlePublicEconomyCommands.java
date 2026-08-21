package com.community.servercore.neoforge;

import com.community.servercore.ServerCoreRuntime;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Objects;
import java.util.function.Supplier;

/** Public economy commands that must never inherit the staff-only admin tree gate. */
final class GamingCastlePublicEconomyCommands {
    private GamingCastlePublicEconomyCommands() { }

    static void register(RegisterCommandsEvent event, Supplier<ServerCoreRuntime> runtimeSupplier) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(runtimeSupplier, "runtimeSupplier");

        event.getDispatcher().register(Commands.literal("balance")
                .executes(context -> balance(context.getSource(), runtimeSupplier)));
        event.getDispatcher().register(Commands.literal("money")
                .executes(context -> balance(context.getSource(), runtimeSupplier)));
    }

    private static int balance(CommandSourceStack source, Supplier<ServerCoreRuntime> runtimeSupplier)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerCoreRuntime runtime = runtimeSupplier.get();
        if (runtime == null) {
            source.sendFailure(Component.literal("ServerCore has not finished starting."));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        long balance = runtime.wallets().balance(player.getUUID());
        source.sendSuccess(() -> Component.literal("Balance: " + balance + " SC")
                .withStyle(ChatFormatting.GOLD), false);
        return 1;
    }
}
