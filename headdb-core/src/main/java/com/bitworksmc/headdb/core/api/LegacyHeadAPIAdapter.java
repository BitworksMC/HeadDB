package com.bitworksmc.headdb.core.api;

import com.bitworksmc.headdb.api.HeadAPI;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Bridges the modern HeadAPI namespace to the legacy namespace for backwards compatibility.
 */
@SuppressWarnings("removal")
public final class LegacyHeadAPIAdapter implements com.github.thesilentpro.headdb.api.HeadAPI {

    private final HeadAPI delegate;

    public LegacyHeadAPIAdapter(HeadAPI delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void awaitReady() {
        delegate.awaitReady();
    }

    @Override
    public boolean isReady() {
        return delegate.isReady();
    }

    @Override
    public CompletableFuture<List<com.github.thesilentpro.headdb.api.model.Head>> onReady() {
        return delegate.onReady().thenApply(this::asLegacyHeads);
    }

    @NotNull
    @Override
    public CompletableFuture<List<com.github.thesilentpro.headdb.api.model.Head>> searchByName(@NotNull String name, boolean lenient) {
        return delegate.searchByName(name, lenient).thenApply(this::asLegacyHeads);
    }

    @NotNull
    @Override
    public CompletableFuture<Optional<com.github.thesilentpro.headdb.api.model.Head>> findByName(@NotNull String name, boolean lenient) {
        return delegate.findByName(name, lenient).thenApply(this::asLegacyHead);
    }

    @NotNull
    @Override
    public CompletableFuture<Optional<com.github.thesilentpro.headdb.api.model.Head>> findById(int id) {
        return delegate.findById(id).thenApply(this::asLegacyHead);
    }

    @NotNull
    @Override
    public CompletableFuture<Optional<com.github.thesilentpro.headdb.api.model.Head>> findByTexture(@NotNull String texture) {
        return delegate.findByTexture(texture).thenApply(this::asLegacyHead);
    }

    @NotNull
    @Override
    public CompletableFuture<List<com.github.thesilentpro.headdb.api.model.Head>> findByCategory(@NotNull String category) {
        return delegate.findByCategory(category).thenApply(this::asLegacyHeads);
    }

    @NotNull
    @Override
    public CompletableFuture<List<com.github.thesilentpro.headdb.api.model.Head>> findByTags(@NotNull String... tags) {
        return delegate.findByTags(tags).thenApply(this::asLegacyHeads);
    }

    @NotNull
    @Override
    public CompletableFuture<List<com.github.thesilentpro.headdb.api.model.Head>> getHeads() {
        return delegate.getHeads().thenApply(this::asLegacyHeads);
    }

    @NotNull
    @Override
    public List<ItemStack> computeLocalHeads() {
        return delegate.computeLocalHeads();
    }

    @NotNull
    @Override
    public Optional<ItemStack> computeLocalHead(UUID uniqueId) {
        return delegate.computeLocalHead(uniqueId);
    }

    @NotNull
    @Override
    public List<String> findKnownCategories() {
        return delegate.findKnownCategories();
    }

    @NotNull
    @Override
    public ExecutorService getExecutor() {
        return delegate.getExecutor();
    }

    private List<com.github.thesilentpro.headdb.api.model.Head> asLegacyHeads(List<? extends com.bitworksmc.headdb.api.model.Head> heads) {
        return new ArrayList<>(heads);
    }

    private Optional<com.github.thesilentpro.headdb.api.model.Head> asLegacyHead(Optional<? extends com.bitworksmc.headdb.api.model.Head> head) {
        return head.map(h -> h);
    }
}
