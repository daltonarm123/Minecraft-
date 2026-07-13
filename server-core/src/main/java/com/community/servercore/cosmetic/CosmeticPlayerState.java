package com.community.servercore.cosmetic;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Locale;
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
        Set<String> normalizedOwned = new HashSet<>();
        if (ownedCosmeticIds != null) {
            ownedCosmeticIds.forEach(id -> normalizedOwned.add(normalizeId(id)));
        }
        ownedCosmeticIds = Set.copyOf(normalizedOwned);

        EnumMap<CosmeticCategory, String> equipped = new EnumMap<>(CosmeticCategory.class);
        if (equippedByCategory != null) {
            equippedByCategory.forEach((category, cosmeticId) -> {
                Objects.requireNonNull(category, "equipped category");
                String normalizedId = normalizeId(cosmeticId);
                if (!normalizedOwned.contains(normalizedId)) {
                    throw new IllegalArgumentException(
                            "Equipped cosmetic must also be owned: " + normalizedId);
                }
                equipped.put(category, normalizedId);
            });
        }
        equippedByCategory = Map.copyOf(equipped);
    }

    public static CosmeticPlayerState empty(UUID playerId) {
        return new CosmeticPlayerState(playerId, Set.of(), Map.of());
    }

    private static String normalizeId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("cosmetic ID must not be blank");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
