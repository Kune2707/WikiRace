package com.wikirace.wikipedia;

import java.util.Set;

public record ArticleData(String title, Set<String> outgoingLinks, String html, Long revisionId) {
    public ArticleData {
        outgoingLinks = Set.copyOf(outgoingLinks);
    }

    public ArticleData(String title, Set<String> outgoingLinks) {
        this(title, outgoingLinks, "", null);
    }
}
