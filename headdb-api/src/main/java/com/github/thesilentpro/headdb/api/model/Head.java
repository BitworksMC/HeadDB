package com.github.thesilentpro.headdb.api.model;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * @deprecated Use {@link com.bitworksmc.headdb.api.model.Head} instead.
 */
@Deprecated(forRemoval = true, since = "6.0.0")
public interface Head {

    int getId();

    String getName();

    String getTexture();

    String getCategory();

    List<String> getTags();

    ItemStack getItem();

}
