package com.bitworksmc.headdb.legacy;

import com.bitworksmc.headdb.api.HeadAPI;
import com.bitworksmc.headdb.api.HeadDatabase;
import com.bitworksmc.headdb.api.model.Head;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

final class LegacyHeadAPI implements HeadAPI {
    private final ExecutorService executor;
    private final HeadDatabase database;

    LegacyHeadAPI(ExecutorService executor, HeadDatabase database) {
        this.executor = executor;
        this.database = database;
    }

    @Override public void awaitReady() { database.awaitReady(); }
    @Override public boolean isReady() { return database.isReady(); }
    @Override public CompletableFuture<List<Head>> onReady() { return database.onReady(); }

    @Override
    public CompletableFuture<List<Head>> searchByName(final String name, final boolean lenient) {
        Objects.requireNonNull(name, "name");
        return CompletableFuture.supplyAsync(() -> {
            List<Head> result = new ArrayList<Head>();
            String query = name.toLowerCase(Locale.ROOT);
            for (Head head : heads()) {
                String candidate = head.getName().toLowerCase(Locale.ROOT);
                if (lenient ? candidate.contains(query) : candidate.equals(query)) {
                    result.add(head);
                }
            }
            return Collections.unmodifiableList(result);
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<Head>> findByName(final String name, final boolean lenient) {
        return searchByName(name, lenient).thenApply(matches ->
                matches.isEmpty() ? Optional.<Head>empty() : Optional.of(matches.get(0)));
    }

    @Override
    public CompletableFuture<Optional<Head>> findById(final int id) {
        return CompletableFuture.supplyAsync(() -> Optional.ofNullable(database.getById(id)), executor);
    }

    @Override
    public CompletableFuture<Optional<Head>> findByTexture(final String texture) {
        return CompletableFuture.supplyAsync(() -> Optional.ofNullable(database.getByTexture(texture)), executor);
    }

    @Override
    public CompletableFuture<List<Head>> findByCategory(final String category) {
        return CompletableFuture.supplyAsync(() -> database.getByCategory(category), executor);
    }

    @Override
    public CompletableFuture<List<Head>> findByTags(final String... tags) {
        return CompletableFuture.supplyAsync(() -> database.getByTags(tags), executor);
    }

    @Override
    public CompletableFuture<List<Head>> getHeads() {
        return CompletableFuture.supplyAsync(() -> heads(), executor);
    }

    @Override
    public List<ItemStack> computeLocalHeads() {
        List<ItemStack> result = new ArrayList<ItemStack>();
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            ItemStack item = localHead(player);
            if (item != null) {
                result.add(item);
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public Optional<ItemStack> computeLocalHead(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        return Optional.ofNullable(localHead(Bukkit.getOfflinePlayer(uniqueId)));
    }

    private ItemStack localHead(OfflinePlayer player) {
        if (player == null || player.getName() == null) {
            return null;
        }
        ItemStack item = LegacyItemFactory.newPlayerHead();
        org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) item.getItemMeta();
        meta.setOwner(player.getName());
        meta.setDisplayName(player.getName());
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public List<String> findKnownCategories() {
        Set<String> categories = new LinkedHashSet<String>();
        for (Head head : heads()) {
            categories.add(head.getCategory());
        }
        return Collections.unmodifiableList(new ArrayList<String>(categories));
    }

    @Override public ExecutorService getExecutor() { return executor; }

    private List<Head> heads() {
        List<Head> result = database.getHeads();
        return result == null ? Collections.<Head>emptyList() : result;
    }
}
