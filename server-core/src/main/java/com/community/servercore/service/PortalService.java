package com.community.servercore.service;

import com.community.servercore.portal.Portal;
import com.community.servercore.storage.PortalRepository;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PortalService {
    private final PortalRepository repository;
    private final PortalValidator validator;
    private final PortalCooldownService cooldownService;
    private final PortalAccessService accessService;
    private final PortalTeleportService teleportService;
    private final boolean allowOverlaps;
    private final Set<UUID> teleportingPlayers = ConcurrentHashMap.newKeySet();

    public PortalService(
            PortalRepository repository,
            PortalValidator validator,
            PortalCooldownService cooldownService,
            PortalAccessService accessService,
            PortalTeleportService teleportService,
            boolean allowOverlaps) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.cooldownService = Objects.requireNonNull(cooldownService, "cooldownService");
        this.accessService = Objects.requireNonNull(accessService, "accessService");
        this.teleportService = Objects.requireNonNull(teleportService, "teleportService");
        this.allowOverlaps = allowOverlaps;
    }

    public PortalMutationResult save(Portal portal) throws IOException {
        Objects.requireNonNull(portal, "portal");
        PortalValidator.ValidationResult validation =
                validator.validate(portal, repository.findAll(), allowOverlaps);
        if (!validation.valid()) {
            return PortalMutationResult.rejected(portal, validation.errors());
        }
        repository.save(portal);
        return PortalMutationResult.accepted(portal);
    }

    public boolean delete(UUID portalId) throws IOException {
        Objects.requireNonNull(portalId, "portalId");
        return repository.delete(portalId);
    }

    public boolean delete(String portalName) throws IOException {
        Optional<Portal> portal = findByName(portalName);
        return portal.isPresent() && repository.delete(portal.orElseThrow().id());
    }

    public Optional<Portal> setEnabled(String portalName, boolean enabled) throws IOException {
        Optional<Portal> existing = findByName(portalName);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        Portal portal = existing.orElseThrow();
        Portal updated = new Portal(
                portal.id(),
                portal.name(),
                portal.displayName(),
                portal.sourceWorld(),
                portal.region(),
                portal.destination(),
                enabled,
                portal.permission(),
                portal.cooldownSeconds(),
                portal.entryMessage(),
                portal.deniedMessage(),
                portal.metadata());

        PortalMutationResult result = save(updated);
        return result.successful() ? Optional.of(updated) : Optional.empty();
    }

    public List<Portal> list() {
        return repository.findAll();
    }

    public Optional<Portal> findById(UUID id) {
        Objects.requireNonNull(id, "id");
        return repository.findById(id);
    }

    public Optional<Portal> findByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return repository.findByName(name);
    }

    public Optional<Portal> findAt(String world, double x, double y, double z) {
        if (world == null || world.isBlank()) {
            return Optional.empty();
        }
        return repository.findAll().stream()
                .filter(portal -> portal.sourceWorld().equalsIgnoreCase(world.trim()))
                .filter(portal -> portal.region().contains(x, y, z))
                .findFirst();
    }

    public PortalUseResult tryUse(UUID playerId, String world, double x, double y, double z) {
        Objects.requireNonNull(playerId, "playerId");

        Optional<Portal> matchingPortal = findAt(world, x, y, z);
        if (matchingPortal.isEmpty()) {
            return PortalUseResult.noPortal();
        }

        Portal portal = matchingPortal.orElseThrow();
        if (!portal.enabled()) {
            return PortalUseResult.of(PortalUseStatus.DISABLED, portal, "This portal is disabled.");
        }
        if (!accessService.canUse(playerId, portal)) {
            return PortalUseResult.of(PortalUseStatus.ACCESS_DENIED, portal, portal.deniedMessage());
        }

        Duration remaining = cooldownService.remaining(playerId, portal.cooldownSeconds());
        if (!remaining.isZero()) {
            return PortalUseResult.cooldown(portal, remaining);
        }

        if (!teleportingPlayers.add(playerId)) {
            return PortalUseResult.of(
                    PortalUseStatus.TELEPORT_IN_PROGRESS,
                    portal,
                    "A teleport is already in progress.");
        }

        try {
            TeleportResult teleport = teleportService.teleport(playerId, portal.destination());
            if (!teleport.successful()) {
                return PortalUseResult.of(PortalUseStatus.TELEPORT_FAILED, portal, teleport.message());
            }
            cooldownService.recordUse(playerId);
            String successMessage = teleport.message().isBlank() ? portal.entryMessage() : teleport.message();
            return PortalUseResult.of(PortalUseStatus.SUCCESS, portal, successMessage);
        } finally {
            teleportingPlayers.remove(playerId);
        }
    }
}
