package com.community.servercore.neoforge;

import com.community.servercore.command.CommandActor;
import com.community.servercore.selection.WorldPosition;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.UUID;

final class NeoForgeCommandActor implements CommandActor {
    private final CommandSourceStack source;
    private final ServerPlayer player;

    NeoForgeCommandActor(CommandSourceStack source, ServerPlayer player) {
        this.source = Objects.requireNonNull(source, "source");
        this.player = Objects.requireNonNull(player, "player");
    }

    @Override
    public UUID id() {
        return player.getUUID();
    }

    @Override
    public String name() {
        return player.getName().getString();
    }

    @Override
    public WorldPosition position() {
        return new WorldPosition(
                player.level().dimension().location().toString(),
                player.getX(),
                player.getY(),
                player.getZ());
    }

    @Override
    public boolean hasPermission(String permission) {
        return source.hasPermission(2);
    }
}
