package com.community.servercore.command;

import com.community.servercore.portal.Portal;
import com.community.servercore.portal.PortalDestination;
import com.community.servercore.selection.PortalSelection;
import com.community.servercore.selection.PortalSelectionService;
import com.community.servercore.selection.WorldPosition;
import com.community.servercore.service.PortalMutationResult;
import com.community.servercore.service.PortalService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PortalCommandService {
    public static final String ADMIN_PERMISSION = "servercore.admin";

    private final PortalService portalService;
    private final PortalSelectionService selectionService;
    private final int maximumPortals;
    private final int defaultCooldownSeconds;

    public PortalCommandService(
            PortalService portalService,
            PortalSelectionService selectionService,
            int maximumPortals,
            int defaultCooldownSeconds) {
        this.portalService = Objects.requireNonNull(portalService, "portalService");
        this.selectionService = Objects.requireNonNull(selectionService, "selectionService");
        if (maximumPortals < 1) {
            throw new IllegalArgumentException("maximumPortals must be positive");
        }
        if (defaultCooldownSeconds < 0) {
            throw new IllegalArgumentException("defaultCooldownSeconds must not be negative");
        }
        this.maximumPortals = maximumPortals;
        this.defaultCooldownSeconds = defaultCooldownSeconds;
    }

    public CommandResult begin(CommandActor actor, String portalName) {
        if (!authorized(actor)) {
            return denied();
        }
        try {
            PortalSelection selection = selectionService.begin(actor.id(), portalName);
            return CommandResult.success("Started portal selection for '" + selection.portalName() + "'.");
        } catch (IllegalArgumentException exception) {
            return CommandResult.failure(exception.getMessage());
        }
    }

    public CommandResult setFirst(CommandActor actor, String portalName) {
        return setPoint(actor, portalName, true);
    }

    public CommandResult setSecond(CommandActor actor, String portalName) {
        return setPoint(actor, portalName, false);
    }

    public CommandResult create(
            CommandActor actor,
            String portalName,
            String displayName,
            PortalDestination destination,
            String permission) {
        if (!authorized(actor)) {
            return denied();
        }
        if (portalService.list().size() >= maximumPortals) {
            return CommandResult.failure("The configured portal limit has been reached.");
        }
        Optional<PortalSelection> selected = selectionService.get(actor.id());
        if (selected.isEmpty() || !selected.orElseThrow().portalName().equalsIgnoreCase(portalName)) {
            return CommandResult.failure("Select both corners for this portal before creating it.");
        }
        PortalSelection selection = selected.orElseThrow();
        if (!selection.complete()) {
            return CommandResult.failure("Both portal positions must be selected.");
        }

        try {
            Portal portal = new Portal(
                    UUID.randomUUID(),
                    portalName,
                    displayName == null || displayName.isBlank() ? portalName : displayName,
                    selection.world().orElseThrow(),
                    selection.region().orElseThrow(),
                    Objects.requireNonNull(destination, "destination"),
                    true,
                    permission == null ? "" : permission,
                    defaultCooldownSeconds,
                    "Entering " + (displayName == null || displayName.isBlank() ? portalName : displayName) + "...",
                    "You cannot use this portal.",
                    Map.of("createdBy", actor.name()));
            PortalMutationResult result = portalService.save(portal);
            if (!result.successful()) {
                return CommandResult.failure(String.join("; ", result.errors()));
            }
            selectionService.clear(actor.id());
            return CommandResult.success("Created portal '" + portal.name() + "'.");
        } catch (IOException exception) {
            return CommandResult.failure("Unable to save the portal: " + exception.getMessage());
        } catch (IllegalArgumentException | NullPointerException exception) {
            return CommandResult.failure(exception.getMessage());
        }
    }

    public CommandResult delete(CommandActor actor, String portalName) {
        if (!authorized(actor)) {
            return denied();
        }
        try {
            return portalService.delete(portalName)
                    ? CommandResult.success("Deleted portal '" + portalName + "'.")
                    : CommandResult.failure("Portal not found: " + portalName);
        } catch (IOException exception) {
            return CommandResult.failure("Unable to delete the portal: " + exception.getMessage());
        }
    }

    public CommandResult setEnabled(CommandActor actor, String portalName, boolean enabled) {
        if (!authorized(actor)) {
            return denied();
        }
        try {
            return portalService.setEnabled(portalName, enabled)
                    .map(portal -> CommandResult.success(
                            (enabled ? "Enabled" : "Disabled") + " portal '" + portal.name() + "'."))
                    .orElseGet(() -> CommandResult.failure("Portal not found: " + portalName));
        } catch (IOException exception) {
            return CommandResult.failure("Unable to update the portal: " + exception.getMessage());
        }
    }

    public CommandResult list(CommandActor actor) {
        if (!authorized(actor)) {
            return denied();
        }
        List<Portal> portals = portalService.list();
        if (portals.isEmpty()) {
            return CommandResult.success("No portals are configured.");
        }
        List<String> messages = new ArrayList<>();
        messages.add("Configured portals (" + portals.size() + "/" + maximumPortals + "):");
        portals.forEach(portal -> messages.add("- " + portal.name()
                + " [" + (portal.enabled() ? "enabled" : "disabled") + "] -> "
                + portal.destination().type() + ":" + portal.destination().target()));
        return CommandResult.success(messages);
    }

    public CommandResult info(CommandActor actor, String portalName) {
        if (!authorized(actor)) {
            return denied();
        }
        return portalService.findByName(portalName)
                .map(portal -> CommandResult.success(List.of(
                        "Portal: " + portal.name(),
                        "Display name: " + portal.displayName(),
                        "World: " + portal.sourceWorld(),
                        "Enabled: " + portal.enabled(),
                        "Destination: " + portal.destination().type() + ":" + portal.destination().target(),
                        "Cooldown: " + portal.cooldownSeconds() + "s",
                        "Permission: " + (portal.permission().isBlank() ? "none" : portal.permission()))))
                .orElseGet(() -> CommandResult.failure("Portal not found: " + portalName));
    }

    private CommandResult setPoint(CommandActor actor, String portalName, boolean first) {
        if (!authorized(actor)) {
            return denied();
        }
        WorldPosition position = actor.position();
        if (position == null) {
            return CommandResult.failure("This command must be run by an in-game player.");
        }
        try {
            PortalSelection selection = first
                    ? selectionService.setFirst(actor.id(), portalName, position)
                    : selectionService.setSecond(actor.id(), portalName, position);
            String point = first ? "first" : "second";
            String suffix = selection.complete() ? " Selection is complete." : "";
            return CommandResult.success("Set the " + point + " position for '" + selection.portalName() + "'." + suffix);
        } catch (IllegalArgumentException exception) {
            return CommandResult.failure(exception.getMessage());
        }
    }

    private static boolean authorized(CommandActor actor) {
        return actor != null && actor.hasPermission(ADMIN_PERMISSION);
    }

    private static CommandResult denied() {
        return CommandResult.failure("You do not have permission to manage portals.");
    }
}
