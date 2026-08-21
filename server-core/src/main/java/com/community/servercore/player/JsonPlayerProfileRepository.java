package com.community.servercore.player;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** JSON-backed player profile repository used by the production server runtime. */
public final class JsonPlayerProfileRepository implements PlayerProfileRepository {
    private static final Type PROFILE_LIST_TYPE = new TypeToken<List<PlayerProfile>>() { }.getType();

    private final Path file;
    private final Path backup;
    private final Gson gson;
    private final Map<UUID, PlayerProfile> profiles = new LinkedHashMap<>();

    public JsonPlayerProfileRepository(Path file) throws IOException {
        this.file = file.toAbsolutePath().normalize();
        this.backup = this.file.resolveSibling(this.file.getFileName() + ".bak");
        this.gson = new GsonBuilder()
                .registerTypeAdapter(Instant.class,
                        (JsonSerializer<Instant>) (src, type, context) -> context.serialize(src.toString()))
                .registerTypeAdapter(Instant.class,
                        (JsonDeserializer<Instant>) (json, type, context) -> Instant.parse(json.getAsString()))
                .setPrettyPrinting()
                .create();
        load();
    }

    @Override
    public synchronized Optional<PlayerProfile> findById(UUID playerId) {
        return Optional.ofNullable(profiles.get(playerId));
    }

    @Override
    public synchronized Optional<PlayerProfile> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        return profiles.values().stream()
                .filter(profile -> profile.username().toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst();
    }

    @Override
    public synchronized List<PlayerProfile> findAll() {
        return profiles.values().stream()
                .sorted(Comparator.comparingInt(PlayerProfile::rating).reversed()
                        .thenComparing(PlayerProfile::username, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    public synchronized void save(PlayerProfile profile) {
        profiles.put(profile.playerId(), profile);
        try {
            persist();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to persist player profiles", exception);
        }
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
            List<PlayerProfile> loaded = gson.fromJson(reader, PROFILE_LIST_TYPE);
            if (loaded != null) {
                for (PlayerProfile profile : loaded) {
                    profiles.put(profile.playerId(), profile);
                }
            }
        } catch (RuntimeException exception) {
            throw new IOException("Unable to parse player profile file: " + file, exception);
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
                gson.toJson(new ArrayList<>(profiles.values()), PROFILE_LIST_TYPE, writer);
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
