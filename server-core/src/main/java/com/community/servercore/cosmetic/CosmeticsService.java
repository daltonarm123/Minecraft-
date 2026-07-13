package com.community.servercore.cosmetic;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class CosmeticsService {
    private final CosmeticRepository repository;

    public CosmeticsService(CosmeticRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public synchronized CosmeticDefinition registerDefinition(CosmeticDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (repository.findDefinition(definition.id()).isPresent()) {
            throw new IllegalArgumentException("Cosmetic already exists: " + definition.id());
        }
        repository.saveDefinition(definition);
        return definition;
    }

    public synchronized boolean registerIfAbsent(CosmeticDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (repository.findDefinition(definition.id()).isPresent()) {
            return false;
        }
        repository.saveDefinition(definition);
        return true;
    }

    public synchronized CosmeticDefinition updateDefinition(CosmeticDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        requireDefinition(definition.id());
        repository.saveDefinition(definition);
        return definition;
    }

    public synchronized CosmeticDefinition setEnabled(String cosmeticId, boolean enabled) {
        CosmeticDefinition updated = requireDefinition(cosmeticId).withEnabled(enabled);
        repository.saveDefinition(updated);
        if (!enabled) {
            unequipForAllPlayers(updated.id());
        }
        return updated;
    }

    public Optional<CosmeticDefinition> findDefinition(String cosmeticId) {
        return repository.findDefinition(normalizeId(cosmeticId));
    }

    public List<CosmeticDefinition> listDefinitions(boolean includeDisabled) {
        return repository.listDefinitions().stream()
                .filter(definition -> includeDisabled || definition.enabled())
                .toList();
    }

    public CosmeticPlayerState state(UUID playerId) {
        return repository.loadPlayerState(Objects.requireNonNull(playerId, "playerId"));
    }

    public synchronized boolean grant(UUID playerId, String cosmeticId) {
        Objects.requireNonNull(playerId, "playerId");
        CosmeticDefinition definition = requireDefinition(cosmeticId);
        CosmeticPlayerState current = repository.loadPlayerState(playerId);
        if (current.ownedCosmeticIds().contains(definition.id())) {
            return false;
        }

        Set<String> owned = new HashSet<>(current.ownedCosmeticIds());
        owned.add(definition.id());
        repository.savePlayerState(new CosmeticPlayerState(
                playerId,
                owned,
                current.equippedByCategory()));
        return true;
    }

    public synchronized boolean revoke(UUID playerId, String cosmeticId) {
        Objects.requireNonNull(playerId, "playerId");
        String normalizedId = normalizeId(cosmeticId);
        CosmeticPlayerState current = repository.loadPlayerState(playerId);
        if (!current.ownedCosmeticIds().contains(normalizedId)) {
            return false;
        }

        Set<String> owned = new HashSet<>(current.ownedCosmeticIds());
        owned.remove(normalizedId);
        EnumMap<CosmeticCategory, String> equipped = new EnumMap<>(CosmeticCategory.class);
        equipped.putAll(current.equippedByCategory());
        equipped.entrySet().removeIf(entry -> entry.getValue().equals(normalizedId));
        repository.savePlayerState(new CosmeticPlayerState(playerId, owned, equipped));
        return true;
    }

    public synchronized CosmeticPlayerState equip(UUID playerId, String cosmeticId) {
        Objects.requireNonNull(playerId, "playerId");
        CosmeticDefinition definition = requireDefinition(cosmeticId);
        if (!definition.enabled()) {
            throw new IllegalStateException("Cosmetic is disabled: " + definition.id());
        }

        CosmeticPlayerState current = repository.loadPlayerState(playerId);
        if (!current.ownedCosmeticIds().contains(definition.id())) {
            throw new IllegalStateException("Player does not own cosmetic: " + definition.id());
        }

        EnumMap<CosmeticCategory, String> equipped = new EnumMap<>(CosmeticCategory.class);
        equipped.putAll(current.equippedByCategory());
        equipped.put(definition.category(), definition.id());
        CosmeticPlayerState updated = new CosmeticPlayerState(
                playerId,
                current.ownedCosmeticIds(),
                equipped);
        repository.savePlayerState(updated);
        return updated;
    }

    public synchronized CosmeticPlayerState unequip(UUID playerId, CosmeticCategory category) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(category, "category");
        CosmeticPlayerState current = repository.loadPlayerState(playerId);
        if (!current.equippedByCategory().containsKey(category)) {
            return current;
        }

        EnumMap<CosmeticCategory, String> equipped = new EnumMap<>(CosmeticCategory.class);
        equipped.putAll(current.equippedByCategory());
        equipped.remove(category);
        CosmeticPlayerState updated = new CosmeticPlayerState(
                playerId,
                current.ownedCosmeticIds(),
                equipped);
        repository.savePlayerState(updated);
        return updated;
    }

    public List<CosmeticDefinition> equippedDefinitions(UUID playerId) {
        Map<CosmeticCategory, String> equipped = state(playerId).equippedByCategory();
        return equipped.values().stream()
                .map(this::requireDefinition)
                .toList();
    }

    private void unequipForAllPlayers(String cosmeticId) {
        for (CosmeticPlayerState current : repository.listPlayerStates()) {
            boolean equipped = current.equippedByCategory().containsValue(cosmeticId);
            if (!equipped) {
                continue;
            }
            EnumMap<CosmeticCategory, String> updatedEquipment =
                    new EnumMap<>(CosmeticCategory.class);
            updatedEquipment.putAll(current.equippedByCategory());
            updatedEquipment.entrySet().removeIf(entry -> entry.getValue().equals(cosmeticId));
            repository.savePlayerState(new CosmeticPlayerState(
                    current.playerId(),
                    current.ownedCosmeticIds(),
                    updatedEquipment));
        }
    }

    private CosmeticDefinition requireDefinition(String cosmeticId) {
        String normalizedId = normalizeId(cosmeticId);
        return repository.findDefinition(normalizedId)
                .orElseThrow(() -> new IllegalArgumentException("Cosmetic not found: " + normalizedId));
    }

    private static String normalizeId(String cosmeticId) {
        if (cosmeticId == null || cosmeticId.isBlank()) {
            throw new IllegalArgumentException("cosmeticId must not be blank");
        }
        return cosmeticId.trim().toLowerCase(Locale.ROOT);
    }
}
