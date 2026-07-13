package com.community.servercore.cosmetic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class JsonCosmeticRepository implements CosmeticRepository {
    private final Path file;
    private final Path backup;
    private final Gson gson;
    private final Map<String, CosmeticDefinition> definitions = new LinkedHashMap<>();
    private final Map<UUID, CosmeticPlayerState> playerStates = new LinkedHashMap<>();

    public JsonCosmeticRepository(Path file) throws IOException {
        this.file = file.toAbsolutePath().normalize();
        this.backup = this.file.resolveSibling(this.file.getFileName() + ".bak");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        load();
    }

    @Override
    public synchronized Optional<CosmeticDefinition> findDefinition(String cosmeticId) {
        return Optional.ofNullable(definitions.get(normalizeId(cosmeticId)));
    }

    @Override
    public synchronized List<CosmeticDefinition> listDefinitions() {
        return definitions.values().stream()
                .sorted(Comparator.comparing(CosmeticDefinition::id))
                .toList();
    }

    @Override
    public synchronized void saveDefinition(CosmeticDefinition definition) {
        definitions.put(definition.id(), definition);
        persistUnchecked();
    }

    @Override
    public synchronized CosmeticPlayerState loadPlayerState(UUID playerId) {
        return playerStates.getOrDefault(playerId, CosmeticPlayerState.empty(playerId));
    }

    @Override
    public synchronized void savePlayerState(CosmeticPlayerState state) {
        playerStates.put(state.playerId(), state);
        persistUnchecked();
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
            CosmeticSnapshot snapshot = gson.fromJson(reader, CosmeticSnapshot.class);
            if (snapshot == null) {
                throw new IOException("Cosmetics file is empty: " + file);
            }
            for (CosmeticDefinition definition : snapshot.definitions()) {
                definitions.put(definition.id(), definition);
            }
            for (CosmeticPlayerState state : snapshot.playerStates()) {
                playerStates.put(state.playerId(), state);
            }
        } catch (RuntimeException exception) {
            throw new IOException("Unable to parse cosmetics file: " + file, exception);
        }
    }

    private void persistUnchecked() {
        try {
            persist();
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to save cosmetics data: " + file, exception);
        }
    }

    private void persist() throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
        CosmeticSnapshot snapshot = new CosmeticSnapshot(
                new ArrayList<>(definitions.values()),
                new ArrayList<>(playerStates.values()));

        try {
            if (Files.exists(file)) {
                Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                gson.toJson(snapshot, CosmeticSnapshot.class, writer);
            }
            try {
                Files.move(
                        temporary,
                        file,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String normalizeId(String cosmeticId) {
        if (cosmeticId == null || cosmeticId.isBlank()) {
            throw new IllegalArgumentException("cosmeticId must not be blank");
        }
        return cosmeticId.trim().toLowerCase(Locale.ROOT);
    }

    private record CosmeticSnapshot(
            List<CosmeticDefinition> definitions,
            List<CosmeticPlayerState> playerStates) {
        private CosmeticSnapshot {
            definitions = definitions == null ? List.of() : List.copyOf(definitions);
            playerStates = playerStates == null ? List.of() : List.copyOf(playerStates);
        }
    }
}
