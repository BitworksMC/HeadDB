package com.bitworksmc.headdb.implementation;


import com.bitworksmc.headdb.api.HeadAPI;
import com.bitworksmc.headdb.api.HeadDatabase;
import com.bitworksmc.headdb.api.model.Head;
import com.bitworksmc.headdb.core.factory.ItemFactoryRegistry;
import com.bitworksmc.headdb.core.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link HeadAPI} using BaseHeadDatabase.
 */
public class BaseHeadAPI implements HeadAPI {

    private final ExecutorService executor;
    private final HeadDatabase database;

    public BaseHeadAPI(int workerThreads, HeadDatabase headDatabase) {
        this.executor = Utils.executorService(workerThreads, "HeadAPI Worker");
        this.database = headDatabase;
        this.database.update();
    }

    @Override
    public void awaitReady() {
        database.awaitReady();
    }

    @Override
    public boolean isReady() {
        return database.isReady();
    }

    @Override
    public CompletableFuture<List<Head>> onReady() {
        return database.onReady();
    }

    @NotNull
    @Override
    public CompletableFuture<List<Head>> searchByName(@NotNull String name, boolean lenient) {
        return getHeads().thenApplyAsync(heads ->
                heads.stream()
                        .filter(h -> lenient ? Utils.matches(h.getName(), name) : h.getName().equalsIgnoreCase(name))
                        .collect(Collectors.toList()), executor);
    }

    @NotNull
    @Override
    public CompletableFuture<Optional<Head>> findByName(@NotNull String name, boolean lenient) {
        return getHeads().thenApplyAsync(heads -> heads.stream()
                        .filter(h -> lenient ? Utils.matches(h.getName(), name) : h.getName().equalsIgnoreCase(name))
                        .findAny(), executor);
    }

    @NotNull
    @Override
    public CompletableFuture<Optional<Head>> findById(int id) {
        return CompletableFuture.supplyAsync(() -> Optional.ofNullable(database.getById(id)), executor);
    }

    @NotNull
    @Override
    public CompletableFuture<Optional<Head>> findByTexture(@NotNull String texture) {
        return CompletableFuture.supplyAsync(() -> Optional.ofNullable(database.getByTexture(texture)), executor);
    }

    @NotNull
    @Override
    public CompletableFuture<List<Head>> findByCategory(@NotNull String category) {
        return CompletableFuture.supplyAsync(() -> database.getByCategory(category), executor);
    }

    @NotNull
    @Override
    public CompletableFuture<List<Head>> findByTags(@NotNull String... tags) {
        return CompletableFuture.supplyAsync(() -> database.getByTags(tags), executor);
    }

    @NotNull
    @Override
    public CompletableFuture<List<Head>> getHeads() {
        return CompletableFuture.supplyAsync(() -> {
            List<Head> heads = database.getHeads();
            return heads != null ? heads : Collections.emptyList();
        }, executor);
    }

    @NotNull
    @Override
    public List<String> findKnownCategories() {
        List<Head> heads = database.getHeads();
        if (heads == null) {
            return Collections.emptyList();
        }

        Set<String> result = new LinkedHashSet<>();
        for (Head head : heads) {
            result.add(head.getCategory());
        }
        return List.copyOf(result);
    }

    @NotNull
    @Override
    public List<ItemStack> computeLocalHeads() {
        OfflinePlayer[] players = Bukkit.getOfflinePlayers();
        List<ItemStack> heads = new ArrayList<>();
        for (OfflinePlayer player : players) {
            ItemStack item = ItemFactoryRegistry.get().asItem(player);
            if (item != null) {
                heads.add(item);
            }
        }
        return List.copyOf(heads);
    }

    @NotNull
    @Override
    public Optional<ItemStack> computeLocalHead(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        return Optional.ofNullable(ItemFactoryRegistry.get().asItem(Bukkit.getOfflinePlayer(uniqueId)));
    }

    @Override
    public @NotNull ExecutorService getExecutor() {
        return executor;
    }

}
