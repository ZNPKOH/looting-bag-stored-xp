package com.lootingbagherblore;

import com.google.inject.Provides;
import com.lootingbagherblore.data.BagItem;
import com.lootingbagherblore.data.HerbData;
import com.lootingbagherblore.data.PotionRecipe;
import com.lootingbagherblore.data.SupplyTracker;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@PluginDescriptor(
    name = "Looting Bag Stored XP",
    description = "Tracks potential XP stored in your Looting Bag. Herblore with potion selector, vials & secondary tracking. Built for UIM.",
    tags = {"uim", "ultimate", "ironman", "herblore", "looting", "bag", "xp", "stored", "potion"}
)
public class LootingBagHerblorePlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private LootingBagHerbloreConfig config;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private LootingBagHerbloreOverlay overlay;

    private NavigationButton navButton;
    private LootingBagHerblorePanel panel;

    @Getter
    private final List<BagItem> bagHerbItems = new ArrayList<>();

    @Getter
    private final List<BagItem> inventoryHerbItems = new ArrayList<>();

    @Getter
    private final Map<HerbData, PotionRecipe> selectedRecipes = new ConcurrentHashMap<>();

    @Getter
    private final SupplyTracker supplyTracker = new SupplyTracker();

    private static final int LOOTING_BAG_CONTAINER_ID = 516;
    private static final int LOOTING_BAG_WIDGET_GROUP = 81;

    @Provides
    LootingBagHerbloreConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(LootingBagHerbloreConfig.class);
    }

    @Override
    protected void startUp()
    {
        panel = new LootingBagHerblorePanel(this, config);

        BufferedImage icon;
        try
        {
            icon = ImageUtil.loadImageResource(getClass(), "/panel_icon.png");
        }
        catch (Exception e)
        {
            log.warn("Could not load panel icon, using fallback", e);
            icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        }

        navButton = NavigationButton.builder()
            .tooltip("Looting Bag Stored XP")
            .icon(icon)
            .priority(10)
            .panel(panel)
            .build();

        clientToolbar.addNavigation(navButton);
        overlayManager.add(overlay);

        // Default: pick highest XP recipe for each herb
        for (HerbData herb : HerbData.values())
        {
            List<PotionRecipe> recipes = PotionRecipe.getRecipesForHerb(herb);
            if (!recipes.isEmpty())
            {
                PotionRecipe best = recipes.stream()
                    .max(Comparator.comparingDouble(PotionRecipe::getPotionXp))
                    .orElse(recipes.get(0));
                selectedRecipes.put(herb, best);
            }
        }
    }

    @Override
    protected void shutDown()
    {
        clientToolbar.removeNavigation(navButton);
        overlayManager.remove(overlay);
        bagHerbItems.clear();
        inventoryHerbItems.clear();
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        int containerId = event.getContainerId();

        if (containerId == LOOTING_BAG_CONTAINER_ID)
        {
            updateBagItems(event.getItemContainer());
            supplyTracker.updateFromBag(event.getItemContainer());
            panel.rebuild();
        }
        else if (containerId == InventoryID.INVENTORY.getId())
        {
            if (config.includeInventory())
            {
                updateInventoryItems(event.getItemContainer());
            }
            supplyTracker.updateFromInventory(event.getItemContainer());
            panel.rebuild();
        }
    }

    /**
     * When the looting bag widget opens, read its contents directly.
     * ItemContainerChanged only fires on actual changes, not on open.
     */
    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        if (event.getGroupId() == LOOTING_BAG_WIDGET_GROUP)
        {
            clientThread.invokeLater(() ->
            {
                ItemContainer container = client.getItemContainer(LOOTING_BAG_CONTAINER_ID);
                if (container != null)
                {
                    updateBagItems(container);
                    supplyTracker.updateFromBag(container);
                    panel.rebuild();
                }
            });
        }
    }

    private void updateBagItems(ItemContainer container)
    {
        bagHerbItems.clear();
        if (container == null) return;
        extractHerbItems(container, bagHerbItems);
    }

    private void updateInventoryItems(ItemContainer container)
    {
        inventoryHerbItems.clear();
        if (container == null) return;
        extractHerbItems(container, inventoryHerbItems);
    }

    private void extractHerbItems(ItemContainer container, List<BagItem> target)
    {
        Map<Integer, Integer> itemCounts = new LinkedHashMap<>();

        for (Item item : container.getItems())
        {
            if (item.getId() == -1) continue;
            HerbData herb = HerbData.fromAnyId(item.getId());
            if (herb != null)
            {
                itemCounts.merge(item.getId(), item.getQuantity(), Integer::sum);
            }
        }

        for (Map.Entry<Integer, Integer> entry : itemCounts.entrySet())
        {
            BagItem bagItem = BagItem.fromItemId(entry.getKey(), entry.getValue());
            if (bagItem != null)
            {
                target.add(bagItem);
            }
        }
    }

    public void setSelectedRecipe(HerbData herb, PotionRecipe recipe)
    {
        selectedRecipes.put(herb, recipe);
        panel.rebuild();
    }

    public List<BagItem> getAllHerbItems()
    {
        List<BagItem> all = new ArrayList<>(bagHerbItems);
        if (config.includeInventory())
        {
            all.addAll(inventoryHerbItems);
        }
        return all;
    }

    public double getTotalCleaningXp()
    {
        return getAllHerbItems().stream()
            .mapToDouble(BagItem::getCleaningXp)
            .sum();
    }

    public double getTotalPotionXp()
    {
        double total = 0;
        for (BagItem item : getAllHerbItems())
        {
            PotionRecipe recipe = selectedRecipes.get(item.getHerb());
            if (recipe != null)
            {
                total += item.getPotionXp(recipe);
            }
        }
        return total;
    }

    public double getGrandTotalXp()
    {
        return getTotalCleaningXp() + getTotalPotionXp();
    }

    /**
     * Load demo data for UI testing without logging in.
     * Simulates a UIM looting bag with herbs and supplies.
     */
    public void loadDemoData()
    {
        bagHerbItems.clear();
        inventoryHerbItems.clear();

        // Simulate looting bag contents
        bagHerbItems.add(new BagItem(HerbData.RANARR, HerbData.RANARR.getGrimyItemId(), 15, BagItem.ItemState.GRIMY));
        bagHerbItems.add(new BagItem(HerbData.RANARR, HerbData.RANARR.getCleanItemId(), 5, BagItem.ItemState.CLEAN));
        bagHerbItems.add(new BagItem(HerbData.SNAPDRAGON, HerbData.SNAPDRAGON.getGrimyItemId(), 8, BagItem.ItemState.GRIMY));
        bagHerbItems.add(new BagItem(HerbData.KWUARM, HerbData.KWUARM.getGrimyItemId(), 10, BagItem.ItemState.GRIMY));
        bagHerbItems.add(new BagItem(HerbData.TOADFLAX, HerbData.TOADFLAX.getUnfPotionId(), 6, BagItem.ItemState.UNFINISHED));
        bagHerbItems.add(new BagItem(HerbData.HARRALANDER, HerbData.HARRALANDER.getGrimyItemId(), 12, BagItem.ItemState.GRIMY));
        bagHerbItems.add(new BagItem(HerbData.CADANTINE, HerbData.CADANTINE.getGrimyItemId(), 7, BagItem.ItemState.GRIMY));
        bagHerbItems.add(new BagItem(HerbData.LANTADYME, HerbData.LANTADYME.getCleanItemId(), 4, BagItem.ItemState.CLEAN));

        // Simulate some supplies in the supply tracker
        supplyTracker.setDemoData(18, 5);

        log.info("Demo data loaded: {} herb items in bag", bagHerbItems.size());
        panel.rebuild();
    }

    /**
     * Clear demo data.
     */
    public void clearDemoData()
    {
        bagHerbItems.clear();
        inventoryHerbItems.clear();
        supplyTracker.clearDemoData();
        panel.rebuild();
    }
}
