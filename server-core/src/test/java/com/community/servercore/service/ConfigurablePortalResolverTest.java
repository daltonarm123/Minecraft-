package com.community.servercore.service;

import com.community.servercore.portal.DestinationType;
import com.community.servercore.portal.PortalDestination;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurablePortalResolverTest {
    @Test
    void resolvesConfiguredRemoteRouteTargets() {
        PortalDestination mapped = PortalDestination.location("survival", 100, 70, 100, 0, 0);
        ConfigurablePortalResolver resolver = new ConfigurablePortalResolver(Map.of(
                "server:hub", mapped,
                "arena:duels", PortalDestination.world("arena-duel")));

        PortalDestination resolved = resolver.resolve(new PortalDestination(DestinationType.SERVER, "hub", null, null, null, null, null))
                .orElseThrow();

        assertThat(resolved).isEqualTo(mapped);
    }

    @Test
    void leavesUnmappedDestinationsUnresolved() {
        ConfigurablePortalResolver resolver = new ConfigurablePortalResolver(Map.of());

        assertThat(resolver.resolve(new PortalDestination(DestinationType.EVENT, "launch", null, null, null, null, null))).isEmpty();
    }
}
