package com.lootingbagherblore.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ItemID;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@RequiredArgsConstructor
public enum PotionRecipe
{
    // === Guam ===
    ATTACK_POTION(
        HerbData.GUAM, ItemID.EYE_OF_NEWT, ItemID.ATTACK_POTION4,
        "Attack potion", 3, 25.0
    ),

    // === Marrentill ===
    ANTIPOISON(
        HerbData.MARRENTILL, ItemID.UNICORN_HORN_DUST, ItemID.ANTIPOISON4,
        "Antipoison", 5, 37.5
    ),

    // === Tarromin ===
    STRENGTH_POTION(
        HerbData.TARROMIN, ItemID.LIMPWURT_ROOT, ItemID.STRENGTH_POTION4,
        "Strength potion", 12, 50.0
    ),
    SERUM_207(
        HerbData.TARROMIN, ItemID.ASHES, ItemID.SERUM_207_4,
        "Serum 207", 15, 50.0
    ),

    // === Harralander ===
    COMPOST_POTION(
        HerbData.HARRALANDER, ItemID.VOLCANIC_ASH, ItemID.COMPOST_POTION4,
        "Compost potion", 21, 60.0
    ),
    RESTORE_POTION(
        HerbData.HARRALANDER, ItemID.RED_SPIDERS_EGGS, ItemID.RESTORE_POTION4,
        "Restore potion", 22, 62.5
    ),
    ENERGY_POTION(
        HerbData.HARRALANDER, ItemID.CHOCOLATE_DUST, ItemID.ENERGY_POTION4,
        "Energy potion", 26, 67.5
    ),
    COMBAT_POTION(
        HerbData.HARRALANDER, ItemID.GOAT_HORN_DUST, ItemID.COMBAT_POTION4,
        "Combat potion", 36, 84.0
    ),

    // === Ranarr ===
    DEFENCE_POTION(
        HerbData.RANARR, ItemID.WHITE_BERRIES, ItemID.DEFENCE_POTION4,
        "Defence potion", 30, 75.0
    ),
    PRAYER_POTION(
        HerbData.RANARR, ItemID.SNAPE_GRASS, ItemID.PRAYER_POTION4,
        "Prayer potion", 38, 87.5
    ),

    // === Toadflax ===
    AGILITY_POTION(
        HerbData.TOADFLAX, ItemID.TOADS_LEGS, ItemID.AGILITY_POTION4,
        "Agility potion", 34, 80.0
    ),
    SARADOMIN_BREW(
        HerbData.TOADFLAX, ItemID.CRUSHED_NEST, ItemID.SARADOMIN_BREW4,
        "Saradomin brew", 81, 180.0
    ),

    // === Irit ===
    SUPER_ATTACK(
        HerbData.IRIT, ItemID.EYE_OF_NEWT, ItemID.SUPER_ATTACK4,
        "Super attack", 45, 100.0
    ),
    SUPERANTIPOISON(
        HerbData.IRIT, ItemID.UNICORN_HORN_DUST, ItemID.SUPERANTIPOISON4,
        "Superantipoison", 48, 106.3
    ),

    // === Avantoe ===
    FISHING_POTION(
        HerbData.AVANTOE, ItemID.SNAPE_GRASS, ItemID.FISHING_POTION4,
        "Fishing potion", 50, 112.5
    ),
    SUPER_ENERGY(
        HerbData.AVANTOE, ItemID.MORT_MYRE_FUNGUS, ItemID.SUPER_ENERGY4,
        "Super energy", 52, 117.5
    ),
    HUNTER_POTION(
        HerbData.AVANTOE, ItemID.KEBBIT_TEETH_DUST, ItemID.HUNTER_POTION4,
        "Hunter potion", 53, 120.0
    ),

    // === Kwuarm ===
    SUPER_STRENGTH(
        HerbData.KWUARM, ItemID.LIMPWURT_ROOT, ItemID.SUPER_STRENGTH4,
        "Super strength", 55, 125.0
    ),

    // === Snapdragon ===
    SUPER_RESTORE(
        HerbData.SNAPDRAGON, ItemID.RED_SPIDERS_EGGS, ItemID.SUPER_RESTORE4,
        "Super restore", 63, 142.5
    ),

    // === Cadantine ===
    SUPER_DEFENCE(
        HerbData.CADANTINE, ItemID.WHITE_BERRIES, ItemID.SUPER_DEFENCE4,
        "Super defence", 66, 150.0
    ),

    // === Lantadyme ===
    ANTIFIRE_POTION(
        HerbData.LANTADYME, ItemID.DRAGON_SCALE_DUST, ItemID.ANTIFIRE_POTION4,
        "Antifire potion", 69, 157.5
    ),
    MAGIC_POTION(
        HerbData.LANTADYME, ItemID.POTATO_CACTUS, ItemID.MAGIC_POTION4,
        "Magic potion", 76, 172.5
    ),

    // === Dwarf weed ===
    RANGING_POTION(
        HerbData.DWARF_WEED, ItemID.WINE_OF_ZAMORAK, ItemID.RANGING_POTION4,
        "Ranging potion", 72, 162.5
    ),

    // === Torstol ===
    ZAMORAK_BREW(
        HerbData.TORSTOL, ItemID.JANGERBERRIES, ItemID.ZAMORAK_BREW4,
        "Zamorak brew", 78, 175.0
    );

    private final HerbData herb;
    private final int secondaryItemId;
    private final int resultPotionId;
    private final String displayName;
    private final int levelRequired;
    private final double potionXp;

    private static final Map<HerbData, List<PotionRecipe>> BY_HERB = new HashMap<>();

    static
    {
        for (PotionRecipe recipe : values())
        {
            BY_HERB.computeIfAbsent(recipe.herb, k -> new ArrayList<>()).add(recipe);
        }
    }

    public static List<PotionRecipe> getRecipesForHerb(HerbData herb)
    {
        return BY_HERB.getOrDefault(herb, List.of());
    }

    /**
     * Total XP for processing a grimy herb into this potion.
     * Cleaning XP + Potion mixing XP (unf potions give 0 XP).
     */
    public double getTotalXpFromGrimy()
    {
        return herb.getCleanXp() + potionXp;
    }

    /**
     * Total XP from a clean herb into this potion.
     * Just the potion mixing XP (unf gives 0).
     */
    public double getTotalXpFromClean()
    {
        return potionXp;
    }

    /**
     * XP from an unfinished potion into this potion.
     */
    public double getTotalXpFromUnf()
    {
        return potionXp;
    }
}
