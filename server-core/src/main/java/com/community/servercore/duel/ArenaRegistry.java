package com.community.servercore.duel;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ArenaRegistry {
    private final Map<String, ArenaDefinition> arenas = new ConcurrentHashMap<>();
    private final Map<UUID, String> arenaByMatch = new ConcurrentHashMap<>();
    private final Set<String> reservedArenaIds = ConcurrentHashMap.newKeySet();

    public void register(ArenaDefinition arena) {
        Objects.requireNonNull(arena, "arena");
        arenas.put(arena.arenaId(), arena);
    }

    public boolean remove(String arenaId) {
        String normalized = normalize(arenaId);
        if (reservedArenaIds.contains(normalized)) {
            return false;
        }
        return arenas.remove(normalized) != null;
    }

    public Optional<ArenaDefinition> find(String arenaId) {
        return Optional.ofNullable(arenas.get(normalize(arenaId)));
    }

    public List<ArenaDefinition> list() {
        return arenas.values().stream()
                .sorted(Comparator.comparing(ArenaDefinition::arenaId))
                .toList();
    }

    public synchronized Optional<ArenaDefinition> reserveAny(UUID matchId) {
        Objects.requireNonNull(matchId, "matchId");
        String existing = arenaByMatch.get(matchId);
        if (existing != null) {
            return Optional.ofNullable(arenas.get(existing));
        }

        Optional<ArenaDefinition> available = arenas.values().stream()
                .filter(ArenaDefinition::enabled)
                .filter(arena -> !reservedArenaIds.contains(arena.arenaId()))
                .sorted(Comparator.comparing(ArenaDefinition::arenaId))
                .findFirst();
        available.ifPresent(arena -> {
            reservedArenaIds.add(arena.arenaId());
            arenaByMatch.put(matchId, arena.arenaId());
        });
        return available;
    }

    public synchronized void release(UUID matchId) {
        Objects.requireNonNull(matchId, "matchId");
        String arenaId = arenaByMatch.remove(matchId);
        if (arenaId != null) {
            reservedArenaIds.remove(arenaId);
        }
    }

    public boolean isReserved(String arenaId) {
        return reservedArenaIds.contains(normalize(arenaId));
    }

    private static String normalize(String arenaId) {
        if (arenaId == null || arenaId.isBlank()) {
            throw new IllegalArgumentException("arenaId must not be blank");
        }
        return arenaId.trim().toLowerCase();
    }
}
