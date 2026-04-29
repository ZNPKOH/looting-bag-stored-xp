package com.lootingbagherblore;

import com.lootingbagherblore.data.BagItem;
import com.lootingbagherblore.data.HerbData;
import com.lootingbagherblore.data.PotionRecipe;
import com.lootingbagherblore.data.SupplyTracker;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;

public class LootingBagHerblorePanel extends PluginPanel
{
    private static final DecimalFormat XP_FORMAT = new DecimalFormat("#,##0.#");
    private static final Color COLOR_GREEN = new Color(144, 238, 144);
    private static final Color COLOR_BLUE = new Color(100, 200, 255);
    private static final Color COLOR_RED = new Color(255, 100, 100);
    private static final Color COLOR_ORANGE = new Color(255, 176, 46);

    private static final int PANEL_WIDTH = PluginPanel.PANEL_WIDTH;

    private final LootingBagHerblorePlugin plugin;
    private final LootingBagHerbloreConfig config;

    private final JPanel contentPanel = new JPanel();

    public LootingBagHerblorePanel(LootingBagHerblorePlugin plugin, LootingBagHerbloreConfig config)
    {
        super(true); // wrap in scroll pane automatically

        this.plugin = plugin;
        this.config = config;

        setBorder(new EmptyBorder(8, 8, 8, 8));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setLayout(new BorderLayout());

        // Build the main vertical panel
        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Title
        wrapper.add(makeTitle("Looting Bag Stored XP"));
        wrapper.add(Box.createVerticalStrut(2));
        wrapper.add(makeLabel("Open looting bag to scan", ColorScheme.LIGHT_GRAY_COLOR, false));
        wrapper.add(Box.createVerticalStrut(10));

        // Content area (rebuilt on data change)
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        contentPanel.setAlignmentX(LEFT_ALIGNMENT);

        contentPanel.add(makeLabel("No herbs detected.", ColorScheme.LIGHT_GRAY_COLOR, false));

        wrapper.add(contentPanel);

        add(wrapper, BorderLayout.NORTH);
    }

    public void rebuild()
    {
        SwingUtilities.invokeLater(() ->
        {
            contentPanel.removeAll();

            List<BagItem> allItems = plugin.getAllHerbItems();

            if (allItems.isEmpty())
            {
                contentPanel.add(makeLabel("No herbs detected.", ColorScheme.LIGHT_GRAY_COLOR, false));
            }
            else
            {
                // Group by herb
                Map<HerbData, List<BagItem>> grouped = new LinkedHashMap<>();
                for (BagItem item : allItems)
                {
                    grouped.computeIfAbsent(item.getHerb(), k -> new ArrayList<>()).add(item);
                }

                for (Map.Entry<HerbData, List<BagItem>> entry : grouped.entrySet())
                {
                    contentPanel.add(buildHerbCard(entry.getKey(), entry.getValue()));
                    contentPanel.add(Box.createVerticalStrut(6));
                }

                // Supplies card
                contentPanel.add(buildSuppliesCard(allItems));
                contentPanel.add(Box.createVerticalStrut(8));

                // Totals card
                contentPanel.add(buildTotalsCard());
            }

            contentPanel.revalidate();
            contentPanel.repaint();
        });
    }

    private JPanel buildHerbCard(HerbData herb, List<BagItem> items)
    {
        JPanel card = makeCard();
        int totalQty = items.stream().mapToInt(BagItem::getQuantity).sum();

        // Header row: name + qty
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setAlignmentX(LEFT_ALIGNMENT);
        header.add(makeBoldLabel(herb.getDisplayName(), Color.WHITE), BorderLayout.WEST);
        header.add(makeBoldLabel("x" + totalQty, ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.EAST);
        card.add(header);

        // State breakdown
        StringBuilder states = new StringBuilder();
        for (BagItem item : items)
        {
            if (states.length() > 0) states.append(", ");
            states.append(item.getQuantity()).append(" ").append(item.getState().name().toLowerCase());
        }
        card.add(makeLabel(states.toString(), ColorScheme.LIGHT_GRAY_COLOR, false));
        card.add(Box.createVerticalStrut(4));

        // Cleaning XP
        double cleanXp = items.stream().mapToDouble(BagItem::getCleaningXp).sum();
        if (cleanXp > 0)
        {
            card.add(makeLabel("Cleaning: " + XP_FORMAT.format(cleanXp) + " xp", COLOR_GREEN, false));
        }

        // Potion selector
        List<PotionRecipe> recipes = PotionRecipe.getRecipesForHerb(herb);
        if (!recipes.isEmpty())
        {
            card.add(Box.createVerticalStrut(4));

            // Combo box — full width, own row
            JComboBox<PotionRecipe> combo = new JComboBox<>(recipes.toArray(new PotionRecipe[0]));
            combo.setRenderer(new PotionComboRenderer());
            combo.setMaximumSize(new Dimension(PANEL_WIDTH, 24));
            combo.setAlignmentX(LEFT_ALIGNMENT);

            PotionRecipe current = plugin.getSelectedRecipes().get(herb);
            if (current != null)
            {
                combo.setSelectedItem(current);
            }

            combo.addActionListener(e ->
            {
                PotionRecipe sel = (PotionRecipe) combo.getSelectedItem();
                if (sel != null)
                {
                    plugin.setSelectedRecipe(herb, sel);
                }
            });

            card.add(combo);
            card.add(Box.createVerticalStrut(4));

            // Selected potion info
            PotionRecipe selected = plugin.getSelectedRecipes().get(herb);
            if (selected != null)
            {
                double potionXp = selected.getPotionXp() * totalQty;
                card.add(makeLabel(
                    XP_FORMAT.format(potionXp) + " xp  (Lvl " + selected.getLevelRequired() + ")",
                    COLOR_BLUE, false));

                // Secondary status
                SupplyTracker tracker = plugin.getSupplyTracker();
                int have = tracker.getSecondaryCount(selected.getSecondaryItemId());
                int missing = tracker.secondariesMissing(selected, totalQty);
                String secName = SupplyTracker.getSecondaryName(selected.getSecondaryItemId());

                if (missing > 0)
                {
                    card.add(makeLabel(secName + ": need " + missing + " more", COLOR_RED, false));
                }
                else
                {
                    card.add(makeLabel(secName + ": " + have + " ok", COLOR_GREEN, false));
                }

                card.add(Box.createVerticalStrut(4));

                // Total for this herb
                double total = cleanXp + potionXp;
                JPanel totalRow = new JPanel(new BorderLayout());
                totalRow.setOpaque(false);
                totalRow.setAlignmentX(LEFT_ALIGNMENT);
                totalRow.add(makeBoldLabel("Total:", Color.YELLOW), BorderLayout.WEST);
                totalRow.add(makeBoldLabel(XP_FORMAT.format(total) + " xp", Color.YELLOW), BorderLayout.EAST);
                card.add(totalRow);
            }
        }

        return card;
    }

    private JPanel buildSuppliesCard(List<BagItem> allItems)
    {
        JPanel card = makeCard();
        SupplyTracker tracker = plugin.getSupplyTracker();

        card.add(makeBoldLabel("Supplies", COLOR_ORANGE));
        card.add(Box.createVerticalStrut(4));

        // Vials
        int needed = tracker.vialsNeeded(allItems);
        int have = tracker.getTotalVials();
        int missing = tracker.vialsMissing(allItems);

        JPanel vialRow = new JPanel(new BorderLayout());
        vialRow.setOpaque(false);
        vialRow.setAlignmentX(LEFT_ALIGNMENT);
        vialRow.add(makeLabel("Vials of water", ColorScheme.LIGHT_GRAY_COLOR, false), BorderLayout.WEST);

        if (missing > 0)
        {
            vialRow.add(makeLabel(have + "/" + needed + " (-" + missing + ")", COLOR_RED, false), BorderLayout.EAST);
        }
        else if (needed > 0)
        {
            vialRow.add(makeLabel(have + "/" + needed + " ok", COLOR_GREEN, false), BorderLayout.EAST);
        }
        else
        {
            vialRow.add(makeLabel("" + have, ColorScheme.LIGHT_GRAY_COLOR, false), BorderLayout.EAST);
        }

        card.add(vialRow);

        if (have > 0)
        {
            int bag = tracker.getVialsInBag();
            int inv = tracker.getVialsInInventory();
            StringBuilder loc = new StringBuilder("  ");
            if (bag > 0) loc.append(bag).append(" bag");
            if (inv > 0)
            {
                if (bag > 0) loc.append(", ");
                loc.append(inv).append(" inv");
            }
            card.add(makeLabel(loc.toString(), ColorScheme.LIGHT_GRAY_COLOR, false));
        }

        card.add(Box.createVerticalStrut(4));

        // Secondaries
        Set<Integer> shown = new HashSet<>();
        for (Map.Entry<HerbData, PotionRecipe> entry : plugin.getSelectedRecipes().entrySet())
        {
            int secId = entry.getValue().getSecondaryItemId();
            if (shown.contains(secId)) continue;
            shown.add(secId);

            int count = tracker.getSecondaryCount(secId);
            if (count > 0)
            {
                JPanel secRow = new JPanel(new BorderLayout());
                secRow.setOpaque(false);
                secRow.setAlignmentX(LEFT_ALIGNMENT);
                secRow.add(makeLabel(SupplyTracker.getSecondaryName(secId), ColorScheme.LIGHT_GRAY_COLOR, false), BorderLayout.WEST);
                secRow.add(makeLabel("" + count, COLOR_GREEN, false), BorderLayout.EAST);
                card.add(secRow);
            }
        }

        return card;
    }

    private JPanel buildTotalsCard()
    {
        JPanel card = makeCard();

        double cleanXp = plugin.getTotalCleaningXp();
        double potionXp = plugin.getTotalPotionXp();
        double total = cleanXp + potionXp;

        JPanel r1 = new JPanel(new BorderLayout());
        r1.setOpaque(false);
        r1.setAlignmentX(LEFT_ALIGNMENT);
        r1.add(makeLabel("Cleaning", ColorScheme.LIGHT_GRAY_COLOR, false), BorderLayout.WEST);
        r1.add(makeLabel(XP_FORMAT.format(cleanXp) + " xp", COLOR_GREEN, false), BorderLayout.EAST);
        card.add(r1);

        JPanel r2 = new JPanel(new BorderLayout());
        r2.setOpaque(false);
        r2.setAlignmentX(LEFT_ALIGNMENT);
        r2.add(makeLabel("Potions", ColorScheme.LIGHT_GRAY_COLOR, false), BorderLayout.WEST);
        r2.add(makeLabel(XP_FORMAT.format(potionXp) + " xp", COLOR_BLUE, false), BorderLayout.EAST);
        card.add(r2);

        card.add(Box.createVerticalStrut(4));

        JSeparator sep = new JSeparator();
        sep.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        sep.setAlignmentX(LEFT_ALIGNMENT);
        card.add(sep);
        card.add(Box.createVerticalStrut(4));

        JPanel r3 = new JPanel(new BorderLayout());
        r3.setOpaque(false);
        r3.setAlignmentX(LEFT_ALIGNMENT);
        r3.add(makeBoldLabel("TOTAL", Color.YELLOW), BorderLayout.WEST);
        r3.add(makeBoldLabel(XP_FORMAT.format(total) + " xp", Color.YELLOW), BorderLayout.EAST);
        card.add(r3);

        // Level projection
        int currentXp = plugin.getCurrentHerbloreXp();
        if (currentXp > 0)
        {
            card.add(Box.createVerticalStrut(8));
            JSeparator sep2 = new JSeparator();
            sep2.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
            sep2.setAlignmentX(LEFT_ALIGNMENT);
            card.add(sep2);
            card.add(Box.createVerticalStrut(4));

            int currentLevel = com.lootingbagherblore.data.XpTable.levelForXp(currentXp);
            double projectedXp = currentXp + total;
            int projectedLevel = com.lootingbagherblore.data.XpTable.levelForXp(projectedXp);
            int xpToNext = com.lootingbagherblore.data.XpTable.xpToNextLevel(projectedXp);

            JPanel curRow = new JPanel(new BorderLayout());
            curRow.setOpaque(false);
            curRow.setAlignmentX(LEFT_ALIGNMENT);
            curRow.add(makeLabel("Current", ColorScheme.LIGHT_GRAY_COLOR, false), BorderLayout.WEST);
            curRow.add(makeLabel("Lvl " + currentLevel + "  (" + XP_FORMAT.format(currentXp) + ")",
                ColorScheme.LIGHT_GRAY_COLOR, false), BorderLayout.EAST);
            card.add(curRow);

            JPanel projRow = new JPanel(new BorderLayout());
            projRow.setOpaque(false);
            projRow.setAlignmentX(LEFT_ALIGNMENT);
            projRow.add(makeBoldLabel("After bag", COLOR_GREEN), BorderLayout.WEST);
            int levelGain = projectedLevel - currentLevel;
            String gainStr = levelGain > 0 ? " (+" + levelGain + ")" : "";
            projRow.add(makeBoldLabel("Lvl " + projectedLevel + gainStr, COLOR_GREEN), BorderLayout.EAST);
            card.add(projRow);

            if (projectedLevel < com.lootingbagherblore.data.XpTable.MAX_LEVEL)
            {
                JPanel nextRow = new JPanel(new BorderLayout());
                nextRow.setOpaque(false);
                nextRow.setAlignmentX(LEFT_ALIGNMENT);
                nextRow.add(makeLabel("To next", ColorScheme.LIGHT_GRAY_COLOR, false), BorderLayout.WEST);
                nextRow.add(makeLabel(XP_FORMAT.format(xpToNext) + " xp",
                    ColorScheme.LIGHT_GRAY_COLOR, false), BorderLayout.EAST);
                card.add(nextRow);
            }
        }

        return card;
    }

    // -------- UI helpers --------

    private static JPanel makeCard()
    {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setBorder(new CompoundBorder(
            new MatteBorder(1, 1, 1, 1, ColorScheme.MEDIUM_GRAY_COLOR),
            new EmptyBorder(8, 8, 8, 8)
        ));
        card.setAlignmentX(LEFT_ALIGNMENT);
        return card;
    }

    private static JLabel makeTitle(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(FontManager.getRunescapeBoldFont());
        label.setForeground(Color.WHITE);
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private static JLabel makeBoldLabel(String text, Color color)
    {
        JLabel label = new JLabel(text);
        label.setFont(FontManager.getRunescapeBoldFont());
        label.setForeground(color);
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private static JLabel makeLabel(String text, Color color, boolean html)
    {
        JLabel label;
        if (html)
        {
            label = new JLabel("<html>" + text + "</html>");
        }
        else
        {
            label = new JLabel(text);
        }
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(color);
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private static class PotionComboRenderer extends DefaultListCellRenderer
    {
        private static final DecimalFormat FMT = new DecimalFormat("#,##0.#");

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
            int index, boolean isSelected, boolean cellHasFocus)
        {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof PotionRecipe)
            {
                PotionRecipe r = (PotionRecipe) value;
                setText(r.getDisplayName() + " (" + FMT.format(r.getPotionXp()) + "xp)");
            }
            return this;
        }
    }
}
