package com.community.servercore.neoforge;

import com.community.servercore.ServerCoreRuntime;
import com.community.servercore.command.CommandResult;
import com.community.servercore.portal.PortalDestination;
import com.community.servercore.selection.WorldPosition;
import com.community.servercore.economy.MarketItemKind;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
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
                                }))
                        .then(Commands.argument("permission", StringArgumentType.word())
                                .executes(context -> executePlayer(
                                        context.getSource(),
                                        runtimeSupplier,
                                        (runtime, actor) -> {
                                            String name = StringArgumentType.getString(context, "name");
                                            String permission = StringArgumentType.getString(context, "permission");
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
                                                    permission);
                                        })))));

        portal.then(Commands.literal("list")
                .executes(context -> executePlayer(
                        context.getSource(),
                        runtimeSupplier,
                        (runtime, actor) -> runtime.portalCommands().list(actor))));

        event.getDispatcher().register(portal);

        LiteralArgumentBuilder<CommandSourceStack> roles = Commands.literal("role");
        roles.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
        roles.then(Commands.literal("list")
                .executes(context -> executePlayer(
                        context.getSource(),
                        runtimeSupplier,
                        (runtime, actor) -> runtime.roles().listRoles(actor))));
        roles.then(Commands.literal("my")
                .executes(context -> executePlayer(
                        context.getSource(),
                        runtimeSupplier,
                        (runtime, actor) -> runtime.roles().myRoles(actor))));
        roles.then(Commands.literal("access")
                .executes(context -> executePlayer(
                        context.getSource(),
                        runtimeSupplier,
                        (runtime, actor) -> runtime.roles().areaAccess(actor))));
        event.getDispatcher().register(roles);

        LiteralArgumentBuilder<CommandSourceStack> economy = Commands.literal("economy");
        economy.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));

        economy.then(Commands.literal("balance")
                .executes(context -> executePlayer(
                        context.getSource(),
                        runtimeSupplier,
                        (runtime, actor) -> runtime.economyCommands().balance(actor))));

        economy.then(Commands.literal("shop")
                .executes(context -> executePlayer(
                        context.getSource(),
                        runtimeSupplier,
                        (runtime, actor) -> runtime.economyCommands().shop(actor))));

        economy.then(Commands.literal("shopbuy")
                .then(Commands.argument("itemId", StringArgumentType.word())
                        .executes(context -> executePlayer(
                                context.getSource(),
                                runtimeSupplier,
                                (runtime, actor) -> runtime.economyCommands().shopBuy(
                                        actor,
                                        StringArgumentType.getString(context, "itemId"))))));

        economy.then(Commands.literal("pay")
                .then(Commands.argument("target", StringArgumentType.word())
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(context -> executePlayer(
                                        context.getSource(),
                                        runtimeSupplier,
                                        (runtime, actor) -> resolvePlayer(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "target"))
                                                .map(target -> runtime.economyCommands().transfer(
                                                        actor,
                                                        target.getUUID(),
                                                        target.getName().getString(),
                                                        LongArgumentType.getLong(context, "amount")))
                                                .orElseGet(() -> CommandResult.failure("Target player not found.")))))));

        economy.then(Commands.literal("credit")
                .then(Commands.argument("target", StringArgumentType.word())
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(context -> executePlayer(
                                        context.getSource(),
                                        runtimeSupplier,
                                        (runtime, actor) -> resolvePlayer(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "target"))
                                                .map(target -> runtime.economyCommands().adminCredit(
                                                        actor,
                                                        target.getUUID(),
                                                        target.getName().getString(),
                                                        LongArgumentType.getLong(context, "amount"),
                                                        "admin-credit"))
                                                .orElseGet(() -> CommandResult.failure("Target player not found.")))))));

        LiteralArgumentBuilder<CommandSourceStack> market = Commands.literal("market");
        market.then(Commands.literal("list")
                .executes(context -> executePlayer(
                        context.getSource(),
                        runtimeSupplier,
                        (runtime, actor) -> runtime.economyCommands().marketList(actor, 20)))
                .then(Commands.argument("limit", IntegerArgumentType.integer(1, 100))
                        .executes(context -> executePlayer(
                                context.getSource(),
                                runtimeSupplier,
                                (runtime, actor) -> runtime.economyCommands().marketList(
                                        actor,
                                        IntegerArgumentType.getInteger(context, "limit"))))));

        market.then(Commands.literal("sell")
                .then(Commands.argument("itemKey", StringArgumentType.word())
                        .then(Commands.argument("itemName", StringArgumentType.word())
                                .then(Commands.argument("kind", StringArgumentType.word())
                                        .then(Commands.argument("quantity", IntegerArgumentType.integer(1, 64))
                                                .then(Commands.argument("unitPrice", LongArgumentType.longArg(1))
                                                        .executes(context -> executePlayer(
                                                                context.getSource(),
                                                                runtimeSupplier,
                                                                (runtime, actor) -> parseKind(StringArgumentType.getString(context, "kind"))
                                                                        .map(kind -> runtime.economyCommands().marketSell(
                                                                                actor,
                                                                                StringArgumentType.getString(context, "itemKey"),
                                                                                StringArgumentType.getString(context, "itemName"),
                                                                                kind,
                                                                                IntegerArgumentType.getInteger(context, "quantity"),
                                                                                LongArgumentType.getLong(context, "unitPrice"),
                                                                                Duration.ofHours(24)))
                                                                        .orElseGet(() -> CommandResult.failure("Unknown market kind."))))))))));

        market.then(Commands.literal("buy")
                .then(Commands.argument("listingId", StringArgumentType.word())
                        .then(Commands.argument("quantity", IntegerArgumentType.integer(1, 64))
                                .executes(context -> executePlayer(
                                        context.getSource(),
                                        runtimeSupplier,
                                        (runtime, actor) -> parseUuid(StringArgumentType.getString(context, "listingId"))
                                                .map(listingId -> runtime.economyCommands().marketBuy(
                                                        actor,
                                                        listingId,
                                                        IntegerArgumentType.getInteger(context, "quantity")))
                                                .orElseGet(() -> CommandResult.failure("Invalid listing UUID.")))))));

        market.then(Commands.literal("cancel")
                .then(Commands.argument("listingId", StringArgumentType.word())
                        .executes(context -> executePlayer(
                                context.getSource(),
                                runtimeSupplier,
                                (runtime, actor) -> parseUuid(StringArgumentType.getString(context, "listingId"))
                                        .map(listingId -> runtime.economyCommands().marketCancel(actor, listingId))
                                        .orElseGet(() -> CommandResult.failure("Invalid listing UUID."))))));

        economy.then(market);
        event.getDispatcher().register(economy);
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

        private static Optional<ServerPlayer> resolvePlayer(CommandSourceStack source, String name) {
                if (source.getServer() == null) {
                        return Optional.empty();
                }
                return Optional.ofNullable(source.getServer().getPlayerList().getPlayerByName(name));
        }

        private static Optional<UUID> parseUuid(String value) {
                try {
                        return Optional.of(UUID.fromString(value));
                } catch (IllegalArgumentException exception) {
                        return Optional.empty();
                }
        }

        private static Optional<MarketItemKind> parseKind(String value) {
                try {
                        return Optional.of(MarketItemKind.valueOf(value.toUpperCase()));
                } catch (IllegalArgumentException exception) {
                        return Optional.empty();
                }
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
