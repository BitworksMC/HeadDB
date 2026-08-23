package com.bitworksmc.headdb.implementation;

import com.bitworksmc.headdb.api.HeadDatabase;
import com.bitworksmc.headdb.api.catalog.CatalogStatus;
import com.bitworksmc.headdb.api.catalog.CatalogUpdate;
import com.bitworksmc.headdb.api.catalog.CatalogUpdateListener;
import com.bitworksmc.headdb.api.model.Head;
import com.bitworksmc.headdb.implementation.model.HeadMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

public class BaseHeadDatabase implements HeadDatabase {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseHeadDatabase.class);

    private static final Gson GSON = new GsonBuilder().registerTypeAdapter(Head.class, new HeadMapper()).create();
    private static final String DEFAULT_SOURCE_URL = "https://headdb.net/api/v1/catalog/snapshot";
    private static final String DEFAULT_FALLBACK_SOURCE_URL = "https://raw.githubusercontent.com/BitworksMC/HeadDB/refs/heads/master/heads.json";
    private static final String DEFAULT_SYNC_URL = "https://headdb.net/api/v1/catalog/changes";
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int READ_TIMEOUT_MILLIS = 30_000;
    private final Executor executor;
    private final List<String> sourceUrls;
    private final @Nullable String syncUrl;
    private final @Nullable Path cachePath;
    private final EnumSet<Index> indexes;
    private final Object updateLock = new Object();

    /**
     * A complete, immutable database view. Publishing one object prevents readers from
     * observing heads from one update alongside indexes from another update.
     */
    private volatile Snapshot snapshot;
    private volatile int catalogRevision = -1;
    private volatile long lastAttemptEpochMillis;
    private volatile long lastSuccessfulUpdateEpochMillis;
    private volatile String lastError;
    private volatile String activeSource;
    private final CopyOnWriteArrayList<CatalogUpdateListener> updateListeners = new CopyOnWriteArrayList<>();

    // track the latest load
    private volatile CompletableFuture<List<Head>> lastUpdateFuture;

    public BaseHeadDatabase(@Nullable Executor executor, @Nullable List<String> sourceUrls, @Nullable Index... indexes) {
        this(executor, sourceUrls, DEFAULT_SYNC_URL, null, indexes);
    }

    public BaseHeadDatabase(
            @Nullable Executor executor,
            @Nullable List<String> sourceUrls,
            @Nullable String syncUrl,
            @Nullable Path cachePath,
            @Nullable Index... indexes
    ) {
        this.executor = executor != null ? executor : Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "Head Database Worker");
            thread.setDaemon(true);
            return thread;
        });
        this.sourceUrls = normalizeSourceUrls(sourceUrls);
        this.syncUrl = syncUrl == null || syncUrl.isBlank() ? null : syncUrl.trim();
        this.cachePath = cachePath;
        this.indexes = indexes == null || indexes.length == 0
                ? EnumSet.noneOf(Index.class)
                : EnumSet.copyOf(Arrays.asList(indexes));
    }

    public BaseHeadDatabase(@Nullable Executor executor, @Nullable Index... indexes) {
        this(executor, null, indexes);
    }

    public BaseHeadDatabase(@Nullable Executor executor) {
        this(executor, (Index[]) null);
    }

    public BaseHeadDatabase(@Nullable Index... indexes) {
        this(null, indexes);
    }

    public BaseHeadDatabase() {
        this(null, (Index[]) null);
    }

    @Override
    public CompletableFuture<List<Head>> update() {
        synchronized (updateLock) {
            CompletableFuture<List<Head>> currentUpdate = lastUpdateFuture;
            if (currentUpdate != null && !currentUpdate.isDone()) {
                return currentUpdate;
            }

            Snapshot previousSnapshot = snapshot;
            int previousRevision = catalogRevision;
            lastAttemptEpochMillis = System.currentTimeMillis();
            lastUpdateFuture = CompletableFuture.supplyAsync(this::loadDatabase, executor)
                    .whenComplete((heads, failure) -> {
                        if (failure != null) {
                            lastError = rootMessage(failure);
                            return;
                        }
                        lastError = null;
                        lastSuccessfulUpdateEpochMillis = System.currentTimeMillis();
                        CatalogUpdate update = describeUpdate(previousSnapshot, snapshot, previousRevision, catalogRevision);
                        if (update.hasChanges() || previousRevision != catalogRevision) notifyUpdateListeners(update);
                    });
            return lastUpdateFuture;
        }
    }

    private List<Head> loadDatabase() {
        boolean restoredCache = snapshot == null && restoreCatalogCache();
        if (snapshot != null && catalogRevision >= 0 && syncUrl != null) {
            try {
                return loadChanges();
            } catch (IOException | RuntimeException ex) {
                LOGGER.warn("Incremental catalog sync failed: {}. Trying a complete snapshot.", ex.getMessage());
                LOGGER.debug("Detailed incremental catalog sync error", ex);
            }
        }

        try {
            return loadFullSnapshot();
        } catch (CompletionException ex) {
            if (restoredCache && snapshot != null) {
                LOGGER.warn("Remote catalog is unavailable; using the saved catalog revision {}.", catalogRevision);
                return snapshot.heads();
            }
            throw ex;
        }
    }

    private List<Head> loadFullSnapshot() {
        LOGGER.debug("Fetching heads...");
        long start = System.currentTimeMillis();
        Exception lastException = null;

        for (String sourceUrl : sourceUrls) {
            try {
                FetchedCatalog fetched = fetchHeads(sourceUrl);
                Snapshot loadedSnapshot = buildSnapshot(fetched.heads());
                this.snapshot = loadedSnapshot;
                this.catalogRevision = fetched.revision();
                this.activeSource = sourceUrl;
                persistCatalogCache(loadedSnapshot.heads(), fetched.revision());

                long elapsed = System.currentTimeMillis() - start;
                LOGGER.debug("Update took {} seconds ({}ms total)", TimeUnit.MILLISECONDS.toSeconds(elapsed), elapsed);
                return loadedSnapshot.heads();
            } catch (IOException | RuntimeException ex) {
                lastException = ex;
                LOGGER.warn("Failed to load heads from '{}': {}", sourceUrl, ex.getMessage());
                LOGGER.debug("Detailed error while loading heads from '{}'", sourceUrl, ex);
            }
        }

        LOGGER.error("Failed to update heads from all configured sources; keeping the previous database snapshot.");
        throw new CompletionException("Failed to update heads from all configured sources", lastException);
    }

    private FetchedCatalog fetchHeads(String sourceUrl) throws IOException {
        URL url = URI.create(sourceUrl).toURL();
        if (!(url.openConnection() instanceof HttpURLConnection request)) {
            throw new IOException("Unsupported database URL protocol: " + url.getProtocol());
        }

        request.setRequestProperty("Accept", "application/json");
        request.setRequestProperty("Accept-Encoding", "gzip");
        request.setRequestProperty("User-Agent", "HeadDB");
        request.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        request.setReadTimeout(READ_TIMEOUT_MILLIS);
        request.setInstanceFollowRedirects(true);

        try {
            long connectStart = System.currentTimeMillis();
            request.connect();
            int responseCode = request.getResponseCode();
            long connectTime = System.currentTimeMillis() - connectStart;
            LOGGER.debug("Connected to '{}' in {}ms (Response code: {})", sourceUrl, connectTime, responseCode);

            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP response code " + responseCode);
            }

            long readStart = System.currentTimeMillis();
            StringBuilder rawData = new StringBuilder();
            int lineCount = 0;

            try (InputStream raw = request.getInputStream();
                 InputStream in = isGzipEncoded(request.getContentEncoding()) ? new GZIPInputStream(raw) : raw;
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 8192)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    rawData.append(line);
                    lineCount++;
                }
            }

            long readTime = System.currentTimeMillis() - readStart;
            LOGGER.debug("Finished reading {} lines from '{}' in {}ms", lineCount, sourceUrl, readTime);

            long parseStart = System.currentTimeMillis();
            List<Head> fetchedHeads = GSON.fromJson(rawData.toString(), HeadMapper.HEADS_LIST_TYPE);

            if (fetchedHeads == null || fetchedHeads.isEmpty()) {
                throw new IOException("Database payload was empty");
            }

            long parseTime = System.currentTimeMillis() - parseStart;
            LOGGER.debug("Parsed {} heads from '{}' in {}ms", fetchedHeads.size(), sourceUrl, parseTime);
            int revision = "1".equals(request.getHeaderField("X-Catalog-Schema"))
                    ? parseRevision(request.getHeaderField("X-Catalog-Revision"))
                    : -1;
            return new FetchedCatalog(fetchedHeads, revision);
        } finally {
            request.disconnect();
        }
    }

    private List<Head> loadChanges() throws IOException {
        Snapshot current = Objects.requireNonNull(snapshot, "snapshot");
        this.activeSource = syncUrl;
        int fromRevision = catalogRevision;
        int toRevision = -1;
        String cursor = null;
        boolean changed = false;
        Map<Integer, Head> headsById = new HashMap<>(Math.max(16, current.heads().size() * 2));
        for (Head head : current.heads()) {
            headsById.put(head.getId(), head);
        }

        do {
            CatalogSyncResponse response = fetchChanges(fromRevision, cursor);
            if (response.schema != 1 || response.fromRevision != fromRevision) {
                throw new IOException("Unsupported or inconsistent catalog sync response");
            }
            if (toRevision < 0) {
                toRevision = response.toRevision;
            } else if (toRevision != response.toRevision) {
                throw new IOException("Catalog revision changed during pagination");
            }
            if (toRevision < fromRevision) {
                throw new IOException("Catalog returned an older revision");
            }

            List<CatalogChange> changes = response.changes == null
                    ? Collections.emptyList()
                    : response.changes;
            for (CatalogChange change : changes) {
                if (change == null || change.headId <= 0) {
                    throw new IOException("Catalog returned an invalid change");
                }
                if ("remove".equals(change.operation)) {
                    changed |= headsById.remove(change.headId) != null;
                } else if ("upsert".equals(change.operation)
                        && change.head != null
                        && change.head.getId() == change.headId) {
                    headsById.put(change.headId, change.head);
                    changed = true;
                } else {
                    throw new IOException("Catalog returned an unsupported change operation");
                }
            }

            if (response.hasMore && (response.nextCursor == null || response.nextCursor.isBlank())) {
                throw new IOException("Catalog omitted a required continuation cursor");
            }
            cursor = response.hasMore ? response.nextCursor : null;
        } while (cursor != null);

        if (toRevision < 0) {
            throw new IOException("Catalog returned no target revision");
        }
        if (!changed && toRevision == fromRevision) {
            LOGGER.debug("Catalog revision {} is already current.", fromRevision);
            return current.heads();
        }

        Snapshot next = current;
        if (changed) {
            List<Head> mergedHeads = new ArrayList<>(headsById.values());
            mergedHeads.sort(Comparator.comparingInt(Head::getId));
            next = buildSnapshot(mergedHeads);
        }
        persistCatalogCache(next.heads(), toRevision);
        this.catalogRevision = toRevision;
        this.snapshot = next;
        LOGGER.debug("Catalog sync advanced revision {} to {} with {} published heads.",
                fromRevision, toRevision, next.heads().size());
        return next.heads();
    }

    private CatalogSyncResponse fetchChanges(int sinceRevision, @Nullable String cursor) throws IOException {
        StringBuilder location = new StringBuilder(Objects.requireNonNull(syncUrl));
        location.append(syncUrl.contains("?") ? '&' : '?')
                .append("sinceRevision=").append(sinceRevision)
                .append("&limit=5000");
        if (cursor != null) {
            location.append("&cursor=")
                    .append(URLEncoder.encode(cursor, StandardCharsets.UTF_8));
        }

        URL url = URI.create(location.toString()).toURL();
        if (!(url.openConnection() instanceof HttpURLConnection request)) {
            throw new IOException("Unsupported sync URL protocol: " + url.getProtocol());
        }
        request.setRequestProperty("Accept", "application/json");
        request.setRequestProperty("Accept-Encoding", "gzip");
        request.setRequestProperty("User-Agent", "HeadDB");
        request.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        request.setReadTimeout(READ_TIMEOUT_MILLIS);
        request.setInstanceFollowRedirects(true);

        try {
            int responseCode = request.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Catalog sync HTTP response code " + responseCode);
            }
            try (InputStream raw = request.getInputStream();
                 InputStream in = isGzipEncoded(request.getContentEncoding()) ? new GZIPInputStream(raw) : raw;
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 8192)) {
                CatalogSyncResponse response = GSON.fromJson(reader, CatalogSyncResponse.class);
                if (response == null) {
                    throw new IOException("Catalog sync payload was empty");
                }
                return response;
            }
        } finally {
            request.disconnect();
        }
    }

    private boolean restoreCatalogCache() {
        if (cachePath == null || !Files.isRegularFile(cachePath)) {
            return false;
        }
        try {
            JsonObject cache = JsonParser.parseString(Files.readString(cachePath, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            if (cache.get("schema").getAsInt() != 1) {
                throw new IOException("Unsupported cache schema");
            }
            int revision = cache.get("revision").getAsInt();
            List<Head> heads = GSON.fromJson(cache.get("heads"), HeadMapper.HEADS_LIST_TYPE);
            if (revision < 0 || heads == null || heads.isEmpty()) {
                throw new IOException("Saved catalog is incomplete");
            }
            this.snapshot = buildSnapshot(heads);
            this.catalogRevision = revision;
            this.activeSource = "cache:" + cachePath.toAbsolutePath();
            LOGGER.info("Restored {} heads from saved catalog revision {}.", heads.size(), revision);
            return true;
        } catch (IOException | RuntimeException ex) {
            LOGGER.warn("Could not restore saved head catalog '{}': {}", cachePath, ex.getMessage());
            LOGGER.debug("Detailed saved catalog error", ex);
            return false;
        }
    }

    private void persistCatalogCache(List<Head> heads, int revision) {
        if (cachePath == null || revision < 0) {
            return;
        }
        try {
            Path absoluteCache = cachePath.toAbsolutePath();
            Path parent = Objects.requireNonNull(absoluteCache.getParent(), "Catalog cache has no parent directory");
            Files.createDirectories(parent);
            Path temporary = absoluteCache.resolveSibling(absoluteCache.getFileName() + ".tmp");
            JsonObject cache = new JsonObject();
            cache.addProperty("schema", 1);
            cache.addProperty("revision", revision);
            cache.add("heads", GSON.toJsonTree(heads, HeadMapper.HEADS_LIST_TYPE));
            Files.writeString(temporary, GSON.toJson(cache), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, absoluteCache,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, absoluteCache, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException ex) {
            LOGGER.warn("Could not save the local head catalog cache: {}", ex.getMessage());
            LOGGER.debug("Detailed catalog cache write error", ex);
        }
    }

    private static int parseRevision(@Nullable String rawRevision) throws IOException {
        if (rawRevision == null) {
            throw new IOException("Catalog snapshot omitted its revision");
        }
        try {
            int revision = Integer.parseInt(rawRevision);
            if (revision < 0) throw new NumberFormatException("negative revision");
            return revision;
        } catch (NumberFormatException ex) {
            throw new IOException("Catalog snapshot returned an invalid revision", ex);
        }
    }

    private Snapshot buildSnapshot(List<Head> loadedHeads) {
        LOGGER.debug("Indexing heads...");
        long indexStart = System.currentTimeMillis();

        List<Head> heads = List.copyOf(loadedHeads);
        Map<Integer, Head> byId = hasIndex(Index.ID) ? new HashMap<>() : null;
        Map<String, Head> byTexture = hasIndex(Index.TEXTURE) ? new HashMap<>() : null;
        Map<String, List<Head>> byCategory = hasIndex(Index.CATEGORY) ? new HashMap<>() : null;
        Map<String, List<Head>> byTag = hasIndex(Index.TAG) ? new HashMap<>() : null;

        for (Head head : heads) {
            validateHead(head);

            if (byId != null && byId.putIfAbsent(head.getId(), head) != null) {
                throw new IllegalArgumentException("Duplicate head ID: " + head.getId());
            }
            if (byTexture != null && byTexture.putIfAbsent(head.getTexture(), head) != null) {
                throw new IllegalArgumentException("Duplicate head texture: " + head.getTexture());
            }
            if (byCategory != null) {
                byCategory.computeIfAbsent(head.getCategory(), ignored -> new ArrayList<>()).add(head);
            }
            if (byTag != null) {
                for (String tag : head.getTags()) {
                    byTag.computeIfAbsent(tag, ignored -> new ArrayList<>()).add(head);
                }
            }
        }

        long indexTime = System.currentTimeMillis() - indexStart;
        LOGGER.debug("Indexing completed in {}ms", indexTime);
        return new Snapshot(
                heads,
                immutableMap(byId),
                immutableMap(byTexture),
                immutableListMap(byCategory),
                immutableListMap(byTag)
        );
    }

    private static void validateHead(Head head) {
        Objects.requireNonNull(head, "Database contains a null head");
        if (head.getName() == null || head.getName().isBlank()) {
            throw new IllegalArgumentException("Head " + head.getId() + " has no name");
        }
        if (head.getTexture() == null || head.getTexture().isBlank()) {
            throw new IllegalArgumentException("Head " + head.getId() + " has no texture");
        }
        if (head.getCategory() == null || head.getCategory().isBlank()) {
            throw new IllegalArgumentException("Head " + head.getId() + " has no category");
        }
        if (head.getTags() == null || head.getTags().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Head " + head.getId() + " has invalid tags");
        }
    }

    private static boolean isGzipEncoded(@Nullable String contentEncoding) {
        return contentEncoding != null
                && Arrays.stream(contentEncoding.split(","))
                .map(String::trim)
                .anyMatch("gzip"::equalsIgnoreCase);
    }

    private static <K, V> @Nullable Map<K, V> immutableMap(@Nullable Map<K, V> source) {
        return source == null ? null : Map.copyOf(source);
    }

    private static <K, V> @Nullable Map<K, List<V>> immutableListMap(@Nullable Map<K, List<V>> source) {
        if (source == null) {
            return null;
        }

        Map<K, List<V>> result = new HashMap<>();
        source.forEach((key, values) -> result.put(key, List.copyOf(values)));
        return Map.copyOf(result);
    }

    private static List<String> normalizeSourceUrls(@Nullable List<String> sourceUrls) {
        if (sourceUrls == null || sourceUrls.isEmpty()) {
            return List.of(DEFAULT_SOURCE_URL, DEFAULT_FALLBACK_SOURCE_URL);
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String source : sourceUrls) {
            if (source == null) {
                continue;
            }

            String trimmed = source.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }

        if (normalized.isEmpty()) {
            return List.of(DEFAULT_SOURCE_URL, DEFAULT_FALLBACK_SOURCE_URL);
        }

        return List.copyOf(normalized);
    }


    /**
     * Blocks until the most recent update() completes (success or failure),
     * then returns true if it succeeded, or false if it failed.
     */
    @Override
    public boolean awaitReady() {
        CompletableFuture<List<Head>> update = lastUpdateFuture;
        if (update == null) {
            return false;
        }
        try {
            update.join();
            return true;
        } catch (CancellationException | CompletionException ignored) {
            return false;
        }
    }

    /**
     * Non-blocking check for whether a successful, usable snapshot is available.
     */
    @Override
    public boolean isReady() {
        return snapshot != null;
    }

    @Override
    public CompletableFuture<List<Head>> onReady() {
        Snapshot current = snapshot;
        if (current != null) {
            return CompletableFuture.completedFuture(current.heads());
        }

        CompletableFuture<List<Head>> update = lastUpdateFuture;
        return update != null ? update : update();
    }

    @Override
    @Nullable
    public List<Head> getHeads() {
        Snapshot current = snapshot;
        return current == null ? null : current.heads();
    }

    @Override
    @NotNull
    public List<Head> getByCategory(String category) {
        Snapshot current = snapshot;
        if (current == null || category == null) {
            return Collections.emptyList();
        }
        if (current.byCategory() != null) {
            return current.byCategory().getOrDefault(category, Collections.emptyList());
        }

        List<Head> result = new ArrayList<>();
        for (Head head : current.heads()) {
            if (category.equals(head.getCategory())) {
                result.add(head);
            }
        }
        return List.copyOf(result);
    }

    @Override
    @NotNull
    public List<Head> getByTags(String... tags) {
        Snapshot current = snapshot;
        if (current == null) {
            return Collections.emptyList();
        }
        if (tags == null || tags.length == 0) {
            return Collections.emptyList();
        }

        if (current.byTag() != null) {
            Set<Head> resultSet = new LinkedHashSet<>();
            for (String t : tags) {
                if (t != null) {
                    resultSet.addAll(current.byTag().getOrDefault(t, Collections.emptyList()));
                }
            }
            return List.copyOf(resultSet);
        }
        Set<String> tagSet = Arrays.stream(tags).filter(Objects::nonNull).collect(Collectors.toSet());
        List<Head> result = new ArrayList<>();
        for (Head head : current.heads()) {
            for (String hTag : head.getTags()) {
                if (tagSet.contains(hTag)) {
                    result.add(head);
                    break;
                }
            }
        }
        return List.copyOf(result);
    }

    @Override
    @Nullable
    public Head getById(int id) {
        Snapshot current = snapshot;
        if (current == null) {
            return null;
        }
        if (current.byId() != null) {
            return current.byId().get(id);
        }
        for (Head head : current.heads()) {
            if (head.getId() == id) {
                return head;
            }
        }
        return null;
    }

    @Override
    @Nullable
    public Head getByTexture(String texture) {
        Snapshot current = snapshot;
        if (current == null || texture == null) {
            return null;
        }
        if (current.byTexture() != null) {
            return current.byTexture().get(texture);
        }
        for (Head head : current.heads()) {
            if (head.getTexture().equals(texture)) {
                return head;
            }
        }
        return null;
    }

    private boolean hasIndex(Index index) {
        return indexes.contains(index);
    }

    public int getCatalogRevision() {
        return catalogRevision;
    }

    @Override
    public CatalogStatus getCatalogStatus() {
        Snapshot current = snapshot;
        return new CatalogStatus(
                current != null,
                catalogRevision,
                current == null ? 0 : current.heads().size(),
                lastAttemptEpochMillis,
                lastSuccessfulUpdateEpochMillis,
                lastError,
                activeSource
        );
    }

    @Override
    public AutoCloseable addCatalogUpdateListener(CatalogUpdateListener listener) {
        Objects.requireNonNull(listener, "listener");
        updateListeners.add(listener);
        return () -> updateListeners.remove(listener);
    }

    private CatalogUpdate describeUpdate(Snapshot before, Snapshot after, int previousRevision, int revision) {
        Map<Integer, Head> previous = new HashMap<>();
        if (before != null) for (Head head : before.heads()) previous.put(head.getId(), head);
        Map<Integer, Head> current = new HashMap<>();
        if (after != null) for (Head head : after.heads()) current.put(head.getId(), head);
        List<Integer> added = new ArrayList<>();
        List<Integer> updated = new ArrayList<>();
        List<Integer> removed = new ArrayList<>();
        for (Map.Entry<Integer, Head> entry : current.entrySet()) {
            Head old = previous.get(entry.getKey());
            if (old == null) added.add(entry.getKey());
            else if (!sameHead(old, entry.getValue())) updated.add(entry.getKey());
        }
        for (Integer id : previous.keySet()) if (!current.containsKey(id)) removed.add(id);
        Collections.sort(added);
        Collections.sort(updated);
        Collections.sort(removed);
        return new CatalogUpdate(previousRevision, revision, added, updated, removed, System.currentTimeMillis());
    }

    private void notifyUpdateListeners(CatalogUpdate update) {
        for (CatalogUpdateListener listener : updateListeners) {
            try {
                listener.onCatalogUpdate(update);
            } catch (RuntimeException ex) {
                LOGGER.warn("A catalog update listener failed: {}", ex.getMessage());
                LOGGER.debug("Detailed catalog listener failure", ex);
            }
        }
    }

    private static boolean sameHead(Head left, Head right) {
        return left.getId() == right.getId()
                && Objects.equals(left.getName(), right.getName())
                && Objects.equals(left.getTexture(), right.getTexture())
                && Objects.equals(left.getTextureUrl(), right.getTextureUrl())
                && Objects.equals(left.getCategory(), right.getCategory())
                && Objects.equals(left.getTags(), right.getTags());
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record FetchedCatalog(List<Head> heads, int revision) {
    }

    private static final class CatalogSyncResponse {
        private int schema;
        private int fromRevision;
        private int toRevision;
        private List<CatalogChange> changes;
        private boolean hasMore;
        private String nextCursor;
    }

    private static final class CatalogChange {
        private String operation;
        private int headId;
        private Head head;
    }

    private record Snapshot(
            List<Head> heads,
            @Nullable Map<Integer, Head> byId,
            @Nullable Map<String, Head> byTexture,
            @Nullable Map<String, List<Head>> byCategory,
            @Nullable Map<String, List<Head>> byTag
    ) {
    }
}
