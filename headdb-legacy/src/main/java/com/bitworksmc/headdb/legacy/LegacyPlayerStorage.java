package com.bitworksmc.headdb.legacy;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

final class LegacyPlayerStorage {
    private final File file;
    private final String databaseUrl;
    private final String oldDatabaseUrl;
    private final Logger logger;
    private final Map<UUID, LegacyPlayerData> players = new ConcurrentHashMap<UUID, LegacyPlayerData>();

    LegacyPlayerStorage(File dataFolder, Logger logger) {
        File directory = new File(dataFolder, "data");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Could not create " + directory);
        }
        this.file = new File(directory, "legacy-players.yml");
        this.databaseUrl = "jdbc:sqlite:" + new File(directory, "data.db").getAbsolutePath();
        this.oldDatabaseUrl = "jdbc:sqlite:" + new File(dataFolder, "data.db").getAbsolutePath();
        this.logger = logger;
        try {
            Class.forName("org.sqlite.JDBC");
            Connection connection = DriverManager.getConnection(databaseUrl);
            try {
                connection.createStatement().execute("CREATE TABLE IF NOT EXISTS players (uuid TEXT PRIMARY KEY, language TEXT, favorites TEXT, local_favorites TEXT, sound_enabled INTEGER)");
            } finally { connection.close(); }
        } catch (Exception exception) {
            logger.warning("SQLite initialization failed; YAML fallback will be used: " + exception.getMessage());
        }
    }

    LegacyPlayerData get(UUID id) {
        LegacyPlayerData existing = players.get(id);
        if (existing != null) return existing;
        LegacyPlayerData created = new LegacyPlayerData(id);
        LegacyPlayerData raced = players.putIfAbsent(id, created);
        return raced == null ? created : raced;
    }

    synchronized void load() {
        players.clear();
        if (loadSqlite()) {
            if (players.isEmpty() && new File(oldDatabaseUrl.substring("jdbc:sqlite:".length())).isFile()) {
                importOldDatabase();
                if (!players.isEmpty()) saveSqlite();
            }
            return;
        }
        if (!file.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("players");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                LegacyPlayerData data = new LegacyPlayerData(id);
                String path = "players." + key + ".";
                data.setLanguage(yaml.getString(path + "language", "en"));
                data.setSoundsEnabled(yaml.getBoolean(path + "sounds", true));
                data.setFavorites(yaml.getIntegerList(path + "favorites"));
                data.setLocalFavorites(yaml.getStringList(path + "localFavorites"));
                players.put(id, data);
            } catch (IllegalArgumentException ignored) {
                logger.warning("Ignoring invalid player UUID in " + file.getName() + ": " + key);
            }
        }
    }

    synchronized void save() {
        if (saveSqlite()) return;
        YamlConfiguration yaml = new YamlConfiguration();
        for (LegacyPlayerData data : players.values()) {
            String path = "players." + data.getUniqueId() + ".";
            yaml.set(path + "language", data.getLanguage());
            yaml.set(path + "sounds", data.isSoundsEnabled());
            yaml.set(path + "favorites", data.getFavorites());
            yaml.set(path + "localFavorites", data.getLocalFavoriteStrings());
        }
        try {
            yaml.save(file);
        } catch (IOException exception) {
            logger.severe("Could not save legacy player data: " + exception.getMessage());
        }
    }

    private boolean loadSqlite() {
        try {
            Connection connection = DriverManager.getConnection(databaseUrl);
            try {
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT * FROM players");
                while (rows.next()) {
                    UUID id = UUID.fromString(rows.getString("uuid"));
                    LegacyPlayerData data = new LegacyPlayerData(id);
                    data.setLanguage(rows.getString("language"));
                    int sound = rows.getInt("sound_enabled");
                    data.setSoundsEnabled(rows.wasNull() || sound == 1);
                    data.setFavorites(parseIntegers(rows.getString("favorites")));
                    data.setLocalFavorites(parseStrings(rows.getString("local_favorites")));
                    players.put(id, data);
                }
                rows.close(); statement.close();
            } finally { connection.close(); }
            return true;
        } catch (Exception exception) {
            logger.warning("Could not load SQLite player data: " + exception.getMessage());
            return false;
        }
    }

    private boolean saveSqlite() {
        try {
            Connection connection = DriverManager.getConnection(databaseUrl);
            try {
                connection.setAutoCommit(false);
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT OR REPLACE INTO players (uuid, language, favorites, local_favorites, sound_enabled) VALUES (?, ?, ?, ?, ?)");
                for (LegacyPlayerData data : players.values()) {
                    statement.setString(1, data.getUniqueId().toString());
                    statement.setString(2, data.getLanguage());
                    statement.setString(3, join(data.getFavorites()));
                    statement.setString(4, join(data.getLocalFavoriteStrings()));
                    statement.setInt(5, data.isSoundsEnabled() ? 1 : 0);
                    statement.addBatch();
                }
                statement.executeBatch(); statement.close(); connection.commit();
            } finally { connection.close(); }
            return true;
        } catch (SQLException exception) {
            logger.warning("Could not save SQLite player data: " + exception.getMessage());
            return false;
        }
    }

    private void importOldDatabase() {
        try {
            Connection connection = DriverManager.getConnection(oldDatabaseUrl);
            try {
                ResultSet rows = connection.createStatement().executeQuery(
                        "SELECT uuid, lang, soundEnabled, favorites FROM hdb_players");
                while (rows.next()) {
                    UUID id = UUID.fromString(rows.getString("uuid"));
                    LegacyPlayerData data = new LegacyPlayerData(id);
                    data.setLanguage(rows.getString("lang"));
                    boolean enabled = rows.getBoolean("soundEnabled");
                    data.setSoundsEnabled(rows.wasNull() || enabled);
                    data.setFavorites(parseIntegers(rows.getString("favorites")));
                    players.put(id, data);
                }
                rows.close();
            } finally { connection.close(); }
            logger.info("Imported " + players.size() + " player records from the pre-v6 database.");
        } catch (Exception exception) {
            logger.warning("Could not import the pre-v6 player database: " + exception.getMessage());
        }
    }

    private static List<Integer> parseIntegers(String value) {
        List<Integer> result = new ArrayList<Integer>();
        if (value == null) return result;
        for (String token : value.split("[,|]")) {
            try { result.add(Integer.parseInt(token.trim())); } catch (NumberFormatException ignored) { }
        }
        return result;
    }
    private static List<String> parseStrings(String value) {
        List<String> result = new ArrayList<String>();
        if (value == null) return result;
        for (String token : value.split(",")) if (!token.trim().isEmpty()) result.add(token.trim());
        return result;
    }
    private static String join(List<?> values) {
        StringBuilder result = new StringBuilder();
        for (Object value : values) {
            if (result.length() > 0) result.append(',');
            result.append(value);
        }
        return result.toString();
    }
}
