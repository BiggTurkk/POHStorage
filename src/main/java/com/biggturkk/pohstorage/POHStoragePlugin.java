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
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.GameState;
import net.runelite.api.widgets.Widget;
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
import java.util.*;

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

    @Getter
    private final Set<Integer> pohStoredItems = new HashSet<>();

    @Getter
    private final Set<Integer> bankAndInventoryItems = new HashSet<>();

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
        BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/icon.png");
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
        pohStoredItems.clear();
        bankAndInventoryItems.clear();
        itemPanel.clearIconCache(); // Clear icon cache on shutdown
        log.info("POH Storage stopped!");
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        int containerId = event.getItemContainer().getId();
        if (containerId == 95 || containerId == 93) { // Bank (95) or Inventory (93)
            log.info("Bank or inventory updated: processing items");
            updateBankAndInventoryItems();
            clientThread.invokeLater(itemPanel::populateTree); // Refresh panel
        }
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event) {
        if (event.getGroupId() == 116) { // Costume Room widget group
            log.info("POH storage interface loaded");
            updatePOHStoredItems();
            clientThread.invokeLater(itemPanel::populateTree); // Refresh panel
        }
    }

    @Subscribe
    public void onWidgetClosed(WidgetClosed event) {
        if (event.getGroupId() == 12 || event.getGroupId() == 116) {
            log.info("Bank or POH interface closed");
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
            log.info("Clearing POHStorage data due to logout or lobby");
            renderedLines.clear();
            pohStoredItems.clear();
            bankAndInventoryItems.clear();
            clientThread.invokeLater(itemPanel::populateTree);
        }
    }

    private void updatePOHStoredItems() {
        pohStoredItems.clear();
        // Map StorageType to widget child IDs (placeholders, verify in-game)
        Map<StorageType, Integer> storageWidgetIds = new HashMap<>();
        storageWidgetIds.put(StorageType.ARMOUR_CASE, 10);
        storageWidgetIds.put(StorageType.TOY_BOX, 11);
        storageWidgetIds.put(StorageType.MAGIC_WARDROBE, 12);
        storageWidgetIds.put(StorageType.CAPE_RACK, 13);
        storageWidgetIds.put(StorageType.TREASURE_CHEST, 14);
        storageWidgetIds.put(StorageType.FANCY_DRESS_BOX, 15);

        // Log all widgets in group 116 for debugging
        Widget costumeRoomWidget = client.getWidget(116, 0);
        if (costumeRoomWidget != null) {
            log.info("Costume Room (group 116) widgets found. Dumping child IDs:");
            Widget[] children = costumeRoomWidget.getChildren();
            if (children != null) {
                for (int i = 0; i < children.length; i++) {
                    if (children[i] != null) {
                        log.info("Widget 116,{}: ID={}, ItemID={}", i, children[i].getId(), children[i].getItemId());
                    }
                }
            }
        } else {
            log.warn("Costume Room widget (group 116) not found");
        }

        for (StorageType type : StorageType.values()) {
            Integer widgetId = storageWidgetIds.get(type);
            if (widgetId != null) {
                Widget storageWidget = client.getWidget(116, widgetId);
                if (storageWidget != null) {
                    Widget[] items = storageWidget.getChildren();
                    if (items != null) {
                        for (Widget itemWidget : items) {
                            if (itemWidget != null && itemWidget.getItemId() > 0) {
                                pohStoredItems.add(itemWidget.getItemId());
                            }
                        }
                    } else {
                        log.warn("No items found for storage type {} (widget 116,{})", type, widgetId);
                    }
                } else {
                    log.warn("Widget not found for storage type {} (widget 116,{})", type, widgetId);
                }
            }
        }
        log.debug("Updated POH stored items: {}", pohStoredItems);
    }

    private void updateBankAndInventoryItems() {
        bankAndInventoryItems.clear();
        // Check bank (container ID 95)
        ItemContainer bank = client.getItemContainer(95);
        if (bank != null) {
            for (Item item : bank.getItems()) {
                if (item.getId() > 0) {
                    bankAndInventoryItems.add(item.getId());
                }
            }
        }
        // Check inventory (container ID 93)
        ItemContainer inventory = client.getItemContainer(93);
        if (inventory != null) {
            for (Item item : inventory.getItems()) {
                if (item.getId() > 0) {
                    bankAndInventoryItems.add(item.getId());
                }
            }
        }
        log.debug("Updated bank and inventory items: {}", bankAndInventoryItems);
    }

    public boolean isItemInPOHStorage(int itemId) {
        return pohStoredItems.contains(itemId);
    }

    public boolean isItemInBankOrInventory(int itemId) {
        return bankAndInventoryItems.contains(itemId);
    }

    @Provides
    POHStorageConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(POHStorageConfig.class);
    }
}