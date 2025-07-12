package com.biggturkk.pohstorage;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.GameState;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.callback.ClientThread;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(
        name = "POH Storage",
        description = "Highlights items in your bank that can be stored in your POH",
        tags = {"bank", "storage", "poh", "utility", "inventory"}
)
public class POHStoragePlugin extends Plugin {
    @Inject
    private Client client;

    @Inject
    private POHStorageConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private POHStorageOverlay overlay;

    @Inject
    private POHStorageItemManager storageItemManager;

    @Inject
    private ItemManager itemManager;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private ClientThread clientThread;

    @Getter
    private final List<LineComponent> renderedLines = new ArrayList<>();

    private POHItemPanel itemPanel;
    private NavigationButton navButton;

    @Override
    protected void startUp() {
        log.info("POH Storage started!");
        log.info("Config greeting: {}", config.greeting());

        // Initialize with default message
        renderedLines.clear();
        renderedLines.add(LineComponent.builder()
                .left("POHStorage active")
                .right("✓")
                .build());

        // Initialize and add the item panel
        itemPanel = new POHItemPanel(this, storageItemManager, itemManager, clientThread);
        BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/icon.png"); // Update path
        navButton = NavigationButton.builder()
                .tooltip("POH Storage Panel")
                .icon(icon)
                .priority(5)
                .panel(itemPanel)
                .build();
        clientToolbar.addNavigation(navButton);

        overlayManager.add(overlay);
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(overlay);
        clientToolbar.removeNavigation(navButton);
        renderedLines.clear();
        log.info("POH Storage stopped!");
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (event.getItemContainer().getId() != 95) {
            return;
        }

        log.info("Bank updated: processing items");

        renderedLines.clear();
        ItemContainer bank = event.getItemContainer();
        Item[] items = bank.getItems();
        log.info("Bank contains {} items", items.length);

        boolean foundMatch = false;
        for (Item item : items) {
            int id = item.getId();
            if (id <= 0) {
                log.debug("Skipping invalid item ID: {}", id);
                continue;
            }

            String name = itemManager.getItemComposition(id).getName();
            if (name == null || name.isEmpty()) {
                name = "Unknown Item (ID " + id + ")";
            }
            List<StorageType> storages = storageItemManager.getStoragesForItem(id);
            if (!storages.isEmpty()) {
                String matchedStorage = storages.stream()
                        .map(StorageType::name)
                        .collect(Collectors.joining(", "));
                log.info(" → POH match: {} (ID {}) → {}", name, id, matchedStorage);
                renderedLines.add(LineComponent.builder()
                        .left(name)
                        .right(matchedStorage)
                        .build());
                foundMatch = true;
            } else {
                log.info("No POH storage for: {} (ID {})", name, id);
            }
        }

        if (!foundMatch) {
            log.info("No POH-storable items found in bank");
            renderedLines.add(LineComponent.builder()
                    .left("POHStorage active")
                    .right("✓")
                    .build());
        }
    }

    @Subscribe
    public void onWidgetClosed(WidgetClosed event) {
        if (event.getGroupId() == 12) {
            log.info("Bank interface closed");
            renderedLines.clear();
            renderedLines.add(LineComponent.builder()
                    .left("POHStorage active")
                    .right("✓")
                    .build());
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGIN_SCREEN
                || event.getGameState() == GameState.HOPPING
                || event.getGameState() == GameState.CONNECTION_LOST) {
            log.info("Clearing POHStorage renderings due to logout or lobby");
            renderedLines.clear();
        }
    }

    @Provides
    POHStorageConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(POHStorageConfig.class);
    }
}