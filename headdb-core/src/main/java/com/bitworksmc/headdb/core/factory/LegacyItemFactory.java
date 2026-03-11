package com.bitworksmc.headdb.core.factory;

import com.bitworksmc.headdb.api.model.Head;
import com.bitworksmc.headdb.core.HeadDB;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.*;

@SuppressWarnings("deprecation")
@ApiStatus.Internal
public class LegacyItemFactory implements ItemFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyItemFactory.class);
    private final HeadDB plugin;

    public LegacyItemFactory(HeadDB plugin) {
        this.plugin = plugin;
    }

    @Override
    public ItemStack asItem(Head head) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID(), null);

        try {
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(URI.create("http://textures.minecraft.net/texture/" + head.getTexture()).toURL());
            profile.setTextures(textures);
        } catch (MalformedURLException ex) {
            LOGGER.error("Failed to set texture for {} (ID:{} | Texture: {})", head.getName(), head.getId(), head.getTexture(), ex);
            return item;
        }

        String cost = String.valueOf(plugin.getCfg().getHeadOrCategoryPrice(head.getId(), head.getCategory().toLowerCase(Locale.ROOT)));
        Component name = plugin.getCfg().getHeadName().replaceText(builder -> builder.matchLiteral("{name}").replacement(head.getName())).replaceText(builder -> builder.matchLiteral("{cost}").replacement(cost));
        String serializedName = ChatColor.translateAlternateColorCodes('&', LegacyComponentSerializer.legacyAmpersand().serialize(name));
        meta.setItemName(serializedName);
        meta.setDisplayName(serializedName);

        List<Component> lore = new ArrayList<>(plugin.getCfg().getHeadsLore());
        lore.replaceAll(component -> component.replaceText(builder -> builder.matchLiteral("{id}").replacement(String.valueOf(head.getId())))
                .replaceText(builder -> builder.matchLiteral("{name}").replacement(head.getName()))
                .replaceText(builder -> builder.matchLiteral("{category}").replacement(head.getCategory()))
                .replaceText(builder -> builder.matchLiteral("{tags}").replacement(String.join(",", head.getTags())))
                .replaceText(builder -> builder.matchLiteral("{cost}").replacement(String.valueOf(plugin.getCfg().getHeadOrCategoryPrice(head.getId(), head.getCategory().toLowerCase(Locale.ROOT)))))
        );
        meta.setLore(lore.stream().map(component -> ChatColor.translateAlternateColorCodes('&', LegacyComponentSerializer.legacyAmpersand().serialize(component))).toList());

        meta.setOwnerProfile(profile);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public ItemStack asItem(OfflinePlayer player) {
        if (player == null) return null;

        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(player);

        if (player.getName() != null) {
            meta.setItemName(player.getName());
            meta.setDisplayName(player.getName());
        }

        item.setItemMeta(meta);
        return item;
    }

    @Override
    public UUID getIdFromItem(ItemStack item) {
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        org.bukkit.profile.PlayerProfile profile = meta.getOwnerProfile();
        return profile != null ? profile.getUniqueId() : null;
    }

    @Override
    public Component getNameFromItem(ItemStack item) {
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        String itemName = meta.getItemName();
        if (itemName == null || itemName.isBlank()) {
            itemName = meta.getDisplayName();
        }
        return itemName != null ? LegacyComponentSerializer.legacySection().deserialize(itemName) : Component.empty();
    }

    @Override
    public ItemStack setItemDetails(ItemStack item, Component name, Component... lore) {
        ItemMeta meta = item.getItemMeta();
        String serializedName = name != null
                ? ChatColor.translateAlternateColorCodes('&', LegacyComponentSerializer.legacyAmpersand().serialize(name))
                : null;
        meta.setItemName(serializedName);
        meta.setDisplayName(serializedName);
        meta.setLore(
                lore != null
                ? Arrays.stream(lore).map(component -> ChatColor.translateAlternateColorCodes('&', LegacyComponentSerializer.legacyAmpersand().serialize(component))).toList()
                : null
        );
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public ItemStack newItem(Material material) {
        return material == null ? null : new ItemStack(material);
    }

    @Override
    public ItemStack newItem(Material material, Component name, Component... lore) {
        ItemStack item = new ItemStack(material);
        return setItemDetails(item, name, lore);
    }

}
