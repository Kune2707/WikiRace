package com.wikirace.wikipedia;

import java.net.URLDecoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wikirace.exception.GameException;
import com.wikirace.game.model.*;
import com.wikirace.game.service.GameService;
import com.wikirace.game.runtime.RoomRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.wikirace.exception.GameException.Code.*;

@Timeout(5)
class WikipediaArticleProviderTest {
    final ObjectMapper mapper = new ObjectMapper();
    HttpClient http;
    WikipediaProperties properties;
    WikipediaArticleProvider provider;
    final List<HttpRequest> requests = new ArrayList<>();
    Function<Map<String, String>, Object> fixture;
    AtomicLong ticker = new AtomicLong();

    @BeforeEach void setup() {
        http = mock(HttpClient.class);
        properties = new WikipediaProperties("WikiRaceTest/1 (offline)", Duration.ofSeconds(1),
                Duration.ofMillis(100), 2, Duration.ofMinutes(30), 8192);
        fixture = this::graph;
        when(http.sendAsync(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    requests.add(request);
                    assertThat(request.uri().getHost()).isEqualTo("en.wikipedia.org");
                    assertThat(request.uri().getPath()).isEqualTo("/w/api.php");
                    assertThat(request.uri().getScheme()).isEqualTo("https");
                    assertThat(request.headers().firstValue("User-Agent")).contains(properties.userAgent());
                    Object data = fixture.apply(parameters(request));
                    if (data instanceof CompletableFuture<?>) return data;
                    int status = data instanceof Integer ? (Integer) data : 200;
                    byte[] body = data instanceof Integer ? new byte[0] :
                            data instanceof String ? ((String) data).getBytes(StandardCharsets.UTF_8) : mapper.writeValueAsBytes(data);
                    HttpResponse<byte[]> response = mock(HttpResponse.class);
                    when(response.statusCode()).thenReturn(status);
                    when(response.body()).thenReturn(body);
                    return CompletableFuture.completedFuture(response);
                });
        provider = new WikipediaArticleProvider(new MediaWikiClient(http, mapper, properties), properties, ticker::get);
    }

    static Map<String, String> parameters(HttpRequest request) {
        Map<String, String> result = new HashMap<>();
        for (String item : request.uri().getRawQuery().split("&")) {
            String[] pair = item.split("=", 2);
            result.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8), URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
        }
        assertThat(result).containsEntry("formatversion", "2").containsEntry("format", "json");
        return result;
    }

    Object graph(Map<String, String> q) {
        if (q.containsKey("list")) {
            assertThat(q).containsEntry("psnamespace", "0").containsEntry("pslimit", "10");
            return Map.of("query", Map.of("prefixsearch", List.of(Map.of("ns", 0, "title", "Start"),
                    Map.of("ns", 1, "title", "Talk:Start"), Map.of("ns", 0, "title", "Start"))));
        }
        if (q.get("action").equals("parse")) {
            String title = switch (q.get("pageid")) { case "1" -> "Start"; case "2" -> "Middle"; case "3" -> "Target"; default -> "Other"; };
            List<Map<String, Object>> links = switch (title) {
                case "Start" -> List.of(Map.of("ns", 0, "title", "Alias", "exists", true),
                        Map.of("ns", 14, "title", "Category:Games", "exists", true),
                        Map.of("ns", 0, "title", "Missing", "exists", false));
                case "Middle" -> List.of(Map.of("ns", 0, "title", "Target", "exists", true));
                default -> List.of();
            };
            return Map.of("parse", Map.of("title", title, "revid", 42, "text",
                    "<p onclick='bad()'>" + title + " <a href='/wiki/Alias'>Alias</a><script>bad()</script></p>", "links", links));
        }
        assertThat(q).containsEntry("redirects", "1").containsEntry("prop", "info");
        List<Map<String, Object>> pages = new ArrayList<>();
        List<Map<String, String>> redirects = new ArrayList<>();
        List<Map<String, String>> normalized = new ArrayList<>();
        for (String raw : q.get("titles").split("\\|")) {
            String title = raw.equals("start") ? "Start" : raw;
            if (!raw.equals(title)) normalized.add(Map.of("from", raw, "to", title));
            if (title.equals("Alias")) {
                redirects.add(Map.of("from", "Alias", "to", "Alias two"));
                redirects.add(Map.of("from", "Alias two", "to", "Middle"));
                title = "Middle";
            }
            int id = switch (title) { case "Start" -> 1; case "Middle" -> 2; case "Target" -> 3; default -> 4; };
            if (title.equals("Missing")) pages.add(Map.of("ns", 0, "title", title, "missing", true));
            else pages.add(Map.of("ns", title.startsWith("Talk:") ? 1 : 0, "title", title, "pageid", id));
        }
        return Map.of("query", Map.of("pages", pages, "redirects", redirects, "normalized", normalized));
    }

    void fails(GameException.Code code, Runnable run) {
        assertThatThrownBy(run::run).isInstanceOfSatisfying(GameException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test void searchParsesDeduplicatesAndFiltersNamespace() { assertThat(provider.search("Sta")).containsExactly("Start"); }
    @Test void canonicalCasingAndRedirectChains() {
        assertThat(provider.resolve("start")).isEqualTo("Start");
        assertThat(provider.resolve("Alias")).isEqualTo("Middle");
        assertThat(provider.resolve("Main_article")).isEqualTo("Main article");
    }
    @Test void mainNamespaceColonAndDisambiguationAreAllowed() {
        assertThat(provider.resolve("Star Trek: Voyager")).isEqualTo("Star Trek: Voyager");
        assertThat(provider.resolve("Mercury (disambiguation)")).isEqualTo("Mercury (disambiguation)");
    }
    @Test void missingAndNonMainArticlesAreRejected() {
        fails(ARTICLE_NOT_FOUND, () -> provider.resolve("Missing"));
        fails(ARTICLE_NOT_FOUND, () -> provider.resolve("Talk:Start"));
    }
    @ParameterizedTest @ValueSource(strings = {"https://evil.test/page", "//evil.test", "Start|Target", "Start#section", " "})
    void hostileTitlesNeverReachHttp(String title) {
        fails(ARTICLE_NOT_FOUND, () -> provider.resolve(title));
        assertThat(requests).isEmpty();
    }
    @Test void snapshotContainsCanonicalEdgesAndSanitizedHtml() {
        ArticleData article = provider.loadArticle("Start");
        assertThat(article.outgoingLinks()).containsExactly("Middle");
        assertThat(article.html()).contains("data-wiki-title=\"Middle\"").doesNotContain("script", "onclick", "Category:");
        assertThat(article.revisionId()).isEqualTo(42L);
        assertThatThrownBy(() -> article.outgoingLinks().add("Other")).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void warmCacheDoesNotFetchAgainAndExpiryReloads() {
        ArticleData first = provider.loadArticle("Start");
        int count = requests.size();
        assertThat(provider.loadArticle("Start")).isSameAs(first);
        assertThat(requests).hasSize(count);
        ticker.addAndGet(Duration.ofMinutes(31).toNanos());
        assertThat(provider.loadArticle("Start")).isNotSameAs(first);
        assertThat(requests.size()).isGreaterThan(count);
    }
    @Test void cachesAreBounded() throws Exception {
        for (String title : List.of("Start", "Middle", "Target", "Other")) provider.loadArticle(title);
        for (int i = 0; i < 10; i++) provider.resolve("Article " + i);
        for (String field : List.of("articles", "aliases")) {
            var member = WikipediaArticleProvider.class.getDeclaredField(field);
            member.setAccessible(true);
            var cache = (com.github.benmanes.caffeine.cache.Cache<?, ?>) member.get(provider);
            cache.cleanUp();
            assertThat(cache.estimatedSize()).isLessThanOrEqualTo(2);
        }
    }

    @Test void redirectsIntoNonMainNamespaceAreRejected() {
        fixture = q -> Map.of("query", Map.of("redirects", List.of(Map.of("from", "Alias", "to", "Talk:Start")),
                "pages", List.of(Map.of("title", "Talk:Start", "ns", 1, "pageid", 1))));
        fails(ARTICLE_NOT_FOUND, () -> provider.resolve("Alias"));
    }

    @Test void circularRedirectsAreRejected() {
        fixture = q -> Map.of("query", Map.of("redirects", List.of(Map.of("from", "Alias", "to", "Loop"),
                Map.of("from", "Loop", "to", "Alias")), "pages", List.of()));
        fails(ARTICLE_NOT_FOUND, () -> provider.resolve("Alias"));
    }

    @Test void outgoingResolutionUsesBatchesOfAtMostFiftyAndNeverDropsIncompleteData() {
        fixture = q -> {
            if (q.get("action").equals("parse")) {
                List<Map<String, Object>> links = new ArrayList<>();
                for (int i = 0; i < 55; i++) links.add(Map.of("ns", 0, "title", "Link " + i, "exists", true));
                return Map.of("parse", Map.of("title", "Start", "revid", 42, "text", "<p>Start</p>", "links", links));
            }
            return graph(q);
        };
        assertThat(provider.loadArticle("Start").outgoingLinks()).hasSize(55);
        List<Integer> sizes = requests.stream().map(WikipediaArticleProviderTest::parameters)
                .filter(q -> q.containsKey("titles")).map(q -> q.get("titles").split("\\|").length).toList();
        assertThat(sizes).containsExactly(1, 50, 5);
        ticker.addAndGet(Duration.ofMinutes(31).toNanos());
        Function<Map<String, String>, Object> complete = fixture;
        fixture = q -> q.containsKey("titles") && q.get("titles").startsWith("Link") ?
                Map.of("query", Map.of("pages", List.of())) : complete.apply(q);
        fails(WIKIPEDIA_UNAVAILABLE, () -> provider.loadArticle("Start"));
    }

    @Test void streamingBodySizeLimitCancelsBeforeFullResponseIsBuffered() {
        when(http.sendAsync(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenAnswer(invocation -> {
                    HttpResponse.BodyHandler<byte[]> handler = invocation.getArgument(1);
                    var subscriber = handler.apply(mock(HttpResponse.ResponseInfo.class));
                    var subscription = mock(java.util.concurrent.Flow.Subscription.class);
                    subscriber.onSubscribe(subscription);
                    subscriber.onNext(List.of(java.nio.ByteBuffer.wrap(new byte[8193])));
                    verify(subscription).cancel();
                    return subscriber.getBody().toCompletableFuture().thenApply(body -> {
                        HttpResponse<byte[]> response = mock(HttpResponse.class);
                        when(response.statusCode()).thenReturn(200);
                        when(response.body()).thenReturn(body);
                        return response;
                    });
                });
        fails(WIKIPEDIA_UNAVAILABLE, () -> provider.search("Start"));
    }

    @Test void rejectedNamespaceOutgoingDestinationsAreOmitted() {
        fixture = q -> q.containsKey("titles") && q.get("titles").equals("Alias") ?
                Map.of("query", Map.of("redirects", List.of(Map.of("from", "Alias", "to", "Talk:Start")),
                        "pages", List.of(Map.of("title", "Talk:Start", "ns", 1, "pageid", 1)))) : graph(q);
        ArticleData article = provider.loadArticle("Start");
        assertThat(article.outgoingLinks()).isEmpty();
        assertThat(article.html()).doesNotContain("data-wiki-title");
    }
    @ParameterizedTest @ValueSource(ints = {429, 500, 503, 302})
    void upstreamHttpFailuresAreRetryableAndNotCached(int status) {
        fixture = q -> status;
        fails(WIKIPEDIA_UNAVAILABLE, () -> provider.loadArticle("Start"));
        fixture = this::graph;
        assertThat(provider.loadArticle("Start").title()).isEqualTo("Start");
    }
    @ParameterizedTest @ValueSource(strings = {"not json", "[]", "{}", "{\"query\":{\"pages\":{}}}", "{\"error\":{\"code\":\"maxlag\"}}", "{\"query\":{\"pages\":[]}} trailing"})
    void malformedResponsesFailClosed(String body) {
        fixture = q -> body;
        fails(WIKIPEDIA_UNAVAILABLE, () -> provider.resolve("Start"));
    }
    @Test void missingApiErrorIsNotFound() {
        fixture = q -> Map.of("error", Map.of("code", "missingtitle"));
        fails(ARTICLE_NOT_FOUND, () -> provider.loadArticle("Start"));
    }
    @Test void timeoutCancelsRequest() {
        var pending = new CompletableFuture<HttpResponse<byte[]>>();
        fixture = q -> pending;
        fails(WIKIPEDIA_UNAVAILABLE, () -> provider.search("Start"));
        assertThat(pending.isCancelled()).isTrue();
    }
    @Test void connectionFailureIsRetryable() {
        fixture = q -> CompletableFuture.failedFuture(new java.io.IOException("offline"));
        fails(WIKIPEDIA_UNAVAILABLE, () -> provider.search("Start"));
    }
    @Test void oversizedResponseIsRejected() {
        fixture = q -> "x".repeat(8193);
        fails(WIKIPEDIA_UNAVAILABLE, () -> provider.search("Start"));
    }
    @Test void partialParseIsRejectedAndRetrySucceeds() {
        fixture = q -> q.get("action").equals("parse") ? Map.of("parse", Map.of("title", "Start")) : graph(q);
        fails(WIKIPEDIA_UNAVAILABLE, () -> provider.loadArticle("Start"));
        fixture = this::graph;
        assertThat(provider.loadArticle("Start").title()).isEqualTo("Start");
    }
    @Test void realProviderGameValidatesEdgesClosePrivacyAndWinner() {
        AtomicLong seconds = new AtomicLong();
        Clock clock = new Clock() {
            public Instant instant() { return Instant.EPOCH.plusSeconds(seconds.get()); }
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
        };
        var games = new GameService(new RoomRegistry(), provider, clock, 2048);
        var host = games.create("Host");
        String code = host.view().room().roomCode();
        var guest = games.join(code, "Guest");
        String token = host.playerSessionToken();
        games.configure(code, token, new SettingsPatch(true, "start", true, "Target", true, true, null));
        games.ready(code, token, true);
        games.ready(code, guest.playerSessionToken(), true);
        games.start(code, token);
        seconds.set(3);
        fails(INVALID_NAVIGATION, () -> games.move(code, token, new MoveAction(UUID.randomUUID(), MoveType.NAVIGATE, "Other")));
        assertThat(games.get(code, token).me().clickCount()).isZero();
        UUID id = UUID.randomUUID();
        fixture = q -> q.get("action").equals("parse") && q.get("pageid").equals("2") ? 503 : graph(q);
        fails(WIKIPEDIA_UNAVAILABLE, () -> games.move(code, token, new MoveAction(id, MoveType.NAVIGATE, "Alias")));
        assertThat(games.currentArticle(code, token).title()).isEqualTo("Start");
        fixture = this::graph;
        var move = games.move(code, token, new MoveAction(id, MoveType.NAVIGATE, "Alias"));
        assertThat(move.view().me().currentArticle()).isEqualTo("Middle");
        assertThat(move.view().room().players().getFirst().closeToTarget()).isTrue();
        assertThat(move.view().room().players().getFirst().currentArticle()).isNull();
        assertThat(games.currentArticle(code, token).title()).isEqualTo("Middle");
        assertThat(games.currentArticle(code, guest.playerSessionToken()).title()).isEqualTo("Start");
        assertThat(games.move(code, token, new MoveAction(UUID.randomUUID(), MoveType.NAVIGATE, "Target"))
                .view().room().winnerPlayerId()).isEqualTo(host.playerId());
    }
}
