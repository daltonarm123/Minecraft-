package com.community.servercore.service;

import com.community.servercore.portal.PortalDestination;
import com.community.servercore.portal.DestinationType;

import java.util.Objects;
import java.util.Optional;

public interface PortalResolver {
    Optional<PortalDestination> resolve(PortalDestination destination);

    static PortalResolver identity() {
        return destination -> Optional.of(destination);
    }

    static PortalResolver forType(DestinationType type, PortalResolver delegate) {
        Objects.requireNonNull(delegate, "delegate");
        return destination -> destination.type() == type ? delegate.resolve(destination) : Optional.of(destination);
    }
}
