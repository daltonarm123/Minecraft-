package com.community.servercore.cosmetic;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultCosmeticCatalogTest {
    @Test
    void seedsOriginalCatalogIdempotently() {
        CosmeticsService service = new CosmeticsService(new InMemoryCosmeticRepository());

        assertThat(DefaultCosmeticCatalog.seed(service)).isEqualTo(4);
        assertThat(DefaultCosmeticCatalog.seed(service)).isZero();
        assertThat(service.listDefinitions(false))
                .extracting(CosmeticDefinition::id)
                .containsExactly(
                        "ember-trail",
                        "founder-crown",
                        "pioneer-title",
                        "void-nameplate");
    }
}
