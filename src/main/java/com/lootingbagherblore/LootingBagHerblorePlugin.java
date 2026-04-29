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
import net.runelite.api.Skill;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.game.ItemManager;
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
    private ItemManager itemManager;

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
            updateBagFromContainer(event.getItemContainer());
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

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        if (event.getGroupId() == InterfaceID.LOOTING_BAG)
        {
            updateBagFromWidget();
        }
    }

    private int lastBagHash = 0;

    @Subscribe
    public void onGameTick(GameTick event)
    {
        // Always check the item container — it may have been populated even
        // if no ItemContainerChanged event fired for us.
        ItemContainer container = client.getItemContainer(LOOTING_BAG_CONTAINER_ID);
        if (container != null)
        {
            int hash = computeContainerHash(container);
            if (hash != 0 && hash != lastBagHash)
            {
                lastBagHash = hash;
                updateBagFromContainer(container);
                panel.rebuild();
                return;
            }
            else if (hash != 0)
            {
                // Container has items but unchanged since last update — skip rebuild
                return;
            }
        }

        // Fallback: poll the looting bag widget if open.
        updateBagFromWidget();
    }

    private int computeContainerHash(ItemContainer container)
    {
        int hash = 0;
        boolean any = false;
        for (Item it : container.getItems())
        {
            if (it.getId() == -1) continue;
            any = true;
            hash = hash * 31 + it.getId();
            hash = hash * 31 + it.getQuantity();
        }
        return any ? hash : 0;
    }

    /**
     * Read the looting bag widget. Walks the widget tree of the entire
     * looting bag group (not just one child) to find item-bearing widgets.
     */
    private void updateBagFromWidget()
    {
        // Check if the looting bag interface is open at all
        Widget root = client.getWidget(InterfaceID.LOOTING_BAG, 0);
        if (root == null || root.isHidden())
        {
            return;
        }

        Map<Integer, Integer> itemCounts = new LinkedHashMap<>();
        Set<Widget> seen = Collections.newSetFromMap(new IdentityHashMap<>());

        // Scan ALL children of the looting bag group, not just the inventory child.
        // Items might be in dynamic children of root or sibling widgets.
        for (int childId = 0; childId < 50; childId++)
        {
            Widget w = client.getWidget(InterfaceID.LOOTING_BAG, childId);
            if (w == null) continue;
            collectWidgetItems(w, itemCounts, seen);
        }

        if (itemCounts.isEmpty())
        {
            return;
        }

        // Skip rebuild if items haven't changed
        int hash = 0;
        for (Map.Entry<Integer, Integer> e : itemCounts.entrySet())
        {
            hash = hash * 31 + e.getKey();
            hash = hash * 31 + e.getValue();
        }
        if (hash == lastBagHash) return;
        lastBagHash = hash;

        bagHerbItems.clear();
        for (Map.Entry<Integer, Integer> entry : itemCounts.entrySet())
        {
            BagItem bagItem = BagItem.fromItemId(entry.getKey(), entry.getValue());
            if (bagItem != null)
            {
                bagHerbItems.add(bagItem);
            }
        }

        supplyTracker.updateFromBagItemCounts(itemCounts);
        panel.rebuild();
    }

    private void collectWidgetItems(Widget widget, Map<Integer, Integer> itemCounts, Set<Widget> seen)
    {
        if (widget == null || !seen.add(widget))
        {
            return;
        }

        int itemId = widget.getItemId();
        int quantity = widget.getItemQuantity();
        if (itemId > 0)
        {
            int canonicalId = itemManager.canonicalize(itemId);
            itemCounts.merge(canonicalId, Math.max(quantity, 1), Integer::sum);
        }

        collectWidgetItems(widget.getChildren(), itemCounts, seen);
        collectWidgetItems(widget.getDynamicChildren(), itemCounts, seen);
        collectWidgetItems(widget.getStaticChildren(), itemCounts, seen);
        collectWidgetItems(widget.getNestedChildren(), itemCounts, seen);
    }

    private void collectWidgetItems(Widget[] widgets, Map<Integer, Integer> itemCounts, Set<Widget> seen)
    {
        if (widgets == null) return;
        for (Widget child : widgets)
        {
            collectWidgetItems(child, itemCounts, seen);
        }
    }

    private void updateBagFromContainer(ItemContainer container)
    {
        bagHerbItems.clear();
        if (container == null) return;
        extractHerbItems(container.getItems(), bagHerbItems);

        // Canonicalize container item IDs for supply tracking too
        Map<Integer, Integer> canonical = new LinkedHashMap<>();
        for (Item item : container.getItems())
        {
            if (item.getId() == -1) continue;
            int id = itemManager.canonicalize(item.getId());
            canonical.merge(id, item.getQuantity(), Integer::sum);
        }
        supplyTracker.updateFromBagItemCounts(canonical);
    }

    private void updateInventoryItems(ItemContainer container)
    {
        inventoryHerbItems.clear();
        if (container == null) return;
        extractHerbItems(container.getItems(), inventoryHerbItems);
    }

    private void extractHerbItems(Item[] items, List<BagItem> target)
    {
        Map<Integer, Integer> itemCounts = new LinkedHashMap<>();

        for (Item item : items)
        {
            if (item.getId() == -1) continue;
            int canonicalId = itemManager.canonicalize(item.getId());
            HerbData herb = HerbData.fromAnyId(canonicalId);
            if (herb != null)
            {
                itemCounts.merge(canonicalId, item.getQuantity(), Integer::sum);
            }
        }

        for (Map.Entry<Integer, Integer> entry : itemCounts.entrySet())
        {
            BagItem bagItem = BagItem.fromItemId(entry.getKey(), entry.getValue());
            if (bagItem != null) target.add(bagItem);
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
     * Current Herblore XP from the player's stats. Returns 0 if not logged in.
     */
    public int getCurrentHerbloreXp()
    {
        try
        {
            return client.getSkillExperience(Skill.HERBLORE);
        }
        catch (Exception e)
        {
            return 0;
        }
    }

    /**
     * Current Herblore level.
     */
    public int getCurrentHerbloreLevel()
    {
        try
        {
            return client.getRealSkillLevel(Skill.HERBLORE);
        }
        catch (Exception e)
        {
            return 1;
        }
    }
}
