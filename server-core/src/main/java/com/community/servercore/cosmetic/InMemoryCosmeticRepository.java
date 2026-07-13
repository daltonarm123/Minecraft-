package com.community.servercore.cosmetic;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryCosmeticRepository implements CosmeticRepository {
    private final Map<String, CosmeticDefinition> definitions = new ConcurrentHashMap<>();
    private final Map<UUID, CosmeticPlayerState> playerStates = new ConcurrentHashMap<>();

    @Override
    public Optional<CosmeticDefinition> findDefinition(String cosmeticId) {
        return Optional.ofNullable(definitions.get(normalizeId(cosmeticId)));
    }

    @Override
    public List<CosmeticDefinition> listDefinitions() {
        return definitions.values().stream()
                .sorted(Comparator.comparing(CosmeticDefinition::id))
                .toList();
    }

    @Override
    public void saveDefinition(CosmeticDefinition definition) {
        definitions.put(definition.id(), definition);
    }

    @Override
    public CosmeticPlayerState loadPlayerState(UUID playerId) {
        return playerStates.getOrDefault(playerId, CosmeticPlayerState.empty(playerId));
    }

    @Override
    public void savePlayerState(CosmeticPlayerState state) {
        playerStates.put(state.playerId(), state);
    }

    private static String normalizeId(String cosmeticId) {
        if (cosmeticId == null || cosmeticId.isBlank()) {
            throw new IllegalArgumentException("cosmeticId must not be blank");
        }
        return cosmeticId.trim().toLowerCase(Locale.ROOT);
    }
}
