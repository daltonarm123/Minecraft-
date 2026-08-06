package com.community.servercore.portal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PortalDestinationTest {
    @Test
    void locationDestinationsRequireCoordinates() {
        assertThatThrownBy(() -> new PortalDestination(DestinationType.LOCATION, "overworld", null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LOCATION destinations require x, y, and z");
    }

    @Test
    void worldDestinationsAreBuiltFromAWorldName() {
        PortalDestination destination = PortalDestination.world("overworld");

        assertThat(destination.type()).isEqualTo(DestinationType.WORLD);
        assertThat(destination.target()).isEqualTo("overworld");
    }
}
