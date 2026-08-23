package com.bitworksmc.headdb.core.menu.registry;

import com.github.thesilentpro.grim.page.Page;
import com.github.thesilentpro.grim.page.registry.PageRegistry;
import org.bukkit.inventory.InventoryView;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Region-thread-safe page tracking for Paper and Folia inventory events. */
public final class ConcurrentPageRegistry implements PageRegistry {
    public static final ConcurrentPageRegistry INSTANCE = new ConcurrentPageRegistry();

    private final Map<InventoryView, Page> pages = new ConcurrentHashMap<>();

    private ConcurrentPageRegistry() {
    }

    @Override
    public void register(InventoryView view, Page page) {
        pages.put(view, page);
    }

    @Override
    public Optional<Page> get(InventoryView view) {
        return Optional.ofNullable(pages.get(view));
    }

    @Override
    public void remove(InventoryView view) {
        pages.remove(view);
    }

    @Override
    public Map<InventoryView, Page> getPages() {
        return Collections.unmodifiableMap(pages);
    }
}
