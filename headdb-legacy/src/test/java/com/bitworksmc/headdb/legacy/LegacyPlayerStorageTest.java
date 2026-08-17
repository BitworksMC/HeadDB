package com.bitworksmc.headdb.legacy;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.Arrays;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LegacyPlayerStorageTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void persistsModernCompatiblePlayerSchema() throws Exception {
        UUID player = UUID.randomUUID();
        UUID local = UUID.randomUUID();
        LegacyPlayerStorage first = new LegacyPlayerStorage(temporary.newFolder("HeadDB"), Logger.getAnonymousLogger());
        LegacyPlayerData data = first.get(player);
        data.setLanguage("fr");
        data.setSoundsEnabled(false);
        data.toggleFavorite(42);
        data.toggleLocalFavorite(local);
        first.save();

        LegacyPlayerStorage second = new LegacyPlayerStorage(temporary.getRoot().toPath().resolve("HeadDB").toFile(), Logger.getAnonymousLogger());
        second.load();
        LegacyPlayerData loaded = second.get(player);
        assertEquals("fr", loaded.getLanguage());
        assertFalse(loaded.isSoundsEnabled());
        assertEquals(Arrays.asList(42), loaded.getFavorites());
        assertEquals(Arrays.asList(local), loaded.getLocalFavorites());
    }

    @Test
    public void togglesFavoritesWithoutDuplicates() {
        LegacyPlayerData data = new LegacyPlayerData(UUID.randomUUID());
        assertTrue(data.toggleFavorite(7));
        assertTrue(data.isFavorite(7));
        assertFalse(data.toggleFavorite(7));
        assertFalse(data.isFavorite(7));
    }

    @Test
    public void comparesReleaseVersions() {
        assertTrue(LegacyUpdateChecker.newer("v6.0.4", "6.0.3"));
        assertFalse(LegacyUpdateChecker.newer("v6.0.3", "6.0.3"));
        assertFalse(LegacyUpdateChecker.newer("v5.9.9", "6.0.3"));
    }
}
