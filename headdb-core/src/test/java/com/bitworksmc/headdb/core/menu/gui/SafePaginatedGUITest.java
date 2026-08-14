package com.bitworksmc.headdb.core.menu.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SafePaginatedGUITest {

    @Test
    void clampsTrackedPagesToCurrentBounds() {
        assertEquals(-1, SafePaginatedGUI.clampPage(0, 0));
        assertEquals(0, SafePaginatedGUI.clampPage(null, 3));
        assertEquals(0, SafePaginatedGUI.clampPage(-4, 3));
        assertEquals(1, SafePaginatedGUI.clampPage(1, 3));
        assertEquals(2, SafePaginatedGUI.clampPage(99, 3));
    }
}
