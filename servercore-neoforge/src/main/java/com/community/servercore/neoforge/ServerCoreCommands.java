package com.community.servercore.neoforge;

import com.community.servercore.ServerCoreRuntime;
import com.community.servercore.command.CommandResult;
import com.community.servercore.portal.PortalDestination;
import com.community.servercore.selection.WorldPosition;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("status")
                        .executes(context -> status(context.getSource(), runtimeSupplier))));

        LiteralArgumentBuilder<CommandSourceStack> portal = Commands.literal("portal");
        portal.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));

        portal.then(namedPlayerCommand("begin", runtimeSupplier,
                (runtime, actor, name) -> runtime.portalCommands().begin(actor, name)));
        portal.then(namedPlayerCommand("pos1", runtimeSupplier,
                (runtime, actor, name) -> runtime.portalCommands().setFirst(actor, name)));
        portal.then(namedPlayerCommand("pos2", runtimeSupplier,
                (runtime, actor, name) -> runtime.portalCommands().setSecond(actor, name)));
        portal.then(namedPlayerCommand("info", runtimeSupplier,
                (runtime, actor, name) -> runtime.portalCommands().info(actor, name)));
        portal.then(namedPlayerCommand("delete", runtimeSupplier,
                (runtime, actor, name) -> runtime.portalCommands().delete(actor, name)));
        portal.then(namedPlayerCommand("enable", runtimeSupplier,
                (runtime, actor, name) -> runtime.portalCommands().setEnabled(actor, name, true)));
        portal.then(namedPlayerCommand("disable", runtimeSupplier,
                (runtime, actor, name) -> runtime.portalCommands().setEnabled(actor, name, false)));

        portal.then(Commands.literal("create")
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(context -> executePlayer(
                                context.getSource(),
                                runtimeSupplier,
                                (runtime, actor) -> {
                                    String name = StringArgumentType.getString(context, "name");
                                    WorldPosition position = actor.position();
                                    PortalDestination destination = PortalDestination.location(
                                            position.world(),
                                            position.x(),
                                            position.y(),
                                            position.z(),
                                            0.0F,
                                            0.0F);
                                    return runtime.portalCommands().create(
                                            actor,
                                            name,
                                            name,
                                            destination,
                                            "");
                                }))));

        portal.then(Commands.literal("list")
                .executes(context -> executePlayer(
                        context.getSource(),
                        runtimeSupplier,
                        (runtime, actor) -> runtime.portalCommands().list(actor))));

        event.getDispatcher().register(portal);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> namedPlayerCommand(
            String literal,
            Supplier<ServerCoreRuntime> runtimeSupplier,
            NamedPlayerCommand command) {
        return Commands.literal(literal)
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(context -> executePlayer(
                                context.getSource(),
                                runtimeSupplier,
                                (runtime, actor) -> command.execute(
                                        runtime,
                                        actor,
                                        StringArgumentType.getString(context, "name")))));
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

    @FunctionalInterface
    private interface NamedPlayerCommand {
        CommandResult execute(ServerCoreRuntime runtime, NeoForgeCommandActor actor, String name);
    }
}
