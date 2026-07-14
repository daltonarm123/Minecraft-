package com.community.servercore.economy;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

public final class WalletService {
    private final Clock clock;
    private final Map<UUID, Long> balances = new ConcurrentHashMap<>();
    private final List<WalletTransaction> ledger = new CopyOnWriteArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();

    public WalletService() {
        this(Clock.systemUTC());
    }

    public WalletService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public long balance(UUID accountId) {
        Objects.requireNonNull(accountId, "accountId");
        return balances.getOrDefault(accountId, 0L);
    }

    public List<WalletTransaction> recentTransactions(UUID accountId, int limit) {
        Objects.requireNonNull(accountId, "accountId");
        if (limit < 1 || limit > 1_000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
        List<WalletTransaction> filtered = new ArrayList<>();
        for (WalletTransaction transaction : ledger) {
            if (transaction.accountId().equals(accountId)) {
                filtered.add(transaction);
            }
        }
        filtered.sort(Comparator.comparing(WalletTransaction::occurredAt).reversed());
        return filtered.size() <= limit ? List.copyOf(filtered) : List.copyOf(filtered.subList(0, limit));
    }

    public WalletTransaction credit(
            UUID accountId,
            long amountMinor,
            WalletTransactionType type,
            String reason,
            UUID counterpartyId,
            Map<String, String> attributes) {
        if (type != WalletTransactionType.CREDIT
                && type != WalletTransactionType.TRANSFER_IN
                && type != WalletTransactionType.MARKET_SALE) {
            throw new IllegalArgumentException("Unsupported credit transaction type: " + type);
        }
        return post(accountId, amountMinor, true, type, reason, counterpartyId, attributes);
    }

    public WalletTransaction debit(
            UUID accountId,
            long amountMinor,
            WalletTransactionType type,
            String reason,
            UUID counterpartyId,
            Map<String, String> attributes) {
        if (type != WalletTransactionType.DEBIT
                && type != WalletTransactionType.TRANSFER_OUT
                && type != WalletTransactionType.LISTING_FEE
                && type != WalletTransactionType.MARKET_TAX
                && type != WalletTransactionType.SHOP_PURCHASE) {
            throw new IllegalArgumentException("Unsupported debit transaction type: " + type);
        }
        return post(accountId, amountMinor, false, type, reason, counterpartyId, attributes);
    }

    public void transfer(
            UUID fromAccountId,
            UUID toAccountId,
            long amountMinor,
            String reason,
            Map<String, String> attributes,
            WalletTransactionType debitType,
            WalletTransactionType creditType) {
        Objects.requireNonNull(fromAccountId, "fromAccountId");
        Objects.requireNonNull(toAccountId, "toAccountId");
        if (fromAccountId.equals(toAccountId)) {
            throw new IllegalArgumentException("source and destination accounts must be different");
        }
        if (amountMinor < 1) {
            throw new IllegalArgumentException("amountMinor must be positive");
        }

        lock.lock();
        try {
            long sourceBalance = balances.getOrDefault(fromAccountId, 0L);
            if (sourceBalance < amountMinor) {
                throw new IllegalStateException("Insufficient funds");
            }
            balances.put(fromAccountId, sourceBalance - amountMinor);
            balances.put(toAccountId, balances.getOrDefault(toAccountId, 0L) + amountMinor);

            ledger.add(new WalletTransaction(
                    UUID.randomUUID(),
                    fromAccountId,
                    debitType,
                    amountMinor,
                    clock.instant(),
                    reason,
                    toAccountId,
                    attributes));
            ledger.add(new WalletTransaction(
                    UUID.randomUUID(),
                    toAccountId,
                    creditType,
                    amountMinor,
                    clock.instant(),
                    reason,
                    fromAccountId,
                    attributes));
        } finally {
            lock.unlock();
        }
    }

    private WalletTransaction post(
            UUID accountId,
            long amountMinor,
            boolean credit,
            WalletTransactionType type,
            String reason,
            UUID counterpartyId,
            Map<String, String> attributes) {
        Objects.requireNonNull(accountId, "accountId");
        if (amountMinor < 1) {
            throw new IllegalArgumentException("amountMinor must be positive");
        }

        lock.lock();
        try {
            long existing = balances.getOrDefault(accountId, 0L);
            long updated = credit ? existing + amountMinor : existing - amountMinor;
            if (updated < 0) {
                throw new IllegalStateException("Insufficient funds");
            }
            balances.put(accountId, updated);
            WalletTransaction transaction = new WalletTransaction(
                    UUID.randomUUID(),
                    accountId,
                    type,
                    amountMinor,
                    clock.instant(),
                    reason,
                    counterpartyId,
                    attributes);
            ledger.add(transaction);
            return transaction;
        } finally {
            lock.unlock();
        }
    }
}
