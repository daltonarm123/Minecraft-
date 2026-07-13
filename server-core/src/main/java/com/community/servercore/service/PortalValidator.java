package com.community.servercore.service;

import com.community.servercore.portal.Portal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PortalValidator {
    public ValidationResult validate(Portal candidate, List<Portal> existing, boolean allowOverlaps) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(existing, "existing");

        List<String> errors = new ArrayList<>();
        if (!candidate.name().matches("[a-z0-9_-]{2,32}")) {
            errors.add("Portal name must be 2-32 characters using lowercase letters, numbers, underscores, or hyphens");
        }

        for (Portal portal : existing) {
            if (portal.id().equals(candidate.id())) {
                continue;
            }
            if (portal.name().equalsIgnoreCase(candidate.name())) {
                errors.add("A portal named '" + candidate.name() + "' already exists");
            }
            if (!allowOverlaps
                    && portal.sourceWorld().equalsIgnoreCase(candidate.sourceWorld())
                    && portal.region().overlaps(candidate.region())) {
                errors.add("Portal region overlaps with '" + portal.name() + "'");
            }
        }

        return new ValidationResult(errors.isEmpty(), List.copyOf(errors));
    }

    public record ValidationResult(boolean valid, List<String> errors) {
        public ValidationResult {
            errors = List.copyOf(errors);
        }
    }
}
