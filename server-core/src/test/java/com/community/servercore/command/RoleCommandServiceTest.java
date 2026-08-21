package com.community.servercore.command;

import com.community.servercore.selection.WorldPosition;
import com.community.servercore.staff.LocalRoleStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RoleCommandServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesAreaAccessFromRolePermissions() throws IOException {
        RoleCommandService service = new RoleCommandService(
                new LocalRoleStore(tempDir.resolve("roles.json")));
        TestActor developer = new TestActor(Set.of("servercore.role.dev"));
        TestActor support = new TestActor(Set.of("servercore.role.support"));

        assertThat(RoleCommandService.canAccessDevArea(developer)).isTrue();
        assertThat(RoleCommandService.canAccessAdminLounge(developer)).isFalse();
        assertThat(RoleCommandService.canAccessAdminLounge(support)).isTrue();
        assertThat(service.myRoles(developer).successful()).isTrue();
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
            return "staff";
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
