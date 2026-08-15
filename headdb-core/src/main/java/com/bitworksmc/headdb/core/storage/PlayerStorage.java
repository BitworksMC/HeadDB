package com.bitworksmc.headdb.core.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerStorage.class);

    private final Map<UUID, PlayerData> data = new ConcurrentHashMap<>();

    private final PlayerDAO playerDao;

    public PlayerStorage() {
        this(new File("plugins", "HeadDB"));
    }

    public PlayerStorage(File dataFolder) {
        Path pluginDataFolder = Objects.requireNonNull(dataFolder, "dataFolder").toPath().toAbsolutePath();
        Path databaseDirectory = pluginDataFolder.resolve("data");
        ensureDirectoryExists(databaseDirectory);
        this.playerDao = new PlayerDAO(databaseDirectory.resolve("data.db"), pluginDataFolder.resolve("data.db"));
        this.playerDao.createTable();
    }

    public PlayerData getPlayer(UUID id) {
        return this.data.computeIfAbsent(id, i -> new PlayerData(i, "en", true, new ArrayList<>(), new ArrayList<>()));
    }

    public synchronized void load() {
        LOGGER.debug("Loading player and category data...");
        long start = System.currentTimeMillis();
        Map<UUID, PlayerData> legacyData = this.playerDao.loadLegacyPlayers();
        Map<UUID, PlayerData> currentData = this.playerDao.loadAllPlayers();

        // Import players missing from the v6 database while always preserving a
        // current row as authoritative when the same UUID exists in both files.
        legacyData.keySet().removeAll(currentData.keySet());
        this.data.clear();
        this.data.putAll(legacyData);
        this.data.putAll(currentData);

        if (!legacyData.isEmpty()) {
            LOGGER.info("Imported {} player record(s) from the pre-v6 database.", legacyData.size());
            this.playerDao.saveAllPlayers(this.data);
        }
        LOGGER.debug("Loaded all data in {}ms", System.currentTimeMillis() - start);
    }

    public synchronized void save() {
        LOGGER.debug("Saving player and category data...");
        long start = System.currentTimeMillis();
        this.playerDao.saveAllPlayers(this.data);
        LOGGER.debug("Saved all data in {}ms", System.currentTimeMillis() - start);
    }

    private static void ensureDirectoryExists(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create player storage directory: " + directory, ex);
        }
    }
}
