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

class StaffAreaCommandServiceTest {
    @Test
    void createsPermissionLockedStaffAreaPortals() {
        PortalService portalService = new PortalService(
                new InMemoryPortalRepository(),
                new PortalValidator(),
                new PortalCooldownService(),
                (playerId, portal) -> true,
                (playerId, destination) -> TeleportResult.success("ok"),
                false);
        PortalCommandService portalCommands = new PortalCommandService(
                portalService,
                new PortalSelectionService(),
                20,
                3);
        StaffAreaCommandService staffAreas = new StaffAreaCommandService(portalCommands);
        TestActor actor = new TestActor();

        assertThat(portalCommands.begin(actor, StaffAreaCommandService.DEV_PORTAL_NAME).successful()).isTrue();
        assertThat(portalCommands.setFirst(actor, StaffAreaCommandService.DEV_PORTAL_NAME).successful()).isTrue();
        actor.position = new WorldPosition("minecraft:overworld", 3, 67, 3);
        assertThat(portalCommands.setSecond(actor, StaffAreaCommandService.DEV_PORTAL_NAME).successful()).isTrue();

        assertThat(staffAreas.createDevAreaPortal(
                actor,
                "Dev",
                PortalDestination.location("minecraft:overworld", 100, 70, 100, 0, 0)).successful())
                .isTrue();

        assertThat(portalService.findByName(StaffAreaCommandService.DEV_PORTAL_NAME))
                .get()
                .extracting(portal -> portal.permission())
                .isEqualTo(RoleCommandService.DEV_AREA_PERMISSION);
    }

    private static final class TestActor implements CommandActor {
        private final UUID id = UUID.randomUUID();
        private WorldPosition position = new WorldPosition("minecraft:overworld", 0, 64, 0);

        @Override
        public UUID id() {
            return id;
        }

        @Override
        public String name() {
            return "Admin";
        }

        @Override
        public WorldPosition position() {
            return position;
        }

        @Override
        public boolean hasPermission(String permission) {
            return true;
        }
    }
}
