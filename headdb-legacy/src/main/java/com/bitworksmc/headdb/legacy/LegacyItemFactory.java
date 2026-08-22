package com.bitworksmc.headdb.legacy;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

final class LegacyItemFactory {
    private static volatile FileConfiguration configuration;
    private static volatile boolean economyEnabled;
    private LegacyItemFactory() {
    }

    static void configure(FileConfiguration config, boolean economy) {
        configuration = config;
        economyEnabled = economy;
    }

    static ItemStack createHead(LegacyHead head) {
        ItemStack item = newPlayerHead();
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        FileConfiguration config = configuration;
        String mode = economyEnabled ? "economy" : "default";
        String name = config == null ? "{name}" : config.getString("head.name." + mode, "{name}");
        meta.setDisplayName(LegacyMessages.color(replace(name, head)));

        List<String> configuredLore = config == null ? Collections.<String>emptyList()
                : config.getStringList("head.lore." + mode);
        List<String> lore = new ArrayList<String>();
        if (configuredLore.isEmpty()) {
            lore.add(ChatColor.DARK_GRAY + "ID: " + head.getId());
            lore.add(ChatColor.GRAY + head.getCategory());
        } else {
            for (String line : configuredLore) lore.add(LegacyMessages.color(replace(line, head)));
        }
        meta.setLore(lore);

        if (!applyBukkitProfile(meta, head.getTextureUrl()) && !applyAuthlibProfile(meta, head.getTextureUrl())) {
            meta.setOwner("MHF_Question");
        }
        item.setItemMeta(meta);
        return item;
    }

    private static String replace(String value, LegacyHead head) {
        FileConfiguration config = configuration;
        double price = 0D;
        if (config != null) {
            String path = "economy.cost.head." + head.getId();
            price = config.contains(path) ? config.getDouble(path)
                    : config.getDouble("economy.cost.category." + head.getCategory().toLowerCase(), 0D);
        }
        return value.replace("{id}", String.valueOf(head.getId()))
                .replace("{name}", head.getName()).replace("{category}", head.getCategory())
                .replace("{tags}", String.join(",", head.getTags())).replace("{cost}", String.valueOf(price));
    }

    static ItemStack createTextureHead(String textureHash) {
        ItemStack item = newPlayerHead();
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        String textureUrl = "https://textures.minecraft.net/texture/" + textureHash;
        if (!applyBukkitProfile(meta, textureUrl) && !applyAuthlibProfile(meta, textureUrl)) {
            meta.setOwner("MHF_Question");
        }
        item.setItemMeta(meta);
        return item;
    }

    static ItemStack prepareForGive(ItemStack source) {
        ItemStack item = source.clone();
        FileConfiguration config = configuration;
        if (config == null || config.getIntegerList("head.omit").isEmpty()) return item;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) return item;
        List<Integer> omitted = config.getIntegerList("head.omit");
        List<String> lore = new ArrayList<String>();
        List<String> original = meta.getLore();
        for (int i = 0; i < original.size(); i++) if (!omitted.contains(i + 1)) lore.add(original.get(i));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * PLAYER_HEAD was introduced by the 1.13 material flattening. Resolve both
     * names dynamically so this class never links a missing enum constant.
     */
    static ItemStack newPlayerHead() {
        Material modern = Material.matchMaterial("PLAYER_HEAD");
        if (modern != null) {
            return new ItemStack(modern, 1);
        }

        Material legacy = Material.matchMaterial("SKULL_ITEM");
        if (legacy == null) {
            throw new IllegalStateException("This server does not expose a player-head material");
        }
        return new ItemStack(legacy, 1, (short) 3);
    }

    /**
     * Uses the Bukkit profile API introduced after the legacy GameProfile era.
     * All types are resolved reflectively so the class still loads on 1.8.
     */
    private static boolean applyBukkitProfile(SkullMeta meta, String textureUrl) {
        try {
            Class<?> profileClass = Class.forName("org.bukkit.profile.PlayerProfile");
            Method createProfile = org.bukkit.Bukkit.class.getMethod(
                    "createPlayerProfile", UUID.class, String.class);
            Object profile = createProfile.invoke(null, UUID.randomUUID(), null);
            Object textures = profileClass.getMethod("getTextures").invoke(profile);
            textures.getClass().getMethod("setSkin", URL.class).invoke(
                    textures, new URL(textureUrl));
            invokeCompatible(profile, "setTextures", textures);

            if (!invokeCompatible(meta, "setOwnerProfile", profile)) {
                // Paper exposed this spelling before Bukkit standardized setOwnerProfile.
                if (!invokeCompatible(meta, "setPlayerProfile", profile)) {
                    return false;
                }
            }
            return true;
        } catch (ReflectiveOperationException | IOException | LinkageError ignored) {
            return false;
        }
    }

    /**
     * CraftBukkit 1.8-1.20 stores Mojang's GameProfile directly in SkullMeta.
     */
    private static boolean applyAuthlibProfile(SkullMeta meta, String textureUrl) {
        try {
            Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
            Constructor<?> profileConstructor = profileClass.getConstructor(UUID.class, String.class);
            Object profile = profileConstructor.newInstance(UUID.randomUUID(), null);

            String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + textureUrl + "\"}}}";
            String encoded = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));

            Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
            Object property = propertyClass.getConstructor(String.class, String.class)
                    .newInstance("textures", encoded);
            Object properties = profileClass.getMethod("getProperties").invoke(profile);
            Method put = properties.getClass().getMethod("put", Object.class, Object.class);
            put.invoke(properties, "textures", property);

            Field profileField = findField(meta.getClass(), "profile");
            profileField.setAccessible(true);
            profileField.set(meta, profile);
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean invokeCompatible(Object target, String name, Object argument)
            throws ReflectiveOperationException {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name)
                    && method.getParameterTypes().length == 1
                    && method.getParameterTypes()[0].isInstance(argument)) {
                method.setAccessible(true);
                method.invoke(target, argument);
                return true;
            }
        }
        return false;
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
