package com.community.servercore.neoforge;

import com.community.servercore.ServerCoreRuntime;
import com.community.servercore.economy.PlayerMarketService;
import com.community.servercore.economy.WalletTransaction;
import com.community.servercore.economy.WalletTransactionType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Persistent, item-backed public auction house for Gaming Castle.
 *
 * The exact ItemStack is stored as SNBT while listed. Purchases use a small
 * durable pending-purchase journal, so an interrupted server write can be
 * recovered from the wallet ledger instead of duplicating or losing the item.
 */
final class GamingCastleAuctionHouse {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long LISTING_FEE = 25L;
    private static final Duration LISTING_DURATION = Duration.ofHours(24);
    private static final long MAX_PRICE = 1_000_000_000L;
    private static final int LIST_LIMIT = 30;

    private final Path file;
    private final Path backup;
    private final List<AuctionListing> listings = new ArrayList<>();
    private final List<ItemClaim> claims = new ArrayList<>();
    private final List<PendingPurchase> pendingPurchases = new ArrayList<>();

    GamingCastleAuctionHouse(Path file) throws IOException {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        this.backup = this.file.resolveSibling(this.file.getFileName() + ".bak");
        load();
    }

    static void register(
            RegisterCommandsEvent event,
            Supplier<ServerCoreRuntime> runtimeSupplier,
            Supplier<GamingCastleAuctionHouse> auctionSupplier) {
        var market = Commands.literal("market")
                .executes(context -> list(context.getSource(), runtimeSupplier, auctionSupplier));

        market.then(Commands.literal("list")
                .executes(context -> list(context.getSource(), runtimeSupplier, auctionSupplier)));

        market.then(Commands.literal("sell")
                .then(Commands.argument("price", LongArgumentType.longArg(1L, MAX_PRICE))
                        .executes(context -> sell(
                                context.getSource(),
                                LongArgumentType.getLong(context, "price"),
                                runtimeSupplier,
                                auctionSupplier))));

        market.then(Commands.literal("buy")
                .then(Commands.argument("listing", StringArgumentType.word())
                        .executes(context -> buy(
                                context.getSource(),
                                StringArgumentType.getString(context, "listing"),
                                runtimeSupplier,
                                auctionSupplier))));

        market.then(Commands.literal("cancel")
                .then(Commands.argument("listing", StringArgumentType.word())
                        .executes(context -> cancel(
                                context.getSource(),
                                StringArgumentType.getString(context, "listing"),
                                runtimeSupplier,
                                auctionSupplier))));

        market.then(Commands.literal("claim")
                .executes(context -> claim(context.getSource(), runtimeSupplier, auctionSupplier)));

        market.then(Commands.literal("mine")
                .executes(context -> mine(context.getSource(), runtimeSupplier, auctionSupplier)));

        market.then(Commands.literal("help")
                .executes(context -> help(context.getSource())));

        event.getDispatcher().register(market);
    }

    synchronized void recover(ServerCoreRuntime runtime) {
        Objects.requireNonNull(runtime, "runtime");
        if (pendingPurchases.isEmpty()) {
            return;
        }

        List<PendingPurchase> pending = List.copyOf(pendingPurchases);
        boolean changed = false;
        for (PendingPurchase purchase : pending) {
            UUID buyerId;
            try {
                buyerId = UUID.fromString(purchase.buyerId());
            } catch (IllegalArgumentException exception) {
                pendingPurchases.remove(purchase);
                changed = true;
                continue;
            }

            boolean paid = runtime.wallets().recentTransactions(buyerId, 1000).stream()
                    .anyMatch(transaction -> paymentMatches(transaction, purchase.listingId()));
            if (paid) {
                completePurchaseInternal(purchase.listingId(), buyerId);
            } else {
                pendingPurchases.remove(purchase);
            }
            changed = true;
        }
        if (changed) {
            persistUnchecked();
        }
    }

    private static boolean paymentMatches(WalletTransaction transaction, String listingId) {
        return "auction-sale".equals(transaction.reason())
                && listingId.equals(transaction.attributes().get("listingId"))
                && transaction.type() == WalletTransactionType.TRANSFER_OUT;
    }

    private static int sell(
            CommandSourceStack source,
            long price,
            Supplier<ServerCoreRuntime> runtimeSupplier,
            Supplier<GamingCastleAuctionHouse> auctionSupplier)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerCoreRuntime runtime = requireRuntime(source, runtimeSupplier);
        GamingCastleAuctionHouse auction = requireAuction(source, auctionSupplier);
        if (runtime == null || auction == null) return 0;
        auction.recover(runtime);
        auction.expireListings();

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            source.sendFailure(Component.literal("Hold the stack you want to sell in your main hand."));
            return 0;
        }
        if (runtime.wallets().balance(player.getUUID()) < LISTING_FEE) {
            source.sendFailure(Component.literal("You need " + LISTING_FEE + " SC for the listing fee."));
            return 0;
        }

        ItemStack escrow = held.copy();
        String listingId = UUID.randomUUID().toString();
        AuctionListing listing = new AuctionListing(
                listingId,
                player.getUUID().toString(),
                player.getName().getString(),
                escrow.getHoverName().getString(),
                escrow.getCount(),
                serialize(player.getServer(), escrow),
                price,
                System.currentTimeMillis(),
                System.currentTimeMillis() + LISTING_DURATION.toMillis(),
                ListingStatus.ACTIVE);

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        try {
            auction.addListing(listing);
            runtime.wallets().transfer(
                    player.getUUID(),
                    PlayerMarketService.TREASURY_ACCOUNT_ID,
                    LISTING_FEE,
                    "auction-listing-fee",
                    Map.of("listingId", listingId),
                    WalletTransactionType.LISTING_FEE,
                    WalletTransactionType.CREDIT);
        } catch (RuntimeException exception) {
            auction.removeListingQuietly(listingId);
            returnItem(player, escrow);
            source.sendFailure(Component.literal("Listing failed safely: " + exception.getMessage()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                        "Listed " + listing.count() + "x " + listing.displayName()
                                + " for " + listing.price() + " SC. ID: " + shortId(listing.id()))
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int buy(
            CommandSourceStack source,
            String token,
            Supplier<ServerCoreRuntime> runtimeSupplier,
            Supplier<GamingCastleAuctionHouse> auctionSupplier)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer buyer = source.getPlayerOrException();
        ServerCoreRuntime runtime = requireRuntime(source, runtimeSupplier);
        GamingCastleAuctionHouse auction = requireAuction(source, auctionSupplier);
        if (runtime == null || auction == null) return 0;
        auction.recover(runtime);
        auction.expireListings();

        Optional<AuctionListing> resolved = auction.resolve(token);
        if (resolved.isEmpty()) {
            source.sendFailure(Component.literal("Listing not found. Use /market list."));
            return 0;
        }
        AuctionListing listing = resolved.orElseThrow();
        if (listing.status() != ListingStatus.ACTIVE || auction.isPending(listing.id())) {
            source.sendFailure(Component.literal("That listing is no longer available."));
            return 0;
        }
        if (listing.sellerId().equals(buyer.getUUID().toString())) {
            source.sendFailure(Component.literal("You cannot buy your own listing."));
            return 0;
        }
        if (runtime.wallets().balance(buyer.getUUID()) < listing.price()) {
            source.sendFailure(Component.literal("You need " + listing.price() + " SC to buy that listing."));
            return 0;
        }

        UUID sellerId = UUID.fromString(listing.sellerId());
        boolean paid = false;
        try {
            auction.beginPurchase(listing.id(), buyer.getUUID());
            runtime.wallets().transfer(
                    buyer.getUUID(),
                    sellerId,
                    listing.price(),
                    "auction-sale",
                    Map.of("listingId", listing.id(), "item", listing.displayName()),
                    WalletTransactionType.TRANSFER_OUT,
                    WalletTransactionType.MARKET_SALE);
            paid = true;
            auction.completePurchase(listing.id(), buyer.getUUID());
        } catch (RuntimeException exception) {
            if (!paid) {
                auction.releasePendingQuietly(listing.id(), buyer.getUUID());
                source.sendFailure(Component.literal("Purchase failed safely: " + exception.getMessage()));
            } else {
                source.sendFailure(Component.literal(
                        "Payment was recorded, but item delivery needs recovery. Run /market claim again; ServerCore will recover the purchase from the wallet ledger."));
            }
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                        "Purchased " + listing.count() + "x " + listing.displayName()
                                + " for " + listing.price() + " SC. Use /market claim to receive it.")
                .withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int cancel(
            CommandSourceStack source,
            String token,
            Supplier<ServerCoreRuntime> runtimeSupplier,
            Supplier<GamingCastleAuctionHouse> auctionSupplier)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerCoreRuntime runtime = requireRuntime(source, runtimeSupplier);
        GamingCastleAuctionHouse auction = requireAuction(source, auctionSupplier);
        if (runtime == null || auction == null) return 0;
        auction.recover(runtime);
        auction.expireListings();

        Optional<AuctionListing> resolved = auction.resolve(token);
        if (resolved.isEmpty()) {
            source.sendFailure(Component.literal("Listing not found."));
            return 0;
        }
        AuctionListing listing = resolved.orElseThrow();
        boolean staff = NeoForgePermissions.check(player, NeoForgePermissions.STAFF_PERMISSION);
        if (!listing.sellerId().equals(player.getUUID().toString()) && !staff) {
            source.sendFailure(Component.literal("Only the seller or staff can cancel this listing."));
            return 0;
        }
        if (listing.status() != ListingStatus.ACTIVE) {
            source.sendFailure(Component.literal("That listing is already closed."));
            return 0;
        }
        if (auction.isPending(listing.id())) {
            source.sendFailure(Component.literal("That listing currently has a purchase in progress."));
            return 0;
        }

        auction.cancelAndCreateClaim(listing.id());
        source.sendSuccess(() -> Component.literal(
                        "Listing cancelled. The item was placed in the seller's /market claim mailbox.")
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int claim(
            CommandSourceStack source,
            Supplier<ServerCoreRuntime> runtimeSupplier,
            Supplier<GamingCastleAuctionHouse> auctionSupplier)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerCoreRuntime runtime = requireRuntime(source, runtimeSupplier);
        GamingCastleAuctionHouse auction = requireAuction(source, auctionSupplier);
        if (runtime == null || auction == null) return 0;
        auction.recover(runtime);
        auction.expireListings();

        int delivered = auction.deliverClaims(player);
        int remaining = auction.claimCount(player.getUUID());
        if (delivered == 0 && remaining == 0) {
            source.sendSuccess(() -> Component.literal("You have no Market items waiting to be claimed."), false);
            return 1;
        }
        if (remaining > 0) {
            source.sendSuccess(() -> Component.literal(
                            "Claimed " + delivered + " stack(s). " + remaining
                                    + " stack(s) are still safely stored because your inventory needs more room.")
                    .withStyle(ChatFormatting.YELLOW), false);
        } else {
            source.sendSuccess(() -> Component.literal(
                            "Claimed " + delivered + " Market stack(s).")
                    .withStyle(ChatFormatting.GREEN), false);
        }
        return 1;
    }

    private static int list(
            CommandSourceStack source,
            Supplier<ServerCoreRuntime> runtimeSupplier,
            Supplier<GamingCastleAuctionHouse> auctionSupplier) {
        ServerCoreRuntime runtime = requireRuntime(source, runtimeSupplier);
        GamingCastleAuctionHouse auction = requireAuction(source, auctionSupplier);
        if (runtime == null || auction == null) return 0;
        auction.recover(runtime);
        auction.expireListings();

        List<AuctionListing> active = auction.activeListings();
        source.sendSuccess(() -> Component.literal("--- Gaming Castle Player Market ---")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        if (active.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No active player listings. Hold an item and use /market sell <price>."), false);
            return 1;
        }
        for (AuctionListing listing : active) {
            source.sendSuccess(() -> Component.literal(
                    shortId(listing.id()) + " | " + listing.count() + "x " + listing.displayName()
                            + " | " + listing.price() + " SC | seller " + listing.sellerName()), false);
        }
        source.sendSuccess(() -> Component.literal("Buy with /market buy <id>. Items are delivered through /market claim."), false);
        return 1;
    }

    private static int mine(
            CommandSourceStack source,
            Supplier<ServerCoreRuntime> runtimeSupplier,
            Supplier<GamingCastleAuctionHouse> auctionSupplier)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerCoreRuntime runtime = requireRuntime(source, runtimeSupplier);
        GamingCastleAuctionHouse auction = requireAuction(source, auctionSupplier);
        if (runtime == null || auction == null) return 0;
        auction.recover(runtime);
        auction.expireListings();

        List<AuctionListing> mine = auction.listingsFor(player.getUUID());
        if (mine.isEmpty()) {
            source.sendSuccess(() -> Component.literal("You have no active Market listings."), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("Your active Market listings:").withStyle(ChatFormatting.GOLD), false);
        for (AuctionListing listing : mine) {
            source.sendSuccess(() -> Component.literal(
                    shortId(listing.id()) + " | " + listing.count() + "x " + listing.displayName()
                            + " | " + listing.price() + " SC"), false);
        }
        return 1;
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("--- Gaming Castle Market ---").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("/market list - browse active listings"), false);
        source.sendSuccess(() -> Component.literal("/market sell <price> - list the entire stack in your main hand (25 SC fee, 24h)"), false);
        source.sendSuccess(() -> Component.literal("/market buy <id> - buy a listing"), false);
        source.sendSuccess(() -> Component.literal("/market cancel <id> - cancel your listing"), false);
        source.sendSuccess(() -> Component.literal("/market mine - show your active listings"), false);
        source.sendSuccess(() -> Component.literal("/market claim - receive purchased, cancelled, or expired items"), false);
        return 1;
    }

    private synchronized void addListing(AuctionListing listing) {
        listings.add(Objects.requireNonNull(listing, "listing"));
        persistUnchecked();
    }

    private synchronized void beginPurchase(String listingId, UUID buyerId) {
        AuctionListing listing = requireListing(listingId);
        if (listing.status() != ListingStatus.ACTIVE || isPending(listingId)) {
            throw new IllegalStateException("Listing is not available");
        }
        pendingPurchases.add(new PendingPurchase(listingId, buyerId.toString(), System.currentTimeMillis()));
        persistUnchecked();
    }

    private synchronized void completePurchase(String listingId, UUID buyerId) {
        PendingPurchase pending = pendingPurchases.stream()
                .filter(candidate -> candidate.listingId().equals(listingId)
                        && candidate.buyerId().equals(buyerId.toString()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Purchase journal entry is missing"));
        completePurchaseInternal(listingId, buyerId);
        pendingPurchases.remove(pending);
        persistUnchecked();
    }

    private void completePurchaseInternal(String listingId, UUID buyerId) {
        AuctionListing listing = requireListing(listingId);
        if (listing.status() == ListingStatus.SOLD) {
            if (claims.stream().noneMatch(claim -> claim.sourceListingId().equals(listingId)
                    && claim.ownerId().equals(buyerId.toString()))) {
                claims.add(new ItemClaim(
                        UUID.randomUUID().toString(),
                        buyerId.toString(),
                        listing.itemSnbt(),
                        "Purchased " + listing.displayName(),
                        listingId));
            }
            pendingPurchases.removeIf(candidate -> candidate.listingId().equals(listingId));
            return;
        }
        if (listing.status() != ListingStatus.ACTIVE) {
            throw new IllegalStateException("Listing is no longer active");
        }
        replaceListing(listing.withStatus(ListingStatus.SOLD));
        claims.add(new ItemClaim(
                UUID.randomUUID().toString(),
                buyerId.toString(),
                listing.itemSnbt(),
                "Purchased " + listing.displayName(),
                listingId));
        pendingPurchases.removeIf(candidate -> candidate.listingId().equals(listingId));
    }

    private synchronized void releasePendingQuietly(String listingId, UUID buyerId) {
        try {
            if (pendingPurchases.removeIf(candidate -> candidate.listingId().equals(listingId)
                    && candidate.buyerId().equals(buyerId.toString()))) {
                persistUnchecked();
            }
        } catch (RuntimeException ignored) {
            // Recovery will reevaluate any surviving journal entry next time.
        }
    }

    private synchronized void cancelAndCreateClaim(String listingId) {
        AuctionListing listing = requireListing(listingId);
        if (listing.status() != ListingStatus.ACTIVE || isPending(listingId)) {
            throw new IllegalStateException("Listing cannot be cancelled now");
        }
        replaceListing(listing.withStatus(ListingStatus.CANCELLED));
        claims.add(new ItemClaim(
                UUID.randomUUID().toString(),
                listing.sellerId(),
                listing.itemSnbt(),
                "Cancelled " + listing.displayName(),
                listing.id()));
        persistUnchecked();
    }

    private synchronized void expireListings() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (AuctionListing listing : List.copyOf(listings)) {
            if (listing.status() != ListingStatus.ACTIVE
                    || listing.expiresAtMillis() > now
                    || isPending(listing.id())) {
                continue;
            }
            replaceListing(listing.withStatus(ListingStatus.EXPIRED));
            claims.add(new ItemClaim(
                    UUID.randomUUID().toString(),
                    listing.sellerId(),
                    listing.itemSnbt(),
                    "Expired " + listing.displayName(),
                    listing.id()));
            changed = true;
        }
        if (changed) persistUnchecked();
    }

    private synchronized List<AuctionListing> activeListings() {
        return listings.stream()
                .filter(listing -> listing.status() == ListingStatus.ACTIVE)
                .filter(listing -> !isPending(listing.id()))
                .sorted(Comparator.comparingLong(AuctionListing::createdAtMillis).reversed())
                .limit(LIST_LIMIT)
                .toList();
    }

    private synchronized List<AuctionListing> listingsFor(UUID sellerId) {
        String seller = sellerId.toString();
        return listings.stream()
                .filter(listing -> listing.sellerId().equals(seller))
                .filter(listing -> listing.status() == ListingStatus.ACTIVE)
                .filter(listing -> !isPending(listing.id()))
                .sorted(Comparator.comparingLong(AuctionListing::createdAtMillis).reversed())
                .toList();
    }

    private synchronized Optional<AuctionListing> resolve(String token) {
        String normalized = token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) return Optional.empty();
        List<AuctionListing> matches = listings.stream()
                .filter(listing -> listing.id().toLowerCase(Locale.ROOT).startsWith(normalized))
                .toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    private synchronized boolean isPending(String listingId) {
        return pendingPurchases.stream().anyMatch(pending -> pending.listingId().equals(listingId));
    }

    private synchronized int deliverClaims(ServerPlayer player) {
        int delivered = 0;
        String owner = player.getUUID().toString();
        for (ItemClaim claim : List.copyOf(claims)) {
            if (!claim.ownerId().equals(owner)) continue;
            ItemStack stack = deserialize(player.getServer(), claim.itemSnbt());
            player.getInventory().add(stack);
            if (stack.isEmpty()) {
                claims.remove(claim);
                delivered++;
            } else {
                replaceClaim(claim, claim.withItemSnbt(serialize(player.getServer(), stack)));
            }
        }
        persistUnchecked();
        return delivered;
    }

    private synchronized int claimCount(UUID playerId) {
        String owner = playerId.toString();
        return (int) claims.stream().filter(claim -> claim.ownerId().equals(owner)).count();
    }

    private AuctionListing requireListing(String id) {
        return listings.stream()
                .filter(listing -> listing.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Listing not found"));
    }

    private void replaceListing(AuctionListing updated) {
        for (int index = 0; index < listings.size(); index++) {
            if (listings.get(index).id().equals(updated.id())) {
                listings.set(index, updated);
                return;
            }
        }
        throw new IllegalStateException("Listing not found: " + updated.id());
    }

    private void replaceClaim(ItemClaim oldClaim, ItemClaim updated) {
        int index = claims.indexOf(oldClaim);
        if (index >= 0) claims.set(index, updated);
    }

    private synchronized void removeListingQuietly(String listingId) {
        try {
            if (listings.removeIf(listing -> listing.id().equals(listingId))) {
                pendingPurchases.removeIf(pending -> pending.listingId().equals(listingId));
                persistUnchecked();
            }
        } catch (RuntimeException ignored) {
            // The caller still restores the player's item.
        }
    }

    private void load() throws IOException {
        if (!Files.exists(file)) {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            return;
        }
        try {
            loadFrom(file);
        } catch (IOException primary) {
            if (!Files.exists(backup)) throw primary;
            listings.clear();
            claims.clear();
            pendingPurchases.clear();
            loadFrom(backup);
        }
    }

    private void loadFrom(Path source) throws IOException {
        try (Reader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            AuctionState state = GSON.fromJson(reader, AuctionState.class);
            if (state == null) return;
            if (state.listings() != null) listings.addAll(state.listings());
            if (state.claims() != null) claims.addAll(state.claims());
            if (state.pendingPurchases() != null) pendingPurchases.addAll(state.pendingPurchases());
        } catch (RuntimeException exception) {
            throw new IOException("Unable to parse auction house data: " + source, exception);
        }
    }

    private synchronized void persistUnchecked() {
        try {
            persist();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to persist auction house data", exception);
        }
    }

    private void persist() throws IOException {
        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
        try {
            if (Files.exists(file)) {
                Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(new AuctionState(
                        new ArrayList<>(listings),
                        new ArrayList<>(claims),
                        new ArrayList<>(pendingPurchases)), writer);
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

    private static String serialize(MinecraftServer server, ItemStack stack) {
        return stack.saveOptional(server.registryAccess()).toString();
    }

    private static ItemStack deserialize(MinecraftServer server, String snbt) {
        try {
            CompoundTag tag = TagParser.parseTag(snbt);
            return ItemStack.parseOptional(server.registryAccess(), tag);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            throw new IllegalStateException("Stored item data is invalid", exception);
        }
    }

    private static void returnItem(ServerPlayer player, ItemStack stack) {
        ItemStack remainder = stack.copy();
        if (player.getMainHandItem().isEmpty()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, remainder);
            return;
        }
        player.getInventory().add(remainder);
        if (!remainder.isEmpty()) {
            player.drop(remainder, false);
        }
    }

    private static ServerCoreRuntime requireRuntime(
            CommandSourceStack source,
            Supplier<ServerCoreRuntime> runtimeSupplier) {
        ServerCoreRuntime runtime = runtimeSupplier.get();
        if (runtime == null) source.sendFailure(Component.literal("ServerCore has not finished starting."));
        return runtime;
    }

    private static GamingCastleAuctionHouse requireAuction(
            CommandSourceStack source,
            Supplier<GamingCastleAuctionHouse> auctionSupplier) {
        GamingCastleAuctionHouse auction = auctionSupplier.get();
        if (auction == null) source.sendFailure(Component.literal("The Gaming Castle Market is temporarily unavailable."));
        return auction;
    }

    private static String shortId(String id) {
        return id.length() <= 8 ? id : id.substring(0, 8);
    }

    private enum ListingStatus {
        ACTIVE,
        SOLD,
        CANCELLED,
        EXPIRED
    }

    private record AuctionListing(
            String id,
            String sellerId,
            String sellerName,
            String displayName,
            int count,
            String itemSnbt,
            long price,
            long createdAtMillis,
            long expiresAtMillis,
            ListingStatus status) {
        private AuctionListing {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(sellerId, "sellerId");
            Objects.requireNonNull(sellerName, "sellerName");
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(itemSnbt, "itemSnbt");
            Objects.requireNonNull(status, "status");
            if (count < 1) throw new IllegalArgumentException("count must be positive");
            if (price < 1) throw new IllegalArgumentException("price must be positive");
        }

        private AuctionListing withStatus(ListingStatus newStatus) {
            return new AuctionListing(
                    id, sellerId, sellerName, displayName, count, itemSnbt,
                    price, createdAtMillis, expiresAtMillis, newStatus);
        }
    }

    private record ItemClaim(
            String id,
            String ownerId,
            String itemSnbt,
            String reason,
            String sourceListingId) {
        private ItemClaim {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(ownerId, "ownerId");
            Objects.requireNonNull(itemSnbt, "itemSnbt");
            reason = Objects.requireNonNullElse(reason, "Market item");
            sourceListingId = Objects.requireNonNullElse(sourceListingId, "");
        }

        private ItemClaim withItemSnbt(String replacement) {
            return new ItemClaim(id, ownerId, replacement, reason, sourceListingId);
        }
    }

    private record PendingPurchase(String listingId, String buyerId, long createdAtMillis) {
        private PendingPurchase {
            Objects.requireNonNull(listingId, "listingId");
            Objects.requireNonNull(buyerId, "buyerId");
        }
    }

    private record AuctionState(
            List<AuctionListing> listings,
            List<ItemClaim> claims,
            List<PendingPurchase> pendingPurchases) { }
}
