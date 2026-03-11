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
    private static final String DEFAULT_SOURCE_URL = "https://raw.githubusercontent.com/TheSilentPro/heads/refs/heads/main/heads.json";
    private final Executor executor;
    private final List<String> sourceUrls;

    // Non-indexed
    private volatile List<Head> heads = null;

    // Indexed
    private volatile Map<Integer, Head> byId = null;
    private volatile Map<String, Head> byTexture = null;
    private volatile Map<String, List<Head>> byCategory = null;
    private volatile Map<String, List<Head>> byTag = null;
    private final Index[] indexes;

    // track the latest load
    private volatile CompletableFuture<List<Head>> lastUpdateFuture;

    public BaseHeadDatabase(@Nullable Executor executor, @Nullable List<String> sourceUrls, @Nullable Index... indexes) {
        this.executor = executor != null ? executor : Executors.newSingleThreadExecutor(r -> new Thread(r, "Head Database Worker"));
        this.sourceUrls = normalizeSourceUrls(sourceUrls);
        this.indexes = indexes;
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
        if (lastUpdateFuture != null && !lastUpdateFuture.isDone()) {
            return lastUpdateFuture;
        }

        lastUpdateFuture = CompletableFuture.supplyAsync(() -> {
            LOGGER.debug("Fetching heads...");
            long start = System.currentTimeMillis();
            Exception lastException = null;

            for (String sourceUrl : sourceUrls) {
                try {
                    List<Head> loadedHeads = fetchHeads(sourceUrl);
                    if (loadedHeads == null) {
                        continue;
                    }

                    this.heads = loadedHeads;
                    rebuildIndexes();

                    long elapsed = System.currentTimeMillis() - start;
                    LOGGER.debug("Update took {} seconds ({}ms total)", TimeUnit.MILLISECONDS.toSeconds(elapsed), elapsed);
                    return Collections.unmodifiableList(this.heads);
                } catch (IOException | IllegalArgumentException ex) {
                    lastException = ex;
                    LOGGER.warn("Failed to fetch heads from '{}': {}", sourceUrl, ex.getMessage());
                    LOGGER.debug("Detailed error while fetching heads from '{}'", sourceUrl, ex);
                }
            }

            if (lastException != null) {
                LOGGER.error("Failed to update heads from all configured sources.");
                throw new CompletionException("Failed to update heads", lastException);
            }

            LOGGER.error("Failed to update heads from all configured sources.");
            return Collections.emptyList();
        }, executor);

        return lastUpdateFuture;
    }

    private @Nullable List<Head> fetchHeads(String sourceUrl) throws IOException {
        URL url = URI.create(sourceUrl).toURL();
        HttpURLConnection request = (HttpURLConnection) url.openConnection();
        request.setRequestProperty("Accept", "application/json");
        request.setRequestProperty("Accept-Encoding", "gzip");

        try {
            long connectStart = System.currentTimeMillis();
            request.connect();
            int responseCode = request.getResponseCode();
            long connectTime = System.currentTimeMillis() - connectStart;
            LOGGER.debug("Connected to '{}' in {}ms (Response code: {})", sourceUrl, connectTime, responseCode);

            if (responseCode != HttpURLConnection.HTTP_OK) {
                LOGGER.error("Failed to fetch data from '{}'. HTTP Response Code: {}", sourceUrl, responseCode);
                return null;
            }

            long readStart = System.currentTimeMillis();
            StringBuilder rawData = new StringBuilder();
            int lineCount = 0;

            try (InputStream raw = request.getInputStream();
                 InputStream in = "gzip".equalsIgnoreCase(request.getContentEncoding()) ? new GZIPInputStream(raw) : raw;
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
            List<Head> fetchedHeads;
            try {
                fetchedHeads = GSON.fromJson(rawData.toString(), HeadMapper.HEADS_LIST_TYPE);
            } catch (Exception ex) {
                LOGGER.error("Failed to parse fetched JSON from '{}'!", sourceUrl, ex);
                return null;
            }

            if (fetchedHeads == null) {
                LOGGER.error("Source '{}' returned an empty payload.", sourceUrl);
                return null;
            }

            long parseTime = System.currentTimeMillis() - parseStart;
            LOGGER.debug("Parsed {} heads from '{}' in {}ms", fetchedHeads.size(), sourceUrl, parseTime);
            return fetchedHeads;
        } finally {
            request.disconnect();
        }
    }

    private void rebuildIndexes() {
        if (indexes == null) {
            return;
        }

        LOGGER.debug("Indexing heads...");
        long indexStart = System.currentTimeMillis();

        if (hasIndex(Index.ID)) {
            this.byId = this.heads.stream().collect(Collectors.toMap(Head::getId, h -> h));
            LOGGER.debug("Index by ID completed");
        }

        if (hasIndex(Index.TEXTURE)) {
            this.byTexture = this.heads.stream().collect(Collectors.toMap(Head::getTexture, h -> h));
            LOGGER.debug("Index by Texture completed");
        }

        if (hasIndex(Index.CATEGORY)) {
            Map<String, List<Head>> rawCat = this.heads.stream().collect(Collectors.groupingBy(Head::getCategory));
            this.byCategory = rawCat.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> Collections.unmodifiableList(e.getValue())
                    ));
            LOGGER.debug("Index by Category completed");
        }

        if (hasIndex(Index.TAG)) {
            Map<String, List<Head>> tagBuilder = new HashMap<>();
            for (Head head : this.heads) {
                for (String tag : head.getTags()) {
                    tagBuilder
                            .computeIfAbsent(tag, k -> new ArrayList<>())
                            .add(head);
                }
            }
            this.byTag = tagBuilder.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> Collections.unmodifiableList(e.getValue())
                    ));
            LOGGER.debug("Index by Tag completed");
        }

        long indexTime = System.currentTimeMillis() - indexStart;
        LOGGER.debug("Indexing completed in {}ms", indexTime);
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


    // ... rest of class unchanged ...

    /**
     * Blocks until the most recent update() completes (success or failure),
     * then returns true if it succeeded, or false if it failed.
     */
    @Override
    public boolean awaitReady() {
        try {
            lastUpdateFuture.join();
            return true;
        } catch (CompletionException ignored) {
            return false;
        }
    }

    /**
     * Non-blocking check: has the most recent update() finished (successfully or not)?
     */
    @Override
    public boolean isReady() {
        return lastUpdateFuture != null
                && lastUpdateFuture.isDone()
                && !lastUpdateFuture.isCompletedExceptionally()
                && !lastUpdateFuture.isCancelled();
    }

    @Override
    public CompletableFuture<List<Head>> onReady() {
        return Objects.requireNonNullElseGet(lastUpdateFuture, CompletableFuture::new);
    }

    @Override
    @Nullable
    public List<Head> getHeads() {
        if (!isReady()) {
            return Collections.emptyList();
        }
        if (this.heads == null) {
            return null;
        }
        return Collections.unmodifiableList(this.heads);
    }

    @Override
    @NotNull
    public List<Head> getByCategory(String category) {
        if (!isReady()) {
            return Collections.emptyList();
        }
        if (byCategory != null) {
            return byCategory.getOrDefault(category, Collections.emptyList());
        }
        if (heads == null) {
            return Collections.emptyList();
        }

        List<Head> result = new ArrayList<>();
        for (Head head : heads) {
            if (category.equals(head.getCategory())) {
                result.add(head);
            }
        }
        return result;
    }

    @Override
    @NotNull
    public List<Head> getByTags(String... tags) {
        if (!isReady()) {
            return Collections.emptyList();
        }
        if (tags == null || tags.length == 0) {
            return Collections.emptyList();
        }

        if (byTag != null) {
            Set<Head> resultSet = new LinkedHashSet<>();
            for (String t : tags) {
                if (t != null) {
                    resultSet.addAll(byTag.getOrDefault(t, Collections.emptyList()));
                }
            }
            return new ArrayList<>(resultSet);
        }

        if (heads == null) {
            return Collections.emptyList();
        }
        Set<String> tagSet = Arrays.stream(tags).filter(Objects::nonNull).collect(Collectors.toSet());
        List<Head> result = new ArrayList<>();
        for (Head head : heads) {
            for (String hTag : head.getTags()) {
                if (tagSet.contains(hTag)) {
                    result.add(head);
                    break;
                }
            }
        }
        return result;
    }

    @Override
    @Nullable
    public Head getById(int id) {
        if (!isReady()) {
            return null;
        }
        if (byId != null) {
            return byId.get(id);
        }
        if (heads == null) {
            return null;
        }
        for (Head head : heads) {
            if (head.getId() == id) {
                return head;
            }
        }
        return null;
    }

    @Override
    @Nullable
    public Head getByTexture(String texture) {
        if (!isReady()) {
            return null;
        }
        if (byTexture != null) {
            return byTexture.get(texture);
        }
        if (heads == null) {
            return null;
        }
        for (Head head : heads) {
            if (head.getTexture().equals(texture)) {
                return head;
            }
        }
        return null;
    }

    private boolean hasIndex(Index index) {
        if (indexes == null) {
            return false;
        }
        for (Index i : indexes) {
            if (i == index) {
                return true;
            }
        }
        return false;
    }
}
