package com.bitworksmc.headdb.core.factory;

import com.bitworksmc.headdb.api.model.Head;
import com.bitworksmc.headdb.core.HeadDB;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerTextures;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.*;

@ApiStatus.Internal
public class PaperItemFactory implements ItemFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaperItemFactory.class);
    private final HeadDB plugin;
    private final NamespacedKey headIdKey;

    public PaperItemFactory(HeadDB plugin) {
        this.plugin = plugin;
        this.headIdKey = new NamespacedKey(plugin, "head_id");
    }

    @Override
    public ItemStack asItem(Head head) {
        ItemStack item = ItemStack.of(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        PlayerProfile profile = Bukkit.createProfileExact(UUID.randomUUID(), null);

        try {
            URI textureUri = TextureProfileValue.parseTrustedUrl(head.getTextureUrl());
            if (TextureProfileValue.isMojangUrl(textureUri)) {
                PlayerTextures textures = profile.getTextures();
                textures.setSkin(textureUri.toURL());
                profile.setTextures(textures);
            } else {
                // Old catalog caches may still contain a HeadDB-hosted URL. Paper
                // deliberately rejects those in PlayerTextures, so keep this path
                // only as a compatibility fallback until the next catalog sync.
                profile.setProperty(new ProfileProperty(
                        "textures",
                        TextureProfileValue.fromUrl(textureUri)
                ));
            }
            meta.setPlayerProfile(profile);
        } catch (IllegalArgumentException | MalformedURLException ex) {
            LOGGER.error("Failed to set texture for {} (ID:{} | Texture: {})", head.getName(), head.getId(), head.getTexture(), ex);
            return item;
        }

        String cost = String.valueOf(plugin.getCfg().getHeadOrCategoryPrice(head.getId(), head.getCategory().toLowerCase(Locale.ROOT)));
        Component name = plugin.getCfg().getHeadName()
                .replaceText(builder -> builder.matchLiteral("{name}").replacement(head.getName()))
                .replaceText(builder -> builder.matchLiteral("{cost}").replacement(cost));
        meta.itemName(name);
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>(plugin.getCfg().getHeadsLore());
        lore.replaceAll(component -> component.replaceText(builder -> builder.matchLiteral("{id}").replacement(String.valueOf(head.getId())))
                .replaceText(builder -> builder.matchLiteral("{name}").replacement(head.getName()))
                .replaceText(builder -> builder.matchLiteral("{category}").replacement(head.getCategory()))
                .replaceText(builder -> builder.matchLiteral("{tags}").replacement(String.join(",", head.getTags())))
                .replaceText(builder -> builder.matchLiteral("{cost}").replacement(cost)
        ));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(headIdKey, PersistentDataType.INTEGER, head.getId());

        item.setItemMeta(meta);
        return item;
    }

    @Override
    public ItemStack asItem(OfflinePlayer player) {
        if (player == null || player.getName() == null) {
            return null;
        }

        ItemStack item = ItemStack.of(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        PlayerProfile profile = Bukkit.createProfile(player.getUniqueId(), player.getName());
        meta.setPlayerProfile(profile);
        Component name = Component.text(player.getName());
        meta.itemName(name);
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public UUID getIdFromItem(ItemStack item) {
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        PlayerProfile profile = meta.getPlayerProfile();
        return profile != null ? profile.getId() : null;
    }

    @Override
    public Integer getHeadIdFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        Integer stored = meta.getPersistentDataContainer().get(headIdKey, PersistentDataType.INTEGER);
        if (stored != null) return stored;
        List<Component> lore = meta.lore();
        if (lore == null) return null;
        for (Component line : lore) {
            String text = PlainTextComponentSerializer.plainText().serialize(line).trim();
            java.util.regex.Matcher match = java.util.regex.Pattern.compile("(?i)^ID\\s*:\\s*(\\d+)$").matcher(text);
            if (match.find()) {
                try { return Integer.parseInt(match.group(1)); }
                catch (NumberFormatException ignored) { return null; }
            }
        }
        return null;
    }

    @Override
    public Component getNameFromItem(ItemStack item) {
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        Component itemName = meta.itemName();
        if (itemName != null) {
            return itemName;
        }
        Component displayName = meta.displayName();
        return displayName != null ? displayName : Component.empty();
    }

    @Override
    public List<Component> getLoreFromItem(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.lore();
        return lore == null ? null : List.copyOf(lore);
    }

    @Override
    public ItemStack setItemDetails(ItemStack item, Component name, Component... lore) {
        ItemMeta meta = item.getItemMeta();
        meta.itemName(name);
        meta.displayName(name != null ? name.decoration(TextDecoration.ITALIC, false) : null);
        meta.lore(lore != null ? Arrays.asList(lore) : null);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public ItemStack newItem(Material material) {
        return material == null ? null : ItemStack.of(material);
    }

    @Override
    public ItemStack newItem(Material material, Component name, Component... lore) {
        ItemStack item = ItemStack.of(material);
        return setItemDetails(item, name, lore);
    }

}
