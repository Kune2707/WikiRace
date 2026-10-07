package com.wikirace.wikipedia;

import java.util.Map;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ArticleSanitizerTest {
    final ArticleSanitizer sanitizer = new ArticleSanitizer();
    @Test void removesActiveContentAndForgedMetadataButPreservesReadingStructure() {
        String html = sanitizer.sanitize("<h2 id='Intro' style='x' onclick='x'>Intro</h2><p>Hello <b>reader</b></p>" +
                "<script>secret</script><style>secret</style><iframe>secret</iframe><form><input></form>" +
                "<object>secret</object><embed><svg onload='x'></svg><img src='x' onerror='x'>" +
                "<a href='javascript:bad()' data-wiki-title='Target'>Unsafe</a>" +
                "<span data-wiki-title='Target'>Forged</span><a href='#Intro'>Section</a>" +
                "<table><tr><td>Cell</td></tr></table>", Map.of());
        assertThat(html).contains("<b>reader</b>", "<td>Cell</td>", "href=\"#wiki-Intro\"", "id=\"wiki-Intro\"");
        assertThat(html).doesNotContain("secret", "script", "style=", "onclick", "iframe", "input", "object", "embed", "onerror", "data-wiki-title", "javascript:");
    }
    @Test void onlyVerifiedEnglishInternalLinksBecomeMovementLinks() {
        String html = sanitizer.sanitize("<a href='/wiki/Graph_theory#History'>Graph</a>" +
                "<a href='https://en.wikipedia.org/wiki/Alias'>Alias</a>" +
                "<a href='./C%2B%2B'>Language</a><a href='//en.wikipedia.org/wiki/Alias'>Alias2</a>" +
                "<a href='https://evil.test/wiki/Alias'>External</a><a href='https://de.wikipedia.org/wiki/Alias'>Other language</a>" +
                "<a href='/wiki/Talk:Graph_theory'>Talk</a><a href='/w/index.php?title=Alias'>Edit</a>" +
                "<a href='/wiki/Alias?x=1'>Query</a><a href='/wiki/Missing'>Missing</a>",
                Map.of("Graph theory", "Graph theory", "Alias", "Canonical", "C++", "C++"));
        var document = Jsoup.parseBodyFragment(html);
        assertThat(document.select("a[data-wiki-title]")).hasSize(4);
        assertThat(document.select("a").eachAttr("href")).containsOnly("#");
        assertThat(html).doesNotContain("evil.test", "de.wikipedia", "Talk:", "index.php", "?x=", "data-wiki-title=\"Missing\"");
        assertThat(document.text()).contains("External", "Other language", "Missing");
    }
}
