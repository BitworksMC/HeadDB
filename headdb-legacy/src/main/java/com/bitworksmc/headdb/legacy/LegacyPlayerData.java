package com.bitworksmc.headdb.legacy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class LegacyPlayerData {
    private final UUID uniqueId;
    private String language = "en";
    private boolean soundsEnabled = true;
    private final Set<Integer> favorites = new LinkedHashSet<Integer>();
    private final Set<UUID> localFavorites = new LinkedHashSet<UUID>();

    LegacyPlayerData(UUID uniqueId) {
        this.uniqueId = uniqueId;
    }

    UUID getUniqueId() { return uniqueId; }
    String getLanguage() { return language; }
    void setLanguage(String language) {
        this.language = language == null || language.trim().isEmpty() ? "en" : language;
    }
    boolean isSoundsEnabled() { return soundsEnabled; }
    void setSoundsEnabled(boolean enabled) { soundsEnabled = enabled; }
    synchronized boolean isFavorite(int id) { return favorites.contains(id); }
    synchronized boolean toggleFavorite(int id) {
        if (favorites.remove(id)) return false;
        favorites.add(id);
        return true;
    }
    synchronized List<Integer> getFavorites() { return new ArrayList<Integer>(favorites); }
    synchronized void setFavorites(List<Integer> values) {
        favorites.clear();
        if (values != null) favorites.addAll(values);
    }
    synchronized boolean isLocalFavorite(UUID id) { return localFavorites.contains(id); }
    synchronized boolean toggleLocalFavorite(UUID id) {
        if (localFavorites.remove(id)) return false;
        localFavorites.add(id);
        return true;
    }
    synchronized List<UUID> getLocalFavorites() { return new ArrayList<UUID>(localFavorites); }
    synchronized void setLocalFavorites(List<String> values) {
        localFavorites.clear();
        if (values == null) return;
        for (String value : values) {
            try { localFavorites.add(UUID.fromString(value)); } catch (IllegalArgumentException ignored) { }
        }
    }
    synchronized List<String> getLocalFavoriteStrings() {
        List<String> values = new ArrayList<String>();
        for (UUID id : localFavorites) values.add(id.toString());
        return Collections.unmodifiableList(values);
    }
}
