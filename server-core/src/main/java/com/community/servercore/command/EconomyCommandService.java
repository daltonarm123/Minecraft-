package com.community.servercore.command;

import com.community.servercore.cosmetic.CosmeticsService;
import com.community.servercore.economy.LaunchShopCatalog;
import com.community.servercore.economy.MarketItemKind;
import com.community.servercore.economy.MarketListing;
import com.community.servercore.economy.PlayerMarketService;
import com.community.servercore.economy.ShopItemDefinition;
import com.community.servercore.economy.WalletService;
import com.community.servercore.economy.WalletTransactionType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class EconomyCommandService {
    public static final String USE_PERMISSION = "servercore.economy.use";
    public static final String ADMIN_PERMISSION = "servercore.economy.admin";
    public static final String MODERATION_PERMISSION = "servercore.economy.moderate";

    private final WalletService wallets;
    private final PlayerMarketService market;
    private final CosmeticsService cosmetics;
    private final List<ShopItemDefinition> catalog;

    public EconomyCommandService(
            WalletService wallets,
            PlayerMarketService market,
            CosmeticsService cosmetics) {
        this.wallets = Objects.requireNonNull(wallets, "wallets");
        this.market = Objects.requireNonNull(market, "market");
        this.cosmetics = Objects.requireNonNull(cosmetics, "cosmetics");
        this.catalog = List.copyOf(LaunchShopCatalog.items());
    }

    public CommandResult balance(CommandActor actor) {
        if (!canUse(actor)) {
            return deniedUse();
        }
        long balance = wallets.balance(actor.id());
        return CommandResult.success("Balance: " + formatAmount(balance));
    }

    public CommandResult transfer(
            CommandActor actor,
            UUID targetId,
            String targetName,
            long amountMinor) {
        if (!canUse(actor)) {
            return deniedUse();
        }
        try {
            wallets.transfer(
                    actor.id(),
                    targetId,
                    amountMinor,
                    "player-transfer",
                    Map.of("targetName", Objects.requireNonNullElse(targetName, "player")),
                    WalletTransactionType.TRANSFER_OUT,
                    WalletTransactionType.TRANSFER_IN);
            return CommandResult.success("Transferred " + formatAmount(amountMinor) + " to " + targetName + ".");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return CommandResult.failure(exception.getMessage());
        }
    }

    public CommandResult marketList(CommandActor actor, int limit) {
        if (!canUse(actor)) {
            return deniedUse();
        }
        List<MarketListing> listings = market.activeListings(limit).stream()
                .sorted(Comparator.comparing(MarketListing::createdAt).reversed())
                .toList();
        if (listings.isEmpty()) {
            return CommandResult.success("No market listings are active.");
        }

        List<String> messages = new ArrayList<>();
        messages.add("Active listings:");
        for (MarketListing listing : listings) {
            messages.add("- " + listing.listingId()
                    + " | " + listing.itemName()
                    + " x" + listing.quantity()
                    + " | " + formatAmount(listing.unitPriceMinor())
                    + " each");
        }
        return CommandResult.success(messages);
    }

    public CommandResult marketSell(
            CommandActor actor,
            String itemKey,
            String itemName,
            MarketItemKind kind,
            int quantity,
            long unitPriceMinor,
            Duration duration) {
        if (!canUse(actor)) {
            return deniedUse();
        }
        try {
            MarketListing listing = market.createListing(
                    actor.id(),
                    itemKey,
                    itemName,
                    kind,
                    quantity,
                    unitPriceMinor,
                    duration);
            return CommandResult.success("Listing created: " + listing.listingId());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return CommandResult.failure(exception.getMessage());
        }
    }

    public CommandResult marketBuy(CommandActor actor, UUID listingId, int quantity) {
        if (!canUse(actor)) {
            return deniedUse();
        }
        try {
            MarketListing listing = market.buy(actor.id(), listingId, quantity);
            return CommandResult.success(
                    "Purchase completed. Listing status: " + listing.status() + ".");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return CommandResult.failure(exception.getMessage());
        }
    }

    public CommandResult marketCancel(CommandActor actor, UUID listingId) {
        if (!canUse(actor)) {
            return deniedUse();
        }
        try {
            Optional<MarketListing> cancelled = market.cancelListing(
                    listingId,
                    actor.id(),
                    actor.hasPermission(MODERATION_PERMISSION) || actor.hasPermission(ADMIN_PERMISSION));
            if (cancelled.isEmpty()) {
                return CommandResult.failure("Listing not found.");
            }
            return CommandResult.success("Listing status: " + cancelled.orElseThrow().status());
        } catch (IllegalStateException exception) {
            return CommandResult.failure(exception.getMessage());
        }
    }

    public CommandResult shop(CommandActor actor) {
        if (!canUse(actor)) {
            return deniedUse();
        }
        List<String> messages = new ArrayList<>();
        messages.add("Shop catalog (" + LaunchShopCatalog.CURRENCY_SYMBOL + "):");
        for (ShopItemDefinition item : catalog) {
            messages.add("- " + item.itemId() + " | " + item.displayName() + " | " + formatAmount(item.priceMinor()));
        }
        return CommandResult.success(messages);
    }

    public CommandResult shopBuy(CommandActor actor, String itemId) {
        if (!canUse(actor)) {
            return deniedUse();
        }
        String normalizedId = itemId == null ? "" : itemId.trim().toLowerCase();
        Optional<ShopItemDefinition> item = catalog.stream()
                .filter(candidate -> candidate.itemId().equals(normalizedId))
                .findFirst();
        if (item.isEmpty()) {
            return CommandResult.failure("Shop item not found: " + normalizedId);
        }

        ShopItemDefinition selected = item.orElseThrow();
        try {
            wallets.transfer(
                    actor.id(),
                    PlayerMarketService.TREASURY_ACCOUNT_ID,
                    selected.priceMinor(),
                    "shop-purchase",
                    Map.of("itemId", selected.itemId(), "itemName", selected.displayName()),
                    WalletTransactionType.SHOP_PURCHASE,
                    WalletTransactionType.CREDIT);

            if (selected.kind() == MarketItemKind.COSMETIC
                    || selected.kind() == MarketItemKind.OUTFIT
                    || selected.kind() == MarketItemKind.TITLE) {
                cosmetics.grant(actor.id(), selected.itemId());
            }

            return CommandResult.success(
                    "Purchased " + selected.displayName() + " for " + formatAmount(selected.priceMinor()) + ".");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return CommandResult.failure(exception.getMessage());
        }
    }

    public CommandResult adminCredit(
            CommandActor actor,
            UUID targetId,
            String targetName,
            long amountMinor,
            String reason) {
        if (!canAdmin(actor)) {
            return CommandResult.failure("You do not have permission to manage the economy.");
        }
        try {
            wallets.credit(
                    targetId,
                    amountMinor,
                    WalletTransactionType.CREDIT,
                    reason == null || reason.isBlank() ? "admin-credit" : reason,
                    actor.id(),
                    Map.of("targetName", Objects.requireNonNullElse(targetName, "player")));
            return CommandResult.success("Credited " + formatAmount(amountMinor) + " to " + targetName + ".");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return CommandResult.failure(exception.getMessage());
        }
    }

    private static boolean canUse(CommandActor actor) {
        return actor != null
                && (actor.hasPermission(USE_PERMISSION)
                || actor.hasPermission(ADMIN_PERMISSION));
    }

    private static boolean canAdmin(CommandActor actor) {
        return actor != null && actor.hasPermission(ADMIN_PERMISSION);
    }

    private static CommandResult deniedUse() {
        return CommandResult.failure("You do not have permission to use the economy.");
    }

    private static String formatAmount(long amountMinor) {
        return amountMinor + " " + LaunchShopCatalog.CURRENCY_SYMBOL;
    }
}
