package com.bitworksmc.headdb.core.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class PlayerDAO {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerDAO.class);
    private static final Path DEFAULT_DATA_FOLDER = Path.of("plugins", "HeadDB");

    private final String databaseUrl;
    private final Path legacyDatabasePath;

    public PlayerDAO() {
        this(DEFAULT_DATA_FOLDER.resolve("data").resolve("data.db"), DEFAULT_DATA_FOLDER.resolve("data.db"));
    }

    public PlayerDAO(Path databasePath, Path legacyDatabasePath) {
        this.databaseUrl = "jdbc:sqlite:" + Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath();
        this.legacyDatabasePath = Objects.requireNonNull(legacyDatabasePath, "legacyDatabasePath").toAbsolutePath();
    }

    public void createTable() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(SqlUtils.CREATE_TABLE);
        } catch (SQLException ex) {
            LOGGER.error("Failed to create table", ex);
        }
    }

    public void saveAllPlayers(Map<UUID, PlayerData> dataMap) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement stmt = conn.prepareStatement(SqlUtils.INSERT_OR_REPLACE)) {
                // Take a stable snapshot of the map. Individual PlayerData fields are
                // backed by thread-safe/volatile values and are snapshotted below.
                List<PlayerData> players = new ArrayList<>(dataMap.values());

                for (PlayerData data : players) {
                    stmt.setString(1, data.getUniqueId().toString());
                    stmt.setString(2, data.getLanguage());

                    String favorites = data.getFavorites().stream()
                            .filter(Objects::nonNull)
                            .map(String::valueOf)
                            .collect(Collectors.joining(","));
                    stmt.setString(3, favorites);

                    String localFavs = data.getLocalFavorites().stream()
                            .filter(Objects::nonNull)
                            .map(UUID::toString)
                            .collect(Collectors.joining(","));

                    stmt.setString(4, localFavs);
                    stmt.setInt(5, data.isSoundEnabled() ? 1 : 0);

                    stmt.addBatch();
                }

                stmt.executeBatch();
                conn.commit();
            } catch (SQLException ex) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackException) {
                    ex.addSuppressed(rollbackException);
                }
                throw ex;
            }

        } catch (SQLException ex) {
            LOGGER.error("Failed to save players", ex);
        }
    }

    public Map<UUID, PlayerData> loadAllPlayers() {
        Map<UUID, PlayerData> dataMap = new HashMap<>();

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(SqlUtils.SELECT_ALL)) {

            while (rs.next()) {
                String rawUuid = rs.getString("uuid");
                try {
                    UUID uuid = UUID.fromString(rawUuid);
                    String lang = rs.getString("language");
                    int storedSound = rs.getInt("sound_enabled");
                    boolean sound = rs.wasNull() || storedSound == 1;
                    List<Integer> favorites = parseIntegerList(rs.getString("favorites"), uuid);
                    List<UUID> localFavorites = parseUuidList(rs.getString("local_favorites"), uuid);

                    dataMap.put(uuid, new PlayerData(uuid, lang, sound, favorites, localFavorites));
                } catch (IllegalArgumentException ex) {
                    // One damaged row must not prevent every other player's data from
                    // loading or stop the plugin from enabling.
                    LOGGER.warn("Skipping player data row with invalid UUID '{}': {}", rawUuid, ex.getMessage());
                }
            }

        } catch (SQLException ex) {
            LOGGER.error("Failed to load players", ex);
        }

        return dataMap;
    }

    /**
     * Imports the pre-v6 player database without modifying it. The caller is
     * responsible for merging these rows beneath current-format data.
     */
    public Map<UUID, PlayerData> loadLegacyPlayers() {
        if (!Files.isRegularFile(legacyDatabasePath)) {
            return new HashMap<>();
        }
        return loadLegacyPlayers("jdbc:sqlite:" + legacyDatabasePath);
    }

    Map<UUID, PlayerData> loadLegacyPlayers(String databaseUrl) {
        Map<UUID, PlayerData> dataMap = new HashMap<>();

        try (Connection conn = DriverManager.getConnection(databaseUrl)) {
            // SQLite's read-only pragma prevents an import bug from modifying the
            // user's only legacy copy. It still allows reading an existing file.
            try (Statement readOnly = conn.createStatement()) {
                readOnly.execute("PRAGMA query_only = ON");
            }

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(SqlUtils.SELECT_LEGACY_PLAYERS)) {
                while (rs.next()) {
                    String rawUuid = rs.getString("uuid");
                    try {
                        UUID uuid = UUID.fromString(rawUuid);
                        List<Integer> favorites = parseIntegerList(rs.getString("favorites"), uuid);
                        boolean soundEnabled = rs.getBoolean("soundEnabled");
                        if (rs.wasNull()) {
                            soundEnabled = true;
                        }
                        dataMap.put(uuid, new PlayerData(
                                uuid,
                                rs.getString("lang"),
                                soundEnabled,
                                favorites,
                                List.of()
                        ));
                    } catch (IllegalArgumentException ex) {
                        LOGGER.warn("Skipping legacy player data row with invalid UUID '{}': {}", rawUuid, ex.getMessage());
                    }
                }
            }
        } catch (SQLException ex) {
            LOGGER.warn("Could not import legacy player data from {}: {}", databaseUrl, ex.getMessage());
            LOGGER.debug("Detailed legacy player import error", ex);
        }

        return dataMap;
    }

    static List<Integer> parseIntegerList(String value, UUID playerId) {
        if (value == null || value.isBlank()) {
            return new ArrayList<>();
        }

        List<Integer> result = new ArrayList<>();
        // Current data uses commas; HeadDB v5 and earlier used pipes.
        for (String token : value.split("[,|]")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                result.add(Integer.parseInt(trimmed));
            } catch (NumberFormatException ex) {
                LOGGER.warn("Ignoring invalid favorite head ID '{}' for player {}", trimmed, playerId);
            }
        }
        return result;
    }

    static List<UUID> parseUuidList(String value, UUID playerId) {
        if (value == null || value.isBlank()) {
            return new ArrayList<>();
        }

        List<UUID> result = new ArrayList<>();
        for (String token : value.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                result.add(UUID.fromString(trimmed));
            } catch (IllegalArgumentException ex) {
                LOGGER.warn("Ignoring invalid local favorite UUID '{}' for player {}", trimmed, playerId);
            }
        }
        return result;
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(databaseUrl);
    }
}
