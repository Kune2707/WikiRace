package com.wikirace.wikipedia;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.wikirace.exception.GameException;
import static com.wikirace.exception.GameException.Code.ARTICLE_NOT_FOUND;
import static com.wikirace.exception.GameException.Code.WIKIPEDIA_UNAVAILABLE;

public class MediaWikiClient {
    private static final Logger LOG = LoggerFactory.getLogger(MediaWikiClient.class);
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final WikipediaProperties properties;

    public MediaWikiClient(HttpClient http, ObjectMapper mapper, WikipediaProperties properties) {
        this.http = http;
        this.mapper = mapper;
        this.properties = properties;
    }

    public JsonNode get(Map<String, String> parameters, long deadlineNanos) {
        long remaining = deadlineNanos - System.nanoTime();
        if (remaining <= 0) throw unavailable();
        var query = new TreeMap<>(parameters);
        query.put("format", "json");
        query.put("formatversion", "2");
        String encoded = query.entrySet().stream().map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        var request = HttpRequest.newBuilder(URI.create("https://en.wikipedia.org/w/api.php?" + encoded))
                .timeout(Duration.ofNanos(remaining)).header("User-Agent", properties.userAgent())
                .header("Accept", "application/json").GET().build();
        CompletableFuture<HttpResponse<byte[]>> pending;
        try {
            pending = http.sendAsync(request, info -> new LimitedBody(properties.maxResponseBytes()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw unavailable();
        }
        try {
            var response = pending.get(remaining, TimeUnit.NANOSECONDS);
            if (response.statusCode() != 200 || response.body().length > properties.maxResponseBytes()) {
                LOG.warn("MediaWiki request failed with HTTP status {}", response.statusCode());
                throw unavailable();
            }
            JsonNode root = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(response.body());
            if (root == null || !root.isObject()) throw unavailable();
            if (root.has("error")) {
                String code = root.path("error").path("code").asText();
                if (code.equals("missingtitle") || code.equals("invalidtitle")) {
                    throw new GameException(ARTICLE_NOT_FOUND, "Wikipedia article not found.");
                }
                throw unavailable();
            }
            return root;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (ExecutionException | TimeoutException | IOException e) {
            LOG.warn("MediaWiki request could not complete ({})", e.getClass().getSimpleName());
            throw unavailable();
        } finally {
            if (!pending.isDone()) pending.cancel(true);
        }
    }

    public static GameException unavailable() {
        return new GameException(WIKIPEDIA_UNAVAILABLE, "Wikipedia is temporarily unavailable. Please retry.");
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private final long limit;
        private long received;
        private Flow.Subscription subscription;
        private boolean failed;
        LimitedBody(long limit) { this.limit = limit; }
        public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            delegate.onSubscribe(subscription);
        }
        public void onNext(List<ByteBuffer> buffers) {
            if (failed) return;
            received += buffers.stream().mapToLong(ByteBuffer::remaining).sum();
            if (received > limit) {
                failed = true;
                subscription.cancel();
                delegate.onError(new IOException("Response exceeds configured limit."));
            } else delegate.onNext(buffers);
        }
        public void onError(Throwable error) { if (!failed) delegate.onError(error); }
        public void onComplete() { if (!failed) delegate.onComplete(); }
    }
}
