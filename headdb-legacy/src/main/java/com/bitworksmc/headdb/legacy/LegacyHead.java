package com.bitworksmc.headdb.legacy;

import com.bitworksmc.headdb.api.model.Head;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class LegacyHead implements Head {
    private int id;
    private String name;
    private String texture;
    private String textureUrl;
    private String category;
    private List<String> tags;
    private transient volatile ItemStack cachedItem;

    @Override
    public int getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getTexture() {
        return texture;
    }

    @Override
    public String getTextureUrl() {
        return isEmpty(textureUrl) ? Head.super.getTextureUrl() : textureUrl;
    }

    @Override
    public String getCategory() {
        return category;
    }

    @Override
    public List<String> getTags() {
        if (tags == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(tags);
    }

    void validate() {
        if (id < 0 || isEmpty(name) || isEmpty(texture) || isEmpty(category)) {
            throw new IllegalArgumentException("Invalid head entry with ID " + id);
        }
        tags = tags == null ? Collections.<String>emptyList() : new ArrayList<String>(tags);
    }

    @Override
    public ItemStack getItem() {
        ItemStack item = cachedItem;
        if (item == null) {
            synchronized (this) {
                item = cachedItem;
                if (item == null) {
                    item = LegacyItemFactory.createHead(this);
                    cachedItem = item;
                }
            }
        }
        return item.clone();
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
