package com.bitworksmc.headdb.core.factory;

import com.bitworksmc.headdb.core.HeadDB;
import com.bitworksmc.headdb.core.util.Compatibility;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class ItemFactoryRegistry {

    private static ItemFactory INSTANCE;

    public static void init(HeadDB plugin) {
        if (INSTANCE != null) {
            throw new IllegalStateException("ItemFactory already initialized");
        }
        if (Compatibility.IS_PAPER) {
            INSTANCE = new PaperItemFactory(plugin);
        } else {
            INSTANCE = new LegacyItemFactory(plugin);
        }
    }

    public static ItemFactory get() {
        if (INSTANCE == null) {
            throw new IllegalStateException("ItemFactory not initialized");
        }
        return INSTANCE;
    }

}
