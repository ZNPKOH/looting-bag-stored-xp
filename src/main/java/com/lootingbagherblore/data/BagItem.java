package com.lootingbagherblore.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Represents a herb-related item found in the looting bag.
 * Tracks its state (grimy/clean/unf) and quantity.
 */
@Getter
@AllArgsConstructor
public class BagItem
{
    private final HerbData herb;
    private final int itemId;
    private final int quantity;
    private final ItemState state;

    public enum ItemState
    {
        GRIMY,
        CLEAN,
        UNFINISHED
    }

    public static BagItem fromItemId(int itemId, int quantity)
    {
        HerbData herb = HerbData.fromGrimyId(itemId);
        if (herb != null)
        {
            return new BagItem(herb, itemId, quantity, ItemState.GRIMY);
        }

        herb = HerbData.fromCleanId(itemId);
        if (herb != null)
        {
            return new BagItem(herb, itemId, quantity, ItemState.CLEAN);
        }

        herb = HerbData.fromUnfId(itemId);
        if (herb != null)
        {
            return new BagItem(herb, itemId, quantity, ItemState.UNFINISHED);
        }

        return null;
    }

    /**
     * Get cleaning XP for this item (only if grimy).
     */
    public double getCleaningXp()
    {
        if (state == ItemState.GRIMY)
        {
            return herb.getCleanXp() * quantity;
        }
        return 0;
    }

    /**
     * Get potion-making XP for this item using a specific recipe.
     */
    public double getPotionXp(PotionRecipe recipe)
    {
        return recipe.getPotionXp() * quantity;
    }

    /**
     * Get total XP (cleaning + potion) for this item using a specific recipe.
     */
    public double getTotalXp(PotionRecipe recipe)
    {
        return getCleaningXp() + getPotionXp(recipe);
    }

    public String getDisplayName()
    {
        switch (state)
        {
            case GRIMY:
                return "Grimy " + herb.getDisplayName();
            case CLEAN:
                return herb.getDisplayName();
            case UNFINISHED:
                return herb.getDisplayName() + " potion (unf)";
            default:
                return herb.getDisplayName();
        }
    }
}
