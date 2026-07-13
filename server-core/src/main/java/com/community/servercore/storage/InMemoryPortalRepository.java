package com.community.servercore.storage;

import com.community.servercore.portal.Portal;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryPortalRepository implements PortalRepository {
    private final Map<UUID, Portal> portals = new LinkedHashMap<>();

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
        String normalizedName = name.trim();
        return portals.values().stream()
                .filter(portal -> portal.name().equalsIgnoreCase(normalizedName))
                .findFirst();
    }

    @Override
    public synchronized void save(Portal portal) {
        portals.put(portal.id(), portal);
    }

    @Override
    public synchronized boolean delete(UUID id) {
        return portals.remove(id) != null;
    }
}
