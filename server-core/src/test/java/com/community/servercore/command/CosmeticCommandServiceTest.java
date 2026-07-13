package com.community.servercore.command;

import com.community.servercore.audit.AuditEventType;
import com.community.servercore.audit.InMemoryAuditSink;
import com.community.servercore.cosmetic.CosmeticCategory;
import com.community.servercore.cosmetic.CosmeticsService;
import com.community.servercore.cosmetic.DefaultCosmeticCatalog;
import com.community.servercore.cosmetic.InMemoryCosmeticRepository;
import com.community.servercore.selection.WorldPosition;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CosmeticCommandServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-13T04:00:00Z");

    @Test
    void grantsEquipsDisablesAndAuditsCosmetic() {
        CosmeticsService cosmetics = new CosmeticsService(new InMemoryCosmeticRepository());
        DefaultCosmeticCatalog.seed(cosmetics);
        InMemoryAuditSink audit = new InMemoryAuditSink();
        CosmeticCommandService commands = new CosmeticCommandService(
                cosmetics,
                audit,
                Clock.fixed(NOW, ZoneOffset.UTC));
        TestActor admin = new TestActor(true);
        TestActor player = new TestActor(false);

        assertThat(commands.grant(
                admin,
                player.id(),
                player.name(),
                "founder-crown").successful()).isTrue();
        assertThat(commands.equip(player, "founder-crown").successful()).isTrue();
        assertThat(cosmetics.state(player.id()).equippedByCategory())
                .containsEntry(CosmeticCategory.HEAD, "founder-crown");

        assertThat(commands.setEnabled(admin, "founder-crown", false).successful()).isTrue();
        assertThat(cosmetics.state(player.id()).equippedByCategory()).isEmpty();
        assertThat(commands.equip(player, "founder-crown").successful()).isFalse();

        assertThat(audit.recent(10))
                .extracting(event -> event.type())
                .contains(
                        AuditEventType.COSMETIC_GRANTED,
                        AuditEventType.COSMETIC_EQUIPPED,
                        AuditEventType.COSMETIC_DISABLED);
    }

    @Test
    void preventsNormalPlayerFromUsingStaffCommands() {
        CosmeticsService cosmetics = new CosmeticsService(new InMemoryCosmeticRepository());
        DefaultCosmeticCatalog.seed(cosmetics);
        CosmeticCommandService commands = new CosmeticCommandService(
                cosmetics,
                new InMemoryAuditSink(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        TestActor player = new TestActor(false);

        assertThat(commands.grant(
                player,
                player.id(),
                player.name(),
                "founder-crown").successful()).isFalse();
        assertThat(commands.catalog(player).successful()).isTrue();
    }

    private static final class TestActor implements CommandActor {
        private final UUID id = UUID.randomUUID();
        private final boolean administrator;

        private TestActor(boolean administrator) {
            this.administrator = administrator;
        }

        @Override
        public UUID id() {
            return id;
        }

        @Override
        public String name() {
            return administrator ? "Administrator" : "Player";
        }

        @Override
        public WorldPosition position() {
            return new WorldPosition("minecraft:overworld", 0, 64, 0);
        }

        @Override
        public boolean hasPermission(String permission) {
            return administrator || CosmeticCommandService.USE_PERMISSION.equals(permission);
        }
    }
}
