package com.community.servercore.command;

import com.community.servercore.cosmetic.CosmeticsService;
import com.community.servercore.cosmetic.DefaultCosmeticCatalog;
import com.community.servercore.cosmetic.InMemoryCosmeticRepository;
import com.community.servercore.economy.MarketItemKind;
import com.community.servercore.economy.PlayerMarketService;
import com.community.servercore.economy.WalletService;
import com.community.servercore.economy.WalletTransactionType;
import com.community.servercore.selection.WorldPosition;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EconomyCommandServiceTest {
    @Test
    void supportsShopPurchaseAndMarketFlow() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC);
        WalletService wallets = new WalletService(clock);
        PlayerMarketService market = new PlayerMarketService(wallets, 25, 500, clock);
        CosmeticsService cosmetics = new CosmeticsService(new InMemoryCosmeticRepository());
        DefaultCosmeticCatalog.seed(cosmetics);
        EconomyCommandService commands = new EconomyCommandService(wallets, market, cosmetics);

        TestActor admin = new TestActor(Set.of(EconomyCommandService.ADMIN_PERMISSION));
        TestActor alice = new TestActor(Set.of(EconomyCommandService.USE_PERMISSION));
        TestActor bob = new TestActor(Set.of(EconomyCommandService.USE_PERMISSION));

        assertThat(commands.adminCredit(admin, alice.id(), "Alice", 20_000, "seed").successful()).isTrue();
        assertThat(commands.adminCredit(admin, bob.id(), "Bob", 20_000, "seed").successful()).isTrue();

        assertThat(commands.shopBuy(alice, "nomad-outfit").successful()).isTrue();
        assertThat(cosmetics.state(alice.id()).ownedCosmeticIds()).contains("nomad-outfit");

        assertThat(commands.marketSell(
                alice,
                "atm:steel_ingot",
                "Steel Ingot",
                MarketItemKind.MATERIAL_BUNDLE,
                5,
                400,
                Duration.ofHours(2)).successful()).isTrue();

        String listingLine = commands.marketList(bob, 5).messages().get(1);
        String listingIdText = listingLine.substring(2, listingLine.indexOf('|')).trim();
        UUID listingId = UUID.fromString(listingIdText);
        assertThat(commands.marketBuy(bob, listingId, 2).successful()).isTrue();
        assertThat(wallets.balance(alice.id())).isGreaterThan(0);
    }

    private static final class TestActor implements CommandActor {
        private final UUID id = UUID.randomUUID();
        private final Set<String> permissions;

        private TestActor(Set<String> permissions) {
            this.permissions = permissions;
        }

        @Override
        public UUID id() {
            return id;
        }

        @Override
        public String name() {
            return "player";
        }

        @Override
        public WorldPosition position() {
            return new WorldPosition("minecraft:overworld", 0, 64, 0);
        }

        @Override
        public boolean hasPermission(String permission) {
            return permissions.contains(permission);
        }
    }
}
