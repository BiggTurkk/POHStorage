package com.biggturkk.pohstorage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.*;

@Slf4j
@Singleton
public class POHStorageItemManager {
    private final Map<Integer, List<StorageType>> itemStorageMap = new HashMap<>();
    private final Map<StorageType, Map<String, List<ItemEntry>>> itemsBySet = new HashMap<>();

    @Getter
    public static class ItemEntry {
        final int id;
        final String name;

        ItemEntry(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    @Inject
    public POHStorageItemManager() {
        log.info("✅ POHStorageItemManager initialized");
        loadStorableItems();
    }

    private void loadStorableItems() {
        try (InputStreamReader reader = new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream("/storable_items.json"),
                        "Missing resource: /storable_items.json"))) {
            Type mapType = new TypeToken<Map<String, List<Map<String, Object>>>>() {}.getType();
            Map<String, List<Map<String, Object>>> map = new Gson().fromJson(reader, mapType);

            for (StorageType type : StorageType.values()) {
                itemsBySet.put(type, new HashMap<>());
            }

            for (Map.Entry<String, List<Map<String, Object>>> entry : map.entrySet()) {
                try {
                    StorageType storageType = StorageType.valueOf(entry.getKey());
                    Map<String, List<ItemEntry>> setMap = itemsBySet.get(storageType);

                    for (Map<String, Object> set : entry.getValue()) {
                        String setName = (String) set.get("set_name");
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> items = (List<Map<String, Object>>) set.get("items");
                        List<ItemEntry> itemEntries = new ArrayList<>();

                        for (Map<String, Object> item : items) {
                            int itemId = ((Double) item.get("id")).intValue();
                            String itemName = (String) item.get("name");
                            itemEntries.add(new ItemEntry(itemId, itemName));

                            // Update itemStorageMap
                            itemStorageMap.computeIfAbsent(itemId, k -> new ArrayList<>()).add(storageType);
                        }

                        setMap.put(setName, itemEntries);
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("Unknown storage type in JSON: {}", entry.getKey());
                }
            }

            log.info("✅ Successfully loaded storable_items.json");
            log.info("📦 Loaded {} total unique item IDs", itemStorageMap.size());
            log.info("🧾 Item ID List: {}", itemStorageMap.keySet());
        } catch (Exception ex) {
            log.error("❌ Failed to load storable_items.json", ex);
            itemStorageMap.clear();
            itemsBySet.clear();
        }
    }

    public List<StorageType> getStoragesForItem(int itemId) {
        return itemStorageMap.getOrDefault(itemId, Collections.emptyList());
    }

    public List<Integer> getAllItemIds() {
        return new ArrayList<>(itemStorageMap.keySet());
    }

    public Map<String, List<ItemEntry>> getItemsBySet(StorageType type) {
        return itemsBySet.getOrDefault(type, Collections.emptyMap());
    }
}