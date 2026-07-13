package com.community.servercore.cosmetic;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CosmeticRepository {
    Optional<CosmeticDefinition> findDefinition(String cosmeticId);

    List<CosmeticDefinition> listDefinitions();

    void saveDefinition(CosmeticDefinition definition);

    CosmeticPlayerState loadPlayerState(UUID playerId);

    List<CosmeticPlayerState> listPlayerStates();

    void savePlayerState(CosmeticPlayerState state);
}
