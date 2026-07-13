package com.community.servercore.service;

import com.community.servercore.portal.Portal;

import java.util.List;
import java.util.Objects;

public record PortalMutationResult(boolean successful, Portal portal, List<String> errors) {
    public PortalMutationResult {
        Objects.requireNonNull(portal, "portal");
        errors = errors == null ? List.of() : List.copyOf(errors);
        if (successful && !errors.isEmpty()) {
            throw new IllegalArgumentException("A successful mutation cannot contain validation errors");
        }
        if (!successful && errors.isEmpty()) {
            throw new IllegalArgumentException("A failed mutation must contain at least one validation error");
        }
    }

    public static PortalMutationResult accepted(Portal portal) {
        return new PortalMutationResult(true, portal, List.of());
    }

    public static PortalMutationResult rejected(Portal portal, List<String> errors) {
        return new PortalMutationResult(false, portal, errors);
    }
}
