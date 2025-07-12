package com.biggturkk.pohstorage;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.callback.ClientThread;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class POHItemPanel extends PluginPanel {
    private JTree tree;
    private final Map<Integer, JLabel> itemLabels = new HashMap<>();
    private final POHStoragePlugin plugin; // For future extensibility (e.g., event handling)
    private final POHStorageItemManager itemManager;
    private final ItemManager itemManagerService;
    private final ClientThread clientThread;

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
        tree = new JTree(); // Placeholder tree
        tree.setBackground(ColorScheme.DARK_GRAY_COLOR);
        tree.setForeground(Color.WHITE);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);

        tree.setCellRenderer(new DefaultTreeCellRenderer() {
            {
                setBackgroundNonSelectionColor(ColorScheme.DARK_GRAY_COLOR);
                setTextNonSelectionColor(Color.WHITE);
                setBorderSelectionColor(ColorScheme.BRAND_ORANGE);
            }

            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
                                                          boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                Object userObject = node.getUserObject();
                if (userObject instanceof StorageType) {
                    StorageType type = (StorageType) userObject;
                    setIcon(getStorageTypeIcon(type));
                } else if (userObject instanceof String) {
                    setIcon(null);
                }
                return this;
            }
        });

        JScrollPane scrollPane = new JScrollPane(tree);
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        add(scrollPane, BorderLayout.CENTER);

        // Defer tree population to client thread
        clientThread.invokeLater(this::populateTree);
    }

    private void populateTree() {
        tree.setModel(createTreeModel());
    }

    private DefaultTreeModel createTreeModel() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");
        Map<StorageType, DefaultMutableTreeNode> typeNodes = new HashMap<>();

        for (Integer itemId : itemManager.getAllItemIds()) {
            List<StorageType> storages = itemManager.getStoragesForItem(itemId);
            for (StorageType type : storages) {
                DefaultMutableTreeNode typeNode = typeNodes.computeIfAbsent(type, k -> {
                    DefaultMutableTreeNode node = new DefaultMutableTreeNode(type);
                    root.add(node);
                    return node;
                });
                String itemName = itemManagerService.getItemComposition(itemId).getName();
                if (itemName == null || itemName.isEmpty()) {
                    itemName = "Unknown Item (ID " + itemId + ")";
                }
                typeNode.add(new DefaultMutableTreeNode(itemName + " - Unknown"));
            }
        }

        return new DefaultTreeModel(root);
    }

    private ImageIcon getStorageTypeIcon(StorageType type) {
        int itemId = getRepresentativeItemId(type);
        return new ImageIcon(itemManagerService.getImage(itemId, 1, false));
    }

    private int getRepresentativeItemId(StorageType type) {
        switch (type) {
            case ARMOUR_CASE: return 1127; // Platebody
            case TOY_BOX: return 2520; // Toy Sword
            case MAGIC_WARDROBE: return 577; // Wizard Hat
            case CAPE_RACK: return 6570; // Fire Cape
            case TREASURE_CHEST: return 995; // Coins
            case FANCY_DRESS_BOX: return 1038; // Party Hat
            case BOOKCASE: return 387; // Book
            default: return 0;
        }
    }
}