package com.lootingbagherblore.data;

import lombok.Getter;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;

import java.util.*;

/**
 * Tracks secondary ingredients and vials of water across
 * looting bag and inventory, and calculates what's missing.
 */
public class SupplyTracker
{
    @Getter
    private int vialsInBag = 0;
    @Getter
    private int vialsInInventory = 0;

    // Secondary item counts: itemId -> quantity (bag + inventory combined)
    @Getter
    private final Map<Integer, Integer> secondariesInBag = new LinkedHashMap<>();
    @Getter
    private final Map<Integer, Integer> secondariesInInventory = new LinkedHashMap<>();

    public void updateFromBag(ItemContainer container)
    {
        vialsInBag = 0;
        secondariesInBag.clear();
        if (container == null) return;
        scanContainer(container, true);
    }

    public void updateFromInventory(ItemContainer container)
    {
        vialsInInventory = 0;
        secondariesInInventory.clear();
        if (container == null) return;
        scanContainer(container, false);
    }

    /**
     * Update bag supplies from a flat itemId->quantity map, e.g. parsed
     * from a widget. Handles vials and secondaries.
     */
    public void updateFromBagItemCounts(Map<Integer, Integer> itemCounts)
    {
        vialsInBag = 0;
        secondariesInBag.clear();
        if (itemCounts == null) return;

        for (Map.Entry<Integer, Integer> entry : itemCounts.entrySet())
        {
            int itemId = entry.getKey();
            int qty = entry.getValue();

            if (itemId == ItemID.VIAL_OF_WATER)
            {
                vialsInBag += qty;
            }
            else if (isSecondaryIngredient(itemId))
            {
                secondariesInBag.merge(itemId, qty, Integer::sum);
            }
        }
    }

    private void scanContainer(ItemContainer container, boolean isBag)
    {
        for (Item item : container.getItems())
        {
            if (item.getId() == -1) continue;

            if (item.getId() == ItemID.VIAL_OF_WATER)
            {
                if (isBag)
                    vialsInBag += item.getQuantity();
                else
                    vialsInInventory += item.getQuantity();
                continue;
            }

            // Check if this item is a secondary for any potion recipe
            if (isSecondaryIngredient(item.getId()))
            {
                if (isBag)
                    secondariesInBag.merge(item.getId(), item.getQuantity(), Integer::sum);
                else
                    secondariesInInventory.merge(item.getId(), item.getQuantity(), Integer::sum);
            }
        }
    }

    private boolean isSecondaryIngredient(int itemId)
    {
        for (PotionRecipe recipe : PotionRecipe.values())
        {
            if (recipe.getSecondaryItemId() == itemId)
            {
                return true;
            }
        }
        return false;
    }

    public int getTotalVials()
    {
        return vialsInBag + vialsInInventory;
    }

    /**
     * Get total count of a specific secondary across bag + inventory.
     */
    public int getSecondaryCount(int secondaryItemId)
    {
        return secondariesInBag.getOrDefault(secondaryItemId, 0)
            + secondariesInInventory.getOrDefault(secondaryItemId, 0);
    }

    /**
     * Calculate how many vials of water are needed for all grimy + clean herbs.
     * Unfinished potions don't need vials.
     */
    public int vialsNeeded(List<BagItem> herbItems)
    {
        int needed = 0;
        for (BagItem item : herbItems)
        {
            if (item.getState() == BagItem.ItemState.GRIMY
                || item.getState() == BagItem.ItemState.CLEAN)
            {
                needed += item.getQuantity();
            }
        }
        return needed;
    }

    /**
     * How many vials are missing (needed - have).
     */
    public int vialsMissing(List<BagItem> herbItems)
    {
        return Math.max(0, vialsNeeded(herbItems) - getTotalVials());
    }

    /**
     * Calculate how many of a given potion can actually be made,
     * limited by available secondaries.
     */
    public int maxPotionsPossible(PotionRecipe recipe, int herbCount)
    {
        int secondaryAvailable = getSecondaryCount(recipe.getSecondaryItemId());
        return Math.min(herbCount, secondaryAvailable);
    }

    /**
     * How many secondaries are missing for a recipe given herb count.
     */
    public int secondariesMissing(PotionRecipe recipe, int herbCount)
    {
        int available = getSecondaryCount(recipe.getSecondaryItemId());
        return Math.max(0, herbCount - available);
    }

    /**
     * Get display name for a secondary item.
     */
    public static String getSecondaryName(int itemId)
    {
        // Map common secondaries to readable names
        switch (itemId)
        {
            case ItemID.EYE_OF_NEWT: return "Eye of newt";
            case ItemID.UNICORN_HORN_DUST: return "Unicorn horn dust";
            case ItemID.LIMPWURT_ROOT: return "Limpwurt root";
            case ItemID.ASHES: return "Ashes";
            case ItemID.VOLCANIC_ASH: return "Volcanic ash";
            case ItemID.RED_SPIDERS_EGGS: return "Red spiders' eggs";
            case ItemID.CHOCOLATE_DUST: return "Chocolate dust";
            case ItemID.GOAT_HORN_DUST: return "Goat horn dust";
            case ItemID.WHITE_BERRIES: return "White berries";
            case ItemID.SNAPE_GRASS: return "Snape grass";
            case ItemID.TOADS_LEGS: return "Toad's legs";
            case ItemID.CRUSHED_NEST: return "Crushed nest";
            case ItemID.MORT_MYRE_FUNGUS: return "Mort myre fungus";
            case ItemID.KEBBIT_TEETH_DUST: return "Kebbit teeth dust";
            case ItemID.DRAGON_SCALE_DUST: return "Dragon scale dust";
            case ItemID.POTATO_CACTUS: return "Potato cactus";
            case ItemID.WINE_OF_ZAMORAK: return "Wine of zamorak";
            case ItemID.JANGERBERRIES: return "Jangerberries";
            default: return "Unknown (ID: " + itemId + ")";
        }
    }
}
