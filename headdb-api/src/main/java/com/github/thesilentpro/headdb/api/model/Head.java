package com.github.thesilentpro.headdb.api.model;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * @deprecated Use {@link com.bitworksmc.headdb.api.model.Head} instead.
 */
@Deprecated
public interface Head {

    int getId();

    String getName();

    String getTexture();

    /**
     * Returns the complete skin URL used to create this head. Older API
     * implementations only expose a Mojang texture hash, so that remains the
     * default.
     */
    default String getTextureUrl() {
        return "https://textures.minecraft.net/texture/" + getTexture();
    }

    String getCategory();

    List<String> getTags();

    ItemStack getItem();

}
