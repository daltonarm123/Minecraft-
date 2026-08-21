package com.community.servercore.neoforge;

import com.community.servercore.ServerCoreRuntime;
import com.community.servercore.economy.WalletTransactionType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Community lifecycle hooks: onboarding, playtime rewards, profiles, and mutes. */
final class GamingCastlePlayerEvents {
    private static final long STARTER_REWARD = 500L;
    private static final long PLAYTIME_REWARD = 100L;
    private static final int PLAYTIME_REWARD_TICKS = 20 * 60 * 30; // 30 minutes

    private final Supplier<ServerCoreRuntime> runtimeSupplier;
    private final Supplier<GamingCastleDataStore> dataSupplier;

    GamingCastlePlayerEvents(
            Supplier<ServerCoreRuntime> runtimeSupplier,
            Supplier<GamingCastleDataStore> dataSupplier) {
        this.runtimeSupplier = Objects.requireNonNull(runtimeSupplier, "runtimeSupplier");
        this.dataSupplier = Objects.requireNonNull(dataSupplier, "dataSupplier");
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerCoreRuntime runtime = runtimeSupplier.get();
        GamingCastleDataStore data = dataSupplier.get();
        if (runtime == null || data == null) {
            return;
        }

        runtime.playerStats().registerOrUpdate(player.getUUID(), player.getName().getString());
        boolean firstWelcome = data.markWelcomed(player.getUUID());
        if (firstWelcome) {
            runtime.wallets().credit(
                    player.getUUID(),
                    STARTER_REWARD,
                    WalletTransactionType.CREDIT,
                    "starter-reward",
                    null,
                    Map.of("community", "Gaming Castle"));
            GamingCastleTeleports.teleport(player.getServer(), player, GamingCastleTeleports.HUB);
            player.sendSystemMessage(Component.literal("====================================")
                    .withStyle(ChatFormatting.DARK_PURPLE));
            player.sendSystemMessage(Component.literal("WELCOME TO GAMING CASTLE")
                    .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
            player.sendSystemMessage(Component.literal(
                    "Your adventure starts at the Realm Nexus. Use the four portal districts for Survival, Market, Duels, and Staff.")
                    .withStyle(ChatFormatting.AQUA));
            player.sendSystemMessage(Component.literal(
                    "You received " + STARTER_REWARD + " SC starter currency. Use /help and /rules before heading out.")
                    .withStyle(ChatFormatting.GOLD));
            player.sendSystemMessage(Component.literal("====================================")
                    .withStyle(ChatFormatting.DARK_PURPLE));
        } else {
            player.sendSystemMessage(Component.literal("Welcome back to Gaming Castle. Use /hub anytime.")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount <= 0) {
            return;
        }
        if (player.tickCount % PLAYTIME_REWARD_TICKS != 0) {
            return;
        }
        ServerCoreRuntime runtime = runtimeSupplier.get();
        if (runtime == null) {
            return;
        }
        runtime.wallets().credit(
                player.getUUID(),
                PLAYTIME_REWARD,
                WalletTransactionType.CREDIT,
                "playtime-reward",
                null,
                Map.of("minutes", "30"));
        player.sendSystemMessage(Component.literal(
                "Playtime reward: +" + PLAYTIME_REWARD + " SC for hanging out in Gaming Castle!")
                .withStyle(ChatFormatting.GOLD));
    }

    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
        GamingCastleDataStore data = dataSupplier.get();
        if (data == null) {
            return;
        }
        long mutedUntil = data.mutedUntil(event.getPlayer().getUUID());
        long now = System.currentTimeMillis();
        if (mutedUntil <= now) {
            if (mutedUntil != 0L) {
                data.unmute(event.getPlayer().getUUID());
            }
            return;
        }
        event.setCanceled(true);
        long seconds = Math.max(1L, (mutedUntil - now + 999L) / 1000L);
        event.getPlayer().sendSystemMessage(Component.literal(
                "You are muted for another " + seconds + " second(s).")
                .withStyle(ChatFormatting.RED));
    }
}
