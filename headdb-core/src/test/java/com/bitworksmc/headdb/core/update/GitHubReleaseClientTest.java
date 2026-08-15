package com.bitworksmc.headdb.core.update;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubReleaseClientTest {

    private HttpServer server;
    private final List<GitHubReleaseClient> clients = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    @AfterEach
    void tearDown() {
        clients.forEach(GitHubReleaseClient::close);
        server.stop(0);
    }

    @Test
    void parsesLatestReleaseAndReusesItAfterNotModified() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/latest", exchange -> {
            if (requests.incrementAndGet() == 1) {
                exchange.getResponseHeaders().add("ETag", "\"release-v6.0.3\"");
                respond(exchange, 200, """
                        {"tag_name":"v6.0.3","name":"HeadDB 6.0.3","unknown":{"safe":true}}
                        """);
                return;
            }
            assertEquals("\"release-v6.0.3\"", exchange.getRequestHeaders().getFirst("If-None-Match"));
            respond(exchange, 304, "");
        });

        GitHubReleaseClient client = client();
        assertEquals("v6.0.3", client.fetchLatest().orElseThrow().tagName());
        assertEquals("v6.0.3", client.fetchLatest().orElseThrow().tagName());
        assertEquals(2, requests.get());
    }

    @Test
    void treatsNotFoundAsNoPublishedRelease() throws Exception {
        server.createContext("/latest", exchange -> respond(exchange, 404, "{}"));

        assertEquals(Optional.empty(), client().fetchLatest());
    }

    @Test
    void distinguishesRegularHttpFailuresFromRateLimits() {
        server.createContext("/latest", exchange -> respond(exchange, 403, "{\"message\":\"Forbidden\"}"));

        GitHubReleaseClient.HttpStatusException exception = assertThrows(
                GitHubReleaseClient.HttpStatusException.class,
                () -> client().fetchLatest()
        );
        assertEquals(403, exception.statusCode());
    }

    @Test
    void reportsRetryTimeForRateLimits() {
        server.createContext("/latest", exchange -> {
            exchange.getResponseHeaders().add("Retry-After", "120");
            respond(exchange, 429, "{\"message\":\"rate limit exceeded\"}");
        });

        Instant before = Instant.now().plusSeconds(115);
        GitHubReleaseClient.RateLimitException exception = assertThrows(
                GitHubReleaseClient.RateLimitException.class,
                () -> client().fetchLatest()
        );
        assertTrue(exception.retrySpecifiedByServer());
        assertTrue(exception.retryAt().isAfter(before));
        assertTrue(exception.retryAt().isBefore(Instant.now().plusSeconds(125)));
    }

    @Test
    void recognizesRateLimitedForbiddenResponses() {
        long reset = Instant.now().plusSeconds(300).getEpochSecond();
        server.createContext("/latest", exchange -> {
            exchange.getResponseHeaders().add("X-RateLimit-Remaining", "0");
            exchange.getResponseHeaders().add("X-RateLimit-Reset", Long.toString(reset));
            respond(exchange, 403, "{\"message\":\"API rate limit exceeded\"}");
        });

        GitHubReleaseClient.RateLimitException exception = assertThrows(
                GitHubReleaseClient.RateLimitException.class,
                () -> client().fetchLatest()
        );
        assertEquals(Instant.ofEpochSecond(reset), exception.retryAt());
    }

    @Test
    void ignoresPrimaryResetWhenSecondaryLimitHasRemainingRequests() {
        long unrelatedReset = Instant.now().plus(Duration.ofDays(1)).getEpochSecond();
        server.createContext("/latest", exchange -> {
            exchange.getResponseHeaders().add("X-RateLimit-Remaining", "10");
            exchange.getResponseHeaders().add("X-RateLimit-Reset", Long.toString(unrelatedReset));
            respond(exchange, 403, "{\"message\":\"secondary rate limit\"}");
        });

        GitHubReleaseClient.RateLimitException exception = assertThrows(
                GitHubReleaseClient.RateLimitException.class,
                () -> client().fetchLatest()
        );
        assertFalse(exception.retrySpecifiedByServer());
        assertTrue(exception.retryAt().isBefore(Instant.now().plus(Duration.ofHours(2))));
    }

    @Test
    void rejectsMalformedReleaseResponses() {
        AtomicReference<String> responseBody = new AtomicReference<>();
        server.createContext("/latest", exchange -> respond(exchange, 200, responseBody.get()));
        for (String body : new String[]{
                "", "[]", "{}", "{\"tag_name\":null}", "{\"tag_name\":602}", "{\"tag_name\":\"  \"}"
        }) {
            responseBody.set(body);
            assertThrows(IOException.class, () -> client().fetchLatest(), () -> "Expected malformed body: " + body);
        }
    }

    @Test
    void rejectsOversizedResponses() {
        byte[] oversized = new byte[GitHubReleaseClient.MAX_RESPONSE_BYTES + 1];
        server.createContext("/latest", exchange -> respond(exchange, 200, oversized));

        IOException exception = assertThrows(IOException.class, () -> client().fetchLatest());
        assertTrue(exception.getMessage().contains("exceeded"));
    }

    @Test
    void preservesTypedStatusForOversizedServerErrors() {
        byte[] oversized = new byte[GitHubReleaseClient.MAX_RESPONSE_BYTES + 1];
        server.createContext("/latest", exchange -> respond(exchange, 500, oversized));

        GitHubReleaseClient.HttpStatusException exception = assertThrows(
                GitHubReleaseClient.HttpStatusException.class,
                () -> client().fetchLatest()
        );
        assertEquals(500, exception.statusCode());
    }

    @Test
    void clearsCachedEtagWhenReleaseDisappears() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/latest", exchange -> {
            int request = requests.incrementAndGet();
            if (request == 1) {
                exchange.getResponseHeaders().add("ETag", "\"old-release\"");
                respond(exchange, 200, "{\"tag_name\":\"v6.0.3\"}");
            } else if (request == 2) {
                assertEquals("\"old-release\"", exchange.getRequestHeaders().getFirst("If-None-Match"));
                respond(exchange, 404, "{}");
            } else {
                assertEquals(null, exchange.getRequestHeaders().getFirst("If-None-Match"));
                respond(exchange, 200, "{\"tag_name\":\"v6.0.4\"}");
            }
        });

        GitHubReleaseClient client = client();
        assertEquals("v6.0.3", client.fetchLatest().orElseThrow().tagName());
        assertTrue(client.fetchLatest().isEmpty());
        assertEquals("v6.0.4", client.fetchLatest().orElseThrow().tagName());
    }

    @Test
    void enforcesRequestTimeout() {
        server.createContext("/latest", exchange -> {
            try {
                Thread.sleep(500L);
                respond(exchange, 200, "{\"tag_name\":\"v6.0.3\"}");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });

        GitHubReleaseClient client = track(new GitHubReleaseClient(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
                endpoint(),
                Duration.ofMillis(100),
                "HeadDB-Test"
        ));
        assertThrows(HttpTimeoutException.class, client::fetchLatest);
    }

    @Test
    void requestTimeoutAlsoBoundsPartialResponseBodies() {
        server.createContext("/latest", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(200, 2);
                exchange.getResponseBody().write('{');
                exchange.getResponseBody().flush();
                Thread.sleep(500L);
                exchange.getResponseBody().write('}');
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });

        GitHubReleaseClient client = track(new GitHubReleaseClient(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
                endpoint(),
                Duration.ofMillis(100),
                "HeadDB-Test"
        ));
        assertThrows(HttpTimeoutException.class, client::fetchLatest);
    }

    private GitHubReleaseClient client() {
        return track(new GitHubReleaseClient(endpoint(), "HeadDB-Test"));
    }

    private GitHubReleaseClient track(GitHubReleaseClient client) {
        clients.add(client);
        return client;
    }

    private URI endpoint() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/latest");
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        respond(exchange, status, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        try (exchange) {
            if (status == 304) {
                exchange.sendResponseHeaders(status, -1);
                return;
            }
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
        }
    }
}
