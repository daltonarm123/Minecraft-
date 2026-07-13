package com.community.servercore;

import com.community.servercore.audit.AuditEvent;
import com.community.servercore.audit.AuditEventType;
import com.community.servercore.audit.AuditSink;
import com.community.servercore.audit.InMemoryAuditSink;
import com.community.servercore.command.CosmeticCommandService;
import com.community.servercore.command.PortalCommandService;
import com.community.servercore.config.JsonConfigLoader;
import com.community.servercore.config.ServerCoreConfig;
import com.community.servercore.cosmetic.CosmeticsService;
import com.community.servercore.cosmetic.DefaultCosmeticCatalog;
import com.community.servercore.cosmetic.JsonCosmeticRepository;
import com.community.servercore.duel.ArenaRegistry;
import com.community.servercore.duel.MatchmakingService;
import com.community.servercore.duel.RatingService;
import com.community.servercore.player.InMemoryPlayerProfileRepository;
import com.community.servercore.player.PlayerStatsService;
import com.community.servercore.selection.PortalSelectionService;
import com.community.servercore.service.PortalAccessService;
import com.community.servercore.service.PortalCooldownService;
import com.community.servercore.service.PortalService;
import com.community.servercore.service.PortalTeleportService;
import com.community.servercore.service.PortalValidator;
import com.community.servercore.storage.JsonPortalRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;

public final class ServerCoreRuntime {
    private final ServerCoreConfig config;
    private final PortalService portalService;
    private final PortalSelectionService portalSelectionService;
    private final PortalCommandService portalCommandService;
    private final PlayerStatsService playerStatsService;
    private final ArenaRegistry arenaRegistry;
    private final MatchmakingService matchmakingService;
    private final CosmeticsService cosmeticsService;
    private final CosmeticCommandService cosmeticCommandService;
    private final AuditSink auditSink;

    private ServerCoreRuntime(
            ServerCoreConfig config,
            PortalService portalService,
            PortalSelectionService portalSelectionService,
            PortalCommandService portalCommandService,
            PlayerStatsService playerStatsService,
            ArenaRegistry arenaRegistry,
            MatchmakingService matchmakingService,
            CosmeticsService cosmeticsService,
            CosmeticCommandService cosmeticCommandService,
            AuditSink auditSink) {
        this.config = Objects.requireNonNull(config, "config");
        this.portalService = Objects.requireNonNull(portalService, "portalService");
        this.portalSelectionService = Objects.requireNonNull(portalSelectionService, "portalSelectionService");
        this.portalCommandService = Objects.requireNonNull(portalCommandService, "portalCommandService");
        this.playerStatsService = Objects.requireNonNull(playerStatsService, "playerStatsService");
        this.arenaRegistry = Objects.requireNonNull(arenaRegistry, "arenaRegistry");
        this.matchmakingService = Objects.requireNonNull(matchmakingService, "matchmakingService");
        this.cosmeticsService = Objects.requireNonNull(cosmeticsService, "cosmeticsService");
        this.cosmeticCommandService = Objects.requireNonNull(cosmeticCommandService, "cosmeticCommandService");
        this.auditSink = Objects.requireNonNull(auditSink, "auditSink");
    }

    public static ServerCoreRuntime bootstrap(
            Path dataDirectory,
            PortalAccessService accessService,
            PortalTeleportService teleportService) throws IOException {
        return bootstrap(dataDirectory, accessService, teleportService, Clock.systemUTC());
    }

    static ServerCoreRuntime bootstrap(
            Path dataDirectory,
            PortalAccessService accessService,
            PortalTeleportService teleportService,
            Clock clock) throws IOException {
        Path normalizedDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory")
                .toAbsolutePath()
                .normalize();
        Files.createDirectories(normalizedDirectory);
        Objects.requireNonNull(accessService, "accessService");
        Objects.requireNonNull(teleportService, "teleportService");
        Objects.requireNonNull(clock, "clock");

        JsonConfigLoader configLoader = new JsonConfigLoader(normalizedDirectory.resolve("servercore.json"));
        ServerCoreConfig config = configLoader.loadOrCreate();
        JsonPortalRepository portalRepository = new JsonPortalRepository(
                normalizedDirectory.resolve(config.portalFile()));
        PortalService portalService = new PortalService(
                portalRepository,
                new PortalValidator(),
                new PortalCooldownService(clock),
                accessService,
                teleportService,
                config.allowOverlappingPortals());
        PortalSelectionService selectionService = new PortalSelectionService(clock);
        PortalCommandService portalCommandService = new PortalCommandService(
                portalService,
                selectionService,
                config.maximumPortals(),
                config.defaultCooldownSeconds());
        PlayerStatsService playerStatsService = new PlayerStatsService(
                new InMemoryPlayerProfileRepository(),
                clock);
        ArenaRegistry arenaRegistry = new ArenaRegistry();
        MatchmakingService matchmakingService = new MatchmakingService(
                arenaRegistry,
                playerStatsService,
                new RatingService(),
                clock,
                300);
        AuditSink auditSink = new InMemoryAuditSink();
        CosmeticsService cosmeticsService = new CosmeticsService(
                new JsonCosmeticRepository(normalizedDirectory.resolve("cosmetics.json")));
        int seededCosmetics = DefaultCosmeticCatalog.seed(cosmeticsService);
        CosmeticCommandService cosmeticCommandService = new CosmeticCommandService(
                cosmeticsService,
                auditSink);

        auditSink.publish(AuditEvent.system(
                AuditEventType.CONFIG_LOADED,
                clock.instant(),
                "Loaded ServerCore configuration from " + normalizedDirectory));
        if (seededCosmetics > 0) {
            auditSink.publish(AuditEvent.system(
                    AuditEventType.COSMETIC_REGISTERED,
                    clock.instant(),
                    "Seeded " + seededCosmetics + " default cosmetic definitions"));
        }

        return new ServerCoreRuntime(
                config,
                portalService,
                selectionService,
                portalCommandService,
                playerStatsService,
                arenaRegistry,
                matchmakingService,
                cosmeticsService,
                cosmeticCommandService,
                auditSink);
    }

    public ServerCoreConfig config() {
        return config;
    }

    public PortalService portals() {
        return portalService;
    }

    public PortalSelectionService portalSelections() {
        return portalSelectionService;
    }

    public PortalCommandService portalCommands() {
        return portalCommandService;
    }

    public PlayerStatsService playerStats() {
        return playerStatsService;
    }

    public ArenaRegistry arenas() {
        return arenaRegistry;
    }

    public MatchmakingService matchmaking() {
        return matchmakingService;
    }

    public CosmeticsService cosmetics() {
        return cosmeticsService;
    }

    public CosmeticCommandService cosmeticCommands() {
        return cosmeticCommandService;
    }

    public AuditSink audit() {
        return auditSink;
    }
}
