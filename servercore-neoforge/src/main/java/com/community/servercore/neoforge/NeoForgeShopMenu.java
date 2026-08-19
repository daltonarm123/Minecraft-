package com.community.servercore.neoforge;

import com.community.servercore.cosmetic.CosmeticsService;
import com.community.servercore.economy.LaunchShopCatalog;
import com.community.servercore.economy.MarketItemKind;
import com.community.servercore.economy.PlayerMarketService;
import com.community.servercore.economy.ShopItemDefinition;
import com.community.servercore.economy.WalletService;
import com.community.servercore.economy.WalletTransactionType;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.MenuProvider;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class NeoForgeShopMenu extends AbstractContainerMenu {
    private static final String SHOP_ITEM_KEY = "ShopItemId";

    // Available to the shop GUI; null on the client side
    @Nullable private final List<ShopItemDefinition> catalog;
    @Nullable private final WalletService wallets;
    @Nullable private final CosmeticsService cosmetics;
    @Nullable private final UUID buyerId;

    // Maps container slot index → catalog item for click processing
    private final Map<Integer, ShopItemDefinition> slotToItem = new HashMap<>();

    // ── Client-side constructor ────────────────────────────────────────────────
    public NeoForgeShopMenu(int windowId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(windowId, null, null, null, null);
    }

    // ── Server-side constructor ────────────────────────────────────────────────
    NeoForgeShopMenu(int windowId,
                     @Nullable List<ShopItemDefinition> catalog,
                     @Nullable WalletService wallets,
                     @Nullable CosmeticsService cosmetics,
                     @Nullable UUID buyerId) {
        super(ServerCoreMod.SHOP_MENU.get(), windowId);
        this.catalog = catalog;
        this.wallets = wallets;
        this.cosmetics = cosmetics;
        this.buyerId = buyerId;

        SimpleContainer container = new SimpleContainer(54);
        if (catalog != null && wallets != null && buyerId != null) {
            fill(container, catalog, wallets, buyerId);
        }

        // Register 54 read-only slots (6 rows × 9 columns)
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                int slot = row * 9 + col;
                addSlot(new Slot(container, slot, col * 18 + 8, row * 18 + 18) {
                    @Override public boolean mayPickup(Player p) { return false; }
                    @Override public boolean mayPlace(ItemStack s) { return false; }
                });
            }
        }
    }

    // ── Slot layout ───────────────────────────────────────────────────────────
    private void fill(SimpleContainer container,
                      List<ShopItemDefinition> catalog,
                      WalletService wallets,
                      UUID buyerId) {
        // Glass-pane border for all 54 slots
        ItemStack filler = labeled(Items.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) container.setItem(i, filler.copy());

        // Place shop items in the inner 7-wide × 4-tall area
        int[] itemPositions = {10, 11, 12, 13, 14, 15, 16,   // row 1
                               19, 20, 21, 22, 23, 24, 25,   // row 2
                               28, 29, 30, 31, 32, 33, 34,   // row 3
                               37, 38, 39, 40, 41, 42, 43};  // row 4
        for (int i = 0; i < catalog.size() && i < itemPositions.length; i++) {
            ShopItemDefinition item = catalog.get(i);
            container.setItem(itemPositions[i], buildItem(item));
            slotToItem.put(itemPositions[i], item);
        }

        // Bottom-center: player balance display
        long balance = wallets.balance(buyerId);
        ItemStack balanceDisplay = new ItemStack(Items.GOLD_NUGGET);
        balanceDisplay.set(DataComponents.ITEM_NAME,
                Component.literal("Your Balance").withStyle(ChatFormatting.GOLD));
        balanceDisplay.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal(String.format("%,d %s", balance, LaunchShopCatalog.CURRENCY_SYMBOL))
                        .withStyle(ChatFormatting.YELLOW))));
        container.setItem(49, balanceDisplay);
    }

    private static ItemStack buildItem(ShopItemDefinition item) {
        Item icon = switch (item.kind()) {
            case OUTFIT -> Items.DIAMOND_CHESTPLATE;
            case COSMETIC -> Items.NETHER_STAR;
            case TITLE -> Items.NAME_TAG;
            case MATERIAL_BUNDLE, UTILITY -> Items.BUNDLE;
        };
        ItemStack stack = new ItemStack(icon);
        stack.set(DataComponents.ITEM_NAME,
                Component.literal(item.displayName()).withStyle(ChatFormatting.YELLOW));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal(item.description()).withStyle(ChatFormatting.GRAY));
        lore.add(Component.empty());
        lore.add(Component.literal("Price: " + String.format("%,d", item.priceMinor()) + " " + LaunchShopCatalog.CURRENCY_SYMBOL)
                .withStyle(ChatFormatting.GOLD));
        lore.add(Component.literal("Type: " + item.kind().name().toLowerCase()).withStyle(ChatFormatting.DARK_GRAY));
        lore.add(Component.empty());
        lore.add(Component.literal("Click to purchase!").withStyle(ChatFormatting.GREEN));
        stack.set(DataComponents.LORE, new ItemLore(lore));

        // Hide armor attributes tooltip
        stack.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, net.minecraft.util.Unit.INSTANCE);

        // Tag with item ID so server can resolve purchase
        CompoundTag tag = new CompoundTag();
        tag.putString(SHOP_ITEM_KEY, item.itemId());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static ItemStack labeled(Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.ITEM_NAME, Component.literal(name));
        return stack;
    }

    // ── Click handling (server side only) ─────────────────────────────────────
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId < 0 || !(player instanceof ServerPlayer serverPlayer)
                || wallets == null || buyerId == null) {
            return;
        }
        ShopItemDefinition item = slotToItem.get(slotId);
        if (item == null) return;

        long balance = wallets.balance(buyerId);
        if (balance < item.priceMinor()) {
            serverPlayer.sendSystemMessage(
                    Component.literal("Not enough balance. Need %,d %s, you have %,d %s."
                                    .formatted(item.priceMinor(), LaunchShopCatalog.CURRENCY_SYMBOL,
                                               balance, LaunchShopCatalog.CURRENCY_SYMBOL))
                            .withStyle(ChatFormatting.RED));
            return;
        }

        try {
            wallets.debit(buyerId, item.priceMinor(), WalletTransactionType.SHOP_PURCHASE,
                    "shop-purchase", PlayerMarketService.TREASURY_ACCOUNT_ID,
                    Map.of("itemId", item.itemId(), "itemName", item.displayName()));
            if (cosmetics != null) cosmetics.grant(buyerId, item.itemId());
            long newBalance = wallets.balance(buyerId);
            serverPlayer.sendSystemMessage(
                    Component.literal("Purchased %s! New balance: %,d %s."
                                    .formatted(item.displayName(), newBalance, LaunchShopCatalog.CURRENCY_SYMBOL))
                            .withStyle(ChatFormatting.GREEN));
        } catch (Exception e) {
            serverPlayer.sendSystemMessage(
                    Component.literal("Purchase failed: " + e.getMessage())
                            .withStyle(ChatFormatting.RED));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    // ── Factory for server-side open ──────────────────────────────────────────
    static MenuProvider providerFor(List<ShopItemDefinition> catalog,
                                    WalletService wallets,
                                    CosmeticsService cosmetics,
                                    UUID buyerId) {
        return new net.minecraft.world.SimpleMenuProvider(
                (id, inv, player) -> new NeoForgeShopMenu(id, catalog, wallets, cosmetics, buyerId),
                Component.literal("Server Shop"));
    }
}
