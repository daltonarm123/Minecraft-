package com.community.servercore.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

// RegisterMenuScreensEvent is a mod lifecycle event — must use Bus.MOD.
@EventBusSubscriber(modid = ServerCoreMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
final class NeoForgeClientEvents {
    @SubscribeEvent
    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ServerCoreMod.SHOP_MENU.get(), NeoForgeShopScreen::new);
    }

    private NeoForgeClientEvents() {}
}
