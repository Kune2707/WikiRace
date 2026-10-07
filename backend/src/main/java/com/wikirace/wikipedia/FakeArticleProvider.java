package com.wikirace.wikipedia;

import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import com.wikirace.exception.GameException;
import static com.wikirace.exception.GameException.Code.ARTICLE_NOT_FOUND;

@Component
@Profile("test")
public class FakeArticleProvider implements ArticleProvider {
    private static final Map<String, Set<String>> GRAPH = Map.of(
            "Computer Science", Set.of("Algorithm", "Network", "Mathematics"),
            "Algorithm", Set.of("Computer Science", "Graph Theory", "Mathematics"),
            "Graph Theory", Set.of("Algorithm", "Mathematics", "Network"),
            "Mathematics", Set.of("Algorithm", "Physics", "Quantum Mechanics"),
            "Physics", Set.of("Mathematics", "Quantum Mechanics"),
            "Quantum Mechanics", Set.of("Physics"),
            "Network", Set.of("Computer Science", "Internet", "Graph Theory"),
            "Internet", Set.of("Network", "World Wide Web"),
            "World Wide Web", Set.of("Internet", "Computer Science"));

    @Override
    public List<String> search(String query) {
        String prefix = query.trim().replace('_', ' ').toLowerCase(Locale.ROOT);
        return GRAPH.keySet().stream().filter(title -> title.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted().limit(10).toList();
    }

    @Override
    public String resolve(String title) {
        if (title != null) {
            String normalized = title.trim().replace('_', ' ');
            for (String canonical : GRAPH.keySet()) {
                if (canonical.equalsIgnoreCase(normalized)) return canonical;
            }
        }
        throw new GameException(ARTICLE_NOT_FOUND, "Article is not in the fake graph.");
    }

    @Override
    public ArticleData loadArticle(String title) {
        String canonical = resolve(title);
        String links = GRAPH.get(canonical).stream().sorted()
                .map(link -> "<li><a href=\"#\" data-wiki-title=\"" + link + "\">" + link + "</a></li>")
                .collect(java.util.stream.Collectors.joining());
        return new ArticleData(canonical, GRAPH.get(canonical), "<p>Fake article: " + canonical + "</p><ul>" + links + "</ul>", null);
    }
}
