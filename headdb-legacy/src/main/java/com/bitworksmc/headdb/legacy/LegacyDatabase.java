package com.bitworksmc.headdb.legacy;

import com.bitworksmc.headdb.api.HeadDatabase;
import com.bitworksmc.headdb.api.catalog.CatalogStatus;
import com.bitworksmc.headdb.api.catalog.CatalogUpdate;
import com.bitworksmc.headdb.api.catalog.CatalogUpdateListener;
import com.bitworksmc.headdb.api.model.Head;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.CopyOnWriteArrayList;

final class LegacyDatabase implements HeadDatabase {
    private static final java.lang.reflect.Type HEAD_LIST =
            new TypeToken<List<LegacyHead>>() { }.getType();

    private final List<String> sourceUrls;
    private final Executor executor;
    private final Object updateLock = new Object();
    private volatile Snapshot snapshot;
    private volatile CompletableFuture<List<Head>> updateFuture;
    private volatile long lastAttemptEpochMillis;
    private volatile long lastSuccessfulUpdateEpochMillis;
    private volatile String lastError;
    private volatile String activeSource;
    private final CopyOnWriteArrayList<CatalogUpdateListener> updateListeners =
            new CopyOnWriteArrayList<CatalogUpdateListener>();

    LegacyDatabase(String sourceUrl, Executor executor) {
        this(Collections.singletonList(sourceUrl), executor);
    }

    LegacyDatabase(List<String> sourceUrls, Executor executor) {
        this.sourceUrls = new ArrayList<String>();
        if (sourceUrls != null) for (String source : sourceUrls) {
            if (source != null && !source.trim().isEmpty() && !this.sourceUrls.contains(source.trim())) {
                this.sourceUrls.add(source.trim());
            }
        }
        if (this.sourceUrls.isEmpty()) throw new IllegalArgumentException("At least one database URL is required");
        this.executor = executor;
    }

    @Override
    public CompletableFuture<List<Head>> update() {
        synchronized (updateLock) {
            if (updateFuture != null && !updateFuture.isDone()) {
                return updateFuture;
            }
            final Snapshot previous = snapshot;
            lastAttemptEpochMillis = System.currentTimeMillis();
            updateFuture = CompletableFuture.supplyAsync(() -> load(), executor)
                    .whenComplete((heads, failure) -> {
                        if (failure != null) {
                            lastError = rootMessage(failure);
                            return;
                        }
                        lastError = null;
                        lastSuccessfulUpdateEpochMillis = System.currentTimeMillis();
                        CatalogUpdate update = describeUpdate(previous, snapshot);
                        if (update.hasChanges()) notifyListeners(update);
                    });
            return updateFuture;
        }
    }

    private List<Head> load() {
        RuntimeException last = null;
        for (String sourceUrl : sourceUrls) {
            try { return load(sourceUrl); }
            catch (RuntimeException exception) { last = exception; }
        }
        throw new CompletionException("Failed to update HeadDB from every configured source", last);
    }

    private List<Head> load(String sourceUrl) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(sourceUrl).openConnection();
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "HeadDB-Legacy");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);

            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP response code " + status);
            }

            List<LegacyHead> loaded;
            InputStream stream = connection.getInputStream();
            try {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8), 8192);
                loaded = new Gson().fromJson(reader, HEAD_LIST);
            } finally {
                stream.close();
            }
            if (loaded == null || loaded.isEmpty()) {
                throw new IOException("Database payload was empty");
            }

            Snapshot next = Snapshot.create(loaded);
            snapshot = next;
            activeSource = sourceUrl;
            return next.heads;
        } catch (IOException | RuntimeException exception) {
            throw new CompletionException("Failed to update HeadDB from " + sourceUrl, exception);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @Override
    public boolean awaitReady() {
        CompletableFuture<List<Head>> future = updateFuture;
        if (future == null) {
            return false;
        }
        try {
            future.join();
            return true;
        } catch (CompletionException exception) {
            return false;
        }
    }

    @Override
    public boolean isReady() {
        return snapshot != null;
    }

    @Override
    public CompletableFuture<List<Head>> onReady() {
        Snapshot current = snapshot;
        if (current != null) {
            return CompletableFuture.completedFuture(current.heads);
        }
        CompletableFuture<List<Head>> future = updateFuture;
        return future == null ? update() : future;
    }

    @Override
    public List<Head> getHeads() {
        Snapshot current = snapshot;
        return current == null ? null : current.heads;
    }

    @Override
    public List<Head> getByCategory(String category) {
        Snapshot current = snapshot;
        if (current == null || category == null) {
            return Collections.emptyList();
        }
        List<Head> result = current.byCategory.get(category.toLowerCase(Locale.ROOT));
        return result == null ? Collections.<Head>emptyList() : result;
    }

    @Override
    public List<Head> getByTags(String... tags) {
        Snapshot current = snapshot;
        if (current == null || tags == null || tags.length == 0) {
            return Collections.emptyList();
        }
        Set<Head> result = new LinkedHashSet<Head>();
        for (String tag : tags) {
            if (tag == null) {
                continue;
            }
            List<Head> matches = current.byTag.get(tag.toLowerCase(Locale.ROOT));
            if (matches != null) {
                result.addAll(matches);
            }
        }
        return immutableList(result);
    }

    @Override
    public Head getById(int id) {
        Snapshot current = snapshot;
        return current == null ? null : current.byId.get(id);
    }

    @Override
    public Head getByTexture(String texture) {
        Snapshot current = snapshot;
        return current == null || texture == null ? null : current.byTexture.get(texture);
    }

    @Override
    public CatalogStatus getCatalogStatus() {
        Snapshot current = snapshot;
        return new CatalogStatus(current != null, -1, current == null ? 0 : current.heads.size(),
                lastAttemptEpochMillis, lastSuccessfulUpdateEpochMillis, lastError, activeSource);
    }

    @Override
    public AutoCloseable addCatalogUpdateListener(final CatalogUpdateListener listener) {
        if (listener == null) throw new NullPointerException("listener");
        updateListeners.add(listener);
        return new AutoCloseable() {
            @Override public void close() { updateListeners.remove(listener); }
        };
    }

    private CatalogUpdate describeUpdate(Snapshot before, Snapshot after) {
        Map<Integer, Head> previous = before == null
                ? Collections.<Integer, Head>emptyMap() : before.byId;
        Map<Integer, Head> current = after == null
                ? Collections.<Integer, Head>emptyMap() : after.byId;
        List<Integer> added = new ArrayList<Integer>();
        List<Integer> updated = new ArrayList<Integer>();
        List<Integer> removed = new ArrayList<Integer>();
        for (Map.Entry<Integer, Head> entry : current.entrySet()) {
            Head old = previous.get(entry.getKey());
            if (old == null) added.add(entry.getKey());
            else if (!sameHead(old, entry.getValue())) updated.add(entry.getKey());
        }
        for (Integer id : previous.keySet()) if (!current.containsKey(id)) removed.add(id);
        Collections.sort(added); Collections.sort(updated); Collections.sort(removed);
        return new CatalogUpdate(-1, -1, added, updated, removed, System.currentTimeMillis());
    }

    private void notifyListeners(CatalogUpdate update) {
        for (CatalogUpdateListener listener : updateListeners) {
            try { listener.onCatalogUpdate(update); }
            catch (RuntimeException ignored) { }
        }
    }

    private static boolean sameHead(Head left, Head right) {
        return left.getId() == right.getId()
                && java.util.Objects.equals(left.getName(), right.getName())
                && java.util.Objects.equals(left.getTexture(), right.getTexture())
                && java.util.Objects.equals(left.getTextureUrl(), right.getTextureUrl())
                && java.util.Objects.equals(left.getCategory(), right.getCategory())
                && java.util.Objects.equals(left.getTags(), right.getTags());
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static List<Head> immutableList(Collection<? extends Head> source) {
        return Collections.unmodifiableList(new ArrayList<Head>(source));
    }

    private static final class Snapshot {
        private final List<Head> heads;
        private final Map<Integer, Head> byId;
        private final Map<String, Head> byTexture;
        private final Map<String, List<Head>> byCategory;
        private final Map<String, List<Head>> byTag;

        private Snapshot(List<Head> heads, Map<Integer, Head> byId, Map<String, Head> byTexture,
                         Map<String, List<Head>> byCategory, Map<String, List<Head>> byTag) {
            this.heads = heads;
            this.byId = byId;
            this.byTexture = byTexture;
            this.byCategory = byCategory;
            this.byTag = byTag;
        }

        private static Snapshot create(List<LegacyHead> loaded) {
            List<Head> heads = new ArrayList<Head>(loaded.size());
            Map<Integer, Head> byId = new LinkedHashMap<Integer, Head>();
            Map<String, Head> byTexture = new LinkedHashMap<String, Head>();
            Map<String, List<Head>> byCategory = new LinkedHashMap<String, List<Head>>();
            Map<String, List<Head>> byTag = new LinkedHashMap<String, List<Head>>();

            for (LegacyHead head : loaded) {
                head.validate();
                if (byId.put(head.getId(), head) != null) {
                    throw new IllegalArgumentException("Duplicate head ID " + head.getId());
                }
                byTexture.put(head.getTexture(), head);
                heads.add(head);
                add(byCategory, head.getCategory().toLowerCase(Locale.ROOT), head);
                for (String tag : head.getTags()) {
                    if (tag != null) {
                        add(byTag, tag.toLowerCase(Locale.ROOT), head);
                    }
                }
            }
            freezeValues(byCategory);
            freezeValues(byTag);
            return new Snapshot(immutableList(heads), Collections.unmodifiableMap(byId),
                    Collections.unmodifiableMap(byTexture), Collections.unmodifiableMap(byCategory),
                    Collections.unmodifiableMap(byTag));
        }

        private static void add(Map<String, List<Head>> index, String key, Head value) {
            List<Head> values = index.get(key);
            if (values == null) {
                values = new ArrayList<Head>();
                index.put(key, values);
            }
            values.add(value);
        }

        private static void freezeValues(Map<String, List<Head>> index) {
            for (Map.Entry<String, List<Head>> entry : index.entrySet()) {
                entry.setValue(Collections.unmodifiableList(entry.getValue()));
            }
        }
    }
}
