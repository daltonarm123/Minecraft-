package com.community.servercore.command;

import com.community.servercore.portal.PortalDestination;
import com.community.servercore.selection.PortalSelectionService;
import com.community.servercore.selection.WorldPosition;
import com.community.servercore.service.PortalCooldownService;
import com.community.servercore.service.PortalService;
import com.community.servercore.service.PortalValidator;
import com.community.servercore.service.TeleportResult;
import com.community.servercore.storage.InMemoryPortalRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PortalCommandServiceTest {
    @Test
    void createsPortalFromPlayerSelection() {
        PortalService portalService = new PortalService(
                new InMemoryPortalRepository(),
                new PortalValidator(),
                new PortalCooldownService(),
                (playerId, portal) -> true,
                (playerId, destination) -> TeleportResult.success("ok"),
                false);
        PortalCommandService commands = new PortalCommandService(
                portalService,
                new PortalSelectionService(),
                100,
                3);
        TestActor actor = new TestActor();

        assertThat(commands.begin(actor, "survival").successful()).isTrue();
        actor.position = new WorldPosition("minecraft:overworld", 0, 64, 0);
        assertThat(commands.setFirst(actor, "survival").successful()).isTrue();
        actor.position = new WorldPosition("minecraft:overworld", 3, 67, 1);
        assertThat(commands.setSecond(actor, "survival").successful()).isTrue();
        CommandResult created = commands.create(
                actor,
                "survival",
                "Survival",
                PortalDestination.location("minecraft:overworld", 100, 70, 100, 0, 0),
                "");

        assertThat(created.successful()).isTrue();
        assertThat(portalService.findByName("survival")).isPresent();
    }

    @Test
    void deniesNonAdministrator() {
        PortalService portalService = new PortalService(
                new InMemoryPortalRepository(),
                new PortalValidator(),
                new PortalCooldownService(),
                (playerId, portal) -> true,
                (playerId, destination) -> TeleportResult.success("ok"),
                false);
        PortalCommandService commands = new PortalCommandService(
                portalService,
                new PortalSelectionService(),
                100,
                3);
        TestActor actor = new TestActor();
        actor.administrator = false;

        assertThat(commands.list(actor).successful()).isFalse();
    }

    private static final class TestActor implements CommandActor {
        private final UUID id = UUID.randomUUID();
        private WorldPosition position = new WorldPosition("minecraft:overworld", 0, 64, 0);
        private boolean administrator = true;

        @Override
        public UUID id() {
            return id;
        }

        @Override
        public String name() {
            return "Tester";
        }

        @Override
        public WorldPosition position() {
            return position;
        }

        @Override
        public boolean hasPermission(String permission) {
            return administrator;
        }
    }
}
