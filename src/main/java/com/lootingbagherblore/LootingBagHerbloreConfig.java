package com.lootingbagherblore;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("lootingbagherblore")
public interface LootingBagHerbloreConfig extends Config
{
    @ConfigItem(
        keyName = "showOverlay",
        name = "Show overlay",
        description = "Show total stored XP overlay on screen"
    )
    default boolean showOverlay()
    {
        return true;
    }

    @ConfigItem(
        keyName = "includeInventory",
        name = "Include inventory",
        description = "Also count herbs in your inventory"
    )
    default boolean includeInventory()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showCleaningXpSeparately",
        name = "Show cleaning XP",
        description = "Show cleaning XP as a separate line in the overlay"
    )
    default boolean showCleaningXpSeparately()
    {
        return true;
    }
}
