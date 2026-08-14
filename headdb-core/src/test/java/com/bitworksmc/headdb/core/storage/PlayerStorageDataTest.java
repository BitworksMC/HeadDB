package com.bitworksmc.headdb.core.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerStorageDataTest {

    private static final UUID PLAYER_ID = UUID.fromString("7a5949e0-ccde-43ad-a47a-28a28fd71046");
    private static final UUID LOCAL_HEAD_ID = UUID.fromString("9101cbef-0338-46a2-b7ef-bc81321cc733");

    @TempDir
    Path tempDirectory;

    @Test
    void malformedFavoritesDoNotDiscardValidEntries() {
        assertEquals(
                List.of(12, 34, 56),
                PlayerDAO.parseIntegerList("12,broken, 34,,56", PLAYER_ID)
        );
    }

    @Test
    void malformedLocalFavoritesDoNotDiscardValidEntries() {
        assertEquals(
                List.of(LOCAL_HEAD_ID),
                PlayerDAO.parseUuidList(LOCAL_HEAD_ID + ",not-a-uuid", PLAYER_ID)
        );
    }

    @Test
    void playerDataNormalizesDefaultsAndPreventsDuplicateFavorites() {
        PlayerData data = new PlayerData(
                PLAYER_ID,
                null,
                true,
                List.of(12, 12),
                List.of(LOCAL_HEAD_ID, LOCAL_HEAD_ID)
        );

        data.addFavorite(12);
        data.addLocalFavorite(LOCAL_HEAD_ID);
        data.addLocalFavorite(null);

        assertEquals("en", data.getLanguage());
        assertEquals(List.of(12), data.getFavorites());
        assertEquals(List.of(LOCAL_HEAD_ID), data.getLocalFavorites());
        assertFalse(data.getLocalFavorites().contains(null));
    }

    @Test
    void importsValidLegacyRowsWhileSkippingMalformedData() throws Exception {
        String databaseUrl = "jdbc:sqlite:" + tempDirectory.resolve("legacy.db");
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE hdb_players (
                        uuid TEXT,
                        lang TEXT,
                        soundEnabled BOOLEAN,
                        favorites TEXT
                    )
                    """);
            statement.execute("INSERT INTO hdb_players VALUES ('" + PLAYER_ID + "', 'fr', 0, '12|bad|34')");
            statement.execute("INSERT INTO hdb_players VALUES ('not-a-uuid', 'en', 1, '56')");
        }

        Map<UUID, PlayerData> imported = new PlayerDAO().loadLegacyPlayers(databaseUrl);

        assertEquals(1, imported.size());
        assertEquals("fr", imported.get(PLAYER_ID).getLanguage());
        assertFalse(imported.get(PLAYER_ID).isSoundEnabled());
        assertEquals(List.of(12, 34), imported.get(PLAYER_ID).getFavorites());
    }

    @Test
    void storesDataUnderTheProvidedPluginDataFolder() {
        Path pluginDataFolder = tempDirectory.resolve("custom-plugin-folder");
        PlayerStorage storage = new PlayerStorage(pluginDataFolder.toFile());
        storage.getPlayer(PLAYER_ID).addFavorite(42);
        storage.getPlayer(PLAYER_ID).setSoundEnabled(false);
        storage.save();

        assertTrue(java.nio.file.Files.isRegularFile(pluginDataFolder.resolve("data").resolve("data.db")));

        PlayerStorage reloaded = new PlayerStorage(pluginDataFolder.toFile());
        reloaded.load();
        assertEquals(List.of(42), reloaded.getPlayer(PLAYER_ID).getFavorites());
        assertFalse(reloaded.getPlayer(PLAYER_ID).isSoundEnabled());
    }
}
