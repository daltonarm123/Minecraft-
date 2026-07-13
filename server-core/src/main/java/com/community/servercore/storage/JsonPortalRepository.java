package com.community.servercore.storage;

import com.community.servercore.portal.Portal;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class JsonPortalRepository implements PortalRepository {
    private static final Type PORTAL_LIST_TYPE = new TypeToken<List<Portal>>() { }.getType();

    private final Path file;
    private final Path backup;
    private final Gson gson;
    private final Map<UUID, Portal> portals = new LinkedHashMap<>();

    public JsonPortalRepository(Path file) throws IOException {
        this.file = file.toAbsolutePath().normalize();
        this.backup = this.file.resolveSibling(this.file.getFileName() + ".bak");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        load();
    }

    @Override
    public synchronized List<Portal> findAll() {
        return portals.values().stream()
                .sorted(Comparator.comparing(Portal::name))
                .toList();
    }

    @Override
    public synchronized Optional<Portal> findById(UUID id) {
        return Optional.ofNullable(portals.get(id));
    }

    @Override
    public synchronized Optional<Portal> findByName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return portals.values().stream()
                .filter(portal -> portal.name().equalsIgnoreCase(name.trim()))
                .findFirst();
    }

    @Override
    public synchronized void save(Portal portal) throws IOException {
        portals.put(portal.id(), portal);
        persist();
    }

    @Override
    public synchronized boolean delete(UUID id) throws IOException {
        Portal removed = portals.remove(id);
        if (removed != null) {
            persist();
            return true;
        }
        return false;
    }

    private void load() throws IOException {
        if (!Files.exists(file)) {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<Portal> loaded = gson.fromJson(reader, PORTAL_LIST_TYPE);
            if (loaded != null) {
                for (Portal portal : loaded) {
                    portals.put(portal.id(), portal);
                }
            }
        } catch (RuntimeException exception) {
            throw new IOException("Unable to parse portal file: " + file, exception);
        }
    }

    private void persist() throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
        try {
            if (Files.exists(file)) {
                Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                gson.toJson(new ArrayList<>(portals.values()), PORTAL_LIST_TYPE, writer);
            }
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
