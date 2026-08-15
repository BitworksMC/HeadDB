package com.bitworksmc.headdb.implementation;

import com.bitworksmc.headdb.api.model.Head;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class BaseHeadDatabaseTest {

    private static final String HEADS_JSON = """
            [
              {"id":1,"name":"Melon","texture":"texture-one","category":"Plants","tags":["Fruit","Summer"]},
              {"id":2,"name":"Bread","texture":"texture-two","category":"Food & Drinks","tags":["Food"]}
            ]
            """;

    private HttpServer server;
    private ExecutorService databaseExecutor;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        databaseExecutor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        databaseExecutor.shutdownNow();
    }

    @Test
    void concurrentUpdatesShareOneRequestAndBuildAllIndexes() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        CountDownLatch requestStarted = new CountDownLatch(1);
        CountDownLatch releaseResponse = new CountDownLatch(1);
        server.createContext("/heads", exchange -> {
            requests.incrementAndGet();
            requestStarted.countDown();
            await(releaseResponse);
            respond(exchange, 200, HEADS_JSON);
        });

        BaseHeadDatabase database = database("/heads", Index.values());
        CompletableFuture<List<Head>> first = database.update();
        assertTrue(requestStarted.await(5, TimeUnit.SECONDS));
        CompletableFuture<List<Head>> second = database.update();

        assertSame(first, second);
        releaseResponse.countDown();
        assertEquals(2, first.join().size());
        assertEquals(1, requests.get());
        assertEquals("Melon", database.getById(1).getName());
        assertEquals("Bread", database.getByTexture("texture-two").getName());
        assertEquals(1, database.getByCategory("Plants").size());
        assertEquals(1, database.getByTags("Summer").size());
    }

    @Test
    void failedRefreshKeepsTheLastGoodSnapshotAvailable() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        CountDownLatch refreshStarted = new CountDownLatch(1);
        CountDownLatch releaseRefresh = new CountDownLatch(1);
        server.createContext("/heads", exchange -> {
            if (requests.incrementAndGet() == 1) {
                respond(exchange, 200, HEADS_JSON);
                return;
            }

            refreshStarted.countDown();
            await(releaseRefresh);
            respond(exchange, 503, "unavailable");
        });

        BaseHeadDatabase database = database("/heads", Index.ID);
        List<Head> original = database.update().join();
        CompletableFuture<List<Head>> refresh = database.update();
        assertTrue(refreshStarted.await(5, TimeUnit.SECONDS));

        assertTrue(database.isReady());
        assertEquals(original, database.getHeads());
        assertEquals(original, database.onReady().join());

        releaseRefresh.countDown();
        assertThrows(CompletionException.class, refresh::join);
        assertTrue(database.isReady());
        assertEquals("Melon", database.getById(1).getName());
    }

    @Test
    void malformedPrimaryFallsBackToGzipSource() throws IOException {
        server.createContext("/bad", exchange -> respond(exchange, 200, "not json"));
        server.createContext("/gzip", exchange -> {
            byte[] compressed = gzip(HEADS_JSON);
            exchange.getResponseHeaders().add("Content-Encoding", "gzip, identity");
            respond(exchange, 200, compressed);
        });

        BaseHeadDatabase database = new BaseHeadDatabase(
                databaseExecutor,
                List.of(url("/bad"), url("/gzip")),
                Index.ID
        );

        assertEquals(2, database.update().join().size());
        assertTrue(database.awaitReady());
    }

    @Test
    void emptyPayloadFailsInsteadOfReplacingTheDatabase() {
        server.createContext("/empty", exchange -> respond(exchange, 200, "[]"));
        BaseHeadDatabase database = database("/empty", Index.ID);

        assertFalse(database.awaitReady());
        assertThrows(CompletionException.class, () -> database.onReady().join());
        assertFalse(database.isReady());
        assertNull(database.getHeads());
    }

    @Test
    void publishedCollectionsCannotBeMutated() {
        server.createContext("/heads", exchange -> respond(exchange, 200, HEADS_JSON));
        BaseHeadDatabase database = database("/heads", Index.CATEGORY, Index.TAG);

        List<Head> heads = database.update().join();
        assertThrows(UnsupportedOperationException.class, heads::clear);
        assertThrows(UnsupportedOperationException.class, () -> heads.getFirst().getTags().add("changed"));
        assertThrows(UnsupportedOperationException.class, () -> database.getByCategory("Plants").clear());
        assertThrows(UnsupportedOperationException.class, () -> database.getByTags("Fruit").clear());
    }

    private BaseHeadDatabase database(String path, Index... indexes) {
        return new BaseHeadDatabase(databaseExecutor, List.of(url(path)), indexes);
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        respond(exchange, status, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        try (exchange) {
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
        }
    }

    private static byte[] gzip(String value) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(value.getBytes(StandardCharsets.UTF_8));
        }
        return output.toByteArray();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test latch");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for test latch", ex);
        }
    }
}
