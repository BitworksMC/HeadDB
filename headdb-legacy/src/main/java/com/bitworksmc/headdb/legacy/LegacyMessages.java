package com.bitworksmc.headdb.legacy;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

final class LegacyMessages {
    private final YamlConfiguration messages;

    LegacyMessages(File dataFolder) {
        messages = YamlConfiguration.loadConfiguration(new File(dataFolder, "messages/en.yml"));
    }

    String get(String key, String fallback, String... replacements) {
        String value = messages.getString(key, fallback);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            value = value.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return color(value);
    }

    static String color(String value) {
        if (value == null) return "";
        Map<String, ChatColor> colors = new LinkedHashMap<String, ChatColor>();
        colors.put("black", ChatColor.BLACK); colors.put("dark_blue", ChatColor.DARK_BLUE);
        colors.put("dark_green", ChatColor.DARK_GREEN); colors.put("dark_aqua", ChatColor.DARK_AQUA);
        colors.put("dark_red", ChatColor.DARK_RED); colors.put("dark_purple", ChatColor.DARK_PURPLE);
        colors.put("gold", ChatColor.GOLD); colors.put("gray", ChatColor.GRAY);
        colors.put("dark_gray", ChatColor.DARK_GRAY); colors.put("blue", ChatColor.BLUE);
        colors.put("green", ChatColor.GREEN); colors.put("aqua", ChatColor.AQUA);
        colors.put("red", ChatColor.RED); colors.put("light_purple", ChatColor.LIGHT_PURPLE);
        colors.put("yellow", ChatColor.YELLOW); colors.put("white", ChatColor.WHITE);
        colors.put("bold", ChatColor.BOLD); colors.put("italic", ChatColor.ITALIC);
        colors.put("underlined", ChatColor.UNDERLINE); colors.put("strikethrough", ChatColor.STRIKETHROUGH);
        colors.put("reset", ChatColor.RESET);
        for (Map.Entry<String, ChatColor> entry : colors.entrySet()) {
            value = value.replace("<" + entry.getKey() + ">", entry.getValue().toString());
            value = value.replace("</" + entry.getKey() + ">", ChatColor.RESET.toString());
        }
        value = value.replaceAll("<#[0-9a-fA-F]{6}>", "").replace("<rainbow>", "").replace("</rainbow>", "");
        value = value.replaceAll("<key:[^>]+>", "Click");
        return ChatColor.translateAlternateColorCodes('&', value);
    }
}
