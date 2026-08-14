package com.bitworksmc.headdb.core.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundConfigTest {

    @Test
    void malformedFieldTypesDoNotAbortTheWholeSoundConfig() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("bad.sound", 123);
        config.set("good.sound", "entity.player.levelup");
        config.set("good.source", 42);
        config.set("good.volume", "0.5");
        config.set("good.pitch", "not-a-number");

        SoundConfig sounds = new SoundConfig();

        assertDoesNotThrow(() -> sounds.load(config));
        assertEquals(1, sounds.getSounds().size());
        assertTrue(sounds.getSounds().containsKey("good"));
    }

    @Test
    void reloadingRemovesDeletedSoundEntries() {
        YamlConfiguration initial = new YamlConfiguration();
        initial.set("first.sound", "entity.player.levelup");

        SoundConfig sounds = new SoundConfig();
        sounds.load(initial);
        sounds.load(new YamlConfiguration());

        assertTrue(sounds.getSounds().isEmpty());
    }
}
