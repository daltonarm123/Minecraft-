package com.community.servercore.neoforge;

import com.community.servercore.ServerCoreRuntime;
import com.community.servercore.economy.PlayerMarketService;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Persistent, item-backed auction house. The actual ItemStack is escrowed as
 * SNBT, so currency can never change hands without a durable item claim.
 */
final class GamingCastleAuctionHouse {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long LISTING_FEE = 25L;
    private static final int TAX_BASIS_POINTS = 500; // 5%
    private static final Duration LISTING_DURATION = Duration.ofHours(24);
    private static final long MAX_PRICE = 1_000_000_000L;

    private final Path file;
    private final Path backup;
    private final List<AuctionListing> listings = new ArrayList<>();
    private final List<ItemClaim> claims = new ArrayList<>();

    GamingCastleAuctionHouse(Path file) throws IOException {
        this.file = file.toAbsolutePath().normalize();
        this.backup = this.file.resolveSibling(this.file.getFileName() + ".bak");
        load();
    }

    static void register(
            RegisterCommandsEvent event,
            Supplier<ServerCoreRuntime> runtimeSupplier,
            Supplier<GamingCastleAuctionHouse> auctionSupplier) {
        var market = Commands.literal("market")
                .executes(context -> list(context.getSource(), auctionSupplier));

        market.then(Commands.literal("list")
                .executes(context -> list(context.getSource(), auctionSupplier)));

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
                                auctionSupplier))));

        market.then(Commands.literal("claim")
                .executes(context -> claim(context.getSource(), auctionSupplier)));

        market.then(Commands.literal("mine")
                .executes(context -> mine(context.getSource(), auctionSupplier)));

        market.then(Commands.literal("help")
                .executes(context -> help(context.getSource())));

        event.getDispatcher().register(market);
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
        String itemSnbt = serialize(player.getServer(), escrow);
        AuctionListing listing = new AuctionListing(
                UUID.randomUUID().toString(),
                player.getUUID().toString(),
                player.getName().getString(),
                escrow.getHoverName().getString(),
                escrow.getCount(),
                itemSnbt,
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
                    java.util.Map.of("listingId", listing.id()),
                    WalletTransactionType.LISTING_FEE,
                    WalletTransactionType.CREDIT);
        } catch (RuntimeException exception) {
            auction.removeListingQuietly(listing.id());
            returnItem(player, escrow);
            source.sendFailure(Component.literal("Listing failed: " + exception.getMessage()));
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

        auction.expireListings();
        Optional<AuctionListing> resolved = auction.resolve(token);
        if (resolved.isEmpty()) {
            source.sendFailure(Component.literal("Listing not found. Use /market list."));
            return 0;
        }
        AuctionListing listing = resolved.orElseThrow();
        if (listing.status() != ListingStatus.ACTIVE) {
            source.sendFailure(Component.literal("That listing is no longer active."));
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
        long tax = Math.multiplyExact(listing.price(), TAX_BASIS_POINTS) / 10_000L;
        long sellerNet = listing.price() - tax;

        try {
            runtime.wallets().transfer(
                    buyer.getUUID(),
                    sellerId,
                    sellerNet,
                    "auction-sale",
                    java.util.Map.of("listingId", listing.id()),
                    WalletTransactionType.TRANSFER_OUT,
                    WalletTransactionType.MARKET_SALE);
            if (tax > 0) {
                runtime.wallets().transfer(
                        buyer.getUUID(),
                        PlayerMarketService.TREASURY_ACCOUNT_ID,
                        tax,
                        "auction-tax",
                        java.util.Map.of("listingId", listing.id()),
                        WalletTransactionType.MARKET_TAX,
                        WalletTransactionType.CREDIT);
            }
            auction.markSoldAndCreateClaim(listing.id(), buyer.getUUID(), listing.itemSnbt(),
                    "Purchased " + listing.displayName());
        } catch (RuntimeException exception) {
            source.sendFailure(Component.literal("Purchase failed safely: " + exception.getMessage()));
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
            Supplier<GamingCastleAuctionHouse> auctionSupplier)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        GamingCastleAuctionHouse auction = requireAuction(source, auctionSupplier);
        if (auction == null) return 0;

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

        auction.cancelAndCreateClaim(listing.id());
        source.sendSuccess(() -> Component.literal(
                        "Listing cancelled. The item is waiting in the seller's /market claim mailbox.")
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int claim(
            CommandSourceStack source,
            Supplier<GamingCastleAuctionHouse> auctionSupplier)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        GamingCastleAuctionHouse auction = requireAuction(source, auctionSupplier);
        if (auction == null) return 0;
        auction.expireListings();

        List<ItemClaim> mine = auction.claimsFor(player.getUUID());
        if (mine.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Your Market mailbox is empty."), false);
            return 1;
        }

        int delivered = 0;
        int remainingClaims = 0;
        for (ItemClaim itemClaim : mine) {
            ItemStack stack;
            try {
                stack = deserialize(player.getServer(), itemClaim.itemSnbt());
            } catch (RuntimeException exception) {
                remainingClaims++;
                continue;
            }
            if (stack.isEmpty()) {
                auction.removeClaim(itemClaim.id());
                continue;
            }

            ItemStack remainder = stack.copy();
            player.getInventory().add(remainder);
            int moved = stack.getCount() - remainder.getCount();
            if (moved > 0) {
                delivered += moved;
            }
            if (remainder.isEmpty()) {
                auction.removeClaim(itemClaim.id());
            } else {
                auction.updateClaimItem(itemClaim.id(), serialize(player.getServer(), remainder));
                remainingClaims++;
            }
        }
        player.getInventory().setChanged();

        int movedTotal = delivered;
        int left = remainingClaims;
        source.sendSuccess(() -> Component.literal(
                        "Market mailbox: delivered " + movedTotal + " item(s)."
                                + (left > 0 ? " " + left + " claim(s) still need inventory space." : ""))
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int list(
            CommandSourceStack source,
            Supplier<GamingCastleAuctionHouse> auctionSupplier) {
        GamingCastleAuctionHouse auction = requireAuction(source, auctionSupplier);
        if (auction == null) return 0;
        auction.expireListings();
        List<AuctionListing> active = auction.activeListings();

        source.sendSuccess(() -> Component.literal("--- Gaming Castle Player Market ---")
                .withStyle(ChatFormatting.GOLD), false);
        if (active.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No player listings are active."), false);
        } else {
            for (AuctionListing listing : active.stream().limit(20).toList()) {
                source.sendSuccess(() -> Component.literal(
                        shortId(listing.id()) + " | " + listing.count() + "x " + listing.displayName()
                                + " | " + listing.price() + " SC | seller: " + listing.sellerName()), false);
            }
        }
        source.sendSuccess(() -> Component.literal(
                "Sell your entire main-hand stack: /market sell <price> | Buy: /market buy <id> | /market claim"), false);
        return 1;
    }

    private static int mine(
            CommandSourceStack source,
            Supplier<GamingCastleAuctionHouse> auctionSupplier)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        GamingCastleAuctionHouse auction = requireAuction(source, auctionSupplier);
        if (auction == null) return 0;
        auction.expireListings();
        List<AuctionListing> mine = auction.listingsFor(player.getUUID());
        source.sendSuccess(() -> Component.literal("--- Your Market Listings ---")
                .withStyle(ChatFormatting.GOLD), false);
        if (mine.isEmpty()) {
            source.sendSuccess(() -> Component.literal("You have no listings."), false);
        } else {
            for (AuctionListing listing : mine.stream().limit(20).toList()) {
                source.sendSuccess(() -> Component.literal(
                        shortId(listing.id()) + " | " + listing.status() + " | "
                                + listing.count() + "x " + listing.displayName() + " | " + listing.price() + " SC"), false);
            }
        }
        return 1;
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("--- Gaming Castle Market ---")
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("/market list - browse active listings"), false);
        source.sendSuccess(() -> Component.literal("/market sell <price> - escrow your entire main-hand stack for 24 hours (25 SC fee)"), false);
        source.sendSuccess(() -> Component.literal("/market buy <id> - buy a listing; item goes to your persistent mailbox"), false);
        source.sendSuccess(() -> Component.literal("/market cancel <id> - cancel your listing"), false);
        source.sendSuccess(() -> Component.literal("/market claim - deliver purchased/returned items into your inventory"), false);
        source.sendSuccess(() -> Component.literal("/market mine - show your listings"), false);
        return 1;
    }

    private synchronized void addListing(AuctionListing listing) {
        listings.add(listing);
        persistUnchecked();
    }

    private synchronized void removeListingQuietly(String id) {
        listings.removeIf(listing -> listing.id().equals(id));
        persistUnchecked();
    }

    private synchronized Optional<AuctionListing> resolve(String token) {
        String normalized = token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
        List<AuctionListing> matches = listings.stream()
                .filter(listing -> listing.id().toLowerCase(Locale.ROOT).startsWith(normalized))
                .toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    private synchronized List<AuctionListing> activeListings() {
        return listings.stream()
                .filter(listing -> listing.status() == ListingStatus.ACTIVE)
                .sorted(Comparator.comparingLong(AuctionListing::createdAtMillis).reversed())
                .toList();
    }

    private synchronized List<AuctionListing> listingsFor(UUID sellerId) {
        String id = sellerId.toString();
        return listings.stream()
                .filter(listing -> listing.sellerId().equals(id))
                .sorted(Comparator.comparingLong(AuctionListing::createdAtMillis).reversed())
                .toList();
    }

    private synchronized List<ItemClaim> claimsFor(UUID ownerId) {
        String id = ownerId.toString();
        return claims.stream()
                .filter(claim -> claim.ownerId().equals(id))
                .sorted(Comparator.comparingLong(ItemClaim::createdAtMillis))
                .toList();
    }

    private synchronized void markSoldAndCreateClaim(
            String listingId,
            UUID buyerId,
            String itemSnbt,
            String reason) {
        replaceListingStatus(listingId, ListingStatus.SOLD);
        claims.add(new ItemClaim(
                UUID.randomUUID().toString(),
                buyerId.toString(),
                itemSnbt,
                reason,
                System.currentTimeMillis()));
        persistUnchecked();
    }

    private synchronized void cancelAndCreateClaim(String listingId) {
        AuctionListing listing = listings.stream()
                .filter(candidate -> candidate.id().equals(listingId))
                .findFirst()
                .orElseThrow();
        replaceListingStatus(listingId, ListingStatus.CANCELLED);
        claims.add(new ItemClaim(
                UUID.randomUUID().toString(),
                listing.sellerId(),
                listing.itemSnbt(),
                "Cancelled listing " + shortId(listing.id()),
                System.currentTimeMillis()));
        persistUnchecked();
    }

    private synchronized void expireListings() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (int index = 0; index < listings.size(); index++) {
            AuctionListing listing = listings.get(index);
            if (listing.status() == ListingStatus.ACTIVE && listing.expiresAtMillis() <= now) {
                listings.set(index, listing.withStatus(ListingStatus.EXPIRED));
                claims.add(new ItemClaim(
                        UUID.randomUUID().toString(),
                        listing.sellerId(),
                        listing.itemSnbt(),
                        "Expired listing " + shortId(listing.id()),
                        now));
                changed = true;
            }
        }
        if (changed) persistUnchecked();
    }

    private void replaceListingStatus(String listingId, ListingStatus status) {
        for (int index = 0; index < listings.size(); index++) {
            AuctionListing listing = listings.get(index);
            if (listing.id().equals(listingId)) {
                if (listing.status() != ListingStatus.ACTIVE) {
                    throw new IllegalStateException("Listing is already closed");
                }
                listings.set(index, listing.withStatus(status));
                return;
            }
        }
        throw new IllegalArgumentException("Listing not found");
    }

    private synchronized void removeClaim(String claimId) {
        claims.removeIf(claim -> claim.id().equals(claimId));
        persistUnchecked();
    }

    private synchronized void updateClaimItem(String claimId, String itemSnbt) {
        for (int index = 0; index < claims.size(); index++) {
            ItemClaim claim = claims.get(index);
            if (claim.id().equals(claimId)) {
                claims.set(index, new ItemClaim(
                        claim.id(), claim.ownerId(), itemSnbt, claim.reason(), claim.createdAtMillis()));
                persistUnchecked();
                return;
            }
        }
    }

    private void load() throws IOException {
        if (!Files.exists(file)) {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            AuctionState state = GSON.fromJson(reader, AuctionState.class);
            if (state != null) {
                if (state.listings() != null) listings.addAll(state.listings());
                if (state.claims() != null) claims.addAll(state.claims());
            }
        } catch (RuntimeException exception) {
            throw new IOException("Unable to parse auction house data: " + file, exception);
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
                GSON.toJson(new AuctionState(new ArrayList<>(listings), new ArrayList<>(claims)), writer);
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
        CompoundTag tag = stack.saveOptional(server.registryAccess());
        return tag.toString();
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

    private enum ListingStatus { ACTIVE, SOLD, CANCELLED, EXPIRED }

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
        AuctionListing {
            Objects.requireNonNull(id);
            Objects.requireNonNull(sellerId);
            Objects.requireNonNull(sellerName);
            Objects.requireNonNull(displayName);
            Objects.requireNonNull(itemSnbt);
            Objects.requireNonNull(status);
        }

        AuctionListing withStatus(ListingStatus value) {
            return new AuctionListing(
                    id, sellerId, sellerName, displayName, count, itemSnbt,
                    price, createdAtMillis, expiresAtMillis, value);
        }
    }

    private record ItemClaim(
            String id,
            String ownerId,
            String itemSnbt,
            String reason,
            long createdAtMillis) { }

    private record AuctionState(List<AuctionListing> listings, List<ItemClaim> claims) { }
}
