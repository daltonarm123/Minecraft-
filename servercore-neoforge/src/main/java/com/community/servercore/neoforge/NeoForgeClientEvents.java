package com.community.servercore.neoforge;

import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

// RegisterMenuScreensEvent is a mod lifecycle event — must use Bus.MOD.
@EventBusSubscriber(modid = ServerCoreMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
final class NeoForgeClientEvents {
    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register((net.minecraft.world.inventory.MenuType) ServerCoreMod.SHOP_MENU.get(), ContainerScreen::new);
    }

    private NeoForgeClientEvents() {}
}
