package com.community.servercore.cosmetic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JsonCosmeticRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void savesAndReloadsCatalogAndPlayerWardrobe() throws Exception {
        Path file = temporaryDirectory.resolve("cosmetics.json");
        UUID playerId = UUID.randomUUID();
        CosmeticDefinition definition = new CosmeticDefinition(
                "founder-crown",
                "Founder Crown",
                CosmeticCategory.HEAD,
                CosmeticRarity.LEGENDARY,
                CosmeticUnlockSource.FOUNDER,
                "servercore:cosmetics/founder-crown",
                "Original founder cosmetic",
                true,
                Set.of("launch"));

        JsonCosmeticRepository firstRepository = new JsonCosmeticRepository(file);
        CosmeticsService firstService = new CosmeticsService(firstRepository);
        firstService.registerDefinition(definition);
        firstService.grant(playerId, definition.id());
        firstService.equip(playerId, definition.id());

        JsonCosmeticRepository reloadedRepository = new JsonCosmeticRepository(file);
        CosmeticsService reloadedService = new CosmeticsService(reloadedRepository);

        assertThat(reloadedService.listDefinitions(true)).containsExactly(definition);
        assertThat(reloadedService.state(playerId).ownedCosmeticIds())
                .containsExactly(definition.id());
        assertThat(reloadedService.state(playerId).equippedByCategory())
                .containsEntry(CosmeticCategory.HEAD, definition.id());
        assertThat(file.resolveSibling("cosmetics.json.bak")).exists();
    }
}
