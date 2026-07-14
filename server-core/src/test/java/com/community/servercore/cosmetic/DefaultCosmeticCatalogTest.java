package com.community.servercore.cosmetic;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultCosmeticCatalogTest {
    @Test
    void seedsOriginalCatalogIdempotently() {
        CosmeticsService service = new CosmeticsService(new InMemoryCosmeticRepository());

        assertThat(DefaultCosmeticCatalog.seed(service)).isEqualTo(9);
        assertThat(DefaultCosmeticCatalog.seed(service)).isZero();
        assertThat(service.listDefinitions(false))
                .extracting(CosmeticDefinition::id)
            .containsExactlyInAnyOrder(
                "crimson-warden-outfit",
                        "ember-trail",
                        "founder-crown",
                "guild-banner-back",
                "merchant-cap",
                "nomad-outfit",
                        "pioneer-title",
                "starlight-robe-outfit",
                        "void-nameplate");
    }
}
