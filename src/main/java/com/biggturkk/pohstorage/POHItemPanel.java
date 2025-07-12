package com.biggturkk.pohstorage;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.game.ItemManager;
import net.runelite.client.callback.ClientThread;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class POHItemPanel extends PluginPanel {
    private JTree tree;
    private final Map<Integer, JLabel> itemLabels = new HashMap<>();
    private final POHStoragePlugin plugin;
    private final POHStorageItemManager itemManager;
    private final ItemManager itemManagerService;
    private final ClientThread clientThread;
    private static final int ICON_SIZE = 25; // Consistent icon size for visibility

    // Colors for item status
    private static final Color COLOR_UNKNOWN = Color.WHITE; // Unknown status
    private static final Color COLOR_MISSING = Color.RED;   // Missing from POH
    private static final Color COLOR_STORED = Color.GREEN;  // In POH storage

    // Icon caches
    private final Map<Integer, ImageIcon> itemIconCache = new HashMap<>();
    private final Map<StorageType, ImageIcon> storageIconCache = new HashMap<>();

    // Inner class to store item ID with name for tree nodes
    private static class ItemNode {
        final int id;
        final String name;

        ItemNode(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    // Inner class to store set name for set nodes
    private static class SetNode {
        final String setName;

        SetNode(String setName) {
            this.setName = setName;
        }

        @Override
        public String toString() {
            return setName;
        }
    }

    @Inject
    public POHItemPanel(POHStoragePlugin plugin, POHStorageItemManager itemManager, ItemManager itemManagerService, ClientThread clientThread) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.itemManagerService = itemManagerService;
        this.clientThread = clientThread;
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        initializePanel();
    }

    private void initializePanel() {
        // Preload icons on client thread
        clientThread.invokeLater(this::preloadIcons);

        // Main panel to hold title and tree
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout()); // Changed to BorderLayout for full space usage
        mainPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Add plugin name title with a nice color
        JLabel title = new JLabel("POH Storage");
        title.setForeground(ColorScheme.BRAND_ORANGE); // Vibrant orange
        title.setFont(new Font("Arial", Font.BOLD, 16));
        title.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        mainPanel.add(title, BorderLayout.NORTH);

        // Initialize tree
        tree = new JTree();
        tree.setBackground(ColorScheme.DARK_GRAY_COLOR);
        tree.setForeground(Color.WHITE);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(false); // Disable root handles to reduce indent
        tree.setRowHeight(30); // Fixed row height for consistent spacing

        // Customize tree renderer
        tree.setCellRenderer(new DefaultTreeCellRenderer() {
            {
                setBackgroundNonSelectionColor(ColorScheme.DARK_GRAY_COLOR);
                setTextNonSelectionColor(Color.WHITE);
                setBorderSelectionColor(ColorScheme.BRAND_ORANGE);
                setIconTextGap(8); // Reduced space between icon and text
            }

            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
                                                          boolean leaf, int row, boolean hasFocus) {
                Component c = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                Object userObject = node.getUserObject();

                if (userObject instanceof StorageType) {
                    // Storage type header
                    StorageType type = (StorageType) userObject;
                    String displayName = type.name().replace("_", " "); // Remove underscores
                    setText(displayName);
                    setForeground(new Color(52, 152, 219)); // Blue for headers
                    ImageIcon icon = getStorageTypeIcon(type);
                    setIcon(icon); // Use cached icon
                } else if (userObject instanceof SetNode) {
                    // Set node
                    SetNode setNode = (SetNode) userObject;
                    setText(setNode.setName);
                    setForeground(new Color(241, 196, 15)); // Yellow for set names
                    setIcon(null); // No icon for set nodes
                } else if (userObject instanceof ItemNode) {
                    // Item node
                    ItemNode itemNode = (ItemNode) userObject;
                    setText(itemNode.name);
                    setForeground(getItemColor(itemNode.id)); // Color based on POH status
                    ImageIcon icon = getItemIcon(itemNode.id);
                    setIcon(icon); // Use cached icon
                    setToolTipText("Item ID: " + itemNode.id); // Add tooltip
                }

                // Reduced padding for less indent
                if (c instanceof JComponent) {
                    ((JComponent) c).setBorder(BorderFactory.createEmptyBorder(3, 2, 3, 5)); // Less left padding
                }
                return c;
            }
        });

        // JScrollPane for tree
        JScrollPane scrollPane = new JScrollPane(tree);
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        mainPanel.add(scrollPane, BorderLayout.CENTER); // Fill available space

        add(mainPanel, BorderLayout.CENTER);

        // Populate tree on client thread
        clientThread.invokeLater(this::populateTree);
    }

    private void preloadIcons() {
        // Cache icons for items in itemStorageMap
        for (Integer itemId : itemManager.getAllItemIds()) {
            if (itemId > 0 && !itemIconCache.containsKey(itemId)) {
                BufferedImage image = itemManagerService.getImage(itemId, 1, false);
                if (image != null) {
                    Image scaledImage = image.getScaledInstance(ICON_SIZE, ICON_SIZE, Image.SCALE_SMOOTH);
                    itemIconCache.put(itemId, new ImageIcon(scaledImage));
                }
            }
        }

        // Cache icons for storage types
        for (StorageType type : StorageType.values()) {
            int itemId = getRepresentativeItemId(type);
            if (itemId > 0 && !storageIconCache.containsKey(type)) {
                BufferedImage image = itemManagerService.getImage(itemId, 1, false);
                if (image != null) {
                    Image scaledImage = image.getScaledInstance(ICON_SIZE, ICON_SIZE, Image.SCALE_SMOOTH);
                    storageIconCache.put(type, new ImageIcon(scaledImage));
                }
            }
        }
    }

    public void populateTree() {
        tree.setModel(createTreeModel());
        // Collapse all nodes initially
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.collapseRow(i);
        }
    }

    private DefaultTreeModel createTreeModel() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");
        Map<StorageType, DefaultMutableTreeNode> typeNodes = new HashMap<>();

        for (StorageType type : StorageType.values()) {
            Map<String, List<POHStorageItemManager.ItemEntry>> sets = itemManager.getItemsBySet(type);
            if (sets.isEmpty()) {
                continue; // Skip empty storage types
            }
            DefaultMutableTreeNode typeNode = new DefaultMutableTreeNode(type);
            typeNodes.put(type, typeNode);
            root.add(typeNode);

            for (Map.Entry<String, List<POHStorageItemManager.ItemEntry>> setEntry : sets.entrySet()) {
                String setName = setEntry.getKey();
                List<POHStorageItemManager.ItemEntry> items = setEntry.getValue();
                if (items.isEmpty()) {
                    continue; // Skip empty sets
                }
                DefaultMutableTreeNode setNode = new DefaultMutableTreeNode(new SetNode(setName));
                typeNode.add(setNode);

                for (POHStorageItemManager.ItemEntry item : items) {
                    String itemName = item.name;
                    if (itemName == null || itemName.isEmpty()) {
                        itemName = "Unknown Item (ID " + item.id + ")";
                    }
                    setNode.add(new DefaultMutableTreeNode(new ItemNode(item.id, itemName)));
                }
            }
        }

        return new DefaultTreeModel(root);
    }

    private ImageIcon getStorageTypeIcon(StorageType type) {
        return storageIconCache.get(type);
    }

    private ImageIcon getItemIcon(int itemId) {
        return itemIconCache.get(itemId);
    }

    private int getRepresentativeItemId(StorageType type) {
        switch (type) {
            case ARMOUR_CASE: return 1127; // Rune Platebody
            case TOY_BOX: return 2520; // Toy Sword
            case MAGIC_WARDROBE: return 577; // Wizard Hat
            case CAPE_RACK: return 6570; // Fire Cape
            case TREASURE_CHEST: return 995; // Coins
            case FANCY_DRESS_BOX: return 1038; // Party Hat
            default: return 0;
        }
    }

    private Color getItemColor(int itemId) {
        if (plugin.isItemInPOHStorage(itemId)) {
            return COLOR_STORED; // Green
        }
        if (plugin.isItemInBankOrInventory(itemId)) {
            return COLOR_MISSING; // Red
        }
        return COLOR_UNKNOWN; // White
    }

    public void clearIconCache() {
        itemIconCache.clear();
        storageIconCache.clear();
    }
}