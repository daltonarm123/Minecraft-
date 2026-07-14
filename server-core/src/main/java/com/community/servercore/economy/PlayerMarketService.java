package com.community.servercore.economy;

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
    public static final UUID TREASURY_ACCOUNT_ID = UUID.nameUUIDFromBytes("servercore-treasury".getBytes());

    private final WalletService wallets;
    private final Clock clock;
    private final long listingFeeMinor;
    private final int salesTaxBasisPoints;
    private final Map<UUID, MarketListing> listings = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    public PlayerMarketService(
            WalletService wallets,
            long listingFeeMinor,
            int salesTaxBasisPoints,
            Clock clock) {
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
                throw new IllegalStateException("Listing has expired");
            }
            if (listing.status() != MarketListingStatus.ACTIVE
                    && listing.status() != MarketListingStatus.PARTIALLY_FILLED) {
                throw new IllegalStateException("Listing is no longer available");
            }
            if (quantity > listing.quantity()) {
                throw new IllegalStateException("Requested quantity exceeds available stock");
            }

            long gross = listing.unitPriceMinor() * quantity;
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
            return updated;
        } finally {
            lock.unlock();
        }
    }
}
