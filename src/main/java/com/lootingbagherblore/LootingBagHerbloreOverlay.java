package com.lootingbagherblore;

import com.lootingbagherblore.data.SupplyTracker;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.text.DecimalFormat;

public class LootingBagHerbloreOverlay extends OverlayPanel
{
    private static final DecimalFormat XP_FORMAT = new DecimalFormat("#,##0.#");
    private static final Color COLOR_GREEN = new Color(144, 238, 144);
    private static final Color COLOR_BLUE = new Color(100, 200, 255);
    private static final Color COLOR_RED = new Color(255, 100, 100);

    private final LootingBagHerblorePlugin plugin;
    private final LootingBagHerbloreConfig config;

    @Inject
    public LootingBagHerbloreOverlay(LootingBagHerblorePlugin plugin, LootingBagHerbloreConfig config)
    {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setPriority(OverlayPriority.LOW);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.showOverlay())
        {
            return null;
        }

        double totalXp = plugin.getGrandTotalXp();
        if (totalXp <= 0)
        {
            return null;
        }

        panelComponent.getChildren().add(TitleComponent.builder()
            .text("Bag Stored XP")
            .color(COLOR_GREEN)
            .build());

        if (config.showCleaningXpSeparately())
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Cleaning:")
                .right(XP_FORMAT.format(plugin.getTotalCleaningXp()))
                .rightColor(COLOR_GREEN)
                .build());

            panelComponent.getChildren().add(LineComponent.builder()
                .left("Potions:")
                .right(XP_FORMAT.format(plugin.getTotalPotionXp()))
                .rightColor(COLOR_BLUE)
                .build());
        }

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Total:")
            .right(XP_FORMAT.format(totalXp) + " XP")
            .rightColor(Color.YELLOW)
            .build());

        // Vials status
        SupplyTracker tracker = plugin.getSupplyTracker();
        int vialsMissing = tracker.vialsMissing(plugin.getAllHerbItems());
        if (vialsMissing > 0)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Vials needed:")
                .right(String.valueOf(vialsMissing))
                .rightColor(COLOR_RED)
                .build());
        }

        return super.render(graphics);
    }
}
