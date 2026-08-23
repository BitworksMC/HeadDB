package com.bitworksmc.headdb.core.menu.registry;

import com.github.thesilentpro.grim.gui.registry.GUIRegistry;
import org.bukkit.NamespacedKey;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Region-thread-safe per-GUI navigation state. */
public final class ConcurrentGUIRegistry<T> implements GUIRegistry<T> {
    private final Map<UUID, Map<NamespacedKey, T>> pageTracker = new ConcurrentHashMap<>();

    @Override
    public void setCurrentPage(UUID playerId, NamespacedKey key, T page) {
        pageTracker.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>()).put(key, page);
    }

    @Override
    public T getCurrentPage(UUID playerId, NamespacedKey key, T fallback) {
        return pageTracker.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(key, ignored -> fallback);
    }

    @Override
    public Optional<T> getCurrentPage(UUID playerId, NamespacedKey key) {
        return getData(playerId).map(data -> data.get(key));
    }

    @Override
    public Optional<Map<NamespacedKey, T>> getData(UUID playerId) {
        return Optional.ofNullable(pageTracker.get(playerId));
    }
}
