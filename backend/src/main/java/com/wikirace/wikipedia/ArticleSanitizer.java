package com.wikirace.wikipedia;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;

public class ArticleSanitizer {
    private static final Safelist ALLOWED = new Safelist()
            .addTags("p", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "dl", "dt", "dd",
                    "strong", "em", "b", "i", "sup", "sub", "br", "blockquote", "pre", "code",
                    "table", "thead", "tbody", "tr", "th", "td", "caption", "a", "span")
            .addAttributes(":all", "id")
            .addAttributes("a", "href", "data-wiki-title");

    public String sanitize(String html, Map<String, String> canonicalLinks) {
        Document document = Jsoup.parseBodyFragment(html);
        document.select("script,style,iframe,form,input,button,textarea,select,object,embed,svg,math,link,meta").remove();
        for (Element element : document.getAllElements()) {
            element.removeAttr("data-wiki-title");
            if (element.hasAttr("id")) element.attr("id", "wiki-" + element.id());
        }
        for (Element anchor : document.select("a")) {
            String href = anchor.attr("href");
            String destination = internalTitle(href);
            String canonical = destination == null ? null : canonicalLinks.get(destination);
            if (href.startsWith("#") && href.length() > 1) {
                anchor.attr("href", "#wiki-" + href.substring(1));
            } else if (canonical != null) {
                anchor.attr("href", "#").attr("data-wiki-title", canonical);
            } else anchor.unwrap();
        }
        Document clean = new Cleaner(ALLOWED).clean(document);
        clean.outputSettings().prettyPrint(false);
        return clean.body().html();
    }

    private static String internalTitle(String href) {
        try {
            URI uri = URI.create(href);
            if (uri.getScheme() != null && uri.getHost() == null) return null;
            if (uri.getRawUserInfo() != null || uri.getPort() != -1 || uri.getRawQuery() != null) return null;
            if (uri.getHost() != null && !uri.getHost().equalsIgnoreCase("en.wikipedia.org")) return null;
            if (uri.getScheme() != null && !uri.getScheme().equalsIgnoreCase("https")) return null;
            String path = uri.getRawPath();
            String encoded;
            if (path != null && path.startsWith("/wiki/")) encoded = path.substring(6);
            else if (uri.getHost() == null && uri.getScheme() == null && path != null && path.startsWith("./")) encoded = path.substring(2);
            else return null;
            // '+' is literal in article URL paths, unlike form-encoded query strings.
            return WikiTitles.normalize(URLDecoder.decode(encoded.replace("+", "%2B"), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException | com.wikirace.exception.GameException e) { return null; }
    }
}
