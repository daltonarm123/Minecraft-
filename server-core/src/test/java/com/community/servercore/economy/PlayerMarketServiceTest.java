package com.community.servercore.economy;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerMarketServiceTest {
    @Test
    void createsListingAndSettlesPurchaseWithTax() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC);
        WalletService wallets = new WalletService(clock);
        PlayerMarketService market = new PlayerMarketService(wallets, 25, 500, clock);
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();

        wallets.credit(seller, 1_000, WalletTransactionType.CREDIT, "seed", null, Map.of());
        wallets.credit(buyer, 10_000, WalletTransactionType.CREDIT, "seed", null, Map.of());

        MarketListing listing = market.createListing(
                seller,
                "atm:steel_ingot",
                "Steel Ingot",
                MarketItemKind.MATERIAL_BUNDLE,
                10,
                200,
                Duration.ofHours(2));

        assertThat(wallets.balance(seller)).isEqualTo(975);

        MarketListing updated = market.buy(buyer, listing.listingId(), 3);

        assertThat(updated.status()).isEqualTo(MarketListingStatus.PARTIALLY_FILLED);
        assertThat(updated.quantity()).isEqualTo(7);
        assertThat(wallets.balance(seller)).isEqualTo(1_545);
        assertThat(wallets.balance(buyer)).isEqualTo(9_400);
        assertThat(wallets.balance(PlayerMarketService.TREASURY_ACCOUNT_ID)).isEqualTo(55);
    }
}
