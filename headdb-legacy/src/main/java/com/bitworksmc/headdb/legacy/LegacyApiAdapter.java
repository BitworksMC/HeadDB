package com.bitworksmc.headdb.legacy;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@SuppressWarnings("deprecation")
final class LegacyApiAdapter implements com.github.thesilentpro.headdb.api.HeadAPI {
    private final com.bitworksmc.headdb.api.HeadAPI delegate;

    LegacyApiAdapter(com.bitworksmc.headdb.api.HeadAPI delegate) { this.delegate = delegate; }
    @Override public void awaitReady() { delegate.awaitReady(); }
    @Override public boolean isReady() { return delegate.isReady(); }
    @Override public CompletableFuture<List<com.github.thesilentpro.headdb.api.model.Head>> onReady() {
        return delegate.onReady().thenApply(LegacyApiAdapter::heads);
    }
    @Override public CompletableFuture<List<com.github.thesilentpro.headdb.api.model.Head>> searchByName(String name, boolean lenient) {
        return delegate.searchByName(name, lenient).thenApply(LegacyApiAdapter::heads);
    }
    @Override public CompletableFuture<Optional<com.github.thesilentpro.headdb.api.model.Head>> findByName(String name, boolean lenient) {
        return delegate.findByName(name, lenient).thenApply(LegacyApiAdapter::head);
    }
    @Override public CompletableFuture<Optional<com.github.thesilentpro.headdb.api.model.Head>> findById(int id) {
        return delegate.findById(id).thenApply(LegacyApiAdapter::head);
    }
    @Override public CompletableFuture<Optional<com.github.thesilentpro.headdb.api.model.Head>> findByTexture(String texture) {
        return delegate.findByTexture(texture).thenApply(LegacyApiAdapter::head);
    }
    @Override public CompletableFuture<List<com.github.thesilentpro.headdb.api.model.Head>> findByCategory(String category) {
        return delegate.findByCategory(category).thenApply(LegacyApiAdapter::heads);
    }
    @Override public CompletableFuture<List<com.github.thesilentpro.headdb.api.model.Head>> findByTags(String... tags) {
        return delegate.findByTags(tags).thenApply(LegacyApiAdapter::heads);
    }
    @Override public CompletableFuture<List<com.github.thesilentpro.headdb.api.model.Head>> getHeads() {
        return delegate.getHeads().thenApply(LegacyApiAdapter::heads);
    }
    @Override public List<ItemStack> computeLocalHeads() { return delegate.computeLocalHeads(); }
    @Override public Optional<ItemStack> computeLocalHead(UUID uniqueId) { return delegate.computeLocalHead(uniqueId); }
    @Override public List<String> findKnownCategories() { return delegate.findKnownCategories(); }
    @Override public ExecutorService getExecutor() { return delegate.getExecutor(); }

    private static List<com.github.thesilentpro.headdb.api.model.Head> heads(
            List<? extends com.github.thesilentpro.headdb.api.model.Head> values) {
        return new ArrayList<com.github.thesilentpro.headdb.api.model.Head>(values);
    }
    private static Optional<com.github.thesilentpro.headdb.api.model.Head> head(
            Optional<? extends com.github.thesilentpro.headdb.api.model.Head> value) {
        return value.isPresent() ? Optional.<com.github.thesilentpro.headdb.api.model.Head>of(value.get())
                : Optional.<com.github.thesilentpro.headdb.api.model.Head>empty();
    }
}
