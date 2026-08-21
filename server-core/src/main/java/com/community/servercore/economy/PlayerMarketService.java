package com.community.servercore.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class PlayerMarketService {
    private static final Type LISTING_LIST_TYPE = new TypeToken<List<MarketListing>>() { }.getType();

    public static final UUID TREASURY_ACCOUNT_ID = UUID.nameUUIDFromBytes("servercore-treasury".getBytes());

    private final WalletService wallets;
    private final Clock clock;
    private final long listingFeeMinor;
    private final int salesTaxBasisPoints;
    private final Map<UUID, MarketListing> listings = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Path persistenceFile;
    private final Path backupFile;
    private final Gson gson;

    /** In-memory constructor retained for tests. */
    public PlayerMarketService(
            WalletService wallets,
            long listingFeeMinor,
            int salesTaxBasisPoints,
            Clock clock) {
        this(wallets, listingFeeMinor, salesTaxBasisPoints, clock, null, false);
    }

    /** Production constructor with durable JSON listing persistence. */
    public PlayerMarketService(
            WalletService wallets,
            long listingFeeMinor,
            int salesTaxBasisPoints,
            Clock clock,
            Path persistenceFile) throws IOException {
        this(wallets, listingFeeMinor, salesTaxBasisPoints, clock, persistenceFile, true);
    }

    private PlayerMarketService(
            WalletService wallets,
            long listingFeeMinor,
            int salesTaxBasisPoints,
            Clock clock,
            Path persistenceFile,
            boolean loadPersistence) {
        this.wallets = Objects.requireNonNull(wallets, "wallets");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (listingFeeMinor < 0) {
            throw new IllegalArgumentException("listingFeeMinor must be non-negative");
        }
        if (salesTaxBasisPoints < 0 || salesTaxBasisPoints > 5000) {
            throw new IllegalArgumentException("salesTaxBasisPoints must be between 0 and 5000");
        }
        this.listingFeeMinor = listingFeeMinor;
        this.salesTaxBasisPoints = salesTaxBasisPoints;
        this.persistenceFile = persistenceFile == null ? null : persistenceFile.toAbsolutePath().normalize();
        this.backupFile = this.persistenceFile == null
                ? null
                : this.persistenceFile.resolveSibling(this.persistenceFile.getFileName() + ".bak");
        this.gson = new GsonBuilder()
                .registerTypeAdapter(Instant.class,
                        (JsonSerializer<Instant>) (src, type, context) -> context.serialize(src.toString()))
                .registerTypeAdapter(Instant.class,
                        (JsonDeserializer<Instant>) (json, type, context) -> Instant.parse(json.getAsString()))
                .setPrettyPrinting()
                .create();
        if (loadPersistence) {
            try {
                load();
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to load player market listings", exception);
            }
        }
    }

    public MarketListing createListing(
            UUID sellerId,
            String itemKey,
            String itemName,
            MarketItemKind kind,
            int quantity,
            long unitPriceMinor,
            Duration duration) {
        Objects.requireNonNull(sellerId, "sellerId");
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("duration must be positive");
        }

        Instant now = clock.instant();
        if (listingFeeMinor > 0) {
            wallets.transfer(
                    sellerId,
                    TREASURY_ACCOUNT_ID,
                    listingFeeMinor,
                    "market-listing-fee",
                    Map.of("sellerId", sellerId.toString()),
                    WalletTransactionType.LISTING_FEE,
                    WalletTransactionType.CREDIT);
        }

        MarketListing listing = new MarketListing(
                UUID.randomUUID(),
                sellerId,
                itemKey,
                itemName,
                kind,
                quantity,
                unitPriceMinor,
                now,
                now.plus(duration),
                MarketListingStatus.ACTIVE);
        listings.put(listing.listingId(), listing);
        persistUnchecked();
        return listing;
    }

    public Optional<MarketListing> cancelListing(UUID listingId, UUID requesterId, boolean staffOverride) {
        Objects.requireNonNull(listingId, "listingId");
        Objects.requireNonNull(requesterId, "requesterId");

        lock.lock();
        try {
            MarketListing listing = listings.get(listingId);
            if (listing == null) {
                return Optional.empty();
            }
            if (!staffOverride && !listing.sellerId().equals(requesterId)) {
                throw new IllegalStateException("Only the seller or staff can cancel this listing");
            }
            if (listing.status() != MarketListingStatus.ACTIVE
                    && listing.status() != MarketListingStatus.PARTIALLY_FILLED) {
                return Optional.of(listing);
            }
            MarketListing cancelled = listing.withQuantityAndStatus(listing.quantity(), MarketListingStatus.CANCELLED);
            listings.put(listingId, cancelled);
            persistUnchecked();
            return Optional.of(cancelled);
        } finally {
            lock.unlock();
        }
    }

    public Optional<MarketListing> findById(UUID listingId) {
        return Optional.ofNullable(listings.get(Objects.requireNonNull(listingId, "listingId")));
    }

    public List<MarketListing> activeListings(int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
        expireOldListings();
        Instant now = clock.instant();
        List<MarketListing> active = new ArrayList<>();
        for (MarketListing listing : listings.values()) {
            if ((listing.status() == MarketListingStatus.ACTIVE
                    || listing.status() == MarketListingStatus.PARTIALLY_FILLED)
                    && listing.expiresAt().isAfter(now)) {
                active.add(listing);
            }
        }
        active.sort(Comparator.comparing(MarketListing::createdAt).reversed());
        return active.size() <= limit ? List.copyOf(active) : List.copyOf(active.subList(0, limit));
    }

    public MarketListing buy(UUID buyerId, UUID listingId, int quantity) {
        Objects.requireNonNull(buyerId, "buyerId");
        Objects.requireNonNull(listingId, "listingId");
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be positive");
        }

        lock.lock();
        try {
            MarketListing listing = listings.get(listingId);
            if (listing == null) {
                throw new IllegalArgumentException("Listing not found");
            }
            if (listing.sellerId().equals(buyerId)) {
                throw new IllegalStateException("Sellers cannot buy their own listings");
            }
            if (listing.expiresAt().isBefore(clock.instant())) {
                MarketListing expired = listing.withQuantityAndStatus(
                        listing.quantity(),
                        MarketListingStatus.EXPIRED);
                listings.put(listing.listingId(), expired);
                persistUnchecked();
                throw new IllegalStateException("Listing has expired");
            }
            if (listing.status() != MarketListingStatus.ACTIVE
                    && listing.status() != MarketListingStatus.PARTIALLY_FILLED) {
                throw new IllegalStateException("Listing is no longer available");
            }
            if (quantity > listing.quantity()) {
                throw new IllegalStateException("Requested quantity exceeds available stock");
            }

            long gross = Math.multiplyExact(listing.unitPriceMinor(), quantity);
            long tax = (gross * salesTaxBasisPoints) / 10_000L;
            long sellerNet = gross - tax;

            wallets.transfer(
                    buyerId,
                    listing.sellerId(),
                    sellerNet,
                    "market-sale",
                    Map.of(
                            "listingId", listing.listingId().toString(),
                            "itemKey", listing.itemKey(),
                            "quantity", String.valueOf(quantity)),
                    WalletTransactionType.TRANSFER_OUT,
                    WalletTransactionType.MARKET_SALE);
            if (tax > 0) {
                wallets.transfer(
                        buyerId,
                        TREASURY_ACCOUNT_ID,
                        tax,
                        "market-tax",
                        Map.of("listingId", listing.listingId().toString()),
                        WalletTransactionType.MARKET_TAX,
                        WalletTransactionType.CREDIT);
            }

            int remaining = listing.quantity() - quantity;
            MarketListingStatus status = remaining == 0
                    ? MarketListingStatus.SOLD
                    : MarketListingStatus.PARTIALLY_FILLED;
            MarketListing updated = listing.withQuantityAndStatus(remaining, status);
            listings.put(listingId, updated);
            persistUnchecked();
            return updated;
        } finally {
            lock.unlock();
        }
    }

    private void expireOldListings() {
        boolean changed = false;
        Instant now = clock.instant();
        lock.lock();
        try {
            for (Map.Entry<UUID, MarketListing> entry : listings.entrySet()) {
                MarketListing listing = entry.getValue();
                if ((listing.status() == MarketListingStatus.ACTIVE
                        || listing.status() == MarketListingStatus.PARTIALLY_FILLED)
                        && !listing.expiresAt().isAfter(now)) {
                    entry.setValue(listing.withQuantityAndStatus(
                            listing.quantity(),
                            MarketListingStatus.EXPIRED));
                    changed = true;
                }
            }
            if (changed) {
                persistUnchecked();
            }
        } finally {
            lock.unlock();
        }
    }

    private void load() throws IOException {
        if (persistenceFile == null || !Files.exists(persistenceFile)) {
            if (persistenceFile != null && persistenceFile.getParent() != null) {
                Files.createDirectories(persistenceFile.getParent());
            }
            return;
        }
        try (Reader reader = Files.newBufferedReader(persistenceFile, StandardCharsets.UTF_8)) {
            List<MarketListing> loaded = gson.fromJson(reader, LISTING_LIST_TYPE);
            if (loaded != null) {
                for (MarketListing listing : loaded) {
                    listings.put(listing.listingId(), listing);
                }
            }
        } catch (RuntimeException exception) {
            throw new IOException("Unable to parse player market file: " + persistenceFile, exception);
        }
    }

    private void persistUnchecked() {
        if (persistenceFile == null) {
            return;
        }
        try {
            persist();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to persist player market listings", exception);
        }
    }

    private void persist() throws IOException {
        Path parent = persistenceFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = Files.createTempFile(parent, persistenceFile.getFileName().toString(), ".tmp");
        try {
            if (Files.exists(persistenceFile)) {
                Files.copy(persistenceFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
            }
            List<MarketListing> snapshot = listings.values().stream()
                    .sorted(Comparator.comparing(MarketListing::createdAt))
                    .toList();
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                gson.toJson(snapshot, LISTING_LIST_TYPE, writer);
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
}
