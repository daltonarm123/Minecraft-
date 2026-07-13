package com.community.servercore.command;

import com.community.servercore.audit.AuditEvent;
import com.community.servercore.audit.AuditEventType;
import com.community.servercore.audit.AuditSink;
import com.community.servercore.cosmetic.CosmeticCategory;
import com.community.servercore.cosmetic.CosmeticDefinition;
import com.community.servercore.cosmetic.CosmeticPlayerState;
import com.community.servercore.cosmetic.CosmeticsService;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class CosmeticCommandService {
    public static final String USE_PERMISSION = "servercore.cosmetics.use";
    public static final String ADMIN_PERMISSION = "servercore.cosmetics.admin";

    private final CosmeticsService cosmetics;
    private final AuditSink audit;
    private final Clock clock;

    public CosmeticCommandService(CosmeticsService cosmetics, AuditSink audit) {
        this(cosmetics, audit, Clock.systemUTC());
    }

    CosmeticCommandService(CosmeticsService cosmetics, AuditSink audit, Clock clock) {
        this.cosmetics = Objects.requireNonNull(cosmetics, "cosmetics");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CommandResult catalog(CommandActor actor) {
        if (!canUse(actor)) {
            return deniedUse();
        }
        List<CosmeticDefinition> definitions = cosmetics.listDefinitions(false);
        if (definitions.isEmpty()) {
            return CommandResult.success("No cosmetics are available yet.");
        }
        List<String> messages = new ArrayList<>();
        messages.add("Available cosmetics (" + definitions.size() + "):");
        definitions.forEach(definition -> messages.add(formatDefinition(definition)));
        return CommandResult.success(messages);
    }

    public CommandResult owned(CommandActor actor) {
        if (!canUse(actor)) {
            return deniedUse();
        }
        CosmeticPlayerState state = cosmetics.state(actor.id());
        if (state.ownedCosmeticIds().isEmpty()) {
            return CommandResult.success("You do not own any cosmetics yet.");
        }
        return CommandResult.success(List.of(
                "Owned cosmetics (" + state.ownedCosmeticIds().size() + "):",
                String.join(", ", state.ownedCosmeticIds().stream().sorted().toList())));
    }

    public CommandResult equipped(CommandActor actor) {
        if (!canUse(actor)) {
            return deniedUse();
        }
        CosmeticPlayerState state = cosmetics.state(actor.id());
        if (state.equippedByCategory().isEmpty()) {
            return CommandResult.success("You have no cosmetics equipped.");
        }
        List<String> messages = new ArrayList<>();
        messages.add("Equipped cosmetics:");
        state.equippedByCategory().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> messages.add("- " + entry.getKey() + ": " + entry.getValue()));
        return CommandResult.success(messages);
    }

    public CommandResult equip(CommandActor actor, String cosmeticId) {
        if (!canUse(actor)) {
            return deniedUse();
        }
        try {
            CosmeticPlayerState state = cosmetics.equip(actor.id(), cosmeticId);
            String normalizedId = state.equippedByCategory().values().stream()
                    .filter(id -> id.equalsIgnoreCase(cosmeticId))
                    .findFirst()
                    .orElse(cosmeticId);
            publish(
                    AuditEventType.COSMETIC_EQUIPPED,
                    actor,
                    "Equipped cosmetic " + normalizedId,
                    Map.of("cosmeticId", normalizedId));
            return CommandResult.success("Equipped cosmetic '" + normalizedId + "'.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return CommandResult.failure(exception.getMessage());
        }
    }

    public CommandResult unequip(CommandActor actor, CosmeticCategory category) {
        if (!canUse(actor)) {
            return deniedUse();
        }
        Objects.requireNonNull(category, "category");
        CosmeticPlayerState before = cosmetics.state(actor.id());
        String removed = before.equippedByCategory().get(category);
        cosmetics.unequip(actor.id(), category);
        if (removed == null) {
            return CommandResult.success("Nothing is equipped in the " + category + " category.");
        }
        publish(
                AuditEventType.COSMETIC_UNEQUIPPED,
                actor,
                "Unequipped cosmetic " + removed,
                Map.of("cosmeticId", removed, "category", category.name()));
        return CommandResult.success("Unequipped cosmetic '" + removed + "'.");
    }

    public CommandResult grant(
            CommandActor actor,
            UUID targetPlayerId,
            String targetPlayerName,
            String cosmeticId) {
        if (!canAdmin(actor)) {
            return deniedAdmin();
        }
        try {
            boolean granted = cosmetics.grant(targetPlayerId, cosmeticId);
            if (!granted) {
                return CommandResult.success(targetPlayerName + " already owns '" + cosmeticId + "'.");
            }
            publish(
                    AuditEventType.COSMETIC_GRANTED,
                    actor,
                    "Granted " + cosmeticId + " to " + targetPlayerName,
                    Map.of(
                            "cosmeticId", cosmeticId,
                            "targetPlayerId", targetPlayerId.toString(),
                            "targetPlayerName", targetPlayerName));
            return CommandResult.success("Granted '" + cosmeticId + "' to " + targetPlayerName + ".");
        } catch (IllegalArgumentException exception) {
            return CommandResult.failure(exception.getMessage());
        }
    }

    public CommandResult revoke(
            CommandActor actor,
            UUID targetPlayerId,
            String targetPlayerName,
            String cosmeticId) {
        if (!canAdmin(actor)) {
            return deniedAdmin();
        }
        boolean revoked = cosmetics.revoke(targetPlayerId, cosmeticId);
        if (!revoked) {
            return CommandResult.success(targetPlayerName + " does not own '" + cosmeticId + "'.");
        }
        publish(
                AuditEventType.COSMETIC_REVOKED,
                actor,
                "Revoked " + cosmeticId + " from " + targetPlayerName,
                Map.of(
                        "cosmeticId", cosmeticId,
                        "targetPlayerId", targetPlayerId.toString(),
                        "targetPlayerName", targetPlayerName));
        return CommandResult.success("Revoked '" + cosmeticId + "' from " + targetPlayerName + ".");
    }

    public CommandResult setEnabled(CommandActor actor, String cosmeticId, boolean enabled) {
        if (!canAdmin(actor)) {
            return deniedAdmin();
        }
        try {
            CosmeticDefinition definition = cosmetics.setEnabled(cosmeticId, enabled);
            AuditEventType type = enabled
                    ? AuditEventType.COSMETIC_ENABLED
                    : AuditEventType.COSMETIC_DISABLED;
            publish(
                    type,
                    actor,
                    (enabled ? "Enabled " : "Disabled ") + definition.id(),
                    Map.of("cosmeticId", definition.id()));
            return CommandResult.success(
                    (enabled ? "Enabled" : "Disabled") + " cosmetic '" + definition.id() + "'.");
        } catch (IllegalArgumentException exception) {
            return CommandResult.failure(exception.getMessage());
        }
    }

    public CommandResult inspect(
            CommandActor actor,
            UUID targetPlayerId,
            String targetPlayerName) {
        if (!canAdmin(actor)) {
            return deniedAdmin();
        }
        CosmeticPlayerState state = cosmetics.state(targetPlayerId);
        List<String> messages = new ArrayList<>();
        messages.add("Wardrobe for " + targetPlayerName + ":");
        messages.add("Owned: " + (state.ownedCosmeticIds().isEmpty()
                ? "none"
                : String.join(", ", state.ownedCosmeticIds().stream().sorted().toList())));
        if (state.equippedByCategory().isEmpty()) {
            messages.add("Equipped: none");
        } else {
            messages.add("Equipped:");
            state.equippedByCategory().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> messages.add("- " + entry.getKey() + ": " + entry.getValue()));
        }
        return CommandResult.success(messages);
    }

    public CommandResult definition(CommandActor actor, String cosmeticId) {
        if (!canUse(actor)) {
            return deniedUse();
        }
        return cosmetics.findDefinition(cosmeticId)
                .map(definition -> CommandResult.success(List.of(
                        "Cosmetic: " + definition.displayName(),
                        "ID: " + definition.id(),
                        "Category: " + definition.category(),
                        "Rarity: " + definition.rarity(),
                        "Unlock: " + definition.unlockSource(),
                        "Enabled: " + definition.enabled(),
                        "Description: " + definition.description())))
                .orElseGet(() -> CommandResult.failure("Cosmetic not found: " + cosmeticId));
    }

    private void publish(
            AuditEventType type,
            CommandActor actor,
            String message,
            Map<String, String> attributes) {
        audit.publish(AuditEvent.actor(
                type,
                clock.instant(),
                actor.id(),
                actor.name(),
                message,
                attributes));
    }

    private static String formatDefinition(CosmeticDefinition definition) {
        return "- " + definition.id()
                + " [" + definition.category() + ", " + definition.rarity() + "] "
                + definition.displayName();
    }

    private static boolean canUse(CommandActor actor) {
        return actor != null && (actor.hasPermission(USE_PERMISSION) || actor.hasPermission(ADMIN_PERMISSION));
    }

    private static boolean canAdmin(CommandActor actor) {
        return actor != null && actor.hasPermission(ADMIN_PERMISSION);
    }

    private static CommandResult deniedUse() {
        return CommandResult.failure("You do not have permission to use cosmetics.");
    }

    private static CommandResult deniedAdmin() {
        return CommandResult.failure("You do not have permission to manage cosmetics.");
    }
}
