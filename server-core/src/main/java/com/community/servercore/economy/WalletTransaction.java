package com.community.servercore.economy;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record WalletTransaction(
        UUID transactionId,
        UUID accountId,
        WalletTransactionType type,
        long amountMinor,
        Instant occurredAt,
        String reason,
        UUID counterpartyId,
        Map<String, String> attributes) {

    public WalletTransaction {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(occurredAt, "occurredAt");
        reason = Objects.requireNonNullElse(reason, "").trim();
        if (reason.length() > 200) {
            throw new IllegalArgumentException("reason must be 200 characters or fewer");
        }
        if (amountMinor < 1) {
            throw new IllegalArgumentException("amountMinor must be positive");
        }
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
