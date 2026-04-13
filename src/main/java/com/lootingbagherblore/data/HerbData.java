package com.lootingbagherblore.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ItemID;

import java.util.HashMap;
import java.util.Map;

@Getter
@RequiredArgsConstructor
public enum HerbData
{
    GUAM(
        ItemID.GRIMY_GUAM_LEAF, ItemID.GUAM_LEAF, ItemID.GUAM_POTION_UNF,
        "Guam leaf", 3, 2.5
    ),
    MARRENTILL(
        ItemID.GRIMY_MARRENTILL, ItemID.MARRENTILL, ItemID.MARRENTILL_POTION_UNF,
        "Marrentill", 5, 3.8
    ),
    TARROMIN(
        ItemID.GRIMY_TARROMIN, ItemID.TARROMIN, ItemID.TARROMIN_POTION_UNF,
        "Tarromin", 11, 5.0
    ),
    HARRALANDER(
        ItemID.GRIMY_HARRALANDER, ItemID.HARRALANDER, ItemID.HARRALANDER_POTION_UNF,
        "Harralander", 20, 6.3
    ),
    RANARR(
        ItemID.GRIMY_RANARR_WEED, ItemID.RANARR_WEED, ItemID.RANARR_POTION_UNF,
        "Ranarr weed", 25, 7.5
    ),
    TOADFLAX(
        ItemID.GRIMY_TOADFLAX, ItemID.TOADFLAX, ItemID.TOADFLAX_POTION_UNF,
        "Toadflax", 30, 8.0
    ),
    IRIT(
        ItemID.GRIMY_IRIT_LEAF, ItemID.IRIT_LEAF, ItemID.IRIT_POTION_UNF,
        "Irit leaf", 40, 8.8
    ),
    AVANTOE(
        ItemID.GRIMY_AVANTOE, ItemID.AVANTOE, ItemID.AVANTOE_POTION_UNF,
        "Avantoe", 48, 10.0
    ),
    KWUARM(
        ItemID.GRIMY_KWUARM, ItemID.KWUARM, ItemID.KWUARM_POTION_UNF,
        "Kwuarm", 54, 11.3
    ),
    SNAPDRAGON(
        ItemID.GRIMY_SNAPDRAGON, ItemID.SNAPDRAGON, ItemID.SNAPDRAGON_POTION_UNF,
        "Snapdragon", 59, 11.8
    ),
    CADANTINE(
        ItemID.GRIMY_CADANTINE, ItemID.CADANTINE, ItemID.CADANTINE_POTION_UNF,
        "Cadantine", 65, 12.5
    ),
    LANTADYME(
        ItemID.GRIMY_LANTADYME, ItemID.LANTADYME, ItemID.LANTADYME_POTION_UNF,
        "Lantadyme", 67, 13.1
    ),
    DWARF_WEED(
        ItemID.GRIMY_DWARF_WEED, ItemID.DWARF_WEED, ItemID.DWARF_WEED_POTION_UNF,
        "Dwarf weed", 70, 13.8
    ),
    TORSTOL(
        ItemID.GRIMY_TORSTOL, ItemID.TORSTOL, ItemID.TORSTOL_POTION_UNF,
        "Torstol", 75, 15.0
    );

    private final int grimyItemId;
    private final int cleanItemId;
    private final int unfPotionId;
    private final String displayName;
    private final int cleanLevel;
    private final double cleanXp;

    private static final Map<Integer, HerbData> BY_GRIMY_ID = new HashMap<>();
    private static final Map<Integer, HerbData> BY_CLEAN_ID = new HashMap<>();
    private static final Map<Integer, HerbData> BY_UNF_ID = new HashMap<>();

    static
    {
        for (HerbData herb : values())
        {
            BY_GRIMY_ID.put(herb.grimyItemId, herb);
            BY_CLEAN_ID.put(herb.cleanItemId, herb);
            BY_UNF_ID.put(herb.unfPotionId, herb);
        }
    }

    public static HerbData fromGrimyId(int itemId)
    {
        return BY_GRIMY_ID.get(itemId);
    }

    public static HerbData fromCleanId(int itemId)
    {
        return BY_CLEAN_ID.get(itemId);
    }

    public static HerbData fromUnfId(int itemId)
    {
        return BY_UNF_ID.get(itemId);
    }

    public static HerbData fromAnyId(int itemId)
    {
        HerbData herb = fromGrimyId(itemId);
        if (herb != null) return herb;
        herb = fromCleanId(itemId);
        if (herb != null) return herb;
        return fromUnfId(itemId);
    }

    public boolean isGrimy(int itemId)
    {
        return itemId == grimyItemId;
    }

    public boolean isClean(int itemId)
    {
        return itemId == cleanItemId;
    }

    public boolean isUnf(int itemId)
    {
        return itemId == unfPotionId;
    }
}
