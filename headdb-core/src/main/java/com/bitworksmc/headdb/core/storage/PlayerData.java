package com.bitworksmc.headdb.core.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class PlayerData {

    private final UUID uniqueId;
    private String language;
    private List<Integer> favorites;
    private List<UUID> localFavorites;
    private boolean soundEnabled;

    public PlayerData(UUID uniqueId, String language, boolean soundEnabled, List<Integer> favorites, List<UUID> localFavorites) {
        this.uniqueId = uniqueId;
        this.language = language;
        this.soundEnabled = soundEnabled;
        this.favorites = new CopyOnWriteArrayList<>(favorites != null ? favorites : new ArrayList<>());
        this.localFavorites = new CopyOnWriteArrayList<>(localFavorites != null ? localFavorites : new ArrayList<>());
    }

    public void addLocalFavorite(UUID uuid) {
        this.localFavorites.add(uuid);
    }

    public void removeLocalFavorite(UUID uuid) {
        this.localFavorites.remove(uuid);
    }

    public List<UUID> getLocalFavorites() {
        return localFavorites;
    }

    public void setLocalFavorites(List<UUID> localFavorites) {
        this.localFavorites = new CopyOnWriteArrayList<>(localFavorites != null ? localFavorites : new ArrayList<>());
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public void addFavorite(int id) {
        this.favorites.add(id);
    }

    public void removeFavorite(int id) {
        this.favorites.remove((Object) id);
    }

    public void setFavorites(List<Integer> favorites) {
        this.favorites = new CopyOnWriteArrayList<>(favorites != null ? favorites : new ArrayList<>());
    }

    public void setSoundEnabled(boolean soundEnabled) {
        this.soundEnabled = soundEnabled;
    }

    public String getLanguage() {
        return language;
    }

    public List<Integer> getFavorites() {
        return favorites;
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public UUID getUniqueId() {
        return uniqueId;
    }

}
