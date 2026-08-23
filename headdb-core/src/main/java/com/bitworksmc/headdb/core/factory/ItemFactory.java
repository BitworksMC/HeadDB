package com.bitworksmc.headdb.core.factory;

import com.bitworksmc.headdb.api.model.Head;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Represents an item factory for per server implementation creation of ItemStacks.
 * <bold>Intended for internal use.</bold>
 */
@ApiStatus.Internal
public interface ItemFactory {

    ItemStack asItem(Head head);

    @Nullable
    ItemStack asItem(@Nullable OfflinePlayer player);

    @Nullable
    UUID getIdFromItem(ItemStack item);

    /** Returns the stable HeadDB catalog ID stored on a database head item. */
    @Nullable
    default Integer getHeadIdFromItem(ItemStack item) {
        return null;
    }

    Component getNameFromItem(ItemStack item);

    @Nullable
    List<Component> getLoreFromItem(ItemStack item);

    ItemStack setItemDetails(ItemStack item, Component name, Component... lore);

    ItemStack newItem(Material material);

    ItemStack newItem(Material material, Component name, Component... lore);

    default void giveItem(Player player, Collection<Integer> omit, ItemStack... items) {
        Objects.requireNonNull(player, "player");
        if (items == null || items.length == 0) {
            return;
        }

        // Inventory insertion and lore omission both mutate ItemStacks. Work on
        // clones so menu icons and API-owned items are never changed as a side effect.
        ItemStack[] preparedItems = Arrays.stream(items)
                .filter(Objects::nonNull)
                .map(ItemStack::clone)
                .toArray(ItemStack[]::new);
        if (preparedItems.length == 0) {
            return;
        }

        // Use a HashSet for O(1) lookups if omit is not null and not empty
        Set<Integer> omitSet = (omit != null && !omit.isEmpty()) ? (omit instanceof Set ? (Set<Integer>) omit : new HashSet<>(omit)) : null;

        for (ItemStack item : preparedItems) {
            if (omitSet != null) {
                List<Component> lore = getLoreFromItem(item);
                if (lore != null && !lore.isEmpty()) {
                    // Preallocate filtered lore list capacity to original lore size (worst case)
                    int loreSize = lore.size();
                    List<Component> filteredLore = new ArrayList<>(loreSize);

                    for (int j = 0; j < loreSize; j++) {
                        // omit uses 1-based indices; skip those in omitSet
                        if (!omitSet.contains(j + 1)) {
                            filteredLore.add(lore.get(j));
                        }
                    }

                    // Only set item details if lore changed
                    if (filteredLore.size() != loreSize) {
                        setItemDetails(item, getNameFromItem(item), filteredLore.toArray(new Component[0]));
                    }
                }
            }
        }

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(preparedItems);
        for (ItemStack missed : leftovers.values()) {
            int leftover = missed.getAmount();
            final int maxStack = missed.getMaxStackSize();

            // Drop items in maxStack-size chunks efficiently
            while (leftover > 0) {
                int dropAmount = (leftover > maxStack) ? maxStack : leftover;
                ItemStack toDrop = missed.clone();
                toDrop.setAmount(dropAmount);
                player.getWorld().dropItemNaturally(player.getLocation(), toDrop);
                leftover -= dropAmount;
            }
        }
    }

}
