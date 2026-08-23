package com.bitworksmc.headdb.legacy;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.Locale;

final class LegacyMessages {
    private final File messagesDirectory;
    private final Map<String, YamlConfiguration> languages = new LinkedHashMap<String, YamlConfiguration>();

    LegacyMessages(File dataFolder) {
        messagesDirectory = new File(dataFolder, "messages");
        reload();
    }

    String get(String key, String fallback, String... replacements) {
        return getForLanguage("en", key, fallback, replacements);
    }

    String getForLanguage(String language, String key, String fallback, String... replacements) {
        YamlConfiguration messages = languages.get(language == null ? "en" : language.toLowerCase(Locale.ROOT));
        if (messages == null) messages = languages.get("en");
        String value = messages.getString(key, fallback);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            value = value.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return color(value);
    }

    void reload() {
        languages.clear();
        File[] files = messagesDirectory.listFiles((directory, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null) for (File file : files) {
            String name = file.getName();
            languages.put(name.substring(0, name.length() - 4).toLowerCase(Locale.ROOT),
                    YamlConfiguration.loadConfiguration(file));
        }
        if (!languages.containsKey("en")) {
            languages.put("en", YamlConfiguration.loadConfiguration(new File(messagesDirectory, "en.yml")));
        }
    }

    Set<String> availableLanguages() {
        return Collections.unmodifiableSet(languages.keySet());
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
