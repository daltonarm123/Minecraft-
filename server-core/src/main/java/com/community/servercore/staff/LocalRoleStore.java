package com.community.servercore.staff;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LocalRoleStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type FILE_TYPE = new TypeToken<Map<String, Set<String>>>() {}.getType();

    private final Path file;
    private final Map<UUID, Set<StaffRole>> grants = new ConcurrentHashMap<>();

    public LocalRoleStore(Path file) throws IOException {
        this.file = file;
        load();
    }

    public boolean has(UUID playerId, String permission) {
        Set<StaffRole> roles = grants.get(playerId);
        if (roles == null) return false;
        for (StaffRole role : roles) {
            if (role.permission().equals(permission)) return true;
        }
        return false;
    }

    public Set<StaffRole> rolesFor(UUID playerId) {
        Set<StaffRole> roles = grants.get(playerId);
        return roles == null ? Set.of() : Set.copyOf(roles);
    }

    public void grant(UUID playerId, StaffRole role) throws IOException {
        grants.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet()).add(role);
        save();
    }

    public void revoke(UUID playerId, StaffRole role) throws IOException {
        Set<StaffRole> roles = grants.get(playerId);
        if (roles != null) {
            roles.remove(role);
            if (roles.isEmpty()) grants.remove(playerId);
        }
        save();
    }

    private void load() throws IOException {
        if (!Files.exists(file)) return;
        Map<String, Set<String>> raw = GSON.fromJson(Files.readString(file), FILE_TYPE);
        if (raw == null) return;
        for (Map.Entry<String, Set<String>> entry : raw.entrySet()) {
            UUID id;
            try { id = UUID.fromString(entry.getKey()); } catch (IllegalArgumentException e) { continue; }
            Set<StaffRole> roles = ConcurrentHashMap.newKeySet();
            for (String name : entry.getValue()) {
                try { roles.add(StaffRole.valueOf(name)); } catch (IllegalArgumentException ignored) {}
            }
            if (!roles.isEmpty()) grants.put(id, roles);
        }
    }

    private synchronized void save() throws IOException {
        Map<String, Set<String>> raw = new HashMap<>();
        for (Map.Entry<UUID, Set<StaffRole>> entry : grants.entrySet()) {
            Set<String> names = new HashSet<>();
            for (StaffRole role : entry.getValue()) names.add(role.name());
            raw.put(entry.getKey().toString(), names);
        }
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(raw));
    }
}
