package com.community.servercore.economy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MarketListing(
        UUID listingId,
        UUID sellerId,
        String itemKey,
        String itemName,
        MarketItemKind kind,
        int quantity,
        long unitPriceMinor,
        Instant createdAt,
        Instant expiresAt,
        MarketListingStatus status) {

    public MarketListing {
        Objects.requireNonNull(listingId, "listingId");
        Objects.requireNonNull(sellerId, "sellerId");
        itemKey = normalize(itemKey, "itemKey", 80);
        itemName = normalize(itemName, "itemName", 80);
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(status, "status");
        if (status == MarketListingStatus.ACTIVE || status == MarketListingStatus.PARTIALLY_FILLED) {
            if (quantity < 1) {
                throw new IllegalArgumentException("active listings must have positive quantity");
            }
        } else if (quantity < 0) {
            throw new IllegalArgumentException("quantity must be non-negative");
        }
        if (unitPriceMinor < 1) {
            throw new IllegalArgumentException("unitPriceMinor must be positive");
        }
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
    }

    public MarketListing withQuantityAndStatus(int newQuantity, MarketListingStatus newStatus) {
        return new MarketListing(
                listingId,
                sellerId,
                itemKey,
                itemName,
                kind,
                newQuantity,
                unitPriceMinor,
                createdAt,
                expiresAt,
                newStatus);
    }

    private static String normalize(String value, String field, int maxLength) {
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
