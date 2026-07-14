package com.community.servercore.economy;

import java.util.List;

public final class LaunchShopCatalog {
    private LaunchShopCatalog() {
    }

    public static final String CURRENCY_NAME = "Server Credits";
    public static final String CURRENCY_SYMBOL = "SC";

    public static List<ShopItemDefinition> items() {
        return List.of(
                new ShopItemDefinition(
                        "nomad-outfit",
                        "Nomad Outfit",
                        MarketItemKind.OUTFIT,
                        2_500,
                        "Starter outfit with broad launch appeal."),
                new ShopItemDefinition(
                        "starlight-robe-outfit",
                        "Starlight Robe",
                        MarketItemKind.OUTFIT,
                        4_200,
                        "Progression-style robe for event-focused players."),
                new ShopItemDefinition(
                        "crimson-warden-outfit",
                        "Crimson Warden Outfit",
                        MarketItemKind.OUTFIT,
                        6_500,
                        "High-tier visual outfit with no gameplay advantage."),
                new ShopItemDefinition(
                        "merchant-cap",
                        "Merchant Cap",
                        MarketItemKind.COSMETIC,
                        1_600,
                        "Trading-themed head cosmetic."),
                new ShopItemDefinition(
                        "guild-banner-back",
                        "Guild Banner",
                        MarketItemKind.COSMETIC,
                        2_200,
                        "Back accessory designed for social identity."),
                new ShopItemDefinition(
                        "ember-trail",
                        "Ember Trail",
                        MarketItemKind.COSMETIC,
                        3_100,
                        "Noncompetitive particle trail."),
                new ShopItemDefinition(
                        "void-nameplate",
                        "Void Nameplate",
                        MarketItemKind.TITLE,
                        3_600,
                        "Premium nameplate style for launch season."),
                new ShopItemDefinition(
                        "pioneer-title",
                        "Pioneer Title",
                        MarketItemKind.TITLE,
                        2_800,
                        "Identity title for early server supporters."));
    }
}
