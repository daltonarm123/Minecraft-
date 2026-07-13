package com.community.servercore.player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlayerProfileRepository {
    Optional<PlayerProfile> findById(UUID playerId);

    Optional<PlayerProfile> findByUsername(String username);

    List<PlayerProfile> findAll();

    void save(PlayerProfile profile);
}
