package com.bitworksmc.headdb.core.storage;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class PlayerData {

    private final UUID uniqueId;
    private volatile String language;
    private volatile CopyOnWriteArrayList<Integer> favorites;
    private volatile CopyOnWriteArrayList<UUID> localFavorites;
    private volatile boolean soundEnabled;

    public PlayerData(UUID uniqueId, String language, boolean soundEnabled, List<Integer> favorites, List<UUID> localFavorites) {
        this.uniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        this.language = normalizeLanguage(language);
        this.soundEnabled = soundEnabled;
        setFavorites(favorites);
        setLocalFavorites(localFavorites);
    }

    public void addLocalFavorite(UUID uuid) {
        if (uuid != null) {
            this.localFavorites.addIfAbsent(uuid);
        }
    }

    public void removeLocalFavorite(UUID uuid) {
        this.localFavorites.remove(uuid);
    }

    public List<UUID> getLocalFavorites() {
        return localFavorites;
    }

    public void setLocalFavorites(List<UUID> localFavorites) {
        this.localFavorites = copyDistinctNonNull(localFavorites);
    }

    public void setLanguage(String language) {
        this.language = normalizeLanguage(language);
    }

    public void addFavorite(int id) {
        this.favorites.addIfAbsent(id);
    }

    public void removeFavorite(int id) {
        this.favorites.remove((Object) id);
    }

    public void setFavorites(List<Integer> favorites) {
        this.favorites = copyDistinctNonNull(favorites);
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

    private static String normalizeLanguage(String language) {
        return language == null || language.isBlank() ? "en" : language;
    }

    private static <T> CopyOnWriteArrayList<T> copyDistinctNonNull(List<T> values) {
        CopyOnWriteArrayList<T> result = new CopyOnWriteArrayList<>();
        if (values != null) {
            for (T value : values) {
                if (value != null) {
                    result.addIfAbsent(value);
                }
            }
        }
        return result;
    }

}
