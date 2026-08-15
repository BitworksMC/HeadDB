package com.bitworksmc.headdb.implementation;

import com.bitworksmc.headdb.api.HeadDatabase;
import com.bitworksmc.headdb.api.model.Head;
import com.bitworksmc.headdb.implementation.model.HeadMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

public class BaseHeadDatabase implements HeadDatabase {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseHeadDatabase.class);

    private static final Gson GSON = new GsonBuilder().registerTypeAdapter(Head.class, new HeadMapper()).create();
    private static final String DEFAULT_SOURCE_URL = "https://raw.githubusercontent.com/BitworksMC/HeadDB/refs/heads/master/heads.json";
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int READ_TIMEOUT_MILLIS = 30_000;
    private final Executor executor;
    private final List<String> sourceUrls;
    private final EnumSet<Index> indexes;
    private final Object updateLock = new Object();

    /**
     * A complete, immutable database view. Publishing one object prevents readers from
     * observing heads from one update alongside indexes from another update.
     */
    private volatile Snapshot snapshot;

    // track the latest load
    private volatile CompletableFuture<List<Head>> lastUpdateFuture;

    public BaseHeadDatabase(@Nullable Executor executor, @Nullable List<String> sourceUrls, @Nullable Index... indexes) {
        this.executor = executor != null ? executor : Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "Head Database Worker");
            thread.setDaemon(true);
            return thread;
        });
        this.sourceUrls = normalizeSourceUrls(sourceUrls);
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

            lastUpdateFuture = CompletableFuture.supplyAsync(this::loadSnapshot, executor);
            return lastUpdateFuture;
        }
    }

    private List<Head> loadSnapshot() {
        LOGGER.debug("Fetching heads...");
        long start = System.currentTimeMillis();
        Exception lastException = null;

        for (String sourceUrl : sourceUrls) {
            try {
                List<Head> loadedHeads = fetchHeads(sourceUrl);
                Snapshot loadedSnapshot = buildSnapshot(loadedHeads);
                this.snapshot = loadedSnapshot;

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

    private List<Head> fetchHeads(String sourceUrl) throws IOException {
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
            return fetchedHeads;
        } finally {
            request.disconnect();
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
            return List.of(DEFAULT_SOURCE_URL);
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
            return List.of(DEFAULT_SOURCE_URL);
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

    private record Snapshot(
            List<Head> heads,
            @Nullable Map<Integer, Head> byId,
            @Nullable Map<String, Head> byTexture,
            @Nullable Map<String, List<Head>> byCategory,
            @Nullable Map<String, List<Head>> byTag
    ) {
    }
}
