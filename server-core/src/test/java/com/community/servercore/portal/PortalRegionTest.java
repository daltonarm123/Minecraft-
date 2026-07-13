package com.community.servercore.portal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortalRegionTest {
    @Test
    void normalizesCoordinates() {
        PortalRegion region = new PortalRegion(10, 20, 30, 1, 2, 3);

        assertThat(region.minX()).isEqualTo(1);
        assertThat(region.maxX()).isEqualTo(10);
        assertThat(region.minY()).isEqualTo(2);
        assertThat(region.maxY()).isEqualTo(20);
        assertThat(region.minZ()).isEqualTo(3);
        assertThat(region.maxZ()).isEqualTo(30);
    }

    @Test
    void containsBoundaryAndInteriorCoordinates() {
        PortalRegion region = new PortalRegion(0, 0, 0, 10, 10, 10);

        assertThat(region.contains(0, 0, 0)).isTrue();
        assertThat(region.contains(5, 5, 5)).isTrue();
        assertThat(region.contains(11, 5, 5)).isFalse();
    }

    @Test
    void rejectsNonFiniteCoordinates() {
        assertThatThrownBy(() -> new PortalRegion(Double.NaN, 0, 0, 1, 1, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minX");
    }
}
