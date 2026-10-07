package com.wikirace.wikipedia;

import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.wikirace.exception.GameException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import static com.wikirace.exception.GameException.Code.ARTICLE_NOT_FOUND;

@Component
@Profile("!test")
public class WikipediaArticleProvider implements ArticleProvider {
    private record Page(String title, long id) {}
    private final MediaWikiClient client;
    private final WikipediaProperties properties;
    private final Cache<String, Page> aliases;
    private final Cache<String, ArticleData> articles;
    private final ArticleSanitizer sanitizer = new ArticleSanitizer();

    @Autowired
    public WikipediaArticleProvider(MediaWikiClient client, WikipediaProperties properties) {
        this(client, properties, Ticker.systemTicker());
    }

    WikipediaArticleProvider(MediaWikiClient client, WikipediaProperties properties, Ticker ticker) {
        this.client = client;
        this.properties = properties;
        aliases = Caffeine.newBuilder().maximumSize(properties.cacheSize())
                .expireAfterWrite(properties.cacheTtl()).ticker(ticker).build();
        articles = Caffeine.newBuilder().maximumSize(properties.cacheSize())
                .expireAfterWrite(properties.cacheTtl()).ticker(ticker).build();
    }

    private long deadline() { return System.nanoTime() + properties.loadTimeout().toNanos(); }

    public List<String> search(String query) {
        String prefix = WikiTitles.normalize(query);
        JsonNode root = client.get(Map.of("action", "query", "list", "prefixsearch", "pssearch", prefix,
                "psnamespace", "0", "pslimit", "10"), deadline());
        JsonNode results = root.path("query").path("prefixsearch");
        if (!results.isArray()) throw MediaWikiClient.unavailable();
        Set<String> titles = new LinkedHashSet<>();
        for (JsonNode result : results) {
            if (!result.path("ns").isIntegralNumber() || !result.path("title").isTextual()) {
                throw MediaWikiClient.unavailable();
            }
            if (result.path("ns").asInt() == 0) titles.add(WikiTitles.normalize(result.path("title").asText()));
        }
        return titles.stream().limit(10).toList();
    }

    public String resolve(String title) { return resolvePage(title, deadline()).title(); }

    private Page resolvePage(String title, long deadline) {
        String normalized = WikiTitles.normalize(title);
        return aliases.get(normalized, key -> {
            Page page = queryPages(List.of(key), deadline).get(key);
            if (page == null) throw new GameException(ARTICLE_NOT_FOUND, "Wikipedia article not found in the main namespace.");
            return page;
        });
    }

    public ArticleData loadArticle(String title) {
        long deadline = deadline();
        Page page = resolvePage(title, deadline);
        return articles.get(page.title(), key -> load(page, deadline));
    }

    private ArticleData load(Page page, long deadline) {
        JsonNode parsed = client.get(Map.of("action", "parse", "pageid", Long.toString(page.id()),
                "prop", "text|links|revid"), deadline).path("parse");
        if (!parsed.path("title").isTextual() || !page.title().equals(parsed.path("title").asText()) ||
                !parsed.path("text").isTextual() || !parsed.path("revid").isIntegralNumber() ||
                parsed.path("revid").asLong() <= 0 || !parsed.path("links").isArray()) {
            throw MediaWikiClient.unavailable();
        }
        Set<String> rawLinks = new LinkedHashSet<>();
        for (JsonNode link : parsed.path("links")) {
            if (!link.path("ns").isIntegralNumber() || !link.path("title").isTextual() ||
                    (link.has("exists") && !link.path("exists").isBoolean())) throw MediaWikiClient.unavailable();
            if (link.path("ns").asInt() == 0 && link.path("exists").asBoolean(false)) {
                rawLinks.add(WikiTitles.normalize(link.path("title").asText()));
            }
        }
        Map<String, String> canonicalLinks = new HashMap<>();
        List<String> links = new ArrayList<>(rawLinks);
        for (int offset = 0; offset < links.size(); offset += 50) {
            queryPages(links.subList(offset, Math.min(offset + 50, links.size())), deadline)
                    .forEach((raw, canonical) -> canonicalLinks.put(raw, canonical.title()));
        }
        return new ArticleData(page.title(), new HashSet<>(canonicalLinks.values()),
                sanitizer.sanitize(parsed.path("text").asText(), canonicalLinks), parsed.path("revid").asLong());
    }

    private Map<String, Page> queryPages(List<String> titles, long deadline) {
        JsonNode root = client.get(Map.of("action", "query", "titles", String.join("|", titles),
                "redirects", "1", "prop", "info"), deadline);
        JsonNode query = root.path("query");
        if (!query.path("pages").isArray() || root.has("continue")) throw MediaWikiClient.unavailable();
        Map<String, String> rewrites = new HashMap<>();
        for (String field : List.of("normalized", "redirects")) {
            JsonNode mappings = query.path(field);
            if (mappings.isMissingNode()) continue;
            if (!mappings.isArray()) throw MediaWikiClient.unavailable();
            for (JsonNode mapping : mappings) {
                if (!mapping.path("from").isTextual() || !mapping.path("to").isTextual()) throw MediaWikiClient.unavailable();
                rewrites.put(WikiTitles.normalize(mapping.path("from").asText()), WikiTitles.normalize(mapping.path("to").asText()));
            }
        }
        Map<String, Page> pages = new HashMap<>();
        Set<String> returned = new HashSet<>();
        for (JsonNode node : query.path("pages")) {
            if (!node.path("title").isTextual() || !node.path("ns").isIntegralNumber()) throw MediaWikiClient.unavailable();
            String title = WikiTitles.normalize(node.path("title").asText());
            returned.add(title);
            if (node.has("missing") || node.has("invalid") || node.path("ns").asInt() != 0) continue;
            if (!node.path("pageid").isIntegralNumber() || node.path("pageid").asLong() <= 0) throw MediaWikiClient.unavailable();
            pages.put(title, new Page(title, node.path("pageid").asLong()));
        }
        Map<String, Page> result = new HashMap<>();
        for (String original : titles) {
            String canonical = original;
            Set<String> visited = new HashSet<>();
            while (rewrites.containsKey(canonical) && visited.add(canonical)) canonical = rewrites.get(canonical);
            if (rewrites.containsKey(canonical)) continue; // Circular redirects are not playable articles.
            if (!returned.contains(canonical)) throw MediaWikiClient.unavailable();
            Page page = pages.get(canonical);
            if (page != null) result.put(original, page);
        }
        return result;
    }
}
