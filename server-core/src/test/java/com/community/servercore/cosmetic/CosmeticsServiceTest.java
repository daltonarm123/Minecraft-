package com.community.servercore.cosmetic;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CosmeticsServiceTest {
    @Test
    void grantsAndEquipsOwnedCosmetic() {
        CosmeticsService service = serviceWith(
                cosmetic("founder-crown", CosmeticCategory.HEAD, true));
        UUID playerId = UUID.randomUUID();

        assertThat(service.grant(playerId, "founder-crown")).isTrue();
        CosmeticPlayerState state = service.equip(playerId, "founder-crown");

        assertThat(state.ownedCosmeticIds()).containsExactly("founder-crown");
        assertThat(state.equippedByCategory())
                .containsEntry(CosmeticCategory.HEAD, "founder-crown");
    }

    @Test
    void rejectsEquippingUnownedCosmetic() {
        CosmeticsService service = serviceWith(
                cosmetic("founder-crown", CosmeticCategory.HEAD, true));
        UUID playerId = UUID.randomUUID();

        assertThatThrownBy(() -> service.equip(playerId, "founder-crown"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not own");
    }

    @Test
    void replacesExistingCosmeticInSameCategory() {
        CosmeticsService service = serviceWith(
                cosmetic("founder-crown", CosmeticCategory.HEAD, true),
                cosmetic("ember-mask", CosmeticCategory.HEAD, true));
        UUID playerId = UUID.randomUUID();
        service.grant(playerId, "founder-crown");
        service.grant(playerId, "ember-mask");
        service.equip(playerId, "founder-crown");

        CosmeticPlayerState state = service.equip(playerId, "ember-mask");

        assertThat(state.equippedByCategory())
                .containsOnlyKeys(CosmeticCategory.HEAD)
                .containsEntry(CosmeticCategory.HEAD, "ember-mask");
    }

    @Test
    void revokeAlsoUnequipsCosmetic() {
        CosmeticsService service = serviceWith(
                cosmetic("founder-crown", CosmeticCategory.HEAD, true));
        UUID playerId = UUID.randomUUID();
        service.grant(playerId, "founder-crown");
        service.equip(playerId, "founder-crown");

        assertThat(service.revoke(playerId, "founder-crown")).isTrue();

        CosmeticPlayerState state = service.state(playerId);
        assertThat(state.ownedCosmeticIds()).isEmpty();
        assertThat(state.equippedByCategory()).isEmpty();
    }

    @Test
    void disabledCosmeticCannotBeEquipped() {
        CosmeticsService service = serviceWith(
                cosmetic("founder-crown", CosmeticCategory.HEAD, true));
        UUID playerId = UUID.randomUUID();
        service.grant(playerId, "founder-crown");
        service.setEnabled("founder-crown", false);

        assertThatThrownBy(() -> service.equip(playerId, "founder-crown"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void rejectsDuplicateDefinitions() {
        CosmeticsService service = new CosmeticsService(new InMemoryCosmeticRepository());
        CosmeticDefinition definition = cosmetic("founder-crown", CosmeticCategory.HEAD, true);
        service.registerDefinition(definition);

        assertThatThrownBy(() -> service.registerDefinition(definition))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    private static CosmeticsService serviceWith(CosmeticDefinition... definitions) {
        CosmeticsService service = new CosmeticsService(new InMemoryCosmeticRepository());
        for (CosmeticDefinition definition : definitions) {
            service.registerDefinition(definition);
        }
        return service;
    }

    private static CosmeticDefinition cosmetic(
            String id,
            CosmeticCategory category,
            boolean enabled) {
        return new CosmeticDefinition(
                id,
                id,
                category,
                CosmeticRarity.RARE,
                CosmeticUnlockSource.FOUNDER,
                "servercore:cosmetics/" + id,
                "Original ServerCore cosmetic",
                enabled,
                Set.of("launch"));
    }
}
