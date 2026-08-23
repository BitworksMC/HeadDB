package com.bitworksmc.headdb.core.storage;

import com.bitworksmc.headdb.core.config.Config;
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
    private final PlayerDAO localMigrationDao;

    public PlayerStorage() {
        this(new File("plugins", "HeadDB"));
    }

    public PlayerStorage(File dataFolder) {
        this(dataFolder, null);
    }

    public PlayerStorage(File dataFolder, Config config) {
        Path pluginDataFolder = Objects.requireNonNull(dataFolder, "dataFolder").toPath().toAbsolutePath();
        Path databaseDirectory = pluginDataFolder.resolve("data");
        ensureDirectoryExists(databaseDirectory);
        boolean mysql = config != null && "MYSQL".equals(config.getPlayerStorageBackend());
        this.playerDao = mysql
                ? new PlayerDAO(config.getPlayerStorageJdbcUrl(), config.getPlayerStorageUsername(),
                        config.getPlayerStoragePassword(), pluginDataFolder.resolve("data.db"))
                : new PlayerDAO(databaseDirectory.resolve("data.db"), pluginDataFolder.resolve("data.db"));
        this.localMigrationDao = mysql && Files.isRegularFile(databaseDirectory.resolve("data.db"))
                ? new PlayerDAO(databaseDirectory.resolve("data.db"), pluginDataFolder.resolve("data.db"))
                : null;
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
        boolean importedCurrentSqlite = false;
        if (currentData.isEmpty() && localMigrationDao != null) {
            currentData.putAll(localMigrationDao.loadAllPlayers());
            importedCurrentSqlite = !currentData.isEmpty();
        }

        // Import players missing from the v6 database while always preserving a
        // current row as authoritative when the same UUID exists in both files.
        legacyData.keySet().removeAll(currentData.keySet());
        this.data.clear();
        this.data.putAll(legacyData);
        this.data.putAll(currentData);

        if (importedCurrentSqlite) {
            LOGGER.info("Imported {} player record(s) from local SQLite into MySQL.", currentData.size());
        }
        if (!legacyData.isEmpty()) {
            LOGGER.info("Imported {} player record(s) from the pre-v6 database.", legacyData.size());
        }
        if (!legacyData.isEmpty() || importedCurrentSqlite) {
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
