package com.bitworksmc.headdb.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UtilsTest {

    @Test
    void lenientMatchSupportsCaseInsensitivePartialNames() {
        assertTrue(Utils.matches("Cactus Flower", "cact"));
        assertTrue(Utils.matches("Cactus Flower", "FLOW"));
        assertTrue(Utils.matches("Cactus Flower", " cactus flower "));
    }

    @Test
    void lenientMatchRejectsBlankAndNullQueries() {
        assertFalse(Utils.matches("Cactus Flower", "  "));
        assertFalse(Utils.matches("Cactus Flower", null));
        assertFalse(Utils.matches(null, "cactus"));
    }

    @Test
    void normalizesBukkitNamespacedKeys() {
        assertEquals("gui_custom_category-1.2/path", Utils.normalizeNamespacedKey("GUI_Custom Category-1.2/Path"));
        assertEquals("gui_food___drinks", Utils.normalizeNamespacedKey("gui_Food & Drinks"));
    }

    @Test
    void rejectsNonPositiveChunkSizesInsteadOfLoopingForever() {
        assertThrows(IllegalArgumentException.class, () -> Utils.chunk(java.util.List.of(1), 0));
        assertThrows(IllegalArgumentException.class, () -> Utils.chunk(java.util.List.of(1), -1));
    }
}
