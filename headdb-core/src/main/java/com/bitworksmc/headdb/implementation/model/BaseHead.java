package com.bitworksmc.headdb.implementation.model;

import com.bitworksmc.headdb.api.model.Head;
import com.bitworksmc.headdb.core.factory.ItemFactoryRegistry;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class BaseHead implements Head {

    private final int id;
    private final String name;
    private final String texture;
    private final String category;
    private final List<String> tags;
    private volatile ItemStack item;

    public BaseHead(int id, String name, String texture, String category, List<String> tags) {
        this.id = id;
        this.name = name;
        this.texture = texture;
        this.category = category;
        this.tags = tags;
    }

    @Override
    public ItemStack getItem() {
        ItemStack cached = this.item;
        if (cached == null) {
            synchronized (this) {
                cached = this.item;
                if (cached == null) {
                    cached = ItemFactoryRegistry.get().asItem(this);
                    this.item = cached;
                }
            }
        }
        return cached.clone(); // Returns a clone of the original to avoid modifying it.
    }

    @Override
    public int getId() {
        return this.id;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getTexture() {
        return this.texture;
    }

    @Override
    public String getCategory() {
        return this.category;
    }

    @Override
    public List<String> getTags() {
        return this.tags;
    }

    @Override
    public String toString() {
        return "Head{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", texture='" + texture + '\'' +
                ", category='" + category + '\'' +
                ", tags=" + tags +
                '}';
    }

    /**
     * For performance reasons, heads have their id as the hash.
     */
    @Override
    public int hashCode() {
        return this.id;
    }

    /**
     * For performance reasons, heads are only matched by their id.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseHead other)) return false;
        return this.id == other.id;
    }

}
