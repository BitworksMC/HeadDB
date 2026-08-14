package com.bitworksmc.headdb.core.menu.gui;

import com.github.thesilentpro.grim.gui.PaginatedGUI;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

/**
 * Paginated GUI that tolerates stale page tracking after its contents shrink.
 */
abstract class SafePaginatedGUI extends PaginatedGUI {

    protected SafePaginatedGUI(NamespacedKey key) {
        super(key);
    }

    @Override
    public void open(Player player) {
        open(player, 0);
    }

    @Override
    public void open(Player player, Integer requestedPage) {
        int page = clampPage(requestedPage, getPages().size());
        if (page < 0) {
            return;
        }

        getGuiRegistry().setCurrentPage(player.getUniqueId(), getKey(), page);
        super.open(player, page);
    }

    static int clampPage(Integer requestedPage, int pageCount) {
        if (pageCount <= 0) {
            return -1;
        }
        int requested = requestedPage == null ? 0 : requestedPage;
        return Math.max(0, Math.min(requested, pageCount - 1));
    }
}
