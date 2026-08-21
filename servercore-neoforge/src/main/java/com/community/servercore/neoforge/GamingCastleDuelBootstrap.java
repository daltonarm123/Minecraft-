package com.community.servercore.neoforge;

import com.community.servercore.ServerCoreRuntime;
import com.community.servercore.duel.ArenaDefinition;
import com.community.servercore.selection.WorldPosition;

final class GamingCastleDuelBootstrap {
    static final String ARENA_ID = "gaming_castle_main";
    static final GamingCastleDataStore.SavedLocation LOBBY =
            new GamingCastleDataStore.SavedLocation("minecraft:overworld", -1500.0, 72.0, 30.0, 180.0F, 0.0F);

    private GamingCastleDuelBootstrap() { }

    static void ensure(ServerCoreRuntime runtime) {
        runtime.arenas().register(new ArenaDefinition(
                ARENA_ID,
                new WorldPosition("minecraft:overworld", -1515.0, 73.0, -5.0),
                new WorldPosition("minecraft:overworld", -1485.0, 73.0, -5.0),
                new WorldPosition("minecraft:overworld", -1500.0, 78.0, -35.0),
                true));
    }
}
