package com.community.servercore.neoforge;

import com.community.servercore.ServerCoreRuntime;
import com.community.servercore.command.CommandResult;
import com.community.servercore.portal.PortalDestination;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Objects;
import java.util.function.Supplier;

final class ServerCoreCommands {
    private ServerCoreCommands() {
    }

    static void register(RegisterCommandsEvent event, Supplier<ServerCoreRuntime> runtimeSupplier) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(runtimeSupplier, "runtimeSupplier");

        event.getDispatcher().register(Commands.literal("servercore")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status")
                        .executes(context -> status(context.getSource(), runtimeSupplier))));

        event.getDispatcher().register(Commands.literal("portal")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("begin")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> executePlayer(
                                        context.getSource(),
                                        runtimeSupplier,
                                        (runtime, actor) -> runtime.portalCommands().begin(
                                                actor,
                                                StringArgumentType.getString(context, "name"))))))
                .then(Commands.literal("pos1")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> executePlayer(
                                        context.getSource(),
                                        runtimeSupplier,
                                        (runtime, actor) -> runtime.portalCommands().setFirst(
                                                actor,
                                                StringArgumentType.getString(context, "name"))))))
                .then(Commands.literal("pos2")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> executePlayer(
                                        context.getSource(),
                                        runtimeSupplier,
                                        (runtime, actor) -> runtime.portalCommands().setSecond(
                                                actor,
                                                StringArgumentType.getString(context, "name"))))))
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.literal("location")
                                        .then(Commands.argument("world", StringArgumentType.string())
                                                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                                        .executes(context -> executePlayer(
                                                                                context.getSource(),
                                                                                runtimeSupplier,
                                                                                (runtime, actor) -> {
                                                                                    String name = StringArgumentType.getString(context, "name");
                                                                                    PortalDestination destination = PortalDestination.location(
                                                                                            StringArgumentType.getString(context, "world"),
                                                                                            DoubleArgumentType.getDouble(context, "x"),
                                                                                            DoubleArgumentType.getDouble(context, "y"),
                                                                                            DoubleArgumentType.getDouble(context, "z"),
                                                                                            0.0F,
                                                                                            0.0F);
                                                                                    return runtime.portalCommands().create(
                                                                                            actor,
                                                                                            name,
                                                                                            name,
                                                                                            destination,
                                                                                            "");
                                                                                }))))))))))
                .then(Commands.literal("list")
                        .executes(context -> executePlayer(
                                context.getSource(),
                                runtimeSupplier,
                                (runtime, actor) -> runtime.portalCommands().list(actor))))
                .then(Commands.literal("info")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> executePlayer(
                                        context.getSource(),
                                        runtimeSupplier,
                                        (runtime, actor) -> runtime.portalCommands().info(
                                                actor,
                                                StringArgumentType.getString(context, "name"))))))
                .then(Commands.literal("delete")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> executePlayer(
                                        context.getSource(),
                                        runtimeSupplier,
                                        (runtime, actor) -> runtime.portalCommands().delete(
                                                actor,
                                                StringArgumentType.getString(context, "name"))))))
                .then(Commands.literal("enable")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> executePlayer(
                                        context.getSource(),
                                        runtimeSupplier,
                                        (runtime, actor) -> runtime.portalCommands().setEnabled(
                                                actor,
                                                StringArgumentType.getString(context, "name"),
                                                true)))))
                .then(Commands.literal("disable")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> executePlayer(
                                        context.getSource(),
                                        runtimeSupplier,
                                        (runtime, actor) -> runtime.portalCommands().setEnabled(
                                                actor,
                                                StringArgumentType.getString(context, "name"),
                                                false))))));
    }

    private static int status(CommandSourceStack source, Supplier<ServerCoreRuntime> runtimeSupplier) {
        ServerCoreRuntime runtime = runtimeSupplier.get();
        if (runtime == null) {
            source.sendFailure(Component.literal("ServerCore has not finished starting."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "ServerCore is running with " + runtime.portals().list().size() + " configured portals."), false);
        return 1;
    }

    private static int executePlayer(
            CommandSourceStack source,
            Supplier<ServerCoreRuntime> runtimeSupplier,
            PlayerCommand command) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerCoreRuntime runtime = runtimeSupplier.get();
        if (runtime == null) {
            source.sendFailure(Component.literal("ServerCore has not finished starting."));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        CommandResult result = command.execute(runtime, new NeoForgeCommandActor(source, player));
        for (String message : result.messages()) {
            if (result.successful()) {
                source.sendSuccess(() -> Component.literal(message), false);
            } else {
                source.sendFailure(Component.literal(message));
            }
        }
        return result.successful() ? 1 : 0;
    }

    @FunctionalInterface
    private interface PlayerCommand {
        CommandResult execute(ServerCoreRuntime runtime, NeoForgeCommandActor actor);
    }
}
