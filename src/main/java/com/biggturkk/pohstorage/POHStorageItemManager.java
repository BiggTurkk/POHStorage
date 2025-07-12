package com.biggturkk.pohstorage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Singleton
public class POHStorageItemManager {
    private final Map<Integer, List<StorageType>> itemStorageMap = new HashMap<>();

    @Inject
    public POHStorageItemManager() {
        log.info("✅ POHStorageItemManager initialized");
        loadStorableItems();
    }

    private void loadStorableItems() {
        try (InputStreamReader reader = new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream("/storable_items.json"),
                        "Missing resource: /storable_items.json"))) {
            Type mapType = new TypeToken<Map<String, List<Integer>>>() {}.getType();
            Map<String, List<Integer>> map = new Gson().fromJson(reader, mapType);

            for (Map.Entry<String, List<Integer>> entry : map.entrySet()) {
                StorageType storageType = StorageType.valueOf(entry.getKey());
                log.info("Loading storage type: {} with {} item IDs", storageType, entry.getValue().size());
                for (Integer itemId : entry.getValue()) {
                    itemStorageMap.computeIfAbsent(itemId, k -> new ArrayList<>()).add(storageType);
                }
            }

            log.info("✅ Successfully loaded storable_items.json");
            log.info("📦 Loaded {} total unique item IDs", itemStorageMap.size());
            log.info("🧾 Item ID List: {}", itemStorageMap.keySet());
        } catch (Exception ex) {
            log.info("❌ Failed to load storable_items.json", ex);
            itemStorageMap.clear(); // Ensure map is empty if loading fails
        }
    }

    public List<StorageType> getStoragesForItem(int itemId) {
        return itemStorageMap.getOrDefault(itemId, Collections.emptyList());
    }

    // Corrected method to return all item IDs
    public List<Integer> getAllItemIds() {
        return new ArrayList<>(itemStorageMap.keySet());
    }
}