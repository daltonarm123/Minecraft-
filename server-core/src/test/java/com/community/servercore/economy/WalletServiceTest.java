package com.community.servercore.economy;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletServiceTest {
    @Test
    void creditsDebitsAndTransfers() {
        WalletService wallets = new WalletService(Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC));
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        wallets.credit(alice, 10_000, WalletTransactionType.CREDIT, "seed", null, Map.of());
        assertThat(wallets.balance(alice)).isEqualTo(10_000);

        wallets.transfer(
                alice,
                bob,
                1_500,
                "transfer",
                Map.of("note", "gift"),
                WalletTransactionType.TRANSFER_OUT,
                WalletTransactionType.TRANSFER_IN);

        assertThat(wallets.balance(alice)).isEqualTo(8_500);
        assertThat(wallets.balance(bob)).isEqualTo(1_500);

        wallets.debit(alice, 500, WalletTransactionType.DEBIT, "fee", null, Map.of());
        assertThat(wallets.balance(alice)).isEqualTo(8_000);
        assertThat(wallets.recentTransactions(alice, 10)).hasSize(3);
    }

    @Test
    void rejectsInsufficientFunds() {
        WalletService wallets = new WalletService();
        UUID account = UUID.randomUUID();

        assertThatThrownBy(() -> wallets.debit(account, 100, WalletTransactionType.DEBIT, "x", null, Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient funds");
    }
}
