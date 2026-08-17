package com.bitworksmc.headdb.legacy;

import com.bitworksmc.headdb.api.model.Head;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class LegacyDatabaseTest {
    private HttpServer server;
    private ExecutorService executor;

    @Before
    public void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newSingleThreadExecutor();
    }

    @After
    public void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    public void loadsAndIndexesLegacyDatabase() {
        serve("["
                + "{\"id\":1,\"name\":\"Apple\",\"texture\":\"aaa\",\"category\":\"Food\",\"tags\":[\"fruit\"]},"
                + "{\"id\":2,\"name\":\"Pear\",\"texture\":\"bbb\",\"category\":\"Food\",\"tags\":[\"fruit\",\"green\"]}"
                + "]", 200);

        LegacyDatabase database = new LegacyDatabase(url(), executor);
        List<Head> heads = database.update().join();

        assertTrue(database.isReady());
        assertEquals(2, heads.size());
        assertSame(heads.get(0), database.getById(1));
        assertSame(heads.get(1), database.getByTexture("bbb"));
        assertEquals(2, database.getByCategory("food").size());
        assertEquals(2, database.getByTags("fruit").size());
        assertEquals(1, database.getByTags("green").size());
    }

    @Test
    public void failedUpdateDoesNotPublishSnapshot() {
        serve("unavailable", 503);

        LegacyDatabase database = new LegacyDatabase(url(), executor);
        try {
            database.update().join();
        } catch (RuntimeException expected) {
            // CompletionException is the public CompletableFuture failure contract.
        }

        assertFalse(database.isReady());
        assertFalse(database.awaitReady());
    }

    private void serve(final String body, final int status) {
        server.createContext("/heads.json", exchange -> {
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            OutputStream output = exchange.getResponseBody();
            output.write(response);
            output.close();
        });
        server.start();
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/heads.json";
    }
}
