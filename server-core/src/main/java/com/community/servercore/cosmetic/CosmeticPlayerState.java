package com.community.servercore.cosmetic;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record CosmeticPlayerState(
        UUID playerId,
        Set<String> ownedCosmeticIds,
        Map<CosmeticCategory, String> equippedByCategory) {

    public CosmeticPlayerState {
        Objects.requireNonNull(playerId, "playerId");
        ownedCosmeticIds = ownedCosmeticIds == null ? Set.of() : Set.copyOf(ownedCosmeticIds);

        EnumMap<CosmeticCategory, String> equipped = new EnumMap<>(CosmeticCategory.class);
        if (equippedByCategory != null) {
            equippedByCategory.forEach((category, cosmeticId) -> {
                Objects.requireNonNull(category, "equipped category");
                Objects.requireNonNull(cosmeticId, "equipped cosmeticId");
                equipped.put(category, cosmeticId);
            });
        }
        equippedByCategory = Map.copyOf(equipped);
    }

    public static CosmeticPlayerState empty(UUID playerId) {
        return new CosmeticPlayerState(playerId, Set.of(), Map.of());
    }
}
