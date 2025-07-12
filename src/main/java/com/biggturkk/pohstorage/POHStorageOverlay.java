package com.biggturkk.pohstorage;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.PanelComponent;

import javax.inject.Inject;
import java.awt.Dimension;
import java.awt.Graphics2D;

@Slf4j
public class POHStorageOverlay extends Overlay
{
    private final POHStoragePlugin plugin;
    private final PanelComponent panelComponent = new PanelComponent();

    @Inject
    public POHStorageOverlay(POHStoragePlugin plugin)
    {
        log.info("✅ POHStorageOverlay initialized");
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    private long lastRenderLog = 0;
    private static final long LOG_COOLDOWN_MS = 10000; // 10 seconds

    private boolean shouldLog(long last)
    {
        return System.currentTimeMillis() - last > LOG_COOLDOWN_MS;
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (plugin.getRenderedLines().isEmpty())
        {
            return null;
        }

        if (shouldLog(lastRenderLog))
        {
            log.info("Rendering overlay with {} lines", plugin.getRenderedLines().size());
            lastRenderLog = System.currentTimeMillis();
        }

        panelComponent.getChildren().clear();
        panelComponent.getChildren().addAll(plugin.getRenderedLines());
        panelComponent.setPreferredSize(new Dimension(250, plugin.getRenderedLines().size() * 20));
        return panelComponent.render(graphics);
    }
}