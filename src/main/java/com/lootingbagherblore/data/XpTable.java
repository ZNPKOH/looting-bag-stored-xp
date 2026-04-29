package com.lootingbagherblore.data;

/**
 * OSRS XP table — XP required to reach each level (1-126).
 */
public final class XpTable
{
    public static final int MAX_LEVEL = 126;
    public static final int[] XP_FOR_LEVEL = new int[MAX_LEVEL + 1];

    static
    {
        // Standard OSRS XP formula
        XP_FOR_LEVEL[1] = 0;
        double total = 0;
        for (int level = 1; level < MAX_LEVEL; level++)
        {
            total += Math.floor(level + 300 * Math.pow(2, level / 7.0));
            XP_FOR_LEVEL[level + 1] = (int) Math.floor(total / 4.0);
        }
    }

    private XpTable() {}

    /**
     * Get XP required to reach a level.
     */
    public static int xpForLevel(int level)
    {
        if (level < 1) return 0;
        if (level > MAX_LEVEL) return XP_FOR_LEVEL[MAX_LEVEL];
        return XP_FOR_LEVEL[level];
    }

    /**
     * Calculate the level for a given amount of XP.
     */
    public static int levelForXp(double xp)
    {
        for (int level = MAX_LEVEL; level >= 1; level--)
        {
            if (xp >= XP_FOR_LEVEL[level])
            {
                return level;
            }
        }
        return 1;
    }

    /**
     * XP remaining until next level.
     */
    public static int xpToNextLevel(double currentXp)
    {
        int currentLevel = levelForXp(currentXp);
        if (currentLevel >= MAX_LEVEL) return 0;
        return XP_FOR_LEVEL[currentLevel + 1] - (int) currentXp;
    }
}
