package com.community.servercore.duel;

import com.community.servercore.selection.WorldPosition;

import java.util.Objects;

public record ArenaDefinition(
        String arenaId,
        WorldPosition firstSpawn,
        WorldPosition secondSpawn,
        WorldPosition spectatorSpawn,
        boolean enabled) {

    public ArenaDefinition {
        if (arenaId == null || arenaId.isBlank()) {
            throw new IllegalArgumentException("arenaId must not be blank");
        }
        arenaId = arenaId.trim().toLowerCase();
        Objects.requireNonNull(firstSpawn, "firstSpawn");
        Objects.requireNonNull(secondSpawn, "secondSpawn");
        if (!firstSpawn.world().equalsIgnoreCase(secondSpawn.world())) {
            throw new IllegalArgumentException("Arena player spawns must be in the same world");
        }
        if (spectatorSpawn != null && !firstSpawn.world().equalsIgnoreCase(spectatorSpawn.world())) {
            throw new IllegalArgumentException("Spectator spawn must be in the arena world");
        }
    }
}
