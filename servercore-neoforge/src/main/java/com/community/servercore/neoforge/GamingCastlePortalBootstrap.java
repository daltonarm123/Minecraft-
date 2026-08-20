package com.community.servercore.neoforge;

import com.community.servercore.ServerCoreRuntime;
import com.community.servercore.portal.Portal;
import com.community.servercore.portal.PortalDestination;
import com.community.servercore.portal.PortalRegion;
import com.community.servercore.service.PortalMutationResult;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/** Creates and maintains the Gaming Castle hub/destination portal network. */
final class GamingCastlePortalBootstrap {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final int COOLDOWN_SECONDS = 2;

    private GamingCastlePortalBootstrap() {}

    static void ensure(ServerCoreRuntime runtime) throws IOException {
        // Hub -> destination portals. Regions are the walk-on pads directly in front of
        // the four labeled portal gates in Gaming Castle.
        upsert(
                runtime,
                "gc_market",
                "Gaming Castle Market",
                new PortalRegion(-226, 66, 4, -210, 71, 10),
                PortalDestination.location(OVERWORLD, 1500, 72, 30, 180.0F, 0.0F),
                "");
        upsert(
                runtime,
                "gc_staff",
                "Gaming Castle Staff Lounge",
                new PortalRegion(-86, 66, 4, -70, 71, 10),
                PortalDestination.location(OVERWORLD, 0, 72, -1480, 180.0F, 0.0F),
                "servercore.staff");
        upsert(
                runtime,
                "gc_survival",
                "Gaming Castle Survival",
                new PortalRegion(-226, 66, 92, -210, 71, 100),
                PortalDestination.location(OVERWORLD, 0, 72, 1500, 180.0F, 0.0F),
                "");
        upsert(
                runtime,
                "gc_duels",
                "Gaming Castle Duels",
                new PortalRegion(-86, 66, 92, -70, 71, 100),
                PortalDestination.location(OVERWORLD, -1500, 72, 30, 180.0F, 0.0F),
                "");

        // Destination -> hub return portals.
        PortalDestination hubReturn =
                PortalDestination.location(OVERWORLD, -145, 67, 70, 180.0F, 0.0F);
        upsert(
                runtime,
                "gc_market_return",
                "Return to Gaming Castle",
                new PortalRegion(1493, 71, 38, 1507, 75, 45),
                hubReturn,
                "");
        upsert(
                runtime,
                "gc_duels_return",
                "Return to Gaming Castle",
                new PortalRegion(-1507, 71, 38, -1493, 75, 45),
                hubReturn,
                "");
        upsert(
                runtime,
                "gc_staff_return",
                "Return to Gaming Castle",
                new PortalRegion(-7, 71, -1462, 7, 75, -1458),
                hubReturn,
                "");
        upsert(
                runtime,
                "gc_survival_return",
                "Return to Gaming Castle",
                new PortalRegion(-7, 71, 1520, 7, 75, 1524),
                hubReturn,
                "");
    }

    private static void upsert(
            ServerCoreRuntime runtime,
            String name,
            String displayName,
            PortalRegion region,
            PortalDestination destination,
            String permission) throws IOException {
        UUID id = runtime.portals().findByName(name)
                .map(Portal::id)
                .orElseGet(UUID::randomUUID);
        Portal portal = new Portal(
                id,
                name,
                displayName,
                OVERWORLD,
                region,
                destination,
                true,
                permission,
                COOLDOWN_SECONDS,
                "Entering " + displayName + "...",
                "You do not have access to this destination.",
                Map.of("managedBy", "gaming_castle"));
        PortalMutationResult result = runtime.portals().save(portal);
        if (!result.successful()) {
            throw new IOException(
                    "Unable to configure portal '" + name + "': " + String.join("; ", result.errors()));
        }
    }
}
