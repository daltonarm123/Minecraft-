package com.community.servercore.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class ServerCoreBootstrapSupport {
    private ServerCoreBootstrapSupport() {
    }

    public static ServerCoreConfig ensureConfig(Path dataDirectory) throws IOException {
        Objects.requireNonNull(dataDirectory, "dataDirectory");
        Files.createDirectories(dataDirectory);
        JsonConfigLoader loader = new JsonConfigLoader(dataDirectory.resolve("servercore.json"));
        return loader.loadOrCreate();
    }

    public static List<String> initialSetupNotes(ServerCoreConfig config) {
        Objects.requireNonNull(config, "config");
        return List.of(
                "ServerCore config initialized.",
                "Use /servercore setup to create default staff-area portals.",
                "Review config/servercore/servercore.json before enabling public features.");
    }
}
