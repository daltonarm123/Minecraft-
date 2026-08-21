package com.community.servercore.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
    private final Path persistenceFile;
    private final Path backupFile;
    private final Gson gson;

    /** In-memory constructor retained for tests and embedded use. */
    public WalletService() {
        this.clock = Clock.systemUTC();
        this.persistenceFile = null;
        this.backupFile = null;
        this.gson = createGson();
    }

    /** In-memory constructor retained for tests and embedded use. */
    public WalletService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.persistenceFile = null;
        this.backupFile = null;
        this.gson = createGson();
    }

    /** Production constructor with durable JSON persistence. */
    public WalletService(Path persistenceFile, Clock clock) throws IOException {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.persistenceFile = Objects.requireNonNull(persistenceFile, "persistenceFile")
                .toAbsolutePath()
                .normalize();
        this.backupFile = this.persistenceFile.resolveSibling(this.persistenceFile.getFileName() + ".bak");
        this.gson = createGson();
        load();
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
            persistUnchecked();
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
            persistUnchecked();
            return transaction;
        } finally {
            lock.unlock();
        }
    }

    private void load() throws IOException {
        if (!Files.exists(persistenceFile)) {
            Path parent = persistenceFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            return;
        }
        try (Reader reader = Files.newBufferedReader(persistenceFile, StandardCharsets.UTF_8)) {
            WalletSnapshot snapshot = gson.fromJson(reader, WalletSnapshot.class);
            if (snapshot == null) {
                return;
            }
            if (snapshot.balances() != null) {
                for (Map.Entry<String, Long> entry : snapshot.balances().entrySet()) {
                    try {
                        UUID id = UUID.fromString(entry.getKey());
                        long value = entry.getValue() == null ? 0L : entry.getValue();
                        if (value >= 0) {
                            balances.put(id, value);
                        }
                    } catch (IllegalArgumentException ignored) {
                        // Ignore malformed account IDs instead of preventing server startup.
                    }
                }
            }
            if (snapshot.ledger() != null) {
                ledger.addAll(snapshot.ledger());
            }
        } catch (RuntimeException exception) {
            throw new IOException("Unable to parse wallet file: " + persistenceFile, exception);
        }
    }

    private void persistUnchecked() {
        if (persistenceFile == null) {
            return;
        }
        try {
            persist();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to persist wallet data", exception);
        }
    }

    private void persist() throws IOException {
        Path parent = persistenceFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Map<String, Long> serializedBalances = new LinkedHashMap<>();
        balances.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> serializedBalances.put(entry.getKey().toString(), entry.getValue()));
        WalletSnapshot snapshot = new WalletSnapshot(serializedBalances, new ArrayList<>(ledger));

        Path temp = Files.createTempFile(parent, persistenceFile.getFileName().toString(), ".tmp");
        try {
            if (Files.exists(persistenceFile)) {
                Files.copy(persistenceFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
            }
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                gson.toJson(snapshot, writer);
            }
            try {
                Files.move(temp, persistenceFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temp, persistenceFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static Gson createGson() {
        return new GsonBuilder()
                .registerTypeAdapter(Instant.class,
                        (JsonSerializer<Instant>) (src, type, context) -> context.serialize(src.toString()))
                .registerTypeAdapter(Instant.class,
                        (JsonDeserializer<Instant>) (json, type, context) -> Instant.parse(json.getAsString()))
                .setPrettyPrinting()
                .create();
    }

    private record WalletSnapshot(Map<String, Long> balances, List<WalletTransaction> ledger) { }
}
