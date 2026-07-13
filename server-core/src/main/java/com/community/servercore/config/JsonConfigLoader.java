package com.community.servercore.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

public final class JsonConfigLoader {
    private final Path file;
    private final Gson gson;

    public JsonConfigLoader(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public synchronized ServerCoreConfig loadOrCreate() throws IOException {
        if (!Files.exists(file)) {
            ServerCoreConfig defaults = ServerCoreConfig.defaults();
            save(defaults, false);
            return defaults;
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            ServerCoreConfig config = gson.fromJson(reader, ServerCoreConfig.class);
            if (config == null) {
                throw new IOException("Configuration file is empty: " + file);
            }
            return config;
        } catch (JsonParseException | IllegalArgumentException exception) {
            throw new IOException("Invalid ServerCore configuration: " + file, exception);
        }
    }

    public synchronized void save(ServerCoreConfig config) throws IOException {
        save(config, true);
    }

    private void save(ServerCoreConfig config, boolean createBackup) throws IOException {
        Objects.requireNonNull(config, "config");
        Path parent = file.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
        Path backup = file.resolveSibling(file.getFileName() + ".bak");

        try {
            if (createBackup && Files.exists(file)) {
                Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                gson.toJson(config, ServerCoreConfig.class, writer);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
