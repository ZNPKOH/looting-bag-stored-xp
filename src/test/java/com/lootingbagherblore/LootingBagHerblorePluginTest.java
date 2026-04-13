package com.lootingbagherblore;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Launches RuneLite with this plugin loaded for development/testing.
 *
 * Run this from IntelliJ to start a full RuneLite client with the plugin active.
 * You log in with normal OSRS credentials (NOT Jagex Launcher).
 *
 * If you use a Jagex account, you'll need to set a regular password
 * via the OSRS website first for dev testing.
 */
public class LootingBagHerblorePluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(LootingBagHerblorePlugin.class);
        RuneLite.main(args);
    }
}
