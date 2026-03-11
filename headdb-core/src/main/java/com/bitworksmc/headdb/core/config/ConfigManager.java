package com.bitworksmc.headdb.core.config;

import com.bitworksmc.headdb.core.HeadDB;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class ConfigManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigManager.class);

    private final Config config;
    private final SoundConfig soundConfig;

    public ConfigManager(HeadDB plugin) {
        this.config = new Config(plugin);
        this.soundConfig = new SoundConfig();
    }

    public void loadAll(JavaPlugin plugin) {
        LOGGER.debug("Loading configurations...");
        long configStart = System.currentTimeMillis();
        config.load();
        LOGGER.debug("Loaded config.yml in {}ms", System.currentTimeMillis() - configStart);

        long soundsStart = System.currentTimeMillis();
        File soundFile = new File(plugin.getDataFolder(), "sounds.yml");
        if (!soundFile.exists()) {
            plugin.saveResource("sounds.yml", false);
        }

        soundConfig.load(YamlConfiguration.loadConfiguration(soundFile));
        LOGGER.debug("Loaded sounds.yml in {}ms", System.currentTimeMillis() - soundsStart);
    }

    public Config getConfig() {
        return config;
    }

    public SoundConfig getSoundConfig() {
        return soundConfig;
    }

}
