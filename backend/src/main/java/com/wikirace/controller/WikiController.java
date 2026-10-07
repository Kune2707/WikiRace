package com.wikirace.controller;

import java.util.List;
import com.wikirace.wikipedia.ArticleProvider;
import com.wikirace.exception.GameException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wiki")
public class WikiController {
    private final ArticleProvider articles;
    public WikiController(ArticleProvider articles) { this.articles = articles; }
    public record SearchResult(String title) {}
    public record SearchResponse(List<SearchResult> results) {}

    @GetMapping("/search")
    public SearchResponse search(@RequestParam String q) {
        if (q.isBlank() || q.length() > 300 || q.chars().anyMatch(Character::isISOControl)) {
            throw new GameException(GameException.Code.INVALID_REQUEST, "Provide a search query of 1-300 characters.");
        }
        return new SearchResponse(articles.search(q).stream().map(SearchResult::new).toList());
    }
}
