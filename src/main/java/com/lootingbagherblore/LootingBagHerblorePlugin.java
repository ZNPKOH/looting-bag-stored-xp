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
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
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
    private static final int LOOTING_BAG_ITEMS_CHILD = 5;

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
        if (event.getGroupId() == LOOTING_BAG_WIDGET_GROUP)
        {
            log.debug("Looting bag widget loaded");
            clientThread.invokeLater(this::tryReadBag);
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        // Poll the looting bag widget every tick; if it exists, read items.
        Widget bagRoot = client.getWidget(LOOTING_BAG_WIDGET_GROUP, 0);
        if (bagRoot != null && !bagRoot.isHidden())
        {
            tryReadBag();
        }
    }

    /**
     * Read looting bag contents — try ItemContainer first, fallback to widget tree scan.
     */
    private void tryReadBag()
    {
        // Method 1: try the item container
        ItemContainer container = client.getItemContainer(LOOTING_BAG_CONTAINER_ID);
        if (container != null)
        {
            boolean hasItems = false;
            for (Item it : container.getItems())
            {
                if (it.getId() != -1)
                {
                    hasItems = true;
                    break;
                }
            }
            if (hasItems)
            {
                log.debug("Reading looting bag from ItemContainer 516");
                updateBagFromContainer(container);
                panel.rebuild();
                return;
            }
        }

        // Method 2: scan the widget tree for items
        List<Widget> foundItems = new ArrayList<>();
        // Try several known/likely children of the looting bag widget group
        for (int childId = 0; childId < 30; childId++)
        {
            Widget w = client.getWidget(LOOTING_BAG_WIDGET_GROUP, childId);
            if (w == null) continue;
            collectItemWidgets(w, foundItems);
        }

        if (foundItems.isEmpty())
        {
            return;
        }

        log.debug("Found {} item widgets in looting bag widget tree", foundItems.size());
        updateBagFromWidget(foundItems.toArray(new Widget[0]));
        panel.rebuild();
    }

    /**
     * Recursively collect any widget that has an itemId set.
     */
    private void collectItemWidgets(Widget w, List<Widget> out)
    {
        if (w == null) return;
        if (w.getItemId() > 0 && w.getItemQuantity() > 0)
        {
            out.add(w);
        }
        Widget[] dynamic = w.getDynamicChildren();
        if (dynamic != null)
        {
            for (Widget c : dynamic) collectItemWidgets(c, out);
        }
        Widget[] statics = w.getStaticChildren();
        if (statics != null)
        {
            for (Widget c : statics) collectItemWidgets(c, out);
        }
        Widget[] nested = w.getNestedChildren();
        if (nested != null)
        {
            for (Widget c : nested) collectItemWidgets(c, out);
        }
    }

    private void updateBagFromContainer(ItemContainer container)
    {
        bagHerbItems.clear();
        if (container == null) return;
        extractHerbItems(container.getItems(), bagHerbItems);
        supplyTracker.updateFromBag(container);
    }

    /**
     * Parse items from looting bag widget. Each child widget has itemId and itemQuantity.
     */
    private void updateBagFromWidget(Widget[] items)
    {
        bagHerbItems.clear();
        Map<Integer, Integer> itemCounts = new LinkedHashMap<>();
        int vials = 0;
        Map<Integer, Integer> secondaries = new LinkedHashMap<>();

        for (Widget w : items)
        {
            if (w == null) continue;
            int itemId = w.getItemId();
            int qty = w.getItemQuantity();
            if (itemId <= 0 || qty <= 0) continue;

            HerbData herb = HerbData.fromAnyId(itemId);
            if (herb != null)
            {
                itemCounts.merge(itemId, qty, Integer::sum);
            }
            else if (itemId == net.runelite.api.ItemID.VIAL_OF_WATER)
            {
                vials += qty;
            }
            else if (isSecondaryIngredient(itemId))
            {
                secondaries.merge(itemId, qty, Integer::sum);
            }
        }

        for (Map.Entry<Integer, Integer> entry : itemCounts.entrySet())
        {
            BagItem bagItem = BagItem.fromItemId(entry.getKey(), entry.getValue());
            if (bagItem != null) bagHerbItems.add(bagItem);
        }

        supplyTracker.setBagSupplies(vials, secondaries);
    }

    private static boolean isSecondaryIngredient(int itemId)
    {
        for (PotionRecipe r : PotionRecipe.values())
        {
            if (r.getSecondaryItemId() == itemId) return true;
        }
        return false;
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
            HerbData herb = HerbData.fromAnyId(item.getId());
            if (herb != null)
            {
                itemCounts.merge(item.getId(), item.getQuantity(), Integer::sum);
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
}
