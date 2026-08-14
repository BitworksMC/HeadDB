package com.bitworksmc.headdb.core.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class GitHubReleaseClient implements AutoCloseable {

    static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final String API_VERSION = "2026-03-10";

    private final HttpClient httpClient;
    private final URI endpoint;
    private final Duration requestTimeout;
    private final String userAgent;

    private String etag;
    private ReleaseInfo cachedRelease;

    GitHubReleaseClient(URI endpoint, String userAgent) {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build(),
                endpoint,
                Duration.ofSeconds(10),
                userAgent
        );
    }

    GitHubReleaseClient(HttpClient httpClient, URI endpoint, Duration requestTimeout, String userAgent) {
        this.httpClient = httpClient;
        this.endpoint = endpoint;
        this.requestTimeout = requestTimeout;
        this.userAgent = userAgent;
    }

    Optional<ReleaseInfo> fetchLatest() throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                .GET()
                .timeout(requestTimeout)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", API_VERSION)
                .header("User-Agent", userAgent);
        if (etag != null) {
            request.header("If-None-Match", etag);
        }

        HttpResponse<byte[]> response = sendWithDeadline(request.build());

        int status = response.statusCode();
        if (status == 304) {
            if (cachedRelease == null) {
                throw new IOException("GitHub returned 304 before a release was cached");
            }
            return Optional.of(cachedRelease);
        }
        if (status == 404) {
            etag = null;
            cachedRelease = null;
            return Optional.empty();
        }
        if (status == 429 || (status == 403 && hasRateLimitHeaders(response.headers()))) {
            throw rateLimitException(response.headers());
        }
        if (status != 200 && status != 403) {
            throw new HttpStatusException(status);
        }

        String responseBody = new String(response.body(), StandardCharsets.UTF_8);
        if (status == 403) {
            if (responseBody.toLowerCase(Locale.ROOT).contains("rate limit")) {
                throw rateLimitException(response.headers());
            }
            throw new HttpStatusException(status);
        }

        ReleaseInfo release = parseRelease(responseBody);
        cachedRelease = release;
        etag = response.headers().firstValue("ETag").orElse(null);
        return Optional.of(release);
    }

    private static RateLimitException rateLimitException(HttpHeaders headers) {
        Optional<Instant> retryAt = resolveRetryAt(headers);
        return new RateLimitException(
                "GitHub API rate limit reached",
                retryAt.orElseGet(() -> Instant.now().plus(Duration.ofHours(1))),
                retryAt.isPresent()
        );
    }

    private HttpResponse<byte[]> sendWithDeadline(HttpRequest request) throws IOException, InterruptedException {
        CompletableFuture<HttpResponse<byte[]>> response = httpClient.sendAsync(
                request,
                GitHubReleaseClient::bodySubscriber
        );
        try {
            return response.get(requestTimeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException ex) {
            response.cancel(true);
            HttpTimeoutException timeout = new HttpTimeoutException("GitHub release request timed out");
            timeout.initCause(ex);
            throw timeout;
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            while (cause instanceof CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IOException("GitHub release request failed", cause);
        } catch (InterruptedException ex) {
            response.cancel(true);
            throw ex;
        }
    }

    private static HttpResponse.BodySubscriber<byte[]> bodySubscriber(HttpResponse.ResponseInfo responseInfo) {
        int status = responseInfo.statusCode();
        if (status == 200 || status == 403) {
            return new BoundedBodySubscriber(MAX_RESPONSE_BYTES);
        }
        return HttpResponse.BodySubscribers.replacing(new byte[0]);
    }

    private static ReleaseInfo parseRelease(String responseBody) throws IOException {
        try {
            JsonElement root = JsonParser.parseString(responseBody);
            if (!root.isJsonObject()) {
                throw new IOException("GitHub release response was not a JSON object");
            }

            JsonObject object = root.getAsJsonObject();
            JsonElement tagElement = object.get("tag_name");
            if (tagElement == null || !tagElement.isJsonPrimitive() || !tagElement.getAsJsonPrimitive().isString()) {
                throw new IOException("GitHub release response did not contain a string tag_name");
            }

            String tagName = tagElement.getAsString().trim();
            if (tagName.isEmpty()) {
                throw new IOException("GitHub release tag_name was blank");
            }
            return new ReleaseInfo(tagName);
        } catch (JsonParseException | IllegalStateException ex) {
            throw new IOException("Could not parse GitHub release response", ex);
        }
    }

    private static boolean hasRateLimitHeaders(HttpHeaders headers) {
        return headers.firstValue("Retry-After").isPresent()
                || headers.firstValue("X-RateLimit-Remaining")
                .map(String::trim)
                .filter("0"::equals)
                .isPresent();
    }

    private static Optional<Instant> resolveRetryAt(HttpHeaders headers) {
        Optional<String> retryAfter = headers.firstValue("Retry-After");
        if (retryAfter.isPresent()) {
            String value = retryAfter.get().trim();
            try {
                return Optional.of(Instant.now().plusSeconds(Math.max(0L, Long.parseLong(value))));
            } catch (NumberFormatException | DateTimeException ignored) {
                try {
                    return Optional.of(ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
                } catch (DateTimeParseException ignoredDate) {
                    // Fall through to X-RateLimit-Reset.
                }
            }
        }

        boolean primaryLimitExhausted = headers.firstValue("X-RateLimit-Remaining")
                .map(String::trim)
                .filter("0"::equals)
                .isPresent();
        if (!primaryLimitExhausted) {
            return Optional.empty();
        }

        return headers.firstValue("X-RateLimit-Reset").flatMap(value -> {
            try {
                return Optional.of(Instant.ofEpochSecond(Long.parseLong(value.trim())));
            } catch (NumberFormatException | DateTimeException ignored) {
                return Optional.empty();
            }
        });
    }

    @Override
    public void close() {
        httpClient.shutdownNow();
    }

    record ReleaseInfo(String tagName) {
    }

    static final class RateLimitException extends IOException {

        private final Instant retryAt;
        private final boolean retrySpecifiedByServer;

        RateLimitException(String message, Instant retryAt, boolean retrySpecifiedByServer) {
            super(message);
            this.retryAt = retryAt;
            this.retrySpecifiedByServer = retrySpecifiedByServer;
        }

        Instant retryAt() {
            return retryAt;
        }

        boolean retrySpecifiedByServer() {
            return retrySpecifiedByServer;
        }
    }

    static final class HttpStatusException extends IOException {

        private final int statusCode;

        HttpStatusException(int statusCode) {
            super("GitHub release request returned HTTP " + statusCode);
            this.statusCode = statusCode;
        }

        int statusCode() {
            return statusCode;
        }
    }

    private static final class BoundedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {

        private final int maximumBytes;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private Flow.Subscription subscription;

        private BoundedBodySubscriber(int maximumBytes) {
            this.maximumBytes = maximumBytes;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            if (this.subscription != null) {
                subscription.cancel();
                return;
            }
            this.subscription = subscription;
            subscription.request(1L);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (body.isDone()) {
                return;
            }
            for (ByteBuffer buffer : buffers) {
                int length = buffer.remaining();
                if (output.size() + (long) length > maximumBytes) {
                    subscription.cancel();
                    body.completeExceptionally(new IOException(
                            "GitHub release response exceeded " + maximumBytes + " bytes"
                    ));
                    return;
                }
                byte[] bytes = new byte[length];
                buffer.get(bytes);
                output.writeBytes(bytes);
            }
            subscription.request(1L);
        }

        @Override
        public void onError(Throwable throwable) {
            body.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            body.complete(output.toByteArray());
        }
    }
}
