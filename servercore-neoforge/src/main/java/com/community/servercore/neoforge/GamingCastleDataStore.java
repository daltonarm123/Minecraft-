package com.community.servercore.neoforge;

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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persistent state for homes, daily rewards, welcome state, and moderation notes. */
final class GamingCastleDataStore {
    private static final Type STORE_TYPE = new TypeToken<Map<String, PlayerData>>() { }.getType();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final Path backup;
    private final Map<String, PlayerData> players = new LinkedHashMap<>();

    GamingCastleDataStore(Path file) throws IOException {
        this.file = file.toAbsolutePath().normalize();
        this.backup = this.file.resolveSibling(this.file.getFileName() + ".bak");
        load();
    }

    synchronized Optional<SavedLocation> home(UUID playerId) {
        return Optional.ofNullable(data(playerId).home());
    }

    synchronized void setHome(UUID playerId, SavedLocation home) {
        PlayerData current = data(playerId);
        put(playerId, current.withHome(home));
    }

    /** @return true only the first time this installation welcomes the player. */
    synchronized boolean markWelcomed(UUID playerId) {
        PlayerData current = data(playerId);
        if (current.welcomed()) {
            return false;
        }
        put(playerId, current.withWelcomed(true));
        return true;
    }

    synchronized DailyClaim claimDaily(UUID playerId, LocalDate today) {
        PlayerData current = data(playerId);
        LocalDate last = parseDate(current.lastDaily());
        if (today.equals(last)) {
            return new DailyClaim(false, current.dailyStreak());
        }
        int streak = last != null && today.minusDays(1).equals(last)
                ? current.dailyStreak() + 1
                : 1;
        put(playerId, current.withDaily(today.toString(), streak));
        return new DailyClaim(true, streak);
    }

    synchronized int addWarning(UUID playerId, String note) {
        PlayerData current = data(playerId);
        List<String> notes = new ArrayList<>(current.notes());
        if (note != null && !note.isBlank()) {
            notes.add("WARN: " + note.trim());
        }
        PlayerData updated = current.withWarnings(current.warnings() + 1, notes);
        put(playerId, updated);
        return updated.warnings();
    }

    synchronized void addNote(UUID playerId, String note) {
        if (note == null || note.isBlank()) {
            return;
        }
        PlayerData current = data(playerId);
        List<String> notes = new ArrayList<>(current.notes());
        notes.add(note.trim());
        put(playerId, current.withNotes(notes));
    }

    synchronized List<String> notes(UUID playerId) {
        return List.copyOf(data(playerId).notes());
    }

    synchronized int warnings(UUID playerId) {
        return data(playerId).warnings();
    }

    synchronized void muteUntil(UUID playerId, long epochMillis) {
        put(playerId, data(playerId).withMutedUntil(Math.max(0L, epochMillis)));
    }

    synchronized void unmute(UUID playerId) {
        put(playerId, data(playerId).withMutedUntil(0L));
    }

    synchronized long mutedUntil(UUID playerId) {
        return data(playerId).mutedUntilEpochMillis();
    }

    private PlayerData data(UUID playerId) {
        return players.getOrDefault(playerId.toString(), PlayerData.empty());
    }

    private void put(UUID playerId, PlayerData data) {
        players.put(playerId.toString(), data);
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
            Map<String, PlayerData> loaded = GSON.fromJson(reader, STORE_TYPE);
            if (loaded != null) {
                players.putAll(loaded);
            }
        } catch (RuntimeException exception) {
            throw new IOException("Unable to parse Gaming Castle player data: " + file, exception);
        }
    }

    private void persistUnchecked() {
        try {
            persist();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to persist Gaming Castle player data", exception);
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
                GSON.toJson(players, STORE_TYPE, writer);
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

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    record SavedLocation(
            String dimension,
            double x,
            double y,
            double z,
            float yaw,
            float pitch) { }

    record DailyClaim(boolean claimed, int streak) { }

    record PlayerData(
            SavedLocation home,
            String lastDaily,
            int dailyStreak,
            boolean welcomed,
            int warnings,
            List<String> notes,
            long mutedUntilEpochMillis) {

        PlayerData {
            lastDaily = lastDaily == null ? "" : lastDaily;
            notes = notes == null ? List.of() : List.copyOf(notes);
            dailyStreak = Math.max(0, dailyStreak);
            warnings = Math.max(0, warnings);
            mutedUntilEpochMillis = Math.max(0L, mutedUntilEpochMillis);
        }

        static PlayerData empty() {
            return new PlayerData(null, "", 0, false, 0, List.of(), 0L);
        }

        PlayerData withHome(SavedLocation value) {
            return new PlayerData(value, lastDaily, dailyStreak, welcomed, warnings, notes, mutedUntilEpochMillis);
        }

        PlayerData withWelcomed(boolean value) {
            return new PlayerData(home, lastDaily, dailyStreak, value, warnings, notes, mutedUntilEpochMillis);
        }

        PlayerData withDaily(String date, int streak) {
            return new PlayerData(home, date, streak, welcomed, warnings, notes, mutedUntilEpochMillis);
        }

        PlayerData withWarnings(int value, List<String> updatedNotes) {
            return new PlayerData(home, lastDaily, dailyStreak, welcomed, value, updatedNotes, mutedUntilEpochMillis);
        }

        PlayerData withNotes(List<String> value) {
            return new PlayerData(home, lastDaily, dailyStreak, welcomed, warnings, value, mutedUntilEpochMillis);
        }

        PlayerData withMutedUntil(long value) {
            return new PlayerData(home, lastDaily, dailyStreak, welcomed, warnings, notes, value);
        }
    }
}
