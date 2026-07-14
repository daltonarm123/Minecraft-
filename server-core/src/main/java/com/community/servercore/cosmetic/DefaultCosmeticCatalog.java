package com.community.servercore.cosmetic;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class DefaultCosmeticCatalog {
    private DefaultCosmeticCatalog() {
    }

    public static int seed(CosmeticsService service) {
        Objects.requireNonNull(service, "service");
        int added = 0;
        for (CosmeticDefinition definition : definitions()) {
            if (service.registerIfAbsent(definition)) {
                added++;
            }
        }
        return added;
    }

    public static List<CosmeticDefinition> definitions() {
        return List.of(
            definition(
                "nomad-outfit",
                "Nomad Outfit",
                CosmeticCategory.OUTFIT,
                CosmeticRarity.UNCOMMON,
                CosmeticUnlockSource.SUPPORTER,
                "servercore:cosmetics/outfits/nomad",
                "Starter outfit tuned for launch progression and market circulation."),
            definition(
                "crimson-warden-outfit",
                "Crimson Warden Outfit",
                CosmeticCategory.OUTFIT,
                CosmeticRarity.EPIC,
                CosmeticUnlockSource.SUPPORTER,
                "servercore:cosmetics/outfits/crimson_warden",
                "High-tier outfit designed for endgame style without stat bonuses."),
            definition(
                "starlight-robe-outfit",
                "Starlight Robe",
                CosmeticCategory.OUTFIT,
                CosmeticRarity.RARE,
                CosmeticUnlockSource.QUEST,
                "servercore:cosmetics/outfits/starlight_robe",
                "Ritual-style robe for players focusing on progression milestones."),
            definition(
                "merchant-cap",
                "Merchant Cap",
                CosmeticCategory.HEAD,
                CosmeticRarity.UNCOMMON,
                CosmeticUnlockSource.SUPPORTER,
                "servercore:cosmetics/head/merchant_cap",
                "Trading-themed head cosmetic for economy-focused players."),
            definition(
                "guild-banner-back",
                "Guild Banner",
                CosmeticCategory.BACK,
                CosmeticRarity.RARE,
                CosmeticUnlockSource.QUEST,
                "servercore:cosmetics/back/guild_banner",
                "Back accessory built for social identity and team events."),
                definition(
                        "pioneer-title",
                        "Pioneer",
                        CosmeticCategory.TITLE,
                        CosmeticRarity.RARE,
                        CosmeticUnlockSource.DEFAULT,
                        "servercore:cosmetics/titles/pioneer",
                        "Launch-era title for early community members."),
                definition(
                        "founder-crown",
                        "Founder Crown",
                        CosmeticCategory.HEAD,
                        CosmeticRarity.LEGENDARY,
                        CosmeticUnlockSource.FOUNDER,
                        "servercore:cosmetics/head/founder_crown",
                        "Original crown reserved for verified founders."),
                definition(
                        "ember-trail",
                        "Ember Trail",
                        CosmeticCategory.TRAIL,
                        CosmeticRarity.EPIC,
                        CosmeticUnlockSource.ACHIEVEMENT,
                        "servercore:cosmetics/trails/ember",
                        "A noncompetitive particle trail unlocked through achievements."),
                definition(
                        "void-nameplate",
                        "Void Nameplate",
                        CosmeticCategory.NAMEPLATE,
                        CosmeticRarity.EPIC,
                        CosmeticUnlockSource.EVENT,
                        "servercore:cosmetics/nameplates/void",
                        "An original event nameplate style."));
    }

    private static CosmeticDefinition definition(
            String id,
            String displayName,
            CosmeticCategory category,
            CosmeticRarity rarity,
            CosmeticUnlockSource unlockSource,
            String assetId,
            String description) {
        return new CosmeticDefinition(
                id,
                displayName,
                category,
                rarity,
                unlockSource,
                assetId,
                description,
                true,
                Set.of("launch", "original"));
    }
}
