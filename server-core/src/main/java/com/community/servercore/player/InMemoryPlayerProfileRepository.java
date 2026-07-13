package com.community.servercore.player;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryPlayerProfileRepository implements PlayerProfileRepository {
    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();

    @Override
    public Optional<PlayerProfile> findById(UUID playerId) {
        return Optional.ofNullable(profiles.get(playerId));
    }

    @Override
    public Optional<PlayerProfile> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        String normalized = username.trim();
        return profiles.values().stream()
                .filter(profile -> profile.username().equalsIgnoreCase(normalized))
                .findFirst();
    }

    @Override
    public List<PlayerProfile> findAll() {
        return profiles.values().stream()
                .sorted(Comparator.comparingInt(PlayerProfile::rating).reversed()
                        .thenComparing(PlayerProfile::username, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    public void save(PlayerProfile profile) {
        profiles.put(profile.playerId(), profile);
    }
}
