package com.wikirace.wikipedia;

import java.util.Map;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ArticleSanitizerTest {
    final ArticleSanitizer sanitizer = new ArticleSanitizer();
    @Test void preservesTableSemanticsWithoutWikipediaLayoutOverrides() {
        var document = Jsoup.parseBodyFragment(sanitizer.sanitize("""
            <table class="infobox wikitable sortable navbox evil blocked" width="9999" style="width:9999px;min-width:9999px;max-width:1px;float:right;clear:both;position:fixed;display:block">
              <caption>Credits</caption><colgroup span="2"><col span="2" width="4"></colgroup>
              <thead><tr><th id="Name" scope="col" colspan="2" abbr="Name">Actor</th></tr></thead>
              <tbody><tr><th scope="row" rowspan="2">Richard Burton</th><td headers="Name" rowspan="0"><div style="display:none">Block entry</div><div>\u200B</div><ul><li><a href="/wiki/Actor">Actor</a></li></ul></td></tr><tr></tr></tbody>
              <tfoot><tr><td colspan="2">Long notes</td></tr></tfoot>
            </table>
            """, Map.of("Actor", "Actor")));
        assertThat(document.selectFirst("table").classNames()).containsExactlyInAnyOrder("infobox", "wikitable", "sortable", "navbox");
        assertThat(document.select("[style],[width],[min-width],[max-width]")).isEmpty();
        assertThat(document.selectFirst("th").attr("colspan")).isEqualTo("2");
        assertThat(document.selectFirst("th[scope=row]").attr("rowspan")).isEqualTo("2");
        assertThat(document.selectFirst("td[rowspan]").attr("rowspan")).isEqualTo("0");
        assertThat(document.selectFirst("td[headers]").attr("headers")).isEqualTo("wiki-Name");
        assertThat(document.selectFirst("th").id()).isEqualTo("wiki-Name");
        assertThat(document.selectFirst("td > div").text()).isEqualTo("Block entry");
        assertThat(document.select("td > div")).hasSize(1);
        assertThat(document.select("caption,thead,tbody,tfoot,colgroup,col,ul,li,a[data-wiki-title]")).hasSize(9);
    }
    @Test void preservesDifferentMergedAndUnclassifiedTableShapes() {
        for (String table : java.util.List.of(
                "<table><tr><th rowspan='4'>Era</th><td>First</td></tr><tr><td>Second</td></tr><tr><td>Third</td></tr><tr><td>Fourth</td></tr></table>",
                "<table class='navbox-inner'><tr><th colspan='12'>Awards</th></tr><tr><td colspan='6'>Film</td><td colspan='6'>Television</td></tr></table>",
                "<table><tr><td><p>Long readable notes</p><ul><li>Nomination</li><li>Winner</li></ul><table><tr><td>Nested</td></tr></table></td></tr></table>")) {
            var original = Jsoup.parseBodyFragment(table);
            var clean = Jsoup.parseBodyFragment(sanitizer.sanitize(table, Map.of()));
            assertThat(clean.text()).isEqualTo(original.text());
            assertThat(clean.select("table,tr,th,td,ul,li,p").eachText())
                    .isEqualTo(original.select("table,tr,th,td,ul,li,p").eachText());
            assertThat(clean.select("[rowspan]").eachAttr("rowspan")).isEqualTo(original.select("[rowspan]").eachAttr("rowspan"));
            assertThat(clean.select("[colspan]").eachAttr("colspan")).isEqualTo(original.select("[colspan]").eachAttr("colspan"));
        }
    }
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
