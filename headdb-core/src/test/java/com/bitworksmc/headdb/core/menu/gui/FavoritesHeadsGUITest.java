package com.bitworksmc.headdb.core.menu.gui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FavoritesHeadsGUITest {

    @Test
    void databaseAndLocalFavoritesSharePageCapacity() {
        assertEquals(
                List.of(
                        new FavoritesHeadsGUI.PageRange(0, 36, 0, 0),
                        new FavoritesHeadsGUI.PageRange(36, 40, 0, 32),
                        new FavoritesHeadsGUI.PageRange(40, 40, 32, 40)
                ),
                FavoritesHeadsGUI.calculatePageRanges(40, 40, 36)
        );
    }

    @Test
    void emptyFavoritesHaveNoPages() {
        assertEquals(List.of(), FavoritesHeadsGUI.calculatePageRanges(0, 0, 36));
    }

    @Test
    void rejectsNonPositivePageSize() {
        assertThrows(IllegalArgumentException.class, () ->
                FavoritesHeadsGUI.calculatePageRanges(1, 1, 0));
    }
}
