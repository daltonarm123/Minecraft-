package com.community.servercore.economy;

import java.util.Objects;

public record ShopItemDefinition(
        String itemId,
        String displayName,
        MarketItemKind kind,
        long priceMinor,
        String description) {

    public ShopItemDefinition {
        itemId = normalizeId(itemId);
        displayName = requireText(displayName, "displayName", 80);
        Objects.requireNonNull(kind, "kind");
        if (priceMinor < 1) {
            throw new IllegalArgumentException("priceMinor must be positive");
        }
        description = Objects.requireNonNullElse(description, "").trim();
        if (description.length() > 400) {
            throw new IllegalArgumentException("description must be 400 characters or fewer");
        }
    }

    private static String normalizeId(String value) {
        String normalized = Objects.requireNonNull(value, "itemId").trim().toLowerCase();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("itemId must not be blank");
        }
        return normalized;
    }

    private static String requireText(String value, String field, int maxLength) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " must be " + maxLength + " characters or fewer");
        }
        return normalized;
    }
}
