package com.community.servercore.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonConfigLoaderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsAndReloadsDefaultConfiguration() throws Exception {
        Path file = temporaryDirectory.resolve("servercore.json");
        JsonConfigLoader loader = new JsonConfigLoader(file);

        ServerCoreConfig first = loader.loadOrCreate();
        ServerCoreConfig second = loader.loadOrCreate();

        assertThat(first).isEqualTo(ServerCoreConfig.defaults());
        assertThat(second).isEqualTo(first);
        assertThat(file).exists();
    }

    @Test
    void rejectsInvalidConfigurationValues() throws Exception {
        Path file = temporaryDirectory.resolve("servercore.json");
        Files.writeString(file, """
                {
                  "debugMode": false,
                  "portalCheckIntervalTicks": 0,
                  "portalFile": "portals.json",
                  "createBackups": true,
                  "maximumPortals": 100,
                  "allowOverlappingPortals": false,
                  "defaultCooldownSeconds": 3,
                  "logPortalUsage": true,
                  "apiBaseUrl": "",
                  "apiTimeoutSeconds": 10
                }
                """, StandardCharsets.UTF_8);

        JsonConfigLoader loader = new JsonConfigLoader(file);

        assertThatThrownBy(loader::loadOrCreate)
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Invalid ServerCore configuration");
    }

    @Test
    void createsBackupWhenSavingExistingConfiguration() throws Exception {
        Path file = temporaryDirectory.resolve("servercore.json");
        JsonConfigLoader loader = new JsonConfigLoader(file);
        loader.loadOrCreate();

        ServerCoreConfig changed = new ServerCoreConfig(
                true, 10, "custom-portals.json", true, 250, false, 5, true, "", 15);
        loader.save(changed);

        assertThat(loader.loadOrCreate()).isEqualTo(changed);
        assertThat(file.resolveSibling("servercore.json.bak")).exists();
    }
}
