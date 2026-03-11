package com.bitworksmc.headdb.core.config;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class SoundConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(SoundConfig.class);

    private final Map<String, Sound> sounds = new HashMap<>();

    @SuppressWarnings("PatternValidation")
    public void load(ConfigurationSection section) {
        Map<String, Map<String, Object>> grouped = new HashMap<>();
        flatten(section, "", grouped);

        for (Map.Entry<String, Map<String, Object>> entry : grouped.entrySet()) {
            String key = entry.getKey();
            Map<String, Object> soundData = entry.getValue();

            String soundName = (String) soundData.get("sound");
            if (soundName == null || soundName.isBlank()) {
                LOGGER.warn("Invalid sound '{}' in sounds.yml for {}", soundName, key);
                continue; // Skip sounds with no name
            }

            String sourceName = (String) soundData.getOrDefault("source", "MASTER");
            float volume = ((Number) soundData.getOrDefault("volume", 1.0)).floatValue();
            float pitch = ((Number) soundData.getOrDefault("pitch", 1.0)).floatValue();

            Sound.Source source;
            try {
                source = Sound.Source.valueOf(sourceName.toUpperCase());
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Invalid source '{}' in sounds.yml for {}", sourceName, key);
                source = Sound.Source.MASTER; // Fallback on invalid source
            }

            try {
                sounds.put(key, Sound.sound(Key.key(soundName), source, volume, pitch));
                LOGGER.debug("Registered sound key '{}' as {}", key, soundName);
            } catch (IllegalArgumentException ex) {
                // Skip invalid key syntax
                LOGGER.warn("Invalid sound: {}", key);
            }
        }
    }

    private void flatten(ConfigurationSection section, String path, Map<String, Map<String, Object>> result) {
        for (String key : section.getKeys(false)) {
            String fullKey = path.isEmpty() ? key : path + "." + key;
            Object val = section.get(key);

            if (val instanceof ConfigurationSection nested) {
                flatten(nested, fullKey, result);
            } else {
                int lastDot = fullKey.lastIndexOf('.');
                if (lastDot == -1) continue;

                String groupKey = fullKey.substring(0, lastDot);
                String field = fullKey.substring(lastDot + 1);

                result.computeIfAbsent(groupKey, k -> new HashMap<>())
                        .put(field, val);
            }
        }
    }

    @Nullable
    public Sound get(String key) {
        return sounds.get(key);
    }

    public Map<String, Sound> getSounds() {
        return Collections.unmodifiableMap(sounds);
    }

}