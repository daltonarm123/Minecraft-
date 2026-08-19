package com.community.servercore.neoforge;

import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

// Registers the vanilla ContainerScreen renderer for the shop menu on the physical client.
@EventBusSubscriber(modid = ServerCoreMod.MOD_ID, value = Dist.CLIENT)
final class NeoForgeClientEvents {
    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register((net.minecraft.world.inventory.MenuType) ServerCoreMod.SHOP_MENU.get(), ContainerScreen::new);
    }

    private NeoForgeClientEvents() {}
}
