package com.wikirace.wikipedia;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import com.wikirace.exception.GameException;
import static com.wikirace.exception.GameException.Code.ARTICLE_NOT_FOUND;

public final class WikiTitles {
    private WikiTitles() {}
    public static String normalize(String title) {
        if (title == null) throw invalid();
        String normalized = title.trim().replace('_', ' ');
        if (normalized.isBlank() || normalized.length() > 300 || normalized.contains("|") ||
                normalized.contains("#") || normalized.contains("://") || normalized.startsWith("//") ||
                normalized.codePoints().anyMatch(Character::isISOControl)) throw invalid();
        return normalized;
    }
    public static String sourceUrl(String title) {
        return "https://en.wikipedia.org/wiki/" + URLEncoder.encode(title.replace(' ', '_'), StandardCharsets.UTF_8)
                .replace("+", "%20");
    }
    private static GameException invalid() { return new GameException(ARTICLE_NOT_FOUND, "A valid English Wikipedia article title is required."); }
}
