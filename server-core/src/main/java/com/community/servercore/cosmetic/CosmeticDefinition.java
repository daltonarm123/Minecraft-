package com.community.servercore.cosmetic;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public record CosmeticDefinition(
        String id,
        String displayName,
        CosmeticCategory category,
        CosmeticRarity rarity,
        CosmeticUnlockSource unlockSource,
        String assetId,
        String description,
        boolean enabled,
        Set<String> tags) {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_.-]{1,64}");
    private static final Pattern ASSET_PATTERN = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public CosmeticDefinition {
        id = normalizeId(id);
        displayName = requireText(displayName, "displayName", 80);
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(rarity, "rarity");
        Objects.requireNonNull(unlockSource, "unlockSource");
        assetId = requireAssetId(assetId);
        description = Objects.requireNonNullElse(description, "").trim();
        if (description.length() > 500) {
            throw new IllegalArgumentException("description must be 500 characters or fewer");
        }
        tags = tags == null
                ? Set.of()
                : tags.stream()
                        .map(CosmeticDefinition::normalizeTag)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public CosmeticDefinition withEnabled(boolean newEnabled) {
        return new CosmeticDefinition(
                id,
                displayName,
                category,
                rarity,
                unlockSource,
                assetId,
                description,
                newEnabled,
                tags);
    }

    private static String normalizeId(String value) {
        String normalized = Objects.requireNonNull(value, "id")
                .trim()
                .toLowerCase(Locale.ROOT);
        if (!ID_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "id must contain only lowercase letters, numbers, dots, underscores, or hyphens");
        }
        return normalized;
    }

    private static String requireAssetId(String value) {
        String normalized = Objects.requireNonNull(value, "assetId")
                .trim()
                .toLowerCase(Locale.ROOT);
        if (!ASSET_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("assetId must use the namespace:path format");
        }
        return normalized;
    }

    private static String requireText(String value, String field, int maximumLength) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(field + " must be " + maximumLength + " characters or fewer");
        }
        return normalized;
    }

    private static String normalizeTag(String value) {
        String normalized = Objects.requireNonNull(value, "tag")
                .trim()
                .toLowerCase(Locale.ROOT);
        if (!ID_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("tags must use cosmetic identifier characters");
        }
        return normalized;
    }
}
